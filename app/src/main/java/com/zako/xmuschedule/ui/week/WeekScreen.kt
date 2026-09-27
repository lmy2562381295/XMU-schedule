package com.zako.xmuschedule.ui.week

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
private val TIME_COL = 44.dp
private val ROW_HEIGHT = 52.dp

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
            val gridHeight = ROW_HEIGHT * state.sections.size

            Column(Modifier.verticalScroll(rememberScrollState())) {
                // 表头：星期 + 日期
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.width(TIME_COL)) {}
                    state.days.forEachIndexed { index, date ->
                        val isToday = date == today && state.isCurrentWeek
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f).background(
                                if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent
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

                // 网格主体：左侧时间轴 + 每天一列（课程块纵向跨满所占据的节次）
                Row(Modifier.fillMaxWidth().height(gridHeight)) {
                    Column(Modifier.width(TIME_COL)) {
                        state.sections.forEach { section ->
                            Box(Modifier.height(ROW_HEIGHT), contentAlignment = Alignment.Center) {
                                Text(
                                    state.times[section]?.startTime?.removePrefix("0").orEmpty(),
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                    state.days.forEachIndexed { index, date ->
                        val dow = date.dayOfWeek.value
                        val isToday = date == today && state.isCurrentWeek
                        Column(
                            Modifier
                                .weight(1f)
                                .height(gridHeight)
                                .background(
                                    if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else Color.Transparent
                                ),
                        ) {
                            var cursor = 1
                            state.dayCourses[dow].orEmpty().forEach { course ->
                                val gap = (course.startSection - cursor).coerceAtLeast(0)
                                if (gap > 0) {
                                    GapBlock(
                                        height = ROW_HEIGHT * gap,
                                        firstSection = cursor,
                                        onPick = { section -> prefill = dow to section },
                                    )
                                }
                                val span = (course.endSection - course.startSection + 1).coerceAtLeast(1)
                                CourseBlock(
                                    course = course,
                                    height = ROW_HEIGHT * span,
                                    onClick = { editing = course },
                                )
                                cursor = course.endSection + 1
                            }
                            if (cursor <= state.sections.size) {
                                GapBlock(
                                    height = ROW_HEIGHT * (state.sections.size - cursor + 1),
                                    firstSection = cursor,
                                    onPick = { section -> prefill = dow to section },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "点击空白处可手动添加课程，点击课程块可编辑/删除",
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

/** 空闲格子：按点击位置换算出节次，用于手动添加课程 */
@Composable
private fun GapBlock(height: androidx.compose.ui.unit.Dp, firstSection: Int, onPick: (Int) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .padding(1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .pointerInput(firstSection) {
                detectTapGestures { offset ->
                    val picked = firstSection + (offset.y / ROW_HEIGHT.value).toInt()
                    onPick(picked)
                }
            },
    )
}

/** 课程块：跨满所占据的节次，展示课程名、教室、老师 */
@Composable
private fun CourseBlock(course: CourseEntity, height: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val color = colorForCourse(course.name)
    Column(
        Modifier
            .fillMaxWidth()
            .height(height)
            .padding(1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.88f))
            .clickable(onClick = onClick)
            .padding(4.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            course.name,
            fontSize = 9.sp,
            lineHeight = 10.sp,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
        )
        if (course.room.isNotBlank()) {
            Text(
                course.room,
                fontSize = 8.sp,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (course.teacher.isNotBlank()) {
            Text(
                course.teacher,
                fontSize = 8.sp,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
