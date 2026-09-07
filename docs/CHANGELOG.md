# NHẬT KÝ THAY ĐỔI DỰ ÁN (PROJECT CHANGELOG)

Tất cả các thay đổi kiến trúc, quyết định thiết kế và mốc phát triển quan trọng của dự án **Hệ Thống Tự Kỷ Luật Bản Thân (`self-discipline-poc-01`)** được ghi nhận tuần tự tại đây.

---

## [Phase 17] - 2026-09-07: CORE DATA ARCHITECTURE & DAILY CYCLE 04:00 ALIGNMENT
### Bản chất giai đoạn:
- **Hiện thực hóa Checkpoint CP5 (Chu kỳ ngày 04:00) theo Canonical Design V2 (Mục 8).**
- **Xây dựng Nền tảng Dữ liệu Cốt lõi Đề xuất (Technical Data Foundation) cho CP3 (Core Task) và CP6 (Vault App ↔ Task).**
- **OPEN-05 VẪN Ở TRẠNG THÁI OPEN.**
- Giữ nguyên 100% DataStore cho runtime App Lock; không di trú mù; bảo toàn hồi quy.

### Các nội dung đã thực hiện:
- **Chu kỳ Ngày 04:00 (Daily Cycle Alignment - CP5):**
  - Tạo abstraction `BusinessDayProvider` và `BusinessDayProviderImpl` với mốc chuẩn `04:00:00`.
  - Tách bạch Wall Clock (chu kỳ, lịch trình, ngày nghiệp vụ) và Elapsed Realtime (đo lường thời lượng phiên).
  - Tích hợp `UsageTracker` xác định chu kỳ ngày theo `BusinessDayProvider`, phân bổ thời lượng sử dụng qua mốc 04:00; phiên chạy qua nửa đêm (23:30 -> 01:30) vẫn thuộc cùng 1 chu kỳ.
  - Hỗ trợ quy tắc Canonical: Task bắt đầu trước 04:00 thuộc chu kỳ cũ, kể cả khi hoàn thành sau 04:00.
  - Triển khai ma trận kiểm thử 12 kịch bản trong `BusinessDayProviderTest.kt` (PASS 100%).
