package com.zako.xmuschedule.ui.today

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zako.xmuschedule.ui.theme.colorForCourse

@Composable
fun TodayScreen(
    onGoImport: () -> Unit,
    onGoSettings: () -> Unit,
) {
    val viewModel: TodayViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = if (state.weekNumber != null) "${state.dateText} · 第${state.weekNumber}周" else state.dateText,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

            state.isEmpty -> EmptyCard(onImport = onGoImport, onDemo = viewModel::loadDemo)

            state.needsConfig -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("还没有设置学期开始时间", style = MaterialTheme.typography.titleMedium)
                    Text("设置学期第一周的周一日期后，才能计算当前周次并提醒。", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onGoSettings) { Text("去设置") }
                }
            }

            state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("今天没有课，好好休息～", style = MaterialTheme.typography.bodyLarge)
            }

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.items, key = { it.course.id }) { item ->
                    val color = colorForCourse(item.course.name)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.course.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${item.startText}–${item.endText} · ${item.course.room.ifBlank { "线上课" }}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (item.course.teacher.isNotBlank()) {
                                    Text(item.course.teacher, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (item.isNow) {
                                Text("进行中", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            } else if (item.isPast) {
                                Text("已结束", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCard(onImport: () -> Unit, onDemo: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("导入你的课表", style = MaterialTheme.typography.titleMedium)
            Text(
                "登录厦大统一身份认证后，可一键从教务系统（jw.xmu.edu.cn）导入本学期课表；也可以先载入演示课表体验。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onImport) { Text("登录教务系统导入") }
                OutlinedButton(onClick = onDemo) { Text("载入演示课表") }
            }
        }
    }
}
