package com.tep.timeswitch.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 3005 状态解析 + 5012 设备信息解析测试。 */
class StatusParserTest {

    @Test
    fun `parse 5012 dev info per spec`() {
        // §6.4 例:型号 ZJSK02、版本 1.2.3
        // fe01 0010 5012 00 06 5A4A534B3032 05 312E322E33
        val arg = Hex.hexToBytes("00065a4a534b303205312e322e33")
        val info = StatusParser.parseDevInfo(arg)
        assertEquals("ZJSK02", info!!.productId)
        assertEquals("1.2.3", info.version)
    }

    @Test
    fun `parse 5012 tolerates missing version`() {
        val arg = Hex.hexToBytes("00065a4a534b3032")
        val info = StatusParser.parseDevInfo(arg)
        assertEquals("ZJSK02", info!!.productId)
        assertEquals("", info.version)
    }

    @Test
    fun `parse 5012 rejects truncated`() {
        assertNull(StatusParser.parseDevInfo(Hex.hexToBytes("00065a4a")))
    }

    @Test
    fun `parse 3005 mode and all power`() {
        // 条目:LEN=04 TYPE=3007 VALUE=02 | LEN=04 TYPE=3225 VALUE=01(LEN 含自身)
        val arg = Hex.hexToBytes("02" + "04300702" + "04322501")
        val s = StatusParser.parse(arg)
        assertEquals(2, s.mode)
        assertEquals(true, s.allPower)
    }

    @Test
    fun `parse 3005 channel powers`() {
        // 条目:LEN=07(含自身 1+2+4) TYPE=3221 VALUE=00 01 01 00(通道0开、通道1关)
        val arg = Hex.hexToBytes("01" + "073221" + "00010100")
        val s = StatusParser.parse(arg)
        assertEquals(mapOf(0 to true, 1 to false), s.channelPower)
    }

    @Test
    fun `parse 3005 with timer group entry`() {
        // 定时组条目 VALUE = §7.4 的 20 字节载荷
        val timerPayload = Hex.hexToBytes("01130001007f3038333030307f31383030303001")
        val item = Hex.u8(1 + 2 + timerPayload.size) + "3234" + Hex.bytesToHex(timerPayload)
        val arg = Hex.hexToBytes("01" + item)
        val s = StatusParser.parse(arg, ModelTable.Family.GROUP)
        assertEquals(ModelTable.Family.GROUP, s.timerFamily)
        assertEquals(1, s.timers!!.size)
        val t = s.timers!![0]
        assertEquals(8, t.startH)
        assertEquals(30, t.startM)
        assertEquals(18, t.endH)
        assertEquals(true, t.event)
        assertEquals(0x7F, t.weekStart)
    }

    @Test
    fun `parse 3005 stops at bad item length`() {
        // 第一条 LEN=0(非法)→ 立即停止,不崩
        val arg = Hex.hexToBytes("02" + "00" + "03322501")
        val s = StatusParser.parse(arg)
        assertNull(s.allPower)
    }

    @Test
    fun `parse 3005 keeps old values for missing types`() {
        val arg = Hex.hexToBytes("01" + "04300702")
        val s = StatusParser.parse(arg)
        assertNull(s.channelPower)   // 未上报的条目保持 null(由上层保留旧值)
        assertNull(s.allPower)
    }

    @Test
    fun `parse 3005 legacy timer entry is no-seconds`() {
        val timerPayload = Hex.hexToBytes("010f0001007f303833307f3138303001")
        val item = Hex.u8(1 + 2 + timerPayload.size) + "3231" + Hex.bytesToHex(timerPayload)
        val arg = Hex.hexToBytes("01" + item)
        val s = StatusParser.parse(arg)
        assertEquals(ModelTable.Family.LEGACY, s.timerFamily)
        assertEquals(0, s.timers!![0].startS)   // 无秒族秒恒 0
    }

    @Test
    fun `clock arg is 14 ascii digits`() {
        val arg = StatusParser.clockArg(2026, 11, 18, 22, 10, 0)
        assertEquals(14, arg.size)
        assertEquals("20261118221000", String(arg, Charsets.US_ASCII))
    }
}
