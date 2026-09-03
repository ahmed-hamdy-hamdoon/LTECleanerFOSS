/*
 * SPDX-FileCopyrightText: 2020-2023 Hunter J Drum
 * SPDX-FileCopyrightText: 2024-2025 MDP43140
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.mdp43140.ltecleaner
object Constants {
	const val BGCLEAN_WORK_NAME = "scheduled_cleanup_work"
	const val BGCLEAN_WORK_TAG = "cleanup_work_tag"
	const val NOTIFICATION_ID_SERVICE = 1
	const val NOTIFICATION_CHANNEL_SERVICE = "CLEANUP_SERVICE"
	val blacklistDefault: Set<String> = setOf(
		"/storage/[^/]+/.*/LOST\\.DIR$",
		"/storage/[^/]+/.*/\\.DS_Store$",
		"/storage/[^/]+/.*/desktop\\.ini$",
		"/storage/[^/]+/.*thumbs?\\.db$",
		"/storage/[^/]+/.*/fseventd$",
		"/storage/[^/]+/.*/\\.Trash.*",
		"/storage/[^/]+/.*/\\.trashed-.*",
		"/storage/[^/]+/\\.ext4",
		"/storage/[^/]+/\\.sstmp",
		"/storage/[^/]+/\\.dev",
		"/storage/[^/]+/\\.UTSystemConfig",
		"/storage/[^/]+/\\.Uc2.*",
		"/storage/[^/]+/\\.DataStorage",
		"/storage/[^/]+/\\.userReturn",
		"/storage/[^/]+/\\.estrongs",
		"/storage/[^/]+/.*\\.tmp$",
		"/storage/[^/]+/.*\\.temp$",
		"/storage/[^/]+/.*\\.crdownload$",
		"/storage/[^/]+/.*\\.part$",
		"/storage/[^/]+/.*\\.downloading$",
		"/storage/[^/]+/.*\\.log$",
		"/storage/[^/]+/.*/[Ll]ogs?$",
		"/storage/[^/]+/.*/[Ll]og$",
		"/storage/[^/]+/.*[Cc]rash.*\\.txt$",
		"/storage/[^/]+/.*logcat.*\\.txt$",
		"/storage/[^/]+/.*gltools_crashlog.*",
		"/storage/[^/]+/.*/bugreports?$",
		"/storage/[^/]+/.*/Bug[Rr]eport.*",
		"/storage/[^/]+/MIUI/debug_log",
		"/storage/[^/]+/MIUI/BugReportCache",
		"/storage/[^/]+/DCIM/.*\\.thumbnails?.*",
		"/storage/[^/]+/DCIM/.*albumthumbs.*",
		"/storage/[^/]+/DCIM/Camera/\\.escheck\\.tmp",
		"/storage/[^/]+/DCIM/\\.edit_temp.*",
		"/storage/[^/]+/DCIM/\\.editing_cache.*",
		"/storage/[^/]+/Pictures/\\.thumbnails.*",
		"/storage/[^/]+/Pictures/\\.gallery_cache.*",
		"/storage/[^/]+/DCIM/\\.trash.*",
		"/storage/[^/]+/Samsung/.*/\\.trash.*",
		"/storage/[^/]+/Samsung/logs.*",
		"/storage/[^/]+/Samsung/QuickShare/temp.*",
		"/storage/[^/]+/QuickShare/\\.temp.*",
		"/storage/[^/]+/SmartSwitch/temp.*",
		"/storage/[^/]+/Voice Recorder/\\.trash.*",
		"/storage/[^/]+/.*\\.thumb[0-9]*$",
		"/storage/[^/]+/.*\\.dthumb$",
		"/storage/[^/]+/.*\\.sthumbs$",
		"/storage/[^/]+/.*[Cc]ache$",
		"/storage/[^/]+/supercache",
		"/storage/[^/]+/.*\\.exo$",
		"/storage/[^/]+/.*WhatsApp/Databases/msgstore-[0-9]{4}-[0-9]{2}-[0-9]{2}\\..*",
		"/storage/[^/]+/.*WhatsApp/Databases/.*\\.tmp$",
		"/storage/[^/]+/.*WhatsApp/Backups/.*\\.tmp$",
		"/storage/[^/]+/.*WhatsApp/Media/\\.Statuses.*",
		"/storage/[^/]+/.*WhatsApp/Media/\\.Thumbs.*",
		"/storage/[^/]+/.*WhatsApp/Media/\\.Shared.*",
		"/storage/[^/]+/.*WhatsApp/Media/\\.trash.*",
		"/storage/[^/]+/Telegram/.*/\\.temp.*",
		"/storage/[^/]+/.*UnityAds.*",
		"/storage/[^/]+/.*\\.chartboost.*",
		"/storage/[^/]+/.*mobvista.*",
		"/storage/[^/]+/.*supersonicads.*",
		"/storage/[^/]+/.*\\.pangled.*",
		"/storage/[^/]+/.*splashad.*",
		"/storage/[^/]+/.*vguard.*",
		"/storage/[^/]+/.*[Aa]nalytics.*",
		"/storage/[^/]+/.*leakcanary.*",
		"/storage/[^/]+/ColorOS",
		"/storage/[^/]+/Tencent",
		"/storage/[^/]+/com\\.UCMobile\\.intl",
		"/storage/[^/]+/UCShare",
		"/storage/[^/]+/Download/UCDownloads",
		"/storage/[^/]+/Download/MGC_CRASH_LOG",
		"/storage/[^/]+/Download/AppMonitorSDKLogs",
		"/storage/[^/]+/ApkEditor/tmp"
	)
	val blacklistOnDefault: Set<String> = blacklistDefault
	val whitelistDefault: Set<String> = setOf(
		"/storage/[^/]+/Download/.*\\.pdf$",
		"/storage/[^/]+/Download/.*\\.docx?$",
		"/storage/[^/]+/Download/.*\\.xlsx?$",
		"/storage/[^/]+/Download/.*\\.pptx?$",
		"/storage/[^/]+/Download/.*\\.zip$",
		"/storage/[^/]+/Download/.*\\.rar$",
		"/storage/[^/]+/Download/.*\\.7z$"
	)
	val whitelistOnDefault: Set<String> = whitelistDefault
	val filter_apkFiles: ArrayList<String> = arrayListOf(
		".aab", // should this be considered?
		".apk",
		".apks",
		".apkm"
	)
	val filter_genericFiles: ArrayList<String> = arrayListOf(
		".tmp",
		".log"
	)
	val filter_genericFolders: ArrayList<String> = arrayListOf(
		"Logs",
		"logs",
		"temp",
		"Temporary",
		"temporary"
	)
	val filter_autoWhite: ArrayList<String> = arrayListOf(
		"backup",
		"copy",
		"copies",
		"important",
		"do_not_edit",
		".stfolder"
	)
}
