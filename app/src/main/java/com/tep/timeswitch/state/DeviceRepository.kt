package com.tep.timeswitch.state

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.tep.timeswitch.ble.BleConnState
import com.tep.timeswitch.ble.BleConnection
import com.tep.timeswitch.ble.BleScanner
import com.tep.timeswitch.ble.ScannedDevice
import com.tep.timeswitch.protocol.Commands
import com.tep.timeswitch.protocol.Frame
import com.tep.timeswitch.protocol.Hex
import com.tep.timeswitch.protocol.ModelTable
import com.tep.timeswitch.protocol.StatusParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 设备仓库:业务层唯一入口。
 * - 握手时序(§4.1 步骤⑦–⑬):Ready 后 5011 → 100ms → 5001 → 收 5012 → 500ms → 3004
 * - 3005 解析合并进 [DeviceState]
 * - 控制指令封装(开关/模式/对时/状态查询)
 */
class DeviceRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    val scanner = BleScanner(context)
    val connection = BleConnection(context, scope)

    private val _state = MutableStateFlow(DeviceState())
    val state: StateFlow<DeviceState> = _state

    /** 原始帧日志(M1 验收:可见 5012/3005)。 */
    private val _frameLog = MutableStateFlow<List<String>>(emptyList())
    val frameLog: StateFlow<List<String>> = _frameLog

    private var handshakeJob: Job? = null
    private var reconnectJob: Job? = null

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
        _state.update { it.copy(device = device) }
        reconnectJob?.cancel()
        val btDevice = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            ?.getRemoteDevice(device.address) ?: return
        connection.connect(btDevice)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        reconnectJob?.cancel()
        handshakeJob?.cancel()
        connection.close()
    }

    private fun onConnState(s: BleConnState) {
        when (s) {
            is BleConnState.Ready -> {
                appendLog("[链路] 就绪,开始握手")
                startHandshake()
            }
            is BleConnState.Disconnected -> {
                handshakeJob?.cancel()
                if (!s.manual) {
                    appendLog("[链路] 意外断开(${s.reason}),5s 后自动重连")
                    scheduleReconnect()
                } else {
                    appendLog("[链路] 已断开")
                }
            }
            else -> Unit
        }
    }

    @SuppressLint("MissingPermission")
    private fun scheduleReconnect() {
        val device = _state.value.device ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(5_000)
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

    private fun mergeStatus(st: DeviceState, s: StatusParser.Status): DeviceState = st.copy(
        channelCount = s.channelCount ?: st.channelCount,
        power = s.channelPower ?: st.power,
        allPower = s.allPower ?: st.allPower,
        mode = s.mode ?: st.mode,
        outputState = s.outputState ?: st.outputState,
        childLock = s.childLock ?: st.childLock,
        powerState = s.powerState ?: st.powerState,
        timers = s.timers ?: st.timers,
    )

    private fun appendLog(line: String) {
        Log.i(TAG, line)
        _frameLog.update { (it + line).takeLast(200) }
    }

    // ---------------- 控制指令 ----------------

    private suspend fun sendCmd(cmd: Int, arg: ByteArray = ByteArray(0)) {
        val frame = Frame.build(cmd, arg)
        appendLog("[发] ${Hex.frameHex(frame)}")
        connection.send(frame)
    }

    /** 对时(5001):14B ASCII yyyyMMddHHmmss。 */
    suspend fun sendClock() {
        val c = Calendar.getInstance()
        val arg = StatusParser.clockArg(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND),
        )
        sendCmd(Commands.SYNC_CLOCK, arg)
    }

    /** 单通道开关(3221):通道 + 状态。 */
    suspend fun setChannelPower(channel: Int, on: Boolean) {
        sendCmd(Commands.SET_CHANNEL_POWER, byteArrayOf(channel.toByte(), if (on) 1 else 0))
    }

    /** 总开关(3225)。 */
    suspend fun setAllPower(on: Boolean) {
        sendCmd(Commands.SET_ALL_POWER, byteArrayOf(if (on) 1 else 0))
    }

    /** 工作模式(3007)。 */
    suspend fun setMode(mode: Int) {
        sendCmd(Commands.SET_MODE, byteArrayOf(mode.toByte()))
    }

    /** 读全量状态(3004)。 */
    suspend fun refresh() = sendCmd(Commands.QUERY_STATUS)

    /** 童锁(3229)。 */
    suspend fun setChildLock(lock: Boolean) {
        sendCmd(Commands.SET_CHILD_LOCK, byteArrayOf(if (lock) 1 else 0))
    }

    companion object {
        private const val TAG = "DeviceRepository"
    }
}
