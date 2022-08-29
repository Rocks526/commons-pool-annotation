package org.apache.commons.pool2.impl;

/**
 * 池对象JMX管理
 */
public interface DefaultPooledObjectInfoMBean {

    // 当前对象被借用次数
    long getBorrowedCount();

    // 当前对象创建时间
    long getCreateTime();
    String getCreateTimeFormatted();

    // 最后一次借用时间
    long getLastBorrowTime();
    String getLastBorrowTimeFormatted();

    // 最后一次借用堆栈信息
    String getLastBorrowTrace();

    // 最后一次归还时间
    long getLastReturnTime();
    String getLastReturnTimeFormatted();

    // 池对象字符串输出
    String getPooledObjectToString();

    // 获取池对象的所属类名称
    String getPooledObjectType();

}
