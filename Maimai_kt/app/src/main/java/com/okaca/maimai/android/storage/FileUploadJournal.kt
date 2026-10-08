package com.okaca.maimai.android.storage

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kt.service.UpsertJournal
import kt.transport.JsonSupport

/**
 * 一次迁移的上传报文留底：每发一包 upsert，就把那包完整的 JSON 写成 `<目录>/<第几次>.json`。
 *
 * 目录名由 [SnapshotStore.newUploadJournal] 按开始迁移的时间定下（`yyyyMMdd-HHmmss`），
 * 和 `data/<userId>.json` 同在应用私有的 `data/` 下。
 *
 * ★ 写不进去就抛异常 —— 那一包不会发出，迁移中止（目标账号照常登出）。
 *   没留底的包不能发，否则断点续传时无从判断服务端收到了哪几包。
 */
class FileUploadJournal internal constructor(
    /** 本次迁移的记录目录；第一次记录时才创建，一包都没发就不会留下空目录。 */
    val directory: File,
) : UpsertJournal {

    override suspend fun record(sequence: Int, payload: Map<String, Any?>) {
        withContext(Dispatchers.IO) {
            directory.mkdirs()
            File(directory, "$sequence.json")
                .writeText(WRITER.writeValueAsString(payload), Charsets.UTF_8)
        }
    }

    private companion object {
        /** 缩进输出，和快照文件的格式一致，方便直接对比。 */
        val WRITER = JsonSupport.mapper.writerWithDefaultPrettyPrinter()
    }
}
