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

package io.bloviate.db;

/**
 * Container images used by this module's integration tests, in one place.
 *
 * <p>Every tag is pinned to an explicit version. A floating tag such as {@code :latest} would let a
 * database release change test behaviour with no commit to this repository, which is at odds with
 * the seed-reproducibility invariant in CONTRIBUTING.md: a failure that cannot be reproduced from a
 * checkout is a failure with nothing to bisect.
 *
 * <p>These constants were previously repeated across a dozen test classes, so bumping a version was
 * a find-and-replace that was easy to do incompletely.
 */
final class TestImages {

    /** PostgreSQL. Alpine variant: smallest image here, and it starts in about a second. */
    static final String POSTGRES = "postgres:18-alpine";

    /** MySQL. Slowest of the four to become healthy, at roughly 7s per container. */
    static final String MYSQL = "mysql:9.7";

    /** MariaDB. */
    static final String MARIADB = "mariadb:11.4";

    /** CockroachDB. Pinned in #579; this was the one image still tracking {@code :latest}. */
    static final String COCKROACH = "cockroachdb/cockroach:v26.2.4";

    private TestImages() {
        // constants only
    }
}
