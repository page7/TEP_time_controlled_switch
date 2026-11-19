package com.tep.timeswitch.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 组帧测试:测试向量取自说明书 §5.3。 */
class FrameTest {

    @Test
    fun `build query device info 5011`() {
        val f = Frame.build(0x5011)
        assertEquals("fe0100025011", Hex.frameHex(f))
    }

    @Test
    fun `build sync clock 5001 with 14 ascii chars`() {
        // 说明书 §5.3:对时 2026-11-18 22:10:00
        val arg = StatusParser.clockArg(2026, 11, 18, 22, 10, 0)
        val f = Frame.build(0x5001, arg)
        assertEquals("fe01001050013230323631313138323231303030", Hex.frameHex(f))
    }

    @Test
    fun `build query status 3004`() {
        assertEquals("fe0100023004", Hex.frameHex(Frame.build(0x3004)))
    }

    @Test
    fun `build channel power on 3221`() {
        // 通道 0 开:fe01 0004 3221 00 01
        val f = Frame.build(0x3221, byteArrayOf(0x00, 0x01))
        assertEquals("fe01000432210001", Hex.frameHex(f))
    }

    @Test
    fun `build all power on 3225`() {
        // fe01 0003 3225 01
        assertEquals("fe010003322501", Hex.frameHex(Frame.build(0x3225, byteArrayOf(0x01))))
    }

    @Test
    fun `build set auto mode 3007`() {
        // fe01 0003 3007 02
        assertEquals("fe010003300702", Hex.frameHex(Frame.build(0x3007, byteArrayOf(0x02))))
    }

    @Test
    fun `cmd and arg roundtrip`() {
        val f = Frame.build(0x3221, byteArrayOf(0x00, 0x01))
        assertEquals(0x3221, Frame.cmdOf(f))
        assertEquals("0001", Hex.bytesToHex(Frame.argOf(f)))
        assertEquals(f.size, Frame.totalLengthOf(f))
    }

    @Test
    fun `len excludes header and itself`() {
        val f = Frame.build(0x3234, ByteArray(20))
        // LEN = 2 + 20 = 22 = 0x0016;总长 = 4 + 22 = 26
        assertEquals(0x0016, ((f[2].toInt() and 0xFF) shl 8) or (f[3].toInt() and 0xFF))
        assertEquals(26, f.size)
    }
}

/** 收包状态机测试:半包/粘包/一包多帧/帧头错位/噪声前缀。 */
class FrameDecoderTest {

    private fun decode(vararg chunks: ByteArray): List<String> {
        val frames = mutableListOf<String>()
        val d = FrameDecoder { frames += Hex.frameHex(it) }
        chunks.forEach { d.feed(it) }
        return frames
    }

    @Test
    fun `single frame in single chunk`() {
        val out = decode(Hex.hexToBytes("fe0100025011"))
        assertEquals(listOf("fe0100025011"), out)
    }

    @Test
    fun `half frame across two chunks`() {
        val out = decode(
            Hex.hexToBytes("fe01000250"),
            Hex.hexToBytes("11"),
        )
        assertEquals(listOf("fe0100025011"), out)
    }

    @Test
    fun `two frames in one chunk`() {
        val out = decode(Hex.hexToBytes("fe0100025011fe0100023004"))
        assertEquals(listOf("fe0100025011", "fe0100023004"), out)
    }

    @Test
    fun `noise prefix before sof is dropped`() {
        val out = decode(Hex.hexToBytes("aabbccfe0100025011"))
        assertEquals(listOf("fe0100025011"), out)
    }

    @Test
    fun `misaligned sof then real frame`() {
        // 先来一段带假帧头碎片(LEN 声称很长但数据不足),再补真实帧:
        // 假帧头 fe01 ffff 会被 MAX_FRAME/长度不足挡住,真实帧到达后对齐取出。
        val out = decode(
            Hex.hexToBytes("fe0100"),
            Hex.hexToBytes("025011"),
        )
        assertEquals(listOf("fe0100025011"), out)
    }

    @Test
    fun `three frames split arbitrarily`() {
        val buf = Hex.hexToBytes("fe0100025011fe0100023004fe010003322501")
        val out = decode(
            buf.copyOfRange(0, 5),
            buf.copyOfRange(5, 12),
            buf.copyOfRange(12, 17),
            buf.copyOfRange(17, buf.size),
        )
        assertEquals(listOf("fe0100025011", "fe0100023004", "fe010003322501"), out)
    }

    @Test
    fun `frame with realistic status payload survives`() {
        val frame = Frame.build(0x3005, Hex.hexToBytes("010400073202"))
        val out = decode(frame)
        assertEquals(1, out.size)
        assertEquals(Hex.frameHex(frame), out[0])
    }
}
