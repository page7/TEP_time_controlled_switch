package com.tep.timeswitch.ui.loop

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tep.timeswitch.protocol.LoopTimerCodec
import com.tep.timeswitch.ui.components.TimeField
import kotlinx.coroutines.flow.SharedFlow

/**
 * 设置循环定时页(开发文档 §6.1):开启时长 + 关闭时长 + 底部「保存」满格按钮。
 * 打开即以设备存储值初始化表单(无单独"当前值"展示);
 * 保存 → 3233 单组下发(AGENT P9,EVENT 恒发 00 与原厂一致)+ 自动 3007=04。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoopTimerScreen(
    loopTimer: List<LoopTimerCodec.LoopTimer>,
    messages: SharedFlow<String>,
    onSave: (sh: Int, sm: Int, ss: Int, eh: Int, em: Int, es: Int) -> Unit,
    onBack: () -> Unit,
) {
    val current = loopTimer.firstOrNull()
    var sh by remember { mutableIntStateOf(current?.startH ?: 0) }
    var sm by remember { mutableIntStateOf(current?.startM ?: 0) }
    var ss by remember { mutableIntStateOf(current?.startS ?: 0) }
    var eh by remember { mutableIntStateOf(current?.endH ?: 0) }
    var em by remember { mutableIntStateOf(current?.endM ?: 0) }
    var es by remember { mutableIntStateOf(current?.endS ?: 0) }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        messages.collect { snackbar.showSnackbar(it) }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("设置循环定时", fontSize = 17.sp) },
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
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = { onSave(sh, sm, ss, eh, em, es) },
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    TimeField(
                        label = "开启时长",
                        hour = sh, minute = sm, second = ss,
                    ) { h, m, s -> sh = h; sm = m; ss = s }
                    TimeField(
                        label = "关闭时长",
                        hour = eh, minute = em, second = es,
                    ) { h, m, s -> eh = h; em = m; es = s }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("说明", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "开启时长与关闭时长循环交替通断,保存后自动切换到「循环定时」模式生效。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}
