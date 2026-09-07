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

