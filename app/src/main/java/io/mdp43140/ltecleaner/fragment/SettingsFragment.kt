/*
 * SPDX-FileCopyrightText: 2020-2023 Hunter J Drum
 * SPDX-FileCopyrightText: 2024-2025 MDP43140
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.mdp43140.ltecleaner.fragment
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialSharedAxis
import io.mdp43140.ael.ErrorLogger
import io.mdp43140.ltecleaner.util.putData // SharedPreferencesExtension.kt
import org.json.JSONArray
import org.json.JSONObject
import io.mdp43140.ltecleaner.App
import io.mdp43140.ltecleaner.CleanupService
import io.mdp43140.ltecleaner.CommonFunctions
import io.mdp43140.ltecleaner.MainActivity
import io.mdp43140.ltecleaner.AdbScriptDialog
import io.mdp43140.ltecleaner.shizuku.ShizukuManager
import io.mdp43140.ltecleaner.ScheduledWorker.Companion.enqueueWork
import io.mdp43140.ltecleaner.R
class SettingsFragment: PreferenceFragmentCompat(){
	private val shizukuStateListener = {
		activity?.runOnUiThread {
			updateShizukuPreference()
		}
		Unit
	}
	private val importFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		if (uri != null){
			val jsonObject = JSONObject(
				requireContext().contentResolver.openInputStream(uri)
					?.use { it.readBytes() }
					!!.toString(Charsets.UTF_8)
			)
			val prefsEditor = PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
			val text = buildString {
				for (key in jsonObject.keys()){
					val value = jsonObject.get(key)
					if (prefsEditor?.putData(key,value) != true)
						append("\n- $key: $value")
				}
			}
			prefsEditor?.apply()
			Snackbar.make(
				(requireActivity() as MainActivity).binding.root,
				if (text == "") "Settings imported!" else "Unsupported data type:${text}",
				Snackbar.LENGTH_SHORT
			).let {
				it.setAction(getString(android.R.string.ok)){ _: View ->
					it.dismiss()
				}
				it.show()
			}
			(requireActivity() as MainActivity).startFragment(SettingsFragment())
		}
	}
	private val exportFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
		if (uri != null){
			val jsonData: String = JSONObject(PreferenceManager.getDefaultSharedPreferences(requireContext()).all as Map<String, Any?>).toString(2)
			CommonFunctions.writeContentToUri(requireContext(), uri, jsonData)
			Snackbar.make(
				(requireActivity() as MainActivity).binding.root,
				"Settings exported!",
				Snackbar.LENGTH_SHORT
			).let {
				it.setAction(getString(android.R.string.ok)){ _: View ->
					it.dismiss()
				}
				it.show()
			}
		}
	}

	override fun onViewCreated(
		v: View,
		savedInstanceState: Bundle?
	){
		super.onViewCreated(v,savedInstanceState)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
			// Handle Edge-to-edge
			v.setOnApplyWindowInsetsListener { v, windowInsets ->
				val insets = windowInsets.getInsets(
					WindowInsets.Type.systemBars() or
					WindowInsets.Type.displayCutout()
				)
				v.setPadding(
					insets.left,
					insets.top,
					insets.right,
					insets.bottom
				)
				WindowInsets.CONSUMED
			}
		}
	}
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setHasOptionsMenu(true) // deprecated, newer api dont need boolean, but compile error
		enterTransition   = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true )
		returnTransition  = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)
		exitTransition    = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true )
		reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)
		findPreference<Preference>("blacklist")!!.setOnPreferenceClickListener {
			(requireActivity() as MainActivity).startFragment(BlacklistFragment())
			false
		}
		findPreference<Preference>("whitelist")!!.setOnPreferenceClickListener {
			(requireActivity() as MainActivity).startFragment(WhitelistFragment())
			false
		}
		findPreference<Preference>("clean_every")!!.setOnPreferenceChangeListener { _:Preference, _:Any? ->
			enqueueWork(requireContext().applicationContext)
			true
		}
		findPreference<Preference>("theme")!!.setOnPreferenceChangeListener { _:Preference, value:Any? ->
			val themeStr = resources.getStringArray(R.array.themes_key)
			AppCompatDelegate.setDefaultNightMode(when (value){
				themeStr[1] -> AppCompatDelegate.MODE_NIGHT_NO
				themeStr[2] -> AppCompatDelegate.MODE_NIGHT_YES
				else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
			})
			true
		}
		findPreference<Preference>("data_import")!!.setOnPreferenceClickListener {
			importFileLauncher.launch(arrayOf("application/json"))
			false
		}
		findPreference<Preference>("data_export")!!.setOnPreferenceClickListener {
			exportFileLauncher.launch("LTECleaner_settings.json")
			false
		}
		findPreference<Preference>("adb_script_helper")?.setOnPreferenceClickListener {
			AdbScriptDialog.show(requireContext())
			true
		}
		findPreference<Preference>("shizuku_status")?.setOnPreferenceClickListener {
			handleShizukuStatusClick()
			true
		}
		updateShizukuPreference()
	}

	override fun onResume() {
		super.onResume()
		ShizukuManager.registerListener(shizukuStateListener)
		updateShizukuPreference()
	}

	override fun onPause() {
		super.onPause()
		ShizukuManager.unregisterListener(shizukuStateListener)
	}

	private fun updateShizukuPreference() {
		val pref = findPreference<Preference>("shizuku_status") ?: return
		val ctx = context ?: return
		when (ShizukuManager.getState(ctx)) {
			ShizukuManager.ShizukuState.AUTHORIZED -> {
				pref.setTitle(R.string.shizuku_status_title)
				pref.setSummary(R.string.shizuku_status_authorized)
			}
			ShizukuManager.ShizukuState.AVAILABLE_UNAUTHORIZED -> {
				pref.setTitle(R.string.shizuku_status_title)
				pref.setSummary(R.string.shizuku_status_unauthorized)
			}
			ShizukuManager.ShizukuState.DEAD -> {
				pref.setTitle(R.string.shizuku_status_title)
				pref.setSummary(R.string.shizuku_status_dead)
			}
			ShizukuManager.ShizukuState.NOT_INSTALLED -> {
				pref.setTitle(R.string.shizuku_status_title)
				pref.setSummary(R.string.shizuku_status_not_installed)
			}
		}
	}

	private fun handleShizukuStatusClick() {
		val ctx = requireContext()
		when (ShizukuManager.getState(ctx)) {
			ShizukuManager.ShizukuState.AUTHORIZED -> {
				val success = ShizukuManager.grantStoragePermissionsViaShizuku(ctx.packageName)
				if (success) {
					Toast.makeText(ctx, R.string.storage_permission_granted_toast, Toast.LENGTH_SHORT).show()
				} else {
					Toast.makeText(ctx, "Shizuku is active & authorized!", Toast.LENGTH_SHORT).show()
				}
			}
			ShizukuManager.ShizukuState.AVAILABLE_UNAUTHORIZED -> {
				ShizukuManager.requestPermission()
			}
			ShizukuManager.ShizukuState.DEAD -> {
				ShizukuManager.openShizukuApp(ctx)
			}
			ShizukuManager.ShizukuState.NOT_INSTALLED -> {
				ShizukuManager.openShizukuApp(ctx)
			}
		}
	}

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		setPreferencesFromResource(R.xml.preferences,rootKey)
	}
}
