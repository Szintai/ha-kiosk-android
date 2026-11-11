package hu.szintai.hakiosk.ha

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class HaResponse(val code: Int, val body: String) {
    val ok: Boolean get() = code in 200..299
}

/** Minimal JSON-over-HTTP client for the Home Assistant REST/webhook API. */
@Singleton
class HaApi @Inject constructor() {

    suspend fun postJson(url: String, token: String?, body: JSONObject): HaResponse =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                HaResponse(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
            } finally {
                conn.disconnect()
            }
        }

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}
