/*
 * Copyright (c) 2021 Tim Veil
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.bloviate.gen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class SequentialIntegerGenerator extends AbstractDataGenerator<Integer> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final int initialValue;
    private final int maxValue;
    private final AtomicInteger counter;

    @Override
    public Integer generate(Random random) {

        int value = counter.get();;

        if (value < maxValue) {
            counter.set(value + 1);
        } else if (value == maxValue) {
            logger.trace("max value of {} has been reached, counter will be reset to {}", maxValue, initialValue);
            counter.set(initialValue);
        } else {
            logger.warn("value {} is greater than max value {}", value, maxValue);
        }

        return value;
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Integer value) throws SQLException {
        statement.setInt(parameterIndex, value);
    }

    public static class Builder implements io.bloviate.gen.Builder {

        private final int initialValue;
        private final int maxValue;

        public Builder(int initialValue, int maxValue) {
            this.initialValue = initialValue;
            this.maxValue = maxValue;
        }

        @Override
        public SequentialIntegerGenerator build() {
            return new SequentialIntegerGenerator(this);
        }
    }

    private SequentialIntegerGenerator(Builder builder) {
        this.initialValue = builder.initialValue;
        this.maxValue = builder.maxValue;
        this.counter = new AtomicInteger(builder.initialValue);
    }
}
