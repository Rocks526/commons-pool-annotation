package org.apache.commons.pool2;

/**
 * 池对象所有可能的状态
 */
public enum PooledObjectState {

    // 位于队列中，空闲状态
    IDLE,

    // 已经分配出去，使用中状态
    ALLOCATED,

    // 位于队列中，当前正在检测空闲对象回收
    EVICTION,

    // 对象在检查是否空闲时，被借出对象池，则处于此状态
    // 当对象检测完空闲状态后，修改为IDLE，并重新加入空闲队列
    EVICTION_RETURN_TO_HEAD,

    // 位于队列中，空闲状态，当前正在验证有效性
    // 即testOnIdle配置
    VALIDATION,

    // 分配前验证状态
    // 当对象从池中被借出，在配置了testOnBorrow 的情况下，对像从队列移出和进行分配的时候会进行验证
    VALIDATION_PREALLOCATED,

    // 使用完即将归还池中
    // 配置了 testOnReturn 时，返回池中前验证有效性
    VALIDATION_RETURN_TO_HEAD,

    // 无效状态，空闲对象即将回收或者validate验证失败，将要销毁的对象
    INVALID,

    // 泄露检测配置，确定长时间未使用，属于泄露对象，即将回收
    ABANDONED,

    // 正在返回池里
    RETURNING

}
