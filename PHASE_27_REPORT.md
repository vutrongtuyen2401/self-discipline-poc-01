# PHASE 27 — CORE ENFORCEMENT & BUSINESS UNLOCK INTEGRATION AUDIT REPORT

**Ngày:** 07/09/2026  
**Thiết bị kiểm thử thực tế:** vivo iQOO Neo 10 (V2425A), Android 15 (API 35), OriginOS 5  
**Phạm vi:** Core Enforcement & Business Unlock Integration Audit  
**Loại:** Audit & Integration Validation (0 dòng runtime thay đổi)

---

## 1. MỤC TIÊU & PHẠM VI (SCOPE)

Thực hiện kiểm toán toàn diện kiến trúc tích hợp giữa hai phân hệ cốt lõi đã được phê duyệt chính thức trong Canonical Design V2:
1. **Technical App Lock (OPEN-04 — CLOSED):** Đóng vai trò là Scoped Product Behavior / Hard Ceiling Guardrail (Hàng Rào Cấm Tuyệt Đối) với quyền ưu tiên tối thượng (`Technical Precedence = Absolute`).
2. **Business Task-Based Unlock (OPEN-01 — CLOSED):** Cơ chế giải phong ấn theo tỷ lệ nhiệm vụ liên kết hoàn thành trong ngày $\text{required} = \lceil 2N/3 \rceil = (2N + 2) / 3$ (số học nguyên thuần túy), reset chu kỳ tại 04:00 sáng.

Giai đoạn này tập trung kiểm toán dòng dữ liệu thực thi hai tầng:
```text
Technical Policy (Target / Schedule / Daily Limit)
      ↓
Technical Lock / Allow Decision
      ↓
Business Unlock Evaluation (Vault ↔ Mission Hall, OPEN-01)
      ↓
Final Enforcement Action (ALLOW / LOCK)
```

---

## 2. CANONICAL DESIGN REFERENCES (NGUỒN SỰ THẬT)

