package com.tep.timeswitch.ui.countdown

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tep.timeswitch.protocol.CountdownCodec
import com.tep.timeswitch.ui.components.TimeField
import kotlinx.coroutines.flow.SharedFlow

/**
 * 设置倒计时页(开发文档 §6.1):倒计时时长 + 执行操作单选,底部「保存」满格按钮。
 * 保存 → 3232 下发 + 自动 3007=03;倒计时是一次性的,切模式后开始计时(§8.1)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownScreen(
    countdown: CountdownCodec.Countdown?,
    messages: SharedFlow<String>,
    onSave: (h: Int, m: Int, s: Int, event: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var h by remember { mutableIntStateOf(countdown?.h ?: 0) }
    var m by remember { mutableIntStateOf(countdown?.m ?: 0) }
    var s by remember { mutableIntStateOf(countdown?.s ?: 0) }
    var event by remember { mutableStateOf(countdown?.event ?: false) }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        messages.collect { snackbar.showSnackbar(it) }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("设置倒计时", fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = { onSave(h, m, s, event) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("保存", fontSize = 16.sp)
                }
            }
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
            if (countdown != null) {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("当前倒计时", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "%02d:%02d:%02d 后%s".format(
                                countdown.h, countdown.m, countdown.s,
                                if (countdown.event) "开启" else "关闭",
                            ),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            Card {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    TimeField(label = "倒计时时长", hour = h, minute = m, second = s) { nh, nm, ns ->
                        h = nh; m = nm; s = ns
                    }
                }
            }

            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("执行操作", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "倒计时结束时执行的动作",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(4.dp))
                    RadioRow("开启", event) { event = true }
                    RadioRow("关闭", !event) { event = false }
                }
            }
        }
    }
}

/** MD 单选行(RadioButton + 文案)。 */
@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
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
