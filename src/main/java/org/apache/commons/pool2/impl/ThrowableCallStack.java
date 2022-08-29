package org.apache.commons.pool2.impl;

import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

/**
 * 通过异常 {@link Throwable} 获取堆栈信息
 */
public class ThrowableCallStack implements CallStack {

    // 基于异常实现的堆栈打印
    private static class Snapshot extends Throwable {
        private static final long serialVersionUID = 1L;
        private final long timestampMillis = System.currentTimeMillis();
    }

    // 格式化信息
    private final String messageFormat;

    //@GuardedBy("dateFormat")
    private final DateFormat dateFormat;

    private volatile Snapshot snapshot;

    public ThrowableCallStack(final String messageFormat, final boolean useTimestamp) {
        this.messageFormat = messageFormat;
        this.dateFormat = useTimestamp ? new SimpleDateFormat(messageFormat) : null;
    }

    @Override
    public void clear() {
        snapshot = null;
    }

    @Override
    public void fillInStackTrace() {
        snapshot = new Snapshot();
    }

    @Override
    public synchronized boolean printStackTrace(final PrintWriter writer) {
        final Snapshot snapshotRef = this.snapshot;
        if (snapshotRef == null) {
            return false;
        }
        final String message;

        // 输出抬头信息
        if (dateFormat == null) {
            message = messageFormat;
        } else {
            synchronized (dateFormat) {
                message = dateFormat.format(Long.valueOf(snapshotRef.timestampMillis));
            }
        }
        writer.println(message);

        // 输出堆栈
        snapshotRef.printStackTrace(writer);
        return true;
    }
}
