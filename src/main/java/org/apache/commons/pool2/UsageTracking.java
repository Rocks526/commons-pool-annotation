package org.apache.commons.pool2;

/**
 * 堆栈跟踪接口，实现此接口，代表支持池对象的堆栈追踪能力
 * @param <T>
 */
public interface UsageTracking<T> {

    /**
     * 每次使用池对象时，通过此方法记录池对象的堆栈记录
     * @param pooledObject
     */
    void use(T pooledObject);

}
