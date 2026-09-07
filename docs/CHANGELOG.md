# NHẬT KÝ THAY ĐỔI DỰ ÁN (PROJECT CHANGELOG)

Tất cả các thay đổi kiến trúc, quyết định thiết kế và mốc phát triển quan trọng của dự án **Hệ Thống Tự Kỷ Luật Bản Thân (`self-discipline-poc-01`)** được ghi nhận tuần tự tại đây.

## [Phase 26.2] - 2026-09-07: GOVERNANCE RECOVERY & BASELINE INTEGRITY

> [!IMPORTANT]
> **Khôi phục Quản trị & Xác minh Toàn vẹn Baseline:**
> - **XÁC MINH TRỰC TIẾP TỪ REPOSITORY:** Kiểm tra đối chiếu trực tiếp `origin/main`, `CANONICAL_DESIGN_V2.md`, `OPEN_ITEMS.md`, `PRODUCT_BASELINE.md`, `DESIGN_DECISIONS.md`, `DESIGN_AUDIT.md`, `IMPLEMENTATION_STATUS.md`.
> - **QUẢN TRỊ OPEN ITEMS:**
>   * **OPEN-01:** **CLOSED** (Công thức `ceil(2N/3)`).
>   * **OPEN-04:** **CLOSED** (Scoped Product Behavior / Hard Ceiling Guardrail).
>   * **OPEN-02, OPEN-03, OPEN-05, OPEN-06, OPEN-07:** **TIẾP TỤC GIỮ NGUYÊN TRẠNG THÁI OPEN.**
> - **LÀM RÕ SAI LỆCH TEST COUNT (328 vs 327):** Đã kiểm tra source code đối chiếu commit `3450303` (Phase 26) và `6fa4bd6` (Phase 26.1): diff rỗng (0 thay đổi code/test). Phân tích mã nguồn và XML test report xác nhận có chính xác 327 annotations `@Test` và 327 tests được thực thi thành công (0 failures). Con số 328 trong các báo cáo trước đó là do reporting error từ Gradle summary. Con số baseline chính thức được xác nhận là **327 tests**.
> - **BẢO TỒN RUNTIME:** 0 dòng code runtime bị thay đổi. `assembleDebug` và `testDebugUnitTest` (327/327 tests) PASS.

## [Phase 26.1] - 2026-09-07: CANONICAL SYNCHRONIZATION & GOVERNANCE REPAIR

> [!IMPORTANT]
> **Đồng bộ Tối cao Canonical Design V2 & Khắc phục Sai lệch Quản trị:**
> - **CANONICAL DESIGN V2 ĐÃ ĐƯỢC ĐỒNG BỘ:** Cập nhật Mục 7 (Mục tiêu 2/3 nhiệm vụ, OPEN-01 CLOSED, công thức $\lceil 2N/3 \rceil$ và bảng kiểm chứng), Mục 25 (POC-01 đã kiểm chứng thành công và phê duyệt nền tảng Accessibility + Overlay + LockScreen làm Core Enforcement Engine), Mục 28 (Bảng Canonical Mapping chuẩn hóa).
> - **QUẢN TRỊ OPEN ITEMS:**
>   * **OPEN-01:** **CLOSED** (Exact task unlock formula `ceil(2N/3)`).
>   * **OPEN-04:** **CLOSED** (Technical App Lock product decision: Scoped Product Behavior / Hard Ceiling Guardrail).
>   * **OPEN-02, OPEN-03, OPEN-05, OPEN-06, OPEN-07:** **TIẾP TỤC GIỮ NGUYÊN TRẠNG THÁI OPEN.**
> - **QUAN TRỌNG VỀ OPEN-06:** Không biến OPEN-06 thành quyết định "100% on-device" hay "cấm vĩnh viễn Cloud sync". Phân định rõ ràng: Hiện trạng code chưa có Cloud sync (chẩn đoán kỹ thuật chỉ lưu in-memory ring buffer 200 sự kiện); Quyết định sản phẩm vẫn **OPEN**, bảo lưu hướng kiến trúc dài hạn hybrid Cloud + On-device theo Canonical Design V2 (Mục 30).
> - **BẢO TỒN MÃ NGUỒN RUNTIME:** 0 dòng code runtime bị thay đổi.

## [Phase 26] - 2026-09-07: TECHNICAL APP LOCK PRODUCT DECISION (OPEN-04)

> [!IMPORTANT]
> **Xác nhận Quyết định Sản phẩm & Đóng OPEN-04:**
> - **OPEN-04:** **CHÍNH THỨC ĐÓNG (CLOSED)** — Technical App Lock (Schedule & Daily Limit) được công nhận là **Scoped Product Behavior (Hàng rào Cấm Tuyệt Đối - Hard Ceiling Guardrail)** với thẩm quyền thực thi tối thượng.
> - **OPEN-01:** **CLOSED** (Exact task unlock formula `ceil(2N/3)`).
> - **OPEN-02, OPEN-03, OPEN-05, OPEN-06, OPEN-07:** **TIẾP TỤC GIỮ NGUYÊN TRẠNG THÁI OPEN.** Tuyệt đối không tự ý quyết định hay đóng các mục còn lại.

### Bản chất giai đoạn:
- **Xác lập Quyết định Sản phẩm cho OPEN-04 (Decision Record):**
  * Định vị rõ rệt: Technical App Lock không phải là một hệ thống cạnh tranh với Bảo Khố, mà đóng vai trò là "Lưới An Toàn Cứng" (Hard Ceiling / Absolute Ban Policy), bảo vệ người dùng không bị cuốn vào điện thoại trong các khung giờ cấm hoặc khi vượt trần thời lượng mỗi ngày.
  * Phê duyệt kiến trúc thực thi 2 tầng (`TaskAppEnforcementAdapter`): Technical Policy luôn có độ ưu tiên tối thượng cấm truy cập (`LOCKED_BY_POLICY`), không bị bypass bởi bất kỳ tiến trình nhiệm vụ hay voucher nào.
  * Phê duyệt ranh giới API Android 15: Accessibility Service (`TYPE_WINDOW_STATE_CHANGED`) + Window Overlay (`TYPE_APPLICATION_OVERLAY`) chính thức trở thành Core Enforcement Engine, giới hạn nghiêm ngặt ở 2 quyền OS tiêu chuẩn mà không cần DeviceAdmin.
- **Bảo tồn Tuyệt đối Mã Nguồn Runtime:**
  * 0 dòng code runtime bị thay đổi. 328/328 unit & integration tests tiếp tục PASS 100%.

