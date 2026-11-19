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
| 真机 | 待用户提供 | BLE 必须真机验证 |

## 4. 里程碑进度

| 里程碑 | 内容 | 状态 | 验收 |
|---|---|---|---|
| M0 | 规划文档 + AGENT 日志 | ✅ 完成(2026-11-19) | 开发文档 v1.0 已评审 |
| M1 | 工程骨架 + BLE 链路(扫描/连接/收发帧/握手) | 🚧 代码完成,待真机验收 | 真机扫描连接后日志可见 5012/3005 |
| M2 | 主控(开关/模式/状态渲染/断线重连) | ⬜ | 继电器可控、自动重连 |
| M3 | 定时组三族下发 | ⬜ | 设备到点动作正确 |
| M4 | 倒计时 + 收尾 | ⬜ | 功能清单全勾 |

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

## 6. 协议补充(说明书未覆盖,带源码证据)

| # | 补充 | 证据 | 影响 |
|---|---|---|---|
| P1 | 广播厂商数据偏移:`type` = hex[0..4)(2B)、`encode` = hex[4..6)、`macAddr` = hex[6..18)、`productId` = hex[18..) 转 ASCII | `app-service.js getDeviceInfoByAdv`:`{type:e.slice(0,4),encode:e.slice(4,6),macAddr:e.slice(6,18),productId:asciiToChar(e.slice(18))}` | Android 端从 ScanRecord 的 0xFF AD 结构取数据后按此偏移解析;注意 WeChat 的 advertisData 即厂商数据整体(含 type 两字节) |
| P2 | 扫描过滤原逻辑:type 必须在型号目录中 **且** encode=="41" **且** productId 在产品目录中,否则不显示 | `app-service.js isDeviceFoundOrPaired` | App 策略:type=4951/4957 且 encode=41 即显示;productId 未知的标记「未知型号」并默认 GROUP 族(UI 提示),避免漏掉新品 |
| P3 | 定时组上限按型号:16/18/50/100/108/默认28(见开发文档 B1) | `cycle.js initTimerCnt` | ModelTable 按此建表 |
| P4 | BATCH 族删除=置 EN=00 后单组重发(开发文档 B2) | `cycle.js deleteTimer2` | M3 实现 |
| P5 | **循环定时可读回**:3233 是 3005 条目之一(§9.2),回传布局 = `[LEN][IDX][EN][SWID][保留] sH sM sS [00] eH eM eS [00] [EVENT]`,与下发格式一致(时分秒 ASCII) | `app-service.js recvDataDeal case "3233"`(三处实现一致:s_hour 取 `slice(11,12)+slice(13,14)` 低半字节对) | 循环定时/倒计时/定时组均可 3004 读回;解析在 M4+ 实现(协议层已预留) |
| P6 | **无「读设备当前时间」指令**:枚举 recvDataDeal 全部 60 个设备→主机 case(0001…5021)+ 协议 §6.2,无任何读时钟指令;5001 只写;UI 显示的时间是手机本地时钟(`updateClock` 用 `new Date()`)。5021 是 FTM007 配网状态,与时间无关 | `app-service.js recvDataDeal` 全 case 枚举;`updateClock:function` | 设备 RTC 时间不可读回;App 每次连接发 5001 对时即可保证准点;RTC 走时精度只能用「设近期定时观察是否准时动作」间接验证 |
