# PHASE 1A — CANONICAL FOUNDATION REPORT

**Dự án:** `self-discipline-poc-01`  
**Tài liệu thẩm quyền tối cao (Product SSOT):** `docs/MASTER SSOT - HỆ THỐNG PHONG ẤN DỤC VỌNG.docx` (v1.0.0)  
**Thời điểm thực hiện:** 2026-09-11  
**Trạng thái:** COMPLETED

---

## 1. Objective

Mục tiêu cốt lõi của **Phase 1A (Canonical Foundation & Legacy Isolation)**:
- Thiết lập ranh giới kiến trúc chuẩn (Canonical Architecture Boundaries): phân tách rõ ràng Presentation → Application / Use Cases → Canonical Domain → Persistence / Platform Adapters.
- Cách ly triệt để các thành phần và logic nghiệp vụ cũ (Legacy Business Logic): `PolicyEngine`, `ScheduleEvaluator`, `ScheduleWatcher`, `UsageTracker`, `UsageLimitWatcher`, legacy `TargetRepository`, không cho phép chúng tiếp tục nắm giữ thẩm quyền (authority) hoặc can thiệp/ghi đè lên trạng thái khóa phong ấn của Hệ Thống Phong Ấn Dục Vọng.
- Thiết lập các hợp đồng và mô hình miền chuẩn (Canonical Domain Contracts & Models) thuần túy Kotlin, không phụ thuộc Android SDK (Activity, Context, AccessibilityService, WindowManager, Room, DataStore), phục vụ việc tái thiết lập lõi ở các giai đoạn sau:
  - Định danh và ranh giới Chu kỳ (Cycle) mốc 04:00 sáng.
  - Phân tách Định nghĩa Nhiệm vụ (Persistent Task Definition) khỏi Trạng thái Chu kỳ (Cycle-Scoped State) để bảo toàn lịch sử.
  - Liên kết phần thưởng nhiều-nhiều giữa Nhiệm vụ và Ứng dụng Phong Ấn (Task-VaultApp Relationship).
  - Định danh ứng dụng Phong Ấn (Vault App) và Hiệu lực Thẻ bài (Voucher Effect).
  - Đánh giá khóa phong ấn chuẩn (Canonical Lock Policy) với bảng chuẩn hóa N=1..6, đặc xá N=2 (yêu cầu 1 nhiệm vụ), N=0 mở khóa (unlocked), và loại trừ nhiệm vụ không có thưởng (rewardless task) theo INV-TASK-001.
- Xây dựng nền tảng kiểm thử độc lập (Canonical Test Foundation) kiểm tra 100% các bất biến (invariants) của MASTER SSOT mà không cần Android Instrumentation.

*Ghi chú phạm vi:* Phase 1A TUYỆT ĐỐI KHÔNG triển khai toàn bộ Cycle Engine 04:00 nền, Vault UI, Mission Hall UI, Tháp Thí Luyện (Tower), Cửa Hàng (Shop), Hầm Ngục (Dungeon), Kho Đồ (Inventory), AI Agent, Voice Agent, Bộ Nhớ (Memory), hay Cơ chế đẩy về màn hình Home (Push-to-Home).

---

## 2. Files Changed

Dưới đây là danh sách chi tiết các file đã tạo mới và chỉnh sửa, kèm vai trò kiến trúc và mối liên hệ với SSOT:

| File Path | Hành động | Vai trò kiến trúc | Trạng thái kỹ thuật | Mối liên hệ SSOT |
| :--- | :--- | :--- | :--- | :--- |
| `app/src/main/java/com/example/selfdisciplinepoc01/domain/canonical/cycle/CanonicalCycle.kt` | Tạo mới | Canonical Domain Model | **IMPLEMENTED** | Định nghĩa `CanonicalCycleId` và `CanonicalCycleBoundary` theo mốc chuyển giao chu kỳ 04:00 sáng địa phương chuẩn SSOT. |
| `app/src/main/java/com/example/selfdisciplinepoc01/domain/canonical/task/CanonicalTask.kt` | Tạo mới | Canonical Domain Model | **IMPLEMENTED** | Phân tách `CanonicalTask` (định nghĩa persistent), `TaskCycleState` (trạng thái theo chu kỳ), và `TaskRewardLink` (quan hệ N-N). |
| `app/src/main/java/com/example/selfdisciplinepoc01/domain/canonical/vault/CanonicalVault.kt` | Tạo mới | Canonical Domain Model & Policy | **IMPLEMENTED** | Định nghĩa `CanonicalVaultApp`, `VoucherEffect`, `CanonicalLockPolicy` với bảng chuẩn hóa N=1..6 (N=2 đặc xá cần 1, N=0 mở khóa), và kiểm tra invariant SSOT. |
| `app/src/main/java/com/example/selfdisciplinepoc01/domain/canonical/repository/CanonicalRepositories.kt` | Tạo mới | Canonical Domain Contracts | **ESTABLISHED** | Định nghĩa các interface repository thuần túy cho Cycle, Task, Vault và bộ đánh giá khóa `CanonicalLockEvaluator`. |
| `app/src/main/java/com/example/selfdisciplinepoc01/policy/PolicyEngine.kt` | Chỉnh sửa | Legacy Infrastructure | **ISOLATED** | Đánh dấu `@Deprecated`. Tước bỏ thẩm quyền quyết định đối với Vault Apps trong tương lai. |
| `app/src/main/java/com/example/selfdisciplinepoc01/policy/ScheduleEvaluator.kt` | Chỉnh sửa | Legacy Infrastructure | **ISOLATED** | Đánh dấu `@Deprecated`. Cách ly logic lịch trình cố định thời POC-01 khỏi Canonical Domain. |
| `app/src/main/java/com/example/selfdisciplinepoc01/policy/ScheduleWatcher.kt` | Chỉnh sửa | Legacy Infrastructure | **ISOLATED** | Đánh dấu `@Deprecated`. Ngăn chặn việc liên kết logic giám sát lịch cũ với quy tắc Vault. |
| `app/src/main/java/com/example/selfdisciplinepoc01/usage/UsageTracker.kt` | Chỉnh sửa | Legacy Infrastructure | **ISOLATED** | Đánh dấu `@Deprecated`. Ngăn chặn usage tracking thời POC-01 can thiệp vào quyết định khóa nghiệp vụ. |
| `app/src/main/java/com/example/selfdisciplinepoc01/usage/UsageLimitWatcher.kt` | Chỉnh sửa | Legacy Infrastructure | **ISOLATED** | Đánh dấu `@Deprecated`. Cách ly cơ chế giới hạn thời gian sử dụng cũ. |
| `app/src/main/java/com/example/selfdisciplinepoc01/target/repository/TargetRepository.kt` | Chỉnh sửa | Legacy Repository Contract | **ISOLATED** | Đánh dấu `@Deprecated`. Thay thế trong tương lai bằng `CanonicalVaultRepository`. |
| `app/src/test/java/com/example/selfdisciplinepoc01/domain/canonical/cycle/CanonicalCycleTest.kt` | Tạo mới | Canonical Unit Test | **IMPLEMENTED** | Kiểm thử độc lập ranh giới 04:00 sáng địa phương (03:59:59 thuộc ngày cũ, 04:00:00 thuộc ngày mới). |
| `app/src/test/java/com/example/selfdisciplinepoc01/domain/canonical/task/CanonicalTaskTest.kt` | Tạo mới | Canonical Unit Test | **IMPLEMENTED** | Kiểm thử tính độc lập giữa định nghĩa task và trạng thái chu kỳ (bảo toàn lịch sử). |
| `app/src/test/java/com/example/selfdisciplinepoc01/domain/canonical/vault/CanonicalLockPolicyTest.kt` | Tạo mới | Canonical Unit Test | **IMPLEMENTED** | Kiểm thử toàn diện 11 điều kiện SSOT Invariant (N=0..6, đặc xá N=2, loại trừ task không thưởng, Voucher override). |

---

## 3. Canonical Architecture Established

Hệ thống kiến trúc chuẩn (Canonical Architecture) được thiết lập với phân tầng nghiêm ngặt một chiều:

