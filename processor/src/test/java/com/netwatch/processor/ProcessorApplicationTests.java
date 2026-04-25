package com.netwatch.processor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ProcessorApplicationTests {

    @Test
    void mainClassExists() {
        // Verify the application entry point compiles and is discoverable.
        // Full Spring context test requires a running Kafka broker
        // and is exercised in integration tests.
        assertDoesNotThrow(() ->
            ProcessorApplication.class.getDeclaredMethod("main", String[].class));
    }
}
