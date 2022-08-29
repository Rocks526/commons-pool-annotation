package org.apache.commons.pool2.impl;

import java.util.Set;

/**
 * JMX管理 属性、方法定义
 */
public interface GenericObjectPoolMXBean {

    // ======================== 属性 获取相关配置信息 ================================================

    // 借用时没有空闲对象，是否阻塞
    boolean getBlockWhenExhausted();

    // 累计借用对象的数量
    long getBorrowedCount();

    // 累计创建对象数量
    long getCreatedCount();

    // 获取创建对象池的堆栈跟踪
    String getCreationStackTrace();

    /**
     * 获取借用对象时，有效性校验失败销毁的对象数
     * See {@link GenericObjectPool#getDestroyedByBorrowValidationCount()}
     * @return See {@link GenericObjectPool#getDestroyedByBorrowValidationCount()}
     */
    long getDestroyedByBorrowValidationCount();

    /**
     *  获取空闲检测销毁的对象数
     * See {@link GenericObjectPool#getDestroyedByEvictorCount()}
     * @return See {@link GenericObjectPool#getDestroyedByEvictorCount()}
     */
    long getDestroyedByEvictorCount();

    /**
     * 获取累计销毁对象数
     * See {@link GenericObjectPool#getDestroyedCount()}
     * @return See {@link GenericObjectPool#getDestroyedCount()}
     */
    long getDestroyedCount();

    /**
     * 获取工厂类型
     * See {@link GenericObjectPool#getFactoryType()}
     * @return See {@link GenericObjectPool#getFactoryType()}
     */
    String getFactoryType();

    /**
     * 是否为等待线程公平提供服务，公平即先来先服务
     * See {@link GenericObjectPool#getFairness()}
     * @return See {@link GenericObjectPool#getFairness()}
     */
    boolean getFairness();

    /**
     *  归还对象时，是否后进先出
     * See {@link GenericObjectPool#getLifo()}
     * @return See {@link GenericObjectPool#getLifo()}
     */
    boolean getLifo();

    /**
     * 是否开启泄露对象销毁的日志记录
     * See {@link GenericObjectPool#getLogAbandoned()}
     * @return See {@link GenericObjectPool#getLogAbandoned()}
     */
    boolean getLogAbandoned();

    /**
     * 获取从池中借用对象耗时最长的一次时间
     * See {@link GenericObjectPool#getMaxBorrowWaitTimeMillis()}
     * @return See {@link GenericObjectPool#getMaxBorrowWaitTimeMillis()}
     */
    long getMaxBorrowWaitTimeMillis();

    /**
     * 获取最大空闲数量
     * See {@link GenericObjectPool#getMaxIdle()}
     * @return See {@link GenericObjectPool#getMaxIdle()}
     */
    int getMaxIdle();

    /**
     * 获取最大对象数量
     * See {@link GenericObjectPool#getMaxTotal()}
     * @return See {@link GenericObjectPool#getMaxTotal()}
     */
    int getMaxTotal();

    /**
     * 获取从池中借用对象的超时时间
     * See {@link GenericObjectPool#getMaxWaitDuration()}
     * @return See {@link GenericObjectPool#getMaxWaitDuration()}
     */
    long getMaxWaitMillis();


    // ================================= 属性 获取监控相关信息 ==========================================

    /**
     * 池中对象平均空闲时间
     * See {@link GenericObjectPool#getMeanIdleTimeMillis()}
     * @return See {@link GenericObjectPool#getMeanIdleTimeMillis()}
     */
    long getMeanIdleTimeMillis();

    /**
     *  获取从池中对象借出平均使用时间
     * See {@link GenericObjectPool#getMeanActiveTimeMillis()}
     * @return See {@link GenericObjectPool#getMeanActiveTimeMillis()}
     */
    long getMeanActiveTimeMillis();

    /**
     *  获取从池中获取一个对象的平均时间
     * See {@link GenericObjectPool#getMeanBorrowWaitTimeMillis()}
     * @return See {@link GenericObjectPool#getMeanBorrowWaitTimeMillis()}
     */
    long getMeanBorrowWaitTimeMillis();

    /**
     * 获取空闲对象超时时间，超过则回收
     * See {@link GenericObjectPool#getMinEvictableIdleDuration()}
     * @return See {@link GenericObjectPool#getMinEvictableIdleDuration()}
     */
    long getMinEvictableIdleTimeMillis();

