# BÁO CÁO KỸ THUẬT PHASE 19 — VAULT INTEGRATION & TASK ↔ APP LINKAGE

**Dự án:** Hệ Thống Tự Kỷ Luật Bản Thân (`self-discipline-poc-01`)  
**Package:** `com.example.selfdisciplinepoc01`  
**Nguồn sự thật tối cao (Canonical Source of Truth):** [`docs/CANONICAL_DESIGN_V2.md`](file:///c:/Code/self-discipline-poc-01/docs/CANONICAL_DESIGN_V2.md)  
**Ngày thực hiện:** 07/09/2026  
**Thiết bị kiểm chứng thực tế:** vivo iQOO Neo 10 (V2425A / Android 15 / OriginOS 5)  

---

## 1. MỤC TIÊU GIAI ĐOẠN (PHASE OBJECTIVE)

Thiết lập kết nối sản phẩm thực tế đầu tiên giữa:
```
Bảo Khố (Vault)
      ↓
Task (Nhiệm Vụ Đường)
      ↓
Task ↔ App Linkage (Quan hệ Many-to-Many)
      ↓
TaskAppEnforcementAdapter (Ranh giới an toàn OPEN-01)
      ↓
Frozen Core App Lock (Hạ tầng thực thi kỹ thuật)
```

**Nguyên tắc Quản trị Tối cao (Governance):**
- Canonical Design V2 là Single Source of Truth tối cao.
- **OPEN-01, OPEN-02, OPEN-03, OPEN-04, OPEN-05, OPEN-06, OPEN-07 TIẾP TỤC GIỮ NGUYÊN TRẠNG THÁI OPEN.**
- Tuyệt đối **KHÔNG TỰ Ý PHÁT MINH CÔNG THỨC 2/3**, không tính toán tỷ lệ % mở khóa, không điểm thưởng, không tu vi.
- Tách bạch rạch ròi giữa Product Vault (Room Database) và Technical App Lock Configuration (DataStore runtime).
- Bảo toàn 100% Frozen Core App Lock và 237 bài test hồi quy kỹ thuật cũ.

---

## 2. PHÂN LOẠI TRẠNG THÁI CÁC THÀNH PHẦN (EXPLICIT CLASSIFICATION)

| Thành phần / Phân hệ | Phân loại trạng thái | Ghi chú chi tiết |
|:---|:---:|:---|
| **Bảo Khố (Vault Domain - CP4)** | **DONE** | Đã hoàn thành đầy đủ năng lực kỹ thuật và luồng sản phẩm theo Canonical Mục 6: Khám phá app cài đặt, thu nạp app, hiển thị ô item không có icon ổ khóa, gỡ app cascade quan hệ chéo, re-add không tự phục hồi liên kết cũ. |
| **Quan hệ Task ↔ App (Many-to-Many - CP6)** | **DONE** | Đã hoàn thành nghiệp vụ và lưu trữ liên kết N-N: Hỗ trợ trường hợp A (1 task 1 app), B (1 task nhiều app), C (nhiều task cùng 1 app); ràng buộc chặt chẽ chỉ lấy từ Bảo Khố; dialog liên kết trong Nhiệm Vụ Đường. |
| **Nhiệm Vụ Đường (Mission Hall - CP3, CP7)** | **PARTIAL** | Core Task Domain và chuỗi tuần tự advance tự động (CP3) tích hợp liên kết app Bảo Khố (CP7). Chưa có điểm tu vi (OPEN-02) và multimedia tiên hiệp. |
| **Ranh giới Thực thi (TaskAppEnforcementAdapter)** | **TECHNICAL FOUNDATION** | Thiết lập ranh giới adapter an toàn, trả về `DISABLED_PENDING_OPEN_01` (`isTaskBasedUnlockApproved = false`), chờ Ký chủ phê duyệt công thức giải phong ấn. |
| **Chu kỳ ngày 04:00 (Daily Cycle - CP5)** | **DONE** | Hoàn thành từ Phase 17 với `BusinessDayProvider`. Bảo Khố và liên kết Task-App KHÔNG bị reset lúc 04:00. |
| **Hệ thống Phong Ấn kỹ thuật (Frozen Core App Lock)** | **TECHNICAL FOUNDATION** | Tiếp tục vận hành ổn định qua DataStore và Accessibility Service. |
| **Công thức mở khóa (OPEN-01)** | **OPEN** | Giữ nguyên trạng thái OPEN. Tuyệt đối không tự động mở khóa. |
| **Điểm Tu Vi & Phần Thưởng (OPEN-02)** | **OPEN** | Chưa có hệ thống điểm. |
| **Lược đồ CSDL chính thức (OPEN-05)** | **OPEN** | Room Database hiện tại là Nền tảng kỹ thuật đề xuất cho CP3/CP4/CP5/CP6, giữ nguyên `version = 1`. |
| **Tu Luyện, Bí Cảnh, Tháp, Thương Thành, Túi Trữ Vật, Khí Linh** | **NOT IMPLEMENTED** | 0 dòng code, bảo toàn phạm vi. |

---

## 3. CHECKPOINTS TRACKING

- **CP1 (Accessibility & Background App Detection):** `DONE` (Duy trì 100%).
- **CP2 (Blocking Overlay & Session Guard):** `DONE` (Duy trì 100%).
- **CP3 (Core Task Domain & Sequential Chain):** `DONE` (Đã hoàn thành trong Phase 18).
- **CP4 (Vault Domain):** **`DONE`** (Hoàn thành chính thức trong Phase 19).
- **CP5 (Daily Cycle 04:00 Alignment):** `DONE` (Đã hoàn thành trong Phase 17).
- **CP6 (Task ↔ App Linkage):** **`DONE`** (Hoàn thành chính thức trong Phase 19).
- **CP7 (Basic Mission Hall Flow):** `DONE` (Đã hoàn thành trong Phase 18, nâng cấp tích hợp Bảo Khố trong Phase 19).
- **CP8 (Product Enforcement Integration):** **`TECHNICAL FOUNDATION`** (Adapter an toàn đã thiết lập nhưng CHƯA ĐƯỢC ĐÁNH DẤU `DONE` do `OPEN-01` chưa chốt).

---

## 4. CHI TIẾT HIỆN THỰC HÓA BẢO KHỐ (VAULT DOMAIN)

### 4.1. Khám phá Ứng dụng An toàn
- Triển khai `InstalledAppDiscoveryService`:
  - Sử dụng `PackageManager.queryIntentActivities` với intent `Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)`.
  - Khai báo thẻ `<queries>` trong `AndroidManifest.xml` tuân thủ Android 11+ / Android 15 Package Visibility Filtering.
  - Tự động lọc bỏ package của chính ứng dụng hiện tại (`com.example.selfdisciplinepoc01`).
  - Không đòi hỏi thêm bất kỳ quyền nguy hiểm nào từ hệ thống.

### 4.2. Giao diện Tuân thủ Canonical Mục 6
- Giao diện danh sách ứng dụng dạng ô item túi đồ / kho đồ tu tiên.
- **TUYỆT ĐỐI KHÔNG CÓ ICON Ổ KHÓA TRÊN AVATAR**: Theo Mục 6 Canonical Design V2, app phong ấn không được hiển thị icon ổ khóa thô thiển; app có thể hiện avatar xám hoặc phong cách tu tiên.
- Thẻ Quy Mô Bảo Khố hiển thị tổng số ứng dụng đã thu nạp và số ứng dụng đang có liên kết nhiệm vụ.

### 4.3. Vòng Đời Xóa (Cascade Removal) & Tính Bất Đối Xứng (Asymmetry)
- Khi Ký chủ gỡ App X khỏi Bảo Khố:
  - `RemoveVaultAppUseCase` xóa App X khỏi bảng `vault_apps`.
  - Toàn bộ liên kết trong `task_app_cross_ref` liên quan đến App X tự động bị xóa cascade.
  - Các nhiệm vụ trong `mission_tasks` và lịch sử hoàn thành trong `daily_task_completions` giữ nguyên 100%.
  - Các ứng dụng liên kết khác của cùng nhiệm vụ giữ nguyên vẹn.
- Khi thêm lại App X vào Bảo Khố:
  - App X xuất hiện trong Bảo Khố ở trạng thái chưa liên kết.
  - **CÁC LIÊN KẾT CŨ KHÔNG TỰ ĐỘNG PHỤC HỒI**. Phải chờ Ký chủ chủ động gán lại.

---

## 5. CHI TIẾT HIỆN THỰC HÓA QUAN HỆ TASK ↔ APP MANY-TO-MANY (CP6)

- Bảng dữ liệu Room: `TaskAppCrossRef(taskId: Long, packageName: String)`.
- Ràng buộc toàn vẹn:
  - Chỉ cho phép gán các ứng dụng đang tồn tại trong Bảo Khố (`vault_apps`).
  - Kiểm tra nghiêm ngặt trong `UpdateTaskLinkedAppsUseCase`: Nếu package không có trong Bảo Khố, tự động từ chối hoặc lọc bỏ.
- Hỗ trợ đầy đủ 3 trường hợp Many-to-Many:
  - **Trường hợp A:** Task A → App X.
  - **Trường hợp B:** Task A → App X & App Y.
  - **Trường hợp C:** Task A → App X & Task B → App X.
- Giao diện Nhiệm Vụ Đường:
  - Thẻ nhiệm vụ hiển thị danh sách các chip ứng dụng liên kết và nút `+ Liên kết App` / `Sửa liên kết`.
  - `TaskLinkageDialog` cho phép chọn/bỏ chọn ứng dụng từ Bảo Khố với Checkbox trực quan.
  - Cập nhật reactive tức thì trên UI khi có thay đổi từ Bảo Khố.

---

## 6. RANH GIỚI AN TOÀN OPEN-01 (TASK-APP ENFORCEMENT BOUNDARY)

- Tạo `TaskAppEnforcementAdapter.kt`:
  ```kotlin
  enum class TaskAppEnforcementStatus {
      DISABLED_PENDING_OPEN_01,
      ACTIVE,
      UNSUPPORTED
  }
  ```
- Hành vi bảo vệ:
  - Phương thức `evaluateTaskAppEnforcement(packageName)` trả về `TaskAppEnforcementDecision(isTaskBasedUnlockApproved = false, status = DISABLED_PENDING_OPEN_01)`.
  - Tuyệt đối không tự ý áp dụng công thức 2/3.
  - Không tự phát minh tỷ lệ % hay điểm thưởng để tự động mở khóa.
  - Tầng thực thi Frozen Core App Lock tiếp tục vận hành độc lập theo DataStore Policy mà không bị ảnh hưởng.

---

## 7. BẢO TOÀN FROZEN CORE & ĐIỀU HƯỚNG 3 TAB

- Tích hợp Material 3 `NavigationBar` tại `MainActivity.kt`:
  - **Tab 0:** Nhiệm Vụ Đường (`MissionHallScreen`).
  - **Tab 1:** Bảo Khố (`VaultScreen`).
  - **Tab 2:** Quản Trị Thực Thi (`SettingsScreen` — quản trị kỹ thuật App Lock cũ).
- Bảo toàn 100% Frozen Core:
  - `AppDetectorAccessibilityService`
  - `BlockingShieldOverlay`
  - `LockScreenActivity`
  - `PolicyEngine`, `ScheduleWatcher`, `UsageLimitWatcher`
  - `DataStore` target repository

---

## 8. KẾT QUẢ KIỂM THỬ (TEST MATRIX & AUTOMATION)

### 8.1. Unit Test Suite (286/286 PASS — 100%)
- `VaultDomainTest.kt` (22/22 PASS):
  - Nhóm A (Bảo Khố): thêm app, danh sách app, gỡ app, persistence, duplicate idempotent, re-add không tự phục hồi liên kết cũ.
  - Nhóm B (Many-to-Many): 1 task 1 app, 1 task nhiều app, nhiều task cùng 1 app, gỡ 1 link giữ nguyên link khác.
  - Nhóm C (Cascade): gỡ app xóa crossrefs, task giữ nguyên, lịch sử giữ nguyên, app khác giữ nguyên.
  - Nhóm D (Mission Hall): task hiện linked apps, chọn/bỏ chọn, cập nhật đồng bộ.
  - Nhóm E (OPEN-01 Protection): từ chối công thức 2/3, không tỷ lệ %, trả về DISABLED_PENDING_OPEN_01.
- `VaultViewModelTest.kt` (5/5 PASS).
- `MissionHallViewModelTest.kt` (9/9 PASS).
- Toàn bộ 250 bài test kỹ thuật và chu kỳ 04:00 cũ: PASS 100%.

### 8.2. Kiểm Chứng Thực Tế trên Thiết Bị (vivo iQOO Neo 10 / Android 15)
1. **Khởi động:** 3 Tab điều hướng hiển thị sắc nét, chuẩn xác.
2. **Bảo Khố:** Mở dialog thu nạp, tìm thấy danh sách app launcher trên máy, thêm thành công 2 app (`1.1.1.1` và `AgklbjBkqsM`), hiển thị dạng ô item túi đồ KHÔNG có icon ổ khóa.
3. **Nhiệm Vụ Đường:** Tạo nhiệm vụ `ChayBo`, mở dialog liên kết, chọn cả 2 app Bảo Khố, lưu liên kết, thẻ nhiệm vụ hiển thị 2 chip app liên kết.
4. **Cascade Removal:** Sang Bảo Khố, bấm Gỡ app `1.1.1.1`, xác nhận dialog cảnh báo; quay lại Nhiệm Vụ Đường: app `1.1.1.1` tự động biến mất khỏi task `ChayBo`, chỉ còn `AgklbjBkqsM`, task `ChayBo` và task hoàn thành trước đó vẫn còn nguyên vẹn 100%.
5. **Quản trị Thực thi:** Tab 2 hiển thị đầy đủ cấu hình kỹ thuật App Lock, không có bất kỳ regression hay crash/ANR nào.

---

## 9. CÁC TỆP ĐÃ TẠO & SỬA ĐỔI

### Tệp Tạo Mới:
- `app/src/main/java/com/example/selfdisciplinepoc01/domain/model/VaultApp.kt`
- `app/src/main/java/com/example/selfdisciplinepoc01/domain/discovery/InstalledAppDiscoveryService.kt`
- `app/src/main/java/com/example/selfdisciplinepoc01/domain/usecase/AddVaultAppUseCase.kt`
- `app/src/main/java/com/example/selfdisciplinepoc01/domain/usecase/RemoveVaultAppUseCase.kt`
- `app/src/main/java/com/example/selfdisciplinepoc01/domain/usecase/GetVaultAppsUseCase.kt`
- `app/src/main/java/com/example/selfdisciplinepoc01/domain/usecase/GetTaskLinkedAppsUseCase.kt`
- `app/src/main/java/com/example/selfdisciplinepoc01/domain/usecase/UpdateTaskLinkedAppsUseCase.kt`
- `app/src/main/java/com/example/selfdisciplinepoc01/domain/enforcement/TaskAppEnforcementAdapter.kt`
- `app/src/main/java/com/example/selfdisciplinepoc01/ui/vault/VaultViewModel.kt`
- `app/src/main/java/com/example/selfdisciplinepoc01/ui/vault/VaultScreen.kt`
- `app/src/test/java/com/example/selfdisciplinepoc01/domain/VaultDomainTest.kt`
- `app/src/test/java/com/example/selfdisciplinepoc01/ui/vault/VaultViewModelTest.kt`
- `docs/PHASE_19_VAULT_LINKAGE.md`

### Tệp Sửa Đổi:
- `app/src/main/AndroidManifest.xml` (bổ sung `<queries>`)
- `app/src/main/java/com/example/selfdisciplinepoc01/data/repository/CoreDataRepository.kt`
- `app/src/main/java/com/example/selfdisciplinepoc01/data/repository/CoreDataRepositoryImpl.kt`
- `app/src/main/java/com/example/selfdisciplinepoc01/ui/missionhall/MissionHallViewModel.kt`
- `app/src/main/java/com/example/selfdisciplinepoc01/ui/missionhall/MissionHallScreen.kt`
- `app/src/main/java/com/example/selfdisciplinepoc01/MainActivity.kt` (3 tabs navigation)
- `app/src/test/java/com/example/selfdisciplinepoc01/ui/missionhall/MissionHallViewModelTest.kt`
- `docs/IMPLEMENTATION_STATUS.md`
- `docs/DESIGN_AUDIT.md`
- `docs/DESIGN_DECISIONS.md`
- `docs/OPEN_ITEMS.md`
- `docs/CHANGELOG.md`

---

## 10. ĐỀ XUẤT CHO PHASE TIẾP THEO (PHASE 20)

Sau khi hoàn thành liên kết Bảo Khố và Nhiệm Vụ Đường (CP4, CP6):
1. Tham vấn Ký chủ về việc chốt **OPEN-01** (Công thức giải phong ấn nhiệm vụ) hoặc **OPEN-02** (Công thức điểm tu vi).
2. Nếu Ký chủ chốt OPEN-01: Tiến hành tích hợp thực thi phong ấn thực tế theo chuỗi nhiệm vụ vào `LockScreenActivity` và `PolicyEngine` (CP8).
3. Nếu Ký chủ muốn phát triển phân hệ tu luyện trước: Bắt đầu triển khai **Phase 20 — Cultivation & Progression Foundation** (Tu Luyện Core, Bí Cảnh theo Canonical Mục 9).
