package com.yanban.sandboxbroker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BrokerControllerHealthTimeoutTest {
    @Test
    void providerColdStartAboveThreeSecondsFitsWithinFiniteHealthLimit() {
        long timeout = BrokerController.providerHealthTimeoutMillis();
        assertThat(timeout).isEqualTo(15_000L).isGreaterThan(8_700L).isLessThanOrEqualTo(30_000L);
    }
}
