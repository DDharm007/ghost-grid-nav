package com.example.ghostgrid.hybrid

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class StrictOfflineBlockedException(
    message: String = "Blocked by GhostGrid Strict Offline Mode. Disable Strict Offline Mode in the top bar to permit on-demand HTTPS requests."
) : IOException(message)

/**
 * Transport-layer firewall interceptor attached to every OkHttp client in GhostGrid.
 * When Strict Offline Mode is ON (default), ZERO outbound packets can leave the process.
 */
class StrictOfflineInterceptor(
    private val isStrictOfflineEnabled: () -> Boolean
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (isStrictOfflineEnabled()) {
            throw StrictOfflineBlockedException()
        }
        val request = chain.request()
        if (!request.isHttps) {
            throw IOException("GhostGrid refuses non-HTTPS outbound connections.")
        }
        return chain.proceed(request)
    }
}
