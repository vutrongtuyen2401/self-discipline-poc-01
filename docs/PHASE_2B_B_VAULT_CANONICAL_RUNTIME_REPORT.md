# BÁO CÁO NGHIỆM THU: PHASE 2B-B — VAULT CANONICAL RUNTIME

**Dự án**: `self-discipline-poc-01`  
**Thời gian thực hiện**: 2026-09-12  
**Thiết bị kiểm thử thực tế**: vivo iQOO Neo 10 (Model: `V2425A`, Android 15 / API 35, Build: `15`, Serial: `10CF3J1F3400238`)  
**Tình trạng nghiệm thu**: **HOÀN THÀNH TOÀN BỘ (100% PASS — ĐẠT CHUẨN TEST EVIDENCE GATE)**  

---

## 1. TỔNG QUAN THỰC HIỆN (EXECUTIVE SUMMARY)

Mục tiêu cốt lõi của **Phase 2B-B**:
> Di chuyển hoàn toàn phân hệ **Bảo Khố (Vault)** từ legacy/POC runtime sang **Canonical Vault Runtime**, bảo đảm toàn bộ giao diện Bảo Khố (Vault UI) và cơ chế thực thi bảo vệ Android (Android Enforcement) cùng sử dụng duy nhất nguồn trạng thái chuẩn tắc (Canonical Vault State) thông qua bộ ba: `CanonicalVaultRepository` + `CanonicalTaskRepository` + `CanonicalLockEvaluator`.

Các hạng mục trọng tâm đã đạt được:
1. **Canonical Vault App State**: Ứng dụng trong Bảo Khố được lưu trữ và quản lý thống nhất qua Room database `canonical_vault_apps`.
2. **Quan hệ N:M Reward Links**: Quản lý đa chiều giữa Nhiệm vụ và Ứng dụng qua `canonical_task_reward_links`, phản ánh chính xác trạng thái hoàn thành trong chu kỳ (`cycleId`).
3. **Chiếu trạng thái Khóa / Mở (Lock/Unlock Projection)**: Áp dụng công thức ngưỡng chuẩn tắc SSOT:
   - $N = 0 \rightarrow 0$ (Không có nhiệm vụ liên kết $\rightarrow$ Mở)
   - $N \in \{1, 2\} \rightarrow 1$
   - $N = 3 \rightarrow 2$
   - $N = 4 \rightarrow 3$
   - $N \in \{5, 6\} \rightarrow 4$
   - $N > 6 \rightarrow \lceil 2N/3 \rceil$
4. **Nguyên tử hóa việc Gỡ và Thêm lại (Add / Remove Semantics)**: Khi gỡ ứng dụng khỏi Bảo Khố, mọi liên kết trong `canonical_task_reward_links` bị dọn dẹp sạch sẽ trong cùng transaction `database.withTransaction`. Khi thêm lại, app bắt đầu với $N = 0$, **tuyệt đối không tự khôi phục các liên kết cũ**.
5. **Độc lập của Nhiệm vụ không phần thưởng (Rewardless Task Neutrality)**: Các nhiệm vụ không có liên kết ứng dụng (`hasReward = false` hoặc không có row trong bảng link) hoàn toàn không ảnh hưởng đến số lượng $N$ hay trạng thái khóa của bất kỳ ứng dụng nào.
6. **Thẩm quyền thực thi duy nhất (Single Source of Truth)**: `CanonicalLockEvaluator` là cơ quan thẩm quyền duy nhất quyết định việc khóa/mở tại runtime. Legacy PolicyEngine bị vô hiệu hóa hoàn toàn, không thể ghi đè hay can thiệp vào quyết định của Canonical Evaluator.

---

## 2. ĐỐI SOÁT VÀ TUÂN THỦ NGUỒN SỰ THẬT (SSOT ALIGNMENT)

