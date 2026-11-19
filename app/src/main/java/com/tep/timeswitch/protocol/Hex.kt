package com.tep.timeswitch.protocol

/** Hex 编解码工具。协议字段大量使用 hex 字符串与 ASCII 数字编码,统一在此处理。 */
object Hex {

    private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

    fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(HEX_CHARS[(b.toInt() shr 4) and 0x0F])
            sb.append(HEX_CHARS[b.toInt() and 0x0F])
        }
        return sb.toString()
    }

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "")
        require(clean.length % 2 == 0) { "hex length must be even: $hex" }
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    /** 2 字节大端十六进制表示,如 22 -> "0016"。 */
    fun u16(value: Int): String = value.toString(16).padStart(4, '0')

    /** 1 字节十六进制表示,如 1 -> "01"。 */
    fun u8(value: Int): String = value.toString(16).padStart(2, '0')

    /**
     * 数字转协议时间字段:两位 ASCII 数字。
     * 注意:是 ASCII('0'..'9' = 0x30..0x39),不是 BCD!如 8 -> "08" -> 0x30 0x38。
     */
    fun ascii2(value: Int): ByteArray {
        require(value in 0..99) { "ascii2 out of range: $value" }
        val s = value.toString().padStart(2, '0')
        return byteArrayOf(s[0].code.toByte(), s[1].code.toByte())
    }

    /** 协议时间字段还原:两个 ASCII 数字字节 -> Int。如 0x31 0x38 -> 18。 */
    fun fromAscii2(b1: Byte, b2: Byte): Int =
        (b1.toInt() and 0x0F) * 10 + (b2.toInt() and 0x0F)

    /** 帧 hex 字符串便于日志与测试断言(小写,无分隔)。 */
    fun frameHex(bytes: ByteArray): String = bytesToHex(bytes).lowercase()
}
