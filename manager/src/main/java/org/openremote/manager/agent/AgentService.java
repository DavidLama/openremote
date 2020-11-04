/*
 * Copyright 2016, OpenRemote Inc.
 *
 * See the CONTRIBUTORS.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openremote.manager.agent;

import org.apache.camel.builder.RouteBuilder;
import org.openremote.agent.protocol.ProtocolAssetService;
import org.openremote.container.Container;
import org.openremote.container.message.MessageBrokerService;
import org.openremote.container.persistence.PersistenceEvent;
import org.openremote.container.timer.TimerService;
import org.openremote.manager.asset.*;
import org.openremote.manager.event.ClientEventService;
import org.openremote.manager.gateway.GatewayService;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.manager.web.ManagerWebService;
import org.openremote.container.ContainerService;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetModelProvider;
import org.openremote.model.asset.AssetType;
import org.openremote.model.asset.agent.*;
import org.openremote.model.attribute.*;
import org.openremote.model.attribute.AttributeEvent.Source;
import org.openremote.model.event.shared.TenantFilter;
import org.openremote.model.query.AssetQuery;
import org.openremote.model.query.filter.PathPredicate;
import org.openremote.model.query.filter.RefPredicate;
import org.openremote.model.security.ClientRole;
import org.openremote.model.util.Pair;
import org.openremote.model.util.TextUtil;

import javax.persistence.EntityManager;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.openremote.container.concurrent.GlobalLock.withLock;
import static org.openremote.container.concurrent.GlobalLock.withLockReturning;
import static org.openremote.container.persistence.PersistenceEvent.*;
import static org.openremote.manager.asset.AssetProcessingService.ASSET_QUEUE;
import static org.openremote.model.AbstractValueTimestampHolder.VALUE_TIMESTAMP_FIELD_NAME;
import static org.openremote.model.asset.AssetType.AGENT;
import static org.openremote.model.asset.agent.AgentLink.getAgentLink;
import static org.openremote.model.asset.agent.ConnectionStatus.*;
import static org.openremote.model.asset.agent.Protocol.ACTUATOR_TOPIC;
import static org.openremote.model.asset.agent.Protocol.SENSOR_QUEUE;
import static org.openremote.model.attribute.Attribute.attributesFromJson;
import static org.openremote.model.attribute.Attribute.getAddedOrModifiedAttributes;
import static org.openremote.model.attribute.AttributeEvent.HEADER_SOURCE;
import static org.openremote.model.attribute.AttributeEvent.Source.GATEWAY;
import static org.openremote.model.attribute.AttributeEvent.Source.SENSOR;
import static org.openremote.model.util.TextUtil.isNullOrEmpty;
import static org.openremote.model.util.TextUtil.isValidURN;

/**
 * Handles life cycle and communication with {@link Protocol}s.
 * <p>
 * Finds all {@link Agent} assets and manages their {@link Protocol} instances.
 */
public class AgentService extends RouteBuilder implements ContainerService, AssetUpdateProcessor, ProtocolAssetService, AssetModelProvider {

    private static final Logger LOG = Logger.getLogger(AgentService.class.getName());
    public static final int PRIORITY = DEFAULT_PRIORITY + 100; // Start quite late to ensure protocols etc. are initialised
    protected TimerService timerService;
    protected ManagerIdentityService identityService;
    protected AssetProcessingService assetProcessingService;
    protected AssetStorageService assetStorageService;
    protected MessageBrokerService messageBrokerService;
    protected ClientEventService clientEventService;
    protected GatewayService gatewayService;
    protected final Map<AttributeRef, Pair<Attribute, ConnectionStatus>> protocolConfigurations = new HashMap<>();
    protected final Map<String, List<Consumer<PersistenceEvent<Asset>>>> childAssetSubscriptions = new HashMap<>();
    protected final Map<String, Protocol> protocols = new HashMap<>();
    protected final Map<AttributeRef, List<Attribute>> linkedAttributes = new HashMap<>();
    protected Map<String, Asset> agentMap;
    protected boolean initDone;
    protected Container container;

