package com.tep.timeswitch.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tep.timeswitch.state.DeviceState
import com.tep.timeswitch.state.ThemeMode

/**
 * 设置页(开发文档 §6.1):主题明暗控制、设备信息、日志、删除设备、APP 版本。
 * 设备信息与通信日志自首页迁入(2026-11-20)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    deviceState: DeviceState,
    frameLog: List<String>,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    versionName: String,
    onDeleteDevice: () -> Unit,
    onBack: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // —— 主题 ——
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("主题", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    ThemeRadioRow("跟随系统", themeMode == ThemeMode.SYSTEM) { onThemeModeChange(ThemeMode.SYSTEM) }
                    ThemeRadioRow("浅色", themeMode == ThemeMode.LIGHT) { onThemeModeChange(ThemeMode.LIGHT) }
                    ThemeRadioRow("深色", themeMode == ThemeMode.DARK) { onThemeModeChange(ThemeMode.DARK) }
                }
            }

            // —— 设备信息(自首页迁入)——
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("设备信息", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    InfoRow("型号", deviceState.productId.ifEmpty { "—" })
                    InfoRow("版本", deviceState.version.ifEmpty { "—" })
                    InfoRow("固件族", deviceState.family.name)
                    InfoRow("定时上限", "${deviceState.timerMaxCnt} 组")
                    InfoRow("通道数", "${deviceState.channelCount}")
                    if (deviceState.loopTimer.isNotEmpty()) {
                        val lt = deviceState.loopTimer.first()
                        InfoRow(
                            "循环定时",
                            "%02d:%02d:%02d ~ %02d:%02d:%02d %s".format(
                                lt.startH, lt.startM, lt.startS,
                                lt.endH, lt.endM, lt.endS,
                                if (lt.event) "到点开" else "到点关",
                            ),
                        )
                    }
                }
            }

            // —— 日志(自首页迁入)——
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("日志", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    frameLog.takeLast(60).forEach { line ->
                        Text(
                            text = line,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            // —— 删除设备 ——
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pendingDelete = true }
                        .padding(16.dp),
                ) {
                    Text("删除设备", fontSize = 15.sp, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "清除已记住的设备并断开连接",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }

            // —— APP 版本 ——
            Card {
                Column(Modifier.padding(16.dp)) {
                    InfoRow("APP 版本", versionName.ifEmpty { "—" })
                }
            }
        }
    }

    if (pendingDelete) {
        AlertDialog(
            onDismissRequest = { pendingDelete = false },
            title = { Text("提示") },
            text = { Text("是否删除该设备?删除后将清除记住的设备并断开连接") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteDevice()
                    pendingDelete = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ThemeRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 15.sp)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, fontSize = 13.sp)
    }
}
