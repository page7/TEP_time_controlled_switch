package com.tep.timeswitch.protocol

import com.tep.timeswitch.protocol.TimerCodec.TimerGroup

/**
 * 3005 全量状态解析(说明书 §9)。
 *
 * 外层:`[条目数 N] + N × [ITEM_LEN][TYPE(2B)][VALUE]`
 * ITEM_LEN 含自身,VALUE 长度 = ITEM_LEN − 3;TYPE 是 2 字节(区别于 3281/3213 的 1 字节)。
 * 设备只报支持的条目,缺失字段保持旧值;条目 LEN 为 0 或越界立即停止本包。
 */
object StatusParser {

    /** 解析结果:出现过的条目更新对应字段,null=未出现。 */
    data class Status(
        val channelCount: Int? = null,
        val channelPower: Map<Int, Boolean>? = null,   // 通道号 -> 通断
        val allPower: Boolean? = null,
        val mode: Int? = null,
        val outputState: Boolean? = null,
        val childLock: Boolean? = null,
        val powerState: Boolean? = null,
        val sunriseMode: Int? = null,
        val timers: List<TimerGroup>? = null,
        val timerFamily: ModelTable.Family? = null,    // 以收到的定时条目 TYPE 为准
        val countdown: List<CountdownCodec.Countdown>? = null,   // 3232,null=未出现
        val loopTimer: List<LoopTimerCodec.LoopTimer>? = null,   // 3233(P5),null=未出现
    )

    /**
     * @param arg 3005 帧的 ARG(条目数起)
     * @param familyHint 型号查表所得族;用于判断定时条目是否含秒(3231 无秒)。
     *   若收到的定时条目 TYPE 与 hint 不符,以条目 TYPE 为准。
     */
    fun parse(arg: ByteArray, familyHint: ModelTable.Family? = null): Status {
        if (arg.isEmpty()) return Status()
        var pos = 0
        val count = arg[pos++].toInt() and 0xFF

        var channelCount: Int? = null
        var channelPower: MutableMap<Int, Boolean>? = null
        var allPower: Boolean? = null
        var mode: Int? = null
        var outputState: Boolean? = null
        var childLock: Boolean? = null
        var powerState: Boolean? = null
        var sunriseMode: Int? = null
        var timers: List<TimerGroup>? = null
        var timerFamily: ModelTable.Family? = null
        var countdown: List<CountdownCodec.Countdown>? = null
        var loopTimer: List<LoopTimerCodec.LoopTimer>? = null

        for (unused in 0 until count) {
            if (pos + 3 > arg.size) break                 // 截断:停止整包(§9.3)
            val itemLen = arg[pos].toInt() and 0xFF
            if (itemLen == 0 || pos + itemLen > arg.size) break
            val type = ((arg[pos + 1].toInt() and 0xFF) shl 8) or (arg[pos + 2].toInt() and 0xFF)
            val value = arg.copyOfRange(pos + 3, pos + itemLen)

            when (type) {
                Commands.StatusType.CHANNEL_COUNT ->
                    if (value.isNotEmpty()) channelCount = value[0].toInt() and 0xFF

                Commands.StatusType.CHANNEL_POWER -> {
                    val map = channelPower ?: LinkedHashMap<Int, Boolean>().also { channelPower = it }
                    var i = 0
                    while (i + 1 < value.size) {
                        map[value[i].toInt() and 0xFF] = (value[i + 1].toInt() and 0xFF) != 0
                        i += 2
                    }
                }

                Commands.StatusType.ALL_POWER ->
                    if (value.isNotEmpty()) allPower = value[0].toInt() and 0xFF != 0

                Commands.StatusType.MODE ->
                    if (value.isNotEmpty()) mode = value[0].toInt() and 0xFF

                Commands.StatusType.OUTPUT_STATE ->
                    if (value.isNotEmpty()) outputState = value[0].toInt() and 0xFF != 0

                Commands.StatusType.CHILD_LOCK ->
                    if (value.isNotEmpty()) childLock = value[0].toInt() and 0xFF != 0

                Commands.StatusType.POWER_STATE ->
                    if (value.isNotEmpty()) powerState = value[0].toInt() and 0xFF != 0

                Commands.StatusType.SUNRISE_MODE ->
                    if (value.isNotEmpty()) sunriseMode = value[0].toInt() and 0xFF

                Commands.StatusType.TIMER_LEGACY -> {
                    timers = TimerCodec.decode(value, withSeconds = false)
                    timerFamily = ModelTable.Family.LEGACY
                }

                Commands.StatusType.TIMER_GROUP -> {
                    timers = TimerCodec.decode(value, withSeconds = true)
                    timerFamily = ModelTable.Family.GROUP
                }

                Commands.StatusType.TIMER_BATCH -> {
                    timers = TimerCodec.decode(value, withSeconds = true)
                    timerFamily = ModelTable.Family.BATCH
                }

                Commands.StatusType.COUNTDOWN ->           // 3232 倒计时详情(§8.1 同构)
                    countdown = CountdownCodec.decode(value)

                Commands.StatusType.LOOP_TIMER ->          // 3233 循环定时详情(P5,只读)
                    loopTimer = LoopTimerCodec.decode(value)
                // 3235/3281/3282/3172/3261:M5+ 解析
            }
            pos += itemLen
        }

        return Status(
            channelCount, channelPower?.toMap(), allPower, mode,
            outputState, childLock, powerState, sunriseMode, timers, timerFamily,
            countdown, loopTimer,
        )
    }

    /**
     * 5012 设备信息解析(说明书 §6.4):
     * `ARG = 00 | n1 | productId(n1 ASCII) | n2 | version(n2 ASCII)`
     * 严格逐段解析,不做偏移猜测;版本仅展示用。
     */
    data class DevInfo(val productId: String, val version: String)

    fun parseDevInfo(arg: ByteArray): DevInfo? {
        if (arg.size < 4) return null
        var i = 0
        i++                                   // 跳过首字节 00(保留位)
        val n1 = arg[i++].toInt() and 0xFF
        if (i + n1 > arg.size) return null
        val productId = String(arg, i, n1, Charsets.US_ASCII); i += n1
        if (i >= arg.size) return DevInfo(productId, "")
        val n2 = arg[i++].toInt() and 0xFF
        if (i + n2 > arg.size) return DevInfo(productId, "")
        val version = String(arg, i, n2, Charsets.US_ASCII)
        return DevInfo(productId, version)
    }

    /** 对时参数:14B ASCII yyyyMMddHHmmss(说明书 §5.3)。 */
    fun clockArg(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): ByteArray {
        val s = "%04d%02d%02d%02d%02d%02d".format(year, month, day, hour, minute, second)
        return s.toByteArray(Charsets.US_ASCII)
    }
}
