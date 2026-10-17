package de.ideenwerft.kiez

import android.app.Application
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import de.ideenwerft.kiez.data.Api
import de.ideenwerft.kiez.data.Prefs
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

// Gemeinsame Objekte der App: Server-Verbindung, Einstellungen, Bild- und Video-Zwischenspeicher
class KiezApp : Application(), SingletonImageLoader.Factory {
    lateinit var prefs: Prefs
    lateinit var api: Api
    lateinit var http: OkHttpClient

    @UnstableApi
    val videoCache: SimpleCache by lazy {
        // Bereits gesehene Videos nicht erneut laden (wie AndroidVideoCache bei Jodel)
        SimpleCache(File(cacheDir, "video"), LeastRecentlyUsedCacheEvictor(200L * 1024 * 1024), StandaloneDatabaseProvider(this))
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        http = OkHttpClient.Builder()
            .cache(Cache(File(cacheDir, "http"), 20L * 1024 * 1024))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .build()
        api = Api(BuildConfig.SERVER_URL, http, prefs)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
        .diskCache { DiskCache.Builder().directory(File(cacheDir, "images")).maxSizeBytes(150L * 1024 * 1024).build() }
        .crossfade(true)
        .build()

    companion object {
        lateinit var instance: KiezApp
            private set
    }
}