    /**
     * 获取最小空闲数量
     * See {@link GenericObjectPool#getMinIdle()}
     * @return See {@link GenericObjectPool#getMinIdle()}
     */
    int getMinIdle();

    /**
     * 获取使用中的对象数
     * See {@link GenericObjectPool#getNumActive()}
     * @return See {@link GenericObjectPool#getNumActive()}
     */
    int getNumActive();

    /**
     * 获取空闲对象数
     * See {@link GenericObjectPool#getNumIdle()}
     * @return See {@link GenericObjectPool#getNumIdle()}
     */
    int getNumIdle();

    /**
     * 获取每次空闲对象回收扫描的对象比例
     * See {@link GenericObjectPool#getNumTestsPerEvictionRun()}
     * @return See {@link GenericObjectPool#getNumTestsPerEvictionRun()}
     */
    int getNumTestsPerEvictionRun();

    /**
     * 获取正在阻塞等待借用对象的数量
     * See {@link GenericObjectPool#getNumWaiters()}
     * @return See {@link GenericObjectPool#getNumWaiters()}
     */
    int getNumWaiters();

    /**
     * 获取借用时是否检测泄露对象配置
     * See {@link GenericObjectPool#getRemoveAbandonedOnBorrow()}
     * @return See {@link GenericObjectPool#getRemoveAbandonedOnBorrow()}
     */
    boolean getRemoveAbandonedOnBorrow();

    /**
     * 获取是否后台检测泄露对象配置
     * See {@link GenericObjectPool#getRemoveAbandonedOnMaintenance()}
     * @return See {@link GenericObjectPool#getRemoveAbandonedOnMaintenance()}
     */
    boolean getRemoveAbandonedOnMaintenance();

    /**
     * 泄露对象检测的超时时间
     * See {@link GenericObjectPool#getRemoveAbandonedTimeoutDuration()}
     * @return See {@link GenericObjectPool#getRemoveAbandonedTimeoutDuration()}
     */
    int getRemoveAbandonedTimeout();

    /**
     * 获取累计返回对象池的数量
     * See {@link GenericObjectPool#getReturnedCount()}
     * @return See {@link GenericObjectPool#getReturnedCount()}
     */
    long getReturnedCount();

    /**
     * 借用时是否开启有效性测试
     * See {@link GenericObjectPool#getTestOnBorrow()}
     * @return See {@link GenericObjectPool#getTestOnBorrow()}
     */
    boolean getTestOnBorrow();

    // ================================= 泄露对象检测配置 ===========================

    /**
     * 创建对象时是否校验有效性
     * See {@link GenericObjectPool#getTestOnCreate()}
     * @return See {@link GenericObjectPool#getTestOnCreate()}
     * @since 2.2
     */
    boolean getTestOnCreate();

    /**
     * 返回时是否检验有效性
     * See {@link GenericObjectPool#getTestOnReturn()}
     * @return See {@link GenericObjectPool#getTestOnReturn()}
     */
    boolean getTestOnReturn();

    /**
     * 空闲时是否检测有效性
     * See {@link GenericObjectPool#getTestWhileIdle()}
     * @return See {@link GenericObjectPool#getTestWhileIdle()}
     */
    boolean getTestWhileIdle();

    /**
     * 空闲对象检测线程检测周期
     * See {@link GenericObjectPool#getDurationBetweenEvictionRuns()}
     * @return See {@link GenericObjectPool#getDurationBetweenEvictionRuns()}
     */
    long getTimeBetweenEvictionRunsMillis();

    /**
     * 是否开启泄露检测配置
     * See {@link GenericObjectPool#isAbandonedConfig()}
     * @return See {@link GenericObjectPool#isAbandonedConfig()}
     */
    boolean isAbandonedConfig();

    /**
     * 对象池是否关闭
     * See {@link GenericObjectPool#isClosed()}
     * @return See {@link GenericObjectPool#isClosed()}
     */
    boolean isClosed();

    /**
     * 获取所有池对象信息
     * See {@link GenericObjectPool#listAllObjects()}
     * @return See {@link GenericObjectPool#listAllObjects()}
     */
    Set<DefaultPooledObjectInfo> listAllObjects();
}