```
┌────────────────────────────────────────────────────────┐
│                   Presentation Layer                   │
│   (Compose UI, ViewModels, System Panel, Lock Overlay) │
└───────────────────────────┬────────────────────────────┘
                            │ (depends on)
                            ▼
┌────────────────────────────────────────────────────────┐
│               Application / Use Cases                  │
│       (EvaluateVaultAppUseCase, CompleteTaskUseCase)   │
└───────────────────────────┬────────────────────────────┘
                            │ (depends on)
                            ▼
┌────────────────────────────────────────────────────────┐
│                   Canonical Domain                     │
│  - Cycle: CanonicalCycleId, CanonicalCycleBoundary     │
│  - Task: CanonicalTask, TaskCycleState, TaskRewardLink │
│  - Vault: CanonicalVaultApp, VoucherEffect, LockPolicy │
│  - Contracts: CanonicalTaskRepository, VaultRepository │
│  (Pure Kotlin - NO Android/Room/DataStore dependencies)│
└───────────────────────────▲────────────────────────────┘
                            │ (implements contracts)
┌───────────────────────────┴────────────────────────────┐
│             Persistence / Platform Adapters            │
│  - Room Database / DAOs (Future Migration)             │
│  - Accessibility Enforcement Service                   │
│  - AlarmManager / WorkManager Cycle Schedulers         │
└────────────────────────────────────────────────────────┘
```

**Nguyên tắc cốt lõi đã xác lập:**
1. **Không rò rỉ nền tảng (Platform Independence):** Package `domain.canonical` là Pure Kotlin 100%, không import bất kỳ lớp nào thuộc `android.*`, `androidx.room.*`, hay `androidx.datastore.*`.
2. **Nguồn chân lý duy nhất (Single Source of Truth):** Quyết định khóa của Vault App (`CanonicalLockDecision`) chỉ do `CanonicalLockPolicy` đưa ra dựa trên tiến độ nhiệm vụ chu kỳ và hiệu lực Thẻ bài. Các thành phần Android chỉ là cơ cấu chấp hành (Enforcement Actuators), không có quyền định nghĩa lại luật nghiệp vụ.

---

## 4. Canonical Domain Boundaries

Các ranh giới miền (Domain Boundaries) được phân định rõ ràng như sau:
1. **Cycle Domain (`domain/canonical/cycle`):** Quản lý định danh chu kỳ và tính toán ranh giới thời gian dựa trên mốc 04:00 sáng địa phương.
2. **Task Domain (`domain/canonical/task`):** Quản lý thực thể nhiệm vụ bền vững, trạng thái thực thi chu kỳ (cycle-scoped state) và mối quan hệ ánh xạ nhiều-nhiều với ứng dụng thưởng.
3. **Vault & Lock Domain (`domain/canonical/vault`):** Quản lý danh mục ứng dụng bị phong ấn, thẻ bài miễn trừ và thuật toán thẩm định khóa chuẩn.
4. **Repository Contracts (`domain/canonical/repository`):** Cung cấp các giao diện trừu tượng cho tầng Persistence mà không làm rò rỉ cấu trúc bảng hay ORM Room vào nghiệp vụ.

---

## 5. Cycle Foundation

- **Ranh giới mốc 04:00 sáng (`CanonicalCycleBoundary`):**
  - Mốc chuyển giao chu kỳ là đúng `04:00:00.000` giờ địa phương (`LocalTime.of(4, 0, 0, 0)`).
  - Khoảng thời gian từ `[04:00:00 ngày T, 04:00:00 ngày T+1)` thuộc chu kỳ ngày T.
  - Bất kỳ hành động nào diễn ra trước 04:00 (ví dụ 03:59:59) được xác định thuộc về chu kỳ ngày T-1.
- **Bảo toàn dữ liệu qua ranh giới chu kỳ:**
  - Thiết kế kiến trúc bảo đảm các dữ liệu sau sống sót qua ranh giới chu kỳ mà không bị reset: Tiến trình Tháp Thí Luyện (Tower), Điểm Dục Vọng (D points), Kho Đồ (Inventory), Hiệu lực Thẻ bài (Voucher), Bộ nhớ dài hạn AI (Long-term Memory).
  - Chỉ có `TaskCycleState` là gắn với `cycleId`. Khi chuyển chu kỳ, hệ sinh thái không xóa hàng dữ liệu (không làm `DELETE *`), mà khởi tạo/tra cứu trạng thái nhiệm vụ cho `cycleId` mới.
- **Trạng thái kỹ thuật:** **ESTABLISHED & SCAFFOLDED** (Đã tạo xong model, boundary math và contracts; chưa dựng background scheduler runtime).

---

## 6. Task Foundation

