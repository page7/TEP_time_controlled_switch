package com.tep.timeswitch.protocol

/**
 * 型号表(说明书附录 A + 小程序 cycle.js initTimerCnt / constant.js productInfo)。
 * productId -> 固件族 + 中文名 + 定时组上限。
 */
object ModelTable {

    /** 定时指令族(说明书 §7.1)。 */
    enum class Family {
        /** 3231,无秒,单包全发,EN 生效 */
        LEGACY,

        /** 3234,有秒,10 组/包,EN 被强制 01(停用=删除+重排) */
        GROUP,

        /** 3236,有秒,1 组/包,EN 生效(删除=置 EN=00 单组重发) */
        BATCH,
    }

    data class Model(
        val productId: String,
        val name: String,
        val family: Family,
        val timerMaxCnt: Int,
        val type: Int = 0x4951,       // 4951 插座;4957 时控商照
        val metered: Boolean = false, // 计量型号(电参/阈值)
    )

    // —— LEGACY 族(3231)——
    private val legacy = listOf(
        Model("IS0213", "时控开关", Family.LEGACY, 16),
        Model("IS0214", "时控开关", Family.LEGACY, 28),
        Model("IS0217", "时控商照", Family.LEGACY, 16, type = 0x4957),
    )

    // —— BATCH 族(3236)——
    private val batch = listOf(
        Model("DZL002", "时控开关", Family.BATCH, 28),
        Model("ZJSK05", "计量时控通断器", Family.BATCH, 28, metered = true),
        Model("CXHY02", "计量时控通断器", Family.BATCH, 28, metered = true),
        Model("WZHF10", "电能计量通断器", Family.BATCH, 28, metered = true),
    )

    // —— GROUP 族(3234)——
    // timerMaxCnt 来自 cycle.js initTimerCnt:IS0222/IS0223/NBE003=100;
    // WZHF03/WZHF05/ZJSK03/YQHG02=50;WZHF06/TZJD*/ZJMJ01/NBE007=108;
    // YQOB01=18;其余默认 28。
    private val group = listOf(
        Model("IS0215", "时控开关", Family.GROUP, 28),
        Model("IS0218", "时控开关", Family.GROUP, 28),
        Model("IS0219", "时控开关", Family.GROUP, 28),
        Model("IS0222", "时控开关", Family.GROUP, 100),
        Model("IS0223", "时控开关", Family.GROUP, 100),
        Model("ZSCY03", "时控开关", Family.GROUP, 28),
        Model("NBE007", "时控开关", Family.GROUP, 108),
        Model("YQOB01", "时控开关", Family.GROUP, 18),
        Model("YQOB02", "时控开关", Family.GROUP, 28),
        Model("ZJGD01", "时控开关", Family.GROUP, 28),
        Model("ZSLX01", "时控开关", Family.GROUP, 28),
        Model("WZHF03", "时控开关", Family.GROUP, 50),
        Model("WZHF05", "时控开关", Family.GROUP, 50),
        Model("WZHF06", "时控开关", Family.GROUP, 108),
        Model("WZHF08", "时控开关", Family.GROUP, 28),
        Model("TZJD01", "时控开关", Family.GROUP, 108),
        Model("TZJD02", "时控开关", Family.GROUP, 108),
        Model("TZJD03", "时控开关", Family.GROUP, 108),
        Model("ZJMJ01", "时控开关", Family.GROUP, 108),
        Model("YQZX01", "时控开关", Family.GROUP, 28),
        Model("NBE003", "时控开关", Family.GROUP, 100),
        Model("ZJSK02", "时控开关", Family.GROUP, 28),
        Model("ZJSK03", "时控开关", Family.GROUP, 50),
        Model("YQHG02", "时控开关", Family.GROUP, 50),
        Model("SZCJ01", "时控开关", Family.GROUP, 28),
        Model("WZWS03", "时控开关", Family.GROUP, 28),
        Model("YQXW05", "时控开关", Family.GROUP, 28),
        Model("WZHF09", "单火线定时开关", Family.GROUP, 28),
    )

    private val all: Map<String, Model> = (legacy + batch + group).associateBy { it.productId }

    /** 查型号;未知 productId 返回 null(扫描列表标记「未知型号」,按 GROUP 兜底)。 */
    fun find(productId: String): Model? = all[productId.trim()]

    /** 未知型号兜底族:GROUP(34 个已知型号中 28 个属于该族)。 */
    val FALLBACK_FAMILY = Family.GROUP

    val allProductIds: Set<String> get() = all.keys
}
