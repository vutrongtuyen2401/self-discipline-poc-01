# BÁO CÁO NGHIỆM THU PHASE 11-A — PRODUCTION HARDENING

**Thiết bị kiểm thử:** vivo iQOO Neo 10 (Model V2425A)  
**Hệ điều hành:** Android 15 / OriginOS 5  
**Baseline kế thừa:** Phase 05–10-C (FROZEN & ACCEPTED)  
**Trạng thái nghiệm thu:** **ACCEPTED (HOÀN THÀNH TOÀN DIỆN)**

---

## 1. TỔNG QUAN TRIỂN KHAI (Implementation Summary)

Phase 11-A tập trung củng cố toàn diện chất lượng sản phẩm (Production Hardening) ở các khía cạnh:
- **Vòng đời dịch vụ (Lifecycle):** Bảo đảm an toàn tuyệt đối khi `onServiceConnected` được gọi nhiều lần, `onDestroy` dọn dẹp sạch sẽ tài nguyên, hủy triệt để mọi callback treo và không để rò rỉ bộ nhớ.
- **Quyền sở hữu Watcher (Watcher Ownership):** Bảo đảm tính bất biến (idempotent) của `start()` và `stop()`; quản lý thế hệ `currentGeneration` để loại bỏ 100% callback trễ (stale callbacks); vô hiệu hóa deadline khi có thay đổi target hoặc cập nhật chính sách.
- **Vòng đời Activity (LockScreenActivity Lifecycle):** Bảo toàn hành vi `singleTop`, an toàn khi tái tạo Activity (recreation) thông qua việc lưu và khôi phục `currentSessionId` qua `onSaveInstanceState`, bảo toàn các quy tắc khóa Frozen Core.
- **An toàn tài nguyên BlockingShieldOverlay:** Đồng bộ hóa đa luồng với `synchronized(lock)`, cân bằng tuyệt đối các lượt `addView()` và `removeView()`, bắt ngoại lệ an toàn `Throwable` để không bao giờ xảy ra rò rỉ WindowManager.
- **Chống tắc nghẽn I/O (Repository Resilience):** Truy xuất trạng thái đích tức thì qua bộ nhớ in-memory (`ConcurrentHashMap`), tuyệt đối không sử dụng `runBlocking()` hay I/O đĩa trên critical path của Accessibility Event; cơ chế fallback an toàn khi gặp dữ liệu lỗi.
- **Kiểm thử áp lực (Stress / Soak Tests):** Xây dựng bộ test xác định (deterministic) với tối thiểu 100 chu kỳ cho mỗi khía cạnh, không sử dụng `Thread.sleep` hay đồng bộ thời gian giả tạo.

---

## 2. DANH SÁCH FILE THAY ĐỔI (Files Changed)

