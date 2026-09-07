# Đề Xuất Kiến Trúc Dữ Liệu Room Database (Proposed Room Schema)

> **TRẠNG THÁI QUẢN TRỊ (GOVERNANCE STATUS):**  
> **PROPOSAL — OPEN-05 REMAINS OPEN.**  
> *"Phase 17 introduces a proposed Room data foundation; this does not close OPEN-05."*  
> Đề xuất này là nền tảng kỹ thuật (Technical Foundation) cho Checkpoint CP3 (Core Task), CP5 (Daily Cycle 04:00) và CP6 (Vault App ↔ Task relations). Nó **KHÔNG PHẢI** là quyết định schema chính thức và **KHÔNG** đóng OPEN-05.

---

## 1. Phạm Vi Đề Xuất (Scope)

Đề xuất này chỉ tập trung vào tầng dữ liệu cốt lõi tối thiểu cho:
1. **Bảo Khố (Vault Apps):** Quản lý ứng dụng tự kỷ luật (`AppEntity`).
2. **Nhiệm Vụ Đường (Mission Tasks):** Quản lý danh sách nhiệm vụ tự kỷ luật (`TaskEntity`).
3. **Quan Hệ Nhiều - Nhiều (Task ↔ App Relationships):** Bảng liên kết chéo (`TaskAppCrossRef`).
4. **Trạng Thái Hoàn Thành Chu Kỳ Ngày (Daily Task Completion State):** Gắn liền với mốc thời gian reset 04:00 của `BusinessDayProvider` (`DailyTaskCompletionEntity`).

**Ngoài phạm vi (Strictly Out of Scope):**
- Không có entity cho: Tu Luyện (Cultivation), Tháp (Tower), Thương Thành (Shop), Túi Đồ (Inventory), Khí Linh (Spirit Assistant), Memory AI, Cloud Sync.
- Không tự ý đóng hay quyết định các công thức còn để ngỏ: OPEN-01 (công thức mở khóa / 2/3), OPEN-02 (hệ thống điểm thưởng), OPEN-03 (tháp/hình phạt).

---

## 2. Các Thực Thể Đề Xuất (Entities & Fields)

### 2.1. `AppEntity` (Bảng `vault_apps`)
Đại diện cho ứng dụng được quản lý trong Bảo Khố (Vault).

| Trường (Field) | Kiểu Dữ Liệu | Ràng Buộc | Mô Tả |
| :--- | :--- | :--- | :--- |
| `packageName` | `TEXT` | `PRIMARY KEY` | Định danh gói ứng dụng Android (vd: `com.facebook.katana`) |
| `appName` | `TEXT` | `NOT NULL` | Tên hiển thị người dùng (vd: "Facebook") |
| `isVaultManaged` | `INTEGER` (Boolean) | `DEFAULT 1` | Trạng thái ứng dụng đang nằm trong Bảo Khố |
| `createdAtWallMillis`| `INTEGER` (Long) | `NOT NULL` | Thời điểm thêm vào Bảo Khố (Wall Clock) |

### 2.2. `TaskEntity` (Bảng `mission_tasks`)
Đại diện cho nhiệm vụ tự kỷ luật tạo tại Nhiệm Vụ Đường (Mission Hall).

| Trường (Field) | Kiểu Dữ Liệu | Ràng Buộc | Mô Tả |
| :--- | :--- | :--- | :--- |
| `id` | `INTEGER` (Long) | `PRIMARY KEY AUTOINCREMENT` | Mã định danh duy nhất của nhiệm vụ |
| `name` | `TEXT` | `NOT NULL` | Tên nhiệm vụ (Tối giản: Tên + Xác nhận, theo Section 5 Canonical) |
| `createdAtWallMillis`| `INTEGER` (Long) | `NOT NULL` | Thời điểm tạo nhiệm vụ |
| `isArchived` | `INTEGER` (Boolean) | `DEFAULT 0` | Trạng thái lưu trữ/xoá mềm khi không còn kích hoạt |

### 2.3. `TaskAppCrossRef` (Bảng `task_app_cross_ref`)
Bảng liên kết Nhiều - Nhiều giữa Nhiệm Vụ và Ứng Dụng Bảo Khố.

| Trường (Field) | Kiểu Dữ Liệu | Ràng Buộc | Mô Tả |
| :--- | :--- | :--- | :--- |
| `taskId` | `INTEGER` (Long) | `FOREIGN KEY` -> `mission_tasks(id)` ON DELETE CASCADE | ID nhiệm vụ |
| `appPackageName` | `TEXT` | `FOREIGN KEY` -> `vault_apps(packageName)` ON DELETE CASCADE | Package name ứng dụng |
| `linkedAtWallMillis` | `INTEGER` (Long) | `NOT NULL` | Thời điểm tạo liên kết |

- **Khóa chính (Primary Key):** Composite `[taskId, appPackageName]`.
- **Chỉ mục (Indexes):**
  - Index trên `taskId`
  - Index trên `appPackageName`

### 2.4. `DailyTaskCompletionEntity` (Bảng `daily_task_completions`)
Ghi nhận trạng thái hoàn thành nhiệm vụ theo chu kỳ ngày kinh doanh 04:00.

