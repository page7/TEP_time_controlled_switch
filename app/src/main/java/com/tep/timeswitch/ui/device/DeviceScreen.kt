package com.tep.timeswitch.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tep.timeswitch.ble.BleConnState
import com.tep.timeswitch.protocol.Commands
import com.tep.timeswitch.state.DeviceState

/**
 * 控制主页(M1:主控 + 帧日志;M2/M3 扩展定时与倒计时区块)。
 * 布局参考小程序 socket 页:① 主控区 ② 模式胶囊 ③(后续)功能列表/定时。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    connState: BleConnState,
    deviceState: DeviceState,
    frameLog: List<String>,
    onPower: (Boolean) -> Unit,
    onMode: (Int) -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "${deviceState.device?.displayName ?: deviceState.productId}" +
                                " ${deviceState.device?.displayCode ?: ""}",
                            fontSize = 17.sp,
                        )
                        Text(
                            text = connLabel(connState),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                },
                actions = {
                    OutlinedButton(onClick = onRefresh, modifier = Modifier.padding(end = 8.dp)) {
                        Text("刷新", fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = onDisconnect, modifier = Modifier.padding(end = 12.dp)) {
                        Text("断开", fontSize = 13.sp)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ① 主控区
            item {
                Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val on = deviceState.mainPower
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (on) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .size(140.dp)
                                .clickable { onPower(!on) },
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                tint = if (on) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (on) "开关开启" else "开关关闭",
                            color = if (on) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 15.sp,
                        )
                    }
                }
            }

            // ② 工作模式胶囊(小程序 data-id 0–5,V1 展示 0–4)
            item {
                Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("工作模式", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Spacer(Modifier.height(12.dp))
                        val modes = listOf(
                            Commands.Mode.OFF to "常关",
                            Commands.Mode.ON to "常开",
                            Commands.Mode.AUTO to "自动",
                        )
                        val modes2 = listOf(
                            Commands.Mode.COUNTDOWN to "倒计时",
                            Commands.Mode.LOOP to "循环定时",
                        )
                        ModeRow(modes, deviceState.mode, onMode)
                        Spacer(Modifier.height(10.dp))
                        ModeRow(modes2, deviceState.mode, onMode)
                    }
                }
            }

            // ③ 设备信息
            item {
                Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("设备信息", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Spacer(Modifier.height(8.dp))
                        InfoRow("型号", deviceState.productId.ifEmpty { "—" })
                        InfoRow("版本", deviceState.version.ifEmpty { "—" })
                        InfoRow("固件族", deviceState.family.name)
                        InfoRow("定时上限", "${deviceState.timerMaxCnt} 组")
                        InfoRow("通道数", "${deviceState.channelCount}")
                    }
                }
            }

            // ④ 帧日志(M1 验收用,M4 精简为调试入口)
            item {
                Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("通信日志", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            items(frameLog.takeLast(60)) { line ->
                Text(
                    text = line,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ModeRow(
    modes: List<Pair<Int, String>>,
    current: Int,
    onMode: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        modes.forEach { (id, label) ->
            val selected = current == id
            Text(
                text = label,
                fontSize = 14.sp,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .clickable { onMode(id) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }
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

private fun connLabel(s: BleConnState): String = when (s) {
    is BleConnState.Idle -> "未连接"
    is BleConnState.Connecting -> "连接中…"
    is BleConnState.Discovering -> "发现服务…"
    is BleConnState.Ready -> "已连接"
    is BleConnState.Disconnected -> if (s.manual) "已断开" else "连接断开,自动重连中…"
    is BleConnState.Failed -> "连接失败:${s.message}"
}
