package com.sevis.photos

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.api.*
import io.ktor.http.*

class PhotosApplication : Application(), SingletonImageLoader.Factory {

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
