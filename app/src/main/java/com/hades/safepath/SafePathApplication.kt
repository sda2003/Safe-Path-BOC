package com.hades.safepath

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration as OsmdroidConfiguration
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class SafePathApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        configureOsmdroidOffline()
        createNotificationChannel()
    }

    /**
     * Point OsmDroid at internal storage before any MapView inflates,
     * so the default provider never writes OSM 403 tiles to external cache.
     */
    private fun configureOsmdroidOffline() {
        val base = File(filesDir, "osmdroid").also { it.mkdirs() }
        val cache = File(base, "tiles").also { it.mkdirs() }
        OsmdroidConfiguration.getInstance().apply {
            load(this@SafePathApplication, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = packageName
            osmdroidBasePath = base
            osmdroidTileCache = cache
            isMapTileDownloaderFollowRedirects = false
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "safepath_alerts",
                "SafePath Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical disaster alerts and warnings"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
