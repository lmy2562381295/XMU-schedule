package com.zako.xmuschedule.ui.importing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ImportScreen(onClose: () -> Unit) {
    val viewModel: ImportViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var semesterInput by remember { mutableStateOf("") }
    var studentInput by remember { mutableStateOf("") }
    var showUnrecognized by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // 顶栏
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "关闭") }
            Text("从教务系统导入课表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        // 登录与页面内导入阶段：WebView 保持挂载
        val inWebFlow = state is ImportUiState.NeedLogin || state is ImportUiState.WebImporting
        if (inWebFlow) {
            Text(
                "登录完成后会自动取数并导入，无需其他操作。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Box(Modifier.fillMaxSize()) {
                LoginWebView(
                    onReady = viewModel::onWebReady,
                    onAttach = viewModel::attachWebView,
                    onDetach = viewModel::detachWebView,
                )
                if (state is ImportUiState.WebImporting) {
                    Card(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(
                            Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator()
                            Text("正在从教务系统读取课表…")
                        }
                    }
                }
            }
        } else {
            when (val s = state) {
                is ImportUiState.Done -> Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("导入成功", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("共导入 ${s.count} 条课程安排（学期：${s.semester}）", style = MaterialTheme.typography.bodyMedium)
                            if (s.unrecognized > 0) {
                                Text("有 ${s.unrecognized} 条记录未能识别，可点击下方按钮查看说明。", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("记得在「设置」里核对学期第一周日期与节次时间。", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (s.unrecognized > 0) {
                        OutlinedButton(onClick = { showUnrecognized = true }) { Text("查看未识别记录") }
                    }
                    Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("完成") }
                }

                is ImportUiState.Error -> Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("导入失败", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(s.message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { viewModel.startImport(semesterInput.ifBlank { null }, studentInput.ifBlank { null }) }) { Text("重试") }
                        OutlinedButton(onClick = onClose) { Text("返回") }
                    }
                }

                else -> Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "将打开厦大统一身份认证页面。登录成功后 App 会自动从教务系统拉取本学期课表。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = studentInput,
                        onValueChange = { studentInput = it },
                        label = { Text("学号（必填，课表查询接口需要）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = semesterInput,
                        onValueChange = { semesterInput = it },
                        label = { Text("学期代码（选填，如 ${viewModel.semesterCodeGuess()}）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "提示：填好学号后登录，App 会自动尝试多个学期代码取课表。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Button(
                        onClick = { viewModel.startImport(semesterInput.ifBlank { null }, studentInput.ifBlank { null }) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("下一步：登录并导入") }
                }
            }
        }
    }

    if (showUnrecognized) {
        AlertDialog(
            onDismissRequest = { showUnrecognized = false },
            title = { Text("未识别记录") },
            text = {
                Text(
                    "原始返回已保存到应用私有目录（dumps/），可用 Android Studio 的 Device Explorer 查看，把 JSON 内容发给开发者即可完成字段适配。",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = { TextButton(onClick = { showUnrecognized = false }) { Text("知道了") } },
        )
    }
}
