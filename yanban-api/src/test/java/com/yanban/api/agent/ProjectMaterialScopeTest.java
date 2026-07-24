package com.yanban.api.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProjectMaterialScopeTest {

    @Test
    void extractsConcreteChineseAdjacentPathsAndNormalizesSeparators() {
        assertThat(ProjectMaterialScope.explicitRelativePaths(
                "读取IEEE_TAES_regular_template.tex，然后读取good_code\\s2\\main(multi_s2_fix).py。"))
                .containsExactlyInAnyOrder(
                        "ieee_taes_regular_template.tex",
                        "good_code/s2/main(multi_s2_fix).py");
    }

    @Test
    void doesNotTreatToolNamesOrDirectoriesAsFileMaterials() {
        assertThat(ProjectMaterialScope.explicitRelativePaths(
                "Use project_read_file and project_search inside good_code/s2/."))
                .isEmpty();
    }

    @Test
    void resolvesABasenameOnlyWhenTheCurrentManifestMatchIsUnique() {
        ProjectMaterialScope.MaterialPathResolution unique = ProjectMaterialScope.resolveAgainstManifest(
                Set.of("sort.java"), List.of("README.md", "src/main/java/Sort.java"));
        ProjectMaterialScope.MaterialPathResolution ambiguous = ProjectMaterialScope.resolveAgainstManifest(
                Set.of("sort.java"), List.of("src/main/java/Sort.java", "examples/Sort.java"));

        assertThat(unique.paths()).containsExactly("src/main/java/sort.java");
        assertThat(unique.ambiguous()).isFalse();
        assertThat(ambiguous.paths()).isEmpty();
        assertThat(ambiguous.ambiguous()).isTrue();
        assertThat(ambiguous.ambiguities().get("sort.java"))
                .containsExactly("examples/Sort.java", "src/main/java/Sort.java");
    }

    @Test
    void canonicalSandboxResolutionPreservesManifestCaseAndCollapsesAliases() {
        ProjectMaterialScope.CanonicalPathResolution resolution =
                ProjectMaterialScope.resolveCanonicalPaths(
                        Set.of("Sort.java", "src/main/java/Sort.java"),
                        List.of("src/main/java/Sort.java"));

        assertThat(resolution.valid()).isTrue();
        assertThat(resolution.paths()).containsExactly("src/main/java/Sort.java");
        assertThat(resolution.canonicalAlias("Sort.java")).isEqualTo("src/main/java/Sort.java");
        assertThat(resolution.canonicalAlias("src/main/java/sort.java"))
                .isEqualTo("src/main/java/Sort.java");
    }

    @Test
    void canonicalSandboxResolutionReportsMissingAndAmbiguousPaths() {
        ProjectMaterialScope.CanonicalPathResolution resolution =
                ProjectMaterialScope.resolveCanonicalPaths(
                        Set.of("Sort.java", "Missing.java"),
                        List.of("src/main/java/Sort.java", "examples/Sort.java"));

        assertThat(resolution.valid()).isFalse();
        assertThat(resolution.paths()).isEmpty();
        assertThat(resolution.missingPaths()).containsExactly("Missing.java");
        assertThat(resolution.ambiguities().get("Sort.java"))
                .containsExactly("examples/Sort.java", "src/main/java/Sort.java");
    }
}
