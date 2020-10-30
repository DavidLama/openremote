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
package org.openremote.model.protocol;

import org.openremote.model.asset.Asset;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.asset.AssetTreeNode;
import org.openremote.model.file.FileInfo;

/**
 * To be used by protocols that support device import from one or more protocol specific files.
 * <p>
 * The import process should return an array of {@link Asset}s that represent the physical devices
 * connected to a particular {@link org.openremote.model.asset.agent.ProtocolConfiguration}. The device
 * assets should contain all the appropriate {@link Attribute}s needed for interacting with the
 * device. Each {@link Attribute} should be of the correct type and have any required
 * {@link org.openremote.model.attribute.MetaItem}s already set.
 * <p>
 * <b>
 * NOTE: It is not necessary to set an {@link org.openremote.model.asset.agent.AgentLink}
 * {@link org.openremote.model.attribute.MetaItem} for each attribute as the system will insert these
 * automatically if omitted.
 * </b>
 */
public interface ProtocolAssetImport {

    /**
     * Import devices for the specified {@link org.openremote.model.asset.agent.ProtocolConfiguration}
     * using the supplied {@link FileInfo}.
     * @throws IllegalStateException when there is an issue processing the provided {@link FileInfo}
     */
    AssetTreeNode[] discoverLinkedAttributes(Attribute protocolConfiguration, FileInfo fileInfo) throws IllegalStateException;
}
