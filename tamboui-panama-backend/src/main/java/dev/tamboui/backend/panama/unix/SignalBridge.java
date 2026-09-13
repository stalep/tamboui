/*
 * Copyright TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.backend.panama.unix;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import dev.tamboui.terminal.BackendException;

/**
 * Bridges Unix signals through the JVM signal dispatcher.
 */
final class SignalBridge implements AutoCloseable {

    private static final Class<?> SIGNAL_CLASS = loadClass("sun.misc.Signal");
    private static final Class<?> SIGNAL_HANDLER_CLASS = loadClass("sun.misc.SignalHandler");
    private static final Constructor<?> SIGNAL_CONSTRUCTOR = constructor();
    private static final Method HANDLE_METHOD = handleMethod();

    private final Object signal;
    private final Object previousHandler;
    private boolean closed;

    private SignalBridge(String name, Runnable handler) {
        try {
            signal = SIGNAL_CONSTRUCTOR.newInstance(name);
            Object signalHandler = Proxy.newProxyInstance(
                    SignalBridge.class.getClassLoader(),
                    new Class<?>[] {SIGNAL_HANDLER_CLASS},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == SIGNAL_HANDLER_CLASS) {
                            handler.run();
                        }
                        return null;
                    });
            previousHandler = HANDLE_METHOD.invoke(null, signal, signalHandler);
        } catch (ReflectiveOperationException e) {
            throw new BackendException("Failed to install JVM signal handler for " + name, e);
        }
    }

    static SignalBridge onWindowChange(Runnable handler) {
        return new SignalBridge("WINCH", handler);
    }

    @Override
    public void close() {
        if (!closed) {
            try {
                HANDLE_METHOD.invoke(null, signal, previousHandler);
                closed = true;
            } catch (ReflectiveOperationException e) {
                throw new BackendException("Failed to restore JVM signal handler", e);
            }
        }
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new BackendException("JVM signal handling is unavailable", e);
        }
    }

    private static Constructor<?> constructor() {
        try {
            return SIGNAL_CLASS.getConstructor(String.class);
        } catch (NoSuchMethodException e) {
            throw new BackendException("JVM signal handling is unavailable", e);
        }
    }

    private static Method handleMethod() {
        try {
            return SIGNAL_CLASS.getMethod("handle", SIGNAL_CLASS, SIGNAL_HANDLER_CLASS);
        } catch (NoSuchMethodException e) {
            throw new BackendException("JVM signal handling is unavailable", e);
        }
    }
}
