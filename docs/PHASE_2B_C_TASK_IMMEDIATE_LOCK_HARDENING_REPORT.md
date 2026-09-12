# BÁO CÁO NGHIỆM THU PHASE 2B-C: CANONICAL TASK MUTATION, REWARD LINK & IMMEDIATE LOCK RECOMPUTATION HARDENING

Project: `self-discipline-poc-01`  
Authoritative SSOT: `docs/MASTER SSOT - HỆ THỐNG PHONG ẤN DỤC VỌNG.docx`  
Device kiểm thử vật lý chuẩn: **vivo iQOO Neo 10 (V2425A)**  
Device Serial: `10CF3J1F3400238`  
Hệ điều hành: Android 15 (API 35, VanillaIceCream) / OriginOS 15 (`PD2425_A_15.0.18.8.W10.V000L1`)  
Ngày lập báo cáo: 12/09/2026  

---

## 1. Executive Summary

Giai đoạn Phase 2B-C được kích hoạt nhằm giải quyết triệt để lỗi runtime nghiêm trọng do người dùng phát hiện:
- **Hiện tượng lỗi ban đầu**: Người dùng thêm một ứng dụng mới vào Vault -> Tạo một nhiệm vụ mới -> Liên kết ứng dụng đó làm reward với nhiệm vụ -> Không chọn "Áp dụng chu kỳ tiếp theo" (chọn áp dụng ngay lập tức) -> Ứng dụng **KHÔNG** bị khóa ngay lập tức mà vẫn dùng được bình thường, hoặc phải đợi chuyển chu kỳ 04:00, hoặc phải tắt mở lại ứng dụng.
- **Mục tiêu tối thượng**: Thực hiện hợp đồng chuẩn tắc: Nếu mutation là `IMMEDIATE_CURRENT_CYCLE`, ngay sau khi transaction DB commit thành công, `CanonicalLockEvaluator` phải recompute tức thì, snapshot in-memory runtime được cập nhật, `AppDetectorAccessibilityService` nhận diện trạng thái LOCK và ứng dụng mục tiêu bị chặn ngay lập tức. Ngược lại, nếu là `PENDING_NEXT_CYCLE`, cấu hình được lưu tạm và chu kỳ hiện tại tuyệt đối không bị thay đổi.
- **Kết quả nghiệm thu**: 
  - Đã triển khai đầy đủ kiến trúc atomic mutation và `CanonicalMutationSyncManager`.
  - Toàn bộ 24/24 unit/regression tests chuẩn (`TASK-IMM-01` đến `TASK-IMM-24`) đạt **PASS 100%**.
  - Toàn bộ 5 test case thiết bị vật lý thật (`REAL-IMM-01`, `REAL-IMM-02`, `REAL-IMM-03`, `REAL-NEXT-01`, `REAL-AI-01`) trên vivo iQOO Neo 10 đạt **PASS 100%** với đầy đủ bằng chứng logcat và ảnh chụp màn hình thực tế.

---

## 2. SSOT Rules Used

1. **Nguyên tắc Immediate Mutation**:
   - Nếu user/AI không chọn "áp dụng chu kỳ tiếp theo", thay đổi reward/task có hiệu lực ngay trong chu kỳ hiện tại (`CURRENT CYCLE`).
   - Với $N$ task liên kết, số task cần hoàn thành $Required(N)$ được tính lại ngay. Nếu $K < Required(N)$ và không có voucher hiệu lực $\rightarrow$ ứng dụng phải bị **LOCKED ngay lập tức**.
2. **Nguyên tắc Next-Cycle Mutation**:
   - Nếu user chọn "áp dụng chu kỳ tiếp theo", chu kỳ hiện tại giữ nguyên cấu hình reward có hiệu lực.
   - Cấu hình mới được lưu vào `pendingNextCycleRewards`.
   - Tại mốc 04:00 AM (`CycleTransitionManager`), hệ thống tự động apply cấu hình pending, cập nhật chu kỳ mới và tính toán lại khóa.
3. **Công thức chuẩn tắc $Required(N)$**:
   $$Required(0) = 0$$
   $$Required(1) = 1$$
   $$Required(2) = 1$$
   $$Required(3) = 2$$
   $$Required(4) = 3$$
   $$Required(5) = 4$$
   $$Required(6) = 4$$
   $$Required(N > 6) = \lceil 2N / 3 \rceil$$
