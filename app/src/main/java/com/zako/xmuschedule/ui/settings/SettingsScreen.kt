package com.zako.xmuschedule.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zako.xmuschedule.data.db.PeriodTimeEntity
import java.time.LocalDate

private val leadOptions = listOf(5, 10, 15, 30)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onGoImport: () -> Unit) {
    val viewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showDatePicker by remember { mutableStateOf(false) }
    var editingPeriod by remember { mutableStateOf<PeriodTimeEntity?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var lead by remember { mutableStateOf(viewModel.leadMinutes()) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        // ── 学期设置 ─────────────────────────────
        Text("学期", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("第一周周一", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = state.firstWeekMondayEpochDay?.let {
                                LocalDate.ofEpochDay(it).toString()
                            } ?: "未设置（必设，否则无法计算周次）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Button(onClick = { showDatePicker = true }) { Text("选择日期") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("学期总周数：${state.totalWeeks}", modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.changeTotalWeeks(-1) }) { Text("−", style = MaterialTheme.typography.titleMedium) }
                    IconButton(onClick = { viewModel.changeTotalWeeks(+1) }) { Text("+", style = MaterialTheme.typography.titleMedium) }
                }
                if (state.semesterCode.isNotBlank()) {
                    Text("当前学期代码：${state.semesterCode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        // ── 提醒设置 ─────────────────────────────
        Text("提醒", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("提前提醒时间", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    leadOptions.forEach { option ->
                        FilterChip(
                            selected = lead == option,
                            onClick = { lead = option; viewModel.setLeadMinutes(option) },
                            label = { Text("${option}分钟") },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::testNotification) { Text("发送测试通知") }
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < 33
                    if (!granted) {
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= 33) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }) { Text("开启通知权限") }
                    }
                }
                if (!viewModel.canScheduleExact()) {
                    Text(
                        "未开启“闹钟和提醒”特殊权限，提醒可能不准时。点下方按钮去系统设置开启。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= 31) {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                            )
                        }
                    }) { Text("开启精确提醒") }
                }
            }
        }

        // ── 节次时间 ─────────────────────────────
        Text("节次时间（以学校作息为准，可逐节修改）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.periods.forEach { period ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("第${period.section}节", Modifier.width(64.dp), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${period.startTime} – ${period.endTime}",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { editingPeriod = period }) { Text("修改") }
                    }
                }
                if (state.periods.isEmpty()) Text("尚未初始化，返回今日页稍候即可", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = viewModel::resetPeriods) { Text("恢复默认节次表") }
            }
        }

        // ── 数据 ─────────────────────────────
        Text("数据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onGoImport) { Text("重新导入课表") }
                    OutlinedButton(onClick = viewModel::loadDemo) { Text("载入演示课表") }
                }
                OutlinedButton(onClick = { showClearConfirm = true }) { Text("清空全部课程数据") }
            }
        }

        Text(
            "所有数据仅保存在手机本地，不上传任何服务器。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }

    // 日期选择
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (state.firstWeekMondayEpochDay ?: LocalDate.now().toEpochDay()) * 86_400_000L
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.setFirstWeekMonday(it / 86_400_000L) }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) {
            DatePicker(state = pickerState)
        }
    }

    // 节次时间编辑
    if (editingPeriod != null) {
        val period = editingPeriod!!
        val startParts = period.startTime.split(":")
        val endParts = period.endTime.split(":")
        val startState = rememberTimePickerState(
            initialHour = startParts.getOrNull(0)?.toIntOrNull() ?: 8,
            initialMinute = startParts.getOrNull(1)?.toIntOrNull() ?: 0,
            is24Hour = true,
        )
        val endState = rememberTimePickerState(
            initialHour = endParts.getOrNull(0)?.toIntOrNull() ?: 8,
            initialMinute = endParts.getOrNull(1)?.toIntOrNull() ?: 45,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { editingPeriod = null },
            title = { Text("第${period.section}节时间") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("上课时间", style = MaterialTheme.typography.bodySmall)
                    TimePicker(state = startState)
                    Text("下课时间", style = MaterialTheme.typography.bodySmall)
                    TimePicker(state = endState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    fun fmt(h: Int, m: Int) = String.format("%02d:%02d", h, m)
                    viewModel.updatePeriod(period.section, fmt(startState.hour, startState.minute), fmt(endState.hour, endState.minute))
                    editingPeriod = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingPeriod = null }) { Text("取消") } },
        )
    }

    // 清空确认
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空全部课程？") },
            text = { Text("将删除所有课程与学期配置，提醒一并取消。此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); showClearConfirm = false }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消") } },
        )
    }
}
