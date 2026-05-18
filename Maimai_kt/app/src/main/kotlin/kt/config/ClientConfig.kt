package kt.config

import kt.constants.CryptoConstants
import kt.constants.DefaultValues
import kt.constants.EnvNames
import kt.transport.PostDelayObserver
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * 客户端配置。
 *
 * 默认值尽量和 Python 版 maimai_client.config.ClientConfig 保持一致；
 * 如果需要换机厅、区域或路径，可以优先通过环境变量覆盖。
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
    val waitBeforeUpsertMillisProvider: (() -> Long)? = null,
    val postDelayObserver: PostDelayObserver = PostDelayObserver.None,
    val musicDbPath: Path = Path(env(EnvNames.MUSIC_DB_PATH, DefaultValues.MUSIC_DB_PATH)),
) {
    /** 当前协议版本使用的 AES Key。 */
    val aesKey: String
        get() = if (use2024Api) CryptoConstants.AES_KEY_2024 else CryptoConstants.AES_KEY_LEGACY

    /** 当前协议版本使用的 AES IV。 */
    val aesIv: String
        get() = if (use2024Api) CryptoConstants.AES_IV_2024 else CryptoConstants.AES_IV_LEGACY

    /** API 鍚嶇О hash 鏃朵娇鐢ㄧ殑娣锋穯鍙傛暟銆?*/
    val obfuscateParam: String
        get() = if (use2024Api) CryptoConstants.OBFUSCATE_2024 else CryptoConstants.OBFUSCATE_LEGACY

    fun currentWaitBeforeUpsertMillis(): Long =
        waitBeforeUpsertMillisProvider?.invoke() ?: waitBeforeUpsertMillis
}

private fun env(name: String, default: String): String = System.getenv(name) ?: default

private fun envInt(name: String, default: Int): Int = System.getenv(name)?.toIntOrNull() ?: default

private fun envLong(name: String, default: Long): Long = System.getenv(name)?.toLongOrNull() ?: default

private fun envBool(name: String, default: Boolean): Boolean =
    System.getenv(name)?.lowercase()?.let { it == "1" || it == "true" || it == "yes" } ?: default
