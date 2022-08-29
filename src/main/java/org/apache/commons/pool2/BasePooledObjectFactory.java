package org.apache.commons.pool2;

/**
 * PooledObjectFactory的基础实现
 * @param <T>
 * @param <E>
 */
public abstract class BasePooledObjectFactory<T, E extends Exception> extends BaseObject implements PooledObjectFactory<T, E> {

    // 激活对象，空操作
    @Override
    public void activateObject(final PooledObject<T> p) throws E {
        // The default implementation is a no-op.
    }

    // 创建对象，将makeObject()的创建对象并包装分离成两个方法
    public abstract T create() throws E;

    // 销毁对象，空操作
    @Override
    public void destroyObject(final PooledObject<T> p)
        throws E  {
        // The default implementation is a no-op.
    }

    // 默认通过 create + wrap 实现，子类不需要实现此方法
    @Override
    public PooledObject<T> makeObject() throws E {
        return wrap(create());
    }


    // 钝化对象，空操作
    @Override
    public void passivateObject(final PooledObject<T> p)
        throws E {
        // The default implementation is a no-op.
    }


    // 校验对象有效性，默认true
    @Override
    public boolean validateObject(final PooledObject<T> p) {
        return true;
    }

    // 将池对象包装为PooledObject类型
    public abstract PooledObject<T> wrap(T obj);
}
