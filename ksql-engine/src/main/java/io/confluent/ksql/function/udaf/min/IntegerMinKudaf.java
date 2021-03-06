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

package io.confluent.ksql.function.udaf.min;

import io.confluent.ksql.function.AggregateFunctionArguments;
import io.confluent.ksql.function.BaseAggregateFunction;
import io.confluent.ksql.function.KsqlAggregateFunction;
import java.util.Collections;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.streams.kstream.Merger;

public class IntegerMinKudaf extends BaseAggregateFunction<Integer, Integer> {

  IntegerMinKudaf(final String functionName, final int argIndexInValue) {
    super(functionName, argIndexInValue, () -> Integer.MAX_VALUE, Schema.OPTIONAL_INT32_SCHEMA,
        Collections.singletonList(Schema.OPTIONAL_INT32_SCHEMA),
        "Computes the minimum integer value for a key."
    );
  }

  @Override
  public Integer aggregate(final Integer currentValue, final Integer aggregateValue) {
    if (currentValue == null) {
      return aggregateValue;
    }
    return Math.min(currentValue, aggregateValue);
  }

  @Override
  public Merger<String, Integer> getMerger() {
    return (aggKey, aggOne, aggTwo) -> Math.min(aggOne, aggTwo);
  }

  @Override
  public KsqlAggregateFunction<Integer, Integer> getInstance(
      final AggregateFunctionArguments aggregateFunctionArguments) {
    return new IntegerMinKudaf(functionName, aggregateFunctionArguments.udafIndex());
  }
}