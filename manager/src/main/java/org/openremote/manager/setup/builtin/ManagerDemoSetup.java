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
package org.openremote.manager.setup.builtin;

import org.openremote.agent.protocol.http.HttpClientProtocol;
import org.openremote.agent.protocol.simulator.SimulatorProtocol;
import org.openremote.container.Container;
import org.openremote.container.util.UniqueIdentifierGenerator;
import org.openremote.manager.setup.AbstractManagerSetup;
import org.openremote.model.apps.ConsoleAppConfig;
import org.openremote.model.asset.Asset;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.asset.AssetType;
import org.openremote.model.asset.ElectricityProducerOrientationType;
import org.openremote.model.attribute.AttributeLink;
import org.openremote.model.attribute.AttributeRef;
import org.openremote.model.attribute.AttributeType;
import org.openremote.model.attribute.MetaItem;
import org.openremote.model.geo.GeoJSONPoint;
import org.openremote.model.security.Tenant;
import org.openremote.model.simulator.element.NumberSimulatorElement;
import org.openremote.model.simulator.element.ReplaySimulatorElement;
import org.openremote.model.value.JsonPathFilter;
import org.openremote.model.value.Value;
import org.openremote.model.value.ValueFilter;
import org.openremote.model.value.Values;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Random;

import static java.time.temporal.ChronoField.SECOND_OF_DAY;
import static org.openremote.agent.protocol.http.HttpClientProtocol.*;
import static org.openremote.model.Constants.UNITS_POWER_KILOWATT;
import static org.openremote.model.asset.AssetType.*;
import static org.openremote.model.asset.agent.ProtocolConfiguration.initProtocolConfiguration;
import static org.openremote.model.attribute.AttributeValueType.*;
import static org.openremote.model.attribute.MetaItemType.*;

public class ManagerDemoSetup extends AbstractManagerSetup {

    public static GeoJSONPoint STATIONSPLEIN_LOCATION = new GeoJSONPoint(4.470175, 51.923464);
    public String masterRealm;
    public String realmCityTenant;
    public String area1Id;
    public String smartcitySimulatorAgentId;
    public String energyManagementId;
    public String weatherHttpApiAgentId;

    private final long halfHourInMillis = Duration.ofMinutes(30).toMillis();

    public ManagerDemoSetup(Container container) {
        super(container);
    }

    private static int getRandomNumberInRange(int min, int max) {
        if (min >= max) {
            throw new IllegalArgumentException("max must be greater than min");
        }
        Random r = new Random();
        return r.nextInt((max - min) + 1) + min;
    }

