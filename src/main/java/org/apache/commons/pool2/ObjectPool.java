package org.apache.commons.pool2;

import java.io.Closeable;
import java.util.NoSuchElementException;

/**
 * 对象池接口
 * @param <T>   池对象类型
 * @param <E>   池操作抛出异常类型
 */
public interface ObjectPool<T, E extends Exception> extends Closeable {

    // 使用工厂创建一个对象，钝化并且将它放入空闲对象池，此方法一般用于初始化时预加载一部分池对象
    void addObject() throws E, IllegalStateException, UnsupportedOperationException;
    default void addObjects(final int count) throws E {
        for (int i = 0; i < count; i++) {
            addObject();
        }
    }

    // 从池中借用一个对象
    // 要么调用PooledObjectFactory.makeObject方法创建，要么对一个空闲对象使用PooledObjectFactory.activeObject进行激活
    // 然后使用PooledObjectFactory.validateObject方法进行验证后再返回
    T borrowObject() throws E, NoSuchElementException, IllegalStateException;

    // 清除池中的所有空闲对象，释放其关联的资源（可选）
    // 清除空闲对象必须使用PooledObjectFactory.destroyObject方法
    void clear() throws E, UnsupportedOperationException;

    // 关闭池并释放关联的资源
    @Override
    void close();

    // 返回池中借出的对象数量，如果这个信息不可用，返回一个负数
    int getNumActive();

    // 返回池中空闲的对象数量，有可能是池中可供借出对象的近似值，如果这个信息无效，返回一个负数
    int getNumIdle();

    // 废弃一个对象
    // 对象必须是使用borrowObject方法从池中借出的，通常在对象发生了异常或其他问题时使用此方法废弃它，不再归还对象池
    void invalidateObject(T obj) throws E;
    default void invalidateObject(final T obj, final DestroyMode destroyMode) throws E {
        invalidateObject(obj);
    }

    // 将一个对象返还给池
    // 对象必须是使用borrowObject方法从池中借出的
    void returnObject(T obj) throws E;

}
