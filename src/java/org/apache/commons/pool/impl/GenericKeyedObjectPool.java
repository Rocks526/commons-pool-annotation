/*
 * $Header: /home/jerenkrantz/tmp/commons/commons-convert/cvs/home/cvs/jakarta-commons//pool/src/java/org/apache/commons/pool/impl/GenericKeyedObjectPool.java,v 1.4 2002/05/01 06:33:01 rwaldhoff Exp $
 * $Revision: 1.4 $
 * $Date: 2002/05/01 06:33:01 $
 *
 * ====================================================================
 *
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 1999-2001 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "The Jakarta Project", "Commons", and "Apache Software
 *    Foundation" must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache"
 *    nor may "Apache" appear in their names without prior written
 *    permission of the Apache Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.commons.pool.impl;

import org.apache.commons.pool.*;
import org.apache.commons.collections.CursorableLinkedList;
import org.apache.commons.collections.CursorableLinkedList.Cursor;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Enumeration;
import java.util.EmptyStackException;
import java.util.ListIterator;
import java.util.HashMap;
import java.util.Set;

/**
 * A configurable {@link KeyedObjectPool} implementation.
 * <p>
 * When coupled with the appropriate {@link KeyedPoolableObjectFactory},
 * <tt>GenericKeyedObjectPool</tt> provides robust pooling functionality for
 * arbitrary objects.
 * <p>
 * A <tt>GenericKeyedObjectPool</tt> provides a number of configurable parameters:
 * <ul>
 *  <li>
 *    {@link #setMaxActive <i>maxActive</i>} controls the maximum number of objects (per key)
 *    that can be borrowed from the pool at one time.  When non-positive, there
 *    is no limit to the number of objects that may be active at one time.
 *    When {@link #setMaxActive <i>maxActive</i>} is exceeded, the pool is said to be exhausted.
 *  </li>
 *  <li>
 *    {@link #setMaxIdle <i>maxIdle</i>} controls the maximum number of objects that can
 *    sit idle in the pool (per key) at any time.  When non-positive, there
 *    is no limit to the number of objects that may be idle at one time.
 *  </li>
 *  <li>
 *    {@link #setWhenExhaustedAction <i>whenExhaustedAction</i>} specifies the
 *    behaviour of the {@link #borrowObject} method when the pool is exhausted:
 *    <ul>
 *    <li>
 *      When {@link #setWhenExhaustedAction <i>whenExhaustedAction</i>} is
 *      {@link #WHEN_EXHAUSTED_FAIL}, {@link #borrowObject} will throw
 *      a {@link NoSuchElementException}
 *    </li>
 *    <li>
 *      When {@link #setWhenExhaustedAction <i>whenExhaustedAction</i>} is
 *      {@link #WHEN_EXHAUSTED_GROW}, {@link #borrowObject} will create a new
 *      object and return it(essentially making {@link #setMaxActive <i>maxActive</i>}
 *      meaningless.)
 *    </li>
 *    <li>
 *      When {@link #setWhenExhaustedAction <i>whenExhaustedAction</i>}
 *      is {@link #WHEN_EXHAUSTED_BLOCK}, {@link #borrowObject} will block
 *      (invoke {@link Object#wait} until a new or idle object is available.
 *      If a positive {@link #setMaxWait <i>maxWait</i>}
 *      value is supplied, the {@link #borrowObject} will block for at
 *      most that many milliseconds, after which a {@link NoSuchElementException}
 *      will be thrown.  If {@link #setMaxWait <i>maxWait</i>} is non-positive,
 *      the {@link #borrowObject} method will block indefinitely.
 *    </li>
 *    </ul>
 *  </li>
 *  <li>
 *    When {@link #setTestOnBorrow <i>testOnBorrow</i>} is set, the pool will
 *    attempt to validate each object before it is returned from the
 *    {@link #borrowObject} method. (Using the provided factory's
 *    {@link PoolableObjectFactory#validateObject} method.)  Objects that fail
 *    to validate will be dropped from the pool, and a different object will
 *    be borrowed.
 *  </li>
 *  <li>
 *    When {@link #setTestOnReturn <i>testOnReturn</i>} is set, the pool will
 *    attempt to validate each object before it is returned to the pool in the
 *    {@link #returnObject} method. (Using the provided factory's
 *    {@link PoolableObjectFactory#validateObject}
 *    method.)  Objects that fail to validate will be dropped from the pool.
 *  </li>
 * </ul>
 * <p>
 * Optionally, one may configure the pool to examine and possibly evict objects as they
 * sit idle in the pool.  This is performed by an "idle object eviction" thread, which
 * runs asychronously.  The idle object eviction thread may be configured using the
 * following attributes:
 * <ul>
 *  <li>
 *   {@link #setTimeBetweenEvictionRunsMillis <i>timeBetweenEvictionRunsMillis</i>}
 *   indicates how long the eviction thread should sleep before "runs" of examining
 *   idle objects.  When non-positive, no eviction thread will be launched.
 *  </li>
 *  <li>
 *   {@link #setMinEvictableIdleTimeMillis <i>minEvictableIdleTimeMillis</i>}
 *   specifies the minimum amount of time that an object may sit idle in the pool
 *   before it is eligable for eviction due to idle time.  When non-positive, no object
 *   will be dropped from the pool due to idle time alone.
 *  </li>
 *  <li>
 *   {@link #setTestWhileIdle <i>testWhileIdle</i>} indicates whether or not idle
 *   objects should be validated using the factory's
 *   {@link PoolableObjectFactory#validateObject} method.  Objects
 *   that fail to validate will be dropped from the pool.
 *  </li>
 * </ul>
 * @see GenericObjectPool
 * @author Rodney Waldhoff
 * @version $Id: GenericKeyedObjectPool.java,v 1.4 2002/05/01 06:33:01 rwaldhoff Exp $
 */
