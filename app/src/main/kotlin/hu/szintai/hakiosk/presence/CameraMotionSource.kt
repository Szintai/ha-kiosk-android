package hu.szintai.hakiosk.presence

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import hu.szintai.hakiosk.BuildConfig
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Front camera -> tiny luminance grids at a low frame rate. Frames are analysed in memory
 * and discarded; nothing is stored or sent anywhere.
 */
class CameraMotionSource(
    private val context: Context,
    private val onGrid: (IntArray) -> Unit,
) {
    private var provider: ProcessCameraProvider? = null
    private var executor: ExecutorService? = null
    private var lastFrameAt = 0L
    private var received = 0L

    /**
     * Binds the camera to [owner]'s lifecycle. CameraX then opens/closes it with the owner's
     * STARTED/STOPPED states by itself, so this only needs to be called once (idempotent) and
     * [stop] only when presence detection is switched off.
     */
    fun start(owner: LifecycleOwner) {
        Log.i(TAG, "start() executorSet=${executor != null} state=${owner.lifecycle.currentState}")
        if (executor != null) return
        val exec = Executors.newSingleThreadExecutor().also { executor = it }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (executor !== exec) return@addListener // stop() ran before we got here
            try {
                val cameraProvider = future.get().also { provider = it }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            ).build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(exec, ::analyze)
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
                Log.i(TAG, "bound: ${camera.cameraInfo}  analysisResolution=${analysis.resolutionInfo?.resolution}")
            } catch (e: Exception) {
                Log.e(TAG, "Camera start failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        val exec = executor
        executor = null // makes a still-pending start() a no-op
        provider?.unbindAll()
        provider = null
        exec?.shutdown()
    }

    private fun analyze(image: ImageProxy) {
        try {
            if (BuildConfig.DEBUG && ++received % 30L == 1L) {
                Log.i(TAG, "frames received=$received size=${image.width}x${image.height} format=${image.format} rot=${image.imageInfo.rotationDegrees}")
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastFrameAt < MIN_FRAME_INTERVAL_MS) return
            lastFrameAt = now
            onGrid(image.toLuminanceGrid())
        } catch (e: Exception) {
            Log.e(TAG, "analysis failed", e)
        } finally {
            image.close()
        }
    }

    /** Averages the Y plane into a [GRID_COLS] x [GRID_ROWS] grid (sampling, not a full scan). */
    private fun ImageProxy.toLuminanceGrid(): IntArray {
        val plane = planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val cellW = width / GRID_COLS
        val cellH = height / GRID_ROWS
        val grid = IntArray(GRID_COLS * GRID_ROWS)
        for (gy in 0 until GRID_ROWS) {
            for (gx in 0 until GRID_COLS) {
                var sum = 0
                var n = 0
                var y = gy * cellH
                while (y < (gy + 1) * cellH) {
                    var x = gx * cellW
                    while (x < (gx + 1) * cellW) {
                        sum += buffer.get(y * rowStride + x * pixelStride).toInt() and 0xFF
                        n++
                        x += SAMPLE_STEP
                    }
                    y += SAMPLE_STEP
                }
                grid[gy * GRID_COLS + gx] = if (n > 0) sum / n else 0
            }
        }
        return grid
    }

    private companion object {
        const val TAG = "CameraMotionSource"
        const val ANALYSIS_WIDTH = 320
        const val ANALYSIS_HEIGHT = 240
        const val GRID_COLS = 32
        const val GRID_ROWS = 24
        const val SAMPLE_STEP = 3
        const val MIN_FRAME_INTERVAL_MS = 330L // ~3 fps
    }
}
