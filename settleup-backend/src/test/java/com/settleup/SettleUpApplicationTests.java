package com.settleup;

import org.junit.jupiter.api.Test;

/**
 * Smoke test — verifies the project compiles and the test framework is wired correctly.
 * Full Spring context tests are in separate integration test classes (using Testcontainers).
 *
 * We intentionally do NOT use @SpringBootTest here to avoid requiring
 * Docker/Redis/RabbitMQ to run unit tests.
 */
class SettleUpApplicationTests {

    @Test
    void contextLoads() {
        // This test just confirms the project compiles successfully.
        // Integration tests that need a real DB use @Testcontainers.
    }
}
