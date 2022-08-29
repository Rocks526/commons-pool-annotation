package org.apache.commons.pool2;

/**
 * 对象销毁模式
 * 通过invalidateObject方法和destroyObject方法传入，在对象工厂里获取
 */
public enum DestroyMode {

    // 标准销毁模式，在对象无效时调用
    NORMAL,

    // 废弃销毁模式，在检测对象泄露时调用
    ABANDONED

}
