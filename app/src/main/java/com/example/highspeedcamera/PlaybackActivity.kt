package com.example.highspeedcamera

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.highspeedcamera.databinding.ActivityPlaybackBinding
import org.json.JSONObject
import java.io.File

class PlaybackActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PlaybackActivity"
        const val EXTRA_VIDEO_PATH = "video_path"
        const val EXTRA_META_PATH = "meta_path"
    }

    private lateinit var binding: ActivityPlaybackBinding
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val progressRunnable = object : Runnable {
        override fun run() {
            val mp = mediaPlayer ?: return
            if (mp.isPlaying) {
                val current = mp.currentPosition
                val total = mp.duration
                if (total > 0) {
                    binding.seekbarPlayback.progress = (current * 100 / total)
                    binding.tvCurrentTime.text = formatTime(current)
                }
                handler.postDelayed(this, 200)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaybackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val videoPath = intent.getStringExtra(EXTRA_VIDEO_PATH)
        val metaPath  = intent.getStringExtra(EXTRA_META_PATH)

        if (videoPath == null) {
            Toast.makeText(this, "No video to play", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadMetadata(metaPath)
        setupVideoPlayer(videoPath)
        setupControls(videoPath)
    }

    private fun loadMetadata(metaPath: String?) {
        if (metaPath == null) {
            binding.cardMetadata.visibility = View.GONE
            return
        }
        val metaFile = File(metaPath)
        if (!metaFile.exists()) {
            binding.cardMetadata.visibility = View.GONE
            return
        }

        try {
            val json = JSONObject(metaFile.readText())
            val fps       = json.optInt("fps", 0)
            val resolution= json.optString("resolution", "—")
            val iso       = json.optInt("iso", 0)
            val shutter   = json.optString("shutter_speed", "—")
            val timestamp = json.optString("timestamp_utc", "—")
            val duration  = json.optDouble("duration_seconds", 0.0)
            val manual    = json.optBoolean("manual_exposure", false)

            binding.tvMetaFps.text        = "$fps FPS"
            binding.tvMetaResolution.text = resolution
            binding.tvMetaIso.text        = "ISO $iso"
            binding.tvMetaShutter.text    = shutter
            binding.tvMetaTimestamp.text  = timestamp.replace("T", "  ").replace("Z", " UTC")
            binding.tvMetaDuration.text   = String.format("%.1f s", duration)
            binding.tvMetaExposureMode.text = if (manual) "Manual" else "Auto"
            binding.cardMetadata.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load metadata", e)
            binding.cardMetadata.visibility = View.GONE
        }
    }

    private fun setupVideoPlayer(videoPath: String) {
        val videoFile = File(videoPath)
        if (!videoFile.exists()) {
            Toast.makeText(this, "Video file not found", Toast.LENGTH_LONG).show()
            return
        }

        binding.tvVideoPath.text = videoFile.name

        binding.videoView.setOnPreparedListener { mp ->
            mediaPlayer = mp
            mp.isLooping = false
            val dur = mp.duration
            binding.tvTotalTime.text = formatTime(dur)
            binding.tvCurrentTime.text = "00:00"
            binding.progressLoading.visibility = View.GONE
            binding.btnPlay.isEnabled = true
            binding.btnPlay.text = "▶  Play"

            // Note: playback speed is controlled by the container FPS.
            // A 240fps file played in a standard player shows slow motion (240/30 = 8x slow).
            // The video IS recorded at 240fps and plays back at that declared frame rate.
            mp.start()
            binding.btnPlay.text = "⏸  Pause"
            handler.post(progressRunnable)
        }

        binding.videoView.setOnCompletionListener {
            binding.btnPlay.text = "▶  Play"
            handler.removeCallbacks(progressRunnable)
        }

        binding.videoView.setOnErrorListener { _, what, extra ->
            Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
            Toast.makeText(this, "Playback error ($what). Try opening in Gallery.", Toast.LENGTH_LONG).show()
            true
        }

        binding.progressLoading.visibility = View.VISIBLE
        binding.btnPlay.isEnabled = false
        binding.videoView.setVideoURI(Uri.fromFile(videoFile))
        binding.videoView.requestFocus()
    }

    private fun setupControls(videoPath: String) {
        binding.btnPlay.setOnClickListener {
            val mp = mediaPlayer ?: return@setOnClickListener
            if (mp.isPlaying) {
                mp.pause()
                handler.removeCallbacks(progressRunnable)
                binding.btnPlay.text = "▶  Play"
            } else {
                mp.start()
                handler.post(progressRunnable)
                binding.btnPlay.text = "⏸  Pause"
            }
        }

        binding.btnRestart.setOnClickListener {
            mediaPlayer?.seekTo(0)
            mediaPlayer?.start()
            handler.post(progressRunnable)
            binding.btnPlay.text = "⏸  Pause"
        }

        binding.seekbarPlayback.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val mp = mediaPlayer ?: return
                    val pos = (mp.duration * progress / 100)
                    mp.seekTo(pos)
                    binding.tvCurrentTime.text = formatTime(pos)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        binding.btnOpenGallery.setOnClickListener {
            val file = File(videoPath)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(file), "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "No video player found", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnShare.setOnClickListener {
            val file = File(videoPath)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Video"))
        }

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun formatTime(ms: Int): String {
        val s = ms / 1000
        return String.format("%02d:%02d", s / 60, s % 60)
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }
}
