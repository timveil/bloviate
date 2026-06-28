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

package io.bloviate.datafaker;

/**
 * A coherent person identity for one row: the {@code email}, {@code username}, and {@code fullName}
 * are all <em>derived from</em> the same {@code firstName}/{@code lastName}, so columns that project
 * different fields of the same {@link Person} agree (no "jane.doe with email bob@…"). Materialized
 * once per row by {@link People}.
 *
 * @since 2.12.0
 * @see People
 * @see RowContext
 */
public record Person(String firstName, String lastName, String fullName, String email, String username) {
}
