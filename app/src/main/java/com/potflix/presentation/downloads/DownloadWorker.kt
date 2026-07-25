package com.potflix.presentation.downloads

import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString("url") ?: return Result.failure()
        val title = inputData.getString("title") ?: return Result.failure()

        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) return@withContext Result.retry()

                val file = File(
                    applicationContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                    "${title.replace(Regex("[^a-zA-Z0-9]"), "_")}.mp4"
                )

                val totalBytes = response.body?.contentLength() ?: -1L
                var downloadedBytes = 0L

                response.body?.byteStream()?.use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            
                            if (totalBytes > 0) {
                                setProgress(workDataOf(
                                    "progress" to ((downloadedBytes * 100) / totalBytes).toInt()
                                ))
                            }
                        }
                    }
                }

                Result.success()
            } catch (e: Exception) {
                Result.retry()
            }
        }
    }
}
