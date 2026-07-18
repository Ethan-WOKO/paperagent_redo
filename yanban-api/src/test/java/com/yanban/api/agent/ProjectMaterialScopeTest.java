package com.yanban.api.agent;

import static org.assertj.core.api.Assertions.assertThat;

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
}
