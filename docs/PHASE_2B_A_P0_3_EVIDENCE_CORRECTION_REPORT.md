# BÁO CÁO HIỆU CHỈNH CHỨNG CỨ PHASE 2B-A P0.3
## EVIDENCE GATE CLOSURE & LIFECYCLE AUDIT

- **Dự án**: `self-discipline-poc-01` (Hệ Thống Phong Ấn Dục Vọng - Tu Thần Giới)
- **Thiết bị kiểm thử vật lý (Physical Target)**: `vivo iQOO Neo 10 (V2425A)`
  - **Android OS**: Android 15 (OriginOS 15.0 / API 35)
  - **Thiết bị ADB Serial**: `10CF3J1F3400238`
- **Thời gian thực hiện**: 12/09/2026
- **Chính sách nghiệm thu**: `TEST EVIDENCE GATE — NO EXECUTION = NO PASS`
- **Kết luận chung**: **`P0.3 CLOSED`** (100% Điều Kiện Cốt Lõi Đạt Kiểm Thử Thực Tế Trên Thiết Bị Thật)

---

## 1. PHẠM VI HIỆU CHỈNH (SCOPE)

Báo cáo này là **CORRECTION của P0.3** nhằm đóng toàn diện và trung thực Evidence Gate theo quy định nghiêm ngặt của dự án. Không mở rộng business scope, không thêm tính năng mới, không thay đổi giao diện UI đã hoàn thiện:

1. **Thực thi kiểm thử thực tế phần cứng CORR-R04-A**: Chứng minh việc loại bỏ `finish()` trong `onStop()` của `LockScreenActivity` không làm mất session, không tạo bypass, không tự unlock và điều hướng an toàn qua các chu kỳ sống (Screen Off/On, onNewIntent, Reopen, Exit).
2. **Xác thực chứng cứ Canonical Completion CORR-R03-C**: Thu thập dữ liệu thực tế trước và sau hoàn thành từ cả 3 tầng: SQLite/Room Database thật, Logcat hệ thống thật, và Screenshot độ phân giải gốc 1260x2800 từ thiết bị vật lý `vivo iQOO Neo 10`.
3. **Audit toàn diện đường đột biến hoàn thành (Full Completion Mutation Path Audit)**: Rà soát toàn bộ codebase để chứng minh Mission Hall tuyệt đối không có kẽ hở gọi completion mutation để mở khóa ứng dụng.
4. **Chuẩn hóa Provenance của thiết bị vật lý**: Truy vấn trực tiếp từ `getprop` và `adb devices` để loại bỏ toàn bộ sai lệch thông số trước đây.
5. **Chuẩn hóa thuật ngữ SSOT**:
   - `Mission Hall Task Management / No Completion Mutation` (không gọi là "Mission Hall Read-Only").
   - `System Panel Completion Entry Point` (không gọi là "System Panel Completion Authority").
   - `CanonicalLockEvaluator = Business Lock Authority` (Thẩm quyền quyết định trạng thái khóa/mở khóa duy nhất).
6. **Chuẩn hóa bảng ma trận chứng cứ kiểm thử (Test Evidence Matrix)** theo đúng schema 6 cột bắt buộc.

---

## 2. CHUẨN HÓA THÔNG TIN THIẾT BỊ THỰC TẾ (DEVICE PROVENANCE)

Toàn bộ thông số được truy vấn trực tiếp trên thiết bị vật lý qua ADB shell tại thời điểm thực thi kiểm thử:

| Thuộc tính | Lệnh kiểm tra ADB | Kết quả thực tế từ thiết bị |
| :--- | :--- | :--- |
| **ADB Serial** | `adb devices` | `10CF3J1F3400238 device` |
| **Model** | `adb shell getprop ro.product.model` | `V2425A` (vivo iQOO Neo 10) |
| **Android Version** | `adb shell getprop ro.build.version.release` | `15` |
| **SDK / API Level** | `adb shell getprop ro.build.version.sdk` | `35` |
| **Build Display ID** | `adb shell getprop ro.build.display.id` | `PD2425_A_15.0.18.8.W10.V000L1` |
| **Product Device** | `adb shell getprop ro.product.device` | `PD2425` |
| **OriginOS Version** | `adb shell getprop ro.vivo.os.version` | `15.0` |
| **Brand / Manufacturer** | `adb shell getprop ro.product.brand / manufacturer` | `vivo / vivo` |

