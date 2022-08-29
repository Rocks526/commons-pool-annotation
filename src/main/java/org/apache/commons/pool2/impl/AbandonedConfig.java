package org.apache.commons.pool2.impl;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.time.Duration;

import org.apache.commons.pool2.TrackedUse;
import org.apache.commons.pool2.UsageTracking;


/**
 * 线程池保护机制的配置类
 */
public class AbandonedConfig {

    // 泄露对象检测阈值，超过此值没有使用的对象认为已泄露，默认5分钟
    private static final Duration DEFAULT_REMOVE_ABANDONED_TIMEOUT_DURATION = Duration.ofMinutes(5);

    // 返回AbandonedConfig
    public static AbandonedConfig copy(final AbandonedConfig abandonedConfig) {
        return abandonedConfig == null ? null : new AbandonedConfig(abandonedConfig);
    }

    // 是否开启 借出对象时检测泄露对象
    private boolean removeAbandonedOnBorrow;

    // 是否开启 异步线程检测泄露对象
    private boolean removeAbandonedOnMaintenance;

    // 泄露对象检测的超时时间，即借出对象后，超过此时间没有使用，则认为对象泄露，默认5分钟
    private Duration removeAbandonedTimeoutDuration = DEFAULT_REMOVE_ABANDONED_TIMEOUT_DURATION;

    // 泄露对象销毁时，是否记录日志和堆栈信息
    private boolean logAbandoned;

    // logAbandoned开启时，是否记录完整堆栈
    private boolean requireFullStackTrace = true;

    // 记录泄露对象的日志输出
    private PrintWriter logWriter = new PrintWriter(new OutputStreamWriter(System.out, Charset.defaultCharset()));

    // 池对象是否实现了UsageTracking接口，是否开启记录对象堆栈追踪，用于记录最近使用时间和堆栈跟踪
    private boolean useUsageTracking;



    public AbandonedConfig() {
        // empty
    }

    @SuppressWarnings("resource")
    private AbandonedConfig(final AbandonedConfig abandonedConfig) {
        this.setLogAbandoned(abandonedConfig.getLogAbandoned());
        this.setLogWriter(abandonedConfig.getLogWriter());
        this.setRemoveAbandonedOnBorrow(abandonedConfig.getRemoveAbandonedOnBorrow());
        this.setRemoveAbandonedOnMaintenance(abandonedConfig.getRemoveAbandonedOnMaintenance());
        this.setRemoveAbandonedTimeout(abandonedConfig.getRemoveAbandonedTimeoutDuration());
        this.setUseUsageTracking(abandonedConfig.getUseUsageTracking());
        this.setRequireFullStackTrace(abandonedConfig.getRequireFullStackTrace());
    }

    public boolean getLogAbandoned() {
        return this.logAbandoned;
    }

    public PrintWriter getLogWriter() {
        return logWriter;
    }

    public boolean getRemoveAbandonedOnBorrow() {
        return this.removeAbandonedOnBorrow;
    }

    public boolean getRemoveAbandonedOnMaintenance() {
        return this.removeAbandonedOnMaintenance;
    }

    @Deprecated
    public int getRemoveAbandonedTimeout() {
        return (int) this.removeAbandonedTimeoutDuration.getSeconds();
    }

    public Duration getRemoveAbandonedTimeoutDuration() {
        return this.removeAbandonedTimeoutDuration;
    }

    public boolean getRequireFullStackTrace() {
        return requireFullStackTrace;
    }

    public boolean getUseUsageTracking() {
        return useUsageTracking;
    }

    public void setLogAbandoned(final boolean logAbandoned) {
        this.logAbandoned = logAbandoned;
    }

    public void setLogWriter(final PrintWriter logWriter) {
        this.logWriter = logWriter;
    }

    public void setRemoveAbandonedOnBorrow(final boolean removeAbandonedOnBorrow) {
        this.removeAbandonedOnBorrow = removeAbandonedOnBorrow;
    }

    public void setRemoveAbandonedOnMaintenance(final boolean removeAbandonedOnMaintenance) {
        this.removeAbandonedOnMaintenance = removeAbandonedOnMaintenance;
    }

    public void setRemoveAbandonedTimeout(final Duration removeAbandonedTimeout) {
        this.removeAbandonedTimeoutDuration = PoolImplUtils.nonNull(removeAbandonedTimeout, DEFAULT_REMOVE_ABANDONED_TIMEOUT_DURATION);
    }

    @Deprecated
    public void setRemoveAbandonedTimeout(final int removeAbandonedTimeoutSeconds) {
        setRemoveAbandonedTimeout(Duration.ofSeconds(removeAbandonedTimeoutSeconds));
    }

    public void setRequireFullStackTrace(final boolean requireFullStackTrace) {
        this.requireFullStackTrace = requireFullStackTrace;
    }

    public void setUseUsageTracking(final boolean useUsageTracking) {
        this.useUsageTracking = useUsageTracking;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("AbandonedConfig [removeAbandonedOnBorrow=");
        builder.append(removeAbandonedOnBorrow);
        builder.append(", removeAbandonedOnMaintenance=");
        builder.append(removeAbandonedOnMaintenance);
        builder.append(", removeAbandonedTimeoutDuration=");
        builder.append(removeAbandonedTimeoutDuration);
        builder.append(", logAbandoned=");
        builder.append(logAbandoned);
        builder.append(", logWriter=");
        builder.append(logWriter);
        builder.append(", useUsageTracking=");
        builder.append(useUsageTracking);
        builder.append("]");
        return builder.toString();
    }
}
