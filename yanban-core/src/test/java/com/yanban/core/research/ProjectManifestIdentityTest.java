package com.yanban.core.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectManifestIdentityTest {
    private static final FileHash A = new FileHash("a".repeat(64));
    private static final FileHash B = new FileHash("b".repeat(64));

    @Test
    void identityIsStableAcrossInputOrderAndChangesWithContent() {
        var first = new ProjectManifestIdentity.Entry(new ProjectRelativePath("paper/main.tex"), A, 12);
        var second = new ProjectManifestIdentity.Entry(new ProjectRelativePath("src/Main.java"), B, 34);

        assertThat(ProjectManifestIdentity.derive(List.of(first, second)))
                .isEqualTo(ProjectManifestIdentity.derive(List.of(second, first)));
        assertThat(ProjectManifestIdentity.derive(List.of(first, second)))
                .isNotEqualTo(ProjectManifestIdentity.derive(List.of(first,
                        new ProjectManifestIdentity.Entry(second.relativePath(), A, second.sizeBytes()))));
    }

    @Test
    void supportsEmptyAndRejectsDuplicateOrNonPortableManifests() {
        assertThat(ProjectManifestIdentity.derive(List.of()).value()).matches("[a-f0-9]{64}");
        var entry = new ProjectManifestIdentity.Entry(new ProjectRelativePath("paper.tex"), A, 1);
        assertThatThrownBy(() -> ProjectManifestIdentity.derive(List.of(entry, entry)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProjectRelativePath("../paper.tex"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
