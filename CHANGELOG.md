# Changelog

All notable changes to this project will be documented in this file.

The format is simplified version of [Keep a Changelog](https://keepachangelog.com/en/1.1.0/):
- `Additions` - New features
- `Changes` - Behaviour/visual changes
- `Fixes` - Bugfixes
- `Translation` - Translation changes/updates
- `Other` - Technical stuff

## [Unreleased]

### Additions
- Sync DoH provider list with upstream (added Mullvad, Control D, Njalla, and Shecan)
- Added option to enable verbose logging

### Changes
- Enable 'Split Tall Images' by default (@Smol-Ame)
- Minor visual adjustments
- Tell user to restart the app when User-Agent is changed (@NGB-Was-Taken)
- Re-enable fetching licensed manga (@Animeboynz)

### Fixes
- Fixed only few DoH provider is actually being used (Cloudflare, Google, AdGuard, and Quad9)
- Fixed "Group by Ungrouped" showing duplicate entries
- Fixed reader sometimes won't load images
- Handle some uncaught crashes
- Fixed crashes due to GestureDetector's firstEvent is sometimes null on some devices

### Other
- Simplify network helper code
- Even more SQLDelight migration effort
- Update dependency com.android.tools:desugar_jdk_libs to v2.1.2
- Update moko to v0.24.2
- Refactor trackers to use DTOs (@MajorTanya)
- Replace Injekt with Koin
- Remove unnecessary permission added by Firebase
- Remove unnecessary features added by Firebase
- Replace BOM dev.chrisbanes.compose:compose-bom with JetPack's BOM
- Update compose BOM to 2024.09.02
- Update dependency com.google.firebase:firebase-bom to v33.3.0
- Update dependency com.squareup.okio:okio to v3.9.1
- Update activity to v1.9.2
- Update lifecycle to v2.8.6
- Update dependency me.zhanghai.android.libarchive:library to v1.1.2
- Update agp to v8.6.1
- Update junit5 monorepo to v5.11.1
- Update dependency androidx.test.ext:junit to v1.2.1
- Update dependency org.jetbrains.kotlinx:kotlinx-collections-immutable to v0.3.8
- Update dependency org.jsoup:jsoup to v1.18.1
- Update dependency org.jetbrains.kotlinx:kotlinx-coroutines-bom to v1.9.0
- Update serialization to v1.7.3
- Update dependency gradle to v8.10.2

## [v1.8.5.7]

### Fixes
- Fixed more NPE crashes

## [v1.8.5.6]

### Fixes
- Fixed NPE crash on tablets

## [v1.8.5.5]

### Fixes
- Fixed crashes caused by certain extension implementation
- Fixed "Theme buttons based on cover" doesn't work properly
- Fixed library cover images looks blurry then become sharp after going to
  entry's detail screen

### Other
- More StorIO to SQLDelight migration effort
- Update dependency dev.chrisbanes.compose:compose-bom to v2024.08.00-alpha02
- Update kotlin monorepo to v2.0.20
- Update aboutlibraries to v11.2.3
- Remove dependency com.github.leandroBorgesFerreira:LoadingButtonAndroid

## [v1.8.5.4]

### Fixes
- Fixed custom cover set from reader didn't show up on manga details

## [v1.8.5.3]

### Additions
- Add toggle to enable/disable chapter swipe action(s)
- Add toggle to enable/disable webtoon double tap to zoom

### Changes
- Custom cover now shown globally

### Fixes
- Fixed chapter number parsing (@Naputt1)
- Reduced library flickering (still happened in some cases when the cached image size is too different from the original image size, but should be reduced quite a bit)
- Fixed entry details header didn't update when being removed from library

### Other
- Refactor chapter recognition (@stevenyomi)
- (Re)added unit test for chapter recognition
- More StorIO to SQLDelight migration effort
- Target Android 15
- Adjust manga cover cache key
- Refactor manga cover fetcher (@ivaniskandar, @AntsyLich, @null2264)

## [v1.8.5.2]

### Fixes
- Fixed some preference not being saved properly

### Other
- Update dependency co.touchlab:kermit to v2.0.4
- Update lifecycle to v2.8.4

## [v1.8.5.1]

### Fixes
- Fixed library showing duplicate entry when using dynamic category

## [v1.8.5]

### Additions
- Add missing "Max automatic backups" option on experimental Data and Storage setting menu
- Add information on when was the last time backup automatically created to experimental Data and Storage setting menu
- Add monochrome icon

### Changes
- Add more info to WorkerInfo page
  - Added "next scheduled run"
  - Added attempt count
- `english` tag no longer cause reading mode to switch to LTR (@mangkoran)
- `chinese` tag no longer cause reading mode to switch to LTR
- `manhua` tag no longer cause reading mode to switch to LTR
- Local source manga's cover now being invalidated on refresh
- It is now possible to create a backup without any entries using experimental Data and Storage setting menu
- Increased default maximum automatic backup files to 5
- It is now possible to edit a local source entry without adding it to library
- Long Strip and Continuous Vertical background color now respect user setting
- Display Color Profile setting no longer limited to Android 8 or newer
- Increased long strip cache size to 4 for Android 8 or newer (@FooIbar)
- Use Coil pipeline to handle HEIF images

### Fixes
- Fixed auto backup, auto extension update, and app update checker stop working
  if it crash/failed
- Fixed crashes when trying to reload extension repo due to connection issue
- Fixed tap controls not working properly after zoom (@arkon, @Paloys, @FooIbar)
- Fixed (sorta, more like workaround) ANR issues when running background tasks, such as updating extensions (@ivaniskandar)
- Fixed split (downloaded) tall images sometimes doesn't work
- Fixed status bar stuck in dark mode when app is following system theme
- Fixed splash screen state only getting updates if library is empty (Should slightly reduce splash screen duration)
- Fixed kitsu tracker issue due to domain change
- Fixed entry custom cover won't load if entry doesn't have cover from source
- Fixed unread badge doesn't work properly for some sources (notably Komga)
- Fixed MAL start date parsing (@MajorTanya)

### Translation
- Update Japanese translation (@akir45)
- Update Brazilian Portuguese translation (@AshbornXS)
- Update Filipino translation (@infyProductions)

### Other
- Re-added several social media links to Mihon
- Some code refactors
  - Simplify some messy code
  - Rewrite version checker
  - Rewrite Migrator (@ghostbear)
  - Split the project into several modules
  - Migrated i18n to use Moko Resources
  - Removed unnecessary dependencies (@null2264, @nonproto)
- Update firebase bom to v33.1.0
- Replace com.google.android.gms:play-services-oss-licenses with com.mikepenz:aboutlibraries
- Update dependency com.google.gms:google-services to v4.4.2
- Add crashlytics integration for Kermit
- Replace ProgressBar with ProgressIndicator from Material3 to improve UI consistency
- More StorIO to SQLDelight migrations
  - Merge lastFetch and lastRead query into library_view VIEW
  - Migrated a few more chapter related queries
  - Migrated most of the manga related queries
- Bump dependency com.github.tachiyomiorg:unifile revision to a9de196cc7
- Update project to Kotlin 2.0 (v2.0.10)
- Update compose bom to v2024.08.00-alpha01
- Refactor archive support to use `libarchive` (@FooIbar)
- Use version catalog for gradle plugins
- Update dependency org.jsoup:jsoup to v1.7.1
- Bump dependency com.github.tachiyomiorg:image-decoder revision to 41c059e540
- Update dependency io.coil-kt.coil3 to v3.0.0-alpha10
- Update Android Gradle Plugin to v8.5.2
- Update gradle to v8.9
- Start using Voyager for navigation
- Update dependency androidx.work:work-runtime-ktx to v2.9.1
- Update dependency androidx.annotation:annotation to v1.8.2

## [1.8.4.6]

### Fixes
- Fixed scanlator filter not working properly if it contains " & "

### Other
- Removed dependency com.dmitrymalkovich.android:material-design-dimens
- Replace dependency br.com.simplepass:loading-button-android with
  com.github.leandroBorgesFerreira:LoadingButtonAndroid
- Replace dependency com.github.florent37:viewtooltip with
  com.github.CarlosEsco:ViewTooltip

## [1.8.4.5]

### Fixes
- Fixed incorrect library entry chapter count

## [1.8.4.4]

### Fixes
- Fixed incompatibility issue with J2K backup file

## [1.8.4.3]

### Fixes
- Fixed "Open source repo" icon's colour

## [1.8.4.2]

### Changes
- Changed "Open source repo" icon to prevent confusion

## [1.8.4.1]

### Fixes
- Fixed saving combined pages not doing anything

## [1.8.4]

### Additions
- Added option to change long tap browse and recents nav behaviour
  - Added browse long tap behaviour to open global search (@AshbornXS)
  - Added recents long tap behaviour to open last read chapter (@AshbornXS)
- Added option to backup sensitive settings (such as tracker login tokens)
- Added beta version of "Data and storage" settings (can be accessed by long tapping "Data and storage")

### Changes
- Remove download location redirection from `Settings > Downloads`
- Moved cache related stuff from `Settings > Advanced` to `Settings > Data and storage`
- Improve webview (@AshbornXS)
  - Show url as subtitle
  - Add option to clear cookies
  - Allow zoom
- Handle urls on global search (@AshbornXS)
- Improve download queue (@AshbornXS)
  - Download badge now show download queue count
  - Add option to move series to bottom
- Only show "open repo url" button when repo url is not empty

### Fixes
- Fix potential crashes for some custom Android rom
- Allow MultipartBody.Builder for extensions
- Refresh extension repo now actually refresh extension(s) trust status
- Custom manga info now relink properly upon migration
- Fixed extension repo list did not update when a repo is added via deep link
- Fixed download unread trying to download filtered (by scanlator) chapters
- Fixed extensions not retaining their repo url
- Fixed more NullPointerException crashes
- Fixed split layout caused non-split images to not load

### Other
- Migrate some StorIO queries to SQLDelight, should improve stability
- Migrate from Timber to Kermit
- Update okhttp monorepo to v5.0.0-alpha.14
- Refactor backup code
  - Migrate backup flags to not use bitwise
  - Split it to several smaller classes
- Update androidx.compose.material3:material3 to v1.3.0-beta02

## [1.8.3.4]

### Fixes
- Fixed crashes caused by invalid ComicInfo XML

  If this caused your custom manga info to stop working, try resetting it by deleting `ComicInfoEdits.xml` file located in `Android/data/eu.kanade.tachiyomi.yokai`

- Fixed crashes caused by the app trying to round NaN value

## [1.8.3.3]

### Changes
- Crash report can now actually be disabled

### Other
- Loading GlobalExceptionHandler before Crashlytics

## [1.8.3.2]

### Other
- Some more NullPointerException prevention that I missed

## [1.8.3.1]

### Other
- A bunch of NullPointerException prevention

## [1.8.3]

### Additions
- Extensions now can be trusted by repo

### Changes
- Extensions now required to have `repo.json`

### Other
- Migrate to SQLDelight
- Custom manga info is now stored in the database

## [1.8.2]

### Additions
- Downloaded chapters now include ComicInfo file
- (LocalSource) entry chapters' info can be edited using ComicInfo

### Fixes
- Fixed smart background colour by page failing causing the image to not load
- Fixed downloaded chapter can't be opened if it's too large
- Downloaded page won't auto append chapter ID even tho the option is enabled

### Other
- Re-route nightly to use its own repo, should fix "What's new" page

## [1.8.1.2]

### Additions
- Added a couple new tags to set entry as SFW (`sfw` and `non-erotic`)

### Fixes
- Fixed smart background colour by page failing causing the image to not load

### Other
- Re-route nightly to use its own repo, should fix "What's new" page

## [1.8.1.1]

### Fixes
- Fixed crashes when user try to edit an entry

## [1.8.1]

### Additions
- (Experimental) Option to append chapter ID to download filename to avoid conflict

### Changes
- Changed notification icon to use Yōkai's logo instead
- Yōkai is now ComicInfo compliant. [Click here to learn more](https://anansi-project.github.io/docs/comicinfo/intro)
- Removed "Couldn't split downloaded image" notification to reduce confusion. It has nothing to do with unsuccessful split, it just think it shouldn't split the image

### Fixes
- Fixed not being able to open different chapter when a chapter is already opened
- Fixed not being able to read chapters from local source
- Fixed local source can't detect archives

### Other
- Wrap SplashState to singleton factory, might fix issue where splash screen shown multiple times
- Use Okio instead of `java.io`, should improve reader stability (especially long strip)

## [1.8.0.2]

### Fixes
- Fixed app crashes when backup directory is null
- Fixed app asking for All Files access permission when it's no longer needed

## [1.8.0.1]

### Additions
- Added CrashScreen

### Fixes
- Fixed version checker for nightly against hotfix patch version
- Fixed download cache causes the app to crash

## [1.8.0]

### Additions
- Added cutout support for some pre-Android P devices
- Added option to add custom colour profile
- Added onboarding screen

### Changes
- Permanently enable 32-bit colour mode
- Unified Storage™ ([Click here](https://mihon.app/docs/faq/storage#migrating-from-tachiyomi-v0-14-x-or-earlier) to learn more about it)

### Fixes
- Fixed cutout behaviour for Android P
- Fixed some extensions doesn't detect "added to library" entries properly ([GH-40](https://github.com/null2264/yokai/issues/40))
- Fixed nightly and debug variant doesn't include their respective prefix on their app name
- Fixed nightly version checker

### Other
- Update dependency com.github.tachiyomiorg:image-decoder to e08e9be535
- Update dependency com.github.null2264:subsampling-scale-image-view to 338caedb5f
- Added Unit Test for version checker
- Use Coil pipeline instead of SSIV for image decode whenever possible, might improve webtoon performance
- Migrated from Coil2 to Coil3
- Update compose compiler to v1.5.14
- Update dependency androidx.compose.animation:animation to v1.6.7
- Update dependency androidx.compose.foundation:foundation to v1.6.7
- Update dependency androidx.compose.material:material to v1.6.7
- Update dependency androidx.compose.ui:ui to v1.6.7
- Update dependency androidx.compose.ui:ui-tooling to v1.6.7
- Update dependency androidx.compose.ui:ui-tooling-preview to v1.6.7
- Update dependency androidx.compose.material:material-icons-extended to v1.6.7
- Update dependency androidx.lifecycle:lifecycle-viewmodel-compose to v2.8.0
- Update dependency androidx.activity:activity-ktx to v1.9.0
- Update dependency androidx.activity:activity-compose to v1.9.0
- Update dependency androidx.annotation:annotation to v1.8.0
- Update dependency androidx.browser:browser to v1.8.0
- Update dependency androidx.core:core-ktx to v1.13.1
- Update dependency androidx.lifecycle:lifecycle-viewmodel-ktx to v2.8.0
- Update dependency androidx.lifecycle:lifecycle-livedata-ktx to v2.8.0
- Update dependency androidx.lifecycle:lifecycle-common to v2.8.0
- Update dependency androidx.lifecycle:lifecycle-process to v2.8.0
- Update dependency androidx.lifecycle:lifecycle-runtime-ktx to v2.8.0
- Update dependency androidx.recyclerview:recyclerview to v1.3.2
- Update dependency androidx.sqlite:sqlite to v2.4.0
- Update dependency androidx.webkit:webkit to v1.11.0
- Update dependency androidx.work:work-runtime-ktx to v2.9.0
- Update dependency androidx.window:window to v1.2.0
- Update dependency com.google.firebase:firebase-crashlytics-gradle to v3.0.1
- Update dependency com.google.gms:google-services to v4.4.1
- Update dependency com.google.android.material:material to v1.12.0
- Update dependency com.squareup.okio:okio to v3.8.0
- Update dependency com.google.firebase:firebase-bom to v33.0.0
- Update dependency org.jetbrains.kotlin:kotlin-gradle-plugin to v1.9.24
- Update dependency org.jetbrains.kotlin:kotlin-serialization to v1.9.24
- Update dependency org.jetbrains.kotlinx:kotlinx-serialization-json to v1.6.2
- Update dependency org.jetbrains.kotlinx:kotlinx-serialization-json-okio to v1.6.2
- Update dependency org.jetbrains.kotlinx:kotlinx-serialization-protobuf to v1.6.2
- Update dependency org.jetbrains.kotlinx:kotlinx-coroutines-android to v1.8.0
- Update dependency org.jetbrains.kotlinx:kotlinx-coroutines-core to v1.8.0
- Resolved some compile warnings
- Update dependency com.github.tachiyomiorg:unifile to 7c257e1c64

## [1.7.14]

### Changes
- Added splash to reader (in case it being opened from shortcut)
- Increased long strip split height
- Use normalized app name by default as folder name

### Fixes
- Fixed cutout support being broken

### Other
- Move AppState from DI to Application class to reduce race condition

## [1.7.13]

### Additions
- Ported Tachi's cutout option
- Added Doki theme (dark only)

### Changes
- Repositioned cutout options in settings
- Splash icon now uses coloured variant of the icon
- Removed deep link for sources, this should be handled by extensions
- Removed braces from nightly (and debug) app name

### Fixes
- Fixed preference summary not updating after being changed once
- Fixed legacy appbar is visible on compose when being launched from deeplink
- Fixed some app icon not generated properly
- Fixed splash icon doesn't fit properly on Android 12+

### Other
- Migrate to using Android 12's SplashScreen API
- Clean up unused variables from ExtensionInstaller

## [1.7.12]

### Additions
- Scanlator filter is now being backed up (@jobobby04)

### Fixes
- Fixed error handling for MAL tracking (@AntsyLich)
- Fixed extension installer preference incompatibility with modern Tachi

### Other
- Split PreferencesHelper even more
- Simplify extension install issue fix (@AwkwardPeak7)
- Update dependency com.github.tachiyomiorg:image-decoder to fbd6601290
- Replace dependency com.github.jays2kings:subsampling-scale-image-view with com.github.null2264:subsampling-scale-image-view
- Update dependency com.github.null2264:subsampling-scale-image-view to e3cffd59c5

## [1.7.11]

### Fixes
- Fixed MAL tracker issue (@AntsyLich)
- Fixed trusting extension caused it to appear twice

### Other
- Change Shikimori client from Tachi's to Yōkai's
- Move TrackPreferences to PreferenceModule

## [1.7.10]

### Addition
- Content type filter to hide SFW/NSFW entries
- Confirmation before revoking all trusted extension

### Changes
- Revert Webcomic -> Webtoon

### Fixes
- Fix app bar disappearing on (scrolled) migration page
- Fix installed extensions stuck in "installable" state
- Fix untrusted extensions not having an icon

### Other
- Changed (most) trackers' client id and secret
- Add or changed user-agent for trackers

## [1.7.9]

### Other
- Sync project with J2K [v1.7.4](https://github.com/Jays2Kings/tachiyomiJ2K/releases/tag/v1.7.4)

## [1.7.8]

### Changes
- Local source now try to find entries not only in `Yōkai/` but also in `Yokai/` and `TachiyomiJ2K/` for easier migration

### Other
- Changed AniList and MAL clientId, you may need to logout and re-login

## [1.7.7]

### Changes
- Hopper icon now changes depending on currently active group type (J2K)

### Fixes
- Fixed bookmarked entries not being detected as bookmarked on certain extensions

## [1.7.6]

### Additions
- Shortcut to Extension Repos from Browser -> Extensions page
- Added confirmation before extension repo deletion

### Changes
- Adjusted dialogs background colour to be more consistent with app theme

### Fixes
- Fixed visual glitch where page sometime empty on launch
- Fixed extension interceptors receiving compressed responses (T)

### Other
- Newly added strings from v1.7.5 is now translatable

## [1.7.5]

### Additions
- Ported custom extension repo from upstream

### Changes
- Removed built-in extension repo
- Removed links related to Tachiyomi
- Ported upstream's trust extension logic
- Rebrand to Yōkai

### Other
- Start migrating to Compose

## [1.7.4]

### Changes
- Rename project to Yōkai (Z)
- Replace Tachiyomi's purged extensions with Keiyoushi extensions (Temporary solution until I ported custom extension repo feature) (Z)
- Unread count now respect scanlator filter (J2K)

### Fixes
- Fixed visual glitch on certain page (J2K)
