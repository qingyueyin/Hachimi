package com.qing.hachimi.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.qing.hachimi.BuildConfig
import java.security.MessageDigest

enum class BuildChannel {
    DEBUG,
    OFFICIAL,
    UNOFFICIAL,
    UNPINNED,
}

object OfficialBuild {
    const val REPO_URL = "https://github.com/qingyueyin/Hachimi"
    const val RELEASES_URL = "$REPO_URL/releases"

    fun versionLabel(): String =
        "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    fun channel(context: Context): BuildChannel {
        val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debug) return BuildChannel.DEBUG
        val pin = BuildConfig.OFFICIAL_CERT_SHA256
        if (pin.isBlank()) return BuildChannel.UNPINNED
        val current = signingCertSha256(context) ?: return BuildChannel.UNOFFICIAL
        return if (current.equals(pin, ignoreCase = true)) {
            BuildChannel.OFFICIAL
        } else {
            BuildChannel.UNOFFICIAL
        }
    }

    fun channelLabel(channel: BuildChannel): String = when (channel) {
        BuildChannel.DEBUG -> "Debug"
        BuildChannel.OFFICIAL -> "官方"
        BuildChannel.UNOFFICIAL -> "非官方构建"
        BuildChannel.UNPINNED -> "未登记签名"
    }

    fun versionSummary(context: Context): String =
        "${versionLabel()} · ${channelLabel(channel(context))}"
}

internal fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
}

internal fun signingCertSha256(context: Context): String? {
    val info = context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.GET_SIGNING_CERTIFICATES,
    )
    val cert = info.signingInfo?.apkContentsSigners?.firstOrNull() ?: return null
    return sha256Hex(cert.toByteArray())
}
