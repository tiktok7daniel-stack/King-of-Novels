package app.riwaya.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.riwaya.MainActivity
import app.riwaya.R
import app.riwaya.Riwaya
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/** Every few hours: new chapters in the library's novels, with a notification for each novel that has some. */
class UpdatesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!Riwaya.settings.notifyUpdates.get()) return Result.success()
        // The extensions load when the app starts; wait for them a little
        withTimeoutOrNull(30_000) { Riwaya.extensions.installed.first { it.isNotEmpty() } } ?: return Result.retry()
        val found = runCatching { Riwaya.home.checkLibraryUpdates() }.getOrElse { return Result.retry() }
        found.forEach { (novel, count, newest) -> notify(applicationContext, novel, count, newest) }
        return Result.success()
    }

    companion object {
        private const val WORK = "library_updates"
        const val CHANNEL = "updates"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdatesWorker>(4, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK)

        fun notify(context: Context, novel: SavedNovel, count: Int, newest: String) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.updates_channel), NotificationManager.IMPORTANCE_DEFAULT))
            val open = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SOURCE, novel.sourceId)
                .putExtra(MainActivity.EXTRA_KEY, novel.key)
                .putExtra(MainActivity.EXTRA_TITLE, novel.title)
                .putExtra(MainActivity.EXTRA_COVER, novel.cover)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val id = novel.id.hashCode()
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ms_auto_stories_fill1)
                .setContentTitle(novel.title)
                .setContentText(context.getString(R.string.notify_new_chapters, count, newest))
                .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notify_new_chapters, count, newest)))
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
        }
    }
}
