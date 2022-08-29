package org.apache.commons.pool2.impl;

import java.time.Duration;

import org.apache.commons.pool2.BaseObject;


/**
 * 对象池的配置类
 * @param <T>
 */
public abstract class BaseObjectPoolConfig<T> extends BaseObject implements Cloneable {


    // ==================================== 默认配置信息Start =======================================================

    // 对象归还时采用LIFO配置，默认true
    public static final boolean DEFAULT_LIFO = true;

    // 公平配置，默认true
    public static final boolean DEFAULT_FAIRNESS = false;

    // 借用对象时的阻塞超时时间，默认永不超时
    @Deprecated
    public static final long DEFAULT_MAX_WAIT_MILLIS = -1L;
    public static final Duration DEFAULT_MAX_WAIT = Duration.ofMillis(DEFAULT_MAX_WAIT_MILLIS);

    // 空闲对象时间阈值，超过此时间的认为是空闲对象，默认30分钟
    @Deprecated
    public static final long DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS = 1000L * 60L * 30L;
    public static final Duration DEFAULT_MIN_EVICTABLE_IDLE_DURATION =
            Duration.ofMillis(DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS);
    @Deprecated
    public static final Duration DEFAULT_MIN_EVICTABLE_IDLE_TIME =
            Duration.ofMillis(DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS);


    // 空闲对象时间阈值，超过此时间的认为是空闲对象，空闲对象大于 minIdle 时会被回收，默认-1，即超过 minIdle 立刻回收
    @Deprecated
    public static final long DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS = -1;
    @Deprecated
    public static final Duration DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME =
            Duration.ofMillis(DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS);
    public static final Duration DEFAULT_SOFT_MIN_EVICTABLE_IDLE_DURATION =
            Duration.ofMillis(DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS);


    // 空闲对象回收线程的关闭超时时间
    @Deprecated
    public static final long DEFAULT_EVICTOR_SHUTDOWN_TIMEOUT_MILLIS = 10L * 1000L;
    public static final Duration DEFAULT_EVICTOR_SHUTDOWN_TIMEOUT =
            Duration.ofMillis(DEFAULT_EVICTOR_SHUTDOWN_TIMEOUT_MILLIS);


    // 每次空闲对象检测线程扫描的对象数，默认每次三分之一
    public static final int DEFAULT_NUM_TESTS_PER_EVICTION_RUN = 3;

    // testOnCreate 配置默认值，false
    public static final boolean DEFAULT_TEST_ON_CREATE = false;

    // testOnBorrow 配置默认值，false
    public static final boolean DEFAULT_TEST_ON_BORROW = false;

    // testOnReturn 配置默认值，false
    public static final boolean DEFAULT_TEST_ON_RETURN = false;

    // testOnIdle 配置默认值，false
    public static final boolean DEFAULT_TEST_WHILE_IDLE = false;

    // 异步线程检测对象泄露周期，默认不开启
    @Deprecated
    public static final long DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS = -1L;
    public static final Duration DEFAULT_TIME_BETWEEN_EVICTION_RUNS = Duration
            .ofMillis(DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS);


    // 借用对象时阻塞等待，默认开启
    public static final boolean DEFAULT_BLOCK_WHEN_EXHAUSTED = true;

    // JMX管理，默认开启
    public static final boolean DEFAULT_JMX_ENABLE = true;

    // 默认JMX前缀
    public static final String DEFAULT_JMX_NAME_PREFIX = "pool";

    // 默认JMX名称
    public static final String DEFAULT_JMX_NAME_BASE = null;

    // 默认的空闲对象回收策略
    public static final String DEFAULT_EVICTION_POLICY_CLASS_NAME = DefaultEvictionPolicy.class.getName();

    // ==================================== 默认配置信息End =======================================================

    // ====================================  对象池配置信息Start ===========================================================
    private boolean lifo = DEFAULT_LIFO;

    private boolean fairness = DEFAULT_FAIRNESS;

    private Duration maxWaitDuration = DEFAULT_MAX_WAIT;

    private Duration minEvictableIdleDuration = DEFAULT_MIN_EVICTABLE_IDLE_TIME;

    private Duration evictorShutdownTimeoutDuration = DEFAULT_EVICTOR_SHUTDOWN_TIMEOUT;

    private Duration softMinEvictableIdleDuration = DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME;

    private int numTestsPerEvictionRun = DEFAULT_NUM_TESTS_PER_EVICTION_RUN;

    private EvictionPolicy<T> evictionPolicy; // Only 2.6.0 applications set this

    private String evictionPolicyClassName = DEFAULT_EVICTION_POLICY_CLASS_NAME;

    private boolean testOnCreate = DEFAULT_TEST_ON_CREATE;

