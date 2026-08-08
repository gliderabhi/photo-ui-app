package com.sevis.photos

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.ktor2.KtorNetworkFetcherFactory
import com.sevis.photos.localscan.LocalScanWorker
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.api.*
import io.ktor.http.*

class PhotosApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        // LocalScanWorker (MediaStore sync + place-name resolution) only needs to
        // run once the app is actually backgrounded, not on every screen
        // composition — this used to also gate on-device face detection before
        // that moved server-side (see LocalScanWorker).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                Log.d("AppLifecycle", "onStop — background, triggering LocalScanWorker")
                LocalScanWorker.runOnce(applicationContext)
            }
        })
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        val httpClient = HttpClient(Android) {
            install(createClientPlugin("DynamicAuth") {
                onRequest { request, _ ->
                    AppState.token?.let {
                        request.headers.append(HttpHeaders.Authorization, "Bearer $it")
                    }
                    AppState.folderPassword?.let {
                        request.headers.append("X-Folder-Password", it)
                    }
                }
            })
        }
        return ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory(httpClient = httpClient))
            }
            .build()
    }
}
