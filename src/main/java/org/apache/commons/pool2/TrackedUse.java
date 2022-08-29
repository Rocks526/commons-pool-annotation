package org.apache.commons.pool2;

import java.time.Instant;

/**
 * 池对象使用追踪，用于暴露部分使用信息给对象池，协助其判断是否应该释放空闲对象等
 * 用于扩展池对象的信息，允许使用方自定义上次使用时间等信息
 */
public interface TrackedUse {

    // 上次使用时间
    @Deprecated
    long getLastUsed();
    default Instant getLastUsedInstant() {
        return Instant.ofEpochMilli(getLastUsed());
    }

}
