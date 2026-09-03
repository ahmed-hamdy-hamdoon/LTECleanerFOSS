/*
 * SPDX-FileCopyrightText: 2020-2023 Hunter J Drum
 * SPDX-FileCopyrightText: 2024-2025 MDP43140
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.mdp43140.ltecleaner
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.text.DecimalFormat
//import io.mdp43140.ltecleaner.Constants
import kotlin.system.exitProcess
object CommonFunctions {
	fun isDarkThemeActive(ctx: Context): Boolean {
		return (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
	}
	fun makeNotificationChannel(ctx: Context, name: String, description: String?, channelName: String, importance: Int){
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
			val channel = NotificationChannel(channelName, name, importance)
			channel.description = description
			(ctx.getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
		}
	}
	fun makeNotification(ctx: Context, channel: String): NotificationCompat.Builder {
		return NotificationCompat.Builder(ctx, channel)
			.setSmallIcon(R.drawable.ic_cleanup)
			.setAutoCancel(true)
			.setPriority(NotificationCompat.PRIORITY_DEFAULT)
			.setVibrate(LongArray(0))
	}
	fun makeStatusNotification(ctx: Context, message: String?): NotificationCompat.Builder {
		// Name of Notification Channel for verbose notifications of background work
		val title: CharSequence = ctx.getString(R.string.svc_notification_title)

		// Make a channel if necessary
		makeNotificationChannel(
			ctx,
			ctx.getString(R.string.default_notification_name),
			ctx.getString(R.string.default_notification_sum),
			Constants.NOTIFICATION_CHANNEL_SERVICE,
			NotificationManager.IMPORTANCE_DEFAULT
		)

		// Create the notification
		return makeNotification(ctx, Constants.NOTIFICATION_CHANNEL_SERVICE)
			.setContentTitle(title)
			.setContentText(message)
	}
	fun sendNotification(ctx: Context, id: Int, notification: Notification){
		NotificationManagerCompat.from(ctx).notify(id, notification)
	}
	fun sendNotification(ctx: Context, id: Int, notification: NotificationCompat.Builder){
		sendNotification(ctx, id, notification.build())
	}
	fun updateTheme(ctx: Context, theme: String){
		val themeStr = ctx.resources.getStringArray(R.array.themes_key)
		AppCompatDelegate.setDefaultNightMode(when (theme){
			themeStr[1] -> AppCompatDelegate.MODE_NIGHT_NO
			themeStr[2] -> AppCompatDelegate.MODE_NIGHT_YES
			else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
		})
	}
	fun writeContentToUri(ctx: Context,uri: Uri, content: String){
		ctx.contentResolver.openOutputStream(uri)?.use { outputStream ->
			outputStream.write(content.toByteArray())
		}
	}
	fun convertSize(bytes: Long): String {
		if (bytes <= 0) return "0 B"
		val format = DecimalFormat("#.##")
		val kib = 1024.0
		val mib = 1024.0 * 1024.0
		val gib = 1024.0 * 1024.0 * 1024.0
		return when {
			bytes >= gib -> format.format(bytes / gib) + " GB"
			bytes >= mib -> format.format(bytes / mib) + " MB"
			bytes >= kib -> format.format(bytes / kib) + " KB"
			else -> "$bytes B"
		}
	}
}
