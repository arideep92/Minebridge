package com.minebridge.hook;

/**
 * Utility methods for optional hooks and safe reflection.
 */
public final class HookUtil {

    private HookUtil() {
    }

    /**
     * Checks if a thrown {@link Throwable} is a fatal JVM error that should
     * never be caught or suppressed by optional integration error handling.
     * Propagates {@link VirtualMachineError} (OutOfMemoryError, StackOverflowError,
     * InternalError, UnknownError) and {@link ThreadDeath}.
     *
     * @param t the throwable to check
     */
    @SuppressWarnings("removal")
    public static void checkFatal(Throwable t) {
        if (t instanceof VirtualMachineError) {
            throw (VirtualMachineError) t;
        }
        if (t instanceof ThreadDeath) {
            throw (ThreadDeath) t;
        }
    }
}
