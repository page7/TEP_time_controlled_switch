package com.tep.timeswitch.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 循环定时 3233 Codec 测试:下发布局取自说明书 §8.2 + AGENT P9,
 * 原厂向量 = cycle.js sendLoopTimer("00010000"+sHsMsS+"00"+eHeMeS+"00")。
 */
class LoopTimerCodecTest {

    @Test
    fun `encode matches spec and factory layout`() {
        // 08:00:00 开始 → 09:00:00 结束;ARG = 01 | 13 | 00 01 00 00 | sTimes | 00 | eTimes | 00
        val arg = LoopTimerCodec.encode(8, 0, 0, 9, 0, 0)
        val frame = Frame.build(Commands.LOOP_TIMER, arg)
        assertEquals(
            "fe01001632330113000100003038303030300030393030303000",
            Hex.frameHex(frame),
        )
    }

    @Test
    fun `group length byte is 19 including itself`() {
        val arg = LoopTimerCodec.encode(1, 2, 3, 4, 5, 6)
        assertEquals(0x13, arg[1].toInt() and 0xFF)    // LEN=19 含自身
        assertEquals(20, arg.size)                      // 组数 1 + 单组 19
    }

    @Test
    fun `event defaults to 00 like factory`() {
        // 原厂 sendLoopTimer 尾字节恒发 00(P9)
        val arg = LoopTimerCodec.encode(8, 0, 0, 9, 0, 0)
        assertEquals(0x00, arg[19].toInt() and 0xFF)
    }

    @Test
    fun `time fields are ascii at documented offsets`() {
        // §8.2:LEN IDX EN SWID 00 sH sM sS 00 eH eM eS EVENT
        val arg = LoopTimerCodec.encode(8, 0, 0, 9, 30, 15)
        assertEquals("3038", Hex.bytesToHex(arg.copyOfRange(6, 8)))     // sH "08"
        assertEquals("3030", Hex.bytesToHex(arg.copyOfRange(8, 10)))    // sM "00"
        assertEquals("3030", Hex.bytesToHex(arg.copyOfRange(10, 12)))   // sS "00"
        assertEquals(0x00, arg[12].toInt() and 0xFF)                    // s/e 间分隔
        assertEquals("3039", Hex.bytesToHex(arg.copyOfRange(13, 15)))   // eH "09"
        assertEquals("3330", Hex.bytesToHex(arg.copyOfRange(15, 17)))   // eM "30"
        assertEquals("3135", Hex.bytesToHex(arg.copyOfRange(17, 19)))   // es "15"
    }

    @Test
    fun `decode roundtrip`() {
        val arg = LoopTimerCodec.encode(23, 59, 59, 0, 0, 1, event = true, switchId = 2, index = 5)
        val list = LoopTimerCodec.decode(arg)
        assertEquals(1, list.size)
        val lt = list[0]
        assertEquals(5, lt.index)
        assertTrue(lt.enabled)
        assertEquals(2, lt.switchId)
        assertEquals(23, lt.startH)
        assertEquals(59, lt.startM)
        assertEquals(59, lt.startS)
        assertEquals(0, lt.endH)
        assertEquals(0, lt.endM)
        assertEquals(1, lt.endS)
        assertTrue(lt.event)
    }

    @Test
    fun `decode default event false`() {
        val list = LoopTimerCodec.decode(LoopTimerCodec.encode(8, 0, 0, 9, 0, 0))
        assertFalse(list[0].event)
    }
}
