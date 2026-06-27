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

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declaratively fills a test database with Bloviate-generated data before each test.
 *
 * <p>Apply this to a test class (or an individual test method) together with a field marked
 * {@link FillSource} that exposes the target {@link javax.sql.DataSource} or
 * {@link java.sql.Connection}. The schema must already exist — Bloviate fills the tables it
 * discovers — so create it first (init script, Flyway/Liquibase, a Testcontainers
 * {@code withInitScript(...)}, etc.).
 *
 * <pre>{@code
 * @FillDatabase(rows = 50, seed = 42)
 * class OrdersTest {
 *
 *     @FillSource
 *     DataSource dataSource = ...; // schema already created
 *
 *     @Test
 *     void hasData() throws Exception { ... }
 * }
 * }</pre>
 *
 * <p>The annotation is meta-annotated with {@link ExtendWith}, so adding
 * {@code @ExtendWith(BloviateExtension.class)} separately is unnecessary. A method-level
 * {@code @FillDatabase} overrides a class-level one for that test.
 *
 * @see FillSource
 * @see BloviateExtension
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@ExtendWith(BloviateExtension.class)
public @interface FillDatabase {

    /**
     * @return the number of rows to generate per table (the default row count); defaults to 100
     */
    long rows() default 100L;

    /**
     * @return the JDBC batch size used for INSERTs; defaults to 1000
     */
    int batchSize() default 1000;

    /**
     * The base seed for reproducible generation. The same schema filled with the same seed always
     * produces identical data; change it for a different but equally reproducible dataset.
     *
     * @return the base seed; defaults to 0
     */
    long seed() default 0L;
}
