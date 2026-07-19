package com.yanban.api.agent.sandbox;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SandboxCommandPolicyTest {
    private final SandboxCommandPolicy policy = new SandboxCommandPolicy();

    @Test void acceptsStructuredAllowlistedCommandWithEmptyEnvironment() {
        assertThatCode(() -> policy.validate(List.of("mvn", "-o", "test"), Map.of()))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validate(List.of("mvn", "-o", "test"), Map.of("LANG", "C.UTF-8")))
                .isInstanceOf(SandboxExecutionException.class);
    }

    @Test void rejectsShellAndUnlistedExecutable() {
        assertThatThrownBy(() -> policy.validate(List.of("bash", "-c", "mvn test"), Map.of()))
                .isInstanceOf(SandboxExecutionException.class)
                .extracting(ex -> ((SandboxExecutionException) ex).code())
                .isEqualTo(SandboxFailureCode.COMMAND_NOT_ALLOWED);
    }

    @Test void rejectsSecretsAndMultilineValues() {
        assertThatThrownBy(() -> policy.validate(List.of("java", "-version"), Map.of("API_TOKEN", "secret")))
                .isInstanceOf(SandboxExecutionException.class);
        assertThatThrownBy(() -> policy.validate(List.of("java", "-version\nwhoami"), Map.of()))
                .isInstanceOf(SandboxExecutionException.class);
    }

    @Test void rejectsMavenModulePathEscapes() {
        for (String module : List.of(".", "..", ".hidden", "../api", "api/core", "api\\core"))
            assertThatThrownBy(() -> policy.validate(List.of("mvn", "-o", "-pl", module, "test"), Map.of()))
                    .isInstanceOf(SandboxExecutionException.class);
        assertThatCode(() -> policy.validate(List.of("mvn", "-o", "-pl", "yanban-api,yanban-core", "test"), Map.of()))
                .doesNotThrowAnyException();
    }
}
