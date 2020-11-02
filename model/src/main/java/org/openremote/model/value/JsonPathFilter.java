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
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ParseContext;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import org.openremote.model.util.TextUtil;

import java.util.Optional;

import static org.openremote.model.value.RegexValueFilter.NAME;

/**
 * This filter works on any type of data; when applying the filter the data should be converted to JSON representation
 * using a tool like Jackson and then the JSON path expression should be applied to this JSON string.
 */
@JsonTypeName(NAME)
public class JsonPathFilter extends ValueFilter {

    protected static ParseContext jsonPathParser = JsonPath.using(
        Configuration.builder()
        .jsonProvider(new JacksonJsonNodeJsonProvider())
        .mappingProvider(new JacksonMappingProvider())
        .build()
                .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL)
        );

    public static final String NAME = "jsonPath";

    @JsonProperty
    public String path;

    @JsonProperty
    public boolean returnFirst;

    @JsonProperty
    public boolean returnLast;

    @JsonCreator
    public JsonPathFilter(@JsonProperty("path") String path,
                          @JsonProperty("returnFirst") boolean returnFirst,
                          @JsonProperty("returnLast") boolean returnLast) {
        this.path = path;
        this.returnFirst = returnFirst;
        this.returnLast = returnLast;
    }

    @Override
    public Object filter(Object value) {
        if (TextUtil.isNullOrEmpty(path)) {
            return null;
        }

        Optional<String> valueStr = Values.getValue(value, String.class, true);
        if (!valueStr.isPresent()) {
            return null;
        }

        if (value == null) {
            return null;
        }

        Object obj = jsonPathParser.parse(valueStr.get()).read(path);
        String pathJson = obj != null ? obj.toString() : null;
        if (TextUtil.isNullOrEmpty(pathJson)) {
            return null;
        }

        return Values.parse(pathJson).map(jsonNode -> {
            if ((returnFirst || returnLast) && jsonNode.isArray()) {
                jsonNode = jsonNode.get(returnFirst ? 0 : jsonNode.size() - 1);
            }

            return jsonNode;
        }).orElse(null);
    }
}
