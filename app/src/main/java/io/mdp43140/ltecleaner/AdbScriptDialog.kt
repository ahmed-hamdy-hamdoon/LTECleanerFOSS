/*
 * SPDX-FileCopyrightText: 2024-2026 MDP43140 & LTE Cleaner Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.mdp43140.ltecleaner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.mdp43140.ltecleaner.shizuku.ShizukuManager

object AdbScriptDialog {

	fun show(context: Context) {
		val packageName = context.packageName
		val scriptText = ShizukuManager.getAdbScript(packageName)

		val container = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(48, 24, 48, 16)
		}

		val descTextView = TextView(context).apply {
			text = context.getString(R.string.adb_script_dialog_desc)
			setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
			setPadding(0, 0, 0, 24)
		}
		container.addView(descTextView)

		// Monospace script container
		val codeScrollView = ScrollView(context).apply {
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				420
			)
			setBackgroundResource(R.drawable.rounded_view)
			setPadding(24, 24, 24, 24)
		}

		val codeTextView = TextView(context).apply {
			text = scriptText
			typeface = Typeface.MONOSPACE
			setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
			setTextIsSelectable(true)
		}
		codeScrollView.addView(codeTextView)
		container.addView(codeScrollView)

		// Action buttons row
		val buttonLayout = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(0, 24, 0, 0)
		}

		val copyButton = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonStyle).apply {
			text = context.getString(R.string.copy_adb_commands)
			setOnClickListener {
				val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
				val clip = ClipData.newPlainText("LTECleaner ADB Script", scriptText)
				clipboard.setPrimaryClip(clip)
				Toast.makeText(context, R.string.adb_copied_toast, Toast.LENGTH_SHORT).show()
			}
		}
		buttonLayout.addView(copyButton)

		if (ShizukuManager.hasPermission()) {
			val grantViaShizukuBtn = MaterialButton(
				context,
				null,
				com.google.android.material.R.attr.materialButtonOutlinedStyle
			).apply {
				text = context.getString(R.string.grant_via_shizuku)
				setOnClickListener {
					val success = ShizukuManager.grantStoragePermissionsViaShizuku(packageName)
					if (success) {
						Toast.makeText(context, R.string.storage_permission_granted_toast, Toast.LENGTH_SHORT).show()
					} else {
						Toast.makeText(context, "Failed to execute grant via Shizuku", Toast.LENGTH_SHORT).show()
					}
				}
			}
			buttonLayout.addView(grantViaShizukuBtn)
		}

		container.addView(buttonLayout)

		MaterialAlertDialogBuilder(context)
			.setTitle(R.string.adb_script_dialog_title)
			.setView(container)
			.setPositiveButton(android.R.string.ok) { dialog, _ ->
				dialog.dismiss()
			}
			.setNeutralButton("Shizuku App") { dialog, _ ->
				ShizukuManager.openShizukuApp(context)
				dialog.dismiss()
			}
			.show()
	}
}
