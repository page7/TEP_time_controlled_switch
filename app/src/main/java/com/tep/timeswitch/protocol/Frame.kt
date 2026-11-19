package com.tep.timeswitch.protocol

/**
 * FE01 帧协议(说明书 §5)。
 *
 * 帧结构:`FE 01 | LEN(2B 大端) | CMD(2B) | ARG(nB)`
 * LEN = 2 + ARG 长度(只算 CMD+ARG,不含帧头与自身)。
 */
object Frame {

    const val SOF0: Byte = 0xFE.toByte()
    const val SOF1: Byte = 0x01

    /**
     * 组帧。cmd 为 2 字节指令码,如 0x3004。
     * 示例:frame(0x5011) == fe01 0002 5011
     */
    fun build(cmd: Int, arg: ByteArray = ByteArray(0)): ByteArray {
        require(cmd in 0..0xFFFF) { "cmd out of range: $cmd" }
        val len = 2 + arg.size
        val out = ByteArray(6 + arg.size)
        out[0] = SOF0
        out[1] = SOF1
        out[2] = (len shr 8).toByte()
        out[3] = len.toByte()
        out[4] = (cmd shr 8).toByte()
        out[5] = cmd.toByte()
        arg.copyInto(out, 6)
        return out
    }

    /** 取帧内 CMD(2B -> Int)。帧必须完整(长度 >= 6)。 */
    fun cmdOf(frame: ByteArray): Int {
        require(frame.size >= 6) { "frame too short" }
        return ((frame[4].toInt() and 0xFF) shl 8) or (frame[5].toInt() and 0xFF)
    }

    /** 取帧内 ARG(第 6 字节起)。 */
    fun argOf(frame: ByteArray): ByteArray {
        require(frame.size >= 6) { "frame too short" }
        return frame.copyOfRange(6, frame.size)
    }

    /** 帧总长 = 4 + LEN。LEN 在第 3-4 字节(大端)。 */
    fun totalLengthOf(frame: ByteArray): Int {
        require(frame.size >= 4) { "frame too short" }
        val len = ((frame[2].toInt() and 0xFF) shl 8) or (frame[3].toInt() and 0xFF)
        return 4 + len
    }
}
