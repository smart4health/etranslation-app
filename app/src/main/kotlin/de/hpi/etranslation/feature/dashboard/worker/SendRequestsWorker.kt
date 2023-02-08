package de.hpi.etranslation.feature.dashboard.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.hpi.etranslation.R
import de.hpi.etranslation.feature.dashboard.DashboardError
import de.hpi.etranslation.feature.dashboard.usecase.CancelTranslationRequestsUseCase
import de.hpi.etranslation.feature.dashboard.usecase.SendRequestsUseCase
import kotlinx.coroutines.channels.SendChannel

internal const val WORKER_CHANNEL_ID = "worker_channel"
internal const val NORMAL_CHANNEL_ID = "normal_channel"
private const val NOTIFICATION_ID = 23_03_2022_1

@HiltWorker
class SendRequestsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val sendRequestsUseCase: SendRequestsUseCase,
    private val dashboardErrorSender: @JvmSuppressWildcards SendChannel<DashboardError>,
    private val cancelTranslationRequestsUseCase: CancelTranslationRequestsUseCase,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        Log.i("HPI", "Worker sending requests")
        val outcome = sendRequestsUseCase()
        if (outcome.failureCount > 0) {
            outcome.failureCount
                .let(DashboardError::SendRequestsFailure)
                .let { dashboardErrorSender.send(it) }

            cancelTranslationRequestsUseCase()
        }
        Log.i("HPI", "Worker done")

        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(NOTIFICATION_ID, createNotification())
    }

    private fun createNotification(): Notification {
        ensureWorkerNotificationChannel(applicationContext)

        return NotificationCompat.Builder(applicationContext, WORKER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(applicationContext.getString(R.string.notification_uploading_title))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

internal fun ensureWorkerNotificationChannel(applicationContext: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            WORKER_CHANNEL_ID,
            "Foreground operations",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Notifications for workers"
        }

        NotificationManagerCompat.from(applicationContext)
            .createNotificationChannel(channel)
    }
}

internal fun ensureNormalNotificationChannel(applicationContext: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            NORMAL_CHANNEL_ID,
            "Document readiness notifications",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Notifications for normal operations"
        }

        NotificationManagerCompat.from(applicationContext)
            .createNotificationChannel(channel)
    }
}
