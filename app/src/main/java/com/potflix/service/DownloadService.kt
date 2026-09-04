package com.potflix.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.potflix.data.local.dao.LocalDownloadDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import android.os.Environment

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var localDownloadDao: LocalDownloadDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient()
    private val activeDownloads = mutableMapOf<Long, Job>()

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_CANCEL = "ACTION_CANCEL"
        const val EXTRA_ID = "EXTRA_ID"
        const val EXTRA_URL = "EXTRA_URL"
        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val EXTRA_POSTER = "EXTRA_POSTER"
        
        // Status constants
        const val STATUS_PENDING = 1 shl 0
        const val STATUS_RUNNING = 1 shl 1
        const val STATUS_PAUSED = 1 shl 2
        const val STATUS_SUCCESSFUL = 1 shl 3
        const val STATUS_FAILED = 1 shl 4
        
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "download_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Starting Download Service...", 0, 100))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val action = it.action
            val id = it.getLongExtra(EXTRA_ID, -1)
            val url = it.getStringExtra(EXTRA_URL)
            val title = it.getStringExtra(EXTRA_TITLE)
            val poster = it.getStringExtra(EXTRA_POSTER)

            if (id != -1L) {
                when (action) {
                    ACTION_START -> if (url != null && title != null) startDownload(id, url, title, poster)
                    ACTION_PAUSE -> pauseDownload(id)
                    ACTION_RESUME -> if (url != null && title != null) startDownload(id, url, title, poster, isResume = true)
                    ACTION_CANCEL -> cancelDownload(id)
                }
            }
        }
        return START_STICKY
    }

    private fun startDownload(id: Long, url: String, title: String, poster: String?, isResume: Boolean = false) {
        if (activeDownloads.containsKey(id)) return // Already downloading
        
        val job = serviceScope.launch {
            try {
                // Initial DB Update
                var entity = localDownloadDao.getDownloadById(id)
                if (entity == null) {
                    entity = com.potflix.data.local.entity.LocalDownloadEntity(
                        downloadId = id,
                        title = title,
                        streamUrl = url,
                        localUri = null,
                        poster = poster,
                        status = STATUS_RUNNING,
                        progress = 0
                    )
                    localDownloadDao.insert(entity)
                } else {
                    entity = entity.copy(status = STATUS_RUNNING)
                    localDownloadDao.update(entity)
                }

                val downloadDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                val appDir = File(downloadDir, "PotFlix")
                if (!appDir.exists()) appDir.mkdirs()
                
                // Filename safe
                val safeTitle = title.replace("""[\\/:*?"<>|]""".toRegex(), "_")
                val file = File(appDir, "$safeTitle.mp4")

                var downloadedBytes = 0L
                if (isResume && file.exists()) {
                    downloadedBytes = file.length()
                } else if (!isResume && file.exists()) {
                    file.delete()
                }

                val requestBuilder = Request.Builder().url(url)
                if (downloadedBytes > 0) {
                    requestBuilder.addHeader("Range", "bytes=$downloadedBytes-")
                }
                
                val response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) throw Exception("Failed to connect")
                
                val contentType = response.header("Content-Type")
                if (contentType?.contains("text/html") == true) {
                    throw Exception("HTML page detected (possible captive portal or unreachable server)")
                }
                
                val body = response.body ?: throw Exception("Empty body")
                val contentLength = body.contentLength()
                val totalBytes = downloadedBytes + contentLength

                val randomAccessFile = RandomAccessFile(file, "rw")
                randomAccessFile.seek(downloadedBytes)

                val inputStream = body.byteStream()
                val buffer = ByteArray(8192)
                var bytesRead: Int = 0
                
                var lastUpdateTime = System.currentTimeMillis()
                var bytesSinceLastUpdate = 0L

                while (isActive && inputStream.read(buffer).also { bytesRead = it } != -1) {
                    randomAccessFile.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    bytesSinceLastUpdate += bytesRead

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdateTime >= 1000) {
                        val progress = if (totalBytes > 0) (downloadedBytes * 100 / totalBytes).toInt() else 0
                        val speed = bytesSinceLastUpdate // bytes per second
                        val eta = if (speed > 0) (totalBytes - downloadedBytes) / speed else 0L

                        // Update DB
                        val currentEntity = localDownloadDao.getDownloadById(id)
                        if (currentEntity != null && currentEntity.status != STATUS_PAUSED && currentEntity.status != STATUS_CANCELLED) {
                             localDownloadDao.update(
                                currentEntity.copy(
                                    progress = progress,
                                    totalBytes = totalBytes,
                                    bytesDownloaded = downloadedBytes,
                                    speedBytesPerSecond = speed,
                                    etaSeconds = eta,
                                    status = STATUS_RUNNING
                                )
                            )
                        }
                        
                        updateNotification("Downloading $title", progress, 100)

                        lastUpdateTime = currentTime
                        bytesSinceLastUpdate = 0
                    }
                }
                
                randomAccessFile.close()
                inputStream.close()
                
                if (isActive) {
                    // Completed successfully
                    val finalEntity = localDownloadDao.getDownloadById(id)
                    if (finalEntity != null) {
                        localDownloadDao.update(
                            finalEntity.copy(
                                progress = 100,
                                status = STATUS_SUCCESSFUL,
                                localUri = file.absolutePath,
                                speedBytesPerSecond = 0,
                                etaSeconds = 0
                            )
                        )
                    }
                    updateNotification("$title Downloaded", 100, 100)
                }

            } catch (e: Exception) {
                if (e is CancellationException) {
                    // Paused or Cancelled
                } else {
                    // Failed
                    val entity = localDownloadDao.getDownloadById(id)
                    if (entity != null) {
                        localDownloadDao.update(entity.copy(status = STATUS_FAILED))
                    }
                    updateNotification("Failed to download $title", 0, 0)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(applicationContext, "Failed to download $title. Check connection.", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } finally {
                activeDownloads.remove(id)
                checkStopSelf()
            }
        }
        activeDownloads[id] = job
    }

    private fun pauseDownload(id: Long) {
        activeDownloads[id]?.cancel()
        activeDownloads.remove(id)
        serviceScope.launch {
            val entity = localDownloadDao.getDownloadById(id)
            if (entity != null) {
                localDownloadDao.update(entity.copy(status = STATUS_PAUSED, speedBytesPerSecond = 0, etaSeconds = 0))
            }
            checkStopSelf()
        }
    }

    private fun cancelDownload(id: Long) {
        activeDownloads[id]?.cancel()
        activeDownloads.remove(id)
        serviceScope.launch {
            val entity = localDownloadDao.getDownloadById(id)
            if (entity != null) {
                localDownloadDao.deleteById(id)
                // Delete file
                val downloadDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                val appDir = File(downloadDir, "PotFlix")
                val safeTitle = entity.title.replace("""[\\/:*?"<>|]""".toRegex(), "_")
                val file = File(appDir, "$safeTitle.mp4")
                if (file.exists()) file.delete()
            }
            checkStopSelf()
        }
    }
    
    // Using an arbitrary STATUS_CANCELLED constant for internal cancellation checks
    private val STATUS_CANCELLED = -1

    private fun checkStopSelf() {
        if (activeDownloads.isEmpty()) {
            stopForeground(true)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String, progress: Int, max: Int): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PotFlix Download")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(max, progress, false)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(text: String, progress: Int, max: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(text, progress, max))
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