public class GenericKeyedObjectPool extends BaseKeyedObjectPool implements KeyedObjectPool {

    //--- public constants -------------------------------------------

    /**
     * A "when exhausted action" type indicating that when the pool is
     * exhausted (i.e., the maximum number of active objects has
     * been reached), the {@link #borrowObject}
     * method should fail, throwing a {@link NoSuchElementException}.
     * @see #WHEN_EXHAUSTED_BLOCK
     * @see #WHEN_EXHAUSTED_GROW
     * @see #setWhenExhaustedAction
     */
    public static final byte WHEN_EXHAUSTED_FAIL   = 0;

    /**
     * A "when exhausted action" type indicating that when the pool
     * is exhausted (i.e., the maximum number
     * of active objects has been reached), the {@link #borrowObject}
     * method should block until a new object is available, or the
     * {@link #getMaxWait maximum wait time} has been reached.
     * @see #WHEN_EXHAUSTED_FAIL
     * @see #WHEN_EXHAUSTED_GROW
     * @see #setMaxWait
     * @see #getMaxWait
     * @see #setWhenExhaustedAction
     */
    public static final byte WHEN_EXHAUSTED_BLOCK  = 1;

    /**
     * A "when exhausted action" type indicating that when the pool is
     * exhausted (i.e., the maximum number
     * of active objects has been reached), the {@link #borrowObject}
     * method should simply create a new object anyway.
     * @see #WHEN_EXHAUSTED_FAIL
     * @see #WHEN_EXHAUSTED_GROW
     * @see #setWhenExhaustedAction
     */
    public static final byte WHEN_EXHAUSTED_GROW   = 2;

    /**
     * The default cap on the number of idle instances in the pool
     * (per key).
     * @see #getMaxIdle
     * @see #setMaxIdle
     */
    public static final int DEFAULT_MAX_IDLE  = 8;

    /**
     * The default cap on the total number of active instances from the pool
     * (per key).
     * @see #getMaxActive
     * @see #setMaxActive
     */
    public static final int DEFAULT_MAX_ACTIVE  = 8;

    /**
     * The default "when exhausted action" for the pool.
     * @see #WHEN_EXHAUSTED_BLOCK
     * @see #WHEN_EXHAUSTED_FAIL
     * @see #WHEN_EXHAUSTED_GROW
     * @see #setWhenExhaustedAction
     */
    public static final byte DEFAULT_WHEN_EXHAUSTED_ACTION = WHEN_EXHAUSTED_BLOCK;

    /**
     * The default maximum amount of time (in millis) the
     * {@link #borrowObject} method should block before throwing
     * an exception when the pool is exhausted and the
     * {@link #getWhenExhaustedAction "when exhausted" action} is
     * {@link #WHEN_EXHAUSTED_BLOCK}.
     * @see #getMaxWait
     * @see #setMaxWait
     */
    public static final long DEFAULT_MAX_WAIT = -1L;

    /**
     * The default "test on borrow" value.
     * @see #getTestOnBorrow
     * @see #setTestOnBorrow
     */
    public static final boolean DEFAULT_TEST_ON_BORROW = false;

    /**
     * The default "test on return" value.
     * @see #getTestOnReturn
     * @see #setTestOnReturn
     */
    public static final boolean DEFAULT_TEST_ON_RETURN = false;

    /**
     * The default "test while idle" value.
     * @see #getTestWhileIdle
     * @see #setTestWhileIdle
     * @see #getTimeBetweenEvictionRunsMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public static final boolean DEFAULT_TEST_WHILE_IDLE = false;

    /**
     * The default "time between eviction runs" value.
     * @see #getTimeBetweenEvictionRunsMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public static final long DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS = -1L;

    /**
     * The default number of objects to examine per run in the
     * idle object evictor.
     * @see #getNumTestsPerEvictionRun
     * @see #setNumTestsPerEvictionRun
     * @see #getTimeBetweenEvictionRunsMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public static final int DEFAULT_NUM_TESTS_PER_EVICTION_RUN = 3;

    /**
     * The default value for {@link #getMinEvictableIdleTimeMillis}.
     * @see #getMinEvictableIdleTimeMillis
     * @see #setMinEvictableIdleTimeMillis
     */
    public static final long DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS = 1000L * 60L * 30L;

    //--- constructors -----------------------------------------------