    /**
     * It's important that {@link Protocol}s have a lower priority than this service so they are fully initialized
     * before this service is started.
     */
    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public void init(Container container) throws Exception {
        this.container = container;
        timerService = container.getService(TimerService.class);
        identityService = container.getService(ManagerIdentityService.class);
        assetProcessingService = container.getService(AssetProcessingService.class);
        assetStorageService = container.getService(AssetStorageService.class);
        messageBrokerService = container.getService(MessageBrokerService.class);
        clientEventService = container.getService(ClientEventService.class);
        gatewayService = container.getService(GatewayService.class);
        localAgentConnector = new LocalAgentConnector(this);

        // Register this service as an asset model provider
        container.getService(AssetModelService.class).addAssetModelProvider(this);

        if (initDone) {
            return;
        }

        clientEventService.addSubscriptionAuthorizer((auth, subscription) ->
            subscription.isEventType(AgentStatusEvent.class)
                && identityService.getIdentityProvider()
                .canSubscribeWith(auth, subscription.getFilter() instanceof TenantFilter<?>
                    ? ((TenantFilter<?>) subscription.getFilter())
                    : null, ClientRole.READ_ASSETS));

        container.getService(ManagerWebService.class).getApiSingletons().add(
            new AgentResourceImpl(
                container.getService(TimerService.class),
                container.getService(ManagerIdentityService.class),
                assetStorageService,
                this)
        );



        initDone = true;
    }

    @Override
    public void start(Container container) throws Exception {
        container.getService(MessageBrokerService.class).getContext().addRoutes(this);

        // Load all protocol instances and fail hard and fast when a duplicate is found
        Collection<Protocol> discoveredProtocols = container.getServices(Protocol.class);

        discoveredProtocols
            .forEach(
                discoveredProtocol -> {
                    if (isNullOrEmpty(discoveredProtocol.getProtocolName())
                        || !isValidURN(discoveredProtocol.getProtocolName()))
                        throw new IllegalStateException(
                            "Protocol name is not a valid URN: " + discoveredProtocol.getClass()
                        );
                    if (protocols.containsKey(discoveredProtocol.getProtocolName()))
                        throw new IllegalStateException(
                            "A protocol with the name '" + discoveredProtocol.getProtocolName()
                                + "' has already been loaded: " + discoveredProtocol.getClass()
                        );
                    protocols.put(discoveredProtocol.getProtocolName(), discoveredProtocol);
                }
            );

        Collection<Asset> agents = getAgents().values();
        LOG.fine("Deploy all agents in all realms: " + agents.size());

        /// For all agents, go through their protocol configurations and find
        // assets that are linked to them, to create the binding on startup
        for (Asset agent : agents) {
            linkProtocolConfigurations(agent, agent.getAttributesStream()
                .filter(ProtocolConfiguration::isProtocolConfiguration)
                .collect(Collectors.toList())
            );
        }
    }

    @Override
    public void stop(Container container) throws Exception {
        agentMap.values().forEach(agent ->
            unlinkProtocolConfigurations(
                agent,
                agent.getAttributesStream()
                    .filter(ProtocolConfiguration::isProtocolConfiguration)
                    .collect(Collectors.toList())));
        agentMap.clear();
        protocols.clear();
    }

    @Override
    public void configure() throws Exception {
        from(PERSISTENCE_TOPIC)
            .routeId("AgentPersistenceChanges")
            .filter(isPersistenceEventForEntityType(Asset.class))
            .process(exchange -> {
                @SuppressWarnings("unchecked")
                PersistenceEvent<Asset> persistenceEvent = (PersistenceEvent<Asset>)exchange.getIn().getBody(PersistenceEvent.class);
                Asset asset = persistenceEvent.getEntity();
                if (isPersistenceEventForAssetType(AGENT).matches(exchange)) {
                    processAgentChange(asset, persistenceEvent);
                } else {
                    processAssetChange(asset, persistenceEvent);
                }
            });

        // A protocol wants to write a new sensor value
        from(SENSOR_QUEUE)
            .routeId("FromSensorUpdates")
            .filter(body().isInstanceOf(AttributeEvent.class))
            .setHeader(HEADER_SOURCE, () -> SENSOR)
            .to(ASSET_QUEUE);
    }

    /**
     * This should only be called by protocol implementations to request an update to
     * one of their own protocol configuration attributes.
     */
    @Override
    public void updateProtocolConfiguration(Attribute protocolConfiguration) {
        if (protocolConfiguration == null || !protocolConfiguration.getReference().isPresent()) {
            LOG.warning("Cannot update invalid: " + protocolConfiguration);
            return;
        }

        AttributeRef protocolRef = protocolConfiguration.getReference().get();
        Asset agent = assetStorageService.find(protocolRef.getEntityId(), true);
        if (agent == null || agent.getWellKnownType() != AssetType.AGENT || !agent.hasAttribute(protocolRef.getAttributeName())) {
            LOG.warning("Protocol configuration doesn't belong to a valid agent: " + protocolConfiguration);
            return;
        }

        // Check protocol configuration has changed
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        Attribute oldProtocolConfiguration = agent.getAttribute(protocolRef.getAttributeName()).get();
        if (oldProtocolConfiguration.equals(protocolConfiguration)) {
            // Protocol configuration hasn't changed so nothing to do here
            return;
        }

        agent.replaceAttribute(protocolConfiguration);
        LOG.fine("Updating agent protocol configuration: " + protocolRef);
        assetStorageService.merge(agent);
    }

