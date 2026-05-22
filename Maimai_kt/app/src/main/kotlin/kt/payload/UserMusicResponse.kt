package kt.payload

/**
 * GetUserMusicApi 的完整响应。
 *
 * 外层 length 表示 userMusicList 的总量/长度，具体歌曲成绩在每个分组的 userMusicDetailList 里。
 */
data class UserMusicResponse(
    val userId: Long = 0,
    val length: Int = 0,
    val nextIndex: Int = 0,
    val userMusicList: List<UserMusicGroup> = emptyList(),
)

/** userMusicList 中的一组歌曲成绩。 */
data class UserMusicGroup(
    val userMusicDetailList: List<MusicDetail> = emptyList(),
    val length: Int = 0,
)
