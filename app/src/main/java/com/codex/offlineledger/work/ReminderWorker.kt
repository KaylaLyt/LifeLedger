package com.codex.offlineledger.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.codex.offlineledger.R
import com.codex.offlineledger.data.AppDatabase
import com.codex.offlineledger.data.repo.LedgerRepository
import java.time.LocalDate
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "offline-ledger-reminders"
private const val UNIQUE_WORK_NAME = "offline-ledger-reminder-worker"

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val repository = LedgerRepository(AppDatabase.getInstance(appContext))

    override suspend fun doWork(): Result {
        ReminderScheduler.ensureChannel(applicationContext)
        repository.generateBirthdayTodos(LocalDate.now())
        val now = System.currentTimeMillis()
        val dueTodos = repository.dueTodoNotifications(now)
        dueTodos.forEachIndexed { index, todo ->
            maybeNotify(todo.id.toInt() + index, todo.title, todo.description.ifBlank { "有一条待办需要处理" })
            repository.markTodoNotified(todo.id, now)
        }
        return Result.success()
    }

    private fun maybeNotify(id: Int, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(id, notification)
    }
}

object ReminderScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "离线账本提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Todo 与生日提醒"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
