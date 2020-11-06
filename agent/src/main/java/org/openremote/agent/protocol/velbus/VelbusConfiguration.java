/*
 * Copyright 2017, OpenRemote Inc.
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
package org.openremote.agent.protocol.velbus;

import org.openremote.model.AbstractValueHolder;
import org.openremote.model.attribute.AttributeValidationFailure;
import org.openremote.model.ValueHolder;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.AttributeValidationResult;
import org.openremote.model.attribute.MetaItem;
import org.openremote.model.value.ValueType;

import static org.openremote.agent.protocol.velbus.AbstractVelbusProtocol.*;
import static org.openremote.model.attribute.MetaItem.isMetaNameEqualTo;
import static org.openremote.model.util.TextUtil.isNullOrEmpty;

public final class VelbusConfiguration {

    private VelbusConfiguration() {}

    public static int getTimeInjectionIntervalSeconds(Attribute protocolConfiguration) {
        return protocolConfiguration
                .getMetaItem(META_VELBUS_TIME_INJECTION_INTERVAL_SECONDS)
                .flatMap(AbstractValueHolder::getValueAsInteger)
                .orElse(DEFAULT_TIME_INJECTION_INTERVAL_SECONDS);
    }

    public static int getVelbusDeviceAddress(Attribute assetAttribute) {
        return assetAttribute
            .getMetaItem(META_VELBUS_DEVICE_ADDRESS)
            .flatMap(AbstractValueHolder::getValueAsInteger)
            .orElse(0);
    }

    public static String getVelbusDevicePropertyLink(Attribute assetAttribute) {
        return assetAttribute
            .getMetaItem(META_VELBUS_DEVICE_VALUE_LINK)
            .flatMap(AbstractValueHolder::getValueAsString)
            .map(String::toUpperCase)
            .orElse("");
    }

    public static boolean validateSocketConfiguration(Attribute attribute, AttributeValidationResult result) {
        boolean failure = false;

        if (!isSocketConfiguration(attribute)) {
            failure = true;
            if (result != null) {
                result.addAttributeFailure(
                    new AttributeValidationFailure(
                        ValueHolder.ValueFailureReason.VALUE_MISMATCH,
                        VelbusTcpProtocol.PROTOCOL_NAME));
            }
        }

        boolean hostFound = false;
        boolean portFound = false;

        if (attribute.getMeta() != null && !attribute.getMeta().isEmpty()) {
            for (int i = 0; i < attribute.getMeta().size(); i++) {
                MetaItem metaItem = attribute.getMeta().get(i);
                if (isMetaNameEqualTo(metaItem, VelbusTcpProtocol.META_VELBUS_HOST)) {
                    hostFound = true;
                    if (isNullOrEmpty(metaItem.getValueAsString().orElse(null))) {
                        failure = true;
                        if (result == null) {
                            break;
                        }
                        result.addMetaFailure(i,
                            new AttributeValidationFailure(MetaItem.MetaItemFailureReason.META_ITEM_VALUE_IS_REQUIRED, ValueType.STRING.name()));
                    }
                } else if (isMetaNameEqualTo(metaItem, VelbusTcpProtocol.META_VELBUS_PORT)) {
                    portFound = true;
                    int port = metaItem.getValueAsInteger().orElse(0);
                    if (port <= 0 || port > 65536) {
                        failure = true;
                        if (result == null) {
                            break;
                        }
                        result.addMetaFailure(i,
                            new AttributeValidationFailure(ValueHolder.ValueFailureReason.VALUE_MISMATCH, "1-65536"));
                    }
                }
            }
        }

        if (!hostFound) {
            failure = true;
            if (result != null) {
                result.addMetaFailure(
                    new AttributeValidationFailure(
                        MetaItem.MetaItemFailureReason.META_ITEM_MISSING,
                        VelbusTcpProtocol.META_VELBUS_HOST));
            }
        }
        if (!portFound) {
            failure = true;
            if (result != null) {
                result.addMetaFailure(
                    new AttributeValidationFailure(
                        MetaItem.MetaItemFailureReason.META_ITEM_MISSING,
                        VelbusTcpProtocol.META_VELBUS_PORT));
            }
        }

        return !failure;
    }

    public static boolean validateSerialConfiguration(Attribute protocolConfiguration, AttributeValidationResult result) {
        boolean failure = false;

        if (!isSerialConfiguration(protocolConfiguration)) {
            failure = true;
            if (result != null) {
                result.addAttributeFailure(
                    new AttributeValidationFailure(
                        ValueHolder.ValueFailureReason.VALUE_MISMATCH,
                        VelbusSerialProtocol.PROTOCOL_NAME));
            }
        }

        boolean portFound = false;

        if (protocolConfiguration.getMeta() != null && !protocolConfiguration.getMeta().isEmpty()) {
            for (int i = 0; i < protocolConfiguration.getMeta().size(); i++) {
                MetaItem metaItem = protocolConfiguration.getMeta().get(i);
                if (isMetaNameEqualTo(metaItem, VelbusSerialProtocol.META_VELBUS_SERIAL_PORT)) {
                    portFound = true;
                    if (isNullOrEmpty(metaItem.getValueAsString().orElse(null))) {
                        failure = true;
                        if (result == null) {
                            break;
                        }

                        result.addMetaFailure(i,
                            new AttributeValidationFailure(MetaItem.MetaItemFailureReason.META_ITEM_VALUE_IS_REQUIRED, ValueType.STRING.name()));
                    }
                } else if (isMetaNameEqualTo(metaItem, VelbusSerialProtocol.META_VELBUS_SERIAL_BAUDRATE)) {
                    int baudrate = metaItem.getValueAsInteger().orElse(0);
                    if (baudrate <= 0) {
                        failure = true;
                        if (result == null) {
                            break;
                        }

                        result.addMetaFailure(i,
                            new AttributeValidationFailure(ValueHolder.ValueFailureReason.VALUE_MISMATCH));
                    }
                }
            }
        }

        if (!portFound) {
            failure = true;
            if (result != null) {
                result.addMetaFailure(
                    new AttributeValidationFailure(MetaItem.MetaItemFailureReason.META_ITEM_MISSING, VelbusTcpProtocol.META_VELBUS_PORT)
                );
            }
        }

        return !failure;
    }

    public static boolean isValidSerialConfiguration(Attribute protocolConfiguration) {
        return validateSerialConfiguration(protocolConfiguration, null);
    }

    public static boolean isValidSocketConfiguration(Attribute protocolConfiguration) {
        return validateSocketConfiguration(protocolConfiguration, null);
    }

    public static boolean isSocketConfiguration(Attribute attribute) {
        return attribute != null && attribute.getValueAsString().map(value -> value.equals(VelbusTcpProtocol.PROTOCOL_NAME)).orElse(false);
    }

    public static boolean isSerialConfiguration(Attribute attribute) {
        return attribute != null && attribute.getValueAsString().map(value -> value.equals(VelbusSerialProtocol.PROTOCOL_NAME)).orElse(false);
    }
}