    @Override
    public Asset mergeAsset(Asset asset) {
        Objects.requireNonNull(asset.getId());
        Objects.requireNonNull(asset.getParentId());
        return mergeAsset(asset, null);
    }

    @Override
    public Asset mergeAsset(Asset asset, MergeOptions options) {
        Objects.requireNonNull(asset.getId());
        Objects.requireNonNull(asset.getParentId());

        Asset updatedAsset = Asset.map(asset, new Asset());
        // Use the unique identifier provided by the protocol, it manages its own identifier space
        updatedAsset.setId(asset.getId());

        if (options != null && (options.getIgnoredAttributeNames() != null || options.getIgnoredAttributeKeys() != null)) {
            Asset existingAsset = assetStorageService.find(updatedAsset.getId(), true);
            if (existingAsset != null) {
                // Check if any attributes except the ignored ones were modified
                List<Attribute> existingAttributes = existingAsset.getAttributesList();
                List<Attribute> updatedAttributes = updatedAsset.getAttributesList();

                List<Attribute> addedOrModifiedAttributes = getAddedOrModifiedAttributes(
                    existingAttributes, updatedAttributes, options.getAttributeNamesToEvaluate(), options.getIgnoredAttributeNames(), options.getIgnoredAttributeKeys()
                ).collect(Collectors.toList());

                List<Attribute> removedAttributes = getAddedOrModifiedAttributes(
                    updatedAttributes, existingAttributes, options.getAttributeNamesToEvaluate(), options.getIgnoredAttributeNames(), options.getIgnoredAttributeKeys()
                ).collect(Collectors.toList());

                if (addedOrModifiedAttributes.isEmpty() && removedAttributes.isEmpty()) {
                    LOG.finest("Skipping merge, protocol-provided asset unchanged (excluding ignored attribute names/keys): " + asset);
                    return existingAsset;
                }
            }
        }

        LOG.fine("Merging (and overriding existing older version of) with protocol-provided: " + asset);
        if (options != null && options.getAssignToUserName() != null) {
            return assetStorageService.merge(updatedAsset, true, false, options.getAssignToUserName());
        } else {
            return assetStorageService.merge(updatedAsset, true);
        }
    }

    @Override
    public boolean deleteAsset(String assetId) {
        LOG.fine("Deleting protocol-provided: " + assetId);
        return assetStorageService.delete(Collections.singletonList(assetId), false);
    }

    @Override
    public Asset findAsset(String assetId) {
        LOG.fine("Getting protocol-provided: " + assetId);
        return assetStorageService.find(assetId);
    }

    @Override
    public List<Asset> findAssets(String assetId, AssetQuery assetQuery) {
        if (TextUtil.isNullOrEmpty(assetId) || assetQuery == null) {
            return Collections.emptyList();
        }

        // Ensure agent ID is injected into each path predicate
        if (assetQuery.paths != null) {
            for (PathPredicate pathPredicate : assetQuery.paths) {
                int len = pathPredicate.path.length;
                pathPredicate.path = Arrays.copyOf(pathPredicate.path, len+1);
                pathPredicate.path[len] = assetId;
            }
        } else {
            assetQuery.paths(new PathPredicate(assetId));
        }

        return assetStorageService.findAll(assetQuery);
    }

    @Override
    public void sendAttributeEvent(AttributeEvent attributeEvent) {
        assetProcessingService.sendAttributeEvent(attributeEvent);
    }

    @Override
    public Asset getAgent(Attribute protocolConfiguration) {
        return getAgents().getOrDefault(protocolConfiguration.getReferenceOrThrow().getEntityId(), null);
    }