    private boolean testOnBorrow = DEFAULT_TEST_ON_BORROW;

    private boolean testOnReturn = DEFAULT_TEST_ON_RETURN;

    private boolean testWhileIdle = DEFAULT_TEST_WHILE_IDLE;

    private Duration durationBetweenEvictionRuns = DEFAULT_TIME_BETWEEN_EVICTION_RUNS;

    private boolean blockWhenExhausted = DEFAULT_BLOCK_WHEN_EXHAUSTED;

    private boolean jmxEnabled = DEFAULT_JMX_ENABLE;

    // TODO Consider changing this to a single property for 3.x
    private String jmxNamePrefix = DEFAULT_JMX_NAME_PREFIX;

    private String jmxNameBase = DEFAULT_JMX_NAME_BASE;

    // ====================================  对象池配置信息End ===========================================================


    // ====================================  以下都是设置属性和获取属性的方法  ===========================================================

    public boolean getBlockWhenExhausted() {
        return blockWhenExhausted;
    }

    public Duration getDurationBetweenEvictionRuns() {
        return durationBetweenEvictionRuns;
    }

    public EvictionPolicy<T> getEvictionPolicy() {
        return evictionPolicy;
    }

    public String getEvictionPolicyClassName() {
        return evictionPolicyClassName;
    }

    @Deprecated
    public Duration getEvictorShutdownTimeout() {
        return evictorShutdownTimeoutDuration;
    }

    public Duration getEvictorShutdownTimeoutDuration() {
        return evictorShutdownTimeoutDuration;
    }

    @Deprecated
    public long getEvictorShutdownTimeoutMillis() {
        return evictorShutdownTimeoutDuration.toMillis();
    }

    public boolean getFairness() {
        return fairness;
    }

    public boolean getJmxEnabled() {
        return jmxEnabled;
    }

    public String getJmxNameBase() {
        return jmxNameBase;
    }

    public String getJmxNamePrefix() {
        return jmxNamePrefix;
    }

    public boolean getLifo() {
        return lifo;
    }

    public Duration getMaxWaitDuration() {
        return maxWaitDuration;
    }

    @Deprecated
    public long getMaxWaitMillis() {
        return maxWaitDuration.toMillis();
    }

    public Duration getMinEvictableIdleDuration() {
        return minEvictableIdleDuration;
    }

    @Deprecated
    public Duration getMinEvictableIdleTime() {
        return minEvictableIdleDuration;
    }

    @Deprecated
    public long getMinEvictableIdleTimeMillis() {
        return minEvictableIdleDuration.toMillis();
    }

    public int getNumTestsPerEvictionRun() {
        return numTestsPerEvictionRun;
    }

    public Duration getSoftMinEvictableIdleDuration() {
        return softMinEvictableIdleDuration;
    }

    @Deprecated
    public Duration getSoftMinEvictableIdleTime() {
        return softMinEvictableIdleDuration;
    }

    @Deprecated
    public long getSoftMinEvictableIdleTimeMillis() {
        return softMinEvictableIdleDuration.toMillis();
    }

    public boolean getTestOnBorrow() {
        return testOnBorrow;
    }

    public boolean getTestOnCreate() {
        return testOnCreate;
    }

    public boolean getTestOnReturn() {
        return testOnReturn;
    }

    public boolean getTestWhileIdle() {
        return testWhileIdle;
    }

    @Deprecated
    public Duration getTimeBetweenEvictionRuns() {
        return durationBetweenEvictionRuns;
    }

    @Deprecated
    public long getTimeBetweenEvictionRunsMillis() {
        return durationBetweenEvictionRuns.toMillis();
    }

    public void setBlockWhenExhausted(final boolean blockWhenExhausted) {
        this.blockWhenExhausted = blockWhenExhausted;
    }

    public void setEvictionPolicy(final EvictionPolicy<T> evictionPolicy) {
        this.evictionPolicy = evictionPolicy;
    }

    public void setEvictionPolicyClassName(final String evictionPolicyClassName) {
        this.evictionPolicyClassName = evictionPolicyClassName;
    }

    public void setEvictorShutdownTimeout(final Duration evictorShutdownTimeoutDuration) {
        this.evictorShutdownTimeoutDuration = PoolImplUtils.nonNull(evictorShutdownTimeoutDuration, DEFAULT_EVICTOR_SHUTDOWN_TIMEOUT);
    }

    @Deprecated
    public void setEvictorShutdownTimeoutMillis(final Duration evictorShutdownTimeout) {
        setEvictorShutdownTimeout(evictorShutdownTimeout);
    }

