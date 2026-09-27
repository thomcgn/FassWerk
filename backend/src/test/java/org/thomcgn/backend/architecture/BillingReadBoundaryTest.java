package org.thomcgn.backend.architecture;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class BillingReadBoundaryTest {
    private static final Pattern BILLING_REFERENCE = Pattern.compile("org\\.thomcgn\\.backend\\.billing\\.([\\w.]+)");

    @ParameterizedTest
    @ValueSource(strings = {"report", "shift"})
    void consumersUseOnlyTheExportedBillingApplicationPackage(String context) throws Exception {
        Path directory = Path.of("src/main/java/org/thomcgn/backend", context);
        assertThat(directory).isDirectory();
        try (var paths = Files.walk(directory)) {
            var sources = paths.filter(path -> path.toString().endsWith(".java")).toList();
            assertThat(sources).isNotEmpty();
            for (Path source : sources) {
                var references = BILLING_REFERENCE.matcher(Files.readString(source));
                while (references.find()) {
                    assertThat(references.group(1)).as("Billing dependency in %s", source)
                            .startsWith("application.");
                }
            }
        }
    }
}