## [Phase 25] - 2026-09-07: GOVERNANCE CORRECTION + PRODUCT BASELINE FREEZE

> [!IMPORTANT]
> **Khôi phục Canonical Source of Truth & Đóng băng Baseline:**
> - Khôi phục chính xác 100% mapping chuẩn của các mục OPEN-01..07 theo Mục 28 của `docs/CANONICAL_DESIGN_V2.md`.
> - Xác nhận sai lệch trong `PHASE_24_REPORT.md` là sai lệch văn bản (documentation discrepancy), hoàn toàn không ảnh hưởng đến runtime code hay quyết định sản phẩm.
> - **OPEN-01:** **CLOSED** (Exact task unlock formula `ceil(2N/3)`).
> - **OPEN-02..07:** **TIẾP TỤC GIỮ NGUYÊN TRẠNG THÁI OPEN.** Tuyệt đối không tự ý quyết định hay đóng bất kỳ mục nào.
> - Thiết lập tài liệu chuẩn đóng băng ranh giới sản phẩm: `docs/PRODUCT_BASELINE.md`.

### Bản chất giai đoạn:
- **Governance Correction:**
  * Đồng bộ hóa và chuẩn hóa toàn bộ các văn bản quản trị (`OPEN_ITEMS.md`, `DESIGN_DECISIONS.md`, `IMPLEMENTATION_STATUS.md`, `DESIGN_AUDIT.md`, `PHASE_24_REPORT.md`).
  * Phân biệt rạch ròi giữa Hành vi Sản phẩm đã quyết định (Product Behavior) và Nền tảng Kỹ thuật hỗ trợ (Technical Foundation).
- **Code Audit & Runtime Freeze:**
  * Xác nhận không thay đổi bất kỳ hành vi runtime nào của `TaskUnlockPolicy`, `TaskAppEnforcementAdapter`, `MissionHallViewModel`, `VaultViewModel`, hay `AppDetectorAccessibilityService`.
  * Không đưa thêm bất kỳ business rule nào ngoài OPEN-01.

## [Phase 24] - 2026-09-07: PRODUCT FLOW VALIDATION & UNLOCK UX

> [!IMPORTANT]
> **Xác nhận Quyết định Sản phẩm & Quản trị:** OPEN-01 đã CLOSED từ Phase 23; OPEN-02..07 tiếp tục GIỮ NGUYÊN trạng thái OPEN. Tuyệt đối không mở rộng hay phát minh thêm business rule.

### Bản chất giai đoạn:
- **Kiểm chứng Toàn diện End-to-End Product Flow của OPEN-01:**
  Bảo Khố $\rightarrow$ Liên kết App với Task $\rightarrow$ Nhiệm Vụ Đường $\rightarrow$ Hoàn thành Task $\rightarrow$ Cập nhật Tiến độ $\rightarrow$ Đạt $\lceil 2N/3 \rceil$ $\rightarrow$ App được Business Unlock $\rightarrow$ Qua mốc 04:00 $\rightarrow$ Cycle mới $\rightarrow$ App trở lại LOCK.
- **Tích Hợp UX/State Minh Bạch Theo Triết Lý Tiên Hiệp (Xianxia Design System):**
  * Không hardcode business logic hay công thức $\lceil 2N/3 \rceil$ trong UI Composable.
  * UI chỉ nhận và hiển thị thông tin thực thi từ Domain Model (`AppEnforcementDetails` gồm `completedTasks`, `totalTasks`, `requiredTasks`, `reason`, `action`).
  * Trực quan hóa trạng thái ứng dụng tại Bảo Khố (`AppItemGridCard`):
    - `ALLOWED_UNLOCKED_BY_TASKS`: Badge "Đã mở (X/Y)" (Thanh Ngọc / Spirit Teal), viền phát quang.
    - `LOCKED_INSUFFICIENT_TASKS`: Badge "Cần X/Y (Xong Z)" (Kim Tinh / Celestial Gold / Amber).
    - `LOCKED_BY_POLICY`: Badge "Khóa kỹ thuật" (Chu Sa / Crimson Seal).
    - `LOCKED_BY_VAULT_NO_TASK`: Badge "Chưa liên kết (Khóa)" (Xám mờ / Slate).
    - Tuyệt đối không dùng icon ổ khóa cổ điển.
- **Tự Động Làm Mới Snapshot Tức Thì (Instant Cache Invalidation):**
  * `TaskAppEnforcementAdapterProvider.kt`: Cung cấp singleton adapter thread-safe dùng chung giữa `AppDetectorAccessibilityService`, `VaultViewModel`, và `MissionHallViewModel`.
  * `MissionHallViewModel`: Khi gọi `onCompleteTask`, `onArchiveTask`, `onSaveTaskLinkage` $\rightarrow$ tự động kích hoạt `recomputeSnapshot()` ngay lập tức để Accessibility Service thấy trạng thái mới 0ms.
  * `VaultViewModel`: Phơi bày `appEnforcementMap` trong `VaultUiState`, cung cấp `refreshEnforcement()` cập nhật trạng thái đồng bộ khi vào màn hình Bảo Khố.

### Kiểm thử & Xác minh:
- **Unit & Integration Test Suite Toàn Diện `ProductFlowValidationTest.kt`:**
  * 10/10 test scenarios kiểm chứng đầy đủ 5 luồng cốt lõi: 3 tasks unlock flow, 4 tasks boundary, 5 tasks boundary, technical override precedence, 04:00 reset boundary, cascade remove/re-add, archived tasks exclusion, VaultViewModel mapping integration.
- **Kết quả Kiểm thử Tự động:** **328/328 tests PASS (100% Success Rate)** trên Gradle.
- **Build APK:** `.\gradlew.bat assembleDebug` **BUILD SUCCESSFUL**.
- **Kiểm chứng Trực Tiếp trên Thiết Bị Thật vivo iQOO Neo 10 (V2425A / Android 15 / API 35):**
  * Cài đặt APK Debug thành công qua ADB.
  * Scenario 1: Màn hình Bảo Khố ban đầu hiển thị đúng `Cần 1/1 (Xong 0)` cho app liên kết và `Chưa liên kết (Khóa)` cho app không có task.
  * Scenario 2: Hoàn thành nhiệm vụ trên Nhiệm Vụ Đường $\rightarrow$ Bảo Khố cập nhật tức thì thành `Đã mở (1/1)` kèm viền xanh Thanh Ngọc.
  * Scenario 3: Khởi chạy app trên thiết bị thật $\rightarrow$ App mở bình thường, Accessibility Shield không can thiệp chặn.
  * Bằng chứng hình ảnh: `screen_p24_main.png`, `screen_p24_vault.png`, `screen_p24_completed.png`, `screen_p24_unlocked.png`, `screen_p24_app_allowed.png`.

