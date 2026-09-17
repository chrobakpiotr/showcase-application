package com.cp.ecommerce.adapter.common.testfixtures;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import org.yaml.snakeyaml.Yaml;

/**
 * Loads {@code etc/asyncapi/asyncapi.yml} once per test JVM and exposes each message schema's declared property names, so
 * producer-side contract tests (e.g. {@code OrderMessageContractTest}, {@code OrderAnalyticsEventContractTest}) can assert the
 * actual wire payload against the spec itself instead of a hand-maintained {@code Set.of(...)} duplicating its field list.
 *
 * <p>
 * Without this, the AsyncAPI spec and the contract tests could silently drift apart - e.g. a field renamed on the DTO would
 * only ever break the spec's accuracy as documentation, never the build. Reading the spec directly here turns it into an
 * enforced, load-bearing contract: renaming/removing/adding a field on one side without updating the other now fails the
 * affected module's tests.
 * </p>
 */
public final class AsyncApiSchema {

    private static final String SPEC_PATH_PROPERTY = "asyncApiSpecPath";

    private static final Map<String, Object> SPEC = loadSpec();

    private AsyncApiSchema() {

    }

    /**
     * @param schemaName name of a schema under {@code components.schemas} in the AsyncAPI spec, e.g. {@code "OrderMessage"}
     * @return the property names declared for that schema, in spec-declaration order
     */
    @SuppressWarnings("unchecked")
    public static Set<String> declaredProperties(final String schemaName) {

        final Map<String, Object> components = (Map<String, Object>) SPEC.get("components");
        final Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        final Map<String, Object> schema = (Map<String, Object>) schemas.get(schemaName);
        if (schema == null) {

            throw new IllegalArgumentException(
                    "No schema named '" + schemaName + "' found under components.schemas in " + specPath());
        }
        final Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        return properties.keySet();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadSpec() {

        try (InputStream in = Files.newInputStream(Path.of(specPath()))) {

            return new Yaml().load(in);
        } catch (final IOException e) {

            throw new UncheckedIOException("Failed reading AsyncAPI spec at " + specPath(), e);
        }
    }

    private static String specPath() {

        final String path = System.getProperty(SPEC_PATH_PROPERTY);
        if (path == null || path.isBlank()) {

            throw new IllegalStateException(
                    "System property '" + SPEC_PATH_PROPERTY
                            + "' is not set - it is configured in the root build.gradle's shared test{} block "
                            + "and must be present on the test JVM's command line.");
        }
        return path;
    }

}
