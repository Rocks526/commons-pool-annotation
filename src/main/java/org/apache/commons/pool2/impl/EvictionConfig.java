package org.apache.commons.pool2.impl;

import java.time.Duration;

/**
 * 空闲对象回收配置
 */
public class EvictionConfig {

    // 默认值，空闲对象永不回收
    private static final Duration MAX_DURATION = Duration.ofMillis(Long.MAX_VALUE);
    // 空闲时间阈值，超过此时间不关心空闲数量，直接强制回收 (默认策略下)
    private final Duration idleEvictDuration;
    // 空闲时间阈值，超过此时间并且空闲对象数小于minIdle，会回收空闲对象 (默认策略下)
    private final Duration idleSoftEvictDuration;
    // 最小空闲数
    private final int minIdle;


    public EvictionConfig(final Duration idleEvictDuration, final Duration idleSoftEvictDuration, final int minIdle) {
        this.idleEvictDuration = PoolImplUtils.isPositive(idleEvictDuration) ? idleEvictDuration : MAX_DURATION;
        this.idleSoftEvictDuration = PoolImplUtils.isPositive(idleSoftEvictDuration) ? idleSoftEvictDuration : MAX_DURATION;
        this.minIdle = minIdle;
    }

    @Deprecated
    public EvictionConfig(final long poolIdleEvictMillis, final long poolIdleSoftEvictMillis, final int minIdle) {
        this(Duration.ofMillis(poolIdleEvictMillis), Duration.ofMillis(poolIdleSoftEvictMillis), minIdle);
    }

    public Duration getIdleEvictDuration() {
        return idleEvictDuration;
    }

    @Deprecated
    public long getIdleEvictTime() {
        return idleEvictDuration.toMillis();
    }

    @Deprecated
    public Duration getIdleEvictTimeDuration() {
        return idleEvictDuration;
    }

    public Duration getIdleSoftEvictDuration() {
        return idleSoftEvictDuration;
    }

    @Deprecated
    public long getIdleSoftEvictTime() {
        return idleSoftEvictDuration.toMillis();
    }

    @Deprecated
    public Duration getIdleSoftEvictTimeDuration() {
        return idleSoftEvictDuration;
    }

    public int getMinIdle() {
        return minIdle;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("EvictionConfig [idleEvictDuration=");
        builder.append(idleEvictDuration);
        builder.append(", idleSoftEvictDuration=");
        builder.append(idleSoftEvictDuration);
        builder.append(", minIdle=");
        builder.append(minIdle);
        builder.append("]");
        return builder.toString();
    }
}
