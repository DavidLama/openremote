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
package org.openremote.model.asset.agent;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.openremote.model.asset.AssetDescriptor;

/**
 * Special type of {@link AssetDescriptor} that describes an agent {@link org.openremote.model.asset.Asset}
 */
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@JsonDeserialize(as = AgentDescriptorImpl.class)
public interface AgentDescriptor extends AssetDescriptor {
    @JsonProperty
    boolean hasInstanceDiscovery();

    @JsonProperty
    boolean hasAssetDiscovery();

    @JsonProperty
    boolean hasInstanceImport();

    @JsonProperty
    boolean hasAssetImport();
}