4. **Quy tắc Voucher**:
   - Nếu có voucher hiệu lực cho ứng dụng, ứng dụng được phép mở (`ALLOW`) bất chấp $K < Required(N)$. Khi voucher hết hạn, evaluator phải lập tức đánh giá lại theo $K < Required(N) \rightarrow \text{LOCK}$.
5. **Quy tắc Xóa và Thêm lại ứng dụng (Re-add)**:
   - Khi xóa app khỏi Vault, toàn bộ liên kết bị hủy. Khi thêm lại, app bắt đầu với $N=0 \rightarrow \text{UNLOCKED}$, tuyệt đối không tự động phục hồi các liên kết cũ.

---

## 3. Root Cause of Previous Bug

Qua kiểm toán forensic toàn diện mã nguồn trước khi sửa đổi:
1. **Thiếu tính nguyên tử (Atomicity Gap)**: Quá trình tạo task và liên kết reward diễn ra qua nhiều câu lệnh DAO rời rạc. Task được tạo trước, link được thêm sau, không nằm trong cùng một Room transaction nguyên tử.
2. **Evaluator đọc snapshot cũ (Stale Snapshot Race)**: `TaskAppEnforcementAdapter` lưu giữ một `inMemorySnapshot`. Khi UseCase cập nhật Room DB xong, UseCase chỉ trả về kết quả cho UI mà không thông báo cho runtime enforcement. Adapter vẫn giữ snapshot cũ với $N=0$ cho đến khi có sự kiện cycle transition 04:00 hoặc ứng dụng khởi động lại.
3. **Accessibility Service bị cô lập**: `AppDetectorAccessibilityService` chỉ truy vấn adapter khi nhận AccessibilityEvent từ hệ thống, nhưng bản thân adapter chưa invalidate snapshot nên kết quả đánh giá trả về vẫn là `ALLOW`.
4. **Hợp đồng Timing không tường minh**: Mã nguồn cũ sử dụng cờ boolean mơ hồ hoặc không truyền cờ timing xuyên suốt từ UseCase xuống Repository.

---

## 4. Before/After Mutation Flow

### Luồng lỗi trước đây (Before)
```
UI / AI
  ↓
Create Task (TaskRepo)
  ↓ (không atomic với link)
Insert TaskRewardLink (DAO rời rạc)
  ↓
DB commit
  ↓
[KẾT THÚC USE CASE]  <-- Không thông báo cho Adapter / Accessibility Service!
  ↓
Adapter snapshot VẪN CŨ (N=0)
  ↓
AppDetector đọc snapshot cũ → ALLOW (Ứng dụng KHÔNG bị khóa!)
```

### Luồng chuẩn tắc sau khi sửa đổi (After)
```
UI / AI
  ↓
Canonical UseCase (CreateTaskUseCase / UpdateTaskRewardLinkageUseCase)
  ↓
Repository Atomic Mutation (@Transaction: DB atomic write task + links + pending)
  ↓
DB commit thành công
  ↓
CanonicalMutationSyncManager.notifyMutationCommitted(affectedPackages)
  ↓
TaskAppEnforcementAdapter.recomputeSnapshot() (Tải lại canonical DB, cập nhật in-memory)
  ↓
Phát tín hiệu trực tiếp tới AppDetectorAccessibilityService listener
  ↓
Service đánh giá lại ứng dụng đang foreground hoặc mục tiêu bị ảnh hưởng
  ↓
Enforcement runtime lập tức thi hành LOCK / Launch LockScreenActivity
```

---

## 5. Canonical Architecture

Hệ thống tuân thủ nghiêm ngặt mô hình phân lớp SSOT:
- **Không cho phép UI / AI truy cập DAO trực tiếp**.
- **Không cho phép UI / AI tự tính $K$, $N$, $Required(N)$**.
- **Không cho phép Accessibility Service tự quyết định business lock**.
- **`CanonicalLockEvaluator` là Authority duy nhất** tính toán quyết định nghiệp vụ (`ALLOW` / `LOCK`).
- **`CanonicalMutationSyncManager`** là cầu nối điều phối đồng bộ (decoupled sync bridge) giữa tầng Domain UseCase và Runtime Enforcement Adapter / Service.

---

## 6. Immediate vs Next-Cycle Semantics

Định nghĩa chính thức tại `domain/canonical/task/RewardMutationTiming.kt`:
```kotlin
enum class RewardMutationTiming {
    IMMEDIATE_CURRENT_CYCLE,
    PENDING_NEXT_CYCLE
}
```

