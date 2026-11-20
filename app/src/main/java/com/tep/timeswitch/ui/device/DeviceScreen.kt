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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.PowerOff
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tep.timeswitch.DevicePage
import com.tep.timeswitch.ble.BleConnState
import com.tep.timeswitch.state.DeviceState
import kotlinx.coroutines.flow.SharedFlow

/** 连接状态绿色(已连接,与品牌绿一致)。 */
private val ConnectGreen = Color(0xFF10B981)

/**
 * 控制主页(开发文档 §6.1,2026-11-20 重布局):
 * ① 顶栏「时控开关 + 识别码」+ 返回(断开回添加设备页)/刷新(图标按钮)
 * ② 状态区(无卡片底色,仅图标区着色)+ 连接/开关状态色字(已连接=绿,已开启=蓝)
 * ③ 工作模式(无标题,5 大按钮横排,图标占位)
 * ④ 菜单列表(设置分组定时/设置倒计时/设置循环定时/设置 → 独立子页面)
 * 设备信息与日志已迁入「设置」页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    connState: BleConnState,
    deviceState: DeviceState,
    messages: SharedFlow<String>,
    onMode: (Int) -> Unit,
    onRefresh: () -> Unit,
    onBackToScan: () -> Unit,
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
                navigationIcon = {
                    IconButton(onClick = onBackToScan) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, modifier = Modifier.padding(end = 8.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
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
            // ② 状态区(不显示卡片背景,仅图标区着色;连接状态色字)
            item {
                val on = deviceState.mainPower
                val ready = connState is BleConnState.Ready
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 60.dp, bottom = 44.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(120.dp)
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
                            modifier = Modifier.size(60.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (ready) "已连接" else "未连接",
                        fontSize = 12.sp,
                        color = if (ready) ConnectGreen
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                }
            }

            // ③ 工作模式(无标题,5 大按钮横排等分)
            item {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val mode = deviceState.mode
                    ModeButton("常关", Icons.Outlined.PowerOff, mode == 0, { onMode(0) }, Modifier.weight(1f))
                    ModeButton("常开", Icons.Outlined.Power, mode == 1, { onMode(1) }, Modifier.weight(1f))
                    ModeButton("自动", Icons.Outlined.Schedule, mode == 2, { onMode(2) }, Modifier.weight(1f))
                    ModeButton("倒计时", Icons.Outlined.Timer, mode == 3, { onMode(3) }, Modifier.weight(1f))
                    ModeButton("循环", Icons.Outlined.Autorenew, mode == 4, { onMode(4) }, Modifier.weight(1f))
                }
            }

            // ④ 菜单列表(独立子页面入口)
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                ) {
                    Column {
                        MenuRow(Icons.Outlined.EventNote, "设置分组定时") { onOpenPage(DevicePage.TIMERS) }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        MenuRow(Icons.Outlined.HourglassEmpty, "设置倒计时") { onOpenPage(DevicePage.COUNTDOWN) }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        MenuRow(Icons.Outlined.Autorenew, "设置循环定时") { onOpenPage(DevicePage.LOOP) }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        MenuRow(Icons.Outlined.Settings, "设置") { onOpenPage(DevicePage.SETTINGS) }
                    }
                }
            }
        }
    }
}

// ---------------- 小组件 ----------------

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
