package com.yapnix.game.recording

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.yapnix.game.audio.PcmClock
import com.yapnix.game.audio.SharedMicrophone
import java.io.File
import java.nio.ByteBuffer

/** Adds AAC from the shared PCM without decoding/re-encoding the captured H.264 picture. */
object RecordingAudioMuxer {
    fun combine(video: File, pcm: File, output: File, audioOffsetUs: Long) {
        check(pcm.length() > 0 && pcm.length() % 2 == 0L) { "Missing recorded audio" }
        val extractor = MediaExtractor()
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var codecStarted = false
        try {
            extractor.setDataSource(video.absolutePath)
            val videoIndex = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
            extractor.selectTrack(videoIndex)
            val writer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { muxer = it }
            val videoTrack = writer.addTrack(extractor.getTrackFormat(videoIndex))
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SharedMicrophone.RATE, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start(); codecStarted = true
            var audioTrack = -1
            var bytes = 0L
            var lastAudioUs = -1L
            var inputEnded = false
            var outputEnded = false
            var lastOutput = System.nanoTime()
            val info = MediaCodec.BufferInfo()
            pcm.inputStream().buffered().use { source ->
                while (!outputEnded) {
                    check(System.nanoTime() - lastOutput < 15_000_000_000L) { "Audio encoder stalled" }
                    if (!inputEnded) {
                        val index = codec.dequeueInputBuffer(10_000)
                        if (index >= 0) {
                            val target = checkNotNull(codec.getInputBuffer(index)); target.clear()
                            val buffer = ByteArray(minOf(target.remaining(), 8192) / 2 * 2)
                            val count = source.read(buffer)
                            val timestamp = PcmClock.timestampUs(bytes, audioOffsetUs)
                            if (count < 0) {
                                codec.queueInputBuffer(index, 0, 0, timestamp, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                target.put(buffer, 0, count)
                                codec.queueInputBuffer(index, 0, count, timestamp, 0); bytes += count
                            }
                        }
                    }
                    when (val index = codec.dequeueOutputBuffer(info, 10_000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(!muxerStarted)
                            audioTrack = writer.addTrack(codec.outputFormat)
                            writer.start(); muxerStarted = true; lastOutput = System.nanoTime()
                        }
                        else -> if (index >= 0) {
                            lastOutput = System.nanoTime()
                            val encoded = checkNotNull(codec.getOutputBuffer(index))
                            if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                check(muxerStarted)
                                encoded.position(info.offset); encoded.limit(info.offset + info.size)
                                info.presentationTimeUs = maxOf(info.presentationTimeUs, lastAudioUs + 1)
                                lastAudioUs = info.presentationTimeUs
                                writer.writeSampleData(audioTrack, encoded, info)
                            }
                            outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec.releaseOutputBuffer(index, false)
                        }
                    }
                }
            }
            val size = extractor.getTrackFormat(videoIndex).let { if (it.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) it.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0 }
            val buffer = ByteBuffer.allocateDirect(maxOf(size, 4 * 1024 * 1024))
            while (true) {
                buffer.clear()
                val count = extractor.readSampleData(buffer, 0)
                if (count < 0) break
                info.set(0, count, extractor.sampleTime, if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                writer.writeSampleData(videoTrack, buffer, info)
                extractor.advance()
            }
            writer.stop(); muxerStarted = false
        } finally {
            if (codecStarted) runCatching { codec.stop() }
            codec.release(); extractor.release()
            if (muxerStarted) runCatching { muxer?.stop() }
            muxer?.release()
        }
    }
}
