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
package org.openremote.model.simulator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.openremote.model.ValidationFailure;
import org.openremote.model.attribute.AttributeRef;
import org.openremote.model.v2.ValueDescriptor;
import org.openremote.model.value.Values;

import org.openremote.model.simulator.element.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@JsonSubTypes({
    // Events used on client and server (serializable)
    @JsonSubTypes.Type(value = NumberSimulatorElement.class, name = NumberSimulatorElement.ELEMENT_NAME),
    @JsonSubTypes.Type(value = SwitchSimulatorElement.class, name = SwitchSimulatorElement.ELEMENT_NAME),
    @JsonSubTypes.Type(value = ColorSimulatorElement.class, name = ColorSimulatorElement.ELEMENT_NAME),
    @JsonSubTypes.Type(value = ReplaySimulatorElement.class, name = ReplaySimulatorElement.ELEMENT_NAME),
})
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "elementType"
)
public abstract class SimulatorElement implements ValueHolder {

    public AttributeRef attributeRef;
    public ValueDescriptor<?> expectedType;
    @JsonIgnore
    public Value elementValue = null;

    protected SimulatorElement() {
    }

    public SimulatorElement(AttributeRef attributeRef, AttributeValueDescriptor expectedType) {
        this.attributeRef = attributeRef;
        this.expectedType = expectedType;
    }

    public AttributeRef getAttributeRef() {
        return attributeRef;
    }

    public AttributeValueDescriptor getExpectedType() {
        return expectedType;
    }

    @JsonIgnore
    @Override
    public Optional<Value> getValue() {
        return Optional.ofNullable(elementValue);
    }

    @JsonProperty("value")
    private Value getValueInternal() {
        return getValue().orElse(null);
    }

    @JsonProperty("value")
    @Override
    public void setValue(Value value) {
        this.elementValue = value;
    }

    @Override
    public void clearValue() {
        this.elementValue = null;
    }

    @Override
    public Optional<String> getValueAsString() {
        return Values.getString(elementValue);
    }

    @Override
    public Optional<Double> getValueAsNumber() {
        return Values.getNumber(elementValue);
    }

    @Override
    public Optional<Integer> getValueAsInteger() {
        return Values.getNumber(elementValue).map(Double::intValue);
    }

    @Override
    public Optional<Boolean> getValueAsBoolean() {
        return Values.getBoolean(elementValue);
    }

    @Override
    public Optional<ObjectValue> getValueAsObject() {
        return Values.getObject(elementValue);
    }

    @Override
    public Optional<ArrayValue> getValueAsArray() {
        return Values.getArray(elementValue);
    }

    @Override
    public List<ValidationFailure> getValidationFailures() {
        List<ValidationFailure> failures = new ArrayList<>();
        if (elementValue != null) {
            expectedType.getValidator().flatMap(v -> v.apply(elementValue)).ifPresent(failures::add);
        }
        return failures;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
            "value=" + elementValue +
            "}";
    }
}
