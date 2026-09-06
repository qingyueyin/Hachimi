package com.qing.hachimi

import android.app.Application
import android.util.Log
import coil3.ImageLoader
import okio.Path.Companion.toPath
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.qing.hachimi.di.appModule
import com.qing.hachimi.util.AppLogger
import org.jaudiotagger.tag.TagOptionSingleton
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import java.io.File

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        // Logger 初始化（含崩溃处理器）
        runCatching { AppLogger.init(this) }
            .onFailure { Log.e("Hachimi", "AppLogger.init failed", it) }

        runCatching { TagOptionSingleton.getInstance().setAndroid(true) }
            .onFailure { Log.e("Hachimi", "jaudiotagger init failed", it) }

        runCatching {
            SingletonImageLoader.setSafe { context ->
                ImageLoader.Builder(context)
                    .crossfade(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .memoryCache {
                        MemoryCache.Builder().apply {
                            maxSizePercent(context, 0.2)
                        }.build()
                    }
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .diskCache {
                        DiskCache.Builder().apply {
                            directory("${context.cacheDir}/coil_cache".toPath())
                            maxSizeBytes(50 * 1024 * 1024)
                        }.build()
                    }
                    .build()
            }
        }.onFailure { Log.e("Hachimi", "Coil init failed", it) }

        runCatching {
            startKoin {
                androidLogger(Level.ERROR)
                androidContext(this@App)
                modules(appModule)
            }
        }.onFailure { error ->
            Log.e("Hachimi", "Koin init failed: ${error.message}", error)
            // Ensure Koin is always running so MainActivity doesn't crash
            if (error.message?.contains("already created") != true) {
                try {
                    startKoin {
                        androidLogger(Level.ERROR)
                        androidContext(this@App)
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
