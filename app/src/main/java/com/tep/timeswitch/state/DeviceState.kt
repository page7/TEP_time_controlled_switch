package com.tep.timeswitch.state

import com.tep.timeswitch.ble.ScannedDevice
import com.tep.timeswitch.protocol.CountdownCodec
import com.tep.timeswitch.protocol.LoopTimerCodec
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
    val timerSlotCount: Int = 0,                 // 设备定时槽位跨度(max(index)+1 与列表长度取大),BATCH 清空用
    val countdown: CountdownCodec.Countdown? = null,
    val loopTimer: List<LoopTimerCodec.LoopTimer> = emptyList(),   // 只读(P5),编辑 M5+
) {
    /** 单通道机型快捷取值。 */
    val mainPower: Boolean get() = power[0] ?: outputState
}
