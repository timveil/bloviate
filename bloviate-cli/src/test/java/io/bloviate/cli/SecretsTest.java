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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class SecretsTest {

    @Test
    void urlPasswordsAreMaskedInEveryForm() {
        assertEquals("jdbc:postgresql://host/db?user=u&password=****&ssl=true",
                Secrets.redactUrl("jdbc:postgresql://host/db?user=u&password=hunter2&ssl=true"));
        assertEquals("jdbc:postgresql://u:****@host:5432/db", Secrets.redactUrl("jdbc:postgresql://u:hunter2@host:5432/db"));
        assertEquals("jdbc:h2:mem:x;USER=sa;PASSWORD=****", Secrets.redactUrl("jdbc:h2:mem:x;USER=sa;PASSWORD=hunter2"));
        assertEquals("jdbc:sqlserver://h;user=u;password=****;encrypt=true",
                Secrets.redactUrl("jdbc:sqlserver://h;user=u;password=hunter2;encrypt=true"));
        assertEquals("jdbc:mysql://h/db?pwd=****", Secrets.redactUrl("jdbc:mysql://h/db?pwd=hunter2"));
    }

    @Test
    void theShapeOfAUrlAloneIsEnoughWhereNoPasswordIsKnown() {
        assertEquals("Unmatched argument: 'jdbc:postgresql://u:****@h/db'",
                Secrets.redactUrl("Unmatched argument: 'jdbc:postgresql://u:hunter2@h/db'"));
        assertEquals("'jdbc:h2:mem:x;PASSWORD=****'", Secrets.redactUrl("'jdbc:h2:mem:x;PASSWORD=hunter2'"));
        assertEquals("password=****", Secrets.redactUrl("password=hunter2"));
        assertEquals("jdbc:postgresql://u:****@h/db", Secrets.redactUrl("jdbc:postgresql://u:hun@ter2@h/db"));
        assertEquals("jdbc:postgresql://h:5432/db?email=a@b", Secrets.redactUrl("jdbc:postgresql://h:5432/db?email=a@b"));
    }

    @Test
    void aUrlWithoutAPasswordIsUnchanged() {
        String url = "jdbc:postgresql://host:5432/db?user=u&ssl=true";
        assertEquals(url, Secrets.redactUrl(url));
        assertEquals("jdbc:h2:mem:x", Secrets.redactUrl("jdbc:h2:mem:x"));
    }

    @Test
    void everyKnownPasswordIsScrubbedFromAnyText() {
        Secrets secrets = new Secrets("jdbc:postgresql://u:from-url@host/db", "from-env");

        String scrubbed = secrets.scrub("connect failed for from-url and from-env: FATAL password from-env rejected");

        assertFalse(scrubbed.contains("from-url"), scrubbed);
        assertFalse(scrubbed.contains("from-env"), scrubbed);
        assertEquals("connect failed for **** and ****: FATAL password **** rejected", scrubbed);
    }

    @Test
    void aPasswordThatIsNotInTheUrlIsStillScrubbedFromAMessageThatQuotesTheUrl() {
        Secrets secrets = Secrets.none();

        assertEquals("cannot connect to jdbc:x://h/d?password=****", secrets.scrub("cannot connect to jdbc:x://h/d?password=oops"));
        assertNull(secrets.scrub(null));
    }
}
