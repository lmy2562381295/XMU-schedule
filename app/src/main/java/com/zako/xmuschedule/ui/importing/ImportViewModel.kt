package com.zako.xmuschedule.ui.importing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zako.xmuschedule.data.ImportResult
import com.zako.xmuschedule.data.ScheduleRepository
import com.zako.xmuschedule.data.remote.JwClient
import com.zako.xmuschedule.reminder.ReminderScheduler
import com.zako.xmuschedule.util.AppLog
import com.zako.xmuschedule.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Loading : ImportUiState
    data class NeedLogin(val hint: String) : ImportUiState
    data class Done(val count: Int, val unrecognized: Int, val semester: String) : ImportUiState
    data class Error(val message: String) : ImportUiState
}

class ImportViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ScheduleRepository(app)
    private var pendingCode: String? = null

    /** 连续进入登录页的次数：统一身份认证登录态尚存时会自动跳回，用上限阻断弹跳循环 */
    private var loginAttempts = 0

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    fun semesterCodeGuess(): String = TimeUtils.guessSemesterCode()

    fun startImport(code: String?) {
        pendingCode = code
        loginAttempts = 0
        transition(ImportUiState.Loading, "startImport")
        viewModelScope.launch(Dispatchers.IO) { runImport(code) }
    }

    /** WebView 登录成功后回调 */
    fun onLoginSuccess() {
        transition(ImportUiState.Loading, "onLoginSuccess 第${loginAttempts + 1}次尝试")
        viewModelScope.launch(Dispatchers.IO) { runImport(pendingCode) }
    }

    private fun transition(s: ImportUiState, reason: String) {
        AppLog.event(getApplication(), "import", "state -> ${s::class.simpleName} ($reason)")
        _state.value = s
    }

    private suspend fun runImport(code: String?) {
        val app = getApplication<Application>()
        try {
            val client = JwClient(app)
            val valid = client.isSessionValid()
            AppLog.event(app, "import", "isSessionValid=$valid")
            if (!valid) {
                loginAttempts += 1
                if (loginAttempts >= 3) {
                    transition(
                        ImportUiState.Error(
                            "已连续多次登录但教务系统会话仍无效，为避免反复跳转已停止自动尝试。" +
                                "可点击重试再来一次；若反复出现，请把启动时的运行日志发给开发者。"
                        ),
                        "loginAttempts>=3",
                    )
                    return
                }
                transition(ImportUiState.NeedLogin("请完成厦大统一身份认证登录"), "need login")
                return
            }
            when (val result = repo.importFromJw(client, code)) {
                is ImportResult.Success -> {
                    ReminderScheduler.scheduleWindow(app)
                    transition(ImportUiState.Done(result.count, result.unrecognized, result.semester), "success")
                }
                is ImportResult.Failure -> transition(ImportUiState.Error(result.message), "failure: ${result.message.take(80)}")
            }
        } catch (t: Throwable) {
            AppLog.error(app, "import", t)
            transition(
                ImportUiState.Error("导入过程出现异常：${t.javaClass.simpleName}: ${t.message}"),
                "exception",
            )
        }
    }

    fun reset() {
        loginAttempts = 0
        transition(ImportUiState.Idle, "reset")
    }
}
