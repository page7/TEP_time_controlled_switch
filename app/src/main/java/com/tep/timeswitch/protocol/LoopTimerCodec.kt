package com.tep.timeswitch.protocol

import com.tep.timeswitch.protocol.Hex

/**
 * 循环定时 3233 只读解码(说明书 §8.2 + AGENT 协议补充 P5)。
 *
 * 单组布局(§8.2):
 * ```
 * LEN | IDX | EN | SWID | 00 | sH sM sS | 00 | eH eM eS | EVENT     (19B,LEN 含自身)
 * ```
 * P5 记载回传存在尾部 `[00] [EVENT]` 的 20B 变体;解码按组 LEN 字节前移、EVENT 取组末字节,
 * 两种布局均兼容(时间字段偏移 5–10 / 12–17 在两种布局下一致)。
 *
 * V1 仅读回展示(3004/3005);下发/编辑属 M5+(循环定时编辑不在 V1 范围)。
 */
object LoopTimerCodec {

    const val GROUP_LEN = 19            // §8.2 标准组长度

    /** 一组循环定时。 */
    data class LoopTimer(
        val index: Int,
        val enabled: Boolean,
        val switchId: Int,
        val startH: Int, val startM: Int, val startS: Int,
        val endH: Int, val endM: Int, val endS: Int,
        val event: Boolean,             // true=时段起点开(到终点关)
    )

    /**
     * 解码 3005 中 TYPE=3233 条目的 VALUE(以组数字节开头)。
     * 容错:LEN 为 0/越界即停;LEN 与 19 不符时按实际 LEN 前移(兼容 20B 变体)。
     */
    fun decode(value: ByteArray): List<LoopTimer> {
        if (value.isEmpty()) return emptyList()
        val count = value[0].toInt() and 0xFF
        if (count == 0) return emptyList()
        val result = ArrayList<LoopTimer>(count)
        var pos = 1
        repeat(count) {
            if (pos + GROUP_LEN > value.size) return result          // 截断即停(§9.3)
            val len = value[pos].toInt() and 0xFF
            if (len < GROUP_LEN || pos + len > value.size) return result
            var i = pos + 1
            val index = value[i++].toInt() and 0xFF
            val en = (value[i++].toInt() and 0xFF) != 0
            val swid = value[i++].toInt() and 0xFF
            i++                                                    // 保留
            val sh = Hex.fromAscii2(value[i], value[i + 1]); i += 2
            val sm = Hex.fromAscii2(value[i], value[i + 1]); i += 2
            val ss = Hex.fromAscii2(value[i], value[i + 1]); i += 2
            i++                                                    // s/e 间分隔 00
            val eh = Hex.fromAscii2(value[i], value[i + 1]); i += 2
            val em = Hex.fromAscii2(value[i], value[i + 1]); i += 2
            val es = Hex.fromAscii2(value[i], value[i + 1]); i += 2
            val event = (value[pos + len - 1].toInt() and 0xFF) != 0   // 组末字节(兼容 19/20B)
            result.add(LoopTimer(index, en, swid, sh, sm, ss, eh, em, es, event))
            pos += len
        }
        return result
    }
}
