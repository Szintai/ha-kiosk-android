package hu.szintai.hakiosk.share

import android.util.Log
import hu.szintai.hakiosk.config.AppConfig
import hu.szintai.hakiosk.config.ConfigRepository
import hu.szintai.hakiosk.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tiny HTTP server that shares the front camera, **only while someone is watching**.
 *
 *  - `GET /snapshot.jpg?t=TOKEN`     one JPEG
 *  - `GET /stream.mjpeg?t=TOKEN`     multipart MJPEG (what Home Assistant's `mjpeg` camera reads)
 *
 * No frames are encoded while no client is connected ([wantsFrames] is false), and nothing is ever
 * pushed anywhere: Home Assistant pulls the stream when a dashboard actually shows it.
 * Access needs the secret token; the server is plain HTTP, meant for the home network only.
 */
@Singleton
class CameraFrameServer @Inject constructor(
    private val configRepository: ConfigRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val clients = AtomicInteger(0)
    private val pool = Executors.newCachedThreadPool()

    @Volatile private var latest: ByteArray? = null
    @Volatile private var seq = 0L
    @Volatile private var token = ""
    private var server: ServerSocket? = null
    private var port = -1

    /** The port actually bound (differs from the configured one only when that is 0, as in tests). */
    val localPort: Int get() = server?.localPort ?: -1

    /** True while at least one client is connected, i.e. frames should be produced. */
    val wantsFrames: Boolean get() = clients.get() > 0

    init {
        scope.launch { configRepository.config.collect { apply(it) } }
    }

    fun publish(jpeg: ByteArray) {
        latest = jpeg
        seq++
    }

    @Synchronized
    private fun apply(config: AppConfig) {
        if (!config.cameraShare) {
            stop()
            return
        }
        if (config.cameraToken.isBlank()) {
            scope.launch { configRepository.update { it.copy(cameraToken = newToken()) } }
            return
        }
        token = config.cameraToken
        if (server == null || port != config.cameraPort) {
            stop()
            start(config.cameraPort)
        }
    }

    private fun start(p: Int) {
        try {
            val s = ServerSocket(p)
            server = s
            port = p
            Log.i(TAG, "camera share listening on :$p")
            Thread({
                while (!s.isClosed) {
                    try {
                        val socket = s.accept()
                        pool.execute { handle(socket) }
                    } catch (e: Exception) {
                        if (!s.isClosed) Log.w(TAG, "accept failed: ${e.message}")
                    }
                }
            }, "camera-share-accept").apply { isDaemon = true }.start()
        } catch (e: Exception) {
            Log.w(TAG, "cannot start camera share on :$p: ${e.message}")
            server = null
        }
    }

    private fun stop() {
        server?.close()
        server = null
        port = -1
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            s.soTimeout = READ_TIMEOUT_MS
            val reader = s.getInputStream().bufferedReader()
            val requestLine = reader.readLine() ?: return
            while (true) { // drain headers
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }
            val target = requestLine.split(' ').getOrNull(1) ?: return respond(s.getOutputStream(), 400, "Bad Request")
            val path = target.substringBefore('?')
            val query = target.substringAfter('?', "").split('&').filter { it.contains('=') }
                .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
            if (!constantTimeEquals(query["t"].orEmpty(), token)) return respond(s.getOutputStream(), 403, "Forbidden")

            val out = s.getOutputStream()
            clients.incrementAndGet()
            try {
                when (path) {
                    "/snapshot.jpg" -> snapshot(out)
                    "/stream.mjpeg" -> stream(out, s)
                    else -> respond(out, 404, "Not Found")
                }
            } catch (e: Exception) {
                // client went away
            } finally {
                clients.decrementAndGet()
            }
        }
    }

    private fun snapshot(out: OutputStream) {
        val frame = awaitFrame(seq, SNAPSHOT_WAIT_MS) ?: return respond(out, 503, "No frame yet")
        out.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.size}\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray(),
        )
        out.write(frame)
        out.flush()
    }

    private fun stream(out: OutputStream, socket: Socket) {
        out.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=frame\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray(),
        )
        var last = -1L
        while (!socket.isClosed) {
            val frame = awaitFrame(last, STREAM_FRAME_WAIT_MS) ?: break
            last = seq
            out.write("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.size}\r\n\r\n".toByteArray())
            out.write(frame)
            out.write("\r\n".toByteArray())
            out.flush()
            Thread.sleep(FRAME_INTERVAL_MS)
        }
    }

    /** Waits for a frame newer than [after]; null on timeout. */
    private fun awaitFrame(after: Long, timeoutMs: Long): ByteArray? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val frame = latest
            if (frame != null && seq != after) return frame
            Thread.sleep(POLL_MS)
        }
        return null
    }

    private fun respond(out: OutputStream, code: Int, text: String) {
        out.write("HTTP/1.1 $code $text\r\nContent-Length: ${text.length}\r\nConnection: close\r\n\r\n$text".toByteArray())
        out.flush()
    }

    private fun constantTimeEquals(a: String, b: String) =
        b.isNotEmpty() && MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    private fun newToken(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "CameraShare"
        const val READ_TIMEOUT_MS = 5_000
        const val SNAPSHOT_WAIT_MS = 3_000L
        const val STREAM_FRAME_WAIT_MS = 5_000L
        const val FRAME_INTERVAL_MS = 120L
        const val POLL_MS = 25L
    }
}
