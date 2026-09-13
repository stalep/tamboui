/*
 * Copyright TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.backend.panama.unix;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs({OS.LINUX, OS.MAC})
class SignalBridgeTest {

    @Test
    void dispatchesOperatingSystemWinchSignalAndRestoresPreviousHandler() throws Exception {
        CountDownLatch restored = new CountDownLatch(1);

        try (SignalBridge previous = SignalBridge.onWindowChange(restored::countDown)) {
            assertNotNull(previous);
            CountDownLatch received = new CountDownLatch(1);
            try (SignalBridge bridge = SignalBridge.onWindowChange(received::countDown)) {
                assertNotNull(bridge);
                sendWinch();
                assertTrue(received.await(5, TimeUnit.SECONDS), "SIGWINCH was not dispatched");
            }

            sendWinch();
            assertTrue(restored.await(5, TimeUnit.SECONDS), "Previous SIGWINCH handler was not restored");
        }
    }

    private static void sendWinch() throws Exception {
        Process kill = new ProcessBuilder(
                "kill", "-WINCH", Long.toString(ProcessHandle.current().pid()))
                .start();
        assertEquals(0, kill.waitFor());
    }
}