- **`IMMEDIATE_CURRENT_CYCLE`**:
  1. Ghi đè trực tiếp danh sách `TaskRewardLinkEntity` có hiệu lực trong chu kỳ hiện tại.
  2. Xóa các cấu hình pending cũ của task nếu có.
  3. Sau commit DB, kích hoạt `CanonicalMutationSyncManager.notifyMutationCommitted(affectedPackages)`.
  4. Snapshot in-memory được build lại, runtime enforcement lập tức áp dụng trạng thái khóa mới.
- **`PENDING_NEXT_CYCLE`**:
  1. Không sửa đổi các liên kết đang có hiệu lực trong chu kỳ hiện tại.
  2. Lưu cấu hình mới dưới dạng chuỗi phân cách trong `TaskEntity.pendingNextCycleRewards`.
  3. Không kích hoạt sync ép khóa ở chu kỳ hiện tại.
  4. Tại mốc 04:00 AM, `CycleTransitionManager` gọi `applyPendingRewards()` để biến các cấu hình pending thành liên kết chính thức trong chu kỳ mới, sau đó mới recompute lock.

---

## 7. Task Mutation Audit

- **`CreateTaskUseCase`**: Nhận tham số `timing: RewardMutationTiming`. Gọi `taskRepository.createTaskAtomic(task, linkedAppPackageNames, timing)`. Nếu `timing == IMMEDIATE_CURRENT_CYCLE`, gọi `CanonicalMutationSyncManager.notifyMutationCommitted(linkedAppPackageNames)`.
- **`DeleteTaskUseCase`**: Truy vấn danh sách app liên quan trước khi xóa, gọi `taskRepository.deleteTask(id)` (cascade xóa link trong chu kỳ hiện tại mà không làm hỏng lịch sử cycle), sau đó gọi `notifyMutationCommitted(affectedPackages)` để unlock ngay cho các app không còn task liên kết.

---

## 8. Reward Link Mutation Audit

- **`UpdateTaskRewardLinkageUseCase`**:
  - Hỗ trợ cả hai chế độ: `IMMEDIATE_CURRENT_CYCLE` và `PENDING_NEXT_CYCLE`.
  - Khi chọn `IMMEDIATE_CURRENT_CYCLE`: Tính toán `affectedPackages = (oldLinks + newLinks).distinct()`, thực thi `taskRepository.updateTaskRewardLinkageAtomic(taskId, selectedPackageNames, timing)`, sau đó thông báo `notifyMutationCommitted(affectedPackages)` để khóa app mới thêm và mở khóa app vừa gỡ bỏ.

---

## 9. Vault Mutation Audit

- **`AddVaultAppUseCase`**:
  - Thêm app mới vào Vault (`CanonicalVaultAppEntity`).
  - Ban đầu app có $N=0 \rightarrow Required(0)=0 \rightarrow \text{UNLOCKED}$.
  - Recompute snapshot ngay để đảm bảo app được quản lý trong danh sách giám sát.
- **`RemoveVaultAppUseCase`**:
  - Xóa app khỏi Vault. Đồng thời xóa toàn bộ link reward liên quan trong chu kỳ hiện tại.
  - Snapshot được cập nhật ngay lập tức.
  - Khi thêm lại cùng một package name sau đó, app có $N=0$, các liên kết cũ hoàn toàn biến mất (không tự phục hồi).

---

## 10. Lock Recompute Audit

- `CanonicalLockEvaluator`:
  - Nhận package name, đọc danh sách task liên kết đang hoạt động từ `CanonicalTaskRepository`.
  - Tính $N = \text{tổng số task liên kết}$.
  - Đọc trạng thái chu kỳ hiện tại từ `CanonicalCycleRepository` để tính $K = \text{số task đã COMPLETED}$.
  - Áp dụng công thức $Required(N)$.
  - Kiểm tra voucher hiệu lực qua `CanonicalVoucherRepository`.
  - Trả về `CanonicalLockEvaluationResult(decision = LOCK/ALLOW, ...)`.
- Mọi quyết định đều được tập trung tại đây, loại bỏ hoàn toàn các logic legacy cũ.

---

## 11. Cache / Snapshot Audit

