# Video Downloader (Android)

A minimal, Play-Store-safe starter project: paste a URL, the app checks whether
it points directly to a video file (via HTTP `Content-Type` / file extension),
and if so downloads it in the background into `Movies/VideoDownloader` using
Scoped Storage (MediaStore API).

**Deliberately does not** scrape/extract from YouTube, Instagram, TikTok, etc. —
see the policy discussion in the chat this was generated from.

## How to open this project

1. Install **Android Studio** (Koala or newer).
2. `File > Open` and select the `VideoDownloader` folder.
3. Let Gradle sync (it will download the Gradle wrapper, AGP, Kotlin, and the
   dependencies listed in `app/build.gradle.kts` — this needs internet access).
4. Run on an emulator or device (`minSdk 24`, i.e. Android 7.0+).

> Note: this project does not include the Gradle wrapper jar/scripts
> (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`) since those are binary/generated
> files. Android Studio will generate them automatically on first open, or run
> `gradle wrapper` if you have Gradle installed locally.

> Note: launcher icons (`mipmap-*`) are not included. Android Studio's
> **Image Asset Studio** (`res > New > Image Asset`) will generate them for you
> in a couple of clicks — or Studio will just use a default icon until you do.

## Project structure

```
app/src/main/java/com/example/videodownloader/
├── MainActivity.kt          # Compose UI: URL input, check, download button, progress
├── data/
│   ├── LinkChecker.kt        # Detects direct media links (HEAD request)
│   └── DownloadViewModel.kt  # UI state + triggers WorkManager download
└── download/
    └── DownloadWorker.kt     # Background download via OkHttp -> MediaStore
```

## How the "is this downloadable?" check works

`LinkChecker` sends a `HEAD` request to the pasted URL and looks at:
- The `Content-Type` response header (must start with `video/` or `audio/`), or
- The URL's file extension (`.mp4`, `.mov`, `.mkv`, `.webm`, etc.)

If neither matches, the app tells the user the link isn't supported, rather
than trying to guess/extract a stream — this is what keeps it inside Play
Store policy.

## Now included: history screen + notifications

- **Download history screen** (`HistoryScreen.kt` + `HistoryViewModel.kt` +
  `DownloadHistoryRepository.kt`): tap "Downloads" on the main screen to see
  everything saved to `Movies/VideoDownloader`, with play (opens system video
  player) and delete actions.
- **Foreground progress notification** (`DownloadNotifications.kt`, wired into
  `DownloadWorker.kt`): shows a persistent notification with live percentage
  while downloading, and a "complete/failed" notification when done — so
  progress is visible even if the user leaves the app.

## Next steps you'll likely want

- **Pause/resume**: OkHttp supports range requests (`Range` header) — worth
  adding once basic download works end-to-end.
- **App icon + splash** via Image Asset Studio.
- **Play Store listing language**: avoid naming specific platforms (YouTube,
  Instagram, etc.) in your store listing/marketing, since the app doesn't
  support them — this also keeps you clearly within policy.

## Permissions used

- `INTERNET`, `ACCESS_NETWORK_STATE` — fetch the file.
- `POST_NOTIFICATIONS` (Android 13+) — for WorkManager's optional progress
  notifications; download still works if denied.
- No `WRITE_EXTERNAL_STORAGE` needed — Scoped Storage (MediaStore) handles
  saving without broad storage permission on Android 10+.