| Hạng mục quy định | Yêu cầu SSOT | Hiện thực hóa trong Phase 2B-B | Đánh giá |
| :--- | :--- | :--- | :--- |
| **Thẩm quyền khóa Bảo Khố** | `CanonicalLockEvaluator` là business authority duy nhất. | Vault UI và Enforcement Adapter đều đọc trực tiếp từ `CanonicalLockEvaluator.evaluateApp()`. Legacy policy bị cô lập. | **TUÂN THỦ 100%** |
| **Công thức ngưỡng khóa** | $N=0 \rightarrow 0$; $N=1,2 \rightarrow 1$; $N=3 \rightarrow 2$; $N=4 \rightarrow 3$; $N=5,6 \rightarrow 4$; $N>6 \rightarrow \lceil 2N/3 \rceil$. | Cài đặt tại `CanonicalLockPolicy.calculateRequiredCompletions(n)`. Đã kiểm chứng qua 20 unit tests và kiểm thử thiết bị thật. | **TUÂN THỦ 100%** |
| **Voucher Foundation** | Voucher hợp lệ có thể mở khóa app ngay lập tức (Bypassing task requirement). | `CanonicalLockEvaluator` thẩm định `hasEffectiveVoucher()` trước khi xét task requirement. Kiểm chứng qua `VB-A12` và `VB-A13`. | **TUÂN THỦ 100%** |
| **Xóa ứng dụng Bảo Khố** | Xóa app phải xóa toàn bộ link liên quan một cách nguyên tử. Nhiệm vụ trong Mission Hall vẫn được giữ nguyên. | `removeVaultApp` chạy trong `database.withTransaction`: xóa app, xóa links, cập nhật `hasReward = false` cho task bị mất hết link. | **TUÂN THỦ 100%** |
| **Thêm lại ứng dụng** | Thêm lại ứng dụng cũ đã xóa: $N = 0$, không khôi phục liên kết cũ. | Kiểm chứng qua `VB-A14` (unit test) và `VB-R05` (thiết bị thật vivo). | **TUÂN THỦ 100%** |
| **Nhiệm vụ không liên kết** | Rewardless task không được tính vào $N$ của bất kỳ app nào. | Kiểm chứng qua `VB-A15` (unit test) và `VB-R06` (thiết bị thật). | **TUÂN THỦ 100%** |
| **Giao diện Bảo Khố (Vault UI)** | Theme Tiên Hiệp, không dùng icon ổ khóa, hiển thị đúng tiến độ $K/N$. | Card ứng dụng hiển thị trạng thái "Đã mở ($K/N$)" hoặc "Đang khóa ($K/N$)", nút Gỡ có xác nhận phong cách Tiên Hiệp. | **TUÂN THỦ 100%** |
| **Kỷ luật phạm vi (Scope Discipline)** | Không can thiệp các phân hệ chưa tới phase; giữ nguyên OPEN-001/002/003. | Giữ nguyên trạng thái OPEN của 3 open questions; không động vào Tower, Dungeon, Shop, Inventory, AI. | **TUÂN THỦ 100%** |

---

## 3. KIẾN TRÚC VÀ DELTA TRIỂN KHAI (ARCHITECTURE & RUNTIME DELTA)

### 3.1. Data & Persistence Layer
1. **`CanonicalRepositoryImpls.kt`**:
   - `removeVaultApp(packageName: String)`:
     - Thực thi trong `database.withTransaction`.
     - Xóa bản ghi trong `canonical_vault_apps`.
     - Lấy danh sách các task liên kết với app này qua `linkDao.getTasksForApp(packageName)`.
     - Xóa toàn bộ liên kết qua `linkDao.deleteByApp(packageName)`.
     - Với mỗi task bị ảnh hưởng: kiểm tra xem còn liên kết nào với app khác không; nếu không còn (`remainingLinks.isEmpty()`), tự động cập nhật `hasReward = false` cho task đó trong `canonical_tasks`.
     - Đồng bộ dọn dẹp bảng legacy: `appDao.deleteByPackageName(packageName)` và `crossRefDao.deleteByAppPackageName(packageName)`.
   - `linkTaskToApp` & `unlinkTaskFromApp`: Đảm bảo tính nhất quán của cờ `hasReward` trên `CanonicalTask`.

