package com.qing.hachimi.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * 存储权限统一管理。
 *
 * 背景：App 通过裸 File API 写公共存储目录，依赖「所有文件访问权限」（MANAGE_EXTERNAL_STORAGE）。
 * 在鸿蒙卓易通/出境易等兼容层环境中存在两类问题：
 * 1. [Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION] 设置页可能不存在，
 *    直接 startActivity 会抛 ActivityNotFoundException；
 * 2. 权限开关显示已开启，但容器桥接层仍可能拒绝真实写入。
 *
 * 因此所有检查以 [probeCanWrite] 的真实写入探测为准，
 * 所有跳转必须走 [openAllFilesAccessSettings] 的三级降级链路。
 */
object StoragePermissionManager {

    private const val TAG = "StoragePermission"

    /**
     * 是否拥有「所有文件访问权限」。
     * 注意：部分兼容层可能未实现该检查或返回不准确的结果，
     * 最终可写性请以 [probeCanWrite] 为准。
     */
    fun hasAllFilesAccess(): Boolean {
        return try {
            Environment.isExternalStorageManager()
        } catch (e: Exception) {
            AppLogger.warn("[$TAG] isExternalStorageManager threw", e)
            false
        }
    }

    /**
     * 真实写入探测：在目标目录创建临时文件并删除。
     * 这是判断"能否真正落盘"的唯一可靠依据（兼容层开关状态可能失真）。
     */
    fun probeCanWrite(dir: File): Boolean {
        return try {
            if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory) {
                AppLogger.warn("[$TAG] probe: mkdirs failed ${dir.absolutePath}")
                return false
            }
            val probe = File(dir, ".hachimi_probe_${System.currentTimeMillis()}.tmp")
            probe.writeText("probe")
            val ok = probe.exists() && probe.length() > 0L
            probe.delete()
            if (!ok) AppLogger.warn("[$TAG] probe: write failed in ${dir.absolutePath}")
            ok
        } catch (e: Exception) {
            AppLogger.warn("[$TAG] probe write failed for ${dir.absolutePath}", e)
            false
        }
    }

    /**
     * 跳转授权页，三级降级：
     * 1. 本应用专属的「所有文件访问权限」页；
     * 2. 全局「所有文件访问权限」列表页（部分 ROM/兼容层只提供此入口）；
     * 3. 应用详情页（手动进入权限设置）。
     *
     * @return 是否成功拉起任一系统页面
     */
    fun openAllFilesAccessSettings(context: Context): Boolean {
        val appSpecific = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        if (tryStart(context, appSpecific)) return true

        val globalList = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        if (tryStart(context, globalList)) return true

        val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        if (tryStart(context, appDetails)) return true

        AppLogger.error("[$TAG] all storage settings intents failed to resolve")
        return false
    }

    private fun tryStart(context: Context, intent: Intent): Boolean {
        return try {
            if (intent.resolveActivity(context.packageManager) == null) {
                AppLogger.warn("[$TAG] no resolver for ${intent.action}")
                return false
            }
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (e: Exception) {
            AppLogger.warn("[$TAG] start ${intent.action} failed", e)
            false
        }
    }

    /**
     * 探测是否运行于安卓兼容层（鸿蒙卓易通/出境易等），仅用于定制提示文案。
     * 包可见性受限时静默返回 null，不影响任何功能。
     */
    fun detectCompatibilityLayer(context: Context): String? {
        return try {
            val pm: PackageManager = context.packageManager
            // 卓易通 / 出境易（上海卓易科技）容器组件包名
            val knownPackages = arrayOf(
                "com.droi.easyabroad",
                "com.droi.zhuoyitong",
                "com.droi.droidmanager",
            )
            for (pkg in knownPackages) {
                if (runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess) return "卓易通"
            }
            val fingerprint = Build.FINGERPRINT ?: ""
            val manufacturer = Build.MANUFACTURER ?: ""
            if (fingerprint.contains("droi", ignoreCase = true) ||
                manufacturer.contains("droi", ignoreCase = true)
            ) {
                return "卓易通"
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}

internal fun storageAccessSummary(granted: Boolean, writable: Boolean): String = when {
    granted && writable -> "已授予所有文件访问权限"
    granted -> "已授权但目录不可写，点击重试或更换下载目录"
    else -> "未授予，下载将失败，点击去开启"
}
