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
package org.openremote.model.query.filter;

import org.openremote.model.query.AssetQuery;

public class NumberPredicate implements ValuePredicate {

    public static final String name = "number";
    public double value;
    public double rangeValue; // Used as upper bound when Operator.BETWEEN
    public AssetQuery.Operator operator = AssetQuery.Operator.EQUALS;
    public AssetQuery.NumberType numberType = AssetQuery.NumberType.DOUBLE;
    public boolean negate;

    public NumberPredicate() {
    }

    public NumberPredicate(double value) {
        this.value = value;
    }

    public NumberPredicate(double value, AssetQuery.Operator operator) {
        this.value = value;
        this.operator = operator;
    }

    public NumberPredicate(double value, AssetQuery.NumberType numberType) {
        this.value = value;
        this.numberType = numberType;
    }

    public NumberPredicate(double value, AssetQuery.Operator operator, AssetQuery.NumberType numberType) {
        this.value = value;
        this.operator = operator;
        this.numberType = numberType;
    }

    public static NumberPredicate fromObjectValue(ObjectValue objectValue) {
        NumberPredicate numberPredicate = new NumberPredicate();
        objectValue.getNumber("value").ifPresent(value -> {
            numberPredicate.value = value;
        });
        objectValue.getNumber("rangeValue").ifPresent(rangeValue -> {
            numberPredicate.rangeValue = rangeValue;
        });
        objectValue.getBoolean("negate").ifPresent(negate -> {
            numberPredicate.negate = negate;
        });
        objectValue.getString("operator").ifPresent(operator -> {
            numberPredicate.operator = AssetQuery.Operator.valueOf(operator);
        });
        return numberPredicate;
    }

    public NumberPredicate predicate(double predicate) {
        this.value = predicate;
        return this;
    }

    public NumberPredicate numberMatch(AssetQuery.Operator operator) {
        this.operator = operator;
        return this;
    }

    public NumberPredicate numberType(AssetQuery.NumberType numberType) {
        this.numberType = numberType;
        return this;
    }

    public NumberPredicate rangeValue(double rangeValue) {
        this.operator = AssetQuery.Operator.BETWEEN;
        this.rangeValue = rangeValue;
        return this;
    }

    public NumberPredicate negate(boolean negate) {
        this.negate = negate;
        return this;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
            "value=" + value +
            ", rangeValue=" + rangeValue +
            ", operator=" + operator +
            ", numberType=" + numberType +
            ", negate=" + negate +
            '}';
    }
}
