package org.apache.commons.pool2.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.commons.pool2.DestroyMode;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PoolUtils;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.PooledObjectState;
import org.apache.commons.pool2.SwallowedExceptionListener;
import org.apache.commons.pool2.TrackedUse;
import org.apache.commons.pool2.UsageTracking;

/**
 * 对象池基础实现
 * @param <T>
 * @param <E>
 */
public class GenericObjectPool<T, E extends Exception> extends BaseGenericObjectPool<T, E>
        implements ObjectPool<T, E>, GenericObjectPoolMXBean, UsageTracking<T> {

    // JMX specific attributes
    private static final String ONAME_BASE =
        "org.apache.commons.pool2:type=GenericObjectPool,name=";

    private volatile String factoryType;

    private volatile int maxIdle = GenericObjectPoolConfig.DEFAULT_MAX_IDLE;

    private volatile int minIdle = GenericObjectPoolConfig.DEFAULT_MIN_IDLE;

    private final PooledObjectFactory<T, E> factory;

    /*
     * All of the objects currently associated with this pool in any state. It
     * excludes objects that have been destroyed. The size of
     * {@link #allObjects} will always be less than or equal to {@link
     * #_maxActive}. Map keys are pooled objects, values are the PooledObject
     * wrappers used internally by the pool.
     */
    private final ConcurrentHashMap<IdentityWrapper<T>, PooledObject<T>> allObjects = new ConcurrentHashMap<>();

    /*
     * The combined count of the currently created objects and those in the
     * process of being created. Under load, it may exceed {@link #_maxActive}
     * if multiple threads try and create a new object at the same time but
     * {@link #create()} will ensure that there are never more than
     * {@link #_maxActive} objects created at any one time.
     */
    private final AtomicLong createCount = new AtomicLong();

    private long makeObjectCount;

    private final Object makeObjectCountLock = new Object();

    private final LinkedBlockingDeque<PooledObject<T>> idleObjects;

    /**
     * Creates a new {@code GenericObjectPool} using defaults from
     * {@link GenericObjectPoolConfig}.
     *
     * @param factory The object factory to be used to create object instances
     *                used by this pool
     */
    public GenericObjectPool(final PooledObjectFactory<T, E> factory) {
        this(factory, new GenericObjectPoolConfig<>());
    }

    /**
     * Creates a new {@code GenericObjectPool} using a specific
     * configuration.
     *
     * @param factory   The object factory to be used to create object instances
     *                  used by this pool
     * @param config    The configuration to use for this pool instance. The
     *                  configuration is used by value. Subsequent changes to
     *                  the configuration object will not be reflected in the
     *                  pool.
     */
    public GenericObjectPool(final PooledObjectFactory<T, E> factory,
            final GenericObjectPoolConfig<T> config) {

        super(config, ONAME_BASE, config.getJmxNamePrefix());

        if (factory == null) {
            jmxUnregister(); // tidy up
            throw new IllegalArgumentException("Factory may not be null");
        }
        this.factory = factory;

        idleObjects = new LinkedBlockingDeque<>(config.getFairness());

        setConfig(config);
    }

    /**
     * Creates a new {@code GenericObjectPool} that tracks and destroys
     * objects that are checked out, but never returned to the pool.
     *
     * @param factory   The object factory to be used to create object instances
     *                  used by this pool
     * @param config    The base pool configuration to use for this pool instance.
     *                  The configuration is used by value. Subsequent changes to
     *                  the configuration object will not be reflected in the
     *                  pool.
     * @param abandonedConfig  Configuration for abandoned object identification
     *                         and removal.  The configuration is used by value.
     */
    public GenericObjectPool(final PooledObjectFactory<T, E> factory,
            final GenericObjectPoolConfig<T> config, final AbandonedConfig abandonedConfig) {
        this(factory, config);
        setAbandonedConfig(abandonedConfig);
    }

    /**
     * Adds the provided wrapped pooled object to the set of idle objects for
     * this pool. The object must already be part of the pool.  If {@code p}
     * is null, this is a no-op (no exception, but no impact on the pool).
     *
     * @param p The object to make idle
     *
     * @throws E If the factory fails to passivate the object
     */
    private void addIdleObject(final PooledObject<T> p) throws E {
        if (p != null) {
            factory.passivateObject(p);
            if (getLifo()) {
                idleObjects.addFirst(p);
            } else {
                idleObjects.addLast(p);
            }
        }
    }

    /**
     * Creates an object, and place it into the pool. addObject() is useful for
     * "pre-loading" a pool with idle objects.
     * <p>
     * If there is no capacity available to add to the pool, this is a no-op
     * (no exception, no impact to the pool). </p>
     */
    @Override
    public void addObject() throws E {
        assertOpen();
        if (factory == null) {
            throw new IllegalStateException("Cannot add objects without a factory.");
        }
        addIdleObject(create());
    }

    /**
     * 获取池对象
     * @return  返回池对象实例
     * @throws E    获取失败时抛出的异常
     */
    @Override
    public T borrowObject() throws E {
        return borrowObject(getMaxWaitDuration());
    }
    public T borrowObject(final Duration borrowMaxWaitDuration) throws E {
        // 1.检查对象池状态
        assertOpen();

        // 2.废弃/泄露对象检测
        final AbandonedConfig ac = this.abandonedConfig;
        if (ac != null && ac.getRemoveAbandonedOnBorrow() && getNumIdle() < 2 &&
                getNumActive() > getMaxTotal() - 3) {
            // 开启泄露检测 && 池对象很多(> max -3) && 大部分都是借出状态(idle < 2)
            // 此时怀疑客户端使用有误，导致大量池对象没有归还，开启检测机制
            removeAbandoned(ac);
        }

        // 获取的池对象
        PooledObject<T> p = null;
        // 获取池资源耗尽时的阻塞策略，提前获取，避免运行时被修改，导致前后获取不一致
        final boolean blockWhenExhausted = getBlockWhenExhausted();
        // 是否需要创建
        boolean create;
        // 阻塞时间
        final long waitTimeMillis = System.currentTimeMillis();

        // 3.获取池对象
        while (p == null) {
            create = false;
            // 3.1 非阻塞尝试获取
            p = idleObjects.pollFirst();
            if (p == null) {
                // 3.2 暂无可用对象，尝试创建对象
                p = create();
                if (p != null) {
                    create = true;
                }
            }
            // 3.3 检查是否获取到对象
            if (blockWhenExhausted) {
                // 3.3.1 阻塞模式下，阻塞等待是否有对象返回池中
                if (p == null) {
                    try {
                        p = borrowMaxWaitDuration.isNegative() ? idleObjects.takeFirst() : idleObjects.pollFirst(borrowMaxWaitDuration);
                    } catch (final InterruptedException e) {
                        // Don't surface exception type of internal locking mechanism.
                        throw cast(e);
                    }
                }
                if (p == null) {
                    throw new NoSuchElementException(appendStats(
                            "Timeout waiting for idle object, borrowMaxWaitDuration=" + borrowMaxWaitDuration));
                }
            } else if (p == null) {
                // 3.3.2 非阻塞模式下，获取对象失败 && 创建对象失败 直接抛出异常
                throw new NoSuchElementException(appendStats("Pool exhausted"));
            }

            // 3.4 分配对象
            if (!p.allocate()) {
                // 初始化失败，直接丢弃，开始下一轮
                p = null;
            }

            if (p != null) {
                try {
                    // 3.5 激活对象
                    factory.activateObject(p);
                } catch (final Exception e) {
                    try {
                        destroy(p, DestroyMode.NORMAL);
                    } catch (final Exception ignored) {
                        // ignored - activation failure is more important
                    }
                    p = null;
                    if (create) {
                        final NoSuchElementException nsee = new NoSuchElementException(
                                appendStats("Unable to activate object"));
                        nsee.initCause(e);
                        throw nsee;
                    }
                }
                // 3.6 开启TestOnBorrow时，校验对象有效性
                if (p != null && getTestOnBorrow()) {
                    boolean validate = false;
                    Throwable validationThrowable = null;
                    try {
                        validate = factory.validateObject(p);
                    } catch (final Throwable t) {
                        PoolUtils.checkRethrow(t);
                        validationThrowable = t;
                    }
                    if (!validate) {
                        try {
                            // 如果无效，直接销毁对象
                            destroy(p, DestroyMode.NORMAL);
                            destroyedByBorrowValidationCount.incrementAndGet();
                        } catch (final Exception ignored) {
                            // ignored - validation failure is more important
                        }
                        p = null;
                        if (create) {
                            final NoSuchElementException nsee = new NoSuchElementException(
                                    appendStats("Unable to validate object"));
                            nsee.initCause(validationThrowable);
                            throw nsee;
                        }
                    }
                }
            }
        }

        // 4.更新借用对象相关的统计数据
        updateStatsBorrow(p, Duration.ofMillis(System.currentTimeMillis() - waitTimeMillis));

        // 5.返回对象实例
        return p.getObject();
    }
    public T borrowObject(final long borrowMaxWaitMillis) throws E {
        return borrowObject(Duration.ofMillis(borrowMaxWaitMillis));
    }

    /**
     * Clears any objects sitting idle in the pool by removing them from the
     * idle instance pool and then invoking the configured
     * {@link PooledObjectFactory#destroyObject(PooledObject)} method on each
     * idle instance.
     * <p>
     * Implementation notes:
     * </p>
     * <ul>
     * <li>This method does not destroy or effect in any way instances that are
     * checked out of the pool when it is invoked.</li>
     * <li>Invoking this method does not prevent objects being returned to the
     * idle instance pool, even during its execution. Additional instances may
     * be returned while removed items are being destroyed.</li>
     * <li>Exceptions encountered destroying idle instances are swallowed
     * but notified via a {@link SwallowedExceptionListener}.</li>
     * </ul>
     */
    @Override
    public void clear() {
        PooledObject<T> p = idleObjects.poll();

        while (p != null) {
            try {
                destroy(p, DestroyMode.NORMAL);
            } catch (final Exception e) {
                swallowException(e);
            }
            p = idleObjects.poll();
        }
    }

    /**
     * Closes the pool. Once the pool is closed, {@link #borrowObject()} will
     * fail with IllegalStateException, but {@link #returnObject(Object)} and
     * {@link #invalidateObject(Object)} will continue to work, with returned
     * objects destroyed on return.
     * <p>
     * Destroys idle instances in the pool by invoking {@link #clear()}.
     * </p>
     */
    @Override
    public void close() {
        if (isClosed()) {
            return;
        }

        synchronized (closeLock) {
            if (isClosed()) {
                return;
            }

            // Stop the evictor before the pool is closed since evict() calls
            // assertOpen()

            stopEvictor();
            closed = true;
            // This clear removes any idle objects
            clear();

            jmxUnregister();

            // Release any threads that were waiting for an object
            idleObjects.interuptTakeWaiters();
        }
    }

    /**
     * Attempts to create a new wrapped pooled object.
     * <p>
     * If there are {@link #getMaxTotal()} objects already in circulation or in process of being created, this method
     * returns null.
     * </p>
     *
     * @return The new wrapped pooled object
     * @throws E if the object factory's {@code makeObject} fails
     */
    private PooledObject<T> create() throws E {
        int localMaxTotal = getMaxTotal();
        // This simplifies the code later in this method
        if (localMaxTotal < 0) {
            localMaxTotal = Integer.MAX_VALUE;
        }

        final long localStartTimeMillis = System.currentTimeMillis();
        final long localMaxWaitTimeMillis = Math.max(getMaxWaitDuration().toMillis(), 0);

        // Flag that indicates if create should:
        // - TRUE:  call the factory to create an object
        // - FALSE: return null
        // - null:  loop and re-test the condition that determines whether to
        //          call the factory
        Boolean create = null;
        while (create == null) {
            synchronized (makeObjectCountLock) {
                final long newCreateCount = createCount.incrementAndGet();
                if (newCreateCount > localMaxTotal) {
                    // The pool is currently at capacity or in the process of
                    // making enough new objects to take it to capacity.
                    createCount.decrementAndGet();
                    if (makeObjectCount == 0) {
                        // There are no makeObject() calls in progress so the
                        // pool is at capacity. Do not attempt to create a new
                        // object. Return and wait for an object to be returned
                        create = Boolean.FALSE;
                    } else {
                        // There are makeObject() calls in progress that might
                        // bring the pool to capacity. Those calls might also
                        // fail so wait until they complete and then re-test if
                        // the pool is at capacity or not.
                        try {
                            makeObjectCountLock.wait(localMaxWaitTimeMillis);
                        } catch (final InterruptedException e) {
                            // Don't surface exception type of internal locking mechanism.
                            throw cast(e);
                        }
                    }
                } else {
                    // The pool is not at capacity. Create a new object.
                    makeObjectCount++;
                    create = Boolean.TRUE;
                }
            }

            // Do not block more if maxWaitTimeMillis is set.
            if (create == null &&
                localMaxWaitTimeMillis > 0 &&
                 System.currentTimeMillis() - localStartTimeMillis >= localMaxWaitTimeMillis) {
                create = Boolean.FALSE;
            }
        }

        if (!create.booleanValue()) {
            return null;
        }

        final PooledObject<T> p;
        try {
            p = factory.makeObject();
            if (getTestOnCreate() && !factory.validateObject(p)) {
                createCount.decrementAndGet();
                return null;
            }
        } catch (final Throwable e) {
            createCount.decrementAndGet();
            throw e;
        } finally {
            synchronized (makeObjectCountLock) {
                makeObjectCount--;
                makeObjectCountLock.notifyAll();
            }
        }

        final AbandonedConfig ac = this.abandonedConfig;
        if (ac != null && ac.getLogAbandoned()) {
            p.setLogAbandoned(true);
            p.setRequireFullStackTrace(ac.getRequireFullStackTrace());
        }

        createdCount.incrementAndGet();
        allObjects.put(new IdentityWrapper<>(p.getObject()), p);
        return p;
    }

    /**
     * Destroys a wrapped pooled object.
     *
     * @param toDestroy The wrapped pooled object to destroy
     * @param destroyMode DestroyMode context provided to the factory
     *
     * @throws E If the factory fails to destroy the pooled object
     *                   cleanly
     */
    private void destroy(final PooledObject<T> toDestroy, final DestroyMode destroyMode) throws E {
        toDestroy.invalidate();
        idleObjects.remove(toDestroy);
        allObjects.remove(new IdentityWrapper<>(toDestroy.getObject()));
        try {
            factory.destroyObject(toDestroy, destroyMode);
        } finally {
            destroyedCount.incrementAndGet();
            createCount.decrementAndGet();
        }
    }

    /**
     * Tries to ensure that {@code idleCount} idle instances exist in the pool.
     * <p>
     * Creates and adds idle instances until either {@link #getNumIdle()} reaches {@code idleCount}
     * or the total number of objects (idle, checked out, or being created) reaches
     * {@link #getMaxTotal()}. If {@code always} is false, no instances are created unless
     * there are threads waiting to check out instances from the pool.
     * </p>
     *
     * @param idleCount the number of idle instances desired
     * @param always true means create instances even if the pool has no threads waiting
     * @throws E if the factory's makeObject throws
     */
    private void ensureIdle(final int idleCount, final boolean always) throws E {
        if (idleCount < 1 || isClosed() || !always && !idleObjects.hasTakeWaiters()) {
            return;
        }

        while (idleObjects.size() < idleCount) {
            final PooledObject<T> p = create();
            if (p == null) {
                // Can't create objects, no reason to think another call to
                // create will work. Give up.
                break;
            }
            if (getLifo()) {
                idleObjects.addFirst(p);
            } else {
                idleObjects.addLast(p);
            }
        }
        if (isClosed()) {
            // Pool closed while object was being added to idle objects.
            // Make sure the returned object is destroyed rather than left
            // in the idle object pool (which would effectively be a leak)
            clear();
        }
    }

    @Override
    void ensureMinIdle() throws E {
        ensureIdle(getMinIdle(), true);
    }


    // 驱逐多余的空闲对象
    @Override
    public void evict() throws E {

        // 1.检查对象池状态
        assertOpen();

        // 2.检查是否有空闲对象
        if (!idleObjects.isEmpty()) {

            // 备份一下驱逐策略
            PooledObject<T> underTest = null;
            final EvictionPolicy<T> evictionPolicy = getEvictionPolicy();

            // 3.加锁
            synchronized (evictionLock) {
                // 驱逐配置
                final EvictionConfig evictionConfig = new EvictionConfig(
                        getMinEvictableIdleDuration(),
                        getSoftMinEvictableIdleDuration(),
                        getMinIdle());

                // 是否空闲检测
                final boolean testWhileIdle = getTestWhileIdle();

                // 4.遍历空闲对象
                for (int i = 0, m = getNumTests(); i < m; i++) {
                    if (evictionIterator == null || !evictionIterator.hasNext()) {
                        // 5.第一次检测或所有空闲对象已经遍历完，重新构建迭代器
                        evictionIterator = new EvictionIterator(idleObjects);
                    }

                    if (!evictionIterator.hasNext()) {
                        // 6.没有空闲对象，直接结束
                        return;
                    }

                    try {
                        // 7.获取空闲对象
                        underTest = evictionIterator.next();
                    } catch (final NoSuchElementException nsee) {
                        // 其他线程借走了此对象，跳过
                        i--;
                        evictionIterator = null;
                        continue;
                    }

                    // 8.调用对象方法，通知对象开始空闲回收测试，对象进行状态修改
                    if (!underTest.startEvictionTest()) {
                        // Object was borrowed in another thread
                        // Don't count this as an eviction test so reduce i;
                        i--;
                        continue;
                    }

                    // User provided eviction policy could throw all sorts of
                    // crazy exceptions. Protect against such an exception
                    // killing the eviction thread.
                    boolean evict;
                    try {
                        // 9.通过策略判断是否需要回收
                        evict = evictionPolicy.evict(evictionConfig, underTest,
                                idleObjects.size());
                    } catch (final Throwable t) {
                        // Slightly convoluted as SwallowedExceptionListener
                        // uses Exception rather than Throwable
                        // 捕捉驱逐策略抛出的异常，避免用户实现的驱逐策略导致整个驱逐线程挂掉
                        PoolUtils.checkRethrow(t);
                        swallowException(new Exception(t));
                        // Don't evict on error conditions
                        evict = false;
                    }


                    if (evict) {
                        // 10.1 此对象需要回收，进行销毁并更新相关统计数据
                        destroy(underTest, DestroyMode.NORMAL);
                        destroyedByEvictorCount.incrementAndGet();
                    } else {
                        // 10.2 对象不应该被回收
                        if (testWhileIdle) {
                            // 10.3 开启空闲时测试有效性，检测对象有效性，检查之前需要先激活 (因为空闲对象返回池中时会钝化)
                            boolean active = false;
                            try {
                                // 10.4 激活对象
                                factory.activateObject(underTest);
                                active = true;
                            } catch (final Exception e) {
                                // 10.5 激活失败直接销毁
                                destroy(underTest, DestroyMode.NORMAL);
                                destroyedByEvictorCount.incrementAndGet();
                            }
                            if (active) {
                                // 10.6 激活之后校验有效性
                                boolean validate = false;
                                Throwable validationThrowable = null;
                                try {
                                    validate = factory.validateObject(underTest);
                                } catch (final Throwable t) {
                                    PoolUtils.checkRethrow(t);
                                    validationThrowable = t;
                                }
                                if (!validate) {
                                    // 10.7 对象无效则销毁
                                    destroy(underTest, DestroyMode.NORMAL);
                                    destroyedByEvictorCount.incrementAndGet();
                                    if (validationThrowable != null) {
                                        if (validationThrowable instanceof RuntimeException) {
                                            throw (RuntimeException) validationThrowable;
                                        }
                                        throw (Error) validationThrowable;
                                    }
                                } else {
                                    try {
                                        // 10.8 对象有效则重新钝化
                                        factory.passivateObject(underTest);
                                    } catch (final Exception e) {
                                        destroy(underTest, DestroyMode.NORMAL);
                                        destroyedByEvictorCount.incrementAndGet();
                                    }
                                }
                            }
                        }

                        // 11.通知对象空闲回收结束
                        underTest.endEvictionTest(idleObjects);
                        // TODO - May need to add code here once additional
                        // states are used
                    }
                }
            }
        }
        final AbandonedConfig ac = this.abandonedConfig;
        if (ac != null && ac.getRemoveAbandonedOnMaintenance()) {
            // 12.如果开启泄露检测，执行一次泄露检测流程
            removeAbandoned(ac);
        }
    }

    /**
     * Gets a reference to the factory used to create, destroy and validate
     * the objects used by this pool.
     *
     * @return the factory
     */
    public PooledObjectFactory<T, E> getFactory() {
        return factory;
    }


    // 获取工厂类型
    @Override
    public String getFactoryType() {
        // Not thread safe. Accept that there may be multiple evaluations.
        if (factoryType == null) {
            final StringBuilder result = new StringBuilder();
            result.append(factory.getClass().getName());
            result.append('<');
            final Class<?> pooledObjectType =
                    PoolImplUtils.getFactoryType(factory.getClass());
            result.append(pooledObjectType.getName());
            result.append('>');
            factoryType = result.toString();
        }
        return factoryType;
    }


    // 获取最大空闲数量
    @Override
    public int getMaxIdle() {
        return maxIdle;
    }


    // 获取最小空闲数量
    @Override
    public int getMinIdle() {
        final int maxIdleSave = getMaxIdle();
        return Math.min(this.minIdle, maxIdleSave);
    }

    // 获取使用中的对象数
    @Override
    public int getNumActive() {
        return allObjects.size() - idleObjects.size();
    }

    @Override
    public int getNumIdle() {
        return idleObjects.size();
    }

    // 计算每次空闲驱逐要检查的对象数
    private int getNumTests() {
        final int numTestsPerEvictionRun = getNumTestsPerEvictionRun();
        if (numTestsPerEvictionRun >= 0) {
            return Math.min(numTestsPerEvictionRun, idleObjects.size());
        }
        return (int) Math.ceil(idleObjects.size() /
                Math.abs((double) numTestsPerEvictionRun));
    }

   // 获取当前阻塞等待借用对象的线程数
    @Override
    public int getNumWaiters() {
        if (getBlockWhenExhausted()) {
            return idleObjects.getTakeQueueLength();
        }
        return 0;
    }

    PooledObject<T> getPooledObject(final T obj) {
        return allObjects.get(new IdentityWrapper<>(obj));
    }

    @Override
    String getStatsString() {
        // Simply listed in AB order.
        return super.getStatsString() +
                String.format(", createdCount=%,d, makeObjectCount=%,d, maxIdle=%,d, minIdle=%,d",
                        createdCount.get(), makeObjectCount, maxIdle, minIdle);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Activation of this method decrements the active count and attempts to destroy the instance, using the default
     * (NORMAL) {@link DestroyMode}.
     * </p>
     *
     * @throws E if an exception occurs destroying the
     * @throws IllegalStateException if obj does not belong to this pool
     */
    @Override
    public void invalidateObject(final T obj) throws E {
        invalidateObject(obj, DestroyMode.NORMAL);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Activation of this method decrements the active count and attempts to destroy the instance, using the provided
     * {@link DestroyMode}.
     * </p>
     *
     * @throws E if an exception occurs destroying the object
     * @throws IllegalStateException if obj does not belong to this pool
     * @since 2.9.0
     */
    @Override
    public void invalidateObject(final T obj, final DestroyMode destroyMode) throws E {
        final PooledObject<T> p = getPooledObject(obj);
        if (p == null) {
            if (isAbandonedConfig()) {
                return;
            }
            throw new IllegalStateException(
                    "Invalidated object not currently part of this pool");
        }
        synchronized (p) {
            if (p.getState() != PooledObjectState.INVALID) {
                // 销毁对象
                destroy(p, destroyMode);
            }
        }
        // 更新空闲数量
        ensureIdle(1, false);
    }


    // 获取所有池对象信息
    @Override
    public Set<DefaultPooledObjectInfo> listAllObjects() {
        return allObjects.values().stream().map(DefaultPooledObjectInfo::new).collect(Collectors.toSet());
    }
    /**
     * Tries to ensure that {@link #getMinIdle()} idle instances are available
     * in the pool.
     *
     * @throws E If the associated factory throws an exception
     * @since 2.4
     */
    public void preparePool() throws E {
        if (getMinIdle() < 1) {
            return;
        }
        ensureMinIdle();
    }

    // 销毁被借出但长时间未使用的对象
    @SuppressWarnings("resource") // PrintWriter is managed elsewhere
    private void removeAbandoned(final AbandonedConfig abandonedConfig) {
        // 生成要删除的废弃对象列表
        final ArrayList<PooledObject<T>> remove = createRemoveList(abandonedConfig, allObjects);
        // 移除废弃对象
        remove.forEach(pooledObject -> {
            // 记录日志
            if (abandonedConfig.getLogAbandoned()) {
                pooledObject.printStackTrace(abandonedConfig.getLogWriter());
            }
            try {
                // 销毁对象
                invalidateObject(pooledObject.getObject(), DestroyMode.ABANDONED);
            } catch (final Exception e) {
                swallowException(e);
            }
        });
    }


    // 返回对象
    @Override
    public void returnObject(final T obj) {

        // 1.检查是否为池中的对象
        final PooledObject<T> p = getPooledObject(obj);

        if (p == null) {
            // 不开启泄露对象检测时，直接抛出异常 (开启泄露检测后，对象池可能强制销毁长时间空闲的对象，当使用方后续调用return时，不抛出异常，和关闭泄露检测情况下保持一致)
            if (!isAbandonedConfig()) {
                throw new IllegalStateException(
                        "Returned object not currently part of this pool");
            }
            return; // Object was abandoned and removed
        }

        // 2.修改状态为返回中
        markReturningState(p);

        // 3.记录上次借用时长
        final Duration activeTime = p.getActiveDuration();

        // 4.开启TestOnReturn时，校验对象有效性
        if (getTestOnReturn() && !factory.validateObject(p)) {
            try {
                // 4.1 对象无效，直接销毁对象
                destroy(p, DestroyMode.NORMAL);
            } catch (final Exception e) {
                swallowException(e);
            }
            try {
                // 4.2 更新空闲对象数
                ensureIdle(1, false);
            } catch (final Exception e) {
                swallowException(e);
            }
            // 4.3 更新归还对象的统计信息
            updateStatsReturn(activeTime);
            return;
        }

        try {
            // 5.钝化对象
            factory.passivateObject(p);
        } catch (final Exception e1) {
            swallowException(e1);
            try {
                // 钝化失败则销毁
                destroy(p, DestroyMode.NORMAL);
            } catch (final Exception e) {
                swallowException(e);
            }
            try {
                ensureIdle(1, false);
            } catch (final Exception e) {
                swallowException(e);
            }
            updateStatsReturn(activeTime);
            return;
        }

        // 6.调用对象的归还方法
        if (!p.deallocate()) {
            throw new IllegalStateException(
                    "Object has already been returned to this pool or is invalid");
        }

        // 7.检查空闲对象数
        final int maxIdleSave = getMaxIdle();
        if (isClosed() || maxIdleSave > -1 && maxIdleSave <= idleObjects.size()) {
            // 7.1 大于配置最大的空闲对象数，直接销毁
            try {
                destroy(p, DestroyMode.NORMAL);
            } catch (final Exception e) {
                swallowException(e);
            }
            try {
                ensureIdle(1, false);
            } catch (final Exception e) {
                swallowException(e);
            }
        } else {
            // 7.2 小于配置的最大空闲数，则根据配置返回队头或队尾
            if (getLifo()) {
                idleObjects.addFirst(p);
            } else {
                idleObjects.addLast(p);
            }
            if (isClosed()) {
                // Pool closed while object was being added to idle objects.
                // Make sure the returned object is destroyed rather than left
                // in the idle object pool (which would effectively be a leak)
                clear();
            }
        }

        // 8.更新归还对象的统计信息
        updateStatsReturn(activeTime);
    }

    /**
     * Sets the cap on the number of "idle" instances in the pool. If maxIdle
     * is set too low on heavily loaded systems it is possible you will see
     * objects being destroyed and almost immediately new objects being created.
     * This is a result of the active threads momentarily returning objects
     * faster than they are requesting them, causing the number of idle
     * objects to rise above maxIdle. The best value for maxIdle for heavily
     * loaded system will vary but the default is a good starting point.
     *
     * @param maxIdle
     *            The cap on the number of "idle" instances in the pool. Use a
     *            negative value to indicate an unlimited number of idle
     *            instances
     *
     * @see #getMaxIdle
     */
    public void setMaxIdle(final int maxIdle) {
        this.maxIdle = maxIdle;
    }

    /**
     * Sets the base pool configuration.
     *
     * @param conf the new configuration to use. This is used by value.
     *
     * @see GenericObjectPoolConfig
     */
    public void setConfig(final GenericObjectPoolConfig<T> conf) {
        // 更新配置，通用配置收归到父类
        super.setConfig(conf);
        // 池核心配置
        setMaxIdle(conf.getMaxIdle());
        setMinIdle(conf.getMinIdle());
        setMaxTotal(conf.getMaxTotal());
    }

    /**
     * Sets the target for the minimum number of idle objects to maintain in
     * the pool. This setting only has an effect if it is positive and
     * {@link #getDurationBetweenEvictionRuns()} is greater than zero. If this
     * is the case, an attempt is made to ensure that the pool has the required
     * minimum number of instances during idle object eviction runs.
     * <p>
     * If the configured value of minIdle is greater than the configured value
     * for maxIdle then the value of maxIdle will be used instead.
     * </p>
     *
     * @param minIdle
     *            The minimum number of objects.
     *
     * @see #getMinIdle()
     * @see #getMaxIdle()
     * @see #getDurationBetweenEvictionRuns()
     */
    public void setMinIdle(final int minIdle) {
        this.minIdle = minIdle;
    }

    @Override
    protected void toStringAppendFields(final StringBuilder builder) {
        super.toStringAppendFields(builder);
        builder.append(", factoryType=");
        builder.append(factoryType);
        builder.append(", maxIdle=");
        builder.append(maxIdle);
        builder.append(", minIdle=");
        builder.append(minIdle);
        builder.append(", factory=");
        builder.append(factory);
        builder.append(", allObjects=");
        builder.append(allObjects);
        builder.append(", createCount=");
        builder.append(createCount);
        builder.append(", idleObjects=");
        builder.append(idleObjects);
        builder.append(", abandonedConfig=");
        builder.append(abandonedConfig);
    }

    @Override
    public void use(final T pooledObject) {
        final AbandonedConfig abandonedCfg = this.abandonedConfig;
        if (abandonedCfg != null && abandonedCfg.getUseUsageTracking()) {
            getPooledObject(pooledObject).use();
        }
    }

}
