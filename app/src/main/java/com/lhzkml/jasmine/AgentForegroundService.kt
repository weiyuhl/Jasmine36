package com.lhzkml.jasmine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Agent 前台服务
 * 在 Agent 模式执行任务期间保持进程存活，防止用户退到后台后被系统杀死。
 * 任务完成/失败后发送通知并自动停止。
 */
class AgentForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "agent_running"
        const val CHANNEL_NAME = "Agent 任务"
        const val ONGOING_NOTIFICATION_ID = 1001
        const val RESULT_NOTIFICATION_ID = 1002

        private const val EXTRA_TYPE = "type"
        private const val EXTRA_SUMMARY = "summary"
        private const val EXTRA_ERROR = "error"
        private const val TYPE_COMPLETE = "complete"
        private const val TYPE_FAILED = "failed"

        /** 启动前台服务 */
        fun start(context: Context) {
            val intent = Intent(context, AgentForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** 停止前台服务 */
        fun stop(context: Context) {
            context.stopService(Intent(context, AgentForegroundService::class.java))
        }

        /** 通知任务完成 + 停止服务 */
        fun notifyComplete(context: Context, summary: String) {
            val intent = Intent(context, AgentForegroundService::class.java).apply {
                putExtra(EXTRA_TYPE, TYPE_COMPLETE)
                putExtra(EXTRA_SUMMARY, summary)
            }
            context.startService(intent)
        }

        /** 通知任务失败 + 停止服务 */
        fun notifyFailed(context: Context, error: String) {
            val intent = Intent(context, AgentForegroundService::class.java).apply {
                putExtra(EXTRA_TYPE, TYPE_FAILED)
                putExtra(EXTRA_ERROR, error)
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra(EXTRA_TYPE)) {
            TYPE_COMPLETE -> {
                val summary = intent.getStringExtra(EXTRA_SUMMARY) ?: "任务已完成"
                showResultNotification(
                    title = "Agent 任务已完成",
                    content = summary
                )
                stopSelf()
                return START_NOT_STICKY
            }
            TYPE_FAILED -> {
                val error = intent.getStringExtra(EXTRA_ERROR) ?: "未知错误"
                showResultNotification(
                    title = "Agent 任务失败",
                    content = error
                )
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // 默认：启动前台通知
        val notification = buildOngoingNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                ONGOING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(ONGOING_NOTIFICATION_ID, notification)
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // 低优先级，不发出声音
            ).apply {
                description = "Agent 模式后台任务执行状态"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * 构建「正在执行」常驻通知
     */
    private fun buildOngoingNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Agent 正在工作")
            .setContentText("AI 正在执行任务，点击返回查看进度")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openPending)
            .build()
    }

    /**
     * 发送「完成/失败」结果通知（可点击跳回 App）
     */
    private fun showResultNotification(title: String, content: String) {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(RESULT_NOTIFICATION_ID, notification)
    }
}
