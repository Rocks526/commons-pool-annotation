package org.apache.commons.pool2.impl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.apache.commons.pool2.BaseObject;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectState;
import org.apache.commons.pool2.SwallowedExceptionListener;

/**
 * 对象池的公共实现，作为GenericObjectPool和GenericKeyObjectPool的基类
 * 提供通用方法，如JMX属性获取、空闲对象回收等
 * @param <T>
 * @param <E>
 */
public abstract class BaseGenericObjectPool<T, E extends Exception> extends BaseObject {

    // 空闲对象迭代器，用于空闲对象回收，每次回收扫描这个迭代器快照
    class EvictionIterator implements Iterator<PooledObject<T>> {

        // 所有空闲对象
        private final Deque<PooledObject<T>> idleObjects;
        // 迭代器
        private final Iterator<PooledObject<T>> idleObjectIterator;

        EvictionIterator(final Deque<PooledObject<T>> idleObjects) {
            // 记录空闲对象
            this.idleObjects = idleObjects;

            // 生成迭代器
            if (getLifo()) {
                idleObjectIterator = idleObjects.descendingIterator();
            } else {
                idleObjectIterator = idleObjects.iterator();
            }
        }

        public Deque<PooledObject<T>> getIdleObjects() {
            return idleObjects;
        }

        @Override
        public boolean hasNext() {
            return idleObjectIterator.hasNext();
        }

        @Override
        public PooledObject<T> next() {
            return idleObjectIterator.next();
        }

        @Override
        public void remove() {
            idleObjectIterator.remove();
        }

    }

    // 空闲对象回收任务
    class Evictor implements Runnable {

        private ScheduledFuture<?> scheduledFuture;

        void cancel() {
            scheduledFuture.cancel(false);
        }

        BaseGenericObjectPool<T, E> owner() {
            return BaseGenericObjectPool.this;
        }


        @Override
        public void run() {
            final ClassLoader savedClassLoader =
                    Thread.currentThread().getContextClassLoader();
            try {
                if (factoryClassLoader != null) {
                    // Set the class loader for the factory
                    final ClassLoader cl = factoryClassLoader.get();
                    if (cl == null) {
                        // The pool has been dereferenced and the class loader
                        // GC'd. Cancel this timer so the pool can be GC'd as
                        // well.
                        cancel();
                        return;
                    }
                    Thread.currentThread().setContextClassLoader(cl);
                }

                // Evict from the pool
                try {
                    evict();
                } catch(final Exception e) {
                    swallowException(e);
                } catch(final OutOfMemoryError oome) {
                    // Log problem but give evictor thread a chance to continue
                    // in case error is recoverable
                    oome.printStackTrace(System.err);
                }
                // Re-create idle instances.
                try {
                    ensureMinIdle();
                } catch (final Exception e) {
                    swallowException(e);
                }
            } finally {
                // Restore the previous CCL
                Thread.currentThread().setContextClassLoader(savedClassLoader);
            }
        }

        void setScheduledFuture(final ScheduledFuture<?> scheduledFuture) {
            this.scheduledFuture = scheduledFuture;
        }

    }

    /**
     * Wrapper for objects under management by the pool.
     *
     * GenericObjectPool and GenericKeyedObjectPool maintain references to all
     * objects under management using maps keyed on the objects. This wrapper
     * class ensures that objects can work as hash keys.
     *
     * @param <T> type of objects in the pool
     */
    static class IdentityWrapper<T> {
        /** Wrapped object */
        private final T instance;

        /**
         * Constructs a wrapper for an instance.
         *
         * @param instance object to wrap
         */
        public IdentityWrapper(final T instance) {
            this.instance = instance;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public boolean equals(final Object other) {
            return other instanceof IdentityWrapper && ((IdentityWrapper) other).instance == instance;
        }

        /**
         * @return the wrapped object
         */
        public T getObject() {
            return instance;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(instance);
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("IdentityWrapper [instance=");
            builder.append(instance);
            builder.append("]");
            return builder.toString();
        }
    }


    /**
     * Maintains a cache of values for a single metric and reports
     * statistics on the cached values.
     */
    private class StatsStore {

        private static final int NULL = -1;
        private final AtomicLong[] values;
        private final int size;
        private int index;

        /**
         * Constructs a StatsStore with the given cache size.
         *
         * @param size number of values to maintain in the cache.
         */
        StatsStore(final int size) {
            this.size = size;
            values = new AtomicLong[size];
            for (int i = 0; i < size; i++) {
                values[i] = new AtomicLong(NULL);
            }
        }

        void add(final Duration value) {
            add(value.toMillis());
        }

        /**
         * Adds a value to the cache.  If the cache is full, one of the
         * existing values is replaced by the new value.
         *
         * @param value new value to add to the cache.
         */
        synchronized void add(final long value) {
            values[index].set(value);
            index++;
            if (index == size) {
                index = 0;
            }
        }

        /**
         * Gets the current values as a List.
         *
         * @return the current values as a List.
         */
        synchronized List<AtomicLong> getCurrentValues() {
            return Arrays.stream(values, 0, index).collect(Collectors.toList());
        }

