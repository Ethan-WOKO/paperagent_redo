package com.yanban.sandboxbroker;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** The complete, non-secret environment visible to every sbx subprocess. */
@Component
final class ProviderEnvironment {
    private final BrokerProperties properties;

    ProviderEnvironment(BrokerProperties properties) { this.properties = properties; }

    void apply(ProcessBuilder builder) {
        Map<String, String> environment = builder.environment();
        environment.clear();
        environment.putAll(values());
    }

    Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("HOME", properties.getProviderHome());
        values.put("XDG_CONFIG_HOME", properties.getProviderConfigHome());
        values.put("XDG_DATA_HOME", properties.getProviderDataHome());
        values.put("XDG_STATE_HOME", properties.getProviderStateHome());
        values.put("SBX_NO_TELEMETRY", "1");
        return Map.copyOf(values);
    }
}
