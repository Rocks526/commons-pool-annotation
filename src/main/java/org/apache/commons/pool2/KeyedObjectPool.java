package org.apache.commons.pool2;

import java.io.Closeable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * KV池，为每个K维护一个对象实例
 * @param <K>   Key
 * @param <V>   Value
 * @param <E>
 */
public interface KeyedObjectPool<K, V, E extends Exception> extends Closeable {

    // 预加载
    void addObject(K key) throws E, IllegalStateException, UnsupportedOperationException;
    default void addObjects(final Collection<K> keys, final int count) throws E, IllegalArgumentException {
        if (keys == null) {
            throw new IllegalArgumentException(PoolUtils.MSG_NULL_KEYS);
        }
        for (final K key : keys) {
            addObjects(key, count);
        }
    }
    default void addObjects(final K key, final int count) throws E, IllegalArgumentException {
        if (key == null) {
            throw new IllegalArgumentException(PoolUtils.MSG_NULL_KEY);
        }
        for (int i = 0; i < count; i++) {
            addObject(key);
        }
    }

    // 借出对象
    V borrowObject(K key) throws E, NoSuchElementException, IllegalStateException;

    // 清理池
    void clear() throws E, UnsupportedOperationException;
    void clear(K key) throws E, UnsupportedOperationException;

    // 关闭池
    @Override
    void close();


    // 获取Key列表
    default List<K> getKeys() {
        return Collections.emptyList();
    }

    // 获取借出数量
    int getNumActive();
    int getNumActive(K key);

    // 获取空闲数量
    int getNumIdle();
    int getNumIdle(K key);

    // 失效对象
    void invalidateObject(K key, V obj) throws E;
    default void invalidateObject(final K key, final V obj, final DestroyMode destroyMode) throws E {
        invalidateObject(key, obj);
    }

    // 归还对象
    void returnObject(K key, V obj) throws E;
}