    @Override
    public void onStart() throws Exception {

        KeycloakDemoSetup keycloakDemoSetup = setupService.getTaskOfType(KeycloakDemoSetup.class);
        Tenant masterTenant = keycloakDemoSetup.masterTenant;
        Tenant tenantCity = keycloakDemoSetup.tenantCity;
        masterRealm = masterTenant.getRealm();
        this.realmCityTenant = tenantCity.getRealm();

        // ################################ Demo assets for 'master' realm ###################################


        // ################################ Link demo users and assets ###################################


        // ################################ Make users restricted ###################################


        // ################################ Realm smartcity ###################################

        Asset smartcitySimulatorAgent = new Asset("Simulator Agent", AssetType.AGENT);
        smartcitySimulatorAgent.setRealm(this.realmCityTenant);
        smartcitySimulatorAgent
                .addAttributes(
                        initProtocolConfiguration(new Attribute("inputSimulator"), SimulatorProtocol.PROTOCOL_NAME)
                                .addMeta(
                                        new MetaItem(
                                                SimulatorProtocol.CONFIG_MODE,
                                                Values.create(SimulatorProtocol.Mode.WRITE_THROUGH_IMMEDIATE.toString())
                                        ))
                )
                .addAttributes(
                        initProtocolConfiguration(new Attribute("replaySimulator"), SimulatorProtocol.PROTOCOL_NAME)
                                .addMeta(
                                        new MetaItem(
                                                SimulatorProtocol.CONFIG_MODE,
                                                Values.create(SimulatorProtocol.Mode.REPLAY.toString())
                                        )
                                )
                );
        smartcitySimulatorAgent = assetStorageService.merge(smartcitySimulatorAgent);
        smartcitySimulatorAgentId = smartcitySimulatorAgent.getId();

        LocalTime midnight = LocalTime.of(0, 0);

        // ################################ Realm smartcity - Energy Management ###################################

        Asset energyManagement = new Asset();
        energyManagement.setRealm(this.realmCityTenant);
        energyManagement.setName("Energy Management");
        energyManagement.setType(THING);
        energyManagement.addAttributes(
                new Attribute("totalPowerProducers", POWER).addMeta(
                        LABEL.withInitialValue("Combined power of all producers"),
                        UNIT_TYPE.withInitialValue(UNITS_POWER_KILOWATT),
                        STORE_DATA_POINTS,
                        READ_ONLY,
                        RULE_STATE),
                new Attribute("totalPowerConsumers", POWER).addMeta(
                        LABEL.withInitialValue("Combined power use of all consumers"),
                        UNIT_TYPE.withInitialValue(UNITS_POWER_KILOWATT),
                        STORE_DATA_POINTS,
                        RULE_STATE,
                        READ_ONLY)
        );
        energyManagement.setId(UniqueIdentifierGenerator.generateId(energyManagement.getName()));
        energyManagement = assetStorageService.merge(energyManagement);
        energyManagementId = energyManagement.getId();

        // ### De Rotterdam ###
        Asset building1Asset = new Asset("De Rotterdam", BUILDING, energyManagement);
        building1Asset.setAttributes(
                new Attribute(AttributeType.GEO_STREET, Values.create("Wilhelminakade 139")),
                new Attribute(AttributeType.GEO_POSTAL_CODE, Values.create("3072 AP")),
                new Attribute(AttributeType.GEO_CITY, Values.create("Rotterdam")),
                new Attribute(AttributeType.GEO_COUNTRY, Values.create("Netherlands")),
                new Attribute(AttributeType.LOCATION, new GeoJSONPoint(4.488324, 51.906577).toValue())
                        .removeMeta(SHOW_ON_DASHBOARD),
                new Attribute("powerBalance", POWER).addMeta(
                        LABEL.withInitialValue("Balance of power production and use"),
                        UNIT_TYPE.withInitialValue(UNITS_POWER_KILOWATT),
                        STORE_DATA_POINTS,
                        RULE_STATE,
                        READ_ONLY)
        );
        building1Asset.setId(UniqueIdentifierGenerator.generateId(building1Asset.getName() + "building"));
        building1Asset = assetStorageService.merge(building1Asset);

        Asset storage1Asset = createDemoElectricityStorageAsset("Battery De Rotterdam", building1Asset, new GeoJSONPoint(4.488324, 51.906577));
        storage1Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Super-B")));
        storage1Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Nomia")));
        storage1Asset.setId(UniqueIdentifierGenerator.generateId(storage1Asset.getName()));
        storage1Asset = assetStorageService.merge(storage1Asset);

        Asset consumption1Asset = createDemoElectricityConsumerAsset("Consumption De Rotterdam", building1Asset, new GeoJSONPoint(4.487519, 51.906544));
        consumption1Asset.getAttribute("totalPower").ifPresent(assetAttribute -> {
            assetAttribute.addMeta(STORE_DATA_POINTS);
            assetAttribute.addMeta(
                    new MetaItem(
                            AGENT_LINK,
                            new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()
                    ),
                    new MetaItem(
                            SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(ReplaySimulatorElement.ELEMENT_NAME)
                    ),
                    new MetaItem(
                            SimulatorProtocol.REPLAY_ATTRIBUTE_LINK_DATA,
                            Values.createArray().addAll(
                                    Values.createObject().put("timestamp", midnight.get(SECOND_OF_DAY)).put("value", 23),
                                    Values.createObject().put("timestamp", midnight.plusHours(1).get(SECOND_OF_DAY)).put("value", 21),
                                    Values.createObject().put("timestamp", midnight.plusHours(2).get(SECOND_OF_DAY)).put("value", 20),
                                    Values.createObject().put("timestamp", midnight.plusHours(3).get(SECOND_OF_DAY)).put("value", 22),
                                    Values.createObject().put("timestamp", midnight.plusHours(4).get(SECOND_OF_DAY)).put("value", 21),
                                    Values.createObject().put("timestamp", midnight.plusHours(5).get(SECOND_OF_DAY)).put("value", 22),
                                    Values.createObject().put("timestamp", midnight.plusHours(6).get(SECOND_OF_DAY)).put("value", 41),
                                    Values.createObject().put("timestamp", midnight.plusHours(7).get(SECOND_OF_DAY)).put("value", 54),
                                    Values.createObject().put("timestamp", midnight.plusHours(8).get(SECOND_OF_DAY)).put("value", 63),
                                    Values.createObject().put("timestamp", midnight.plusHours(9).get(SECOND_OF_DAY)).put("value", 76),
                                    Values.createObject().put("timestamp", midnight.plusHours(10).get(SECOND_OF_DAY)).put("value", 80),
                                    Values.createObject().put("timestamp", midnight.plusHours(11).get(SECOND_OF_DAY)).put("value", 79),
                                    Values.createObject().put("timestamp", midnight.plusHours(12).get(SECOND_OF_DAY)).put("value", 84),
                                    Values.createObject().put("timestamp", midnight.plusHours(13).get(SECOND_OF_DAY)).put("value", 76),
                                    Values.createObject().put("timestamp", midnight.plusHours(14).get(SECOND_OF_DAY)).put("value", 82),
                                    Values.createObject().put("timestamp", midnight.plusHours(15).get(SECOND_OF_DAY)).put("value", 83),
                                    Values.createObject().put("timestamp", midnight.plusHours(16).get(SECOND_OF_DAY)).put("value", 77),
                                    Values.createObject().put("timestamp", midnight.plusHours(17).get(SECOND_OF_DAY)).put("value", 71),
                                    Values.createObject().put("timestamp", midnight.plusHours(18).get(SECOND_OF_DAY)).put("value", 63),
                                    Values.createObject().put("timestamp", midnight.plusHours(19).get(SECOND_OF_DAY)).put("value", 41),
                                    Values.createObject().put("timestamp", midnight.plusHours(20).get(SECOND_OF_DAY)).put("value", 27),
                                    Values.createObject().put("timestamp", midnight.plusHours(21).get(SECOND_OF_DAY)).put("value", 22),
                                    Values.createObject().put("timestamp", midnight.plusHours(22).get(SECOND_OF_DAY)).put("value", 24),
                                    Values.createObject().put("timestamp", midnight.plusHours(23).get(SECOND_OF_DAY)).put("value", 20)
                            )
                    )
            );
        });
        consumption1Asset.setId(UniqueIdentifierGenerator.generateId(consumption1Asset.getName()));
        consumption1Asset = assetStorageService.merge(consumption1Asset);

        Asset production1Asset = createDemoElectricityProducerAsset("Solar De Rotterdam", building1Asset, new GeoJSONPoint(4.488592, 51.907047));
        production1Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("AEG")));
        production1Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("AS-P60")));
        production1Asset.getAttribute("totalPower").ifPresent(assetAttribute -> {
            assetAttribute.addMeta(STORE_DATA_POINTS);
            assetAttribute.addMeta(
                    new MetaItem(
                            AGENT_LINK,
                            new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()
                    ),
                    new MetaItem(
                            SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(ReplaySimulatorElement.ELEMENT_NAME)
                    ),
                    new MetaItem(
                            SimulatorProtocol.REPLAY_ATTRIBUTE_LINK_DATA,
                            Values.createArray().addAll(
                                    Values.createObject().put("timestamp", midnight.get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(1).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(2).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(3).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(4).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(5).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(6).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(7).get(SECOND_OF_DAY)).put("value", 1),
                                    Values.createObject().put("timestamp", midnight.plusHours(8).get(SECOND_OF_DAY)).put("value", 10),
                                    Values.createObject().put("timestamp", midnight.plusHours(9).get(SECOND_OF_DAY)).put("value", 15),
                                    Values.createObject().put("timestamp", midnight.plusHours(10).get(SECOND_OF_DAY)).put("value", 39),
                                    Values.createObject().put("timestamp", midnight.plusHours(11).get(SECOND_OF_DAY)).put("value", 52),
                                    Values.createObject().put("timestamp", midnight.plusHours(12).get(SECOND_OF_DAY)).put("value", 50),
                                    Values.createObject().put("timestamp", midnight.plusHours(13).get(SECOND_OF_DAY)).put("value", 48),
                                    Values.createObject().put("timestamp", midnight.plusHours(14).get(SECOND_OF_DAY)).put("value", 36),
                                    Values.createObject().put("timestamp", midnight.plusHours(15).get(SECOND_OF_DAY)).put("value", 23),
                                    Values.createObject().put("timestamp", midnight.plusHours(16).get(SECOND_OF_DAY)).put("value", 24),
                                    Values.createObject().put("timestamp", midnight.plusHours(17).get(SECOND_OF_DAY)).put("value", 18),
                                    Values.createObject().put("timestamp", midnight.plusHours(18).get(SECOND_OF_DAY)).put("value", 10),
                                    Values.createObject().put("timestamp", midnight.plusHours(19).get(SECOND_OF_DAY)).put("value", 8),
                                    Values.createObject().put("timestamp", midnight.plusHours(20).get(SECOND_OF_DAY)).put("value", 3),
                                    Values.createObject().put("timestamp", midnight.plusHours(21).get(SECOND_OF_DAY)).put("value", 1),
                                    Values.createObject().put("timestamp", midnight.plusHours(22).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(23).get(SECOND_OF_DAY)).put("value", 0)
                            )
                    )
            );
        });
        production1Asset.getAttribute("totalEnergy").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(152689)));
        production1Asset.getAttribute("installedCapacity").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(89.6)));
        production1Asset.getAttribute("systemEfficiency").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(93)));
        production1Asset.getAttribute("panelOrientation").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(ElectricityProducerOrientationType.EAST_WEST.name())));
        production1Asset.setId(UniqueIdentifierGenerator.generateId(production1Asset.getName()));
        production1Asset = assetStorageService.merge(production1Asset);

        // ### Stadhuis ###

        Asset building2Asset = new Asset("Stadhuis", BUILDING, energyManagement);
        building2Asset.setAttributes(
                new Attribute(AttributeType.GEO_STREET, Values.create("Coolsingel 40")),
                new Attribute(AttributeType.GEO_POSTAL_CODE, Values.create("3011 AD")),
                new Attribute(AttributeType.GEO_CITY, Values.create("Rotterdam")),
                new Attribute(AttributeType.GEO_COUNTRY, Values.create("Netherlands")),
                new Attribute(AttributeType.LOCATION, new GeoJSONPoint(4.47985, 51.92274).toValue())
                        .removeMeta(SHOW_ON_DASHBOARD)
        );
        building2Asset.setId(UniqueIdentifierGenerator.generateId(building2Asset.getName() + "building"));
        building2Asset = assetStorageService.merge(building2Asset);

        Asset storage2Asset = createDemoElectricityStorageAsset("Battery Stadhuis", building2Asset, new GeoJSONPoint(4.47985, 51.92274));
        storage2Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("LG Chem")));
        storage2Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("ESS Industrial")));
        storage2Asset.setId(UniqueIdentifierGenerator.generateId(storage2Asset.getName()));
        storage2Asset = assetStorageService.merge(storage2Asset);

        Asset consumption2Asset = createDemoElectricityConsumerAsset("Consumption Stadhuis", building2Asset, new GeoJSONPoint(4.47933, 51.92259));
        consumption2Asset.getAttribute("totalPower").ifPresent(assetAttribute -> {
            assetAttribute.addMeta(STORE_DATA_POINTS);
            assetAttribute.addMeta(
                    new MetaItem(
                            AGENT_LINK,
                            new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()
                    ),
                    new MetaItem(
                            SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(ReplaySimulatorElement.ELEMENT_NAME)
                    ),
                    new MetaItem(
                            SimulatorProtocol.REPLAY_ATTRIBUTE_LINK_DATA,
                            Values.createArray().addAll(
                                    Values.createObject().put("timestamp", midnight.get(SECOND_OF_DAY)).put("value", 7),
                                    Values.createObject().put("timestamp", midnight.plusHours(1).get(SECOND_OF_DAY)).put("value", 8),
                                    Values.createObject().put("timestamp", midnight.plusHours(2).get(SECOND_OF_DAY)).put("value", 7),
                                    Values.createObject().put("timestamp", midnight.plusHours(3).get(SECOND_OF_DAY)).put("value", 9),
                                    Values.createObject().put("timestamp", midnight.plusHours(4).get(SECOND_OF_DAY)).put("value", 8),
                                    Values.createObject().put("timestamp", midnight.plusHours(5).get(SECOND_OF_DAY)).put("value", 9),
                                    Values.createObject().put("timestamp", midnight.plusHours(6).get(SECOND_OF_DAY)).put("value", 12),
                                    Values.createObject().put("timestamp", midnight.plusHours(7).get(SECOND_OF_DAY)).put("value", 22),
                                    Values.createObject().put("timestamp", midnight.plusHours(8).get(SECOND_OF_DAY)).put("value", 30),
                                    Values.createObject().put("timestamp", midnight.plusHours(9).get(SECOND_OF_DAY)).put("value", 36),
                                    Values.createObject().put("timestamp", midnight.plusHours(10).get(SECOND_OF_DAY)).put("value", 39),
                                    Values.createObject().put("timestamp", midnight.plusHours(11).get(SECOND_OF_DAY)).put("value", 32),
                                    Values.createObject().put("timestamp", midnight.plusHours(12).get(SECOND_OF_DAY)).put("value", 36),
                                    Values.createObject().put("timestamp", midnight.plusHours(13).get(SECOND_OF_DAY)).put("value", 44),
                                    Values.createObject().put("timestamp", midnight.plusHours(14).get(SECOND_OF_DAY)).put("value", 47),
                                    Values.createObject().put("timestamp", midnight.plusHours(15).get(SECOND_OF_DAY)).put("value", 44),
                                    Values.createObject().put("timestamp", midnight.plusHours(16).get(SECOND_OF_DAY)).put("value", 38),
                                    Values.createObject().put("timestamp", midnight.plusHours(17).get(SECOND_OF_DAY)).put("value", 38),
                                    Values.createObject().put("timestamp", midnight.plusHours(18).get(SECOND_OF_DAY)).put("value", 34),
                                    Values.createObject().put("timestamp", midnight.plusHours(19).get(SECOND_OF_DAY)).put("value", 33),
                                    Values.createObject().put("timestamp", midnight.plusHours(20).get(SECOND_OF_DAY)).put("value", 23),
                                    Values.createObject().put("timestamp", midnight.plusHours(21).get(SECOND_OF_DAY)).put("value", 13),
                                    Values.createObject().put("timestamp", midnight.plusHours(22).get(SECOND_OF_DAY)).put("value", 9),
                                    Values.createObject().put("timestamp", midnight.plusHours(23).get(SECOND_OF_DAY)).put("value", 8)
                            )
                    )
            );
        });
        consumption2Asset.setId(UniqueIdentifierGenerator.generateId(consumption2Asset.getName()));
        consumption2Asset = assetStorageService.merge(consumption2Asset);

        Asset production2Asset = createDemoElectricityProducerAsset("Solar Stadhuis", building2Asset, new GeoJSONPoint(4.47945, 51.92301));
        production2Asset.getAttribute("totalPower").ifPresent(assetAttribute -> {
            assetAttribute.addMeta(STORE_DATA_POINTS);
            assetAttribute.addMeta(
                    new MetaItem(
                            AGENT_LINK,
                            new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()
                    ),
                    new MetaItem(
                            SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(ReplaySimulatorElement.ELEMENT_NAME)
                    ),
                    new MetaItem(
                            SimulatorProtocol.REPLAY_ATTRIBUTE_LINK_DATA,
                            Values.createArray().addAll(
                                    Values.createObject().put("timestamp", midnight.get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(1).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(2).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(3).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(4).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(5).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(6).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(7).get(SECOND_OF_DAY)).put("value", 1),
                                    Values.createObject().put("timestamp", midnight.plusHours(8).get(SECOND_OF_DAY)).put("value", 2),
                                    Values.createObject().put("timestamp", midnight.plusHours(9).get(SECOND_OF_DAY)).put("value", 3),
                                    Values.createObject().put("timestamp", midnight.plusHours(10).get(SECOND_OF_DAY)).put("value", 8),
                                    Values.createObject().put("timestamp", midnight.plusHours(11).get(SECOND_OF_DAY)).put("value", 14),
                                    Values.createObject().put("timestamp", midnight.plusHours(12).get(SECOND_OF_DAY)).put("value", 12),
                                    Values.createObject().put("timestamp", midnight.plusHours(13).get(SECOND_OF_DAY)).put("value", 10),
                                    Values.createObject().put("timestamp", midnight.plusHours(14).get(SECOND_OF_DAY)).put("value", 7),
                                    Values.createObject().put("timestamp", midnight.plusHours(15).get(SECOND_OF_DAY)).put("value", 5),
                                    Values.createObject().put("timestamp", midnight.plusHours(16).get(SECOND_OF_DAY)).put("value", 7),
                                    Values.createObject().put("timestamp", midnight.plusHours(17).get(SECOND_OF_DAY)).put("value", 5),
                                    Values.createObject().put("timestamp", midnight.plusHours(18).get(SECOND_OF_DAY)).put("value", 3),
                                    Values.createObject().put("timestamp", midnight.plusHours(19).get(SECOND_OF_DAY)).put("value", 2),
                                    Values.createObject().put("timestamp", midnight.plusHours(20).get(SECOND_OF_DAY)).put("value", 1),
                                    Values.createObject().put("timestamp", midnight.plusHours(21).get(SECOND_OF_DAY)).put("value", 1),
                                    Values.createObject().put("timestamp", midnight.plusHours(22).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(23).get(SECOND_OF_DAY)).put("value", 0)
                            )
                    )
            );
        });
        production2Asset.getAttribute("totalEnergy").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(88961)));
        production2Asset.getAttribute("installedCapacity").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(19.2)));
        production2Asset.getAttribute("systemEfficiency").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(79)));
        production2Asset.getAttribute("panelOrientation").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(ElectricityProducerOrientationType.SOUTH.name())));
        production2Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Solarwatt")));
        production2Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("EasyIn 60M")));
        production2Asset.setId(UniqueIdentifierGenerator.generateId(production2Asset.getName()));
        production2Asset = assetStorageService.merge(production2Asset);

        // ### Markthal ###

        Asset building3Asset = new Asset("Markthal", BUILDING, energyManagement);
        building3Asset.setAttributes(
                new Attribute(AttributeType.GEO_STREET, Values.create("Dominee Jan Scharpstraat 298")),
                new Attribute(AttributeType.GEO_POSTAL_CODE, Values.create("3011 GZ")),
                new Attribute(AttributeType.GEO_CITY, Values.create("Rotterdam")),
                new Attribute(AttributeType.GEO_COUNTRY, Values.create("Netherlands")),
                new Attribute(AttributeType.LOCATION, new GeoJSONPoint(4.47945, 51.92301).toValue())
                        .removeMeta(SHOW_ON_DASHBOARD),
                new Attribute("allChargersInUse", BOOLEAN)
                        .addMeta(
                                LABEL.withInitialValue("All chargers in use"),
                                RULE_STATE,
                                READ_ONLY)
        );
        building3Asset.setId(UniqueIdentifierGenerator.generateId(building3Asset.getName() + "building"));
        building3Asset = assetStorageService.merge(building3Asset);

        Asset production3Asset = createDemoElectricityProducerAsset("Solar Markthal", building3Asset, new GeoJSONPoint(4.47945, 51.92301));
        production3Asset.getAttribute("totalPower").ifPresent(assetAttribute -> {
            assetAttribute.addMeta(STORE_DATA_POINTS);
            assetAttribute.addMeta(
                    new MetaItem(
                            AGENT_LINK,
                            new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()
                    ),
                    new MetaItem(
                            SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(ReplaySimulatorElement.ELEMENT_NAME)
                    ),
                    new MetaItem(
                            SimulatorProtocol.REPLAY_ATTRIBUTE_LINK_DATA,
                            Values.createArray().addAll(
                                    Values.createObject().put("timestamp", midnight.get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(1).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(2).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(3).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(4).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(5).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(6).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(7).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(8).get(SECOND_OF_DAY)).put("value", 2),
                                    Values.createObject().put("timestamp", midnight.plusHours(9).get(SECOND_OF_DAY)).put("value", 6),
                                    Values.createObject().put("timestamp", midnight.plusHours(10).get(SECOND_OF_DAY)).put("value", 10),
                                    Values.createObject().put("timestamp", midnight.plusHours(11).get(SECOND_OF_DAY)).put("value", 13),
                                    Values.createObject().put("timestamp", midnight.plusHours(12).get(SECOND_OF_DAY)).put("value", 21),
                                    Values.createObject().put("timestamp", midnight.plusHours(13).get(SECOND_OF_DAY)).put("value", 14),
                                    Values.createObject().put("timestamp", midnight.plusHours(14).get(SECOND_OF_DAY)).put("value", 17),
                                    Values.createObject().put("timestamp", midnight.plusHours(15).get(SECOND_OF_DAY)).put("value", 10),
                                    Values.createObject().put("timestamp", midnight.plusHours(16).get(SECOND_OF_DAY)).put("value", 9),
                                    Values.createObject().put("timestamp", midnight.plusHours(17).get(SECOND_OF_DAY)).put("value", 7),
                                    Values.createObject().put("timestamp", midnight.plusHours(18).get(SECOND_OF_DAY)).put("value", 5),
                                    Values.createObject().put("timestamp", midnight.plusHours(19).get(SECOND_OF_DAY)).put("value", 4),
                                    Values.createObject().put("timestamp", midnight.plusHours(20).get(SECOND_OF_DAY)).put("value", 2),
                                    Values.createObject().put("timestamp", midnight.plusHours(21).get(SECOND_OF_DAY)).put("value", 1),
                                    Values.createObject().put("timestamp", midnight.plusHours(22).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(23).get(SECOND_OF_DAY)).put("value", 0)
                            )
                    )
            );
        });
        production3Asset.getAttribute("totalEnergy").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(24134)));
        production3Asset.getAttribute("installedCapacity").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(29.8)));
        production3Asset.getAttribute("systemEfficiency").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(91)));
        production3Asset.getAttribute("panelOrientation").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(ElectricityProducerOrientationType.SOUTH.name())));
        production3Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Sunpower")));
        production3Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("E20-327")));
        production3Asset.setId(UniqueIdentifierGenerator.generateId(production3Asset.getName()));
        production3Asset = assetStorageService.merge(production3Asset);

        Asset charger1Asset = createDemoElectricityChargerAsset("Charger 1 Markthal", building3Asset, new GeoJSONPoint(4.486143, 51.920058));
        charger1Asset.getAttribute("power").ifPresent(assetAttribute -> {
            assetAttribute.setValue(Values.create(0));
            assetAttribute.addMeta(
                    new MetaItem(
                            AGENT_LINK,
                            new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()
                    ),
                    new MetaItem(
                            SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(ReplaySimulatorElement.ELEMENT_NAME)
                    ),
                    new MetaItem(
                            SimulatorProtocol.REPLAY_ATTRIBUTE_LINK_DATA,
                            Values.createArray().addAll(
                                    Values.createObject().put("timestamp", midnight.get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(1).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(2).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(3).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(4).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(5).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(6).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(7).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(8).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(9).get(SECOND_OF_DAY)).put("value", 2),
                                    Values.createObject().put("timestamp", midnight.plusHours(10).get(SECOND_OF_DAY)).put("value", 5),
                                    Values.createObject().put("timestamp", midnight.plusHours(11).get(SECOND_OF_DAY)).put("value", 10),
                                    Values.createObject().put("timestamp", midnight.plusHours(12).get(SECOND_OF_DAY)).put("value", 5),
                                    Values.createObject().put("timestamp", midnight.plusHours(13).get(SECOND_OF_DAY)).put("value", 3),
                                    Values.createObject().put("timestamp", midnight.plusHours(14).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(15).get(SECOND_OF_DAY)).put("value", 15),
                                    Values.createObject().put("timestamp", midnight.plusHours(16).get(SECOND_OF_DAY)).put("value", 32),
                                    Values.createObject().put("timestamp", midnight.plusHours(17).get(SECOND_OF_DAY)).put("value", 35),
                                    Values.createObject().put("timestamp", midnight.plusHours(18).get(SECOND_OF_DAY)).put("value", 17),
                                    Values.createObject().put("timestamp", midnight.plusHours(19).get(SECOND_OF_DAY)).put("value", 9),
                                    Values.createObject().put("timestamp", midnight.plusHours(20).get(SECOND_OF_DAY)).put("value", 6),
                                    Values.createObject().put("timestamp", midnight.plusHours(21).get(SECOND_OF_DAY)).put("value", 3),
                                    Values.createObject().put("timestamp", midnight.plusHours(22).get(SECOND_OF_DAY)).put("value", 3),
                                    Values.createObject().put("timestamp", midnight.plusHours(23).get(SECOND_OF_DAY)).put("value", 0)
                            )
                    )
            );
        });
        charger1Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Allego")));
        charger1Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("HPC")));
        charger1Asset.setId(UniqueIdentifierGenerator.generateId(charger1Asset.getName()));
        charger1Asset = assetStorageService.merge(charger1Asset);

        Asset charger2Asset = createDemoElectricityChargerAsset("Charger 2 Markthal", building3Asset, new GeoJSONPoint(4.486188, 51.919957));
        charger2Asset.getAttribute("power").ifPresent(assetAttribute -> {
            assetAttribute.setValue(Values.create(0));
            assetAttribute.addMeta(
                    new MetaItem(
                            AGENT_LINK,
                            new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()
                    ),
                    new MetaItem(
                            SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(ReplaySimulatorElement.ELEMENT_NAME)
                    ),
                    new MetaItem(
                            SimulatorProtocol.REPLAY_ATTRIBUTE_LINK_DATA,
                            Values.createArray().addAll(
                                    Values.createObject().put("timestamp", midnight.get(SECOND_OF_DAY)).put("value", 5),
                                    Values.createObject().put("timestamp", midnight.plusHours(1).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(2).get(SECOND_OF_DAY)).put("value", 11),
                                    Values.createObject().put("timestamp", midnight.plusHours(3).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(4).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(5).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(6).get(SECOND_OF_DAY)).put("value", 5),
                                    Values.createObject().put("timestamp", midnight.plusHours(7).get(SECOND_OF_DAY)).put("value", 10),
                                    Values.createObject().put("timestamp", midnight.plusHours(8).get(SECOND_OF_DAY)).put("value", 6),
                                    Values.createObject().put("timestamp", midnight.plusHours(9).get(SECOND_OF_DAY)).put("value", 3),
                                    Values.createObject().put("timestamp", midnight.plusHours(10).get(SECOND_OF_DAY)).put("value", 3),
                                    Values.createObject().put("timestamp", midnight.plusHours(11).get(SECOND_OF_DAY)).put("value", 17),
                                    Values.createObject().put("timestamp", midnight.plusHours(12).get(SECOND_OF_DAY)).put("value", 14),
                                    Values.createObject().put("timestamp", midnight.plusHours(13).get(SECOND_OF_DAY)).put("value", 9),
                                    Values.createObject().put("timestamp", midnight.plusHours(14).get(SECOND_OF_DAY)).put("value", 4),
                                    Values.createObject().put("timestamp", midnight.plusHours(15).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(16).get(SECOND_OF_DAY)).put("value", 28),
                                    Values.createObject().put("timestamp", midnight.plusHours(17).get(SECOND_OF_DAY)).put("value", 38),
                                    Values.createObject().put("timestamp", midnight.plusHours(18).get(SECOND_OF_DAY)).put("value", 32),
                                    Values.createObject().put("timestamp", midnight.plusHours(19).get(SECOND_OF_DAY)).put("value", 26),
                                    Values.createObject().put("timestamp", midnight.plusHours(20).get(SECOND_OF_DAY)).put("value", 13),
                                    Values.createObject().put("timestamp", midnight.plusHours(21).get(SECOND_OF_DAY)).put("value", 6),
                                    Values.createObject().put("timestamp", midnight.plusHours(22).get(SECOND_OF_DAY)).put("value", 3),
                                    Values.createObject().put("timestamp", midnight.plusHours(23).get(SECOND_OF_DAY)).put("value", 0)
                            )
                    )
            );
        });
        charger2Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Bosch")));
        charger2Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("EV800")));
        charger2Asset.setId(UniqueIdentifierGenerator.generateId(charger2Asset.getName()));
        charger2Asset = assetStorageService.merge(charger2Asset);

        Asset charger3Asset = createDemoElectricityChargerAsset("Charger 3 Markthal", building3Asset, new GeoJSONPoint(4.486232, 51.919856));
        charger1Asset.getAttribute("power").ifPresent(assetAttribute -> {
            assetAttribute.setValue(Values.create(0));
            assetAttribute.addMeta(
                    new MetaItem(
                            AGENT_LINK,
                            new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()
                    ),
                    new MetaItem(
                            SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(ReplaySimulatorElement.ELEMENT_NAME)
                    ),
                    new MetaItem(
                            SimulatorProtocol.REPLAY_ATTRIBUTE_LINK_DATA,
                            Values.createArray().addAll(
                                    Values.createObject().put("timestamp", midnight.get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(1).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(2).get(SECOND_OF_DAY)).put("value", 4),
                                    Values.createObject().put("timestamp", midnight.plusHours(3).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(4).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(5).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(6).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(7).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(8).get(SECOND_OF_DAY)).put("value", 4),
                                    Values.createObject().put("timestamp", midnight.plusHours(9).get(SECOND_OF_DAY)).put("value", 7),
                                    Values.createObject().put("timestamp", midnight.plusHours(10).get(SECOND_OF_DAY)).put("value", 9),
                                    Values.createObject().put("timestamp", midnight.plusHours(11).get(SECOND_OF_DAY)).put("value", 6),
                                    Values.createObject().put("timestamp", midnight.plusHours(12).get(SECOND_OF_DAY)).put("value", 2),
                                    Values.createObject().put("timestamp", midnight.plusHours(13).get(SECOND_OF_DAY)).put("value", 6),
                                    Values.createObject().put("timestamp", midnight.plusHours(14).get(SECOND_OF_DAY)).put("value", 18),
                                    Values.createObject().put("timestamp", midnight.plusHours(15).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(16).get(SECOND_OF_DAY)).put("value", 4),
                                    Values.createObject().put("timestamp", midnight.plusHours(17).get(SECOND_OF_DAY)).put("value", 29),
                                    Values.createObject().put("timestamp", midnight.plusHours(18).get(SECOND_OF_DAY)).put("value", 34),
                                    Values.createObject().put("timestamp", midnight.plusHours(19).get(SECOND_OF_DAY)).put("value", 22),
                                    Values.createObject().put("timestamp", midnight.plusHours(20).get(SECOND_OF_DAY)).put("value", 14),
                                    Values.createObject().put("timestamp", midnight.plusHours(21).get(SECOND_OF_DAY)).put("value", 3),
                                    Values.createObject().put("timestamp", midnight.plusHours(22).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(23).get(SECOND_OF_DAY)).put("value", 0)
                            )
                    )
            );
        });
        charger3Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Siemens")));
        charger3Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("CPC 50")));
        charger3Asset.setId(UniqueIdentifierGenerator.generateId(charger3Asset.getName()));
        charger3Asset = assetStorageService.merge(charger3Asset);

        Asset charger4Asset = createDemoElectricityChargerAsset("Charger 4 Markthal", building3Asset, new GeoJSONPoint(4.486286, 51.919733));
        charger4Asset.getAttribute("power").ifPresent(assetAttribute -> {
            assetAttribute.setValue(Values.create(0));
            assetAttribute.addMeta(
                    new MetaItem(
                            AGENT_LINK,
                            new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()
                    ),
                    new MetaItem(
                            SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(ReplaySimulatorElement.ELEMENT_NAME)
                    ),
                    new MetaItem(
                            SimulatorProtocol.REPLAY_ATTRIBUTE_LINK_DATA,
                            Values.createArray().addAll(
                                    Values.createObject().put("timestamp", midnight.get(SECOND_OF_DAY)).put("value", 3),
                                    Values.createObject().put("timestamp", midnight.plusHours(1).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(2).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(3).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(4).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(5).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(6).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(7).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(8).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(9).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(10).get(SECOND_OF_DAY)).put("value", 4),
                                    Values.createObject().put("timestamp", midnight.plusHours(11).get(SECOND_OF_DAY)).put("value", 17),
                                    Values.createObject().put("timestamp", midnight.plusHours(12).get(SECOND_OF_DAY)).put("value", 15),
                                    Values.createObject().put("timestamp", midnight.plusHours(13).get(SECOND_OF_DAY)).put("value", 8),
                                    Values.createObject().put("timestamp", midnight.plusHours(14).get(SECOND_OF_DAY)).put("value", 16),
                                    Values.createObject().put("timestamp", midnight.plusHours(15).get(SECOND_OF_DAY)).put("value", 4),
                                    Values.createObject().put("timestamp", midnight.plusHours(16).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(17).get(SECOND_OF_DAY)).put("value", 15),
                                    Values.createObject().put("timestamp", midnight.plusHours(18).get(SECOND_OF_DAY)).put("value", 34),
                                    Values.createObject().put("timestamp", midnight.plusHours(19).get(SECOND_OF_DAY)).put("value", 30),
                                    Values.createObject().put("timestamp", midnight.plusHours(20).get(SECOND_OF_DAY)).put("value", 11),
                                    Values.createObject().put("timestamp", midnight.plusHours(21).get(SECOND_OF_DAY)).put("value", 16),
                                    Values.createObject().put("timestamp", midnight.plusHours(22).get(SECOND_OF_DAY)).put("value", 7),
                                    Values.createObject().put("timestamp", midnight.plusHours(23).get(SECOND_OF_DAY)).put("value", 4)
                            )
                    )
            );
        });
        charger4Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("SemaConnect")));
        charger4Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("The Series 6")));
        charger4Asset.setId(UniqueIdentifierGenerator.generateId(charger4Asset.getName()));
        charger4Asset = assetStorageService.merge(charger4Asset);

        // ### Erasmianum ###

        Asset building4Asset = new Asset("Erasmianum", BUILDING, energyManagement);
        building4Asset.setAttributes(
                new Attribute(AttributeType.GEO_STREET, Values.create("Wytemaweg 25")),
                new Attribute(AttributeType.GEO_POSTAL_CODE, Values.create("3015 CN")),
                new Attribute(AttributeType.GEO_CITY, Values.create("Rotterdam")),
                new Attribute(AttributeType.GEO_COUNTRY, Values.create("Netherlands")),
                new Attribute(AttributeType.LOCATION, new GeoJSONPoint(4.468324, 51.912062).toValue())
                        .removeMeta(SHOW_ON_DASHBOARD)
        );
        building4Asset.setId(UniqueIdentifierGenerator.generateId(building4Asset.getName() + "building"));
        building4Asset = assetStorageService.merge(building4Asset);

        Asset consumption4Asset = createDemoElectricityConsumerAsset("Consumption Erasmianum", building4Asset, new GeoJSONPoint(4.468324, 51.912062));
        consumption4Asset.getAttribute("totalPower").ifPresent(assetAttribute -> {
            assetAttribute.addMeta(STORE_DATA_POINTS);
            assetAttribute.addMeta(
                    new MetaItem(
                            AGENT_LINK,
                            new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()
                    ),
                    new MetaItem(
                            SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(ReplaySimulatorElement.ELEMENT_NAME)
                    ),
                    new MetaItem(
                            SimulatorProtocol.REPLAY_ATTRIBUTE_LINK_DATA,
                            Values.createArray().addAll(
                                    Values.createObject().put("timestamp", midnight.get(SECOND_OF_DAY)).put("value", 6),
                                    Values.createObject().put("timestamp", midnight.plusHours(1).get(SECOND_OF_DAY)).put("value", 5),
                                    Values.createObject().put("timestamp", midnight.plusHours(2).get(SECOND_OF_DAY)).put("value", 6),
                                    Values.createObject().put("timestamp", midnight.plusHours(3).get(SECOND_OF_DAY)).put("value", 7),
                                    Values.createObject().put("timestamp", midnight.plusHours(4).get(SECOND_OF_DAY)).put("value", 5),
                                    Values.createObject().put("timestamp", midnight.plusHours(5).get(SECOND_OF_DAY)).put("value", 6),
                                    Values.createObject().put("timestamp", midnight.plusHours(6).get(SECOND_OF_DAY)).put("value", 9),
                                    Values.createObject().put("timestamp", midnight.plusHours(7).get(SECOND_OF_DAY)).put("value", 23),
                                    Values.createObject().put("timestamp", midnight.plusHours(8).get(SECOND_OF_DAY)).put("value", 37),
                                    Values.createObject().put("timestamp", midnight.plusHours(9).get(SECOND_OF_DAY)).put("value", 41),
                                    Values.createObject().put("timestamp", midnight.plusHours(10).get(SECOND_OF_DAY)).put("value", 47),
                                    Values.createObject().put("timestamp", midnight.plusHours(11).get(SECOND_OF_DAY)).put("value", 49),
                                    Values.createObject().put("timestamp", midnight.plusHours(12).get(SECOND_OF_DAY)).put("value", 51),
                                    Values.createObject().put("timestamp", midnight.plusHours(13).get(SECOND_OF_DAY)).put("value", 43),
                                    Values.createObject().put("timestamp", midnight.plusHours(14).get(SECOND_OF_DAY)).put("value", 48),
                                    Values.createObject().put("timestamp", midnight.plusHours(15).get(SECOND_OF_DAY)).put("value", 45),
                                    Values.createObject().put("timestamp", midnight.plusHours(16).get(SECOND_OF_DAY)).put("value", 46),
                                    Values.createObject().put("timestamp", midnight.plusHours(17).get(SECOND_OF_DAY)).put("value", 41),
                                    Values.createObject().put("timestamp", midnight.plusHours(18).get(SECOND_OF_DAY)).put("value", 38),
                                    Values.createObject().put("timestamp", midnight.plusHours(19).get(SECOND_OF_DAY)).put("value", 30),
                                    Values.createObject().put("timestamp", midnight.plusHours(20).get(SECOND_OF_DAY)).put("value", 19),
                                    Values.createObject().put("timestamp", midnight.plusHours(21).get(SECOND_OF_DAY)).put("value", 15),
                                    Values.createObject().put("timestamp", midnight.plusHours(22).get(SECOND_OF_DAY)).put("value", 7),
                                    Values.createObject().put("timestamp", midnight.plusHours(23).get(SECOND_OF_DAY)).put("value", 6)
                            )
                    )
            );
        });
        consumption4Asset.setId(UniqueIdentifierGenerator.generateId(consumption4Asset.getName()));
        consumption4Asset = assetStorageService.merge(consumption4Asset);

        // ### Oostelijk zwembad ###

        Asset building5Asset = new Asset("Oostelijk zwembad", BUILDING, energyManagement);
        building5Asset.setAttributes(
                new Attribute(AttributeType.GEO_STREET, Values.create("Gerdesiaweg 480")),
                new Attribute(AttributeType.GEO_POSTAL_CODE, Values.create("3061 RA")),
                new Attribute(AttributeType.GEO_CITY, Values.create("Rotterdam")),
                new Attribute(AttributeType.GEO_COUNTRY, Values.create("Netherlands")),
                new Attribute(AttributeType.LOCATION, new GeoJSONPoint(4.498048, 51.925770).toValue())
                        .removeMeta(SHOW_ON_DASHBOARD)
        );
        building5Asset.setId(UniqueIdentifierGenerator.generateId(building5Asset.getName() + "building"));
        building5Asset = assetStorageService.merge(building5Asset);

        Asset consumption5Asset = createDemoElectricityConsumerAsset("Consumption Zwembad", building5Asset, new GeoJSONPoint(4.498048, 51.925770));
        consumption5Asset.getAttribute("totalPower").ifPresent(assetAttribute -> {
            assetAttribute.addMeta(STORE_DATA_POINTS);
            assetAttribute.addMeta(
                    new MetaItem(
                            AGENT_LINK,
                            new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()
                    ),
                    new MetaItem(
                            SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(ReplaySimulatorElement.ELEMENT_NAME)
                    ),
                    new MetaItem(
                            SimulatorProtocol.REPLAY_ATTRIBUTE_LINK_DATA,
                            Values.createArray().addAll(
                                    Values.createObject().put("timestamp", midnight.get(SECOND_OF_DAY)).put("value", 16),
                                    Values.createObject().put("timestamp", midnight.plusHours(1).get(SECOND_OF_DAY)).put("value", 16),
                                    Values.createObject().put("timestamp", midnight.plusHours(2).get(SECOND_OF_DAY)).put("value", 15),
                                    Values.createObject().put("timestamp", midnight.plusHours(3).get(SECOND_OF_DAY)).put("value", 16),
                                    Values.createObject().put("timestamp", midnight.plusHours(4).get(SECOND_OF_DAY)).put("value", 17),
                                    Values.createObject().put("timestamp", midnight.plusHours(5).get(SECOND_OF_DAY)).put("value", 16),
                                    Values.createObject().put("timestamp", midnight.plusHours(6).get(SECOND_OF_DAY)).put("value", 24),
                                    Values.createObject().put("timestamp", midnight.plusHours(7).get(SECOND_OF_DAY)).put("value", 35),
                                    Values.createObject().put("timestamp", midnight.plusHours(8).get(SECOND_OF_DAY)).put("value", 32),
                                    Values.createObject().put("timestamp", midnight.plusHours(9).get(SECOND_OF_DAY)).put("value", 33),
                                    Values.createObject().put("timestamp", midnight.plusHours(10).get(SECOND_OF_DAY)).put("value", 34),
                                    Values.createObject().put("timestamp", midnight.plusHours(11).get(SECOND_OF_DAY)).put("value", 33),
                                    Values.createObject().put("timestamp", midnight.plusHours(12).get(SECOND_OF_DAY)).put("value", 34),
                                    Values.createObject().put("timestamp", midnight.plusHours(13).get(SECOND_OF_DAY)).put("value", 31),
                                    Values.createObject().put("timestamp", midnight.plusHours(14).get(SECOND_OF_DAY)).put("value", 36),
                                    Values.createObject().put("timestamp", midnight.plusHours(15).get(SECOND_OF_DAY)).put("value", 34),
                                    Values.createObject().put("timestamp", midnight.plusHours(16).get(SECOND_OF_DAY)).put("value", 32),
                                    Values.createObject().put("timestamp", midnight.plusHours(17).get(SECOND_OF_DAY)).put("value", 37),
                                    Values.createObject().put("timestamp", midnight.plusHours(18).get(SECOND_OF_DAY)).put("value", 38),
                                    Values.createObject().put("timestamp", midnight.plusHours(19).get(SECOND_OF_DAY)).put("value", 37),
                                    Values.createObject().put("timestamp", midnight.plusHours(20).get(SECOND_OF_DAY)).put("value", 38),
                                    Values.createObject().put("timestamp", midnight.plusHours(21).get(SECOND_OF_DAY)).put("value", 35),
                                    Values.createObject().put("timestamp", midnight.plusHours(22).get(SECOND_OF_DAY)).put("value", 24),
                                    Values.createObject().put("timestamp", midnight.plusHours(23).get(SECOND_OF_DAY)).put("value", 19)
                            )
                    )
            );
        });
        consumption5Asset.setId(UniqueIdentifierGenerator.generateId(consumption5Asset.getName()));
        consumption5Asset = assetStorageService.merge(consumption5Asset);

        Asset production5Asset = createDemoElectricityProducerAsset("Solar Zwembad", building5Asset, new GeoJSONPoint(4.498281, 51.925507));
        production5Asset.getAttribute("totalPower").ifPresent(assetAttribute -> {
            assetAttribute.addMeta(STORE_DATA_POINTS);
            assetAttribute.addMeta(
                    new MetaItem(
                            AGENT_LINK,
                            new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()
                    ),
                    new MetaItem(
                            SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(ReplaySimulatorElement.ELEMENT_NAME)
                    ),
                    new MetaItem(
                            SimulatorProtocol.REPLAY_ATTRIBUTE_LINK_DATA,
                            Values.createArray().addAll(
                                    Values.createObject().put("timestamp", midnight.get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(1).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(2).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(3).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(4).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(5).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(6).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(7).get(SECOND_OF_DAY)).put("value", 1),
                                    Values.createObject().put("timestamp", midnight.plusHours(8).get(SECOND_OF_DAY)).put("value", 3),
                                    Values.createObject().put("timestamp", midnight.plusHours(9).get(SECOND_OF_DAY)).put("value", 8),
                                    Values.createObject().put("timestamp", midnight.plusHours(10).get(SECOND_OF_DAY)).put("value", 30),
                                    Values.createObject().put("timestamp", midnight.plusHours(11).get(SECOND_OF_DAY)).put("value", 44),
                                    Values.createObject().put("timestamp", midnight.plusHours(12).get(SECOND_OF_DAY)).put("value", 42),
                                    Values.createObject().put("timestamp", midnight.plusHours(13).get(SECOND_OF_DAY)).put("value", 41),
                                    Values.createObject().put("timestamp", midnight.plusHours(14).get(SECOND_OF_DAY)).put("value", 29),
                                    Values.createObject().put("timestamp", midnight.plusHours(15).get(SECOND_OF_DAY)).put("value", 19),
                                    Values.createObject().put("timestamp", midnight.plusHours(16).get(SECOND_OF_DAY)).put("value", 16),
                                    Values.createObject().put("timestamp", midnight.plusHours(17).get(SECOND_OF_DAY)).put("value", 11),
                                    Values.createObject().put("timestamp", midnight.plusHours(18).get(SECOND_OF_DAY)).put("value", 4),
                                    Values.createObject().put("timestamp", midnight.plusHours(19).get(SECOND_OF_DAY)).put("value", 3),
                                    Values.createObject().put("timestamp", midnight.plusHours(20).get(SECOND_OF_DAY)).put("value", 2),
                                    Values.createObject().put("timestamp", midnight.plusHours(21).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(22).get(SECOND_OF_DAY)).put("value", 0),
                                    Values.createObject().put("timestamp", midnight.plusHours(23).get(SECOND_OF_DAY)).put("value", 0)
                            )
                    )
            );
        });
        production5Asset.getAttribute("totalEnergy").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(23461)));
        production5Asset.getAttribute("installedCapacity").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(76.2)));
        production5Asset.getAttribute("systemEfficiency").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(86)));
        production5Asset.getAttribute("panelOrientation").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(ElectricityProducerOrientationType.SOUTH.name())));
        production5Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("S-Energy")));
        production5Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("SN260P-10")));
        production5Asset.setId(UniqueIdentifierGenerator.generateId(production5Asset.getName()));
        production5Asset = assetStorageService.merge(production5Asset);

        // ### Weather ###

        Asset weatherHttpApiAgent = new Asset("Weather Agent", AssetType.AGENT, energyManagement);
        weatherHttpApiAgent.addAttributes(
                initProtocolConfiguration(new Attribute("weatherApiClient"), HttpClientProtocol.PROTOCOL_NAME)
                        .addMeta(
                                new MetaItem(META_PROTOCOL_BASE_URI, Values.create("https://api.openweathermap.org/data/2.5/")),
                                new MetaItem(META_QUERY_PARAMETERS, Values.createObject()
                                        .put("appid", "a6ea6724e5d116ea6d938bee2a8f4689")
                                        .put("lat", 51.918849)
                                        .put("lon", 4.463250)
                                        .put("units", "metric")),
                                new MetaItem(META_HEADERS, Values.createObject()
                                        .put("Accept", "application/json")
                                )
                        )
        );
        weatherHttpApiAgent = assetStorageService.merge(weatherHttpApiAgent);
        weatherHttpApiAgentId = weatherHttpApiAgent.getId();

        Asset weather = new Asset("Weather", WEATHER, energyManagement);
        weather.setId(UniqueIdentifierGenerator.generateId(weather.getName()));
        weather.addAttributes(
                new Attribute("currentWeather", OBJECT)
                        .setMeta(
                                new MetaItem(
                                        AGENT_LINK,
                                        new AttributeRef(weatherHttpApiAgentId, "weatherApiClient").toArrayValue()),
                                new MetaItem(META_ATTRIBUTE_PATH, Values.create("weather")),
                                new MetaItem(META_ATTRIBUTE_POLLING_MILLIS, Values.create(halfHourInMillis)),
                                new MetaItem(LABEL, Values.create("Open Weather Map API weather end point")),
                                new MetaItem(READ_ONLY, Values.create(true)),
                                new MetaItem(ATTRIBUTE_LINK, createWeatherApiAttributeLink(weather.getId(), "main", "temp", "temperature")),
                                new MetaItem(ATTRIBUTE_LINK, createWeatherApiAttributeLink(weather.getId(), "main", "humidity", "humidity")),
                                new MetaItem(ATTRIBUTE_LINK, createWeatherApiAttributeLink(weather.getId(), "wind", "speed", "windSpeed")),
                                new MetaItem(ATTRIBUTE_LINK, createWeatherApiAttributeLink(weather.getId(), "wind", "deg", "windDirection"))
                        ));
        new Attribute(AttributeType.LOCATION, new GeoJSONPoint(4.463250, 51.918849).toValue());
        weather = assetStorageService.merge(weather);

        // ################################ Realm smartcity - Environment monitor ###################################

        Asset environmentMonitor = new Asset();
        environmentMonitor.setRealm(this.realmCityTenant);
        environmentMonitor.setName("Environment Monitor");
        environmentMonitor.setType(THING);
        environmentMonitor.setId(UniqueIdentifierGenerator.generateId(environmentMonitor.getName()));
        environmentMonitor = assetStorageService.merge(environmentMonitor);

        Asset environment1Asset = createDemoEnvironmentAsset("Oudehaven", environmentMonitor, new GeoJSONPoint(4.49313, 51.91885), () -> new MetaItem[]{
                new MetaItem(AGENT_LINK, new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()),
                new MetaItem(SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(NumberSimulatorElement.ELEMENT_NAME))
        });
        Asset environment2Asset = createDemoEnvironmentAsset("Kaappark", environmentMonitor, new GeoJSONPoint(4.480434, 51.899287), () -> new MetaItem[]{
                new MetaItem(AGENT_LINK, new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()),
                new MetaItem(SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(NumberSimulatorElement.ELEMENT_NAME))
        });
        Asset environment3Asset = createDemoEnvironmentAsset("Museumpark", environmentMonitor, new GeoJSONPoint(4.472457, 51.912047), () -> new MetaItem[]{
                new MetaItem(AGENT_LINK, new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()),
                new MetaItem(SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(NumberSimulatorElement.ELEMENT_NAME))
        });
        Asset environment4Asset = createDemoEnvironmentAsset("Eendrachtsplein", environmentMonitor, new GeoJSONPoint(4.473599, 51.916292), () -> new MetaItem[]{
                new MetaItem(AGENT_LINK, new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()),
                new MetaItem(SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(NumberSimulatorElement.ELEMENT_NAME))
        });

        Asset[] environmentArray = {environment1Asset, environment2Asset, environment3Asset, environment4Asset};
        for (int i = 0; i < environmentArray.length; i++) {
            environmentArray[i].getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Intemo")));
            environmentArray[i].getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Josene outdoor")));
            environmentArray[i].getAttribute("ozone").ifPresent(assetAttribute -> {
                assetAttribute.addMeta(
                        new MetaItem(
                                AGENT_LINK,
                                new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()
                        ),
                        new MetaItem(
                                SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(ReplaySimulatorElement.ELEMENT_NAME)
                        ),
                        new MetaItem(
                                SimulatorProtocol.REPLAY_ATTRIBUTE_LINK_DATA,
                                Values.createArray().addAll(
                                        Values.createObject().put("timestamp", midnight.get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(80,90)),
                                        Values.createObject().put("timestamp", midnight.plusHours(1).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(80,90)),
                                        Values.createObject().put("timestamp", midnight.plusHours(2).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(80,90)),
                                        Values.createObject().put("timestamp", midnight.plusHours(3).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(80,90)),
                                        Values.createObject().put("timestamp", midnight.plusHours(4).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(80,90)),
                                        Values.createObject().put("timestamp", midnight.plusHours(5).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(80,90)),
                                        Values.createObject().put("timestamp", midnight.plusHours(6).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(80,90)),
                                        Values.createObject().put("timestamp", midnight.plusHours(7).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(80,90)),
                                        Values.createObject().put("timestamp", midnight.plusHours(8).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(80,90)),
                                        Values.createObject().put("timestamp", midnight.plusHours(9).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(80,90)),
                                        Values.createObject().put("timestamp", midnight.plusHours(10).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(90,110)),
                                        Values.createObject().put("timestamp", midnight.plusHours(11).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(90,110)),
                                        Values.createObject().put("timestamp", midnight.plusHours(12).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(90,110)),
                                        Values.createObject().put("timestamp", midnight.plusHours(13).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(90,110)),
                                        Values.createObject().put("timestamp", midnight.plusHours(14).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(110,120)),
                                        Values.createObject().put("timestamp", midnight.plusHours(15).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(115,125)),
                                        Values.createObject().put("timestamp", midnight.plusHours(16).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(115,125)),
                                        Values.createObject().put("timestamp", midnight.plusHours(17).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(110,120)),
                                        Values.createObject().put("timestamp", midnight.plusHours(18).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(90,110)),
                                        Values.createObject().put("timestamp", midnight.plusHours(19).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(90,110)),
                                        Values.createObject().put("timestamp", midnight.plusHours(20).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(80,90)),
                                        Values.createObject().put("timestamp", midnight.plusHours(21).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(80,90)),
                                        Values.createObject().put("timestamp", midnight.plusHours(22).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(80,90)),
                                        Values.createObject().put("timestamp", midnight.plusHours(23).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(80,90))
                                )
                        )
                );
            });
            environmentArray[i].setId(UniqueIdentifierGenerator.generateId(environmentArray[i].getName()));
            environmentArray[i] = assetStorageService.merge(environmentArray[i]);
        }

        Asset groundwater1Asset = createDemoGroundwaterAsset("Leuvehaven", environmentMonitor, new GeoJSONPoint(4.48413, 51.91431));
        Asset groundwater2Asset = createDemoGroundwaterAsset("Steiger", environmentMonitor, new GeoJSONPoint(4.482887, 51.920082));
        Asset groundwater3Asset = createDemoGroundwaterAsset("Stadhuis", environmentMonitor, new GeoJSONPoint(4.480876, 51.923212));

        Asset[] groundwaterArray = {groundwater1Asset, groundwater2Asset, groundwater3Asset};
        for (int i = 0; i < groundwaterArray.length; i++) {
            groundwaterArray[i].getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Eijkelkamp")));
            groundwaterArray[i].getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("TeleControlNet")));
            groundwaterArray[i].getAttribute("soilTemperature").ifPresent(assetAttribute -> {
                assetAttribute.addMeta(
                        new MetaItem(
                                AGENT_LINK,
                                new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()
                        ),
                        new MetaItem(
                                SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(ReplaySimulatorElement.ELEMENT_NAME)
                        ),
                        new MetaItem(
                                SimulatorProtocol.REPLAY_ATTRIBUTE_LINK_DATA,
                                Values.createArray().addAll(
                                        Values.createObject().put("timestamp", midnight.get(SECOND_OF_DAY)).put("value", 12.2),
                                        Values.createObject().put("timestamp", midnight.plusHours(1).get(SECOND_OF_DAY)).put("value", 12.1),
                                        Values.createObject().put("timestamp", midnight.plusHours(2).get(SECOND_OF_DAY)).put("value", 12.0),
                                        Values.createObject().put("timestamp", midnight.plusHours(3).get(SECOND_OF_DAY)).put("value", 11.8),
                                        Values.createObject().put("timestamp", midnight.plusHours(4).get(SECOND_OF_DAY)).put("value", 11.7),
                                        Values.createObject().put("timestamp", midnight.plusHours(5).get(SECOND_OF_DAY)).put("value", 11.7),
                                        Values.createObject().put("timestamp", midnight.plusHours(6).get(SECOND_OF_DAY)).put("value", 11.9),
                                        Values.createObject().put("timestamp", midnight.plusHours(7).get(SECOND_OF_DAY)).put("value", 12.1),
                                        Values.createObject().put("timestamp", midnight.plusHours(8).get(SECOND_OF_DAY)).put("value", 12.8),
                                        Values.createObject().put("timestamp", midnight.plusHours(9).get(SECOND_OF_DAY)).put("value", 13.5),
                                        Values.createObject().put("timestamp", midnight.plusHours(10).get(SECOND_OF_DAY)).put("value", 13.9),
                                        Values.createObject().put("timestamp", midnight.plusHours(11).get(SECOND_OF_DAY)).put("value", 15.2),
                                        Values.createObject().put("timestamp", midnight.plusHours(12).get(SECOND_OF_DAY)).put("value", 15.3),
                                        Values.createObject().put("timestamp", midnight.plusHours(13).get(SECOND_OF_DAY)).put("value", 15.5),
                                        Values.createObject().put("timestamp", midnight.plusHours(14).get(SECOND_OF_DAY)).put("value", 15.5),
                                        Values.createObject().put("timestamp", midnight.plusHours(15).get(SECOND_OF_DAY)).put("value", 15.4),
                                        Values.createObject().put("timestamp", midnight.plusHours(16).get(SECOND_OF_DAY)).put("value", 15.2),
                                        Values.createObject().put("timestamp", midnight.plusHours(17).get(SECOND_OF_DAY)).put("value", 15.2),
                                        Values.createObject().put("timestamp", midnight.plusHours(18).get(SECOND_OF_DAY)).put("value", 14.6),
                                        Values.createObject().put("timestamp", midnight.plusHours(19).get(SECOND_OF_DAY)).put("value", 14.2),
                                        Values.createObject().put("timestamp", midnight.plusHours(20).get(SECOND_OF_DAY)).put("value", 13.8),
                                        Values.createObject().put("timestamp", midnight.plusHours(21).get(SECOND_OF_DAY)).put("value", 13.4),
                                        Values.createObject().put("timestamp", midnight.plusHours(22).get(SECOND_OF_DAY)).put("value", 12.8),
                                        Values.createObject().put("timestamp", midnight.plusHours(23).get(SECOND_OF_DAY)).put("value", 12.3)
                                )
                        )
                );
            });
            groundwaterArray[i].getAttribute("waterLevel").ifPresent(assetAttribute -> {
                assetAttribute.addMeta(
                        new MetaItem(
                                AGENT_LINK,
                                new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()
                        ),
                        new MetaItem(
                                SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(ReplaySimulatorElement.ELEMENT_NAME)
                        ),
                        new MetaItem(
                                SimulatorProtocol.REPLAY_ATTRIBUTE_LINK_DATA,
                                Values.createArray().addAll(
                                        Values.createObject().put("timestamp", midnight.get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(110,120)),
                                        Values.createObject().put("timestamp", midnight.plusHours(1).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(110,120)),
                                        Values.createObject().put("timestamp", midnight.plusHours(2).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(110,120)),
                                        Values.createObject().put("timestamp", midnight.plusHours(3).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(110,120)),
                                        Values.createObject().put("timestamp", midnight.plusHours(4).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(110,120)),
                                        Values.createObject().put("timestamp", midnight.plusHours(5).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(110,120)),
                                        Values.createObject().put("timestamp", midnight.plusHours(6).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(110,120)),
                                        Values.createObject().put("timestamp", midnight.plusHours(7).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(110,120)),
                                        Values.createObject().put("timestamp", midnight.plusHours(8).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(110,120)),
                                        Values.createObject().put("timestamp", midnight.plusHours(9).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(110,120)),
                                        Values.createObject().put("timestamp", midnight.plusHours(10).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(110,120)),
                                        Values.createObject().put("timestamp", midnight.plusHours(11).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(110,120)),
                                        Values.createObject().put("timestamp", midnight.plusHours(12).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(110,120)),
                                        Values.createObject().put("timestamp", midnight.plusHours(13).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(110,120)),
                                        Values.createObject().put("timestamp", midnight.plusHours(14).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(110,120)),
                                        Values.createObject().put("timestamp", midnight.plusHours(15).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(100,120)),
                                        Values.createObject().put("timestamp", midnight.plusHours(16).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(100,120)),
                                        Values.createObject().put("timestamp", midnight.plusHours(17).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(90,110)),
                                        Values.createObject().put("timestamp", midnight.plusHours(18).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(100,110)),
                                        Values.createObject().put("timestamp", midnight.plusHours(19).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(100,120)),
                                        Values.createObject().put("timestamp", midnight.plusHours(20).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(110,120)),
                                        Values.createObject().put("timestamp", midnight.plusHours(21).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(110,120)),
                                        Values.createObject().put("timestamp", midnight.plusHours(22).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(110,120)),
                                        Values.createObject().put("timestamp", midnight.plusHours(23).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(110,120))
                                )
                        )
                );
            });
            groundwaterArray[i].setId(UniqueIdentifierGenerator.generateId(groundwaterArray[i].getName()));
            groundwaterArray[i] = assetStorageService.merge(groundwaterArray[i]);
        }

        // ################################ Realm smartcity - Mobility and Safety ###################################

        Asset mobilityAndSafety = new Asset();
        mobilityAndSafety.setRealm(this.realmCityTenant);
        mobilityAndSafety.setName("Mobility and Safety");
        mobilityAndSafety.setType(THING);
        mobilityAndSafety.setId(UniqueIdentifierGenerator.generateId(mobilityAndSafety.getName()));
        mobilityAndSafety = assetStorageService.merge(mobilityAndSafety);

        // ### Parking ###

        Asset parkingGroupAsset = new Asset("Parking group", GROUP, mobilityAndSafety);
        parkingGroupAsset.getAttribute("childAssetType").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("urn:openremote:asset:parking")));
        parkingGroupAsset.addAttributes(
                new Attribute("totalOccupancy", PERCENTAGE)
                        .addMeta(
                                LABEL.withInitialValue("Percentage of total parking spaces in use"),
                                RULE_STATE,
                                READ_ONLY,
                                STORE_DATA_POINTS));
        parkingGroupAsset.setId(UniqueIdentifierGenerator.generateId(parkingGroupAsset.getName()));
        parkingGroupAsset = assetStorageService.merge(parkingGroupAsset);

        Asset parking1Asset = createDemoParkingAsset("Markthal", parkingGroupAsset, new GeoJSONPoint(4.48527, 51.91984));
        parking1Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("SKIDATA")));
        parking1Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Barrier.Gate")));
        parking1Asset.getAttribute("occupiedSpaces").ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                    new MetaItem(
                            AGENT_LINK,
                            new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()
                    ),
                    new MetaItem(
                            SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(ReplaySimulatorElement.ELEMENT_NAME)
                    ),
                    new MetaItem(
                            SimulatorProtocol.REPLAY_ATTRIBUTE_LINK_DATA,
                            Values.createArray().addAll(
                                    Values.createObject().put("timestamp", midnight.get(SECOND_OF_DAY)).put("value", 34),
                                    Values.createObject().put("timestamp", midnight.plusHours(1).get(SECOND_OF_DAY)).put("value", 37),
                                    Values.createObject().put("timestamp", midnight.plusHours(2).get(SECOND_OF_DAY)).put("value", 31),
                                    Values.createObject().put("timestamp", midnight.plusHours(3).get(SECOND_OF_DAY)).put("value", 36),
                                    Values.createObject().put("timestamp", midnight.plusHours(4).get(SECOND_OF_DAY)).put("value", 32),
                                    Values.createObject().put("timestamp", midnight.plusHours(5).get(SECOND_OF_DAY)).put("value", 39),
                                    Values.createObject().put("timestamp", midnight.plusHours(6).get(SECOND_OF_DAY)).put("value", 47),
                                    Values.createObject().put("timestamp", midnight.plusHours(7).get(SECOND_OF_DAY)).put("value", 53),
                                    Values.createObject().put("timestamp", midnight.plusHours(8).get(SECOND_OF_DAY)).put("value", 165),
                                    Values.createObject().put("timestamp", midnight.plusHours(9).get(SECOND_OF_DAY)).put("value", 301),
                                    Values.createObject().put("timestamp", midnight.plusHours(10).get(SECOND_OF_DAY)).put("value", 417),
                                    Values.createObject().put("timestamp", midnight.plusHours(11).get(SECOND_OF_DAY)).put("value", 442),
                                    Values.createObject().put("timestamp", midnight.plusHours(12).get(SECOND_OF_DAY)).put("value", 489),
                                    Values.createObject().put("timestamp", midnight.plusHours(13).get(SECOND_OF_DAY)).put("value", 467),
                                    Values.createObject().put("timestamp", midnight.plusHours(14).get(SECOND_OF_DAY)).put("value", 490),
                                    Values.createObject().put("timestamp", midnight.plusHours(15).get(SECOND_OF_DAY)).put("value", 438),
                                    Values.createObject().put("timestamp", midnight.plusHours(16).get(SECOND_OF_DAY)).put("value", 457),
                                    Values.createObject().put("timestamp", midnight.plusHours(17).get(SECOND_OF_DAY)).put("value", 402),
                                    Values.createObject().put("timestamp", midnight.plusHours(18).get(SECOND_OF_DAY)).put("value", 379),
                                    Values.createObject().put("timestamp", midnight.plusHours(19).get(SECOND_OF_DAY)).put("value", 336),
                                    Values.createObject().put("timestamp", midnight.plusHours(20).get(SECOND_OF_DAY)).put("value", 257),
                                    Values.createObject().put("timestamp", midnight.plusHours(21).get(SECOND_OF_DAY)).put("value", 204),
                                    Values.createObject().put("timestamp", midnight.plusHours(22).get(SECOND_OF_DAY)).put("value", 112),
                                    Values.createObject().put("timestamp", midnight.plusHours(23).get(SECOND_OF_DAY)).put("value", 75)
                            )
                    )
            );
        });
        parking1Asset.getAttribute("priceHourly").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(3.75)));
        parking1Asset.getAttribute("priceDaily").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(25.00)));
        parking1Asset.getAttribute("totalSpaces").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(512)));
        parking1Asset.setId(UniqueIdentifierGenerator.generateId(parking1Asset.getName()));
        parking1Asset = assetStorageService.merge(parking1Asset);

        Asset parking2Asset = createDemoParkingAsset("Lijnbaan", parkingGroupAsset, new GeoJSONPoint(4.47681, 51.91849));
        parking2Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("SKIDATA")));
        parking2Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Barrier.Gate")));
        parking2Asset.getAttribute("occupiedSpaces").ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                    new MetaItem(
                            AGENT_LINK,
                            new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()
                    ),
                    new MetaItem(
                            SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(ReplaySimulatorElement.ELEMENT_NAME)
                    ),
                    new MetaItem(
                            SimulatorProtocol.REPLAY_ATTRIBUTE_LINK_DATA,
                            Values.createArray().addAll(
                                    Values.createObject().put("timestamp", midnight.get(SECOND_OF_DAY)).put("value", 31),
                                    Values.createObject().put("timestamp", midnight.plusHours(1).get(SECOND_OF_DAY)).put("value", 24),
                                    Values.createObject().put("timestamp", midnight.plusHours(2).get(SECOND_OF_DAY)).put("value", 36),
                                    Values.createObject().put("timestamp", midnight.plusHours(3).get(SECOND_OF_DAY)).put("value", 38),
                                    Values.createObject().put("timestamp", midnight.plusHours(4).get(SECOND_OF_DAY)).put("value", 46),
                                    Values.createObject().put("timestamp", midnight.plusHours(5).get(SECOND_OF_DAY)).put("value", 48),
                                    Values.createObject().put("timestamp", midnight.plusHours(6).get(SECOND_OF_DAY)).put("value", 52),
                                    Values.createObject().put("timestamp", midnight.plusHours(7).get(SECOND_OF_DAY)).put("value", 89),
                                    Values.createObject().put("timestamp", midnight.plusHours(8).get(SECOND_OF_DAY)).put("value", 142),
                                    Values.createObject().put("timestamp", midnight.plusHours(9).get(SECOND_OF_DAY)).put("value", 187),
                                    Values.createObject().put("timestamp", midnight.plusHours(10).get(SECOND_OF_DAY)).put("value", 246),
                                    Values.createObject().put("timestamp", midnight.plusHours(11).get(SECOND_OF_DAY)).put("value", 231),
                                    Values.createObject().put("timestamp", midnight.plusHours(12).get(SECOND_OF_DAY)).put("value", 367),
                                    Values.createObject().put("timestamp", midnight.plusHours(13).get(SECOND_OF_DAY)).put("value", 345),
                                    Values.createObject().put("timestamp", midnight.plusHours(14).get(SECOND_OF_DAY)).put("value", 386),
                                    Values.createObject().put("timestamp", midnight.plusHours(15).get(SECOND_OF_DAY)).put("value", 312),
                                    Values.createObject().put("timestamp", midnight.plusHours(16).get(SECOND_OF_DAY)).put("value", 363),
                                    Values.createObject().put("timestamp", midnight.plusHours(17).get(SECOND_OF_DAY)).put("value", 276),
                                    Values.createObject().put("timestamp", midnight.plusHours(18).get(SECOND_OF_DAY)).put("value", 249),
                                    Values.createObject().put("timestamp", midnight.plusHours(19).get(SECOND_OF_DAY)).put("value", 256),
                                    Values.createObject().put("timestamp", midnight.plusHours(20).get(SECOND_OF_DAY)).put("value", 123),
                                    Values.createObject().put("timestamp", midnight.plusHours(21).get(SECOND_OF_DAY)).put("value", 153),
                                    Values.createObject().put("timestamp", midnight.plusHours(22).get(SECOND_OF_DAY)).put("value", 83),
                                    Values.createObject().put("timestamp", midnight.plusHours(23).get(SECOND_OF_DAY)).put("value", 25)
                            )
                    )
            );
        });
        parking2Asset.getAttribute("priceHourly").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(3.50)));
        parking2Asset.getAttribute("priceDaily").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(23.00)));
        parking2Asset.getAttribute("totalSpaces").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(390)));
        parking2Asset.setId(UniqueIdentifierGenerator.generateId(parking2Asset.getName()));
        parking2Asset = assetStorageService.merge(parking2Asset);

        Asset parking3Asset = createDemoParkingAsset("Erasmusbrug", parkingGroupAsset, new GeoJSONPoint(4.48207, 51.91127));
        parking3Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Kiestra")));
        parking3Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Genius Rainbow")));
        parking3Asset.getAttribute("occupiedSpaces").ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                    new MetaItem(
                            AGENT_LINK,
                            new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()
                    ),
                    new MetaItem(
                            SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(ReplaySimulatorElement.ELEMENT_NAME)
                    ),
                    new MetaItem(
                            SimulatorProtocol.REPLAY_ATTRIBUTE_LINK_DATA,
                            Values.createArray().addAll(
                                    Values.createObject().put("timestamp", midnight.get(SECOND_OF_DAY)).put("value", 25),
                                    Values.createObject().put("timestamp", midnight.plusHours(1).get(SECOND_OF_DAY)).put("value", 23),
                                    Values.createObject().put("timestamp", midnight.plusHours(2).get(SECOND_OF_DAY)).put("value", 23),
                                    Values.createObject().put("timestamp", midnight.plusHours(3).get(SECOND_OF_DAY)).put("value", 21),
                                    Values.createObject().put("timestamp", midnight.plusHours(4).get(SECOND_OF_DAY)).put("value", 18),
                                    Values.createObject().put("timestamp", midnight.plusHours(5).get(SECOND_OF_DAY)).put("value", 13),
                                    Values.createObject().put("timestamp", midnight.plusHours(6).get(SECOND_OF_DAY)).put("value", 29),
                                    Values.createObject().put("timestamp", midnight.plusHours(7).get(SECOND_OF_DAY)).put("value", 36),
                                    Values.createObject().put("timestamp", midnight.plusHours(8).get(SECOND_OF_DAY)).put("value", 119),
                                    Values.createObject().put("timestamp", midnight.plusHours(9).get(SECOND_OF_DAY)).put("value", 257),
                                    Values.createObject().put("timestamp", midnight.plusHours(10).get(SECOND_OF_DAY)).put("value", 357),
                                    Values.createObject().put("timestamp", midnight.plusHours(11).get(SECOND_OF_DAY)).put("value", 368),
                                    Values.createObject().put("timestamp", midnight.plusHours(12).get(SECOND_OF_DAY)).put("value", 362),
                                    Values.createObject().put("timestamp", midnight.plusHours(13).get(SECOND_OF_DAY)).put("value", 349),
                                    Values.createObject().put("timestamp", midnight.plusHours(14).get(SECOND_OF_DAY)).put("value", 370),
                                    Values.createObject().put("timestamp", midnight.plusHours(15).get(SECOND_OF_DAY)).put("value", 367),
                                    Values.createObject().put("timestamp", midnight.plusHours(16).get(SECOND_OF_DAY)).put("value", 355),
                                    Values.createObject().put("timestamp", midnight.plusHours(17).get(SECOND_OF_DAY)).put("value", 314),
                                    Values.createObject().put("timestamp", midnight.plusHours(18).get(SECOND_OF_DAY)).put("value", 254),
                                    Values.createObject().put("timestamp", midnight.plusHours(19).get(SECOND_OF_DAY)).put("value", 215),
                                    Values.createObject().put("timestamp", midnight.plusHours(20).get(SECOND_OF_DAY)).put("value", 165),
                                    Values.createObject().put("timestamp", midnight.plusHours(21).get(SECOND_OF_DAY)).put("value", 149),
                                    Values.createObject().put("timestamp", midnight.plusHours(22).get(SECOND_OF_DAY)).put("value", 108),
                                    Values.createObject().put("timestamp", midnight.plusHours(23).get(SECOND_OF_DAY)).put("value", 47)
                            )
                    )
            );
        });
        parking3Asset.getAttribute("priceHourly").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(3.40)));
        parking3Asset.getAttribute("priceDaily").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(20.00)));
        parking3Asset.getAttribute("totalSpaces").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(373)));
        parking3Asset.setId(UniqueIdentifierGenerator.generateId(parking3Asset.getName()));
        parking3Asset = assetStorageService.merge(parking3Asset);

        // ### Crowd control ###

        Asset assetAreaStation = new Asset("Stationsplein", AREA, mobilityAndSafety)
                .setAttributes(
                        new Attribute(AttributeType.LOCATION, STATIONSPLEIN_LOCATION.toValue()),
                        new Attribute(AttributeType.GEO_POSTAL_CODE, Values.create("3013 AK")),
                        new Attribute(AttributeType.GEO_CITY, Values.create("Rotterdam")),
                        new Attribute(AttributeType.GEO_COUNTRY, Values.create("Netherlands"))
                );
        assetAreaStation.setId(UniqueIdentifierGenerator.generateId(assetAreaStation.getName()));
        assetAreaStation = assetStorageService.merge(assetAreaStation);
        area1Id = assetAreaStation.getId();

        Asset peopleCounter1Asset = createDemoPeopleCounterAsset("People Counter South", assetAreaStation, new GeoJSONPoint(4.470147, 51.923171), () -> new MetaItem[]{
                new MetaItem(AGENT_LINK, new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()),
                new MetaItem(SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(NumberSimulatorElement.ELEMENT_NAME))
        });
        peopleCounter1Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("ViNotion")));
        peopleCounter1Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("ViSense")));
        peopleCounter1Asset.getAttribute("peopleCountGrowth").ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                    new MetaItem(
                            AGENT_LINK,
                            new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()
                    ),
                    new MetaItem(
                            SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(ReplaySimulatorElement.ELEMENT_NAME)
                    ),
                    new MetaItem(
                            SimulatorProtocol.REPLAY_ATTRIBUTE_LINK_DATA,
                            Values.createArray().addAll(
                                    Values.createObject().put("timestamp", midnight.get(SECOND_OF_DAY)).put("value", 0.2),
                                    Values.createObject().put("timestamp", midnight.plusHours(1).get(SECOND_OF_DAY)).put("value", 0.3),
                                    Values.createObject().put("timestamp", midnight.plusHours(2).get(SECOND_OF_DAY)).put("value", 0.1),
                                    Values.createObject().put("timestamp", midnight.plusHours(3).get(SECOND_OF_DAY)).put("value", 0.0),
                                    Values.createObject().put("timestamp", midnight.plusHours(4).get(SECOND_OF_DAY)).put("value", 0.2),
                                    Values.createObject().put("timestamp", midnight.plusHours(5).get(SECOND_OF_DAY)).put("value", 0.4),
                                    Values.createObject().put("timestamp", midnight.plusHours(6).get(SECOND_OF_DAY)).put("value", 0.5),
                                    Values.createObject().put("timestamp", midnight.plusHours(7).get(SECOND_OF_DAY)).put("value", 0.7),
                                    Values.createObject().put("timestamp", midnight.plusHours(8).get(SECOND_OF_DAY)).put("value", 1.8),
                                    Values.createObject().put("timestamp", midnight.plusHours(9).get(SECOND_OF_DAY)).put("value", 2.1),
                                    Values.createObject().put("timestamp", midnight.plusHours(10).get(SECOND_OF_DAY)).put("value", 2.4),
                                    Values.createObject().put("timestamp", midnight.plusHours(11).get(SECOND_OF_DAY)).put("value", 1.9),
                                    Values.createObject().put("timestamp", midnight.plusHours(12).get(SECOND_OF_DAY)).put("value", 1.8),
                                    Values.createObject().put("timestamp", midnight.plusHours(13).get(SECOND_OF_DAY)).put("value", 2.1),
                                    Values.createObject().put("timestamp", midnight.plusHours(14).get(SECOND_OF_DAY)).put("value", 1.8),
                                    Values.createObject().put("timestamp", midnight.plusHours(15).get(SECOND_OF_DAY)).put("value", 1.7),
                                    Values.createObject().put("timestamp", midnight.plusHours(16).get(SECOND_OF_DAY)).put("value", 2.3),
                                    Values.createObject().put("timestamp", midnight.plusHours(17).get(SECOND_OF_DAY)).put("value", 3.1),
                                    Values.createObject().put("timestamp", midnight.plusHours(18).get(SECOND_OF_DAY)).put("value", 2.8),
                                    Values.createObject().put("timestamp", midnight.plusHours(19).get(SECOND_OF_DAY)).put("value", 2.2),
                                    Values.createObject().put("timestamp", midnight.plusHours(20).get(SECOND_OF_DAY)).put("value", 1.6),
                                    Values.createObject().put("timestamp", midnight.plusHours(21).get(SECOND_OF_DAY)).put("value", 1.7),
                                    Values.createObject().put("timestamp", midnight.plusHours(22).get(SECOND_OF_DAY)).put("value", 1.1),
                                    Values.createObject().put("timestamp", midnight.plusHours(23).get(SECOND_OF_DAY)).put("value", 0.8)
                            )
                    )
            );
        });
        peopleCounter1Asset.setId(UniqueIdentifierGenerator.generateId(peopleCounter1Asset.getName()));
        peopleCounter1Asset = assetStorageService.merge(peopleCounter1Asset);

        Asset peopleCounter2Asset = createDemoPeopleCounterAsset("People Counter North", assetAreaStation, new GeoJSONPoint(4.469329, 51.923700), () -> new MetaItem[]{
                new MetaItem(AGENT_LINK, new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()),
                new MetaItem(SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(NumberSimulatorElement.ELEMENT_NAME))
        });
        peopleCounter2Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Axis")));
        peopleCounter2Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("P1375-E")));
        peopleCounter2Asset.getAttribute("peopleCountGrowth").ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                    new MetaItem(
                            AGENT_LINK,
                            new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()
                    ),
                    new MetaItem(
                            SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(ReplaySimulatorElement.ELEMENT_NAME)
                    ),
                    new MetaItem(
                            SimulatorProtocol.REPLAY_ATTRIBUTE_LINK_DATA,
                            Values.createArray().addAll(
                                    Values.createObject().put("timestamp", midnight.get(SECOND_OF_DAY)).put("value", 0.3),
                                    Values.createObject().put("timestamp", midnight.plusHours(1).get(SECOND_OF_DAY)).put("value", 0.2),
                                    Values.createObject().put("timestamp", midnight.plusHours(2).get(SECOND_OF_DAY)).put("value", 0.3),
                                    Values.createObject().put("timestamp", midnight.plusHours(3).get(SECOND_OF_DAY)).put("value", 0.1),
                                    Values.createObject().put("timestamp", midnight.plusHours(4).get(SECOND_OF_DAY)).put("value", 0.0),
                                    Values.createObject().put("timestamp", midnight.plusHours(5).get(SECOND_OF_DAY)).put("value", 0.3),
                                    Values.createObject().put("timestamp", midnight.plusHours(6).get(SECOND_OF_DAY)).put("value", 0.7),
                                    Values.createObject().put("timestamp", midnight.plusHours(7).get(SECOND_OF_DAY)).put("value", 0.6),
                                    Values.createObject().put("timestamp", midnight.plusHours(8).get(SECOND_OF_DAY)).put("value", 1.9),
                                    Values.createObject().put("timestamp", midnight.plusHours(9).get(SECOND_OF_DAY)).put("value", 2.2),
                                    Values.createObject().put("timestamp", midnight.plusHours(10).get(SECOND_OF_DAY)).put("value", 2.8),
                                    Values.createObject().put("timestamp", midnight.plusHours(11).get(SECOND_OF_DAY)).put("value", 1.6),
                                    Values.createObject().put("timestamp", midnight.plusHours(12).get(SECOND_OF_DAY)).put("value", 1.9),
                                    Values.createObject().put("timestamp", midnight.plusHours(13).get(SECOND_OF_DAY)).put("value", 2.2),
                                    Values.createObject().put("timestamp", midnight.plusHours(14).get(SECOND_OF_DAY)).put("value", 1.9),
                                    Values.createObject().put("timestamp", midnight.plusHours(15).get(SECOND_OF_DAY)).put("value", 1.6),
                                    Values.createObject().put("timestamp", midnight.plusHours(16).get(SECOND_OF_DAY)).put("value", 2.4),
                                    Values.createObject().put("timestamp", midnight.plusHours(17).get(SECOND_OF_DAY)).put("value", 3.2),
                                    Values.createObject().put("timestamp", midnight.plusHours(18).get(SECOND_OF_DAY)).put("value", 2.9),
                                    Values.createObject().put("timestamp", midnight.plusHours(19).get(SECOND_OF_DAY)).put("value", 2.3),
                                    Values.createObject().put("timestamp", midnight.plusHours(20).get(SECOND_OF_DAY)).put("value", 1.7),
                                    Values.createObject().put("timestamp", midnight.plusHours(21).get(SECOND_OF_DAY)).put("value", 1.4),
                                    Values.createObject().put("timestamp", midnight.plusHours(22).get(SECOND_OF_DAY)).put("value", 1.2),
                                    Values.createObject().put("timestamp", midnight.plusHours(23).get(SECOND_OF_DAY)).put("value", 0.7)
                            )
                    )
            );
        });
        peopleCounter2Asset.setId(UniqueIdentifierGenerator.generateId(peopleCounter2Asset.getName()));
        peopleCounter2Asset = assetStorageService.merge(peopleCounter2Asset);

        Asset microphone1Asset = createDemoMicrophoneAsset("Microphone South", assetAreaStation, new GeoJSONPoint(4.470362, 51.923201), () -> new MetaItem[]{
                new MetaItem(AGENT_LINK, new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()),
                new MetaItem(SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(ReplaySimulatorElement.ELEMENT_NAME)),
                new MetaItem(
                        SimulatorProtocol.REPLAY_ATTRIBUTE_LINK_DATA,
                        Values.createArray().addAll(
                                Values.createObject().put("timestamp", midnight.get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(50,60)),
                                Values.createObject().put("timestamp", midnight.plusHours(1).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(45,50)),
                                Values.createObject().put("timestamp", midnight.plusHours(2).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(45,50)),
                                Values.createObject().put("timestamp", midnight.plusHours(3).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(45,50)),
                                Values.createObject().put("timestamp", midnight.plusHours(4).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(45,50)),
                                Values.createObject().put("timestamp", midnight.plusHours(5).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(45,50)),
                                Values.createObject().put("timestamp", midnight.plusHours(6).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(50,55)),
                                Values.createObject().put("timestamp", midnight.plusHours(7).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(50,55)),
                                Values.createObject().put("timestamp", midnight.plusHours(8).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(50,55)),
                                Values.createObject().put("timestamp", midnight.plusHours(9).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(55,60)),
                                Values.createObject().put("timestamp", midnight.plusHours(10).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(55,60)),
                                Values.createObject().put("timestamp", midnight.plusHours(11).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(55,60)),
                                Values.createObject().put("timestamp", midnight.plusHours(12).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(60,65)),
                                Values.createObject().put("timestamp", midnight.plusHours(13).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(60,65)),
                                Values.createObject().put("timestamp", midnight.plusHours(14).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(55,60)),
                                Values.createObject().put("timestamp", midnight.plusHours(15).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(55,60)),
                                Values.createObject().put("timestamp", midnight.plusHours(16).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(60,65)),
                                Values.createObject().put("timestamp", midnight.plusHours(17).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(60,70)),
                                Values.createObject().put("timestamp", midnight.plusHours(18).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(60,65)),
                                Values.createObject().put("timestamp", midnight.plusHours(19).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(55,60)),
                                Values.createObject().put("timestamp", midnight.plusHours(20).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(55,60)),
                                Values.createObject().put("timestamp", midnight.plusHours(21).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(60,70)),
                                Values.createObject().put("timestamp", midnight.plusHours(22).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(60,65)),
                                Values.createObject().put("timestamp", midnight.plusHours(23).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(50,60))
                        )
                )
        });
        microphone1Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Sorama")));
        microphone1Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("CAM1K")));
        microphone1Asset.setId(UniqueIdentifierGenerator.generateId(microphone1Asset.getName()));
        microphone1Asset = assetStorageService.merge(microphone1Asset);

        Asset microphone2Asset = createDemoMicrophoneAsset("Microphone North", assetAreaStation, new GeoJSONPoint(4.469190, 51.923786), () -> new MetaItem[]{
                new MetaItem(AGENT_LINK, new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()),
                new MetaItem(SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(ReplaySimulatorElement.ELEMENT_NAME)),
                new MetaItem(
                        SimulatorProtocol.REPLAY_ATTRIBUTE_LINK_DATA,
                        Values.createArray().addAll(
                                Values.createObject().put("timestamp", midnight.get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(50,60)),
                                Values.createObject().put("timestamp", midnight.plusHours(1).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(45,50)),
                                Values.createObject().put("timestamp", midnight.plusHours(2).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(45,50)),
                                Values.createObject().put("timestamp", midnight.plusHours(3).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(45,50)),
                                Values.createObject().put("timestamp", midnight.plusHours(4).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(45,50)),
                                Values.createObject().put("timestamp", midnight.plusHours(5).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(45,50)),
                                Values.createObject().put("timestamp", midnight.plusHours(6).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(50,55)),
                                Values.createObject().put("timestamp", midnight.plusHours(7).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(50,55)),
                                Values.createObject().put("timestamp", midnight.plusHours(8).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(50,55)),
                                Values.createObject().put("timestamp", midnight.plusHours(9).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(55,60)),
                                Values.createObject().put("timestamp", midnight.plusHours(10).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(55,60)),
                                Values.createObject().put("timestamp", midnight.plusHours(11).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(55,60)),
                                Values.createObject().put("timestamp", midnight.plusHours(12).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(60,65)),
                                Values.createObject().put("timestamp", midnight.plusHours(13).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(60,65)),
                                Values.createObject().put("timestamp", midnight.plusHours(14).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(55,60)),
                                Values.createObject().put("timestamp", midnight.plusHours(15).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(55,60)),
                                Values.createObject().put("timestamp", midnight.plusHours(16).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(60,65)),
                                Values.createObject().put("timestamp", midnight.plusHours(17).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(60,70)),
                                Values.createObject().put("timestamp", midnight.plusHours(18).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(60,65)),
                                Values.createObject().put("timestamp", midnight.plusHours(19).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(55,60)),
                                Values.createObject().put("timestamp", midnight.plusHours(20).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(55,60)),
                                Values.createObject().put("timestamp", midnight.plusHours(21).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(60,70)),
                                Values.createObject().put("timestamp", midnight.plusHours(22).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(60,65)),
                                Values.createObject().put("timestamp", midnight.plusHours(23).get(SECOND_OF_DAY)).put("value", getRandomNumberInRange(50,60))
                        )
                )
        });
        microphone2Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Sorama")));
        microphone2Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("CAM1K")));
        microphone2Asset.setId(UniqueIdentifierGenerator.generateId(microphone2Asset.getName()));
        microphone2Asset = assetStorageService.merge(microphone2Asset);

        Asset lightStation1Asset = createDemoLightAsset("Station Light NW", assetAreaStation, new GeoJSONPoint(4.468874, 51.923881));
        lightStation1Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Philips")));
        lightStation1Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("CityTouch")));
        lightStation1Asset.setId(UniqueIdentifierGenerator.generateId(lightStation1Asset.getName()));
        lightStation1Asset = assetStorageService.merge(lightStation1Asset);

        Asset lightStation2Asset = createDemoLightAsset("Station Light NE", assetAreaStation, new GeoJSONPoint(4.470539, 51.923991));
        lightStation2Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Philips")));
        lightStation2Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("CityTouch")));
        lightStation2Asset.setId(UniqueIdentifierGenerator.generateId(lightStation2Asset.getName()));
        lightStation2Asset = assetStorageService.merge(lightStation2Asset);

        Asset lightStation3Asset = createDemoLightAsset("Station Light S", assetAreaStation, new GeoJSONPoint(4.470558, 51.923186));
        lightStation3Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Philips")));
        lightStation3Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("CityTouch")));
        lightStation3Asset.setId(UniqueIdentifierGenerator.generateId(lightStation3Asset.getName()));
        lightStation3Asset = assetStorageService.merge(lightStation3Asset);

        // ### Lighting controller ###

        Asset lightingControllerOPAsset = createDemoLightControllerAsset("Lighting Noordereiland", mobilityAndSafety, new GeoJSONPoint(4.496177, 51.915060));
        lightingControllerOPAsset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Pharos")));
        lightingControllerOPAsset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("LPC X")));
        lightingControllerOPAsset.setId(UniqueIdentifierGenerator.generateId(lightingControllerOPAsset.getName()));
        lightingControllerOPAsset = assetStorageService.merge(lightingControllerOPAsset);

        Asset lightOP1Asset = createDemoLightAsset("OnsPark1", lightingControllerOPAsset, new GeoJSONPoint(4.49626, 51.91516));
        lightOP1Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Schréder")));
        lightOP1Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Axia 2")));
        lightOP1Asset.setId(UniqueIdentifierGenerator.generateId(lightOP1Asset.getName()));
        lightOP1Asset = assetStorageService.merge(lightOP1Asset);

        Asset lightOP2Asset = createDemoLightAsset("OnsPark2", lightingControllerOPAsset, new GeoJSONPoint(4.49705, 51.91549));
        lightOP2Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Schréder")));
        lightOP2Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Axia 2")));
        lightOP2Asset.setId(UniqueIdentifierGenerator.generateId(lightOP2Asset.getName()));
        lightOP2Asset = assetStorageService.merge(lightOP2Asset);

        Asset lightOP3Asset = createDemoLightAsset("OnsPark3", lightingControllerOPAsset, new GeoJSONPoint(4.49661, 51.91495));
        lightOP3Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Schréder")));
        lightOP3Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Axia 2")));
        lightOP3Asset.setId(UniqueIdentifierGenerator.generateId(lightOP3Asset.getName()));
        lightOP3Asset = assetStorageService.merge(lightOP3Asset);

        Asset lightOP4Asset = createDemoLightAsset("OnsPark4", lightingControllerOPAsset, new GeoJSONPoint(4.49704, 51.91520));
        lightOP4Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Schréder")));
        lightOP4Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Axia 2")));
        lightOP4Asset.setId(UniqueIdentifierGenerator.generateId(lightOP4Asset.getName()));
        lightOP4Asset = assetStorageService.merge(lightOP4Asset);

        Asset lightOP5Asset = createDemoLightAsset("OnsPark5", lightingControllerOPAsset, new GeoJSONPoint(4.49758, 51.91440));
        lightOP5Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Schréder")));
        lightOP5Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Axia 2")));
        lightOP5Asset.setId(UniqueIdentifierGenerator.generateId(lightOP5Asset.getName()));
        lightOP5Asset = assetStorageService.merge(lightOP5Asset);

        Asset lightOP6Asset = createDemoLightAsset("OnsPark6", lightingControllerOPAsset, new GeoJSONPoint(4.49786, 51.91452));
        lightOP6Asset.getAttribute("manufacturer").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Schréder")));
        lightOP6Asset.getAttribute("model").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Axia 2")));
        lightOP6Asset.setId(UniqueIdentifierGenerator.generateId(lightOP6Asset.getName()));
        lightOP6Asset = assetStorageService.merge(lightOP6Asset);

        // ### Ships ###

        Asset shipGroupAsset = new Asset("Ship group", GROUP, mobilityAndSafety);
        shipGroupAsset.getAttribute("childAssetType").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("urn:openremote:asset:ship")));
        shipGroupAsset.setId(UniqueIdentifierGenerator.generateId(shipGroupAsset.getName()));
        shipGroupAsset = assetStorageService.merge(shipGroupAsset);

        Asset ship1Asset = createDemoShipAsset("Hotel New York", shipGroupAsset, new GeoJSONPoint(4.48527, 51.91984));
        ship1Asset.getAttribute("shipLength").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(12)));
        ship1Asset.getAttribute("shipType").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create("Passenger")));
        ship1Asset.getAttribute("shipImoNumber").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(9183527)));
        ship1Asset.getAttribute("shipMssiNumber").ifPresent(assetAttribute -> assetAttribute.setValue(Values.create(244650537)));
        ship1Asset.getAttribute("location").ifPresent(assetAttribute -> {
                    assetAttribute.addMeta(
                            new MetaItem(
                                    AGENT_LINK,
                                    new AttributeRef(smartcitySimulatorAgentId, "replaySimulator").toArrayValue()
                            ),
                            new MetaItem(
                                    SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(ReplaySimulatorElement.ELEMENT_NAME)
                            ),
                            new MetaItem(
                                    SimulatorProtocol.REPLAY_ATTRIBUTE_LINK_DATA,
                                    Values.createArray().addAll(
                                            Values.createObject().put("timestamp", midnight.plusHours(6).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(6).plusMinutes(5).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(6).plusMinutes(10).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(6).plusMinutes(15).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(6).plusMinutes(20).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(6).plusMinutes(25).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(6).plusMinutes(30).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(6).plusMinutes(35).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(6).plusMinutes(40).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(6).plusMinutes(45).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(6).plusMinutes(50).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(6).plusMinutes(55).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(7).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(7).plusMinutes(5).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(7).plusMinutes(10).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(7).plusMinutes(15).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(7).plusMinutes(20).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(7).plusMinutes(25).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(7).plusMinutes(30).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(7).plusMinutes(35).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(7).plusMinutes(40).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(7).plusMinutes(45).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(7).plusMinutes(50).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(7).plusMinutes(55).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(8).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(8).plusMinutes(5).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(8).plusMinutes(10).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(8).plusMinutes(15).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(8).plusMinutes(20).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(8).plusMinutes(25).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(8).plusMinutes(30).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(8).plusMinutes(35).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(8).plusMinutes(40).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(8).plusMinutes(45).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(8).plusMinutes(50).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(8).plusMinutes(55).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(9).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(9).plusMinutes(5).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(9).plusMinutes(10).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(9).plusMinutes(15).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(9).plusMinutes(20).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(9).plusMinutes(25).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(9).plusMinutes(30).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(9).plusMinutes(35).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(9).plusMinutes(40).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(9).plusMinutes(45).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(9).plusMinutes(50).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(9).plusMinutes(55).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(10).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(10).plusMinutes(5).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(10).plusMinutes(10).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(10).plusMinutes(15).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(10).plusMinutes(20).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(10).plusMinutes(25).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(10).plusMinutes(30).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(10).plusMinutes(35).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(10).plusMinutes(40).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(10).plusMinutes(45).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(10).plusMinutes(50).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(10).plusMinutes(55).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(11).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(11).plusMinutes(5).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(11).plusMinutes(10).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(11).plusMinutes(15).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(11).plusMinutes(20).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(11).plusMinutes(25).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(11).plusMinutes(30).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(11).plusMinutes(35).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(11).plusMinutes(40).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(11).plusMinutes(45).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(11).plusMinutes(50).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(11).plusMinutes(55).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(12).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(12).plusMinutes(5).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(12).plusMinutes(10).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(12).plusMinutes(15).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(12).plusMinutes(20).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(12).plusMinutes(25).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(12).plusMinutes(30).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(12).plusMinutes(35).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(12).plusMinutes(40).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(12).plusMinutes(45).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(12).plusMinutes(50).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(12).plusMinutes(55).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(13).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(13).plusMinutes(5).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(13).plusMinutes(10).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(13).plusMinutes(15).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(13).plusMinutes(20).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(13).plusMinutes(25).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(13).plusMinutes(30).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(13).plusMinutes(35).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(13).plusMinutes(40).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(13).plusMinutes(45).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(13).plusMinutes(50).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(13).plusMinutes(55).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(14).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(14).plusMinutes(5).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(14).plusMinutes(10).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(14).plusMinutes(15).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(14).plusMinutes(20).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(14).plusMinutes(25).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(14).plusMinutes(30).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(14).plusMinutes(35).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(14).plusMinutes(40).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(14).plusMinutes(45).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(14).plusMinutes(50).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(14).plusMinutes(55).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(15).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(15).plusMinutes(5).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(15).plusMinutes(10).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(15).plusMinutes(15).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(15).plusMinutes(20).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(15).plusMinutes(25).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(15).plusMinutes(30).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(15).plusMinutes(35).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(15).plusMinutes(40).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(15).plusMinutes(45).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(15).plusMinutes(50).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(15).plusMinutes(55).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(16).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(16).plusMinutes(5).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(16).plusMinutes(10).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(16).plusMinutes(15).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(16).plusMinutes(20).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(16).plusMinutes(25).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(16).plusMinutes(30).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(16).plusMinutes(35).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(16).plusMinutes(40).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(16).plusMinutes(45).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(16).plusMinutes(50).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(16).plusMinutes(55).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(17).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(17).plusMinutes(5).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(17).plusMinutes(10).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(17).plusMinutes(15).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(17).plusMinutes(20).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(17).plusMinutes(25).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(17).plusMinutes(30).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(17).plusMinutes(35).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(17).plusMinutes(40).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(17).plusMinutes(45).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(17).plusMinutes(50).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(17).plusMinutes(55).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(18).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(18).plusMinutes(5).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(18).plusMinutes(10).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(18).plusMinutes(15).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(18).plusMinutes(20).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(18).plusMinutes(25).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(18).plusMinutes(30).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(18).plusMinutes(35).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(18).plusMinutes(40).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(18).plusMinutes(45).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(18).plusMinutes(50).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(18).plusMinutes(55).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(19).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(19).plusMinutes(5).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(19).plusMinutes(10).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(19).plusMinutes(15).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(19).plusMinutes(20).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(19).plusMinutes(25).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(19).plusMinutes(30).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(19).plusMinutes(35).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(19).plusMinutes(40).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(19).plusMinutes(45).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(19).plusMinutes(50).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(19).plusMinutes(55).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(20).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(20).plusMinutes(5).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(20).plusMinutes(10).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(20).plusMinutes(15).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(20).plusMinutes(20).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(20).plusMinutes(25).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(20).plusMinutes(30).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(20).plusMinutes(35).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(20).plusMinutes(40).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(20).plusMinutes(45).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(20).plusMinutes(50).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(20).plusMinutes(55).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(21).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(21).plusMinutes(5).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(21).plusMinutes(10).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(21).plusMinutes(15).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(21).plusMinutes(20).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(21).plusMinutes(25).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(21).plusMinutes(30).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(21).plusMinutes(35).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(21).plusMinutes(40).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(21).plusMinutes(45).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(21).plusMinutes(50).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(21).plusMinutes(55).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(22).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(22).plusMinutes(5).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(22).plusMinutes(10).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(22).plusMinutes(15).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(22).plusMinutes(20).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(22).plusMinutes(25).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(22).plusMinutes(30).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(22).plusMinutes(35).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(22).plusMinutes(40).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(22).plusMinutes(45).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(22).plusMinutes(50).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(22).plusMinutes(55).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(23).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(23).plusMinutes(5).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(23).plusMinutes(10).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(23).plusMinutes(15).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(23).plusMinutes(20).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(23).plusMinutes(25).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(23).plusMinutes(30).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.484374, 51.903518).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(23).plusMinutes(35).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.479779, 51.904404).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(23).plusMinutes(40).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482914, 51.906769).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(23).plusMinutes(45).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.486156, 51.908570).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(23).plusMinutes(50).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.483362, 51.911897).toValue()),
                                            Values.createObject().put("timestamp", midnight.plusHours(23).plusMinutes(55).get(SECOND_OF_DAY)).put("value", new GeoJSONPoint(4.482669, 51.916436).toValue())
                                    )
                            )
                    );
                });
        ship1Asset.setId(UniqueIdentifierGenerator.generateId(ship1Asset.getName()));
        ship1Asset = assetStorageService.merge(ship1Asset);

        // ### Mobile app config ###

        persistenceService.doTransaction(entityManager ->
            entityManager.merge(new ConsoleAppConfig(
                realmCityTenant,
                "https://demo.openremote.io/mobile/?realm=smartcity&consoleProviders=geofence push storage",
                "https://demo.openremote.io/main/?realm=smartcity&consoleProviders=geofence push storage&consoleAutoEnable=true#!geofences",
                false,
                ConsoleAppConfig.MenuPosition.BOTTOM_LEFT,
                null,
                "#FAFAFA",
                "#AFAFAF",
                Arrays.asList(
                    new ConsoleAppConfig.AppLink("Map", "https://demo.openremote.io/mobile/?realm=smartcity&consoleProviders=geofence push storage&consoleAutoEnable=true#!geofences")
                )))
        );
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    protected static Value createWeatherApiAttributeLink (String assetId, String jsonParentName, String jsonName, String parameter){
        return Values.convertToValue(new AttributeLink(
                new AttributeRef(assetId, parameter),
                null,
                new ValueFilter[]{
                        new JsonPathFilter("$." + jsonParentName + "." + jsonName, true, false),
                }
        ), Values.JSON.writer()).get();
    }
}
