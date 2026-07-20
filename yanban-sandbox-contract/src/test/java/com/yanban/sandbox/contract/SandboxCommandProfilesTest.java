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
}
