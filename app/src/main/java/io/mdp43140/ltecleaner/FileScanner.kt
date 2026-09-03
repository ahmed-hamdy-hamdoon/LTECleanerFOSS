/*
 * SPDX-FileCopyrightText: 2020-2023 Hunter J Drum
 * SPDX-FileCopyrightText: 2024-2025 MDP43140
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.mdp43140.ltecleaner

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Color
import android.widget.TextView
import io.mdp43140.ltecleaner.fragment.BlacklistFragment
import io.mdp43140.ltecleaner.fragment.WhitelistFragment
import io.mdp43140.ltecleaner.PreferenceRepository
import io.mdp43140.ltecleaner.shizuku.ShizukuManager
import java.io.File
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class FileScanner(private val path: File, context: Context){
	private var prefs: PreferenceRepository = App.prefs!!
	private val context = context
	private var res: Resources = context.resources
	private var filesRemoved = AtomicInteger(0)
	private var bytesTotal = AtomicLong(0)
	var delete = false
	var autoWhite = prefs.autoWhite
	var corpse = prefs.cleanCorpse
	var emptyFile = prefs.cleanEmptyFile
	var emptyDir = prefs.cleanEmptyFolder
	var updateProgress: ((context: Context, percent: Double) -> Unit)? = null
	var addText: ((context: Context, path: String, type: Int) -> TextView?)? = null
	private var installedPackages = getInstalledPackages()
	private var guiScanProgressMax = 0
	private var guiScanProgressProgress = AtomicInteger(0)
	private var foundFiles: ArrayList<File>? = null

	/**
	 * Accurately calculates size of a file or directory in bytes.
	 */
	private fun calculatePathBytes(file: File): Long {
		return try {
			if (!file.exists()) return 0L
			if (file.isFile) {
				file.length()
			} else if (file.isDirectory) {
				var size = 0L
				file.walkTopDown().maxDepth(5).forEach { child ->
					if (child.isFile) {
						size += child.length()
					}
				}
				size
			} else 0L
		} catch (_: Exception) {
			0L
		}
	}

	/**
	 * Used to generate a list of all files on device.
	 * If parallel processing is enabled, subdirectories are explored across worker threads.
	 * @param parentDirectory where to start searching from
	 * @return List of all files on device (besides whitelisted ones)
	 */
	private fun getListFiles(parentDirectory: File): ArrayList<File> {
		val inFiles = ArrayList<File>()
		val files = parentDirectory.listFiles()
		if (files != null) {
			for (file in files) {
				if (file != null && !isWhiteListed(file)) {
					if (file.isDirectory) { // folder
						if (autoWhite) { // if auto whitelist enabled
							if (!autoWhiteList(file)) inFiles.add(file) // if file is not in autowhitelist index, add it
						} else inFiles.add(file) // add folder itself
						inFiles.addAll(getListFiles(file)) // add contents to returned list
					} else inFiles.add(file) // add file
				}
			}
		} else if (prefs.useShizuku && ShizukuManager.hasPermission() && parentDirectory.isDirectory) {
			// Elevated fallback for restricted directories (e.g. Android/data, Android/obb, or internal app caches)
			val elevatedNames = ShizukuManager.listFilesElevated(parentDirectory.absolutePath)
			for (name in elevatedNames) {
				val child = File(parentDirectory, name)
				if (!isWhiteListed(child)) {
					inFiles.add(child)
					// Only recurse into subdirectories if needed (e.g. Android data/obb or app caches)
					if (name == "data" || name == "obb" || name == "cache" || name == "code_cache" ||
						parentDirectory.name == "data" || parentDirectory.name == "obb" || parentDirectory.name == "Android" ||
						parentDirectory.name == "cache" || parentDirectory.name == "code_cache"
					) {
						inFiles.addAll(getListFiles(child))
					}
				}
			}
		}
		return inFiles
	}

	/**
	 * Parallel file search traversing directory trees concurrently across the configured number of workers.
	 */
	private fun getListFilesParallel(roots: List<File>, workerCount: Int): ArrayList<File> {
		val resultList = Collections.synchronizedList(ArrayList<File>())
		val executor = Executors.newFixedThreadPool(workerCount.coerceIn(1, 10))
		val activeTasks = AtomicInteger(0)
		val visitedPaths = ConcurrentHashMap.newKeySet<String>()

		fun scanDirectory(dir: File) {
			try {
				if (visitedPaths.add(dir.absolutePath)) {
					val files = dir.listFiles()
					if (files != null) {
						for (file in files) {
							if (file != null && !isWhiteListed(file)) {
								if (file.isDirectory) {
									if (autoWhite) {
										if (!autoWhiteList(file)) resultList.add(file)
									} else {
										resultList.add(file)
									}
									// Dispatch subdirectory exploration to executor pool
									activeTasks.incrementAndGet()
									executor.execute {
										try {
											scanDirectory(file)
										} finally {
											activeTasks.decrementAndGet()
										}
									}
								} else {
									resultList.add(file)
								}
							}
						}
					} else if (prefs.useShizuku && ShizukuManager.hasPermission() && dir.isDirectory) {
						val elevatedNames = ShizukuManager.listFilesElevated(dir.absolutePath)
						for (name in elevatedNames) {
							val child = File(dir, name)
							if (!isWhiteListed(child)) {
								resultList.add(child)
								if (name == "data" || name == "obb" || name == "cache" || name == "code_cache" ||
									dir.name == "data" || dir.name == "obb" || dir.name == "Android" ||
									dir.name == "cache" || dir.name == "code_cache"
								) {
									activeTasks.incrementAndGet()
									executor.execute {
										try {
											scanDirectory(child)
										} finally {
											activeTasks.decrementAndGet()
										}
									}
								}
							}
						}
					}
				}
			} catch (_: Exception) {
			}
		}

		for (root in roots) {
			if (root.exists() && !isWhiteListed(root)) {
				activeTasks.incrementAndGet()
				executor.execute {
					try {
						scanDirectory(root)
					} finally {
						activeTasks.decrementAndGet()
					}
				}
			}
		}

		// Wait for all recursive tasks to finish processing
		while (activeTasks.get() > 0) {
			try {
				Thread.sleep(25)
			} catch (_: InterruptedException) {
				break
			}
		}

		executor.shutdown()
		try {
			executor.awaitTermination(30, TimeUnit.SECONDS)
		} catch (_: InterruptedException) {
		}

		return ArrayList(resultList)
	}

	/**
	 * Discovers and scans secondary external storage (MicroSD cards)
	 */
	private fun getSecondarySdCardFiles(): ArrayList<File> {
		val result = ArrayList<File>()
		if (!prefs.cleanSdCard) return result
		val sdDirs = ShizukuManager.getSecondaryStorageDirs(context)
		for (sd in sdDirs) {
			if (!isWhiteListed(sd)) {
				addText?.invoke(context, String.format(context.getString(R.string.scanning_sdcard), sd.name), 0)
				result.addAll(getListFiles(sd))
			}
		}
		return result
	}

	/**
	 * Discovers and scans internal storage app caches and tmp directory
	 */
	private fun getInternalStorageFiles(): ArrayList<File> {
		val result = ArrayList<File>()
		if (!prefs.cleanInternal || !prefs.useShizuku || !ShizukuManager.hasPermission()) {
			return result
		}
		addText?.invoke(context, context.getString(R.string.scanning_internal), 0)

		// 1. /data/local/tmp
		val tmpDir = File("/data/local/tmp")
		if (!isWhiteListed(tmpDir)) {
			result.addAll(getListFiles(tmpDir))
		}

		// 2. /data/data and /data/user/0 app cache directories
		val dataDirs = listOf(File("/data/data"), File("/data/user/0"))
		for (parent in dataDirs) {
			val appDirs = ShizukuManager.listFilesElevated(parent.absolutePath)
			for (pkg in appDirs) {
				if (pkg == context.packageName) continue // Skip our own app's cache while running
				val cacheDir = File(parent, "$pkg/cache")
				if (!isWhiteListed(cacheDir)) {
					result.addAll(getListFiles(cacheDir))
				}
				val codeCacheDir = File(parent, "$pkg/code_cache")
				if (!isWhiteListed(codeCacheDir)) {
					result.addAll(getListFiles(codeCacheDir))
				}
			}
		}
		return result
	}

	private fun getInstalledPackages(): ArrayList<String> {
		val pm = context.packageManager
		val pkgs = pm.getInstalledApplications(PackageManager.GET_META_DATA)
		val pkgsStr: ArrayList<String> = ArrayList()
		for (pkg in pkgs) {
			pkgsStr.add(pkg.packageName)
		}
		return pkgsStr
	}

	/**
	 * Checks if the file or folder is whitelisted.
	 */
	private fun isWhiteListed(file: File): Boolean {
		val absolutePath = file.absolutePath
		val name = file.name
		for (path in whitelist){
			if (path.equals(absolutePath) ||
					path.equals(name, ignoreCase = true)) return true
		}
		return false
	}

	private fun isBlackListed(file: File): Boolean {
		val absolutePath = file.absolutePath
		for (pattern in blacklist){
			if (absolutePath.matches(pattern)) return true
		}
		return false
	}

	/**
	 * Automatically adds protected folders to whitelist based on pattern.
	 */
	@Synchronized
	private fun autoWhiteList(file: File): Boolean {
		for (protectedFile in autoWhitelist){
			val whiteLists = whitelist
			if (
				file.name.lowercase().contains(protectedFile) &&
				!whiteLists.contains(file.absolutePath.lowercase())
			) {
				whiteLists
					.toMutableList()
					.add(file.absolutePath.lowercase())
				prefs.whitelist = HashSet(whiteLists)
				return true
			}
		}
		return false
	}

	/**
	 * Evaluates whether the given file or directory matches cleanup criteria.
	 */
	fun filter(file: File): Boolean {
		// Internal cache and temporary files filter
		if (prefs.cleanInternal && (
			file.parentFile?.name == "cache" ||
			file.parentFile?.name == "code_cache" ||
			file.absolutePath.startsWith("/data/local/tmp") ||
			file.absolutePath.contains("/cache/")
		)) return true

		if (
			// corpse checking
			// Android/Data/[file != .nomedia]
			corpse &&
			file.parentFile?.name == "data" &&
			file.parentFile?.parentFile?.name == "Android" &&
			file.name != ".nomedia" &&
			!installedPackages.contains(file.name) ||
			// empty file
			emptyFile &&
			isFileEmpty(file) ||
			// empty folder
			emptyDir &&
			isDirectoryEmpty(file) ||
			// blacklist (targeted to get deleted)
			isBlackListed(file)
		) return true

		// file
		val absolutePath = file.absolutePath.lowercase()
		for (filter in filters){
			if (absolutePath.matches(filter.lowercase().toRegex())) return true
		}
		return false
	}

	private fun isDirectoryEmpty(directory: File): Boolean {
		if (!directory.isDirectory) return false
		val list = directory.list()
		if (list == null) return false
		return list.isNullOrEmpty()
	}

	private fun isFileEmpty(file: File): Boolean {
		return file.isFile && file.length() == 0L
	}

	/**
	 * Configures filters based on user preferences.
	 */
	fun setFilters(generic: Boolean, apk: Boolean){
		filters.clear()
		// Filters
		if (generic){
			for (folder in Constants.filter_genericFolders) filters.add(getRegexForFolder(folder))
			for (file in Constants.filter_genericFiles) filters.add(getRegexForFile(file))
		}
		// Android APKs and various split APK extensions
		if (apk){
			for (apk in Constants.filter_apkFiles) filters.add(getRegexForFile(apk))
		}

		// cached whitelist/blacklist values
		whitelist = WhitelistFragment.whiteListOn.mapNotNull { it }
		blacklist = BlacklistFragment.blackListOn.mapNotNull { it.toRegex() }
		// Auto whitelist
		if (autoWhite){
			autoWhitelist.clear()
			autoWhitelist.addAll(Constants.filter_autoWhite)
		}
	}

	/**
	 * Starts the scan and cleanup routine.
	 * Respects user's parallel search and parallel delete settings with configurable worker count (1-10).
	 * Accurately tracks bytes found or freed.
	 */
	fun start(): Long {
		isRunning = true
		var cycles: Byte = 0
		val maxCycles: Byte = if (delete) prefs.multiRun.toByte() else 1
		val isParallel = prefs.parallelProcessing
		val workerCount = prefs.parallelWorkers.coerceIn(1, 10)

		// removes the need to 'clean' multiple times to get everything
		while (cycles < maxCycles) {
			// cycle indicator
			addText?.invoke(context, "Running Cycle " + cycles + "/" + maxCycles, 0)

			// 1. Find / scan files
			if (foundFiles == null) {
				foundFiles = ArrayList<File>()
				addText?.invoke(context, context.getString(R.string.scanning_external), 0)

				if (isParallel && workerCount > 1) {
					// Gather roots to scan in parallel
					val scanRoots = ArrayList<File>()
					scanRoots.add(path)
					if (prefs.cleanSdCard) {
						scanRoots.addAll(ShizukuManager.getSecondaryStorageDirs(context))
					}
					foundFiles!!.addAll(getListFilesParallel(scanRoots, workerCount))
					foundFiles!!.addAll(getInternalStorageFiles())
				} else {
					foundFiles!!.addAll(getListFiles(path))
					foundFiles!!.addAll(getSecondarySdCardFiles())
					foundFiles!!.addAll(getInternalStorageFiles())
				}
			}

			guiScanProgressMax = guiScanProgressMax + foundFiles!!.size
			val filesSnapshot = foundFiles!!

			// 2. Filter & Delete files
			if (isParallel && workerCount > 1) {
				val executor = Executors.newFixedThreadPool(workerCount)
				for (file in filesSnapshot) {
					executor.execute {
						try {
							if (filter(file)) {
								// Accurately capture size in bytes before deleting
								val itemBytes = calculatePathBytes(file)
								val tv: TextView? = addText?.invoke(context, file.absolutePath, 1)
								bytesTotal.addAndGet(itemBytes)

								if (delete) {
									filesRemoved.incrementAndGet()
									var isDeleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
									if (!isDeleted && prefs.useShizuku && ShizukuManager.hasPermission()) {
										isDeleted = ShizukuManager.deleteElevated(file.absolutePath)
									}
									if (!isDeleted) {
										tv?.setTextColor(Color.GRAY)
									}
								}
							}
						} catch (_: Exception) {
						} finally {
							val currentProgress = guiScanProgressProgress.incrementAndGet()
							if (guiScanProgressMax > 0) {
								updateProgress?.invoke(context, currentProgress * 100.0 / guiScanProgressMax)
							}
						}
					}
				}
				executor.shutdown()
				try {
					executor.awaitTermination(5, TimeUnit.MINUTES)
				} catch (_: InterruptedException) {
				}
			} else {
				// Sequential execution
				for (file in filesSnapshot) {
					if (filter(file)) {
						val itemBytes = calculatePathBytes(file)
						val tv: TextView? = addText?.invoke(context, file.absolutePath, 1)
						bytesTotal.addAndGet(itemBytes)

						if (delete) {
							filesRemoved.incrementAndGet()
							var isDeleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
							if (!isDeleted && prefs.useShizuku && ShizukuManager.hasPermission()) {
								isDeleted = ShizukuManager.deleteElevated(file.absolutePath)
							}
							if (!isDeleted) {
								tv?.setTextColor(Color.GRAY)
							}
						}
					}
					val currentProgress = guiScanProgressProgress.incrementAndGet()
					if (guiScanProgressMax > 0) {
						updateProgress?.invoke(context, currentProgress * 100.0 / guiScanProgressMax)
					}
				}
			}

			foundFiles = null
			if (filesRemoved.get() == 0) break
			filesRemoved.set(0)
			++cycles
		}

		addText?.invoke(context, "Finished!", 1)
		isRunning = false
		return bytesTotal.get()
	}

	private fun getRegexForFolder(folder: String): String {
		return ".*(\\\\|/)$folder(\\\\|/|$).*"
	}

	private fun getRegexForFile(file: String): String {
		return ".+" + file.replace(".", "\\.") + "$"
	}

	companion object {
		var isRunning = false
		private val filters = ArrayList<String>()
		private var blacklist: List<Regex> = emptyList()
		private var whitelist: List<String> = emptyList()
		private val autoWhitelist: MutableList<String> = ArrayList<String>()
	}

	init {
		BlacklistFragment.getBlackList(prefs)
		BlacklistFragment.getBlacklistOn(prefs)
		WhitelistFragment.getWhiteList(prefs)
		WhitelistFragment.getWhitelistOn(prefs)
	}
}
