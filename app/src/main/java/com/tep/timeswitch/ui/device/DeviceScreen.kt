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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.Outlet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import com.tep.timeswitch.ble.BleConnState
import com.tep.timeswitch.protocol.Commands
import com.tep.timeswitch.protocol.TimerCodec
import com.tep.timeswitch.state.DeviceState
import com.tep.timeswitch.ui.components.TimeField
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * 控制主页(开发文档 §6.1):
 * ① 主控区(通断状态展示 + 模式胶囊)② 功能列表(添加定时/设置倒计时)
 * ③ 定时组区(卡片列表/编辑/删除/清空)④ 倒计时区(时分秒 + 执行操作)
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
    onAddTimer: () -> Unit,
    onEditTimer: (Int) -> Unit,
    onDeleteTimer: (Int) -> Unit,
    onClearTimers: () -> Unit,
    onSaveCountdown: (h: Int, m: Int, s: Int, event: Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<Int?>(null) }
    var pendingClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
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
            state = listState,
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
                        // 纯状态展示(与小程序开关图标区一致):通断由模式/定时/倒计时控制,不提供手动开关入口
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (on) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .size(140.dp),
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
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "状态显示,由工作模式与定时控制",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            // ① 模式胶囊(小程序 data-id 0–4)
            item {
                Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("工作模式", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Spacer(Modifier.height(12.dp))
                        ModeRow(
                            listOf(
                                Commands.Mode.OFF to "常关",
                                Commands.Mode.ON to "常开",
                                Commands.Mode.AUTO to "自动",
                            ),
                            deviceState.mode, onMode,
                        )
                        Spacer(Modifier.height(10.dp))
                        ModeRow(
                            listOf(
                                Commands.Mode.COUNTDOWN to "倒计时",
                                Commands.Mode.LOOP to "循环定时",
                            ),
                            deviceState.mode, onMode,
                        )
                    }
                }
            }

            // ② 功能列表
            item {
                Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column {
                        FunctionRow("添加定时", onClick = onAddTimer)
                        FunctionRow("设置倒计时", onClick = {
                            scope.launch { listState.animateScrollToItem(3 + deviceState.timers.size) }
                        })
                    }
                }
            }

            // ③ 定时组区
            item {
                Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "定时 ${deviceState.timers.size}/${deviceState.timerMaxCnt}",
                                fontWeight = FontWeight.Medium, fontSize = 15.sp,
                            )
                            if (deviceState.timers.isNotEmpty()) {
                                TextButton(onClick = { pendingClear = true }) {
                                    Text("清空", fontSize = 13.sp)
                                }
                            }
                        }
                        if (deviceState.timers.isEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "暂无内容,请先添加定时哦 ~",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
            items(deviceState.timers.size) { i ->
                TimerCard(
                    label = "定时${i + 1}",
                    timer = deviceState.timers[i],
                    powerOn = deviceState.power[deviceState.timers[i].switchId] ?: deviceState.mainPower,
                    onClick = { onEditTimer(i) },
                    onDelete = { pendingDelete = i },
                )
            }

            // ④ 倒计时区
            item {
                CountdownCard(
                    current = deviceState.countdown,
                    onSave = onSaveCountdown,
                )
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
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // —— 确认弹窗(删除/清空)——
    pendingDelete?.let { index ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("提示") },
            text = { Text("是否删除此定时") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTimer(index)
                    pendingDelete = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
    if (pendingClear) {
        AlertDialog(
            onDismissRequest = { pendingClear = false },
            title = { Text("提示") },
            text = { Text("是否清空全部定时") },
            confirmButton = {
                TextButton(onClick = {
                    onClearTimers()
                    pendingClear = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pendingClear = false }) { Text("取消") }
            },
        )
    }
}

// ---------------- ④ 倒计时区 ----------------

@Composable
private fun CountdownCard(
    current: com.tep.timeswitch.protocol.CountdownCodec.Countdown?,
    onSave: (h: Int, m: Int, s: Int, event: Boolean) -> Unit,
) {
    var h by remember { mutableIntStateOf(current?.h ?: 0) }
    var m by remember { mutableIntStateOf(current?.m ?: 0) }
    var s by remember { mutableIntStateOf(current?.s ?: 0) }
    var event by remember { mutableStateOf(current?.event ?: false) }

    Card(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("倒计时", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            if (current != null) {
                Text(
                    "当前: %02d:%02d:%02d 后%s".format(
                        current.h, current.m, current.s,
                        if (current.event) "开启" else "关闭",
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
            TimeField(label = "倒计时时长", hour = h, minute = m, second = s) { nh, nm, ns ->
                h = nh; m = nm; s = ns
            }
            Spacer(Modifier.height(8.dp))
            Text("执行操作", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModeChip("开启", event, onClick = { event = true })
                ModeChip("关闭", !event, onClick = { event = false })
            }
            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick = { onSave(h, m, s, event) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存", fontSize = 15.sp)
            }
        }
    }
}

// ---------------- 小组件 ----------------

@Composable
private fun ModeRow(
    modes: List<Pair<Int, String>>,
    current: Int,
    onMode: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        modes.forEach { (id, label) ->
            ModeChip(label, current == id, onClick = { onMode(id) })
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

@Composable
private fun FunctionRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp)
        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
    }
}

/** 定时卡片(参考小程序 timer_item:插座图标随通断着色 + 时间段 + 定时N + 星期摘要 + 删除)。 */
@Composable
private fun TimerCard(
    label: String,
    timer: TimerCodec.TimerGroup,
    powerOn: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Outlet,
                contentDescription = null,
                tint = if (timer.enabled && powerOn) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = timeText(timer.startH, timer.startM, timer.startS, timer.endH, timer.endM, timer.endS),
                    fontSize = 16.sp,
                )
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        TimerCodec.weekStr(timer.weekStart) +
                            if (timer.enabled) "" else " · 已停用",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }
        }
    }
}

private fun timeText(sh: Int, sm: Int, ss: Int, eh: Int, em: Int, es: Int): String =
    "%02d:%02d:%02d ~ %02d:%02d:%02d".format(sh, sm, ss, eh, em, es)

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
