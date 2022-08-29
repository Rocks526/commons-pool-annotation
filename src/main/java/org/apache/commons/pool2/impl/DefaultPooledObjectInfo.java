package org.apache.commons.pool2.impl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;

import org.apache.commons.pool2.PooledObject;

/**
 * 池对象JMX信息实现类
 */
public class DefaultPooledObjectInfo implements DefaultPooledObjectInfoMBean {

    // 日期格式化
    private static final String PATTERN = "yyyy-MM-dd HH:mm:ss Z";

    // 对象引用
    private final PooledObject<?> pooledObject;

    public DefaultPooledObjectInfo(final PooledObject<?> pooledObject) {
        this.pooledObject = pooledObject;
    }

    @Override
    public long getBorrowedCount() {
        return pooledObject.getBorrowedCount();
    }

    @Override
    public long getCreateTime() {
        return pooledObject.getCreateInstant().toEpochMilli();
    }

    @Override
    public String getCreateTimeFormatted() {
        return getTimeMillisFormatted(getCreateTime());
    }

    @Override
    public long getLastBorrowTime() {
        return pooledObject.getLastBorrowInstant().toEpochMilli();
    }


    @Override
    public String getLastBorrowTimeFormatted() {
        return getTimeMillisFormatted(getLastBorrowTime());
    }

    @Override
    public String getLastBorrowTrace() {
        final StringWriter sw = new StringWriter();
        // 上次借用堆栈信息
        pooledObject.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    @Override
    public long getLastReturnTime() {
        return pooledObject.getLastReturnInstant().toEpochMilli();
    }

    @Override
    public String getLastReturnTimeFormatted() {
        return getTimeMillisFormatted(getLastReturnTime());
    }

    @Override
    public String getPooledObjectToString() {
        return pooledObject.getObject().toString();
    }

    @Override
    public String getPooledObjectType() {
        return pooledObject.getObject().getClass().getName();
    }

    private String getTimeMillisFormatted(final long millis) {
        return new SimpleDateFormat(PATTERN).format(Long.valueOf(millis));
    }

    /**
     * @since 2.4.3
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("DefaultPooledObjectInfo [pooledObject=");
        builder.append(pooledObject);
        builder.append("]");
        return builder.toString();
    }
}
