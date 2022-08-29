package org.apache.commons.pool2.impl;

import org.apache.commons.pool2.PooledObject;

/**
 * 空闲对象回收策略
 * @param <T>
 */
public interface EvictionPolicy<T> {

    /**
     * 判断空闲对象是否要回收
     * @param config    回收配置
     * @param underTest 空闲对象
     * @param idleCount 空闲数量
     * @return  是否要回收此对象
     */
    boolean evict(EvictionConfig config, PooledObject<T> underTest, int idleCount);
}
