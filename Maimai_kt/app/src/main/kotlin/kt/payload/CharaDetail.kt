package kt.payload

import org.jetbrains.annotations.NotNull

/** 旅行伙伴。 */
data class CharaDetail(
    var characterId: Int,
    val characterLevel: Int,
    val awake: Int = 0
) {

    companion object {
        // 手动设置id
        const val CHARA_ID_NONE = -1
        fun default(
            characterId: Int = CHARA_ID_NONE,
            characterLevel: Int = -1,
            awake: Int = 0
        ): CharaDetail = CharaDetail(characterId, characterLevel, awake)

        fun max(
            @NotNull characterId: Int,
            characterLevel: Int = 9999,
            awake: Int = 5
        ): CharaDetail = CharaDetail(characterId, characterLevel, awake)

        fun defaultList(): List<CharaDetail> = listOf(
            default(),
            default(),
            default(),
            default(),
            default()
        )
    }
}
