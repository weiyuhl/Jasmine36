package com.lhzkml.jasmine.core.prompt.model

/**
 * 供应商账户余额信息
 */
data class BalanceInfo(
    /** 是否可用（余额充足） */
    val isAvailable: Boolean,
    /** 余额明细列表 */
    val balances: List<BalanceDetail>
) {
    /** 格式化显示文本 */
    fun toDisplayString(): String {
        if (balances.isEmpty()) return if (isAvailable) "可用" else "余额不足"
        return balances.joinToString("  ") { detail ->
            "${detail.currency} ${detail.totalBalance}"
        }
    }
}

data class BalanceDetail(
    /** 货币类型，如 CNY、USD */
    val currency: String,
    /** 总可用余额 */
    val totalBalance: String,
    /** 赠送余额（可选） */
    val grantedBalance: String? = null,
    /** 充值余额（可选） */
    val toppedUpBalance: String? = null
)
