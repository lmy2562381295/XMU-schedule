package com.zako.xmuschedule.ui.week

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zako.xmuschedule.data.db.CourseEntity
import com.zako.xmuschedule.ui.edit.CourseEditDialog
import com.zako.xmuschedule.ui.theme.colorForCourse
import java.time.LocalDate

private val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
fun WeekScreen(onGoImport: () -> Unit) {
    val viewModel: WeekViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<CourseEntity?>(null) }
    var prefill by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    Column(Modifier.fillMaxSize().padding(8.dp)) {

        // 周次切换栏
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = viewModel::prevWeek) { Icon(Icons.Filled.ChevronLeft, contentDescription = "上一周") }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(
                    text = state.weekNumber?.let { "第 $it 周" } ?: "未设置学期",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (state.weekNumber != null && !state.isCurrentWeek) {
                    TextButton(onClick = viewModel::backToCurrent) { Text("回到本周") }
                }
            }
            IconButton(onClick = viewModel::nextWeek) { Icon(Icons.Filled.ChevronRight, contentDescription = "下一周") }
        }

        if (state.needsConfig) {
            Card(Modifier.fillMaxWidth().padding(8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("请先在「设置」里选择学期第一周的周一日期", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else if (state.isEmpty) {
            Card(Modifier.fillMaxWidth().padding(8.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("还没有课表数据", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onGoImport) { Text("去导入课表") }
                }
            }
        } else {
            val today = LocalDate.now()
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // 表头：星期 + 日期
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.width(TIME_COL)) {}
                    state.days.forEachIndexed { index, date ->
                        val isToday = date == today && state.isCurrentWeek
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f).background(
                                if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else androidx.compose.ui.graphics.Color.Transparent
                            ),
                        ) {
                            Text(
                                "周${dayNames[index]}",
                                fontSize = 11.sp,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "${date.monthValue}/${date.dayOfMonth}",
                                fontSize = 10.sp,
                                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }

                // 课程网格
                state.sections.forEach { section ->
                    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT)) {
                        Box(Modifier.width(TIME_COL), contentAlignment = Alignment.Center) {
                            val time = state.times[section]?.startTime ?: ""
                            Text(time.removePrefix("0"), fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        (1..7).forEach { day ->
                            val key = day to section
                            val course = state.startCells[key]
                            val isOccupied = key in state.occupied
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .padding(1.dp)
                                    .background(
                                        color = when {
                                            course != null -> colorForCourse(course.name).copy(alpha = 0.85f)
                                            isOccupied -> androidx.compose.ui.graphics.Color.Transparent
                                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        },
                                        shape = RoundedCornerShape(4.dp),
                                    )
                                    .clickable(enabled = !isOccupied || course != null) {
                                        if (course != null) editing = course
                                        else prefill = day to section
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (course != null) {
                                    Column(Modifier.padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            course.name,
                                            fontSize = 9.sp,
                                            lineHeight = 10.sp,
                                            color = androidx.compose.ui.graphics.Color.White,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (course.room.isNotBlank()) {
                                            Text(
                                                course.room,
                                                fontSize = 8.sp,
                                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "点击空格可手动添加课程，点击色块可编辑/删除",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }

    // 编辑对话框
    val editingCourse = editing
    if (editingCourse != null || prefill != null) {
        CourseEditDialog(
            initial = editingCourse,
            prefillDay = prefill?.first,
            prefillSection = prefill?.second,
            onDismiss = { editing = null; prefill = null },
            onSave = { viewModel.saveCourse(it); editing = null; prefill = null },
            onDelete = editingCourse?.let { course ->
                { viewModel.deleteCourse(course.id); editing = null }
            },
        )
    }
}

private val TIME_COL = 44.dp
private val ROW_HEIGHT = 52.dp
