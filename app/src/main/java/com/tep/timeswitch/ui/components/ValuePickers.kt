package com.tep.timeswitch.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 时间字段(参考小程序 cycle-edit 滚轮选择器,改为点段弹列表选择)。
 * LEGACY 族无秒,showSeconds=false 时只显示 时:分。
 */
@Composable
fun TimeField(
    label: String,
    hour: Int,
    minute: Int,
    second: Int,
    showSeconds: Boolean = true,
    onChange: (h: Int, m: Int, s: Int) -> Unit,
) {
    var picking by remember { mutableStateOf<Int?>(null) }   // 0=时 1=分 2=秒

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimeSegment(hour, 2) { picking = 0 }
            Colon()
            TimeSegment(minute, 2) { picking = 1 }
            if (showSeconds) {
                Colon()
                TimeSegment(second, 2) { picking = 2 }
            }
        }
    }

    when (picking) {
        0 -> ValuePickerDialog("选择时", hour, 0..23, { onChange(it, minute, second); picking = null }, { picking = null })
        1 -> ValuePickerDialog("选择分", minute, 0..59, { onChange(hour, it, second); picking = null }, { picking = null })
        2 -> ValuePickerDialog("选择秒", second, 0..59, { onChange(hour, minute, it); picking = null }, { picking = null })
    }
}

@Composable
private fun Colon() {
    Text(":", fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun TimeSegment(value: Int, pad: Int, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = value.toString().padStart(pad, '0'),
            fontSize = 20.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** 数值选择弹窗:列表点选(滚轮的 Android 简化替代)。 */
@Composable
fun ValuePickerDialog(
    title: String,
    value: Int,
    range: IntRange,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.height(320.dp)) {
                items(range.toList()) { v ->
                    val selected = v == value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(v) }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = v.toString().padStart(2, '0'),
                            fontSize = 17.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        if (selected) {
                            Text("✓", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
