package com.crazystudio.sportrecorder

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.crazystudio.sportrecorder.di.appModule
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SportApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SportApplication)
            modules(appModule)
        }
    }

    /**
     * Coil 3 loader for the shared UI. Meal photos are local files (built-in fetcher); the only
     * network images are the Insights map tiles, and OpenStreetMap's tile policy requires a
     * User-Agent that identifies the app — so the OkHttp fetcher is registered with one here
     * instead of the anonymous default client.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
            }
            .build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(client)) }
            .build()
    }

    private companion object {
        val USER_AGENT =
            "SportRecorder/${BuildConfig.VERSION_NAME} (Android; +https://github.com/Aidan79225/SportRecorder)"
    }
}
