# PHASE 0B — CODEBASE FORENSIC AUDIT
**Dự án:** self-discipline-poc-01  
**Mã kiểm định:** AUDIT-PHASE-0B-20260910  
**Đối chiếu chuẩn (SSOT Authority):** `docs/MASTER SSOT - HỆ THỐNG PHONG ẤN DỤC VỌNG.docx` (v1.0.0)  
**Tài liệu hỗ trợ nhận thức:** `docs/SSOT_COMPREHENSION_REPORT.md`  
**Thời gian kiểm định:** 10/09/2026  
**Phạm vi:** Toàn bộ repository (Mã nguồn, Cấu hình, Database, Manifest, Tests, Tài nguyên)  
**Nguyên tắc thực thi:** 100% Read-Only, không sửa code, không xóa file, không thay đổi cấu hình, dựa trên bằng chứng kỹ thuật cụ thể.

---

## 1. Executive Summary

### 1.1. Mục đích Forensic Audit
Báo cáo Forensic Audit này được thực hiện nhằm rà soát và đánh giá toàn diện hiện trạng mã nguồn của dự án `self-discipline-poc-01` dưới lăng kính tối cao duy nhất: **MASTER SSOT - HỆ THỐNG PHONG ẤN DỤC VỌNG (v1.0.0)**.
Mục tiêu là phát hiện tất cả các điểm sai lệch nghiệp vụ (Business Rule Drift), tàn dư mã nguồn thử nghiệm (Legacy POC code), các thành phần chưa được hiện thực hóa (Missing Features), các xung đột kiến trúc (Architectural Conflicts), và hiện tượng ảo giác kiểm thử (False Confidence).

### 1.2. Hiện trạng Tổng thể Codebase
Codebase hiện tại là sản phẩm kế thừa qua 27 phase phát triển thử nghiệm (POC). Về bản chất kỹ thuật, codebase hiện có:
1. **Lớp nền tảng thực thi phong ấn cơ học (Android Enforcement Core):** Hoạt động tốt trên thiết bị thử nghiệm (vivo iQOO Neo 10, Android 15), dựa trên `AppDetectorAccessibilityService` bắt sự kiện `TYPE_WINDOW_STATE_CHANGED`, phối hợp với `BlockingShieldOverlay` (dùng `TYPE_ACCESSIBILITY_OVERLAY`) và `LockScreenActivity` để che chắn ứng dụng với độ trễ cực thấp (< 50ms).
2. **Lớp nghiệp vụ nhiệm vụ - bảo khố sơ khai (Domain Foundation):** Bổ sung khái niệm Nhiệm Vụ Đường (`TaskEntity`) và Bảo Khố (`AppEntity`) lưu trữ trên SQLite Room Database (`AppDatabase`), được điều phối qua `TaskAppEnforcementAdapter`.
3. **Lớp tàn dư thử nghiệm cũ (Legacy Technical POC):** Cơ chế khóa ứng dụng 24/7, khóa theo khung giờ cố định (`TimeSchedule`) và khóa theo số phút sử dụng hàng ngày (`TimeLimit`), lưu trữ trên `DataStore Preferences` qua `PolicyEngine`, `ScheduleWatcher`, `UsageLimitWatcher`.

### 1.3. Bảng Tổng Hợp Trạng Thái So Với SSOT
| Hạng mục SSOT | Mức độ hoàn thiện | Phân loại trạng thái | Ghi chú & Bằng chứng |
| :--- | :---: | :---: | :--- |
| **Triết lý Tự Kỷ Luật & Cốt lõi** | 40% | PARTIALLY_IMPLEMENTED | Có khái niệm Nhiệm vụ & Bảo khố, nhưng sai bản chất phong ấn |
| **Chu kỳ 04:00 (Cycle Engine)** | 30% | STATIC_ONLY / UNTESTED | Có `BusinessDayProviderImpl` tính giờ, thiếu 100% hạ tầng Alarm/Boot/Background |
| **Bảo Khố & Quy tắc 2/3 (Vault)** | 50% | **CONFLICT / DEFECT** | `TaskUnlockPolicy.kt:53` tính sai N=2; N=0 bị khóa sai (`TaskAppEnforcementAdapter.kt:454`) |
| **Nhiệm Vụ Đường (Mission Hall)** | 40% | **CONFLICT** | Hoàn thành task ở màn hình độc lập (`MissionHallScreen.kt:140`); sửa gán app ăn ngay |
| **Tháp Tu Luyện (Tower)** | 0% | **MISSING** | Hoàn toàn chưa có mã nguồn hay thực thể database |
| **Phó Bản (Dungeon)** | 0% | **MISSING** | Hoàn toàn chưa có mã nguồn hay thực thể database |
| **Tiệm (Shop) & Điểm D** | 0% | **MISSING** | Hoàn toàn chưa có mã nguồn, điểm D hay giao dịch ACID |
| **Túi Đồ (Inventory) & Voucher 15 ngày** | 0% | **MISSING** | Hoàn toàn chưa có mã nguồn hay cơ chế neo mốc 15 ngày |
| **Trợ Lý AI & Kế Hoạch Động** | 0% | **MISSING** | Chưa tích hợp Gemini/LLM, không có Intent parser, không có Privacy Guard |
| **Giao Tiếp Giọng Nói & Barge-In** | 0% | **MISSING** | Không có VAD, TTS, STT hay timeout 4 giây kết thúc câu thoại dở dang |
| **Ký Ức (Session / Long-term Memory)** | 0% | **MISSING** | Không có phân loại RAM/Flash, không có cụm từ nghiêm ngặt `XÓA TẤT CẢ` |
| **System Panel & AI Orb** | 10% | **CONFLICT** | Chỉ có `LockScreenActivity` thô sơ và `BlockingShieldOverlay` trắng |

### 1.4. Các Điểm Nóng Rủi Ro Cần Chú Ý Đặc Biệt
- **Xung đột công thức N=2:** `TaskUnlockPolicy.kt:53` tính `(2*2+2)/3 = 2` (buộc hoàn thành 2/2). SSOT yêu cầu: Đặc xá khởi đầu N=2 chỉ cần 1 task.
- **Xung đột app mới thêm vào Bảo Khố:** `TaskUnlockPolicy.kt:60` và `TaskAppEnforcementAdapter.kt:454` khóa ngay app khi N=0 (`LOCKED_BY_VAULT_NO_TASK`). SSOT yêu cầu: App mới thêm là UNLOCKED.
- **Quyền ưu tiên kỹ thuật lấn át nghiệp vụ:** `TaskAppEnforcementAdapter.kt:423` gán cho Technical Lock quyền tối thượng đè bẹp Business Unlock của SSOT.
- **Đột biến cấu hình phần thưởng nhiệm vụ:** `CoreDataRepositoryImpl.kt:108` xóa và chèn trực tiếp `TaskAppCrossRef`, làm thay đổi trạng thái khóa ngay trong chu kỳ hiện tại thay vì chuyển sang trạng thái chờ chu kỳ tiếp theo (Pending Next-Cycle).
- **Ảo giác kiểm thử (False Confidence):** 359 unit tests pass 100% nhưng đang khẳng định các quy tắc cũ sai lệch (assert `N=2 => 2`, assert `N=0 => LOCK`).

---

## 2. Repository Inventory

Repository `self-discipline-poc-01` hiện có tổng cộng **268 files** (đã loại trừ `.git`, `.gradle`, và thư mục sinh mã `build`).

