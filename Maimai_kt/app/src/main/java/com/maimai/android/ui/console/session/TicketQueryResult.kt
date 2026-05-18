package com.maimai.android.ui.console.session

/**
 * 查票弹窗使用的数据结构。
 *
 * 核心接口返回的是 Map，因为标题服返回字段比较动态；Android 页面先把 Map
 * 转成明确的数据类，弹窗渲染时就不用到处写字符串 key 了。
 */
data class TicketQueryResult(
    val userId: Long,
    val length: Int,
    val tickets: List<TicketChargeItem>,
) {
    companion object {
        fun fromMap(value: Map<String, Any?>): TicketQueryResult {
            val tickets = (value["userChargeList"] as? List<*>)
                .orEmpty()
                .mapNotNull { it as? Map<*, *> }
                .map { TicketChargeItem.fromMap(it) }

            return TicketQueryResult(
                userId = value["userId"].toLongOrZero(),
                length = value["length"].toIntOr(tickets.size),
                tickets = tickets,
            )
        }
    }
}

data class TicketChargeItem(
    val chargeId: Int,
    val stock: Int,
    val purchaseDate: String,
    val validDate: String,
    val extNum1: Int,
) {
    companion object {
        fun fromMap(value: Map<*, *>): TicketChargeItem =
            TicketChargeItem(
                chargeId = value["chargeId"].toIntOr(0),
                stock = value["stock"].toIntOr(0),
                purchaseDate = value["purchaseDate"]?.toString().orEmpty(),
                validDate = value["validDate"]?.toString().orEmpty(),
                extNum1 = value["extNum1"].toIntOr(0),
            )
    }
}

private fun Any?.toLongOrZero(): Long =
    when (this) {
        is Number -> toLong()
        is String -> toLongOrNull() ?: 0L
        else -> 0L
    }

private fun Any?.toIntOr(defaultValue: Int): Int =
    when (this) {
        is Number -> toInt()
        is String -> toIntOrNull() ?: defaultValue
        else -> defaultValue
    }