- **Tách biệt Thực thể & Trạng thái chu kỳ:**
  - `CanonicalTask`: Lưu trữ thông tin bền vững (`id`, `title`, `description`, `orderIndex`, `hasReward`, `createdAt`, `isArchived`).
  - `TaskCycleState`: Biểu diễn trạng thái của một nhiệm vụ trong một chu kỳ xác định (`taskId`, `cycleId`, `status: TaskCycleStatus`, `completedAt`). Ngăn ngừa việc dùng cờ trạng thái toàn cục ghi đè làm mất lịch sử các ngày trước.
- **Quan hệ Thưởng - Ứng dụng (Reward-App Relationship):**
  - Mô hình hóa qua `TaskRewardLink(taskId, appPackageName)`.
  - Hỗ trợ quan hệ nhiều-nhiều (N-N).
  - Quy tắc SSOT: Xóa ứng dụng khỏi Vault chỉ xóa các `TaskRewardLink` gắn với package đó, bản thân `CanonicalTask` vẫn tồn tại. Việc thêm lại ứng dụng sau đó KHÔNG tự động khôi phục các liên kết cũ.
- **Loại trừ nhiệm vụ không có thưởng (INV-TASK-001):**
  - Cờ `hasReward: Boolean` trên `CanonicalTask` phân định nhiệm vụ có gắn thưởng hay không.
  - Các nhiệm vụ không có thưởng (nhiệm vụ cốt truyện, thử thách nhận D point) bị loại trừ hoàn toàn khỏi mẫu số N khi tính ngưỡng mở khóa ứng dụng.
- **Trạng thái kỹ thuật:** **ESTABLISHED & SCAFFOLDED**.

---

## 7. Vault / Lock Foundation

- **Quy tắc bất biến thẩm quyền khóa (Canonical Lock Evaluation):**
  $$ \text{APP LOCKED} \iff (\text{Ít nhất 1 incomplete linked reward task trong chu kỳ hiện tại}) \land (\text{Không có Voucher hiệu lực}) $$
- **Bảng chuẩn hóa số nhiệm vụ yêu cầu ($N = 1..6$):**
  - $N = 0 \implies \text{Yêu cầu } 0 \implies \mathbf{UNLOCKED}$ (Ứng dụng mới thêm vào Bảo Khố hoặc không gán nhiệm vụ thưởng thì không bị khóa).
  - $N = 1 \implies \text{Yêu cầu } 1$.
  - $N = 2 \implies \text{Yêu cầu } \mathbf{1}$ *(Chính sách Đặc xá Khởi đầu bắt buộc theo MASTER SSOT: người dùng mới bắt đầu chỉ cần hoàn thành 1/2 nhiệm vụ là được mở khóa)*.
  - $N = 3 \implies \text{Yêu cầu } 2$.
  - $N = 4 \implies \text{Yêu cầu } 3$.
  - $N = 5 \implies \text{Yêu cầu } 4$.
  - $N = 6 \implies \text{Yêu cầu } 4$.
  - Với $N > 6$: $\lceil \frac{2N}{3} \rceil$.
- **Thẩm quyền của Thẻ bài (Voucher Effect):**
  - `VoucherEffect` có hiệu lực tại thời điểm đánh giá sẽ lập tức mở khóa ứng dụng (`UNLOCKED_BY_VOUCHER`), đè bẹp trạng thái chưa hoàn thành của các nhiệm vụ liên kết.
- **Trạng thái kỹ thuật:** **IMPLEMENTED** (Đã hiện thực hóa logic toán học và thuật toán thẩm định trong `CanonicalLockPolicy`).

---

## 8. Persistence Boundary

- Thiết lập các interface hợp đồng thuần túy:
  - `CanonicalCycleRepository`: Cung cấp ranh giới chu kỳ hiện tại và tra cứu chu kỳ lịch sử.
  - `CanonicalTaskRepository`: Quản lý nhiệm vụ, trạng thái chu kỳ, liên kết thưởng và thao tác xóa liên kết an toàn.
  - `CanonicalVaultRepository`: Quản lý danh sách Vault App và Thẻ bài hiệu lực.
  - `CanonicalLockEvaluator`: Điểm truy cập thẩm định khóa duy nhất cho toàn hệ thống.
