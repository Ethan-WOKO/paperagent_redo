package com.yanban.sandboxbroker;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Complete provider-process environment. Provider secrets are never forwarded into a user sandbox. */
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
        if (properties.getProvider() == BrokerProperties.Provider.E2B) {
            values.put("E2B_API_KEY", properties.getE2bApiKey());
            values.put("PYTHONUNBUFFERED", "1");
            return Map.copyOf(values);
        }
        values.put("HOME", properties.getProviderHome());
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("windows")) {
            values.put("USERPROFILE", properties.getProviderHome());
            values.put("LOCALAPPDATA", properties.getProviderDataHome());
            values.put("APPDATA", properties.getProviderConfigHome());
        }
        values.put("XDG_CONFIG_HOME", properties.getProviderConfigHome());
        values.put("XDG_DATA_HOME", properties.getProviderDataHome());
        values.put("XDG_STATE_HOME", properties.getProviderStateHome());
        values.put("SBX_NO_TELEMETRY", "1");
        return Map.copyOf(values);
    }
}
