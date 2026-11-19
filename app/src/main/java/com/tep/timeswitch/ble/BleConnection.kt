package com.tep.timeswitch.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.UUID

/** 连接状态(UI 展示与自动重连依据)。 */
sealed class BleConnState {
    data object Idle : BleConnState()
    data object Connecting : BleConnState()
    data object Discovering : BleConnState()
    data object Ready : BleConnState()          // 服务/特征/notify 就绪,可收发
    data class Disconnected(val manual: Boolean, val reason: String = "") : BleConnState()
    data class Failed(val message: String) : BleConnState()
}

/**
 * BLE 连接管理(说明书 §4):
 * 连接(5s 超时)→ 服务发现(短 UUID 9AF4,退化为 write+notify 服务)→
 * 请求 MTU 185 → 打开 notify(CCCD 2902)→ 等 500ms → 就绪。
 *
 * 写入:串行队列(Write With Response),逐包分片(MTU-3),单包失败重试 2 次。
 * 收包:FrameDecoder 状态机拼帧后经 [frames] 发出完整帧(一包多帧/错位均处理)。
 */
class BleConnection(private val context: Context, private val scope: CoroutineScope) {

    private val _state = MutableStateFlow<BleConnState>(BleConnState.Idle)
    val state: StateFlow<BleConnState> = _state

    /** 完整 FE01 帧流(已拼帧)。 */
    private val _frames = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val frames: SharedFlow<ByteArray> = _frames

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    @Volatile private var mtu = 23
    @Volatile private var manualClose = false

    private val writeMutex = Mutex()          // 保证整帧串行写
    private val gattOpMutex = Mutex()         // 串行化 GATT 设置操作
    @Volatile private var ack: CompletableDeferred<Unit>? = null
    private var setupJob: Job? = null

    private val decoder = com.tep.timeswitch.protocol.FrameDecoder { frame ->
        scope.launch { _frames.emit(frame) }
    }

