/*
 * SPDX-FileCopyrightText: 2024-2026 MDP43140 & LTE Cleaner Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.mdp43140.ltecleaner.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object ShizukuManager {
	const val TAG = "ShizukuManager"
	const val SHIZUKU_PERMISSION_REQUEST_CODE = 43140
	const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

	data class ShellResult(
		val exitCode: Int,
		val stdout: String,
		val stderr: String
	) {
		val isSuccess: Boolean get() = exitCode == 0
	}

	enum class ShizukuState {
		NOT_INSTALLED,
		DEAD,
		AVAILABLE_UNAUTHORIZED,
		AUTHORIZED
	}

	private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
		notifyStateChanged()
	}

	private val binderDeadListener = Shizuku.OnBinderDeadListener {
		notifyStateChanged()
	}

	private val requestPermissionResultListener =
		Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
			if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
				notifyStateChanged()
			}
		}

	private val listeners = mutableListOf<() -> Unit>()

	fun registerListener(listener: () -> Unit) {
		if (!listeners.contains(listener)) {
			listeners.add(listener)
		}
	}

	fun unregisterListener(listener: () -> Unit) {
		listeners.remove(listener)
	}

	private fun notifyStateChanged() {
		listeners.forEach {
			try {
				it()
			} catch (e: Exception) {
				Log.e(TAG, "Listener notification error", e)
			}
		}
	}

	fun init() {
		try {
			Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
			Shizuku.addBinderDeadListener(binderDeadListener)
			Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
		} catch (e: Throwable) {
			Log.e(TAG, "Init error", e)
		}
	}

	fun destroy() {
		try {
			Shizuku.removeBinderReceivedListener(binderReceivedListener)
			Shizuku.removeBinderDeadListener(binderDeadListener)
			Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
			listeners.clear()
		} catch (e: Throwable) {
			Log.e(TAG, "Destroy error", e)
		}
	}

	fun isInstalled(context: Context): Boolean {
		return try {
			context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
			true
		} catch (_: PackageManager.NameNotFoundException) {
			false
		}
	}

	fun isRunning(): Boolean {
		return try {
			Shizuku.pingBinder()
		} catch (_: Throwable) {
			false
		}
	}

	fun hasPermission(): Boolean {
		if (!isRunning()) return false
		return try {
			if (Shizuku.isPreV11()) {
				false
			} else {
				Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
			}
		} catch (_: Throwable) {
			false
		}
	}

	fun getState(context: Context): ShizukuState {
		return when {
			!isInstalled(context) -> ShizukuState.NOT_INSTALLED
			!isRunning() -> ShizukuState.DEAD
			!hasPermission() -> ShizukuState.AVAILABLE_UNAUTHORIZED
			else -> ShizukuState.AUTHORIZED
		}
	}

	fun requestPermission() {
		try {
			if (isRunning() && !hasPermission()) {
				Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
			}
		} catch (e: Throwable) {
			Log.e(TAG, "Request permission error", e)
		}
	}

	fun openShizukuApp(context: Context) {
		val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
		if (intent != null) {
			context.startActivity(intent)
		} else {
			// Redirect to GitHub or download
			val browserIntent = Intent(
				Intent.ACTION_VIEW,
				Uri.parse("https://github.com/RikkaApps/Shizuku/releases")
			)
			context.startActivity(browserIntent)
		}
	}

	/**
	 * Executes a shell command with elevated privileges using Shizuku.
	 */
	fun executeCommand(command: String): ShellResult {
		if (!hasPermission()) {
			return ShellResult(-1, "", "Shizuku is not running or permission is denied")
		}

		return try {
			val method = Shizuku::class.java.getDeclaredMethod(
				"newProcess",
				Array<String>::class.java,
				Array<String>::class.java,
				String::class.java
			)
			method.isAccessible = true
			val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
			val stdoutBuilder = StringBuilder()
			val stderrBuilder = StringBuilder()

			val stdoutThread = Thread {
				BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
					var line = reader.readLine()
					while (line != null) {
						stdoutBuilder.append(line).append("\n")
						line = reader.readLine()
					}
				}
			}

			val stderrThread = Thread {
				BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
					var line = reader.readLine()
					while (line != null) {
						stderrBuilder.append(line).append("\n")
						line = reader.readLine()
					}
				}
			}

			stdoutThread.start()
			stderrThread.start()

			val exitCode = process.waitFor()
			stdoutThread.join(2000)
			stderrThread.join(2000)

			ShellResult(exitCode, stdoutBuilder.toString().trim(), stderrBuilder.toString().trim())
		} catch (e: Throwable) {
			Log.e(TAG, "executeCommand error", e)
			ShellResult(-1, "", e.localizedMessage ?: "Execution error")
		}
	}

	/**
	 * Automatically grant MANAGE_EXTERNAL_STORAGE and storage permissions
	 * via elevated Shizuku shell.
	 */
	fun grantStoragePermissionsViaShizuku(packageName: String): Boolean {
		if (!hasPermission()) return false
		val cmd = buildString {
			append("appops set $packageName MANAGE_EXTERNAL_STORAGE allow; ")
			append("pm grant $packageName android.permission.READ_EXTERNAL_STORAGE; ")
			append("pm grant $packageName android.permission.WRITE_EXTERNAL_STORAGE; ")
			append("pm grant $packageName android.permission.PACKAGE_USAGE_STATS")
		}
		val res = executeCommand(cmd)
		return res.isSuccess
	}

	/**
	 * Lists files in a restricted path (such as Android/data, internal cache, or SD card)
	 * using Shizuku when standard File.listFiles() returns null or empty due to permissions.
	 */
	fun listFilesElevated(directoryPath: String): List<String> {
		if (!hasPermission()) return emptyList()
		val escaped = directoryPath.replace("'", "'\\''")
		// ls -1A prints 1 entry per line, including hidden files except . and ..
		val result = executeCommand("ls -1A '$escaped'")
		if (!result.isSuccess || result.stdout.isBlank()) return emptyList()
		return result.stdout.lines().map { it.trim() }.filter { it.isNotEmpty() }
	}

	/**
	 * Deletes a file or directory using elevated privileges if normal File.delete() fails.
	 */
	fun deleteElevated(path: String): Boolean {
		if (!hasPermission()) return false
		val escaped = path.replace("'", "'\\''")
		val result = executeCommand("rm -rf '$escaped'")
		return result.isSuccess
	}

	/**
	 * Discovers secondary external storage directories (MicroSD cards, USB OTG).
	 */
	fun getSecondaryStorageDirs(context: Context): List<File> {
		val result = mutableListOf<File>()
		try {
			val primary = Environment.getExternalStorageDirectory().canonicalPath
			val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
			for (dir in externalDirs) {
				if (dir != null) {
					// Extract root storage mount point from /storage/XXXX-XXXX/Android/data/...
					var root: File? = dir
					while (root != null && root.parentFile != null && root.parentFile?.name != "storage") {
						root = root.parentFile
					}
					if (root != null && root.exists() && root.canRead()) {
						if (root.canonicalPath != primary && !result.any { it.canonicalPath == root.canonicalPath }) {
							result.add(root)
						}
					}
				}
			}

			// Fallback: check /storage/ subdirectories
			val storageRoot = File("/storage")
			val children = storageRoot.listFiles()
			if (children != null) {
				for (child in children) {
					if (child.isDirectory && child.name != "emulated" && child.name != "self" && child.name != "knox") {
						if (!result.any { it.canonicalPath == child.canonicalPath }) {
							result.add(child)
						}
					}
				}
			}
		} catch (e: Exception) {
			Log.e(TAG, "getSecondaryStorageDirs error", e)
		}
		return result
	}

	/**
	 * Discovers internal storage cache and temporary junk locations.
	 */
	fun getInternalStorageJunkPaths(): List<String> {
		return listOf(
			"/data/local/tmp",
			"/data/data",
			"/data/user/0"
		)
	}

	/**
	 * Generates the full ADB script to grant permissions and start Shizuku.
	 */
	fun getAdbScript(packageName: String): String {
		return buildString {
			appendLine("# ========================================================")
			appendLine("#  LTE Cleaner - Storage Access & Elevated Permissions Script")
			appendLine("# ========================================================")
			appendLine("# 1. Grant All Files Access (External Storage, Android 11+):")
			appendLine("adb shell appops set $packageName MANAGE_EXTERNAL_STORAGE allow")
			appendLine()
			appendLine("# 2. Grant Standard Storage Read & Write Permissions:")
			appendLine("adb shell pm grant $packageName android.permission.READ_EXTERNAL_STORAGE")
			appendLine("adb shell pm grant $packageName android.permission.WRITE_EXTERNAL_STORAGE")
			appendLine()
			appendLine("# 3. Grant Package Usage Stats:")
			appendLine("adb shell pm grant $packageName android.permission.PACKAGE_USAGE_STATS")
			appendLine()
			appendLine("# 4. Start Shizuku Service (for Internal & Restricted Storage):")
			appendLine("adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh")
		}
	}
}
