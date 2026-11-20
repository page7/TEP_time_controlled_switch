package com.tep.timeswitch.ui.timer

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Outlet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tep.timeswitch.protocol.TimerCodec
import com.tep.timeswitch.state.DeviceState
import kotlinx.coroutines.flow.SharedFlow

/**
 * 设置分组定时页(定时组列表):查看/编辑/删除/清空,底部「添加定时」满格按钮。
 * 卡片参考小程序 cycle.wxml timer_item(插座图标随通断着色 + 时间段 + 定时N + 星期摘要);
 * 点卡片进 TimerEditScreen 编辑,增删改即时下发。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerListScreen(
    deviceState: DeviceState,
    messages: SharedFlow<String>,
    onAddTimer: () -> Unit,
    onEditTimer: (Int) -> Unit,
    onDeleteTimer: (Int) -> Unit,
    onClearTimers: () -> Unit,
    onBack: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<Int?>(null) }
    var pendingClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        messages.collect { snackbar.showSnackbar(it) }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("设置分组定时", fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (deviceState.timers.isNotEmpty()) {
                        TextButton(onClick = { pendingClear = true }) {
                            Text("清空", fontSize = 14.sp)
                        }
                    }
                },
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = onAddTimer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("添加定时", fontSize = 16.sp)
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "定时 ${deviceState.timers.size}/${deviceState.timerMaxCnt}",
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                    )
                }
            }
            if (deviceState.timers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "暂无内容,请先添加定时哦 ~",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
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

/** 定时卡片(插座图标随通断着色 + 时间段 + 定时N + 星期摘要 + 删除;点卡片=编辑)。 */
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
                    text = "%02d:%02d:%02d ~ %02d:%02d:%02d".format(
                        timer.startH, timer.startM, timer.startS,
                        timer.endH, timer.endM, timer.endS,
                    ),
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
