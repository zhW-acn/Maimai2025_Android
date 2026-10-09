package com.okaca.maimai.android.storage

import android.content.Context
import android.util.Log
import com.fasterxml.jackson.databind.ObjectWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kt.constants.PayloadKeys
import kt.transport.JsonSupport

/**
 * 账号快照的本地存档。
 *
 * 目录和文件名都对齐 Python 版：`<应用私有目录>/data/<userId>.json`，
 * 一份快照一个文件，**覆盖式**写入（对齐 `get_need_user_data.save_user_data`）。
 *
 * 放应用私有目录不需要任何存储权限；要拿走文件用页面上的「导出」按钮走 SAF。
 */
@Singleton
class SnapshotStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** 一份已保存的快照。 */
    data class SavedSnapshot(
        val uid: Long,
        val sizeBytes: Long,
        val modifiedAt: Long,
        /** 曲目记录条数（`userMusicDetailList` -> `userMusicList`）。 */
        val musicCount: Int,
    ) {
        val fileName: String get() = "$uid.json"
    }

    /** 快照目录（`filesDir/data`）。 */
    val directory: File
        get() = File(context.filesDir, DATA_DIR_NAME)

    /** 一份快照对应的文件。 */
    fun fileFor(uid: Long): File = File(directory, "$uid.json")

    /** 账号 [userId] 的上传记录目录（`data/<userId>`），下面每次迁移一个时间戳子目录。 */
    private fun journalRootOf(userId: Long): File = File(directory, userId.toString())

    /**
     * 新开一次上传的报文记录，目录名取当前时间，记在目标账号的目录下。
     *
     * 目录在第一次记录时才创建，没发出任何一包就不会留下空目录。
     */
    fun newUploadJournal(userId: Long): FileUploadJournal =
        FileUploadJournal(File(journalRootOf(userId), LocalDateTime.now().format(FOLDER_STAMP)))

    /** 目标账号最近一次上传记录的目录名（时间戳名字可直接比大小）；没有返回 null。 */
    suspend fun latestUploadFolder(userId: Long): String? = withContext(Dispatchers.IO) {
        journalRootOf(userId)
            .listFiles { file -> file.isDirectory && FOLDER_NAME.matches(file.name) }
            ?.maxByOrNull { it.name }
            ?.name
    }

    /** 续传：打开账号下已有的上传记录目录；目录名不合法或不存在直接报错，不新建。 */
    fun resumeUploadJournal(userId: Long, folder: String): FileUploadJournal {
        require(FOLDER_NAME.matches(folder)) { "非法的上传记录目录名：$folder" }
        val target = File(journalRootOf(userId), folder)
        require(target.isDirectory) { "找不到上传记录目录：$folder" }
        return FileUploadJournal(target)
    }

    /**
     * 覆盖写入一份快照。
     *
     * 用带缩进的写法（对齐 Python 的 `indent=2`），方便导出后人工核对；
     * Jackson 默认不转义非 ASCII，中文能保持原样（对齐 `ensure_ascii=False`）。
     */
    suspend fun save(uid: Long, snapshot: Map<String, Any?>): File = withContext(Dispatchers.IO) {
        val target = fileFor(uid)
        target.parentFile?.mkdirs()
        target.writeText(PRETTY_WRITER.writeValueAsString(snapshot), Charsets.UTF_8)
        target
    }

    /**
     * 列出已保存的快照，**新的在前**。
     *
     * 曲目条数需要读一遍文件才能知道大小，快照本身有几百 KB～几 MB，
     * 所以一定要在 IO 线程跑（本方法已经是 suspend）。
     */
    suspend fun list(): List<SavedSnapshot> = withContext(Dispatchers.IO) {
        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(SUFFIX) }
            ?: return@withContext emptyList()

        files.mapNotNull { file ->
            val uid = file.nameWithoutExtension.toLongOrNull() ?: return@mapNotNull null
            SavedSnapshot(
                uid = uid,
                sizeBytes = file.length(),
                modifiedAt = file.lastModified(),
                musicCount = musicCount(file, uid),
            )
        }.sortedByDescending { it.modifiedAt }
    }

    /**
     * 读出快照原文（导出用）。
     *
     * ★ 读失败一律返回 null，不往外抛 —— 调用方（载入 / 导出）跑在 `viewModelScope` 里，
     *   漏出去就是一个没头没尾的闪退，还不如让页面报「快照文件不存在或已被删除」。
     */
    suspend fun readText(uid: Long): String? = withContext(Dispatchers.IO) {
        val file = fileFor(uid)
        if (file.isFile) {
            runCatching { file.readText(Charsets.UTF_8) }
                .onFailure { Log.w(TAG, "读取快照 $uid 失败", it) }
                .getOrNull()
        } else {
            null
        }
    }

    /** 读出快照并解析成 Map。 */
    suspend fun readMap(uid: Long): MutableMap<String, Any?>? =
        readText(uid)?.let { text ->
            if (text.isBlank()) null else JsonSupport.parseObject(text)
        }

    /** 删除一份快照。删不掉（或本来就不在）返回 false，同样不往外抛。 */
    suspend fun delete(uid: Long): Boolean = withContext(Dispatchers.IO) {
        val file = fileFor(uid)
        if (!file.exists()) {
            true
        } else {
            runCatching { file.delete() }
                .onFailure { Log.w(TAG, "删除快照 $uid 失败", it) }
                .getOrDefault(false)
        }
    }

    /** 从快照里数出曲目条数（`userMusicDetailList` -> `userMusicList`）。 */
    fun musicCountOf(snapshot: Map<String, Any?>): Int =
        ((snapshot[PayloadKeys.USER_MUSIC_DETAIL_LIST] as? Map<*, *>)
            ?.get(PayloadKeys.USER_MUSIC_LIST) as? List<*>)?.size ?: 0

    private fun musicCount(file: File, uid: Long): Int = try {
        musicCountOf(JsonSupport.parseObject(file.readText(Charsets.UTF_8)))
    } catch (error: Throwable) {
        // 单个文件坏了不该让整张列表挂掉
        Log.w(TAG, "快照 $uid 解析失败，曲目数按 0 显示", error)
        0
    }

    private companion object {
        const val TAG = "SnapshotStore"
        const val DATA_DIR_NAME = "data"
        const val SUFFIX = ".json"

        /** 上传报文目录名：开始迁移的时间，如 `20261008-143005`。 */
        val FOLDER_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        val FOLDER_NAME = Regex("""\d{8}-\d{6}""")

        /** 缩进输出，对齐 Python 的 `json.dump(..., indent=2)`。 */
        val PRETTY_WRITER: ObjectWriter =
            JsonSupport.mapper.writerWithDefaultPrettyPrinter()
    }
}
