/*
 * SPDX-FileCopyrightText: 2020-2023 Hunter J Drum
 * SPDX-FileCopyrightText: 2024-2025 MDP43140
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.mdp43140.ltecleaner
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.mdp43140.ltecleaner.R
import io.mdp43140.ltecleaner.databinding.ActivityMainBinding
import io.mdp43140.ltecleaner.fragment.MainFragment
import io.mdp43140.ltecleaner.fragment.BlacklistFragment
import io.mdp43140.ltecleaner.AdbScriptDialog
import io.mdp43140.ltecleaner.shizuku.ShizukuManager
import io.mdp43140.ltecleaner.fragment.WhitelistFragment
import io.mdp43140.ltecleaner.fragment.SettingsFragment
class MainActivity: AppCompatActivity(){
	val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
	lateinit var dialogBuilder: MaterialAlertDialogBuilder
	override fun onCreate(savedInstanceState: Bundle?){
		super.onCreate(savedInstanceState)
		ShizukuManager.init()
		setContentView(binding.root)
		// Set black background
		if (CommonFunctions.isDarkThemeActive(this) && App.prefs!!.pitchBlack)
			window.decorView.setBackgroundColor(Color.BLACK)
		// Start Main Fragment
		val mainFrag = MainFragment()
		startFragment(mainFrag)
		// Load whitelist
		WhitelistFragment.getWhiteList(App.prefs)
		dialogBuilder = MaterialAlertDialogBuilder(this)
		// Handle intent action (from shortcut stuff)
		val intentAction = intent.getStringExtra("action")
		when (intentAction){
			"cleanup" -> mainFrag.clean()
			"stopBgApps" -> mainFrag.stopBgApps()
			else -> if (intentAction != null) Toast.makeText(this, "Invalid intent action: $intentAction", Toast.LENGTH_SHORT).show()
		}
	}
	/**
	 * Used by child fragments to start
	 * a Fragment inside Activity's fragment
	 * scope. Based on Akane Tan's code, without
	 * requiring androidx.fragments dependency
	 *
	 * @param frag: Target fragment
	 */
	fun startFragment(frag: Fragment, args: (Bundle.() -> Unit)? = null) {
		supportFragmentManager
			.beginTransaction().apply {
				// If last fragment is available, move to backstack, hide, add new one
				// else (first startup) load fragment
				val lastFrag = supportFragmentManager.fragments.lastOrNull()
				val fragArgs = frag.apply { args?.let { arguments = Bundle().apply(it) } }
				if (lastFrag == null){
					replace(R.id.fragment_container, fragArgs)
				} else {
					addToBackStack(System.currentTimeMillis().toString())
					hide(lastFrag)
					add(R.id.fragment_container, fragArgs)
				}
				commit()
			}
	}
	override fun onDestroy() {
		super.onDestroy()
		ShizukuManager.destroy()
	}

	/**
	 * Handles whether the user grants permission.
	 * Shows an alert dialog asking
	 * user to give storage permission or configure via Shizuku / ADB.
	 */
	override fun onRequestPermissionsResult(
		requestCode:Int,
		permissions:Array<String>,
		grantResults:IntArray
	){
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
			val builder = MaterialAlertDialogBuilder(this)
				.setTitle(R.string.permission_needed)
				.setMessage(getString(R.string.grantPermissions_sum) + "\n\n" +
					getString(R.string.adb_script_dialog_desc))
				.setPositiveButton(R.string.settings) { dialogInterface: DialogInterface, _: Int ->
					startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
						data = Uri.fromParts("package", packageName, null)
					})
					dialogInterface.dismiss()
				}
				.setNeutralButton("ADB / Shizuku") { dialogInterface: DialogInterface, _: Int ->
					AdbScriptDialog.show(this)
					dialogInterface.dismiss()
				}
				.setNegativeButton(android.R.string.cancel) { dialogInterface: DialogInterface, _: Int ->
					dialogInterface.dismiss()
				}

			if (ShizukuManager.hasPermission()) {
				builder.setPositiveButton(R.string.grant_via_shizuku) { dialogInterface: DialogInterface, _: Int ->
					val success = ShizukuManager.grantStoragePermissionsViaShizuku(packageName)
					if (success) {
						Toast.makeText(this, R.string.storage_permission_granted_toast, Toast.LENGTH_SHORT).show()
					}
					dialogInterface.dismiss()
				}
			}
			builder.show()
		}
	}
}
