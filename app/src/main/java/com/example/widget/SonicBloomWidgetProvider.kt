package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.SonicBloomApplication
import com.example.playback.SonicBloomPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max

class SonicBloomWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? SonicBloomApplication
                val snapshot = app?.container?.preferencesRepository?.getSavedPlaybackSession()
                val title = snapshot?.lastTrackTitle?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.widget_empty_title)
                val artist = snapshot?.lastTrackArtist?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.widget_empty_subtitle)
                val artUri = snapshot?.lastTrackArtUri
                val isPlaying = snapshot?.lastIsPlaying ?: false

                val views = buildRemoteViews(context, title, artist, artUri, isPlaying)
                appWidgetIds.forEach { appWidgetId ->
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun updateWidgets(
            context: Context,
            title: String?,
            artist: String?,
            artUri: String?,
            isPlaying: Boolean
        ) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val component = ComponentName(context, SonicBloomWidgetProvider::class.java)
                val ids = manager.getAppWidgetIds(component)
                if (ids.isEmpty()) return

                val resolvedTitle = title?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.widget_empty_title)
                val resolvedArtist = artist?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.widget_empty_subtitle)

                val views = buildRemoteViews(context, resolvedTitle, resolvedArtist, artUri, isPlaying)
                manager.updateAppWidget(ids, views)
            }
        }

        private fun buildRemoteViews(
            context: Context,
            title: String,
            artist: String,
            artUri: String?,
            isPlaying: Boolean
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_sonicbloom)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)
            views.setImageViewResource(
                R.id.widget_btn_play_pause,
                if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            )

            val bitmap = decodeWidgetArtwork(context, artUri)
            if (bitmap != null) {
                views.setImageViewBitmap(R.id.widget_artwork, bitmap)
            } else {
                views.setImageViewResource(R.id.widget_artwork, R.drawable.img_vinyl_bloom_art)
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val launchPending = PendingIntent.getActivity(
                context,
                100,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, launchPending)

            views.setOnClickPendingIntent(
                R.id.widget_btn_prev,
                serviceActionPendingIntent(context, SonicBloomPlaybackService.ACTION_WIDGET_PREVIOUS, 101)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_play_pause,
                serviceActionPendingIntent(context, SonicBloomPlaybackService.ACTION_WIDGET_PLAY_PAUSE, 102)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_next,
                serviceActionPendingIntent(context, SonicBloomPlaybackService.ACTION_WIDGET_NEXT, 103)
            )

            return views
        }

        private fun serviceActionPendingIntent(
            context: Context,
            action: String,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, SonicBloomPlaybackService::class.java).apply {
                this.action = action
            }
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            } else {
                PendingIntent.getService(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
        }

        private fun decodeWidgetArtwork(context: Context, artUriString: String?): Bitmap? {
            if (artUriString.isNullOrBlank()) return null
            return runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                openBitmapBounds(context, artUriString, bounds)
                val maxDim = max(bounds.outWidth, bounds.outHeight)
                var sample = 1
                while (maxDim / sample > 128) {
                    sample *= 2
                }
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                decodeBitmapWithOptions(context, artUriString, opts)
            }.getOrNull()
        }

        private fun openBitmapBounds(context: Context, artUriString: String, opts: BitmapFactory.Options) {
            if (artUriString.startsWith("/")) {
                BitmapFactory.decodeFile(artUriString, opts)
            } else {
                val uri = Uri.parse(artUriString)
                if (uri.scheme == "file" && uri.path != null) {
                    BitmapFactory.decodeFile(uri.path, opts)
                } else {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }
                }
            }
        }

        private fun decodeBitmapWithOptions(
            context: Context,
            artUriString: String,
            opts: BitmapFactory.Options
        ): Bitmap? {
            return if (artUriString.startsWith("/")) {
                BitmapFactory.decodeFile(artUriString, opts)
            } else {
                val uri = Uri.parse(artUriString)
                if (uri.scheme == "file" && uri.path != null) {
                    BitmapFactory.decodeFile(File(uri.path!!).absolutePath, opts)
                } else {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }
                }
            }
        }
    }
}
