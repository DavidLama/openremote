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
package org.openremote.model.asset.agent;

import org.openremote.model.ValidationFailure;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.MetaItem;
import org.openremote.model.util.TextUtil;
import org.openremote.model.value.Values;

import java.util.Optional;
import java.util.function.UnaryOperator;

import static org.openremote.model.attribute.MetaItemType.PROTOCOL_CONFIGURATION;
import static org.openremote.model.attribute.MetaItem.isMetaNameEqualTo;

/**
 * Agent attributes can be named protocol configurations, defining a logical instance of a protocol.
 * <p>
 * A protocol configuration attribute must be marked
 * {@link MetaItemType#PROTOCOL_CONFIGURATION} and its
 * value must be valid RfC 2141 URN.
 * <p>
 * Protocol-specific settings and details are managed as {@link MetaItem} of the attribute.
 */
final public class ProtocolConfiguration {

    public enum ValidationFailureReason implements ValidationFailure.Reason {
        VALUE_NOT_A_VALID_PROTOCOL_URN
    }

    private ProtocolConfiguration() {
    }

    public static Attribute initProtocolConfiguration(Attribute attribute, String protocolName) throws IllegalArgumentException {
        if (attribute == null) {
            return null;
        }
        isValidProtocolNameOrThrow(protocolName);
        attribute.setReadOnly(true);
        attribute.setType(AttributeValueType.STRING);
        attribute.setValue(Values.create(protocolName));
        attribute.getMeta().add(new MetaItem(PROTOCOL_CONFIGURATION, Values.create(true)));
        return attribute;
    }

    public static UnaryOperator<Attribute> initProtocolConfiguration(String protocolName) throws IllegalArgumentException {
        return attribute -> initProtocolConfiguration(attribute, protocolName);
    }

    public static boolean isValidProtocolName(String protocolName) {
        return TextUtil.isValidURN(protocolName);
    }

    public static void isValidProtocolNameOrThrow(String protocolName) throws IllegalArgumentException {
        if (!isValidProtocolName(protocolName)) {
            throw new IllegalArgumentException("Protocol name must start with 'urn:' but is: " + protocolName);
        }
    }

    public static boolean isProtocolConfiguration(Attribute attribute) {
        return getProtocolName(attribute).isPresent()
            && attribute.getMetaStream().filter(isMetaNameEqualTo(PROTOCOL_CONFIGURATION))
            .findFirst()
            .map(metaItem -> metaItem.getValueAsBoolean().orElse(false))
            .orElse(false);
    }

    public static Optional<String> getProtocolName(Attribute attribute) {
        if (attribute == null) {
            return Optional.empty();
        }

        return attribute
            .getValueAsString()
            .map(name -> isValidProtocolName(name) ? name : null);
    }

    public static Attribute setProtocolName(Attribute attribute, String protocolName) throws IllegalArgumentException {
        if (attribute == null) {
            return null;
        }
        isValidProtocolNameOrThrow(protocolName);
        attribute.setValue(Values.create(protocolName));
        return attribute;
    }

    public static UnaryOperator<Attribute> setProtocolName(String protocolName) throws IllegalArgumentException {
        return attribute -> setProtocolName(attribute, protocolName);
    }
}
