package ai.milf.client.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import android.util.Base64
import android.view.Display
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ScreenshotCapture {
    suspend fun captureBase64Png(service: AccessibilityService): String {
        val bitmap = takeScreenshot(service)
        val bytes = try {
            ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private suspend fun takeScreenshot(service: AccessibilityService): Bitmap =
        suspendCancellableCoroutine { continuation ->
            val executor = Executors.newSingleThreadExecutor()
            continuation.invokeOnCancellation { executor.shutdownNow() }
            try {
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    executor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                            val bitmap = runCatching {
                                val buffer = screenshot.hardwareBuffer
                                try {
                                    bitmapFromHardwareBuffer(buffer, screenshot.colorSpace)
                                } finally {
                                    buffer.close()
                                }
                            }
                            executor.shutdown()
                            bitmap.fold(
                                onSuccess = { wrappedBitmap ->
                                    if (continuation.isActive) {
                                        continuation.resume(wrappedBitmap)
                                    } else {
                                        wrappedBitmap.recycle()
                                    }
                                },
                                onFailure = { error ->
                                    if (continuation.isActive) {
                                        continuation.resumeWithException(error)
                                    }
                                }
                            )
                        }

                        override fun onFailure(errorCode: Int) {
                            executor.shutdown()
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    IllegalStateException("Screenshot failed: $errorCode")
                                )
                            }
                        }
                    }
                )
            } catch (error: Throwable) {
                executor.shutdownNow()
                if (continuation.isActive) {
                    continuation.resumeWithException(error)
                }
            }
        }

    private fun bitmapFromHardwareBuffer(
        buffer: HardwareBuffer,
        colorSpace: ColorSpace
    ): Bitmap =
        Bitmap.wrapHardwareBuffer(buffer, colorSpace)
            ?.copy(Bitmap.Config.ARGB_8888, false)
            ?: throw IllegalStateException("Could not wrap screenshot buffer")
}