| Trường (Field) | Kiểu Dữ Liệu | Ràng Buộc | Mô Tả |
| :--- | :--- | :--- | :--- |
| `taskId` | `INTEGER` (Long) | `FOREIGN KEY` -> `mission_tasks(id)` ON DELETE CASCADE | ID nhiệm vụ |
| `businessDate` | `TEXT` | `NOT NULL` | Ngày kinh doanh chuẩn hoá `yyyy-MM-dd` tính theo mốc 04:00 |
| `isCompleted` | `INTEGER` (Boolean) | `DEFAULT 1` | Đã hoàn thành trong chu kỳ |
| `completedAtWallMillis` | `INTEGER` (Long) | `NOT NULL` | Thời điểm ấn hoàn thành (Wall Clock) |

- **Khóa chính (Primary Key):** Composite `[taskId, businessDate]`.
- **Chỉ mục (Indexes):**
  - Index trên `businessDate`
  - Index trên `taskId`

---

## 3. Mối Quan Hệ & Ràng Buộc Nghiệp Vụ (Relationships & Invariants)

1. **Nhiều - Nhiều (Many-to-Many):**
   - Một Nhiệm Vụ có thể mở khóa nhiều Ứng Dụng.
   - Một Ứng Dụng có thể được mở khóa bởi nhiều Nhiệm Vụ khác nhau.
   - **Quy tắc Canonical Design V2 (Mục 5 & 6):** Nếu 1 App liên kết với 2 Task thì đây là **2 Task riêng biệt**, tính hoàn thành độc lập, không gộp lại.
2. **Quy tắc Xóa khỏi Bảo Khố (Vault Removal Cascade):**
   - Khi xóa một Ứng Dụng khỏi Bảo Khố (`vault_apps`), liên kết trong `task_app_cross_ref` sẽ bị xóa bỏ tự động (`ON DELETE CASCADE`), nhưng bản thân `TaskEntity` vẫn tồn tại nguyên vẹn.
3. **Chu Kỳ Ngày 04:00 Tự Nhiên (Natural Daily Reset):**
   - Bằng việc lưu trạng thái hoàn thành theo cặp `(taskId, businessDate)`, khi bước qua mốc 04:00 (chu kỳ mới), truy vấn trạng thái ngày mới sẽ trả về rỗng/chưa hoàn thành một cách tự nhiên.
   - Không cần chạy cron job quét và reset cờ trong bảng `mission_tasks`, tránh race condition và bảo toàn lịch sử hoàn thành của các chu kỳ cũ.

---

## 4. Lý Do Thiết Kế & Căn Cứ (Rationale)

- **Đơn giản, tách biệt quan tâm (Separation of Concerns):** Giữ entity static (Task definition) tách rời khỏi entity dynamic (Completion state per cycle).
- **Tuân thủ triệt để Canonical Design V2:**
  - Mục 5: Tạo nhiệm vụ tối giản, task tuần tự.
  - Mục 6: Bảo Khố quản lý app tự kỷ luật, xoá app gỡ task.
  - Mục 8: Chu kỳ reset 04:00.
- **Sẵn sàng mở rộng tương lai:** Khi các OPEN items (OPEN-01, OPEN-02) được định nghĩa, chỉ cần bổ sung các trường tính toán (thời gian mở khóa, điểm thưởng, reward type) mà không phá vỡ cấu trúc khóa và quan hệ đã thiết lập.

---

## 5. Chiến Lược Song Song & Di Trú An Toàn (Coexistence & Migration Strategy)

### 5.1. Phân định rõ ràng giữa Runtime Enforcement vs Core Data
- **DataStore hiện tại (Technical Target Management):** Tiếp tục chịu trách nhiệm lưu trữ cấu hình khóa ứng dụng (`target_packages`, `policy_configs`, `schedules`, `daily_limits`, `usage_records`) phục vụ trực tiếp cho runtime accessibility service, watchers, overlay.
- **Room Database đề xuất (Core Data Foundation):** Đóng vai trò là nền tảng nghiệp vụ sản phẩm (Product Vault & Mission Hall) trong tương lai.

### 5.2. Không Di Trú Mù (No Blind Migration)
- Không có bất kỳ migration tự động nào chuyển từ DataStore sang Room trong phase này.
- Mọi dữ liệu hiện có trong DataStore của người dùng và các bài kiểm thử hồi quy được bảo toàn 100%.

---

## 6. Các Quyết Định Chưa Giải Quyết & Mối Quan Hệ Với OPEN-05

| Mục | Tình Trạng | Ghi Chú |
| :--- | :--- | :--- |
| **OPEN-05: Room Database Schema Chính Thức** | **OPEN** | Đề xuất này cung cấp Technical Foundation, **chưa phải** là schema chính thức cuối cùng của toàn bộ sản phẩm. |
| Cơ chế đồng bộ giữa Vault App và LockedApp runtime | Chưa chốt | Sẽ được giải quyết khi xây dựng Module Bảo Khố (CP6). |
| Trường lưu trữ công thức thưởng / mở khóa trong Task | Tạm hoãn | Chờ OPEN-01 và OPEN-02 được quyết định. |
| Schema lưu trữ Tu Luyện / Tháp / Khí Linh | Chưa thiết kế | Hoàn toàn nằm ngoài phạm vi Phase 17. |
