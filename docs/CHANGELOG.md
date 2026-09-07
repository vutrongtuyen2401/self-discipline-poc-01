# NHẬT KÝ THAY ĐỔI DỰ ÁN (PROJECT CHANGELOG)

Tất cả các thay đổi kiến trúc, quyết định thiết kế và mốc phát triển quan trọng của dự án **Hệ Thống Tự Kỷ Luật Bản Thân (`self-discipline-poc-01`)** được ghi nhận tuần tự tại đây.

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
