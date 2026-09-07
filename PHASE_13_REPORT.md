# Phase 13 — Release Candidate Audit & Ship Gate

## 1. Executive Summary

- **Status:** **SHIP**
- **Production code changes:** **0** (Tuyệt đối không sửa đổi mã nguồn sản xuất)
- **Kiểm toán viên:** Antigravity Autonomous Implementation Agent
- **Thiết bị kiểm chuẩn thực tế:** vivo iQOO Neo 10 (Model: `V2425A` / Serial: `10CF3J1F3400238`) — Android 15 / API 35 / OriginOS 5
- **Kết quả Unit & Regression Tests:** **215/215 PASS** (100% thành công, 0 failures, 0 errors, 0 skipped)
- **Kết quả Real-Device Release Validation:** **20/20 PASS** (100% thành công trên thiết bị thật)

Dự án `self-discipline-poc-01` (package: `com.example.selfdisciplinepoc01`) đã hoàn thành toàn diện quy trình kiểm toán 9 bước nghiêm ngặt theo phương châm **AUDIT-FIRST**. Bản build Release Candidate đáp ứng toàn bộ các bất biến kiến trúc đã đóng băng (Frozen Core), không có bất kỳ khiếm khuyết nào thuộc mức P0 hay P1, và sẵn sàng vượt qua cổng phát hành (**Ship Gate**).

---

## 2. Repository Build Configuration

Cấu hình toolchain thực tế được trích xuất trực tiếp từ các tệp cấu hình của kho lưu trữ (`gradle-wrapper.properties`, `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`):

| Item | Actual Value | Evidence |
|------|--------------|----------|
| **applicationId** | `com.example.selfdisciplinepoc01` | `app/build.gradle.kts:12` |
| **versionCode** | `1` | `app/build.gradle.kts:15` |
| **versionName** | `1.0` | `app/build.gradle.kts:16` |
| **minSdk** | `26` (Android 8.0 Oreo) | `app/build.gradle.kts:13` |
| **targetSdk** | `35` (Android 15) | `app/build.gradle.kts:14` |
| **compileSdk** | `35` (Android 15) | `app/build.gradle.kts:9` |
| **Gradle Wrapper** | `9.1.0` (bin distribution) | `gradle/wrapper/gradle-wrapper.properties:3` (SHA-256 verified) |
| **Android Gradle Plugin (AGP)** | `8.7.3` | `gradle/libs.versions.toml:2` |
| **Kotlin Version** | `2.0.21` (Compose Compiler plugin 2.0.21) | `gradle/libs.versions.toml:13,35` |
| **JDK Requirement** | Java 17 compatibility (`jvmTarget = "17"`) | `app/build.gradle.kts:26-30`, Foojay convention resolver 1.0.0 |
| **Build Variants** | `debug`, `release` | `app/build.gradle.kts:19-24` |
| **Compose BOM** | `2024.10.01` | `gradle/libs.versions.toml:6` |
| **DataStore Preferences** | `1.1.1` | `gradle/libs.versions.toml:14` |

---

## 3. Build Verification

Toàn bộ các tác vụ Gradle wrapper được thực thi từ trạng thái hoàn toàn sạch sẽ (clean state), sử dụng wrapper chính thức của repo:

| Command | Status | Thời gian thực thi | Ghi chú & Kết quả |
|---------|--------|-------------------|-------------------|
| `.\gradlew clean` | **SUCCESS** | 2s | Xóa sạch toàn bộ build cache và outputs trung gian |
| `.\gradlew test` | **SUCCESS** | 8s | Biên dịch và thực thi toàn bộ 215 unit tests (cả Debug & Release unit test tasks) |
| `.\gradlew assembleDebug` | **SUCCESS** | 2s | Tạo thành công `app-debug.apk` |
| `.\gradlew assembleRelease` | **SUCCESS** | 51s | Biên dịch production dex, tối ưu tài nguyên, xuất `app-release-unsigned.apk` |

---

## 4. Release Artifact

Thông tin định danh của artifact Release Candidate được tạo ra:

- **Filename:** `app-release-unsigned.apk`
- **Loại tệp:** APK (Android Package)
- **Đường dẫn tuyệt đối:** `C:\Code\self-discipline-poc-01\app\build\outputs\apk\release\app-release-unsigned.apk`
- **Kích thước tệp (File Size):** `7,091,208 bytes` (~6.76 MB)
- **Mã băm SHA-256:** `78055A80D63C05E420F94347AA7FF9795E7DEC7BA027FA1B565C0744D3B422DD`
- **Application ID:** `com.example.selfdisciplinepoc01`
- **Version Code:** `1`
- **Version Name:** `1.0`
- **Build Variant:** `release`
- **Thời điểm đóng gói:** 2026-09-04 13:02:51 (UTC+07:00)
- **Git Commit Hash:** `54304c3`

> [!NOTE]
> Để phục vụ kiểm toán hành vi trên thiết bị thật vivo iQOO Neo 10, một bản sao của artifact đã được ký kiểm chuẩn (test-signed qua Android SDK `apksigner` với scheme v2/v3) tại đường dẫn: `app\build\outputs\apk\release\app-release-signed.apk` mà không can thiệp vào mã nguồn hay cấu hình git.

---

## 5. Manifest / Security / Privacy Audit

Kiểm toán toàn diện tệp Merged Manifest (`app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`) và cấu hình dịch vụ trợ năng:

| Thành phần kiểm tra | Đánh giá | Bằng chứng kỹ thuật |
|---------------------|----------|---------------------|
| **AccessibilityService Declaration** | **PASS** | `AppDetectorAccessibilityService` được bảo vệ bằng quyền hệ thống `android.permission.BIND_ACCESSIBILITY_SERVICE`. Chỉ OS mới có quyền liên kết (bind). |
| **Window Content Extraction Privacy** | **PASS** | `android:canRetrieveWindowContent="false"` trong `accessibility_service_config.xml`. Dịch vụ **KHÔNG** đọc nội dung màn hình, tin nhắn, mật khẩu, hay view hierarchy của ứng dụng khác. |
| **SYSTEM_ALERT_WINDOW Permission** | **PASS** | Hoàn toàn **KHÔNG** khai báo quyền `SYSTEM_ALERT_WINDOW`. `BlockingShieldOverlay` sử dụng trực tiếp `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY` trong ngữ cảnh AccessibilityService. |
| **LockScreenActivity Export** | **PASS** | `android:exported="false"`, `launchMode="singleTop"`. Ngăn chặn hoàn toàn việc các ứng dụng bên ngoài can thiệp hoặc khởi chạy trái phép màn hình khóa. |
| **MainActivity Export** | **PASS** | `android:exported="true"` với intent-filter chuẩn `ACTION_MAIN` / `CATEGORY_LAUNCHER`. |
| **Quyền mạng & Lưu trữ ngoài** | **PASS** | Hoàn toàn **KHÔNG** yêu cầu `INTERNET`, `ACCESS_NETWORK_STATE`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`. Ứng dụng hoạt động 100% offline. |
| **Sensitive Data Logging** | **PASS** | `DiagnosticLogger` / `DiagnosticEvent` chỉ ghi metadata hệ thống (package, sessionId, elapsedMs, reasons). Không ghi log mật khẩu, thông tin cá nhân, hay text người dùng. |
| **Cleartext Traffic** | **PASS** | Không sử dụng mạng, không có cấu hình network security config nguy hiểm. |

---

## 6. Persistence / Upgrade Audit

Kiểm toán tính bền vững của dữ liệu lưu trữ qua Jetpack DataStore Preferences (`target_preferences.preferences_pb`):

| Kịch bản kiểm toán | Kết quả | Chi tiết đánh giá |
|-------------------|---------|-------------------|
| 1. Cài đặt mới (Fresh install) | **PASS** | Khởi tạo an toàn với target mặc định `com.android.chrome` (enabled) |
| 2. Khởi chạy đầu tiên (First launch) | **PASS** | UI MainActivity nạp danh sách target trơn tru |
| 3. Onboarding quyền Accessibility | **PASS** | Hiển thị hướng dẫn và phát hiện trạng thái kích hoạt dịch vụ chính xác |
| 4. Thêm target động (Add target) | **PASS** | `TargetRepository.add()` cập nhật tức thì in-memory cache và ghi xuống DataStore |
| 5. Bật/Tắt target (Enable/disable) | **PASS** | `TargetRepository.setEnabled()` phản ánh ngay lập tức vào `activeLockedPackages` |
| 6. Cấu hình lịch biểu (Schedule config) | **PASS** | Lưu chính xác `startHour`, `startMinute`, `endHour`, `endMinute` trong `locked_apps_json` |
| 7. Cấu hình giới hạn ngày (Daily limit) | **PASS** | Lưu chính xác `dailyLimitMinutes` / `dailyLimitSeconds` |
| 8. Tạo dữ liệu thời gian dùng (Usage data) | **PASS** | `UsageTracker` tính toán thời gian theo `elapsedRealtime` và ghi vào `usage_aggregates_json` |
| 9. Khởi động lại tiến trình (Process restart) | **PASS** | Dữ liệu target và thời gian dùng nạp lại chuẩn xác từ DataStore |
| 10. Mở lại ứng dụng (Reopen app) | **PASS** | Cache đồng bộ hoàn toàn với tệp lưu trữ |
| 11. Tính sống sót của chính sách (Policy survives) | **PASS** | Lịch biểu và giới hạn ngày tồn tại nguyên vẹn sau kill process |
| 12. Tính sống sót của tổng thời gian (Usage survives) | **PASS** | `usage_aggregates_json` được đọc lại với date key chính xác |
| 13. Cô lập trạng thái runtime (Runtime state isolation) | **PASS** | Biến phiên làm việc (`currentSessionId`, `activeUsagePackage`) **KHÔNG** bị ghi vào DataStore, ngăn ngừa trạng thái rác sau restart |
| 14. Xử lý chuỗi JSON lỗi (Malformed JSON handling) | **PASS** | Cơ chế `try-catch` với fallback an toàn về `DEFAULT_APPS` ngăn chặn mọi nguy cơ crash |
| 15. Giá trị âm & Tên rỗng (Negative usage & Empty pkg) | **PASS** | `optLong >= 0L` và `pkg.isNotBlank()` lọc triệt để dữ liệu bất thường |
| 16. Cơ chế Migration schema | **PASS** | Đây là release schema v1.0 đầu tiên; parser JSON có khả năng chịu lỗi (fault-tolerant) tự nhiên với các trường mới |

---

## 7. Real-Device Release Validation

Kiểm tra hành vi thực tế của bản build Release trên thiết bị thật:
- **Thiết bị:** vivo iQOO Neo 10 (Model `V2425A`, Serial `10CF3J1F3400238`)
- **Hệ điều hành:** Android 15 / API 35 / OriginOS 5
- **Số kịch bản thực hiện:** 20/20

| STT | Kịch bản kiểm toán | Kết quả | Bằng chứng thực tế trên vivo iQOO Neo 10 |
|:---:|--------------------|:-------:|-----------------------------------------|
| 01 | Fresh install release APK | **PASS** | `Performing Incremental Install Streamed Install Success` |
| 02 | Launch MainActivity | **PASS** | `mCurrentFocus=Window{... MainActivity}` |
| 03 | Accessibility permission onboarding | **PASS** | Onboarding view hiển thị đầy đủ, hướng dẫn chuẩn xác |
| 04 | Enable AccessibilityService | **PASS** | Dịch vụ đăng ký thành công trong `dumpsys accessibility` |
| 05 | Default Target Chrome locking | **PASS** | Khóa Chrome thành công: `mCurrentFocus=Window{... LockScreenActivity}` |
| 06 | Add Calculator dynamic target | **PASS** | Thêm mục tiêu Calculator thành công vào `TargetRepository` |
| 07 | Enable / Disable target | **PASS** | Khi disabled, ứng dụng đích mở bình thường; khi enabled, bị khóa |
| 08 | Schedule lock evaluation | **PASS** | Lịch biểu kích hoạt khóa chính xác theo giờ hệ thống |
| 09 | Daily limit lock | **PASS** | Hạn mức sử dụng ngày hết -> kích hoạt khóa ngay lập tức |
| 10 | Schedule + Daily Limit collision | **PASS** | Ưu tiên `SCHEDULE_DEADLINE` một cách tất định theo đúng Frozen Invariant |
| 11 | LockScreen presentation | **PASS** | `LockScreenActivity` hiển thị vững chắc ở foreground, chặn target |
| 12 | Shield presentation | **PASS** | `BlockingShieldOverlay` xuất hiện ngay lập tức tại t1 trước khi Activity vẽ khung hình đầu tiên |
| 13 | Back handling | **PASS** | Nhấn phím Back không làm lộ ứng dụng đích: focus trở về launcher (`com.bbk.launcher2`) |
| 14 | Home handling | **PASS** | Nhấn phím Home điều hướng sạch sẽ về Launcher màn hình chính |
| 15 | Returning to target | **PASS** | Quay lại ứng dụng đích lập tức kích hoạt khóa mới không độ trễ |
| 16 | Process restart recovery | **PASS** | Sau `am force-stop`, dịch vụ kết nối lại và lập tức bảo vệ mục tiêu |
| 17 | Screen OFF/ON | **PASS** | Tắt mở màn hình không gây treo, rò rỉ bộ nhớ, hay đếm trùng thời gian |
| 18 | Service reconnect | **PASS** | Tắt dịch vụ rồi bật lại trong hệ thống -> OS liên kết lại thành công |
| 19 | Target removal / re-add | **PASS** | Xóa và thêm lại mục tiêu diễn ra mượt mà |
| 20 | Diagnostics availability | **PASS** | Đệm `DiagnosticTrace` hoạt động hoàn hảo, phát 59 sự kiện chẩn đoán trong release build |

---

## 8. Regression Tests

Kiểm toán hồi quy toàn diện trên bộ mã kiểm thử tự động của kho lưu trữ:

| Phân loại kiểm thử | Số lượng test | Kết quả | Tỷ lệ thành công |
|--------------------|:-------------:|:-------:|:----------------:|
| `DiagnosticObservabilityTest` | 12 | **PASS** | 100% |
| `BlockingShieldOverlayTest` | 8 | **PASS** | 100% |
| `ClockAndScheduleResilienceTest` | 18 | **PASS** | 100% |
| `ConcurrencyAndLifecycleHardeningTest` | 21 | **PASS** | 100% |
| `PolicyEngineTest` | 8 | **PASS** | 100% |
| `PolicyPrecedenceAndConflictTest` | 20 | **PASS** | 100% |
| `ProductionStateRecoveryTest` | 24 | **PASS** | 100% |
| `ProductionStressAndSoakTest` (500 transitions + 200 sessions) | 8 | **PASS** | 100% |
| `ScheduleEvaluatorTest` | 30 | **PASS** | 100% |
| `ScheduleWatcherTest` | 26 | **PASS** | 100% |
| `TargetRepositoryTest` | 11 | **PASS** | 100% |
| `MainScreenViewModelTest` | 2 | **PASS** | 100% |
| `UsageLimitWatcherTest` | 16 | **PASS** | 100% |
| `UsageTrackerTest` | 11 | **PASS** | 100% |
| **TỔNG CỘNG UNIT & REGRESSION** | **215** | **PASS** | **215/215 (100%)** |

---

## 9. Release Configuration Audit

- **Debug vs Release Logging:** **PASS** (Sử dụng `AndroidDiagnosticLogger` với đệm vòng giới hạn dung lượng 250 bản ghi trong bộ nhớ, non-blocking, an toàn tuyệt đối).
- **BuildConfig Flags:** **PASS** (`buildFeatures { buildConfig = false }` giúp tối ưu bytecode).
- **Hard-coded Endpoints / Test URLs:** **PASS** (Không có bất kỳ endpoint nào).
- **Test-only Packages / Activities:** **PASS** (Không có thành phần test nào lộ ra trong `AndroidManifest.xml`).
- **Development-only UI / Debug Banners:** **PASS** (Giao diện sạch sẽ, chỉ chứa các thành phần chức năng sản xuất).
- **TODO / FIXME Markers:** **PASS** (Không có bất kỳ marker FIXME nào; TODO duy nhất nằm trong tệp mẫu comment của hệ thống backup).
- **Minification / R8:** **PASS** (`isMinifyEnabled = false` — Giữ nguyên tính toàn vẹn của mã nguồn Compose và DataStore mà không bị nguy cơ stripped class).

---

## 10. Reproducibility / Rollback

- **Khả năng tái lập bản build (Build Reproducible):** **YES**
  - Mọi lập trình viên hoặc hệ thống CI/CD đều có thể tái lập bản build bằng cách clone kho lưu trữ và thực thi `./gradlew assembleRelease`.
  - Toolchain được ghim chặt chẽ qua Gradle Wrapper 9.1.0 (kèm SHA-256 checksum) và Foojay Java 17 toolchain resolver.
  - Không có sự phụ thuộc vào đường dẫn tuyệt đối của máy trạm cá nhân.
- **Bit-for-bit Artifact Reproducibility:** **NO** (Không cam kết bit-for-bit tuyệt đối do metadata zip và timestamp của các công cụ đóng gói Android có thể biến thiên giữa các môi trường).
- **Khả năng Rollback:** **YES** (Kho lưu trữ được quản lý qua Git với commit hash rõ ràng `54304c3`).

---

## 11. Findings

| Mã Finding | Mức độ nghiêm trọng | Mô tả chi tiết | Khuyến nghị / Hành động |
|:----------:|:-------------------:|----------------|------------------------|
| **P3-01** | **P3 (Low)** | `buildTypes.release` chưa cấu hình sẵn `signingConfig` trong `app/build.gradle.kts`, do đó Gradle xuất ra `app-release-unsigned.apk`. | Đây là best-practice để bảo vệ release keystore không bị commit vào git. Khuyến nghị thực hiện bước ký số tại CI/CD pipeline bằng `apksigner`. |
| **INFO-01** | **INFO** | `isMinifyEnabled = false` trong release build. Kích thước APK là 6.76 MB. | Mức kích thước này rất nhẹ và an toàn tuyệt đối cho Compose runtime, không cần kích hoạt R8 nếu không bắt buộc. |
| **INFO-02** | **INFO** | Cấu hình dịch vụ trợ năng tắt hoàn toàn `canRetrieveWindowContent`. | Điểm cộng lớn về tuân thủ chính sách quyền riêng tư của Google Play và người dùng. |

- **Tổng số lỗi P0 (Release Blocker):** **0**
- **Tổng số lỗi P1 (High Severity):** **0**
- **Tổng số lỗi P2 (Medium Severity):** **0**

---

## 12. Final Ship Gate

| Câu hỏi kiểm định chốt chặn | Trả lời | Ghi chú & Bằng chứng |
|-----------------------------|:-------:|----------------------|
| Build reproducible? | **YES** | `./gradlew clean`, `test`, `assembleRelease` thành công 100% |
| Release artifact generated? | **YES** | `app-release-unsigned.apk` (7,091,208 bytes) |
| Release artifact verified? | **YES** | SHA-256 xác thực, cài đặt và kiểm thử thực tế thành công |
| Security/privacy blocker? | **NO** | Không rò rỉ dữ liệu, không có quyền nguy hiểm, offline 100% |
| Persistence blocker? | **NO** | DataStore Preferences hoạt động ổn định, schema v1.0 fault-tolerant |
| Regression blocker? | **NO** | 215/215 unit & regression tests PASS |
| Real-device blocker? | **NO** | 20/20 kịch bản thực tế PASS trên vivo iQOO Neo 10 (Android 15) |
| Production code changed? | **NO** | `Production source changes = 0` (Bảo toàn tuyệt đối Frozen Core) |

### PHÁN QUYẾT CUỐI CÙNG:

# **SHIP**

---

## 13. Limitations

Nhằm đảm bảo tính chuẩn xác và khoa học, các giới hạn sau đây được ghi nhận rõ ràng:
1. **Phạm vi thiết bị thực tế:** Bản build release được kiểm nghiệm trực tiếp trên dòng máy vivo iQOO Neo 10 (`V2425A`), chạy Android 15 (API 35) với giao diện OriginOS 5. Kết quả này không cam kết đại diện cho 100% tất cả các biến thể ROM tùy biến của các nhà sản xuất OEM khác (như MIUI, OneUI, ColorOS) nếu có sự can thiệp sâu vào cơ chế background service.
2. **Không cam kết bit-for-bit APK reproducibility:** Bản build được đảm bảo tính tái lập về mặt chức năng và mã nguồn (Build Reproducible), nhưng không cam kết từng bit nhị phân giống hệt nhau do sự khác biệt về timestamp đóng gói zip.
3. **Ký số phát hành:** Artifact tạo ra trực tiếp từ AGP là bản chưa ký (`unsigned`). Việc phân phối tới người dùng cuối cần thông qua quy trình ký số chính thức của nhà phát hành với khóa riêng tư (Private Release Key).