    @Deprecated
    public void setEvictorShutdownTimeoutMillis(final long evictorShutdownTimeoutMillis) {
        setEvictorShutdownTimeout(Duration.ofMillis(evictorShutdownTimeoutMillis));
    }

    public void setFairness(final boolean fairness) {
        this.fairness = fairness;
    }

    public void setJmxEnabled(final boolean jmxEnabled) {
        this.jmxEnabled = jmxEnabled;
    }

    public void setJmxNameBase(final String jmxNameBase) {
        this.jmxNameBase = jmxNameBase;
    }

    public void setJmxNamePrefix(final String jmxNamePrefix) {
        this.jmxNamePrefix = jmxNamePrefix;
    }

    public void setLifo(final boolean lifo) {
        this.lifo = lifo;
    }

    public void setMaxWait(final Duration maxWaitDuration) {
        this.maxWaitDuration = PoolImplUtils.nonNull(maxWaitDuration, DEFAULT_MAX_WAIT);
    }

    @Deprecated
    public void setMaxWaitMillis(final long maxWaitMillis) {
        setMaxWait(Duration.ofMillis(maxWaitMillis));
    }

    public void setMinEvictableIdleTime(final Duration minEvictableIdleTime) {
        this.minEvictableIdleDuration = PoolImplUtils.nonNull(minEvictableIdleTime, DEFAULT_MIN_EVICTABLE_IDLE_TIME);
    }

    @Deprecated
    public void setMinEvictableIdleTimeMillis(final long minEvictableIdleTimeMillis) {
        this.minEvictableIdleDuration = Duration.ofMillis(minEvictableIdleTimeMillis);
    }

    public void setNumTestsPerEvictionRun(final int numTestsPerEvictionRun) {
        this.numTestsPerEvictionRun = numTestsPerEvictionRun;
    }

    public void setSoftMinEvictableIdleTime(final Duration softMinEvictableIdleTime) {
        this.softMinEvictableIdleDuration = PoolImplUtils.nonNull(softMinEvictableIdleTime, DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME);
    }

    @Deprecated
    public void setSoftMinEvictableIdleTimeMillis(
            final long softMinEvictableIdleTimeMillis) {
        setSoftMinEvictableIdleTime(Duration.ofMillis(softMinEvictableIdleTimeMillis));
    }

    public void setTestOnBorrow(final boolean testOnBorrow) {
        this.testOnBorrow = testOnBorrow;
    }

    public void setTestOnCreate(final boolean testOnCreate) {
        this.testOnCreate = testOnCreate;
    }

    public void setTestOnReturn(final boolean testOnReturn) {
        this.testOnReturn = testOnReturn;
    }

    public void setTestWhileIdle(final boolean testWhileIdle) {
        this.testWhileIdle = testWhileIdle;
    }

    public void setTimeBetweenEvictionRuns(final Duration timeBetweenEvictionRuns) {
        this.durationBetweenEvictionRuns = PoolImplUtils.nonNull(timeBetweenEvictionRuns, DEFAULT_TIME_BETWEEN_EVICTION_RUNS);
    }

    @Deprecated
    public void setTimeBetweenEvictionRunsMillis(final long timeBetweenEvictionRunsMillis) {
        setTimeBetweenEvictionRuns(Duration.ofMillis(timeBetweenEvictionRunsMillis));
    }

    @Override
    protected void toStringAppendFields(final StringBuilder builder) {
        builder.append("lifo=");
        builder.append(lifo);
        builder.append(", fairness=");
        builder.append(fairness);
        builder.append(", maxWaitDuration=");
        builder.append(maxWaitDuration);
        builder.append(", minEvictableIdleTime=");
        builder.append(minEvictableIdleDuration);
        builder.append(", softMinEvictableIdleTime=");
        builder.append(softMinEvictableIdleDuration);
        builder.append(", numTestsPerEvictionRun=");
        builder.append(numTestsPerEvictionRun);
        builder.append(", evictionPolicyClassName=");
        builder.append(evictionPolicyClassName);
        builder.append(", testOnCreate=");
        builder.append(testOnCreate);
        builder.append(", testOnBorrow=");
        builder.append(testOnBorrow);
        builder.append(", testOnReturn=");
        builder.append(testOnReturn);
        builder.append(", testWhileIdle=");
        builder.append(testWhileIdle);
        builder.append(", timeBetweenEvictionRuns=");
        builder.append(durationBetweenEvictionRuns);
        builder.append(", blockWhenExhausted=");
        builder.append(blockWhenExhausted);
        builder.append(", jmxEnabled=");
        builder.append(jmxEnabled);
        builder.append(", jmxNamePrefix=");
        builder.append(jmxNamePrefix);
        builder.append(", jmxNameBase=");
        builder.append(jmxNameBase);
    }
}
