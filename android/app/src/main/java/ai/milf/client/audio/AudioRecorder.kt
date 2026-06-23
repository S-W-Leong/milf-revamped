package ai.milf.client.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorder(
    private val context: Context
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start() {
        cancel()

        val file = File.createTempFile("milf-goal-", ".m4a", context.cacheDir)
        val newRecorder = createRecorder()

        try {
            newRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            outputFile = file
            recorder = newRecorder
        } catch (error: Throwable) {
            runCatching { newRecorder.release() }
            file.delete()
            throw error
        }
    }

    fun stop(): ByteArray {
        val activeRecorder = recorder ?: error("Recorder is not running")
        val file = outputFile ?: error("Recording file missing")

        return try {
            activeRecorder.stop()
            file.readBytes()
        } finally {
            runCatching { activeRecorder.release() }
            file.delete()
            recorder = null
            outputFile = null
        }
    }

    fun cancel() {
        val activeRecorder = recorder
        val file = outputFile

        recorder = null
        outputFile = null

        if (activeRecorder != null) {
            runCatching { activeRecorder.stop() }
            runCatching { activeRecorder.release() }
        }
        file?.delete()
    }

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context.applicationContext)
        } else {
            MediaRecorder()
        }
}
