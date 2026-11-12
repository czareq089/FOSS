package com.foss.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.foss.app.MainActivity
import com.foss.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

object RestTimerNotificationManager {
    const val CHANNEL_TIMER_ID = "foss_rest_timer_channel"
    const val CHANNEL_FINISHED_ID = "foss_rest_finished_channel"
    const val NOTIFICATION_ID_TIMER = 1001
    const val NOTIFICATION_ID_FINISHED = 1002

    const val ACTION_MINUS_30 = "com.foss.app.ACTION_REST_TIMER_MINUS_30"
    const val ACTION_PLUS_30 = "com.foss.app.ACTION_REST_TIMER_PLUS_30"

    var timerRemaining by mutableIntStateOf(0)
        private set
    var timerTotal by mutableIntStateOf(0)
        private set
    var isTimerRunning by mutableStateOf(false)
        private set

    private val timerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var tickerJob: Job? = null

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val timerChannel = NotificationChannel(
                CHANNEL_TIMER_ID,
                "Workout Rest Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows stylized active rest timer countdown during workouts"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }

            val finishedChannel = NotificationChannel(
                CHANNEL_FINISHED_ID,
                "Rest Timer Complete",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when rest interval finishes"
                setShowBadge(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)
            }

            nm.createNotificationChannel(timerChannel)
            nm.createNotificationChannel(finishedChannel)
        }
    }

    private fun getPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    private fun getActionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, RestTimerReceiver::class.java).apply {
            this.action = action
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    fun startTimer(context: Context, seconds: Int) {
        if (seconds <= 0) return
        tickerJob?.cancel()
        timerTotal = seconds
        timerRemaining = seconds
        isTimerRunning = true
        updateTimerNotification(context, timerRemaining, timerTotal)

        tickerJob = timerScope.launch {
            while (isActive && timerRemaining > 0 && isTimerRunning) {
                delay(1000L)
                if (!isActive || !isTimerRunning) break
                timerRemaining -= 1
                if (timerRemaining > 0) {
                    updateTimerNotification(context, timerRemaining, timerTotal)
                } else {
                    isTimerRunning = false
                    notifyTimerFinished(context)
                    break
                }
            }
        }
    }

    fun adjustTimer(context: Context, deltaSeconds: Int) {
        if (!isTimerRunning && timerRemaining <= 0) return
        val newRemaining = maxOf(0, timerRemaining + deltaSeconds)
        if (newRemaining <= 0) {
            cancelTimer(context)
        } else {
            timerRemaining = newRemaining
            if (deltaSeconds > 0) {
                timerTotal += deltaSeconds
            }
            if (!isTimerRunning) {
                isTimerRunning = true
                tickerJob?.cancel()
                tickerJob = timerScope.launch {
                    while (isActive && timerRemaining > 0 && isTimerRunning) {
                        delay(1000L)
                        if (!isActive || !isTimerRunning) break
                        timerRemaining -= 1
                        if (timerRemaining > 0) {
                            updateTimerNotification(context, timerRemaining, timerTotal)
                        } else {
                            isTimerRunning = false
                            notifyTimerFinished(context)
                            break
                        }
                    }
                }
            }
            updateTimerNotification(context, timerRemaining, timerTotal)
        }
    }

    fun cancelTimer(context: Context) {
        tickerJob?.cancel()
        tickerJob = null
        timerTotal = 0
        timerRemaining = 0
        isTimerRunning = false
        cancelTimerNotification(context)
    }

    fun updateTimerNotification(context: Context, remainingSeconds: Int, totalSeconds: Int) {
        createNotificationChannels(context)
        val mm = remainingSeconds / 60
        val ss = remainingSeconds % 60
        val timeStr = String.format(Locale.US, "%02d:%02d", mm, ss)

        val remoteViews = RemoteViews(context.packageName, R.layout.notification_rest_timer).apply {
            setTextViewText(R.id.tv_timer_countdown, timeStr)
            setProgressBar(R.id.timer_progress, maxOf(1, totalSeconds), remainingSeconds, false)
            setOnClickPendingIntent(R.id.btn_minus_30, getActionPendingIntent(context, ACTION_MINUS_30, 101))
            setOnClickPendingIntent(R.id.btn_plus_30, getActionPendingIntent(context, ACTION_PLUS_30, 102))
            setOnClickPendingIntent(R.id.notification_container, getPendingIntent(context))
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_TIMER_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Rest Timer: $timeStr")
            .setContentText("Next set in $timeStr")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(getPendingIntent(context))
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_TIMER, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun notifyTimerFinished(context: Context) {
        createNotificationChannels(context)
        cancelTimerNotification(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_FINISHED_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Rest Finished! ⏰")
            .setContentText("Time for your next set. Let's do this!")
            .setAutoCancel(true)
            .setContentIntent(getPendingIntent(context))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 500, 250, 500))

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_FINISHED, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancelTimerNotification(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID_TIMER)
            nm?.cancel(NOTIFICATION_ID_FINISHED)
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_TIMER)
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_FINISHED)
        } catch (_: Exception) {
        }
    }
}
