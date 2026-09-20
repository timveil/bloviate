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

package io.bloviate.cli;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How a failure is condensed to the one line the CLI prints: the reason must always survive. */
class FailureMessageTest {

    private static final String REASON = "duplicate key value violates unique constraint \"orders_pkey\"";

    private static String quotedStatement(int rows) {
        return "insert into orders (id, amount) values " + "(1, 2),".repeat(rows);
    }

    @Test
    void aReasonBuriedInTheMiddleOfAVeryLongMessageIsAppended() {
        // the driver's reason sits between two huge stretches of quoted statement, where abbreviating cuts it out
        String message = "Batch entry 0 " + quotedStatement(400) + " was aborted: " + REASON + " " + quotedStatement(400);
        SQLException failure = new SQLException(message, new SQLException(REASON));

        String described = FillCommand.describe(failure);

        assertTrue(described.contains(REASON), "the reason must be visible: " + described);
        assertTrue(described.contains("(caused by: "), described);
        assertTrue(described.length() < 1500, "still short: " + described.length());
    }

    @Test
    void aReasonAtTheEndOfALongMessageIsNotDuplicated() {
        String message = "failed to fill table [orders]: Batch entry 0 " + quotedStatement(400) + " was aborted: " + REASON;
        SQLException failure = new SQLException(message, new SQLException(REASON));

        String described = FillCommand.describe(failure);

        assertTrue(described.endsWith(REASON), described);
        assertFalse(described.contains("(caused by"), described);
        assertEquals(1, described.split(java.util.regex.Pattern.quote(REASON), -1).length - 1, described);
    }

    @Test
    void aShortMessageThatAlreadySaysTheReasonIsNotDuplicated() {
        SQLException failure = new SQLException("failed to fill table [orders]: " + REASON, new SQLException(REASON));

        assertEquals("failed to fill table [orders]: " + REASON, FillCommand.describe(failure));
    }

    @Test
    void aReasonTheMessageDoesNotSayIsAppended() {
        SQLException failure = new SQLException("something failed", new SQLException(REASON));

        assertEquals("something failed (caused by: " + REASON + ")", FillCommand.describe(failure));
    }

    @Test
    void aBatchFailuresRealErrorIsFoundThroughGetNextException() {
        SQLException batch = new SQLException("Batch entry 0 " + quotedStatement(400) + " was aborted. See next.");
        batch.setNextException(new SQLException(REASON));

        assertTrue(FillCommand.describe(batch).endsWith("(caused by: " + REASON + ")"), FillCommand.describe(batch));
    }
}
