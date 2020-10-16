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
package org.openremote.model.attribute;

import org.openremote.model.util.Pair;
import org.openremote.model.value.ObjectValue;
import org.openremote.model.value.Value;
import org.openremote.model.value.Values;

import java.util.Objects;
import java.util.Optional;

/**
 * The desired or current or past state of an {@link AttributeRef}.
 * <p>
 * <code>null</code> is a valid {@link #value}.
 * </p>
 */
public class AttributeState {

    protected AttributeRef attributeRef;
    protected Value value;
    protected boolean deleted;

    protected AttributeState() {
    }

    public AttributeState(String entityId, Attribute attribute) {
        this(entityId, attribute.getNameOrThrow(), attribute.getValue().orElse(null));
    }

    public AttributeState(String entityId, String attributeName, Value value) {
        this(new AttributeRef(entityId, attributeName), value);
    }

    /**
     * Sets the {@link #value} to <code>null</code>.
     */
    public AttributeState(String entityId, String attributeName) {
        this(new AttributeRef(entityId, attributeName), null);
    }

    /**
     * @param value can be <code>null</code> if the attribute has no value.
     */
    public AttributeState(AttributeRef attributeRef, Value value) {
        this.attributeRef = Objects.requireNonNull(attributeRef);
        this.value = value;
    }

    /**
     * Sets the {@link #value} to <code>null</code>.
     */
    public AttributeState(AttributeRef attributeRef) {
        this(attributeRef, null);
    }

    public AttributeRef getAttributeRef() {
        return attributeRef;
    }

    public Optional<Value> getValue() {
        return Optional.ofNullable(value);
    }

    public void setValue(Value value) {
        this.value = value;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public ObjectValue toObjectValue() {
        ObjectValue objectValue = Values.createObject();
        objectValue.put("attributeRef", getAttributeRef().toArrayValue());
        getValue().ifPresent(v -> objectValue.put("value", value));
        if (deleted) {
            objectValue.put("deleted", Values.create(true));
        }
        return objectValue;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
            "attributeRef=" + attributeRef +
            ", value=" + value +
            ", deleted=" + deleted +
            '}';
    }

    public static boolean isAttributeState(Value value) {
        return Values.getObject(value)
            .flatMap(objectValue -> objectValue.get("attributeRef"))
            .filter(AttributeRef::isAttributeRef)
            .isPresent();
    }

    public static Optional<AttributeState> fromValue(Value value) {
        return Values.getObject(value)
            .filter(AttributeState::isAttributeState)
            .map(objectValue -> new Pair<>(
                    AttributeRef.fromValue(objectValue.get("attributeRef").get()),
                    objectValue.get("value")
                )
            )
            .filter(pair -> pair.key.isPresent())
            .map(pair -> new AttributeState(pair.key.get(), pair.value.orElse(null)));
    }
}
