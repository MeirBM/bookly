package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Turn-1 criterion 1.21: no request DTO carrying a credential exposes it through {@code toString()}.
 *
 * <p>Why the criterion exists: a Java record generates a {@code toString()} that prints every
 * component, and Spring logs a handler's arguments at TRACE. Together those put the plaintext
 * password of every registration and every login into the log stream without a single line of
 * application code ever logging it. Criterion 1.9 does not catch it, because 1.9 only reaches the
 * levels a deployment runs at.
 *
 * <p>A unit test with no Spring context, as spec part 4 requires, and it names no class from
 * {@code backend/src/main}. The subjects are derived from the contract: the request-body schemas of
 * {@code docs/api/turn-1-openapi.json} that declare a credential property, matched to compiled
 * classes by name through reflection, the way any external tool would. A request DTO added to the
 * contract later with a credential property is therefore covered without anyone remembering to add
 * it here.
 *
 * <p>Scope note: the criterion says <em>request</em> DTO, so response schemas are not subjects
 * here even when they carry a token. See the turn audit for the separate finding about
 * {@code TokenPairResponse}.
 */
class CredentialMaskingTest {

    /** Property names the contract uses for a secret the caller sends. */
    private static final Set<String> CREDENTIAL_PROPERTIES = Set.of("password", "refreshToken");

    private static final String SECRET = "s3cr3t-value-that-must-never-be-printed-b7f1";  // allow-secret: test fixture, never a real credential

    private static final ObjectMapper JSON = new ObjectMapper();

    @TestFactory
    @DisplayName("1.21 no request DTO prints a credential in toString()")
    List<DynamicTest> credentialsAreMaskedInToString() {
        Map<String, List<String>> subjects = credentialCarryingRequestSchemas();

        assertThat(subjects)
                .as("request schemas in the OpenAPI contract that carry a credential; none means "
                        + "the criterion has nothing to decide and the derivation is broken")
                .isNotEmpty();

        List<DynamicTest> cases = new ArrayList<>();
        subjects.forEach((schema, credentialProperties) -> {
            List<Class<?>> implementations = compiledClassesNamed(schema);
            cases.add(dynamicTest(schema, () -> {
                assertThat(implementations)
                        .as("a compiled class named %s, the request schema the contract documents", schema)
                        .isNotEmpty();
                for (Class<?> type : implementations) {
                    List<String> present = credentialFieldsOf(type, credentialProperties);
                    assertThat(present)
                            .as("%s must carry the credential properties the contract declares", type.getName())
                            .isNotEmpty();

                    String rendered = String.valueOf(instantiateWithSecret(type, present));
                    assertThat(rendered)
                            .as("%s.toString() must not print the value of %s — Spring logs handler "
                                    + "arguments at TRACE, so whatever this prints reaches the log",
                                    type.getName(), present)
                            .doesNotContain(SECRET);
                    assertThat(rendered)
                            .as("%s.toString() must still be useful for debugging", type.getName())
                            .isNotBlank();
                }
            }));
        });
        return cases;
    }

    // ------------------------------------------------- derivation from the contract

    /** Request-body schema names that declare a credential property, mapped to those properties. */
    private static Map<String, List<String>> credentialCarryingRequestSchemas() {
        JsonNode contract = openApiContract();
        Set<String> requestSchemas = new LinkedHashSet<>();

        JsonNode paths = contract.path("paths");
        for (Iterator<String> pathNames = paths.fieldNames(); pathNames.hasNext(); ) {
            JsonNode operations = paths.path(pathNames.next());
            for (Iterator<String> methods = operations.fieldNames(); methods.hasNext(); ) {
                JsonNode content = operations.path(methods.next()).path("requestBody").path("content");
                for (Iterator<String> mediaTypes = content.fieldNames(); mediaTypes.hasNext(); ) {
                    String ref = content.path(mediaTypes.next()).path("schema").path("$ref").asText("");
                    if (!ref.isBlank()) {
                        requestSchemas.add(ref.substring(ref.lastIndexOf('/') + 1));
                    }
                }
            }
        }

        Map<String, List<String>> carriers = new LinkedHashMap<>();
        JsonNode schemas = contract.path("components").path("schemas");
        for (String schema : requestSchemas) {
            JsonNode properties = schemas.path(schema).path("properties");
            List<String> credentials = new ArrayList<>();
            properties.fieldNames().forEachRemaining(property -> {
                if (CREDENTIAL_PROPERTIES.contains(property)) {
                    credentials.add(property);
                }
            });
            if (!credentials.isEmpty()) {
                carriers.put(schema, credentials);
            }
        }
        return carriers;
    }

