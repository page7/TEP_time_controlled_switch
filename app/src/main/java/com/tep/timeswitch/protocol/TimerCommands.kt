package com.tep.timeswitch.protocol

import com.tep.timeswitch.protocol.ModelTable.Family
import com.tep.timeswitch.protocol.TimerCodec.TimerGroup

/**
 * 定时组三族下发策略(说明书 §7.5 + 小程序 cycle.js sendTimerData/deleteTimer/deleteTimer2 实证)。
 *
 * 纯函数:只产出「待发帧序列」,不含 BLE/延时;包间隔 100ms 由调用方(Repository)控制。
 *
 * | 族 | 指令 | 分包 | 删除语义 |
 * |---|---|---|---|
 * | LEGACY | 3231 | 单包全发(无秒) | 整表重发;删至空发 `00` 清空 |
 * | GROUP  | 3234 | 10 组/包(EN 恒 01) | 移除+重排 IDX+整表重发;删至空发 `00` 清空 |
 * | BATCH  | 3236 | 1 组/包(EN 生效) | 被删组置 EN=00 单组重发(占位顶掉旧数据) |
 */
object TimerCommands {

    /** 3234 每包含量(§7.1)。 */
    const val GROUPS_PER_PACKET = 10

    /**
     * 整表写入计划。timers 为空 → 清空计划([planClear])。
     * @param prevSlots 设备上次上报的定时槽位跨度(max(index)+1 与列表长度取大),BATCH 清空时逐槽覆盖。
     */
    fun planWrite(family: Family, timers: List<TimerGroup>, prevSlots: Int): List<ByteArray> {
        if (timers.isEmpty()) return planClear(family, prevSlots)
        return when (family) {
            Family.LEGACY -> listOf(
                Frame.build(
                    Commands.TIMER_LEGACY,
                    TimerCodec.encode(timers, withSeconds = false, forceEnable = true),
                ),
            )
            Family.GROUP -> timers.chunked(GROUPS_PER_PACKET).map { chunk ->
                Frame.build(
                    Commands.TIMER_GROUP,
                    TimerCodec.encode(chunk, withSeconds = true, forceEnable = true),
                )
            }
            Family.BATCH -> timers.map { planWriteSingle(it) }
        }
    }

    /** BATCH 族单组写(新增/编辑一组,1 组/包;EN 按组内语义,cycle-edit saveTimer2 实证)。 */
    fun planWriteSingle(group: TimerGroup): ByteArray =
        Frame.build(Commands.TIMER_BATCH, TimerCodec.encode(listOf(group), withSeconds = true))

    /**
     * 删除一组后的下发计划。
     * - LEGACY/GROUP:remaining 重排 IDX 后整表重发;删至空 → `00` 清空(B3)。
     * - BATCH:仅把被删组置 EN=00 单组重发,index 保持不变(B2/deleteTimer2)。
     */
    fun planDelete(
        family: Family,
        deleted: TimerGroup,
        remaining: List<TimerGroup>,
        prevSlots: Int,
    ): List<ByteArray> = when (family) {
        Family.BATCH -> listOf(planWriteSingle(deleted.copy(enabled = false)))
        else -> planWrite(family, reindex(remaining), prevSlots)
    }

    /**
     * 清空全部定时。
     * - LEGACY/GROUP:载荷仅一个 `00` 组数字节(§7.3)。
     * - BATCH:无整表清空指令,对 0..<prevSlots 每个槽位补发 EN=00 占位组(§7.5 删除陷阱)。
     */
    fun planClear(family: Family, prevSlots: Int): List<ByteArray> = when (family) {
        Family.LEGACY -> listOf(Frame.build(Commands.TIMER_LEGACY, TimerCodec.encodeClear()))
        Family.GROUP -> listOf(Frame.build(Commands.TIMER_GROUP, TimerCodec.encodeClear()))
        Family.BATCH -> (0 until prevSlots).map { planWriteSingle(disabledPlaceholder(it)) }
    }

    /** 重排 IDX 为 0..n-1(删除后整表重发前必须做,§11.2 坑 5)。 */
    fun reindex(timers: List<TimerGroup>): List<TimerGroup> =
        timers.mapIndexed { i, t -> t.copy(index = i) }

    /**
     * 新组可用槽位:最小的未占用 index(缺口优先,等价小程序 cycle-edit onLoad 缺口扫描),
     * 无空位返回 -1(调用方提示「最大支持 N 组定时」)。
     * - BATCH:EN=00 的槽位视为可复用(initTimerData 取首个 en=false 槽)。
     * - LEGACY/GROUP:所有上报组都占用槽位。
     */
    fun nextIndex(timers: List<TimerGroup>, family: Family, maxCnt: Int): Int {
        val blocked = when (family) {
            Family.BATCH -> timers.filter { it.enabled }.map { it.index }
            else -> timers.map { it.index }
        }.toSet()
        for (i in 0 until maxCnt) if (i !in blocked) return i
        return -1
    }

    /** BATCH 占位组(EN=00,内容无所谓,顶掉设备旧数据用)。 */
    private fun disabledPlaceholder(index: Int): TimerGroup = TimerGroup(
        index = index, enabled = false, switchId = 0,
        weekStart = 0, weekEnd = 0,
        startH = 0, startM = 0, startS = 0,
        endH = 0, endM = 0, endS = 0,
        event = false,
    )
}