### Mã nguồn chính (`app/src/main/`):
1. [`BlockingShieldOverlay.kt`](file:///c:/Code/self-discipline-poc-01/app/src/main/java/com/example/selfdisciplinepoc01/overlay/BlockingShieldOverlay.kt):
   - Bổ sung đối tượng khóa `private val lock = Any()`, bọc `synchronized(lock)` cho `show()`, `hide()`, `cleanup()`, `isShown()`.
   - Bắt mọi ngoại lệ `Throwable` khi `addView` và `removeView` để tránh sập app do WindowManager.
   - Tự động hủy `timeoutRunnable` khi hide/cleanup để tránh giữ tham chiếu Handler.
2. [`LockScreenActivity.kt`](file:///c:/Code/self-discipline-poc-01/app/src/main/java/com/example/selfdisciplinepoc01/LockScreenActivity.kt):
   - Override `onSaveInstanceState` lưu `currentSessionId`.
   - Khôi phục `currentSessionId` từ `savedInstanceState` trong `onCreate` khi Activity bị recreate.
3. [`ScheduleWatcher.kt`](file:///c:/Code/self-discipline-poc-01/app/src/main/java/com/example/selfdisciplinepoc01/policy/ScheduleWatcher.kt) & [`UsageLimitWatcher.kt`](file:///c:/Code/self-discipline-poc-01/app/src/main/java/com/example/selfdisciplinepoc01/usage/UsageLimitWatcher.kt):
   - Củng cố tính idempotent của `stop()`: nếu `!isRunning` thì lập tức trả về mà không tăng generation thừa.
   - Thêm phương thức kiểm tra trạng thái phục vụ kiểm thử.
4. [`AppDetectorAccessibilityService.kt`](file:///c:/Code/self-discipline-poc-01/app/src/main/java/com/example/selfdisciplinepoc01/AppDetectorAccessibilityService.kt):
   - Thêm kiểm tra `if (!isRunning) return` trong `handleLimitDeadlineReached` và `handleScheduleDeadlineReached` để loại bỏ dứt điểm callback muộn sau khi destroy service.
   - Bọc `try-catch` an toàn khi gọi `registerReceiver` và `unregisterReceiver`.

### Bộ kiểm thử mới (`app/src/test/`):
5. [`ConcurrencyAndLifecycleHardeningTest.kt`](file:///c:/Code/self-discipline-poc-01/app/src/test/java/com/example/selfdisciplinepoc01/policy/ConcurrencyAndLifecycleHardeningTest.kt):
   - **21 unit tests mới** bao phủ toàn bộ 15 kịch bản Concurrency/Race Matrix, tính an toàn của Shield, tính idempotent của Watcher, Repository resilience và UsageTracker segment boundary.
6. [`ProductionStressAndSoakTest.kt`](file:///c:/Code/self-discipline-poc-01/app/src/test/java/com/example/selfdisciplinepoc01/policy/ProductionStressAndSoakTest.kt):
   - **6 stress tests mới** thực thi >= 100 chu kỳ cho mỗi kịch bản, xác định 100%, không sleep.

### Kịch bản kiểm thử thiết bị (`scratch/`):
7. [`run_phase11a_regressions.ps1`](file:///c:/Code/self-discipline-poc-01/scratch/run_phase11a_regressions.ps1):
   - Script tự động hóa 12 kịch bản thực tế 11A-01 đến 11A-12 trên vivo iQOO Neo 10.

---

## 3. XÁC MINH FROZEN CORE (Frozen Core Verification)

Toàn bộ các bất biến Frozen Core đã được bảo toàn nguyên vẹn:
- [x] **Ngữ nghĩa cốt lõi:** `lastForegroundPackage`, `isLockScreenVisible`, `isChromeLockedForCurrentTransition`, `currentSessionId`, `lastLockLaunchTimestamp` giữ nguyên định danh và logic cập nhật.
- [x] **Điểm vào duy nhất:** Duy nhất `launchLockSession()` chịu trách nhiệm kích hoạt màn hình khóa; không tạo điểm vào thay thế nào.
- [x] **Các quy tắc loại trừ:** Rule A (LockScreen visible guard), Rule B (intra-session duplicate guard), Rule C (cooldown guard) hoàn toàn không đổi.
- [x] **Enum LockReason:** Giữ nguyên chính xác 3 giá trị: `ACCESSIBILITY_EVENT`, `DAILY_LIMIT`, `SCHEDULE_DEADLINE`.
- [x] **Kiến trúc:** Không tạo thêm bất kỳ State Machine hay Policy Engine mới nào.

---

## 4. MA TRẬN VÒNG ĐỜI (Lifecycle Matrix)

| Thành phần | Sự kiện kiểm tra | Hành vi xác minh | Kết quả |
| :--- | :--- | :--- | :--- |
| **AccessibilityService** | `onServiceConnected()` gọi lặp lại | Không tạo watcher trùng lặp, không rò rỉ receiver | **ĐẠT** |
| **AccessibilityService** | `onDestroy()` | Hủy ScheduleWatcher, UsageLimitWatcher, dọn dẹp Shield, đóng session | **ĐẠT** |
| **AccessibilityService** | Callback đến sau `onDestroy()` | Bị bỏ qua an toàn bởi cờ `!isRunning` | **ĐẠT** |
| **AccessibilityService** | Reconnect / Restart | Sinh thế hệ watcher mới (`generation`), không giữ callback cũ | **ĐẠT** |
| **LockScreenActivity** | `onNewIntent()` | Giữ nguyên phiên khóa hiện tại, không tạo duplicate lock | **ĐẠT** |
| **LockScreenActivity** | Activity Recreation (Xoay/Hệ thống khôi phục) | Khôi phục `currentSessionId` qua `onSaveInstanceState`, Rule A giữ vững | **ĐẠT** |
| **LockScreenActivity** | `onDestroy()` dọn dẹp Shield | An toàn ngay cả khi view chưa add, add lỗi hoặc đã remove | **ĐẠT** |
| **LockScreenActivity** | Back / Home navigation | Thoát khóa chuẩn xác, reset cờ `isLockScreenVisible`, sẵn sàng cho lần khóa sau | **ĐẠT** |

---

## 5. MA TRẬN ĐỒNG THỜI & TRANH CHẤP (Concurrency & Race Matrix)

Tất cả 15 kịch bản theo yêu cầu Mục 6 đã được kiểm thử xác định (deterministic) tại `ConcurrencyAndLifecycleHardeningTest.kt`:

| ID | Kịch bản Tranh chấp (Race Scenario) | Bất biến yêu cầu (Required Invariant) | Kết quả Test |
| :---: | :--- | :--- | :---: |
| **01** | Accessibility event + Schedule deadline | Đúng 1 lock session chiến thắng (ACCESSIBILITY_EVENT) | **PASS** |
| **02** | Accessibility event + Daily Limit deadline | Đúng 1 lock session chiến thắng (ACCESSIBILITY_EVENT) | **PASS** |
| **03** | Schedule deadline + Daily Limit deadline | Đúng 1 lock session chiến thắng, không duplicate | **PASS** |
| **04** | Schedule deadline + foreground switch | Chuyển target hủy bỏ deadline cũ của target trước | **PASS** |
| **05** | Daily Limit deadline + foreground switch | Chuyển target hủy bỏ deadline limit của target trước | **PASS** |
| **06** | Deadline + screen OFF | Screen OFF tạm dừng watcher, không bắn deadline khi màn hình tắt | **PASS** |
| **07** | Deadline + service destroy | Service destroy hủy watcher, callback muộn bị loại bỏ an toàn | **PASS** |
| **08** | Deadline + policy update | Cập nhật chính sách hủy bỏ deadline cũ không còn hợp lệ | **PASS** |
| **09** | Deadline + LockScreen already visible | Rule A ngăn chặn deadline tạo duplicate lock session | **PASS** |
| **10** | Hai callback nhắm cùng một package | Callback thứ hai bị loại bỏ bởi Rule B / Rule C | **PASS** |
| **11** | Callback cũ nhắm package trước | Guard package mismatch loại bỏ callback, không khóa nhầm | **PASS** |
| **12** | Callback sau khi watcher stop | Watcher stop tăng generation, callback bị drop an toàn | **PASS** |
| **13** | Callback sau khi watcher restart | Watcher restart tăng generation mới, callback generation cũ bị loại | **PASS** |
| **14** | startActivity failure + shield cleanup | Bắt lỗi khởi chạy và tự động dọn dẹp shield an toàn | **PASS** |
| **15** | Dịch vụ kết nối lại nhanh (Rapid reconnect) | Khởi tạo sạch sẽ, không callback treo, không launch sai | **PASS** |

---

## 6. MA TRẬN AN TOÀN TÀI NGUYÊN SHIELD (Shield Cleanup Matrix)

| Kịch bản rút lui (Exit Path) | Trạng thái WindowManager | Trạng thái View Tree | Kết quả |
| :--- | :--- | :--- | :---: |
| **Khởi chạy bình thường thành công** | View được add và sau đó handoff sang LockScreen | `removeView()` được gọi chính xác 1 lần | **ĐẠT** |
| **LockScreen Resume** | Gọi `hide("handoff_to_lockscreen")` | View được gỡ sạch, không view thừa | **ĐẠT** |
| **Service Interrupt / Destroy** | Gọi `cleanup()` trong `onDestroy()` | Gỡ bỏ view an toàn, hủy Runnable timeout | **ĐẠT** |
| **startActivity gặp lỗi** | Gọi `hide("startActivity_failure")` | Không để lại màn hình trắng che phủ máy | **ĐẠT** |
| **Màn hình tắt (Screen OFF)** | Gọi `cleanup()` | View được gỡ bỏ ngay lập tức | **ĐẠT** |
| **Cầu chì an toàn 2000ms (Safety fuse)** | Handler kích hoạt `hide("safety_timeout_2000ms")` | Thu hồi shield nếu Activity không kịp resume | **ĐẠT** |
| **Gọi hide/cleanup lặp lại nhiều lần** | Không ném ngoại lệ | No-op an toàn (Idempotent) | **ĐẠT** |
| **WindowManager ném BadToken/Exception** | Bắt `Throwable`, reset cờ nội bộ | Không gây sập app, không rò rỉ bộ nhớ | **ĐẠT** |

---

## 7. KẾT QUẢ PROCESS DEATH & PHỤC HỒI (Process Restart Results)

1. **Process bị kill khi ứng dụng đích đang ở Foreground:**
   - Ứng dụng POC bị terminate qua `am force-stop`.
   - Khi dịch vụ Accessibility khởi động lại, target repo nạp lại danh sách từ Jetpack DataStore preferences.
   - Khi ứng dụng đích phát sinh accessibility event mới, hệ thống nhận diện tức thì và kích hoạt khóa chính xác.
2. **Process bị kill khi LockScreen đang hiển thị:**
   - Phiên khóa cũ kết thúc sạch sẽ; khi mở lại ứng dụng đích, một phiên khóa mới độc lập được khởi tạo với fresh session ID.
3. **Process bị kill khi có Watcher deadline đang chờ:**
   - Deadline tạm thời bị hủy do tiến trình bị hủy; khi dịch vụ khởi động lại, deadline được tính toán lại dựa trên mốc thời gian thực tế (Wall-Clock và ElapsedRealtime), không bị mất hạn định.
4. **Không phục hồi mù quáng:** Hệ thống tuân thủ nguyên tắc không tự ý hiển thị LockScreen khi chưa có bằng chứng fresh foreground event có thẩm quyền từ Accessibility.

---

## 8. ĐỘ BỀN VỮNG REPOSITORY & DATASTORE (Repository Resilience)

- **Đọc không tắc nghẽn (Non-blocking):** `TargetRepositoryImpl` lưu trữ toàn bộ trạng thái đích vào `ConcurrentHashMap` in-memory. Các hàm `isLocked()` và `getTarget()` đọc trực tiếp từ bộ nhớ Ram với độ trễ < 1ms, hoàn toàn không gọi `runBlocking()` hay disk I/O trên luồng tiếp nhận sự kiện Accessibility.
- **Khả năng chịu lỗi định dạng (Malformed Data Fallback):** Khi tệp preferences JSON bị lỗi cú pháp hoặc thiếu trường, parser tự động bắt `Throwable` và trả về danh sách rỗng an toàn thay vì gây crash ứng dụng (`testMalformedJsonFallback` PASS).
- **Đồng bộ hóa tức thì:** Mọi thay đổi qua `MainActivity` (thêm, xóa, cập nhật lịch, cập nhật giới hạn) đều cập nhật cache in-memory ngay lập tức và phát tín hiệu Flow đến các watcher.

---

## 9. KẾT QUẢ KIỂM THỬ ÁP LỰC (Stress / Soak Test Counts)

Tại file test `ProductionStressAndSoakTest.kt`:

| STT | Bài kiểm thử Áp lực (Stress Test) | Số chu kỳ thực thi | Kết quả | Ghi chú |
| :---: | :--- | :---: | :---: | :--- |
| **01** | Rapid Foreground Transitions | **100 chu kỳ** | **PASS** | Đổi qua lại target/free app liên tục, 0 launch sai |
| **02** | Watcher Start / Stop Cycles | **100 chu kỳ** | **PASS** | Kiểm tra tính idempotent start/stop liên tiếp |
| **03** | Service Lifecycle Cycles | **100 chu kỳ** | **PASS** | onCreate -> connect -> destroy lặp lại 100 lần |
| **04** | Rapid Policy Updates | **100 chu kỳ** | **PASS** | Cập nhật chính sách schedule 100 lần, deadline cập nhật chuẩn |
| **05** | Stale Callback Attempts | **100 chu kỳ** | **PASS** | Cố tình gọi 100 callback thế hệ cũ, 100% bị từ chối |
| **06** | Shield Add/Remove Cleanup Cycles | **100 chu kỳ** | **PASS** | 100 chu kỳ add/remove/cleanup và 100 chu kỳ chịu lỗi ngoại lệ |

---

## 10. TỔNG HỢP SỐ LƯỢNG UNIT TESTS (Complete Unit-Test Count)

| Test Suite File | Số lượng Tests | Trạng thái |
| :--- | :---: | :---: |
| `BlockingShieldOverlayTest.kt` | 8 | PASS |
| `ClockAndScheduleResilienceTest.kt` | 18 | PASS |
| `ConcurrencyAndLifecycleHardeningTest.kt` *(NEW - Phase 11-A)* | **21** | **PASS** |
| `PolicyEngineTest.kt` | 8 | PASS |
| `PolicyPrecedenceAndConflictTest.kt` | 20 | PASS |
| `ProductionStressAndSoakTest.kt` *(NEW - Phase 11-A)* | **6** | **PASS** |
| `ScheduleEvaluatorTest.kt` | 30 | PASS |
| `ScheduleWatcherTest.kt` | 26 | PASS |
| `TargetRepositoryTest.kt` | 11 | PASS |
| `MainScreenViewModelTest.kt` | 2 | PASS |
| `UsageLimitWatcherTest.kt` | 16 | PASS |
| `UsageTrackerTest.kt` | 11 | PASS |
| **TỔNG CỘNG** | **177 tests** | **100% PASS** |

*(Tăng từ 150 tests ở Phase 10-C lên 177 tests ở Phase 11-A, 0 failure, 0 error, 0 skipped).*

---

## 11. KẾT QUẢ KIỂM THỬ TRÊN THIẾT BỊ THỰC (Real-Device Scenarios)

Thiết bị: **vivo iQOO Neo 10 (Android 15 / OriginOS 5)**  
Thực thi qua kịch bản tự động `scratch/run_phase11a_regressions.ps1`:

| ID | Kịch bản kiểm thử | Kỳ vọng kiểm tra | Kết quả thực tế |
| :---: | :--- | :--- | :---: |
| **11A-01** | Normal Chrome lock | Mở Chrome khi đã bật khóa -> LockScreen hiện, Shield che phủ | **PASS** |
| **11A-02** | Calculator 24/7 lock | Khóa Calculator 24/7 -> Khóa tức thì (< 1ms quyết định) | **PASS** |
| **11A-03** | Repeated service reconnect | Bật/tắt service 3 lần liên tiếp -> Service phục hồi, tiếp tục khóa | **PASS** |
| **11A-04** | Rapid foreground switching | Chuyển qua lại Calculator và Home 5 lần -> Không duplicate lock | **PASS** |
| **11A-05** | Screen OFF/ON during pending deadline | Tắt/bật màn hình khi có deadline -> Không blind lock, khóa khi có event | **PASS** |
| **11A-06** | Policy update during pending deadline | Đổi chính sách hủy deadline trước khi chạm mốc -> Ứng dụng không bị khóa | **PASS** |
| **11A-07** | Schedule + Daily Limit collision | Cả Schedule và Limit cùng chạm mốc -> Đúng 1 lock session duy nhất | **PASS** |
| **11A-08** | Target removal during pending deadline | Xóa target khỏi repo trước mốc giờ -> Không còn bị khóa khi tới giờ | **PASS** |
| **11A-09** | LockScreen repeated launch attempt | Cố tình launch khi LockScreen đang mở -> Rule A bỏ qua, không duplicate | **PASS** |
| **11A-10** | Shield cleanup after normal exit | Đóng LockScreen về Home -> Shield được thu hồi hoàn toàn khỏi WindowManager | **PASS** |
| **11A-11** | Shield safety fuse / abnormal cleanup | Kiểm tra cầu chì 2000ms và thu hồi bất thường | **PASS** |
| **11A-12** | Process / Service recovery | Force-stop tiến trình app và mở lại -> Nạp dữ liệu DataStore và khóa chuẩn | **PASS** |

---

## 12. CÁC HẠN CHẾ & LƯU Ý KỸ THUẬT (Limitations & Observations)

1. **Tuyên bố rò rỉ bộ nhớ (Memory Leak Disclaimer):** Mặc dù tất cả các View, Handler callback, Runnable và Receiver đều đã được giải phóng có chủ đích trong code và kiểm thử 100 chu kỳ cân bằng, chúng tôi **không tuyên bố "Zero Memory Leaks 100%"** vì chưa chạy qua công cụ profiling bộ nhớ chuyên sâu (như Android Studio Memory Profiler hoặc LeakCanary) trong môi trường tải nặng 24/7.
2. **Khả năng miễn nhiễm Race Condition:** Không tuyên bố "100% race immunity" trên mọi biến thể phần cứng Android OEM; hệ thống được chứng minh bảo toàn các bất biến (invariants) đã được đặt ra trong phạm vi thiết kế kiến trúc và thực nghiệm trên thiết bị vivo iQOO Neo 10 / Android 15.
3. **Đặc thù OriginOS 5:** Hệ thống ghi log hệ thống của vivo với tần suất cực cao (CameraView, GMS, vivo frameworkui), do đó khi kiểm tra logcat cần lọc trực tiếp theo thẻ `-s AppDetectorService:* BlockingShieldOverlay:*` để tránh bị trôi buffer log.

---

## 13. TÓM TẮT GIT DIFF (Git Diff Summary)

- `BlockingShieldOverlay.kt`: Thêm đồng bộ hóa `lock`, bọc `synchronized`, bảo vệ `Throwable` khi thao tác WindowManager, hủy timeout runnable khi ẩn.
- `LockScreenActivity.kt`: Lưu và phục hồi `currentSessionId` qua `onSaveInstanceState`.
- `ScheduleWatcher.kt` & `UsageLimitWatcher.kt`: Idempotent `stop()`, guard thế hệ `currentGeneration`.
- `AppDetectorAccessibilityService.kt`: Bổ sung kiểm tra `isRunning` trước khi xử lý deadline callback.
- `ConcurrencyAndLifecycleHardeningTest.kt`: 21 test cases toàn diện cho Race matrix và Lifecycle.
- `ProductionStressAndSoakTest.kt`: 6 test cases áp lực cao (>= 100 chu kỳ, 0 sleep).
- `run_phase11a_regressions.ps1`: 12 kịch bản tự động trên vivo iQOO Neo 10.

---

## 14. KẾT LUẬN & ĐÁNH GIÁ NGHIỆM THU

Phase 11-A đã hoàn thành toàn bộ mục tiêu đề ra:
- **Frozen Core giữ nguyên 100%**.
- **Không sinh thêm State Machine hay Policy Engine**.
- **177/177 Unit Tests PASS (100%)**.
- **6/6 Stress Tests (100 chu kỳ mỗi bài) PASS (100%)**.
- **12/12 Kịch bản kiểm thử trên thiết bị vivo iQOO Neo 10 PASS (100%)**.

**PHASE 11-A — PRODUCTION HARDENING: CHÍNH THỨC HOÀN THÀNH VÀ SẴN SÀNG ĐÓNG GÓI.**
