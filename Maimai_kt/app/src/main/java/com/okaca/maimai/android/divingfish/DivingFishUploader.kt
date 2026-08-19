package com.okaca.maimai.android.divingfish

import android.util.Log
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import okhttp3.Call
import okhttp3.ConnectionSpec
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.TlsVersion

class DivingFishUploader {
    private val diffNames = mapOf(
        0 to "Basic",
        1 to "Advanced",
        2 to "Expert",
        3 to "Master",
        4 to "Re:Master",
    )

    private var client: OkHttpClient = buildHttpClient(followRedirect = false)

    fun getWechatAuthUrl(): String {
        client = buildHttpClient(followRedirect = true)
        val request = Request.Builder()
            .url(WECHAT_AUTH_URL)
            .headers(WECHAT_AUTH_HEADERS)
            .build()

        client.newCall(request).execute().use { response ->
            val url = response.request.url.toString()
                .replace("redirect_uri=https", "redirect_uri=http")
            Log.d(TAG, "Auth url: $url")
            return url
        }
    }

    fun fetchAndUploadData(
        username: String,
        password: String,
        difficulties: Set<Int>,
        wechatAuthUrl: String,
    ) {
        val normalizedAuthUrl = wechatAuthUrl.replaceFirst("http", "https")
        cookieJar.clearCookieStore()

        try {
            DivingFishUploadCoordinator.startAuth()
            DivingFishUploadCoordinator.writeLog("开始登录舞萌官方账号，请稍后...")
            loginWechat(normalizedAuthUrl)
            DivingFishUploadCoordinator.writeLog("舞萌官方登录完成")
        } catch (error: Exception) {
            DivingFishUploadCoordinator.writeLog("登录舞萌官方时出现错误")
            DivingFishUploadCoordinator.onError(error)
            return
        }

        try {
            fetchAndUploadData(username, password, difficulties)
            DivingFishUploadCoordinator.writeLog("水鱼成绩上传完成")
            DivingFishUploadCoordinator.finishUpdate()
        } catch (error: Exception) {
            DivingFishUploadCoordinator.writeLog("上传水鱼成绩时出现错误")
            DivingFishUploadCoordinator.onError(error)
        }
    }

    private fun loginWechat(wechatAuthUrl: String) {
        client = buildHttpClient(followRedirect = true)
        Log.d(TAG, wechatAuthUrl)

        val request = Request.Builder()
            .headers(WX_WINDOWS_HEADERS)
            .get()
            .url(wechatAuthUrl)
            .build()

        client.newCall(request).execute().use { response ->
            response.body?.string()?.let { Log.d(TAG, it) }
            DivingFishUploadCoordinator.writeLog("舞萌官方登录响应码：${response.code}")
            if (response.code >= 400) {
                error("登录舞萌官方失败，请重试")
            }

            val location = response.header("Location")
            if (response.code in 300..399 && location != null) {
                client.newCall(Request.Builder().url(location).get().build())
                    .execute()
                    .close()
            }
        }
    }

    private fun fetchAndUploadData(
        username: String,
        password: String,
        difficulties: Set<Int>,
    ) {
        val tasks = difficulties.map { diff ->
            CompletableFuture.runAsync {
                fetchAndUploadData(username, password, diff, retryCount = 1)
            }
        }
        tasks.forEach { it.join() }
    }

