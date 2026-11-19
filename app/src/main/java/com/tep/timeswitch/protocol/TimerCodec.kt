package com.tep.timeswitch.protocol

/**
 * 定时组编解码(说明书 §7,三族:3231/3234/3236)。
 *
 * 单组结构(有秒族 19B / 无秒族 15B,LEN 字段含自身):
 * ```
 * 偏移  字段     长度   说明
 * 0    LEN      1     0x13(19) 或 0x0F(15),含自身
 * 1    IDX      1     组序号,0 起连续
 * 2    EN       1     是否启用(仅 BATCH 族生效;GROUP 族恒发 01)
 * 3    SWID     1     通道号,单路恒 00
 * 4    WEEK_S   1     开始星期位图(bit0=周一 … bit6=周日)
 * 5    SH SM SS 6     开始时间 ASCII(无秒族无 SS,共 4B)
 * 11   WEEK_E   1     结束星期位图
 * 12   EH EM ES 6     结束时间 ASCII(无秒族无 ES,共 4B)
 * 18   EVENT    1     到点动作 00 关 / 01 开
 * ```
 * 整包 = `[组数 N] + N × [LEN][单组正文]`(组数字节在最前,LEN 不含它)。
 */
object TimerCodec {

    /** 一组定时的设备无关模型。 */
    data class TimerGroup(
        val index: Int,          // IDX,0 起连续
        val enabled: Boolean,    // EN(仅 BATCH 族语义有效)
        val switchId: Int,       // 通道号
        val weekStart: Int,      // 位图
        val weekEnd: Int,        // 位图(原厂组包时与 weekStart 同值)
        val startH: Int, val startM: Int, val startS: Int,
        val endH: Int, val endM: Int, val endS: Int,
        val event: Boolean,      // true=开 false=关
    )

    const val GROUP_LEN_WITH_SEC = 19     // 3234/3236
    const val GROUP_LEN_NO_SEC = 15       // 3231

    // ---------- 组包 ----------

    /**
     * 编码整包载荷(不含帧头/CMD/LEN,即 ARG 部分)。
     * @param withSeconds true=3234/3236(含秒,19B/组);false=3231(无秒,15B/组)
     * @param forceEnable true=EN 恒发 01(GROUP 族语义)
     */
    fun encode(
        timers: List<TimerGroup>,
        withSeconds: Boolean,
        forceEnable: Boolean = false,
    ): ByteArray {
        val out = ArrayList<Byte>(1 + timers.size * GROUP_LEN_WITH_SEC)
        out.add(timers.size.toByte())
        for (t in timers) {
            val body = encodeGroup(t, withSeconds, forceEnable)
            out.add((body.size + 1).toByte())   // 组 LEN 含自身!19(0x13)/15(0x0F)
            out.addAll(body.toList())
        }
        return out.toByteArray()
    }

    /** 清空定时:载荷仅一个组数字节 00(说明书 §7.3)。 */
    fun encodeClear(): ByteArray = byteArrayOf(0x00)

    /** 编码单组正文(不含组 LEN 自身),用于 BATCH 族单组重发(含 EN=00 占位)。 */
    fun encodeGroup(t: TimerGroup, withSeconds: Boolean, forceEnable: Boolean = false): ByteArray {
        val en = if (forceEnable) 1 else if (t.enabled) 1 else 0
        val out = ArrayList<Byte>(GROUP_LEN_WITH_SEC - 1)
        out.add(t.index.toByte())
        out.add(en.toByte())
        out.add(t.switchId.toByte())
        out.add(t.weekStart.toByte())
        out.addAll(Hex.ascii2(t.startH).toList())
        out.addAll(Hex.ascii2(t.startM).toList())
        if (withSeconds) out.addAll(Hex.ascii2(t.startS).toList())
        out.add(t.weekEnd.toByte())
        out.addAll(Hex.ascii2(t.endH).toList())
        out.addAll(Hex.ascii2(t.endM).toList())
        if (withSeconds) out.addAll(Hex.ascii2(t.endS).toList())
        out.add(if (t.event) 1 else 0)
        return out.toByteArray()
    }

