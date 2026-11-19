package com.tep.timeswitch.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 定时组 Codec 测试:测试向量取自说明书 §7.4。
 * 场景:1 组定时,每天 08:30:00 开 → 18:00:00 关。
 */
class TimerCodecTest {

    private val sample = TimerCodec.TimerGroup(
        index = 0, enabled = true, switchId = 0,
        weekStart = 0x7F, weekEnd = 0x7F,
        startH = 8, startM = 30, startS = 0,
        endH = 18, endM = 0, endS = 0,
        event = true,
    )

    // ---------- 有秒族(3234/3236,19B/组)----------

    @Test
    fun `encode with-seconds sample matches spec hex`() {
        // §7.4:fe01 0016 3234 01130001007f3038333030307f31383030303001
        val payload = TimerCodec.encode(listOf(sample), withSeconds = true)
        val frame = Frame.build(0x3234, payload)
        assertEquals("fe010016323401130001007f3038333030307f31383030303001", Hex.frameHex(frame))
    }

    @Test
    fun `group length byte is 19 including itself`() {
        val payload = TimerCodec.encode(listOf(sample), withSeconds = true)
        assertEquals(0x13, payload[1].toInt() and 0xFF)   // 组 LEN = 19
        assertEquals(1 + 19, payload.size)                 // 组数 1 + 一组 19
    }

    @Test
    fun `decode with-seconds roundtrip`() {
        val payload = TimerCodec.encode(listOf(sample), withSeconds = true)
        val list = TimerCodec.decode(payload, withSeconds = true)
        assertNotNull(list)
        assertEquals(1, list!!.size)
        assertEquals(sample, list[0])
    }

    // ---------- 无秒族(3231,15B/组)----------

    @Test
    fun `encode no-seconds sample matches spec hex`() {
        // §7.4:fe01 0012 3231 010f0001007f303833307f3138303001
        val payload = TimerCodec.encode(listOf(sample), withSeconds = false)
        val frame = Frame.build(0x3231, payload)
        assertEquals("fe0100123231010f0001007f303833307f3138303001", Hex.frameHex(frame))
    }

    @Test
    fun `group length byte is 15 including itself`() {
        val payload = TimerCodec.encode(listOf(sample), withSeconds = false)
        assertEquals(0x0F, payload[1].toInt() and 0xFF)
        assertEquals(1 + 15, payload.size)                 // 注意:载荷是 16 字节,不是 15(§7.4 坑)
    }

    @Test
    fun `decode no-seconds roundtrip`() {
        val payload = TimerCodec.encode(listOf(sample), withSeconds = false)
        val list = TimerCodec.decode(payload, withSeconds = false)
        assertNotNull(list)
        assertEquals(sample, list!![0])
    }

    // ---------- EN 语义 ----------

    @Test
    fun `group family forces en to 01`() {
        val disabled = sample.copy(enabled = false)
        val payload = TimerCodec.encode(listOf(disabled), withSeconds = true, forceEnable = true)
        // 载荷布局:[组数][LEN][IDX][EN][SWID]…
        assertEquals(0x01, payload[3].toInt() and 0xFF)   // EN 字节
    }

    @Test
    fun `batch family respects en`() {
        val disabled = sample.copy(enabled = false)
        val payload = TimerCodec.encode(listOf(disabled), withSeconds = true, forceEnable = false)
        assertEquals(0x00, payload[3].toInt() and 0xFF)
    }

    @Test
    fun `batch delete placeholder is single disabled group`() {
        val out = TimerCodec.encodeSingleDisabled(sample)
        // 单组包:LEN(0x13=19,含自身) + 正文 18;EN=00 在 offset 2
        assertEquals(19, out.size)
        assertEquals(0x13, out[0].toInt() and 0xFF)
        assertEquals(0x00, out[2].toInt() and 0xFF)       // EN
    }

    // ---------- 清空 / 多组 ----------

    @Test
    fun `clear is single zero byte`() {
        assertEquals("00", Hex.bytesToHex(TimerCodec.encodeClear()))
    }

    @Test
    fun `multi group encode-decode roundtrip`() {
        val groups = listOf(
            sample,
            sample.copy(index = 1, startH = 7, startM = 0, event = false),
            sample.copy(index = 2, weekStart = 0x1F, weekEnd = 0x1F, endH = 20),
        )
        val payload = TimerCodec.encode(groups, withSeconds = true)
        val list = TimerCodec.decode(payload, withSeconds = true)
        assertEquals(groups, list)
    }

    @Test
    fun `decode zero count is empty list`() {
        assertEquals(emptyList<TimerCodec.TimerGroup>(), TimerCodec.decode(byteArrayOf(0), true))
    }

    @Test
    fun `decode truncated stops gracefully`() {
        val payload = TimerCodec.encode(listOf(sample), withSeconds = true)
        val truncated = payload.copyOfRange(0, payload.size - 5)   // 砍掉尾巴
        val list = TimerCodec.decode(truncated, withSeconds = true, tolerant = true)
        assertTrue(list!!.isEmpty())
    }

    // ---------- 星期位图 ----------

    @Test
    fun `week bitmap roundtrip`() {
        val days = booleanArrayOf(true, true, true, true, true, false, false) // 周一~五
        assertEquals(0x1F, TimerCodec.weekBits(days))
        assertTrue(TimerCodec.weekArray(0x1F).copyOf(5).all { it })
        assertFalse(TimerCodec.weekArray(0x1F)[5])
    }

    @Test
    fun `weekStr matches miniprogram getWeekStr`() {
        assertEquals("每天", TimerCodec.weekStr(0x7F))
        assertEquals("仅一次", TimerCodec.weekStr(0))
        assertEquals("仅一次", TimerCodec.weekStr(0x80))
        assertEquals("周一 周二 周三 周四 周五", TimerCodec.weekStr(0x1F))
        assertEquals("周六 周日", TimerCodec.weekStr(0x60))
    }

    // ---------- ASCII 时间 ----------

    @Test
    fun `ascii time encoding is not bcd`() {
        // "08:30" -> 30 38 33 30(§11.2 坑 2)
        assertEquals("3038", Hex.bytesToHex(Hex.ascii2(8)))
        assertEquals("3330", Hex.bytesToHex(Hex.ascii2(30)))
        assertEquals(18, Hex.fromAscii2(0x31, 0x38))
        assertEquals(8, Hex.fromAscii2(0x30, 0x38))
    }
}
