package com.zako.xmuschedule.ui.edit

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zako.xmuschedule.data.db.CourseEntity
import com.zako.xmuschedule.util.SectionsParser
import com.zako.xmuschedule.util.WeeksParser

private val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
fun CourseEditDialog(
    initial: CourseEntity?,
    prefillDay: Int?,
    prefillSection: Int?,
    onDismiss: () -> Unit,
    onSave: (CourseEntity) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var teacher by remember { mutableStateOf(initial?.teacher ?: "") }
    var campus by remember { mutableStateOf(initial?.campus ?: "") }
    var room by remember { mutableStateOf(initial?.room ?: "") }
    var day by remember { mutableStateOf(initial?.dayOfWeek ?: (prefillDay ?: 1)) }
    var startText by remember { mutableStateOf((initial?.startSection ?: prefillSection ?: 1).toString()) }
    var endText by remember { mutableStateOf((initial?.endSection ?: prefillSection ?: 1).toString()) }
    var weeksText by remember { mutableStateOf(initial?.weeksText?.ifBlank { null } ?: "1-20周") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加课程" else "编辑课程") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("课程名（必填）") }, singleLine = true,
                )
                OutlinedTextField(
                    value = teacher, onValueChange = { teacher = it },
                    label = { Text("教师") }, singleLine = true,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dayNames.forEachIndexed { index, label ->
                        FilterChip(
                            selected = day == index + 1,
                            onClick = { day = index + 1 },
                            label = { Text("周$label") },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startText, onValueChange = { startText = it },
                        label = { Text("开始节") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endText, onValueChange = { endText = it },
                        label = { Text("结束节") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = weeksText, onValueChange = { weeksText = it },
                    label = { Text("周次（如 1-16周 / 1-16周(单) / 1,3,5-8周）") }, singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = campus, onValueChange = { campus = it },
                        label = { Text("校区") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = room, onValueChange = { room = it },
                        label = { Text("教室") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val sections = SectionsParser.parse("$startText-$endText") ?: return@TextButton
                if (name.isBlank()) return@TextButton
                val weeks = WeeksParser.parse(weeksText).ifEmpty { (1..20).toSet() }
                onSave(
                    CourseEntity(
                        id = initial?.id ?: 0L,
                        name = name.trim(),
                        teacher = teacher.trim(),
                        dayOfWeek = day,
                        startSection = sections.first,
                        endSection = sections.second,
                        weeksSet = WeeksParser.serialize(weeks),
                        weeksText = weeksText.trim(),
                        campus = campus.trim(),
                        room = room.trim(),
                        source = initial?.source ?: "manual",
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("删除") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