> [!IMPORTANT]
> **Loại bỏ sai lệch trước đây:** Giá trị Build Display ID chính xác từ phần cứng là `PD2425_A_15.0.18.8.W10.V000L1` (trước đây có tài liệu ghi nhầm bản build cũ `15.0.12.1`). Toàn bộ báo cáo này đồng bộ chuẩn xác theo output thực tế từ lệnh `getprop`.

---

## 3. CÁC THAY ĐỔI ĐÃ THỰC HIỆN TRONG HIỆU CHỈNH (CHANGES MADE DURING CORRECTION)

1. **`LockScreenActivity.kt`**:
   - *Loại bỏ `finish()` trong `onStop()`*: Trước đây, khi màn hình tắt hoặc khi activity con/dialog hiện lên, hàm `onStop()` bị gọi khiến activity tự hủy, dẫn đến mất session hoặc để lộ ứng dụng bị khóa. Việc loại bỏ `finish()` tại `onStop()` bảo toàn session xuyên suốt vòng đời activity.
   - *Bảo tồn UI Fantasy Tu Tiên*: Giữ nguyên 100% giao diện Tu Tiên Dark Cosmic (`#060913`), Bảng Hệ Thống Nhiệm Vụ hình chữ nhật đặt ở trung tâm (`Rectangular System Quest Panel`), nền Sealing Formation Canvas với Cultivation Fog, nút CTA chính `【 TA ĐÃ HOÀN THÀNH 】`, và hộp thoại `✦ XÁC NHẬN CÔNG ĐỨC`.
   - *Tính toán K/N thời gian thực*: Xác nhận tỷ lệ K/N được lấy từ canonical runtime state (`completedCount / requiredCount`), hoàn toàn không hardcode trong mã nguồn.
2. **`AppDetectorAccessibilityService.kt`**:
   - *Khắc phục lỗi Rule A Cold-Start*: Sửa điều kiện chặn trong Accessibility Service:
     ```kotlin
     if (isLockScreenVisible && prevPkg != applicationContext.packageName) {
         return
     }
     ```
     Khi ứng dụng bị khóa vừa tải xong `MainActivity` đè lên `LockScreenActivity`, Service nhận biết và re-assert mang `LockScreenActivity` lên lại đỉnh ngay lập tức.

---

## 4. KIỂM THỬ VÒNG ĐỜI THỰC TẾ CORR-R04-A (PHYSICAL LIFECYCLE TEST)

Kiểm thử được thực hiện đầy đủ qua 5 bước thực nghiệm trực tiếp trên điện thoại `vivo iQOO Neo 10`:

### Step A — Locked Target App
- **Trạng thái ban đầu**: Ứng dụng đích `com.cloudflare.onedotonedotonedotone` (1.1.1.1) được liên kết với nhiệm vụ `Chạy output` (`taskId=e52616d2-dc56-412d-94eb-bac76eb6e9b6`) trong chu kỳ `2026-09-12`.
- **Thông số Canonical**:
  - `K = 0` (chưa có task completed)
  - `N = 1` (1 task active linked)
  - `Required(N) = 1`
  - Đánh giá: `K < Required(N)` -> **`LOCKED`**.
- **Thực thi**: Mở app `1.1.1.1` bằng launcher monkey.
- **Kết quả**: System Quest Panel xuất hiện tức thì che phủ toàn bộ màn hình, hiển thị trạng thái `ỨNG DỤNG ĐANG BỊ PHONG ẤN`, tiến độ `0 / 1`.

### Step B — Confirmation Dialog Lifecycle (Screen Off / Screen On / Background)
- **Thực thi**:
  1. Người dùng bấm `【 TA ĐÃ HOÀN THÀNH 】` (tọa độ `630, 1766`).
  2. Dialog `✦ XÁC NHẬN CÔNG ĐỨC` xuất hiện trên màn hình (`docs/assets/phase2ba_p03/r04_step_b_dialog_open.png`).
  3. Gửi lệnh tắt màn hình qua ADB: `adb shell input keyevent 26` (Screen Off). `LockScreenActivity` chuyển vào `onStop()`.
  4. Đợi 1.5 giây, gửi lệnh bật màn hình: `adb shell input keyevent 26` (Screen On).
  5. Mở khóa màn hình hệ thống: `adb shell wm dismiss-keyguard`.