    /**
     * Looks for new, modified and obsolete protocol configurations and links / unlinks any associated attributes
     */
    protected void processAgentChange(Asset agent, PersistenceEvent<?> persistenceEvent) {

        LOG.finest("Processing agent persistence event: " + persistenceEvent.getCause());

        switch (persistenceEvent.getCause()) {
            case CREATE:
                if (!addReplaceAgent(agent)) {
                    LOG.finest("Agent is a gateway asset so ignoring");
                    return;
                }
                linkProtocolConfigurations(
                    agent,
                    agent.getAttributesStream()
                        .filter(ProtocolConfiguration::isProtocolConfiguration)
                        .collect(Collectors.toList())
                );
                break;
            case UPDATE:
                if (!addReplaceAgent(agent)) {
                    LOG.finest("Agent is a gateway asset so ignoring");
                    return;
                }
                // Check if any protocol config attributes have been added/removed or modified
                int attributesIndex = Arrays.asList(persistenceEvent.getPropertyNames()).indexOf("attributes");
                if (attributesIndex < 0) {
                    return;
                }

                // Attributes have possibly changed so need to compare old and new state to determine
                // which protocol configs are affected
                List<Attribute> oldProtocolConfigurations =
                    attributesFromJson(
                        (ObjectValue) persistenceEvent.getPreviousState()[attributesIndex],
                        agent.getId()
                    )
                        .filter(ProtocolConfiguration::isProtocolConfiguration)
                        .collect(Collectors.toList());

                List<Attribute> newProtocolConfigurations =
                    attributesFromJson(
                        (ObjectValue) persistenceEvent.getCurrentState()[attributesIndex],
                        agent.getId()
                    )
                        .filter(ProtocolConfiguration::isProtocolConfiguration)
                        .collect(Collectors.toList());

                // Compare protocol configurations by JSON value
                // Unlink protocols that are in oldConfigs but not in newConfigs
                unlinkProtocolConfigurations(agent, oldProtocolConfigurations
                    .stream()
                    .filter(oldProtocolAttribute -> newProtocolConfigurations
                        .stream()
                        .noneMatch(oldProtocolAttribute::equals)
                    )
                    .collect(Collectors.toList())
                );

                // Link protocols that are in newConfigs but not in oldConfigs
                linkProtocolConfigurations(agent, newProtocolConfigurations
                    .stream()
                    .filter(newProtocolAttribute -> oldProtocolConfigurations
                        .stream()
                        .noneMatch(newProtocolAttribute::equals)
                    )
                    .collect(Collectors.toList())
                );

                break;
            case DELETE:
                if (!removeAgent(agent)) {
                    LOG.finest("Agent is a gateway asset so ignoring");
                    return;
                }
                // Unlink any attributes that have an agent link to this agent
                unlinkProtocolConfigurations(agent, agent.getAttributesStream()
                    .filter(ProtocolConfiguration::isProtocolConfiguration)
                    .collect(Collectors.toList())
                );
                break;
        }
    }

