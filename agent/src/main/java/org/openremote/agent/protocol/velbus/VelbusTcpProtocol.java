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

import io.netty.channel.ChannelHandler;
import org.openremote.agent.protocol.io.AbstractNettyIoClient;
import org.openremote.agent.protocol.io.IoClient;
import org.openremote.agent.protocol.tcp.TcpIoClient;
import org.openremote.model.AbstractValueHolder;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.AttributeValidationResult;
import org.openremote.model.attribute.MetaItem;
import org.openremote.model.attribute.MetaItemDescriptor;
import org.openremote.model.attribute.MetaItemDescriptorImpl;
import org.openremote.model.util.TextUtil;
import org.openremote.model.value.ValueType;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.openremote.model.util.TextUtil.REGEXP_PATTERN_INTEGER_POSITIVE_NON_ZERO;

public class VelbusTcpProtocol extends AbstractVelbusProtocol {

    public static final String PROTOCOL_NAME = PROTOCOL_BASE_NAME + "Tcp";
    public static final String PROTOCOL_DISPLAY_NAME = "VELBUS TCP";
    public static final String META_VELBUS_HOST = PROTOCOL_NAME + ":host";
    public static final String META_VELBUS_PORT = PROTOCOL_NAME + ":port";
    public static final List<MetaItemDescriptorImpl> META_ITEM_DESCRIPTORS = Arrays.asList(
        new MetaItemDescriptorImpl(
                META_VELBUS_HOST,
            ValueType.STRING,
            true,
            null,
            null,
            1,
            null,
            false,
                null, null, null),
        new MetaItemDescriptorImpl(
                META_VELBUS_PORT,
            ValueType.NUMBER,
            true,
            REGEXP_PATTERN_INTEGER_POSITIVE_NON_ZERO,
            MetaItemDescriptor.PatternFailure.INTEGER_POSITIVE_NON_ZERO.name(),
            1,
            null,
            false,
                null, null, null)
    );

    @Override
    public String getProtocolName() {
        return PROTOCOL_NAME;
    }

    @Override
    public String getProtocolDisplayName() {
        return PROTOCOL_DISPLAY_NAME;
    }

    @Override
    protected List<MetaItemDescriptor> getProtocolConfigurationMetaItemDescriptors() {
        List<MetaItemDescriptor> descriptors = super.getProtocolConfigurationMetaItemDescriptors();
        descriptors.addAll(META_ITEM_DESCRIPTORS);
        return descriptors;
    }

    @Override
    public AttributeValidationResult validateProtocolConfiguration(Attribute<?> protocolConfiguration) {
        AttributeValidationResult result = super.validateProtocolConfiguration(protocolConfiguration);
        if (result.isValid()) {
            VelbusConfiguration.validateSocketConfiguration(protocolConfiguration, result);
        }
        return result;
    }

    @Override
    public Attribute<?> getProtocolConfigurationTemplate() {
        return super.getProtocolConfigurationTemplate()
            .addMeta(
                new MetaItem<>(META_VELBUS_HOST, null),
                new MetaItem<>(META_VELBUS_PORT, null)
            );
    }

    @Override
    protected IoClient<VelbusPacket> createIoClient(Attribute<?> protocolConfiguration) throws RuntimeException {

        // Extract IP and Host
        String host = protocolConfiguration.getMetaItem(META_VELBUS_HOST).flatMap(AbstractValueHolder::getValueAsString).orElse(null);
        Integer port = protocolConfiguration.getMetaItem(META_VELBUS_PORT).flatMap(AbstractValueHolder::getValueAsInteger).orElse(null);

        TextUtil.requireNonNullAndNonEmpty(host, "Host cannot be null or empty");
        Objects.requireNonNull(port, "Port cannot be null");
        TcpIoClient<VelbusPacket> client = new TcpIoClient<>(host, port, executorService);
        client.setEncoderDecoderProvider(
            () -> new ChannelHandler[] {
                new VelbusPacketEncoder(),
                new VelbusPacketDecoder(),
                new AbstractNettyIoClient.MessageToMessageDecoder<>(VelbusPacket.class, client)
            }
        );
        return client;
    }

    @Override
    protected String getUniqueNetworkIdentifier(Attribute<?> protocolConfiguration) {
        String host = protocolConfiguration.getMetaItem(META_VELBUS_HOST)
            .flatMap(AbstractValueHolder::getValueAsString)
            .orElse("");
        String port = protocolConfiguration.getMetaItem(META_VELBUS_PORT)
            .flatMap(AbstractValueHolder::getValueAsInteger)
            .map(Object::toString)
            .orElse("");

        return host + ":" + port;
    }
}
