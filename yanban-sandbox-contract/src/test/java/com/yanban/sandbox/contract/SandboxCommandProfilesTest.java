package com.yanban.sandbox.contract;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class SandboxCommandProfilesTest {
    @Test
    void allowsOnlyOneSafeJavaSourceInSourceFileMode() {
        assertDoesNotThrow(() -> SandboxCommandProfiles.requireAllowed(
                List.of("java", "src/main/java/xhs_1111.java")));
        assertThrows(IllegalArgumentException.class, () -> SandboxCommandProfiles.requireAllowed(
                List.of("java", "-cp", "src/main/java", "xhs_1111")));
        assertThrows(IllegalArgumentException.class, () -> SandboxCommandProfiles.requireAllowed(
                List.of("java", "../xhs_1111.java")));
    }

    @Test
    void allowsOnlyFixedMultiLanguageRunnerProfiles() {
        assertDoesNotThrow(() -> SandboxCommandProfiles.requireAllowed(
                List.of("yanban-runner", "python", "experiments/model.py")));
        assertDoesNotThrow(() -> SandboxCommandProfiles.requireAllowed(
                List.of("yanban-runner", "c", "src/model.c")));
        assertDoesNotThrow(() -> SandboxCommandProfiles.requireAllowed(
                List.of("yanban-runner", "cpp", "src/model.cpp")));
        assertThrows(IllegalArgumentException.class, () -> SandboxCommandProfiles.requireAllowed(
                List.of("yanban-runner", "matlab", "model.m")));
        assertThrows(IllegalArgumentException.class, () -> SandboxCommandProfiles.requireAllowed(
                List.of("yanban-runner", "python", "../secret.py")));
        assertThrows(IllegalArgumentException.class, () -> SandboxCommandProfiles.requireAllowed(
                List.of("python3", "model.py")));
    }
}