    private fun fetchAndUploadData(
        username: String,
        password: String,
        diff: Int,
        retryCount: Int,
    ) {
        val diffName = diffNames[diff] ?: diff.toString()
        DivingFishUploadCoordinator.writeLog("开始获取 $diffName 难度数据")
        val request = Request.Builder()
            .url("https://maimai.wahlap.com/maimai-mobile/record/musicGenre/search/?genre=99&diff=$diff")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val data = response.body?.string().orEmpty()
                DivingFishUploadCoordinator.writeLog("$diffName 难度数据已获取，正在上传至水鱼")
                uploadData(
                    diff = diff,
                    data = "<login><u>$username</u><p>$password</p></login>$data",
                    retryCount = 1,
                )
            }
        } catch (error: Exception) {
            retryFetchAndUploadData(error, username, password, diff, retryCount)
        }
    }

    private fun uploadData(diff: Int, data: String, retryCount: Int) {
        val diffName = diffNames[diff] ?: diff.toString()
        val request = Request.Builder()
            .url(DIVING_FISH_PAGE_PARSER_URL)
            .addHeader("content-type", "text/plain")
            .post(data.toRequestBody(TEXT))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val result = response.body?.string().orEmpty()
                DivingFishUploadCoordinator.writeLog("$diffName 难度上传状态：$result")
            }
        } catch (error: Exception) {
            retryUploadData(error, diff, data, retryCount)
        }
    }

    private fun retryFetchAndUploadData(
        error: Exception,
        username: String,
        password: String,
        diff: Int,
        currentRetryCount: Int,
    ) {
        val diffName = diffNames[diff] ?: diff.toString()
        DivingFishUploadCoordinator.writeLog("获取 $diffName 难度数据时出现错误：$error")
        if (currentRetryCount < MAX_RETRY_COUNT) {
            DivingFishUploadCoordinator.writeLog("进行第 $currentRetryCount 次重试")
            fetchAndUploadData(username, password, diff, currentRetryCount + 1)
        } else {
            DivingFishUploadCoordinator.writeLog("$diffName 难度数据获取失败")
        }
    }

    private fun retryUploadData(error: Exception, diff: Int, data: String, currentRetryCount: Int) {
        val diffName = diffNames[diff] ?: diff.toString()
        DivingFishUploadCoordinator.writeLog("上传 $diffName 难度数据至水鱼时出现错误：$error")
        if (currentRetryCount < MAX_RETRY_COUNT) {
            DivingFishUploadCoordinator.writeLog("进行第 $currentRetryCount 次重试")
            uploadData(diff, data, currentRetryCount + 1)
        } else {
            DivingFishUploadCoordinator.writeLog("$diffName 难度数据上传失败")
        }
    }

    private fun buildHttpClient(followRedirect: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followRedirects(followRedirect)
            .followSslRedirects(followRedirect)
            .cookieJar(cookieJar)
            .cache(null)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request()
                        .newBuilder()
                        .addHeader("Cache-Control", "no-cache")
                        .build()
                )
            }
            .connectionSpecs(
                listOf(
                    ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
                        .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0)
                        .allEnabledCipherSuites()
                        .build(),
                    ConnectionSpec.CLEARTEXT,
                )
            )
            .pingInterval(3, TimeUnit.SECONDS)

        if (IGNORE_CERT) {
            builder.ignoreCert()
        }
        return builder.build()
    }

    private fun OkHttpClient.Builder.ignoreCert() {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
                Unit

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) =
                Unit

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, arrayOf(trustManager), SecureRandom())
        sslSocketFactory(sslContext.socketFactory, trustManager)
        hostnameVerifier { _, _ -> true }
    }

    private companion object {
        const val TAG = "DivingFishUploader"
        const val IGNORE_CERT = false
        const val MAX_RETRY_COUNT = 4
        const val WECHAT_AUTH_URL =
            "https://tgk-wcaime.wahlap.com/wc_auth/oauth/authorize/maimai-dx"
        const val DIVING_FISH_PAGE_PARSER_URL = "https://www.diving-fish.com/api/pageparser/page"

        val TEXT = "text/plain".toMediaType()
        val cookieJar = SimpleCookieJar()

        val WECHAT_AUTH_HEADERS: Headers = Headers.Builder()
            .add("Host", "tgk-wcaime.wahlap.com")
            .add("Upgrade-Insecure-Requests", "1")
            .add(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 12; IN2010 Build/RKQ1.211119.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/86.0.4240.99 XWEB/4317 MMWEBSDK/20220903 Mobile Safari/537.36 MMWEBID/363 MicroMessenger/8.0.28.2240(0x28001C57) WeChat/arm64 Weixin NetType/WIFI Language/zh_CN ABI/arm64"
            )
            .add(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/wxpic,image/tpg,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
            )
            .add("X-Requested-With", "com.tencent.mm")
            .add("Sec-Fetch-Site", "none")
            .add("Sec-Fetch-Mode", "navigate")
            .add("Sec-Fetch-User", "?1")
            .add("Sec-Fetch-Dest", "document")
            .add("Accept-Encoding", "gzip, deflate")
            .add("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
            .build()

        val WX_WINDOWS_HEADERS: Headers = Headers.Builder()
            .add("Connection", "keep-alive")
            .add("Upgrade-Insecure-Requests", "1")
            .add(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36 NetType/WIFI MicroMessenger/7.0.20.1781(0x6700143B) WindowsWechat(0x6307001e)"
            )
            .add(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
            )
            .add("Sec-Fetch-Site", "none")
            .add("Sec-Fetch-Mode", "navigate")
            .add("Sec-Fetch-User", "?1")
            .add("Sec-Fetch-Dest", "document")
            .add("Accept-Encoding", "gzip, deflate, br")
            .add("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
            .build()
    }
}
