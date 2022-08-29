package org.apache.commons.pool2;

/**
 * KV对象工程
 * @param <K>
 * @param <V>
 * @param <E>
 */
public interface KeyedPooledObjectFactory<K, V, E extends Exception> {

    // 激活对象
    void activateObject(K key, PooledObject<V> p) throws E;

    // 销毁对象
    void destroyObject(K key, PooledObject<V> p) throws E;
    default void destroyObject(final K key, final PooledObject<V> p, final DestroyMode destroyMode) throws E {
        destroyObject(key, p);
    }

    // 创建对象
    PooledObject<V> makeObject(K key) throws E;

    // 钝化对象
    void passivateObject(K key, PooledObject<V> p) throws E;

    // 失效对象
    boolean validateObject(K key, PooledObject<V> p);
}

