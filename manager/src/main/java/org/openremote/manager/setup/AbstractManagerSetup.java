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
package org.openremote.manager.setup;

import org.openremote.agent.protocol.macro.MacroAction;
import org.openremote.agent.protocol.macro.MacroProtocol;
import org.openremote.agent.protocol.timer.TimerValue;
import org.openremote.model.Container;
import org.openremote.manager.asset.AssetProcessingService;
import org.openremote.manager.asset.AssetStorageService;
import org.openremote.manager.concurrent.ManagerExecutorService;
import org.openremote.manager.datapoint.AssetDatapointService;
import org.openremote.manager.persistence.ManagerPersistenceService;
import org.openremote.manager.predicted.AssetPredictedDatapointService;
import org.openremote.manager.rules.RulesetStorageService;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.model.asset.Asset;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.*;
import org.openremote.model.geo.GeoJSON;
import org.openremote.model.geo.GeoJSONPoint;
import org.openremote.model.value.Values;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.openremote.agent.protocol.macro.MacroProtocol.META_MACRO_ACTION_INDEX;
import static org.openremote.agent.protocol.timer.TimerConfiguration.initTimerConfiguration;
import static org.openremote.agent.protocol.timer.TimerProtocol.META_TIMER_VALUE_LINK;
import static org.openremote.model.Constants.UNITS_TEMPERATURE_CELSIUS;
import static org.openremote.model.asset.AssetType.*;
import static org.openremote.model.asset.agent.ProtocolConfiguration.initProtocolConfiguration;
import static org.openremote.model.attribute.AttributeValueType.*;
import static org.openremote.model.attribute.MetaItemType.*;

public abstract class AbstractManagerSetup implements Setup {

    final protected ManagerExecutorService executorService;
    final protected ManagerPersistenceService persistenceService;
    final protected ManagerIdentityService identityService;
    final protected AssetStorageService assetStorageService;
    final protected AssetProcessingService assetProcessingService;
    final protected AssetDatapointService assetDatapointService;
    final protected AssetPredictedDatapointService assetPredictedDatapointService;
    final protected RulesetStorageService rulesetStorageService;
    final protected SetupService setupService;

    public AbstractManagerSetup(Container container) {
        this.executorService = container.getService(ManagerExecutorService.class);
        this.persistenceService = container.getService(ManagerPersistenceService.class);
        this.identityService = container.getService(ManagerIdentityService.class);
        this.assetStorageService = container.getService(AssetStorageService.class);
        this.assetProcessingService = container.getService(AssetProcessingService.class);
        this.assetDatapointService = container.getService(AssetDatapointService.class);
        this.assetPredictedDatapointService = container.getService(AssetPredictedDatapointService.class);
        this.rulesetStorageService = container.getService(RulesetStorageService.class);
        this.setupService = container.getService(SetupService.class);
    }

    // ################################ Demo apartment with complex scenes ###################################

