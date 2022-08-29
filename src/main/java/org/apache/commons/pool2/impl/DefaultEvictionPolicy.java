package org.apache.commons.pool2.impl;

import org.apache.commons.pool2.PooledObject;


/**
 * 默认的空闲对象回收策略
 * @param <T>
 */
public class DefaultEvictionPolicy<T> implements EvictionPolicy<T> {

    @Override
    public boolean evict(final EvictionConfig config, final PooledObject<T> underTest, final int idleCount) {
        // @formatter:off
        // 以下两种情况会回收：
        // 1.空闲时间大于idleEvictDuration
        // 2.空闲时间大于idleSoftEvictDuration、并且空闲对象数大于minIdle
        return config.getIdleSoftEvictDuration().compareTo(underTest.getIdleDuration()) < 0 &&
                config.getMinIdle() < idleCount ||
                config.getIdleEvictDuration().compareTo(underTest.getIdleDuration()) < 0;
        // @formatter:on
    }
}
