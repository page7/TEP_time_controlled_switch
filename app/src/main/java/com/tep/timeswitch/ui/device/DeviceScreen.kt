package com.tep.timeswitch.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.NotInterested
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tep.timeswitch.DevicePage
import com.tep.timeswitch.ble.BleConnState
import com.tep.timeswitch.state.DeviceState
import kotlinx.coroutines.flow.SharedFlow

/**
 * 控制主页(开发文档 §6.1,2026-11-20 重布局):
 * ① 顶栏「时控开关 + 识别码」+ 刷新/断开
 * ② 状态区(无卡片底色,仅图标区着色)+ 连接状态/开关状态小字
 * ③ 工作模式(无标题,5 大按钮横排,图标占位)
 * ④ 菜单列表(设置分组定时/设置倒计时/设置循环定时 → 独立子页面)
 * ⑤ 设备信息 ⑥ 通信日志(调试)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    connState: BleConnState,
    deviceState: DeviceState,
    frameLog: List<String>,
    messages: SharedFlow<String>,
    onMode: (Int) -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenPage: (DevicePage) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "时控开关 ${deviceState.device?.displayCode ?: deviceState.productId}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                    )
                },
                actions = {
                    TextButton(onClick = onRefresh) {
                        Text("刷新", fontSize = 14.sp)
                    }
                    TextButton(onClick = onDisconnect, modifier = Modifier.padding(end = 8.dp)) {
                        Text("断开", fontSize = 14.sp)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // ② 状态区(不显示卡片背景,仅图标区着色)
            item {
                val on = deviceState.mainPower
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(
                                if (on) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                    ) {
                        Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            tint = if (on) Color.White
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    StatusLine("连接状态", connLabel(connState))
                    Spacer(Modifier.height(6.dp))
                    StatusLine("开关状态", if (on) "开启" else "关闭")
                }
            }

            // ③ 工作模式(无标题,5 大按钮横排等分,图标占位待替换)
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val mode = deviceState.mode
                    ModeButton("常关", Icons.Outlined.NotInterested, mode == 0, { onMode(0) }, Modifier.weight(1f))
                    ModeButton("常开", Icons.Outlined.Bolt, mode == 1, { onMode(1) }, Modifier.weight(1f))
                    ModeButton("自动", Icons.Outlined.Schedule, mode == 2, { onMode(2) }, Modifier.weight(1f))
                    ModeButton("倒计时", Icons.Outlined.Timer, mode == 3, { onMode(3) }, Modifier.weight(1f))
                    ModeButton("循环", Icons.Outlined.Repeat, mode == 4, { onMode(4) }, Modifier.weight(1f))
                }
            }

            // ④ 菜单列表(独立子页面入口)
            item {
                Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column {
                        MenuRow(Icons.Outlined.EventNote, "设置分组定时") { onOpenPage(DevicePage.TIMERS) }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        MenuRow(Icons.Outlined.HourglassEmpty, "设置倒计时") { onOpenPage(DevicePage.COUNTDOWN) }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        MenuRow(Icons.Outlined.Autorenew, "设置循环定时") { onOpenPage(DevicePage.LOOP) }
                    }
                }
            }

            // ⑤ 设备信息
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
            }

            // ⑥ 通信日志(调试)
            item {
                Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("通信日志", fontWeight = FontWeight.Medium, fontSize = 15.sp)
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
            }
        }
    }
}

// ---------------- 小组件 ----------------

/** 状态区小字行(标签 + 值)。 */
@Composable
private fun StatusLine(label: String, value: String) {
    Row {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Spacer(Modifier.width(10.dp))
        Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
    }
}

/** 工作模式大按钮(横排等分,图标 + 文字,选中蓝底反白;图标为占位,后续替换)。 */
@Composable
private fun ModeButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) Color.White
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            fontSize = 11.sp,
            color = if (selected) Color.White
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

/** 菜单行(图标 + 文案 + 右箭头,MD 列表行 56dp)。 */
@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
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
