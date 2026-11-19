package com.tep.timeswitch.protocol

/**
 * 收包状态机(说明书 §5.2,修复原实现缺陷)。
 *
 * 原实现不处理「一包两帧」和「帧头错位」;本实现:
 * 1. 缓冲区搜索 `fe01`,之前字节全部丢弃(对齐);
 * 2. 无 fe01 → 清空缓冲区;
 * 3. 缓冲区不足 4+LEN → 等待后续数据;
 * 4. 取出整帧回调,移除后继续循环(一包多帧)。
 *
 * 线程模型:外部保证串行调用(单线程调度或加锁)。
 */
class FrameDecoder(private val onFrame: (ByteArray) -> Unit) {

    private var buffer = ByteArray(0)

    /** 喂入一段通知数据(可能是半帧/多帧/噪声)。 */
    fun feed(chunk: ByteArray) {
        buffer += chunk
        while (true) {
            // 1. 搜索帧头
            val sof = indexOfSof(buffer)
            if (sof < 0) {
                buffer = ByteArray(0)   // 2. 无帧头:全丢
                return
            }
            if (sof > 0) {
                buffer = buffer.copyOfRange(sof, buffer.size)   // 丢弃错位字节
            }
            // 3. 需要至少 4 字节读 LEN
            if (buffer.size < 4) return
            val total = Frame.totalLengthOf(buffer)
            if (total < 6 || total > MAX_FRAME) {
                // LEN 非法:丢掉帧头重新对齐,防止死循环
                buffer = buffer.copyOfRange(1, buffer.size)
                continue
            }
            if (buffer.size < total) return   // 4. 半帧:等后续数据
            val frame = buffer.copyOfRange(0, total)
            buffer = buffer.copyOfRange(total, buffer.size)
            onFrame(frame)
        }
    }

    fun reset() { buffer = ByteArray(0) }

    private fun indexOfSof(data: ByteArray): Int {
        var i = 0
        while (i < data.size - 1) {
            if (data[i] == Frame.SOF0 && data[i + 1] == Frame.SOF1) return i
            i++
        }
        return -1
    }

    companion object {
        /** 帧长上限保护(定时表一节最多 28 组 × 19B + 头 ≈ 540B;留冗余)。 */
        const val MAX_FRAME = 2048
    }
}
