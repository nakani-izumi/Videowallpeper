package com.dawson.videowallpaper

import android.content.Context
import android.media.MediaPlayer
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine() {

        private var mediaPlayer: MediaPlayer? = null

        private val prefs by lazy {
            getSharedPreferences("vwp_prefs", Context.MODE_PRIVATE)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            startVideo(holder)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            releasePlayer()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            // Intentionally empty — keep running even when not visible
        }

        private fun startVideo(holder: SurfaceHolder) {
            val path = prefs.getString("video_path", null) ?: return
            val freeze = prefs.getBoolean("freeze_last_frame", true)

            releasePlayer()

            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(path)
                    setSurface(holder.surface)
                    isLooping = !freeze
                    prepareAsync()

                    setOnPreparedListener { mp -> mp.start() }

                    if (freeze) {
                        setOnCompletionListener { mp ->
                            try {
                                val last = mp.duration - 50
                                if (last > 0) mp.seekTo(last)
                            } catch (e: Exception) {
                                // ignore
                            }
                        }
                    }

                    setOnErrorListener { _, _, _ ->
                        releasePlayer()
                        true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun releasePlayer() {
            try {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) mp.stop()
                    mp.reset()
                    mp.release()
                }
            } catch (e: Exception) {
                // ignore
            }
            mediaPlayer = null
        }
    }
}
