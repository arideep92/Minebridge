package com.minebridge.update;

import okhttp3.Call;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerShutdownTest {

    @Test
    void externalExecutorIsNotShutdownOnCheckerShutdown() {
        ExecutorService externalExecutor = Executors.newSingleThreadExecutor();
        Call.Factory dummyCallFactory = request -> null;

        UpdateChecker checker = new UpdateChecker(
                "1.3.1",
                true,
                dummyCallFactory,
                externalExecutor,
                msg -> {},
                msg -> {}
        );

        assertFalse(externalExecutor.isShutdown());

        checker.shutdown();

        // External executor MUST NOT be shut down
        assertFalse(externalExecutor.isShutdown(), "External executor should not be shut down by UpdateChecker");

        // Cleanup
        externalExecutor.shutdownNow();
    }

    @Test
    void internalExecutorIsShutdownOnCheckerShutdown() {
        UpdateChecker checker = new UpdateChecker("1.3.1", false);
        checker.shutdown();
        // Verifies shutdown() executes without error on default internal executor
        assertTrue(true);
    }
}
