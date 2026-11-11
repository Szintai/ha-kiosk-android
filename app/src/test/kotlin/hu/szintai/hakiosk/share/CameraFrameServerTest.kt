package hu.szintai.hakiosk.share

import hu.szintai.hakiosk.config.AppConfig
import hu.szintai.hakiosk.config.ConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.Socket
import kotlin.concurrent.thread

class CameraFrameServerTest {
    private val state = MutableStateFlow(AppConfig(cameraShare = true, cameraPort = 0, cameraToken = "secret"))
    private val repo = object : ConfigRepository {
        override val config: Flow<AppConfig> = state
        override suspend fun update(transform: (AppConfig) -> AppConfig) {
            state.value = transform(state.value)
        }
    }
    private lateinit var server: CameraFrameServer
    private var publisher: Thread? = null

    @Before
    fun setUp() {
        server = CameraFrameServer(repo, CoroutineScope(Dispatchers.Unconfined))
    }

    @After
    fun tearDown() {
        publisher?.interrupt()
        state.value = state.value.copy(cameraShare = false) // closes the socket
    }

    /** Publishes a "JPEG" every 20 ms, like the camera analyser does while a client is watching. */
    private fun startCamera() {
        publisher = thread(isDaemon = true) {
            var n = 0
            try {
                while (true) {
                    if (server.wantsFrames) server.publish(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), (n++).toByte(), 0xFF.toByte(), 0xD9.toByte()))
                    Thread.sleep(20)
                }
            } catch (_: InterruptedException) {
            }
        }
    }

    private fun request(path: String, maxBytes: Int = 4096): Pair<String, ByteArray> {
        Socket("127.0.0.1", server.localPort).use { s ->
            s.soTimeout = 4000
            s.getOutputStream().write("GET $path HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
            val buf = ByteArray(maxBytes)
            var n = 0
            while (n < maxBytes) {
                val r = s.getInputStream().read(buf, n, maxBytes - n)
                if (r <= 0) break
                n += r
                // A stream never ends: stop once the first multipart boundary after the headers arrived.
                if (path.startsWith("/stream") && String(buf, 0, n, Charsets.ISO_8859_1).substringAfter("\r\n\r\n", "").startsWith("--frame")) break
            }
            val all = buf.copyOf(n)
            val text = String(all, Charsets.ISO_8859_1)
            return text.substringBefore("\r\n\r\n") to all.copyOfRange(text.indexOf("\r\n\r\n") + 4, all.size)
        }
    }

    @Test
    fun `wrong or missing token is forbidden`() {
        assertTrue(request("/snapshot.jpg?t=nope").first.startsWith("HTTP/1.1 403"))
        assertTrue(request("/snapshot.jpg").first.startsWith("HTTP/1.1 403"))
    }

    @Test
    fun `unknown path is 404 with a valid token`() {
        assertTrue(request("/nothing?t=secret").first.startsWith("HTTP/1.1 404"))
    }

    @Test
    fun `snapshot returns the published jpeg`() {
        startCamera()
        val (head, body) = request("/snapshot.jpg?t=secret")
        assertTrue(head, head.startsWith("HTTP/1.1 200"))
        assertTrue(head.contains("Content-Type: image/jpeg"))
        assertEquals(0xFF.toByte(), body[0])
        assertEquals(0xD8.toByte(), body[1])
    }

    @Test
    fun `stream is multipart mjpeg`() {
        startCamera()
        val (head, body) = request("/stream.mjpeg?t=secret")
        assertTrue(head.contains("multipart/x-mixed-replace; boundary=frame"))
        assertTrue(String(body, Charsets.ISO_8859_1).startsWith("--frame"))
    }

    @Test
    fun `no frames are wanted while nobody is connected`() {
        assertFalse(server.wantsFrames)
        startCamera()
        Socket("127.0.0.1", server.localPort).use { s ->
            s.getOutputStream().write("GET /stream.mjpeg?t=secret HTTP/1.1\r\n\r\n".toByteArray())
            val deadline = System.currentTimeMillis() + 3000
            while (!server.wantsFrames && System.currentTimeMillis() < deadline) Thread.sleep(20)
            assertTrue("frames wanted while a client watches", server.wantsFrames)
        }
        val deadline = System.currentTimeMillis() + 3000
        while (server.wantsFrames && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertFalse("frames stop once the client is gone", server.wantsFrames)
    }
}
