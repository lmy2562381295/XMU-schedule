package com.zako.xmuschedule.ui.importing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zako.xmuschedule.data.ImportResult
import com.zako.xmuschedule.data.ScheduleRepository
import com.zako.xmuschedule.data.remote.JwClient
import com.zako.xmuschedule.reminder.ReminderScheduler
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

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    fun semesterCodeGuess(): String = TimeUtils.guessSemesterCode()

    /** 触发导入：先探测会话，无效则进入登录态 */
    fun startImport(code: String?) {
        pendingCode = code
        viewModelScope.launch(Dispatchers.IO) { runImport(code) }
    }

    /** WebView 登录成功后回调 */
    fun onLoginSuccess() {
        viewModelScope.launch(Dispatchers.IO) { runImport(pendingCode) }
    }

    private suspend fun runImport(code: String?) {
        _state.value = ImportUiState.Loading
        val client = JwClient()
        if (!client.isSessionValid()) {
            _state.value = ImportUiState.NeedLogin("请完成厦大统一身份认证登录")
            return
        }
        when (val result = repo.importFromJw(client, code)) {
            is ImportResult.Success -> {
                ReminderScheduler.scheduleWindow(getApplication())
                _state.value = ImportUiState.Done(result.count, result.unrecognized, result.semester)
            }
            is ImportResult.Failure -> _state.value = ImportUiState.Error(result.message)
        }
    }

    fun reset() {
        _state.value = ImportUiState.Idle
    }
}
