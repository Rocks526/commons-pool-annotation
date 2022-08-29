package org.apache.commons.pool2;

import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;

/**
 * 池对象的包装器
 *  1. 附带一些创建时间、使用时间信息供空闲对象回收策略使用
 *  2. 提供一些分配对象、归还对象、测试对象空闲等方法，用于线程池在有针对对象的操作时通知到对象，对象应自动变更自己的状态
 * @param <T>
 */
public interface PooledObject<T> extends Comparable<PooledObject<T>> {

    // 分配对象，即池对象被借出，需要修改自己状态为ALLOCATED
    // 如果对象正在检测是否空闲，即处于EVICTION状态，则修改为EVICTION_RETURN_TO_HEAD，用于区分EVICTION，在检查完后要将对象归还空闲队列 (借出的逻辑由线程池控制，已经对象已经移除队列)
    boolean allocate();

    // 在池中以双端队列存储，此方法控制顺序
    @Override
    int compareTo(PooledObject<T> other);

    // 归还对象，即池对象被归还，需要修改状态为IDLE
    boolean deallocate();

    // 通知对象开始 "空闲对象回收"检查，修改自己状态为EVICTION
    boolean startEvictionTest();

    // 通知对象 "空闲对象回收"检查结束，修改自己状态为IDLE
    boolean endEvictionTest(Deque<PooledObject<T>> idleQueue);

    @Override
    boolean equals(Object obj);

    // 获取此对象上次借用时长，如果此时还在使用中，返回此次借用直到现在的时长
    default Duration getActiveDuration() {
        // Take copies to avoid threading issues
        // 上次归还时间
        final Instant lastReturnInstant = getLastReturnInstant();
        // 上次借出时间
        final Instant lastBorrowInstant = getLastBorrowInstant();
        // @formatter:off
        return lastReturnInstant.isAfter(lastBorrowInstant) ?
                // 对象空闲，则统计上次借出使用时间
                Duration.between(lastBorrowInstant, lastReturnInstant) :
                // 对象被借出还未归还，则统计此次借出时间
                Duration.between(lastBorrowInstant, Instant.now());
        // @formatter:on
    }
    @Deprecated
    default Duration getActiveTime() {
        return getActiveDuration();
    }
    @Deprecated
    long getActiveTimeMillis();

    // 获取此对象借用次数
    default long getBorrowedCount() {
        return -1;
    }

    // 获取此对象创建时间
    default Instant getCreateInstant() {
        return Instant.ofEpochMilli(getCreateTime());
    }
    @Deprecated
    long getCreateTime();

    // 获取对象创建到现在的时长
    default Duration getFullDuration() {
        return Duration.between(getCreateInstant(), Instant.now());
    }

    // 获取对象上次空闲时长，如果此时处于空闲状态，则返回上次归还到现在的时长
    default Duration getIdleDuration() {
        return Duration.ofMillis(getIdleTimeMillis());
    }
    @Deprecated
    default Duration getIdleTime() {
        return Duration.ofMillis(getIdleTimeMillis());
    }
    @Deprecated
    long getIdleTimeMillis();

    // 上次对象借用时间
    default Instant getLastBorrowInstant() {
        return Instant.ofEpochMilli(getLastBorrowTime());
    }
    @Deprecated
    long getLastBorrowTime();

    // 上次对象归还时间
    default Instant getLastReturnInstant() {
        return Instant.ofEpochMilli(getLastReturnTime());
    }
    @Deprecated
    long getLastReturnTime();

    // 获取上次对象使用时间，如果对象实现了TrackedUse接口，则返回TrackedUse#getLastUsedInstant和this#getLastBorrowTime的最大值，否则返回this#getLastBorrowTime
    // 返回的上次使用时间会干涉到空闲对象回收策略的行为，因此提供TrackedUse接口用于扩展池对象的使用时间，调用use方法可以更新
    default Instant getLastUsedInstant() {
        return Instant.ofEpochMilli(getLastUsedTime());
    }
    @Deprecated
    long getLastUsedTime();

    // 获取包装的原对象
    T getObject();

    // 获取池对象状态
    PooledObjectState getState();

    // 哈希值
    @Override
    int hashCode();

    // 将此对象设置为无效状态 INVALID
    void invalidate();

    // 将此对象设置为废弃状态，泄露对象检测会调用此方法更新对象状态 ABANDONED
    void markAbandoned();

    // 将此对象设置为正在返回池状态，归还对象池时调用 RETURNING
    void markReturning();

    // 打印此对象的堆栈跟踪信息
    void printStackTrace(PrintWriter writer);

    // 设置是否跟踪对象堆栈
    void setLogAbandoned(boolean logAbandoned);

    // 设置是否要完整的对象堆栈跟踪信息
    default void setRequireFullStackTrace(final boolean requireFullStackTrace) {
        // noop
    }

    // 格式化输出
    @Override
    String toString();

    // 记录上次对象使用时间，用于干涉空闲对象回收策略
    // 跟踪对象堆栈信息，用于对象泄露进行销毁时打印提示信息
    void use();

}
