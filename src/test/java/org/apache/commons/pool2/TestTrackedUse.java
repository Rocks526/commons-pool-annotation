package org.apache.commons.pool2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * 测试
 */
public class TestTrackedUse {

    // 每次返回都是刚刚使用过此对象，避免此对象被当做空闲对象驱逐
    class DefaultTrackedUse implements TrackedUse {

        @Override
        public long getLastUsed() {
            return 1;
        }

    }

    @Test
    public void testDefaultGetLastUsedInstant() {
        assertEquals(Instant.ofEpochMilli(1), new DefaultTrackedUse().getLastUsedInstant());
    }
}
