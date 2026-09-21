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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Proxy;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Issue #640: what a fill stores must not depend on the JVM's default time zone.
 *
 * <p>A {@code java.sql} temporal is an instant; turning one into the value a column holds needs a
 * zone, and the no-Calendar {@code setTimestamp}/{@code setDate}/{@code setTime} overloads leave that
 * choice to the driver, which uses the JVM default. These tests bind through a recording
 * {@link PreparedStatement} and assert that every generator on the default fill path passes an
 * explicit UTC calendar instead, under three different JVM zones.
 *
 * <p>{@link Isolated} because {@link TimeZone#setDefault} is global and this module runs its test
 * classes concurrently.
 */
@Isolated
class TemporalBindingTest {

    private static final int COLUMN = 1;
    private static final Random RANDOM = new Random(1);

    /** A zone west of UTC, UTC itself, and one east of it: a wrong zone shows up as either sign. */
    private static final List<String> ZONES = List.of("America/New_York", "UTC", "Asia/Tokyo");

    private static final Timestamp TIMESTAMP = Timestamp.from(java.time.Instant.parse("2020-01-15T22:00:49Z"));
    private static final Date DATE = new Date(TIMESTAMP.getTime());
    private static final Time TIME = new Time(TIMESTAMP.getTime());

    private final TimeZone originalZone = TimeZone.getDefault();

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    /** One recorded bind: which setter ran, with what value, and in which calendar zone. */
    private record Bind(String setter, Object value, String zoneId) {
    }

    private static PreparedStatement recording(List<Bind> binds) {
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    if (method.getName().startsWith("set") && args != null && args.length == 3
                            && args[2] instanceof Calendar calendar) {
                        binds.add(new Bind(method.getName(), args[1], calendar.getTimeZone().getID()));
                    } else if (method.getName().startsWith("set")) {
                        // a bind that passed no calendar is exactly the bug, so record it as such
                        binds.add(new Bind(method.getName(), args == null ? null : args[args.length - 1], null));
                    }
                    return null;
                });
    }

    private static List<Bind> bindEveryTemporalGenerator() throws SQLException {
        List<Bind> binds = new ArrayList<>();
        PreparedStatement statement = recording(binds);

        new SqlTimestampGenerator.Builder(RANDOM).build().set(null, statement, COLUMN, TIMESTAMP);
        new SkewedTimestampGenerator.Builder(RANDOM).build().set(null, statement, COLUMN, TIMESTAMP);
        new CurrentSqlTimestampGenerator.Builder(RANDOM).build().set(null, statement, COLUMN, TIMESTAMP);
        new SqlDateGenerator.Builder(RANDOM).build().set(null, statement, COLUMN, DATE);
        new SqlTimeGenerator.Builder(RANDOM).build().set(null, statement, COLUMN, TIME);

        return binds;
    }

    @Test
    void everyTemporalGeneratorBindsInUtcWhateverTheJvmZoneIs() throws SQLException {
        List<Bind> reference = null;

        for (String zone : ZONES) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));

            List<Bind> binds = bindEveryTemporalGenerator();

            assertEquals(5, binds.size(), "every generator must bind exactly once under " + zone);
            for (Bind bind : binds) {
                assertEquals("UTC", bind.zoneId(),
                        bind.setter() + " bound in the JVM zone instead of UTC under " + zone);
            }

            // the binds are identical across zones, which is the property the issue is about
            if (reference == null) {
                reference = binds;
            } else {
                assertEquals(reference, binds, "binds differ under " + zone);
            }
        }
    }

    @Test
    void theHelperBindsAndReadsInUtc() throws SQLException {
        for (String zone : ZONES) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));

            List<Bind> binds = new ArrayList<>();
            PreparedStatement statement = recording(binds);

            TemporalBinding.setTimestamp(statement, COLUMN, TIMESTAMP);
            TemporalBinding.setDate(statement, COLUMN, DATE);
            TemporalBinding.setTime(statement, COLUMN, TIME);

            assertEquals(List.of(
                            new Bind("setTimestamp", TIMESTAMP, "UTC"),
                            new Bind("setDate", DATE, "UTC"),
                            new Bind("setTime", TIME, "UTC")),
                    binds, "under " + zone);
        }
    }

    /**
     * The calendar must not be shared: it is mutable, drivers set its time while rendering a value,
     * and a fill binds from as many threads as it has workers.
     */
    @Test
    void eachBindGetsItsOwnCalendar() throws SQLException {
        List<Calendar> calendars = new ArrayList<>();
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    if (args != null && args.length == 3 && args[2] instanceof Calendar calendar) {
                        calendars.add(calendar);
                    }
                    return null;
                });

        TemporalBinding.setTimestamp(statement, COLUMN, TIMESTAMP);
        TemporalBinding.setTimestamp(statement, COLUMN, TIMESTAMP);

        assertEquals(2, calendars.size());
        assertNotSame(calendars.get(0), calendars.get(1));
        assertSame(calendars.get(0).getTimeZone().getID(), calendars.get(1).getTimeZone().getID());
    }
}