    /** The committed contract, which criteria 1.16 and 1.20 also treat as a source of truth. */
    private static JsonNode openApiContract() {
        Path directory = Path.of("").toAbsolutePath();
        for (Path candidate = directory; candidate != null; candidate = candidate.getParent()) {
            Path contract = candidate.resolve("docs/api/turn-1-openapi.json");
            if (Files.isRegularFile(contract)) {
                try {
                    return JSON.readTree(Files.readString(contract));
                } catch (IOException e) {
                    throw new IllegalStateException("could not read " + contract, e);
                }
            }
        }
        throw new IllegalStateException(
                "docs/api/turn-1-openapi.json not found above " + directory + "; the subjects of this test "
                        + "are derived from it and cannot be guessed");
    }

    // ------------------------------------------------------------------- reflection

    private static List<Class<?>> compiledClassesNamed(String simpleName) {
        Path classes = compiledApplicationClasses();
        List<Class<?>> matches = new ArrayList<>();
        try (Stream<Path> tree = Files.walk(classes)) {
            for (Path file : tree.filter(f -> f.toString().endsWith(".class")).toList()) {
                String binaryName = classes.relativize(file).toString()
                        .replace(java.io.File.separatorChar, '.')
                        .replaceAll("\\.class$", "");
                if (!binaryName.endsWith("." + simpleName) && !binaryName.equals(simpleName)) {
                    continue;
                }
                try {
                    Class<?> type = Class.forName(binaryName, false, CredentialMaskingTest.class.getClassLoader());
                    if (!type.isInterface() && !type.isEnum() && !Modifier.isAbstract(type.getModifiers())) {
                        matches.add(type);
                    }
                } catch (Throwable unloadable) {
                    // Not loadable in the test JVM; it cannot be the DTO under test.
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("could not walk " + classes, e);
        }
        return matches;
    }

    private static List<String> credentialFieldsOf(Class<?> type, List<String> wanted) {
        Set<String> names = new TreeSet<>();
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                if (wanted.contains(component.getName())) {
                    names.add(component.getName());
                }
            }
        } else {
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && wanted.contains(field.getName())) {
                    names.add(field.getName());
                }
            }
        }
        return List.copyOf(names);
    }

    /** The directory holding the application's compiled classes, found next to the test classes. */
    private static Path compiledApplicationClasses() {
        try {
            Path testClasses = Path.of(CredentialMaskingTest.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            Path classes = testClasses.getParent().resolve("classes");
            if (!Files.isDirectory(classes)) {
                throw new IllegalStateException("expected compiled application classes at " + classes);
            }
            return classes;
        } catch (URISyntaxException e) {
            throw new IllegalStateException("could not locate the compiled classes", e);
        }
    }

    // ---------------------------------------------------------------- instantiation

    /** Builds an instance whose credential fields hold {@link #SECRET} and whose others hold sample text. */
    private static Object instantiateWithSecret(Class<?> type, List<String> credentialFields) throws Exception {
        if (type.isRecord()) {
            RecordComponent[] components = type.getRecordComponents();
            Class<?>[] parameterTypes = new Class<?>[components.length];
            Object[] arguments = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                parameterTypes[i] = components[i].getType();
                arguments[i] = sampleValue(components[i].getName(), components[i].getType(), credentialFields);
            }
            Constructor<?> canonical = type.getDeclaredConstructor(parameterTypes);
            canonical.setAccessible(true);
            return canonical.newInstance(arguments);
        }

        Constructor<?> noArgs;
        try {
            noArgs = type.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new AssertionError(type.getName()
                    + " carries a credential but this test cannot construct it: it is neither a record "
                    + "nor a class with a no-argument constructor. Extend the test rather than the DTO.", e);
        }
        noArgs.setAccessible(true);
        Object instance = noArgs.newInstance();
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            Object value = sampleValue(field.getName(), field.getType(), credentialFields);
            if (value != null) {
                field.setAccessible(true);
                field.set(instance, value);
            }
        }
        return instance;
    }

    private static Object sampleValue(String name, Class<?> type, List<String> credentialFields) {
        if (type == String.class) {
            return credentialFields.contains(name) ? SECRET : "sample-" + name;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return 'x';
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == int.class) {
            return 0;
        }
        return null;
    }
}
