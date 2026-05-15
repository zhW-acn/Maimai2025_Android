package com.maimai.kt.payload

/** 把动态 JSON Map 取值转成 Map，集中处理 unchecked cast。 */
@Suppress("UNCHECKED_CAST")
fun Any?.asStringAnyMap(): MutableMap<String, Any?> = this as MutableMap<String, Any?>

/** 把动态 JSON Map 取值转成列表。 */
@Suppress("UNCHECKED_CAST")
fun Any?.asAnyList(): MutableList<Any?> = this as MutableList<Any?>

/** 把 Number/String 统一转 Long。 */
fun Any?.asLongValue(): Long = when (this) {
    is Number -> toLong()
    is String -> toLong()
    else -> error("无法转换为 Long: $this")
}

/** 把 Number/String 统一转 Int。 */
fun Any?.asIntValue(): Int = when (this) {
    is Number -> toInt()
    is String -> toInt()
    else -> error("无法转换为 Int: $this")
}