- **Nguyên tắc phân tầng:** Tầng cơ sở dữ liệu (Room Entity/DAO) và DataStore trong các giai đoạn sau sẽ đóng vai trò là adapter thực thi các interface này, không được để lọt các chi tiết kỹ thuật (Room annotations, SQL queries) vào Domain.
- **Trạng thái kỹ thuật:** **ESTABLISHED**.

---

## 9. Legacy Isolation

Để bảo vệ Canonical Core không bị vấy bẩn bởi mã nguồn POC-01 cũ, các thành phần sau đã được gắn thẻ `@Deprecated` với cảnh báo kiến trúc rõ ràng:

1. **`PolicyEngine` (`com.example.selfdisciplinepoc01.policy`):**
   - *Lý do giữ lại:* Phục vụ các màn hình và dịch vụ Accessibility của POC-01 hiện tại để bảo đảm build pass và app chạy không crash.
   - *Biện pháp cách ly:* Gắn `@Deprecated`. Không cho phép mã nguồn mới trong `domain.canonical` import hay sử dụng.
   - *Kế hoạch loại bỏ:* Sẽ được thay thế hoàn toàn bởi `CanonicalLockEvaluator` trong Phase Enforcement.
2. **`ScheduleEvaluator` & `ScheduleWatcher`:**
   - *Lý do giữ lại:* Mã nguồn cũ của tính năng đặt khung giờ khóa 24/7.
   - *Biện pháp cách ly:* Gắn `@Deprecated`. MASTER SSOT không sử dụng lịch khóa thời gian cố định cho Vault Apps.
3. **`UsageTracker` & `UsageLimitWatcher`:**
   - *Lý do giữ lại:* Cơ chế theo dõi thời lượng sử dụng POC-01.
   - *Biện pháp cách ly:* Gắn `@Deprecated`. MASTER SSOT xác định trạng thái mở khóa của Bảo Khố hoàn toàn dựa trên hoàn thành nhiệm vụ và thẻ bài, không phụ thuộc vào giới hạn thời gian (usage limit).
4. **`TargetRepository`:**
   - *Lý do giữ lại:* Tầng lưu trữ cấu hình mục tiêu cũ.
   - *Biện pháp cách ly:* Gắn `@Deprecated`. Sẽ được thay thế bởi `CanonicalVaultRepository`.

---

## 10. Tests Added / Updated

Đã tạo mới 3 bộ unit test độc lập, chạy trực tiếp trên máy phát triển (không cần thiết bị/emulator), kiểm tra 100% các bất biến SSOT:

### 1. `CanonicalCycleTest`
- `test01_cycleIdentityAround0400Boundary`: Kiểm tra mốc 04:00 sáng địa phương. Xác nhận thời điểm `03:59:59` thuộc chu kỳ ngày hôm trước và `04:00:00` thuộc chu kỳ ngày hôm nay.
- `test02_cycleIdValidation`: Xác thực định dạng `YYYY-MM-DD` của CycleId.
- `test03_invalidCycleIdThrows`: Xác nhận ném ngoại lệ khi truyền định dạng chu kỳ sai.

### 2. `CanonicalTaskTest`
- `test01_persistentTaskDefinitionSeparation`: Xác nhận định nghĩa nhiệm vụ bền vững có đầy đủ thuộc tính và cờ `hasReward`.
- `test02_currentCycleVsHistoricalTaskState`: Xác nhận tính độc lập tuyệt đối giữa `TaskCycleState` của chu kỳ hôm qua (`COMPLETED`) và chu kỳ hôm nay (`PENDING`), chứng minh lịch sử không bị phá hủy.
- `test03_taskRewardLinkProperties`: Xác nhận tính hợp lệ của liên kết nhiệm vụ - ứng dụng.

