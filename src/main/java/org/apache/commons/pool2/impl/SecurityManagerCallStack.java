package org.apache.commons.pool2.impl;

import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 通过 {@link SecurityManager} 实现堆栈跟踪，需要运行时权限，如果无权限，通过抛出异常实现堆栈跟踪
 */
public class SecurityManagerCallStack implements CallStack {

    // 自定义安全管理器
    private static class PrivateSecurityManager extends SecurityManager {

        // 记录堆栈
        private List<WeakReference<Class<?>>> getCallStack() {
            final Stream<WeakReference<Class<?>>> map = Stream.of(getClassContext()).map(WeakReference::new);
            return map.collect(Collectors.toList());
        }
    }

    // 堆栈快照
    private static class Snapshot {
        private final long timestampMillis = System.currentTimeMillis();
        private final List<WeakReference<Class<?>>> stack;

        private Snapshot(final List<WeakReference<Class<?>>> stack) {
            this.stack = stack;
        }
    }

    private final String messageFormat;

    //@GuardedBy("dateFormat")
    private final DateFormat dateFormat;

    private final PrivateSecurityManager securityManager;

    private volatile Snapshot snapshot;

    /**
     * Creates a new instance.
     *
     * @param messageFormat message format
     * @param useTimestamp whether to format the dates in the output message or not
     */
    public SecurityManagerCallStack(final String messageFormat, final boolean useTimestamp) {
        this.messageFormat = messageFormat;
        this.dateFormat = useTimestamp ? new SimpleDateFormat(messageFormat) : null;
        this.securityManager = AccessController.doPrivileged((PrivilegedAction<PrivateSecurityManager>) PrivateSecurityManager::new);
    }

    @Override
    public void clear() {
        snapshot = null;
    }

    @Override
    public void fillInStackTrace() {
        snapshot = new Snapshot(securityManager.getCallStack());
    }

    @Override
    public boolean printStackTrace(final PrintWriter writer) {
        final Snapshot snapshotRef = this.snapshot;
        if (snapshotRef == null) {
            return false;
        }

        // 输出抬头信息
        final String message;
        if (dateFormat == null) {
            message = messageFormat;
        } else {
            synchronized (dateFormat) {
                message = dateFormat.format(Long.valueOf(snapshotRef.timestampMillis));
            }
        }
        writer.println(message);

        // 输出堆栈
        snapshotRef.stack.forEach(reference -> writer.println(reference.get()));
        return true;
    }
}