### 3.2. Domain Layer
1. **`CanonicalUseCases.kt`**:
   - Bổ sung data class `CanonicalVaultAppItem`:
     ```kotlin
     data class CanonicalVaultAppItem(
         val app: CanonicalVaultApp,
         val linkedTasksCount: Int,
         val completedTasksCount: Int,
         val requiredTasksCount: Int,
         val isUnlocked: Boolean,
         val hasVoucher: Boolean
     )
     ```
   - Bổ sung use case `GetCanonicalVaultAppsUseCase`: Trực tiếp tập hợp thông tin từ `vaultRepository`, `taskRepository` và `lockEvaluator` để cung cấp state hoàn chỉnh cho UI.

### 3.3. Presentation Layer
1. **`VaultViewModel.kt`**:
   - Chuyển sang mô hình chuẩn: Inject `GetCanonicalVaultAppsUseCase`, `CanonicalVaultRepository`, `CanonicalTaskRepository`, `TaskAppEnforcementAdapter`.
   - Hỗ trợ secondary constructor tương thích ngược với các integration/flow tests cũ (`TargetRepository`, `UsageTracker`, `BusinessDayProvider`).
   - Cung cấp StateFlow `vaultApps: StateFlow<List<CanonicalVaultAppItem>>` và `stats: StateFlow<VaultStats>`.
   - Các hành động `removeAppFromVault`, `addAppToVault` đều tự động kích hoạt `enforcementAdapter?.recomputeSnapshot()`.
2. **`VaultScreen.kt`**:
   - Hiển thị danh sách ứng dụng chuẩn tắc với layout 2 cột Grid phong cách Tiên Hiệp.
   - Thống kê Quy Mô Bảo Khố (tổng số app, số app có liên kết, số app đã giải phong ấn).
   - Dialog xác nhận gỡ phong ấn ứng dụng chuẩn mực Tiên Hiệp.

### 3.4. LockScreenActivity (System Panel)
1. **`LockScreenActivity.kt`**:
   - Tích hợp gọi `TaskAppEnforcementAdapterProvider.getAdapter(context).recomputeSnapshot()` ngay khi người dùng bấm "XÁC NHẬN" hoàn thành nhiệm vụ trên System Panel.
   - Tự động chuyển giao diện từ "BẢO KHỐ PHONG ẤN TRẬN PHÁP" sang "【 PHONG ẤN ĐÃ GIẢI TRỪ 】 — CÔNG ĐỨC VIÊN MÃN", mở nút "【 VÀO ỨNG DỤNG 】" để người dùng tiếp tục trải nghiệm mà không bị chặn lại.

---

## 4. BẢNG BẰNG CHỨNG KIỂM THỬ TỰ ĐỘNG (AUTOMATED TEST EVIDENCE MATRIX)

Bộ test tự động chuyên biệt `CanonicalVaultRuntimeTest.kt` gồm 20 bài test đã chạy thành công 100%:

| Test ID | Tên Kiểm Thử | Kịch bản & Mục tiêu xác minh | Kết quả |
| :--- | :--- | :--- | :--- |
| **VB-A01** | `testInitialEmptyVault` | Khởi tạo ban đầu: Bảo Khố rỗng, 0 apps. | **PASS** |
| **VB-A02** | `testAddSingleAppToVault` | Thêm 1 app vào Bảo Khố -> hiển thị đúng trong state. | **PASS** |
| **VB-A03** | `testUnlinkedApp_unlockedByDefault` | App không có liên kết nhiệm vụ ($N=0$) -> Mặc định UNLOCKED ($0/0$). | **PASS** |
| **VB-A04** | `testLinkSingleTask_pending_locked` | Liên kết 1 task PENDING ($N=1, K=0$) -> Required=1 -> LOCKED. | **PASS** |
| **VB-A05** | `testLinkSingleTask_completed_unlocked` | Liên kết 1 task COMPLETED ($N=1, K=1$) -> Required=1 -> UNLOCKED. | **PASS** |
| **VB-A06** | `testThreshold_N2_requires1` | Ngưỡng $N=2$: Hoàn thành 1 task -> UNLOCKED ($K=1 \ge 1$). | **PASS** |
| **VB-A07** | `testThreshold_N3_requires2` | Ngưỡng $N=3$: $K=1 \rightarrow$ LOCKED; $K=2 \rightarrow$ UNLOCKED. | **PASS** |
| **VB-A08** | `testThreshold_N4_requires3` | Ngưỡng $N=4$: $K=2 \rightarrow$ LOCKED; $K=3 \rightarrow$ UNLOCKED. | **PASS** |
| **VB-A09** | `testThreshold_N5_requires4` | Ngưỡng $N=5$: $K=3 \rightarrow$ LOCKED; $K=4 \rightarrow$ UNLOCKED. | **PASS** |
| **VB-A10** | `testThreshold_N6_requires4` | Ngưỡng $N=6$: $K=3 \rightarrow$ LOCKED; $K=4 \rightarrow$ UNLOCKED. | **PASS** |
| **VB-A11** | `testThreshold_N7_requires5` | Ngưỡng $N=7$: $\lceil 14/3 \rceil = 5$; $K=4 \rightarrow$ LOCKED; $K=5 \rightarrow$ UNLOCKED. | **PASS** |
| **VB-A12** | `testVoucherBypassesLock_specificApp` | App có voucher hiệu lực cho chính package đó -> Bỏ qua task, UNLOCKED. | **PASS** |
| **VB-A13** | `testVoucherBypassesLock_wildcardAllApps` | Voucher toàn năng (wildcard `*`) -> Mở khóa tất cả app trong Bảo Khố. | **PASS** |
| **VB-A14** | `testDeleteApp_purgesRewardLinks_readdStartsFresh` | Xóa app xóa sạch links; thêm lại app bắt đầu với $N=0$, không khôi phục liên kết cũ. | **PASS** |
| **VB-A15** | `testRewardlessTask_doesNotAffectAppLockState` | Task không phần thưởng không làm tăng $N$ hay đổi trạng thái của Vault app. | **PASS** |
| **VB-A16** | `testManyToMany_taskCompletes_unlocksMultipleApps` | 1 task liên kết 2 app: hoàn thành task này đồng thời thỏa mãn cho cả 2 app. | **PASS** |
| **VB-A17** | `testArchivedTask_notCountedTowardsLock` | Task bị archive không được tính vào $N$ hay $K$ của app. | **PASS** |
| **VB-A18** | `testVaultStats_calculation` | Tính toán thống kê Quy Mô Bảo Khố (tổng, có liên kết, đã giải phong ấn). | **PASS** |
| **VB-A19** | `testEnforcementAdapter_evaluateSync_reflectsCanonicalEvaluator` | EnforcementAdapter `evaluateSync` phản ánh 1:1 kết quả của `CanonicalLockEvaluator`. | **PASS** |
| **VB-A20** | `testBatchMutation_finalEvaluationConsistent` | Chuỗi mutation liên tiếp (thêm, liên kết, hủy liên kết) -> kết quả evaluator nhất quán. | **PASS** |

> **Kết quả lệnh Gradle**: `.\gradlew.bat testDebugUnitTest`  
> `BUILD SUCCESSFUL in 47s. 25 actionable tasks: 5 executed, 20 up-to-date.`  
> **100% PASSED (0 FAILED, 0 SKIPPED)**.

---

## 5. BẢNG BẰNG CHỨNG KIỂM THỬ THỰC TẾ TRÊN THIẾT BỊ THẬT (REAL DEVICE PHYSICAL EVIDENCE MATRIX)

Kiểm thử trực tiếp trên điện thoại **vivo iQOO Neo 10 (`V2425A`)**, Android 15 (API 35), Serial `10CF3J1F3400238`:

| Test ID | Nội dung kiểm thử | Thao tác vật lý trên thiết bị | Kết quả quan sát & Bằng chứng thực tế | Đánh giá |
| :--- | :--- | :--- | :--- | :--- |
| **VB-R01** | **Hiển thị Vault Screen phản ánh Canonical State** | Mở tab "Bảo Khố" trên `MainActivity`. | Hiển thị 2 apps: `1.1.1.1` ("Đã mở (1/1)"), `Album` ("Đã mở (0/0)"). Thống kê: 2 apps, 1 liên kết, 2 đã giải phong ấn.<br>• File: `screen_vbr01_vault.png`<br>• Dump: `ui_vbr01_vault.xml` | **PASS** |
| **VB-R02** | **App bị phong ấn (Locked app)** | Seed `com.android.bbkcalculator` ($N=1, K=0$). Mở Máy tính (`bbkcalculator`). | `AppDetectorService` phát hiện trong 0.94ms, `BlockingShieldOverlay` hiển thị trong 22.26ms, `LockScreenActivity` hiển thị trong 114.64ms. Calculator bị chặn hoàn toàn, không leak UI hay touch.<br>• File: `screen_vbr02_locked.png`<br>• Dump: `ui_vbr02.xml` | **PASS** |
| **VB-R03** | **Hoàn thành nhiệm vụ $\rightarrow$ Mở khóa** | Trên System Panel: Bấm nút "【 TA ĐÃ HOÀN THÀNH 】" $\rightarrow$ Dialog "XÁC NHẬN CÔNG ĐỨC" $\rightarrow$ Bấm "XÁC NHẬN". | Panel chuyển sang "【 PHONG ẤN ĐÃ GIẢI TRỪ 】 — CÔNG ĐỨC VIÊN MÃN" ($1/1$). Bấm "【 VÀO ỨNG DỤNG 】": Calculator mở thành công, Accessibility log: `action=ALLOW, reason=ALLOWED_UNLOCKED_BY_TASKS`.<br>• File: `screen_vbr03_completed_panel.png`, `screen_vbr03_unlocked.png`<br>• Dump: `ui_vbr03.xml` | **PASS** |
| **VB-R04** | **Gỡ ứng dụng khỏi Bảo Khố (Delete app)** | Trên màn hình Bảo Khố, bấm nút "Gỡ" trên card `Test Vault App` $\rightarrow$ Bấm "Xác nhận gỡ". | App biến mất khỏi UI. SQLite DB: `canonical_vault_apps` giảm từ 3 xuống 2. `canonical_task_reward_links` bị xóa sạch. Task trong Mission Hall vẫn tồn tại với `hasReward = 0`.<br>• File: `screen_vbr04_deleted.png`<br>• Dump: `ui_vbr04.xml` | **PASS** |
| **VB-R05** | **Thêm lại ứng dụng đã xóa (Re-add app)** | Thêm lại `com.android.bbkcalculator` vào Bảo Khố. | App xuất hiện lại với trạng thái "Đã mở (0/0)". DB: `linksCount = 0`. Không tự khôi phục liên kết cũ. Mở Calculator thành công, không bị chặn.<br>• File: `screen_vbr05_readded.png`, `screen_vbr05_app.png`<br>• Dump: `ui_vbr05.xml` | **PASS** |
| **VB-R06** | **Nhiệm vụ không liên kết (Rewardless task)** | Tạo task không có reward link; truy vấn trạng thái 3 app trong Vault. | DB ghi nhận task `hasReward = 0`. Logcat `[QUERY_VAULT_APP]` cho thấy cả 3 apps không bị tăng $N$ hay thay đổi kết quả thẩm định.<br>• DB log: `read_db.py`<br>• Logcat: `CanonicalTestSeam` | **PASS** |
| **VB-R07** | **Legacy Authority Regression** | Mở Calculator khi Canonical Evaluator = UNLOCKED. | Accessibility Service đánh giá `action=ALLOW, reason=ALLOWED_UNLOCKED_BY_TASKS`. Legacy PolicyEngine hoàn toàn không can thiệp hay khóa app.<br>• File: `screen_vbr07_legacy_regression.png`<br>• Dump: `ui_vbr07.xml` | **PASS** |

---

## 6. ĐO LƯỜNG ĐỘ TRỄ THỰC THI (LATENCY & PERFORMANCE METRICS)

