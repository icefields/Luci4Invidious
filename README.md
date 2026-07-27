# Luci4Invidious

A minimal Android app that intercepts YouTube links and redirects them to a self-hosted [Invidious](https://invidious.io) instance, opening them inside an in-app WebView with built-in HTTP Basic Authentication.

No YouTube. No Google. No tracking. Just a clean pipe to your own Invidious front-end.

---

## Table of Contents

- [What It Does](#what-it-does)
- [Requirements](#requirements)
- [Installation](#installation)
- [Usage](#usage)
- [Configuration](#configuration)
- [How It Works](#how-it-works)
- [Project Structure](#project-structure)
- [Building from Source](#building-from-source)
- [Testing](#testing)
- [Architecture and Design Decisions](#architecture-and-design-decisions)
- [Intent Filters Reference](#intent-filters-reference)
- [URL Conversion Logic](#url-conversion-logic)
- [Security Considerations](#security-considerations)
- [Troubleshooting](#troubleshooting)
- [License](#license)

---

## What It Does

When you tap a YouTube link anywhere on your phone, in a chat, browser, email, any app, Android will offer **Luci4Invidious** as an app to open it with. Instead of launching the YouTube app or opening youtube.com, it:

1. Converts the YouTube URL to the equivalent Invidious URL
2. Opens it in an embedded WebView
3. Automatically sends your HTTP Basic Auth credentials so you don't have to log in manually

Supported YouTube URL formats:

| Source URL | Converted To |
|---|---|
| `https://www.youtube.com/watch?v=ID` | `https://my.invidious.org/watch?v=ID` |
| `https://youtube.com/watch?v=ID` | `https://my.invidious.org/watch?v=ID` |
| `https://m.youtube.com/watch?v=ID` | `https://my.invidious.org/watch?v=ID` |
| `https://music.youtube.com/watch?v=ID` | `https://my.invidious.org/watch?v=ID` |
| `https://youtu.be/ID` | `https://my.invidious.org/watch?v=ID` |
| `https://www.youtube.com/shorts/ID` | `https://my.invidious.org/shorts/ID` |
| `https://www.youtube.com/embed/ID` | `https://my.invidious.org/embed/ID` |

Query parameters (timestamps, playlists, etc.) are preserved:

| Source URL | Converted To |
|---|---|
| `https://youtu.be/ID?t=120` | `https://my.invidious.org/watch?v=ID&t=120` |
| `https://youtube.com/watch?v=ID&list=PL...` | `https://my.invidious.org/watch?v=ID&list=PL...` |

If you open the app directly (no link), it loads the Invidious homepage.

---

## Requirements

- An Android device running **Android 7.0 (API 24)** or higher
- A self-hosted (or hosted) Invidious instance with HTTP Basic Authentication enabled
- For building: **Android Studio** or **Gradle 9.4.1+** with **JDK 17**, **AGP 9.2.1**, **Kotlin 2.2.10**

---

## Installation

### Option A: Build with Android Studio

1. Open Android Studio
2. **File > Open** > select `~/Code/Android/Luci4Invidious`
3. Let Gradle sync complete
4. Connect your device (USB debugging enabled) or start an emulator
5. **Run > Run 'app'**

### Option B: Build from command line

```bash
cd ~/Code/Android/Luci4Invidious
gradle wrapper          # generates ./gradlew if not present
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

Install it on your device:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### After Installation

When you first tap a YouTube link, Android will ask which app to open it with. Select **Luci4Invidious** and tap **Always** to make it the default handler for YouTube links.

To change this later: **Settings > Apps > Luci4Invidious > Open by default > Clear defaults**

---

## Usage

There's no UI beyond the WebView itself. The app is intentionally transparent, it's just a gateway.

- **Tap a YouTube link** > opens in Invidious via the app
- **Back button** > exits fullscreen video first, then navigates WebView history, then exits the app
- **Open app from launcher** > loads the Invidious homepage
- **Links clicked inside the WebView** > YouTube links are automatically converted to Invidious URLs; non-Invidious external links open in the system browser
- **Fullscreen video** > tap the fullscreen button in the Invidious player to enter immersive fullscreen; system bars hide and reappear on exit

---

## Configuration

All configuration lives in a gitignored secrets file at the project root:

**`secrets.properties`** (not committed):

```properties
INVIDIOUS_HOST=my.invidious.org
INVIDIOUS_USER=user
INVIDIOUS_PASS=pass
```

A template is provided at **`secrets.properties.example`** (committed). Copy it to `secrets.properties` and fill in your real credentials:

```bash
cp secrets.properties.example secrets.properties
# Edit secrets.properties with your real values
```

The Gradle build reads `secrets.properties` and injects the values into `BuildConfig`, making them available at runtime as `BuildConfig.INVIDIOUS_HOST`, `BuildConfig.INVIDIOUS_USER`, and `BuildConfig.INVIDIOUS_PASS`.

No settings screen, no SharedPreferences. Change `secrets.properties`, rebuild, reinstall.

---

## How It Works

### 1. Intent Filters (AndroidManifest.xml)

The app declares multiple intent filters that match `VIEW` intents with YouTube URLs. When Android encounters a YouTube link, it offers this app as a handler. The filters cover:

- `youtube.com`, `www.youtube.com`, `m.youtube.com`, `music.youtube.com` for `/watch`, `/shorts/`, `/embed/` paths
- `youtu.be` for all paths
- Both `http` and `https` schemes

### 2. URL Conversion (UrlConverter.kt)

A pure Kotlin class (`UrlConverter`) takes the YouTube URL, parses it with `java.net.URL`, and produces the Invidious equivalent. The logic is:

- **`youtu.be/VIDEO_ID`** > strip the leading `/`, convert to `/watch?v=VIDEO_ID`
- **`youtube.com/watch?v=ID`** > keep the path and query, swap the host
- **`youtube.com/shorts/ID`** > keep the path, swap the host
- **`youtube.com/embed/ID`** > keep the path, swap the host
- **Non-YouTube URLs** > return `null` (app ignores them)
- **YouTube homepage** > return `null` (not a video link)

This class has zero Android dependencies, it uses only JDK classes, so it's fully unit-testable on the JVM.

### 3. WebView with Basic Auth (MainActivity.kt)

`MainActivity` uses Jetpack Compose with `AndroidView` to host a `WebView` inside a `FrameLayout`. It:

- Enables JavaScript and DOM storage (Invidious needs both)
- Enables `mediaPlaybackRequiresUserGesture = false` (allows autoplay)
- Disables Safe Browsing (prevents URL leakage to Google)
- Overrides `onReceivedHttpAuthRequest` to inject credentials via `handler.proceed(user, pass)` for all auth challenges
- Overrides `shouldOverrideUrlLoading` to intercept YouTube links clicked inside the WebView and convert them; non-Invidious external links are sent to the system browser
- Sets a `WebChromeClient` with `onShowCustomView`/`onHideCustomView` for fullscreen video support, hiding and showing system bars via `WindowInsetsControllerCompat`
- Uses `WindowCompat.setDecorFitsSystemWindows(false)` with `systemBarsPadding()` for edge-to-edge layout
- Implements back-button handling: exit fullscreen first, then WebView history, then app exit

### 4. Launch Flow

```
User taps YouTube link
        |
        v
Android resolves intent > Luci4Invidious (MainActivity)
        |
        v
intent.data > UrlConverter.convert()
        |
        +- YouTube URL > "https://my.invidious.org/watch?v=ID"
        +- Not YouTube  > null > load Invidious homepage
        |
        v
webView.loadUrl(convertedUrl)
        |
        v
onReceivedHttpAuthRequest > proceed(user, pass)
        |
        v
Invidious page renders in WebView
```

---

## Project Structure

```
Luci4Invidious/
├── .gitignore
├── settings.gradle.kts              # Module declaration
├── build.gradle.kts                 # Root Gradle config (plugin versions)
├── gradle.properties                # Gradle and AndroidX flags
├── gradle/wrapper/
│   └── gradle-wrapper.properties    # Gradle distribution URL (9.4.1)
├── secrets.properties               # Gitignored, real credentials
├── secrets.properties.example       # Template, committed
├── README.md                        # You are here
│
└── app/
    ├── build.gradle.kts             # App module config (SDK, deps, secrets, BuildConfig)
    ├── proguard-rules.pro           # Empty (no minification)
    │
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml          # Intent filters + INTERNET permission
        │   ├── java/com/devilplan/luci4invidious/
        │   │   ├── UrlConverter.kt          # Pure URL conversion logic
        │   │   └── MainActivity.kt          # Compose + WebView + auth + fullscreen
        │   └── res/
        │       └── values/
        │           ├── strings.xml          # App name
        │           └── themes.xml           # Material NoActionBar
        │
        └── test/
            └── java/com/devilplan/luci4invidious/
                └── UrlConverterTest.kt      # 40 JVM unit tests
```

### File-by-File Breakdown

#### `AndroidManifest.xml`
Declares `INTERNET` permission, the `MainActivity` with two roles:
- **Launcher activity** (`MAIN` + `LAUNCHER`) > app appears in the app drawer
- **Link handler** (multiple `VIEW` intent filters) > catches YouTube URLs

`android:configChanges="orientation|screenSize"` prevents the WebView from reloading on rotation.

`android:usesCleartextTraffic="false"` blocks non-HTTPS traffic.

#### `UrlConverter.kt`
Pure Kotlin class with `invidiousHost` constructor parameter. No Android imports. Contains:
- `convert(youtubeUrl: String): String?` > converts YouTube URLs to Invidious URLs
- `isInvidiousHost(url: String): Boolean` > checks if a URL targets the Invidious instance
- `homepageUrl(): String` > returns the Invidious homepage URL
- `buildHomepageUrl(host: String): String` > companion method for building homepage URLs
- Internal host set for known YouTube domains
- Exception-safe: returns `null` on any parse failure

#### `MainActivity.kt`
Single `ComponentActivity` using Jetpack Compose. Uses:
- `AndroidView` with `FrameLayout` + `WebView` (no XML layout)
- `WebViewClient` with `onReceivedHttpAuthRequest` (auth) and `shouldOverrideUrlLoading` (YouTube redirect + external links to browser)
- `WebChromeClient` with `onShowCustomView`/`onHideCustomView` for fullscreen video
- `WindowInsetsControllerCompat` to hide/show system bars in fullscreen
- `systemBarsPadding()` for edge-to-edge layout
- `OnBackPressedCallback` for back navigation (fullscreen > history > exit)
- `WebViewDatabase` is not used; `onReceivedHttpAuthRequest` handles all auth
- Reads `intent.data` on launch to determine the initial URL
- Lifecycle: `onPause` pauses WebView, `onResume` resumes, `onDestroy` removes fullscreen view, detaches and destroys WebView

#### `UrlConverterTest.kt`
40 JUnit4 tests, no Android framework needed. Runs in `./gradlew test`.

#### `secrets.properties` / `secrets.properties.example`
Key-value properties file read by Gradle at build time. Three keys: `INVIDIOUS_HOST`, `INVIDIOUS_USER`, `INVIDIOUS_PASS`. The real file is gitignored; the example template is committed.

---

## Building from Source

### Prerequisites

- JDK 17
- Android SDK with `platform-37` and `build-tools` installed
- Gradle 9.4.1+ (or use the wrapper)
- AGP 9.2.1, Kotlin 2.2.10, Compose compiler plugin 2.2.10

### First-Time Setup

```bash
cd ~/Code/Android/Luci4Invidious

# Create secrets file
cp secrets.properties.example secrets.properties
# Edit secrets.properties with your real Invidious credentials

# Generate the Gradle wrapper (first time only)
gradle wrapper
```

### Build

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (needs signing config)
./gradlew assembleRelease

# Install to connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Android Studio

1. **File > Open** > select the `Luci4Invidious` directory
2. Wait for Gradle sync
3. Select your device/emulator
4. **Run > Run 'app'** or **Shift+F10**

---

## Testing

### Unit Tests (JVM)

The tests cover `UrlConverter` with no Android dependencies, they run on any machine with JDK 17.

```bash
./gradlew test
```

### Test Coverage

| Category | Tests | Description |
|---|---|---|
| Standard watch URLs | 4 | `www.youtube.com`, `youtube.com`, `m.youtube.com`, `music.youtube.com` |
| `youtu.be` short links | 3 | Plain ID, ID with timestamp, ID with multiple params |
| Shorts and embeds | 5 | `www.youtube.com/shorts/`, `m.youtube.com/shorts/`, `youtube.com/shorts/`, `/embed/` with and without www |
| Query param preservation | 3 | Timestamp, playlist, multiple params |
| Edge cases and invalid input | 10 | Non-YouTube URL, homepage, root path, channel URL, malformed, empty, null, javascript:, data: |
| HTTP scheme | 4 | `http://` variants for youtube.com and youtu.be |
| Case insensitivity | 2 | Mixed-case `WWW.YouTube.com` and `YOUTU.BE` |
| `isInvidiousHost` | 6 | Matching host, other host, malformed, case insensitive, empty, null |
| `homepageUrl` / `buildHomepageUrl` | 3 | Default host, custom host, subdomain |
| Custom host | 3 | Convert, isInvidiousHost, homepageUrl with custom host |
| `youtu.be` edge cases | 3 | Slash only, no path, long video ID |
| **Total** | **40** | |

### Test Report

After running `./gradlew test`, the HTML report is at:

```
app/build/reports/tests/testDebugUnitTest/index.html
```

### Adding Tests

All tests go in `app/src/test/java/com/devilplan/luci4invidious/`. No Robolectric or instrumented tests are needed since `UrlConverter` is pure Kotlin. If you add Android-dependent logic (e.g., testing `MainActivity`), use:

- **Instrumented tests** > `app/src/androidTest/java/...` with `androidx.test.ext:junit` and `espresso`
- **Robolectric** > `app/src/test/java/...` with `org.robolectric:robolectric`

---

## Architecture and Design Decisions

### Why secrets.properties instead of a settings screen?

The app is intentionally minimal. A settings screen adds complexity: SharedPreferences, a UI, input validation, lifecycle management. For a single-user app with a known Invidious instance, a properties file read at build time is simpler, more reliable, and easier to audit. Change the file, rebuild, done. The secrets are injected into `BuildConfig` and never stored in the git repository.

### Why Jetpack Compose instead of XML layouts?

Compose is the modern Android UI toolkit. For a WebView app, `AndroidView` hosting a `FrameLayout` + `WebView` is marginally more code than an XML layout, but it provides better lifecycle integration, easier state management, and native edge-to-edge support via `systemBarsPadding()`. No XML layout files are needed.

### Why `UrlConverter` as a separate class?

Separating the URL conversion logic from `MainActivity` achieves:
- **Testability** > pure Kotlin, no Android imports, testable on the JVM without a device or Robolectric
- **Reusability** > both `MainActivity` (for the initial URL) and `WebViewClient.shouldOverrideUrlLoading` (for in-page links) use the same logic
- **Single responsibility** > `MainActivity` handles Android lifecycle and WebView; `UrlConverter` handles URL parsing

### Why `ComponentActivity` instead of `AppCompatActivity`?

No app bar, no menus, no fragments, no Material toolbar. `ComponentActivity` is the lightest `Activity` subclass that supports `OnBackPressedDispatcher` and Compose's `setContent`. Less code, fewer dependencies.

### Why not use `WebViewClient.shouldOverrideUrlLoading` for the initial URL too?

`shouldOverrideUrlLoading` only fires for subsequent navigations (links clicked inside the page). The initial `loadUrl()` doesn't trigger it. So we handle the launch URL explicitly in `onCreate` via `intent.data`.

### Why `android:configChanges="orientation|screenSize"`?

Without this, Android destroys and recreates the activity on rotation, which reloads the WebView and loses the current page. With it, the WebView survives orientation changes seamlessly.

### Why `usesCleartextTraffic="false"`?

Hard block on HTTP. Invidious should be served over HTTPS anyway, and this prevents accidental plaintext requests. The intent filters still match `http://` YouTube links (some legacy links exist), but the conversion always produces `https://` Invidious URLs.

### Why no ProGuard minification?

The app is tiny. Minification saves ~50KB on an APK that's already under 1MB, and it can break WebView string reflection. Not worth the risk for no measurable benefit.

### Why disable Safe Browsing?

Google Safe Browsing sends every URL the WebView loads to Google's servers. For an app whose entire purpose is to avoid Google tracking, leaving it enabled would be counterproductive. Disabled via `webView.settings.safeBrowsingEnabled = false` (API 26+).

---

## Intent Filters Reference

The manifest declares 5 separate intent-filter blocks (all `VIEW` + `DEFAULT` + `BROWSABLE`):

| # | Scheme | Host(s) | Path Prefix | Purpose |
|---|---|---|---|---|
| 1 | `https` | `youtube.com`, `www.youtube.com`, `m.youtube.com`, `music.youtube.com` | `/watch` | Standard watch pages |
| 2 | `https` | `youtube.com`, `www.youtube.com`, `m.youtube.com` | `/shorts/` | YouTube Shorts |
| 3 | `https` | `youtube.com`, `www.youtube.com` | `/embed/` | Embedded players |
| 4 | `https` | `youtu.be` | *(any path)* | Short links |
| 5 | `http` | `youtube.com`, `www.youtube.com`, `m.youtube.com`, `youtu.be` | `/watch` or *(any)* | Legacy HTTP links |

**Note:** Filter 4 and 5 match any path on `youtu.be` because every `youtu.be` path is a video ID. For `youtube.com` in filter 5, only `/watch` is matched to avoid intercepting channel pages etc.

If you need to add more YouTube subdomains (e.g., `gaming.youtube.com`), add them to the `YOUTUBE_HOSTS` set in `UrlConverter.kt` and to the relevant `<intent-filter>` blocks in `AndroidManifest.xml`.

---

## URL Conversion Logic

```
Input:  https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s

Step 1: Parse with java.net.URL
        host = "www.youtube.com" (lowercased)
        path = "/watch"
        query = "v=dQw4w9WgXcQ&t=42s"

Step 2: Host match
        "www.youtube.com" in YOUTUBE_HOSTS > yes

Step 3: Build Invidious URL
        "https://" + "my.invidious.org" + "/watch" + "?v=dQw4w9WgXcQ&t=42s"

Output: https://my.invidious.org/watch?v=dQw4w9WgXcQ&t=42s
```

```
Input:  https://youtu.be/dQw4w9WgXcQ?t=120

Step 1: Parse with java.net.URL
        host = "youtu.be"
        path = "/dQw4w9WgXcQ"
        query = "t=120"

Step 2: Host match
        "youtu.be" > special case

Step 3: Extract video ID from path, build watch URL
        "https://" + "my.invidious.org" + "/watch?v=" + "dQw4w9WgXcQ" + "&t=120"

Output: https://my.invidious.org/watch?v=dQw4w9WgXcQ&t=120
```

---

## Security Considerations

### Credentials in secrets.properties

The Invidious basic auth credentials are stored in `secrets.properties`, which is gitignored. They are read at build time and injected into `BuildConfig` as compile-time constants. This means:
- They are baked into the APK and visible to anyone who can decompile it
- They are NOT in the git repository (gitignored)
- They are NOT in the source code files

**Mitigation:** This app is for personal use on a personal device. The credentials protect a privacy front-end, not sensitive data. If you're concerned:
- Don't publish the APK
- Use a dedicated low-privilege Invidious user account
- Consider token-based auth or a VPN instead of basic auth

### WebView JavaScript is enabled

Invidious requires JavaScript to function. This means the Invidious instance's JS runs inside the WebView. Since you control the Invidious instance, this is a calculated risk. If you're using a third-party instance, you're trusting their JS.

### Safe Browsing is disabled

Google Safe Browsing is disabled to prevent URL leakage to Google. The tradeoff is that malicious URLs are not flagged. Since all traffic goes to your own Invidious instance, this is an acceptable tradeoff.

### No SSL pinning

The WebView uses the system trust store. If your Invidious instance uses a custom CA or self-signed cert, you'll need to either use a proper certificate (Let's Encrypt) or override `onReceivedSslError` (not recommended for production).

### `usesCleartextTraffic="false"`

Hard-blocks all HTTP traffic. Only HTTPS is allowed. This is the safe default.

---

## Troubleshooting

### YouTube links still open in the YouTube app

Android remembers your default app choice. Clear it:
- **Settings > Apps > YouTube > Open by default > Clear defaults**
- **Settings > Apps > Luci4Invidious > Open by default > Supported links > Enable**

Next time you tap a YouTube link, you'll get the app chooser again. Select Luci4Invidious and tap **Always**.

### WebView shows a blank page

- Verify your Invidious instance is reachable: `curl -u user:pass https://my.invidious.org`
- Check that the credentials in `secrets.properties` are correct
- Check `adb logcat` for SSL errors or auth failures
- Ensure the instance has a valid TLS certificate

### App loads Invidious homepage instead of the video

This happens when `UrlConverter.convert()` returns `null`, meaning the URL didn't match any known YouTube pattern. Check `adb logcat` for the incoming intent data. If it's a new YouTube URL format, add it to the intent filters and `UrlConverter`.

### "App not installed" on APK install

- Ensure `adb install -r` is used (reinstall flag)
- Check that the APK architecture matches your device (debug APK is universal)
- Verify `minSdk` (24) is <= your device's Android version

### Back button exits immediately

This happens if the WebView has no history (you opened a link directly). The back callback checks fullscreen first, then `canGoBack()`; if there's nothing to go back to, it exits. This is correct behavior.

### Rotation reloads the page

Make sure `android:configChanges="orientation|screenSize"` is present in the manifest's `<activity>` tag. Without it, Android recreates the activity on rotation.

### Fullscreen video doesn't work

The app uses `WebChromeClient.onShowCustomView`/`onHideCustomView` to handle fullscreen. If the Invidious player's fullscreen button doesn't respond, make sure `WebChromeClient` is set on the WebView (it is in the current code). The fullscreen view is added to the `FrameLayout` root container, not the WebView itself.

### No audio on physical device (but works in emulator)

This is typically a device-specific issue, not a code issue. Check:
- Media volume is turned up (not just ring volume)
- No Do Not Disturb mode blocking media
- No other app holding audio focus
- Try killing background apps that might interfere with audio playback

---

## License

This is a personal project. Do whatever you want with it. No warranty, no support, no guarantees.

If you fork it, change the credentials in `secrets.properties` before building.

---

_Made with FOSS principles. No Google. No tracking. No nonsense._