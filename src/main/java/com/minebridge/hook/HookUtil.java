package com.minebridge.hook;

import java.lang.reflect.Method;

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

    /**
     * Safely attempts to load a class using the provided ClassLoader first, falling back to Class.forName.
     * Returns null if the class cannot be found.
     */
    public static Class<?> loadClass(ClassLoader loader, String className) {
        if (loader != null) {
            try {
                return loader.loadClass(className);
            } catch (ClassNotFoundException ignored) {
            }
        }
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    /**
     * Safely finds a public method on the target class. Returns null if not found.
     */
    public static Method findMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        if (clazz == null) {
            return null;
        }
        try {
            return clazz.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