Trích xuất từ logcat thực tế khi chặn `com.android.bbkcalculator` trên thiết bị vivo iQOO Neo 10 (Android 15):

```text
[SHIELD_LATENCY]
sessionId=1
target_package=com.android.bbkcalculator
event_to_shield_request_ms=0.94
event_to_shield_firstFrame_ms=22.26
shield_request_to_firstFrame_ms=21.32

[LOCK_LATENCY]
target_package=com.android.bbkcalculator
event_to_launch_ms=0.60
launch_to_onCreate_ms=25.59
onCreate_to_firstFrame_ms=81.50
event_to_firstFrame_ms=114.64
```

- **Thời gian đưa BlockingShieldOverlay lên màn hình**: **22.26 ms** (đạt xuất sắc ngưỡng yêu cầu < 50 ms).
- **Thời gian đưa LockScreenActivity lên màn hình**: **114.64 ms** (đạt xuất sắc ngưỡng yêu cầu < 150 ms).
- **Trải nghiệm người dùng**: Chặn tức thì, không có hiện tượng chớp nháy (visual flicker) hay rò rỉ nội dung app đích.

---

## 7. XÁC MINH ATOMIC DELETION & LINK PURGE (PHẦN CỨNG THỰC TẾ)

Dữ liệu SQLite trích xuất trước và sau khi gỡ app `com.android.bbkcalculator`:

**Trước khi gỡ:**
```text
=== canonical_vault_apps === (Count: 3)
   ['com.cloudflare.onedotonedotonedotone', '1.1.1.1', '1789189500620']
   ['com.vivo.gallery', 'Album', '1789189500620']
   ['com.android.bbkcalculator', 'Test Vault App', '1789208841510']
=== canonical_task_reward_links === (Count: 2)
   ['e52616d2-dc56-412d-94eb-bac76eb6e9b6', 'com.cloudflare.onedotonedotonedotone', '1789189653107']
   ['test_task_com_android_bbkcalculator', 'com.android.bbkcalculator', '1789208841894']
```

**Sau khi gỡ:**
```text
=== canonical_vault_apps === (Count: 2)
   ['com.cloudflare.onedotonedotonedotone', '1.1.1.1', '1789189500620']
   ['com.vivo.gallery', 'Album', '1789189500620']
=== canonical_task_reward_links === (Count: 1)
   ['e52616d2-dc56-412d-94eb-bac76eb6e9b6', 'com.cloudflare.onedotonedotonedotone', '1789189653107']
=== canonical_tasks === (Task vẫn tồn tại, hasReward chuyển thành 0)
   ['test_task_com_android_bbkcalculator', 'Nhiệm vụ phong ấn test cho com.android.bbkcalculator', '', '0', '0', '1789209608840', '0', 'None']
```

Toàn bộ thao tác thực hiện trong một Room transaction duy nhất (`database.withTransaction`), bảo đảm tính toàn vẹn ACID tuyệt đối.

---

## 8. KIỂM TOÁN CHỐNG KHẢ NĂNG PASS ẢO (ANTI-FALSE-PASS AUDIT)

| Giả thiết lỗi / Nguy cơ Pass Ảo | Cơ chế phát hiện & Biện pháp kiểm chứng | Kết quả thẩm định |
| :--- | :--- | :--- |
| **App không bị chặn vì Accessibility Service chết** | Kiểm tra `AppDetectorAccessibilityService` đang active trong `enabled_accessibility_services`. Logcat ghi nhận `[EVENT: RECEIVED]` và `[LOCK_SESSION_STARTED]`. | **Không có false-pass**: Service hoàn toàn hoạt động. |
| **App mở được vì nhầm lẫn sang package khác** | Sử dụng `adb shell monkey -p com.android.bbkcalculator 1` và dump UI chính xác của `com.android.bbkcalculator.Calculator`. | **Không có false-pass**: Target app chuẩn xác. |
| **Links cũ vẫn âm thầm tồn tại trong database** | Đọc trực tiếp byte SQLite bằng script Python (`dump_db.py`, `read_db.py`) và đọc bảng `canonical_task_reward_links`. | **Không có false-pass**: Links bị xóa sạch hoàn toàn. |
| **Legacy PolicyEngine can thiệp ngầm** | Kiểm tra toàn bộ trace log của `AppDetectorService` và `TaskAppEnforcementAdapter`. Tất cả action đều căn cứ trên `isVaultApp` và `CanonicalLockEvaluator`. | **Không có false-pass**: Legacy Policy hoàn toàn bị cách ly. |

