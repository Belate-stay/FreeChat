package com.freechat.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** 清单里声明的最新版本（官网 download 目录下的 latest.json）。 */
data class AppLatest(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val apkUrl: String
)

/**
 * 「检查更新」：读官网 download 目录下的 latest.json，按编译版本号（versionCode）判新旧；
 * 下载后再核对安装包的包名与编译版本号与清单一致才拉起安装 —— 三个都对得上才敢装。
 * 清单示例：
 * { "packageName": "com.freechat", "versionName": "1.1.10", "versionCode": 206,
 *   "apkUrl": "https://freechater.com/download/FreeChat_v1.1.10.apk" }
 */
object AppUpdateChecker {
    const val LATEST_URL = "https://freechater.com/download/latest.json"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    /** 解析并严格校验清单；任何必填项缺失或包名不符都当没有清单（宁可不更新，不可装错包）。 */
    fun parseLatest(json: String, expectedPackage: String): AppLatest? = runCatching {
        val o = JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val packageName = o.get("packageName")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        val versionName = o.get("versionName")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        val versionCode = o.get("versionCode")?.takeIf { it.isJsonPrimitive }?.asLong ?: return null
        val apkUrl = o.get("apkUrl")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        if (packageName != expectedPackage) return null
        if (versionName.isBlank() || versionCode <= 0L) return null
        if (!apkUrl.startsWith("https://")) return null
        AppLatest(packageName, versionName, versionCode, apkUrl)
    }.getOrNull()

    /** 只认编译版本号：比本机大才算有更新，相等或更小都是「已是最新」。 */
    fun needsUpdate(currentVersionCode: Long, latest: AppLatest): Boolean =
        latest.versionCode > currentVersionCode

    /** 安装包与清单的一致性：包名相同且编译版本号相同（对照「版本号/包名/编译版本号是否一致」）。 */
    fun archiveMatches(latest: AppLatest, packageName: String?, versionCode: Long): Boolean =
        packageName == latest.packageName && versionCode == latest.versionCode

    fun fetchLatest(expectedPackage: String): AppLatest? = runCatching {
        val body = httpClient.newCall(Request.Builder().url(LATEST_URL).build())
            .execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string() ?: return null
            }
        parseLatest(body, expectedPackage)
    }.getOrNull()

    fun download(url: String, dest: File) {
        httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("空响应")
            dest.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
    }

    fun archiveVersionCode(pm: PackageManager, path: String): Long? = runCatching {
        val info = pm.getPackageArchiveInfo(path, 0) ?: return null
        @Suppress("DEPRECATION")
        if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }.getOrNull()

    fun archivePackageName(pm: PackageManager, path: String): String? = runCatching {
        pm.getPackageArchiveInfo(path, 0)?.packageName
    }.getOrNull()

    /** 校验通过后拉起系统安装器（FileProvider 走 cache 目录）。 */
    fun installIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "com.freechat.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
