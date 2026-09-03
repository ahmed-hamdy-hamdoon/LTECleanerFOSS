/*
 * SPDX-FileCopyrightText: 2020-2023 Hunter J Drum
 * SPDX-FileCopyrightText: 2024-2025 MDP43140
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.mdp43140.ltecleaner
import android.app.Application
import android.content.Context
import com.google.android.material.color.DynamicColors
//import io.mdp43140.ltecleaner.CommonFunctions
import io.mdp43140.ael.ErrorLogger
class App: Application(){
	private var runCount = 0
	override fun attachBaseContext(base: Context){
		super.attachBaseContext(base)
		// Catches bugs and crashes, and makes it easy to report the bug
		if (ErrorLogger.instance == null){
			ErrorLogger(base)
			ErrorLogger.instance?.isNotification = true // sends a notification
			ErrorLogger.reportUrl = "https://github.com/mdp43140/LTECleanerFOSS/issues/new"
		}
	}
	override fun onCreate(){
		super.onCreate()
		prefs = PreferenceRepository(this)
		val isFirstInit = !prefs!!.isDefaultsConfigured()
		prefs!!.initializeDefaultsIfNecessary()
		// Stores how many times the app has been opened
		if (!isFirstInit) {
			prefs!!.runCount = prefs!!.runCount + 1
		}
		// Update theme and apply dynamic color
		CommonFunctions.updateTheme(this,prefs!!.theme)
		if (prefs!!.dynamicColor){
			DynamicColors.applyToActivitiesIfAvailable(this)
			DynamicColors.wrapContextIfAvailable(this)
		}
	}
	companion object {
		var prefs: PreferenceRepository? = null
	}
}