        /**
         * Gets the mean of the cached values.
         *
         * @return the mean of the cache, truncated to long
         */
        public long getMean() {
            double result = 0;
            int counter = 0;
            for (int i = 0; i < size; i++) {
                final long value = values[i].get();
                if (value != NULL) {
                    counter++;
                    result = result * ((counter - 1) / (double) counter) + value / (double) counter;
                }
            }
            return (long) result;
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("StatsStore [");
            // Only append what's been filled in.
            builder.append(getCurrentValues());
            builder.append("], size=");
            builder.append(size);
            builder.append(", index=");
            builder.append(index);
            builder.append("]");
            return builder.toString();
        }

    }
    // Constants
    // 缓存数据的大小，用于统计部分信息，如缓存最近100次对象借用时长
    public static final int MEAN_TIMING_STATS_CACHE_SIZE = 100;

    // 空闲对象回收策略
    private static final String EVICTION_POLICY_TYPE_NAME = EvictionPolicy.class.getName();

    // 泄露对象超时时间
    private static final Duration DEFAULT_REMOVE_ABANDONED_TIMEOUT = Duration.ofSeconds(Integer.MAX_VALUE);

    // 最大对象数
    private volatile int maxTotal = GenericKeyedObjectPoolConfig.DEFAULT_MAX_TOTAL;

    // 当对象池暂无可用对象时，是否阻塞等待
    private volatile boolean blockWhenExhausted = BaseObjectPoolConfig.DEFAULT_BLOCK_WHEN_EXHAUSTED;

    // 借用对象时的超时时间，当blockWhenExhausted为true时生效，为-1时代表永远阻塞
    private volatile Duration maxWaitDuration = BaseObjectPoolConfig.DEFAULT_MAX_WAIT;

    // 返回对象时，采用FIFO还是LIFO
    private volatile boolean lifo = BaseObjectPoolConfig.DEFAULT_LIFO;

    // 是否公平分配模式，先借用对象的线程先获取对象
    private final boolean fairness;

    // 创建对象时是否校验有效性
    private volatile boolean testOnCreate = BaseObjectPoolConfig.DEFAULT_TEST_ON_CREATE;

    // 借用对象时是否校验有效性
    private volatile boolean testOnBorrow = BaseObjectPoolConfig.DEFAULT_TEST_ON_BORROW;

    // 返回时是否检验有效性
    private volatile boolean testOnReturn = BaseObjectPoolConfig.DEFAULT_TEST_ON_RETURN;

    // 空闲时是否检测有效性
    private volatile boolean testWhileIdle = BaseObjectPoolConfig.DEFAULT_TEST_WHILE_IDLE;

    // 空闲对象检测线程检测周期
    private volatile Duration durationBetweenEvictionRuns = BaseObjectPoolConfig.DEFAULT_TIME_BETWEEN_EVICTION_RUNS;

    // 每次空闲对象回收线程扫描的对象比例
    private volatile int numTestsPerEvictionRun = BaseObjectPoolConfig.DEFAULT_NUM_TESTS_PER_EVICTION_RUN;

    // 空闲对象空闲超时时间
    private volatile Duration minEvictableIdleDuration = BaseObjectPoolConfig.DEFAULT_MIN_EVICTABLE_IDLE_DURATION;

    // 空闲对象空闲超时时间
    private volatile Duration softMinEvictableIdleDuration = BaseObjectPoolConfig.DEFAULT_SOFT_MIN_EVICTABLE_IDLE_DURATION;

    // 空闲对象回收策略
    private volatile EvictionPolicy<T> evictionPolicy;

    // 空闲对象回收线程关闭超时时间
    private volatile Duration evictorShutdownTimeoutDuration = BaseObjectPoolConfig.DEFAULT_EVICTOR_SHUTDOWN_TIMEOUT;

    // Internal (primarily state) attributes
    final Object closeLock = new Object();

    // volatile修饰保证线程间的可见性
    // 对象池是否关闭
    volatile boolean closed;

    // 空闲对象回收锁
    final Object evictionLock = new Object();

    // 空闲对象回收器
    private Evictor evictor; // @GuardedBy("evictionLock")

    // 空闲对象回收迭代器
    EvictionIterator evictionIterator; // @GuardedBy("evictionLock")

    /**
     * Class loader for evictor thread to use since, in a JavaEE or similar
     * environment, the context class loader for the evictor thread may not have
     * visibility of the correct factory. See POOL-161. Uses a weak reference to
     * avoid potential memory leaks if the Pool is discarded rather than closed.
     */
    private final WeakReference<ClassLoader> factoryClassLoader;

    // JMX属性
    private final ObjectName objectName;

    // 创建此对象池的堆栈信息
    private final String creationStackTrace;

    // 累计借用对象数
    private final AtomicLong borrowedCount = new AtomicLong();

    // 累计归还对象数
    private final AtomicLong returnedCount = new AtomicLong();

