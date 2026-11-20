package com.tep.timeswitch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tep.timeswitch.ble.BleConnState
import com.tep.timeswitch.ble.ScannedDevice
import com.tep.timeswitch.protocol.ModelTable
import com.tep.timeswitch.protocol.TimerCommands
import com.tep.timeswitch.protocol.TimerCodec
import com.tep.timeswitch.protocol.TimerCodec.TimerGroup
import com.tep.timeswitch.state.AppSettingsStore
import com.tep.timeswitch.state.DeviceRepository
import com.tep.timeswitch.state.DeviceState
import com.tep.timeswitch.state.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 定时编辑草稿(listIndex=-1 表示新增;group.index 为设备槽位号)。 */
data class TimerDraft(
    val listIndex: Int,
    val group: TimerGroup,
) {
    val isNew: Boolean get() = listIndex < 0
}

/** 控制页二级页面(状态路由,BackHandler 返回首页)。 */
enum class DevicePage { HOME, TIMERS, COUNTDOWN, LOOP, SETTINGS }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DeviceRepository(app, viewModelScope)
    private val settings = AppSettingsStore(app)

    /** 主题模式(设置页「主题」,持久化)。 */
    val themeMode: StateFlow<ThemeMode> = settings.themeMode

    val devices: StateFlow<List<ScannedDevice>> = repo.scanner.devices
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val scanning: StateFlow<Boolean> = repo.scanner.scanning
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val connState: StateFlow<BleConnState> = repo.connection.state
    val deviceState: StateFlow<DeviceState> = repo.state
    val frameLog: StateFlow<List<String>> = repo.frameLog
    val messages: SharedFlow<String> = repo.messages

    /** 非空 = 正在编辑定时(显示 TimerEditScreen)。 */
    private val _editing = MutableStateFlow<TimerDraft?>(null)
    val editing: StateFlow<TimerDraft?> = _editing

    /** 控制页当前二级页面(HOME = 首页)。 */
    private val _page = MutableStateFlow(DevicePage.HOME)
    val page: StateFlow<DevicePage> = _page

    fun openPage(p: DevicePage) {
        _page.value = p
    }

    fun backHome() {
        _page.value = DevicePage.HOME
    }

    // ---------------- 设置 ----------------

    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)

    /** 返回添加设备页(断开当前连接并清除设备状态)。 */
    fun backToScan() {
        repo.leaveDevice()
    }

    /** 删除设备:清除记住的设备并断开回扫描页。 */
    fun forgetDevice() {
        repo.forgetDevice()
    }

    // ---------------- 扫描 / 连接 ----------------

    fun toggleScan() {
        if (scanning.value) repo.scanner.stop() else repo.scanner.start()
    }

    fun connect(device: ScannedDevice) = repo.connect(device)

    fun disconnect() = repo.disconnect()

    /** 启动自动重连上次设备(A10)。 */
    fun autoConnectLast() = repo.autoConnectLast()

    // ---------------- 模式 / 状态刷新 ----------------

    // 注:手动开关 UI 入口已移除(主控区改为纯状态展示),协议封装
    // DeviceRepository.setChannelPower/setAllPower 保留备用。

    fun setMode(mode: Int) = viewModelScope.launch { repo.setMode(mode) }

    fun refresh() = viewModelScope.launch { repo.refresh() }

    // ---------------- 定时组(M3) ----------------

    /** 新增定时:上限校验 + 默认值(开发文档 B13:周一~五、08:00→09:00、到点开)。 */
    fun addTimer() {
        val st = repo.state.value
        val index = TimerCommands.nextIndex(st.timers, st.family, st.timerMaxCnt)
        if (index < 0) {
            emit("最大支持 ${st.timerMaxCnt} 组定时")
            return
        }
        _editing.value = TimerDraft(
            listIndex = -1,
            group = TimerGroup(
                index = index, enabled = true, switchId = 0,
                weekStart = 0x1F, weekEnd = 0x1F,
                startH = 8, startM = 0, startS = 0,
                endH = 9, endM = 0, endS = 0,
                event = true,
            ),
        )
    }

    /** 编辑已有定时(点卡片进入)。 */
    fun editTimer(listIndex: Int) {
        val timers = repo.state.value.timers
        val group = timers.getOrNull(listIndex) ?: return
        _editing.value = TimerDraft(listIndex, group)
    }

    fun cancelEdit() {
        _editing.value = null
    }

    /** 保存定时(新增追加/编辑覆盖),写表后自动 3007=02。 */
    fun saveTimer(draft: TimerDraft) = viewModelScope.launch {
        val timers = repo.state.value.timers.toMutableList()
        val changedIndex: Int
        if (draft.isNew) {
            timers.add(draft.group)
            changedIndex = timers.size - 1
        } else {
            timers[draft.listIndex] = draft.group
            changedIndex = draft.listIndex
        }
        _editing.value = null
        runCatching { repo.saveTimer(timers, changedIndex) }
            .onFailure { emit("下发失败:${it.message}") }
    }

    /** 删除定时(弹确认后调用)。 */
    fun deleteTimer(listIndex: Int) = viewModelScope.launch {
        val timers = repo.state.value.timers.toMutableList()
        val deleted = timers.getOrNull(listIndex) ?: return@launch
        timers.removeAt(listIndex)
        runCatching { repo.deleteTimer(deleted, timers) }
            .onFailure { emit("删除失败:${it.message}") }
    }

    /** 清空全部定时(弹确认后调用)。 */
    fun clearTimers() = viewModelScope.launch {
        runCatching { repo.clearTimers() }
            .onFailure { emit("清空失败:${it.message}") }
    }

    // ---------------- 倒计时(M4) ----------------

    /** 下发倒计时(3232)+ 自动 3007=03。 */
    fun saveCountdown(h: Int, m: Int, s: Int, event: Boolean) = viewModelScope.launch {
        runCatching { repo.saveCountdown(h, m, s, event) }
            .onFailure { emit("倒计时下发失败:${it.message}") }
    }

    /** 下发循环定时(3233)+ 自动 3007=04。 */
    fun saveLoopTimer(sh: Int, sm: Int, ss: Int, eh: Int, em: Int, es: Int) = viewModelScope.launch {
        runCatching { repo.saveLoopTimer(sh, sm, ss, eh, em, es) }
            .onFailure { emit("循环定时下发失败:${it.message}") }
    }

    // ---------------- 其他 ----------------

    fun release() {
        repo.scanner.stop()
        repo.disconnect()
    }

    private fun emit(msg: String) = repo.emitMessage(msg)
}
