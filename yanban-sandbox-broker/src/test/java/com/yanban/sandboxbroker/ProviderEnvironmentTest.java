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
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("windows"))
            assertThat(builder.environment()).containsOnlyKeys("HOME", "USERPROFILE", "LOCALAPPDATA", "APPDATA", "XDG_CONFIG_HOME", "XDG_DATA_HOME", "XDG_STATE_HOME", "SBX_NO_TELEMETRY");
        else assertThat(builder.environment()).containsOnlyKeys("HOME", "XDG_CONFIG_HOME", "XDG_DATA_HOME", "XDG_STATE_HOME", "SBX_NO_TELEMETRY");
        assertThat(builder.environment()).doesNotContainKey("DATABASE_PASSWORD");
    }

    @Test void e2bReceivesOnlyItsKeyAndProcessBufferingFlag() {
        BrokerProperties properties = new BrokerProperties();
        properties.setProvider(BrokerProperties.Provider.E2B);
        properties.setE2bApiKey("server-side-key-that-is-long-enough");
        ProcessBuilder builder = new ProcessBuilder("ignored");
        builder.environment().put("MYSQL_PASSWORD", "must-not-leak");

        new ProviderEnvironment(properties).apply(builder);

        assertThat(builder.environment()).containsOnly(
                org.assertj.core.data.MapEntry.entry("E2B_API_KEY", "server-side-key-that-is-long-enough"),
                org.assertj.core.data.MapEntry.entry("PYTHONUNBUFFERED", "1"));
    }
}
