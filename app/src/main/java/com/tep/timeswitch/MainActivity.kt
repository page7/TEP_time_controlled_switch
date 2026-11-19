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
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tep.timeswitch.ui.device.DeviceScreen
import com.tep.timeswitch.ui.scan.ScanScreen
import com.tep.timeswitch.ui.theme.TEPTimeSwitchTheme

class MainActivity : ComponentActivity() {

    private lateinit var vm: MainViewModel

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) ensureBluetoothEnabled()
    }

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* 用户拒绝开蓝牙也允许进入,扫描时再提示 */ }

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

                    if (deviceState.device == null) {
                        ScanScreen(
                            scanning = scanning,
                            devices = devices,
                            onScanClick = {
                                if (hasBlePermissions()) vm.toggleScan()
                                else requestBlePermissions()
                            },
                            onDeviceClick = { vm.connect(it) },
                        )
                    } else {
                        DeviceScreen(
                            connState = connState,
                            deviceState = deviceState,
                            frameLog = frameLog,
                            onPower = { vm.setPower(it) },
                            onMode = { vm.setMode(it) },
                            onRefresh = { vm.refresh() },
                            onDisconnect = { vm.disconnect() },
                        )
                    }
                }
            }
        }

        if (hasBlePermissions()) ensureBluetoothEnabled() else requestBlePermissions()
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