    // 累计创建对象数
    final AtomicLong createdCount = new AtomicLong();

    // 获取累计销毁对象数
    final AtomicLong destroyedCount = new AtomicLong();

    // 获取空闲检测销毁的对象数
    final AtomicLong destroyedByEvictorCount = new AtomicLong();

    // 获取借用对象时，有效性校验失败销毁的对象数
    final AtomicLong destroyedByBorrowValidationCount = new AtomicLong();

    // 统计从池中对象借出平均使用时间
    private final StatsStore activeTimes = new StatsStore(MEAN_TIMING_STATS_CACHE_SIZE);

    // 统计池中对象平均空闲时间
    private final StatsStore idleTimes = new StatsStore(MEAN_TIMING_STATS_CACHE_SIZE);

    // 统计从池中借用一个对象的平均等待时间
    private final StatsStore waitTimes = new StatsStore(MEAN_TIMING_STATS_CACHE_SIZE);

    // 获取从池中借用对象耗时最长的一次时间
    private final AtomicReference<Duration> maxBorrowWaitDuration = new AtomicReference<>(Duration.ZERO);

    // 异常处理监听器
    private volatile SwallowedExceptionListener swallowedExceptionListener;

    // 开启统计信息
    private volatile boolean messageStatistics;

    // 泄露检测配置
    protected volatile AbandonedConfig abandonedConfig;

    /**
     * Handles JMX registration (if required) and the initialization required for
     * monitoring.
     *
     * @param config        Pool configuration
     * @param jmxNameBase   The default base JMX name for the new pool unless
     *                      overridden by the config
     * @param jmxNamePrefix Prefix to be used for JMX name for the new pool
     */
    public BaseGenericObjectPool(final BaseObjectPoolConfig<T> config,
            final String jmxNameBase, final String jmxNamePrefix) {
        if (config.getJmxEnabled()) {
            this.objectName = jmxRegister(config, jmxNameBase, jmxNamePrefix);
        } else {
            this.objectName = null;
        }

        // Populate the creation stack trace
        this.creationStackTrace = getStackTrace(new Exception());

        // save the current TCCL (if any) to be used later by the evictor Thread
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            factoryClassLoader = null;
        } else {
            factoryClassLoader = new WeakReference<>(cl);
        }

        fairness = config.getFairness();
    }

    /**
     * Appends statistics if enabled.
     * <p>
     * Statistics may not accurately reflect snapshot state at the time of the exception because we do not want to lock the pool when gathering this
     * information.
     * </p>
     *
     * @param string The root string.
     * @return The root string plus statistics.
     */
    String appendStats(final String string) {
        return messageStatistics ? string + ", " + getStatsString() : string;
    }

    // 检查对象池状态
    final void assertOpen() throws IllegalStateException {
        if (isClosed()) {
            throw new IllegalStateException("Pool not open");
        }
    }

    /**
     * Casts the given throwable to E.
     *
     * @param throwable the throwable.
     * @return the input.
     * @since 2.12.0
     */
    @SuppressWarnings("unchecked")
    protected E cast(final Throwable throwable) {
        return (E) throwable;
    }

    /**
     * Closes the pool, destroys the remaining idle objects and, if registered
     * in JMX, deregisters it.
     */
    public abstract void close();


    /**
     * 检测池对象状态，返回要销毁的池对象（泄露的）
     * @param abandonedConfig   泄露检测配置
     * @param allObjects    要检测的池对象
     * @return  要销毁的池对象列表
     */
    ArrayList<PooledObject<T>> createRemoveList(final AbandonedConfig abandonedConfig, final Map<IdentityWrapper<T>, PooledObject<T>> allObjects) {
        // 根据泄露检测配置，计算要求的对象上次使用时间
        final Instant timeout = Instant.now().minus(abandonedConfig.getRemoveAbandonedTimeoutDuration());
        // 要销毁的对象
        final ArrayList<PooledObject<T>> remove = new ArrayList<>();
        allObjects.values().forEach(pooledObject -> {
            // 遍历时锁定单个池对象，避免相关操作
            synchronized (pooledObject) {
                if (pooledObject.getState() == PooledObjectState.ALLOCATED &&
                        pooledObject.getLastUsedInstant().compareTo(timeout) <= 0) {
                    // 对象是借出状态，并且上次使用时间早于阈值，则修改为泄露对象状态，并加入列表
                    pooledObject.markAbandoned();
                    remove.add(pooledObject);
                }
            }
        });
        return remove;
    }

    // 确保 minIdle 个空闲对象
    abstract void ensureMinIdle() throws E;

    // 空闲对象回收 && 泄露对象检测
    public abstract void evict() throws E;

    /**
     * Gets whether to block when the {@code borrowObject()} method is
     * invoked when the pool is exhausted (the maximum number of "active"
     * objects has been reached).
     *
     * @return {@code true} if {@code borrowObject()} should block
     *         when the pool is exhausted
     *
     * @see #setBlockWhenExhausted
     */
    public final boolean getBlockWhenExhausted() {
        return blockWhenExhausted;
    }