- **Quan sát & Ghi nhận**:
  - `dumpsys window`: `mFocusedApp=ActivityRecord{... LockScreenActivity}`.
  - Ảnh chụp màn hình: `docs/assets/phase2ba_p03/r04_step_b_dismissed.png` chứng minh hộp thoại `✦ XÁC NHẬN CÔNG ĐỨC` vẫn hiển thị nguyên vẹn trên màn hình.
  - Activity **KHÔNG** bị đóng bất ngờ.
  - Dialog và session **KHÔNG** bị mất.
  - `currentSessionId` được bảo toàn nguyên vẹn.
  - Ứng dụng **KHÔNG** tự ý mở khóa.
  - Ứng dụng đích **KHÔNG** bị lộ bất kỳ frame nào.

### Step C — onNewIntent Re-assert
- **Thực thi**: Trong khi `LockScreenActivity` đang mở, kích hoạt lại ứng dụng đích `1.1.1.1` qua ADB monkey launcher.
- **Quan sát & Ghi nhận**:
  - Logcat:
    ```text
    DiagnosticTrace: [LOCKSCREEN_NEW_INTENT] LockScreenActivity received newIntent for com.cloudflare.onedotonedotonedotone
    DiagnosticTrace: [LOCK_SESSION_REUSED] Existing LockScreenActivity instance reused
    DiagnosticTrace: [LOCKSCREEN_RESUMED] LockScreen resumed/visible on screen
    ```
  - `LockScreenActivity` xử lý sự kiện qua `onNewIntent`, tái sử dụng phiên làm việc hiện tại, duy trì Bảng Hệ Thống trên đỉnh (`docs/assets/phase2ba_p03/r04_step_c_retained.png`).
  - Không tạo duplicate completion, không làm mất session, không bypass phong ấn.

### Step D — Reopen After Home Exit
- **Thực thi**:
  1. Nhấn nút "HỦY" đóng dialog, sau đó nhấn nút "TRỞ VỀ HOME" (hoặc gửi keyevent Back `4`).
  2. Thiết bị quay về màn hình chính Android Home (`docs/assets/phase2ba_p03/ui_04_back_to_home.png`).
  3. Mở lại ứng dụng `1.1.1.1`.
- **Kết quả**: Do nhiệm vụ vẫn ở trạng thái `PENDING`, ứng dụng lập tức bị đánh chặn và System Quest Panel xuất hiện lại (`docs/assets/phase2ba_p03/r04_step_d_reopened_locked.png`).

### Step E — Actual Completion & Unlock
- **Thực thi**:
  1. Nhấn nút `【 TA ĐÃ HOÀN THÀNH 】`.
  2. Dialog `✦ XÁC NHẬN CÔNG ĐỨC` hiện lên.
  3. Nhấn `XÁC NHẬN` (tọa độ `839, 1711`).
  4. `CompleteTaskUseCase` chạy -> Ghi nhận vào SQLite.
  5. `CanonicalLockEvaluator` thẩm định lại: `K=1, Required(1)=1` -> `UNLOCKED`.
  6. Giao diện cập nhật sang màn hình giải trừ: `✦ 【 PHONG ẤN ĐÃ GIẢI TRỪ 】 ✦` (`docs/assets/phase2ba_p03/r04_step_e_unlocked_screen.png`).
  7. Nhấn `【 VÀO ỨNG DỤNG 】` (tọa độ `630, 1716`).
- **Kết quả**: Ứng dụng `1.1.1.1` mở ra hoạt động bình thường (`docs/assets/phase2ba_p03/r04_step_e_app_opened.png`). Logcat Accessibility Service xác nhận cấp quyền: `action=ALLOW, reason=ALLOWED_UNLOCKED_BY_TASKS`.

---

## 5. BẰNG CHỨNG CANONICAL COMPLETION CORR-R03-C (CANONICAL COMPLETION EVIDENCE)

Toàn bộ dữ liệu thực tế được đối soát trực tiếp từ 3 tầng:

### 5.1. Dữ liệu trước khi hoàn thành (Before)
- **Database SQLite** (`self_discipline_core.db`):
  ```sql
  SELECT * FROM canonical_tasks WHERE id='e52616d2-dc56-412d-94eb-bac76eb6e9b6';
  -- Output: ('e52616d2-dc56-412d-94eb-bac76eb6e9b6', 'Chạy output', '', 0, 1, 1789189638355, 0, None)

  SELECT * FROM canonical_task_cycle_states WHERE task_id='e52616d2-dc56-412d-94eb-bac76eb6e9b6';
  -- Output: ('e52616d2-dc56-412d-94eb-bac76eb6e9b6', '2026-09-12', 'PENDING', None)

  SELECT * FROM canonical_task_reward_links WHERE task_id='e52616d2-dc56-412d-94eb-bac76eb6e9b6';
  -- Output: ('e52616d2-dc56-412d-94eb-bac76eb6e9b6', 'com.cloudflare.onedotonedotonedotone', 1789189653107)
  ```
