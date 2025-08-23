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

/**
 * Builder interface for creating data generator instances.
 * Implementations of this interface follow the Builder pattern to construct
 * configured data generators.
 *
 * @since 1.0.0
 */
public interface Builder {
    /**
     * Builds and returns a configured data generator.
     *
     * @return a new instance of AbstractDataGenerator configured with the builder's settings
     */
    AbstractDataGenerator<?> build();
}
