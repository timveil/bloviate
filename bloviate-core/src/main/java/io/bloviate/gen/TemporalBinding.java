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

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Binds and reads {@code java.sql} temporal values in a fixed zone, so what a fill stores never
 * depends on the machine it runs on (issue #640).
 *
 * <p>A {@link Timestamp}, {@link Date} or {@link Time} is an instant. Turning one into the value a
 * column holds needs a time zone, and the no-Calendar {@code setTimestamp}/{@code setDate}/
 * {@code setTime} overloads leave that zone to the driver, which uses the JVM's default. The same
 * seed and schema therefore produced different stored values on two machines &mdash; on MySQL a
 * {@code DATETIME} came out five hours apart under {@code America/New_York} and {@code UTC} &mdash;
 * which breaks the seed-reproducibility invariant in {@code CONTRIBUTING.md}.
 *
 * <p>Every bind here passes an explicit UTC {@link Calendar} instead, so what leaves the client is
 * the same on every JVM: a column that stores a wall clock ({@code DATE}, {@code TIME},
 * {@code DATETIME}, SQL {@code TIMESTAMP}) gets the instant rendered in UTC, and one that stores an
 * instant ({@code TIMESTAMP WITH TIME ZONE}) gets that instant. Reads use the same zone, so a value
 * round-trips to the object it was bound from.
 *
 * <p>One thing this cannot pin, because no client can: MySQL's {@code TIMESTAMP} is an instant the
 * <em>server</em> converts from the session time zone on write and back to it on read, so its
 * stored value follows that zone whatever is sent. MySQL's {@code DATE}, {@code TIME} and
 * {@code DATETIME} carry no zone and do not move.
 *
 * <p>A JVM already running in UTC &mdash; CI, and any container started with
 * {@code -Duser.timezone=UTC} &mdash; is unaffected: it was already producing these values.
 *
 * <p>The calendar is built per call rather than shared: {@link Calendar} is mutable and drivers set
 * its time while rendering the value, so one instance cannot be handed to two threads, and a fill
 * binds from as many threads as it has workers. The allocation is negligible beside the JDBC work it
 * accompanies.
 *
 * @since 3.9.1
 */
public final class TemporalBinding {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private TemporalBinding() {
    }

    /** A fresh calendar in UTC; never shared, see the class note. */
    private static Calendar utc() {
        return Calendar.getInstance(UTC, Locale.ROOT);
    }

    public static void setTimestamp(PreparedStatement statement, int parameterIndex, Timestamp value) throws SQLException {
        statement.setTimestamp(parameterIndex, value, utc());
    }

    public static Timestamp getTimestamp(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getTimestamp(columnIndex, utc());
    }

    public static void setDate(PreparedStatement statement, int parameterIndex, Date value) throws SQLException {
        statement.setDate(parameterIndex, value, utc());
    }

    public static Date getDate(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getDate(columnIndex, utc());
    }

    public static void setTime(PreparedStatement statement, int parameterIndex, Time value) throws SQLException {
        statement.setTime(parameterIndex, value, utc());
    }

    public static Time getTime(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getTime(columnIndex, utc());
    }
}