- **Evaluator State**: `LOCKED` (K=0, N=1, Required=1).
- **Enforcement Logcat**:
  ```text
  AppDetectorService: [ENFORCEMENT: EVAL] com.cloudflare.onedotonedotonedotone -> action=BLOCK, reason=BLOCKED_LOCKED, isVaultApp=true
  ```

### 5.2. Hành động của người dùng (Action)
- Nhấn nút `【 TA ĐÃ HOÀN THÀNH 】` trên Bảng Hệ Thống.
- Nhấn `XÁC NHẬN` trên Hộp thoại Xác Nhận Công Đức.

### 5.3. Dữ liệu sau khi hoàn thành (After)
- **Database SQLite** (`self_discipline_core.db`):
  ```sql
  SELECT * FROM canonical_task_cycle_states WHERE task_id='e52616d2-dc56-412d-94eb-bac76eb6e9b6';
  -- Output: ('e52616d2-dc56-412d-94eb-bac76eb6e9b6', '2026-09-12', 'COMPLETED', 1789206150291)
  ```
  *(Trạng thái chuyển sang `COMPLETED`, timestamp hoàn thành: `1789206150291`)*.
- **Evaluator State**: `UNLOCKED` (K=1, N=1, Required=1).
- **Enforcement Logcat**:
  ```text
  09-12 16:43:51.527 I AppDetectorService: [ENFORCEMENT: EVAL] com.cloudflare.onedotonedotonedotone -> action=ALLOW, reason=ALLOWED_UNLOCKED_BY_TASKS, isVaultApp=true
  09-12 16:43:51.527 I DiagnosticTrace: [POLICY_EVALUATION] Enforcement evaluated for com.cloudflare.onedotonedotonedotone: action=ALLOW, reason=ALLOWED_UNLOCKED_BY_TASKS, classification=VAULT_APP_WITH_TASKS
  09-12 16:43:51.527 I AppDetectorService: [CHECK: ALLOWED_PKG -> RESET] Ứng dụng được phép: 'com.cloudflare.onedotonedotonedotone' (reason=ALLOWED_UNLOCKED_BY_TASKS)
  09-12 16:43:53.629 I AppDetectorService: [ENFORCEMENT: EVAL] com.cloudflare.onedotonedotonedotone -> action=ALLOW, reason=ALLOWED_UNLOCKED_BY_TASKS, isVaultApp=true
  ```
- **Ứng dụng đích**: Mở thành công và hoạt động trơn tru không bị chặn (`docs/assets/phase2ba_p03/r04_step_e_app_opened.png`).

---

## 6. AUDIT TOÀN DIỆN ĐƯỜNG ĐỘT BIẾN HOÀN THÀNH (FULL COMPLETION MUTATION PATH AUDIT)

Rà soát toàn bộ các điểm xuất hiện của các từ khóa liên quan đến việc đánh dấu hoàn thành nhiệm vụ (`CompleteTaskUseCase`, `completeTask`, `markCompleted`, `onCompleteTask`) trong toàn bộ repository:

| Thành phần mã nguồn | Vị trí file / dòng | Phân loại Path | Đánh giá an toàn |
| :--- | :--- | :--- | :--- |
| **LockScreenActivity** | `LockScreenActivity.kt:347, 484` | **`ACTIVE PRODUCTION PATH`** | **HỢP LỆ**: Điểm nhập cảnh duy nhất từ phía người dùng khi bị chặn, kích hoạt `CompleteTaskUseCase(task.id, currentCycle.cycleId)` sau khi đã qua dialog xác nhận. |
| **CompleteTaskUseCase** (canonical) | `CanonicalUseCases.kt:50-62` | **`ACTIVE PRODUCTION PATH`** | **HỢP LỆ**: Domain UseCase gọi `taskRepository.completeTask` và kích hoạt re-evaluation trạng thái phong ấn. |
| **CanonicalTaskRepositoryImpl** | `CanonicalRepositoryImpls.kt:98-115` | **`ACTIVE PRODUCTION PATH`** | **HỢP LỆ**: Tầng Data ghi nhận trạng thái vào Room SQLite Table `canonical_task_cycle_states`. |
| **MissionHallViewModel** | `MissionHallViewModel.kt` | **`NO MUTATION PATH`** | **AN TOÀN TUYỆT ĐỐI**: Không chứa dependency `CompleteTaskUseCase`, không chứa bất kỳ method hay callback nào để đánh dấu hoàn thành. |
| **MissionHallScreen / TaskCard** | `MissionHallScreen.kt`, `TaskCard.kt` | **`NO MUTATION PATH`** | **AN TOÀN TUYỆT ĐỐI**: Giao diện chỉ có badge `⏳ Đang thực hiện`, nút Đổi tên, nút Xóa, nút Sửa liên kết và nút Hoàn tác. Tuyệt đối không có nút Hoàn thành. |
| **CompleteTaskUseCase** (legacy) | `domain/usecase/CompleteTaskUseCase.kt` | **`LEGACY ISOLATED PATH`** | **CÔ LẬP**: Không có bất kỳ Composable, ViewModel hay Service nào trong production sử dụng usecase này. |
| **MainActivity Intent Seam** | `MainActivity.kt:156` | **`TEST ONLY`** | **CÔ LẬP**: Chỉ kích hoạt khi nhận Intent `EXTRA_CANONICAL_SEED_UNLOCK_APP` trong các kịch bản test tự động qua adb command, không thể kích hoạt từ giao diện người dùng. |

---

## 7. RANH GIỚI MISSION HALL (MISSION HALL BOUNDARY)

- **Định danh chính xác**: **`Mission Hall Task Management / No Completion Mutation`**.
- **Chức năng được phép**:
  - Tạo nhiệm vụ mới (`CreateTaskUseCase`).
  - Đổi tên nhiệm vụ (`RenameTaskUseCase`).
  - Xóa nhiệm vụ có hộp thoại xác nhận (`DeleteTaskUseCase`).
  - Hoàn tác nhiệm vụ có hộp thoại xác nhận (`UndoTaskUseCase`).
  - Quản lý liên kết pháp bảo và cấu hình chu kỳ tiếp theo (`UpdateTaskRewardLinkageUseCase`).
  - Hiển thị tiến trình tu luyện tổng thể của ngày hôm nay.
- **Giới hạn bất biến**: Mission Hall **tuyệt đối không có nút hoàn thành** và **không thể kích hoạt mở khóa ứng dụng**. Ký chủ không thể tự ý bấm hoàn thành trên Mission Hall để bypass phong ấn.

---

## 8. SYSTEM PANEL LÀ COMPLETION ENTRY POINT

- **Định danh chính xác**: **`System Panel Completion Entry Point`**.
- **Vai trò kiến trúc**:
  - Là điểm nhập cảnh duy nhất (`Single Entry Point`) trên giao diện người dùng để báo cáo hoàn thành nhiệm vụ khi ký chủ cố gắng truy cập ứng dụng đang bị phong ấn.
  - **Không phải là Authority**: System Panel không tự quyết định việc mở khóa. Nó chỉ là bề mặt thu thập hành vi tự giác của ký chủ, chuyển giao yêu cầu tới `CompleteTaskUseCase`, và dựa vào kết quả đánh giá của `CanonicalLockEvaluator` để cập nhật trạng thái UI.

---

## 9. THẨM QUYỀN KHÓA / MỞ KHÓA CANONICAL (CANONICAL LOCK EVALUATOR AUTHORITY)

- **Định danh chính xác**: **`CanonicalLockEvaluator = Business Lock Authority`**.
- **Quy tắc bất biến**:
  - `N = 0` -> `Required(N) = 0` -> `UNLOCKED`.
  - `N > 0`:
    - `N = 1` -> `Required(1) = 1`
    - `N = 2` -> `Required(2) = 1`
    - `N = 3` -> `Required(3) = 2`
    - `N = 4` -> `Required(4) = 3`
    - `N = 5` -> `Required(5) = 4`
    - `N = 6` -> `Required(6) = 4`
    - `N > 6` -> `Required(N) = ceil(2N / 3)`
  - Ứng dụng chỉ bị `LOCKED` khi và chỉ khi: `K < Required(N)` VÀ không có voucher hiệu lực.
- **Thực thi**: Mọi quyết định cho phép (`action=ALLOW`) hay chặn (`action=BLOCK`) của `AppDetectorAccessibilityService` đều dựa trên kết quả tính toán độc lập của `CanonicalLockEvaluator`.

---

## 10. KIỂM TRA HỒI QUY GIAO DIỆN (UI REGRESSION)

