package com.aryanmaheshwari.taskmanager.utils

import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.util.UUID

/**
 * Utility class for recording audio from the microphone.
 * Handles recording lifecycle, file management, and error handling.
 */
class AudioRecorder(private val cacheDir: File) {

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null

    companion object {
        private const val TAG = "AudioRecorder"
    }

    /**
     * Starts recording audio. Creates a temporary M4A file.
     * Must call [stopRecording] to finalize the file.
     *
     * @return true if recording started successfully, false otherwise
     */
    fun startRecording(): Boolean {
        return try {
            // Using .m4a as it matches MPEG_4/AAC better than .mp3
            recordingFile = File(cacheDir, "voice_${UUID.randomUUID()}.m4a")
            Log.d(TAG, "Starting recording to: ${recordingFile?.absolutePath}")

            @Suppress("DEPRECATION")
            mediaRecorder = MediaRecorder()

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(recordingFile?.absolutePath)
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaRecorder", e)
            mediaRecorder = null
            recordingFile?.delete()
            recordingFile = null
            false
        }
    }

    /**
     * Stops recording and returns the audio file.
     *
     * @return the audio file if recording succeeded, null if recording failed
     */
    fun stopRecording(): File? {
        Log.d(TAG, "stopRecording() requested")
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            Log.d(TAG, "Recording stopped successfully. File size: ${recordingFile?.length()} bytes")
            recordingFile
        } catch (e: Exception) {
            // This is often triggered if the recording was too short (< 1 sec)
            Log.e(TAG, "MediaRecorder.stop() failed. (Recording might be too short)", e)
            mediaRecorder?.release()
            mediaRecorder = null
            recordingFile?.delete()
            recordingFile = null
            null
        }
    }

    /**
     * Cancels recording and cleans up the file.
     */
    fun cancelRecording() {
        Log.d(TAG, "cancelRecording() requested")
        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    // Ignore if not started
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while cancelling recording", e)
        }
        mediaRecorder = null
        recordingFile?.delete()
        recordingFile = null
    }

    /**
     * Cleans up a recording file (for example, after it's been sent to Gemini).
     */
    fun deleteRecordingFile(file: File?) {
        try {
            file?.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting recording file", e)
        }
    }
}