- **`TaskAppEnforcementAdapter`**:
  - Duy trì `volatile var inMemorySnapshot: Map<String, TaskAppEnforcementDecision>`.
  - Cung cấp phương thức công khai `recomputeSnapshot()`.
  - Được đăng ký thông qua `CanonicalMutationSyncManager.registerAdapterProvider()`.
  - Khi mutation hoàn tất, `recomputeSnapshot()` được gọi trực tiếp, đọc dữ liệu Room DB mới nhất và thay thế con trỏ snapshot bằng bản đồ bất biến mới (thread-safe, lock-free read).

---

## 12. AI Mutation Path Audit

- AI Agent (thông qua Tool / Intent / UseCase callers) bắt buộc phải sử dụng cùng `CreateTaskUseCase` và `UpdateTaskRewardLinkageUseCase` như giao diện người dùng (UI).
- AI **không được** gọi DAO trực tiếp, không được tự set cờ khóa trong SharedPreferences, không được tự phán quyết $Required(N)$.
- Khi AI thực thi lệnh thay đổi liên kết reward với cờ `IMMEDIATE_CURRENT_CYCLE`, mutation đi qua đúng pipeline chuẩn và phát tín hiệu runtime ngay lập tức.

---

## 13. Automated Test Results

Tập tin kiểm thử: `app/src/test/java/com/example/selfdisciplinepoc01/domain/canonical/task/CanonicalTaskMutationImmediateLockTest.kt`  
Lệnh thực thi: `./gradlew testDebugUnitTest --tests com.example.selfdisciplinepoc01.domain.canonical.task.CanonicalTaskMutationImmediateLockTest`

| Mã Test Case | Mục Đích Kiểm Thử | Trạng Thái |
|---|---|---|
| `TASK-IMM-01` | Tạo task + immediate reward link $\rightarrow$ App bị khóa ngay lập tức ($N=1, K=0, Req=1 \rightarrow LOCK$) | **PASS** |
| `TASK-IMM-02` | Task có sẵn + thêm immediate reward $\rightarrow$ App bị khóa ngay lập tức | **PASS** |
| `TASK-IMM-03` | Gỡ immediate reward $\rightarrow$ App mở khóa ngay lập tức khi không còn lý do khóa | **PASS** |
| `TASK-IMM-04` | Xóa task có reward $\rightarrow$ Tính toán lại và mở khóa app ngay lập tức | **PASS** |
| `TASK-IMM-05` | Thêm app mới vào Vault $\rightarrow N=0, Required=0 \rightarrow$ UNLOCKED | **PASS** |
| `TASK-IMM-06` | App mới + Task mới + Immediate link $\rightarrow N=1, K=0 \rightarrow$ LOCKED ngay | **PASS** |
| `TASK-IMM-07` | Task không có reward $\rightarrow$ Không làm tăng $N$, không ảnh hưởng khóa app | **PASS** |
| `TASK-IMM-08` | Next-cycle reward mutation $\rightarrow$ Chu kỳ hiện tại giữ nguyên trạng thái khóa | **PASS** |
| `TASK-IMM-09` | Next-cycle reward mutation $\rightarrow$ Cấu hình pending tồn tại trong DB | **PASS** |
| `TASK-IMM-10` | 04:00 AM transition $\rightarrow$ Pending reward có hiệu lực $\rightarrow$ Khóa app nếu cần | **PASS** |
| `TASK-IMM-11` | Công thức $N=2 \rightarrow Required=1$ (Hoàn thành 1/2 là UNLOCKED) | **PASS** |
| `TASK-IMM-12` | Công thức $N=0 \rightarrow Required=0 \rightarrow$ UNLOCKED | **PASS** |
| `TASK-IMM-13` | Công thức $N=3 \rightarrow Required=2$ ($K=1 \rightarrow LOCK, K=2 \rightarrow ALLOW$) | **PASS** |
| `TASK-IMM-14` | Công thức $N=4 \rightarrow Required=3$ ($K=2 \rightarrow LOCK, K=3 \rightarrow ALLOW$) | **PASS** |
| `TASK-IMM-15` | Công thức $N=5 \rightarrow Required=4$ ($K=3 \rightarrow LOCK, K=4 \rightarrow ALLOW$) | **PASS** |
| `TASK-IMM-16` | Công thức $N=6 \rightarrow Required=4$ ($K=3 \rightarrow LOCK, K=4 \rightarrow ALLOW$) | **PASS** |
| `TASK-IMM-17` | Công thức $N=7 \rightarrow Required=5$ ($\lceil 2 \times 7 / 3 \rceil = 5$) | **PASS** |
| `TASK-IMM-18` | Voucher hiệu lực ghi đè trạng thái khóa $\rightarrow$ ALLOW | **PASS** |
| `TASK-IMM-19` | Voucher hết hạn $\rightarrow$ Evaluator tự động đánh giá lại $\rightarrow$ LOCK | **PASS** |
| `TASK-IMM-20` | Xóa app rồi thêm lại $\rightarrow$ Không phục hồi liên kết cũ ($N=0$) | **PASS** |
| `TASK-IMM-21` | UI và AI sử dụng cùng Canonical UseCase $\rightarrow$ Kết quả deterministic giống nhau | **PASS** |
| `TASK-IMM-22` | Immediate mutation kích hoạt cập nhật snapshot in-memory | **PASS** |
| `TASK-IMM-23` | Post-commit evaluator đọc đúng dữ liệu Room DB chuẩn tắc mới | **PASS** |
| `TASK-IMM-24` | Không có PolicyEngine legacy nào có quyền ghi đè quyết định của CanonicalLockEvaluator | **PASS** |

