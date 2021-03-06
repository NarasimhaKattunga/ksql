/*
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.parser.tree;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.Optional;

public class Map extends Type {

  private final Type valueType;

  public Map(final Type valueType) {
    this(Optional.empty(), valueType);
  }

  public Map(final NodeLocation location, final Type valueType) {
    this(Optional.of(location), valueType);
  }

  private Map(final Optional<NodeLocation> location, final Type valueType) {
    super(location, KsqlType.MAP);
    requireNonNull(valueType, "itemType is null");
    this.valueType = valueType;
  }

  @Override
  public <R, C> R accept(final AstVisitor<R, C> visitor, final C context) {
    return visitor.visitMap(this, context);
  }

  public Type getValueType() {
    return valueType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(valueType);
  }

  @Override
  public boolean equals(final Object obj) {
    return
        obj instanceof Map
        && Objects.equals(valueType, ((Map)obj).valueType);
  }
}
