package com.mobilecarconnect.app.entry

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.mobilecarconnect.app.R
import com.mobilecarconnect.app.ui.HomeActivity
import com.mobilecarconnect.core.connection.MobileCarConnectHost
import com.mobilecarconnect.core.connection.MobileCarConnectHost.OpenReason
import com.mobilecarconnect.core.core.PhoneKind

/**
 * MobileCarConnect's [MobileCarConnectHost]: brings Home to the front from the background. Android 10+ blocks
 * background activity starts unless the app may draw over other apps (both engines ask for it);
 * without it, a full-screen notification opens Home instead, as DiAuto's auto-start receivers do.
 */
object HomeLauncher : MobileCarConnectHost {
    override fun openHome(context: Context, reason: OpenReason, address: String?, kind: PhoneKind?, name: String?) {
        val intent = HomeActivity.intent(context).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (reason == OpenReason.PHONE && kind != null) {
                putExtra(HomeActivity.EXTRA_AUTO_PHONE_ADDRESS, address)
                putExtra(HomeActivity.EXTRA_AUTO_PHONE_KIND, kind.name)
                putExtra(HomeActivity.EXTRA_AUTO_PHONE_NAME, name)
            }
        }
        val canStartDirectly = Build.VERSION.SDK_INT < 29 || Settings.canDrawOverlays(context)
        if (canStartDirectly) {
            runCatching { context.startActivity(intent) }
                .onSuccess { return }
                .onFailure { Log.w(TAG, "Direct launch failed: ${it.javaClass.simpleName}") }
        }
        if (reason == OpenReason.UNPLUGGED || reason == OpenReason.ENGINE_START) return
        notify(context, intent, name)
    }

    override fun onCallMicrophoneConflict(context: Context) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, R.string.mobilecarconnect_notice_call_microphone, Toast.LENGTH_LONG).show()
        }
    }

    private fun notify(context: Context, intent: Intent, phoneName: String?) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.notification_channel_launch),
                NotificationManager.IMPORTANCE_HIGH).apply { setShowBadge(false) })
        }
        val pending = PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_mobilecarconnect)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(phoneName?.let { context.getString(R.string.notification_phone_ready, it) }
                ?: context.getString(R.string.notification_open))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private const val TAG = "MobileCarConnect-Launch"
    private const val CHANNEL = "mobilecarconnect_launch"
    private const val NOTIFICATION_ID = 7001
}
