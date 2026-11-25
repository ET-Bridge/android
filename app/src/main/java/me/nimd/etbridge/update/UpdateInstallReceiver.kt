package me.nimd.etbridge.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import dagger.hilt.android.AndroidEntryPoint
import me.nimd.etbridge.BuildConfig
import me.nimd.etbridge.sync.Notifications
import javax.inject.Inject

/** Status callbacks from the PackageInstaller session started by [UpdateManager]. */
@AndroidEntryPoint
class UpdateInstallReceiver : BroadcastReceiver() {

    @Inject lateinit var updates: UpdateManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UpdateManager.ACTION_INSTALL_STATUS) return
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android's own "Do you want to update this app?" screen.
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm == null) {
                    updates.onInstallFailed("Android didn't show the install prompt", cancelled = false)
                    return
                }
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { updates.onInstallFailed(it.message, cancelled = false) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // This process is replaced; PackageReplacedReceiver takes over.
            else -> updates.onInstallFailed(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
                cancelled = status == PackageInstaller.STATUS_FAILURE_ABORTED,
            )
        }
    }
}

/**
 * Installing an update kills the running app, so tell the user it worked and give them a way
 * back in. Fires only for updates of this package (any source, which is fine).
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Notifications.post(
            context,
            id = Notifications.ID_APP_UPDATED,
            channel = Notifications.CHANNEL_APP_UPDATES,
            title = "ETBridge updated",
            text = "Now on version ${BuildConfig.VERSION_NAME}. Tap to open.",
        )
    }
}
