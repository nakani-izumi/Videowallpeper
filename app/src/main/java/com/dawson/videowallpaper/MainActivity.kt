package com.dawson.videowallpaper

import android.Manifest
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.dawson.videowallpaper.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { getSharedPreferences("vwp_prefs", Context.MODE_PRIVATE) }

    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleVideoUri(it) }
    }

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pickVideo.launch("video/mp4")
        else Toast.makeText(this, "Permission requise", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        refreshUI()

        binding.btnPickVideo.setOnClickListener { checkPermissionAndPick() }

        binding.switchFreeze.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("freeze_last_frame", checked).apply()
        }

        binding.btnSetWallpaper.setOnClickListener {
            if (prefs.getString("video_path", null) == null) {
                Toast.makeText(this, "Choisissez d'abord une video", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            setLiveWallpaper()
        }

        binding.btnSetLock.setOnClickListener {
            val path = prefs.getString("video_path", null)
            if (path == null) {
                Toast.makeText(this, "Choisissez d'abord une video", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            setLockScreenFromLastFrame(path)
        }
    }

    private fun refreshUI() {
        val savedPath = prefs.getString("video_path", null)
        binding.tvVideoName.text = if (savedPath != null) File(savedPath).name
                                   else "Aucune video selectionnee"
        binding.switchFreeze.isChecked = prefs.getBoolean("freeze_last_frame", true)
    }

    private fun checkPermissionAndPick() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_VIDEO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            pickVideo.launch("video/mp4")
        } else {
            requestPermission.launch(permission)
        }
    }

    private fun handleVideoUri(uri: Uri) {
        try {
            Toast.makeText(this, "Import en cours...", Toast.LENGTH_SHORT).show()
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val file = File(filesDir, "wallpaper_video.mp4")
            file.outputStream().use { output -> inputStream.copyTo(output) }
            prefs.edit().putString("video_path", file.absolutePath).apply()
            refreshUI()
            Toast.makeText(this, "Video importee avec succes !", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur: " + (e.message ?: "inconnue"), Toast.LENGTH_LONG).show()
        }
    }

    private fun setLiveWallpaper() {
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@MainActivity, VideoWallpaperService::class.java)
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur: " + (e.message ?: "inconnue"), Toast.LENGTH_LONG).show()
        }
    }

    private fun setLockScreenFromLastFrame(videoPath: String) {
        try {
            Toast.makeText(this, "Extraction de la derniere frame...", Toast.LENGTH_SHORT).show()
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoPath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLong() ?: 0L
            val frameUs = maxOf(0L, durationMs - 200L) * 1000L
            val bitmap = retriever.getFrameAtTime(frameUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()

            if (bitmap != null) {
                val wm = WallpaperManager.getInstance(this)
                wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                Toast.makeText(this, "Fond verrouillage defini !", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Impossible d'extraire la frame", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur: " + (e.message ?: "inconnue"), Toast.LENGTH_LONG).show()
        }
    }
}