    // ---------------- 连接 ----------------

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        closeInternal(keepManual = false)
        manualClose = false
        _state.value = BleConnState.Connecting
        Log.i(TAG, "connecting to ${device.address}")
        gatt = openGatt(device)
    }

    /** 自动重连用:重新发起连接(不置 manualClose)。 */
    @SuppressLint("MissingPermission")
    fun reconnect(device: BluetoothDevice) {
        closeInternal(keepManual = false)
        manualClose = false
        _state.value = BleConnState.Connecting
        Log.i(TAG, "reconnecting to ${device.address}")
        gatt = openGatt(device)
    }

    /** 主动断开(不触发自动重连)。 */
    @SuppressLint("MissingPermission")
    fun close() {
        manualClose = true
        closeInternal(keepManual = true)
        _state.value = BleConnState.Disconnected(manual = true)
    }

    @SuppressLint("MissingPermission")
    private fun closeInternal(keepManual: Boolean) {
        setupJob?.cancel()
        ack?.cancel()
        ack = null
        decoder.reset()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        writeChar = null
        notifyChar = null
        if (!keepManual) manualClose = false
    }

    @SuppressLint("MissingPermission")
    private fun openGatt(device: BluetoothDevice): BluetoothGatt =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } else {
            @Suppress("DEPRECATION")
            device.connectGatt(context, false, callback)
        }

    // ---------------- 发送 ----------------

    /**
     * 发送一整帧:按 MTU-3 分片,逐片 Write With Response 串行写。
     * 挂起直到整帧写完;单片失败重试 2 次。
     */
    suspend fun send(frame: ByteArray) {
        writeMutex.withLock {
            val chunkSize = (mtu - 3).coerceAtLeast(20)
            var offset = 0
            while (offset < frame.size) {
                val end = minOf(offset + chunkSize, frame.size)
                writeChunkWithRetry(frame.copyOfRange(offset, end))
                if (end < frame.size) delay(20)   // 片间隔
                offset = end
            }
        }
    }

    /** 入队发送(不挂起调用方,保序)。 */
    fun sendAsync(frame: ByteArray) {
        scope.launch {
            runCatching { send(frame) }
                .onFailure { Log.w(TAG, "send failed: $it") }
        }
    }

    private suspend fun writeChunkWithRetry(chunk: ByteArray) {
        var attempt = 0
        while (true) {
            try {
                writeChunk(chunk)
                return
            } catch (e: Exception) {
                attempt++
                if (attempt > 2) throw e
                Log.w(TAG, "write retry $attempt: ${e.message}")
                delay(50)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeChunk(chunk: ByteArray) {
        val gatt = this.gatt ?: throw IllegalStateException("not connected")
        val ch = writeChar ?: throw IllegalStateException("no write characteristic")
        val deferred = CompletableDeferred<Unit>()
        ack = deferred
        ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(ch, chunk, ch.writeType) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            ch.value = chunk
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(ch)
        }
        if (!started) {
            ack = null
            throw IllegalStateException("writeCharacteristic rejected")
        }
        withTimeout(WRITE_TIMEOUT_MS) { deferred.await() }
    }

    // ---------------- GATT 回调 ----------------

    private val callback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.i(TAG, "connState: status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _state.value = BleConnState.Discovering
                    setupJob = scope.launch {
                        try {
                            gattOpMutex.withLock { g.discoverServices() }
                        } catch (e: Exception) {
                            Log.w(TAG, "discoverServices failed", e)
                        }
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val manual = manualClose
                    closeInternal(keepManual = true)
                    _state.value = BleConnState.Disconnected(manual = manual, reason = "status=$status")
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            Log.i(TAG, "servicesDiscovered: status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = BleConnState.Failed("service discovery failed: $status")
                return
            }
            setupJob = scope.launch {
                try {
                    gattOpMutex.withLock {
                        selectService(g)
                        requestMtuSuspend(g, PREFERRED_MTU)
                        enableNotify(g)
                    }
                    delay(READY_DELAY_MS)      // 链路就绪延时(§4.3,不要改小)
                    _state.value = BleConnState.Ready
                    Log.i(TAG, "ready (mtu=$mtu)")
                } catch (e: Exception) {
                    Log.e(TAG, "setup failed", e)
                    _state.value = BleConnState.Failed("setup failed: ${e.message}")
                }
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            decoder.feed(value)
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            completeAck(status, "write")
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            completeAck(status, "cccd")
        }

        override fun onMtuChanged(g: BluetoothGatt, mtuValue: Int, status: Int) {
            Log.i(TAG, "mtu=$mtuValue status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) mtu = mtuValue
            completeAck(status, "mtu")
        }

        private fun completeAck(status: Int, what: String) {
            val d = ack ?: return
            ack = null
            if (status == BluetoothGatt.GATT_SUCCESS) d.complete(Unit)
            else d.completeExceptionally(IllegalStateException("$what status=$status"))
        }
    }

    // ---------------- 建链细节 ----------------

    /**
     * 服务与特征选择(§4.2):
     * 优先短 UUID "9AF4" 服务(原实现取 UUID 字符串第 5–8 位比对);
     * 未命中退化为「同时含可写 + 可通知特征」的服务。
     */
    @SuppressLint("MissingPermission")
    private fun selectService(g: BluetoothGatt) {
        val services = g.services.orEmpty()
        var target = services.firstOrNull { svc ->
            svc.uuid.toString().substring(4, 8).equals("9AF4", ignoreCase = true)
        }
        if (target == null) {
            target = services.firstOrNull { svc ->
                svc.characteristics.any { it.properties and PROPS_WRITE != 0 } &&
                    svc.characteristics.any { it.properties and PROPS_NOTIFY != 0 }
            }
        }
        requireNotNull(target) { "no suitable service (9AF4)" }

        val w = target.characteristics.firstOrNull { it.properties and PROPS_WRITE != 0 }
        val n = target.characteristics.firstOrNull { it.properties and PROPS_NOTIFY != 0 }
        requireNotNull(w) { "no write characteristic" }
        requireNotNull(n) { "no notify characteristic" }

        writeChar = w
        notifyChar = n
        Log.i(TAG, "service=${target.uuid} write=${w.uuid} notify=${n.uuid}")
    }

    /** 请求 MTU;失败回退 23,发送端自动按 MTU-3 分片。 */
    @SuppressLint("MissingPermission")
    private suspend fun requestMtuSuspend(g: BluetoothGatt, value: Int) {
        val deferred = CompletableDeferred<Unit>()
        ack = deferred
        if (!g.requestMtu(value)) {
            ack = null
            Log.w(TAG, "requestMtu rejected, fallback mtu=23")
            return
        }
        runCatching { withTimeout(MTU_TIMEOUT_MS) { deferred.await() } }
            .onFailure { Log.w(TAG, "mtu request timeout, fallback mtu=23") }
    }

    /** 打开通知:setCharacteristicNotification + 写 CCCD(部分机型只做前者不生效)。 */
    @SuppressLint("MissingPermission")
    private suspend fun enableNotify(g: BluetoothGatt) {
        val n = notifyChar ?: return
        g.setCharacteristicNotification(n, true)
        val cccd = n.getDescriptor(CCCD_UUID) ?: run {
            Log.w(TAG, "no CCCD descriptor; notify may not work on this device")
            return
        }
        val value = if (n.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        else
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

        val deferred = CompletableDeferred<Unit>()
        ack = deferred
        @Suppress("DEPRECATION")
        cccd.value = value
        @Suppress("DEPRECATION")
        if (!g.writeDescriptor(cccd)) {
            ack = null
            throw IllegalStateException("write CCCD rejected")
        }
        withTimeout(WRITE_TIMEOUT_MS) { deferred.await() }
    }

    companion object {
        private const val TAG = "BleConnection"
        const val PREFERRED_MTU = 185
        const val WRITE_TIMEOUT_MS = 3_000L
        const val MTU_TIMEOUT_MS = 2_000L
        const val READY_DELAY_MS = 500L          // 连接成功 → 发 5011 之前的稳定延时(§4.3)
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val PROPS_WRITE = BluetoothGattCharacteristic.PROPERTY_WRITE or
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
        const val PROPS_NOTIFY = BluetoothGattCharacteristic.PROPERTY_NOTIFY or
            BluetoothGattCharacteristic.PROPERTY_INDICATE
    }
}
