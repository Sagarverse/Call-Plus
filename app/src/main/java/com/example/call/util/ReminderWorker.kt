package com.example.call.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.call.MainActivity
import com.example.call.R
import com.example.call.data.Reminder
import com.example.call.data.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val reminderId = inputData.getLong("reminder_id", -1L)
        val number = inputData.getString("number") ?: return Result.failure()
        val name = inputData.getString("name") ?: "Unknown"

        showNotification(reminderId, number, name)
        
        // Remove from repository after showing
        val repository = ReminderRepository(applicationContext)
        repository.removeReminder(reminderId)

        return Result.success()
    }

    private fun showNotification(id: Long, number: String, name: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "reminders_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Call Reminders", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val callPendingIntent = PendingIntent.getActivity(
            applicationContext, 
            id.toInt(), 
            callIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainIntent = Intent(applicationContext, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            applicationContext, 
            id.toInt() + 1000, 
            mainIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Follow-up Call")
            .setContentText("Don't forget to call back $name ($number)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_menu_call, "Call Now", callPendingIntent)
            .build()

        notificationManager.notify(id.toInt(), notification)
    }

    companion object {
        fun schedule(context: Context, reminder: Reminder) {
            val data = workDataOf(
                "reminder_id" to reminder.id,
                "number" to reminder.number,
                "name" to reminder.name
            )

            val delay = reminder.time - System.currentTimeMillis()
            if (delay < 0) return

            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInputData(data)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag("reminder_${reminder.id}")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "reminder_${reminder.id}",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
