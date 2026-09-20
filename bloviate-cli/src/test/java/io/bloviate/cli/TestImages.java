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

/**
 * Container images used by this module's integration tests, in one place, each pinned to an explicit
 * tag (a floating tag would let a database release change test behaviour with no commit here; see
 * {@code TestImages} in bloviate-core's tests, which this mirrors).
 */
final class TestImages {

    /** PostgreSQL. Alpine variant: the smallest image, and it starts in about a second. */
    static final String POSTGRES = "postgres:18-alpine";

    private TestImages() {
        // constants only
    }
}
