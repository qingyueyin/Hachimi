package com.qing.hachimi.di

import com.qing.hachimi.data.api.*
import com.qing.hachimi.data.local.AccountHistoryManager
import com.qing.hachimi.data.local.CookieManager
import com.qing.hachimi.data.local.DiscoveryCacheManager
import com.qing.hachimi.data.local.DownloadHistoryManager
import com.qing.hachimi.data.local.SeenManager
import com.qing.hachimi.data.local.SettingsManager
import com.qing.hachimi.data.local.UpgradeHistoryManager
import com.qing.hachimi.data.repository.NeteaseRepository
import com.qing.hachimi.downloader.DownloadEngine
import com.qing.hachimi.service.UpgradeService
import com.qing.hachimi.ui.screens.DiscoverViewModel
import com.qing.hachimi.ui.screens.SearchViewModel
import com.qing.hachimi.ui.screens.MyViewModel
import com.qing.hachimi.ui.screens.MainViewModel
import com.qing.hachimi.ui.screens.UpgradeViewModel
import com.qing.hachimi.util.AudioQualityAnalyzer
import com.qing.hachimi.data.api.RetryInterceptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.TimeUnit
import com.qing.hachimi.util.LoggingInterceptor

val appModule = module {

    // OkHttpClient singleton
    single {
        val cacheDir = File(androidContext().cacheDir, "http_cache")
        val cache = Cache(cacheDir, 15 * 1024 * 1024)

        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(20, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .cache(cache)
            .addInterceptor(RetryInterceptor(maxRetries = 2, initialDelayMs = 1000))
            .addInterceptor(LoggingInterceptor())
            .build()
    }

    // CoroutineScope for application
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    // Local data managers
    single { AccountHistoryManager(androidContext()) }
    single { CookieManager(androidContext()) }
    single { SettingsManager(androidContext()) }
    single { DownloadHistoryManager(androidContext()) }
    single { SeenManager(androidContext()) }
    single { DiscoveryCacheManager(androidContext()) }
    single { UpgradeHistoryManager(androidContext()) }

    // API layer
    single { NeteaseApi(get()) }
    single { SongApi(get()) }
    single { PlaylistApi(get()) }
    single { SearchApi(get()) }
    single { AlbumApi(get()) }
    single { LoginApi(get()) }
    single { ArtistApi(get()) }
    single { DiscoveryApi(get()) }
    single { CloudApi(get()) }
    single { ListenDataApi(get()) }
    single { CollectionApi(get()) }
    single { UpgradeApi(get(), get()) }

    // Repository
    single {
        NeteaseRepository(
            api = get(),
            cookieManager = get(),
            songApi = get(),
            playlistApi = get(),
            searchApi = get(),
            albumApi = get(),
            loginApi = get(),
            artistApi = get(),
            discoveryApi = get(),
            cloudApi = get(),
            listenDataApi = get(),
            collectionApi = get()
        )
    }

    // Download engine
    single { DownloadEngine(androidContext(), get(), get(), get()) }

    // 音质升级相关
    single { AudioQualityAnalyzer() }
    single { UpgradeService(get(), get(), get(), get()) }

    // ViewModel
    viewModel {
        MainViewModel(
            repository = get(),
            cookieManager = get(),
            downloadEngine = get(),
            settingsManager = get(),
            seenManager = get(),
            discoveryCache = get(),
            accountHistory = get()
        )
    }
    viewModel { DiscoverViewModel(repository = get(), cookieManager = get(), settingsManager = get(), discoveryCache = get(), seenManager = get(), downloadEngine = get()) }
    viewModel { SearchViewModel(repository = get(), cookieManager = get(), settingsManager = get(), downloadEngine = get()) }
    viewModel { MyViewModel(repository = get(), cookieManager = get(), settingsManager = get(), downloadEngine = get(), discoveryCache = get()) }
    viewModel { UpgradeViewModel(get(), get()) }
}
