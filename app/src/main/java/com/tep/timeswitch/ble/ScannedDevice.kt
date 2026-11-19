package com.tep.timeswitch.ble

import com.tep.timeswitch.protocol.ModelTable

/**
 * 扫描到的设备条目(说明书 §3)。
 * 身份来自广播包厂商自定义数据(AD type 0xFF):
 * type(2B hex) | encode(1B hex) | MAC(6B hex) | productId(ASCII)
 */
data class ScannedDevice(
    val name: String,           // 广播名(可能为空)
    val address: String,        // 系统蓝牙地址(连接用)
    val macFromAdv: String,     // 广播内 MAC(hex,展示/识别码计算优先用它)
    val productId: String,
    val type: Int,
    val encode: Int,
    val rssi: Int,
    val model: ModelTable.Model?,   // null = 未知型号
) {
    /** 显示识别码:MAC 末 4 hex -> 十进制 -> ≥5 位取末 4 位,否则左补 0 到 4 位(§3.2)。 */
    val displayCode: String
        get() {
            val tail = macFromAdv.replace(":", "").takeLast(4)
            if (tail.length < 4) return "0000"
            val dec = tail.toInt(16).toString()
            return if (dec.length >= 5) dec.takeLast(4) else dec.padStart(4, '0')
        }

    /** 型号显示名:已知型号用中文名,未知标注。 */
    val displayName: String
        get() = model?.name ?: "未知型号"

    val family: ModelTable.Family
        get() = model?.family ?: ModelTable.FALLBACK_FAMILY
}