    /**
     * Looks for new, modified and obsolete AGENT_LINK attributes and links / unlinks them
     * with the protocol
     */
    protected void processAssetChange(Asset asset, PersistenceEvent<Asset> persistenceEvent) {
        LOG.finest("Processing asset persistence event: " + persistenceEvent.getCause());

        switch (persistenceEvent.getCause()) {
            case CREATE:

                // Check if asset parent is a gateway or a gateway descendant, if so ignore it
                // Need to look at parent as this asset may not have been acknowledged by the gateway service yet
                if (gatewayService.getLocallyRegisteredGatewayId(asset.getId(), asset.getParentId()) != null) {
                    LOG.finest("This is a gateway descendant asset so ignoring: " + asset.getId());
                    return;
                }

                // Asset insert persistence events can be fired before the agent insert persistence event
                // so need to check that all protocol configs exist - any that don't we will exclude here
                // and handle in agent insert

                // If an agent insert just occurred then we will end up trying to link the attribute again
                // so we keep track of linked attributes to avoid this

                // Link any AGENT_LINK attributes to their referenced agent asset
                Map<Agent, List<Attribute>> groupedAgentLinksAttributes =
                    getGroupedAgentLinkAttributes(
                        asset.getAttributesStream(),
                        attribute -> true,
                        attribute -> LOG.warning("Linked protocol configuration not found: " + attribute)
                    );
                groupedAgentLinksAttributes.forEach((agent, attributes) -> this.linkAttributes(agent, asset, attributes));

                break;
            case UPDATE:

                if (gatewayService.getLocallyRegisteredGatewayId(asset.getId(), null) != null) {
                    LOG.finest("This is a gateway descendant asset so ignoring: " + asset.getId());
                    return;
                }

                List<String> propertyNames = Arrays.asList(persistenceEvent.getPropertyNames());

                // Check if attributes of the asset have been modified
                int attributesIndex = propertyNames.indexOf("attributes");
                if (attributesIndex < 0) {
                    return;
                }


                // Attributes have possibly changed so need to compare old and new state to determine any changes to
                // AGENT_LINK attributes
                List<Attribute> oldAgentLinkedAttributes =
                    attributesFromJson(
                        (ObjectValue) persistenceEvent.getPreviousState()[attributesIndex],
                        asset.getId()
                    )
                        .filter(assetAttribute ->
                            // Exclude attributes without agent link or with agent link to not recognised agents (could be gateway agents)
                            assetAttribute.getMetaItem(MetaItemType.AGENT_LINK)
                                .flatMap(agentLinkMetaItem -> AttributeRef.fromValue(agentLinkMetaItem.getValue().orElse(null)))
                                .map(agentLinkRef -> getAgents().containsKey(agentLinkRef.getEntityId()))
                                .orElse(false))
                        .collect(Collectors.toList());

                List<Attribute> newAgentLinkedAttributes =
                    attributesFromJson(
                        (ObjectValue) persistenceEvent.getCurrentState()[attributesIndex],
                        asset.getId())
                        .filter(assetAttribute ->
                            // Exclude attributes without agent link or with agent link to not recognised agents (could be gateway agents)
                            assetAttribute.getMetaItem(MetaItemType.AGENT_LINK)
                                .flatMap(agentLinkMetaItem -> AttributeRef.fromValue(agentLinkMetaItem.getValue().orElse(null)))
                                .map(agentLinkRef -> getAgents().containsKey(agentLinkRef.getEntityId()))
                                .orElse(false))
                        .collect(Collectors.toList());

                // Unlink thing attributes that are in old but not in new
                getGroupedAgentLinkAttributes(
                    getAddedOrModifiedAttributes(newAgentLinkedAttributes, oldAgentLinkedAttributes, key -> key.equals(VALUE_TIMESTAMP_FIELD_NAME)),
                    attribute -> true
                ).forEach(this::unlinkAttributes);

                // Link thing attributes that are in new but not in old
                getGroupedAgentLinkAttributes(
                    getAddedOrModifiedAttributes(oldAgentLinkedAttributes, newAgentLinkedAttributes, key -> key.equals(VALUE_TIMESTAMP_FIELD_NAME)),
                    attribute -> true,
                    attribute -> LOG.warning("Linked protocol configuration not found: " + attribute)
                ).forEach(this::linkAttributes);

                break;
            case DELETE: {

                if (gatewayService.getLocallyRegisteredGatewayId(asset.getId(), null) != null) {
                    LOG.finest("This is a gateway descendant asset so ignoring: " + asset.getId());
                    return;
                }

                // Unlink any AGENT_LINK attributes from the referenced protocol
                Map<Attribute, List<Attribute>> groupedAgentLinkAndProtocolAttributes =
                    getGroupedAgentLinkAttributes(asset.getAttributesStream(), attribute -> true);
                groupedAgentLinkAndProtocolAttributes
                    .forEach(
                        this::unlinkAttributes
                    );
                break;
            }
        }

        String parentAgentId = getAgentAncestorId(asset);
        if (parentAgentId != null) {
            notifyChildAssetChange(parentAgentId, persistenceEvent);
        }
    }

    protected String getAgentAncestorId(Asset asset) {
        if (asset.getPath() == null) {
            // Fully load
            Asset fullyLoaded = assetStorageService.find(asset.getId());
            if (fullyLoaded != null) {
                asset = fullyLoaded;
            } else if (!TextUtil.isNullOrEmpty(asset.getParentId())) {
                fullyLoaded = assetStorageService.find(asset.getParentId());
                List<String> path = new ArrayList<>(Arrays.asList(fullyLoaded.getPath()));
                path.add(0, asset.getId());
                asset.setPath(path.toArray(new String[0]));
            }
        }

        if (asset.getPath() == null) {
            return null;
        }

        return Arrays.stream(asset.getPath())
                .filter(assetId -> getAgents().containsKey(assetId))
                .findFirst()
                .orElse(null);
    }