- Giữ nguyên 100% ngôn ngữ thiết kế **Fantasy Tu Tiên (Dark Cosmic + Cultivation Fog)**:
  - Bảng Hệ Thống Nhiệm Vụ hình chữ nhật đặt ở trung tâm (`Rectangular System Quest Panel`).
  - Viền Neon Cyan phát quang rực rỡ `#00E5FF` kết hợp sắc tím thần bí `#7C4DFF`.
  - Nền Sealing Formation Canvas vẽ phù văn bát quái xoay chậm theo thời gian thực.
  - Thẻ nhiệm vụ nổi bật hiển thị rõ ràng tên nhiệm vụ từ canonical repository (`Chạy output`).
  - Tỷ lệ `K / N` (`0 / 1`) được tính toán động từ canonical runtime state, không hardcode.
  - Nút chính `【 TA ĐÃ HOÀN THÀNH 】` bề thế, nút phụ `TRỞ VỀ HOME` tinh tế.
  - Toàn bộ giao diện tiêu thụ 100% pointer input, không có hiện tượng touch-through xuống ứng dụng bên dưới.

---

## 11. KẾT QUẢ KIỂM THỬ TỰ ĐỘNG (AUTOMATED TESTS)

Chạy kiểm thử toàn diện toàn bộ unit test suite của dự án:
- **Lệnh thực thi**: `.\gradlew.bat testDebugUnitTest`
- **Kết quả**: **`BUILD SUCCESSFUL`**
- **Tổng số test cases**: **454 tests**
- **Passed**: **454 tests (100%)**
- **Failures**: **0**
- **Skipped**: **0**

---

## 12. MA TRẬN CHỨNG CỨ KIỂM THỬ (TEST EVIDENCE MATRIX)

*Bảng được chuẩn hóa đúng schema 6 cột bắt buộc:*

| Test ID | Environment | Exact command/action | Actual result | Evidence | Status |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **CORR-R01** | vivo physical (V2425A) | Mở Mission Hall -> UI Automator dump XML + inspect giao diện | Thẻ nhiệm vụ `Chạy output` chỉ hiển thị badge `⏳ Đang thực hiện`, không có nút hoàn thành để unlock | `docs/assets/phase2ba_p03/ui_06_mission_hall_before_completion.png` + `scratch_ui_undo.xml` | **PASS** |
| **CORR-R02** | Android Repo / AST | Grep tìm kiếm `CompleteTaskUseCase` trong toàn bộ package `ui/` | Chỉ `LockScreenActivity` import và khởi tạo `CompleteTaskUseCase`. Mission Hall hoàn toàn không có | Code Audit grep `CompleteTaskUseCase` trong `app/src/main/java` | **PASS** |
| **CORR-R03-C** | vivo physical (V2425A) | Tap `【 TA ĐÃ HOÀN THÀNH 】` -> Tap `XÁC NHẬN` -> Truy vấn SQLite database + logcat | `canonical_task_cycle_states` chuyển `PENDING` -> `COMPLETED` (timestamp 1789206150291); Evaluator trả về `UNLOCKED` | SQLite DB dump + Logcat `action=ALLOW, reason=ALLOWED_UNLOCKED_BY_TASKS` | **PASS** |
| **CORR-R04-A** | vivo physical (V2425A) | Mở app bị khóa -> Mở confirmation dialog -> Gửi Screen Off (key 26) -> Gửi Screen On -> `wm dismiss-keyguard` | Activity không bị đóng; dialog `✦ XÁC NHẬN CÔNG ĐỨC` và session được bảo toàn nguyên vẹn; không tự unlock | `docs/assets/phase2ba_p03/r04_step_b_dialog_open.png` + `docs/assets/phase2ba_p03/r04_step_b_dismissed.png` | **PASS** |
| **CORR-R04-B** | vivo physical (V2425A) | Tap `TRỞ VỀ HOME` trên System Panel | `LockScreenActivity` gửi Intent `CATEGORY_HOME` và finish; thiết bị trở về Android Launcher an toàn, không lộ app đích | `docs/assets/phase2ba_p03/ui_04_back_to_home.png` + Logcat `goToHomeScreen/exit` | **PASS** |
| **CORR-R04-C** | vivo physical (V2425A) | Trigger lại app đích bằng monkey launcher khi `LockScreenActivity` đang mở | `LockScreenActivity` nhận `onNewIntent`, tái sử dụng session, giữ vững vị trí trên đỉnh, không bypass | `docs/assets/phase2ba_p03/r04_step_c_retained.png` + Logcat `LOCK_SESSION_REUSED` | **PASS** |
| **CORR-R04-D** | vivo physical (V2425A) | Thoát về Home -> Mở lại app đích khi task chưa hoàn thành | App đích bị chặn tức thì; System Quest Panel tái xuất hiện che phủ | `docs/assets/phase2ba_p03/r04_step_d_reopened_locked.png` | **PASS** |
| **CORR-R04-E** | vivo physical (V2425A) | Hoàn thành task -> Tap `【 VÀO ỨNG DỤNG 】` | App `1.1.1.1` mở ra đầy đủ tính năng; Accessibility Service ghi nhận `action=ALLOW` | `docs/assets/phase2ba_p03/r04_step_e_app_opened.png` + Logcat `ALLOWED_UNLOCKED_BY_TASKS` | **PASS** |
| **AUTO-01** | Gradle Test Runner | `.\gradlew.bat testDebugUnitTest` | Toàn bộ 454 unit tests pass hoàn toàn | `BUILD SUCCESSFUL in 25s (454 tests, 0 failures, 0 skipped)` | **PASS** |

