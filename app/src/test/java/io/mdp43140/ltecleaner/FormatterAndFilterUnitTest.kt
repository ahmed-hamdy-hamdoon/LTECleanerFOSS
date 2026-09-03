/*
 * SPDX-FileCopyrightText: 2024-2026 LTE Cleaner Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.mdp43140.ltecleaner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

/**
 * Fast, focused local JVM unit tests for formatter logic, converters, and filter regexes.
 * Follows android-testing-unit skill guidelines.
 */
class FormatterAndFilterUnitTest {

	@Test
	fun convertSize_returnsZeroB_forNonPositiveValues() {
		assertEquals("0 B", CommonFunctions.convertSize(0L))
		assertEquals("0 B", CommonFunctions.convertSize(-1024L))
	}

	@Test
	fun convertSize_formatsBytesCorrectly() {
		assertEquals("500 B", CommonFunctions.convertSize(500L))
		assertEquals("1023 B", CommonFunctions.convertSize(1023L))
	}

	@Test
	fun convertSize_formatsKilobytesCorrectly() {
		assertEquals("1 KB", CommonFunctions.convertSize(1024L))
		assertEquals("1.5 KB", CommonFunctions.convertSize(1536L))
		assertEquals("100 KB", CommonFunctions.convertSize(1024L * 100))
	}

	@Test
	fun convertSize_formatsMegabytesCorrectly() {
		assertEquals("1 MB", CommonFunctions.convertSize(1024L * 1024L))
		assertEquals("2.5 MB", CommonFunctions.convertSize((1024L * 1024L * 2.5).toLong()))
		assertEquals("500 MB", CommonFunctions.convertSize(1024L * 1024L * 500))
	}

	@Test
	fun convertSize_formatsGigabytesCorrectly() {
		assertEquals("1 GB", CommonFunctions.convertSize(1024L * 1024L * 1024L))
		assertEquals("4.25 GB", CommonFunctions.convertSize((1024L * 1024L * 1024L * 4.25).toLong()))
	}

	@Test
	fun blacklistDefault_matchesKnownJunkPatterns() {
		val patterns = Constants.blacklistDefault.map { Pattern.compile(it) }

		val sampleLog = "/storage/emulated/0/Android/data/com.example/logs"
		val sampleTmp = "/storage/emulated/0/Download/sample.tmp"
		val sampleCrdownload = "/storage/emulated/0/Download/file.crdownload"
		val sampleLostDir = "/storage/emulated/0/LOST.DIR"

		assertTrue("Should match log folder", patterns.any { it.matcher(sampleLog).matches() })
		assertTrue("Should match tmp file", patterns.any { it.matcher(sampleTmp).matches() })
		assertTrue("Should match partial download", patterns.any { it.matcher(sampleCrdownload).matches() })
		assertTrue("Should match LOST.DIR", patterns.any { it.matcher(sampleLostDir).matches() })
	}

	@Test
	fun constants_whitelistAndBlacklistDefaults_notEmpty() {
		assertTrue(Constants.blacklistDefault.isNotEmpty())
		assertTrue(Constants.whitelistDefault.isNotEmpty())
		assertTrue(Constants.filter_genericFiles.isNotEmpty())
		assertTrue(Constants.filter_genericFolders.isNotEmpty())
	}
}
