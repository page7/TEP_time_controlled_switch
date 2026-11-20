package com.tep.timeswitch.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 倒计时 3232 Codec 测试:测试向量取自说明书 §8.1。
 * 示例「10:30:00 后关」:fe01 000f 3232 01 0c 0001 0000 3130 3330 3030 00。
 */
class CountdownCodecTest {

    @Test
    fun `encode sample matches spec hex`() {
        // §8.1:fe01 000f 3232 01 0c 0001 0000 3130 3330 3030 00
        val arg = CountdownCodec.encode(h = 10, m = 30, s = 0, event = false)
        val frame = Frame.build(Commands.COUNTDOWN, arg)
        assertEquals("fe01000f3232010c0001000031303330303000", Hex.frameHex(frame))
    }

    @Test
    fun `group length byte is 12 including itself`() {
        val arg = CountdownCodec.encode(h = 1, m = 2, s = 3, event = true)
        assertEquals(0x0C, arg[1].toInt() and 0xFF)     // LEN 含自身
        assertEquals(13, arg.size)                       // 组数 1 + 单组 12
    }

    @Test
    fun `decode sample roundtrip`() {
        val arg = CountdownCodec.encode(h = 10, m = 30, s = 0, event = false)
        val list = CountdownCodec.decode(arg)
        assertEquals(1, list.size)
        val c = list[0]
        assertEquals(10, c.h)
        assertEquals(30, c.m)
        assertEquals(0, c.s)
        assertFalse(c.event)
    }

    @Test
    fun `encode event on and boundaries`() {
        val arg = CountdownCodec.encode(h = 23, m = 59, s = 59, event = true, switchId = 3)
        val list = CountdownCodec.decode(arg)
        assertEquals(23, list[0].h)
        assertEquals(59, list[0].m)
        assertEquals(59, list[0].s)
        assertTrue(list[0].event)
        assertEquals(3, list[0].switchId)
    }

    @Test
    fun `time fields are ascii not bcd`() {
        // "10" -> 31 30(§11.2 坑 2)
        val arg = CountdownCodec.encode(h = 10, m = 30, s = 0, event = false)
        assertEquals("3130", Hex.bytesToHex(arg.copyOfRange(6, 8)))
        assertEquals("3330", Hex.bytesToHex(arg.copyOfRange(8, 10)))
    }

    @Test
    fun `decode zero count is empty`() {
        assertTrue(CountdownCodec.decode(byteArrayOf(0)).isEmpty())
        assertTrue(CountdownCodec.decode(ByteArray(0)).isEmpty())
    }

    @Test
    fun `decode truncated stops gracefully`() {
        val arg = CountdownCodec.encode(h = 10, m = 30, s = 0, event = false)
        val truncated = arg.copyOfRange(0, arg.size - 3)
        assertNotNull(CountdownCodec.decode(truncated))   // 不崩
        assertTrue(CountdownCodec.decode(truncated).isEmpty())
    }
}