    /**
     * Gets the total number of objects successfully borrowed from this pool over the
     * lifetime of the pool.
     * @return the borrowed object count
     */
    public final long getBorrowedCount() {
        return borrowedCount.get();
    }

    public final long getCreatedCount() {
        return createdCount.get();
    }


    public final String getCreationStackTrace() {
        return creationStackTrace;
    }

    public final long getDestroyedByBorrowValidationCount() {
        return destroyedByBorrowValidationCount.get();
    }

    public final long getDestroyedByEvictorCount() {
        return destroyedByEvictorCount.get();
    }

    public final long getDestroyedCount() {
        return destroyedCount.get();
    }

    public final Duration getDurationBetweenEvictionRuns() {
        return durationBetweenEvictionRuns;
    }

    /**
     * Gets the {@link EvictionPolicy} defined for this pool.
     *
     * @return the eviction policy
     * @since 2.4
     * @since 2.6.0 Changed access from protected to public.
     */
    public EvictionPolicy<T> getEvictionPolicy() {
        return evictionPolicy;
    }

    /**
     * Gets the name of the {@link EvictionPolicy} implementation that is
     * used by this pool.
     *
     * @return  The fully qualified class name of the {@link EvictionPolicy}
     *
     * @see #setEvictionPolicyClassName(String)
     */
    public final String getEvictionPolicyClassName() {
        return evictionPolicy.getClass().getName();
    }

    /**
     * Gets the timeout that will be used when waiting for the Evictor to
     * shutdown if this pool is closed and it is the only pool still using the
     * the value for the Evictor.
     *
     * @return  The timeout that will be used while waiting for
     *          the Evictor to shut down.
     * @since 2.10.0
     * @deprecated Use {@link #getEvictorShutdownTimeoutDuration()}.
     */
    @Deprecated
    public final Duration getEvictorShutdownTimeout() {
        return evictorShutdownTimeoutDuration;
    }

    /**
     * Gets the timeout that will be used when waiting for the Evictor to
     * shutdown if this pool is closed and it is the only pool still using the
     * the value for the Evictor.
     *
     * @return  The timeout that will be used while waiting for
     *          the Evictor to shut down.
     * @since 2.11.0
     */
    public final Duration getEvictorShutdownTimeoutDuration() {
        return evictorShutdownTimeoutDuration;
    }

    /**
     * Gets the timeout that will be used when waiting for the Evictor to
     * shutdown if this pool is closed and it is the only pool still using the
     * the value for the Evictor.
     *
     * @return  The timeout in milliseconds that will be used while waiting for
     *          the Evictor to shut down.
     * @deprecated Use {@link #getEvictorShutdownTimeoutDuration()}.
     */
    @Deprecated
    public final long getEvictorShutdownTimeoutMillis() {
        return evictorShutdownTimeoutDuration.toMillis();
    }


    // 是否为等待线程公平提供服务，公平即先来先服务
    public final boolean getFairness() {
        return fairness;
    }

    /**
     * Gets the name under which the pool has been registered with the
     * platform MBean server or {@code null} if the pool has not been
     * registered.
     * @return the JMX name
     */
    public final ObjectName getJmxName() {
        return objectName;
    }


    // 归还对象时，是否后进先出
    public final boolean getLifo() {
        return lifo;
    }


    // 是否开启泄露对象销毁的日志记录
    public boolean getLogAbandoned() {
        final AbandonedConfig ac = this.abandonedConfig;
        return ac != null && ac.getLogAbandoned();
    }

    public final long getMaxBorrowWaitTimeMillis() {
        return maxBorrowWaitDuration.get().toMillis();
    }

    public final int getMaxTotal() {
        return maxTotal;
    }

    public final Duration getMaxWaitDuration() {
        return maxWaitDuration;
    }

    @Deprecated
    public final long getMaxWaitMillis() {
        return maxWaitDuration.toMillis();
    }

    public final long getMeanActiveTimeMillis() {
        return activeTimes.getMean();
    }

    public final long getMeanBorrowWaitTimeMillis() {
        return waitTimes.getMean();
    }

    public final long getMeanIdleTimeMillis() {
        return idleTimes.getMean();
    }

    public boolean getMessageStatistics() {
        return messageStatistics;
    }

    public final Duration getMinEvictableIdleDuration() {
        return minEvictableIdleDuration;
    }

    @Deprecated
    public final Duration getMinEvictableIdleTime() {
        return minEvictableIdleDuration;
    }

    @Deprecated
    public final long getMinEvictableIdleTimeMillis() {
        return minEvictableIdleDuration.toMillis();
    }

    public abstract int getNumIdle();

    public final int getNumTestsPerEvictionRun() {
        return numTestsPerEvictionRun;
    }

    public boolean getRemoveAbandonedOnBorrow() {
        final AbandonedConfig ac = this.abandonedConfig;
        return ac != null && ac.getRemoveAbandonedOnBorrow();
    }

    public boolean getRemoveAbandonedOnMaintenance() {
        final AbandonedConfig ac = this.abandonedConfig;
        return ac != null && ac.getRemoveAbandonedOnMaintenance();
    }

