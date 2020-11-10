/*
 * Copyright 2020, OpenRemote Inc.
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
package org.openremote.agent.protocol.udp;

import org.openremote.agent.protocol.io.AbstractIoClientProtocol;
import org.openremote.agent.protocol.io.IoAgent;
import org.openremote.agent.protocol.tcp.TcpClientProtocol;
import org.openremote.agent.protocol.tcp.TcpIoClient;
import org.openremote.model.asset.AssetDescriptor;

public class UdpClientAgent extends IoAgent<String, UdpIoClient<String>> {

//    public static final AgentDescriptor<TcpClientAgent, TcpClientProtocol> DESCRIPTOR = new AgentDescriptor(
//
//    );

    protected <T extends UdpClientAgent> UdpClientAgent(String name, AssetDescriptor<T> descriptor) {
        super(name, descriptor);
    }

    @Override
    public AbstractIoClientProtocol<String, UdpIoClient<String>, UdpClientAgent> getProtocolInstance() {
        return new UdpClientProtocol(this);
    }
}
