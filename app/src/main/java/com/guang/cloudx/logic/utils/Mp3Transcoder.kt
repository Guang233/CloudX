package com.guang.cloudx.logic.utils

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.naman14.androidlame.AndroidLame
import com.naman14.androidlame.LameBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.max

object Mp3Transcoder {
    private const val TIMEOUT_US = 10_000L
    private const val DEFAULT_BITRATE_KBPS = 192

    suspend fun transcodeM4aToMp3(
        inputFile: File,
        outputFile: File,
        bitrateKbps: Int = DEFAULT_BITRATE_KBPS
    ) = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var lame: AndroidLame? = null
        var completed = false

        try {
            if (outputFile.exists()) outputFile.delete()

            extractor = MediaExtractor().apply {
                setDataSource(inputFile.absolutePath)
            }

            val trackIndex = findAudioTrack(extractor)
            if (trackIndex < 0) throw IllegalArgumentException("未找到音频轨道")

            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalArgumentException("无法识别音频编码")
            if (!mime.startsWith("audio/")) throw IllegalArgumentException("不是音频文件")

            decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }

            FileOutputStream(outputFile).use { mp3Output ->
                val bufferInfo = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                var outputFormat = inputFormat
                var mp3Buffer = ByteArray(8192)

                while (!outputDone) {
                    coroutineContext.ensureActive()

                    if (!inputDone) {
                        val inputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                        if (inputBufferIndex >= 0) {
                            val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                                ?: throw IllegalStateException("无法获取解码输入缓冲区")
                            inputBuffer.clear()
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)

                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    0,
                                    0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    sampleSize,
                                    extractor.sampleTime,
                                    extractor.sampleFlags
                                )
                                extractor.advance()
                            }
                        }
                    }

                    when (val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)) {
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            outputFormat = decoder.outputFormat
                            ensureSupportedPcm(outputFormat)
                        }

                        else -> {
                            if (outputBufferIndex >= 0) {
                                val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)
                                    ?: throw IllegalStateException("无法获取解码输出缓冲区")

                                if (bufferInfo.size > 0) {
                                    ensureSupportedPcm(outputFormat)
                                    val sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                    val channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                    require(channelCount in 1..2) { "暂不支持 $channelCount 声道音频转 MP3" }

                                    if (lame == null) {
                                        lame = LameBuilder()
                                            .setInSampleRate(sampleRate)
                                            .setOutSampleRate(sampleRate)
                                            .setOutChannels(2)
                                            .setOutBitrate(bitrateKbps)
                                            .setMode(LameBuilder.Mode.JSTEREO)
                                            .setQuality(5)
                                            .build()
                                    }

                                    val pcmBuffer = outputBuffer.sliceFor(bufferInfo)
                                    val samplesPerChannel = pcmBuffer.remaining() / 2 / channelCount
                                    val requiredMp3BufferSize = max(8192, (samplesPerChannel * 1.25 + 7200).toInt())
                                    if (mp3Buffer.size < requiredMp3BufferSize) {
                                        mp3Buffer = ByteArray(requiredMp3BufferSize)
                                    }

                                    val encodedSize = encodePcm(
                                        pcmBuffer = pcmBuffer,
                                        channelCount = channelCount,
                                        samplesPerChannel = samplesPerChannel,
                                        lame = lame,
                                        mp3Buffer = mp3Buffer
                                    )
                                    if (encodedSize > 0) {
                                        mp3Output.write(mp3Buffer, 0, encodedSize)
                                    }
                                }

                                outputDone =
                                    bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                decoder.releaseOutputBuffer(outputBufferIndex, false)
                            }
                        }
                    }
                }

                lame?.flush(mp3Buffer)?.takeIf { it > 0 }?.let { size ->
                    mp3Output.write(mp3Buffer, 0, size)
                }
            }

            completed = true
        } finally {
            try {
                decoder?.stop()
            } catch (_: Exception) {
            }
            decoder?.release()
            extractor?.release()
            lame?.close()

            if (!completed) {
                outputFile.delete()
            }
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) return i
        }
        return -1
    }

    private fun ensureSupportedPcm(format: MediaFormat) {
        if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            val encoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
            require(encoding == AudioFormat.ENCODING_PCM_16BIT) {
                "暂不支持非 16-bit PCM 音频转 MP3"
            }
        }
    }

    private fun ByteBuffer.sliceFor(bufferInfo: MediaCodec.BufferInfo): ByteBuffer {
        position(bufferInfo.offset)
        limit(bufferInfo.offset + bufferInfo.size)
        return slice().order(ByteOrder.LITTLE_ENDIAN)
    }

    private fun encodePcm(
        pcmBuffer: ByteBuffer,
        channelCount: Int,
        samplesPerChannel: Int,
        lame: AndroidLame?,
        mp3Buffer: ByteArray
    ): Int {
        if (lame == null || samplesPerChannel <= 0) return 0

        val shortCount = samplesPerChannel * channelCount
        return if (channelCount == 1) {
            val mono = ShortArray(shortCount)
            for (i in mono.indices) {
                mono[i] = pcmBuffer.getShort()
            }
            lame.encode(mono, mono, samplesPerChannel, mp3Buffer)
        } else {
            val stereo = ShortArray(shortCount)
            for (i in stereo.indices) {
                stereo[i] = pcmBuffer.getShort()
            }
            lame.encodeBufferInterLeaved(stereo, samplesPerChannel, mp3Buffer)
        }
    }
}
