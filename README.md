# WizkTerm

WizkTerm là bản fork của [Termux](https://github.com/termux/termux-app) — ứng dụng terminal và môi trường Linux cho Android.

Fork này dùng package riêng `com.wizkterm` nên **cài song song được** với Termux gốc.

## Khác biệt so với Termux

| | Termux | WizkTerm |
|---|---|---|
| Package / applicationId | `com.termux` | `com.wizkterm` |
| Prefix | `/data/data/com.termux/files/usr` | `/data/data/com.wizkterm/files/usr` |
| Kiến trúc | arm, aarch64, i686, x86_64 | **arm64-v8a** |
| Bootstrap | termux-packages | [Wizk-Modz/wizk-packages](https://github.com/Wizk-Modz/wizk-packages) |

Bootstrap (gói Linux tối thiểu kèm theo app) được build riêng với `$PREFIX` hardcode là `/data/data/com.wizkterm/files/usr`. Vì vậy **không thể dùng bootstrap của Termux** cho WizkTerm và ngược lại.

## Yêu cầu

- JDK 17
- Android SDK với compileSdk 35
- Android NDK 27.0.12077973

## Build

```bash
# Build debug APK (tự tải bootstrap aarch64)
./gradlew assembleDebug

# Build release APK (cần keystore, xem phần Ký release)
./gradlew assembleRelease

# Chạy unit test
./gradlew test
```

APK nằm ở `app/build/outputs/apk/<debug|release>/`.

### Ký release APK

`app/build.gradle` chỉ ký bằng keystore release khi có biến môi trường `WIZK_KEYSTORE_PATH`. Nếu không có, bản release dùng debug key.

```bash
export WIZK_KEYSTORE_PATH=/đường/dẫn/wizk.keystore
export WIZK_KEYSTORE_PASSWORD=<mật khẩu keystore>
export WIZK_KEY_ALIAS=<alias>
export WIZK_KEY_PASSWORD=<mật khẩu key>
./gradlew assembleRelease
```

Trên CI, keystore được lấy từ GitHub Secrets. **Không commit keystore hoặc password vào repo.**

### Cập nhật bootstrap

Khi bootstrap được build lại ở [wizk-packages](https://github.com/Wizk-Modz/wizk-packages), phải cập nhật **cả URL lẫn checksum SHA-256** trong task `downloadBootstraps` ở `app/build.gradle`. Build sẽ fail nếu checksum không khớp.

Tag release của bootstrap chứa dấu `+`, phải URL-encode thành `%2B`.

## Phát hành

Workflow `build_release_apk.yml` tự động build APK release đã ký và upload lên GitHub Release khi có release mới được publish. File đính kèm: `WizkTerm-<version>-arm64-v8a.apk` kèm `.sha256`.

```bash
git tag -a v1.0.0 -m "WizkTerm v1.0.0"
git push origin v1.0.0
gh release create v1.0.0 --title "WizkTerm v1.0.0" --notes "..."
```

`versionName` phải theo [semantic versioning 2.0.0](https://semver.org/spec/v2.0.0.html) — build sẽ fail nếu sai định dạng.

## Commit message

Dùng [Conventional Commits](https://www.conventionalcommits.org) với **type viết hoa chữ đầu**. Type hợp lệ: `Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, `Security`. Có thể ghép bằng `|`, thêm `!` trước `:` cho breaking change.

```
Fixed: Sửa lỗi XYZ
Added|Fixed: Thêm ABC và sửa XYZ
Changed!: Đổi cấu trúc package (breaking change)
```

## Cấu trúc

```
app                 Ứng dụng chính: UI, Activity/Service, glue code
terminal-emulator   Mô phỏng terminal (ANSI/VT parsing, PTY) + JNI
terminal-view       Android View hiển thị terminal
termux-shared       Thư viện dùng chung: constants, utils, shell, file
```

## Đổi package name

Xem hướng dẫn trong javadoc của `TermuxConstants` (`termux-shared/src/main/java/com/wizkterm/shared/termux/TermuxConstants.java`). Toàn bộ đường dẫn được suy ra từ `TERMUX_PACKAGE_NAME`, nhưng tên class Java và hàm JNI phải đổi đồng bộ. Sau đó phải build lại bootstrap với prefix mới.

## Giấy phép

Kế thừa từ Termux. Xem [`LICENSE.md`](LICENSE.md) và [`termux-shared/LICENSE.md`](termux-shared/LICENSE.md).
