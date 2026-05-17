package com.maimai.kt.payload

import org.jetbrains.annotations.NotNull

/** 旅行伙伴。 */
data class CharaDetail(
    val characterId: Int,
    val characterLevel: Int,
    val awake: Int
) {

    companion object {
        fun default(
            characterId: Int = 0,
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
