package com.yanban.api.project;

import java.util.List;

/** Server-owned compile/test profiles. The client never supplies argv or environment variables. */
public enum CandidateValidationProfile {
    MAVEN_TEST(List.of("mvn", "-o", "test")),
    MAVEN_VERIFY(List.of("mvn", "-o", "verify")),
    JAVA_SOURCE_RUN("java", ".java"),
    PYTHON_SOURCE_RUN("python", ".py"),
    C_SOURCE_RUN("c", ".c"),
    CPP_SOURCE_RUN("cpp", ".cc", ".cpp", ".cxx");

    private final List<String> argv;
    private final String runnerLanguage;
    private final List<String> suffixes;

    CandidateValidationProfile(List<String> argv) {
        this.argv = List.copyOf(argv);
        this.runnerLanguage = null;
        this.suffixes = List.of();
    }

    CandidateValidationProfile(String runnerLanguage, String... suffixes) {
        this.argv = List.of();
        this.runnerLanguage = runnerLanguage;
        this.suffixes = List.of(suffixes);
    }

    public boolean sourceProfile() { return runnerLanguage != null; }

    public boolean accepts(String path) {
        return sourceProfile() && suffixes.stream().anyMatch(path::endsWith);
    }

    public List<String> argv(String selectedSource) {
        if (sourceProfile()) {
            if (selectedSource == null || selectedSource.isBlank() || !accepts(selectedSource)) {
                throw new IllegalArgumentException(name() + " requires one matching selected source");
            }
            return List.of("yanban-runner", runnerLanguage, selectedSource);
        }
        return argv;
    }
}