    @Deprecated
    public int getRemoveAbandonedTimeout() {
        return (int) getRemoveAbandonedTimeoutDuration().getSeconds();
    }

    public Duration getRemoveAbandonedTimeoutDuration() {
        final AbandonedConfig ac = this.abandonedConfig;
        return ac != null ? ac.getRemoveAbandonedTimeoutDuration() : DEFAULT_REMOVE_ABANDONED_TIMEOUT;
    }

    public final long getReturnedCount() {
        return returnedCount.get();
    }

    public final Duration getSoftMinEvictableIdleDuration() {
        return softMinEvictableIdleDuration;
    }

    @Deprecated
    public final Duration getSoftMinEvictableIdleTime() {
        return softMinEvictableIdleDuration;
    }

    @Deprecated
    public final long getSoftMinEvictableIdleTimeMillis() {
        return softMinEvictableIdleDuration.toMillis();
    }

    // 异常堆栈输出为str
    private String getStackTrace(final Exception e) {
        // Need the exception in string form to prevent the retention of
        // references to classes in the stack trace that could trigger a memory
        // leak in a container environment.
        final Writer w = new StringWriter();
        final PrintWriter pw = new PrintWriter(w);
        e.printStackTrace(pw);
        return w.toString();
    }

    // 获取统计信息
    String getStatsString() {
        // Simply listed in AB order.
        return String.format(
                "activeTimes=%s, blockWhenExhausted=%s, borrowedCount=%,d, closed=%s, createdCount=%,d, destroyedByBorrowValidationCount=%,d, " +
                        "destroyedByEvictorCount=%,d, evictorShutdownTimeoutDuration=%s, fairness=%s, idleTimes=%s, lifo=%s, maxBorrowWaitDuration=%s, " +
                        "maxTotal=%s, maxWaitDuration=%s, minEvictableIdleDuration=%s, numTestsPerEvictionRun=%s, returnedCount=%s, " +
                        "softMinEvictableIdleDuration=%s, testOnBorrow=%s, testOnCreate=%s, testOnReturn=%s, testWhileIdle=%s, " +
                        "durationBetweenEvictionRuns=%s, waitTimes=%s",
                activeTimes.getCurrentValues(), blockWhenExhausted, borrowedCount.get(), closed, createdCount.get(), destroyedByBorrowValidationCount.get(),
                destroyedByEvictorCount.get(), evictorShutdownTimeoutDuration, fairness, idleTimes.getCurrentValues(), lifo, maxBorrowWaitDuration.get(),
                maxTotal, maxWaitDuration, minEvictableIdleDuration, numTestsPerEvictionRun, returnedCount, softMinEvictableIdleDuration, testOnBorrow,
                testOnCreate, testOnReturn, testWhileIdle, durationBetweenEvictionRuns, waitTimes.getCurrentValues());
    }

    public final SwallowedExceptionListener getSwallowedExceptionListener() {
        return swallowedExceptionListener;
    }

    public final boolean getTestOnBorrow() {
        return testOnBorrow;
    }

    public final boolean getTestOnCreate() {
        return testOnCreate;
    }

    public final boolean getTestOnReturn() {
        return testOnReturn;
    }

    public final boolean getTestWhileIdle() {
        return testWhileIdle;
    }

    @Deprecated
    public final Duration getTimeBetweenEvictionRuns() {
        return durationBetweenEvictionRuns;
    }

    @Deprecated
    public final long getTimeBetweenEvictionRunsMillis() {
        return durationBetweenEvictionRuns.toMillis();
    }

    public boolean isAbandonedConfig() {
        return abandonedConfig != null;
    }

    public final boolean isClosed() {
        return closed;
    }

    // 注册JMX管理
    private ObjectName jmxRegister(final BaseObjectPoolConfig<T> config,
            final String jmxNameBase, String jmxNamePrefix) {
        ObjectName newObjectName = null;
        final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        int i = 1;
        boolean registered = false;
        String base = config.getJmxNameBase();
        if (base == null) {
            base = jmxNameBase;
        }
        while (!registered) {
            try {
                ObjectName objName;
                // Skip the numeric suffix for the first pool in case there is
                // only one so the names are cleaner.
                if (i == 1) {
                    objName = new ObjectName(base + jmxNamePrefix);
                } else {
                    objName = new ObjectName(base + jmxNamePrefix + i);
                }
                mbs.registerMBean(this, objName);
                newObjectName = objName;
                registered = true;
            } catch (final MalformedObjectNameException e) {
                if (BaseObjectPoolConfig.DEFAULT_JMX_NAME_PREFIX.equals(
                        jmxNamePrefix) && jmxNameBase.equals(base)) {
                    // Shouldn't happen. Skip registration if it does.
                    registered = true;
                } else {
                    // Must be an invalid name. Use the defaults instead.
                    jmxNamePrefix =
                            BaseObjectPoolConfig.DEFAULT_JMX_NAME_PREFIX;
                    base = jmxNameBase;
                }
            } catch (final InstanceAlreadyExistsException e) {
                // Increment the index and try again
                i++;
            } catch (final MBeanRegistrationException | NotCompliantMBeanException e) {
                // Shouldn't happen. Skip registration if it does.
                registered = true;
            }
        }
        return newObjectName;
    }

