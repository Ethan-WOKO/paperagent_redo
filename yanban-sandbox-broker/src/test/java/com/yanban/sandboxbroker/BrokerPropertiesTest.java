package com.yanban.sandboxbroker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BrokerPropertiesTest {
    @Test void localAcceptanceIsExplicitWindowsLoopbackOnly() {
        BrokerProperties value = new BrokerProperties();
        value.setEnabled(true);
        value.setMode(BrokerProperties.Mode.LOCAL_ACCEPTANCE);
        value.setBindAddress("127.0.0.1");
        value.setRemoteAccess(false);
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("windows");
        assertThat(value.isProcessIdentitySafe()).isEqualTo(windows);
        value.setBindAddress("0.0.0.0");
        assertThat(value.isProcessIdentitySafe()).isFalse();
        value.setBindAddress("127.0.0.1");
        value.setRemoteAccess(true);
        assertThat(value.isProcessIdentitySafe()).isFalse();
    }
}