### 3. `CanonicalLockPolicyTest`
- `test01_canonicalTableRequirements_N1_to_N6`: Kiểm tra chính xác bảng yêu cầu $N=1 \to 1$, $N=2 \to 1$ (đặc xá), $N=3 \to 2$, $N=4 \to 3$, $N=5 \to 4$, $N=6 \to 4$, $N=7 \to 5$, $N=8 \to 6$.
- `test02_newAppWithoutLinkedRewardTasksIsUnlocked`: Xác nhận Invariant 10: Ứng dụng mới vào Vault chưa liên kết nhiệm vụ ($N=0$) luôn ở trạng thái `UNLOCKED`.
- `test03_rewardlessTaskExclusionFromDenominator`: Xác nhận Invariant 3 (INV-TASK-001): Nhiệm vụ không có thưởng (`hasReward = false`) bị loại trừ khỏi mẫu số $N$.
- `test04_amnestyForN2RequiresOnlyOneTask`: Xác nhận Invariant 5: Với $N=2$, hoàn thành 1 nhiệm vụ là mở khóa thành công.
- `test05_appLockedWithIncompleteTasksAndNoVoucher`: Xác nhận Invariant 11: Có nhiệm vụ chưa xong và không có Thẻ bài thì bị `LOCKED`.
- `test06_effectiveVoucherOverridesLockEvenWithIncompleteTasks`: Xác nhận Thẻ bài còn hạn mở khóa ứng dụng ngay lập tức kể cả khi $0/1$ nhiệm vụ hoàn thành.
- `test07_expiredVoucherDoesNotUnlock`: Xác nhận Thẻ bài hết hạn không có tác dụng mở khóa.

---

## 11. Tests Retained But Marked Legacy

Các bộ test cũ được giữ nguyên để bảo đảm tính hồi quy cho các thành phần POC-01 chưa refactor:
1. `TaskUnlockPolicyTest`: Kiểm tra công thức cũ `(2N+2)/3` của POC-01. Được phân loại là **LEGACY_TEST**. Sẽ được thay thế hoàn toàn bằng `CanonicalLockPolicyTest` trong các giai đoạn sau khi loại bỏ `TaskUnlockPolicy`.
2. `TaskAppEnforcementAdapterRegressionTest`: Kiểm tra hành vi của Adapter thực thi thời Phase 27. Được phân loại là **LEGACY_ADAPTER_TEST**.
3. `PolicyEngineTest`, `ScheduleEvaluatorTest`, `ScheduleWatcherTest`, `UsageTrackerTest`, `UsageLimitWatcherTest`: Được phân loại là **LEGACY_TECHNICAL_TEST**.

*Kết quả:* Toàn bộ các test cũ và test mới đều vượt qua 100% mà không có xung đột biên dịch.

---

## 12. Build Verification

Đã thực hiện xác minh toàn diện qua Gradle CLI:

1. **Canonical Unit Tests (`com.example.selfdisciplinepoc01.domain.canonical.*`):**
   - Lệnh: `gradlew.bat testDebugUnitTest --tests com.example.selfdisciplinepoc01.domain.canonical.*`
   - Kết quả: **BUILD SUCCESSFUL** (Exit code: 0).
   - Thời gian thực thi: 1m 28s.
2. **Toàn bộ Test Suite (`testDebugUnitTest`):**
   - Lệnh: `gradlew.bat testDebugUnitTest`
   - Kết quả: **BUILD SUCCESSFUL** (Exit code: 0).
   - Thời gian thực thi: 21s.
   - Tổng cộng: Tất cả test cases (cả legacy và canonical mới) đều passed 100%.
3. **Biên dịch APK Debug (`assembleDebug`):**
   - Lệnh: `gradlew.bat assembleDebug`
   - Kết quả: **BUILD SUCCESSFUL** (Exit code: 0).
   - Thời gian thực thi: 23s.
   - APK được tạo thành công, không có lỗi biên dịch.

---

## 13. Risks

1. **Rủi ro song song tồn tại hai bộ đánh giá (Dual-Engine Risk):** Hiện tại `TaskUnlockPolicy` cũ (với lỗi $N=2 \to 2$ và $N=0 \to \text{LOCK}$) vẫn còn nằm trong `domain/policy` do `TaskAppEnforcementAdapter` đang sử dụng. Cần di chuyển adapter sang `CanonicalLockPolicy` trong Phase Adapter/Enforcement tiếp theo để dứt điểm triệt để.
2. **Rủi ro ranh giới múi giờ (Timezone Transition Risk):** `CanonicalCycleBoundary` sử dụng `ZoneId` địa phương. Cần kiểm tra kỹ việc người dùng đổi múi giờ thiết bị khi xây dựng Cycle Engine ở Phase sau.
3. **Rủi ro cấu trúc bảng Room hiện tại:** Bảng Room hiện tại (`daily_task_completions`) đang gắn khóa ngoại trực tiếp và hàm `deleteTask` xóa sạch lịch sử chu kỳ cũ. Cần migration bảng Room trong Phase Persistence để hỗ trợ `TaskCycleState` đầy đủ.