    // 解除注册JMX
    final void jmxUnregister() {
        if (objectName != null) {
            try {
                ManagementFactory.getPlatformMBeanServer().unregisterMBean(objectName);
            } catch (final MBeanRegistrationException | InstanceNotFoundException e) {
                swallowException(e);
            }
        }
    }

   // 修改对象状态为返回中
    protected void markReturningState(final PooledObject<T> pooledObject) {
        synchronized (pooledObject) {
            if (pooledObject.getState() != PooledObjectState.ALLOCATED) {
                throw new IllegalStateException("Object has already been returned to this pool or is invalid");
            }
            pooledObject.markReturning(); // Keep from being marked abandoned
        }
    }

    public void setAbandonedConfig(final AbandonedConfig abandonedConfig) {
        this.abandonedConfig = AbandonedConfig.copy(abandonedConfig);
    }

    public final void setBlockWhenExhausted(final boolean blockWhenExhausted) {
        this.blockWhenExhausted = blockWhenExhausted;
    }

    protected void setConfig(final BaseObjectPoolConfig<T> config) {
        // 归还队列是否采用LIFO
        setLifo(config.getLifo());
        // 借用对象时的超时时间
        setMaxWait(config.getMaxWaitDuration());
        // 借用对象时是否阻塞
        setBlockWhenExhausted(config.getBlockWhenExhausted());

        // =================== 有效性检查相关配置 ==========================
        // 创建对象时是否校验有效性
        setTestOnCreate(config.getTestOnCreate());
        // 借用对象时是否校验有效性
        setTestOnBorrow(config.getTestOnBorrow());
        // 归还对象时是否检验有效性
        setTestOnReturn(config.getTestOnReturn());
        // 空闲时是否检验有效性
        setTestWhileIdle(config.getTestWhileIdle());

        // ================= 空闲对象回收机制 ================================
        // 每次空闲对象回收线程扫描的对象数
        setNumTestsPerEvictionRun(config.getNumTestsPerEvictionRun());
        // 空闲对象检测线程的检测周期
        setTimeBetweenEvictionRuns(config.getDurationBetweenEvictionRuns());
        // 设置对象空闲时间阈值，超过此值的对象会被认为空闲对象，被回收，默认30分钟
        setMinEvictableIdle(config.getMinEvictableIdleDuration());
        // 设置对象空闲时间阈值，超过此值的对象会被认为空闲对象，当空闲对象大于 minIdle 参数时会被回收，默认-1，代表大于 minIdle 的空闲对象立刻被回收
        setSoftMinEvictableIdle(config.getSoftMinEvictableIdleDuration());
        // 设置空闲对象回收策略，默认策略为 DefaultEvictionPolicy
        final EvictionPolicy<T> policy = config.getEvictionPolicy();
        if (policy == null) {
            // Use the class name (pre-2.6.0 compatible)
            setEvictionPolicyClassName(config.getEvictionPolicyClassName());
        } else {
            // Otherwise, use the class (2.6.0 feature)
            setEvictionPolicy(policy);
        }
        // 空闲对象回收线程关闭时的超时时间，默认10秒
        setEvictorShutdownTimeout(config.getEvictorShutdownTimeoutDuration());
    }

    public void setEvictionPolicy(final EvictionPolicy<T> evictionPolicy) {
        this.evictionPolicy = evictionPolicy;
    }

