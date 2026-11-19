package com.tep.timeswitch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tep.timeswitch.ble.BleConnState
import com.tep.timeswitch.ble.ScannedDevice
import com.tep.timeswitch.protocol.Commands
import com.tep.timeswitch.state.DeviceRepository
import com.tep.timeswitch.state.DeviceState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DeviceRepository(app, viewModelScope)

    val devices: StateFlow<List<ScannedDevice>> = repo.scanner.devices
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val scanning: StateFlow<Boolean> = repo.scanner.scanning
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val connState: StateFlow<BleConnState> = repo.connection.state
    val deviceState: StateFlow<DeviceState> = repo.state
    val frameLog: StateFlow<List<String>> = repo.frameLog

    // ---------------- 扫描 / 连接 ----------------

    fun toggleScan() {
        if (scanning.value) repo.scanner.stop() else repo.scanner.start()
    }

    fun connect(device: ScannedDevice) = repo.connect(device)

    fun disconnect() = repo.disconnect()

    // ---------------- 控制 ----------------

    fun setPower(on: Boolean) = viewModelScope.launch {
        val st = repo.state.value
        if (st.channelCount <= 1) {
            // 单通道机型:3221 为主入口,3225 兜底(§11.1)
            repo.setChannelPower(0, on)
            repo.setAllPower(on)
        } else {
            repo.setChannelPower(0, on)
        }
    }

    fun setMode(mode: Int) = viewModelScope.launch { repo.setMode(mode) }

    fun refresh() = viewModelScope.launch { repo.refresh() }

    fun release() {
        repo.scanner.stop()
        repo.disconnect()
    }
}