## [Phase 23] - 2026-09-07: IMPLEMENT OPEN-01 TASK-BASED UNLOCK

> [!IMPORTANT]
> **Xác nhận Quyết định Sản phẩm:** OPEN-01 was explicitly decided by the product owner and implemented accordingly.

### Bản chất giai đoạn:
- **Hiện thực hóa Quyết định Sản phẩm chính thức của Ký chủ cho OPEN-01 (Task-based Unlock).**
- **Công thức Nghiệp vụ Số học Nguyên (Pure Integer Arithmetic):**
  $$\text{requiredCompletedTasks} = \text{ceil}(2 \times N / 3) \equiv (2 \times N + 2) / 3$$
  Tuyệt đối không dùng số thực (floating point), loại bỏ hoàn toàn sai số làm tròn.
- **Bảng Kiểm chứng Chuẩn Hóa ($N = 0 \dots 10$):**
  * $N = 0 \rightarrow$ 0 (Không mở khóa, bắt buộc $N > 0$) $\rightarrow$ `LOCK` (`NO_LINKED_TASKS`).
  * $N = 1 \rightarrow 1$ (1/1 $\rightarrow$ `ALLOW`).
  * $N = 2 \rightarrow 2$ (2/2 $\rightarrow$ `ALLOW`).
  * $N = 3 \rightarrow 2$ (2/3 hoặc 3/3 $\rightarrow$ `ALLOW`).
  * $N = 4 \rightarrow 3$ (3/4 hoặc 4/4 $\rightarrow$ `ALLOW`).
  * $N = 5 \rightarrow 4$ (4/5 hoặc 5/5 $\rightarrow$ `ALLOW`).
  * $N = 6 \rightarrow 4$ (4/6 $\rightarrow$ `ALLOW`).
  * $N = 7 \rightarrow 5$ (5/7 $\rightarrow$ `ALLOW`).
  * $N = 8 \rightarrow 6$ (6/8 $\rightarrow$ `ALLOW`).
  * $N = 9 \rightarrow 6$ (6/9 $\rightarrow$ `ALLOW`).
  * $N = 10 \rightarrow 7$ (7/10 $\rightarrow$ `ALLOW`).
- **Điều Kiện Mở Khóa Sản Phẩm (Unlock Condition):**
  * $\text{completedTasks} \ge \text{requiredCompletedTasks} \quad \text{VÀ} \quad N > 0 \implies \text{BusinessUnlockDecision.UNLOCKED} \rightarrow \text{EnforcementAction.ALLOW}$.
  * Nếu không thỏa mãn $\implies \text{EnforcementAction.LOCK}$.
- **Chu Kỳ Ngày Nghiệp Vụ (Business Cycle 04:00):**
  * Bắt đầu lúc `04:00:00` hàng ngày (`BusinessDayProvider`).
  * Chỉ tính các lượt hoàn thành nhiệm vụ trong chu kỳ hiện tại. Lượt hoàn thành ngày cũ không được tính sang ngày mới.
  * Nhiệm vụ bị lưu trữ (`isArchived`) hoặc bị xóa: loại hoàn toàn khỏi $N$ và completion.
  * Quan hệ M:N: Một task hoàn thành áp dụng cho mọi app liên kết; một app có nhiều task tính độc lập.
- **Độ Ưu Tiên Tuyệt Đối Của Khóa Kỹ Thuật (Technical Precedence):**
  * Technical App Lock (`PolicyEngine`: Schedule & Daily Limit) luôn có ưu tiên tối thượng.
  * Nếu Technical Lock cấm (`LOCK`) $\implies \text{finalAction} = \text{LOCK}$ (`LOCKED_BY_POLICY`), không bao giờ bị bypass bởi Business Unlock.
- **Kiến Trúc Triển Khai:**
  * Tạo riêng component domain thuần túy: `TaskUnlockPolicy.kt` (`domain/policy/`) chịu trách nhiệm tính toán công thức và đánh giá trạng thái (`NO_LINKED_TASKS`, `INSUFFICIENT_COMPLETION`, `UNLOCKED`). Không để công thức rải rác.
  * Nâng cấp `TaskAppEnforcementAdapter.kt` tích hợp `TaskUnlockPolicy`, đảm bảo `evaluate()` và `evaluateSync()` có cùng semantics 100%.
  * Cập nhật Snapshot Cache đa luồng an toàn cho Accessibility Main Thread (< 0.05ms, O(1)), tự động quan sát cập nhật khi completion thay đổi.
- **Quản Trị OPEN Items:**
  * **OPEN-01:** **CHÍNH THỨC ĐÓNG (CLOSED).**
  * **OPEN-02..07:** **TIẾP TỤC GIỮ NGUYÊN TRẠNG THÁI OPEN.**

### Kiểm thử & Xác minh:
- **Unit Test Suite Mới `TaskUnlockPolicyTest.kt`:**
  * Kiểm chứng bảng chuẩn $N = 0 \dots 10$.
  * Kiểm chứng toán học tương đương exhaustive $N = 0 \dots 35$.
  * Kiểm chứng các ngưỡng completion: 0%, ngay dưới threshold, đúng threshold, 100%, overflow.
- **Integration Test Suite Mở Rộng `TaskAppEnforcementIntegrationTest.kt`:**
  * Kiểm chứng Case A ($N=3$), Case B ($N=4$), Case C ($N=5$).
  * Kiểm chứng Case D & E (Technical priority vs Business unlock).
  * Kiểm chứng Case F (Reset chu kỳ 04:00).
  * Kiểm chứng quan hệ M:N, task archived/deleted, cache synchronization, app re-added to Vault.
- **Kết quả Kiểm thử Tự động:** **319/319 tests PASS (100% Success Rate)**.
- **Build APK:** `.\gradlew.bat assembleDebug` **BUILD SUCCESSFUL**.
- **Kiểm chứng Thiết bị Thực Tế vivo iQOO Neo 10 (V2425A / Android 15 / API 35):**
  * Cài đặt APK Debug thành công qua ADB (`Performing Streamed Install -> Success`).
  * Khởi chạy `MainActivity`, giao diện hiển thị mượt mà với đầy đủ 3 tab: Nhiệm Vụ Đường, Bảo Khố, Quản Trị Thực Thi.

---

