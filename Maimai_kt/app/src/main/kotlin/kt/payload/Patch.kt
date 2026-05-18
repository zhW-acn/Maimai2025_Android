package kt.payload

/** 递归合并 patch，作用和 Python 版 merge_patch 一致。 */
@Suppress("UNCHECKED_CAST")
fun mergePatch(target: MutableMap<String, Any?>, patch: Map<String, Any?>): MutableMap<String, Any?> {
    patch.forEach { (key, value) ->
        val targetValue = target[key]
        if (targetValue is MutableMap<*, *> && value is Map<*, *>) {
            mergePatch(targetValue as MutableMap<String, Any?>, value as Map<String, Any?>)
        } else if (targetValue is MutableList<*> && value is List<*>) {
            mergeList(targetValue as MutableList<Any?>, value as List<Any?>)
        } else {
            target[key] = value
        }
    }
    return target
}

@Suppress("UNCHECKED_CAST")
private fun mergeList(target: MutableList<Any?>, patch: List<Any?>) {
    patch.forEachIndexed { index, patchItem ->
        val targetItem = target.getOrNull(index)
        if (targetItem is MutableMap<*, *> && patchItem is Map<*, *>) {
            mergePatch(targetItem as MutableMap<String, Any?>, patchItem as Map<String, Any?>)
        } else if (index >= target.size) {
            target.add(patchItem)
        } else {
            target[index] = patchItem
        }
    }
}
