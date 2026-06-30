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

import io.bloviate.db.DatabaseConfiguration;
import io.bloviate.db.DatabaseFiller;
import io.bloviate.ext.DatabaseSupport;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * JUnit Jupiter extension that fills a test database before each test, driven by
 * {@link FillDatabase}. It is registered automatically via the {@code @FillDatabase}
 * meta-annotation, so tests usually don't reference this class directly.
 *
 * <p>For each test it resolves the effective {@link FillDatabase} (method-level overrides
 * class-level), locates the {@link FillSource} field, auto-detects the
 * {@link DatabaseSupport} from the connection, and runs a {@link DatabaseFiller} with the
 * annotation's row count, batch size, and seed.
 *
 * @see FillDatabase
 * @see FillSource
 */
public class BloviateExtension implements BeforeEachCallback {

    private static final Logger logger = LoggerFactory.getLogger(BloviateExtension.class);

    /** Creates the extension; instantiated by JUnit via {@code @ExtendWith}. */
    public BloviateExtension() {
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        FillDatabase fill = resolveAnnotation(context);
        if (fill == null) {
            // the extension is registered through @FillDatabase, so this is effectively unreachable
            return;
        }

        Object source = readSource(context);

        if (source instanceof DataSource dataSource) {
            try (Connection connection = dataSource.getConnection()) {
                fill(connection, fill);
            }
        } else if (source instanceof Connection connection) {
            // a raw Connection is owned by the test; use it as-is and leave it open
            fill(connection, fill);
        } else {
            throw new ExtensionConfigurationException(
                    "@FillSource field must be a javax.sql.DataSource or java.sql.Connection");
        }
    }

    private void fill(Connection connection, FillDatabase fill) throws SQLException {
        DatabaseSupport support = DatabaseSupport.forConnection(connection);
        logger.debug("@FillDatabase filling via {} (rows={}, batchSize={}, seed={})",
                support.getClass().getSimpleName(), fill.rows(), fill.batchSize(), fill.seed());
        DatabaseConfiguration configuration =
                new DatabaseConfiguration(fill.batchSize(), fill.rows(), support, null, fill.seed());
        new DatabaseFiller.Builder(connection, configuration).build().fill();
    }

    private @Nullable FillDatabase resolveAnnotation(ExtensionContext context) {
        return context.getTestMethod()
                .flatMap(method -> AnnotationSupport.findAnnotation(method, FillDatabase.class))
                .orElseGet(() -> AnnotationSupport.findAnnotation(
                        context.getRequiredTestClass(), FillDatabase.class).orElse(null));
    }

    private Object readSource(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        Field field = locateSourceField(testClass);

        Object instance = Modifier.isStatic(field.getModifiers())
                ? null
                : context.getRequiredTestInstance();

        Object value;
        try {
            field.setAccessible(true);
            value = field.get(instance);
        } catch (ReflectiveOperationException e) {
            throw new ExtensionConfigurationException(
                    "unable to read @FillSource field [" + field.getName() + "]", e);
        }

        if (value == null) {
            throw new ExtensionConfigurationException(
                    "@FillSource field [" + field.getName() + "] is null; initialize it before the fill runs");
        }
        return value;
    }

    private Field locateSourceField(Class<?> testClass) {
        List<Field> annotated = ReflectionSupport.findFields(testClass,
                field -> field.isAnnotationPresent(FillSource.class), HierarchyTraversalMode.TOP_DOWN);

        if (annotated.size() == 1) {
            return annotated.getFirst();
        }
        if (annotated.size() > 1) {
            throw new ExtensionConfigurationException(
                    "expected exactly one @FillSource field but found " + annotated.size());
        }

        // no explicit @FillSource: fall back to a single DataSource/Connection field
        List<Field> candidates = ReflectionSupport.findFields(testClass,
                field -> DataSource.class.isAssignableFrom(field.getType())
                        || Connection.class.isAssignableFrom(field.getType()),
                HierarchyTraversalMode.TOP_DOWN);

        if (candidates.size() == 1) {
            return candidates.getFirst();
        }
        if (candidates.isEmpty()) {
            throw new ExtensionConfigurationException(
                    "no fill source found; annotate a DataSource or Connection field with @FillSource");
        }
        throw new ExtensionConfigurationException(
                "multiple DataSource/Connection fields found; mark exactly one with @FillSource");
    }
}
