# QUYẾT ĐỊNH THIẾT KẾ & QUY TẮC QUẢN TRỊ (DESIGN DECISIONS & GOVERNANCE)

**Dự án:** Hệ Thống Tự Kỷ Luật Bản Thân (`self-discipline-poc-01`)  
**Tài liệu tham chiếu chuẩn:** [`docs/CANONICAL_DESIGN_V2.md`](file:///c:/Code/self-discipline-poc-01/docs/CANONICAL_DESIGN_V2.md)  
**Ngày thiết lập:** 04/09/2026 (Phase 16)  
**Mục đích:** Thiết lập cơ chế phân tầng thẩm quyền (hierarchy) và quy tắc quản trị thiết kế vĩnh viễn cho dự án, đảm bảo mọi pha phát triển đều bám sát Canonical Design V2.

---

## 1. PHÂN TẦNG THẨM QUYỀN HỆ THỐNG (AUTHORITY HIERARCHY)

Dự án thiết lập trật tự thẩm quyền nghiêm ngặt gồm 4 cấp độ từ cao xuống thấp:

```
[CẤP 1] CANONICAL DESIGN (Tài liệu đặc tả sản phẩm tối cao — docs/CANONICAL_DESIGN_V2.md)
   │
   ▼
[CẤP 2] APPROVED ARCHITECTURE / TECHNICAL CONTRACT (Kiến trúc kỹ thuật đã duyệt & Design Decisions)
   │
   ▼
[CẤP 3] IMPLEMENTATION (Mã nguồn sản xuất hiện tại trong codebase)
   │
   ▼
[CẤP 4] POC / EXPERIMENT (Mã nguồn thử nghiệm, kiểm chứng khả thi)
```

### Quy tắc điều hành thẩm quyền:
- **Khi Implementation khác Canonical Design:**  
  -> **TUYỆT ĐỐI KHÔNG** sửa Canonical Design chỉ để hợp thức hóa đoạn code đã viết.  
  -> Xác định rõ ràng: Code đang sai lệch so với thiết kế hay Ký chủ thực sự đã đưa ra quyết định thay đổi sản phẩm.  
  -> Nếu code sai: ghi nhận là `SAI` và lên kế hoạch khắc phục trong phase phù hợp.  
  -> Nếu thiết kế thay đổi: phải được Ký chủ phê duyệt bằng văn bản trước khi cập nhật tài liệu.
- **Khi Design chưa quyết định (Các mục OPEN):**  
  -> Bắt buộc giữ nguyên trạng thái `OPEN`.  
  -> **TUYỆT ĐỐI KHÔNG** tự ý tạo ra hành vi nghiệp vụ chính thức thay thế.
- **Khi cần thay đổi thiết kế:**  
  -> Phải tạo Design Decision trước trong tài liệu này kèm lý do và căn cứ.  
  -> Sau đó mới tiến hành sửa đổi implementation ở phase tương ứng.

---

## 2. MƯỜI NGUYÊN TẮC QUẢN TRỊ THIẾT KẾ BẮT BUỘC (MANDATORY GOVERNANCE RULES)

Mọi hoạt động phát triển, viết mã, kiểm toán và mở rộng hệ thống bắt buộc phải tuân thủ nghiêm ngặt 10 quy tắc sau:

- **RULE 1 (Single Source of Truth):**  
  Tài liệu [`CANONICAL_DESIGN_V2.md`](file:///c:/Code/self-discipline-poc-01/docs/CANONICAL_DESIGN_V2.md) là Nguồn Sự Thật Duy Nhất (Single Source of Truth) tối cao của toàn bộ sản phẩm.
- **RULE 2 (Code is not the Spec):**  
  Mã nguồn hiện tại **KHÔNG PHẢI** là đặc tả kỹ thuật của sản phẩm. Code được viết ra để hiện thực hóa thiết kế, chứ không có quyền tự định nghĩa lại nghiệp vụ sản phẩm.
- **RULE 3 (POC cannot redefine Requirements):**  
  Một POC kỹ thuật trước đó (ví dụ POC App Detection / App Lock) tuyệt đối **KHÔNG ĐƯỢC PHÉP** tái định nghĩa yêu cầu sản phẩm hay giới hạn phạm vi sản phẩm vào những gì POC đã làm.
- **RULE 4 (Contradiction Resolution):**  
  Nếu phát hiện mã nguồn mâu thuẫn với Canonical Design:
  1. Phải ghi nhận và định danh chính xác điểm mâu thuẫn.
  2. Xác định rõ Canonical Design là thẩm quyền cao nhất trừ khi Ký chủ (Người dùng) có quyết định sản phẩm mới.
  3. Tuyệt đối **KHÔNG ĐƯỢC ÂM THẦM** sửa thiết kế trong tài liệu chỉ để hợp thức hóa đoạn code đã viết sai.
- **RULE 5 (Explicit Modification):**  
  Chỉ có một quyết định sản phẩm mới, rõ ràng và có chủ đích từ phía Ký chủ (Người dùng) mới được phép chỉnh sửa hoặc cập nhật Canonical Design.
- **RULE 6 (OPEN items remain OPEN):**  
  Các mục chưa chốt (OPEN items) bắt buộc phải được giữ nguyên trạng thái `OPEN`.
- **RULE 7 (No Business Fabrication for OPEN):**  
  Trí tuệ nhân tạo (AI) tuyệt đối **KHÔNG ĐƯỢC TỰ Ý BỊA ĐẶT** hành vi, công thức hoặc quy tắc nghiệp vụ cho các mục OPEN khi chưa có quyết định chính thức từ Ký chủ.
- **RULE 8 (Declaration of Future Phases):**  
  Mọi giai đoạn triển khai (phase) trong tương lai bắt buộc phải khai báo rõ ràng:
  - Phân hệ Canonical Design tương ứng.
  - Yêu cầu chính xác đang được triển khai.
  - Trạng thái triển khai hiện tại.
  - Các phụ thuộc (dependencies).
  - Phương pháp kiểm thử / xác thực (validation method).
  - Tác động dự kiến lên thiết kế (expected design impact).
- **RULE 9 (Design Compliance Check):**  
  Mọi phase tương lai bắt buộc phải thực hiện bước Kiểm Tra Tuân Thủ Thiết Kế (Design Compliance Check) trước khi đóng phase.
- **RULE 10 (Technical Foundation vs Product Feature):**  
  Một cải tiến kỹ thuật không tương ứng trực tiếp với một yêu cầu sản phẩm trong Canonical Design bắt buộc phải được định danh là **TECHNICAL FOUNDATION (NỀN TẢNG KỸ THUẬT)**, tuyệt đối không được báo cáo là một tính năng sản phẩm đã hoàn thành (completed product feature).

---

## 3. CÁC QUYẾT ĐỊNH THIẾT KẾ ĐÃ XÁC LẬP (CANONICAL ARCHITECTURAL DECISIONS)

Tất cả các quyết định dưới đây được trích xuất trực tiếp từ các mục tương ứng của [`docs/CANONICAL_DESIGN_V2.md`](file:///c:/Code/self-discipline-poc-01/docs/CANONICAL_DESIGN_V2.md):

### DEC-01: Định vị Phân hệ App Lock trong Tổng thể Sản phẩm
- **Nguồn trích:** Canonical Design V2 — Mục 3 & Mục 7.
- **Nội dung:** Hệ thống phát hiện app và màn hình khóa hiện tại chỉ là **Hệ thống Phong Ấn (Enforcement Core)** của sản phẩm.
- **Ranh giới:** Không đồng nhất App Lock với toàn bộ sản phẩm "Hệ Thống Tự Kỷ Luật Bản Thân". Toàn bộ các phân hệ Nhiệm Vụ Đường, Bảo Khố, Tu Luyện, Thương Thành, Túi Trữ Vật, Khí Linh nằm ở tầng trên điều khiển tầng thực thi này.

### DEC-02: Chu kỳ ngày và Mốc Reset 04:00 (Giờ Dần)
- **Nguồn trích:** Canonical Design V2 — Mục 8.
- **Nội dung:** Hệ thống tính chu kỳ ngày và thực hiện reset trạng thái nhiệm vụ tại mốc **04:00 sáng**.
- **Quy tắc:** Không áp dụng bộ đếm 24h trôi qua kể từ lúc hoàn thành. Nhiệm vụ bắt đầu trước 04:00 vẫn thuộc về chu kỳ ngày cũ.

### DEC-03: Mô hình Dữ liệu Độc lập: Task <-> App (Many-to-Many)
- **Nguồn trích:** Canonical Design V2 — Mục 4, Mục 5, Mục 6.
- **Nội dung:** Mối quan hệ giữa Nhiệm vụ và Ứng dụng trong Bảo Khố là Many-to-Many:
  - Một nhiệm vụ có thể liên kết giải phong ấn cho nhiều ứng dụng.
  - Một ứng dụng có thể gắn nhiều nhiệm vụ; mỗi nhiệm vụ tính độc lập, không tự động gộp.
  - Việc liên kết app với nhiệm vụ sẽ kích hoạt trạng thái phong ấn ngay lập tức.

### DEC-04: Khí Linh (AI Core) — Phân tách Logic và UX
- **Nguồn trích:** Canonical Design V2 — Mục 13, Mục 17, Mục 18.
- **Nội dung:** Khí Linh là một module bên trong ứng dụng, có quyền tự chủ cao trong việc lập kế hoạch, tự sửa lỗi kỹ thuật, sắp xếp dependency.
- **Ranh giới:** Khí Linh bị giới hạn nghiêm ngặt bởi Confirmation Scope, Permission, không tự ý xóa dữ liệu lớn hoặc thay đổi mục tiêu của Ký chủ. Phong cách đối thoại Tiên Hiệp (gọi Ký chủ, giọng loli) là lớp UX, tách rời khỏi logic nghiệp vụ cốt lõi.

### DEC-05: Mô hình Khí Linh Memory Hybrid (Cloud + On-Device)
- **Nguồn trích:** Canonical Design V2 — Mục 15, Mục 16, Mục 19.
- **Nội dung:** Phân tách rạch ròi 5 tầng dữ liệu trí nhớ:
  1. *History:* Toàn bộ nhật ký hành động đã diễn ra.
  2. *Memory:* Thông tin sở thích có giá trị dùng lâu dài.
  3. *Temporary Rule:* Xu hướng mới đang quan sát, chưa đủ bằng chứng.
  4. *Official Rule:* Quy tắc chính thức đã được Ký chủ xác lập.
  5. *Exception:* Ngoại lệ có chủ đích của một lần cụ thể.

### DEC-06: Kiến trúc Nền tảng Thực thi (Technical Enforcement Baseline)
- **Nguồn trích:** Canonical Design V2 — Mục 14, Mục 24, Mục 25.
- **Nội dung:** Nền tảng thực thi đã kiểm chứng qua POC-01 đến Phase 15 bao gồm:
  - Android Accessibility Service bắt sự kiện mở ứng dụng (Android 15 / OriginOS 5).
  - BlockingShieldOverlay (Window overlay TYPE_APPLICATION_OVERLAY) chặn tương tác tức thì.
  - LockScreenActivity làm giao diện chặn tập trung.
  - Session Guard và Stale Callback Protection chống race condition.
  - Schedule Evaluator & Daily Limit Evaluator cho quản lý hạn ngạch.
  - Diagnostic In-Memory Event Ring Buffer cho khả năng quan sát (observability).

---

## 4. TÓM TẮT QUY TẮC QUYẾT ĐỊNH CỦA KHÍ LINH (Q1 – Q75 DECISION SUMMARY)
- **Nguồn trích:** Canonical Design V2 — Mục 17.
- **Nguyên tắc cốt lõi:**
  - *Xác nhận (Confirmation):* Q1 (phạm vi nghiêm ngặt, hành động mới phải đánh giá lại).
  - *Sở thích & Ngoại lệ:* Q3-Q5, Q18-Q22, Q61-Q74 (phân biệt rõ ràng giữa ngoại lệ tạm thời và thay đổi quy tắc chính thức; không đổi rule vì một câu đơn lẻ).
  - *Tự chủ & Tự khắc phục lỗi:* Q23-Q29, Q35-Q36, Q59-Q60 (tự khắc phục lỗi kỹ thuật trong quyền tự chủ, ghi nhật ký, dừng khi vượt quyền hoặc rủi ro cao).
  - *Quản lý Tác vụ & Dependency:* Q30-Q43 (tác vụ độc lập chạy song song, tác vụ phụ thuộc phải chờ tiền đề; tiền đề lỗi thì hủy nhánh phụ thuộc nhưng giữ tác vụ độc lập).
  - *Dừng / Hủy / Khôi phục:* Q44-Q49 (dừng an toàn, hủy giữ lịch sử, khôi phục từ điểm hợp lệ cuối, giải quyết xung đột dữ liệu minh bạch).
  - *Giao tiếp khi bế tắc:* Q50-Q52, Q68 (câu hỏi ngắn gọn, kèm đề xuất cụ thể; không tự đoán khi thiếu thông tin).
  - *Quyền tự quyết phần còn lại:* Q75 (Ký chủ giao quyền tự quyết các chi tiết kỹ thuật nhỏ bên trong quyền tự chủ; chỉ hỏi khi không thể suy ra ý định hoặc vượt quyền).

---

## 5. TÍNH CHẤT CỦA PHASE 16 (PHASE 16 GOVERNANCE SCOPE)
- **Phase 16 là giai đoạn AUDIT & GOVERNANCE thuần túy.**
- **KHÔNG KHÓA THÊM BẤT KỲ BUSINESS RULE MỚI NÀO.**
- Toàn bộ các mục OPEN tiếp tục duy trì trạng thái mở cho đến khi Ký chủ phê duyệt bằng quyết định riêng.

---

## 6. QUYẾT ĐỊNH KỸ THUẬT & QUẢN TRỊ TRONG PHASE 17 (PHASE 17 TECHNICAL ALIGNMENT & DATA FOUNDATION)

### 6.1. Hiện Thực Hóa Chu Kỳ Ngày 04:00 (DEC-02 Implementation)
- **Abstraction:** Đưa vào sử dụng `BusinessDayProvider` với `boundaryHour = 4`, `boundaryMinute = 0`.
- **Tách bạch thời gian:**
  - **Wall Clock + ZoneId:** Dùng cho chu kỳ ngày nghiệp vụ (Business Day), ngày lịch, lịch trình khóa (Schedule).
  - **Elapsed Realtime:** Dùng riêng cho đo lường thời lượng phiên (duration / session timing).
- **Tích hợp:** `UsageTracker` phân bổ thời lượng sử dụng qua mốc 04:00; các phiên chạy qua nửa đêm (23:30 - 01:30) vẫn nằm trong cùng một Business Day.

### 6.2. Nền Tảng Dữ Liệu Cốt Lõi Đề Xuất (Core Data Architecture Proposal)
- **Cơ chế lưu trữ:** Đưa vào Room Database (`AppDatabase`) với 4 Entity đề xuất:
  - `AppEntity` (Bảo Khố)
  - `TaskEntity` (Nhiệm Vụ Đường)
  - `TaskAppCrossRef` (Quan hệ Nhiều - Nhiều giữa Task và App)
  - `DailyTaskCompletionEntity` (Trạng thái hoàn thành gắn với Business Date 04:00)
- **Quản trị OPEN-05:**
  - **OPEN-05 VẪN Ở TRẠNG THÁI OPEN.**
  - Cấu trúc Room Database chỉ là **Nền tảng kỹ thuật đề xuất (Technical Foundation Proposal)** cho Checkpoints CP3, CP5, CP6, không phải schema chính thức đã chốt.
- **Ranh giới Coexistence (Song song an toàn):**
  - **DataStore hiện tại:** Giữ nguyên 100% cho cấu hình khóa (`target_packages`, `policy_configs`, `daily_limits`, `schedules`, `usage_records`) phục vụ runtime App Lock. Tuyệt đối không xóa bỏ hay di trú mù.
  ---

## 7. QUYẾT ĐỊNH KỸ THUẬT & QUẢN TRỊ TRONG PHASE 18 (PHASE 18 CORE TASK DOMAIN & BASIC MISSION HALL)

### 7.1. Hiện Thực Hóa Core Task Domain & Chuỗi Tuần Tự (Sequential Task Chain)
- **Domain Models:**
  - `Task`: Mô hình hóa nhiệm vụ tối giản (`id`, `name`, `createdAtWallMillis`, `isArchived`).
  - `SequentialTaskChain`: Chuỗi nhiệm vụ tuần tự với `currentTask` (nhiệm vụ hiện tại đang cần làm), `incompleteTasks` (danh sách chưa hoàn thành), `completedTasksToday` (danh sách đã hoàn thành hôm nay), `totalActiveTasks`, `completedCountToday`.
- **Hành vi chuỗi (Canonical Design V2 — Mục 5):**
  - Nhiệm vụ hiển thị theo thứ tự khởi tạo.
  - Khi hoàn thành nhiệm vụ hiện tại, hệ thống tự động chuyển sang nhiệm vụ tiếp theo trong chuỗi (`currentTask = incomplete.firstOrNull()`).
  - Nhiệm vụ đã hoàn thành trong chu kỳ ngày hiện tại lập tức biến khỏi danh sách chưa hoàn thành (`incompleteTasks`).
  - Nhiệm vụ bị xóa/lưu trữ (`archive`) giữa chừng tự động bị bỏ qua; chuỗi tự động trỏ tới nhiệm vụ hợp lệ kế tiếp.
  - Khi toàn bộ nhiệm vụ trong ngày hoàn thành: `isAllCompleted = true`, `currentTask = null`.

### 7.2. Tích Hợp Chu Kỳ Reset 04:00 (DEC-02 Integration)
- **Hoàn thành gắn mốc 04:00:** Sử dụng `BusinessDayProvider` để xác định `businessDate` (YYYY-MM-DD) tại mốc 04:00:00 sáng.
- **Idempotency:** Hoàn thành nhiều lần trong cùng chu kỳ không tạo bản ghi trùng lặp và không gây lỗi.
- **Task vắt qua 04:00:** Nhiệm vụ bắt đầu trước 04:00 và hoàn thành sau 04:00 được ghi nhận cho chu kỳ ngày cũ theo đúng quy định tại Canonical Design V2 (Mục 8).
- **Tự động reset:** Sang chu kỳ mới (sau 04:00 hôm sau), các nhiệm vụ chưa hoàn thành của chu kỳ mới tự động hiển thị lại mà không cần cron job hay background batch mutation.

### 7.3. Tạo Nhiệm Vụ Tối Giản (Minimal Task Creation)
- **UI:** Nút thêm nhiệm vụ nhỏ gọn (Floating Action Button kích thước gọn) đặt tại góc dưới bên phải màn hình Nhiệm Vụ Đường.
- **Dialog tối giản:** Gồm ô nhập Tên nhiệm vụ và nút Xác nhận (kèm nút Hủy).
- **Validation:** Tự động cắt khoảng trắng thừa (`trim()`), từ chối chuỗi rỗng hoặc chỉ chứa khoảng trắng với thông báo lỗi rõ ràng.

### 7.4. Bảo Tồn Nền Tảng App Lock Cũ Qua NavigationBar
- **Tích hợp giao diện:** Sử dụng Material 3 `NavigationBar` tại `MainActivity.kt`:
  - **Tab 0:** Nhiệm Vụ Đường (`MissionHallScreen`).
  - **Tab 1:** Quản Trị Thực Thi (giao diện App Lock và cấu hình policy cũ).
- **Bảo toàn 100% Frozen Core:** Không thay đổi bất kỳ hành vi hay intent filter nào của App Lock. Bảo vệ toàn bộ 237 bài test hồi quy cũ.

### 7.5. Quản Trị OPEN Items Trong Phase 18
- **OPEN-01, OPEN-02, OPEN-03, OPEN-05, OPEN-06, OPEN-07 TIẾP TỤC GIỮ NGUYÊN TRẠNG THÁI OPEN.**
- Tuyệt đối không tự ý áp dụng công thức 2/3 (OPEN-01) hay hệ thống điểm thưởng tu vi (OPEN-02).
- Không thêm các phân hệ Tu Luyện, Tháp Thí Luyện, Thương Thành, Túi Trữ Vật, Khí Linh AI Core, hay audio/visual assets vào mã nguồn.

---

## 8. QUYẾT ĐỊNH KỸ THUẬT & QUẢN TRỊ TRONG PHASE 19 (PHASE 19 VAULT INTEGRATION & TASK ↔ APP LINKAGE)

### 8.1. Hiện Thực Hóa Phân Hệ Bảo Khố (Vault Domain — CP4)
- **Domain Models:**
  - `DiscoveredApp`: Ứng dụng đã cài đặt trên thiết bị được tìm thấy qua launcher intent query.
  - `VaultApp`: Ứng dụng trong Bảo Khố (`packageName`, `appName`, `addedAtWallMillis`, `isLockedByDefault`).
- **Khám phá ứng dụng an toàn:**
  - Sử dụng `InstalledAppDiscoveryService` với `PackageManager.queryIntentActivities` lọc theo `Intent.ACTION_MAIN` và `Intent.CATEGORY_LAUNCHER`.
  - Khai báo `<queries>` hợp lệ trong `AndroidManifest.xml` tuân thủ bảo mật Android 11+ / 15.
  - Loại bỏ package ứng dụng hiện tại khỏi danh sách. Không yêu cầu thêm quyền nguy hiểm mới.
- **Tuân thủ thiết kế Canonical Mục 6:**
  - Giao diện danh sách dạng ô túi đồ / item kho đồ tu tiên.
  - **Tuyệt đối KHÔNG hiển thị icon ổ khóa trên avatar** theo đúng quy định cấm tại Canonical Design V2 Mục 6.

### 8.2. Hiện Thực Hóa Quan Hệ Task ↔ App Many-to-Many (CP6)
- **Lưu trữ chuẩn tắc:** Bảng nối quan hệ `TaskAppCrossRef` trong Room Database (`taskId`, `packageName`).
- **Hỗ trợ đầy đủ 3 kịch bản Many-to-Many bắt buộc:**
  - Trường hợp A: Task A → App X.
  - Trường hợp B: Task A → App X và App Y.
  - Trường hợp C: Task A → App X và Task B → App X.
- **Ràng buộc nghiệp vụ (Canonical Mục 5, 6):**
  - Chỉ cho phép liên kết với các ứng dụng **đang hiện diện trong Bảo Khố** (`vault_apps`).
  - Giao diện Nhiệm Vụ Đường hiển thị các chip ứng dụng liên kết và nút chọn liên kết.

### 8.3. Lifecycle Cascade Removal & Tính Bất Đối Xứng Khi Re-add (Mục 6)
- **Cascade Removal:** Khi Ký chủ gỡ App X khỏi Bảo Khố:
  - App X bị xóa khỏi bảng `vault_apps`.
  - Toàn bộ các dòng `TaskAppCrossRef` trỏ tới App X tự động bị xóa sạch.
  - Bản thân các nhiệm vụ (`tasks`) và lịch sử hoàn thành (`daily_task_completions`) được giữ nguyên vẹn 100%.
  - Các app liên kết khác của cùng nhiệm vụ vẫn giữ nguyên.
- **Tính bất đối xứng (Asymmetry):** Nếu App X được thêm lại vào Bảo Khố sau đó, các liên kết cũ **KHÔNG TỰ ĐỘNG PHỤC HỒI**. App X trở lại trạng thái chưa liên kết nhiệm vụ nào cho đến khi Ký chủ chủ động gán lại.

### 8.4. Ranh Giới An Toàn OPEN-01 (TaskAppEnforcementAdapter)
- **Phân tách rạch ròi:**
  - Product Vault & Task Linkage (Room Database).
  - Technical App Lock Configuration (DataStore & PolicyEngine).
- **Rào chắn an toàn (Safety Barrier):**
  - Xây dựng `TaskAppEnforcementAdapter` làm ranh giới cô lập.
  - Do `OPEN-01` vẫn đang OPEN, adapter trả về trạng thái rõ ràng `DISABLED_PENDING_OPEN_01` và `isTaskBasedUnlockApproved = false`.
  - Tuyệt đối không tự ý áp dụng công thức 2/3, không tự phát minh tỷ lệ %, không tính toán điểm thưởng để tự động mở khóa.
  - Tầng thực thi Frozen Core App Lock tiếp tục vận hành độc lập theo DataStore Policy mà không bị phá vỡ.

### 8.5. Tích Hợp Điều Hướng 3 Tab
- Sử dụng NavigationBar gồm 3 Tab rõ ràng:
  - **Tab 0:** Nhiệm Vụ Đường (`MissionHallScreen`).
  - **Tab 1:** Bảo Khố (`VaultScreen`).
  - **Tab 2:** Quản Trị Thực Thi (`SettingsScreen` - quản trị kỹ thuật App Lock cũ).
- Bảo toàn 100% khả năng truy cập cấu hình kỹ thuật của hệ thống Phong Ấn cũ.

### 8.6. Quản Trị OPEN Items Trong Phase 19
- **OPEN-01, OPEN-02, OPEN-03, OPEN-04, OPEN-05, OPEN-06, OPEN-07 TIẾP TỤC GIỮ NGUYÊN TRẠNG THÁI OPEN.**

---

## 9. QUYẾT ĐỊNH KỸ THUẬT & QUẢN TRỊ TRONG PHASE 20 (PHASE 20 APP LOCK INTEGRATION DESIGN)

### 9.1. Kiến Trúc Tích Hợp Đa Tầng (Multi-Tier Integration Architecture)
- Thiết lập dòng dữ liệu chuẩn:
  ```
  Mission Domain (Tasks / Chains / Daily Completions)
          ↓
  Vault Domain (Vault Apps / TaskAppCrossRef)
          ↓
  TaskAppEnforcementAdapter (Classification & Enforcement Evaluation)
          ↓
  App Lock Enforcement Decision (Technical Priority -> Vault Policy)
          ↓
  Accessibility Shield / LockScreenActivity
  ```
- **Separation of Concerns:**
  * Mission & Vault Domains chịu trách nhiệm toàn bộ logic nghiệp vụ (business state).
  * `TaskAppEnforcementAdapter` chuyển đổi trạng thái nghiệp vụ thành đầu vào thực thi kỹ thuật (`AppEnforcementDetails`).
  * `PolicyEngine` và `AppDetectorAccessibilityService` chịu trách nhiệm thực thi phong ấn (enforcement execution).
  * UI hoàn toàn không can dự và không phải là source of truth của luồng thực thi.

### 9.2. Phân Định Quyền Ưu Tiên Tuyệt Đối (Technical App Lock Precedence)
- Technical Lock (`PolicyEngine` / `TargetRepository` với lịch trình Schedule & Daily Limit) luôn có độ ưu tiên cao nhất.
- Nếu Technical Lock đánh giá cấm (`LOCK`), hệ thống lập tức phong ấn mà không phụ thuộc vào trạng thái nhiệm vụ hay Bảo Khố. Adapter tuyệt đối không thể bypass Technical Lock.

### 9.3. Hợp Đồng Tích Hợp & Phân Loại 3 Nhóm Ứng Dụng
- Adapter phân loại mọi ứng dụng thành 3 nhóm rõ ràng:
  1. `NON_VAULT_APP`: Không có trong Bảo Khố.
     * Nếu không bị Technical Lock cấm: `action = ALLOW`, `reason = ALLOWED_NOT_PROTECTED`.
  2. `VAULT_APP_UNLINKED`: Có trong Bảo Khố nhưng chưa được gán bất kỳ nhiệm vụ nào.
     * Luôn luôn phong ấn: `action = LOCK`, `reason = LOCKED_BY_VAULT_NO_TASK`, `decision = NOT_APPLICABLE`.
  3. `VAULT_APP_WITH_TASKS`: Có trong Bảo Khố và đã được liên kết với ít nhất một nhiệm vụ.
     * Nếu còn nhiệm vụ chưa xong hoặc tất cả nhiệm vụ đã xong: `action = LOCK`, `reason = LOCKED_PENDING_BUSINESS_RULE`, `decision = PENDING_OPEN_01`.

### 9.4. Ranh Giới Cứng OPEN-01 (OPEN-01 Hard Boundary)
- Tuyệt đối không tự ý áp dụng công thức "2/3 nhiệm vụ", không làm tròn số (rounding), không tự định nghĩa ngoại lệ.
- Dù toàn bộ các nhiệm vụ liên kết của ứng dụng đã hoàn thành 100% trong ngày, quyết định mở khóa nghiệp vụ vẫn bắt buộc giữ nguyên `BusinessUnlockDecision.PENDING_OPEN_01` và `EnforcementReason.LOCKED_PENDING_BUSINESS_RULE`.
- Ứng dụng chỉ được mở khóa khi có công thức chính thức từ Ký chủ (OPEN-01).

### 9.5. Main Thread Safety Cho Accessibility Service (Snapshot Cache)
- Accessibility Service chạy trên Main Thread của Android OS. Việc truy vấn SQLite/Room DB đồng bộ trên Main Thread bị Android cấm và gây jank/ANR.
- Giải pháp: `TaskAppEnforcementAdapter` duy trì một `Snapshot Cache` trong bộ nhớ RAM được nạp bất đồng bộ qua Coroutine Scope.
- `evaluateSync(packageName)` thực hiện tra cứu O(1) in-memory với độ trễ cực thấp (< 0.05ms), hoàn toàn an toàn cho Main Thread.

### 9.6. Quản Trị OPEN Items Trong Phase 20
- **OPEN-01, OPEN-02, OPEN-03, OPEN-04, OPEN-05, OPEN-06, OPEN-07 TIẾP TỤC GIỮ NGUYÊN TRẠNG THÁI OPEN.**

---

## 10. QUYẾT ĐỊNH SẢN PHẨM CHÍNH THỨC OPEN-01 (PHASE 23 — TASK-BASED UNLOCK)

> [!IMPORTANT]
> **Xác nhận Quyết định Sản phẩm:** OPEN-01 was explicitly decided by the product owner and implemented accordingly.

### 10.1. Công Thức Nghiệp Vụ & Số Học Nguyên (Integer Arithmetic Formula)
Với một Vault App có $N$ nhiệm vụ đang có hiệu lực (active, không bị lưu trữ `isArchived`, không bị xóa) và được liên kết trong chu kỳ nghiệp vụ hiện tại:

$$\text{requiredCompletedTasks} = \left\lceil \frac{2 \times N}{3} \right\rceil$$

Hiện thực hóa hoàn toàn bằng số học số nguyên (integer arithmetic), tuyệt đối không sử dụng số thực (floating point):
```kotlin
required = (2 * N + 2) / 3
```

### 10.2. Bảng Kiểm Chứng Chuẩn Hóa (Canonical Verification Table)
| Số nhiệm vụ hiệu lực ($N$) | Nhiệm vụ hoàn thành yêu cầu (`required`) | Ghi chú điều kiện mở khóa |
| :---: | :---: | :--- |
| **0** | **0** | **KHÔNG MỞ KHÓA** (bắt buộc $N > 0$) $\rightarrow$ `LOCK` |
| **1** | **1** | Cần hoàn thành 1/1 nhiệm vụ $\rightarrow$ `ALLOW` |
| **2** | **2** | Cần hoàn thành 2/2 nhiệm vụ $\rightarrow$ `ALLOW` |
| **3** | **2** | Cần hoàn thành tối thiểu 2/3 nhiệm vụ $\rightarrow$ `ALLOW` |
| **4** | **3** | Cần hoàn thành tối thiểu 3/4 nhiệm vụ $\rightarrow$ `ALLOW` |
| **5** | **4** | Cần hoàn thành tối thiểu 4/5 nhiệm vụ $\rightarrow$ `ALLOW` |
| **6** | **4** | Cần hoàn thành tối thiểu 4/6 nhiệm vụ $\rightarrow$ `ALLOW` |
| **7** | **5** | Cần hoàn thành tối thiểu 5/7 nhiệm vụ $\rightarrow$ `ALLOW` |
| **8** | **6** | Cần hoàn thành tối thiểu 6/8 nhiệm vụ $\rightarrow$ `ALLOW` |
| **9** | **6** | Cần hoàn thành tối thiểu 6/9 nhiệm vụ $\rightarrow$ `ALLOW` |
| **10** | **7** | Cần hoàn thành tối thiểu 7/10 nhiệm vụ $\rightarrow$ `ALLOW` |

### 10.3. Điều Kiện Mở Khóa (Unlock Condition)
- **Mở khóa nghiệp vụ (`BusinessUnlockDecision.UNLOCKED`):**
  $$\text{completedTasks} \ge \text{requiredCompletedTasks} \quad \text{VÀ} \quad N > 0$$
- **Nếu không thỏa mãn:**
  * Nếu $N = 0$: Trạng thái `NO_LINKED_TASKS` $\rightarrow$ `LOCK` (`LOCKED_BY_VAULT_NO_TASK`).
  * Nếu $N > 0$ nhưng $\text{completedTasks} < \text{required}$: Trạng thái `INSUFFICIENT_COMPLETION` $\rightarrow$ `LOCK` (`LOCKED_INSUFFICIENT_TASKS`).

### 10.4. Chu Kỳ Nghiệp Vụ 04:00 (Business Cycle Invariants)
1. **Thời điểm reset:** Chu kỳ ngày bắt đầu lúc `04:00:00` hàng ngày (`BusinessDayProvider`).
2. **Phạm vi tính toán:** Chỉ các lượt hoàn thành (`TaskCompletionEntity`) ghi nhận trong chu kỳ ngày hiện tại mới được tính vào `completedTasks`. Lượt hoàn thành ngày hôm trước không có hiệu lực cho ngày hôm nay.
3. **Nhiệm vụ bị lưu trữ / xóa:**
   - Khi task chuyển sang `isArchived = true` hoặc bị xóa khỏi database: ngay lập tức loại khỏi $N$ và không tính completion.
   - Nếu toàn bộ task bị lưu trữ hoặc xóa ($N$ hiệu lực trở về 0), app tự động chuyển về trạng thái `NO_LINKED_TASKS` $\rightarrow$ `LOCK`.
4. **Tính chất duy nhất:** Mỗi task hoàn thành chỉ tính tối đa 1 lần trong 1 chu kỳ ngày.
5. **Quan hệ M:N (Task $\leftrightarrow$ App):**
   - Một task liên kết nhiều app: hoàn thành task đó sẽ ghi nhận 1 lần hoàn thành cho tất cả các app liên kết.
   - Một app liên kết nhiều task: mỗi task được tính là một đơn vị độc lập để xác định $N$ và `completedTasks`.
6. **Ứng dụng xóa khỏi Bảo Khố:** Khi xóa app khỏi Bảo Khố rồi thêm lại, toàn bộ liên kết cũ bị xóa sạch, app trở về $N=0$ $\rightarrow$ `LOCK`.

### 10.5. Độ Ưu Tiên Tuyệt Đối Của Khóa Kỹ Thuật (Technical Precedence)
- Technical App Lock (`PolicyEngine`: Schedule và Daily Limit) luôn có độ ưu tiên tuyệt đối so với Business Unlock:
  $$\text{Technical Decision} == \text{LOCK} \implies \text{Final Action} = \text{LOCK}$$
- Chỉ khi Technical Lock không kích hoạt (`ALLOW`), hệ thống mới xem xét quyết định mở khóa nghiệp vụ từ `TaskUnlockPolicy`.

### 10.6. Trạng Thái Quản Trị OPEN Items Trong Phase 23
- **OPEN-01:** **CHÍNH THỨC ĐÓNG (CLOSED)** — Đã ký duyệt và hiện thực hóa đầy đủ.
- **OPEN-02 đến OPEN-07:** **TIẾP TỤC GIỮ NGUYÊN TRẠNG THÁI OPEN.** Tuyệt đối không tự ý quyết định hay đóng các mục còn lại.

---

## 11. ĐÍNH CHÍNH QUẢN TRỊ & ĐÓNG BĂNG PRODUCT BASELINE (PHASE 25)

> [!IMPORTANT]
> **Khôi phục Canonical Source of Truth:** Bảng mapping OPEN-01..07 tuân thủ tuyệt đối Mục 28 của `docs/CANONICAL_DESIGN_V2.md`. Mọi sự sai lệch về tên gọi hoặc suy diễn trong tài liệu báo cáo trước đây đều là sai lệch văn bản (documentation discrepancy), không phản ánh thay đổi runtime hay quyết định sản phẩm mới.

### 11.1. Bảng Mapping Chuẩn Hóa Canonical (Single Source of Truth)
| Mã ID | Tên Vấn Đề Chuẩn (Canonical Name) | Trạng Thái Quản Trị | Ghi Chú Ranh Giới |
| :---: | :--- | :---: | :--- |
| **OPEN-01** | Exact task unlock formula `ceil(2N/3)` | **CLOSED** | Đã quyết định chính thức (Phase 23) và kiểm chứng toàn diện (Phase 24). |
| **OPEN-02** | Point / Reward final formula | **OPEN** | Tuyệt đối không tự ý triển khai hệ thống điểm/thưởng. |
| **OPEN-03** | Tower detailed formula / Floor 4 exception | **OPEN** | Tuyệt đối không tự ý triển khai Tháp Thí Luyện. |
| **OPEN-04** | Technical App Lock product decision | **OPEN** | Hạ tầng kỹ thuật hiện có chỉ là Technical Foundation, chưa phải quyết định sản phẩm cuối cùng. |
| **OPEN-05** | Official DB schema & migration strategy | **OPEN** | Room DB hiện tại chỉ phục vụ POC/Checkpoint, schema chính thức và migration strategy vẫn OPEN. |
| **OPEN-06** | Memory / Cloud retention & sync policy | **OPEN** | Tuyệt đối không tự ý triển khai Cloud sync hay retention policy. |
| **OPEN-07** | UI state machine / animation / audio tokens | **OPEN** | UI Tokens hiện có chỉ là Foundation, audio/voice assets và state machine mở rộng vẫn OPEN. |

### 11.2. Đóng Băng Ranh Giới Sản Phẩm (Product Baseline Freeze)
1. **Product behavior đã chốt:**
   - Nhiệm Vụ Đường: Tạo, quản lý, hoàn thành task chuỗi tuần tự.
   - Bảo Khố: Thêm/gỡ app phong ấn (giao diện không icon ổ khóa).
   - Liên kết M:N: Task $\leftrightarrow$ App.
   - Luồng mở khóa nghiệp vụ: `required = (2 * N + 2) / 3`, $N=0 \rightarrow$ LOCK.
   - Chu kỳ ngày nghiệp vụ: Reset mốc `04:00:00` hàng ngày.
   - Độ ưu tiên: Technical App Lock luôn cấm tuyệt đối nếu kích hoạt.
2. **Phân định rạch ròi:** Nền tảng kỹ thuật (Accessibility, Room DB proposal, Snapshot Cache, Cultivation UI Foundation) không tự động biến thành Quyết định Sản phẩm (Product Decision).
3. **OPEN-02..07 bất biến:** Giữ nguyên trạng thái `OPEN` cho đến khi có quyết định bằng văn bản từ Ký chủ.



