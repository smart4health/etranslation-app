package de.hpi.etranslation.feature.dashboard.worker

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.hpi.etranslation.MainActivity
import de.hpi.etranslation.R
import de.hpi.etranslation.feature.dashboard.DashboardError
import de.hpi.etranslation.feature.dashboard.usecase.SyncTranslationsUseCase
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.time.withTimeout
import java.time.Duration

private const val NOTIFICATION_ID = 23_03_2022_2
private const val NOTIFICATION_GET_TRANSLATIONS_COMPLETE = 23_03_2022_3

@HiltWorker
class SyncTranslationsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncTranslationsUseCase: SyncTranslationsUseCase,
    private val dashboardErrorSender: @JvmSuppressWildcards SendChannel<DashboardError>,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        var readyCount = 0
        var errorCount = 0

        withTimeout(Duration.ofMinutes(8)) {
            while (isActive) {
                Log.i("HPI", "getting translations")
                val statuses = syncTranslationsUseCase().filterNot {
                    it == SyncTranslationsUseCase.Status.NO_STATUS
                }

                val counts = statuses.groupingBy { it }.eachCount()
                Log.i("HPI", "counts: $counts")

                readyCount += counts.getOrDefault(SyncTranslationsUseCase.Status.READY, 0)
                errorCount += counts.getOrDefault(SyncTranslationsUseCase.Status.ERROR, 0)

                if (statuses.all { it == SyncTranslationsUseCase.Status.READY })
                    break

                delay(10_000)
            }
        }

        ensureNormalNotificationChannel(applicationContext)

        if (errorCount > 0)
            dashboardErrorSender.send(DashboardError.TranslationFailure(errorCount))

        if (readyCount > 0)
            NotificationCompat.Builder(applicationContext, NORMAL_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_baseline_check_circle_outline_24)
                .setContentTitle(applicationContext.getString(R.string.notification_sync_title))
                .setContentText(
                    applicationContext.getString(R.string.notification_sync_text, readyCount),
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(createIntent().let(this::createPendingIntent))
                .build()
                .let { notification ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ActivityCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.i("HPI", "POST_NOTIFICATIONS permission not granted")
                    } else
                        NotificationManagerCompat.from(applicationContext)
                            .notify(NOTIFICATION_GET_TRANSLATIONS_COMPLETE, notification)
                }

        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(NOTIFICATION_ID, createNotification())
    }

    private fun createNotification(): Notification {
        ensureWorkerNotificationChannel(applicationContext)

        return NotificationCompat.Builder(applicationContext, WORKER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(applicationContext.getString(R.string.notification_waiting_title))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createPendingIntent(intent: Intent): PendingIntent =
        PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)

    private fun createIntent(): Intent =
        Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
}
