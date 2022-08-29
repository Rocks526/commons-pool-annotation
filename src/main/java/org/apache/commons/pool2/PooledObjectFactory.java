package org.apache.commons.pool2;

/**
 * 池对象工厂，用于将对象的创建、初始化、销毁等操作与对象池解耦
 * @param <T>
 * @param <E>
 */
public interface PooledObjectFactory<T, E extends Exception> {

  // 激活对象，调用makeObject()创建对象之后通过此方法初始化对象
  // 对象在返回对象池时都会被钝化，放弃相关占有资源，重新借出时通过此方法初始化
  void activateObject(PooledObject<T> p) throws E;

  // 销毁对象，当池对象被清理时调用
  void destroyObject(PooledObject<T> p) throws E;
  default void destroyObject(final PooledObject<T> p, final DestroyMode destroyMode) throws E {
      destroyObject(p);
  }

  // 创建池对象，并包装成PooledObject类型
  PooledObject<T> makeObject() throws E;

  // 钝化对象，与初始化的逻辑相反，用于释放相关占有资源
  // 所有对象归还对象池时调用
  void passivateObject(PooledObject<T> p) throws E;

  // 验证池对象有效性
  // 如果配置了testOnXXXX 会在借用、归还的时候验证对象有效性
  boolean validateObject(PooledObject<T> p);

}
