package kt.payload

import kt.constants.PayloadKeys

/**
 * userKaleidxScopeList 里的 Gate 数据。
 */
data class KaleidxScopeGate(
    val gateId: Int,
    val musicId: Int,
    val isGateFound: Boolean,
    val isKeyFound: Boolean,
    val isClear: Boolean,
) {
    fun toMap(): Map<String, Any?> =
        mapOf<String, Any?>(
            PayloadKeys.GATE_ID to gateId,
            PayloadKeys.IS_GATE_FOUND to isGateFound,
            PayloadKeys.IS_KEY_FOUND to isKeyFound,
            PayloadKeys.IS_CLEAR to isClear,
            PayloadKeys.TOTAL_REST_LIFE to 0,
            PayloadKeys.TOTAL_ACHIEVEMENT to 0,
            PayloadKeys.TOTAL_DELUXSCORE to 0,
            PayloadKeys.BEST_ACHIEVEMENT to 0,
            PayloadKeys.BEST_DELUXSCORE to 0,
            PayloadKeys.BEST_ACHIEVEMENT_DATE to "",
            PayloadKeys.BEST_DELUXSCORE_DATE to "",
            PayloadKeys.PLAY_COUNT to 0,
            PayloadKeys.CLEAR_DATE to "",
            PayloadKeys.LAST_PLAY_DATE to "",
            PayloadKeys.IS_INFO_WATCHED to false,
        )
}