- **Nền tảng Dữ liệu Cốt lõi Đề xuất (Room Data Architecture Proposal):**
  - Tạo tài liệu thiết kế chi tiết: [`docs/ROOM_SCHEMA_PROPOSAL.md`](file:///c:/Code/self-discipline-poc-01/docs/ROOM_SCHEMA_PROPOSAL.md) (Ghi rõ: `PROPOSAL — OPEN-05 REMAINS OPEN`).
  - Thiết kế 4 Entity Room đề xuất:
    - `AppEntity` (bảng `vault_apps` — Bảo Khố).
    - `TaskEntity` (bảng `mission_tasks` — Nhiệm Vụ Đường tối giản).
    - `TaskAppCrossRef` (bảng `task_app_cross_ref` — Many-to-Many giữa Task và App, Foreign Key `CASCADE`, composite PK).
    - `DailyTaskCompletionEntity` (bảng `daily_task_completions` — lưu trạng thái hoàn thành gắn với Business Date `yyyy-MM-dd` tính từ `BusinessDayProvider`, composite PK).
  - Thiết kế 4 DAO: `AppDao`, `TaskDao`, `TaskAppCrossRefDao`, `DailyTaskCompletionDao`.
  - Tạo Room Database `AppDatabase` và abstraction `CoreDataRepository` / `CoreDataRepositoryImpl`.
  - Viết bộ kiểm thử 10 kịch bản Room foundation trong `CoreDataRepositoryTest.kt` (PASS 100%).
- **Chiến lược Coexistence & Bảo vệ Hồi quy:**
  - Bảo toàn 100% Jetpack DataStore Preferences cho App Lock runtime (`TargetRepositoryImpl`).
  - Không thực hiện di trú mù dữ liệu sản xuất.
  - Không phá vỡ bất kỳ thành phần nào của Frozen Core (Accessibility, BlockingShieldOverlay, LockScreenActivity, Session Guard, Watchers).
- **Kết quả Kiểm thử & Đóng gói:**
  - Unit tests: **237/237 PASS** (100% thành công).
  - Build: `assembleDebug` BUILD SUCCESSFUL.

---

## [Phase 16] - 2026-09-04: CANONICAL DESIGN IMPORT & DESIGN GOVERNANCE ESTABLISHED
### Bản chất giai đoạn:
- **Phase 16 là giai đoạn KIỂM TOÁN VÀ QUẢN TRỊ THIẾT KẾ (AUDIT & GOVERNANCE PHASE).**
- **TUYỆT ĐỐI KHÔNG KHÓA THÊM BẤT KỲ PRODUCT RULE MỚI NÀO.**
- Không tự ý bịa đặt công thức hoặc chuyển các mục OPEN thành CLOSED.
- Không thay đổi mã nguồn sản xuất (`Production source changes = 0`).

### Các nội dung đã thực hiện:
- **Nhập khẩu Canonical Design V2:**
  - Nhập khẩu trung thực 100% toàn bộ nội dung từ tài liệu gốc `He_Thong_Tu_Ky_Luat_Ban_Than_CANONICAL_FULL_V2.docx` sang [`docs/CANONICAL_DESIGN_V2.md`](file:///c:/Code/self-discipline-poc-01/docs/CANONICAL_DESIGN_V2.md) (36,423 bytes, 399 dòng).
  - Xác lập `CANONICAL_DESIGN_V2.md` là Single Source of Truth tối cao và vĩnh viễn của toàn bộ sản phẩm.
- **Thiết lập Quản trị Thiết kế (Design Governance Hierarchy):**
  - Thiết lập phân tầng thẩm quyền: 1. Canonical Design -> 2. Approved Architecture -> 3. Implementation -> 4. POC/Experiment.
  - Ban hành 10 Nguyên tắc Quản trị Thiết kế bắt buộc trong [`docs/DESIGN_DECISIONS.md`](file:///c:/Code/self-discipline-poc-01/docs/DESIGN_DECISIONS.md).
  - Khẳng định rõ: Mã nguồn hiện tại không phải là đặc tả; POC không được tự ý tái định nghĩa sản phẩm; AI tuyệt đối không tự bịa đặt nghiệp vụ cho các mục OPEN.
- **Quản lý Vấn đề Mở (Open Items Tracking):**
  - Tạo [`docs/OPEN_ITEMS.md`](file:///c:/Code/self-discipline-poc-01/docs/OPEN_ITEMS.md) bảo toàn nguyên vẹn 7 mục chưa chốt: OPEN-01 (Công thức 2/3 nhiệm vụ), OPEN-02 (Công thức điểm thưởng), OPEN-03 (Tháp Thí Luyện & Tầng 4), OPEN-04 (Chi tiết App Lock sau POC), OPEN-05 (Lược đồ Room DB & Migration), OPEN-06 (Retention Cloud Memory/History), OPEN-07 (UI State Machine & Audio Tokens).
  - Đánh dấu trạng thái trong code hiện tại của các vấn đề OPEN là `CHƯA XÁC ĐỊNH` hoặc `CHƯA CÓ`, không tự biến thành quy tắc chính thức.
- **Kiểm toán Toàn diện Mã nguồn (Design Audit):**
  - Tạo [`docs/IMPLEMENTATION_STATUS.md`](file:///c:/Code/self-discipline-poc-01/docs/IMPLEMENTATION_STATUS.md) phân loại chuẩn: `DONE` (0), `PARTIAL` (1), `FOUNDATION ONLY` (3), `NOT IMPLEMENTED` (8), `OPEN` (6).
  - Tạo [`docs/DESIGN_AUDIT.md`](file:///c:/Code/self-discipline-poc-01/docs/DESIGN_AUDIT.md) với đầy đủ 15 phần theo đúng cấu trúc chỉ định.
  - Phân loại rõ ràng: 16 thành phần App Lock hiện có là **TECHNICAL FOUNDATION (NỀN TẢNG KỸ THUẬT)**, các phân hệ sản phẩm Tiên Hiệp cốt lõi hiện đạt 0%.
  - Phát hiện mâu thuẫn nghiệp vụ: `UsageTracker.kt:155` reset chu kỳ ngày lúc `00:00:00` thay vì `04:00:00` sáng theo Canonical Design Mục 8.
- **Bảo toàn kiểm thử hồi quy:**
  - 215/215 unit tests PASS tuyệt đối.

---

## [Phase 15] - 2026-09-04: PRODUCTION SIGNING & CI/CD PIPELINE
### Đã thực hiện:
- Thiết lập quy trình ký Release AAB/APK với Android V2/V3 signature scheme.
- Cấu hình CI/CD an toàn, không để lộ private signing key.
- Xác thực 215/215 unit tests PASS, real-device validation PASS.

---

## [Phase 14] - 2026-09-04: RELEASE PACKAGING & DISTRIBUTION READINESS
### Đã thực hiện:
- Đóng gói bản phát hành thử nghiệm Release APK & AAB.
- Thiết lập quy trình nâng cấp và rollback an toàn.

---

## [Phase 13] - 2026-09-04: RELEASE CANDIDATE AUDIT & SHIP GATE
### Đã thực hiện:
- Đóng băng Frozen Core kiến trúc thực thi.
- Kiểm toán toàn diện chất lượng Release Candidate, đạt kết quả SHIP GATE.

---

## [Phase 11-A -> 12] - 2026-09-03: PERSISTENCE, RECOVERY & LONG-RUN SOAK
### Đã thực hiện:
- Kiểm chứng độ bền vững trước process death, service reconnect, app cold start, screen OFF/ON, clock jump.
- Tích hợp in-memory `DiagnosticLogger` (200 events ring buffer).
- Nâng tổng số unit test từ 189 lên 215 tests PASS.

---

## [Phase 05 -> 10-C] - 2026-09-02: POC APP DETECTION & CORE ENFORCEMENT ENGINE
### Đã thực hiện:
- Xây dựng `AppDetectorAccessibilityService` trên Android 15 / iQOO Neo 10.
- Phát triển `BlockingShieldOverlay` chống rò rỉ frame giao diện.
- Phát triển `LockScreenActivity`, `PolicyEngine`, `ScheduleWatcher`, `UsageLimitWatcher`.
- Hoàn thành kiểm chứng Checkpoint CP1 (POC App Detection).
