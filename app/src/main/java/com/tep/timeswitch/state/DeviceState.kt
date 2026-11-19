package com.tep.timeswitch.state

import com.tep.timeswitch.ble.ScannedDevice
import com.tep.timeswitch.protocol.ModelTable
import com.tep.timeswitch.protocol.TimerCodec

/** 设备全量状态(单一数据源,3005 回包更新,UI 只读)。 */
data class DeviceState(
    val device: ScannedDevice? = null,
    val productId: String = "",
    val family: ModelTable.Family = ModelTable.FALLBACK_FAMILY,
    val version: String = "",
    val timerMaxCnt: Int = 28,

    val channelCount: Int = 1,
    val power: Map<Int, Boolean> = emptyMap(),   // 通道号 -> 通断
    val allPower: Boolean = false,
    val mode: Int = 0,
    val outputState: Boolean = false,
    val childLock: Boolean = false,
    val powerState: Boolean = false,

    val timers: List<TimerCodec.TimerGroup> = emptyList(),
    val countdown: Countdown? = null,
) {
    data class Countdown(
        val h: Int, val m: Int, val s: Int,
        val event: Boolean,       // true=到点开 false=到点关
    )

    /** 单通道机型快捷取值。 */
    val mainPower: Boolean get() = power[0] ?: outputState
}