    protected void linkProtocolConfigurations(Asset agent, List<Attribute> configurations) {
        withLock(getClass().getSimpleName() + "::linkProtocolConfigurations", () -> configurations.forEach(configuration -> {
            AttributeRef protocolAttributeRef = configuration.getReferenceOrThrow();
            Protocol protocol = getProtocol(configuration);

            if (protocol == null) {
                LOG.warning("Cannot find protocol that attribute is linked to: " + protocolAttributeRef);
                return;
            }

            // Store the info
            protocolConfigurations.put(protocolAttributeRef, new Pair<>(configuration, null));

            // Create a consumer callback for protocol status updates
            Consumer<ConnectionStatus> deploymentStatusConsumer = status ->
                publishProtocolConnectionStatus(protocolAttributeRef, status);

            // Set status to WAITING (we don't know what the protocol's status will be after linking configuration)
            publishProtocolConnectionStatus(protocolAttributeRef, WAITING);

            // Link the protocol configuration to the protocol
            try {
                protocol.connect();
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Protocol threw an exception during protocol configuration linking", e);
                // Set status to error
                publishProtocolConnectionStatus(protocolAttributeRef, ERROR_CONFIGURATION);
            }

            // Check protocol status and only continue linking attributes if not in error state
            ConnectionStatus connectionStatus = getProtocolConnectionStatus(protocolAttributeRef);
            if (connectionStatus == ERROR_CONFIGURATION || connectionStatus == ERROR) {
                LOG.warning("Protocol connection status is showing error so not linking attributes: " + configuration);
                return;
            }

            // Get all assets that have attributes that use this protocol configuration
            List<Asset> assets = assetStorageService.findAll(
                new AssetQuery()
                    .attributeMeta(
                        new RefPredicate(
                            MetaItemType.AGENT_LINK,
                            protocolAttributeRef.getEntityId(),
                            protocolAttributeRef.getAttributeName()
                        )
                    )
            );

            assets.forEach(
                asset ->
                    getGroupedAgentLinkAttributes(
                        asset.getAttributesStream(),
                        assetAttribute -> getAgentLink(assetAttribute)
                            .map(attributeRef -> attributeRef.equals(protocolAttributeRef))
                            .orElse(false),
                        attribute -> LOG.warning("Linked protocol configuration not found: " + attribute)
                    ).forEach(this::linkAttributes)
            );
        }));
    }

    protected void unlinkProtocolConfigurations(Asset agent, List<Attribute> configurations) {
        withLock(getClass().getSimpleName() + "::unlinkProtocolConfigurations", () -> configurations.forEach(configuration -> {
            AttributeRef protocolAttributeRef = configuration.getReferenceOrThrow();

            // Unlink all linked attributes for this protocol configuration
            List<Attribute> protocolLinkedAttributes = linkedAttributes.remove(protocolAttributeRef);

            if (protocolLinkedAttributes != null && !protocolLinkedAttributes.isEmpty()) {
                unlinkAttributes(configuration, protocolLinkedAttributes);
            }

            Protocol protocol = getProtocol(configuration);

            // Unlink the protocol configuration from the protocol
            try {
                protocol.disconnect();
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Protocol threw an exception during protocol configuration unlinking", e);
            }

            protocolConfigurations.remove(protocolAttributeRef);

            // Check if there are any remaining configs for the agent
            String agentId = configurations.get(0).getReferenceOrThrow().getEntityId();
            if (protocolConfigurations.keySet().stream().noneMatch(protocolConfigRef -> protocolConfigRef.getEntityId().equals(agentId))) {
                childAssetSubscriptions.remove(agentId);
            }
        }));
    }

    protected void publishProtocolConnectionStatus(AttributeRef protocolRef, ConnectionStatus connectionStatus) {
        withLock(getClass().getSimpleName() + "::publishProtocolConnectionStatus", () -> {
            Pair<Attribute, ConnectionStatus> protocolDeploymentInfo = protocolConfigurations.get(protocolRef);
            if (protocolDeploymentInfo != null && protocolDeploymentInfo.value != connectionStatus) {
                LOG.info("Agent protocol status updated to " + connectionStatus + ": " + protocolRef);
                protocolDeploymentInfo.value = connectionStatus;

                if (!agentMap.containsKey(protocolRef.getEntityId())) {
                    return;
                }

                // Notify clients
                clientEventService.publishEvent(
                    new AgentStatusEvent(
                        timerService.getCurrentTimeMillis(),
                        agentMap.get(protocolRef.getEntityId()).getRealm(),
                        protocolRef,
                        connectionStatus
                    )
                );
            }
        });
    }

    public ConnectionStatus getProtocolConnectionStatus(AttributeRef protocolRef) {
        return withLockReturning(getClass().getSimpleName() + "::getProtocolConnectionStatus", () ->
            Optional.ofNullable(protocolConfigurations.get(protocolRef))
                .map(pair -> pair.value)
                .orElse(null));
    }

    protected Protocol getProtocol(Attribute protocolConfiguration) {
        return protocols.get(protocolConfiguration.getValueAsString().orElse(null));
    }