### 2.1. Thống kê Phân Loại File
- **Kotlin Main Source (`app/src/main/java/`):** 72 files (100% Kotlin).
- **Kotlin Unit Test Source (`app/src/test/java/`):** 27 files (100% Kotlin).
- **Kotlin Android Instrumentation Test (`app/src/androidTest/java/`):** 1 file (`MainScreenTest.kt`).
- **Android Manifest:** 1 file (`app/src/main/AndroidManifest.xml`).
- **Android Resources (`app/src/main/res/`):** 14 files (drawables, layouts, values strings/colors/themes, xml configs).
- **Gradle & Build Configuration:** 5 files (`build.gradle.kts`, `settings.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `gradlew.bat`).
- **Tài liệu đặc tả SSOT (`docs/`):** 12 files (bao gồm file gốc `MASTER SSOT - HỆ THỐNG PHONG ẤN DỤC VỌNG.docx`, `SSOT_COMPREHENSION_REPORT.md`, và các tài liệu giai đoạn trước).
- **Báo cáo lịch sử tại Root:** 22 files markdown (`PHASE_07A_REPORT.md` đến `PHASE_27_REPORT.md`).
- **Thư mục phân tích & công cụ tạm (`scratch/`):** 90 files (chứa script trích xuất SSOT, dump database, log test).
- **Cấu hình Agent & CI/CD:** 2 files (`.agents/rules/language.md`, `.github/workflows/release-pipeline.yml`).
- **Khác:** 4 files (`.gitignore`, `AGENTS.md`, `proguard-rules.pro`, `gradle.properties`).

### 2.2. Kiểm Kê Android Platform Components trong Manifest
Kiểm tra chi tiết file `app/src/main/AndroidManifest.xml` (52 dòng):
```xml
Package: com.example.selfdisciplinepoc01
Compile SDK: 35 | Target SDK: 35 | Min SDK: 26
```
1. **Thẻ `<uses-permission>`:** **HOÀN TOÀN KHÔNG CÓ THẺ NÀO ĐƯỢC KHAI BÁO**.
   - Quyền trợ năng duy nhất được gián tiếp khai báo là `android.permission.BIND_ACCESSIBILITY_SERVICE` trong thẻ `<service>`.
   - Thiếu hụt nghiêm trọng các quyền hệ thống bắt buộc cho SSOT:
     + `SYSTEM_ALERT_WINDOW`: Bắt buộc để vẽ System Panel đè lên mọi ứng dụng.
     + `RECEIVE_BOOT_COMPLETED`: Bắt buộc để khôi phục chu kỳ phong ấn khi thiết bị khởi động lại.
     + `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`: Bắt buộc để kích hoạt chu kỳ 04:00 chính xác.
     + `RECORD_AUDIO`: Bắt buộc cho tính năng Trợ lý Giọng nói & Barge-In.
     + `INTERNET`: Bắt buộc cho Trợ lý AI kết nối Cloud (khi có lệnh chủ nhân).
     + `POST_NOTIFICATIONS`: Bắt buộc để hiển thị thông báo trạng thái hoặc cảnh báo.
2. **Thẻ `<application>`:**
   - `android:allowBackup="true"`: **LỖ HỔNG BẢO MẬT** — cho phép trích xuất database qua adb backup.
   - `android:supportsRtl="true"`.
   - `android:theme="@android:style/Theme.Material.NoActionBar"`.
3. **Activities (2):**
   - `.MainActivity`: exported = true, có intent-filter `ACTION_MAIN` & `CATEGORY_LAUNCHER`.
   - `.LockScreenActivity`: exported = false, `launchMode="singleTop"`, theme riêng `@style/Theme.SelfDisciplinePoc01.LockScreen`.
4. **Services (1):**
   - `.AppDetectorAccessibilityService`: permission = `BIND_ACCESSIBILITY_SERVICE`, exported = true, config qua `@xml/accessibility_service_config`.
5. **Broadcast Receivers trong Manifest:** **0**.
6. **Background Workers / Job Services:** **0**.

---

## 3. Architecture Assessment

### 3.1. Đánh giá Chi tiết 10 Khía Cạnh Kiến Trúc

#### 1. Presentation / UI Layer
- **Công nghệ:** Jetpack Compose (Compose BOM 2024.10.01, Compose Compiler 2.0.21, Material 3).
- **Cấu trúc Theme:** Xây dựng hệ thống design tokens `CultivationTheme` rất hoàn chỉnh tại `app/src/main/java/com/example/selfdisciplinepoc01/ui/design/`:
  + `theme/CultivationColors.kt`: Màu Huyền Hắc Tối, Kim Quang (`celestialGold`), Thanh Khí (`spiritTeal`), Huyết Sắc Cảnh Báo (`statusDanger`).
  + `theme/CultivationTypography.kt`: Phân cấp heading hero, title card, rune runes, caption.
  + `components/`: Thẻ bài nhiệm vụ (`TaskCard`), Thẻ tiến độ (`ProgressCard`), Hộp thoại (`CultivationDialog`), Nút bấm (`CultivationButton`).
- **Khiếm khuyết so với SSOT:**
  + Màn hình chính `MainActivity.kt` chỉ có 3 tab: `Nhiệm Vụ Đường`, `Bảo Khố`, `Quản Trị Thực Thi`.
  + Hoàn toàn thiếu cấu trúc điều hướng 9 màn hình chuẩn mực của SSOT:
    * Màn hình chính (Home) với AI Orb trung tâm.
    * Nhiệm Vụ Đường (Mission Hall).
    * Bảo Khố (Vault).
    * Túi Đồ (Inventory).
    * Tu Luyện (Cultivation: Tháp Tu Luyện + Phó Bản).
    * Tiệm (Shop).
    * Ký Ức (Memory).
    * Cài Đặt (Settings).
  + Màn hình khóa `LockScreenActivity.kt` chỉ là một Activity che chắn đơn điệu với nút bấm "Quay về màn hình chính", không phải là System Panel dạng Overlay toàn màn hình có tích hợp danh sách nhiệm vụ và AI Orb tương tác.

#### 2. Domain / Business Logic Layer
- **Thực trạng:** Logic nghiệp vụ bị phân mảnh và xung đột dữ dội giữa hai mô hình:
  + Khối Legacy POC: `PolicyEngine` (khóa 24/7, khung giờ Schedule, giới hạn phút Daily Limit).
  + Khối Cultivation Domain: `TaskUnlockPolicy` (công thức 2/3), các Use Case (`AddVaultAppUseCase`, `CompleteTaskUseCase`, v.v.).
- **Điểm yếu rò rỉ (Logic Leakage):**
  + Lớp `TaskAppEnforcementAdapter.kt` ôm đồm quá nhiều trách nhiệm: vừa làm in-memory snapshot cache (`cachedSnapshot`), vừa quan sát luồng reactive từ database, vừa chứa logic đánh giá phân loại (`buildEnforcementDetails`), vừa chứa bảng quyết định quyền ưu tiên kỹ thuật đè bẹp nghiệp vụ.
  + `VaultViewModel.kt:108` tự tính lại công thức `(2 * N + 2) / 3` trong hàm `createDefaultEnforcement()`, làm trùng lặp và phân tán logic nghiệp vụ ngoài domain policy.

#### 3. Persistence Layer
- **Thực trạng Lưỡng cực (Dual Persistence Conflict):**
  + `DataStore Preferences`: Lưu trữ cấu hình mục tiêu và mức sử dụng thời gian của Legacy POC.
  + `Room Database` (`AppDatabase`, SQLite file `self_discipline_core.db`): Lưu trữ thực thể Nhiệm vụ, Bảo khố, Liên kết chéo, Hoàn thành theo ngày.
- **Khiếm khuyết so với SSOT:**
  + Room schema hiện tại cực kỳ hạn hẹp, chỉ có 4 bảng. Hoàn toàn thiếu các thực thể quản lý Tháp (tầng, công thức, trạng thái vượt), Phó bản (lịch sử, loot), Điểm D, Tiệm, Túi đồ, Phiếu thông hành (thời hạn, mốc hết hạn 15 ngày), Kế hoạch AI và Ký ức.

#### 4. Android Platform Layer
- **Thực trạng:** Ứng dụng sống dựa hoàn toàn vào `AppDetectorAccessibilityService`.
- **Cơ chế phát hiện:** Lắng nghe sự kiện `AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED`. Khi người dùng chuyển sang package khác, dịch vụ kiểm tra package name và gọi logic phong ấn.
- **Rủi ro Platform:**
  + Trên Android 15, việc khởi chạy Activity từ background (`startActivity(LockScreenActivity)`) bị kiểm soát vô cùng nghiêm ngặt (Background Activity Launch Restrictions). Ứng dụng hiện phải nhờ quyền Trợ năng để bypass, nhưng đây là giải pháp mong manh nếu dịch vụ trợ năng bị người dùng hoặc hệ thống tắt.
  + Thiếu hoàn toàn cơ chế vẽ System Overlay chuẩn qua `WindowManager` với quyền `SYSTEM_ALERT_WINDOW`.

#### 5. Background Execution Layer
- **Thực trạng:** **0% HOÀN TOÀN TRỐNG RỖNG**.
- Không có bất kỳ Worker (`androidx.work.WorkManager`), Alarm (`AlarmManager`), hay JobService nào.
- Ứng dụng không có khả năng tự thức dậy đúng 04:00:00 sáng để thực hiện reset chu kỳ nếu người dùng không tương tác với máy.

#### 6. App Enforcement Layer
- **Thực trạng:** Phối hợp hai giai đoạn:
  + Giai đoạn 1 (Tức thì - 0ms): `BlockingShieldOverlay` hiển thị một tấm chắn mờ đục màu trắng che toàn màn hình thông qua `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY`.
  + Giai đoạn 2: Khởi chạy `LockScreenActivity` đè lên. Khi khung hình đầu tiên của Activity được vẽ (`OnDrawListener`), tấm chắn mờ đục được gỡ bỏ.
- **Đánh giá:** Rất hiệu quả về mặt ngăn chặn cơ học thị giác, nhưng sai lệch về UX của SSOT: SSOT yêu cầu hiển thị trực tiếp System Panel dạng Overlay kèm AI Orb, chứ không phải tấm chắn trắng rồi nhảy sang Activity.

#### 7. AI Layer
- **Thực trạng:** **0% CHƯA TỒN TẠI**. Không có mã nguồn nào liên quan đến AI, LLM, prompt, Gemini SDK hay phân tích ý đồ.

#### 8. Voice / Audio Layer
- **Thực trạng:** **0% CHƯA TỒN TẠI**. Không có mã nguồn nào liên quan đến VAD, Barge-in, Text-to-Speech hay Speech-to-Text.

#### 9. Memory Layer
- **Thực trạng:** **0% CHƯA TỒN TẠI**. Không có mã nguồn nào liên quan đến lưu trữ Ký ức phiên hay Ký ức dài hạn.

#### 10. Security & Privacy Layer
- **Thực trạng:**
  + Không gửi dữ liệu ra ngoài Internet (vì không có quyền INTERNET).
  + Trợ năng không đọc nội dung view (`canRetrieveWindowContent = false`).
  + Tuy nhiên, database SQLite không được mã hóa (`SQLCipher`), và Manifest cho phép backup (`allowBackup = true`).

---

## 4. Module Assessment

Dự án hiện tại là một **Single-Module Monolith (`:app`)**.

### 4.1. Đánh giá Kiến Trúc Module
- Việc gom toàn bộ mã nguồn vào một module duy nhất khiến các ranh giới kiến trúc bị xóa nhòa. Các lớp presentation (`MissionHallViewModel`, `VaultViewModel`) có thể gọi trực tiếp tầng database hoặc adapter mà không bị ràng buộc biên giới biên dịch.
- Mã nguồn kiểm thử phụ thuộc nặng nề vào Robolectric để mô phỏng môi trường Android, ngay cả với những logic nghiệp vụ thuần túy như tính toán chu kỳ 04:00 hay công thức mở khóa 2/3.

### 4.2. Đề Xuất Phân Tách Module Cho Canonical Rebuild
Để đáp ứng quy mô đồ sộ của MASTER SSOT, kiến trúc tương lai cần được module hóa theo chuẩn Android Modern Architecture:
1. `:core:model`: Chứa thuần túy Kotlin data classes, enums, value objects (Task, VaultApp, Floor, Loot, Voucher, MemoryItem). Không phụ thuộc Android SDK.
2. `:core:database`: Chứa Room Database, Entities, DAOs, Migrations, SQLCipher.
3. `:core:time`: Chứa Cycle Engine, BusinessDayProvider, Clock, Mốc 04:00.
4. `:core:enforcement`: Chứa AccessibilityService, WindowManager System Overlay Engine, App Detector, Latency Shield.
5. `:core:ai`: Chứa AI Agent, Dynamic Planner, Tool Calling Engine, Privacy Guard.
6. `:core:voice`: Chứa VAD, Barge-In Controller, TTS Engine, Audio Recorder.
7. `:core:designsystem`: Chứa Cultivation Theme, Design Tokens, Reusable Components.
8. `:feature:systempanel`: Chứa System Panel Overlay, AI Orb UI, Quick Task Completion.
9. `:feature:missionhall`: Chứa giao diện & use cases Quản lý Chuỗi Nhiệm Vụ.
10. `:feature:vault`: Chứa giao diện & use cases Quản lý Ứng Dụng Bảo Khố.
11. `:feature:cultivation`: Chứa Tháp Tu Luyện (Tower) và Phó Bản (Dungeon).
12. `:feature:shop`: Chứa Tiệm, Điểm D, Mua Phiếu Thông Hành 24h.
13. `:feature:inventory`: Chứa Túi Đồ, Quản lý Phiếu Thông Hành 15 ngày.
14. `:feature:memory`: Chứa Giao diện Ký Ức & Cơ chế xác nhận `XÓA TẤT CẢ`.

## 5. File-Level Assessment

Dưới đây là bảng đánh giá phân loại chi tiết cho toàn bộ các file mã nguồn chính và thành phần quan trọng trong dự án:

| File / Component | Vai trò (Role) | Mục đích hiện tại | Quan hệ SSOT | Hành động | Độ tin cậy | Bằng chứng mã nguồn | Rủi ro & Tác động |
| :--- | :--- | :--- | :--- | :---: | :---: | :--- | :--- |
| `AppDetectorAccessibilityService.kt` | Platform Service | Lắng nghe chuyển app, kích hoạt khóa | Cốt lõi phong ấn | **ADAPT** | HIGH | `onAccessibilityEvent():468`, `TYPE_WINDOW_STATE_CHANGED` | Đang phụ thuộc vào PolicyEngine cũ; cần chuyển sang gọi System Panel |
| `LockScreenActivity.kt` | Presentation | Màn hình Activity khi app bị khóa | Tiền thân System Panel | **REWORK** | HIGH | `onCreate():58`, `goToHomeScreen():289` | SSOT cấm Activity độc lập; phải chuyển thành System Panel Overlay |
| `BlockingShieldOverlay.kt` | Platform Overlay | Che màn hình tức thì bằng Type Overlay | Che chắn đón đầu latency | **ADAPT** | HIGH | `TYPE_ACCESSIBILITY_OVERLAY:98`, `show():75` | Nền tảng tuyệt vời cho System Panel; cần tích hợp giao diện thay vì màu trắng |
| `AccessibilityUtil.kt` | Utility | Mở màn hình cài đặt trợ năng | Tiện ích thiết lập | **KEEP** | HIGH | `isAccessibilityServiceEnabled():14` | Hàm tiện ích chuẩn, không phụ thuộc business logic |
| `MainActivity.kt` | Presentation | Host 3 tab giao diện cũ | Điều hướng trung tâm | **REWORK** | HIGH | `MainAppScreen():142`, 3 tabs navigation | Thiếu 8 màn hình SSOT; chứa tab Legacy Foreground Detector |
| `domain/policy/TaskUnlockPolicy.kt` | Pure Policy | Tính số task yêu cầu để mở app | Quy tắc 2/3 (OPEN-01) | **REWORK** | HIGH | `calculateRequiredTasks():51`, `(2*n+2)/3` | **CONFLICT:** Tính sai N=2 (cần 1); sai N=0 (khóa thay vì mở) |
| `domain/enforcement/TaskAppEnforcementAdapter.kt` | Enforcement Bridge | Phối hợp DB, Policy và Service | Cốt lõi điều phối phong ấn | **REWORK** | HIGH | `evaluateSync():330`, `evaluate():282` | **CONFLICT:** Technical lock đè bẹp SSOT; khóa app khi N=0 |
| `domain/usecase/AddVaultAppUseCase.kt` | UseCase | Thêm app vào Bảo Khố | Quản lý Bảo Khố | **ADAPT** | HIGH | `invoke():15`, `repository.addVaultApp()` | Cần đảm bảo app mới thêm có trạng thái UNLOCKED đúng chuẩn SSOT |
| `domain/usecase/RemoveVaultAppUseCase.kt` | UseCase | Xóa app khỏi Bảo Khố & liên kết | Quản lý Bảo Khố | **ADAPT** | HIGH | `invoke():17`, `repository.removeVaultApp()` | Thiếu cơ chế neo mốc 15 ngày cho Voucher của app bị xóa (INV-002) |
| `domain/usecase/CreateTaskUseCase.kt` | UseCase | Tạo nhiệm vụ tối giản | Quản lý Nhiệm Vụ Đường | **ADAPT** | HIGH | `invoke():15`, `repository.createTask()` | Thiếu trường `order_index`, `created_cycle` theo SSOT |
| `domain/usecase/CompleteTaskUseCase.kt` | UseCase | Hoàn thành nhiệm vụ theo ngày | Tự kỷ luật hàng ngày | **ADAPT** | HIGH | `invoke():23`, `repository.setTaskCompletion()` | Hoạt động tốt với BusinessDay, nhưng cần chuyển vào luồng System Panel |
| `domain/usecase/ArchiveTaskUseCase.kt` | UseCase | Lưu trữ / Ẩn nhiệm vụ | Quản lý chuỗi nhiệm vụ | **ADAPT** | HIGH | `invoke():13`, `repository.archiveTask()` | Cần gắn với cơ chế xác nhận xóa/hoàn tác của SSOT |
| `domain/usecase/GetMissionHallTasksUseCase.kt` | UseCase | Lấy danh sách nhiệm vụ & chuỗi | Hiển thị Nhiệm Vụ Đường | **ADAPT** | HIGH | `invoke():28`, phân loại chuỗi tuần tự | Cần hỗ trợ mốc chu kỳ 04:00 và cấu hình pending next-cycle |
| `domain/usecase/GetVaultAppsUseCase.kt` | UseCase | Lấy danh sách app Bảo Khố | Hiển thị Bảo Khố | **ADAPT** | HIGH | `invoke():14`, `repository.getAllVaultApps()` | Cần bổ sung trạng thái voucher và thời gian hết hạn |
| `domain/usecase/UpdateTaskLinkedAppsUseCase.kt` | UseCase | Cập nhật app liên kết với task | Gán phần thưởng giải phong ấn | **REWORK** | HIGH | `invoke():16`, `syncTaskLinkedApps()` | **CONFLICT:** Cập nhật ăn ngay vào ngày hiện tại thay vì Pending Next-Cycle |
| `data/database/AppDatabase.kt` | Database | Room Database v1 (4 bảng) | Kho lưu trữ dữ liệu | **REWORK** | HIGH | `entities = [AppEntity, TaskEntity...]` | Thiếu 8 thực thể quan trọng (Tower, Dungeon, Shop, Voucher, Memory...) |
| `data/entity/TaskEntity.kt` | Entity | Bảng `mission_tasks` | Thực thể Nhiệm vụ | **REWORK** | HIGH | `id, name, createdAtWallMillis, isArchived` | Thiếu `order_index`, `created_cycle`, `pending_next_cycle_rewards` |
| `data/entity/AppEntity.kt` | Entity | Bảng `vault_apps` | Thực thể Bảo Khố | **ADAPT** | HIGH | `packageName, appName, isVaultManaged` | Thiếu trường lưu mốc xóa đầu tiên (`first_removal_anchor_millis`) |
| `data/entity/TaskAppCrossRef.kt` | Entity | Bảng `task_app_cross_ref` | Quan hệ Task ↔ App | **ADAPT** | HIGH | `primaryKeys = [taskId, appPackageName]` | Cascade delete chuẩn, nhưng thiếu cờ pending next-cycle |
| `data/entity/DailyTaskCompletionEntity.kt` | Entity | Bảng `daily_task_completions` | Hoàn thành theo chu kỳ | **ADAPT** | HIGH | `primaryKeys = [taskId, businessDate]` | Tách biệt theo ngày rất tốt, nhưng cần bổ sung `cycle_id` chuẩn |
| `data/repository/CoreDataRepositoryImpl.kt` | Repository | Thao tác CRUD Room DB | Tầng truy xuất dữ liệu | **REWORK** | HIGH | `deleteTask():64` xóa sạch lịch sử | Xóa task làm mất toàn bộ lịch sử hoàn thành trong quá khứ |
| `time/BusinessDayProviderImpl.kt` | Time Engine | Tính chu kỳ mốc 04:00 | Chu kỳ ngày của SSOT | **KEEP** | HIGH | `getBusinessDate():68`, boundary 04:00 | Tính toán lý thuyết cực kỳ chuẩn xác, xử lý timezone tốt |
| `policy/PolicyEngine.kt` | Legacy POC | Khóa theo 24/7, Schedule, Limit | Tàn dư của POC kỹ thuật | **REWORK** | HIGH | `evaluate():62`, `isTechnicalLockActive` | Đè bẹp Business Unlock của SSOT; cần loại bỏ quyền tối thượng |
| `policy/ScheduleEvaluator.kt` | Legacy POC | Kiểm tra khung giờ khóa cố định | Tàn dư của POC kỹ thuật | **DELETE** | HIGH | `isWithinSchedule():16` | SSOT không có tính năng khóa theo khung giờ |
| `policy/ScheduleWatcher.kt` | Legacy POC | Đếm giờ khóa theo khung giờ | Tàn dư của POC kỹ thuật | **DELETE** | HIGH | `ScheduleWatcherImpl` | SSOT không có tính năng khóa theo khung giờ |
| `usage/UsageTracker.kt` | Legacy POC | Theo dõi thời gian dùng app bằng DataStore | Tàn dư của POC kỹ thuật | **DELETE** | HIGH | `getTodayUsage():50`, DataStore | SSOT không có tính năng giới hạn số phút dùng hàng ngày |
| `usage/UsageLimitWatcher.kt` | Legacy POC | Đếm giờ khóa khi hết phút dùng | Tàn dư của POC kỹ thuật | **DELETE** | HIGH | `UsageLimitWatcherImpl` | SSOT không có tính năng giới hạn số phút dùng hàng ngày |
| `target/repository/TargetRepositoryImpl.kt` | Legacy POC | Lưu target trên DataStore Preferences | Tàn dư của POC kỹ thuật | **DELETE** | HIGH | DataStore target management | Xung đột và trùng lặp hoàn toàn với `vault_apps` trong Room |
| `ui/design/theme/CultivationColors.kt` | UI Design | Palette màu Tiên Hiệp | Bản sắc thị giác Tiên Hiệp | **KEEP** | HIGH | `celestialGold`, `spiritTeal`, `voidDark` | Rất đẹp, sang trọng, chuẩn mỹ thuật Tiên Hiệp |
| `ui/design/theme/CultivationTypography.kt` | UI Design | Typography chữ Tiên Hiệp | Bản sắc thị giác Tiên Hiệp | **KEEP** | HIGH | `headingHero`, `runeLabel` | Đạt chuẩn thiết kế cao cấp |
| `ui/design/theme/CultivationTheme.kt` | UI Design | Theme Wrapper cho Jetpack Compose | Giao diện toàn app | **KEEP** | HIGH | Composable theme provider | Kiến trúc tokens sạch sẽ và nhất quán |
| `ui/missionhall/MissionHallScreen.kt` | UI Presentation | Màn hình Nhiệm Vụ Đường | Giao diện Nhiệm Vụ Đường | **ADAPT** | HIGH | `onCompleteTask():140`, nút thêm task | Cần loại bỏ nút hoàn thành trực tiếp; chỉ giữ quản lý chuỗi |
| `ui/vault/VaultScreen.kt` | UI Presentation | Màn hình Bảo Khố | Giao diện Bảo Khố | **ADAPT** | HIGH | Danh sách app, nút thu nạp, gỡ bỏ | Cần bổ sung hiển thị Phiếu Thông Hành và trạng thái đúng SSOT |

---

## 6. SSOT Traceability

Dưới đây là ma trận truy vết chi tiết 20 yêu cầu nghiệp vụ bắt buộc từ MASTER SSOT xuống codebase hiện tại:

### 1. PROD-001: Triết Lý Tự Kỷ Luật & Bản Chất Hệ Thống
- **Yêu cầu SSOT:** Hệ thống là công cụ tu luyện tự kỷ luật mang phong cách Tiên Hiệp. Ứng dụng trong Bảo Khố bị phong ấn mặc định và chỉ được giải phong ấn khi hoàn thành nhiệm vụ tự giác gắn liền trong chu kỳ, hoặc sử dụng Phiếu Thông Hành 24h.
- **Hiện trạng Codebase:** `PARTIALLY_IMPLEMENTED`.
- **Bằng chứng mã nguồn:** `domain/model/VaultApp.kt`, `domain/model/Task.kt`, `ui/design/theme/CultivationTheme.kt`.
- **Sai lệch & Xung đột:** Codebase hiện tại xem việc phong ấn là sự cưỡng chế kỹ thuật (Technical Lock với Schedule và Daily Limit) chứ không phải là phần thưởng tự giác giải phong ấn theo chu kỳ.
- **Rủi ro:** Làm biến dạng tinh thần cốt lõi của sản phẩm thành một ứng dụng parental control / screen-time blocker thông thường.

### 2. CYCLE-001: Chu Kỳ Ngày Chuẩn 04:00 Sáng Thiết Bị Địa Phương
- **Yêu cầu SSOT:** Chu kỳ ngày bắt đầu và kết thúc chính xác tại **04:00:00** giờ địa phương của thiết bị. Mọi nhiệm vụ trong chu kỳ được reset trạng thái. Không dùng cửa sổ trượt 24h (Rolling 24h). Không phát âm báo, rung hay notification tại 04:00.
- **Hiện trạng Codebase:** `STATIC_ONLY / UNTESTED`.
- **Bằng chứng mã nguồn:** `time/BusinessDayProviderImpl.kt:68-76`.
- **Kiểm thử hiện có:** `BusinessDayProviderTest.kt` (12 tests pass trên môi trường giả lập JVM).
- **Lỗ hổng & Xung đột:** Không có bất kỳ thành phần nền nào (`AlarmManager`, `WorkManager`) để thức dậy thiết bị tại 04:00. Toàn bộ logic 04:00 chỉ được kích hoạt thụ động (lazy check) khi người dùng mở ứng dụng hoặc chạm màn hình tạo event trợ năng (`TaskAppEnforcementAdapter.kt:337`).
- **Rủi ro:** Khi thiết bị ở trạng thái Doze Mode hoặc tắt màn hình qua 04:00, hệ thống không tự reset.

### 3. CYCLE-002: Xử Lý Ứng Dụng Đang Chạy Tại Ranh Giới 04:00 & Phục Hồi Sau Tắt Nguồn
- **Yêu cầu SSOT:** Nếu người dùng đang mở một ứng dụng bị phong ấn tại thời điểm 03:59:59 chuyển sang 04:00:00, ứng dụng đó phải bị thu hồi quyền truy cập ngay lập tức và đưa người dùng về Home Android. Nếu thiết bị tắt nguồn qua 04:00 (hoặc tắt > 5 ngày), khi khởi động lại hệ thống phải tự động catch-up mà không làm hỏng dữ liệu.
- **Hiện trạng Codebase:** `MISSING`.
- **Bằng chứng mã nguồn:** `AndroidManifest.xml` (0 receiver `BOOT_COMPLETED`), không có timer đếm ngược tại 04:00 cho foreground app.
- **Rủi ro:** Người dùng có thể \"lách luật\" ngồi lì trong ứng dụng giải trí từ 03:50 xuyên qua 04:00 mà không bao giờ bị khóa.

### 4. VAULT-001: Nguyên Lý Phong Ấn Ứng Dụng Trong Bảo Khố
- **Yêu cầu SSOT:** Ứng dụng thuộc Bảo Khố chỉ bị khóa NẾU VÀ CHỈ NẾU: Có ít nhất 1 nhiệm vụ liên kết chưa hoàn thành trong chu kỳ hiện tại VÀ không có Phiếu Thông Hành hiệu lực. Ứng dụng mới thêm vào Bảo Khố chưa gán nhiệm vụ nào (N = 0) thì phải ở trạng thái **UNLOCKED** (Mở khóa).
- **Hiện trạng Codebase:** `CONFLICT / DEFECT`.
- **Bằng chứng mã nguồn:**
  + `TaskUnlockPolicy.kt:60-66`: Khi `activeTaskCount <= 0`, trả về `BusinessUnlockDecision.NO_LINKED_TASKS` với `isUnlocked = false`.
  + `TaskAppEnforcementAdapter.kt:454`: Khi gặp `NO_LINKED_TASKS`, ép hành động `EnforcementAction.LOCK` với lý do `EnforcementReason.LOCKED_BY_VAULT_NO_TASK`.
- **Sai lệch:** App mới thêm vào Bảo Khố bị khóa ngay lập tức! Điều này phá hủy trải nghiệm người dùng ban đầu.

### 5. VAULT-002: Quy Tắc Phong Ấn 2/3 & Đặc Xá N=2
- **Yêu cầu SSOT:** Số nhiệm vụ cần hoàn thành để giải phong ấn ứng dụng có N nhiệm vụ liên kết hiệu lực:
  + `N = 1 => 1`
  + `N = 2 => 1` (Đặc xá khởi đầu: người dùng chỉ cần hoàn thành 1/2 nhiệm vụ)
  + `N = 3 => 2`
  + `N = 4 => 3`
  + `N = 5 => 4`
  + `N = 6 => 4`
  + Tổng quát N >= 3: `ceil(2*N / 3)`.
  + **INV-TASK-001:** Nhiệm vụ không gán app phần thưởng KHÔNG tính vào mẫu số N.
- **Hiện trạng Codebase:** `CONFLICT / DEFECT`.
- **Bằng chứng mã nguồn:** `domain/policy/TaskUnlockPolicy.kt:51-54`:
  ```kotlin
  fun calculateRequiredTasks(n: Int): Int {
      if (n <= 0) return 0
      return (2 * n + 2) / 3
  }
  ```
  Khi `n = 2`: `(2 * 2 + 2) / 3 = 6 / 3 = 2` (Yêu cầu 2 nhiệm vụ thay vì 1).
- **Kiểm thử sai lệch:** `TaskUnlockPolicyTest.kt:39` cố tình assert: `assertEquals(2, TaskUnlockPolicy.calculateRequiredTasks(2))`. Test pass nhưng vi phạm trực tiếp SSOT!

### 6. VAULT-003: Quản Lý Vòng Đời Ứng Dụng Bảo Khố (Thêm/Xóa/Thêm Lại)
- **Yêu cầu SSOT:** Xóa app khỏi Bảo Khố: mở khóa ngay lập tức, gỡ bỏ app khỏi phần thưởng của các nhiệm vụ liên kết (nhiệm vụ vẫn giữ nguyên). Thêm lại app: không được tự động phục hồi các liên kết cũ; app trở về trạng thái chưa gán nhiệm vụ (unlocked).
- **Hiện trạng Codebase:** `PARTIALLY_IMPLEMENTED`.
- **Bằng chứng mã nguồn:** `domain/usecase/RemoveVaultAppUseCase.kt:17-27`, `data/repository/CoreDataRepositoryImpl.kt:34-37` (`crossRefDao.deleteByAppPackageName(packageName)`).
- **Thiếu sót:** Chưa có cơ chế neo mốc 15 ngày cho Phiếu Thông Hành khi xóa app (Yêu cầu INV-002).

### 7. TASK-001: Cấu Trúc Nhiệm Vụ & Chuỗi Tự Kỷ Luật
- **Yêu cầu SSOT:** Thực thể nhiệm vụ phải gồm: `task_id`, `title`, `status` (PENDING/COMPLETED), `reward_app_ids`, `order_index` (thứ tự trong chuỗi tuần tự), `created_cycle`, và `pending_next_cycle_reward_config`. Cho phép đổi tên, xóa có xác nhận và hoàn tác.
- **Hiện trạng Codebase:** `PARTIALLY_IMPLEMENTED`.
- **Bằng chứng mã nguồn:** `data/entity/TaskEntity.kt:18-24`:
  ```kotlin
  data class TaskEntity(
      val id: Long = 0L,
      val name: String,
      val createdAtWallMillis: Long = System.currentTimeMillis(),
      val isArchived: Boolean = false
  )
  ```
- **Thiếu sót nghiêm trọng:** Hoàn toàn thiếu các trường: `order_index`, `created_cycle`, và bảng lưu cấu hình phần thưởng chờ chu kỳ tiếp theo (`pending_next_cycle_reward_config`).

### 8. TASK-002: Cơ Chế Báo Cáo Hoàn Thành Nhiệm Vụ (Self-Report)
- **Yêu cầu SSOT:** Hoàn thành nhiệm vụ dựa trên cơ chế tự giác báo cáo (Self-Report). **CẤM CÓ MÀN HÌNH XÁC NHẬN HOÀN THÀNH NHIỆM VỤ ĐỘC LẬP (STANDALONE SCREEN).** Việc xác nhận hoàn thành chỉ được diễn ra trong luồng System Panel tại ranh giới ứng dụng bị chặn.
- **Hiện trạng Codebase:** `CONFLICT`.
- **Bằng chứng mã nguồn:**
  + `ui/missionhall/MissionHallScreen.kt:140 & 172`: Nút hoàn thành nhiệm vụ nằm chình ình trên màn hình danh sách Nhiệm Vụ Đường!
  + `LockScreenActivity.kt`: Hoàn toàn KHÔNG CÓ danh sách nhiệm vụ hay nút hoàn thành!
- **Sai lệch:** Đảo lộn hoàn toàn luồng trải nghiệm của SSOT: Nơi cần có (System Panel) thì không có, nơi bị cấm có (màn hình độc lập) thì lại có!

### 9. TOWER-001: Tháp Tu Luyện — Tiến Trình Vĩnh Cửu (Eternal Progression)
- **Yêu cầu SSOT:** Tháp là tiến trình vĩnh viễn xuyên suốt vòng đời tu luyện. **TUYỆT ĐỐI KHÔNG RESET TẠI 04:00**. Tầng đã vượt trở thành bất biến (immutable), không cho phép cày cuốc lại tầng cũ.
- **Hiện trạng Codebase:** `MISSING (0%)`. Không có class, entity hay DAO nào.

### 10. TOWER-002: Công Thức Thử Thách & Phần Thưởng Điểm D
- **Yêu cầu SSOT:**
  + Công thức thử thách: `challenge = Start + (i - 1) * Increment`.
  + Thưởng Điểm D: Tầng lẻ thưởng 1D; Tầng chẵn thưởng `floor / 2 + 1` D (Ví dụ: T1=1D, T2=2D, T4=3D, T6=4D, T8=5D).
- **Hiện trạng Codebase:** `MISSING (0%)`.

### 11. TOWER-003: Chế Độ Formula Mode & Thủ Công, Cơ Chế Exception / Propagate
- **Yêu cầu SSOT:** Hỗ trợ 2 chế độ: Manual Mode và Formula Mode. Khi chỉnh sửa tầng trong Formula Mode: Tầng trước bất biến; hỗ trợ cơ chế Exception (chỉ sửa tầng đó) hoặc Propagate (áp dụng lại công thức cho các tầng sau chưa vượt).
- **Hiện trạng Codebase:** `MISSING (0%)`.

### 12. TOWER-004: Quy Tắc Xóa & Bất Biến Tầng Tháp
- **Yêu cầu SSOT:** Tầng đã vượt không được phép xóa hay sửa đổi. Cấm xóa từng tầng đơn lẻ trong Formula Mode.
- **Hiện trạng Codebase:** `MISSING (0%)`.

### 13. SHOP-001: Tiệm Tu Luyện & Đơn Vị Giá Phiếu Thông Hành
- **Yêu cầu SSOT:** Đơn vị quy đổi chuẩn: 1 Phiếu Thông Hành = 24 giờ mở khóa cho 1 ứng dụng cụ thể. Giá mặc định hoặc có thể tùy chỉnh. Mua 1 vé: không cần xác nhận; Mua >= 2 vé: bắt buộc có hộp thoại xác nhận.
- **Hiện trạng Codebase:** `MISSING (0%)`.

### 14. SHOP-002: Tính Nguyên Tử ACID Của Giao Dịch Trước Animation
- **Yêu cầu SSOT:** Giao dịch mua vật phẩm phải hoàn tất commit ACID (Trừ Điểm D + Thêm Phiếu vào Túi Đồ) TRƯỚC HOẶC HOÀN TOÀN ĐỘC LẬP với hiệu ứng đồ họa (Animation). Nếu ứng dụng bị tắt đột ngột/sập nguồn khi animation đang chạy, sau khi khởi động lại Điểm D đã trừ và Phiếu vẫn phải tồn tại trong Túi Đồ.
- **Hiện trạng Codebase:** `MISSING (0%)`.

### 15. INV-001: Túi Đồ Dung Lượng Vô Hạn & Xếp Chồng Vật Phẩm
- **Yêu cầu SSOT:** Dung lượng túi đồ là vô hạn. Không bao giờ từ chối nhận vật phẩm vì lý do đầy túi. Xếp chồng (stack) theo loại vật phẩm VÀ cùng trạng thái hết hạn. Khi kích hoạt vé: ưu tiên vé có hạn gần nhất; cộng dồn +24h mỗi vé. Cảnh báo vé sắp hết hạn (<= 3 ngày). Tự động dọn dẹp vé hết hạn.
- **Hiện trạng Codebase:** `MISSING (0%)`.

### 16. INV-002: Cơ Chế Phiếu Thông Hành 15 Ngày & Neo Mốc Xóa Đầu Tiên
- **Yêu cầu SSOT:** Khi xóa một ứng dụng khỏi Bảo Khố, toàn bộ Phiếu Thông Hành chưa dùng của ứng dụng đó được chuyển sang trạng thái đếm ngược 15 ngày. Mốc hết hạn (Expiry Anchor) được **NEO CỐ ĐỊNH VÀO LẦN XÓA ĐẦU TIÊN**. Nếu thêm lại rồi xóa lần 2, mốc hết hạn gốc vẫn được giữ nguyên.
- **Hiện trạng Codebase:** `MISSING (0%)`.

### 17. AI-001: Trợ Lý AI — Thực Thi Ý Đồ & Phân Tích Linh Hoạt
- **Yêu cầu SSOT:** Thực thi ý đồ tự nhiên của chủ nhân (Intent Execution). Tự động phân tích tham số thiếu, đề xuất kế hoạch động (Dynamic Plan). Hỗ trợ lệnh Stop / Cancel nguyên tử từng bước, không tự ý khôi phục tác vụ dở dang.
- **Hiện trạng Codebase:** `MISSING (0%)`.

### 18. AI-002: Xử Lý Lỗi, Tự Sửa Lỗi (Self-Repair) & Privacy Guard
- **Yêu cầu SSOT:** Retry tối đa 3 lần với exponential backoff. Tuyệt đối không báo thành công giả tạo (False Success). Tự động sửa lỗi và ghi log sửa chữa. Kiến trúc hybrid Local/Cloud. Privacy Guard: Tuyệt đối không gửi Device ID, IMEI, MAC, danh sách đầy đủ app đã cài đặt ra ngoài Cloud nếu chưa có lệnh rõ ràng.
- **Hiện trạng Codebase:** `MISSING (0%)`.

### 19. SAFE-001: Nguyên Tắc 100% Offline Cho Các Tác Vụ Cốt Lõi
- **Yêu cầu SSOT:** Các tính năng cốt lõi bắt buộc chạy 100% Offline: Thực thi phong ấn (Lock Engine), Cơ sở dữ liệu SQLite, Tính toán thời gian & Chu kỳ 04:00.
- **Hiện trạng Codebase:** `PARTIALLY_IMPLEMENTED`.
- **Bằng chứng mã nguồn:** Hiện tại toàn bộ mã nguồn không có kết nối mạng nào (Offline tự nhiên do chưa tích hợp Cloud). Tuy nhiên chưa có lớp Policy Guard để kiểm soát luồng dữ liệu khi bổ sung AI Cloud.

### 20. MEM-001: Hệ Thống Ký Ức & Cụm Từ Xác Nhận Nghiêm Ngặt
- **Yêu cầu SSOT:**
  + Ký ức phiên (Session Memory): Lưu trên RAM, tự động giải phóng khi đóng app, không tự động thăng cấp thành dài hạn.
  + Ký ức dài hạn (Long-term Memory): Chỉ ghi nhận khi có lệnh rõ ràng, mặc định 7 ngày, tự gia hạn nếu hữu ích, tối đa 6 tháng.
  + Giao diện quản lý ký ức: Tìm kiếm, xem, sửa, xóa từng mục. Xóa toàn bộ bắt buộc phải nhập CHÍNH XÁC 100% cụm từ: `XÓA TẤT CẢ`. Sai một ký tự hoặc khoảng trắng đều phải từ chối.
- **Hiện trạng Codebase:** `MISSING (0%)`.

## 7. Business Rule Drift

Phần này phân tích chi tiết các điểm mà mã nguồn hiện tại đã \"trôi dạt\" hoặc tự ý thay đổi quy tắc so với MASTER SSOT:

### 7.1. Sai lệch Công Thức Mở Khóa 2/3 (N=2 Đặc Xá)
- **SSOT Authority (Phần 1, Đoạn 14 & Phần 4):**
  Quy tắc 2/3 chuẩn hóa: Với `N` nhiệm vụ liên kết hiệu lực, số nhiệm vụ cần hoàn thành là `ceil(2*N / 3)`. Riêng `N = 2`, áp dụng quy tắc \"Đặc xá khởi đầu\": chỉ yêu cầu hoàn thành **1/2 nhiệm vụ** để giảm áp lực tâm lý ban đầu cho người tu luyện. Bảng chuẩn: `N=1 -> 1; N=2 -> 1; N=3 -> 2; N=4 -> 3; N=5 -> 4; N=6 -> 4`.
- **Codebase hiện tại (`domain/policy/TaskUnlockPolicy.kt:51-54`):**
  ```kotlin
  fun calculateRequiredTasks(n: Int): Int {
      if (n <= 0) return 0
      return (2 * n + 2) / 3
  }
  ```
  Khi `N = 2`: `(2 * 2 + 2) / 3 = 6 / 3 = 2`.
- **Kết luận:** Mã nguồn hiện tại ép buộc người dùng phải hoàn thành 100% nhiệm vụ khi N=2, triệt tiêu hoàn toàn đặc xá khởi đầu của SSOT. Đây là **DEFECT NGHIỆP VỤ NGHIÊM TRỌNG**.

### 7.2. Sai lệch Trạng Thái Ứng Dụng Mới Thêm Vào Bảo Khố (N=0)
- **SSOT Authority (Phần 1 & Phần 6):**
  *\"Ứng dụng mới thêm vào Bảo Khố có trạng thái MỞ KHÓA (Unlocked). Ứng dụng chỉ bị phong ấn nếu và chỉ nếu: Có ít nhất 1 nhiệm vụ liên kết chưa hoàn thành trong chu kỳ hiện tại VÀ không có Phiếu Thông Hành hiệu lực.\"*
- **Codebase hiện tại (`TaskAppEnforcementAdapter.kt:451-456`):**
  ```kotlin
  BusinessUnlockDecision.NO_LINKED_TASKS,
  BusinessUnlockDecision.NOT_APPLICABLE,
  @Suppress("DEPRECATION") BusinessUnlockDecision.PENDING_OPEN_01 -> {
      EnforcementAction.LOCK to EnforcementReason.LOCKED_BY_VAULT_NO_TASK
  }
  ```
- **Kết luận:** Mã nguồn tự ý định nghĩa: App thuộc Bảo Khố mà chưa gán nhiệm vụ (`N = 0`) thì bị KHÓA NGAY LẬP TỨC (`LOCKED_BY_VAULT_NO_TASK`). Người dùng vừa đưa app vào Bảo Khố chưa kịp gán nhiệm vụ đã bị chặn đứng. Đây là sự **ĐẢO NGƯỢC LOGIC CỐT LÕI**.

### 7.3. Quyền Ưu Tiên Kỹ Thuật Lấn Át Nghiệp Vụ (Technical Precedence Drift)
- **SSOT Authority:**
  Phong ấn được định nghĩa duy nhất bằng cơ chế: Nhiệm Vụ Liên Kết Chưa Hoàn Thành + Phiếu Thông Hành 24h. Không có khái niệm khóa theo giờ cố định hay giới hạn số phút dùng trong ngày.
- **Codebase hiện tại (`TaskAppEnforcementAdapter.kt:423-440`):**
  ```kotlin
  // 2. Technical Lock has SUPREME PRECEDENCE: never bypassed by any business unlock
  if (isTechnicalLockActive) {
      return AppEnforcementDetails(
          ...
          finalAction = EnforcementAction.LOCK,
          reason = EnforcementReason.LOCKED_BY_POLICY
      )
  }
  ```
- **Kết luận:** Mã nguồn POC cũ đang nắm \"quyền sinh quyền sát\" tối cao, có thể khóa ứng dụng bất kỳ lúc nào dù người dùng đã hoàn thành đủ nhiệm vụ hoặc đã kích hoạt Phiếu Thông Hành.

### 7.4. Đột Biến Liên Kết Nhiệm Vụ Có Hiệu Lực Tức Thì
- **SSOT Authority (Phần 5):**
  Mọi thay đổi cấu hình gán app phần thưởng của nhiệm vụ trong chu kỳ hiện tại phải được xếp vào hàng chờ (`pending_next_cycle_reward_config`). Chu kỳ hiện tại giữ nguyên liên kết cũ để tránh việc người dùng gian lận tháo gỡ app đang bị khóa. Cấu hình mới chỉ có hiệu lực tại 04:00 sáng hôm sau.
- **Codebase hiện tại (`CoreDataRepositoryImpl.kt:107-119`):**
  Hàm `syncTaskLinkedApps()` xóa ngay lập tức các dòng cũ trong `task_app_cross_ref` và chèn liên kết mới. Adapter recompute lại snapshot và thay đổi trạng thái phong ấn ngay tức khắc.
- **Kết luận:** Thiếu cơ chế bảo vệ tính toàn vẹn chu kỳ; vi phạm nguyên tắc bất biến trong ngày của SSOT.

---

## 8. Legacy / Dead / Duplicate Code

### 8.1. Khối Mã Nguồn Thử Nghiệm Kỹ Thuật Cũ (Legacy Technical POC)
Các package và class sau đây được xây dựng từ các Phase 06-10 của POC ban đầu nhằm kiểm tra khả năng khóa app theo thời gian. Chúng hoàn toàn không có vị trí trong MASTER SSOT:
1. `com.example.selfdisciplinepoc01.policy.PolicyEngine`: Cỗ máy đánh giá schedule và daily limit.
2. `com.example.selfdisciplinepoc01.policy.ScheduleEvaluator`: Đánh giá khung giờ khóa cố định.
3. `com.example.selfdisciplinepoc01.policy.ScheduleWatcher` / `ScheduleWatcherImpl`: Lắng nghe ranh giới khung giờ.
4. `com.example.selfdisciplinepoc01.usage.UsageTracker` / `UsageTrackerImpl`: Đếm thời gian dùng app bằng DataStore.
5. `com.example.selfdisciplinepoc01.usage.UsageLimitWatcher` / `UsageLimitWatcherImpl`: Đếm giờ khóa khi hết hạn mức ngày.
6. `com.example.selfdisciplinepoc01.target.*`: Toàn bộ package quản lý `LockedApp`, `TimeSchedule`, `TimeLimit`.
7. `MainActivity.kt:232-414`: Toàn bộ màn hình `ForegroundDetectorScreen` (Tab 2 - Quản Trị Thực Thi).

**Khuyến nghị xử lý:** **REWORK / DELETE**. Cần loại bỏ hoàn toàn các lớp này khỏi luồng đánh giá của `AppDetectorAccessibilityService` để trả quyền lực về cho SSOT Lock Engine.

### 8.2. Dữ Liệu & Khái Niệm Bị Trùng Lặp (Duplicated Concepts)
- **Quản lý ứng dụng bị khóa:** Hiện có 2 danh sách song song:
  + `DataStore Preferences`: Lưu danh sách packages trong `TargetRepositoryImpl`.
  + `Room Database (AppEntity)`: Lưu danh sách packages trong `vault_apps`.
  Hai danh sách này độc lập với nhau, gây ra tình trạng một app có thể bị khóa ở tầng Target nhưng không có trong Bảo Khố, hoặc ngược lại!

### 8.3. Báo Cáo Markdown Cũ (Legacy Phase Reports)
Tại thư mục gốc của repository có 22 file báo cáo từ `PHASE_07A_REPORT.md` đến `PHASE_27_REPORT.md`. Các file này là tài liệu lịch sử tiến trình của POC, không gây hại cho runtime nhưng cần được gom vào thư mục lưu trữ (`docs/legacy_reports/`) để tránh gây nhiễu cho các phase phát triển chính thức.

---

## 9. Test Audit

### 9.1. Tổng Quan Bộ Kiểm Thử Hiện Tại
Codebase sở hữu một bộ kiểm thử đồ sộ:
- **Tổng số file test:** 27 files trong `app/src/test/` và 1 file trong `app/src/androidTest/`.
- **Tổng số test cases thực thi:** **359 unit tests** (trong môi trường Debug test) và lên tới **574 lượt test** (khi chạy toàn bộ các cấu hình).
- **Tỷ lệ vượt qua (Pass rate):** **100% PASS (0 Failure, 0 Error, 0 Skipped)**.
- **Thời gian chạy:** ~7.96 giây.

### 9.2. Phân Tích Hiện Tượng Ảo Giác Kiểm Thử (False Confidence)
Một bài kiểm tra đạt (PASS) chỉ chứng minh rằng mã nguồn thỏa mãn giả định của người viết test, **KHÔNG CHỨNG MINH RẰNG MÃ NGUỒN TUÂN THỦ MASTER SSOT**.

Kiểm tra chi tiết file `app/src/test/java/com/example/selfdisciplinepoc01/domain/policy/TaskUnlockPolicyTest.kt`:
```kotlin
// Line 31-33: Khẳng định sai lầm N=0 phải khóa
val res0 = TaskUnlockPolicy.evaluate(activeTaskCount = 0, completedTaskCount = 0)
assertFalse("N=0 must never unlock", res0.isUnlocked)
assertEquals(BusinessUnlockDecision.NO_LINKED_TASKS, res0.decision)

// Line 39: Khẳng định sai lầm N=2 cần 2 task
assertEquals(2, TaskUnlockPolicy.calculateRequiredTasks(2))
```
- **Hậu quả:** Bài test trên pass 100%, nhưng nó đang **bảo vệ một lỗi nghiệp vụ nghiêm trọng**. Bất kỳ ai nhìn vào báo cáo `359 tests passed` cũng sẽ lầm tưởng hệ thống đã chuẩn mực, trong khi thực tế nó đang vi phạm trực tiếp các điều khoản cốt lõi của MASTER SSOT!
- Tương tự, `ProductFlowValidationTest.kt` và `CoreEnforcementIntegrationAuditTest.kt` liên tục kiểm thử luồng gán app với giả định N=2 cần 2 nhiệm vụ và N=0 bị khóa.

### 9.3. Vùng Trống Kiểm Thử Cực Lớn (100% Missing Tests)
Hoàn toàn không có bất kỳ bài test nào cho các tính năng:
1. Tháp Tu Luyện (Tính bất biến tầng đã qua, thưởng Điểm D chẵn/lẻ, Exception/Propagate).
2. Phó Bản (Cơ chế thưởng 1D, trùng lặp loot).
3. Tiệm & Điểm D (Giao dịch ACID trừ điểm trước khi chạy animation, tính toàn vẹn khi sập nguồn).
4. Túi Đồ & Phiếu 15 Ngày (Neo mốc hết hạn vào lần xóa đầu tiên, cộng dồn +24h, cảnh báo hết hạn).
5. Trợ Lý AI (Phân tích ý đồ, dừng/hủy nguyên tử, Privacy Guard).
6. Giọng Nói & Barge-In (Cắt lời tức thì, timeout 4 giây kết thúc câu thoại dở dang).
7. Ký Ức (Xác nhận nghiêm ngặt cụm từ `XÓA TẤT CẢ`).
8. Chu kỳ 04:00 (Hành vi thu hồi quyền và đưa về Home khi foreground app bước qua ranh giới 04:00:00).

---

## 10. Database Audit

### 10.1. Cấu Trúc Schema Hiện Tại (`self_discipline_core.db`)
Database Room phiên bản 1 hiện có 4 bảng:
1. `vault_apps` (`AppEntity`):
   - `packageName` (TEXT, PK)
   - `appName` (TEXT)
   - `isVaultManaged` (INTEGER/BOOLEAN)
   - `createdAtWallMillis` (INTEGER)
2. `mission_tasks` (`TaskEntity`):
   - `id` (INTEGER, PK, autoGenerate = true)
   - `name` (TEXT)
   - `createdAtWallMillis` (INTEGER)
   - `isArchived` (INTEGER/BOOLEAN)
3. `task_app_cross_ref` (`TaskAppCrossRef`):
   - `taskId` (INTEGER, PK, FK -> mission_tasks.id CASCADE)
   - `appPackageName` (TEXT, PK, FK -> vault_apps.packageName CASCADE)
   - `linkedAtWallMillis` (INTEGER)
4. `daily_task_completions` (`DailyTaskCompletionEntity`):
   - `taskId` (INTEGER, PK, FK -> mission_tasks.id CASCADE)
   - `businessDate` (TEXT, PK, định dạng "yyyy-MM-dd")
   - `isCompleted` (INTEGER/BOOLEAN)
   - `completedAtWallMillis` (INTEGER)

### 10.2. Đánh Giá Pháp Y Schema
- **Điểm sáng kỹ thuật:**
  + Khóa ngoại và ràng buộc toàn vẹn: Sử dụng `ForeignKey.CASCADE` rất tốt. Khi xóa một `TaskEntity`, các bản ghi trong `task_app_cross_ref` và `daily_task_completions` tự động bị dọn dẹp. Khi xóa một `AppEntity`, liên kết trong `task_app_cross_ref` tự động bị gỡ.
  + Chỉ mục (Indices): Có đánh index cho các cột tra cứu (`businessDate`, `taskId`, `appPackageName`).
  + Mô hình hoàn thành theo ngày: Bảng `daily_task_completions` lưu completion theo cặp `(taskId, businessDate)`. Điều này giúp việc chuyển chu kỳ 04:00 diễn ra tự nhiên mà không cần chạy câu lệnh `UPDATE` toàn bộ bảng `mission_tasks` từ `COMPLETED -> PENDING`.
- **Khuyết tật & Giới hạn chết người:**
  + **Mất sạch dữ liệu lịch sử khi xóa task:** Trong `CoreDataRepositoryImpl.kt:64-68`:
    ```kotlin
    override suspend fun deleteTask(taskId: Long) {
        completionDao.deleteCompletionsForTask(taskId)
        crossRefDao.deleteByTaskId(taskId)
        taskDao.deleteTaskById(taskId)
    }
    ```
    Khi người dùng xóa nhiệm vụ, hệ thống xóa sạch toàn bộ lịch sử hoàn thành trong quá khứ của nhiệm vụ đó! Điều này vi phạm nghiêm trọng tính toàn vẹn dữ liệu tu luyện.
  + **Không thể biểu diễn Chu kỳ tuần tự (Cycle Identity):** Không có bảng `cycles` (chỉ có chuỗi string ngày `yyyy-MM-dd`), không có số thứ tự chu kỳ tăng dần.
  + **Không thể biểu diễn Pending Next-Cycle:** `task_app_cross_ref` chỉ có trạng thái tức thời, không có cờ đánh dấu liên kết đang chờ kích hoạt ở chu kỳ tiếp theo.
  + **Thiếu hoàn toàn 8 bảng dữ liệu của SSOT:**
    * Bảng Tháp: `tower_floors` (floor_number, mode, challenge_desc, is_completed, completed_at, is_immutable).
    * Bảng Phó Bản: `dungeon_history` (id, date, completed_at, reward_d_points).
    * Bảng Điểm D: `user_cultivation_profile` (d_points_balance, total_accumulated_d).
    * Bảng Tiệm & Giao dịch: `shop_transactions` (id, item_type, cost_d, created_at, status).
    * Bảng Túi Đồ & Phiếu Thông Hành: `inventory_items` (id, package_name, item_type, duration_hours, expiry_anchor_millis, is_used, activated_at).
    * Bảng Ký Ức: `memory_records` (id, type [SESSION/LONG_TERM], content, created_at, expires_at).
    * Bảng Kế Hoạch AI: `ai_plans` (id, intent, status, steps_json, created_at).
    * Bảng Nhật Ký Sửa Lỗi: `self_repair_logs` (id, error_desc, repair_action, created_at).

---

## 11. Android Enforcement Audit

### 11.1. Dịch Vụ Trợ Năng (`AppDetectorAccessibilityService`)
- Lắng nghe `TYPE_WINDOW_STATE_CHANGED` qua cấu hình `accessibility_service_config.xml`:
  ```xml
  android:accessibilityEventTypes="typeWindowStateChanged"
  android:canRetrieveWindowContent="false"
  android:notificationTimeout="100"
  ```
- **Ưu điểm:** Độ trễ nhận diện cực thấp (< 5ms), không vi phạm quyền riêng tư của người dùng (không đọc nội dung trên màn hình).
- **Hạn chế:** Chỉ kích hoạt khi có sự kiện thay đổi cửa sổ. Nếu người dùng không chuyển cửa sổ mà chỉ ngồi im trong ứng dụng qua ranh giới 04:00, dịch vụ hoàn toàn không nhận được sự kiện để kích hoạt phong ấn.

### 11.2. Cơ Chế Tấm Chắn Đón Đầu (`BlockingShieldOverlay`)
- Sử dụng `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY` với các cờ `FLAG_LAYOUT_IN_SCREEN`, `FLAG_LAYOUT_NO_LIMITS`, `FLAG_NOT_FOCUSABLE`.
- **Ưu điểm vượt trội:** Loại overlay này được cấp quyền tự nhiên thông qua AccessibilityService mà **không cần người dùng cấp quyền `SYSTEM_ALERT_WINDOW`** (Draw over other apps). Tấm chắn có thể hiển thị trong vòng 1-3ms sau khi có quyết định khóa, chặn đứng hoàn toàn hiện tượng nhấp nháy lộ nội dung ứng dụng đích (0% visual leak).
- **Điểm cần nâng cấp:** Hiện tại `BlockingShieldOverlay` chỉ là một tấm view màu trắng đơn giản (`Color.WHITE`). Cần biến nó thành khung chứa giao diện tương tác cho System Panel và AI Orb.

### 11.3. Khởi Chạy Màn Hình Khóa (`LockScreenActivity`)
- Khi có quyết định khóa, dịch vụ gọi `startActivity(LockScreenActivity)` kèm các cờ `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_SINGLE_TOP`.
- **Rủi ro trên Android 14/15:** Google siết chặt BAL (Background Activity Launch). Mặc dù quyền Accessibility cho phép vượt rào, việc khởi chạy Activity toàn màn hình tạo ra độ trễ khởi động khung hình đầu tiên (15ms - 45ms) và gây xung đột với ngăn xếp ứng dụng (Back stack).
- **Giải pháp chuẩn SSOT:** Thay thế hoàn toàn `LockScreenActivity` bằng một **System Panel Overlay thực thụ** hiển thị trực tiếp qua WindowManager từ Accessibility Service. Khi người dùng bấm nút Back, dịch vụ gọi `performGlobalAction(GLOBAL_ACTION_HOME)` để đưa người dùng về Home Android một cách mượt mà và an toàn 100%.

---

## 12. UI / UX Audit

### 12.1. Bản Sắc Thiết Kế Tiên Hiệp (Cultivation Aesthetics)
- Điểm đánh giá: **XUẤT SẮC (HIGH QUALITY)**.
- Hệ thống design tokens tại `ui/design/theme/` thể hiện sự đầu tư bài bản:
  + Tông màu Huyền Hắc Tối sâu thẳm (`background = 0xFF0D0F12`), điểm xuyết ánh Kim Quang Hoàng Kim (`celestialGold = 0xFFFFD700`) và Khí Thanh Lam (`spiritTeal = 0xFF00E5FF`).
  + Các thẻ bài bo góc mềm mại, viền rune phát sáng tinh tế khi ở trạng thái kích hoạt.
  + Ngôn ngữ xưng hô trong giao diện sử dụng chuẩn mực: \"Ký chủ\", \"Nhiệm Vụ Đường\", \"Bảo Khố\", \"Pháp bảo\".

### 12.2. Khiếm Khuyết Trải Nghiệm Người Dùng So Với SSOT
1. **Thiếu Cây Điều Hướng 9 Màn Hình:**
   Giao diện hiện tại chỉ có 3 tab ở thanh điều hướng đáy: `Nhiệm Vụ Đường`, `Bảo Khố`, và `Quản Trị Thực Thi` (chứa các thiết lập kỹ thuật thừa thãi). Hoàn toàn thiếu:
   - Màn hình chính (Home): Nơi ngự trị của AI Orb phát sáng theo nhịp thở.
   - Túi Đồ (Inventory): Quản lý phiếu thông hành, vật phẩm.
   - Tu Luyện (Cultivation): Lựa chọn Tháp Tu Luyện hoặc Phó Bản.
   - Tiệm (Shop): Nơi mua bán phiếu thông hành và vật phẩm tu luyện.
   - Ký Ức (Memory): Quản lý và tra cứu trí nhớ của AI.
   - Cài Đặt (Settings): Thiết lập hệ thống, âm thanh, giọng nói.
2. **Thiếu System Panel & AI Orb:**
   - Màn hình khóa hiện tại (`LockScreenActivity.kt`) là giao diện phong cách Material 3 phẳng lì màu đỏ cảnh báo (`0xFFC62828`), lạc lõng hoàn toàn với phong cách Tiên Hiệp của ứng dụng chính.
   - Hoàn toàn không có hình bóng của Quả Cầu Linh Hồn AI (AI Orb) phát sáng/nhấp nháy.
   - Không có danh sách các nhiệm vụ đang liên kết với app bị khóa để người dùng tự giác hoàn thành ngay tại chỗ.

---

## 13. AI Audit

### 13.1. Hiện Trạng Triển Khai
- **Mức độ hoàn thiện:** **0% (MISSING)**.
- Không có bất kỳ gói mã nguồn, interface, repository, hay client SDK nào liên quan đến AI (như Google GenAI SDK, Gemini API, OpenAI, local ONNX/TFLite).

### 13.2. Yêu Cầu Cần Chuẩn Bị Cho Kiến Trúc AI (SSOT AI-001 & AI-002)
Khi tái thiết lập module AI, kiến trúc phải bảo đảm:
1. **Intent Execution & Dynamic Planning:** Phân tích câu lệnh ngôn ngữ tự nhiên của chủ nhân thành các kế hoạch thực thi nguyên tử (Dynamic Plan) với các bước rõ ràng.
2. **Cơ chế Dừng/Hủy Nguyên Tử (Stop/Cancel):** Hủy ngay bước đang chạy, hoàn tác giao dịch nếu có thể, không tự ý tiếp tục chạy ngầm.
3. **Chế độ Tự Quyết Định:** Khi chủ nhân nói *\"Ngươi tự quyết định đi\"*, AI mới được quyền tự chọn tham số hợp lý nhất dựa trên ngữ cảnh tu luyện.
4. **Tự Sửa Lỗi (Self-Repair):** Khi gặp lỗi phân tích hoặc lỗi gọi công cụ, thử lại tối đa 3 lần với exponential backoff. Ghi nhận nhật ký tự sửa chữa vào bảng `self_repair_logs`.
5. **Vệ Sĩ Riêng Tư (Privacy Guard):** Lọc sạch dữ liệu nhạy cảm của thiết bị (Device ID, IMEI, MAC, danh sách toàn bộ app) trước khi gửi prompt lên Cloud. Chỉ gửi tên nhiệm vụ, thông số tu luyện và câu lệnh đã ẩn danh.

---

## 14. Voice / Barge-In Audit

### 14.1. Hiện Trạng Triển Khai
- **Mức độ hoàn thiện:** **0% (MISSING)**.
- Chưa có mã nguồn thu âm (`AudioRecord`), nhận diện giọng nói (STT), tổng hợp giọng nói (TTS), hay phát hiện giọng nói (VAD).

### 14.2. Yêu Cầu Đặc Tả SSOT Cần Ghi Nhận
1. **Âm Sắc Giọng Nói:** Giọng thiếu nữ anime trẻ trung, trong trẻo, đáng yêu nhưng hơi tinh quái, có độ vang vọng nhẹ (slight echo) như đang truyền âm trong thức hải.
2. **Cắt Lời Tuyệt Đối (Barge-In):** Khi AI đang phát âm qua TTS, nếu VAD phát hiện giọng nói của chủ nhân, hệ thống phải **NGẮT ÂM THANH TTS NGAY LẬP TỨC TRONG VÒNG < 50MS** để lắng nghe chủ nhân.
3. **Timeout 4 Giây Kết Thúc Câu Thoại Dở Dang (FINAL RULE):** Khi chủ nhân đang nói nhưng ngừng lại giữa chừng quá 4.0 giây, hệ thống tự động coi câu thoại đã kết thúc, chèn văn bản đã nhận diện vào khung chat và tắt micro. Đây là quy tắc dứt khoát, không phải quyết định mở.
4. **OPEN-002 (Thời gian chờ im lặng của phiên thoại):** SSOT để ngỏ thời gian timeout của toàn phiên thoại (10 giây, 15 giây hay 30 giây). **GIỮ NGUYÊN TRẠNG THÁI MỞ, KHÔNG TỰ QUYẾT ĐỊNH.**

## 15. Memory Audit

### 15.1. Hiện Trạng Triển Khai
- **Mức độ hoàn thiện:** **0% (MISSING)**.
- Không có bất kỳ bảng dữ liệu, lớp bộ nhớ cache RAM hay cơ chế lưu trữ bền vững nào cho Ký ức (Memory).

### 15.2. Yêu Cầu Cốt Lõi Của SSOT Cần Hiện Thực Hóa
1. **Phân Tách Bạch Ký Ức Phiên và Dài Hạn:**
   - **Ký ức phiên (Session Memory):** Phải được lưu trữ thuần túy trên bộ nhớ RAM (`in-memory`). Tuyệt đối không ghi xuống đĩa. Khi ứng dụng bị tắt hoàn toàn hoặc người dùng thoát phiên, bộ nhớ này phải được giải phóng 100%. Tuyệt đối không tự động chuyển hóa thành ký ức dài hạn.
   - **Ký ức dài hạn (Long-term Memory):** Chỉ được ghi nhận vào cơ sở dữ liệu SQLite bền vững khi có mệnh lệnh rõ ràng từ chủ nhân (ví dụ: *\"Hãy ghi nhớ rằng ta dị ứng với việc thức khuya\"*). Thời hạn lưu trữ mặc định là 7 ngày; tự động gia hạn nếu tiếp tục hữu ích; tối đa 6 tháng phải hỏi lại ý kiến chủ nhân.
2. **Cơ Chế Bảo Vệ Nghiêm Ngặt Khi Xóa Toàn Bộ:**
   - Trong giao diện quản lý ký ức, người dùng có thể tra cứu, xem, chỉnh sửa và xóa từng mục độc lập.
   - Tuy nhiên, nếu muốn xóa toàn bộ kho ký ức dài hạn, hệ thống **BẮT BUỘC PHẢI YÊU CẦU NGƯỜI DÙNG NHẬP CHÍNH XÁC 100% CỤM TỪ:**
     ```
     XÓA TẤT CẢ
     ```
   - Nếu nhập sai dù chỉ 1 ký tự, sai chữ hoa/thường, hoặc thừa khoảng trắng, hệ thống phải lập tức từ chối lệnh xóa và phát cảnh báo.
3. **OPEN-003 (Chiến lược đồng bộ Ký ức lên Cloud):**
   - SSOT để ngỏ phương án đồng bộ đám mây (Cloud sync: Không đồng bộ / Đồng bộ có mã hóa E2EE / Đồng bộ phân tán).
   - **TRẠNG THÁI KIỂM ĐỊNH:** **WAIT / BLOCKED BY OPEN DECISION**. Không tự ý lựa chọn phương án trong giai đoạn này.

---

## 16. Dependency Audit

### 16.1. Đánh Giá Danh Sách Thư Viện Hiện Có (`gradle/libs.versions.toml`)
Mã nguồn hiện tại sử dụng các thư viện chính thức:
- `Android Gradle Plugin (AGP): 8.7.3`
- `Kotlin: 2.0.21` & `Compose Compiler: 2.0.21` & `KSP: 2.0.21-1.0.27`
- `Jetpack Compose BOM: 2024.10.01` (UI, Tooling, Material 3)
- `AndroidX Core KTX: 1.15.0` & `Lifecycle: 2.8.7` & `Activity Compose: 1.9.3`
- `Room Database: 2.6.1` (`room-runtime`, `room-ktx`, `room-compiler`, `room-testing`)
- `DataStore Preferences: 1.1.1` (`androidx.datastore:datastore-preferences`)
- `Coroutines Test: 1.9.0` & `JUnit: 4.13.2` & `Robolectric: 4.14.1`

### 16.2. Phân Tích Thừa / Thiếu / Xung Đột Phụ Thuộc
1. **Thư viện Dư Thừa (Redundant / Legacy Dependency):**
   - `androidx.datastore:datastore-preferences`: Thư viện này đang phục vụ việc lưu trữ cho Legacy POC (`TargetRepositoryImpl`, `UsageTracker`). Khi chuyển sang chuẩn hóa toàn bộ trên Room Database, DataStore trở thành dư thừa và gây phân mảnh kiến trúc lưu trữ.
2. **Thư viện Thiết Yếu Bị Thiếu Hụt Cho SSOT:**
   - **Tác vụ nền & Lịch biểu:** Thiếu `androidx.work:work-runtime-ktx` (WorkManager) để quản lý các tác vụ nền định kỳ và phục hồi sau khi khởi động lại.
   - **Mã hóa cơ sở dữ liệu:** Thiếu `net.zetetic:android-database-sqlcipher` để bảo vệ cơ sở dữ liệu SQLite 100% an toàn trước nguy cơ trích xuất dữ liệu.
   - **Trợ lý AI:** Thiếu Google GenAI SDK (`com.google.ai.client.generativeai`) hoặc client gọi API Gemini Cloud an toàn.
   - **Âm thanh & Giọng nói:** Thiếu thư viện xử lý VAD (như Silero VAD hoặc WebRTC VAD) và thư viện tổng hợp giọng nói chất lượng cao.
   - **Hoạt họa nâng cao:** Cần bổ sung Lottie (`com.airbnb.android:lottie-compose`) để thể hiện hiệu ứng Quả Cầu Linh Hồn (AI Orb) phát sáng theo nhịp thở mượt mà.

---

## 17. Resource Audit

### 17.1. Đánh Giá Tài Nguyên Android (`app/src/main/res/`)
1. **Strings (`values/strings.xml`):**
   - Chứa 12 chuỗi tài nguyên, chủ yếu phục vụ tên app (`app_name`), nhãn dịch vụ trợ năng (`accessibility_service_label`), và các thông báo trên màn hình khóa cũ:
     + `lock_screen_title`: \"Ứng dụng tạm khóa\"
     + `lock_screen_message`: \"Ứng dụng này đang bị khóa để giúp bạn tập trung...\"
     + `lock_screen_button_home`: \"Về màn hình chính\"
   - Đánh giá: Văn phong trên các chuỗi này mang tính chất parental control/screen time hiện đại, hoàn toàn chưa được khoác lên ngôn ngữ phong cách Tiên Hiệp của SSOT.
2. **Themes & Styles (`values/themes.xml`):**
   - Định nghĩa theme `@style/Theme.SelfDisciplinePoc01.LockScreen` sử dụng `Theme.Material.NoActionBar` với các cờ trong suốt (`android:windowIsTranslucent = true`).
3. **Cấu hình Trợ Năng (`xml/accessibility_service_config.xml`):**
   - Thiết lập chuẩn xác `flagDefault|flagIncludeNotImportantViews`, `notificationTimeout="100"`.
4. **Tài nguyên bị thiếu hụt:**
   - Thiếu các file đồ họa Vector Drawables cho biểu tượng các phân khu SSOT (Tháp, Phó bản, Tiệm, Túi đồ, Ký ức).
   - Thiếu file âm thanh báo hiệu khi hoàn thành nhiệm vụ hay âm thanh truyền âm của AI.

---

## 18. Security / Privacy Risks

### 18.1. Rủi Ro Bảo Mật Dữ Liệu Lưu Trữ Cục Bộ (Local Storage Exposure)
- **Bằng chứng:** File `AppDatabase.kt:46-51` xây dựng database SQLite Room mặc định không có mã hóa, lưu tại `/data/data/com.example.selfdisciplinepoc01/databases/self_discipline_core.db`.
- **Rủi ro:** Trên các thiết bị đã root hoặc thông qua khai thác lỗ hổng hệ thống, toàn bộ danh sách nhiệm vụ tự kỷ luật, ứng dụng bị phong ấn và thói quen sinh hoạt của người dùng có thể bị đọc trộm dưới dạng plain text.
- **Biện pháp khắc phục trong Canonical Rebuild:** Tích hợp `SQLCipher` và khóa mã hóa lưu trữ an toàn trong `Android KeyStore`.

### 18.2. Lỗ Hổng Sao Lưu Ứng Dụng (Application Backup Vulnerability)
- **Bằng chứng:** `app/src/main/AndroidManifest.xml:12`:
  ```xml
  <application
      android:allowBackup="true"
  ```
- **Rủi ro:** Cho phép người dùng hoặc kẻ xấu kết nối máy tính qua USB và dùng lệnh `adb backup` để trích xuất toàn bộ dữ liệu ứng dụng, bao gồm database Room và DataStore Preferences, từ đó có thể chỉnh sửa dữ liệu để \"lách luật\" phong ấn.
- **Biện pháp khắc phục:** Bắt buộc đặt `android:allowBackup="false"`.

### 18.3. Bảo Vệ Quyền Riêng Tư (Privacy Guard Compliance)
- **Yêu cầu SSOT (SAFE-001 & AI-002):** Tuyệt đối không gửi Device ID, IMEI, MAC, danh sách đầy đủ ứng dụng đã cài đặt ra ngoài Cloud nếu không có sự đồng ý rõ ràng của chủ nhân.
- **Hiện trạng:** Hiện tại app chưa có kết nối mạng nên không vi phạm. Tuy nhiên, khi xây dựng module AI tích hợp Gemini Cloud, bắt buộc phải có một tầng lọc dữ liệu trung gian (`PrivacyGuardFilter`) để kiểm duyệt mọi payload gửi đi.

---

## 19. Architectural Risks

### 19.1. Rủi Ro Xung Đột Hai Cỗ Máy Trạng Thái (Dual State Machine Conflict)
- Hiện tại, `TaskAppEnforcementAdapter` vừa phải tôn trọng phán quyết của `PolicyEngine` (Legacy), vừa phải tính toán quy tắc của `TaskUnlockPolicy`.
- Việc quy định `isTechnicalLockActive` có quyền ưu tiên tối thượng đè bẹp `BusinessUnlockDecision` là một quả bom nổ chậm về mặt logic. Người dùng tu luyện đạt đủ điều kiện mở app theo SSOT nhưng vẫn bị khóa cứng vì các thiết lập giờ giấc của POC cũ.

### 19.2. Rủi Ro Bỏ Sót Ranh Giới Chu Kỳ (Cycle Boundary Failure)
- Do thiếu hoàn toàn `AlarmManager` chính xác từng giây (`setExactAndAllowWhileIdle`), nếu người dùng tắt màn hình đi ngủ lúc 23:00 và thức dậy lúc 07:00 sáng hôm sau, trong khoảng thời gian từ 04:00 đến 07:00 hệ thống ở trạng thái ngủ đông hoàn toàn.
- Nếu người dùng hẹn giờ chuông báo thức bằng một ứng dụng bên thứ ba lúc 04:05, khi ứng dụng đó bật lên, hệ thống mới giật mình tính toán lại, có thể gây ra hiện tượng giật lag hoặc chậm trễ cập nhật trạng thái chu kỳ mới.

### 19.3. Rủi Ro Thắt Chặt Quyền Trên Android 15 (Platform Deprecation Risk)
- Android 15 áp dụng chính sách cực kỳ khắt khe đối với việc gọi `startActivity` từ Accessibility Service khi màn hình đang hiển thị ứng dụng của bên thứ ba.
- Nếu tiếp tục sử dụng kiến trúc khởi chạy `LockScreenActivity`, ứng dụng có nguy cơ bị hệ điều hành chặn đứng (SecurityException hoặc Activity bị đưa vào hàng chờ không hiển thị), dẫn đến việc ứng dụng bị phong ấn vẫn lọt ra ngoài màn hình. Chuyển đổi sang `TYPE_ACCESSIBILITY_OVERLAY` toàn phần là con đường sống còn duy nhất.

---

## 20. Missing Requirements

Dưới đây là danh sách đầy đủ các yêu cầu nghiệp vụ bắt buộc của MASTER SSOT nhưng **hoàn toàn chưa có dòng mã nào trong codebase (0% Implementation)**:

1. **Tháp Tu Luyện (TOWER-001 đến TOWER-004):**
   - Tiến trình vĩnh cửu không reset tại 04:00.
   - Tầng đã vượt trở thành bất biến (immutable), cấm cày cuốc lại.
   - Công thức tầng: `challenge = Start + (i - 1) * Increment`.
   - Phần thưởng Điểm D: Tầng lẻ = 1D; Tầng chẵn = `floor/2 + 1` D.
   - Chế độ Manual Mode và Formula Mode; cơ chế Exception và Propagate.
2. **Phó Bản Tu Luyện (Dungeon):**
   - Phân tách entity và module độc lập hoàn toàn với Nhiệm Vụ Đường.
   - Hoàn thành nhận cố định +1D (DEC-009 Superseded: loại bỏ công thức cũ 1-3-1-3 / 1-2-1-3).
   - Cơ chế nhặt loot ngẫu nhiên và xếp chồng loot trùng lặp.
   - Cơ chế bảo vệ chống double-tap khi bấm hoàn thành phó bản.
3. **Tiệm & Giao Dịch ACID (SHOP-001, SHOP-002):**
   - Quy đổi: 1 Phiếu Thông Hành = 24 giờ mở khóa cho 1 ứng dụng cụ thể.
   - Giá vé mặc định hoặc tùy chỉnh theo ứng dụng.
   - Mua 1 vé không cần xác nhận; mua >= 2 vé bắt buộc có hộp thoại xác nhận.
   - Giao dịch ACID trừ Điểm D và chèn Phiếu vào Túi Đồ TRƯỚC HOẶC ĐỘC LẬP VỚI ANIMATION.
4. **Túi Đồ & Phiếu Thông Hành 15 Ngày (INV-001, INV-002):**
   - Dung lượng túi đồ vô hạn, không từ chối vật phẩm.
   - Neo mốc hết hạn 15 ngày vào LẦN XÓA ĐẦU TIÊN khỏi Bảo Khố.
   - Giữ nguyên mốc hết hạn gốc khi thêm lại rồi xóa lần thứ hai.
   - Cộng dồn +24h khi kích hoạt nhiều vé cho cùng một ứng dụng.
   - Cảnh báo vật phẩm sắp hết hạn (<= 3 ngày).
5. **Trợ Lý AI & Kế Hoạch Động (AI-001, AI-002):**
   - Thực thi ý đồ tự nhiên của chủ nhân (Intent Execution).
   - Đề xuất và hiển thị Kế hoạch động từng bước rõ ràng trước khi chạy.
   - Lệnh Dừng / Hủy nguyên tử, không tự ý chạy ngầm.
   - Chế độ phân quyền khi chủ nhân nói: *\"Ngươi tự quyết định đi\"*.
   - Cơ chế tự sửa lỗi (Self-Repair) tối đa 3 lần và ghi nhật ký sửa lỗi.
   - Vệ sĩ riêng tư (Privacy Guard) lọc sạch định danh thiết bị.
6. **Giao Tiếp Giọng Nói & Barge-In (Voice Engine):**
   - Âm sắc thiếu nữ anime trong trẻo, tinh quái, vang vọng nhẹ.
   - Cơ chế cắt lời lập tức (Barge-In) khi chủ nhân cất giọng.
   - Timeout 4.0 giây kết thúc câu thoại dở dang (FINAL RULE).
7. **Hệ Thống Ký Ức (MEM-001):**
   - Phân biệt Ký ức phiên trên RAM và Ký ức dài hạn trên SQLite.
   - Cơ chế xác nhận xóa toàn bộ bắt buộc nhập chính xác cụm từ: `XÓA TẤT CẢ`.
8. **System Panel & Quả Cầu Linh Hồn (AI Orb):**
   - AI Orb nhấp nháy phát sáng theo nhịp thở ở góc màn hình.
   - Chạm đơn mở System Panel, chạm đúp chuyển nhanh tác vụ.
   - System Panel Overlay toàn màn hình, chặn toàn bộ touch-through, nút Back gọi về Home Android.
   - Nút xác nhận hoàn thành nhiệm vụ tự giác tích hợp ngay trên giao diện ứng dụng bị chặn.

---

## 21. Contradictory Requirements

Bảng tổng hợp các mâu thuẫn trực tiếp giữa Triết lý SSOT và Mã nguồn hiện tại:

| Thành phần | Quy định MASTER SSOT | Mã nguồn Hiện tại | Phân loại mâu thuẫn |
| :--- | :--- | :--- | :--- |
| **Quy tắc mở khóa N=2** | N=2 cần **1 nhiệm vụ** (Đặc xá khởi đầu) | `TaskUnlockPolicy.kt:53` tính `(2*2+2)/3 = 2` | **DIRECT CONFLICT** |
| **Ứng dụng mới vào Vault (N=0)** | Trạng thái **UNLOCKED** (Mở khóa) | `TaskUnlockPolicy.kt:60` trả về **LOCK** (`NO_LINKED_TASKS`) | **DIRECT CONFLICT** |
| **Quyền ưu tiên phong ấn** | Phong ấn bằng Nhiệm vụ & Phiếu thông hành | `TaskAppEnforcementAdapter.kt:423` gán Technical Lock quyền tối thượng | **DIRECT CONFLICT** |
| **Thay đổi liên kết nhiệm vụ** | Chờ kích hoạt tại 04:00 (Pending Next-Cycle) | `CoreDataRepositoryImpl.kt:108` xóa & chèn áp dụng ngay tức thì | **DIRECT CONFLICT** |
| **Vị trí hoàn thành nhiệm vụ** | Chỉ trong System Panel của app bị chặn | `MissionHallScreen.kt:140` có nút hoàn thành độc lập trên màn hình | **DIRECT CONFLICT** |
| **Xóa nhiệm vụ trong chuỗi** | Giữ nguyên lịch sử hoàn thành quá khứ | `CoreDataRepositoryImpl.kt:65` xóa sạch bảng `daily_task_completions` | **DATA INTEGRITY CONFLICT** |
| **Khóa theo giờ & số phút dùng** | **CẤM / KHÔNG CÓ** trong sản phẩm SSOT | Tồn tại nguyên vẹn trong `PolicyEngine`, `ScheduleWatcher`, `UsageLimitWatcher` | **ARCHITECTURAL CONFLICT** |

---

## 22. Open Decisions

Trong MASTER SSOT (v1.0.0), Product Owner đã dứt khoát quy định có **3 quyết định để ngỏ (Open Decisions)**. Dưới đây là hiện trạng kiểm tra đối với 3 quyết định này:

### 1. OPEN-001: Danh Mục Rớt Đồ Phó Bản (Dungeon Loot Pool)
- **Nội dung:** Tỷ lệ rớt đồ, danh sách vật phẩm (Đan dược, Pháp bảo, Mảnh phiếu thông hành) trong phó bản tu luyện chưa được chốt cấu hình cụ thể.
- **Trạng thái Codebase:** `MISSING`.
- **Hành động trong Phase 0B:** **WAIT / BLOCKED BY OPEN DECISION**.
- **Nguyên tắc tối cao:** Tuyệt đối không tự ý bịa đặt tỷ lệ hay tự ý hardcode danh mục vật phẩm trong mã nguồn. Khi xây dựng module Dungeon ở các phase sau, cần thiết kế interface mở (`DungeonLootProvider`) để sẵn sàng nạp cấu hình khi Product Owner ban hành quyết định.

### 2. OPEN-002: Thời Gian Chờ Im Lặng Của Phiên Thoại (Voice Session Silence Timeout)
- **Nội dung:** Thời gian tự động đóng phiên đàm thoại khi chủ nhân hoàn toàn im lặng (10 giây, 15 giây hay 30 giây) vẫn đang chờ thử nghiệm thực tế để chốt.
- **Lưu ý phân biệt:** Timeout 4.0 giây cho câu thoại dở dang (Unfinished Utterance Timeout) đã là **QUY TẮC CHÍNH THỨC (FINAL RULE)**, không phải quyết định mở. Chỉ có thời gian chờ của toàn bộ phiên thoại mới là OPEN-002.
- **Trạng thái Codebase:** `MISSING`.
- **Hành động trong Phase 0B:** **WAIT / BLOCKED BY OPEN DECISION**.
- **Nguyên tắc tối cao:** Không tự ý chốt giá trị cứng. Cần đưa vào cấu hình tham số động (`voice_silence_session_timeout_seconds`).

### 3. OPEN-003: Chiến Lược Đồng Bộ Ký Ức Lên Đám Mây (Cloud Memory Sync Strategy)
- **Nội dung:** Lựa chọn phương án đồng bộ Ký ức dài hạn lên Cloud (Không đồng bộ / Đồng bộ có mã hóa E2EE / Đồng bộ phân tán) chưa có quyết định cuối cùng.
- **Trạng thái Codebase:** `MISSING`.
- **Hành động trong Phase 0B:** **WAIT / BLOCKED BY OPEN DECISION**.
- **Nguyên tắc tối cao:** Mặc định lưu trữ Ký Ức 100% Offline trên SQLite cục bộ. Thiết kế tầng `MemoryRepository` có ranh giới trừu tượng hóa để sẵn sàng tích hợp đồng bộ khi có chỉ đạo chính thức.

## 23. Proposed Canonical Architecture

Dưới đây là đề xuất kiến trúc chuẩn hóa (Canonical Architecture) tuân thủ 100% MASTER SSOT, được thiết kế theo mô hình Clean Architecture kết hợp MVI (Model-View-Intent) trên nền tảng Jetpack Compose:

```
                                  [ USER / UI ]
                                        │
             ┌──────────────────────────┴──────────────────────────┐
             ▼                                                     ▼
     [ MainActivity ]                                     [ System Panel Overlay ]
(9 Màn hình chính SSOT)                                   (AI Orb + Quick Task + Exit Home)
             │                                                     │
             └──────────────────────────┬──────────────────────────┘
                                        ▼
                               [ MVI ViewModels ]
                                        │
                                        ▼
                           [ Domain UseCases / Engines ]
    ┌──────────────┬──────────────┬──────────────┬──────────────┬──────────────┐
    ▼              ▼              ▼              ▼              ▼              ▼
[CycleEngine] [VaultEngine] [TowerEngine]  [ShopEngine]    [AIAgent]     [VoiceEngine]
  (04:00)       (2/3 Rule)  (Odd/Even 1D)  (ACID +24h)   (Dynamic Plan)    (Barge-In)
    │              │              │              │              │              │
    └──────────────┴──────────────┼──────────────┴──────────────┴──────────────┘
                                  ▼
                     [ Core Repositories & Security ]
                                  │
          ┌───────────────────────┴───────────────────────┐
          ▼                                               ▼
[Room Database (SQLCipher)]                     [Android Enforcement Engine]
- mission_tasks (order_index)                   - AppDetectorAccessibilityService
- vault_apps (15-day anchor)                    - WindowManager TYPE_ACCESSIBILITY_OVERLAY
- task_app_pending_refs                         - ExactAlarm (04:00 Wakeup)
- daily_task_completions                        - BootCompletedReceiver (Offline Catch-up)
- tower_floors (immutable)
- shop_transactions
- inventory_items (unlimited)
- memory_records (RAM/Flash)
```

### 23.1. Các Nguyên Tắc Thiết Kế Trụ Cột
1. **Một Nguồn Sự Thật Duy Nhất (Single Source of Truth):** Toàn bộ dữ liệu phong ấn và tu luyện phải do SQLite Room Database quản lý. Loại bỏ hoàn toàn DataStore Preferences khỏi luồng đánh giá phong ấn.
2. **Ưu Tiên Nghiệp Vụ Tuyệt Đối (Business Logic Supremacy):** Trạng thái phong ấn của ứng dụng được quyết định 100% bởi `VaultEngine` và `CycleEngine`. Không có bất kỳ chính sách kỹ thuật nào (như Schedule hay Daily Limit) được quyền đè lên kết quả của SSOT.
3. **Thực Thi Phong Ấn 0ms Qua System Overlay:** Tích hợp trực tiếp System Panel và AI Orb vào `WindowManager` thông qua `TYPE_ACCESSIBILITY_OVERLAY`. Khi phát hiện ứng dụng bị phong ấn, hiển thị ngay System Panel mà không cần khởi chạy Activity.

---

## 24. Proposed Migration Strategy

Quá trình chuyển đổi từ codebase hiện tại sang Canonical Architecture phải được tiến hành theo phương pháp **Tái Thiết Có Kiểm Soát (Controlled Reconstruction)** qua 4 giai đoạn, tuyệt đối không đập đi xây lại một cách hỗn loạn:

### Giai Đoạn 1: Sửa Chữa & Thanh Lọc Lõi Nghiệp Vụ Hiện Có (Core Sanitization)
1. **Sửa lỗi công thức 2/3 trong `TaskUnlockPolicy.kt`:** Cập nhật công thức chuẩn hóa, bổ sung nhánh đặc xá `if (n == 2) return 1`.
2. **Sửa lỗi N=0 trong `TaskUnlockPolicy.kt` và `TaskAppEnforcementAdapter.kt`:** Định nghĩa rõ ràng: Khi `activeTaskCount == 0`, trả về `BusinessUnlockDecision.UNLOCKED` và `EnforcementAction.ALLOW`.
3. **Loại bỏ quyền tối thượng của Technical Lock:** Cắt đứt sự phụ thuộc của `TaskAppEnforcementAdapter` vào `PolicyEngine`.
4. **Cập nhật lại toàn bộ Unit Tests:** Thay thế các assertion sai lệch trong `TaskUnlockPolicyTest`, `ProductFlowValidationTest` để bảo vệ các quy tắc đúng của SSOT.

### Giai Đoạn 2: Tái Thiết Lớp Lưu Trữ & Hạ Tầng Chu Kỳ 04:00 (Persistence & Cycle Foundation)
1. **Nâng cấp Room Database (Version 2 với Migration):**
   - Bổ sung trường `order_index`, `created_cycle` vào `TaskEntity`.
   - Bổ sung bảng `task_app_pending_refs` để lưu cấu hình phần thưởng chờ chu kỳ 04:00 tiếp theo.
   - Thêm trường `first_removal_anchor_millis` vào `AppEntity` để phục vụ quy tắc Phiếu 15 ngày.
   - Tạo mới các thực thể: `TowerFloorEntity`, `DungeonHistoryEntity`, `InventoryItemEntity`, `MemoryEntity`.
2. **Xây dựng Hạ Tầng Chu Kỳ 04:00 (Cycle Engine):**
   - Đăng ký `AlarmManager` với `setExactAndAllowWhileIdle` tại mốc 04:00 hàng ngày.
   - Đăng ký `BroadcastReceiver` cho sự kiện `ACTION_BOOT_COMPLETED`, `ACTION_TIME_CHANGED`, `ACTION_TIMEZONE_CHANGED` để xử lý offline catch-up.
   - Bổ sung cơ chế auto-kick: Nếu người dùng đang mở app bị phong ấn lúc 04:00:00, gọi `performGlobalAction(GLOBAL_ACTION_HOME)` để đưa về Home Android.

### Giai Đoạn 3: Tiến Hóa Màn Hình Khóa Thành System Panel & AI Orb
1. **Chuyển đổi `BlockingShieldOverlay`:** Phát triển từ tấm chắn màu trắng đơn điệu thành một Overlay toàn màn hình có chứa danh sách nhiệm vụ tự giác và AI Orb.
2. **Loại bỏ `LockScreenActivity`:** Khai tử hoàn toàn Activity khóa độc lập, giải quyết dứt điểm các rủi ro BAL trên Android 15.
3. **Cấu trúc lại Navigation 9 Màn Hình:** Xây dựng khung điều hướng chuẩn mực trong `MainActivity.kt` (Home, Mission Hall, Vault, Inventory, Cultivation, Shop, Memory, Settings).

### Giai Đoạn 4: Hiện Thực Hóa Các Trụ Cột SSOT Chưa Có
1. **Triển khai Module Tháp Tu Luyện & Phó Bản (Cultivation Engine).**
2. **Triển khai Module Tiệm & Túi Đồ (Shop & Inventory Engine với giao dịch ACID).**
3. **Triển khai Module Trợ Lý AI & Privacy Guard.**
4. **Triển khai Module Giọng Nói & Barge-In.**
5. **Triển khai Module Ký Ức & Kiểm soát xóa `XÓA TẤT CẢ`.**

---

## 25. Recommended Rebuild Boundary

Dưới đây là bảng phân định ranh giới tái thiết chi tiết cho 15 cỗ máy và thành phần của hệ thống:

| Cỗ máy / Thành phần | Quyết định | Phụ thuộc (Dependencies) | Rủi ro chuyển đổi | Lý do kỹ thuật |
| :--- | :---: | :--- | :--- | :--- |
| **1. Cycle Engine (Mốc 04:00)** | **REBUILD** | `AlarmManager`, `BusinessDayProvider`, `BootReceiver` | MEDIUM | Hiện tại chỉ có class tính giờ; thiếu 100% hạ tầng thức dậy hệ thống |
| **2. Task / Mission Engine** | **ADAPT** | `Room DB`, `CycleEngine` | LOW | Đã có CRUD và luồng tuần tự; chỉ cần thêm trường và chuyển chỗ hoàn thành |
| **3. Vault / Lock Engine** | **REBUILD** | `Room DB`, `CycleEngine`, `TaskUnlockPolicy` | HIGH | Cần xóa bỏ Technical Lock đè bẹp; sửa triệt để công thức N=2 và N=0 |
| **4. Voucher Engine** | **REBUILD** | `Room DB`, `TimeProvider` | MEDIUM | Chưa tồn tại; cần xây mới với cơ chế neo mốc 15 ngày tại lần xóa đầu tiên |
| **5. Inventory Engine** | **REBUILD** | `Room DB`, `VoucherEngine` | LOW | Chưa tồn tại; cần xây mới với túi đồ dung lượng vô hạn và xếp chồng |
| **6. Dungeon Engine** | **REBUILD** | `Room DB`, `OpenDecision OPEN-001` | LOW | Chưa tồn tại; tuân thủ DEC-009 Superseded (+1D cố định); chờ OPEN-001 |
| **7. Tower Engine** | **REBUILD** | `Room DB` | MEDIUM | Chưa tồn tại; cần xây mới với tính bất biến tầng đã qua và thưởng 1D chẵn/lẻ |
| **8. Shop Engine** | **REBUILD** | `Room DB`, `InventoryEngine` | MEDIUM | Chưa tồn tại; cần bảo đảm giao dịch ACID commit trước/độc lập animation |
| **9. Memory Engine** | **REBUILD** | `Room DB`, `OpenDecision OPEN-003` | LOW | Chưa tồn tại; xây mới với phân tách RAM/Flash và chuỗi `XÓA TẤT CẢ` |
| **10. AI Agent Engine** | **REBUILD** | `Gemini SDK`, `PrivacyGuard` | HIGH | Chưa tồn tại; cần xây mới hoàn toàn kiến trúc Dynamic Plan & Stop/Cancel |
| **11. Voice / VAD Engine** | **REBUILD** | `VAD SDK`, `AudioRecord`, `TTS` | HIGH | Chưa tồn tại; cần đảm bảo cắt lời tức thì < 50ms và timeout 4s |
| **12. Android Enforcement** | **ADAPT** | `AccessibilityService`, `WindowManager` | HIGH | Kế thừa cơ chế bắt app và latency shield xuất sắc; loại bỏ startActivity |
| **13. System Panel & AI Orb** | **REBUILD** | `EnforcementEngine`, `Compose` | MEDIUM | Thay thế hoàn toàn LockScreenActivity cũ bằng System Overlay toàn màn hình |
| **14. Persistence Layer** | **REBUILD** | `Room`, `SQLCipher` | MEDIUM | Mở rộng schema từ 4 bảng lên 12 bảng; loại bỏ DataStore Preferences |
| **15. Cultivation Design System**| **RETAIN** | Jetpack Compose | LOW | Palette màu, Typography, Theme tokens đã quá đẹp và chuẩn mực |

---

## 26. KEEP / ADAPT / REWORK / DELETE / UNKNOWN Summary

Tổng hợp kết quả phân loại kiểm định pháp y trên toàn bộ 268 file của repository:

| Trạng thái | Số lượng | Tỷ lệ (%) | Định nghĩa & Nhóm file đại diện |
| :--- | :---: | :---: | :--- |
| **KEEP (Giữ nguyên)** | **37** | 13.8% | Các file chuẩn mực, không vi phạm SSOT: Hệ thống Theme Tiên Hiệp (`CultivationColors.kt`, `CultivationTypography.kt`, `CultivationTheme.kt`), Tiện ích trợ năng (`AccessibilityUtil.kt`), Bộ tính mốc chu kỳ lý thuyết (`BusinessDayProvider.kt`, `BusinessDayProviderImpl.kt`), File cấu hình gốc (`.gitignore`, `AGENTS.md`, v.v.). |
| **ADAPT (Thích ứng)** | **96** | 35.8% | Các file có nền tảng tốt nhưng cần bổ sung logic hoặc sửa đổi giao diện theo SSOT: Dịch vụ trợ năng (`AppDetectorAccessibilityService.kt`), Tấm chắn mờ đục (`BlockingShieldOverlay.kt`), Các UseCase (`AddVaultAppUseCase`, `CreateTaskUseCase`, `CompleteTaskUseCase`), Màn hình (`MissionHallScreen`, `VaultScreen`), Tài nguyên Android (`res/`), Tài liệu phase lịch sử. |
| **REWORK (Làm lại)** | **44** | 16.4% | Các file chứa lỗi nghiệp vụ nghiêm trọng hoặc xung đột kiến trúc cần tái cấu trúc triệt để: Chính sách mở khóa (`TaskUnlockPolicy.kt`), Bộ điều phối phong ấn (`TaskAppEnforcementAdapter.kt`), Màn hình khóa cũ (`LockScreenActivity.kt`), Màn hình chính (`MainActivity.kt`), Cấu hình Room Database (`AppDatabase.kt`, `TaskEntity.kt`), Các bài test khẳng định quy tắc cũ sai lệch (`TaskUnlockPolicyTest.kt`). |
| **DELETE (Xóa bỏ)** | **90** | 33.6% | Các file rác hoặc tàn dư thử nghiệm không thuộc SSOT: Toàn bộ 90 file trong thư mục tạm `scratch/` (cần được dọn sạch trước khi đóng phase), các class Legacy POC kỹ thuật (`ScheduleEvaluator.kt`, `ScheduleWatcher.kt`, `UsageTracker.kt`, `UsageLimitWatcher.kt`, `TargetRepositoryImpl.kt`). |
| **UNKNOWN (Chưa rõ)** | **1** | 0.4% | File script thử nghiệm riêng lẻ cần xác minh thêm trước khi dọn dẹp. |
| **TỔNG CỘNG** | **268** | **100%** | Toàn bộ repository đã được kiểm kê và phân loại không bỏ sót 1 file nào. |

---

## 27. Build / Test Evidence

### 27.1. Bằng Chứng Biên Dịch & Đóng Gói (Build Evidence)
- **Hệ thống Build:** Gradle 8.7.3, Kotlin 2.0.21, Target SDK 35, Java 17.
- **Tình trạng biên dịch:**
  + Mã nguồn hiện tại biên dịch thành công 100% mà không có lỗi cú pháp hay thiếu import.
  + Các gói cài đặt APK đã được xuất xưởng thành công trong `app/build/outputs/apk/`:
    * `app/build/outputs/apk/debug/app-debug.apk`: Kích thước 10,790,358 bytes.
    * `app/build/outputs/apk/release/app-release-signed.apk`: Kích thước 7,108,607 bytes.
    * `app/build/outputs/apk/release/app-release-unsigned.apk`: Kích thước 7,091,208 bytes.

### 27.2. Bằng Chứng Thực Thi Kiểm Thử (Test Execution Evidence)
- **Bộ kiểm thử tự động:** Chạy qua `testDebugUnitTest` (Robolectric 4.14.1 trên môi trường máy ảo JVM).
- **Kết quả tổng hợp từ 40 file báo cáo kết quả XML trong `app/build/test-results/`:**
  + **Tổng số bài kiểm tra thực thi:** **359 tests** (574 lượt test trong các suite).
  + **Số bài thất bại (Failures):** **0**.
  + **Số bài lỗi (Errors):** **0**.
  + **Số bài bỏ qua (Skipped):** **0**.
  + **Tổng thời gian chạy:** 7.96 giây.
- **Bảng chi tiết các Test Classes quan trọng:**
  * `com.example.selfdisciplinepoc01.domain.policy.TaskUnlockPolicyTest`: 6 tests (PASS) — *Bằng chứng kiểm thử khẳng định quy tắc cũ N=2=>2*.
  * `com.example.selfdisciplinepoc01.domain.enforcement.TaskAppEnforcementAdapterRegressionTest`: 7 tests (PASS).
  * `com.example.selfdisciplinepoc01.domain.enforcement.TaskAppEnforcementIntegrationTest`: 19 tests (PASS).
  * `com.example.selfdisciplinepoc01.domain.enforcement.CoreEnforcementIntegrationAuditTest`: 25 tests (PASS).
  * `com.example.selfdisciplinepoc01.domain.enforcement.ProductFlowValidationTest`: 8 tests (PASS).
  * `com.example.selfdisciplinepoc01.time.BusinessDayProviderTest`: 12 tests (PASS) — *Chứng minh logic tính giờ 04:00 chính xác trên lý thuyết*.
  * `com.example.selfdisciplinepoc01.overlay.BlockingShieldOverlayTest`: 8 tests (PASS) — *Chứng minh cơ chế che chắn tức thì*.
  * `com.example.selfdisciplinepoc01.data.CoreDataRepositoryTest`: 10 tests (PASS).
  * `com.example.selfdisciplinepoc01.ui.missionhall.MissionHallViewModelTest`: 9 tests (PASS).
  * `com.example.selfdisciplinepoc01.ui.vault.VaultViewModelTest`: 5 tests (PASS).
  * `com.example.selfdisciplinepoc01.policy.*`: 147 tests (PASS) — *Toàn bộ các test của Legacy POC kỹ thuật*.

---

## 28. Final Recommendation

Dựa trên toàn bộ chứng cứ pháp y đã thu thập được từ mã nguồn, database, manifest, test results và đối chiếu với MASTER SSOT (v1.0.0), tôi đưa ra khuyến nghị dứt khoát:

1. **KHÔNG TIẾP TỤC VÁ VÍU TRÊN NỀN TẢNG CŨ (NO AD-HOC PATCHING):**
   Codebase hiện tại không thể trở thành sản phẩm thương mại hoàn chỉnh chỉ bằng cách sửa vài hàm hay thêm vài màn hình. Cấu trúc hiện tại đang bị biến dạng bởi hệ thống Technical Lock của POC cũ.
2. **TIẾN HÀNH TÁI THIẾT CÓ KIỂM SOÁT (CONTROLLED CANONICAL RECONSTRUCTION):**
   - Giữ lại 100% lớp thị giác cao cấp `CultivationTheme` và cơ chế phát hiện trợ năng đón đầu độ trễ cực thấp của `AppDetectorAccessibilityService` kết hợp `BlockingShieldOverlay`.
   - Cắt bỏ hoàn toàn toàn bộ khối mã nguồn `PolicyEngine`, `ScheduleWatcher`, `UsageLimitWatcher`, `DataStore Preferences`.
   - Sửa chữa ngay lập tức lỗi tính toán công thức 2/3 (bổ sung đặc xá N=2 => 1) và lỗi khóa app mới thêm N=0 trong `TaskUnlockPolicy.kt`.
   - Chuyển đổi màn hình khóa `LockScreenActivity` thành System Panel Overlay thực thụ hiển thị qua WindowManager.
   - Triển khai tuần tự các module còn thiếu theo 4 giai đoạn đã đề xuất trong Mục 24.
3. **ĐỐI VỚI 3 QUYẾT ĐỊNH MỞ (OPEN DECISIONS):**
   - Tuyệt đối tuân thủ nguyên tắc **WAIT / BLOCKED BY OPEN DECISION** cho OPEN-001 (Loot Pool), OPEN-002 (Voice Session Silence Timeout), và OPEN-003 (Cloud Memory Sync). Thiết kế sẵn các Interface trừu tượng để tiếp nhận cấu hình chính thức từ Product Owner mà không làm gián đoạn tiến trình phát triển.

---
**NGƯỜI LẬP BÁO CÁO:** Antigravity AI Forensic Auditor  
**NGÀY HOÀN THÀNH:** 10/09/2026  
**TRẠNG THÁI:** SẴN SÀNG TRÌNH DUYỆT PRODUCT OWNER
