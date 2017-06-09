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
package org.openremote.test.protocol

import org.openremote.agent.protocol.AbstractProtocol
import org.openremote.agent.protocol.knx.KNXProtocol
import org.openremote.agent.protocol.ConnectionStatus
import org.openremote.manager.server.agent.AgentService
import org.openremote.manager.server.asset.AssetProcessingService
import org.openremote.manager.server.asset.AssetStorageService
import org.openremote.manager.server.asset.ServerAsset
import org.openremote.model.Constants
import org.openremote.model.asset.AssetAttribute
import org.openremote.model.asset.AssetMeta
import org.openremote.model.asset.AssetType
import org.openremote.model.asset.agent.ProtocolConfiguration
import org.openremote.model.attribute.*
import org.openremote.model.value.Values
import org.openremote.test.ManagerContainerTrait
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * This tests the KNX protocol and protocol implementation.
 */
class KNXProtocolTest extends Specification implements ManagerContainerTrait {

    def "Check KNX protocol"() {

        given: "expected conditions"
        def conditions = new PollingConditions(timeout: 10, delay: 1)

        and: "the container is started"
        def serverPort = findEphemeralPort()
        def container = startContainerNoDemoAssets(defaultConfig(serverPort), defaultServices())
        def assetStorageService = container.getService(AssetStorageService.class)
        def agentService = container.getService(AgentService.class)
        def assetProcessingService = container.getService(AssetProcessingService.class)
        
        and: "the KNX emulation server is started"
        //TODO

        when: "a KNX agent that uses the KNX protocol is created with a valid protocol configuration"
        def knxAgent = new ServerAsset()
        knxAgent.setName("KNX Agent")
        knxAgent.setType(AssetType.AGENT)
        knxAgent.setAttributes(
            ProtocolConfiguration.initProtocolConfiguration(new AssetAttribute("knxConfig"), KNXProtocol.PROTOCOL_NAME)
                .addMeta(
                    new MetaItem(KNXProtocol.KNX_GATEWAY_IP, Values.create("localhost"))
                ),
            ProtocolConfiguration.initProtocolConfiguration(new AssetAttribute("knxConfigError"), KNXProtocol.PROTOCOL_NAME)
        )
        knxAgent.setRealmId(Constants.MASTER_REALM)
        knxAgent = assetStorageService.merge(knxAgent)

        then: "the protocol configurations should be linked and their deployment status should be available in the agent service"
        conditions.eventually {
            assert agentService.getProtocolDeploymentStatus(knxAgent.getAttribute("knxConfig").get().getReferenceOrThrow()) == ConnectionStatus.CONNECTED
        }
        
        conditions.eventually {
            assert agentService.getProtocolDeploymentStatus(knxAgent.getAttribute("knxConfigError").get().getReferenceOrThrow()) == ConnectionStatus.ERROR
        }


        cleanup: "the server should be stopped"
        stopContainer(container)
    }
}