**Tổng kết Unit/Regression Tests**: **24/24 PASS (100%)**.

---

## 14. Real Device Test Results

Thực hiện trực tiếp trên thiết bị vật lý: **vivo iQOO Neo 10 (V2425A, Serial 10CF3J1F3400238)** chạy Android 15 / OriginOS 15.

### 14.1. REAL-IMM-01: Tạo Task + Immediate Reward Link
- **Thao tác**: Thêm Chrome vào Vault ($N=0 \rightarrow \text{UNLOCKED}$). Tạo task mới `task_rimm01`, liên kết Chrome với `IMMEDIATE_CURRENT_CYCLE`.
- **Kết quả thực tế**: Ngay khi lệnh commit xong, Chrome chuyển trạng thái `LOCKED`. Mở Chrome lập tức bị chặn bởi `LockScreenActivity`. Sau khi hoàn thành task trên System Panel, Chrome mở khóa ngay.
- **Bằng chứng**: `docs/assets/phase2b_c/screen_real_imm_01_locked.png` và `docs/assets/phase2b_c/screen_real_imm_01_unlocked.png`.

### 14.2. REAL-IMM-02: Cập Nhật Thêm Reward Cho Task Có Sẵn
- **Thao tác**: Tạo task không có reward. Cập nhật task thêm liên kết Chrome với `IMMEDIATE_CURRENT_CYCLE`.
- **Kết quả thực tế**: Chrome bị khóa ngay lập tức (`eval=LOCKED`). Khi gỡ bỏ reward link khỏi task, Chrome mở khóa ngay lập tức.
- **Bằng chứng**: `docs/assets/phase2b_c/screen_real_imm_02_locked.png` và `docs/assets/phase2b_c/screen_real_imm_02_unlocked.png`.

### 14.3. REAL-IMM-03: Khóa App Rồi Gỡ Bỏ Liên Kết Duy Nhất
- **Thao tác**: Chrome đang bị khóa bởi 1 task duy nhất ($N=1, K=0$). Gọi `UNLINK_TASK_APP` để gỡ link ngay lập tức.
- **Kết quả thực tế**: Logcat ghi nhận `links: 0, eval=UNLOCKED, syncAction=ALLOW`. Mở Chrome từ màn hình chính, Chrome hoạt động bình thường, không bị chặn.
- **Bằng chứng**: `docs/assets/phase2b_c/screen_real_imm_03_locked.png` và `docs/assets/phase2b_c/screen_real_imm_03_unlocked.png`.

### 14.4. REAL-NEXT-01: Cấu Hình Next-Cycle Reward & Vượt Ngưỡng 04:00 AM
- **Thao tác**: Tạo task với reward link Chrome chọn `PENDING_NEXT_CYCLE`. Kiểm tra chu kỳ hiện tại $\rightarrow$ Chrome vẫn `UNLOCKED`, mở được bình thường. Kích hoạt mốc chuyển chu kỳ 04:00 (`EXTRA_CANONICAL_TRIGGER_BOUNDARY true`).
- **Kết quả thực tế**: Tại chu kỳ mới, pending reward được áp dụng $\rightarrow$ Chrome có `linksCount=1, evaluatorDecision=LOCKED, syncFinalAction=LOCK`. Mở Chrome lập tức bị chặn bởi `LockScreenActivity` (`topResumedActivity=...LockScreenActivity`).
- **Bằng chứng**: `docs/assets/phase2b_c/screen_real_next_01_current_unlocked.png` và `docs/assets/phase2b_c/screen_real_next_01_next_cycle_locked.png`.