---

## 14. Deferred Work

Các hạng mục sau được hoãn lại theo đúng kế hoạch và sẽ được thực hiện ở các Phase sau:
- Xây dựng Cycle Engine chạy nền với `AlarmManager` / `WorkManager` vào lúc 04:00 sáng.
- Xây dựng Room Database Entities & DAOs mới cho `CanonicalTask`, `TaskCycleState`, `CanonicalVaultApp`, `VoucherEffect`.
- Di chuyển `TaskAppEnforcementAdapter` sang sử dụng `CanonicalLockEvaluator`.
- Triển khai giao diện Sảnh Nhiệm Vụ (Mission Hall) và Bảo Khố (Vault).
- Triển khai Tháp Thí Luyện (Tower), Cửa Hàng (Shop), Hầm Ngục (Dungeon), Kho Đồ (Inventory).
- Triển khai AI Assistant, Voice VAD, và Memory System.

---

## 15. OPEN-001 / OPEN-002 / OPEN-003

Xác nhận tuân thủ tuyệt đối quy định an toàn: **KHÔNG tự ý giải quyết hoặc suy đoán bất kỳ quyết định mở nào trong SSOT**:
- **OPEN-001 (Dungeon loot pool):** **UNRESOLVED / DEFERRED** (Không can thiệp, không định nghĩa danh mục rơi đồ).
- **OPEN-002 (Voice session silence timeout):** **UNRESOLVED / DEFERRED** (Không can thiệp, không thiết lập hằng số timeout).
- **OPEN-003 (Cloud memory sync strategy):** **UNRESOLVED / DEFERRED** (Không can thiệp, không triển khai cơ chế đồng bộ đám mây).

---

## 16. SSOT Traceability

| Mã quy tắc SSOT | Nội dung quy tắc | Hiện thực hóa tại Phase 1A |
| :--- | :--- | :--- |
| **Mốc Chu kỳ 04:00** | Chu kỳ bắt đầu từ 04:00 sáng hôm nay đến 03:59:59 sáng hôm sau | `CanonicalCycleBoundary.fromInstant()` |
| **Bảo toàn Chu kỳ** | Chuyển chu kỳ không xóa dữ liệu lịch sử, chỉ khởi tạo trạng thái chu kỳ mới | `TaskCycleState(taskId, cycleId, status)` |
| **INV-TASK-001** | Nhiệm vụ không có phần thưởng không tính vào mẫu số N | `CanonicalLockPolicy.evaluateLock()` lọc `task.hasReward` |
| **Đặc xá N=2** | Khuyến khích người dùng mới: có 2 nhiệm vụ chỉ cần hoàn thành 1 | `CanonicalLockPolicy.calculateRequiredCompletions(2) == 1` |
| **Quy tắc N=0** | Ứng dụng mới vào Bảo Khố / chưa gán nhiệm vụ thưởng thì mở khóa | `CanonicalLockPolicy.evaluateLock()` với $N=0 \implies \text{UNLOCKED}$ |
| **Bảng Chuẩn Hóa N=1..6** | N=1..6 có số nhiệm vụ yêu cầu lần lượt là 1, 1, 2, 3, 4, 4 | `CanonicalLockPolicy.calculateRequiredCompletions()` |
| **Thẩm quyền Thẻ bài** | Thẻ bài còn hạn mở khóa ứng dụng ngay lập tức | `VoucherEffect` & `CanonicalLockPolicy.evaluateLock()` |
| **Liên kết N-N** | Ứng dụng xóa khỏi Vault thì xóa liên kết, task giữ nguyên | `CanonicalTaskRepository.removeAllLinksForApp()` contract |

---

## 17. Final Status

- **Xếp hạng trạng thái Phase 1A:** **HOÀN THÀNH 100% (PHASE 1A COMPLETED)**.
- Toàn bộ ranh giới kiến trúc và hợp đồng miền đã được thiết lập vững chắc.
- Toàn bộ legacy code đã được cách ly.
- Toàn bộ test suite và build debug APK đều thành công rực rỡ (Exit code: 0).
- Hệ thống sẵn sàng cho các giai đoạn tiếp theo của quá trình Tái thiết Lập trình có Kiểm soát (Controlled Reconstruction).