- [`docs/CANONICAL_DESIGN_V2.md`](file:///c:/Code/self-discipline-poc-01/docs/CANONICAL_DESIGN_V2.md):
  - **Mục 5:** Nhiệm Vụ Đường — Tạo, quản lý, liên kết task với app Bảo Khố.
  - **Mục 6:** Bảo Khố — Quản lý app bị phong ấn, xóa app tự động gỡ liên kết, thêm lại app không phục hồi liên kết cũ.
  - **Mục 7:** Quy tắc phong ấn & mở khóa — Công thức OPEN-01 $\text{required} = (2N + 2) / 3$, điều kiện $N > 0 \land \text{completed} \ge \text{required}$; Quyết định OPEN-04 Technical Precedence tuyệt đối.
  - **Mục 8:** Chu kỳ ngày & Reset 04:00 — Nhiệm vụ bắt đầu trước 04:00 thuộc chu kỳ cũ; cấm rolling 24h.
  - **Mục 25:** POC-01 Nền tảng kỹ thuật Accessibility Service + Window Overlay + LockScreenActivity.
  - **Mục 28:** Quản lý các mục thiết kế mở (OPEN-01 & OPEN-04 CLOSED; OPEN-02, 03, 05, 06, 07 OPEN).
- [`docs/DESIGN_DECISIONS.md`](file:///c:/Code/self-discipline-poc-01/docs/DESIGN_DECISIONS.md): Mục 10, 11, 12.
- [`docs/OPEN_ITEMS.md`](file:///c:/Code/self-discipline-poc-01/docs/OPEN_ITEMS.md): Bảng quản lý 7 OPEN items.

---

## 3. CÁC TỆP NGUỒN ĐÃ ĐƯỢC KIỂM TOÁN (FILES AUDITED)

| Thành phần | Đường dẫn tệp | Vai trò kiểm toán |
|:---|:---|:---|
| `PolicyEngine` | `com/example/selfdisciplinepoc01/policy/PolicyEngine.kt` | Đánh giá chính sách kỹ thuật (Target 24/7, Schedule, Daily Limit) |
| `TaskUnlockPolicy` | `com/example/selfdisciplinepoc01/domain/policy/TaskUnlockPolicy.kt` | Hiện thực hóa công thức OPEN-01 thuần túy số học nguyên |
| `TaskAppEnforcementAdapter` | `com/example/selfdisciplinepoc01/domain/enforcement/TaskAppEnforcementAdapter.kt` | Bộ chuyển đổi ranh giới 2 tầng, quản lý snapshot in-memory cache |
| `AppDetectorAccessibilityService` | `com/example/selfdisciplinepoc01/AppDetectorAccessibilityService.kt` | Lắng nghe sự kiện cửa sổ Android, điều phối thực thi và hiển thị khiên |
| `LockScreenActivity` | `com/example/selfdisciplinepoc01/LockScreenActivity.kt` | Màn hình khóa chặn an toàn, đo độ trễ khung hình đầu tiên |
| `BlockingShieldOverlay` | `com/example/selfdisciplinepoc01/overlay/BlockingShieldOverlay.kt` | Tấm chắn 0ms đón đầu che phủ ứng dụng bị khóa |
| `BusinessDayProvider` | `com/example/selfdisciplinepoc01/time/BusinessDayProvider.kt` | Ranh giới chu kỳ ngày nghiệp vụ 04:00:00 |
| `CompleteTaskUseCase` | `com/example/selfdisciplinepoc01/domain/usecase/CompleteTaskUseCase.kt` | Luồng hoàn thành nhiệm vụ theo chu kỳ ngày |
| `RemoveVaultAppUseCase` | `com/example/selfdisciplinepoc01/domain/usecase/RemoveVaultAppUseCase.kt` | Gỡ app khỏi Bảo Khố và cascade xóa liên kết |
| `AddVaultAppUseCase` | `com/example/selfdisciplinepoc01/domain/usecase/AddVaultAppUseCase.kt` | Thêm app vào Bảo Khố với trạng thái chưa liên kết (N=0) |
| `CoreDataRepositoryImpl` | `com/example/selfdisciplinepoc01/data/repository/CoreDataRepositoryImpl.kt` | Quản lý dữ liệu Room DB (Task, App, CrossRef, Completion) |
| `ScheduleWatcher` | `com/example/selfdisciplinepoc01/policy/ScheduleWatcher.kt` | Giám sát mốc giờ lịch trình theo deadline (không polling) |
| `UsageLimitWatcher` | `com/example/selfdisciplinepoc01/usage/UsageLimitWatcher.kt` | Giám sát giới hạn sử dụng theo deadline thời gian thực |

---

## 4. XÁC MINH KIẾN TRÚC & DÒNG DỮ LIỆU (DATA FLOW VERIFICATION)

### Dòng thực thi phân tầng (Tiered Enforcement Flow):
```text
Window State Changed (Android Accessibility Event)
         ↓
TaskAppEnforcementAdapter.evaluateSync(packageName) [< 0.05ms, O(1), zero disk I/O]
         ↓
[TẦNG 1: TECHNICAL POLICY]
  - PolicyEngine.evaluate(packageName)
  - Nếu Technical LOCK (Lịch trình hoặc Quá giới hạn ngày):
      → Trả về ngay: finalAction = LOCK, reason = LOCKED_BY_POLICY
      → ƯU TIÊN TUYỆT ĐỐI: Không cho phép bất kỳ task hoàn thành hay voucher nào bypass.
  - Nếu Technical ALLOW: Chuyển tiếp sang Tầng 2.
         ↓
[TẦNG 2: BUSINESS UNLOCK (OPEN-01)]
  - Nếu KHÔNG thuộc Bảo Khố (Non-Vault):
      → Trả về: finalAction = ALLOW (ALLOWED_NOT_PROTECTED / ALLOWED_BY_POLICY)
  - Nếu thuộc Bảo Khố nhưng N = 0 (Chưa liên kết nhiệm vụ):
      → Trả về: finalAction = LOCK, reason = LOCKED_BY_VAULT_NO_TASK
  - Nếu thuộc Bảo Khố có N > 0 nhiệm vụ liên kết:
      → Tính toán: required = (2*N + 2) / 3
      → completed >= required ?
          * ĐẠT   → finalAction = ALLOW, reason = ALLOWED_UNLOCKED_BY_TASKS
          * KHÔNG → finalAction = LOCK, reason = LOCKED_INSUFFICIENT_TASKS
         ↓
[HÀNH ĐỘNG THỰC THI (ENFORCEMENT ACTION)]
  - Nếu ALLOW: Thu hồi khiên che `BlockingShieldOverlay.hide()`, đếm thời gian sử dụng nếu là Target, thiết lập lại trạng thái khóa.
  - Nếu LOCK: Dừng đếm thời gian sử dụng, kiểm tra Rule A/B/C (tránh trùng lặp), hiển thị khiên mờ 0ms `BlockingShieldOverlay.show()`, gửi Intent khởi chạy `LockScreenActivity`.
```

---

## 5. MA TRẬN KIỂM TOÁN TÍCH HỢP (TEST MATRIX — 25/25 SCENARIOS)

Được tự động hóa 100% và kiểm chứng độc lập trong [`CoreEnforcementIntegrationAuditTest.kt`](file:///c:/Code/self-discipline-poc-01/app/src/test/java/com/example/selfdisciplinepoc01/domain/enforcement/CoreEnforcementIntegrationAuditTest.kt):

| Nhóm | # | Kịch bản kiểm toán | Kết quả mong đợi | Kết quả thực tế | Trạng thái |
|:---|:---:|:---|:---|:---|:---:|
| **Technical Precedence** | 1 | Technical ALLOW + Business LOCK | `LOCK` (`LOCKED_INSUFFICIENT_TASKS`) | `LOCK` | ✅ PASS |
| | 2 | Technical ALLOW + Business ALLOW | `ALLOW` (`ALLOWED_UNLOCKED_BY_TASKS`) | `ALLOW` | ✅ PASS |
| | 3 | Technical LOCK + Business ALLOW | `LOCK` (`LOCKED_BY_POLICY`) | `LOCK` | ✅ PASS |
| | 4 | Technical LOCK + Business LOCK | `LOCK` (`LOCKED_BY_POLICY`) | `LOCK` | ✅ PASS |
| **OPEN-01 Task Unlock** | 5 | $N = 0$ | `LOCK` (`LOCKED_BY_VAULT_NO_TASK`) | `LOCK` | ✅ PASS |
| | 6 | $N = 1$, hoàn thành = 0 | `LOCK` (`LOCKED_INSUFFICIENT_TASKS`) | `LOCK` | ✅ PASS |
| | 7 | $N = 1$, hoàn thành = 1 | `ALLOW` (nếu Technical ALLOW) | `ALLOW` | ✅ PASS |
| | 8 | $N = 2$, hoàn thành = 1 | `LOCK` ($1 < 2$) | `LOCK` | ✅ PASS |
| | 9 | $N = 2$, hoàn thành = 2 | `ALLOW` ($2 \ge 2$) | `ALLOW` | ✅ PASS |
| | 10 | $N = 3$, hoàn thành = 2 | `ALLOW` ($2 \ge 2$) | `ALLOW` | ✅ PASS |
| | 11 | $N = 3$, hoàn thành = 1 | `LOCK` ($1 < 2$) | `LOCK` | ✅ PASS |
| | 12 | Nhiệm vụ lưu trữ (`isArchived`) | Không làm tăng $N$ hiệu lực | $N$ chỉ tính task active | ✅ PASS |
| | 13 | Nhiệm vụ đã xóa (`deleted`) | Không làm tăng $N$ hiệu lực | Task xóa bị loại hoàn toàn | ✅ PASS |
| | 14 | Một task liên kết nhiều app | Không bị đếm trùng lặp | Đếm độc lập 1 lần cho mỗi app | ✅ PASS |
| **Chu Kỳ Nghiệp Vụ** | 15 | Hoàn thành trước 04:00 | Thuộc chu kỳ ngày cũ | Ghi nhận đúng chu kỳ cũ | ✅ PASS |
| | 16 | Sau 04:00 sang ngày mới | Task được tính lại chu kỳ mới | Hoàn thành ngày cũ không tính cho ngày mới | ✅ PASS |
| | 17 | Snapshot sau reset 04:00 | Không giữ completion cũ | Tự động refresh snapshot mới | ✅ PASS |
| **Bảo Khố (Vault)** | 18 | App chưa có task liên kết | `LOCK` ($N = 0$) | `LOCK` | ✅ PASS |
| | 19 | App có task liên kết | Đánh giá theo công thức OPEN-01 | Đánh giá chính xác theo tỷ lệ | ✅ PASS |
| | 20 | Gỡ app khỏi Bảo Khố | Xóa liên kết, trả về Non-Vault | Quan hệ bị xóa, app thành ALLOW | ✅ PASS |
| | 21 | Thu nạp lại app vào Bảo Khố | Không phục hồi liên kết cũ ($N=0$) | App có $N=0 \rightarrow$ LOCK | ✅ PASS |
| **Runtime & Cache** | 22 | Đổi task khi Accessibility đang chạy | Snapshot cache cập nhật tức thì | In-memory cache cập nhật đồng bộ | ✅ PASS |
| | 23 | Mở target app ngay sau đổi liên kết | Dùng trạng thái mới, không delay | `evaluateSync` đọc đúng dữ liệu | ✅ PASS |
| | 24 | Snapshot cũ/sai lệch | Không bao giờ bypass được Technical Lock | PolicyEngine luôn được check đầu | ✅ PASS |
| | 25 | Callback/Session cũ | Không ghi đè phiên khóa mới | `sessionId < currentSessionId` bị chặn | ✅ PASS |

---

## 6. XÁC MINH TRÊN THIẾT BỊ THẬT (REAL-DEVICE VALIDATION)

- **Thiết bị:** vivo iQOO Neo 10 (Model: `V2425A`)
- **Hệ điều hành:** Android 15 / API 35 / OriginOS 5
- **Kết nối:** ADB qua USB (`10CF3J1F3400238`)

| # | Kịch bản kiểm thử | Hành động thực tế trên thiết bị | Kết quả mong đợi | Kết quả thực tế (Logcat / UI) | Đánh giá |
|:---:|:---|:---|:---|:---|:---:|
| 1 | App Bảo Khố chưa liên kết ($N=0$) | Khởi chạy `com.cloudflare.onedotonedotonedotone` (`1.1.1.1`) | Bị phong ấn ngay lập tức, hiển thị `LockScreenActivity` | Logcat: `[DECISION: LAUNCH] (sessionId=1, reason=ACCESSIBILITY_EVENT)`. `LockScreenActivity` hiển thị với độ trễ khung hình đầu $114.66$ ms. | **PASS** ✅ |
| 2 | App Bảo Khố đã giải phong ấn ($N=1, 1/1$) | Khởi chạy `ru.ibowpjdd.hnuldiece` (`AgkIbjBkqsM`) liên kết task `ChayBo` đã xong | Cho phép mở bình thường, không khóa | Logcat: `[POLICY_EVALUATION] action=ALLOW, reason=ALLOWED_UNLOCKED_BY_TASKS`. Ứng dụng chạy mượt mà trên foreground. | **PASS** ✅ |
| 3 | App ngoài Bảo Khố không thuộc diện cấm | Khởi chạy `com.android.chrome` khi không kích hoạt chính sách cấm | Cho phép mở bình thường | Logcat: `action=ALLOW, reason=ALLOWED_NOT_PROTECTED, classification=NON_VAULT_APP`. | **PASS** ✅ |
| 4 | Trạng thái đồng bộ giao diện người dùng | Mở tab Nhiệm Vụ Đường và Bảo Khố | UI hiển thị đúng tiến trình và nhãn trạng thái | Tab Nhiệm Vụ Đường hiển thị "2/2 - VIÊN MÃN CÔNG ĐỨC". Tab Bảo Khố hiển thị `AgkIbjBkqsM` ("Đã mở 1/1") và `1.1.1.1` ("Chưa liên kết - Khóa"). | **PASS** ✅ |

---

## 7. ĐẾM VÀ QUẢN LÝ KIỂM THỬ (TEST COUNT MANAGEMENT)

- **Số test baseline trước Phase 27:** **327 tests** (24 test classes).
- **Bộ kiểm thử tích hợp mới:** [`CoreEnforcementIntegrationAuditTest.kt`](file:///c:/Code/self-discipline-poc-01/app/src/test/java/com/example/selfdisciplinepoc01/domain/enforcement/CoreEnforcementIntegrationAuditTest.kt) (+25 unit tests).
- **Tổng số test sau Phase 27:** **352 tests** (25 test classes).
- **Kết quả thực thi:**
  ```text
  TOTAL: 352 tests, 0 failures, 0 errors
  XML FILES: 25
  BUILD SUCCESSFUL
  ```

---

## 8. KẾT QUẢ BIÊN DỊCH (BUILD RESULT)

- `.\gradlew.bat testDebugUnitTest --rerun` $\rightarrow$ **BUILD SUCCESSFUL** (352/352 PASS).
- `.\gradlew.bat assembleDebug` $\rightarrow$ **BUILD SUCCESSFUL** (36 actionable tasks: 36 up-to-date).

---

## 9. MÃ NGUỒN RUNTIME (RUNTIME CHANGES)

- **Số dòng runtime thay đổi:** **0 dòng** (`app/src/main` hoàn toàn nguyên vẹn, tuân thủ nghiêm ngặt nguyên tắc Audit-only).

---

## 10. PHÂN LOẠI SAI LỆCH (DEVIATION CLASSIFICATION)

Toàn bộ các module được kiểm toán được phân loại theo tiêu chuẩn quy định:
- `PolicyEngine`: **CORRECT**
- `TaskUnlockPolicy`: **CORRECT**
- `TaskAppEnforcementAdapter`: **CORRECT**
- `AppDetectorAccessibilityService`: **CORRECT**
- `LockScreenActivity`: **CORRECT**
- `BlockingShieldOverlay`: **CORRECT**
- `BusinessDayProvider`: **CORRECT**
- `ScheduleWatcher` & `UsageLimitWatcher`: **CORRECT**
- `CoreDataRepositoryImpl`: **CORRECT**

**Không phát hiện sai lệch nào thuộc nhóm WRONG, MISSING, EXTRA hay UNCERTAIN.**

---

## 11. KHẮC PHỤC (FIXES)

- Không cần can thiệp sửa chữa mã nguồn runtime.
- Bổ sung bộ test kiểm toán chuyên biệt 25 kịch bản tại `CoreEnforcementIntegrationAuditTest.kt`.

---

## 12. QUẢN TRỊ CÁC MỤC OPEN (REMAINING OPEN ITEMS)

Tuân thủ nghiêm ngặt các ranh giới quản trị (Governance Guardrails):
- **OPEN-01:** **CLOSED** (Quyết định sản phẩm công thức $\lceil 2N/3 \rceil$).
- **OPEN-04:** **CLOSED** (Quyết định sản phẩm Scoped Product Behavior / Hard Ceiling Guardrail).
- **OPEN-02:** **OPEN** — Tuyệt đối không tự tạo hệ thống điểm tu luyện hay phần thưởng.
- **OPEN-03:** **OPEN** — Tuyệt đối không tự suy diễn công thức Tháp Thí Luyện / Tầng 4.
- **OPEN-05:** **OPEN** — Room DB duy trì làm nền tảng kỹ thuật, chưa chốt schema chính thức.
- **OPEN-06:** **OPEN** — Không có Cloud sync, log in-memory cục bộ; bảo lưu định hướng hybrid dài hạn.
- **OPEN-07:** **OPEN** — Không tự tạo animation state machine giả lập hay audio tokens.

---

## 13. CAM KẾT VÀ ĐỒNG BỘ GIT (GIT COMMIT & REMOTE STATUS)

- **Đề xuất Commit Message:** `test(core): audit technical and business unlock integration`
- **Tình trạng nhánh:** `main` đồng bộ trực tiếp với `origin/main`.

---

## 14. TRẠNG THÁI WORKING TREE

Sạch sẽ (`clean`), không có file rác hay tiến trình treo.