    /**
     * Create a new <tt>GenericKeyedObjectPool</tt> using the specified values.
     * @param factory the (possibly <tt>null</tt>)KeyedPoolableObjectFactory to use to create, validate and destroy objects
     */
    public GenericKeyedObjectPool(KeyedPoolableObjectFactory factory) {
        this(factory,DEFAULT_MAX_ACTIVE,DEFAULT_WHEN_EXHAUSTED_ACTION,DEFAULT_MAX_WAIT,DEFAULT_MAX_IDLE,DEFAULT_TEST_ON_BORROW,DEFAULT_TEST_ON_RETURN,DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS,DEFAULT_NUM_TESTS_PER_EVICTION_RUN,DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS,DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <tt>GenericKeyedObjectPool</tt> using the specified values.
     * @param factory the (possibly <tt>null</tt>)KeyedPoolableObjectFactory to use to create, validate and destroy objects
     * @param config a non-<tt>null</tt> {@link GenericKeyedObjectPool.Config} describing my configuration
     */
    public GenericKeyedObjectPool(KeyedPoolableObjectFactory factory, GenericKeyedObjectPool.Config config) {
        this(factory,config.maxActive,config.whenExhaustedAction,config.maxWait,config.maxIdle,config.testOnBorrow,config.testOnReturn,config.timeBetweenEvictionRunsMillis,config.numTestsPerEvictionRun,config.minEvictableIdleTimeMillis,config.testWhileIdle);
    }

    /**
     * Create a new <tt>GenericKeyedObjectPool</tt> using the specified values.
     * @param factory the (possibly <tt>null</tt>)KeyedPoolableObjectFactory to use to create, validate and destroy objects
     * @param maxActive the maximum number of objects that can be borrowed from me at one time (per key) (see {@link #setMaxActive})
     */
    public GenericKeyedObjectPool(KeyedPoolableObjectFactory factory, int maxActive) {
        this(factory,maxActive,DEFAULT_WHEN_EXHAUSTED_ACTION,DEFAULT_MAX_WAIT,DEFAULT_MAX_IDLE,DEFAULT_TEST_ON_BORROW,DEFAULT_TEST_ON_RETURN,DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS,DEFAULT_NUM_TESTS_PER_EVICTION_RUN,DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS,DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <tt>GenericKeyedObjectPool</tt> using the specified values.
     * @param factory the (possibly <tt>null</tt>)KeyedPoolableObjectFactory to use to create, validate and destroy objects
     * @param maxActive the maximum number of objects that can be borrowed from me at one time (per key) (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #setWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted an and <i>whenExhaustedAction</i> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #setMaxWait})
     */
    public GenericKeyedObjectPool(KeyedPoolableObjectFactory factory, int maxActive, byte whenExhaustedAction, long maxWait) {
        this(factory,maxActive,whenExhaustedAction,maxWait,DEFAULT_MAX_IDLE,DEFAULT_TEST_ON_BORROW,DEFAULT_TEST_ON_RETURN,DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS,DEFAULT_NUM_TESTS_PER_EVICTION_RUN,DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS,DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <tt>GenericKeyedObjectPool</tt> using the specified values.
     * @param factory the (possibly <tt>null</tt>)KeyedPoolableObjectFactory to use to create, validate and destroy objects
     * @param maxActive the maximum number of objects that can be borrowed from me at one time (per key) (see {@link #setMaxActive})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted an and <i>whenExhaustedAction</i> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #setMaxWait})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #setWhenExhaustedAction})
     * @param testOnBorrow whether or not to validate objects before they are returned by the {@link #borrowObject} method (see {@link #setTestOnBorrow})
     * @param testOnReturn whether or not to validate objects after they are returned to the {@link #returnObject} method (see {@link #setTestOnReturn})
     */
    public GenericKeyedObjectPool(KeyedPoolableObjectFactory factory, int maxActive, byte whenExhaustedAction, long maxWait, boolean testOnBorrow, boolean testOnReturn) {
        this(factory,maxActive,whenExhaustedAction,maxWait,DEFAULT_MAX_IDLE,testOnBorrow,testOnReturn,DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS,DEFAULT_NUM_TESTS_PER_EVICTION_RUN,DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS,DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <tt>GenericKeyedObjectPool</tt> using the specified values.
     * @param factory the (possibly <tt>null</tt>)KeyedPoolableObjectFactory to use to create, validate and destroy objects
     * @param maxActive the maximum number of objects that can be borrowed from me at one time (per key) (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #setWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted an and <i>whenExhaustedAction</i> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #setMaxWait})
     * @param maxIdle the maximum number of idle objects in my pool (per key) (see {@link #setMaxIdle})
     */
    public GenericKeyedObjectPool(KeyedPoolableObjectFactory factory, int maxActive, byte whenExhaustedAction, long maxWait, int maxIdle) {
        this(factory,maxActive,whenExhaustedAction,maxWait,maxIdle,DEFAULT_TEST_ON_BORROW,DEFAULT_TEST_ON_RETURN,DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS,DEFAULT_NUM_TESTS_PER_EVICTION_RUN,DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS,DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <tt>GenericKeyedObjectPool</tt> using the specified values.
     * @param factory the (possibly <tt>null</tt>)KeyedPoolableObjectFactory to use to create, validate and destroy objects
     * @param maxActive the maximum number of objects that can be borrowed from me at one time (per key) (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #setWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted an and <i>whenExhaustedAction</i> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #getMaxWait})
     * @param maxIdle the maximum number of idle objects in my pool (see {@link #setMaxIdle})
     * @param testOnBorrow whether or not to validate objects before they are returned by the {@link #borrowObject} method (see {@link #setTestOnBorrow})
     * @param testOnReturn whether or not to validate objects after they are returned to the {@link #returnObject} method (see {@link #setTestOnReturn})
     */
    public GenericKeyedObjectPool(KeyedPoolableObjectFactory factory, int maxActive, byte whenExhaustedAction, long maxWait, int maxIdle, boolean testOnBorrow, boolean testOnReturn) {
        this(factory,maxActive,whenExhaustedAction,maxWait,maxIdle,testOnBorrow,testOnReturn,DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS,DEFAULT_NUM_TESTS_PER_EVICTION_RUN,DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS,DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <tt>GenericKeyedObjectPool</tt> using the specified values.
     * @param factory the (possibly <tt>null</tt>)PoolableObjectFactory to use to create, validate and destroy objects
     * @param maxActive the maximum number of objects that can be borrowed from me at one time (per key) (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #setWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted an and <i>whenExhaustedAction</i> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #setMaxWait})
     * @param maxIdle the maximum number of idle objects in my pool (see {@link #setMaxIdle})
     * @param testOnBorrow whether or not to validate objects before they are returned by the {@link #borrowObject} method (see {@link #setTestOnBorrow})
     * @param testOnReturn whether or not to validate objects after they are returned to the {@link #returnObject} method (see {@link #setTestOnReturn})
     * @param timeBetweenEvictionRunsMillis the amount of time (in milliseconds) to sleep between examining idle objects for eviction (see {@link #setTimeBetweenEvictionRunsMillis})
     * @param numTestsPerEvictionRun the number of idle objects to examine per run within the idle object eviction thread (if any) (see {@link #setNumTestsPerEvictionRun})
     * @param minEvictableIdleTimeMillis the minimum number of milliseconds an object can sit idle in the pool before it is eligable for evcition (see {@link #setMinEvictableIdleTimeMillis})
     * @param testWhileIdle whether or not to validate objects in the idle object eviction thread, if any (see {@link #setTestWhileIdle})
     */
    public GenericKeyedObjectPool(KeyedPoolableObjectFactory factory, int maxActive, byte whenExhaustedAction, long maxWait, int maxIdle, boolean testOnBorrow, boolean testOnReturn, long timeBetweenEvictionRunsMillis, int numTestsPerEvictionRun, long minEvictableIdleTimeMillis, boolean testWhileIdle) {
        _factory = factory;
        _maxActive = maxActive;
        switch(whenExhaustedAction) {
            case WHEN_EXHAUSTED_BLOCK:
            case WHEN_EXHAUSTED_FAIL:
            case WHEN_EXHAUSTED_GROW:
                _whenExhaustedAction = whenExhaustedAction;
                break;
            default:
                throw new IllegalArgumentException("whenExhaustedAction " + whenExhaustedAction + " not recognized.");
        }
        _maxWait = maxWait;
        _maxIdle = maxIdle;
        _testOnBorrow = testOnBorrow;
        _testOnReturn = testOnReturn;
        _timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
        _numTestsPerEvictionRun = numTestsPerEvictionRun;
        _minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
        _testWhileIdle = testWhileIdle;

        _poolMap = new HashMap();
        _activeMap = new HashMap();
        _poolList = new CursorableLinkedList();

        if(_timeBetweenEvictionRunsMillis > 0) {
            _evictor = new Evictor();
            Thread t = new Thread(_evictor);
            t.setDaemon(true);
            t.start();
        }
    }

    //--- public methods ---------------------------------------------

    //--- configuration methods --------------------------------------

    /**
     * Returns the cap on the total number of active instances from my pool.
     * @return the cap on the total number of active instances from my pool.
     * @see #setMaxActive
     */
    public int getMaxActive() {
        return _maxActive;
    }

    /**
     * Sets the cap on the total number of active instances from my pool.
     * @param maxActive The cap on the total number of active instances from my pool.
     *                  Use a negative value for an infinite number of instances.
     * @see #getMaxActive
     */
    public void setMaxActive(int maxActive) {
        _maxActive = maxActive;
        synchronized(this) {
            notifyAll();
        }
    }

    /**
     * Returns the action to take when the {@link #borrowObject} method
     * is invoked when the pool is exhausted (the maximum number
     * of "active" objects has been reached).
     *
     * @return one of {@link #WHEN_EXHAUSTED_BLOCK}, {@link #WHEN_EXHAUSTED_FAIL} or {@link #WHEN_EXHAUSTED_GROW}
     * @see #setWhenExhaustedAction
     */
    public byte getWhenExhaustedAction() {
        return _whenExhaustedAction;
    }

    /**
     * Sets the action to take when the {@link #borrowObject} method
     * is invoked when the pool is exhausted (the maximum number
     * of "active" objects has been reached).
     *
     * @param whenExhaustedAction the action code, which must be one of
     *        {@link #WHEN_EXHAUSTED_BLOCK}, {@link #WHEN_EXHAUSTED_FAIL},
     *        or {@link #WHEN_EXHAUSTED_GROW}
     * @see #getWhenExhaustedAction
     */
    public synchronized void setWhenExhaustedAction(byte whenExhaustedAction) {
        switch(whenExhaustedAction) {
            case WHEN_EXHAUSTED_BLOCK:
            case WHEN_EXHAUSTED_FAIL:
            case WHEN_EXHAUSTED_GROW:
                _whenExhaustedAction = whenExhaustedAction;
                notifyAll();
                break;
            default:
                throw new IllegalArgumentException("whenExhaustedAction " + whenExhaustedAction + " not recognized.");
        }
    }


    /**
     * Returns the maximum amount of time (in milliseconds) the
     * {@link #borrowObject} method should block before throwing
     * an exception when the pool is exhausted and the
     * {@link #setWhenExhaustedAction "when exhausted" action} is
     * {@link #WHEN_EXHAUSTED_BLOCK}.
     *
     * When less than 0, the {@link #borrowObject} method
     * may block indefinitely.
     *
     * @see #setMaxWait
     * @see #setWhenExhaustedAction
     * @see #WHEN_EXHAUSTED_BLOCK
     */
    public synchronized long getMaxWait() {
        return _maxWait;
    }

    /**
     * Sets the maximum amount of time (in milliseconds) the
     * {@link #borrowObject} method should block before throwing
     * an exception when the pool is exhausted and the
     * {@link #setWhenExhaustedAction "when exhausted" action} is
     * {@link #WHEN_EXHAUSTED_BLOCK}.
     *
     * When less than 0, the {@link #borrowObject} method
     * may block indefinitely.
     *
     * @see #getMaxWait
     * @see #setWhenExhaustedAction
     * @see #WHEN_EXHAUSTED_BLOCK
     */
    public synchronized void setMaxWait(long maxWait) {
        _maxWait = maxWait;
    }

    /**
     * Returns the cap on the number of "idle" instances in the pool.
     * @return the cap on the number of "idle" instances in the pool.
     * @see #setMaxIdle
     */
    public int getMaxIdle() {
        return _maxIdle;
    }

    /**
     * Sets the cap on the number of "idle" instances in the pool.
     * @param maxIdle The cap on the number of "idle" instances in the pool.
     *                Use a negative value to indicate an unlimited number
     *                of idle instances.
     * @see #getMaxIdle
     */
    public void setMaxIdle(int maxIdle) {
        _maxIdle = maxIdle;
        synchronized(this) {
            notifyAll();
        }
    }

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * before being returned by the {@link #borrowObject}
     * method.  If the object fails to validate,
     * it will be dropped from the pool, and we will attempt
     * to borrow another.
     *
     * @see #setTestOnBorrow
     */
    public boolean getTestOnBorrow() {
        return _testOnBorrow;
    }

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * before being returned by the {@link #borrowObject}
     * method.  If the object fails to validate,
     * it will be dropped from the pool, and we will attempt
     * to borrow another.
     *
     * @see #getTestOnBorrow
     */
    public void setTestOnBorrow(boolean testOnBorrow) {
        _testOnBorrow = testOnBorrow;
    }

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * before being returned to the pool within the
     * {@link #returnObject}.
     *
     * @see #setTestOnReturn
     */
    public boolean getTestOnReturn() {
        return _testOnReturn;
    }

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * before being returned to the pool within the
     * {@link #returnObject}.
     *
     * @see #getTestOnReturn
     */
    public void setTestOnReturn(boolean testOnReturn) {
        _testOnReturn = testOnReturn;
    }

    /**
     * Returns the number of milliseconds to sleep between runs of the
     * idle object evictor thread.
     * When non-positive, no idle object evictor thread will be
     * run.
     *
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public synchronized long getTimeBetweenEvictionRunsMillis() {
        return _timeBetweenEvictionRunsMillis;
    }

    /**
     * Sets the number of milliseconds to sleep between runs of the
     * idle object evictor thread.
     * When non-positive, no idle object evictor thread will be
     * run.
     *
     * @see #getTimeBetweenEvictionRunsMillis
     */
    public synchronized void setTimeBetweenEvictionRunsMillis(long timeBetweenEvictionRunsMillis) {
        if(_timeBetweenEvictionRunsMillis > 0 && timeBetweenEvictionRunsMillis <= 0) {
            _evictor.cancel();
            _evictor = null;
            _timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
        } else if(_timeBetweenEvictionRunsMillis <= 0 && timeBetweenEvictionRunsMillis > 0) {
            _timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
            _evictor = new Evictor();
            Thread t = new Thread(_evictor);
            t.setDaemon(true);
            t.start();
        } else {
            _timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
        }
    }

    /**
     * Returns the number of objects to examine during each run of the
     * idle object evictor thread (if any).
     *
     * @see #setNumTestsPerEvictionRun
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public int getNumTestsPerEvictionRun() {
        return _numTestsPerEvictionRun;
    }

    /**
     * Sets the number of objects to examine during each run of the
     * idle object evictor thread (if any).
     * <p>
     * When a negative value is supplied, <tt>ceil({@link #numIdle})/abs({@link #getNumTestsPerEvictionRun})</tt>
     * tests will be run.  I.e., when the value is <i>-n</i>, roughly one <i>n</i>th of the
     * idle objects will be tested per run.
     *
     * @see #getNumTestsPerEvictionRun
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public void setNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
        _numTestsPerEvictionRun = numTestsPerEvictionRun;
    }

    /**
     * Returns the minimum amount of time an object may sit idle in the pool
     * before it is eligable for eviction by the idle object evictor
     * (if any).
     *
     * @see #setMinEvictableIdleTimeMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public synchronized long getMinEvictableIdleTimeMillis() {
        return _minEvictableIdleTimeMillis;
    }

    /**
     * Sets the minimum amount of time an object may sit idle in the pool
     * before it is eligable for eviction by the idle object evictor
     * (if any).
     * When non-positive, no objects will be evicted from the pool
     * due to idle time alone.
     *
     * @see #getMinEvictableIdleTimeMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public synchronized void setMinEvictableIdleTimeMillis(long minEvictableIdleTimeMillis) {
        _minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
    }

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * by the idle object evictor (if any).  If an object
     * fails to validate, it will be dropped from the pool.
     *
     * @see #setTestWhileIdle
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public boolean getTestWhileIdle() {
        return _testWhileIdle;
    }

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * by the idle object evictor (if any).  If an object
     * fails to validate, it will be dropped from the pool.
     *
     * @see #getTestWhileIdle
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public void setTestWhileIdle(boolean testWhileIdle) {
        _testWhileIdle = testWhileIdle;
    }

    /**
     * Sets my configuration.
     * @see GenericKeyedObjectPool.Config
     */
    public synchronized void setConfig(GenericKeyedObjectPool.Config conf) {
        setMaxIdle(conf.maxIdle);
        setMaxActive(conf.maxActive);
        setMaxWait(conf.maxWait);
        setWhenExhaustedAction(conf.whenExhaustedAction);
        setTestOnBorrow(conf.testOnBorrow);
        setTestOnReturn(conf.testOnReturn);
        setTestWhileIdle(conf.testWhileIdle);
        setNumTestsPerEvictionRun(conf.numTestsPerEvictionRun);
        setMinEvictableIdleTimeMillis(conf.minEvictableIdleTimeMillis);
        setTimeBetweenEvictionRunsMillis(conf.timeBetweenEvictionRunsMillis);
    }

    //-- ObjectPool methods ------------------------------------------

    public synchronized Object borrowObject(Object key) throws Exception {
        long starttime = System.currentTimeMillis();
        for(;;) {
            CursorableLinkedList pool = (CursorableLinkedList)(_poolMap.get(key));
            if(null == pool) {
                pool = new CursorableLinkedList();
                _poolMap.put(key,pool);
                _poolList.add(key);
            }
            ObjectTimestampPair pair = null;
            // if there are any sleeping, just grab one of those
            try {
                pair = (ObjectTimestampPair)(pool.removeFirst());
                if(null != pair) {
                    _totalIdle--;
                }
            } catch(NoSuchElementException e) { /* ignored */
            }
            // otherwise
            if(null == pair) {
                // check if we can create one
                // (note we know that the num sleeping is 0, else we wouldn't be here)
                int active = 0;
                Integer act = (Integer)(_activeMap.get(key));
                if(null != act) {
                    active = act.intValue();
                }
                if(_maxActive > 0 && active < _maxActive) {
                    Object obj = _factory.makeObject(key);
                    pair = new ObjectTimestampPair(obj);
                } else {
                    // the pool is exhausted
                    switch(_whenExhaustedAction) {
                        case WHEN_EXHAUSTED_GROW:
                            Object obj = _factory.makeObject(key);
                            pair = new ObjectTimestampPair(obj);
                            break;
                        case WHEN_EXHAUSTED_FAIL:
                            throw new NoSuchElementException();
                        case WHEN_EXHAUSTED_BLOCK:
                            try {
                                if(_maxWait <= 0) {
                                    wait();
                                } else {
                                    wait(_maxWait);
                                }
                            } catch(InterruptedException e) {
                                // ignored
                            }
                            if(_maxWait > 0 && ((System.currentTimeMillis() - starttime) >= _maxWait)) {
                                throw new NoSuchElementException("Timeout waiting for idle object");
                            } else {
                                continue; // keep looping
                            }
                        default:
                            throw new IllegalArgumentException("whenExhaustedAction " + _whenExhaustedAction + " not recognized.");
                    }
                }
            }
            _factory.activateObject(key,pair.value);
            if(_testOnBorrow && !_factory.validateObject(key,pair.value)) {
                try {
                    _factory.passivateObject(key,pair.value);
                } catch(Exception e) {
                    ; // ignored, we're throwing it out anyway
                }
                _factory.destroyObject(key,pair.value);
            } else {
                Integer active = (Integer)(_activeMap.get(key));
                if(null == active) {
                    _activeMap.put(key,new Integer(1));
                } else {
                    _activeMap.put(key,new Integer(active.intValue() + 1));
                }
                _totalActive++;
                return pair.value;
            }
        }
    }

    public synchronized void clear() {
        Iterator keyiter = _poolList.iterator();
        while(keyiter.hasNext()) {
            Object key = keyiter.next();
            CursorableLinkedList list = (CursorableLinkedList)(_poolMap.get(key));
            Iterator it = list.iterator();
            while(it.hasNext()) {
                try {
                    _factory.destroyObject(key,((ObjectTimestampPair)(it.next())).value);
                } catch(Exception e) {
                    // ignore error, keep destroying the rest
                }
                it.remove();
            }
        }
        _poolMap.clear();
        _poolList.clear();
        _totalIdle = 0;
        notifyAll();
    }

    public synchronized void clear(Object key) {
        CursorableLinkedList pool = (CursorableLinkedList)(_poolMap.remove(key));
        if(null == pool) {
            return;
        } else {
            _poolList.remove(key);
            Iterator it = pool.iterator();
            while(it.hasNext()) {
                try {
                    _factory.destroyObject(key,((ObjectTimestampPair)(it.next())).value);
                } catch(Exception e) {
                    // ignore error, keep destroying the rest
                }
                it.remove();
                _totalIdle--;
            }
        }
        notifyAll();
    }

    public int getNumActive() {
        return _totalActive;
    }

    public int getNumIdle() {
        return _totalIdle;
    }

    public synchronized int getNumActive(Object key) {
        try {
            return((Integer)(_activeMap.get(key))).intValue();
        } catch(Exception e) {
            return 0;
        }
    }

    public synchronized int getNumIdle(Object key) {
        try {
            return((CursorableLinkedList)(_poolMap.get(key))).size();
        } catch(Exception e) {
            return 0;
        }
    }

    public synchronized void returnObject(Object key, Object obj) throws Exception {
        _totalActive--;

        CursorableLinkedList pool = (CursorableLinkedList)(_poolMap.get(key));
        if(null == pool) {
            pool = new CursorableLinkedList();
            _poolMap.put(key,pool);
            _poolList.add(key);
        }

        Integer active = (Integer)(_activeMap.get(key));
        if(null == active) {
            // do nothing, either null or zero is OK
        } else if(active.intValue() <= 1) {
            _activeMap.remove(key);
        } else {
            _activeMap.put(key,new Integer(active.intValue() - 1));
        }

        if(_maxIdle > 0 && (pool.size() >= _maxIdle || (_testOnReturn && !_factory.validateObject(key,obj)))) {
            try {
                _factory.passivateObject(key,obj);
            } catch(Exception e) {
                ; // ignored, we're throwing it out anyway
            }
            _factory.destroyObject(key,obj);
        } else {
            try {
                _factory.passivateObject(key,obj);
                pool.addFirst(new ObjectTimestampPair(obj));
                _totalIdle++;
            } catch(Exception e) {
                _factory.destroyObject(key,obj);
            }
        }
        notifyAll();
    }

    synchronized public void close() throws Exception {
        clear();
        _poolList = null;
        _poolMap = null;
        _activeMap = null;
        if(null != _evictor) {
            _evictor.cancel();
            _evictor = null;
        }
    }

    synchronized public void setFactory(KeyedPoolableObjectFactory factory) throws IllegalStateException {
        if(0 < getNumActive()) {
            throw new IllegalStateException("Objects are already active");
        } else {
            clear();
            _factory = factory;
        }
    }

    //--- package methods --------------------------------------------

    synchronized String debugInfo() {
        StringBuffer buf = new StringBuffer();
        buf.append("Active: ").append(numActive()).append("\n");
        buf.append("Idle: ").append(numIdle()).append("\n");
        Iterator it = _poolList.iterator();
        while(it.hasNext()) {
            buf.append("\t").append(_poolMap.get(it.next())).append("\n");
        }
        return buf.toString();
    }


    //--- inner classes ----------------------------------------------

    /**
     * A simple "struct" encapsulating an object instance and a timestamp.
     */
    class ObjectTimestampPair {
        Object value;
        long tstamp;

        ObjectTimestampPair(Object val) {
            value = val;
            tstamp = System.currentTimeMillis();
        }

        ObjectTimestampPair(Object val, long time) {
            value = val;
            tstamp = time;
        }

        public String toString() {
            return value + ";" + tstamp;
        }
    }

    /**
     * The idle object evictor thread.
     * @see #setTimeBetweenEvictionRunsMillis
     */
    class Evictor implements Runnable {
        protected boolean _cancelled = false;

        void cancel() {
            _cancelled = true;
        }

        public void run() {
            CursorableLinkedList.Cursor objcursor = null;
            CursorableLinkedList.Cursor keycursor = null;
            Object key = null;

            while(!_cancelled) {
                long sleeptime = 0L;
                synchronized(GenericKeyedObjectPool.this) {
                    sleeptime = _timeBetweenEvictionRunsMillis;
                }
                try {
                    Thread.currentThread().sleep(sleeptime);
                } catch(Exception e) {
                    ; // ignored
                }
                try {
                    synchronized(GenericKeyedObjectPool.this) {
                        for(int i=0,m=getNumTests();i<m;i++) {
                            if(_poolMap.size() > 0) {
                                // if we don't have a key cursor, then create one, and close any object cursor
                                if(null == keycursor) {
                                    keycursor = _poolList.cursor();
                                    key = null;
                                    if(null != objcursor) {
                                        objcursor.close();
                                        objcursor = null;
                                    }
                                }
                                // if we don't have an object cursor
                                if(null == objcursor) {
                                    // if the keycursor has a next value, then use it
                                    if(keycursor.hasNext()) {
                                        key = keycursor.next();
                                        CursorableLinkedList pool = (CursorableLinkedList)(_poolMap.get(key));
                                        objcursor = pool.cursor(pool.size());
                                    } else {
                                        // else close the key cursor and loop back around
                                        if(null != keycursor) {
                                            keycursor.close();
                                            keycursor = _poolList.cursor();
                                            if(null != objcursor) {
                                                objcursor.close();
                                                objcursor = null;
                                            }
                                        }
                                        continue;
                                    }
                                }
                                // if the objcursor has a previous object, then test it
                                if(objcursor.hasPrevious()) {
                                    ObjectTimestampPair pair = (ObjectTimestampPair)(objcursor.previous());
                                    if(System.currentTimeMillis() - pair.tstamp > _minEvictableIdleTimeMillis) {
                                        try {
                                            objcursor.remove();
                                            _totalIdle--;
                                            _factory.destroyObject(key,pair.value);

                                            // if that was the last object for that key, drop that pool
                                            if( ((CursorableLinkedList)(_poolMap.get(key))).isEmpty() ) {
                                                _poolMap.remove(key);
                                                _poolList.remove(key);
                                            }


                                        } catch(Exception e) {
                                            ; // ignored
                                        }
                                    } else if(_testWhileIdle) {
                                        boolean active = false;
                                        try {
                                            _factory.activateObject(key,pair.value);
                                            active = true;
                                        } catch(Exception e) {
                                            objcursor.remove();
                                            try {
                                                _factory.passivateObject(key,pair.value);
                                            } catch(Exception ex) {
                                                ; // ignored
                                            }
                                            _factory.destroyObject(key,pair.value);
                                        }
                                        if(active) {
                                            if(!_factory.validateObject(key,pair.value)) {
                                                try {
                                                    objcursor.remove();
                                                    _totalIdle--;
                                                    try {
                                                        _factory.passivateObject(key,pair.value);
                                                    } catch(Exception e) {
                                                        ; // ignored
                                                    }
                                                    _factory.destroyObject(key,pair.value);
                                                    if( ((CursorableLinkedList)(_poolMap.get(key))).isEmpty() ) {
                                                        _poolMap.remove(key);
                                                        _poolList.remove(key);
                                                    }

                                                } catch(Exception e) {
                                                    ; // ignored
                                                }
                                            } else {
                                                try {
                                                    _factory.passivateObject(key,pair.value);
                                                } catch(Exception e) {
                                                    objcursor.remove();
                                                    _totalIdle--;
                                                    _factory.destroyObject(key,pair.value);
                                                    if( ((CursorableLinkedList)(_poolMap.get(key))).isEmpty() ) {
                                                        _poolMap.remove(key);
                                                        _poolList.remove(key);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // else the objcursor is done, so close it and loop around
                                    if(objcursor != null) {
                                        objcursor.close();
                                        objcursor = null;
                                    }
                                }
                            }
                        }
                    }
                } catch(Exception e) {
                    ; // ignored
                }
            }
            if(null != keycursor) {
                keycursor.close();
            }
            if(null != objcursor) {
                objcursor.close();
            }
        }

        private int getNumTests() {
            if(_numTestsPerEvictionRun >= 0) {
                return _numTestsPerEvictionRun;
            } else {
                return(int)(Math.ceil((double)_totalIdle/Math.abs((double)_numTestsPerEvictionRun)));
            }
        }
    }

    /**
     * A simple "struct" encapsulating the
     * configuration information for a {@link GenericKeyedObjectPool}.
     * @see GenericKeyedObjectPool#GenericKeyedObjectPool(KeyedPoolableObjectFactory,GenericKeyedObjectPool.Config)
     * @see GenericKeyedObjectPool#setConfig
     */
    public static class Config {
        public int maxIdle = GenericKeyedObjectPool.DEFAULT_MAX_IDLE;
        public int maxActive = GenericKeyedObjectPool.DEFAULT_MAX_ACTIVE;
        public long maxWait = GenericKeyedObjectPool.DEFAULT_MAX_WAIT;
        public byte whenExhaustedAction = GenericKeyedObjectPool.DEFAULT_WHEN_EXHAUSTED_ACTION;
        public boolean testOnBorrow = GenericKeyedObjectPool.DEFAULT_TEST_ON_BORROW;
        public boolean testOnReturn = GenericKeyedObjectPool.DEFAULT_TEST_ON_RETURN;
        public boolean testWhileIdle = GenericKeyedObjectPool.DEFAULT_TEST_WHILE_IDLE;
        public long timeBetweenEvictionRunsMillis = GenericKeyedObjectPool.DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;
        public int numTestsPerEvictionRun =  GenericKeyedObjectPool.DEFAULT_NUM_TESTS_PER_EVICTION_RUN;
        public long minEvictableIdleTimeMillis = GenericKeyedObjectPool.DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS;
    }

    //--- protected attributes ---------------------------------------

    /**
     * The cap on the number of idle instances in the pool (per key).
     * @see #setMaxIdle
     * @see #getMaxIdle
     */
    protected int _maxIdle = DEFAULT_MAX_IDLE;

    /**
     * The cap on the total number of active instances from the pool (per key).
     * @see #setMaxActive
     * @see #getMaxActive
     */
    protected int _maxActive = DEFAULT_MAX_ACTIVE;

    /**
     * The maximum amount of time (in millis) the
     * {@link #borrowObject} method should block before throwing
     * an exception when the pool is exhausted and the
     * {@link #getWhenExhaustedAction "when exhausted" action} is
     * {@link #WHEN_EXHAUSTED_BLOCK}.
     *
     * When less than 0, the {@link #borrowObject} method
     * may block indefinitely.
     *
     * @see #setMaxWait
     * @see #getMaxWait
     * @see #WHEN_EXHAUSTED_BLOCK
     * @see #setWhenExhaustedAction
     * @see #getWhenExhaustedAction
     */
    protected long _maxWait = DEFAULT_MAX_WAIT;

    /**
     * The action to take when the {@link #borrowObject} method
     * is invoked when the pool is exhausted (the maximum number
     * of "active" objects has been reached).
     *
     * @see #WHEN_EXHAUSTED_BLOCK
     * @see #WHEN_EXHAUSTED_FAIL
     * @see #WHEN_EXHAUSTED_GROW
     * @see #DEFAULT_WHEN_EXHAUSTED_ACTION
     * @see #setWhenExhaustedAction
     * @see #getWhenExhaustedAction
     */
    protected byte _whenExhaustedAction = DEFAULT_WHEN_EXHAUSTED_ACTION;

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * before being returned by the {@link #borrowObject}
     * method.  If the object fails to validate,
     * it will be dropped from the pool, and we will attempt
     * to borrow another.
     *
     * @see #setTestOnBorrow
     * @see #getTestOnBorrow
     */
    protected boolean _testOnBorrow = DEFAULT_TEST_ON_BORROW;

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * before being returned to the pool within the
     * {@link #returnObject}.
     *
     * @see #getTestOnReturn
     * @see #setTestOnReturn
     */
    protected boolean _testOnReturn = DEFAULT_TEST_ON_RETURN;

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * by the idle object evictor (if any).  If an object
     * fails to validate, it will be dropped from the pool.
     *
     * @see #setTestWhileIdle
     * @see #getTestWhileIdle
     * @see #getTimeBetweenEvictionRunsMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    protected boolean _testWhileIdle = DEFAULT_TEST_WHILE_IDLE;

    /**
     * The number of milliseconds to sleep between runs of the
     * idle object evictor thread.
     * When non-positive, no idle object evictor thread will be
     * run.
     *
     * @see #setTimeBetweenEvictionRunsMillis
     * @see #getTimeBetweenEvictionRunsMillis
     */
    protected long _timeBetweenEvictionRunsMillis = DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;

    /**
     * The number of objects to examine during each run of the
     * idle object evictor thread (if any).
     * <p>
     * When a negative value is supplied, <tt>ceil({@link #numIdle})/abs({@link #getNumTestsPerEvictionRun})</tt>
     * tests will be run.  I.e., when the value is <i>-n</i>, roughly one <i>n</i>th of the
     * idle objects will be tested per run.
     *
     * @see #setNumTestsPerEvictionRun
     * @see #getNumTestsPerEvictionRun
     * @see #getTimeBetweenEvictionRunsMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    protected int _numTestsPerEvictionRun =  DEFAULT_NUM_TESTS_PER_EVICTION_RUN;

    /**
     * The minimum amount of time an object may sit idle in the pool
     * before it is eligable for eviction by the idle object evictor
     * (if any).
     * When non-positive, no objects will be evicted from the pool
     * due to idle time alone.
     *
     * @see #setMinEvictableIdleTimeMillis
     * @see #getMinEvictableIdleTimeMillis
     * @see #getTimeBetweenEvictionRunsMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    protected long _minEvictableIdleTimeMillis = DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS;


    /** My hash of pools (CursorableLinkedLists). */
    protected HashMap _poolMap = null;

    /**
     * A cursorable list of my pools.
     * @see GenericKeyedObjectPool.Evictor#run
     */
    protected CursorableLinkedList _poolList = null;

    /** Count of active objects, per key. */
    protected HashMap _activeMap = null;

    /** The total number of active instances. */
    protected int _totalActive = 0;

    /** The total number of idle instances. */
    protected int _totalIdle = 0;


    /** My {@link KeyedPoolableObjectFactory}. */
    protected KeyedPoolableObjectFactory _factory = null;

    /**
     * My idle object eviction thread, if any.
     */
    protected Evictor _evictor = null;

}
