package de.culture4life.luca.util;

import org.junit.Test;

import java.nio.ByteBuffer;

public class TimeUtilTest {

    @Test
    public void convertToUnixTimestamp() {
        TimeUtil.convertToUnixTimestamp(1601481612123L)
                .test()
                .assertValue(1601481612L);
    }

    @Test
    public void roundUnixTimestampDownToMinute() {
        TimeUtil.roundUnixTimestampDownToMinute(1601481612L)
                .test()
                .assertValue(1601481600L);
    }

    @Test
    public void encodeUnixTimestamp() {
        TimeUtil.encodeUnixTimestamp(1601481600L)
                .map(bytes -> ByteBuffer.wrap(bytes).getInt())
                .test()
                .assertValue(-2136247201);
    }

}