    /** BATCH 族删除/占位:一组 EN=00 的包(单组一包)。 */
    fun encodeSingleDisabled(t: TimerGroup, withSeconds: Boolean = true): ByteArray {
        val body = encodeGroup(t.copy(enabled = false), withSeconds)
        return byteArrayOf((body.size + 1).toByte()) + body   // LEN 含自身
    }

    // ---------- 解包 ----------

    /**
     * 解码整包载荷。tolerant=true 时忽略坏组继续(设备偶发截断上报)。
     * 返回 null 表示载荷整体无效(长度不足)。
     */
    fun decode(payload: ByteArray, withSeconds: Boolean, tolerant: Boolean = true): List<TimerGroup>? {
        if (payload.isEmpty()) return null
        val count = payload[0].toInt() and 0xFF
        if (count == 0) return emptyList()
        val groupLen = if (withSeconds) GROUP_LEN_WITH_SEC else GROUP_LEN_NO_SEC
        val result = ArrayList<TimerGroup>(count)
        var pos = 1
        repeat(count) {
            if (pos + groupLen > payload.size) {
                if (tolerant) return result
                return null
            }
            val len = payload[pos].toInt() and 0xFF
            if (len != groupLen) {          // LEN 异常:整表错位,保底停止(§9.3)
                if (tolerant) return result
                return null
            }
            val g = decodeGroup(payload, pos, withSeconds) ?: return result
            result.add(g)
            pos += groupLen
        }
        return result
    }

    /** 从 payload[pos] 起解一组(pos 指向组 LEN 字节)。 */
    fun decodeGroup(payload: ByteArray, pos: Int, withSeconds: Boolean): TimerGroup? {
        val groupLen = if (withSeconds) GROUP_LEN_WITH_SEC else GROUP_LEN_NO_SEC
        if (pos + groupLen > payload.size) return null
        var i = pos + 1                       // 跳过组 LEN
        val index = payload[i++].toInt() and 0xFF
        val en = (payload[i++].toInt() and 0xFF) != 0
        val swid = payload[i++].toInt() and 0xFF
        val ws = payload[i++].toInt() and 0xFF
        val sh = Hex.fromAscii2(payload[i], payload[i + 1]); i += 2
        val sm = Hex.fromAscii2(payload[i], payload[i + 1]); i += 2
        val ss = if (withSeconds) {
            val v = Hex.fromAscii2(payload[i], payload[i + 1]); i += 2; v
        } else 0
        val we = payload[i++].toInt() and 0xFF
        val eh = Hex.fromAscii2(payload[i], payload[i + 1]); i += 2
        val em = Hex.fromAscii2(payload[i], payload[i + 1]); i += 2
        val es = if (withSeconds) {
            val v = Hex.fromAscii2(payload[i], payload[i + 1]); i += 2; v
        } else 0
        val event = (payload[i].toInt() and 0xFF) != 0
        return TimerGroup(index, en, swid, ws, we, sh, sm, ss, eh, em, es, event)
    }

    // ---------- 星期位图(说明书 §7.2 + 小程序 getWeek/getWeekStr) ----------

    /** 布尔数组[周一..周日] -> 位图。 */
    fun weekBits(days: BooleanArray): Int {
        require(days.size == 7)
        var v = 0
        for (i in 0..6) if (days[i]) v = v or (1 shl i)
        return v
    }

    /** 位图 -> 布尔数组[周一..周日]。 */
    fun weekArray(bitmap: Int): BooleanArray =
        BooleanArray(7) { (bitmap shr it) and 1 == 1 }

    /**
     * 位图 -> 显示文案(小程序 getWeekStr 等价)。
     * 0/0x80 -> "仅一次";0x7F -> "每天";否则逐天 "周一 周二 …"。
     */
    fun weekStr(bitmap: Int): String = when (bitmap) {
        0, 0x80 -> "仅一次"
        0x7F -> "每天"
        else -> {
            val names = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
            (0..6).filter { bitmap and (1 shl it) != 0 }
                .joinToString(" ") { names[it] }
        }
    }
}
