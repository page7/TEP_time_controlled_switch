# AGENT 开发日志(强制维护)

> **本文件是本项目的唯一开发日志,由 AI 开发代理维护。**
> **规则:每一次修改(代码、文档、配置、脚本)都必须在 [§5 变更日志](#5-变更日志) 追加一条记录,不允许跳过。**
> 修改内容与真机行为冲突时,以真机为准,并回写本文件与协议/开发文档。

---

## 1. 项目信息

| 项 | 值 |
|---|---|
| 项目 | TEP 时控开关 Android App |
| 工作目录 | `D:\app\TEP_time_controlled_switch` |
| 目标 | BLE 本地控制时控开关(类型 4951,34 个 productId),替代原厂小程序 |
| 技术栈 | Kotlin + Jetpack Compose + 原生 BLE API,MVVM,仅 Android |
| 参考文档 | `doc/时控开关-设备与协议说明书.md`(协议)、`doc/TEP时控开关-Android开发文档.md`(设计)、`doc/miniprogram/`(UI/逻辑参考) |

## 2. 开发规范

1. **变更即记录**:每次修改在 §5 追加条目:`日期 | 类别 | 文件 | 改了什么 | 为什么`。
2. **协议以说明书为准**:帧格式/指令码/字段偏移不准臆造;实现前必须能指出说明书章节号;说明书未覆盖的细节在 AGENT「协议补充」登记(带源码证据)。
3. **先单测后真机**:帧编解码、定时组 Codec、收包状态机必须有 JVM 单元测试,测试向量取自说明书 §5.3/§7.4 的示例 hex。
4. **一次提交一个主题**:变更日志按主题分组,不混无关改动。
5. **UI 参考小程序但不照抄**:布局/文案取自 `socket`/`cycle`/`cycle-edit`,交互按 Android 习惯调整,视觉后续统一改。
6. **里程碑推进**:按开发文档 §8 的 M1→M4 顺序,完成即在 §4 勾选并记录验收结果。

## 3. 环境记录

| 项 | 值 | 备注 |
|---|---|---|
| Android SDK | `D:\Program\Android SDK` | platforms 28–35,build-tools 30–36,adb ✅ |
| JDK | `.tools/jdk-17`(Temurin 17,自管理) | 系统仅有 JDK 8/11,AGP 8.x 需 17;通过 `gradle.properties` 的 `org.gradle.java.home` 指定 |
| Gradle | Wrapper 8.10.x(自动生成) | 首次构建自动下载发行版 |
| AGP / Kotlin | 8.7.x / 2.0.x + Compose 插件 | compileSdk 35 / minSdk 24 / targetSdk 35 |
| 真机 | ✅ 已提供(M1 验收通过) | BLE 功能验收进行中 |

## 4. 里程碑进度

| 里程碑 | 内容 | 状态 | 验收 |
|---|---|---|---|
| M0 | 规划文档 + AGENT 日志 | ✅ 完成(2026-11-19) | 开发文档 v1.0 已评审 |
| M1 | 工程骨架 + BLE 链路(扫描/连接/收发帧/握手) | ✅ 完成(2026-11-19) | 真机验收通过:用户确认可发现并连接设备 |
| M2 | 主控(开关/模式/状态渲染/断线重连) | 🚧 代码完成,待真机验收 | 继电器可控、自动重连 |
| M3 | 定时组三族下发 | 🚧 代码完成,待真机验收 | 设备到点动作正确 |
| M4 | 倒计时 + 收尾 | 🚧 代码完成,待真机验收 | 功能清单全勾 |

## 5. 变更日志

> 格式:`HH:MM [类别] 文件 — 改了什么(为什么)`。类别:文档/协议/构建/BLE/协议层/UI/测试/修复。

### 2026-11-19

- `09:00 [文档]` doc/时控开关-Android开发文档.md — 创建开发文档 v1.0(架构/BLE/协议/UI/里程碑/风险;UI 结构来自小程序 socket/cycle/cycle-edit 源码提取)。
- `09:30 [文档]` doc/TEP时控开关-Android开发文档.md — 依据小程序源码实证修订 UI 章节:模式 6 键(data-id 0–5,补 mode=05 日落定时)、定时卡片布局、编辑页默认值、倒计时 UI 结构;附录 B 新增 B10–B15。
- `10:00 [协议]` AGENT.md — 新增协议补充 §6:P1 广播偏移语义(getDeviceInfoByAdv 实证:hex 前 4 字符=type、4–6=encode、6–18=MAC、18+=productId ASCII)。Android 解析按此执行。
- `10:00 [文档]` AGENT.md — 创建本日志文件,建立「每次修改必须记录」的维护规则。
- `10:10 [构建]` .tools/、gradle.properties、settings.gradle.kts、build.gradle.kts、app/build.gradle.kts、gradle/wrapper/*、gradlew(.bat)、.gitignore — 创建 Android 工程骨架(AGP 8.7.3 + Kotlin 2.0.21 + Compose BOM 2024.10.01;JDK 17 自管理于 .tools/jdk-17,Temurin 17.0.21;Gradle Wrapper 用本地缓存的 8.11.1,因 8.10.2 下载超时)。
- `10:20 [构建]` app/src/main/AndroidManifest.xml、res/values/{strings,themes}.xml、proguard-rules.pro — BLE 权限(31+ 蓝牙新权限 neverForLocation,≤30 旧权限+定位)、单 Activity、Application 注册。
- `10:30 [协议层]` protocol/Hex.kt、Frame.kt、Commands.kt — hex 工具(含 ASCII 时间编解码)、FE01 组帧/解帧、指令码与 3005 TYPE/Mode 常量。
- `10:40 [协议层]` protocol/ModelTable.kt — 34 型号表(族/中文名/定时上限 16–108/计量标记);未知型号兜底 GROUP 族(P2 策略)。
- `10:50 [协议层]` protocol/TimerCodec.kt、StatusParser.kt、FrameDecoder.kt — 定时组三族编解码(含 EN 语义/BATCH 占位包/清空)、3005 条目式解析(越界即停)、5012 严格逐段解析、收包状态机(一包多帧/帧头错位/噪声前缀)。
- `11:00 [BLE]` ble/ScannedDevice.kt、BleScanner.kt、BleConnection.kt — 广播解析(偏移 P1)、扫描过滤(P2)、显示识别码算法;连接/9AF4 服务发现/MTU 185/CCCD notify/串行写队列(MTU-3 分片、写超时 3s、单片重试 2 次)/READY 后 500ms 稳定延时。
- `11:10 [UI]` state/DeviceState.kt、DeviceRepository.kt — 状态单一数据源;握手时序(5011→100ms→5001→500ms→3004,每次连接都对时);帧日志;控制指令封装(3221+3225 兜底/3007/3004/3229);意外断开 5s 自动重连。
- `11:20 [UI]` MainActivity.kt、MainViewModel.kt、TimeSwitchApp.kt、ui/theme/Theme.kt、ui/scan/ScanScreen.kt、ui/device/DeviceScreen.kt — 权限引导 + 蓝牙开启引导;扫描页(设备卡片=型号+识别码+productId+RSSI);控制页(①主控电源 ②模式胶囊 0–4 ③设备信息 ④通信日志,M1 验收可视);品牌色 #1E86E0(参考小程序)。
- `11:30 [测试]` test/…/FrameTest.kt、TimerCodecTest.kt、StatusParserTest.kt — 41 用例:组帧向量(§5.3 六条)、定时组 §7.4 双向 hex、收包状态机 6 类、星期位图 getWeekStr 等价、ASCII 时间、3005/5012 解析。
- `11:40 [修复]` protocol/TimerCodec.kt — **单测抓到真 bug**:组 LEN 少算自身(即说明书 §11.2 坑 3),`encode/encodeSingleDisabled` 修正为 `body.size+1`;§7.4 示例帧断言通过验证修复正确。
- `11:45 [修复]` test/…/TimerCodecTest.kt、StatusParserTest.kt — 修正测试自身笔误:EN 字节偏移([3] 非 [4])、BATCH 占位包长度 19、3005 合成帧条目 LEN(含自身)。
- `11:50 [构建]` build.gradle.kts — AGP 8.7.2 → 8.7.3(本地缓存版本,避免重复下载)。
- `12:00 [验证]` 全量构建 — `testDebugUnitTest` 41/41 通过;`assembleDebug` BUILD SUCCESSFUL(35 tasks)。M1 代码完成,待真机验收。
- `12:30 [验证]` 模拟器验收(KativaTest AVD,Android 16/API 36) — APK 安装/启动成功;权限弹窗(BLUETOOTH_SCAN/CONNECT)正常弹出并授权;扫描页 UI 正确(标题「时控开关」/按钮「扫描设备」/空态「发现设备」);点击扫描→按钮切换「停止扫描」、状态「正在搜索附近的时控开关…」、logcat `scan started`;停止→回退初始态 `scan stopped`;全流程无崩溃无 ANR。⚠️ 模拟器虚拟蓝牙扫不到真实设备,设备列表为空属预期;「发现真实设备+连接+5012/3005」仍待真机验收。
- `14:00 [协议]` AGENT.md §6 — 新增协议补充 P5(循环定时 3233 可经 3004/3005 读回,回传布局带源码证据)、P6(协议无读设备时间指令,60 个上报 case 枚举证实;设备 RTC 只写不读,App 靠每次连接 5001 对时保证准点)。调研结论见对话 2026-11-19「芯片时间记录」问答。
- `15:00 [测试/验收]` AGENT.md — M1 真机验收通过(用户确认扫描可发现设备并连接成功),里程碑 M1 勾选;M2–M4 开发批量启动。
- `15:10 [协议层]` protocol/CountdownCodec.kt — 新增倒计时 3232 编解码(M4):ARG = `01|0C|IDX|EN|SWID|00|HH MM SS|EVENT`,时分秒 ASCII=倒计时时长;向量取自说明书 §8.1 示例帧(cycle.js sendCountdown/socket.js case 3232 实证,详见 §6 P7)。
- `15:10 [协议层]` protocol/TimerCommands.kt — 新增三族定时下发策略纯函数(M3):planWrite(GROUP 10 组/包、LEGACY 单包无秒、BATCH 1 组/包)/planDelete(BATCH EN=00 单组重发、其余整表重发+重排 IDX、删至空发 00)/planClear(BATCH 逐槽补 EN=00)/nextIndex(缺口扫描,BATCH 复用 en=false 槽位)——全部对应 §7.5 与 cycle.js 实证,便于单测。
- `15:15 [协议层]` protocol/LoopTimerCodec.kt、StatusParser.kt — 3233 循环定时只读解码(P5 要求 M4+ 实现;按组 LEN 前移、EVENT 取组末字节,兼容 §8.2 的 19B 与 P5 的 20B 记载,详见 §6 P8);3005 解析新增 3232 倒计时/3233 循环定时条目。
- `15:20 [UI]` state/DeviceState.kt、DeviceRepository.kt — 状态模型加 countdown(协议层模型)/loopTimer(只读)/timerSlotCount(BATCH 清空跨度);仓库重写:断线重连改指数退避 1/2/4/8/15s×5(§4.5,原固定 5s);定时三族下发+倒计时 3232(写后自动 3007=02/03,风险 #4 与 B9);所有写指令乐观回写+防抖 3004 校准(§3.3);connect 即按广播 productId 定族(风险 #11);保存成功/失败消息流。
- `15:25 [UI]` state/LastDeviceStore.kt、MainViewModel.kt、MainActivity.kt — A10 记住上次设备(SharedPreferences 最小实现,未引 DataStore 以保持最小依赖)并启动自动重连;ViewModel 增定时草稿(TimerDraft)/新增默认值(B13:周一~五 08:00→09:00 到点开)/上限校验「最大支持 N 组定时」/删除/清空/倒计时动作;MainActivity 增 TimerEdit 路由,权限与开蓝牙后 autoConnectLast。
- `15:30 [UI]` ui/device/DeviceScreen.kt、ui/timer/TimerEditScreen.kt、ui/components/ValuePickers.kt — 控制页重写为开发文档 §6.1 全区块:①主控(电源+一键全关+模式胶囊 0–4)②功能列表(添加定时/设置倒计时滚动定位)③定时组区(卡片=插座图标随通断着色+时间段+「定时N」+星期摘要+删除,点卡片=编辑,清空入口)④倒计时区(时分秒+执行操作开/关+保存)⑤设备信息(含循环定时只读行)⑥通信日志;删除/清空确认弹窗、Snackbar 提示;定时编辑页(开启/关闭时间、到点动作、重复时间 7 圆钮显示序日→六、BATCH 启用开关、保存返回);时间选择器改点段弹列表(原滚轮的 Android 简化)。
- `15:35 [测试]` test/…/CountdownCodecTest.kt、TimerCommandsTest.kt、StatusParserTest.kt — 新增 32 用例:3232 §8.1 示例帧向量/回环/ASCII 时间/截断容错,三族下发计划(10 组分包/单组包/EN=00 删除占位/清空/缺口选槽),3233 19B 与 20B 变体解析;合计 70/70 通过。
- `15:40 [验证]` 全量构建 — `testDebugUnitTest` 70/70 通过;`assembleDebug` BUILD SUCCESSFUL。M2/M3/M4 代码完成,待真机验收(继电器动作/定时到点/倒计时/断线重连)。
- `15:45 [文档]` AGENT.md、doc/TEP时控开关-Android开发文档.md — §6 新增 P7(3232 同构实证+§8.1 示例标注勘误)、P8(3233 组长 19/20B 两说与兼容策略);开发文档附录 A 功能清单 A1–A11 全部置「代码完成,待真机验收」;技术选型持久化行更正为 SharedPreferences(实测选择,保持最小依赖)。

### 2026-11-20

- `08:00 [UI]` ui/device/DeviceScreen.kt、MainViewModel.kt、MainActivity.kt — 主控区改为**纯状态展示**:大电源按钮去点击(仅随通断着色)、移除「一键全关」按钮及 onPower/onAllOff 链路(VM `setPower/allOff` 删除,DeviceRepository `setChannelPower/setAllPower` 协议封装保留备用),补「状态显示,由工作模式与定时控制」提示(用户反馈:手动开关在自动模式下被定时自纠覆盖、「一键全关」对单通道机型与大开关关断重复,均无实际用途;回归小程序开关图标区纯展示设计)。
- `08:00 [文档]` doc/TEP时控开关-Android开发文档.md — §1.1 主控范围、§6.1 页面结构、§6.2 对应关系、§6.3 交互规则、M2 小节备注、附录 A5 状态同步本次调整。

## 6. 协议补充(说明书未覆盖,带源码证据)

| # | 补充 | 证据 | 影响 |
|---|---|---|---|
| P1 | 广播厂商数据偏移:`type` = hex[0..4)(2B)、`encode` = hex[4..6)、`macAddr` = hex[6..18)、`productId` = hex[18..) 转 ASCII | `app-service.js getDeviceInfoByAdv`:`{type:e.slice(0,4),encode:e.slice(4,6),macAddr:e.slice(6,18),productId:asciiToChar(e.slice(18))}` | Android 端从 ScanRecord 的 0xFF AD 结构取数据后按此偏移解析;注意 WeChat 的 advertisData 即厂商数据整体(含 type 两字节) |
| P2 | 扫描过滤原逻辑:type 必须在型号目录中 **且** encode=="41" **且** productId 在产品目录中,否则不显示 | `app-service.js isDeviceFoundOrPaired` | App 策略:type=4951/4957 且 encode=41 即显示;productId 未知的标记「未知型号」并默认 GROUP 族(UI 提示),避免漏掉新品 |
| P3 | 定时组上限按型号:16/18/50/100/108/默认28(见开发文档 B1) | `cycle.js initTimerCnt` | ModelTable 按此建表 |
| P4 | BATCH 族删除=置 EN=00 后单组重发(开发文档 B2) | `cycle.js deleteTimer2` | M3 实现 |
| P5 | **循环定时可读回**:3233 是 3005 条目之一(§9.2),回传布局 = `[LEN][IDX][EN][SWID][保留] sH sM sS [00] eH eM eS [00] [EVENT]`,与下发格式一致(时分秒 ASCII) | `app-service.js recvDataDeal case "3233"`(三处实现一致:s_hour 取 `slice(11,12)+slice(13,14)` 低半字节对) | 循环定时/倒计时/定时组均可 3004 读回;解析在 M4+ 实现(协议层已预留) |
| P6 | **无「读设备当前时间」指令**:枚举 recvDataDeal 全部 60 个设备→主机 case(0001…5021)+ 协议 §6.2,无任何读时钟指令;5001 只写;UI 显示的时间是手机本地时钟(`updateClock` 用 `new Date()`)。5021 是 FTM007 配网状态,与时间无关 | `app-service.js recvDataDeal` 全 case 枚举;`updateClock:function` | 设备 RTC 时间不可读回;App 每次连接发 5001 对时即可保证准点;RTC 走时精度只能用「设近期定时观察是否准时动作」间接验证 |
| P7 | **3232 倒计时下发与回传同构**:ARG = `01 \| 0C \| IDX EN SWID 00 \| HH MM SS \| EVENT`,HH/MM/SS 为倒计时「时/分:秒」时长的 ASCII 两位数;说明书 §8.1 示例文字「10 分 30 秒」与其 hex(`3130 3330 3030` = 10:30:00)不符,以 hex 为准(示例标注笔误) | `cycle.js sendCountdown`(`"00010000"+HH+MM+SS+EVENT`,`r=len/2+1`)、`socket.js case "3232"`(hour 取低半字节对,同 3005 回传布局) | M4 编解码按 hex 向量实现(CountdownCodec),单测断言 §8.1 示例帧 |
| P8 | **3233 单组长度两说**:§8.2 布局 19B(EVENT=组末字节)与 P5 记载 20B(尾部 `[00][EVENT]`)并存;小程序发送恒 19B 且尾字节(=EVENT 位)恒发 `00`,读回解析取 `slice(36,38)`(=19B 末字节) | `cycle.js sendLoopTimer`("00010000"+sHsMsS+"00"+eHeMeS+"00")、`socket.js case "3233"`(event=slice(36,38)) | App 解码按组 LEN 前移、EVENT 取组末字节,两布局兼容(LoopTimerCodec);待真机读回实测定稿;循环定时编辑 M5+ 再定发送格式 |
