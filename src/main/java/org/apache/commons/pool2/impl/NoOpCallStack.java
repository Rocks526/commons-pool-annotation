package org.apache.commons.pool2.impl;

import java.io.PrintWriter;

/**
 * 空实现
 */
public class NoOpCallStack implements CallStack {

    // 单例模式
    public static final CallStack INSTANCE = new NoOpCallStack();

    private NoOpCallStack() {
    }

    @Override
    public void clear() {
        // no-op
    }

    @Override
    public void fillInStackTrace() {
        // no-op
    }

    @Override
    public boolean printStackTrace(final PrintWriter writer) {
        return false;
    }
}
