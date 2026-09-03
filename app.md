# LTE Cleaner - State-Managed Project Manifest

## System Architecture Diagram & Data Models

```
+-----------------------------------------------------------------------------------+
|                                   USER INTERFACE                                  |
|  +------------------------------+             +--------------------------------+  |
|  |         MainFragment         |             |        SettingsFragment        |  |
|  | - Analyze / Clean Trigger    |             | - Shizuku Service Toggle       |  |
|  | - Internal / External Stats  |             | - Clean Internal Cache Toggle  |  |
|  | - Elevated Access Status Chip|             | - Clean SD Card Toggle         |  |
|  | - ADB Permission Prompt      |             | - ADB Setup Helper Dialog      |  |
|  +--------------+---------------+             +---------------+----------------+  |
+-----------------|---------------------------------------------|-------------------+
                  |                                             |
                  v                                             v
+-----------------------------------------------------------------------------------+
|                               MANAGEMENT & LOGIC                                  |
|  +-------------------------------------+   +-----------------------------------+  |
|  |            ShizukuManager           |   |       PreferenceRepository        |  |
|  | - Shizuku Binder lifecycle listener |   | - useShizuku: Boolean             |  |
|  | - Permission check & request        |   | - cleanInternal: Boolean          |  |
|  | - Shizuku Shell Execution (UID 2000)|   | - cleanSdCard: Boolean            |  |
|  | - Auto-grant MANAGE_EXTERNAL_STORAGE|   +-----------------------------------+  |
|  | - ADB Setup Script Generator        |                                          |
|  +------------------+------------------+                                          |
|                     |                                                             |
|                     v                                                             |
|  +-----------------------------------------------------------------------------+  |
|  |                                  FileScanner                                |  |
|  | - Primary External Storage (/storage/emulated/0)                            |  |
|  | - Secondary External Storage (SD Cards /storage/XXXX-XXXX)                  |  |
|  | - Restricted Android/data & Android/obb (via elevated Shizuku shell)        |  |
|  | - Internal Storage (/data/data/*/cache, /data/user/0/*/cache, /data/local) |  |
|  | - Elevated deletion fallback (rm -rf via Shizuku shell)                     |  |
|  +-----------------------------------------------------------------------------+  |
+-----------------------------------------------------------------------------------+
```

### Data Models
- `ShizukuState`: Enum (`NOT_INSTALLED`, `DEAD`, `AVAILABLE_UNAUTHORIZED`, `AUTHORIZED`)
- `StorageTarget`: Represents a storage partition (`PRIMARY_EXTERNAL`, `SECONDARY_SD`, `INTERNAL_CACHE`)
- `ShellResult`: Container for `exitCode`, `stdout`, and `stderr`

---

## Full Checklist of Files to Implement

- [x] `gradle/libs.versions.toml` - Add `dev.rikka.shizuku:api` and `provider`
- [x] `app/build.gradle.kts` - Include Shizuku dependencies in app module
- [x] `app/src/main/AndroidManifest.xml` - Add `API_V23` permission, `ShizukuProvider`, and `PACKAGE_USAGE_STATS`
- [x] `app/src/main/java/io/mdp43140/ltecleaner/shizuku/ShizukuManager.kt` - Complete Shizuku manager, permission handler, elevated shell executor, and ADB script generator
- [x] `app/src/main/java/io/mdp43140/ltecleaner/PreferenceRepository.kt` - Add preferences for Shizuku, internal storage, and SD cards
- [x] `app/src/main/res/values/strings.xml` - Add UI strings for Shizuku, ADB scripts, and internal/external storage options
- [x] `app/src/main/res/xml/preferences.xml` - Add Elevated Access (Shizuku & ADB) settings category and switches
- [x] `app/src/main/res/drawable/ic_shield.xml` - Shield vector drawable for elevated access status
- [x] `app/src/main/res/layout/fragment_main.xml` - Add access status chip
- [x] `app/src/main/java/io/mdp43140/ltecleaner/AdbScriptDialog.kt` - Material dialog for ADB scripts & instructions with copy functionality
- [x] `app/src/main/java/io/mdp43140/ltecleaner/FileScanner.kt` - Enhanced scanner covering internal storage, secondary SD cards, and elevated deletion
- [x] `app/src/main/java/io/mdp43140/ltecleaner/MainActivity.kt` - Register Shizuku binder and permission listeners
- [x] `app/src/main/java/io/mdp43140/ltecleaner/fragment/MainFragment.kt` - Display elevated access status and multi-storage scan execution
- [x] `app/src/main/java/io/mdp43140/ltecleaner/fragment/SettingsFragment.kt` - Wire Shizuku status, ADB script dialog, and preference callbacks
- [x] `app/src/main/java/io/mdp43140/ltecleaner/Constants.kt` - Configure default whitelist (7 items), default blacklist (75 regex rules), and enabled filters
- [x] `app/src/main/java/io/mdp43140/ltecleaner/PreferenceRepository.kt` - Set all requested defaults (pitch_black=false, close_bg_apps=true, one_click=true, run_count=56, multi_run=3, theme=dark, etc.) and auto-seed mechanism
- [x] `app/src/main/java/io/mdp43140/ltecleaner/App.kt` - Initialize default configuration and handle run_count baseline
- [x] `app/src/main/res/xml/preferences.xml` - Synchronize XML preference defaultValue attributes with user default app data
- [x] `app/build.gradle.kts` - Remove LeakCanary dependency to prevent companion "Leaks" app installation
- [x] `app/src/main/res/xml/preferences.xml` - Add Performance & Concurrency settings category with parallel processing switch and parallel workers SeekBar (1-10)
- [x] `app/src/main/java/io/mdp43140/ltecleaner/PreferenceRepository.kt` - Add parallelProcessing and parallelWorkers properties with defaults
- [x] `app/src/main/java/io/mdp43140/ltecleaner/CommonFunctions.kt` - Accurate unit conversion for freed bytes (B, KB, MB, GB) with floating point division
- [x] `app/src/main/java/io/mdp43140/ltecleaner/FileScanner.kt` - Multi-threaded parallel file exploration and deletion with thread-safe counters and recursive directory sizing

## Current Status
- Completed Steps: 24/24
- Current step in progress: None (All tasks complete)
- Exact next step: Ready for deployment and runtime testing