### 14.5. REAL-AI-01: Mô Phỏng AI Thực Hiện Mutation Chuẩn Tắc
- **Thao tác**: Kích hoạt mutation hủy liên kết Chrome khỏi `task_rnext01` qua canonical use case intent mô phỏng AI command.
- **Kết quả thực tế**: Logcat ghi nhận `[UNLINK_TASK_APP] Đã hủy liên kết Task 'task_rnext01' khỏi 'com.android.chrome' (IMMEDIATE). Còn lại links: 0, eval=UNLOCKED, syncAction=ALLOW`. Chrome lập tức mở khóa và mở trực tiếp lên màn hình chính mà không qua màn hình khóa.
- **Bằng chứng**: `docs/assets/phase2b_c/screen_real_ai_01_unlocked.png`.

---

## 15. Mandatory Evidence Matrix

Bảng đối chiếu chứng cứ bắt buộc theo quy định nghiêm ngặt:

| Test ID | Environment | Exact command/action | Actual result | Evidence | Status |
|---|---|---|---|---|---|
| **REAL-IMM-01** | vivo iQOO Neo 10 (V2425A, Android 15) | `EXTRA_CANONICAL_VAULT_ADD_PKG com.android.chrome` $\rightarrow$ `EXTRA_CANONICAL_CREATE_TASK_WITH_REWARD "task_rimm01\|Task_RIMM01\|com.android.chrome\|IMMEDIATE"` $\rightarrow$ Launch Chrome | Chrome bị khóa ngay lập tức bởi LockScreenActivity. Hoàn thành task qua System Panel $\rightarrow$ Mở khóa Chrome ngay. | `screen_real_imm_01_locked.png`, `screen_real_imm_01_unlocked.png`, Logcat: `eval=LOCKED, req=1, comp=0` | **PASS** |
| **REAL-IMM-02** | vivo iQOO Neo 10 (V2425A, Android 15) | Task có sẵn $\rightarrow$ `EXTRA_CANONICAL_UPDATE_REWARD_TIMING "task_rimm02\|com.android.chrome\|IMMEDIATE"` $\rightarrow$ Launch Chrome $\rightarrow$ Gỡ link | Chrome bị khóa ngay lập tức (`eval=LOCKED`). Khi gỡ link, Chrome chuyển `eval=UNLOCKED` và mở được ngay. | `screen_real_imm_02_locked.png`, `screen_real_imm_02_unlocked.png`, Logcat: `[UPDATE_REWARD_TIMING] ... App eval=LOCKED` | **PASS** |
| **REAL-IMM-03** | vivo iQOO Neo 10 (V2425A, Android 15) | App đang bị khóa $\rightarrow$ `EXTRA_CANONICAL_UNLINK_TASK_APP "task_rimm03\|com.android.chrome"` $\rightarrow$ Launch Chrome | Chrome chuyển `eval=UNLOCKED, syncAction=ALLOW, links: 0`. Mở Chrome thành công không bị chặn. | `screen_real_imm_03_locked.png`, `screen_real_imm_03_unlocked.png`, Logcat: `[UNLINK_TASK_APP] ... Còn lại links: 0, eval=UNLOCKED` | **PASS** |
| **REAL-NEXT-01** | vivo iQOO Neo 10 (V2425A, Android 15) | `EXTRA_CANONICAL_CREATE_TASK_WITH_REWARD "task_rnext01\|Task_RNEXT01\|com.android.chrome\|NEXT_CYCLE"` $\rightarrow$ Launch Chrome $\rightarrow$ `EXTRA_CANONICAL_TRIGGER_BOUNDARY true` $\rightarrow$ Launch Chrome | Chu kỳ hiện tại: Chrome `UNLOCKED` và mở bình thường. Sau mốc 04:00: Pending link được apply, Chrome chuyển `LOCKED` và bị chặn bởi LockScreenActivity. | `screen_real_next_01_current_unlocked.png`, `screen_real_next_01_next_cycle_locked.png`, Logcat: `[QUERY_VAULT_APP] App 'com.android.chrome': linksCount=1, evaluatorDecision=LOCKED` | **PASS** |
| **REAL-AI-01** | vivo iQOO Neo 10 (V2425A, Android 15) | Mô phỏng AI gọi Canonical UseCase hủy link Chrome khỏi `task_rnext01` $\rightarrow$ Launch Chrome | Chrome lập tức chuyển `eval=UNLOCKED, syncAction=ALLOW`. Chrome mở trực tiếp thành công, `topResumedActivity=...chrome.Main`. | `screen_real_ai_01_unlocked.png`, Logcat: `[UNLINK_TASK_APP] ... links: 0, eval=UNLOCKED, syncAction=ALLOW` | **PASS** |

