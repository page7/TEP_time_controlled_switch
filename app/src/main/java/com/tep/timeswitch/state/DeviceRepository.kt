package com.tep.timeswitch.state

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.tep.timeswitch.ble.BleConnState
import com.tep.timeswitch.ble.BleConnection
import com.tep.timeswitch.ble.BleScanner
import com.tep.timeswitch.ble.ScannedDevice
import com.tep.timeswitch.protocol.Commands
import com.tep.timeswitch.protocol.CountdownCodec
import com.tep.timeswitch.protocol.Frame
import com.tep.timeswitch.protocol.Hex
import com.tep.timeswitch.protocol.ModelTable
import com.tep.timeswitch.protocol.StatusParser
import com.tep.timeswitch.protocol.TimerCommands
import com.tep.timeswitch.protocol.TimerCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.max

/**
 * 设备仓库:业务层唯一入口。
 * - 握手时序(§4.1 步骤⑦–⑬):Ready 后 5011 → 100ms → 5001 → 收 5012 → 500ms → 3004
 * - 3005 解析合并进 [DeviceState]
 * - 控制指令封装(开关/模式/定时三族下发/倒计时)
 * - 写后校准:写指令后自动补 3004,以设备回传为最终事实(§3.3)
 * - 意外断开指数退避自动重连(1s/2s/4s/8s/15s,最多 5 次,§4.5)
 * - 记住上次设备,启动自动重连(A10)
 */
class DeviceRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    val scanner = BleScanner(context)
    val connection = BleConnection(context, scope)
    private val store = LastDeviceStore(context)

    private val _state = MutableStateFlow(DeviceState())
    val state: StateFlow<DeviceState> = _state

    /** 原始帧日志(调试区展示)。 */
    private val _frameLog = MutableStateFlow<List<String>>(emptyList())
    val frameLog: StateFlow<List<String>> = _frameLog

    /** UI 提示消息(保存成功/超上限/重连失败…)。 */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages

    private var handshakeJob: Job? = null
    private var reconnectJob: Job? = null
    private var calibJob: Job? = null
    private var reconnectAttempt = 0

    init {
        scope.launch {
            connection.frames.collect { frame -> onFrame(frame) }
        }
        scope.launch {
            connection.state.collect { s -> onConnState(s) }
        }
    }

    // ---------------- 连接管理 ----------------

    @SuppressLint("MissingPermission")
    fun connect(device: ScannedDevice) {
        scanner.stop()
        _state.update {
            it.copy(
                device = device,
                productId = device.productId,
                // 族判定只看广播 productId(风险 #11),连上即定族,不等 5012
                family = device.model?.family ?: it.family,
                timerMaxCnt = device.model?.timerMaxCnt ?: it.timerMaxCnt,
            )
        }
        store.save(device)                       // A10:记住上次设备
        reconnectJob?.cancel()
        reconnectAttempt = 0
        val btDevice = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            ?.getRemoteDevice(device.address) ?: return
        connection.connect(btDevice)
    }

    /** 启动自动重连上次设备(A10);无记录则什么都不做。 */
    fun autoConnectLast() {
        if (_state.value.device != null) return
        val last = store.load() ?: return
        appendLog("[链路] 发现上次设备 ${last.displayName} ${last.displayCode},自动连接")
        connect(last)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        reconnectJob?.cancel()
        handshakeJob?.cancel()
        calibJob?.cancel()
        reconnectAttempt = 0
        connection.close()
    }

    private fun onConnState(s: BleConnState) {
        when (s) {
            is BleConnState.Ready -> {
                reconnectAttempt = 0
                appendLog("[链路] 就绪,开始握手")
                startHandshake()
            }
            is BleConnState.Disconnected -> {
                handshakeJob?.cancel()
                if (!s.manual) {
                    appendLog("[链路] 意外断开(${s.reason})")
                    scheduleReconnect()
                } else {
                    appendLog("[链路] 已断开")
                }
            }
            else -> Unit
        }
    }

    /** 指数退避自动重连(§4.5):1s/2s/4s/8s/15s,最多 5 次。 */
    @SuppressLint("MissingPermission")
    private fun scheduleReconnect() {
        val device = _state.value.device ?: return
        if (reconnectAttempt >= RECONNECT_MAX) {
            appendLog("[链路] 自动重连 $RECONNECT_MAX 次均失败,请返回扫描页重新连接")
            emitMessage("连接已断开,自动重连失败")
            return
        }
        val ms = RECONNECT_BACKOFF[reconnectAttempt]
        reconnectAttempt++
        appendLog("[链路] ${ms / 1000}s 后自动重连($reconnectAttempt/$RECONNECT_MAX)")
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(ms)
            appendLog("[链路] 自动重连…")
            val btDevice = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                ?.getRemoteDevice(device.address) ?: return@launch
            connection.reconnect(btDevice)
        }
    }

    // ---------------- 握手(§4.1) ----------------

    private fun startHandshake() {
        handshakeJob?.cancel()
        handshakeJob = scope.launch {
            sendCmd(Commands.QUERY_INFO)                    // ⑧ 5011
            delay(100)                                      // ⑨ 100ms
            sendClock()                                     // ⑩ 5001 对时(每次连接都发)
            delay(500)                                      // ⑫ 500ms
            sendCmd(Commands.QUERY_STATUS)                  // ⑬ 3004
        }
    }

    // ---------------- 帧收发 ----------------

    private fun onFrame(frame: ByteArray) {
        val cmd = Frame.cmdOf(frame)
        val arg = Frame.argOf(frame)
        appendLog("[收] ${Hex.frameHex(frame)}")
        when (cmd) {
            Commands.DEV_INFO -> {
                val info = StatusParser.parseDevInfo(arg) ?: return
                appendLog("[5012] 型号=${info.productId} 版本=${info.version}")
                _state.update { st ->
                    val model = ModelTable.find(info.productId)
                    st.copy(
                        productId = info.productId,
                        version = info.version,
                        family = model?.family ?: st.family,
                        timerMaxCnt = model?.timerMaxCnt ?: st.timerMaxCnt,
                    )
                }
            }
            Commands.FULL_STATUS -> {
                val parsed = StatusParser.parse(arg, _state.value.family)
                _state.update { st -> mergeStatus(st, parsed) }
                appendLog("[3005] 通道=${parsed.channelCount} 通断=${parsed.channelPower} " +
                    "模式=${parsed.mode} 定时=${parsed.timers?.size ?: 0} 组")
            }
        }
    }

    private fun mergeStatus(st: DeviceState, s: StatusParser.Status): DeviceState {
        val timers = s.timers ?: st.timers
        val slotCount = if (s.timers != null) {
            max(s.timers.size, s.timers.maxOfOrNull { it.index + 1 } ?: 0)
        } else st.timerSlotCount
        return st.copy(
            channelCount = s.channelCount ?: st.channelCount,
            power = s.channelPower ?: st.power,
            allPower = s.allPower ?: st.allPower,
            mode = s.mode ?: st.mode,
            outputState = s.outputState ?: st.outputState,
            childLock = s.childLock ?: st.childLock,
            powerState = s.powerState ?: st.powerState,
            timers = timers,
            timerSlotCount = slotCount,
            // 3232 出现即覆盖(组数 0 → 清空),未出现保留旧值
            countdown = if (s.countdown != null) s.countdown.firstOrNull() else st.countdown,
            loopTimer = s.loopTimer ?: st.loopTimer,
        )
    }

    private fun appendLog(line: String) {
        Log.i(TAG, line)
        _frameLog.update { (it + line).takeLast(200) }
    }

    /** UI 提示(校验失败/下发结果)。 */
    fun emitMessage(msg: String) {
        _messages.tryEmit(msg)
    }

    // ---------------- 发送 ----------------

    private suspend fun sendCmd(cmd: Int, arg: ByteArray = ByteArray(0)) =
        sendFrame(Frame.build(cmd, arg))

    private suspend fun sendFrame(frame: ByteArray) {
        appendLog("[发] ${Hex.frameHex(frame)}")
        connection.send(frame)
    }

    /** 多帧顺序下发,包间隔 100ms(§4.3 定时表分包间隔)。 */
    private suspend fun sendFrames(frames: List<ByteArray>) {
        frames.forEachIndexed { i, f ->
            if (i > 0) delay(100)
            sendFrame(f)
        }
    }

    /** 写后校准(§3.3):写指令后补 3004,以设备回传为最终事实(防抖 500ms)。 */
    private fun calibrate() {
        calibJob?.cancel()
        calibJob = scope.launch {
            delay(500)
            sendCmd(Commands.QUERY_STATUS)
        }
    }

    // ---------------- 控制指令 ----------------

    /** 对时(5001):14B ASCII yyyyMMddHHmmss(P6:设备 RTC 只写不读,每次连接都必须对时)。 */
    suspend fun sendClock() {
        val c = Calendar.getInstance()
        val arg = StatusParser.clockArg(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND),
        )
        sendCmd(Commands.SYNC_CLOCK, arg)
    }

    /** 单通道开关(3221):通道 + 状态;乐观回写 + 校准。 */
    suspend fun setChannelPower(channel: Int, on: Boolean) {
        sendCmd(Commands.SET_CHANNEL_POWER, byteArrayOf(channel.toByte(), if (on) 1 else 0))
        _state.update { st -> st.copy(power = st.power + (channel to on)) }
        calibrate()
    }

    /** 总开关(3225);乐观回写 + 校准。 */
    suspend fun setAllPower(on: Boolean) {
        sendCmd(Commands.SET_ALL_POWER, byteArrayOf(if (on) 1 else 0))
        _state.update { st ->
            st.copy(
                allPower = on,
                power = if (on) st.power else st.power.mapValues { false },
            )
        }
        calibrate()
    }

    /** 工作模式(3007);乐观回写 + 校准。 */
    suspend fun setMode(mode: Int) {
        sendCmd(Commands.SET_MODE, byteArrayOf(mode.toByte()))
        _state.update { it.copy(mode = mode) }
        calibrate()
    }

    /** 读全量状态(3004)。 */
    suspend fun refresh() = sendCmd(Commands.QUERY_STATUS)

    /** 童锁(3229)。 */
    suspend fun setChildLock(lock: Boolean) {
        sendCmd(Commands.SET_CHILD_LOCK, byteArrayOf(if (lock) 1 else 0))
    }

    // ---------------- 定时组(M3) ----------------

    /**
     * 保存定时(新增/编辑一组):按族下发(§7.5),写完自动 3007=02(风险 #4)。
     * @param timers 保存后的完整定时列表
     * @param changedIndex 本次改动组在 timers 中的下标(BATCH 族只发这一组)
     */
    suspend fun saveTimer(timers: List<TimerCodec.TimerGroup>, changedIndex: Int) {
        val family = _state.value.family
        val frames = when (family) {
            ModelTable.Family.BATCH -> listOf(TimerCommands.planWriteSingle(timers[changedIndex]))
            else -> TimerCommands.planWrite(family, timers, _state.value.timerSlotCount)
        }
        sendFrames(frames)
        setMode(Commands.Mode.AUTO)                 // 写完定时必须切自动模式才生效(风险 #4)
        _state.update { it.copy(timers = timers, mode = Commands.Mode.AUTO) }
        emitMessage("保存成功")
        calibrate()
    }

    /** 删除一组:GROUP/LEGACY 整表重发(B3);BATCH 置 EN=00 单组重发(B2)。 */
    suspend fun deleteTimer(deleted: TimerCodec.TimerGroup, remaining: List<TimerCodec.TimerGroup>) {
        val family = _state.value.family
        val frames = TimerCommands.planDelete(family, deleted, remaining, _state.value.timerSlotCount)
        sendFrames(frames)
        val newList = if (family == ModelTable.Family.BATCH) remaining
        else TimerCommands.reindex(remaining)
        _state.update { it.copy(timers = newList) }
        emitMessage("删除成功")
        calibrate()
    }

    /** 清空全部定时;BATCH 逐槽补 EN=00 占位(§7.5 删除陷阱)。 */
    suspend fun clearTimers() {
        val family = _state.value.family
        val frames = TimerCommands.planClear(family, _state.value.timerSlotCount)
        if (frames.isEmpty()) {
            emitMessage("暂无定时可清空")
            return
        }
        sendFrames(frames)
        _state.update { it.copy(timers = emptyList(), timerSlotCount = 0) }
        emitMessage("已清空定时")
        calibrate()
    }

    // ---------------- 倒计时(M4) ----------------

    /** 下发倒计时(3232)+ 自动 3007=03(B9);倒计时是一次性的,切模式后才开始计时(§8.1)。 */
    suspend fun saveCountdown(h: Int, m: Int, s: Int, event: Boolean) {
        sendCmd(Commands.COUNTDOWN, CountdownCodec.encode(h, m, s, event))
        setMode(Commands.Mode.COUNTDOWN)
        _state.update {
            it.copy(
                countdown = CountdownCodec.Countdown(0, true, 0, h, m, s, event),
                mode = Commands.Mode.COUNTDOWN,
            )
        }
        emitMessage("倒计时已下发")
        calibrate()
    }

    companion object {
        private const val TAG = "DeviceRepository"
        val RECONNECT_BACKOFF = longArrayOf(1_000, 2_000, 4_000, 8_000, 15_000)
        const val RECONNECT_MAX = 5
    }
}
