# Photos — Kotlin Compose Multiplatform App

Android app that mirrors the web UI feature set, with gallery upload and auto-sync.

## Features

| Feature | Details |
|---|---|
| Login | Email/password auth → JWT token stored in SharedPreferences |
| Folder vault | Password-protect your photos (X-Folder-Password header) |
| Gallery | Photos grouped by date, search, favorites, full lightbox with prev/next |
| Bulk actions | Multi-select, bulk delete, add to album |
| Upload | Pick multiple images from device gallery, track upload status per image |
| Auto-upload | Background WorkManager task syncs new gallery photos every 15 min |
| Albums | Create, view, delete albums; tap to see album detail with lightbox |
| Favorites | Heart-based favorites stored locally (persisted to SharedPreferences) |

## Setup

### 1. Configure API base URL

Edit `composeApp/build.gradle.kts`:
```kotlin
buildConfigField("String", "API_BASE_URL", "\"http://YOUR_SERVER_IP\"")
```
- Android emulator → `http://10.0.2.2`
- Real device on same Wi-Fi → `http://192.168.x.x`

### 2. Build

```bash
cd compose-app
./gradlew assembleDebug
```

Requires Android SDK 35 (compileSdk) and JDK 17+.

### 3. Install on device/emulator

```bash
./gradlew installDebug
```

## Project structure

```
composeApp/src/
├── androidMain/
│   ├── AndroidManifest.xml
│   └── kotlin/com/sevis/photos/
│       ├── MainActivity.kt          # Entry point, wires pickers & persistence
│       ├── PhotosApplication.kt     # Coil ImageLoader with dynamic auth headers
│       └── autoupload/
│           ├── AutoUploadWorker.kt  # WorkManager CoroutineWorker
│           ├── AutoUploadScheduler.kt
│           ├── BootReceiver.kt      # Re-schedules on boot
│           └── MediaStoreHelper.kt  # Queries new photos from MediaStore
└── commonMain/
    └── kotlin/com/sevis/photos/
        ├── App.kt                   # Root composable + NavHost
        ├── AppState.kt              # In-memory session state
        ├── data/
        │   ├── Models.kt
        │   └── PhotoApi.kt          # Ktor HTTP client
        └── screens/
            ├── LoginScreen.kt
            ├── FolderCheckScreen.kt
            ├── FolderSetupScreen.kt
            ├── FolderUnlockScreen.kt
            ├── ShellScreen.kt       # Bottom nav shell
            ├── GalleryScreen.kt     # Main gallery with lightbox
            ├── UploadScreen.kt      # Manual pick + auto-upload toggle
            ├── AlbumsScreen.kt
            └── AlbumDetailScreen.kt
```

## Permissions requested

- `READ_MEDIA_IMAGES` (Android 13+) / `READ_EXTERNAL_STORAGE` — gallery access
- `INTERNET` — API calls
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` — background upload
- `RECEIVE_BOOT_COMPLETED` — restart auto-upload after reboot
- `POST_NOTIFICATIONS` — upload completion notifications
