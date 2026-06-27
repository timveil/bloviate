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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.random.RandomGenerator;

/**
 * Generates {@link Timestamp} values in the window {@code [start, end]} skewed toward {@code end} —
 * i.e. <em>recent</em> timestamps are more likely than old ones. Real event/audit columns
 * (created_at, last_login, order_date) are rarely uniform across history; they bunch toward the
 * present.
 *
 * <p>A uniform draw {@code u} is reshaped to {@code u^(1/skew)}: {@code skew == 1} is uniform, and
 * larger {@code skew} pulls more mass toward {@code end}. Bounds default to a deterministic five-year
 * window anchored at {@link AbstractBuilder#DEFAULT_REFERENCE}, so output stays reproducible (it is
 * never anchored to wall-clock {@code now()}); set explicit bounds for a different window.
 *
 * @since 2.11.0
 */
public class SkewedTimestampGenerator extends AbstractDataGenerator<Timestamp> {

    private final long startMillis;
    private final long rangeMillis;
    private final double skew;

    @Override
    public Timestamp generate() {
        double u = randomUtils.nextDouble(0.0, 1.0);
        double fraction = Math.pow(u, 1.0 / skew);
        return new Timestamp(startMillis + (long) (fraction * rangeMillis));
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Timestamp value) throws SQLException {
        statement.setTimestamp(parameterIndex, value);
    }

    @Override
    public Timestamp get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getTimestamp(columnIndex);
    }

    public static class Builder extends AbstractBuilder<Timestamp> {

        private Instant start = DEFAULT_REFERENCE;
        private Instant end = DEFAULT_REFERENCE.plus(Duration.ofDays(5 * 365));
        private double skew = 3.0;

        public Builder(RandomGenerator random) {
            super(random);
        }

        public Builder start(Instant start) {
            this.start = start;
            return this;
        }

        public Builder end(Instant end) {
            this.end = end;
            return this;
        }

        /** Recency skew; {@code 1.0} is uniform, larger pulls mass toward {@code end}. Must be {@code > 0}. */
        public Builder skew(double skew) {
            this.skew = skew;
            return this;
        }

        @Override
        public SkewedTimestampGenerator build() {
            if (skew <= 0) {
                throw new IllegalArgumentException("skew must be positive: " + skew);
            }
            if (end.isBefore(start)) {
                throw new IllegalArgumentException("end must be >= start");
            }
            return new SkewedTimestampGenerator(this);
        }
    }

    private SkewedTimestampGenerator(Builder builder) {
        super(builder.random);
        this.startMillis = builder.start.toEpochMilli();
        this.rangeMillis = builder.end.toEpochMilli() - builder.start.toEpochMilli();
        this.skew = builder.skew;
    }
}
