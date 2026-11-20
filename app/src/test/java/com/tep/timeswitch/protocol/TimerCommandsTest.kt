package com.tep.timeswitch.protocol

import com.tep.timeswitch.protocol.ModelTable.Family
import com.tep.timeswitch.protocol.TimerCodec.TimerGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 三族定时下发策略测试(说明书 §7.5 + 小程序 cycle.js 实证:10 组/包、1 组/包、EN=00 删除占位)。
 */
class TimerCommandsTest {

    private fun group(index: Int, enabled: Boolean = true) = TimerGroup(
        index = index, enabled = enabled, switchId = 0,
        weekStart = 0x7F, weekEnd = 0x7F,
        startH = 8, startM = 30, startS = 0,
        endH = 18, endM = 0, endS = 0,
        event = true,
    )

    // ---------------- 整表写入 ----------------

    @Test
    fun `group family packets 10 groups per frame`() {
        val timers = (0 until 12).map { group(it) }
        val frames = TimerCommands.planWrite(Family.GROUP, timers, prevSlots = 12)
        assertEquals(2, frames.size)                                  // 10 + 2
        assertEquals(Commands.TIMER_GROUP, Frame.cmdOf(frames[0]))
        assertEquals(10, Frame.argOf(frames[0])[0].toInt() and 0xFF)  // 组数
        assertEquals(2, Frame.argOf(frames[1])[0].toInt() and 0xFF)
    }

    @Test
    fun `legacy family single frame without seconds`() {
        val timers = (0 until 3).map { group(it) }
        val frames = TimerCommands.planWrite(Family.LEGACY, timers, prevSlots = 3)
        assertEquals(1, frames.size)
        assertEquals(Commands.TIMER_LEGACY, Frame.cmdOf(frames[0]))
        val arg = Frame.argOf(frames[0])
        assertEquals(3, arg[0].toInt() and 0xFF)
        assertEquals(0x0F, arg[1].toInt() and 0xFF)                    // 单组 15B 无秒
    }

    @Test
    fun `batch family one group per frame`() {
        val timers = (0 until 3).map { group(it) }
        val frames = TimerCommands.planWrite(Family.BATCH, timers, prevSlots = 3)
        assertEquals(3, frames.size)
        frames.forEach {
            assertEquals(Commands.TIMER_BATCH, Frame.cmdOf(it))
            assertEquals(1, Frame.argOf(it)[0].toInt() and 0xFF)      // 每包组数=1
        }
    }

    @Test
    fun `group family forces en to 01 on write`() {
        val frames = TimerCommands.planWrite(Family.GROUP, listOf(group(0, enabled = false)), 1)
        val arg = Frame.argOf(frames[0])
        assertEquals(0x01, arg[3].toInt() and 0xFF)                   // EN 恒 01
    }

    // ---------------- 删除 ----------------

    @Test
    fun `batch delete sends single disabled group with preserved index`() {
        val deleted = group(5)
        val frames = TimerCommands.planDelete(Family.BATCH, deleted, remaining = emptyList(), prevSlots = 6)
        assertEquals(1, frames.size)
        val arg = Frame.argOf(frames[0])
        assertEquals(0x13, arg[1].toInt() and 0xFF)                   // 组 LEN 19
        assertEquals(5, arg[2].toInt() and 0xFF)                      // index 保留
        assertEquals(0x00, arg[3].toInt() and 0xFF)                   // EN=00 占位(B2)
    }

    @Test
    fun `group delete last one sends clear byte`() {
        val frames = TimerCommands.planDelete(Family.GROUP, group(0), remaining = emptyList(), prevSlots = 1)
        assertEquals(1, frames.size)
        assertEquals("fe010003323400", Hex.frameHex(frames[0]))       // 清空(B3)
    }

    @Test
    fun `group delete resends whole table reindexed`() {
        val timers = (0 until 3).map { group(it) }
        val frames = TimerCommands.planDelete(Family.GROUP, timers[0], remaining = timers.drop(1), prevSlots = 3)
        assertEquals(1, frames.size)
        val arg = Frame.argOf(frames[0])
        assertEquals(2, arg[0].toInt() and 0xFF)
        assertEquals(0, arg[2].toInt() and 0xFF)                      // 重排后 IDX=0
        assertEquals(1, arg[21].toInt() and 0xFF)                     // 第二组 IDX=1(21 = 1+19+1)
    }

    @Test
    fun `legacy delete last one sends clear byte`() {
        val frames = TimerCommands.planDelete(Family.LEGACY, group(0), emptyList(), 1)
        assertEquals("fe010003323100", Hex.frameHex(frames[0]))
    }

    // ---------------- 清空 ----------------

    @Test
    fun `batch clear covers every known slot`() {
        val frames = TimerCommands.planClear(Family.BATCH, prevSlots = 3)
        assertEquals(3, frames.size)
        frames.forEachIndexed { i, f ->
            val arg = Frame.argOf(f)
            assertEquals(i, arg[2].toInt() and 0xFF)                  // 槽位号
            assertEquals(0x00, arg[3].toInt() and 0xFF)               // EN=00(§7.5 删除陷阱)
        }
    }

    @Test
    fun `batch clear with no slots sends nothing`() {
        assertTrue(TimerCommands.planClear(Family.BATCH, prevSlots = 0).isEmpty())
    }

    @Test
    fun `write empty list is a clear`() {
        assertEquals("fe010003323400", Hex.frameHex(TimerCommands.planWrite(Family.GROUP, emptyList(), 2).single()))
        assertEquals("fe010003323100", Hex.frameHex(TimerCommands.planWrite(Family.LEGACY, emptyList(), 2).single()))
    }

    // ---------------- 新组槽位 ----------------

    @Test
    fun `next index appends for compact table`() {
        assertEquals(2, TimerCommands.nextIndex(listOf(group(0), group(1)), Family.GROUP, 28))
    }

    @Test
    fun `next index fills gap`() {
        assertEquals(1, TimerCommands.nextIndex(listOf(group(0), group(2)), Family.GROUP, 28))
    }

    @Test
    fun `next index reuses disabled slot for batch`() {
        val timers = listOf(group(0), group(1, enabled = false), group(2))
        assertEquals(1, TimerCommands.nextIndex(timers, Family.BATCH, 28))
    }

    @Test
    fun `next index disabled slots block for group family`() {
        // GROUP 族 EN 无效,停用组仍占槽位
        val timers = listOf(group(0), group(1, enabled = false))
        assertEquals(2, TimerCommands.nextIndex(timers, Family.GROUP, 28))
    }

    @Test
    fun `next index returns -1 when full`() {
        val timers = (0 until 4).map { group(it) }
        assertEquals(-1, TimerCommands.nextIndex(timers, Family.GROUP, 4))
    }

    @Test
    fun `reindex compacts indexes`() {
        val out = TimerCommands.reindex(listOf(group(3), group(7)))
        assertEquals(listOf(0, 1), out.map { it.index })
    }

    // ---------------- 单组写 ----------------

    @Test
    fun `batch single write keeps en semantics`() {
        val frame = TimerCommands.planWriteSingle(group(2, enabled = false))
        val arg = Frame.argOf(frame)
        assertEquals(1, arg[0].toInt() and 0xFF)
        assertEquals(2, arg[2].toInt() and 0xFF)
        assertEquals(0x00, arg[3].toInt() and 0xFF)                   // EN=00 生效(BATCH)
        assertEquals(0x01, arg[19].toInt() and 0xFF)                  // EVENT=到点开(组末字节)
    }
}
