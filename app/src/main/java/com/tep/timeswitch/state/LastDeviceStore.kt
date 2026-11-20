package com.tep.timeswitch.state

import android.content.Context
import com.tep.timeswitch.ble.ScannedDevice
import com.tep.timeswitch.protocol.ModelTable

/**
 * 记住上次连接的设备(功能清单 A10):启动后自动重连用。
 * 用 SharedPreferences 存最小字段(保持最小依赖,不引 DataStore)。
 */
class LastDeviceStore(context: Context) {

    private val prefs = context.getSharedPreferences("last_device", Context.MODE_PRIVATE)

    fun save(device: ScannedDevice) {
        prefs.edit()
            .putString(KEY_NAME, device.name)
            .putString(KEY_ADDRESS, device.address)
            .putString(KEY_MAC, device.macFromAdv)
            .putString(KEY_PRODUCT, device.productId)
            .putInt(KEY_TYPE, device.type)
            .putInt(KEY_ENCODE, device.encode)
            .apply()
    }

    /** 恢复上次设备;无记录或字段缺失返回 null。 */
    fun load(): ScannedDevice? {
        val address = prefs.getString(KEY_ADDRESS, null) ?: return null
        val productId = prefs.getString(KEY_PRODUCT, "") ?: ""
        return ScannedDevice(
            name = prefs.getString(KEY_NAME, "") ?: "",
            address = address,
            macFromAdv = prefs.getString(KEY_MAC, "") ?: "",
            productId = productId,
            type = prefs.getInt(KEY_TYPE, 0x4951),
            encode = prefs.getInt(KEY_ENCODE, 0x41),
            rssi = 0,
            model = ModelTable.find(productId),
        )
    }

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val KEY_NAME = "name"
        private const val KEY_ADDRESS = "address"
        private const val KEY_MAC = "mac"
        private const val KEY_PRODUCT = "productId"
        private const val KEY_TYPE = "type"
        private const val KEY_ENCODE = "encode"
    }
}
