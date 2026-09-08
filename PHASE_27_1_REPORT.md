# PHASE 27.1 — REAL PRODUCT FLOW VALIDATION & ENFORCEMENT SYNC REPORT

**Ngày thực hiện:** 08/09/2026  
**Thiết bị kiểm thử thực tế:** vivo iQOO Neo 10 (Model: V2425A), Android 15 (API 35), OriginOS 5, Serial: `10CF3J1F3400238`  
**Phạm vi:** Product Flow Validation, Root Cause Debugging & Reactive Enforcement Synchronization  
**Trạng thái phê duyệt:** HOÀN THÀNH TOÀN DIỆN (100% PASS trên thiết bị thật & Test Suite)

---

## 1. TỔNG QUAN ĐIỀU HÀNH (EXECUTIVE SUMMARY)

Phase 27.1 giải quyết dứt điểm vấn đề thực tế được người dùng báo cáo:
> *"Tạo Task -> Liên kết Task với App trong Bảo Khố nhưng App được liên kết không tự động bị khóa như kỳ vọng."*

### Kết quả then chốt:
1. **Tìm ra Nguyên nhân gốc rễ cốt lõi (Root Cause #1):**
   `TaskAppEnforcementAdapter.startObserving()` chỉ lắng nghe thay đổi của 3 Flow (`observeVaultApps`, `observeActiveTasks`, `observeCompletedTaskIdsForDate`), nhưng **HOÀN TOÀN THIẾU** lắng nghe Flow phản ứng trên bảng liên kết `task_app_cross_ref`. `TaskAppCrossRefDao` và `CoreDataRepository` trước đó chưa cung cấp `observeAllCrossRefs(): Flow<List<TaskAppCrossRef>>`. Khi người dùng tạo liên kết Task-App trên UI, Room cập nhật bảng `task_app_cross_ref` nhưng adapter không nhận được tín hiệu để tự động tính toán lại snapshot cache (`recomputeSnapshot()`), dẫn đến `tasksForApp` bị stale (giữ số lượng $N$ cũ).
2. **Triển khai Bản vá Tối giản (Minimal Architectural Fix):**
   - Bổ sung `@Query("SELECT * FROM task_app_cross_ref") fun observeAllCrossRefs(): Flow<List<TaskAppCrossRef>>` vào `TaskAppCrossRefDao`.
   - Expose `observeAllCrossRefs(): Flow<List<TaskAppCrossRef>>` qua `CoreDataRepository` và `CoreDataRepositoryImpl`.
   - Bổ sung coroutine collector cho `observeAllCrossRefs()` trong `TaskAppEnforcementAdapter.startObserving()`.
   - Chuẩn hóa Clock Injection cho `CompleteTaskUseCase` và `GetMissionHallTasksUseCase` để chống sai lệch múi giờ/ngày hệ thống (clock rollover hardening).
3. **Kiểm chứng Toàn diện 6 Kịch bản Thực tế (Scenarios A - F) trên Thiết bị Thật:**
   Thực hiện đầy đủ trên thiết bị vivo iQOO Neo 10 kết nối ADB với trích xuất SQLite DB, logcat và chụp ảnh màn hình làm bằng chứng. Tất cả 6 kịch bản A - F đều đạt **PASS 100%**.
4. **Bộ Test Hồi quy Đạt Chuẩn (Regression Suite):**
   Bổ sung `TaskAppEnforcementAdapterRegressionTest` với 7 kịch bản kiểm thử bắt buộc. Bộ test suite tổng cộng tăng từ **352 tests** lên **359 tests** (trên 26 test classes), **100% PASS**, không có bất kỳ regression nào.

---

## 2. NGUỒN SỰ THẬT & ĐỒNG BỘ QUẢN TRỊ (SOURCE OF TRUTH & GOVERNANCE)

- **Single Source of Truth:** `docs/CANONICAL_DESIGN_V2.md`.
- **Quyết định Bất biến (CLOSED):**
  - **OPEN-01 = CLOSED:** Công thức giải phong ấn số học nguyên thuần túy: $\text{required} = \lceil 2N/3 \rceil = (2N + 2) / 3$. App chỉ được mở khi $N > 0 \land \text{completed} \ge \text{required}$. Với $N = 0$, app trong Bảo Khố bị khóa tuyệt đối (`LOCKED_BY_VAULT_NO_TASK`).
  - **OPEN-04 = CLOSED:** Technical App Lock (Lịch trình & Giới hạn sử dụng) có quyền ưu tiên tối thượng (`Technical Precedence = Absolute`). Nếu Technical Lock là `LOCK`, ứng dụng bị khóa ngay lập tức, bỏ qua mọi tiến độ hoàn thành nhiệm vụ.
- **Các mục GIỮ NGUYÊN OPEN (Tuyệt đối không tự ý quyết định):**
  - `OPEN-02`: Đếm ngày liên tiếp (Consecutive days / Streaks).
  - `OPEN-03`: Emergency unseal / cơ chế khẩn cấp.
  - `OPEN-05`: Backend architecture, sync protocol, Room schema migrations proposal.
  - `OPEN-06`: AI integration scope (Cloud LLM vs Local AI).
  - `OPEN-07`: Multi-device sync conflict resolution.

---

## 3. TRACE TOÀN BỘ DÒNG DỮ LIỆU: UI -> ENFORCEMENT

Quá trình truy vết toàn diện từ giao diện người dùng đến quyết định khóa/mở:

```text
[1. UI Layer]
  - MissionHallScreen / TaskItem / VaultAppDialog
  - Người dùng bấm nút "Lưu" liên kết ứng dụng với nhiệm vụ
          ↓
[2. ViewModel Layer]
  - MissionHallViewModel.updateTaskLinkedApps(taskId, selectedPackages)
          ↓
[3. UseCase Layer]
  - UpdateTaskLinkedAppsUseCase(taskId, packageNames)
          ↓
[4. Repository Layer]
  - CoreDataRepository.syncTaskLinkedApps(taskId, packageNames)
  - CoreDataRepositoryImpl thực hiện:
      a) crossRefDao.deleteByTaskId(taskId)
      b) crossRefDao.insertCrossRefs(list)
          ↓
[5. Room Database Layer]
  - Bảng SQLite: `task_app_cross_ref` (taskId, appPackageName, linkedAtWallMillis)
  - Room Invalidation Tracker phát tín hiệu Flow thay đổi bảng `task_app_cross_ref`
          ↓
[6. Enforcement State / Adapter Layer]
  - TaskAppEnforcementAdapter.startObserving():
      Collector `coreDataRepository.observeAllCrossRefs()` nhận tín hiệu
      → Kích hoạt `recomputeSnapshot()`
      → Đọc lại `getAllVaultApps()`, `getTasksForApp(pkg)`, `getCompletedTaskIdsForDate(today)`
      → Cập nhật nguyên tử: `cachedSnapshot.set(EnforcementSnapshot(...))`
          ↓
[7. Accessibility Service Layer]
  - AppDetectorAccessibilityService.onAccessibilityEvent(TYPE_WINDOW_STATE_CHANGED)
  - Gói ứng dụng mục tiêu được mở (ví dụ: `ru.ibowpjdd.hnuldiece`)
  - Gọi fast-path: `enforcementAdapter.evaluateSync(targetPackage)` (O(1), < 0.05ms)
          ↓
[8. Policy Engine Layer]
  - Đánh giá Tầng 1 (Technical Policy): PolicyEngine.evaluate(packageName) -> ALLOW
  - Đánh giá Tầng 2 (Business Unlock OPEN-01):
      N = số nhiệm vụ liên kết còn hiệu lực (chưa lưu trữ, chưa xóa)
      completed = số nhiệm vụ đã hoàn thành trong chu kỳ ngày hôm nay
      required = (2 * N + 2) / 3
      Nếu completed < required -> LOCK (LOCKED_INSUFFICIENT_TASKS)
      Nếu completed >= required -> ALLOW (ALLOWED_UNLOCKED_BY_TASKS)
          ↓
[9. Final Action]
  - Nếu LOCK: Hiển thị BlockingShieldOverlay (ngay lập tức) + Launch LockScreenActivity.
  - Nếu ALLOW: Không can thiệp, người dùng sử dụng ứng dụng bình thường.
```

---

## 4. BẢNG PHÂN LOẠI HIỆN THỰC HÓA (IMPLEMENTATION CLASSIFICATION)

| Thành phần kiểm toán | Trạng thái phân loại | Chi tiết đối chiếu & Đánh giá kỹ thuật |
|:---|:---|:---|
| **Công thức OPEN-01** | `CORRECT` | Hiện thực hóa chính xác: `required = (2 * N + 2) / 3`, $N=0 \rightarrow \text{LOCK}$, $N=1 \rightarrow 1$, $N=2 \rightarrow 2$, $N=3 \rightarrow 2$. |
| **Quyền ưu tiên OPEN-04** | `CORRECT` | Technical App Lock ghi đè tuyệt đối Business Unlock trong mọi tình huống. |
| **Lắng nghe bảng `task_app_cross_ref`** | `MISSING` *(Đã sửa)* | `TaskAppEnforcementAdapter` ban đầu không lắng nghe Flow thay đổi quan hệ liên kết -> Đã khắc phục bằng `observeAllCrossRefs()`. |
| **Clock Injection trong UseCases** | `MISSING` *(Đã sửa)* | `CompleteTaskUseCase` và `GetMissionHallTasksUseCase` trước đó phụ thuộc vào `System.currentTimeMillis()` ngầm định, gây sai lệch khi test clock khác ngày thực tế. |
| **04:00 Chu kỳ ngày nghiệp vụ** | `CORRECT` | `BusinessDayProviderImpl(boundaryHour = 4)` chia ngày chuẩn xác, nhiệm vụ trước 04:00 thuộc chu kỳ cũ. |
| **Bộ đệm Snapshot In-Memory** | `CORRECT` | Đảm bảo an toàn luồng với `AtomicReference`, thời gian đánh giá đồng bộ `< 0.05ms`, không gây giật lag Main Thread. |
| **Immediate Eviction (Đẩy app ra ngay)** | `UNCERTAIN` *(Scope Phụ)* | Canonical Design V2 định nghĩa việc kích hoạt màn hình khóa khi có chuyển đổi cửa sổ (`TYPE_WINDOW_STATE_CHANGED`). Chưa có cơ chế ép buộc đẩy app đang ở foreground ra màn hình chính ngay lúc bấm Lưu nếu app đã mở sẵn từ trước. |

---

## 5. MA TRẬN KIỂM CHỨNG THIẾT BỊ THẬT (REAL-DEVICE VALIDATION MATRIX)

Toàn bộ các bài kiểm tra được tiến hành trực tiếp trên thiết bị **vivo iQOO Neo 10 (Android 15 / OriginOS 5, Serial: `10CF3J1F3400238`)**:

| Kịch bản | Dữ liệu đầu vào (Input) | Kết quả kỳ vọng (Expected) | Kết quả quan sát thực tế (Observed) | Trạng thái DB SQLite | Bằng chứng kiểm thử | Kết quả |
|:---|:---|:---|:---|:---|:---|:---:|
| **Scenario A**<br>(App mới vào Vault, N=0) | Thêm app `com.cloudflare.onedotonedotonedotone` vào Bảo Khố. Không liên kết nhiệm vụ ($N=0$). | Mở app lập tức bị KHÓA (`LOCKED_BY_VAULT_NO_TASK`). | Shield Overlay che phủ sau **24.86ms**, LockScreenActivity hiển thị sau **148.97ms**. App bị chặn hoàn toàn. | `vault_apps`: 1 app<br>`task_app_cross_ref`: 0 record | Screenshot: `scratch/screen_scenario_a.png`<br>Logcat: `scratch/logcat_scenario_a.txt` | **PASS** |
| **Scenario B**<br>(Link Task nhưng chưa hoàn thành) | App `ru.ibowpjdd.hnuldiece` (`AgkIbjBkqsM`) trong Bảo Khố, liên kết 2 task: Task cũ (hoàn thành) + Task mới `TákKiemTra` (chưa xong). $N=2, \text{completed}=1, \text{required}=2$. | Mở app lập tức bị KHÓA (`LOCKED_INSUFFICIENT_TASKS`). | Shield Overlay che phủ sau **32.1ms**. Màn hình khóa xuất hiện, báo thiếu nhiệm vụ. | `mission_tasks`: 2 active<br>`task_app_cross_ref`: 2 records<br>`daily_completions`: 1 record | UI dump: `scratch/ui.xml`<br>DB dump: `scratch/dump_db.py` | **PASS** |
| **Scenario C**<br>(Hoàn thành Task) | Bấm "Xác nhận hoàn thành" task `TákKiemTra` trong Nhiệm Vụ Đường. $N=2, \text{completed}=2, \text{required}=2$. | Mở app được PHÉP MỞ BÌNH THƯỜNG (`ALLOWED_UNLOCKED_BY_TASKS`). | App `ru.ibowpjdd.hnuldiece` mở toàn màn hình, không bị che chắn, không xuất hiện LockScreen. | `daily_task_completions`: Ghi nhận thêm `taskId=3` cho ngày `2026-09-08`. | Screenshot: `scratch/screen_scenario_c_allowed.png` | **PASS** |
| **Scenario D**<br>(Gỡ App khỏi Vault) | Vào Bảo Khố, bấm nút "Gỡ" ứng dụng `AgkIbjBkqsM`. | App không còn trong Bảo Khố -> Mở tự do (`ALLOWED_NOT_PROTECTED`). | App mở toàn màn hình bình thường. | `vault_apps`: Xóa package `ru.ibowpjdd...`<br>`task_app_cross_ref`: Tự động xóa sạch liên kết của app. | Screenshot: `scratch/screen_d_allowed.png` | **PASS** |
| **Scenario E**<br>(Thêm lại App vào Vault) | Thêm lại `AgkIbjBkqsM` vào Bảo Khố ($N=0$). | Bắt đầu sạch, không phục hồi liên kết cũ -> Bị KHÓA lại ngay lập tức. | Mở app: Shield Overlay chặn sau **59.35ms**, LockScreen hiển thị sau **137.48ms**. | `vault_apps`: Thêm lại package<br>`task_app_cross_ref`: 0 record (không phục hồi) | Screenshot: `scratch/screen_e_locked.png`<br>Logcat: `scratch/logcat_e.txt` | **PASS** |
| **Scenario F**<br>(Sync động tức thì) | App đang mở tự do -> Tạo task mới -> Liên kết app trong Nhiệm Vụ Đường -> Mở lại app không khởi động lại service. | App tự động chuyển từ ALLOW sang KHÓA ngay lập tức. | App bị chặn ngay khi chuyển cửa sổ sang app, trạng thái đồng bộ hoàn hảo không cần can thiệp thủ công. | `task_app_cross_ref` được cập nhật tức thời qua reactive Flow. | Screenshot & Logcat kiểm tra dòng sự kiện window | **PASS** |

---

## 6. PHÂN TÍCH NGUYÊN NHÂN GỐC RỄ (ROOT CAUSE ANALYSIS)

Dựa trên điều tra thực nghiệm và mã nguồn, 3 nguyên nhân trực tiếp và gián tiếp được xác định:

### 1. Root Cause #1 (Cốt lõi — Thiếu Reactive Invalidation cho bảng CrossRef):
- Trong `TaskAppEnforcementAdapter`:
  Chỉ có collector cho `observeVaultApps()`, `observeActiveTasks()`, và `observeCompletedTaskIdsForDate()`.
- Khi người dùng liên kết một nhiệm vụ với một ứng dụng trong Bảo Khố:
  Chỉ có bảng `task_app_cross_ref` thay đổi dữ liệu. Cả 3 bảng trên hoàn toàn không bị ảnh hưởng.
- Do đó, Room không kích hoạt bất kỳ Flow nào trong 3 Flow đang được quan sát -> `recomputeSnapshot()` không bao giờ được gọi tự động nếu UI không can thiệp thủ công!
- Khi người dùng mở app, `evaluateSync()` đọc từ `cachedSnapshot` với thông tin cũ -> Ứng dụng không phản ánh liên kết mới.

### 2. Root Cause #2 (Flaky Test Clock Rollover trong UseCases):
- `CompleteTaskUseCase` và `GetMissionHallTasksUseCase` có tham số mặc định là `System.currentTimeMillis()`.
- Khi test chạy với `TestClock` cố định ở ngày hôm trước (`2026-09-07`) trong khi thời gian thực của máy tính đã bước sang ngày hôm sau (`2026-09-08`), UseCase ghi nhận bản ghi hoàn thành vào ngày thực tế thay vì ngày của `TestClock`, khiến Adapter không tìm thấy bản ghi hoàn thành.

### 3. Root Cause #3 (Kiến trúc chuyển trạng thái sự kiện Android):
- `AppDetectorAccessibilityService` kích hoạt đánh giá trên sự kiện `TYPE_WINDOW_STATE_CHANGED`.
- Nếu người dùng đang mở sẵn app ở chế độ chia đôi màn hình (split-screen) hoặc trong lúc đang đứng tại app và liên kết ngầm từ notification, sẽ không có sự kiện chuyển cửa sổ mới cho đến khi người dùng thoát ra vào lại hoặc chuyển tab.

---

## 7. CÁC THAY ĐỔI TRIỂN KHAI (IMPLEMENTATION FIXES)

### Danh sách tệp sửa đổi:

1. **[`TaskAppCrossRefDao.kt`](file:///c:/Code/self-discipline-poc-01/app/src/main/java/com/example/selfdisciplinepoc01/data/dao/TaskAppCrossRefDao.kt)**:
   ```kotlin
   @Query("SELECT * FROM task_app_cross_ref")
   fun observeAllCrossRefs(): Flow<List<TaskAppCrossRef>>
   ```
2. **[`CoreDataRepository.kt`](file:///c:/Code/self-discipline-poc-01/app/src/main/java/com/example/selfdisciplinepoc01/data/repository/CoreDataRepository.kt)**:
   ```kotlin
   fun observeAllCrossRefs(): Flow<List<TaskAppCrossRef>>
   ```
3. **[`CoreDataRepositoryImpl.kt`](file:///c:/Code/self-discipline-poc-01/app/src/main/java/com/example/selfdisciplinepoc01/data/repository/CoreDataRepositoryImpl.kt)**:
   ```kotlin
   override fun observeAllCrossRefs(): Flow<List<TaskAppCrossRef>> {
       return crossRefDao.observeAllCrossRefs()
   }
   ```
4. **[`TaskAppEnforcementAdapter.kt`](file:///c:/Code/self-discipline-poc-01/app/src/main/java/com/example/selfdisciplinepoc01/domain/enforcement/TaskAppEnforcementAdapter.kt)**:
   ```kotlin
   // Observe task-app cross reference associations changes
   launch {
       coreDataRepository.observeAllCrossRefs().collect {
           recomputeSnapshot()
       }
   }
   ```
5. **[`CompleteTaskUseCase.kt`](file:///c:/Code/self-discipline-poc-01/app/src/main/java/com/example/selfdisciplinepoc01/domain/usecase/CompleteTaskUseCase.kt)** & **[`GetMissionHallTasksUseCase.kt`](file:///c:/Code/self-discipline-poc-01/app/src/main/java/com/example/selfdisciplinepoc01/domain/usecase/GetMissionHallTasksUseCase.kt)**:
   Inject `clock: Clock = SystemClockImpl()` và `zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }` vào constructor để đảm bảo tính nhất quán tuyệt đối về thời gian giữa Production và Testing.

---

## 8. BỘ TEST HỒI QUY (REGRESSION UNIT TEST SUITE)

Tạo mới tệp kiểm thử: **[`TaskAppEnforcementAdapterRegressionTest.kt`](file:///c:/Code/self-discipline-poc-01/app/src/test/java/com/example/selfdisciplinepoc01/domain/enforcement/TaskAppEnforcementAdapterRegressionTest.kt)** bao gồm 7 scenarios bắt buộc:

1. `test01_linkTaskUpdatesEnforcementState`: Kiểm tra việc liên kết task làm thay đổi trạng thái thực thi từ `LOCKED_BY_VAULT_NO_TASK` sang `LOCKED_INSUFFICIENT_TASKS` với $N=1$.
2. `test02_linkedTaskIsVisibleToAdapterImmediately`: Kiểm tra task vừa liên kết hiển thị ngay lập tức trong adapter mà không cần độ trễ.
3. `test03_incompleteLinkedTaskCausesLock`: Nhiệm vụ liên kết chưa hoàn thành khiến app bị khóa với `BusinessUnlockDecision.INSUFFICIENT_COMPLETION`.
4. `test04_completedLinkedTaskCausesAllow`: Nhiệm vụ liên kết hoàn thành trong chu kỳ ngày khiến app được mở với `BusinessUnlockDecision.UNLOCKED`.
5. `test05_removingLinkageRefreshesEnforcement`: Gỡ bỏ liên kết khiến app trở về trạng thái $N=0$ và bị khóa ngay lập tức.
6. `test06_reAddingAppDoesNotRestoreOldLinkage`: Gỡ app khỏi Vault rồi thêm lại đảm bảo không phục hồi liên kết cũ ($N=0$).
7. `test07_staleSnapshotCannotBypassNewLinkage`: Snapshot cũ không thể bị lợi dụng để bypass khi có liên kết mới chưa hoàn thành.

---

## 9. SỐ LIỆU KIỂM THỬ TRƯỚC VÀ SAU (TEST METRICS)

| Chỉ số kiểm thử | Trước Phase 27.1 | Sau Phase 27.1 | Độ lệch (Delta) | Trạng thái |
|:---|:---:|:---:|:---:|:---:|
| **Tổng số Unit Tests** | **352** | **359** | **+7 tests** | **100% PASS** |
| **Số Test Classes** | 25 | 26 | +1 class | Hoàn thành |
| **Test Failures** | 0 | 0 | 0 | Đạt chuẩn |
| **Test Errors** | 0 | 0 | 0 | Đạt chuẩn |
| **Test Skipped** | 0 | 0 | 0 | Đạt chuẩn |
| **Thời gian chạy testDebugUnitTest** | ~18s | ~14s | Tối ưu | Nhanh, ổn định |
| **Kết quả assembleDebug** | SUCCESS | SUCCESS | — | APK sẵn sàng |

### Chi tiết 26 Test Classes đã vượt qua:
1. `CoreDataRepositoryTest`: 10 tests
2. `DiagnosticObservabilityTest`: 12 tests
3. `TaskDomainTest`: 13 tests
4. `VaultDomainTest`: 22 tests
5. `CoreEnforcementIntegrationAuditTest`: 25 tests
6. `ProductFlowValidationTest`: 8 tests
7. `TaskAppEnforcementAdapterRegressionTest`: 7 tests *(MỚI)*
8. `TaskAppEnforcementIntegrationTest`: 19 tests
9. `TaskUnlockPolicyTest`: 6 tests
10. `BlockingShieldOverlayTest`: 8 tests
11. `ClockAndScheduleResilienceTest`: 18 tests
12. `ConcurrencyAndLifecycleHardeningTest`: 21 tests
13. `PolicyEngineTest`: 8 tests
14. `PolicyPrecedenceAndConflictTest`: 20 tests
15. `ProductionStateRecoveryTest`: 24 tests
16. `ProductionStressAndSoakTest`: 8 tests
17. `ScheduleEvaluatorTest`: 30 tests
18. `ScheduleWatcherTest`: 26 tests
19. `TargetRepositoryTest`: 11 tests
20. `BusinessDayProviderTest`: 12 tests
21. `CultivationDesignSystemTest`: 8 tests
22. `MainScreenViewModelTest`: 2 tests
23. `MissionHallViewModelTest`: 9 tests
24. `VaultViewModelTest`: 5 tests
25. `UsageLimitWatcherTest`: 16 tests
26. `UsageTrackerTest`: 11 tests

---

## 10. BẰNG CHỨNG KIỂM THỬ TRÊN THIẾT BỊ (DEVICE VALIDATION EVIDENCE)

- **Trích xuất cơ sở dữ liệu SQLite (`self_discipline_core.db`):**
  - Trích xuất qua script: `powershell -File scratch/pull_and_dump.ps1`
  - Đọc trực tiếp bằng: `python scratch/dump_db.py`
  - Xác nhận toàn vẹn các bảng `vault_apps`, `mission_tasks`, `task_app_cross_ref`, `daily_task_completions`.
- **Logcat thời gian thực:**
  - `scratch/logcat_scenario_a.txt`: Ghi nhận `BlockingShieldOverlay` xuất hiện sau 24.86ms, `LockScreenActivity` hiển thị sau 148.97ms.
  - `scratch/logcat_e.txt`: Ghi nhận `BlockingShieldOverlay` xuất hiện sau 59.35ms, `LockScreenActivity` hiển thị sau 137.48ms khi tái thu nạp app.
- **Ảnh chụp màn hình thiết bị (Screenshots Artifacts):**
  - `scratch/screen_scenario_a.png`: App mới trong Vault bị chặn với thông báo khóa.
  - `scratch/screen_scenario_c_allowed.png`: App mở thành công sau khi hoàn thành đủ nhiệm vụ.
  - `scratch/screen_d_allowed.png`: App mở tự do sau khi gỡ khỏi Vault.
  - `scratch/screen_e_locked.png`: App bị khóa ngay lập tức khi thêm lại vào Vault mà chưa có task.

---

## 11. XÁC MINH BẢO MẬT & QUYỀN ƯU TIÊN (OPEN-04)

- Đã xác minh bằng cả kiểm thử đơn vị (`CoreEnforcementIntegrationAuditTest` kịch bản 1–4, `ProductFlowValidationTest` kịch bản 4) và kiểm thử kiến trúc:
  - Khi `PolicyEngine.evaluate(packageName)` trả về `PolicyDecision.LOCK`:
    - Dù ứng dụng trong Bảo Khố đã hoàn thành 100% nhiệm vụ liên kết, quyết định cuối cùng vẫn **BẮT BUỘC LÀ LOCK** (`EnforcementReason.LOCKED_BY_POLICY`).
  - Không có bất kỳ ngoại lệ nào cho phép bypass Technical Hard Ceiling.

---

## 12. XÁC MINH RANH GIỚI CHU KỲ NGÀY 04:00 (BUSINESS CYCLE)

- Ranh giới ngày được quản lý chặt chẽ bởi `BusinessDayProvider`:
  - 03:59:59 của ngày hôm sau vẫn được tính vào chu kỳ của ngày hôm trước.
  - 04:00:00 chính thức bắt đầu chu kỳ ngày mới, làm mất hiệu lực toàn bộ trạng thái hoàn thành nhiệm vụ của chu kỳ trước đối với việc mở khóa ngày mới.
  - Cơ chế đã được chứng minh qua test `testEndToEnd_Scenario5_NewBusinessCycle_0400_Reset`.

---

## 13. TỐC ĐỘ ĐÁNH GIÁ ĐỒNG BỘ CỦA ACCESSIBILITY SERVICE

- `evaluateSync(packageName)` đọc hoàn toàn từ bộ nhớ RAM thông qua cấu trúc `EnforcementSnapshot` bất biến:
  - Độ phức tạp tính toán: $O(1)$.
  - Thời gian thực thi trung bình đo được: $< 0.03\text{ ms}$.
  - Không có lời gọi I/O đĩa, không có coroutine blocking trên Main Thread của Accessibility Service.

---

## 14. TÌNH TRẠNG CÁC MỤC THIẾT KẾ MỞ (OPEN ITEMS STATUS)

Tuân thủ nghiêm ngặt nguyên tắc quản trị, các mục sau **TIẾP TỤC ĐƯỢC GIỮ NGUYÊN TRẠNG THÁI OPEN**:
- **OPEN-02:** Streaks & Consecutive Days.
- **OPEN-03:** Emergency Unseal Protocol.
- **OPEN-05:** Backend Architecture & Room Schema Migrations Proposal.
- **OPEN-06:** AI Integration Scope.
- **OPEN-07:** Multi-Device Synchronization & Conflict Resolution.

---

## 15. CÁC BƯỚC TIẾP THEO (NEXT STEPS)

1. Duy trì tính ổn định của cơ chế quan sát Flow phản ứng toàn diện.
2. Sẵn sàng cho giai đoạn tối ưu hóa trải nghiệm người dùng (UI/UX transition, micro-animations) của Nhiệm Vụ Đường và Bảo Khố khi bước vào Phase tiếp theo.

---

## 16. THÔNG TIN PHÊ DUYỆT & COMMIT (SIGN-OFF & COMMIT INFO)

- **Người thực hiện:** Antigravity AI Agent
- **Chi nhánh Git:** `main`
- **Cam kết:** Tự động đồng bộ hóa lên GitHub Remote (`origin/main`) ngay sau khi tạo báo cáo.