    protected void linkAttributes(Agent agent, Asset asset, Collection<Attribute> attributes) {
        withLock(getClass().getSimpleName() + "::linkAttributes", () -> {
            LOG.fine("Linking all attributes that use protocol attribute: " + protocolConfiguration);
            Protocol protocol = getProtocol(protocolConfiguration);

            if (protocol == null) {
                LOG.severe("Cannot link protocol attributes as protocol is null: " + protocolConfiguration);
                return;
            }

            this.linkedAttributes.compute(
                protocolConfiguration.getReferenceOrThrow(),
                (protocolRef, linkedAttrs) -> {
                    if (linkedAttrs == null) {
                        linkedAttrs = new ArrayList<>(attributes.size());
                    }
                    linkedAttrs.addAll(attributes);
                    return linkedAttrs;
                });

            try {
                LOG.finest("Linking protocol attributes to: " + protocol.getProtocolName());
                protocol.linkAttribute(, attributes);
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "Ignoring error on linking attributes to protocol: " + protocol.getProtocolName(), ex);
                // Update the status of this protocol configuration to error
                publishProtocolConnectionStatus(protocolConfiguration.getReferenceOrThrow(), ERROR);
            }
        });
    }

    protected void unlinkAttributes(Agent agent, Asset asset, Collection<Attribute> attributes) {
        withLock(getClass().getSimpleName() + "::unlinkAttributes", () -> {
            LOG.fine("Unlinking attributes that use protocol attribute: " + protocolConfiguration);
            Protocol protocol = getProtocol(protocolConfiguration);

            if (protocol == null) {
                LOG.severe("Cannot unlink protocol attributes as protocol is null: " + protocolConfiguration);
                return;
            }

            linkedAttributes.computeIfPresent(
                protocolConfiguration.getReferenceOrThrow(),
                (protocolRef, linkedAttrs) -> {
                    linkedAttrs.removeIf(attr -> attributes.stream().anyMatch(a -> a.getReferenceOrThrow().equals(attr.getReferenceOrThrow())));
                    return linkedAttrs.isEmpty() ? null : linkedAttrs;
                }
            );

            try {
                LOG.finest("Unlinking protocol attributes from: " + protocol.getProtocolName());
                protocol.unlinkAttribute(, attributes);
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "Ignoring error on unlinking attributes from protocol: " + protocol.getProtocolName(), ex);
                // Update the status of this protocol configuration to error
                publishProtocolConnectionStatus(protocolConfiguration.getReferenceOrThrow(), ERROR);
            }
        });
    }

    /**
     * If this is an update from a sensor, or if the changed attribute is not linked to an agent's protocol
     * configuration, it's ignored.
     * <p>
     * Otherwise push the update to the attributes' linked protocol to handle and prevent any further
     * processing of this event by the processing chain. The protocol should raise sensor updates as
     * required (i.e. the protocol is responsible for synchronising state with the database).
     */
    @Override
    public boolean processAssetUpdate(EntityManager entityManager,
                                      Asset asset,
                                      Attribute attribute,
                                      Source source) throws AssetProcessingException {

        if (source == SENSOR || source == GATEWAY) {
            return false;
        }

        Boolean result = withLockReturning(getClass().getSimpleName() + "::processAssetUpdate", () ->
            AgentLink.getAgentLink(attribute)
                .map(ref ->
                    getProtocolConfiguration(ref)
                        .orElseThrow(() -> new AssetProcessingException(AssetProcessingException.Reason.INVALID_AGENT_LINK))
                )
                .map(protocolConfiguration -> {
                    // Its' a send to actuator - push the update to the protocol
                    AttributeEvent attributeEvent = new AttributeEvent(new AttributeState(asset.getId(), attribute));
                    LOG.fine("Sending to actuator topic: " + attributeEvent);
                    messageBrokerService.getProducerTemplate().sendBodyAndHeader(
                        ACTUATOR_TOPIC,
                        attributeEvent,
                        Protocol.ACTUATOR_TOPIC_TARGET_PROTOCOL,
                        protocolConfiguration.getValueAsString().orElse("")
                    );
                    return true; // Processing complete, skip other processors
                })
                .orElse(false) // This is a regular attribute so allow the processing to continue
        );
        return result != null ? result : false;
    }

    /**
     * Gets all agent link attributes and their linked protocol configuration and groups them by Protocol Configuration
     */
    protected Map<Attribute, List<Attribute>> getGroupedAgentLinkAttributes(Stream<Attribute> attributes,
                                                                                      Predicate<Attribute> filter) {

        return getGroupedAgentLinkAttributes(attributes, filter, null);
    }

    protected Map<Attribute, List<Attribute>> getGroupedAgentLinkAttributes(Stream<Attribute> attributes,
                                                                                      Predicate<Attribute> filter,
                                                                                      Consumer<Attribute> notFoundConsumer) {
        Map<Attribute, List<Attribute>> result = new HashMap<>();
        attributes
            .filter(assetAttribute ->
                // Exclude attributes without agent link or with agent link to not recognised agents (could be gateway agents)
                assetAttribute.getMetaItem(MetaItemType.AGENT_LINK)
                    .flatMap(agentLinkMetaItem -> AttributeRef.fromValue(agentLinkMetaItem.getValue().orElse(null)))
                    .map(agentLinkRef -> getAgents().containsKey(agentLinkRef.getEntityId()))
                    .orElse(false))
            .filter(filter)
            .map(attribute -> new Pair<>(attribute, getAgentLink(attribute)))
            .filter(pair -> pair.value.isPresent())
            .map(pair -> new Pair<>(pair.key, getProtocolConfiguration(pair.value.get())))
            .filter(pair -> {
                if (pair.value.isPresent()) {
                    return true;
                } else if (notFoundConsumer != null) {
                    notFoundConsumer.accept(pair.key);
                }
                return false;
            })
            .forEach(pair -> result.computeIfAbsent(pair.value.get(), newProtocolConfiguration -> new ArrayList<>())
                .add(pair.key)
            );
        return result;
    }

    public String toString() {
        return getClass().getSimpleName() + "{" +
            '}';
    }

    public Optional<Attribute> getProtocolConfiguration(AttributeRef protocolRef) {
        return withLockReturning(getClass().getSimpleName() + "::getProtocolConfiguration", () -> {
            Pair<Attribute, ConnectionStatus> deploymentStatusPair = protocolConfigurations.get(protocolRef);
            return deploymentStatusPair == null ? Optional.empty() : Optional.of(deploymentStatusPair.key);
        });
    }

    public Optional<AgentConnector> getAgentConnector(Asset agent) {
        if (agent == null || agent.getWellKnownType() != AGENT) {
            return Optional.empty();
        }

        return Optional.of(localAgentConnector);
    }

    protected boolean addReplaceAgent(Asset agent) {
        // Fully load agent asset
        final Asset loadedAgent = assetStorageService.find(agent.getId(), true);
        if (gatewayService.getLocallyRegisteredGatewayId(agent.getId(), agent.getParentId()) != null) {
            return false;
        }
        withLock(getClass().getSimpleName() + "::addReplaceAgent", () -> getAgents().put(loadedAgent.getId(), loadedAgent));
        return true;
    }

    @SuppressWarnings("ConstantConditions")
    protected boolean removeAgent(Asset agent) {
        return withLockReturning(getClass().getSimpleName() + "::removeAgent", () -> getAgents().remove(agent.getId()) != null);
    }

    public Map<String, Asset> getAgents() {
        return withLockReturning(getClass().getSimpleName() + "::getAgents", () -> {
            if (agentMap == null) {
                agentMap = assetStorageService.findAll(new AssetQuery()
                    .types(AssetType.AGENT))
                    .stream()
                    .filter(asset -> gatewayService.getLocallyRegisteredGatewayId(asset.getId(), null) == null)
                    .collect(Collectors.toMap(Asset::getId, agent -> agent));
            }
            return agentMap;
        });
    }

    @Override
    public void subscribeChildAssetChange(String agentId, Consumer<PersistenceEvent<Asset>> assetChangeConsumer) {
        if (protocolConfigurations.keySet().stream().noneMatch(attributeRef -> attributeRef.getEntityId().equals(agentId))) {
            LOG.info("Attempt to subscribe to child asset changes with an invalid agent ID: " +agentId);
            return;
        }

        withLock(getClass().getSimpleName() + "::subscribeChildAssetChange", () -> {
            List<Consumer<PersistenceEvent<Asset>>> consumerList = childAssetSubscriptions
                .computeIfAbsent(agentId, (id) -> new ArrayList<>());
            if (!consumerList.contains(assetChangeConsumer)) {
                consumerList.add(assetChangeConsumer);
            }
        });
    }

    @Override
    public void unsubscribeChildAssetChange(String agentId, Consumer<PersistenceEvent<Asset>> assetChangeConsumer) {
        withLock(getClass().getSimpleName() + "::unsubscribeChildAssetChange", () ->
            childAssetSubscriptions.computeIfPresent(agentId, (id, consumerList) -> {
                consumerList.remove(assetChangeConsumer);
                return consumerList.isEmpty() ? null : consumerList;
            }));
    }

    protected void notifyChildAssetChange(String agentId, PersistenceEvent<Asset> assetPersistenceEvent) {
        withLock(getClass().getSimpleName() + "::notifyChildAssetChange", () ->
            childAssetSubscriptions.computeIfPresent(agentId, (id, consumerList) -> {
                LOG.fine("Notifying child asset change consumers of change to agent child asset: Agent ID=" + id + ", Asset ID=" + assetPersistenceEvent.getEntity().getId());
                try {
                    consumerList.forEach(consumer -> consumer.accept(assetPersistenceEvent));
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Child asset change consumer threw an exception: Agent ID=" + id + ", Asset ID=" + assetPersistenceEvent.getEntity().getId(), e);
                }
                return consumerList;
            }));
    }
}