    @SuppressWarnings("unchecked")
    private void setEvictionPolicy(final String className, final ClassLoader classLoader)
            throws ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        final Class<?> clazz = Class.forName(className, true, classLoader);
        final Object policy = clazz.getConstructor().newInstance();
        this.evictionPolicy = (EvictionPolicy<T>) policy;
    }

    public final void setEvictionPolicyClassName(final String evictionPolicyClassName) {
        setEvictionPolicyClassName(evictionPolicyClassName, Thread.currentThread().getContextClassLoader());
    }

    public final void setEvictionPolicyClassName(final String evictionPolicyClassName, final ClassLoader classLoader) {
        // Getting epClass here and now best matches the caller's environment
        final Class<?> epClass = EvictionPolicy.class;
        final ClassLoader epClassLoader = epClass.getClassLoader();
        try {
            try {
                setEvictionPolicy(evictionPolicyClassName, classLoader);
            } catch (final ClassCastException | ClassNotFoundException e) {
                setEvictionPolicy(evictionPolicyClassName, epClassLoader);
            }
        } catch (final ClassCastException e) {
            throw new IllegalArgumentException("Class " + evictionPolicyClassName + " from class loaders [" +
                    classLoader + ", " + epClassLoader + "] do not implement " + EVICTION_POLICY_TYPE_NAME);
        } catch (final ClassNotFoundException | InstantiationException | IllegalAccessException |
                InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalArgumentException(
                    "Unable to create " + EVICTION_POLICY_TYPE_NAME + " instance of type " + evictionPolicyClassName,
                    e);
        }
    }

    public final void setEvictorShutdownTimeout(final Duration evictorShutdownTimeout) {
        this.evictorShutdownTimeoutDuration = PoolImplUtils.nonNull(evictorShutdownTimeout, BaseObjectPoolConfig.DEFAULT_EVICTOR_SHUTDOWN_TIMEOUT);
    }

    @Deprecated
    public final void setEvictorShutdownTimeoutMillis(final long evictorShutdownTimeoutMillis) {
        setEvictorShutdownTimeout(Duration.ofMillis(evictorShutdownTimeoutMillis));
    }

    public final void setLifo(final boolean lifo) {
        this.lifo = lifo;
    }

    public final void setMaxTotal(final int maxTotal) {
        this.maxTotal = maxTotal;
    }

    public final void setMaxWait(final Duration maxWaitDuration) {
        this.maxWaitDuration = PoolImplUtils.nonNull(maxWaitDuration, BaseObjectPoolConfig.DEFAULT_MAX_WAIT);
    }

    @Deprecated
    public final void setMaxWaitMillis(final long maxWaitMillis) {
        setMaxWait(Duration.ofMillis(maxWaitMillis));
    }

    public void setMessagesStatistics(final boolean messagesDetails) {
        this.messageStatistics = messagesDetails;
    }

    public final void setMinEvictableIdle(final Duration minEvictableIdleTime) {
        this.minEvictableIdleDuration = PoolImplUtils.nonNull(minEvictableIdleTime, BaseObjectPoolConfig.DEFAULT_MIN_EVICTABLE_IDLE_DURATION);
    }

    @Deprecated
    public final void setMinEvictableIdleTime(final Duration minEvictableIdleTime) {
        this.minEvictableIdleDuration = PoolImplUtils.nonNull(minEvictableIdleTime, BaseObjectPoolConfig.DEFAULT_MIN_EVICTABLE_IDLE_DURATION);
    }

    @Deprecated
    public final void setMinEvictableIdleTimeMillis(final long minEvictableIdleTimeMillis) {
        setMinEvictableIdleTime(Duration.ofMillis(minEvictableIdleTimeMillis));
    }

    public final void setNumTestsPerEvictionRun(final int numTestsPerEvictionRun) {
        this.numTestsPerEvictionRun = numTestsPerEvictionRun;
    }

    public final void setSoftMinEvictableIdle(final Duration softMinEvictableIdleTime) {
        this.softMinEvictableIdleDuration = PoolImplUtils.nonNull(softMinEvictableIdleTime, BaseObjectPoolConfig.DEFAULT_SOFT_MIN_EVICTABLE_IDLE_DURATION);
    }

    @Deprecated
    public final void setSoftMinEvictableIdleTime(final Duration softMinEvictableIdleTime) {
        this.softMinEvictableIdleDuration = PoolImplUtils.nonNull(softMinEvictableIdleTime, BaseObjectPoolConfig.DEFAULT_SOFT_MIN_EVICTABLE_IDLE_DURATION);
    }

    @Deprecated
    public final void setSoftMinEvictableIdleTimeMillis(final long softMinEvictableIdleTimeMillis) {
        setSoftMinEvictableIdleTime(Duration.ofMillis(softMinEvictableIdleTimeMillis));
    }

    public final void setSwallowedExceptionListener(
            final SwallowedExceptionListener swallowedExceptionListener) {
        this.swallowedExceptionListener = swallowedExceptionListener;
    }

    public final void setTestOnBorrow(final boolean testOnBorrow) {
        this.testOnBorrow = testOnBorrow;
    }

    public final void setTestOnCreate(final boolean testOnCreate) {
        this.testOnCreate = testOnCreate;
    }

    public final void setTestOnReturn(final boolean testOnReturn) {
        this.testOnReturn = testOnReturn;
    }

    public final void setTestWhileIdle(final boolean testWhileIdle) {
        this.testWhileIdle = testWhileIdle;
    }

    public final void setTimeBetweenEvictionRuns(final Duration timeBetweenEvictionRuns) {
        // 获取配置值
        this.durationBetweenEvictionRuns = PoolImplUtils.nonNull(timeBetweenEvictionRuns, BaseObjectPoolConfig.DEFAULT_TIME_BETWEEN_EVICTION_RUNS);
        // 开启驱逐线程，用于泄露对象异步检测
        startEvictor(this.durationBetweenEvictionRuns);
    }

    @Deprecated
    public final void setTimeBetweenEvictionRunsMillis(final long timeBetweenEvictionRunsMillis) {
        setTimeBetweenEvictionRuns(Duration.ofMillis(timeBetweenEvictionRunsMillis));
    }

    // 开启空闲对象回收线程
    final void startEvictor(final Duration delay) {
        // 全局锁，避免开启和停止并发操作
        synchronized (evictionLock) {
            // delay > 0 则开启
            final boolean isPositiverDelay = PoolImplUtils.isPositive(delay);
            if (evictor == null) {
                if (isPositiverDelay) {
                    // 开启
                    evictor = new Evictor();
                    EvictionTimer.schedule(evictor, delay, delay);
                }
            } else if (isPositiverDelay) {
                // 重启空闲对象回收线程
                synchronized (EvictionTimer.class) { // Ensure no cancel can happen between cancel / schedule calls
                    EvictionTimer.cancel(evictor, evictorShutdownTimeoutDuration, true);
                    evictor = null;
                    evictionIterator = null;
                    evictor = new Evictor();
                    EvictionTimer.schedule(evictor, delay, delay);
                }
            } else {
                // 停止空闲对象回收线程
                EvictionTimer.cancel(evictor, evictorShutdownTimeoutDuration, false);
            }
        }
    }

    // 停止空闲对象回收线程
    void stopEvictor() {
        startEvictor(Duration.ofMillis(-1L));
    }

    // 异常通知
    final void swallowException(final Exception swallowException) {
        final SwallowedExceptionListener listener = getSwallowedExceptionListener();

        if (listener == null) {
            return;
        }

        try {
            listener.onSwallowException(swallowException);
        } catch (final VirtualMachineError e) {
            throw e;
        } catch (final Throwable ignored) {
            // Ignore. Enjoy the irony.
        }
    }

    @Override
    protected void toStringAppendFields(final StringBuilder builder) {
        builder.append("maxTotal=");
        builder.append(maxTotal);
        builder.append(", blockWhenExhausted=");
        builder.append(blockWhenExhausted);
        builder.append(", maxWaitDuration=");
        builder.append(maxWaitDuration);
        builder.append(", lifo=");
        builder.append(lifo);
        builder.append(", fairness=");
        builder.append(fairness);
        builder.append(", testOnCreate=");
        builder.append(testOnCreate);
        builder.append(", testOnBorrow=");
        builder.append(testOnBorrow);
        builder.append(", testOnReturn=");
        builder.append(testOnReturn);
        builder.append(", testWhileIdle=");
        builder.append(testWhileIdle);
        builder.append(", durationBetweenEvictionRuns=");
        builder.append(durationBetweenEvictionRuns);
        builder.append(", numTestsPerEvictionRun=");
        builder.append(numTestsPerEvictionRun);
        builder.append(", minEvictableIdleTimeDuration=");
        builder.append(minEvictableIdleDuration);
        builder.append(", softMinEvictableIdleTimeDuration=");
        builder.append(softMinEvictableIdleDuration);
        builder.append(", evictionPolicy=");
        builder.append(evictionPolicy);
        builder.append(", closeLock=");
        builder.append(closeLock);
        builder.append(", closed=");
        builder.append(closed);
        builder.append(", evictionLock=");
        builder.append(evictionLock);
        builder.append(", evictor=");
        builder.append(evictor);
        builder.append(", evictionIterator=");
        builder.append(evictionIterator);
        builder.append(", factoryClassLoader=");
        builder.append(factoryClassLoader);
        builder.append(", oname=");
        builder.append(objectName);
        builder.append(", creationStackTrace=");
        builder.append(creationStackTrace);
        builder.append(", borrowedCount=");
        builder.append(borrowedCount);
        builder.append(", returnedCount=");
        builder.append(returnedCount);
        builder.append(", createdCount=");
        builder.append(createdCount);
        builder.append(", destroyedCount=");
        builder.append(destroyedCount);
        builder.append(", destroyedByEvictorCount=");
        builder.append(destroyedByEvictorCount);
        builder.append(", destroyedByBorrowValidationCount=");
        builder.append(destroyedByBorrowValidationCount);
        builder.append(", activeTimes=");
        builder.append(activeTimes);
        builder.append(", idleTimes=");
        builder.append(idleTimes);
        builder.append(", waitTimes=");
        builder.append(waitTimes);
        builder.append(", maxBorrowWaitDuration=");
        builder.append(maxBorrowWaitDuration);
        builder.append(", swallowedExceptionListener=");
        builder.append(swallowedExceptionListener);
    }


    // 借用对象后的更新统计信息
    final void updateStatsBorrow(final PooledObject<T> p, final Duration waitDuration) {
        borrowedCount.incrementAndGet();
        idleTimes.add(p.getIdleDuration());
        waitTimes.add(waitDuration);

        // lock-free optimistic-locking maximum
        Duration currentMaxDuration;
        do {
            currentMaxDuration = maxBorrowWaitDuration.get();
//            if (currentMaxDuration >= waitDuration) {
//                break;
//            }
            if (currentMaxDuration.compareTo(waitDuration) >= 0) {
                break;
            }
        } while (!maxBorrowWaitDuration.compareAndSet(currentMaxDuration, waitDuration));
    }

    // 归还对象后更新统计信息
    final void updateStatsReturn(final Duration activeTime) {
        returnedCount.incrementAndGet();
        activeTimes.add(activeTime);
    }


}