---

## 9. DANH SÁCH TẬP TIN THAY ĐỔI VÀ TẠO MỚI

1. [CanonicalRepositoryImpls.kt](file:///c:/Code/self-discipline-poc-01/app/src/main/java/com/example/selfdisciplinepoc01/data/canonical/repository/CanonicalRepositoryImpls.kt): Atomic delete vault app, reward link cleanup, hasReward consistency.
2. [CanonicalUseCases.kt](file:///c:/Code/self-discipline-poc-01/app/src/main/java/com/example/selfdisciplinepoc01/domain/canonical/usecase/CanonicalUseCases.kt): `CanonicalVaultAppItem`, `GetCanonicalVaultAppsUseCase`.
3. [VaultViewModel.kt](file:///c:/Code/self-discipline-poc-01/app/src/main/java/com/example/selfdisciplinepoc01/ui/vault/VaultViewModel.kt): Migration sang Canonical Vault state, backward-compatible constructor cho tests.
4. [VaultScreen.kt](file:///c:/Code/self-discipline-poc-01/app/src/main/java/com/example/selfdisciplinepoc01/ui/vault/VaultScreen.kt): Cập nhật binding `CanonicalVaultAppItem`, Tiên Hiệp aesthetic.
5. [LockScreenActivity.kt](file:///c:/Code/self-discipline-poc-01/app/src/main/java/com/example/selfdisciplinepoc01/LockScreenActivity.kt): Kích hoạt `recomputeSnapshot()` tức thì khi task được xác nhận hoàn thành.
6. [MainActivity.kt](file:///c:/Code/self-discipline-poc-01/app/src/main/java/com/example/selfdisciplinepoc01/MainActivity.kt): Intent test seams cho Vault runtime, cấu hình `launchMode="singleTop"`.
7. [AndroidManifest.xml](file:///c:/Code/self-discipline-poc-01/app/src/main/AndroidManifest.xml): Cấu hình `android:launchMode="singleTop"` cho `MainActivity`.
8. [CanonicalVaultRuntimeTest.kt](file:///c:/Code/self-discipline-poc-01/app/src/test/java/com/example/selfdisciplinepoc01/domain/canonical/vault/CanonicalVaultRuntimeTest.kt): 20 bài unit test tự động chuẩn hóa (`VB-A01` -> `VB-A20`).

---

## 10. TRẠNG THÁI MÃ NGUỒN VÀ ĐỒNG BỘ GIT

- **Trạng thái Build**: `BUILD SUCCESSFUL` (assembleDebug & testDebugUnitTest).
- **Test Suite**: 20/20 test `CanonicalVaultRuntimeTest` PASS (100%), tổng thể 471/471 unit tests toàn bộ dự án PASS (100%).
- **Thiết bị thật**: 7/7 physical verification scenarios (`VB-R01` đến `VB-R07`) PASS trên vivo iQOO Neo 10.
- **Git Push**: Sẽ được thực hiện ngay lập tức sau báo cáo theo quy định hệ thống.

---

## 11. KẾT LUẬN NGHIỆM THU

Phân hệ **Bảo Khố (Vault Canonical Runtime — Phase 2B-B)** đã hoàn thành xuất sắc toàn bộ các tiêu chí nghiệm thu đề ra, tuân thủ 100% Nguồn Sự Thật (SSOT) và đáp ứng nghiêm ngặt tôn chỉ **TEST EVIDENCE GATE**.

**KẾT QUẢ NGHIỆM THU: CHÍNH THỨC ĐÓNG PHASE 2B-B (PASS).**  
Sẵn sàng bước vào các Phase tiếp theo theo lộ trình phát triển của dự án.
