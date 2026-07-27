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

package io.bloviate.bench;

/**
 * Container images used by the end-to-end fill benchmarks.
 *
 * <p>Kept in step with {@code TestImages} in bloviate-core: a benchmark measuring a different
 * database version than the tests exercise would make the two sets of numbers incomparable.
 * The two cannot share one class without publishing a test-jar from bloviate-core, which is a
 * lot of build machinery for four constants &mdash; so they are deliberately parallel files.
 *
 * <p>All tags are pinned. A floating tag would make a benchmark result unattributable: a
 * regression could come from a code change or from a database release, with no way to tell.
 */
final class BenchImages {

    /** PostgreSQL. Matches {@code TestImages.POSTGRES}. */
    static final String POSTGRES = "postgres:18-alpine";

    /** MySQL. Matches {@code TestImages.MYSQL}. */
    static final String MYSQL = "mysql:9.7";

    /** CockroachDB. Matches {@code TestImages.COCKROACH}. */
    static final String COCKROACH = "cockroachdb/cockroach:v26.2.4";

    private BenchImages() {
        // constants only
    }
}