---

## 16. Regression Results

Toàn bộ các bộ test tự động của dự án đã được chạy kiểm tra hồi quy:
- `CanonicalTaskMutationImmediateLockTest`: **24/24 PASS**
- `CanonicalCycleRuntimeTest`: **PASS**
- `CanonicalLockEvaluatorTest`: **PASS**
- `gradlew testDebugUnitTest`: **BUILD SUCCESSFUL (0 failures)**

Không có bất kỳ hiện tượng hồi quy nào đối với các tính năng chu kỳ 04:00, Vault repository hay foreground boundary detection đã hoàn thành ở các Phase trước.

---

## 17. Anti-False-Pass Audit

Tất cả các tiêu chí kiểm định nghiêm ngặt đã được thỏa mãn:
1. **Thực thi trên thiết bị thật**: Mọi bài test trong ma trận chấp nhận đều chạy trực tiếp trên vivo iQOO Neo 10 với số serial xác định `10CF3J1F3400238`.
2. **Không dùng test seam giả lập quyết định nghiệp vụ**: Test seam chỉ đóng vai trò chuyển tiếp Intent thành lời gọi Canonical UseCase thật (`CreateTaskUseCase`, `UpdateTaskRewardLinkageUseCase`, Room DB thật).
3. **Đối chiếu trực tiếp 3 lớp**:
   - Lớp Database Room: Dữ liệu task, link và trạng thái chu kỳ được commit vào SQLite thật.
   - Lớp Logcat: Ghi nhận chính xác lời gọi `recomputeSnapshot()` và tín hiệu sync.
   - Lớp Màn hình vật lý: Dumpsys activity + screencap trực tiếp từ màn hình thiết bị xác nhận `LockScreenActivity` hiển thị hoặc bị chặn đúng kỳ vọng.
4. **Tính xác định**: Hai chu trình `IMMEDIATE` và `NEXT_CYCLE` hoạt động độc lập, không có hành vi thứ ba nào tồn tại.

---

## 18. Known Limitations

- Trên một số bản build ROM tùy biến sâu (như OriginOS), khi ứng dụng đang nằm ở background quá lâu hoặc bị hệ điều hành đóng băng năng lượng, Accessibility Event có thể bị trễ một vài phần trăm giây nếu không có foreground interaction. Tuy nhiên cơ chế `CanonicalMutationSyncManager` đã giải quyết vấn đề này bằng cách ép kiểm tra ngay khi mở ứng dụng mục tiêu.
- Các ứng dụng hệ thống không có giao diện launcher activity không được hỗ trợ trong phạm vi Vault apps.

---

## 19. Remaining Blockers

- **Không còn blocker nào** trong phạm vi Phase 2B-C.
- Toàn bộ các yêu cầu của phase đã được giải quyết trọn vẹn và xác thực thành công.

---

## 20. Final Verdict

# PHASE 2B-C CLOSED (HOÀN THÀNH 100%)

Hệ thống Canonical Task Mutation, Reward Link & Immediate Lock Recomputation đã đạt độ hoàn thiện cao nhất:
- **Immediate means immediate**: Đổi cấu hình tức thì $\rightarrow$ Khóa / Mở khóa có hiệu lực ngay lập tức.
- **Next Cycle means next cycle**: Đổi cấu hình chu kỳ sau $\rightarrow$ Chu kỳ hiện tại tuyệt đối không đổi, tự động thi hành tại mốc 04:00 AM.
- **Không có hành vi thứ ba**: Hệ thống hoạt động hoàn toàn xác định (deterministic) và chuẩn hóa theo đúng SSOT.

---
*Báo cáo được lập bởi: Antigravity AI Agent*  
*Thiết bị xác thực: vivo iQOO Neo 10 (V2425A, Serial 10CF3J1F3400238)*
