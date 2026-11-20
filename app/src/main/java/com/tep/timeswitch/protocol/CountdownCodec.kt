package com.tep.timeswitch.protocol

/**
 * 倒计时 3232 编解码(说明书 §8.1,小程序 cycle.js sendCountdown 实证)。
 *
 * ARG 结构(组数恒为 01,单组 LEN=0x0C=12 含自身):
 * ```
 * 01 | 0C | IDX | EN | SWID | 00(保留) | HH MM SS(各 2B ASCII) | EVENT
 * ```
 * SH/SM/SS 为倒计时时长的「时/分/秒」,ASCII 两位数字(不是 BCD)。
 * EVENT:00 到点关 / 01 到点开。
 *
 * 3005 回包中 TYPE=3232 条目的 VALUE 与下发载荷同构(`[组数 N] + N × [LEN][正文]`,
 * 小程序 socket.js case 3232 解析实证)。
 *
 * 示例「10:30:00 后关」: `fe01 000f 3232 01 0c 0001 0000 3130 3330 3030 00`(§8.1)。
 */
object CountdownCodec {

    const val GROUP_LEN = 12          // 单组 LEN 含自身(0x0C)

    /** 一组倒计时(设备无关模型)。 */
    data class Countdown(
        val index: Int = 0,
        val enabled: Boolean = true,
        val switchId: Int = 0,
        val h: Int, val m: Int, val s: Int,
        val event: Boolean,           // true=到点开 false=到点关
    )

    /** 组包 3232 载荷(不含帧头/CMD/LEN,即 ARG)。 */
    fun encode(
        h: Int, m: Int, s: Int,
        event: Boolean,
        switchId: Int = 0,
    ): ByteArray {
        require(h in 0..99 && m in 0..99 && s in 0..99)
        val out = ArrayList<Byte>(13)
        out.add(0x01)                              // 组数恒 1
        out.add(GROUP_LEN.toByte())                // LEN 含自身
        out.add(0x00)                              // IDX
        out.add(0x01)                              // EN
        out.add(switchId.toByte())                 // SWID
        out.add(0x00)                              // 保留
        out.addAll(Hex.ascii2(h).toList())
        out.addAll(Hex.ascii2(m).toList())
        out.addAll(Hex.ascii2(s).toList())
        out.add(if (event) 1 else 0)               // EVENT
        return out.toByteArray()
    }

    /**
     * 解码 3005 中 TYPE=3232 条目的 VALUE(以组数字节开头)。
     * count=0 或载荷无效返回 emptyList;容错截断(设备偶发截断上报)。
     */
    fun decode(value: ByteArray): List<Countdown> {
        if (value.isEmpty()) return emptyList()
        val count = value[0].toInt() and 0xFF
        if (count == 0) return emptyList()
        val result = ArrayList<Countdown>(count)
        var pos = 1
        repeat(count) {
            if (pos + GROUP_LEN > value.size) return result          // 截断即停(§9.3)
            val len = value[pos].toInt() and 0xFF
            if (len != GROUP_LEN) return result
            var i = pos + 1
            val index = value[i++].toInt() and 0xFF
            val en = (value[i++].toInt() and 0xFF) != 0
            val swid = value[i++].toInt() and 0xFF
            i++                                                    // 保留
            val h = Hex.fromAscii2(value[i], value[i + 1]); i += 2
            val m = Hex.fromAscii2(value[i], value[i + 1]); i += 2
            val s = Hex.fromAscii2(value[i], value[i + 1]); i += 2
            val event = (value[i].toInt() and 0xFF) != 0
            result.add(Countdown(index, en, swid, h, m, s, event))
            pos += GROUP_LEN
        }
        return result
    }
}
