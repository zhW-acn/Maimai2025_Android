package kt.payload

import kt.constants.PayloadKeys

/**
 * userCharacterList 里的旅行伙伴数据。
 */
data class UserCharacter(
    val characterId: Int,
    val point: Int,
    val level: Int,
    val awakening: Int,
    val useCount: Int,
) {
    fun toMap(): Map<String, Any?> =
        mapOf(
            PayloadKeys.CHARACTER_ID to characterId,
            PayloadKeys.POINT to point,
            PayloadKeys.LEVEL to level,
            PayloadKeys.AWAKENING to awakening,
            PayloadKeys.USE_COUNT to useCount,
        )

    fun withCharacterId(newCharacterId: Int): UserCharacter =
        copy(characterId = newCharacterId)

    companion object {
        fun fromCharaDetail(charaDetail: CharaDetail): UserCharacter =
            UserCharacter(
                characterId = charaDetail.characterId,
                point = 3,
                level = charaDetail.characterLevel,
                awakening = charaDetail.awake,
                useCount = 1,
            )
    }
}