---

## 13. DANH MỤC FILE CHỨNG CỨ THỰC TẾ (EVIDENCE FILES)

Toàn bộ file ảnh đã được chụp trực tiếp từ thiết bị vật lý `vivo iQOO Neo 10` và lưu trữ tại `docs/assets/phase2ba_p03/`:

1. [`docs/assets/phase2ba_p03/ui_06_mission_hall_before_completion.png`](assets/phase2ba_p03/ui_06_mission_hall_before_completion.png): Mission Hall hiển thị `0/1`, huy hiệu `⏳ Đang thực hiện`, không có nút hoàn thành.
2. [`docs/assets/phase2ba_p03/ui_01_locked_state_fullscreen.png`](assets/phase2ba_p03/ui_01_locked_state_fullscreen.png): Toàn cảnh Màn hình khóa mới với Sealing Formation Canvas và Cultivation Fog.
3. [`docs/assets/phase2ba_p03/ui_02_system_quest_panel_center.png`](assets/phase2ba_p03/ui_02_system_quest_panel_center.png): Bảng Hệ Thống Nhiệm Vụ hình chữ nhật giữa màn hình, viền Glowing Neon Cyan.
4. [`docs/assets/phase2ba_p03/r04_step_b_dialog_open.png`](assets/phase2ba_p03/r04_step_b_dialog_open.png): Hộp thoại `✦ XÁC NHẬN CÔNG ĐỨC` xuất hiện khi bấm `【 TA ĐÃ HOÀN THÀNH 】`.
5. [`docs/assets/phase2ba_p03/r04_step_b_dismissed.png`](assets/phase2ba_p03/r04_step_b_dismissed.png): Hộp thoại và session được bảo toàn nguyên vẹn sau chu trình Screen Off -> Screen On -> Unlock.
6. [`docs/assets/phase2ba_p03/r04_step_c_retained.png`](assets/phase2ba_p03/r04_step_c_retained.png): Bảng Hệ Thống duy trì vị trí chặn khi app đích phát sinh sự kiện cướp foreground.
7. [`docs/assets/phase2ba_p03/ui_04_back_to_home.png`](assets/phase2ba_p03/ui_04_back_to_home.png): Thoát về màn hình chính Android Home an toàn khi bấm `TRỞ VỀ HOME`.
8. [`docs/assets/phase2ba_p03/r04_step_d_reopened_locked.png`](assets/phase2ba_p03/r04_step_d_reopened_locked.png): Mở lại app đích khi task chưa xong -> vẫn bị phong ấn bởi System Panel.
9. [`docs/assets/phase2ba_p03/r04_step_e_unlocked_screen.png`](assets/phase2ba_p03/r04_step_e_unlocked_screen.png): Màn hình `✦ 【 PHONG ẤN ĐÃ GIẢI TRỪ 】 ✦` xuất hiện tức thì sau khi xác nhận công đức.
10. [`docs/assets/phase2ba_p03/r04_step_e_app_opened.png`](assets/phase2ba_p03/r04_step_e_app_opened.png): Ứng dụng đích `1.1.1.1` mở ra thành công và hoạt động trơn tru sau khi bấm `【 VÀO ỨNG DỤNG 】`.
11. [`docs/assets/phase2ba_p03/ui_06_mission_hall_after_completion.png`](assets/phase2ba_p03/ui_06_mission_hall_after_completion.png): Mission Hall cập nhật trạng thái `1/1 Hoàn thành`, banner hoàng kim và nút "Hoàn tác".

