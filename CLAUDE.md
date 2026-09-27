# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Dự án

**WizkTerm** là bản fork của [Termux](https://github.com/termux/termux-app) — terminal emulator + môi trường Linux cho Android. Khác biệt so với upstream:

- Package/applicationId: `com.termux` → **`com.wizkterm`**
- Prefix: `/data/data/com.termux/files/usr` → **`/data/data/com.wizkterm/files/usr`**
- Tên hiển thị: `Termux` → **`WizkTerm`**
- Chỉ build kiến trúc **arm64-v8a**
- Toolchain: AGP **8.13.0**, Gradle **9.0.0**, JDK **17**, compileSdk **35**, NDK **27.0.12077973**

**Lưu ý quan trọng về bootstrap:** các binary trong bootstrap zip được biên dịch với `$PREFIX` hardcode là `/data/data/com.termux/files/usr`. Bootstrap hiện tại **chưa được build lại** cho `com.wizkterm`, nên `pkg`/`apt` sẽ không hoạt động đúng cho tới khi bootstrap được build lại với prefix mới.

## Lệnh thường dùng

```bash
# Build debug APK (tự tải bootstrap aarch64 nếu chưa có)
./gradlew assembleDebug

# Build release
./gradlew assembleRelease

# Chạy toàn bộ unit test
./gradlew test

# Chạy test của một module
./gradlew :terminal-emulator:test
./gradlew :app:testDebugUnitTest

# Chạy MỘT test class
./gradlew :terminal-emulator:test --tests "com.wizkterm.terminal.TerminalTest"
./gradlew :app:testDebugUnitTest --tests "com.wizkterm.app.TermuxActivityTest"

# Chạy một test method
./gradlew :app:testDebugUnitTest --tests "com.wizkterm.app.TermuxActivityTest.<methodName>"

# In versionName hiện tại
./gradlew versionName

# Xoá build output + bootstrap zip đã tải
./gradlew clean
```

APK output: `app/build/outputs/apk/debug/wizkterm-app_<tag>_arm64-v8a.apk`

**Môi trường build:** cần JDK 17. Nếu build local không tải được Gradle distribution, dùng GitHub Actions (`.github/workflows/debug_build.yml`) — đây cũng là cách kiểm chứng build chính thức của repo.

### Biến môi trường ảnh hưởng build

| Biến | Mặc định | Tác dụng |
|---|---|---|
| `TERMUX_PACKAGE_VARIANT` | `apt-android-7` | Variant bootstrap (`apt-android-7` hoặc `apt-android-5`) |
| `TERMUX_APP_VERSION_NAME` | `""` | Ghi đè `versionName` |
| `TERMUX_APK_VERSION_TAG` | `""` | Tag trong tên file APK |
| `TERMUX_SPLIT_APKS_FOR_DEBUG_BUILDS` | `1` | Bật split APK theo ABI cho debug |
| `TERMUX_SPLIT_APKS_FOR_RELEASE_BUILDS` | `0` | Bật split APK theo ABI cho release |
| `JITPACK_NDK_VERSION` | từ `gradle.properties` | Ghi đè NDK version khi build trên Jitpack |

## Kiến trúc

### 4 module

```
app                 Ứng dụng chính: UI, Activity/Service, glue code
terminal-emulator   Logic mô phỏng terminal (ANSI/VT parsing, PTY) + JNI
terminal-view       Android View hiển thị terminal (render, gesture, text selection)
termux-shared       Thư viện dùng chung: constants, utils, shell, file, settings
```

Phụ thuộc: `app` → `terminal-view` + `termux-shared`; `termux-shared` → `terminal-view`; `terminal-view` → `terminal-emulator`.

### Luồng khởi động

`TermuxApplication` (khởi tạo `TermuxShellManager`, `TermuxShellEnvironment`, crash handler) → `TermuxActivity.onCreate` gọi `startService` + `bindService` → `TermuxService.onServiceConnected` → nếu chưa có session thì `TermuxInstaller.setupBootstrapIfNeeded` rồi tạo session qua `TermuxTerminalSessionActivityClient.addNewSession`.

`TermuxService` là **foreground service** — đây là cơ chế giữ process shell sống khi app ở background.

### Phân lớp terminal

```
TerminalSession (terminal-emulator)  ← quản lý vòng đời process shell, PTY qua JNI
    ↕ byte stream
TerminalEmulator (terminal-emulator) ← state machine parse ANSI escape codes
    ↕ TerminalBuffer/TerminalRow
TerminalRenderer (terminal-view)     ← vẽ buffer lên Canvas
    ↕
TerminalView (terminal-view)         ← Android View, nhận input IME/key
```

`TerminalSession` chạy 3 thread: `InputReader` (PTY → Emulator), `OutputWriter` (Emulator → PTY), `Waiter` (theo dõi process exit).

Hai interface callback cho phép tầng dưới báo ngược lên trên:
- `TerminalSessionClient` — session báo sự kiện (text changed, title changed)
- `TerminalViewClient` — view hỏi cấu hình / báo sự kiện UI

### Glue layer `app/terminal/`

Các class `Termux*` nối terminal core (thuần Java) với Android framework:

| Class | Vai trò |
|---|---|
| `TermuxTerminalSessionActivityClient` | Implement `TerminalSessionClient`; xử lý sự kiện session khi Activity hiển thị |
| `TermuxTerminalSessionServiceClient` | Implement `TerminalSessionClient`; xử lý sự kiện session khi Activity không tồn tại (chạy ngầm) |
| `TermuxTerminalViewClient` | Implement `TerminalViewClient`; xử lý tương tác người dùng |
| `TermuxActivityRootView` | Layout root, xử lý WindowInsets |
| `TermuxSessionsListViewController` | Drawer danh sách session |
| `TermuxTerminalExtraKeys` | Thanh phím tắt (Ctrl/Alt/Esc...) |

Việc tách `ActivityClient` và `ServiceClient` là có chủ đích: cùng một session có thể tồn tại khi Activity bị huỷ.

### `TermuxConstants` — hằng số trung tâm

`termux-shared/src/main/java/com/wizkterm/shared/termux/TermuxConstants.java` là file quan trọng nhất khi cần đổi định danh app. **Toàn bộ đường dẫn được suy ra từ `TERMUX_PACKAGE_NAME`**:

```java
TERMUX_PACKAGE_NAME = "com.wizkterm"
  → TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH = "/data/data/" + TERMUX_PACKAGE_NAME
  → TERMUX_FILES_DIR_PATH = ... + "/files"
  → TERMUX_PREFIX_DIR_PATH = ... + "/usr"      // /data/data/com.wizkterm/files/usr
  → TERMUX_HOME_DIR_PATH = ... + "/home"
```

Package của các plugin (`TERMUX_API_PACKAGE_NAME = TERMUX_PACKAGE_NAME + ".api"`, ...) cũng suy ra từ đây. Các hằng số sinh tên class (`TERMUX_ACTIVITY_NAME`, `BUILD_CONFIG_CLASS_NAME`, `TERMUX_SERVICE_NAME`) phải khớp **cả** `applicationId` **lẫn** cấu trúc Java package — đây là lý do việc đổi package phải thực hiện đồng bộ, không thể chỉ đổi `applicationId`.

**Quy ước:** không hardcode `"com.wizkterm"` trong code — luôn dùng hằng số từ `TermuxConstants`.

### Bootstrap

Bootstrap zip được nhúng vào `.so` (`libtermux-bootstrap`) qua `app/src/main/cpp/termux-bootstrap-zip.S` (dùng `.incbin`) + `termux-bootstrap.c` (JNI `getZip`). `TermuxInstaller` đọc zip qua JNI, giải nén vào `$PREFIX` staging rồi rename, sau đó chạy script second stage.

Task `downloadBootstraps` tải bootstrap từ `github.com/termux/termux-packages/releases`, verify SHA-256, lưu vào `app/src/main/cpp/bootstrap-aarch64.zip`. Task này được hook vào `preBuild` và các task `NdkBuild` vì file `.S` cần zip tồn tại trước khi native build chạy.

### RUN_COMMAND và termux-am

- `RunCommandService` cho phép app bên ngoài gửi lệnh shell qua Intent, bảo vệ bằng permission `com.wizkterm.permission.RUN_COMMAND`.
- `TermuxAmSocketServer` là Unix socket server cho phép tiến trình Linux bên trong bootstrap gọi ngược ra Android (thông báo, mở URL...).

## Native code (ndk-build)

| Module | Android.mk | Library | Sources |
|---|---|---|---|
| `app` | `src/main/cpp/Android.mk` | `libtermux-bootstrap` | `termux-bootstrap-zip.S`, `termux-bootstrap.c` |
| `terminal-emulator` | `src/main/jni/Android.mk` | `libtermux` | `termux.c` |
| `termux-shared` | `src/main/cpp/Android.mk` | `local-socket` | `local-socket.cpp` |

Tên hàm JNI phải khớp package Java (`Java_com_wizkterm_...`). `FindClass` dùng dạng đường dẫn `com/wizkterm/...`. Khi đổi Java package phải đổi đồng thời cả hai.

## Cấu hình build đặc biệt

**`gradle.properties` phải giữ 2 flag này = `false`:**

- `android.nonTransitiveRClass=false` — code dùng resource transitive: `app` tham chiếu `R.raw.bell` và `R.string.action_yes/no` được khai báo trong `termux-shared`. Bật `true` sẽ lỗi compile.
- `android.nonFinalResIds=false` — `ExtraKeysView` khai báo `public static final int ATTR_* = R.attr.*`; resource ID không final sẽ lỗi compile.

**`splits.abi`** chỉ include `arm64-v8a` với `universalApk false`. `downloadBootstraps` cũng chỉ tải aarch64.

**`validateVersionName`** trong `app/build.gradle` bắt buộc `versionName` theo semantic versioning 2.0.0 (`major.minor.patch(-prerelease)(+buildmetadata)`). Build sẽ fail nếu sai — kể cả khi tạo tag release.

**Publishing:** 3 module library publish lên Jitpack với groupId `com.wizkterm`, artifactId `terminal-view` / `termux-shared` / `terminal-emulator`, version `0.118.0`. Cần `publishing { singleVariant('release') }` trong block `android {}` — AGP 8 không tự tạo software component.

## Test

| Module | Thư mục | Framework |
|---|---|---|
| `app` | `app/src/test` | JUnit + Robolectric 4.10 |
| `terminal-emulator` | `terminal-emulator/src/test` | JUnit thuần (19 file, test ANSI/VT parsing) |
| `termux-shared` | `termux-shared/src/androidTest` | Instrumentation (AndroidJUnit) |

## CI

Workflows trigger trên nhánh `main`, dùng JDK 17 (temurin):

- `debug_build.yml` — build debug APK arm64-v8a, upload artifact + sha256sums
- `run_tests.yml` — `./gradlew test`
- `gradle-wrapper-validation.yml` — validate Gradle wrapper
- `attach_debug_apks_to_release.yml` — trigger khi publish release, build và đính APK vào release
- `trigger_library_builds_on_jitpack.yml` — trigger Jitpack build cho 3 library

Không có bước lint trong CI. `lint { disable 'ProtectedPermissions' }` trong `app/build.gradle`.

## Commit message

Theo [Conventional Commits](https://www.conventionalcommits.org) với **type viết hoa chữ đầu** (theo spec của repo). Type hợp lệ: `Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, `Security`. Có thể ghép bằng `|`, thêm `!` trước `:` cho breaking change.

```
Fixed: Sửa lỗi XYZ
Added|Fixed: Thêm ABC và sửa XYZ
Changed!: Đổi cấu trúc package (breaking change)
Fixed(terminal): Sửa lỗi trong module terminal
```

## Quy ước code

- Class riêng của Termux app/plugin đặt trong package `com.wizkterm.shared.termux`; class dùng chung đặt ngoài package đó.
- Không hardcode đường dẫn hay package name — định nghĩa trong `termux-shared` nếu chưa có rồi tham chiếu.
- Khi sửa code dùng chung, cập nhật `termux-shared/LICENSE.md` nếu cần và ghi changelog trong `TermuxConstants.java`.
