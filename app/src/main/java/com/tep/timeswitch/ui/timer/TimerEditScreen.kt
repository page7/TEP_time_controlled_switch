package com.tep.timeswitch.ui.timer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tep.timeswitch.TimerDraft
import com.tep.timeswitch.protocol.ModelTable
import com.tep.timeswitch.protocol.TimerCodec
import com.tep.timeswitch.ui.components.TimeField

/**
 * 定时编辑页(参考小程序 cycle-edit:开启/关闭时间 + 重复时间 7 圆钮 + 保存)。
 * 与小程序差异:① 到点动作开/关可选(协议 EVENT 字节)② BATCH 族显示启用开关(EN 字节)。
 * LEGACY 族时间只到分(无秒字段)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerEditScreen(
    draft: TimerDraft,
    family: ModelTable.Family,
    onSave: (TimerDraft) -> Unit,
    onBack: () -> Unit,
) {
    var group by remember { mutableStateOf(draft.group) }
    val showSeconds = family != ModelTable.Family.LEGACY
    // 显示序 [日 一 二 三 四 五 六] → 位图 bit 序 [6,0,1,2,3,4,5]
    val dayOrder = intArrayOf(6, 0, 1, 2, 3, 4, 5)
    val dayLabels = arrayOf("日", "一", "二", "三", "四", "五", "六")

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (draft.isNew) "添加定时" else "编辑定时", fontSize = 17.sp) },
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
                .padding(horizontal = 16.dp),
        ) {
            // —— 时间 ——
            Card {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    TimeField(
                        label = "开启时间",
                        hour = group.startH, minute = group.startM, second = group.startS,
                        showSeconds = showSeconds,
                    ) { h, m, s -> group = group.copy(startH = h, startM = m, startS = s) }
                    TimeField(
                        label = "关闭时间",
                        hour = group.endH, minute = group.endM, second = group.endS,
                        showSeconds = showSeconds,
                    ) { h, m, s -> group = group.copy(endH = h, endM = m, endS = s) }
                }
            }

            Spacer(Modifier.height(12.dp))

            // —— 到点动作(EVENT:开启时间点的动作,关闭时间点动作相反)——
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("到点动作", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "开启时间点执行的动作,关闭时间点自动执行相反动作",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SelectChip("开启", group.event, onClick = { group = group.copy(event = true) })
                        SelectChip("关闭", !group.event, onClick = { group = group.copy(event = false) })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // —— 重复时间(7 圆钮,多选)——
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("重复时间", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        dayOrder.forEachIndexed { i, bit ->
                            val on = group.weekStart and (1 shl bit) != 0
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (on) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                    .clickable {
                                        val week = group.weekStart xor (1 shl bit)
                                        group = group.copy(weekStart = week, weekEnd = week)
                                    },
                            ) {
                                Text(
                                    text = dayLabels[i],
                                    fontSize = 14.sp,
                                    color = if (on) Color.White
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = TimerCodec.weekStr(group.weekStart),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }

            // —— 启用开关(仅 BATCH 族,EN 字节生效)——
            if (family == ModelTable.Family.BATCH) {
                Spacer(Modifier.height(12.dp))
                Card {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("启用", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text(
                                "停用后设备跳过该组定时",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        }
                        Switch(
                            checked = group.enabled,
                            onCheckedChange = { group = group.copy(enabled = it) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { onSave(draft.copy(group = group)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text("保存", fontSize = 16.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
            .padding(horizontal = 22.dp, vertical = 8.dp),
    )
}
