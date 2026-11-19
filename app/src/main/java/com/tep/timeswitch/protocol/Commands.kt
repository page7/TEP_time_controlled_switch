package com.tep.timeswitch.protocol

/**
 * 指令码常量(说明书 §6)。
 * 注:同码在不同模板下语义可能不同,本项目只服务时控开关(socket 模板)。
 */
object Commands {
    // —— 握手 / 系统 ——
    const val KEEP_ALIVE = 0x0007      // 喂狗/保活
    const val SYNC_CLOCK = 0x5001      // 对时(14B ASCII yyyyMMddHHmmss)
    const val QUERY_INFO = 0x5011      // 查询设备信息 -> 5012
    const val DEV_INFO = 0x5012        // 上报:型号 + 版本

    // —— 状态 ——
    const val QUERY_STATUS = 0x3004    // 读全量状态 -> 3005
    const val FULL_STATUS = 0x3005     // 上报:条目式全量状态

    // —— 控制 ——
    const val SET_MODE = 0x3007        // 工作模式(1B)
    const val SET_POWER_STATE = 0x3020 // 上电状态(1B)
    const val SET_OUTPUT_STATE = 0x3219 // 输出状态(单键机型)
    const val SET_CHANNEL_POWER = 0x3221 // 单通道开关(channel + state)
    const val SET_COUNTDOWN_GEAR = 0x3224 // 倒计时粗档位(channel + 2B 分钟 + 00)
    const val SET_ALL_POWER = 0x3225   // 总开关(1B)
    const val SET_CHILD_LOCK = 0x3229  // 童锁(1B)

    // —— 定时 ——
    const val TIMER_LEGACY = 0x3231    // 写定时组(无秒族)
    const val COUNTDOWN = 0x3232       // 倒计时(精确到秒)
    const val LOOP_TIMER = 0x3233      // 循环定时
    const val TIMER_GROUP = 0x3234     // 写定时组(有秒族,10 组/包)
    const val HALF_SECOND = 0x3235     // 半秒定时
    const val TIMER_BATCH = 0x3236     // 写定时组(有秒族,1 组/包)

    // —— 日出日落 ——
    const val SET_LOCATION = 0x3238    // 经纬度
    const val SET_SUNRISE_MODE = 0x3239 // 日落模式
    const val SUNRISE_TIME = 0x3261    // 上报:日出日落时间

    // —— 密码 ——
    const val SET_PASSWORD = 0x3210
    const val PASSWORD_RESULT = 0x3211
    const val QUERY_CAPABILITY = 0x3212
    const val CAPABILITY = 0x3213

    // —— 计量(仅计量型号) ——
    const val GET_RECORD = 0x3171
    const val RECORD = 0x3172
    const val ELECTRICAL = 0x3281      // 实时电参
    const val THRESHOLD = 0x3282       // 阈值配置

    /** 3005 条目 TYPE(说明书 §9.2)。 */
    object StatusType {
        const val CHANNEL_COUNT = 0x3220
        const val CHANNEL_POWER = 0x3221
        const val COUNTDOWN_GEAR = 0x3224
        const val ALL_POWER = 0x3225
        const val CHILD_LOCK = 0x3229
        const val MODE = 0x3007
        const val POWER_STATE = 0x3020
        const val OUTPUT_STATE = 0x3219
        const val SUNRISE_MODE = 0x3239
        const val BATTERY_GEAR = 0x1036
        const val TIMER_LEGACY = 0x3231
        const val TIMER_GROUP = 0x3234
        const val TIMER_BATCH = 0x3236
        const val COUNTDOWN = 0x3232
        const val LOOP_TIMER = 0x3233
        const val HALF_SECOND = 0x3235
        const val ELECTRICAL = 0x3281
        const val THRESHOLD = 0x3282
        const val RECORD = 0x3172
        const val SUNRISE_TIME = 0x3261
    }

    /** 工作模式(3007 参数,说明书 §6.1 + 小程序 data-id 对照)。 */
    object Mode {
        const val OFF = 0x00            // 常关
        const val ON = 0x01             // 常开
        const val AUTO = 0x02           // 自动(分组定时)
        const val COUNTDOWN = 0x03      // 倒计时
        const val LOOP = 0x04           // 循环定时
        const val SUNRISE = 0x05        // 日落定时
        const val HALF_SECOND = 0x06    // 半秒定时

        fun name(mode: Int): String = when (mode) {
            OFF -> "常关"
            ON -> "常开"
            AUTO -> "自动"
            COUNTDOWN -> "倒计时"
            LOOP -> "循环定时"
            SUNRISE -> "日落定时"
            HALF_SECOND -> "半秒定时"
            else -> "未知($mode)"
        }
    }
}
