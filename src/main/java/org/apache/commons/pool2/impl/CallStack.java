package org.apache.commons.pool2.impl;

import java.io.PrintWriter;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.UsageTracking;

/**
 * 堆栈跟踪器
 * 获取和打印堆栈调用信息，和 {@link UsageTracking} 配合实现池对象堆栈跟踪
 */
public interface CallStack {

    // 清除当前所有堆栈跟踪信息
    void clear();

    /**
     * 获取当前堆栈信息，随后调用 {@link #printStackTrace(PrintWriter writer)} 打印堆栈信息，之后清理
     */
    void fillInStackTrace();

   // 打印堆栈信息
    boolean printStackTrace(final PrintWriter writer);

}
