package com.okaca.maimai.android.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.blankj.utilcode.util.Utils
import com.okaca.maimai.android.AppIntentActions
import com.okaca.maimai.android.R
import com.okaca.maimai.android.ui.console.MainActivity
import com.okaca.maimai.android.ui.console.session.UpsertDelayProgress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 负责发送 App 内业务通知。
 *
 * 这个类不执行任何 maimai API，只封装 Android 通知栏相关逻辑：
 * - upsertUserAll 等待期间，每秒刷新同一个进度通知。
 * - 操作最终成功或失败后，发送结果通知。
 *
 * 把通知逻辑集中在这里，可以避免 ViewModel 里混入太多 Android 通知 API 细节。
 */
@Singleton
class OperationResultNotifier @Inject constructor() {
    private val context = Utils.getApp()

    private val notificationManager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    /**
     * 发送“操作成功”的结果通知。
     */
    fun notifySuccess(title: String, message: String) {
        cancelUpsertProgress()
        notify(
            title = title,
            message = message,
            notificationId = NOTIFICATION_ID_SUCCESS,
            colorRes = R.color.notification_color_success,
        )
    }

    /**
     * 发送“操作失败”的结果通知。
     */
    fun notifyFailure(title: String, message: String) {
        cancelUpsertProgress()
        notify(
            title = title,
            message = message,
            notificationId = NOTIFICATION_ID_FAILURE,
            colorRes = R.color.notification_color_failure,
        )
    }

    /**
     * 刷新 upsertUserAll 等待倒计时通知。
     *
     * 每次 tick 都使用同一个 notificationId，所以通知栏不会刷出很多条通知，
     * 而是同一条通知的文本和进度条不断变化。
     */
    fun notifyUpsertProgress(progress: UpsertDelayProgress) {
        createNotificationChannel()
        if (!canPostNotification()) return

        val elapsedSeconds = (progress.totalSeconds - progress.remainingSeconds).coerceAtLeast(0)
        val message = context.getString(
            R.string.notification_upsert_progress_message,
            progress.label,
            progress.remainingSeconds,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_maimai)
            .setColor(ContextCompat.getColor(context, R.color.notification_color_progress))
            .setLargeIcon(createTintedLargeIcon(R.color.notification_color_progress))
            .setContentTitle(context.getString(R.string.notification_upsert_progress_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(progress.totalSeconds, elapsedSeconds, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID_UPSERT_PROGRESS, notification)
    }

    /**
     * 倒计时结束、真正 POST 发送前刷新通知状态。
     */
    fun notifyUpsertPosting() {
        createNotificationChannel()
        if (!canPostNotification()) return

        val message = context.getString(R.string.notification_upsert_posting_message)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_maimai)
            .setColor(ContextCompat.getColor(context, R.color.notification_color_progress))
            .setLargeIcon(createTintedLargeIcon(R.color.notification_color_progress))
            .setContentTitle(context.getString(R.string.notification_upsert_progress_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, 100, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID_UPSERT_PROGRESS, notification)
    }

    /**
     * 主动移除等待进度通知。
     *
     * 成功/失败通知会先调用它，避免通知栏同时保留“等待中”和“已完成”两条状态冲突的通知。
     */
    fun cancelUpsertProgress() {
        notificationManager.cancel(NOTIFICATION_ID_UPSERT_PROGRESS)
    }

    /**
     * 登录后长时间没有 upsertUserAll 时，提醒用户点击通知执行 logout。
     */
    fun notifyNoUpsertLogoutReminder() {
        createNotificationChannel()
        if (!canPostNotification()) return

        val message = context.getString(R.string.notification_no_upsert_logout_message)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_maimai)
            .setColor(ContextCompat.getColor(context, R.color.notification_color_reminder))
            .setLargeIcon(createTintedLargeIcon(R.color.notification_color_reminder))
            .setContentTitle(context.getString(R.string.notification_no_upsert_logout_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(createNoUpsertLogoutPendingIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID_NO_UPSERT_LOGOUT, notification)
    }

    /**
     * 移除“未 upsert logout”提醒通知。
     */
    fun cancelNoUpsertLogoutReminder() {
        notificationManager.cancel(NOTIFICATION_ID_NO_UPSERT_LOGOUT)
    }

    /**
     * 构建并发送一条普通结果通知。
     */
    private fun notify(title: String, message: String, notificationId: Int, colorRes: Int) {
        createNotificationChannel()
        if (!canPostNotification()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_maimai)
            .setColor(ContextCompat.getColor(context, colorRes))
            .setLargeIcon(createTintedLargeIcon(colorRes))
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    /**
     * 创建点击通知后回到 MainActivity 并触发 logout 的 PendingIntent。
     */
    /**
     * 创建彩色的大图标。
     *
     * Android 会把通知栏顶部 smallIcon 当成“单色蒙版”处理，很多系统不会按我们的颜色显示。
     * largeIcon 是通知内容区域里的图片，系统通常会保留它的颜色，所以更适合表达成功/失败/进度状态。
     */
    private fun createTintedLargeIcon(colorRes: Int): Bitmap? {
        val sourceDrawable =
            ContextCompat.getDrawable(context, R.drawable.ic_stat_maimai) ?: return null
        val iconSize = context.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        val bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val wrappedDrawable = DrawableCompat.wrap(sourceDrawable.mutate())

        DrawableCompat.setTint(wrappedDrawable, ContextCompat.getColor(context, colorRes))
        wrappedDrawable.setBounds(0, 0, canvas.width, canvas.height)
        wrappedDrawable.draw(canvas)

        return bitmap
    }

    private fun createNoUpsertLogoutPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = AppIntentActions.LOGOUT_AFTER_NO_UPSERT_TIMEOUT
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_NO_UPSERT_LOGOUT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * 检查当前系统版本和通知权限状态是否允许发通知。
     */
    private fun canPostNotification(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

        val permissionState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        return permissionState == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 创建通知渠道。
     *
     * Android 8.0(API 26) 之后，所有通知都必须属于一个渠道。
     * 重复调用 createNotificationChannel 是安全的，系统会复用已存在的同名渠道。
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_operation_result_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description =
                context.getString(R.string.notification_channel_operation_result_description)
        }

        notificationManager.createNotificationChannel(channel)
    }

    private companion object {
        private const val CHANNEL_ID = "maimai_operation_result"
        private const val REQUEST_CODE_NO_UPSERT_LOGOUT = 4024
        private const val NOTIFICATION_ID_UPSERT_PROGRESS = 3024
        private const val NOTIFICATION_ID_NO_UPSERT_LOGOUT = 3027
        private const val NOTIFICATION_ID_SUCCESS = 3025
        private const val NOTIFICATION_ID_FAILURE = 3026
    }
}