    protected Asset createDemoApartment(Asset parent, String name, GeoJSONPoint location) {
        Asset apartment = new Asset(name, RESIDENCE, parent);
        apartment.setAttributes(
            new Attribute("alarmEnabled", AttributeValueType.BOOLEAN)
                .setMeta(new Meta(
                    new MetaItem(LABEL, Values.create("Alarm enabled")),
                    new MetaItem(DESCRIPTION, Values.create("Send notifications when presence is detected")),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(SHOW_ON_DASHBOARD, Values.create(true))
                )),
            new Attribute("presenceDetected", AttributeValueType.BOOLEAN)
                .setMeta(new Meta(
                    new MetaItem(LABEL, Values.create("Presence detected")),
                    new MetaItem(DESCRIPTION, Values.create("Presence detected in any room")),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(READ_ONLY, Values.create(true)),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(STORE_DATA_POINTS, Values.create(true)),
                    new MetaItem(SHOW_ON_DASHBOARD, Values.create(true))
                )),
            new Attribute("vacationUntil", TIMESTAMP)
                .setMeta(new Meta(
                    new MetaItem(LABEL, Values.create("Vacation until")),
                    new MetaItem(DESCRIPTION, Values.create("Vacation mode enabled until")),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                    new MetaItem(RULE_STATE, Values.create(true))
                )),
            new Attribute("lastExecutedScene", AttributeValueType.STRING)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Last executed scene")),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(READ_ONLY, Values.create(true)),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(SHOW_ON_DASHBOARD, Values.create(true))
                ),
            new Attribute(AttributeType.LOCATION, location.toValue())
                    .setMeta(new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)))
            /* TODO Unused, can be removed? Port schedule prediction from DRL...
            new Attribute("autoSceneSchedule", AttributeValueType.BOOLEAN)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Automatic scene schedule")),
                    new MetaItem(DESCRIPTION, Values.create("Predict presence and automatically adjust scene schedule")),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                    new MetaItem(RULE_STATE, Values.create(true))
                ),
            new Attribute("lastDetectedScene", AttributeValueType.STRING)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Last detected scene by rules")),
                    new MetaItem(READ_ONLY, Values.create(true)),
                    new MetaItem(RULE_STATE, Values.create(true))
                )
            */
        );
        return apartment;
    }

    protected Asset createDemoApartmentRoom(Asset apartment, String name) {
        Asset room = new Asset(name, ROOM, apartment);
        return room;
    }

    protected void addDemoApartmentRoomMotionSensor(Asset room, boolean shouldBeLinked, Supplier<MetaItem[]> agentLinker) {
        room.addAttributes(
            new Attribute("motionSensor", NUMBER)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Motion sensor")),
                    new MetaItem(DESCRIPTION, Values.create("Greater than zero when motion is sensed")),
                    new MetaItem(READ_ONLY, Values.create(true)),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(STORE_DATA_POINTS)
                ).addMeta(shouldBeLinked ? agentLinker.get() : null),
            new Attribute("presenceDetected", AttributeValueType.BOOLEAN)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Presence detected")),
                    new MetaItem(DESCRIPTION, Values.create("Someone is moving or resting in the room")),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(READ_ONLY, Values.create(true)),
                    new MetaItem(STORE_DATA_POINTS, Values.create(true)),
                    new MetaItem(SHOW_ON_DASHBOARD, Values.create(true))
                ),
            new Attribute("firstPresenceDetected", TIMESTAMP)
                .setMeta(
                    new MetaItem(LABEL, Values.create("First time movement was detected")),
                    new MetaItem(READ_ONLY, Values.create(true)),
                    new MetaItem(RULE_STATE, Values.create(true))
                ),
            new Attribute("lastPresenceDetected", TIMESTAMP)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Last time movement was detected")),
                    new MetaItem(READ_ONLY, Values.create(true)),
                    new MetaItem(RULE_STATE, Values.create(true))
                )
        );
    }

    protected void addDemoApartmentRoomCO2Sensor(Asset room, boolean shouldBeLinked, Supplier<MetaItem[]> agentLinker) {
        room.addAttributes(
            new Attribute("co2Level", CO2)
                .setMeta(
                    new MetaItem(LABEL, Values.create("CO2 level")),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(RULE_EVENT, Values.create(true)),
                    new MetaItem(RULE_EVENT_EXPIRES, Values.create("45m")),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(READ_ONLY, Values.create(true)),
                    new MetaItem(SHOW_ON_DASHBOARD, Values.create(true)),
                    new MetaItem(FORMAT, Values.create("%4d ppm")),
                    new MetaItem(STORE_DATA_POINTS)
                ).addMeta(shouldBeLinked ? agentLinker.get() : null)
        );
    }

    protected void addDemoApartmentRoomHumiditySensor(Asset room, boolean shouldBeLinked, Supplier<MetaItem[]> agentLinker) {
        room.addAttributes(
            new Attribute("humidity", HUMIDITY)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Humidity")),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(RULE_EVENT, Values.create(true)),
                    new MetaItem(RULE_EVENT_EXPIRES, Values.create("45m")),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(READ_ONLY, Values.create(true)),
                    new MetaItem(SHOW_ON_DASHBOARD, Values.create(true)),
                    new MetaItem(FORMAT, Values.create("%3d %%")),
                    new MetaItem(STORE_DATA_POINTS)
                ).addMeta(shouldBeLinked ? agentLinker.get() : null)
        );
    }

    protected void addDemoApartmentRoomThermometer(Asset room,
                                                   boolean shouldBeLinked,
                                                   Supplier<MetaItem[]> agentLinker) {
        room.addAttributes(
            new Attribute("currentTemperature", TEMPERATURE)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Current temperature")),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(READ_ONLY, Values.create(true)),
                    new MetaItem(SHOW_ON_DASHBOARD, Values.create(true)),
                    new MetaItem(UNIT_TYPE.withInitialValue(UNITS_TEMPERATURE_CELSIUS)),
                    new MetaItem(FORMAT, Values.create("%0.1f° C")),
                    new MetaItem(STORE_DATA_POINTS)
                ).addMeta(shouldBeLinked ? agentLinker.get() : null)
        );
    }

    protected void addDemoApartmentTemperatureControl(Asset room,
                                                      boolean shouldBeLinked,
                                                      Supplier<MetaItem[]> agentLinker) {
        room.addAttributes(
            new Attribute("targetTemperature", TEMPERATURE)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Target temperature")),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                    new MetaItem(SHOW_ON_DASHBOARD, Values.create(true)),
                    new MetaItem(UNIT_TYPE.withInitialValue(UNITS_TEMPERATURE_CELSIUS)),
                    new MetaItem(FORMAT, Values.create("%0f° C")),
                    new MetaItem(STORE_DATA_POINTS)
                ).addMeta(shouldBeLinked ? agentLinker.get() : null)
        );
    }

    protected void addDemoApartmentSmartSwitch(Asset room,
                                               String switchName,
                                               boolean shouldBeLinked,
                                               // Integer represents attribute:
                                               // 0 = Mode
                                               // 1 = Time
                                               // 2 = StartTime
                                               // 3 = StopTime
                                               // 4 = Enabled
                                               Function<Integer, MetaItem[]> agentLinker) {

        room.addAttributes(
            // Mode
            new Attribute("smartSwitchMode" + switchName, STRING)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Smart Switch mode " + switchName)),
                    new MetaItem(DESCRIPTION, Values.create("NOW_ON (default when empty) or ON_AT or READY_AT")),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(RULE_EVENT, Values.create(true)),
                    new MetaItem(RULE_EVENT_EXPIRES, Values.create("48h"))
                ).addMeta(shouldBeLinked ? agentLinker.apply(0) : null),
            // Time
            new Attribute("smartSwitchBeginEnd" + switchName, TIMESTAMP)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Smart Switch begin/end cycle " + switchName)),
                    new MetaItem(DESCRIPTION, Values.create("User-provided begin/end time of appliance cycle")),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                    new MetaItem(RULE_STATE, Values.create(true))
                ).addMeta(shouldBeLinked ? agentLinker.apply(1) : null),
            // StartTime
            new Attribute("smartSwitchStartTime" + switchName, TIMESTAMP)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Smart Switch actuator earliest start time " + switchName)),
                    new MetaItem(DESCRIPTION, Values.create("Earliest computed start time sent to actuator")),
                    new MetaItem(READ_ONLY, Values.create(true)),
                    new MetaItem(UNIT_TYPE, Values.create("SECONDS")),
                    new MetaItem(RULE_STATE, Values.create(true))
                ).addMeta(shouldBeLinked ? agentLinker.apply(2) : null),
            // StopTime
            new Attribute("smartSwitchStopTime" + switchName, TIMESTAMP)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Smart Switch actuator latest stop time " + switchName)),
                    new MetaItem(DESCRIPTION, Values.create("Latest computed stop time sent to actuator")),
                    new MetaItem(READ_ONLY, Values.create(true)),
                    new MetaItem(UNIT_TYPE, Values.create("SECONDS")),
                    new MetaItem(RULE_STATE, Values.create(true))
                ).addMeta(shouldBeLinked ? agentLinker.apply(3) : null),
            // Enabled
            new Attribute("smartSwitchEnabled" + switchName, NUMBER)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Smart Switch actuator enabled " + switchName)),
                    new MetaItem(DESCRIPTION, Values.create("1 if actuator only provides power at ideal time between start/stop")),
                    new MetaItem(READ_ONLY, Values.create(true)),
                    new MetaItem(RULE_STATE, Values.create(true))
                ).addMeta(shouldBeLinked ? agentLinker.apply(4) : null)
        );
    }

    protected void addDemoApartmentVentilation(Asset apartment,
                                               boolean shouldBeLinked,
                                               Supplier<MetaItem[]> agentLinker) {
        apartment.addAttributes(
            new Attribute("ventilationLevel", NUMBER)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Ventilation level")),
                    new MetaItem(RANGE_MIN, Values.create(0)),
                    new MetaItem(RANGE_MAX, Values.create(255)),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                    new MetaItem(FORMAT, Values.create("%d")),
                    new MetaItem(STORE_DATA_POINTS)
                ).addMeta(shouldBeLinked ? agentLinker.get() : null),
            new Attribute("ventilationAuto", BOOLEAN)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Ventilation auto")),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true))
                )
        );
    }

    public static class Scene {

        final String attributeName;
        final String attributeLabel;
        final String internalName;
        final String startTime;
        final boolean alarmEnabled;
        final double targetTemperature;

        public Scene(String attributeName,
                     String attributeLabel,
                     String internalName,
                     String startTime,
                     boolean alarmEnabled,
                     double targetTemperature) {
            this.attributeName = attributeName;
            this.attributeLabel = attributeLabel;
            this.internalName = internalName;
            this.startTime = startTime;
            this.alarmEnabled = alarmEnabled;
            this.targetTemperature = targetTemperature;
        }

        Attribute createMacroAttribute(Asset apartment, Asset... rooms) {
            Attribute attribute = initProtocolConfiguration(new Attribute(attributeName), MacroProtocol.PROTOCOL_NAME)
                .addMeta(new MetaItem(LABEL, Values.create(attributeLabel)));
            attribute.getMeta().add(
                new MacroAction(new AttributeState(new AttributeRef(apartment.getId(), "alarmEnabled"), Values.create(alarmEnabled))).toMetaItem()
            );
            for (Asset room : rooms) {
                if (room.hasAttribute("targetTemperature")) {
                    attribute.getMeta().add(
                        new MacroAction(new AttributeState(new AttributeRef(room.getId(), "targetTemperature"), Values.create(targetTemperature))).toMetaItem()
                    );
                }
            }
            attribute.getMeta().add(
                new MacroAction(new AttributeState(new AttributeRef(apartment.getId(), "lastExecutedScene"), Values.create(internalName))).toMetaItem()
            );
            return attribute;
        }

        Attribute[] createTimerAttributes(Asset apartment) {
            List<Attribute> attributes = new ArrayList<>();
            for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
                // "MONDAY" => "Monday"
                String dayOfWeekLabel = dayOfWeek.name().substring(0, 1) + dayOfWeek.name().substring(1).toLowerCase(Locale.ROOT);
                // "0 0 7 ? *" => "0 0 7 ? * MON *"
                String timePattern = startTime + " " + dayOfWeek.name().substring(0, 3).toUpperCase(Locale.ROOT) + " *";
                attributes.add(
                    initTimerConfiguration(new Attribute(attributeName + dayOfWeek.name()), timePattern,
                        new AttributeState(apartment.getId(), attributeName, Values.create("REQUEST_START")))
                        .addMeta(new MetaItem(LABEL, Values.create(attributeLabel + " trigger " + dayOfWeekLabel)))
                );
            }
            return attributes.toArray(new Attribute[attributes.size()]);
        }
    }

    public static Asset createDemoApartmentScenes(AssetStorageService assetStorageService, Asset apartment, Scene[] scenes, Asset... rooms) {

        Asset agent = new Asset("Scene Agent", AGENT, apartment);
        for (Scene scene : scenes) {
            agent.addAttributes(scene.createMacroAttribute(apartment, rooms));
        }
        for (Scene scene : scenes) {
            agent.addAttributes(scene.createTimerAttributes(apartment));
        }

        addDemoApartmentSceneEnableDisableTimer(apartment, agent, scenes);
        agent = assetStorageService.merge(agent);
        linkDemoApartmentWithSceneAgent(apartment, agent, scenes);
        apartment = assetStorageService.merge(apartment);
        return agent;
    }

    protected static void addDemoApartmentSceneEnableDisableTimer(Asset apartment, Asset agent, Scene[] scenes) {
        Attribute enableAllMacro = initProtocolConfiguration(new Attribute("enableSceneTimer"), MacroProtocol.PROTOCOL_NAME)
            .addMeta(new MetaItem(LABEL, Values.create("Enable scene timer")));
        for (Scene scene : scenes) {
            for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
                String sceneAttributeName = scene.attributeName + "Enabled" + dayOfWeek;
                enableAllMacro.getMeta().add(
                    new MacroAction(new AttributeState(new AttributeRef(apartment.getId(), sceneAttributeName), Values.create(true))).toMetaItem()
                );
            }
        }
        enableAllMacro.getMeta().add(
            new MacroAction(new AttributeState(new AttributeRef(apartment.getId(), "sceneTimerEnabled"), Values.create(true))).toMetaItem()
        );
        agent.addAttributes(enableAllMacro);

        Attribute disableAllMacro = initProtocolConfiguration(new Attribute("disableSceneTimer"), MacroProtocol.PROTOCOL_NAME)
            .addMeta(new MetaItem(LABEL, Values.create("Disable scene timer")));
        for (Scene scene : scenes) {
            for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
                String sceneAttributeName = scene.attributeName + "Enabled" + dayOfWeek;
                disableAllMacro.getMeta().add(
                    new MacroAction(new AttributeState(new AttributeRef(apartment.getId(), sceneAttributeName), Values.create(false))).toMetaItem()
                );
            }
        }
        disableAllMacro.getMeta().add(
            new MacroAction(new AttributeState(new AttributeRef(apartment.getId(), "sceneTimerEnabled"), Values.create(false))).toMetaItem()
        );
        agent.addAttributes(disableAllMacro);
    }

    protected static void linkDemoApartmentWithSceneAgent(Asset apartment, Asset agent, Scene[] scenes) {
        for (Scene scene : scenes) {
            apartment.addAttributes(
                new Attribute(scene.attributeName, AttributeValueType.STRING, Values.create(AttributeExecuteStatus.READY.name()))
                    .setMeta(
                        new MetaItem(LABEL, Values.create(scene.attributeLabel)),
                        new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                        new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                        new MetaItem(EXECUTABLE, Values.create(true)),
                        new MetaItem(AGENT_LINK, new AttributeRef(agent.getId(), scene.attributeName).toArrayValue())
                    ),
                new Attribute(scene.attributeName + "AlarmEnabled", AttributeValueType.BOOLEAN)
                    .setMeta(
                        new MetaItem(LABEL, Values.create(scene.attributeLabel + " alarm enabled")),
                        new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                        new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                        new MetaItem(META_MACRO_ACTION_INDEX, Values.create(0)),
                        new MetaItem(AGENT_LINK, new AttributeRef(agent.getId(), scene.attributeName).toArrayValue())
                    ),
                new Attribute(scene.attributeName + "TargetTemperature", AttributeValueType.NUMBER)
                    .setMeta(
                        new MetaItem(LABEL, Values.create(scene.attributeLabel + " target temperature")),
                        new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                        new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                        new MetaItem(META_MACRO_ACTION_INDEX, Values.create(1)),
                        new MetaItem(AGENT_LINK, new AttributeRef(agent.getId(), scene.attributeName).toArrayValue())
                    )
            );
            for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
                // "MONDAY" => "Monday"
                String dayOfWeekLabel = dayOfWeek.name().substring(0, 1) + dayOfWeek.name().substring(1).toLowerCase(Locale.ROOT);
                apartment.addAttributes(
                    new Attribute(scene.attributeName + "Time" + dayOfWeek.name(), AttributeValueType.STRING)
                        .setMeta(
                            new MetaItem(LABEL, Values.create(scene.attributeLabel + " time " + dayOfWeekLabel)),
                            new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                            new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                            new MetaItem(RULE_STATE, Values.create(true)),
                            new MetaItem(META_TIMER_VALUE_LINK, Values.create(TimerValue.TIME.toString())),
                            new MetaItem(AGENT_LINK, new AttributeRef(agent.getId(), scene.attributeName + dayOfWeek.name()).toArrayValue())
                        ),
                    new Attribute(scene.attributeName + "Enabled" + dayOfWeek.name(), AttributeValueType.BOOLEAN)
                        .setMeta(
                            new MetaItem(LABEL, Values.create(scene.attributeLabel + " enabled " + dayOfWeekLabel)),
                            new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                            new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                            new MetaItem(META_TIMER_VALUE_LINK, Values.create(TimerValue.ENABLED.toString())),
                            new MetaItem(AGENT_LINK, new AttributeRef(agent.getId(), scene.attributeName + dayOfWeek.name()).toArrayValue())
                        )
                );
            }
        }
        apartment.addAttributes(
            new Attribute("sceneTimerEnabled", AttributeValueType.BOOLEAN, Values.create(true)) // The scene timer is enabled when the timer protocol starts
                .setMeta(
                    new MetaItem(LABEL, Values.create("Scene timer enabled")),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(SHOW_ON_DASHBOARD, Values.create(true)),
                    new MetaItem(READ_ONLY, Values.create(true))
                ),
            new Attribute("enableSceneTimer", AttributeValueType.STRING)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Enable scene timer")),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                    new MetaItem(EXECUTABLE, Values.create(true)),
                    new MetaItem(AGENT_LINK, new AttributeRef(agent.getId(), "enableSceneTimer").toArrayValue())
                ),
            new Attribute("disableSceneTimer", AttributeValueType.STRING)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Disable scene timer")),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                    new MetaItem(EXECUTABLE, Values.create(true)),
                    new MetaItem(AGENT_LINK, new AttributeRef(agent.getId(), "disableSceneTimer").toArrayValue())
                )
        );
    }

    protected Asset createDemoPeopleCounterAsset(String name, Asset area, GeoJSON location, Supplier<MetaItem[]> agentLinker) {
        Asset peopleCounterAsset = new Asset(name, PEOPLE_COUNTER, area).addAttributes(
            new Attribute(AttributeType.LOCATION, location.toValue())
        );
        peopleCounterAsset.getAttribute("peopleCountIn").ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                new MetaItem(RULE_STATE),
                new MetaItem(STORE_DATA_POINTS)
            );
            if (agentLinker != null) {
                assetAttribute.addMeta(agentLinker.get());
            }
        });
        peopleCounterAsset.getAttribute("peopleCountOut").ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                new MetaItem(RULE_STATE),
                new MetaItem(STORE_DATA_POINTS)
            );
            if (agentLinker != null) {
                assetAttribute.addMeta(agentLinker.get());
            }
        });
        peopleCounterAsset.getAttribute("peopleCountInMinute").ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                new MetaItem(RULE_STATE),
                new MetaItem(STORE_DATA_POINTS)
            );
            if (agentLinker != null) {
                assetAttribute.addMeta(agentLinker.get());
            }
        });
        peopleCounterAsset.getAttribute("peopleCountOutMinute").ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                new MetaItem(RULE_STATE),
                new MetaItem(STORE_DATA_POINTS)
            );
            if (agentLinker != null) {
                assetAttribute.addMeta(agentLinker.get());
            }
        });
        peopleCounterAsset.getAttribute("peopleCountTotal").ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                new MetaItem(RULE_STATE),
                new MetaItem(STORE_DATA_POINTS)
            );
            if (agentLinker != null) {
                assetAttribute.addMeta(agentLinker.get());
            }
        });

        return peopleCounterAsset;
    }

    protected Asset createDemoMicrophoneAsset(String name, Asset area, GeoJSON location, Supplier<MetaItem[]> agentLinker) {
        Asset microphoneAsset = new Asset(name, MICROPHONE, area).addAttributes(
            new Attribute(AttributeType.LOCATION, location.toValue())
        );
        microphoneAsset.getAttribute("microphoneLevel").ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                new MetaItem(RULE_STATE),
                new MetaItem(STORE_DATA_POINTS)
            );
            if (agentLinker != null) {
                assetAttribute.addMeta(agentLinker.get());
            }
        });


        return microphoneAsset;
    }

    protected Asset createDemoSoundEventAsset(String name, Asset area, GeoJSON location, Supplier<MetaItem[]> agentLinker) {
        Asset soundEventAsset = new Asset(name, SOUND_EVENT, area).addAttributes(
            new Attribute(AttributeType.LOCATION, location.toValue())
        );
        soundEventAsset.getAttribute("lastAggressionEvent").ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                new MetaItem(RULE_STATE),
                new MetaItem(STORE_DATA_POINTS)
            );
            if (agentLinker != null) {
                assetAttribute.addMeta(agentLinker.get());
            }
        });
        soundEventAsset.getAttribute("lastGunshotEvent").ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                new MetaItem(RULE_STATE),
                new MetaItem(STORE_DATA_POINTS)
            );
            if (agentLinker != null) {
                assetAttribute.addMeta(agentLinker.get());
            }
        });
        soundEventAsset.getAttribute("lastBreakingGlassEvent").ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                new MetaItem(RULE_STATE),
                new MetaItem(STORE_DATA_POINTS)
            );
            if (agentLinker != null) {
                assetAttribute.addMeta(agentLinker.get());
            }
        });
        soundEventAsset.getAttribute("lastIntensityEvent").ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                new MetaItem(RULE_STATE),
                new MetaItem(STORE_DATA_POINTS)
            );
            if (agentLinker != null) {
                assetAttribute.addMeta(agentLinker.get());
            }
        });
        soundEventAsset.getAttribute("lastEvent").ifPresent(assetAttribute -> {
            assetAttribute.addMeta(
                new MetaItem(RULE_STATE),
                new MetaItem(STORE_DATA_POINTS)
            );
            if (agentLinker != null) {
                assetAttribute.addMeta(agentLinker.get());
            }
        });

        return soundEventAsset;
    }

    protected Asset createDemoEnvironmentAsset(String name, Asset area, GeoJSON location, Supplier<MetaItem[]> agentLinker) {
        Asset environmentAsset = new Asset(name, ENVIRONMENT_SENSOR, area).addAttributes(
            new Attribute(AttributeType.LOCATION, location.toValue())
        );
        environmentAsset.getAttribute("temperature").ifPresent(assetAttribute -> assetAttribute
            .addMeta(agentLinker.get()));
        environmentAsset.getAttribute("nO2").ifPresent(assetAttribute -> assetAttribute
            .addMeta(agentLinker.get()));
        environmentAsset.getAttribute("relHumidity").ifPresent(assetAttribute -> assetAttribute
            .addMeta(agentLinker.get()));
        environmentAsset.getAttribute("particlesPM1").ifPresent(assetAttribute -> assetAttribute
            .addMeta(agentLinker.get()));
        environmentAsset.getAttribute("particlesPM2_5").ifPresent(assetAttribute -> assetAttribute
            .addMeta(agentLinker.get()));
        environmentAsset.getAttribute("particlesPM10").ifPresent(assetAttribute -> assetAttribute
            .addMeta(agentLinker.get()));

        return environmentAsset;
    }

    protected Asset createDemoLightAsset(String name, Asset area, GeoJSON location) {
        Asset lightAsset = new Asset(name, LIGHT, area).addAttributes(
            new Attribute(AttributeType.LOCATION, location.toValue())
                .addMeta(SHOW_ON_DASHBOARD)
        );
        lightAsset.getAttribute("lightStatus").ifPresent(assetAttribute -> assetAttribute.addMeta(
            new MetaItem(RULE_STATE),
            new MetaItem(STORE_DATA_POINTS)
        ));
        lightAsset.getAttribute("lightDimLevel").ifPresent(assetAttribute -> assetAttribute.addMeta(
            new MetaItem(RULE_STATE),
            new MetaItem(STORE_DATA_POINTS)
        ));
        lightAsset.getAttribute("colorRGBW").ifPresent(assetAttribute -> assetAttribute.addMeta(
            new MetaItem(RULE_STATE),
            new MetaItem(STORE_DATA_POINTS)
        ));
        lightAsset.getAttribute("groupNumber").ifPresent(assetAttribute -> assetAttribute.addMeta(
            new MetaItem(RULE_STATE),
            new MetaItem(STORE_DATA_POINTS)
        ));
        lightAsset.getAttribute("scenario").ifPresent(assetAttribute -> assetAttribute.addMeta(
            new MetaItem(RULE_STATE)
        ));

        return lightAsset;
    }

    protected Asset createDemoLightControllerAsset(String name, Asset area, GeoJSON location) {
        Asset lightAsset = new Asset(name, LIGHT_CONTROLLER, area).addAttributes(
            new Attribute(AttributeType.LOCATION, location.toValue())
        );
        lightAsset.getAttribute("lightAllStatus").ifPresent(assetAttribute -> assetAttribute.addMeta(
            new MetaItem(RULE_STATE),
            new MetaItem(STORE_DATA_POINTS)
        ));
        lightAsset.getAttribute("lightAllDimLevel").ifPresent(assetAttribute -> assetAttribute.addMeta(
            new MetaItem(RULE_STATE),
            new MetaItem(STORE_DATA_POINTS)
        ));
        lightAsset.getAttribute("colorAllRGBW").ifPresent(assetAttribute -> assetAttribute.addMeta(
            new MetaItem(RULE_STATE),
            new MetaItem(STORE_DATA_POINTS)
        ));
        lightAsset.getAttribute("scenario").ifPresent(assetAttribute -> assetAttribute.addMeta(
            new MetaItem(RULE_STATE)
        ));

        return lightAsset;
    }

    protected Asset createDemoElectricityStorageAsset(String name, Asset area, GeoJSON location) {
        Asset electricityStorageAsset = new Asset(name, ELECTRICITY_STORAGE, area).addAttributes(
            new Attribute(AttributeType.LOCATION, location.toValue())
        );

    return electricityStorageAsset;
    }

    protected Asset createDemoElectricityProducerAsset(String name, Asset area, GeoJSON location) {
        Asset electricityProducerAsset = new Asset(name, ELECTRICITY_PRODUCER, area).addAttributes(
            new Attribute(AttributeType.LOCATION, location.toValue())
        );

    return electricityProducerAsset;
    }

    protected Asset createDemoElectricityConsumerAsset(String name, Asset area, GeoJSON location) {
        Asset electricityConsumerAsset = new Asset(name, ELECTRICITY_CONSUMER, area).addAttributes(
            new Attribute(AttributeType.LOCATION, location.toValue())
        );

    return electricityConsumerAsset;
    }

    protected Asset createDemoElectricityChargerAsset(String name, Asset area, GeoJSON location) {
        Asset electricityConsumerAsset = new Asset(name, ELECTRICITY_CHARGER, area).addAttributes(
                new Attribute(AttributeType.LOCATION, location.toValue())
        );

        return electricityConsumerAsset;
    }

    protected Asset createDemoGroundwaterAsset(String name, Asset area, GeoJSON location) {
        Asset groundwaterAsset = new Asset(name, GROUNDWATER, area).addAttributes(
            new Attribute(AttributeType.LOCATION, location.toValue())
        );

    return groundwaterAsset;
    }

    protected Asset createDemoParkingAsset(String name, Asset area, GeoJSON location) {
        Asset parkingAsset = new Asset(name, PARKING, area).addAttributes(
            new Attribute(AttributeType.LOCATION, location.toValue())
        );

    return parkingAsset;
    }

    protected Asset createDemoShipAsset(String name, Asset area, GeoJSON location) {
        Asset shipAsset = new Asset(name, SHIP, area).addAttributes(
                new AssetAttribute(AttributeType.LOCATION, location.toValue())
        );

        return shipAsset;
    }
}
