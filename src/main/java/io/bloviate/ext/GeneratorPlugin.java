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

package io.bloviate.ext;

/**
 * Service-provider interface that lets a jar contribute custom generator rules to a
 * {@link GeneratorRegistry} without subclassing {@link DatabaseSupport}.
 *
 * <p>Implementations register matchers (column-name pattern, vendor type name, and/or
 * {@link java.sql.JDBCType}) on the supplied {@link GeneratorRegistry.Builder}. Plugins are
 * picked up automatically by {@link GeneratorRegistry.Builder#discover()} via the standard
 * {@link java.util.ServiceLoader} mechanism: declare the implementation in
 * {@code META-INF/services/io.bloviate.ext.GeneratorPlugin}. They can also be registered
 * explicitly with {@link GeneratorRegistry.Builder#register(GeneratorPlugin)}.
 *
 * <p>Generators contributed this way remain reproducible: the fill engine constructs each
 * generator with an engine-managed seed, exactly as it does for built-in generators.
 *
 * @see GeneratorRegistry
 * @see io.bloviate.gen.DataGenerator
 */
@FunctionalInterface
public interface GeneratorPlugin {

    /**
     * Contributes generator rules to the registry under construction.
     *
     * @param builder the registry builder to register matchers on
     */
    void contribute(GeneratorRegistry.Builder builder);
}
