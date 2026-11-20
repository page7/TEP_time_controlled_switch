package com.tep.timeswitch

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tep.timeswitch.ui.countdown.CountdownScreen
import com.tep.timeswitch.ui.device.DeviceScreen
import com.tep.timeswitch.ui.loop.LoopTimerScreen
import com.tep.timeswitch.ui.scan.ScanScreen
import com.tep.timeswitch.ui.theme.TEPTimeSwitchTheme
import com.tep.timeswitch.ui.timer.TimerEditScreen
import com.tep.timeswitch.ui.timer.TimerListScreen

class MainActivity : ComponentActivity() {

    private lateinit var vm: MainViewModel

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            ensureBluetoothEnabled()
            vm.autoConnectLast()                 // A10:启动自动重连上次设备
        }
    }

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 用户开蓝牙后尝试自动重连上次设备(拒绝也允许进入,扫描时再提示)
        if (hasBlePermissions()) vm.autoConnectLast()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = MainViewModel(application)

        setContent {
            TEPTimeSwitchTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val connState by vm.connState.collectAsStateWithLifecycle()
                    val deviceState by vm.deviceState.collectAsStateWithLifecycle()
                    val scanning by vm.scanning.collectAsStateWithLifecycle()
                    val devices by vm.devices.collectAsStateWithLifecycle()
                    val frameLog by vm.frameLog.collectAsStateWithLifecycle()
                    val editing by vm.editing.collectAsStateWithLifecycle()
                    val page by vm.page.collectAsStateWithLifecycle()

                    val draft = editing
                    when {
                        draft != null -> TimerEditScreen(
                            draft = draft,
                            family = deviceState.family,
                            onSave = { vm.saveTimer(it) },
                            onBack = { vm.cancelEdit() },
                        )
                        deviceState.device == null -> ScanScreen(
                            scanning = scanning,
                            devices = devices,
                            onScanClick = {
                                if (hasBlePermissions()) vm.toggleScan()
                                else requestBlePermissions()
                            },
                            onDeviceClick = { vm.connect(it) },
                        )
                        else -> {
                            BackHandler(enabled = page != DevicePage.HOME) { vm.backHome() }
                            when (page) {
                                DevicePage.HOME -> DeviceScreen(
                                    connState = connState,
                                    deviceState = deviceState,
                                    frameLog = frameLog,
                                    messages = vm.messages,
                                    onMode = { vm.setMode(it) },
                                    onRefresh = { vm.refresh() },
                                    onDisconnect = { vm.disconnect() },
                                    onOpenPage = { vm.openPage(it) },
                                )
                                DevicePage.TIMERS -> TimerListScreen(
                                    deviceState = deviceState,
                                    messages = vm.messages,
                                    onAddTimer = { vm.addTimer() },
                                    onEditTimer = { vm.editTimer(it) },
                                    onDeleteTimer = { vm.deleteTimer(it) },
                                    onClearTimers = { vm.clearTimers() },
                                    onBack = { vm.backHome() },
                                )
                                DevicePage.COUNTDOWN -> CountdownScreen(
                                    countdown = deviceState.countdown,
                                    messages = vm.messages,
                                    onSave = { h, m, s, e -> vm.saveCountdown(h, m, s, e) },
                                    onBack = { vm.backHome() },
                                )
                                DevicePage.LOOP -> LoopTimerScreen(
                                    loopTimer = deviceState.loopTimer,
                                    messages = vm.messages,
                                    onSave = { sh, sm, ss, eh, em, es ->
                                        vm.saveLoopTimer(sh, sm, ss, eh, em, es)
                                    },
                                    onBack = { vm.backHome() },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (hasBlePermissions()) {
            ensureBluetoothEnabled()
            vm.autoConnectLast()
        } else requestBlePermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) vm.release()
    }

    // ---------------- 权限与蓝牙开关 ----------------

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun hasBlePermissions(): Boolean =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun requestBlePermissions() {
        permissionLauncher.launch(requiredPermissions())
    }

    @SuppressLint("MissingPermission")
    private fun ensureBluetoothEnabled() {
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: return
        if (!adapter.isEnabled) {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }
}
