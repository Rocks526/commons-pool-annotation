/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.pool2;

import org.apache.commons.pool2.impl.DefaultPooledObject;

/**
 * A base implementation of <code>PoolableObjectFactory</code>.
 * <p>
 * All operations defined here are essentially no-op's.
 * <p>
 * This class is immutable, and therefore thread-safe
 *
 * @param <T> Type of element managed in this factory.
 *
 * @see PoolableObjectFactory
 * @see BaseKeyedPoolableObjectFactory
 *
 * @version $Revision: 1333925 $
 *
 * @since 2.0
 */
public abstract class BasePooledObjectFactory<T> implements PooledObjectFactory<T> {
    /**
     * Creates an object instance, to be wrapped in a {@link PooledObject}.
     * <p>This method <strong>must</strong> support concurrent, multi-threaded
     * activation.</p>
     * 
     * @return an instance to be served by the pool
     */
    public abstract T create() throws Exception;
    
    @Override
    public PooledObject<T> makeObject() throws Exception {
        return new DefaultPooledObject<T>(create());
    }

    /**
     *  No-op.
     *
     *  @param p ignored
     */
    @Override
    public void destroyObject(PooledObject<T> p)
        throws Exception  {
    }

    /**
     * This implementation always returns {@code true}.
     *
     * @param p ignored
     *
     * @return {@code true}
     */
    @Override
    public boolean validateObject(PooledObject<T> p) {
        return true;
    }

    /**
     *  No-op.
     *
     *  @param p ignored
     */
    @Override
    public void activateObject(PooledObject<T> p) throws Exception {
    }

    /**
     *  No-op.
     *
     * @param p ignored
     */
    @Override
    public void passivateObject(PooledObject<T> p)
        throws Exception {
    }
}