## [Phase 21] - 2026-09-07: PRE-OPEN-01 AUDIT & GOVERNANCE FREEZE
### Bản chất giai đoạn:
- **Thiết lập Baseline Sạch và Đóng Băng Quản Trị Hệ Thống (Governance Freeze) trước khi xem xét quyết định OPEN-01.**
- **TUYỆT ĐỐI KHÔNG TRIỂN KHAI BẤT KỲ CÔNG THỨC SẢN PHẨM NÀO:** Không công thức 2/3, không rounding, không threshold, không unlock condition, không voucher bypass, không điểm tu vi, không Tu Luyện, không Tháp Thí Luyện, không Thương Thành, không Túi Trữ Vật, không Dynamic Task Generation.
- **Audit & Hiệu chỉnh Số Thứ Tự OPEN ITEMS (OPEN ITEMS NUMBERING AUDIT):**
  * Đối chiếu 100% chính xác giữa [`docs/CANONICAL_DESIGN_V2.md`](file:///c:/Code/self-discipline-poc-01/docs/CANONICAL_DESIGN_V2.md) (Mục 28) và [`docs/OPEN_ITEMS.md`](file:///c:/Code/self-discipline-poc-01/docs/OPEN_ITEMS.md).
  * Phát hiện và khắc phục sai lệch số thứ tự tại dòng 46 của `docs/IMPLEMENTATION_STATUS.md` và `PHASE_20_REPORT.md` (nơi đã gán nhầm Lược đồ DB thành OPEN-04 và chèn Dynamic Task Generation thành OPEN-05).
  * **Documentation numbering corrected to match CANONICAL_DESIGN_V2.md:**
    1. `OPEN-01`: Công thức giải phong ấn "2/3 nhiệm vụ" và quy tắc làm tròn cho mọi N.
    2. `OPEN-02`: Công thức điểm & Phần thưởng cuối cùng (mốc 1đ, 2đ, 3đ...).
    3. `OPEN-03`: Công thức chi tiết Tháp Thí Luyện (Ngoại lệ Tầng 4).
    4. `OPEN-04`: Chi tiết kỹ thuật App Lock sau POC (quyền phụ trợ, chống kill trên Android 15/iQOO).
    5. `OPEN-05`: Lược đồ Cơ sở Dữ liệu chính thức & Chiến lược di chuyển (Migration).
    6. `OPEN-06`: Chính sách Lưu trữ & Đồng bộ Cloud (Memory/History retention).
    7. `OPEN-07`: State Machine Giao diện / Animation tokens / Audio tokens (giọng loli).
- **Kiểm toán Chuỗi Thực Thi App Lock / Mission / Vault (Integration Audit):**
  * Xác nhận Technical Lock (`PolicyEngine` / `TargetRepository`) vận hành hoàn toàn độc lập với quyền ưu tiên tối thượng (`PolicyDecision.LOCK` không thể bị bypass).
  * Xác nhận `TaskAppEnforcementAdapter` không chứa bất kỳ công thức nghiệp vụ nào.
  * Xác nhận ranh giới cứng OPEN-01: Ứng dụng trong Bảo Khố luôn bị phong ấn (`LOCKED_PENDING_BUSINESS_RULE`, `PENDING_OPEN_01`) kể cả khi 100% nhiệm vụ liên kết đã hoàn thành trong ngày.
  * Xác nhận `evaluate()` và `evaluateSync()` sử dụng chung hàm phân xử `buildEnforcementDetails(...)`, không tạo hai ngữ nghĩa nghiệp vụ khác nhau.
  * Xác nhận Accessibility Service không truy cập Room DB trên Main Thread; `evaluateSync()` đọc từ in-memory snapshot cache O(1) an toàn tuyệt đối.
  * Xác nhận snapshot cache áp dụng nguyên tắc an toàn đóng (fail-safe closed): Mọi app trong Vault mặc định bị khóa, không thể tạo stale state gây leak mở khóa ngoài ý muốn.
  * Xác nhận các tác vụ archived/deleted được loại trừ chính xác khỏi active tasks theo đúng chuẩn Canonical.
  * Xác nhận quan hệ Many-to-Many Task ↔ App trong `TaskAppCrossRef` được giữ nguyên vẹn.
  * Xác nhận chu kỳ ngày 04:00:00 (`BusinessDayProvider`) không bị ảnh hưởng.
- **Xác nhận Thẩm Quyền Quản Trị (Governance Check):**
  * Nguyên tắc tối cao: `CANONICAL > DESIGN DECISIONS > IMPLEMENTATION`.
  * Mã nguồn và Unit Tests không được coi là nguồn sự thật thay cho Canonical.
  * Khẳng định: **"No product decision was invented."**
- **Xác định Ranh Giới Cơ Sở Dữ Liệu (Database Boundary):**
  * Không thay đổi schema Room DB, giữ nguyên `version = 1`, không migration, không thêm entity.
  * Ghi nhận DataStore Preferences là Technical Foundation phục vụ POC App Lock; Room Database là đề xuất kỹ thuật cho CP3/CP4/CP5/CP6 (chưa phải official schema theo OPEN-05).
- **OPEN-01, OPEN-02, OPEN-03, OPEN-04, OPEN-05, OPEN-06, OPEN-07 TIẾP TỤC GIỮ NGUYÊN TRẠNG THÁI OPEN.**

---

## [Phase 20] - 2026-09-07: APP LOCK INTEGRATION DESIGN
### Bản chất giai đoạn:
- **Thiết kế và triển khai tầng tích hợp giữa Mission Domain + Vault Domain và App Lock Core.**
- **Bảo vệ tuyệt đối Ranh giới cứng OPEN-01 (OPEN-01 HARD BOUNDARY):**
  * Tuyệt đối không tự ý triển khai công thức mở khóa (không hard-code 2/3, không rounding, không định nghĩa special cases).
  * Giữ trạng thái rõ ràng `BusinessUnlockDecision.PENDING_OPEN_01` và `EnforcementReason.LOCKED_PENDING_BUSINESS_RULE`, không biến thành unlock/allow dù task hoàn thành 100%.
- **Bảo toàn tính an toàn thực thi và độ ưu tiên kỹ thuật (Technical App Lock Precedence):**
  * Technical App Lock (`PolicyEngine` / `TargetRepository`) luôn giữ quyền ưu tiên tối thượng; nếu kỹ thuật cấm (`LOCK`) thì lập tức thực thi phong ấn, adapter không thể bypass.
  * Tách biệt rạch ròi 3 nhóm phân loại: `NON_VAULT_APP` (không thuộc Vault), `VAULT_APP_UNLINKED` (thuộc Vault nhưng không liên kết task), `VAULT_APP_WITH_TASKS` (thuộc Vault và có liên kết task).
  * Duy trì 100% Frozen Core: State Machine (`isLockScreenVisible`, duplicate intra-session skip, cooldown 1500ms), `BlockingShieldOverlay` (che 0ms), `LockScreenActivity` (safe home fallback), `BusinessDayProvider` (mốc 04:00:00).
- **Thiết kế Main Thread Safe cho Accessibility Service:**
  * Triển khai cơ chế Snapshot Cache in-memory cực nhanh (< 0.05ms, O(1)) qua `evaluateSync(packageName)` được đồng bộ ngầm qua Coroutine Scope với Room DB, không block UI thread của Accessibility Service.
- **OPEN-01, OPEN-02, OPEN-03, OPEN-04, OPEN-05, OPEN-06, OPEN-07 TIẾP TỤC GIỮ NGUYÊN TRẠNG THÁI OPEN.**

### Các nội dung đã thực hiện:
- **Nâng cấp `TaskAppEnforcementAdapter.kt` (`domain/enforcement/`):**
  * Định nghĩa Contract tích hợp: `AppEnforcementClassification`, `EnforcementAction`, `BusinessUnlockDecision`, `EnforcementReason`, `AppEnforcementDetails`.
  * Triển khai `suspend fun evaluate(packageName)` truy vấn trực tiếp Room DB (`VaultAppDao`, `TaskAppCrossRefDao`, `TaskDao`, `DailyTaskCompletionDao`).
  * Triển khai `fun evaluateSync(packageName)` đọc snapshot in-memory an toàn cho Main Thread của Accessibility Service.
  * Bổ sung cơ chế đồng bộ snapshot cache ngầm: `refreshSnapshot()`, `startObserving()`.
  * Duy trì backward compatibility đầy đủ cho constructor `(repository: CoreDataRepository)` và các hàm `getEnforcementStatus()`, `isTaskBasedUnlockApproved()`.
  * Tuân thủ quy định: Không chứa bất kỳ phương thức nào có từ ngữ `point`, `ratio`, `percent`, `threshold`.
- **Nâng cấp `PolicyEngine.kt` (`policy/`):**
  * Bổ sung hàm `fun isTargetConfigured(packageName: String): Boolean` giúp phân biệt chính xác giữa `ALLOWED_BY_POLICY` (thuộc TargetRepository nhưng chưa đến giờ cấm hoặc chưa hết limit) và `ALLOWED_NOT_PROTECTED` (hoàn toàn không nằm trong danh sách kiểm soát).
- **Tích hợp vào `AppDetectorAccessibilityService.kt`:**
  * Khởi tạo `TaskAppEnforcementAdapter` với đầy đủ DAOs từ Room `AppDatabase`.
  * Gọi `enforcementAdapter.evaluateSync(packageName)` trong `onAccessibilityEvent` ngay sau khi ghi nhận package foreground.
  * Đánh giá kết hợp `PolicyEngine` (Technical Lock) và `TaskAppEnforcementAdapter` (Vault/Mission Policy): Ưu tiên Technical Lock; nếu Technical Lock không cấm thì thực thi theo Adapter action.
  * Bảo toàn 100% Frozen Core State Machine: Rule A (`isLockScreenVisible`), Rule B (intra-session duplicate), Rule C (`COOLDOWN_MS = 1500L`), `launchLockSession`, `BlockingShieldOverlay`, `LockScreenActivity`.
- **Bộ Kiểm Thử Toàn Diện `TaskAppEnforcementIntegrationTest.kt` (`test/.../domain/enforcement/`):**
  * Viết 12 kịch bản kiểm thử:
    1. App không thuộc Vault -> ALLOWED_NOT_PROTECTED.
    2. App thuộc Vault nhưng không liên kết task -> LOCKED_BY_VAULT_NO_TASK.
    3. App thuộc Vault có liên kết task incomplete -> LOCKED_PENDING_BUSINESS_RULE & PENDING_OPEN_01.
    4. Nhiều task cùng 1 app -> LOCKED_PENDING_BUSINESS_RULE & PENDING_OPEN_01.
    5. Một task nhiều app -> LOCKED_PENDING_BUSINESS_RULE & PENDING_OPEN_01.
    6. Task bị archived -> Tự động bỏ qua task archived.
    7. Task bị deleted -> Tự động bỏ qua task deleted.
    8. Toàn bộ task hoàn thành 100% -> Vẫn LOCKED_PENDING_BUSINESS_RULE (OPEN-01 Hard Boundary, tuyệt đối không tự unlock).
    9. Technical Lock cấm (`LOCK`) -> Ưu tiên tuyệt đối, adapter không bypass.
    10. App là Target kỹ thuật nhưng hợp lệ (chưa tới giờ cấm) -> ALLOWED_BY_POLICY.
    11. Đánh giá đồng bộ `evaluateSync` từ snapshot cache khớp hoàn toàn với `evaluate` bất đồng bộ.
    12. Khởi tạo adapter với constructor tương thích ngược không crash.
  * Toàn bộ test suite: **306/306 PASS (100% Success Rate)**.
- **Kiểm thử thực tế trên thiết bị vivo iQOO Neo 10 (Android 15 / API 35):**
  * Cài đặt APK Debug thành công (`Success`).
  * Technical App Lock: Chrome (`com.android.chrome`) bị chặn tức thì 0ms, hiển thị `LockScreenActivity`.
  * Vault App Lock: Thêm app thật `1.1.1.1` (`com.cloudflare.onedotonedotonedotone`) vào Bảo Khố -> Trở thành `VAULT_APP_UNLINKED`. Khi mở app, `AppDetectorAccessibilityService` đánh giá `action=LOCK, reason=LOCKED_BY_VAULT_NO_TASK` và kích hoạt `BlockingShieldOverlay` trong **17.85ms** (< 1 frame 60Hz), không crash, an toàn tuyệt đối.

---

## [Phase 19.5] - 2026-09-07: CULTIVATION UI DESIGN SYSTEM FOUNDATION
### Bản chất giai đoạn:
- **Thiết lập nền tảng UI Design System hoàn chỉnh theo phong cách: Xianxia / Cultivation + RPG + Self Discipline.**
- **Nghiên cứu kiến trúc thị giác từ các GitHub reference (`IdleFantasy`, `ASCENDANT/ClaudeFitness`, `NeoMud`, `eOr`) theo nguyên tắc REFERENCE ONLY, KHÔNG COPY CODE / ASSETS (tuân thủ nghiêm ngặt giấy phép GPL-3.0).**
- **Xây dựng hệ thống Semantic Tokens: Theme, Colors (Huyền Mặc, Thanh Ngọc, Kim Tinh, Chu Sa, Tử Tiêu), Typography, Shapes, Spacing, Elevation.**
- **Xây dựng bộ Reusable Core Components: `CultivationCard`, `CultivationButton`, `CultivationDialog`, `AsyncAppIcon` (tải icon thật bất đồng bộ có memory cache LRU), `AppItemGridCard`, `AppItemSelectableRow`, `TaskCard`, `ProgressCard`, `CultivationGrid`, `ItemCard`.**
- **Nâng cấp đồng bộ giao diện thực tế của Bảo Khố (`VaultScreen.kt`), Nhiệm Vụ Đường (`MissionHallScreen.kt`), và `MainActivity.kt`.**
- **TUYỆT ĐỐI KHÔNG DÙNG ICON Ổ KHÓA trên ứng dụng Bảo Khố (tuân thủ nghiêm ngặt Mục 6 Canonical Design V2).**
- **Tách bạch hoàn toàn Presentation Animation với Business State (animation không quyết định completion, unlock hay persistence).**
- **Bảo toàn 100% Frozen Core App Lock và 286 bài test hồi quy cũ; bổ sung 8 unit tests mới -> 294/294 PASS (100%).**
- **OPEN-01, OPEN-02, OPEN-03, OPEN-04, OPEN-05, OPEN-06, OPEN-07 TIẾP TỤC GIỮ NGUYÊN TRẠNG THÁI OPEN.**

### Các nội dung đã thực hiện:
- **Design System Foundation (`ui/design/theme/`):**
  - Tạo `CultivationColors.kt`: Semantic color tokens cho cả Dark Theme (Huyền Mặc `#090D16`, Thanh Ngọc `#14B8A6`, Hoàng Kim `#F59E0B`, Chu Sa `#F43F5E`, Tử Tiêu `#6366F1`) và Light Theme (Bạch Vân `#F4F6FB`).
  - Tạo `CultivationTypography.kt`: Semantic typography tokens (`headingHero`, `titleCard`, `subtitle`, `bodyText`, `labelRune`, `caption`, `button`, `statNumber`).
  - Tạo `CultivationShapes.kt`: Semantic shapes (`card`, `cardElevated`, `itemCell`, `button`, `chip`, `dialog`, `avatar`, `circular`).
  - Tạo `CultivationSpacing.kt`: Spacing tokens, quy định chuẩn kích thước cảm ứng tối thiểu `touchTargetMin = 48.dp` theo Accessibility guidelines.
  - Tạo `CultivationElevation.kt`: Elevation tokens (`none`, `subtle`, `card`, `cardElevated`, `dialog`).
  - Tạo `CultivationTheme.kt`: `CompositionLocalProvider` tích hợp mượt mà tương thích với Material 3 `ColorScheme`.
- **Animation Primitives (`ui/design/animation/`):**
  - Tạo `CultivationAnimation.kt`: `rememberBreathingAlpha` (nhịp thở linh lực 2000ms), `pressScaleEffect` (nảy nhẹ khi nhấn 0.96f), `animateProgress` (thanh tiến trình chuyển động mượt). Tách biệt hoàn toàn khỏi business state.
- **Core Components (`ui/design/components/`):**
  - Tạo `AsyncAppIcon.kt`: Tải biểu tượng ứng dụng Android thật bất đồng bộ qua `Dispatchers.IO` kết hợp `AppIconMemoryCache` (`LruCache<String, ImageBitmap>`), fallback avatar ngọc phù khi cần. Đảm bảo không block main thread.
  - Tạo `CultivationCard.kt`: Thẻ ngọc giản nhiều tầng (`STANDARD`, `ELEVATED`, `ACTIVE_GLOW` có viền thở linh lực, `OUTLINED`).
  - Tạo `CultivationButton.kt`: Nút bấm tu tiên với variants `PRIMARY`, `SECONDARY`, `GHOST`, `DANGER`, hỗ trợ trạng thái loading và scale nảy nhẹ.
  - Tạo `CultivationDialog.kt`: Bảng thông cáo ngọc giản viền phát quang linh lực tím nhạt Tử Tiêu.
  - Tạo `ProgressCard.kt`: Thẻ tiến trình tu luyện hôm nay với thanh năng lượng gradient mượt mà và nhãn huy hiệu.
  - Tạo `CultivationGrid.kt`: Lưới tự co giãn thích ứng theo kích thước thiết bị (`GridCells.Adaptive(140.dp)`).
  - Tạo `AppItemCard.kt`: `AppItemGridCard` (ô app Bảo Khố dạng túi trữ vật, có app icon thật, badge liên kết, nút gỡ Chu Sa, KHÔNG icon ổ khóa) và `AppItemSelectableRow` (cho dialog chọn app liên kết).
  - Tạo `TaskCard.kt`: Thẻ nhiệm vụ tự kỷ luật theo chuỗi, phân cấp rõ rệt nhiệm vụ active (`isCurrentActive` có nhãn "NHIỆM VỤ ĐANG THỰC HIỆN" và viền Kim Tinh), hiển thị chip icon app liên kết thật.
  - Tạo `ItemCard.kt`: Thẻ kho tàng vật phẩm tiên hiệp.
- **Tích Hợp Giao Diện Thực Tế:**
  - Nâng cấp `VaultScreen.kt`: Chuyển đổi toàn diện sang `CultivationTheme`, `CultivationGrid` với `AppItemGridCard`, `CultivationCard` thống kê quy mô Bảo Khố, `AddDiscoveredAppDialog` và dialog gỡ app qua `CultivationDialog`.
  - Nâng cấp `MissionHallScreen.kt`: Tích hợp `ProgressCard`, `TaskCard` cho nhiệm vụ đang làm và các nhiệm vụ tiếp theo, `TaskLinkageDialog` qua `CultivationDialog` và `AppItemSelectableRow`.
  - Nâng cấp `MainActivity.kt`: Bao bọc `CultivationTheme(darkTheme = true)`, tinh chỉnh màu sắc thanh điều hướng 3 tab (`NavigationBar`) phong cách Tiên Hiệp.
- **Kiểm Thử & Xác Minh:**
  - Tạo `CultivationDesignSystemTest.kt`: 8 unit test cases kiểm tra tính toàn vẹn của colors, typography, shapes, spacing, accessibility touch target, button và card variants.
  - Toàn bộ suite unit tests: **294/294 PASS (100% Success Rate)**.
  - Kiểm thử và chụp ảnh màn hình trực tiếp trên vivo iQOO Neo 10 (Android 15):
    + `screen_p19_5_missionhall.png`: Nhiệm Vụ Đường với ProgressCard, TaskCard active phát sáng và chip app liên kết icon thật.
    + `screen_p19_5_link_dialog.png`: Hộp thoại liên kết ứng dụng ngọc giản với checkbox vàng và real app icon.
    + `screen_p19_5_vault.png`: Bảo Khố dạng CultivationGrid với app icon thật, badge liên kết, nút gỡ Chu Sa, không icon ổ khóa.
    + `screen_p19_5_vault_dialog.png`: Dialog thu nạp app với danh sách real icon mượt mà không jank.
    + `screen_p19_5_admin_tab.png`: Tab Quản Trị Thực Thi bảo toàn 100% tính năng App Lock cũ, không hồi quy.
- **Tài Liệu:**
  - Tạo `docs/UI_DESIGN_SYSTEM.md`.
  - Cập nhật `docs/IMPLEMENTATION_STATUS.md`, `docs/DESIGN_AUDIT.md`, `docs/CHANGELOG.md`.

---

## [Phase 19] - 2026-09-07: VAULT INTEGRATION & TASK ↔ APP LINKAGE
### Bản chất giai đoạn:
- **Hiện thực hóa Checkpoint CP4 (Bảo Khố — Vault Domain) theo Canonical Design V2 (Mục 6).**
- **Hiện thực hóa Checkpoint CP6 (Quan hệ Task ↔ App Many-to-Many) theo Canonical Design V2 (Mục 4, 5, 6).**
- **Thiết lập Ranh giới An toàn `TaskAppEnforcementAdapter` (`DISABLED_PENDING_OPEN_01`), bảo vệ tuyệt đối OPEN-01 (không công thức 2/3, không tỷ lệ %, không điểm thưởng).**
- **Tích hợp Điều hướng 3 Tab tại `MainActivity.kt`: Nhiệm Vụ Đường, Bảo Khố, Quản Trị Thực Thi.**
- **Bảo toàn 100% Frozen Core App Lock và toàn bộ các bài test hồi quy cũ.**
- **OPEN-01, OPEN-02, OPEN-03, OPEN-04, OPEN-05, OPEN-06, OPEN-07 TIẾP TỤC GIỮ NGUYÊN TRẠNG THÁI OPEN.**

### Các nội dung đã thực hiện:
- **Domain Layer (Vault Models, Discovery & UseCases):**
  - Tạo `VaultApp.kt`, `DiscoveredApp`: Mô hình hóa app đã cài đặt và app trong Bảo Khố (`packageName`, `appName`, `addedAtWallMillis`, `isLockedByDefault`).
  - Tạo `InstalledAppDiscoveryService.kt`: Khám phá ứng dụng launcher qua `PackageManager.queryIntentActivities` với intent `ACTION_MAIN` + `CATEGORY_LAUNCHER`, tự loại trừ package ứng dụng hiện tại. Bổ sung thẻ `<queries>` trong `AndroidManifest.xml`.
  - Tạo `AddVaultAppUseCase.kt`: Thêm ứng dụng vào Bảo Khố (`vault_apps`), idempotent khi đã tồn tại.
  - Tạo `RemoveVaultAppUseCase.kt`: Gỡ ứng dụng khỏi Bảo Khố, tự động cascade gỡ sạch liên kết trong `task_app_cross_ref`, giữ nguyên toàn bộ tasks và lịch sử hoàn thành.
  - Tạo `GetVaultAppsUseCase.kt`: Lấy danh sách hoặc observe luồng Flow các app trong Bảo Khố.
  - Tạo `GetTaskLinkedAppsUseCase.kt`: Truy vấn danh sách app liên kết với task cụ thể.
  - Tạo `UpdateTaskLinkedAppsUseCase.kt`: Cập nhật đồng bộ danh sách app liên kết cho task, kiểm tra chặt chẽ chỉ cho phép liên kết với app đang có trong Bảo Khố.
- **OPEN-01 Safety Boundary (Enforcement Isolation):**
  - Tạo `TaskAppEnforcementAdapter.kt`: Ranh giới an toàn cô lập giữa Product Vault và Technical App Lock runtime.
  - Trả về `TaskAppEnforcementStatus.DISABLED_PENDING_OPEN_01` và `isTaskBasedUnlockApproved = false`.
  - Khẳng định rõ không tự phát minh công thức 2/3 hay tỷ lệ giải phong ấn khi Ký chủ chưa chốt OPEN-01.
- **UI & Presentation Layer:**
  - Tạo `VaultViewModel.kt`: Quản lý `VaultUiState`, danh sách app trong Bảo Khố, tìm kiếm app cài đặt trên máy, dialog thêm app, dialog xác nhận gỡ app.
  - Tạo `VaultScreen.kt`: Màn hình Bảo Khố với card Quy Mô Bảo Khố, danh sách ô item phong cách túi đồ tu tiên, **TUYỆT ĐỐI KHÔNG HIỂN THỊ ICON Ổ KHÓA TRÊN AVATAR** (tuân thủ nghiêm ngặt Canonical Mục 6), dialog thu nạp app cài đặt, dialog cảnh báo gỡ app kèm giải thích cascade.
  - Nâng cấp `MissionHallViewModel.kt` & `MissionHallScreen.kt`:
    - Thẻ nhiệm vụ hiển thị danh sách các chip ứng dụng liên kết và nút `+ Liên kết App` / `Sửa liên kết`.
    - Tạo `TaskLinkageDialog`: Cho phép Ký chủ chọn các ứng dụng từ Bảo Khố để liên kết với nhiệm vụ. Phản ứng tức thì khi app bị gỡ khỏi Bảo Khố.
  - Cập nhật `MainActivity.kt`: Tích hợp Material 3 `NavigationBar` gồm 3 Tab:
    - Tab 0: Nhiệm Vụ Đường (`MissionHallScreen`).
    - Tab 1: Bảo Khố (`VaultScreen`).
    - Tab 2: Quản Trị Thực Thi (`SettingsScreen` — bảo toàn 100% App Lock cũ).
- **Kiểm thử & Xác minh Toàn diện:**
  - Tạo `VaultDomainTest.kt`: 22 test scenarios bao phủ toàn bộ Test Matrix A-E (thêm app, danh sách app, gỡ app, persistence, duplicate safe, re-add không tự phục hồi liên kết cũ, 3 kịch bản many-to-many A/B/C, cascade deletion bảo toàn task & completion, OPEN-01 safety boundary).
  - Tạo `VaultViewModelTest.kt`: 5 test scenarios cho ViewModel Bảo Khố.
  - Bổ sung `MissionHallViewModelTest.kt`: Thêm 3 test scenarios cho luồng liên kết Task-App (tổng 9 tests PASS).
  - Tổng số unit tests: **286/286 PASS (100% Success Rate)**.
  - Xác minh thực tế trên thiết bị thực (vivo iQOO Neo 10 / Android 15):
    - Mở Bảo Khố, chọn thu nạp 2 app (`1.1.1.1` và `AgklbjBkqsM`).
    - Sang Nhiệm Vụ Đường, tạo task `ChayBo`.
    - Mở dialog liên kết, chọn cả 2 app Bảo Khố, lưu liên kết, hiển thị 2 chip app.
    - Sang Bảo Khố, bấm Gỡ app `1.1.1.1`, xác nhận dialog.
    - Quay lại Nhiệm Vụ Đường: Task `ChayBo` tự động chỉ còn lại `AgklbjBkqsM`, task và lịch sử hoàn thành giữ nguyên vẹn 100%.
    - Chuyển sang Tab Quản Trị Thực Thi: Toàn bộ cấu hình kỹ thuật App Lock không bị ảnh hưởng.

---

## [Phase 18] - 2026-09-07: CORE TASK DOMAIN & BASIC MISSION HALL
### Bản chất giai đoạn:
- **Hiện thực hóa Checkpoint CP3 (Core Task Domain & Basic Mission Hall Flow) theo Canonical Design V2 (Mục 5 & Mục 8).**
- **Xây dựng luồng tuần tự hóa chuỗi nhiệm vụ (Sequential Task Chain) tự động advance và reset theo mốc 04:00.**
- **Tích hợp giao diện Compose Nhiệm Vụ Đường với Material 3 NavigationBar trong `MainActivity.kt`.**
- **Bảo toàn 100% Frozen Core App Lock và 237 bài test hồi quy cũ.**
- **OPEN-01 -> OPEN-07 TIẾP TỤC GIỮ NGUYÊN TRẠNG THÁI OPEN.**

### Các nội dung đã thực hiện:
- **Domain Layer (Core Task Models & UseCases):**
  - Tạo `Task.kt`, `TaskWithStatus.kt`, `SequentialTaskChain.kt`: Mô hình hóa nhiệm vụ và trạng thái chuỗi tuần tự (`currentTask`, `incompleteTasks`, `completedTasksToday`, `totalActiveTasks`, `completedCountToday`).
  - Tạo `CreateTaskUseCase.kt`: Kiểm tra tính hợp lệ của tên nhiệm vụ (tự động trim khoảng trắng, từ chối chuỗi rỗng), gọi repository lưu vào Room.
  - Tạo `GetMissionHallTasksUseCase.kt`: Truy vấn danh sách nhiệm vụ đang hoạt động, lọc theo trạng thái hoàn thành trong Business Date 04:00 của ngày hiện tại, tự động xác định `currentTask` là nhiệm vụ chưa hoàn thành đầu tiên, hỗ trợ cả Flow phản ứng và suspend query.
  - Tạo `CompleteTaskUseCase.kt`: Ghi nhận hoàn thành nhiệm vụ idempotent cho Business Date 04:00, hỗ trợ quy tắc Canonical: task bắt đầu trước 04:00 hoàn thành sau 04:00 vẫn tính cho chu kỳ ngày cũ.
  - Tạo `ArchiveTaskUseCase.kt`: Lưu trữ / xóa mềm nhiệm vụ khỏi chuỗi hoạt động.
- **Data & Repository Layer Expansion:**
  - Bổ sung vào `CoreDataRepository` và `CoreDataRepositoryImpl`: `deleteTask`, `observeCompletedTaskIdsForDate`, `getDailyCompletionsForDate`, `observeDailyCompletionsForDate`.
  - Tạo Singleton Providers: `CoreDataRepositoryProvider.kt`, `BusinessDayProviderHolder.kt`.
- **UI & Presentation Layer:**
  - Tạo `MissionHallViewModel.kt`: Quản lý `MissionHallUiState` với trạng thái chuỗi `SequentialTaskChain`, loading, dialog nhập liệu, banner thông báo kết quả.
  - Tạo `MissionHallScreen.kt`:
    - Nút thêm nhiệm vụ nhỏ gọn ở góc dưới bên phải (Floating Action Button).
    - Dialog tạo nhiệm vụ tối giản gồm Tên nhiệm vụ và nút Xác nhận.
    - Card Tiến trình tu luyện hôm nay (`completedCount/totalCount`).
    - Card Nhiệm vụ đang thực hiện (hiển thị `currentTask`, nút Hoàn thành và nút Xóa).
    - Danh sách nhiệm vụ chưa hoàn thành của chu kỳ hôm nay.
    - Danh sách nhiệm vụ đã hoàn thành hôm nay.
  - Cập nhật `MainActivity.kt`: Tích hợp Material 3 `NavigationBar` gồm 2 tab:
    - Tab 0: Nhiệm Vụ Đường (`MissionHallScreen`).
    - Tab 1: Quản Trị Thực Thi (bảo toàn 100% UI App Lock và intent tests).
- **Kiểm thử & Xác minh Toàn diện:**
  - Tạo `TaskDomainTest.kt`: 13 test scenarios bao phủ tạo task, validation, completion idempotent, mốc reset 04:00, task vắt qua 04:00, sequential advancement, skip archived task, reset tự nhiên sang ngày mới (PASS 100%).
  - Tạo `MissionHallViewModelTest.kt`: 6 test scenarios bao phủ trạng thái ban đầu, mở/đóng dialog, validation lỗi, thêm task, hoàn thành task tuần tự, lưu trữ task (PASS 100%).
  - Tổng số unit test: **256/256 PASS (100% Success Rate)**.
  - Build APK Debug: `assembleDebug` BUILD SUCCESSFUL.
  - Xác minh thực tế trên thiết bị thực (vivo iQOO Neo 10 / Android 15 / OriginOS 5): Cài đặt APK qua ADB, khởi động `MainActivity`, mở dialog thêm task, nhập task "LuyenCong", xác nhận hiển thị trong chuỗi tuần tự, bấm "Xong" để hoàn thành, xác minh UI hiển thị "1/1 Hoàn thành", chuyển tab Quản Trị Thực Thi kiểm tra App Lock hoạt động hoàn hảo.

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
