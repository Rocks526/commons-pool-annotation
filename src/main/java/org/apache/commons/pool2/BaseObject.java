package org.apache.commons.pool2;

/**
 * 基础父类，重写toString方法
 */
public abstract class BaseObject {

    @Override
    public String toString() {
        // 类信息
        final StringBuilder builder = new StringBuilder();
        builder.append(getClass().getSimpleName());
        builder.append(" [");
        toStringAppendFields(builder);
        builder.append("]");
        return builder.toString();
    }

    // 子类实现，输出具体字段信息
    protected void toStringAppendFields(final StringBuilder builder) {
        // do nothing by default, needed for b/w compatibility.
    }
}
