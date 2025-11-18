package io.github.sd155.aiadvent3.chat.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.io.IOException
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import kotlin.math.sqrt

internal class VoiceInput(
    private val silenceDurationMs: Int = 1000,
) {

    companion object {
        private const val SAMPLE_RATE = 44100.0f
    }

    suspend fun capture(): File = withContext(Dispatchers.IO) {
        val audioFormat = audioFormat()
        val info = DataLine.Info(TargetDataLine::class.java, audioFormat)
        if (!AudioSystem.isLineSupported(info))
            throw IllegalStateException("Microphone not supported")

        val targetDataLine = AudioSystem
            .getTargetDataLine(audioFormat)
            .apply {
                open(audioFormat)
                start()
            }
        val dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val outputFile = File("voice_$dateTime.wav")
        val audioQueue: BlockingQueue<ByteArray> = ArrayBlockingQueue(100)

        val recordingJob = CoroutineScope(Dispatchers.IO).launch {
            var fileOutputStream: FileOutputStream? = null
            try {
                fileOutputStream = FileOutputStream(outputFile)
                while (isActive || audioQueue.isNotEmpty()) {
                    val chunk = withContext(Dispatchers.IO) { audioQueue.poll() }
                    if (chunk != null)
                        fileOutputStream.write(chunk)
                    else
                        delay(1) // Small delay to prevent busy waiting
                }
            }
            catch (e: IOException) {
                e.printStackTrace()
            }
            finally {
                fileOutputStream?.close()
            }
        }

        val readBuffer = ByteArray(4096)
        var silenceStartTime: Long? = null
        val requiredSilenceMs = silenceDurationMs.toLong()
        var isRecording = true
        while (isRecording) {
            val bytesRead = targetDataLine.read(readBuffer, 0, readBuffer.size)
            if (bytesRead > 0) {
                val chunk = readBuffer.copyOf(bytesRead)
                audioQueue.offer(chunk)
                if (isSilent(readBuffer, bytesRead)) {
                    if (silenceStartTime == null)
                        silenceStartTime = System.currentTimeMillis()
                    else if (System.currentTimeMillis() - silenceStartTime >= requiredSilenceMs)
                        isRecording = false
                }
                else {
                    silenceStartTime = null
                }
            }
            yield()
        }

        recordingJob.cancelAndJoin()
        targetDataLine.stop()
        targetDataLine.close()

        val audioBytes = outputFile.readBytes()
        val wavFile = File("temp_$dateTime.wav")
        val audioInputStream = AudioInputStream(
            audioBytes.inputStream(),
            audioFormat,
            audioBytes.size.toLong() / audioFormat.frameSize
        )
        AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, wavFile)
        audioInputStream.close()
        outputFile.delete()
        wavFile.renameTo(outputFile)

        outputFile
    }

    private fun audioFormat(): AudioFormat {
        val encoding = AudioFormat.Encoding.PCM_SIGNED
        val sampleRate = SAMPLE_RATE
        val sampleSizeInBits = 16
        val channels = 1
        val frameSize = 2
        val frameRate = SAMPLE_RATE
        val bigEndian = false
        return AudioFormat(
            encoding,
            sampleRate,
            sampleSizeInBits,
            channels,
            frameSize,
            frameRate,
            bigEndian
        )
    }

    private fun isSilent(buffer: ByteArray, length: Int): Boolean {
        val silenceThreshold = 0.05f
        var sum = 0.0
        var samples = 0

        for (i in 0 until length - 1 step 2) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
            sum += (sample * sample).toDouble()
            samples++
        }

        if (samples == 0) return true

        val normalizedLevel = (sqrt(sum / samples) / 32768.0).toFloat().coerceIn(0f, 1f)
        println("SILENCE :: ${normalizedLevel < silenceThreshold} [$normalizedLevel]")
        return normalizedLevel < silenceThreshold
    }
}