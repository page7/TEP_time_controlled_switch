package com.tep.timeswitch.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import com.tep.timeswitch.protocol.ModelTable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * BLE 扫描 + 广播解析(说明书 §3.1)。
 *
 * 过滤规则:厂商数据 type = 0x4951(或 0x4957 时控商照)且 encode = 0x41。
 * productId 未知也显示(标记「未知型号」,按 GROUP 族兜底)—— 与原厂
 * 「productId 不在目录就不显示」不同,避免漏掉新品;见 AGENT.md 协议补充 P2。
 */
class BleScanner(context: Context) {

    private val scanner = context.getSystemService(BluetoothManager::class.java)
        .adapter?.bluetoothLeScanner

    private val _devices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val devices: StateFlow<List<ScannedDevice>> = _devices

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    // 同一设备重复上报时更新 RSSI,保持列表稳定
    private val byAddress = LinkedHashMap<String, ScannedDevice>()

    private val callback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::handleResult)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "scan failed: $errorCode")
            _scanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        val scanner = this.scanner ?: run {
            Log.w(TAG, "BLE scanner unavailable")
            return
        }
        if (_scanning.value) return
        byAddress.clear()
        _devices.value = emptyList()
        scanner.startScan(callback)
        _scanning.value = true
        Log.i(TAG, "scan started")
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val scanner = this.scanner ?: return
        if (!_scanning.value) return
        scanner.stopScan(callback)
        _scanning.value = false
        Log.i(TAG, "scan stopped")
    }

    private fun handleResult(result: ScanResult) {
        val parsed = parseAdv(result) ?: return
        byAddress[parsed.address] = parsed
        _devices.value = byAddress.values.sortedByDescending { it.rssi }
    }

    /**
     * 广播厂商数据解析(AGENT.md 协议补充 P1,偏移取自小程序 getDeviceInfoByAdv):
     * `type = hex[0..4) | encode = hex[4..6) | macAddr = hex[6..18) | productId = hex[18..) ASCII`
     * 注:hex 位置指厂商数据整体(含 type 两字节)的 hex 字符串偏移。
     */
    private fun parseAdv(result: ScanResult): ScannedDevice? {
        val record = result.scanRecord ?: return null
        // ScanRecord 的 manufacturerSpecificData 按 companyId 分桶;
        // 本生态设备把 type 放在最前(即 AD 结构中 0xFF 之后的整体),直接遍历所有厂商数据段。
        val raw = record.bytes ?: return null
        val payload = findManufacturerPayload(raw) ?: return null
        if (payload.size < 10) return null

        val type = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
        val encode = payload[2].toInt() and 0xFF
        if (encode != 0x41) return null
        if (type != TYPE_SWITCH && type != TYPE_LIGHTING) return null

        val macHex = buildString {
            for (i in 3..8) append("%02X".format(payload[i].toInt() and 0xFF))
        }
        val productId = payload.copyOfRange(9, payload.size)
            .toString(Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }

        val model = ModelTable.find(productId)
        val device = ScannedDevice(
            name = result.device.name ?: model?.name ?: productId,
            address = result.device.address,
            macFromAdv = macHex,
            productId = productId,
            type = type,
            encode = encode,
            rssi = result.rssi,
            model = model,
        )
        Log.d(TAG, "found: $device")
        return device
    }

    /**
     * 从原始广播字节找 AD type = 0xFF 的段,返回其数据部分(含 type 两字节起)。
     * AD 结构:[len][type][data...],len 含 type 与 data。
     */
    private fun findManufacturerPayload(raw: ByteArray): ByteArray? {
        var i = 0
        while (i < raw.size - 1) {
            val len = raw[i].toInt() and 0xFF
            if (len == 0) break
            if (i + len >= raw.size) break
            val adType = raw[i + 1].toInt() and 0xFF
            if (adType == 0xFF) {
                // data 从 i+2 起,len-1 字节;WeChat advertisData 即该段整体(含 type 两字节)。
                return raw.copyOfRange(i + 2, i + 1 + len)
            }
            i += len + 1
        }
        return null
    }

    companion object {
        private const val TAG = "BleScanner"
        const val TYPE_SWITCH = 0x4951       // 时控开关(插座)
        const val TYPE_LIGHTING = 0x4957     // 时控商照
    }
}