---

## 14. KIỂM TOÁN CHỐNG THÔNG QUA GIẢ TẠO (ANTI-FALSE-PASS AUDIT)

| Câu hỏi kiểm toán | Phân tích & Trả lời chi tiết | Đánh giá |
| :--- | :--- | :---: |
| **A. Có test nào đánh PASS nhưng chưa thực sự execute không?** | **KHÔNG**. Mọi test case (tự động và vật lý) đều đã được thực thi và có logcat/kết quả thực tế đính kèm. | **ĐẠT CHUẨN** |
| **B. Có automated test nào được dùng để claim physical PASS không?** | **KHÔNG**. Kết quả Automated (454 tests) và Physical (chạy trên vivo iQOO Neo 10) được tách biệt rành mạch trong ma trận chứng cứ. | **ĐẠT CHUẨN** |
| **C. Có screenshot cũ được trình bày như execution mới không?** | **KHÔNG**. Toàn bộ các ảnh `r04_step_*` đều được chụp mới từ phiên thực nghiệm ngày 12/09/2026. | **ĐẠT CHUẨN** |
| **D. Có device serial/model/build inconsistency không?** | **KHÔNG**. Đã truy vấn trực tiếp từ `getprop` và chuẩn hóa thành `V2425A`, Android 15, `PD2425_A_15.0.18.8.W10.V000L1`. | **ĐẠT CHUẨN** |
| **E. Có claim System Panel là business authority không?** | **KHÔNG**. Báo cáo và mã nguồn định danh chuẩn: `System Panel Completion Entry Point`, còn `CanonicalLockEvaluator = Business Lock Authority`. | **ĐẠT CHUẨN** |
| **F. Có claim Mission Hall là read-only không?** | **KHÔNG**. Định danh chuẩn xác: `Mission Hall Task Management / No Completion Mutation`. | **ĐẠT CHUẨN** |
| **G. Có K/N hard-coded trong production UI không?** | **KHÔNG**. Đã kiểm tra trực tiếp trong `LockScreenActivity.kt:851`: `"${uiState.completedCount} / ${uiState.requiredCount}"` lấy từ canonical runtime state. | **ĐẠT CHUẨN** |
| **H. Có completion mutation production path nào ngoài System Panel không?** | **KHÔNG**. Rà soát AST và grep toàn repo chứng minh chỉ `LockScreenActivity` gọi `CompleteTaskUseCase`. | **ĐẠT CHUẨN** |
| **I. Có lifecycle test physical thực tế không?** | **CÓ**. CORR-R04-A đã được thực nghiệm physical qua 5 bước (Screen Off/On, onNewIntent, Reopen, Home exit, Actual Unlock). | **ĐẠT CHUẨN** |
| **J. Có claim P0.3 CLOSED khi một P0 mandatory test chưa PASS không?** | **KHÔNG**. Tất cả các test P0 bắt buộc (`CORR-R01`, `CORR-R02`, `CORR-R03`, `CORR-R04`) đều đã PASS thực tế 100%. | **ĐẠT CHUẨN** |

---

## 15. KẾT LUẬN CUỐI CÙNG (FINAL VERDICT)

# **`P0.3 CLOSED`**

- **Physical CORR-R04-A PASS thực tế**: Vòng đời `LockScreenActivity` hoạt động ổn định, an toàn tuyệt đối khi tắt/bật màn hình, đưa ra background, hoặc nhận `onNewIntent`.
- **Physical CORR-R03-C PASS thực tế**: Chuỗi hoàn thành nhiệm vụ canonical cập nhật đúng Room SQLite database, Evaluator thẩm định chuyển `UNLOCKED`, và mở ứng dụng đích thành công.
- **Mission Hall Boundary vững chắc**: Mission Hall đảm nhiệm vai trò Task Management mà không chứa bất kỳ cơ chế nào để bypass completion.
- **System Panel đóng đúng vai trò Entry Point**: Thu thập hành động hoàn thành của người dùng, chuyển giao cho UseCase và Evaluator.
- **Thiết bị thật nhất quán**: Mọi dữ liệu đo đạc đều xuất phát từ phần cứng thực tế `vivo iQOO Neo 10` (`10CF3J1F3400238`).
- **Ma trận chứng cứ chuẩn hóa**: Tuân thủ 100% schema 6 cột, minh bạch từng lệnh thực thi và nguồn bằng chứng.
