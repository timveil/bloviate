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

package io.bloviate.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the field {@link BloviateExtension} should fill when {@link FillDatabase} is applied.
 *
 * <p>The annotated field must be a {@link javax.sql.DataSource} or a {@link java.sql.Connection}
 * and may be instance or {@code static}. When the source is a {@code DataSource}, the extension
 * borrows a connection for the fill and closes it afterwards; a {@code Connection} is used as-is
 * and left open (the test owns its lifecycle).
 *
 * <p>If no field is annotated, the extension falls back to a single field assignable to
 * {@code DataSource} or {@code Connection}; it fails fast if none or more than one candidate
 * exists.
 *
 * @see FillDatabase
 * @see BloviateExtension
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FillSource {
}
