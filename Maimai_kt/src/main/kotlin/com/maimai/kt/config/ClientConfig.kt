package com.maimai.kt.config

import com.maimai.kt.constants.CryptoConstants
import com.maimai.kt.constants.DefaultValues
import com.maimai.kt.constants.EnvNames
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * 整个 Kotlin 重构包共用的配置。
 *
 * 这里尽量保持和 Python 版 maimai_client.config.ClientConfig 一致：
 * 默认值直接能跑旧项目逻辑，需要改机厅/区域/路径时优先用环境变量覆盖。
 */
data class ClientConfig(
    val regionId: Int = envInt(EnvNames.REGION_ID, DefaultValues.REGION_ID),
    val regionName: String = env(EnvNames.REGION_NAME, DefaultValues.REGION_NAME),
    val placeId: Int = envInt(EnvNames.PLACE_ID, DefaultValues.PLACE_ID),
    val placeName: String = env(EnvNames.PLACE_NAME, DefaultValues.PLACE_NAME),
    val clientId: String = env(EnvNames.CLIENT_ID, DefaultValues.CLIENT_ID),
    val titleEndpoint: String = env(EnvNames.TITLE_ENDPOINT, DefaultValues.TITLE_ENDPOINT),
    val aimeEndpoint: String = env(EnvNames.AIME_ENDPOINT, DefaultValues.AIME_ENDPOINT),
    val aimeCommonKey: String = env(EnvNames.AIME_COMMON_KEY, DefaultValues.AIME_COMMON_KEY),
    val chipId: String = env(EnvNames.CHIP_ID, DefaultValues.CHIP_ID),
    val titleTimeoutMillis: Long = envLong(EnvNames.TITLE_TIMEOUT_MILLIS, DefaultValues.TITLE_TIMEOUT_MILLIS),
    val maxRetries: Int = envInt(EnvNames.MAX_RETRIES, DefaultValues.MAX_RETRIES),
    val verifyTls: Boolean = envBool(EnvNames.VERIFY_TLS, DefaultValues.VERIFY_TLS),
    val use2024Api: Boolean = envBool(EnvNames.USE_2024_API, DefaultValues.USE_2024_API),
    val waitBeforeUpsertMillis: Long = envLong(EnvNames.WAIT_BEFORE_UPSERT_MILLIS, DefaultValues.WAIT_BEFORE_UPSERT_MILLIS),
    val musicDbPath: Path = Path(env(EnvNames.MUSIC_DB_PATH, DefaultValues.MUSIC_DB_PATH)),
) {
    /** 当前协议版本使用的 AES Key。 */
    val aesKey: String
        get() = if (use2024Api) CryptoConstants.AES_KEY_2024 else CryptoConstants.AES_KEY_LEGACY

    /** 当前协议版本使用的 AES IV。 */
    val aesIv: String
        get() = if (use2024Api) CryptoConstants.AES_IV_2024 else CryptoConstants.AES_IV_LEGACY

    /** API 名称 hash 时使用的混淆参数。 */
    val obfuscateParam: String
        get() = if (use2024Api) CryptoConstants.OBFUSCATE_2024 else CryptoConstants.OBFUSCATE_LEGACY
}

private fun env(name: String, default: String): String = System.getenv(name) ?: default

private fun envInt(name: String, default: Int): Int = System.getenv(name)?.toIntOrNull() ?: default

private fun envLong(name: String, default: Long): Long = System.getenv(name)?.toLongOrNull() ?: default

private fun envBool(name: String, default: Boolean): Boolean =
    System.getenv(name)?.lowercase()?.let { it == "1" || it == "true" || it == "yes" } ?: default
