package com.zako.xmuschedule.ui.importing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ImportScreen(onClose: () -> Unit) {
    val viewModel: ImportViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var semesterInput by remember { mutableStateOf("") }
    var showUnrecognized by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // 顶栏
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "关闭") }
            Text("从教务系统导入课表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        when (val s = state) {
            is ImportUiState.NeedLogin -> {
                Text(
                    s.hint,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Text(
                    "登录完成后会自动跳回本页并导入，无需其他操作。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                LoginWebView(
                    onSuccess = viewModel::onLoginSuccess,
                    onPortalLanded = viewModel::onPortalLanded,
                )
            }

            ImportUiState.Loading -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("正在拉取课表数据…")
            }

            is ImportUiState.Done -> Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("导入成功", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("共导入 ${s.count} 条课程安排（学期：${s.semester}）", style = MaterialTheme.typography.bodyMedium)
                        if (s.unrecognized > 0) {
                            Text("有 ${s.unrecognized} 条记录未能识别，可点击下方按钮查看原文。", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("记得在「设置」里核对学期第一周日期与节次时间。", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (s.unrecognized > 0) {
                    OutlinedButton(onClick = { showUnrecognized = true }) { Text("查看未识别记录") }
                }
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("完成") }
            }

            is ImportUiState.Error -> Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("导入失败", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(s.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { viewModel.startImport(semesterInput.ifBlank { null }) }) { Text("重试") }
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
                    value = semesterInput,
                    onValueChange = { semesterInput = it },
                    label = { Text("学期代码（选填，如 ${viewModel.semesterCodeGuess()}）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "提示：留空时 App 会自动尝试获取当前学期；若导入结果为空或其他学期，可手动填写。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Button(
                    onClick = { viewModel.startImport(semesterInput.ifBlank { null }) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("下一步：登录并导入") }
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
