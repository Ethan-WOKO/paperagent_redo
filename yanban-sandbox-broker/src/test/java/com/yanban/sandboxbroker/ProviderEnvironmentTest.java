package com.yanban.sandboxbroker;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProviderEnvironmentTest {
    @Test void clearsInheritedSecretsAndAddsOnlyConfiguredProviderIdentity() {
        BrokerProperties properties = new BrokerProperties();
        Path root = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().resolve("sbx-identity");
        properties.setProviderHome(root.toString());
        properties.setProviderConfigHome(root.resolve("config").toString());
        properties.setProviderDataHome(root.resolve("data").toString());
        properties.setProviderStateHome(root.resolve("state").toString());
        ProcessBuilder builder = new ProcessBuilder("ignored");
        builder.environment().put("DATABASE_PASSWORD", "must-not-leak");
        new ProviderEnvironment(properties).apply(builder);
        assertThat(builder.environment()).containsOnlyKeys("HOME", "XDG_CONFIG_HOME", "XDG_DATA_HOME", "XDG_STATE_HOME", "SBX_NO_TELEMETRY");
        assertThat(builder.environment()).doesNotContainKey("DATABASE_PASSWORD");
    }
}
