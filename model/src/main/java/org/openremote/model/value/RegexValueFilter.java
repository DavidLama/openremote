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
package org.openremote.model.value;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.openremote.model.value.RegexValueFilter.NAME;

@JsonTypeName(NAME)
public class RegexValueFilter extends ValueFilter {

    public static final String NAME = "regex";

    @JsonDeserialize(using = FromStringDeserializer.class)
    public Pattern pattern;
    public int matchGroup;
    public int matchIndex;

    @JsonCreator
    public RegexValueFilter(@JsonProperty("pattern") Pattern pattern,
                            @JsonProperty("matchGroup") int matchGroup,
                            @JsonProperty("matchIndex") int matchIndex) {
        this.pattern = pattern;
        this.matchGroup = matchGroup;
        this.matchIndex = matchIndex;
    }

    @Override
    public Object filter(Object value) {
        if (pattern == null) {
            return null;
        }

        Optional<String> valueStr = Values.getValue(value, String.class, true);
        if (!valueStr.isPresent()) {
            return null;
        }

        String filteredStr = null;
        Matcher matcher = pattern.matcher(valueStr.get());
        int matchIndex = 0;
        boolean matched = matcher.find();

        while(matched && matchIndex < this.matchIndex) {
            matched = matcher.find();
            matchIndex++;
        }

        if (matched) {
            if (matchGroup <= matcher.groupCount()) {
                filteredStr = matcher.group(matchGroup);
            }
        }

        return filteredStr;
    }
}
