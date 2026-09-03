# BÁO CÁO KỸ THUẬT PHASE 09-A — REAL-TIME DAILY LIMIT ENFORCEMENT

**Dự án**: `self-discipline-poc-01`  
**Package**: `com.example.selfdisciplinepoc01`  
**Thiết bị thử nghiệm thực tế**: `vivo iQOO Neo 10` (Android 15 / OriginOS 5, Chip Snapdragon 8 Gen 3, Màn hình 1260 x 2800)  
**Tiêu chuẩn chất lượng**: BẢO TOÀN TUYỆT ĐỐI NGUYÊN TẮC FROZEN CORE (PHASE 05–08)  

---

## 1. Mục Tiêu Chính (Objective)

Khắc phục triệt để hạn chế đã biết từ Phase 08:
> Khi một ứng dụng mục tiêu được phép mở (`ALLOW`) và người dùng giữ nguyên một màn hình tĩnh mà không phát sinh bất kỳ sự kiện cửa sổ mới nào (`TYPE_WINDOW_STATE_CHANGED`), ứng dụng vẫn phải được kích hoạt khóa (`LOCK`) chính xác tại thời điểm chạm ngưỡng hạn mức hàng ngày (Daily Usage Limit).

**Yêu cầu kỹ thuật cốt lõi**:
- **Cơ chế theo mốc thời gian duy nhất (Deadline-driven)**:
  - **KHÔNG polling liên tục**.
  - **KHÔNG vòng lặp `while(true)`**.
  - **KHÔNG `Thread.sleep()`**.
  - **KHÔNG timer loop 1 giây**.
- **Tính toán mốc thời gian còn lại (remaining time)** dựa trên đồng hồ đơn điệu `elapsedRealtimeMillis` và lên lịch **duy nhất 1 callback** chính xác tại deadline.
- **Bảo toàn kiến trúc Frozen Core**:
  - Không thêm bất kỳ State mới nào vào State Machine.
  - Khởi tạo điểm kích hoạt khóa dùng chung (`launchLockSession`), duy trì thứ tự `[DECISION: LAUNCH] -> blockingShieldOverlay.show() -> startActivity()`.
  - Không tạo đường dẫn phóng Activity thứ hai.

---

## 2. Khảo Sát Kiến Trúc Hiện Hữu Trước Khi Sửa Đổi (Inspected Architecture)

Trước khi chỉnh sửa:
- `AppDetectorAccessibilityService` chỉ kích hoạt thẩm định `PolicyEngine.evaluate(packageName)` khi nhận sự kiện `AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED`.
- Nếu người dùng không chạm màn hình hoặc không chuyển Activity, không có sự kiện Accessibility nào được gửi từ Android Framework. Do đó, dù thời gian tích lũy vượt quá hạn mức, ứng dụng vẫn không bị khóa cho đến lần tương tác chuyển cửa sổ tiếp theo.
- Toàn bộ State Machine khóa (Phase 05–07-B) và bộ tính giờ sử dụng (Phase 08 `UsageTracker`) đã hoạt động ổn định và được bảo vệ nghiêm ngặt.

---

## 3. Danh Sách Tệp Đã Thay Đổi (Files Changed)

1. [NEW] [`app/src/main/java/com/example/selfdisciplinepoc01/usage/UsageLimitWatcher.kt`](file:///c:/Code/self-discipline-poc-01/app/src/main/java/com/example/selfdisciplinepoc01/usage/UsageLimitWatcher.kt):
   - Thành phần độc lập chịu trách nhiệm tính toán deadline và đặt lịch hẹn duy nhất.
   - Cơ chế bảo vệ token thế hệ callback (`currentGeneration: Long`) chống stale callback.
   - Trừu tượng hóa bộ đặt lịch `LimitScheduler` và triển khai `HandlerLimitScheduler(Looper.getMainLooper())`.
2. [MODIFY] [`app/src/main/java/com/example/selfdisciplinepoc01/target/model/LockedApp.kt`](file:///c:/Code/self-discipline-poc-01/app/src/main/java/com/example/selfdisciplinepoc01/target/model/LockedApp.kt):
   - Bổ sung trường `dailyLimitSeconds: Int? = null` và thuộc tính `limitMillis: Long` phục vụ kiểm thử đơn vị và kịch bản thực tế 10 giây mà vẫn bảo toàn tương thích ngược 100%.
3. [MODIFY] [`app/src/main/java/com/example/selfdisciplinepoc01/policy/PolicyEngine.kt`](file:///c:/Code/self-discipline-poc-01/app/src/main/java/com/example/selfdisciplinepoc01/policy/PolicyEngine.kt):
   - Hỗ trợ ngữ nghĩa hạn mức 0 phút (`dailyLimitMinutes >= 0`): Hạn mức bằng 0 phút được coi là chạm ngưỡng ngay lập tức $\implies$ trả về `LOCK`.
   - Sử dụng `limit.limitMillis` thống nhất.
4. [MODIFY] [`app/src/main/java/com/example/selfdisciplinepoc01/target/repository/TargetRepositoryImpl.kt`](file:///c:/Code/self-discipline-poc-01/app/src/main/java/com/example/selfdisciplinepoc01/target/repository/TargetRepositoryImpl.kt):
   - Cập nhật serialization/deserialization JSON cho `dailyLimitSeconds`.
5. [MODIFY] [`app/src/main/java/com/example/selfdisciplinepoc01/AppDetectorAccessibilityService.kt`](file:///c:/Code/self-discipline-poc-01/app/src/main/java/com/example/selfdisciplinepoc01/AppDetectorAccessibilityService.kt):
   - Tái cấu trúc logic khóa thành điểm kích hoạt dùng chung `launchLockSession(packageName, reason, now, t1)` bảo toàn 100% Frozen Core.
   - Tích hợp `UsageLimitWatcher` lắng nghe thay đổi foreground và kết nối callback deadline.
6. [MODIFY] [`app/src/main/java/com/example/selfdisciplinepoc01/MainActivity.kt`](file:///c:/Code/self-discipline-poc-01/app/src/main/java/com/example/selfdisciplinepoc01/MainActivity.kt):
   - Bổ sung xử lý `EXTRA_LIMIT_SECONDS` và thông báo `AppDetectorAccessibilityService.onPolicyUpdated(pkg)` khi cấu hình thay đổi.
7. [NEW] [`app/src/test/java/com/example/selfdisciplinepoc01/usage/UsageLimitWatcherTest.kt`](file:///c:/Code/self-discipline-poc-01/app/src/test/java/com/example/selfdisciplinepoc01/usage/UsageLimitWatcherTest.kt):
   - 15 unit tests chuyên biệt cho `UsageLimitWatcher`.
8. [NEW] [`scratch/run_phase09a_regressions.ps1`](file:///c:/Code/self-discipline-poc-01/scratch/run_phase09a_regressions.ps1):
   - Script tự động hóa 8 bài test thực tế Phase 09-A trên thiết bị vivo iQOO Neo 10.

---

## 4. Kiến Trúc Cuối Cùng (Final Architecture)

```
[AccessibilityEvent (TYPE_WINDOW_STATE_CHANGED)]
        │                                │
        ▼                                ▼
[Foreground Evaluation]        [UsageLimitWatcher]
        │                                │
        │               (Calculates remaining time & schedules
        │                EXACTLY ONE delayed callback at deadline)
        │                                │
        └─────────────────┬──────────────┘
                          │
                          ▼
                  [PolicyEngine.evaluate]
                          │
            ┌─────────────┴─────────────┐
            ▼                           ▼
        [ALLOW]                      [LOCK]
            │                           │
  • usageTracker.start()      • usageTracker.stop()
  • watcher.onForeground()    • watcher.clear()
  • blockingShield.hide()               │
  • Reset session                       ▼
                          [launchLockSession (SHARED)]
                          • Rule A: isLockScreenVisible
                          • Rule B: intra-session duplicate
                          • Rule C: COOLDOWN_MS = 1500L
                          • currentSessionId++
                                        │
                                        ▼
                          [BlockingShieldOverlay.show()]
                                        │
                                        ▼
                          [startActivity(LockScreenActivity)]
```

---

## 5. Thiết Kế `UsageLimitWatcher`

Thành phần `UsageLimitWatcher` độc lập hoàn toàn với vòng lặp sự kiện của Android, vận hành theo các ràng buộc:
1. **Chỉ theo dõi target hợp lệ đang hoạt động**:
   - Khi `packageName == null` hoặc không phải target bật hạn mức: Không đặt hẹn giờ, hủy mọi lịch trình trước đó.
2. **Cơ chế chống Stale Callback bằng Token Thế Hệ (`Generation Token`)**:
   - Biến `currentGeneration: Long` được tăng lên mỗi khi:
     - Foreground thay đổi (`onForegroundChanged`).
     - Chính sách thay đổi (`onPolicyUpdated`).
     - Tắt màn hình (`SCREEN_OFF`).
     - Service dừng (`stop`).
   - Mỗi callback được đặt lịch sẽ lưu lại `capturedGeneration` và `capturedPackage`.
   - Khi callback kích hoạt, điều kiện tiên quyết để xử lý là:
     $$capturedGeneration == currentGeneration \land capturedPackage == activePackage \land isRunning$$
   - Nếu điều kiện không thỏa mãn, callback lập tức bị hủy bỏ (loại bỏ hoàn toàn rủi ro phiên cũ ảnh hưởng phiên mới).
3. **Phòng chống Scheduler Jitter (Đua thời gian)**:
   - Khi callback kích hoạt, hệ thống **tuyệt đối không khóa mù quáng**.
   - Callback gọi `PolicyEngine.evaluate(packageName)`.
   - Nếu `PolicyEngine` trả về `LOCK` $\implies$ Chuyển tiếp vào `launchLockSession`.
   - Nếu `PolicyEngine` trả về `ALLOW` (do scheduler bắn sớm vài mili-giây) $\implies$ Tính toán lại thời gian còn lại và tiếp tục lên lịch cho phần thời gian dư, không khóa sai lệch.

---

## 6. Thiết Kế Bộ Hẹn Giờ (Scheduler Design)

- Sử dụng `LimitScheduler` interface kết hợp `Handler(Looper.getMainLooper())`.
- Callback thực thi hoàn toàn bất đồng bộ (non-blocking), không chứa bất kỳ thao tác I/O, sleep hay block luồng nào.
- Hỗ trợ thay thế bằng `TestLimitScheduler` trong unit test giúp kiểm thử tức thì không phụ thuộc vào thời gian thực.

---

## 7. Ranh Giới PolicyEngine (PolicyEngine Boundary)

- `PolicyEngine` duy trì vai trò thuần túy: Quyết định `LOCK` hay `ALLOW` dựa trên cấu hình target, schedule và daily limit.
- `PolicyEngine` hoàn toàn **không** biết về Handler, Timer, Thread, Service hay State Machine.
- Mọi điều phối thời gian được đảm nhiệm bởi `UsageLimitWatcher`.

---

## 8. Tích Hợp Điểm Khóa Dùng Chung (`launchLockSession`)

Hàm `launchLockSession(packageName, reason, now, t1)` trong `AppDetectorAccessibilityService` gom cụm duy nhất 1 đường dẫn khởi chạy khóa:
1. Kiểm tra Rule A: `isLockScreenVisible` $\to$ Bỏ qua nếu LockScreen đang mở.
2. Kiểm tra Rule B: Trùng lặp nội bộ trong cùng session $\to$ Bỏ qua.
3. Kiểm tra Rule C: `COOLDOWN_MS = 1500L` $\to$ Bỏ qua nếu chưa hết hạ nhiệt.
4. Cập nhật biến State Machine: `isChromeLockedForCurrentTransition = true`, `lastLockLaunchTimestamp = now`, `currentSessionId++`.
5. Gọi `blockingShieldOverlay?.show(t1, t2, launchSessionId, packageName)`.
6. Gửi `startActivity(LockScreenActivity, options)`.

Cả sự kiện Accessibility thông thường và callback deadline từ `UsageLimitWatcher` đều đi qua duy nhất điểm kiểm soát này.

---

## 9. Xử Lý Vòng Đời & Ngữ Nghĩa Sử Dụng (Lifecycle Handling)

- **Không tính giờ khi LockScreen hiển thị**: Ngay khi LockScreen xuất hiện (`onResume`), `usageTracker.stopSession()` và `usageLimitWatcher.onForegroundChanged(null)` được gọi. Thời gian LockScreen không bị tính vào target.
- **Tắt màn hình (`Screen OFF`)**: `screenReceiver` nhận `ACTION_SCREEN_OFF` lập tức tạm dừng phiên sử dụng và hủy lịch hẹn của watcher.
- **Bật màn hình (`Screen ON`)**: Không tự tiện mở lại watcher; watcher chỉ được kích hoạt lại khi có sự kiện Accessibility xác nhận target thực sự đang ở foreground.

---

## 10. Kết Quả Kiểm Thử Đơn Vị (Unit Test Results)

Toàn bộ **72/72 unit test** chạy thành công ($100\%$ PASS, $0$ lỗi, $0$ thất bại) qua lệnh `./gradlew.bat testDebugUnitTest`:

```
TEST-com.example.selfdisciplinepoc01.overlay.BlockingShieldOverlayTest.xml: tests=8, failures=0, errors=0
TEST-com.example.selfdisciplinepoc01.policy.PolicyEngineTest.xml:           tests=8, failures=0, errors=0
TEST-com.example.selfdisciplinepoc01.policy.ScheduleEvaluatorTest.xml:      tests=17, failures=0, errors=0
TEST-com.example.selfdisciplinepoc01.target.TargetRepositoryTest.xml:        tests=11, failures=0, errors=0
TEST-com.example.selfdisciplinepoc01.ui.main.MainScreenViewModelTest.xml:   tests=2, failures=0, errors=0
TEST-com.example.selfdisciplinepoc01.usage.UsageLimitWatcherTest.xml:       tests=15, failures=0, errors=0
TEST-com.example.selfdisciplinepoc01.usage.UsageTrackerTest.xml:             tests=11, failures=0, errors=0
Total: 72 tests PASSED in 4s.
```

### Chi tiết 15 bài kiểm tra của `UsageLimitWatcherTest`:
1. `test1_targetWithNoLimit_noCallbackScheduled`: Target không có limit $\to$ Không lên lịch.
2. `test2_limitDisabled_noCallbackScheduled`: Limit tắt $\to$ Không lên lịch.
3. `test3_usageBelowLimit_schedulesForRemainingTime`: Tính chính xác thời gian còn lại (1,200,000 ms).
4. `test4_exactDeadline_policyReturnsLock_triggersLockCallback`: Đến đúng deadline $\to$ Trả về LOCK và gọi callback.
5. `test5_staleCallback_foregroundChanged_ignored`: Foreground đổi trước khi callback bắn $\to$ Callback cũ bị bỏ qua.
6. `test6_callbackAfterStop_ignored`: Watcher đã stop $\to$ Callback bị bỏ qua.
7. `test7_jitterEarlyCallback_reschedulesInsteadOfBlindLock`: Bắn sớm do jitter $\to$ Tính lại và lên lịch tiếp, không khóa nhầm.
8. `test8_targetDisabledWhilePending_noLock`: Target bị disable trong khi chờ $\to$ ALLOW, không khóa.
9. `test9_scheduleAndLimitInteraction`: Phối hợp chính xác với Schedule.
10. `test10_duplicateOnForegroundChanged_doesNotCreateDuplicateCallbacks`: Gọi trùng lặp không sinh timer đúp.
11. `test11_screenOff_cancelsWatcher`: Tắt màn hình hủy watcher.
12. `test12_screenOn_doesNotBlindlyRestart`: Bật màn hình không tự ý chạy lại trước sự kiện foreground.
13. `test13_policyUpdated_invalidatesAndReschedules`: Cập nhật policy tăng generation và đặt lịch mới.
14. `test14_wallClockChange_doesNotCorruptDeadline`: Đổi giờ hệ thống không làm sai lệch deadline đo bằng monotonic time.
15. `test15_zeroMinuteLimit_locksImmediately`: Hạn mức 0 phút khóa tức thì.

---

## 11. Kết Quả Kiểm Thử Thực Tế Trên Thiết Bị (Real-Device Test Results)

Thực thi trên thiết bị **vivo iQOO Neo 10** (Android 15 / OriginOS 5):
- Tổng số kịch bản kiểm thử lũy kế: **45 kịch bản**
- Số kịch bản đạt: **45/45 (100% PASS)**
- Số kịch bản thất bại: **0**

---

## 12. Kết Quả Kiểm Thử Hồi Quy Phase 07-B (22 Scenarios)

Thực thi file [`scratch/run_22_regressions.ps1`](file:///c:/Code/self-discipline-poc-01/scratch/run_22_regressions.ps1):
- `1_Chrome_First_Launch`: **PASS**
- `2_Exit_Home`: **PASS**
- `3_Rapid_Reopen_Less_1s`: **PASS**
- `4_Back_Gesture`: **PASS**
- `5_Internal_Events_No_Duplicate`: **PASS**
- `6_Stale_Callback_Ignored`: **PASS**
- `7_Calculator_Target_Lock`: **PASS**
- `8_Remove_Calculator_Normal`: **PASS**
- `9_Disable_Chrome_Normal`: **PASS**
- `10_Enable_Chrome_Lock`: **PASS**
- `11_Process_Restart_Persist`: **PASS**
- `12_Pm_Clear_Default_Chrome`: **PASS**
- `13_Chrome_Repeated_20_Cycles`: **PASS**
- `14_Calculator_Repeated_20_Cycles`: **PASS**
- `15_Screen_Off_On_Target`: **PASS**
- `16_Rapid_Home_Target`: **PASS**
- `17_Rapid_Target_Home_Target`: **PASS**
- `18_Service_Interruption`: **PASS**
- `19_Service_Rebind`: **PASS**
- `20_Launch_Failure_Safety_Timeout`: **PASS**
- `21_Target_Crash_During_Handoff`: **PASS**
- `22_Rapid_Repeated_Target_Launches`: **PASS**

👉 **Kết quả Phase 07-B: 22/22 PASSED (100%)**

---

## 13. Kết Quả Kiểm Thử Hồi Quy Phase 08 (15 Scenarios)

Thực thi file [`scratch/run_phase08_regressions.ps1`](file:///c:/Code/self-discipline-poc-01/scratch/run_phase08_regressions.ps1):
- `1_Active_Schedule_Locks`: **PASS**
- `2_Inactive_Schedule_Allows`: **PASS**
- `3_Cross_Midnight_Schedule`: **PASS**
- `4_Limit_Below_Threshold_Allows`: **PASS**
- `5_Exact_Limit_Locks`: **PASS**
- `6_Usage_Accumulation`: **PASS**
- `7_Leaving_Target_Stops_Usage`: **PASS**
- `8_Screen_OFF_Pauses_Usage`: **PASS**
- `9_Screen_ON_Resume_Logic`: **PASS**
- `10_Process_Restart_Preserves_Data`: **PASS**
- `11_Accessibility_Service_Restart`: **PASS**
- `12_Rapid_Transitions`: **PASS**
- `13_Schedule_Limit_Combination`: **PASS**
- `14_LockScreen_Time_Not_Counted`: **PASS**
- `15_Frozen_Core_Regression`: **PASS**

👉 **Kết quả Phase 08: 15/15 PASSED (100%)**

---

## 14. Kết Quả Kiểm Thử Thực Tế Phase 09-A (8 Scenarios)

Thực thi file [`scratch/run_phase09a_regressions.ps1`](file:///c:/Code/self-discipline-poc-01/scratch/run_phase09a_regressions.ps1):

| ID | Tên Kịch Bản | Tiêu Chí Kiểm Tra (Expected Focus / Logcat) | Kết Quả Thực Tế | Trạng Thái |
| :---: | :--- | :--- | :--- | :---: |
| **09A-01** | Deadline Lock Without New Event | Mở target, để nguyên 10.5s không chạm $\to$ Khóa bằng `LockScreenActivity` | `Window{LockScreenActivity}` (Watcher Logged: True) | **PASS** |
| **09A-02** | Leave Target Before Deadline | Thoát target ở giây thứ 4 $\to$ Màn hình Home không bị khóa nhầm | `Window{Launcher}` (Không xuất hiện LockScreen) | **PASS** |
| **09A-03** | Reach Exact Threshold | Mở lại target để đạt đủ 10s $\to$ Khóa bằng `LockScreenActivity` | `Window{LockScreenActivity}` | **PASS** |
| **09A-04** | Screen OFF Pauses Deadline | Tắt màn hình ở giây thứ 4 trong 8s $\to$ Bật lên không bị khóa sớm | `Window{Calculator}` (Thời gian tắt màn hình không bị tính) | **PASS** |
| **09A-05** | Screen ON Awaits Foreground | Bật màn hình lại $\to$ Không đếm đúp mù quáng | Logcat `Screen ON: True, Blind Start: False` | **PASS** |
| **09A-06** | Service Lifecycle Stale Guard | `force-stop` và khởi động lại service $\to$ Không có callback rác | `Window{Launcher}` | **PASS** |
| **09A-07** | Schedule + Daily Limit Coexistence | Schedule đang active $\to$ Khóa tức thì | `Window{LockScreenActivity}` | **PASS** |
| **09A-08** | Rapid Foreground Transitions | 5 chu kỳ mở/đóng liên tục $\to$ Không crash, không treo overlay | `Window{LockScreenActivity}` | **PASS** |

👉 **Kết quả Phase 09-A: 8/8 PASSED (100%)**

**Tổng kết 3 bộ kiểm thử thực tế trên thiết bị**: **45/45 kịch bản PASSED ($100\%$)**.

---

## 15. Xác Nhận Bảo Toàn Frozen Core (Frozen Core Verification)

Rà soát chi tiết toàn bộ mã nguồn `AppDetectorAccessibilityService.kt`:
- `currentSessionId`: Duy trì cơ chế chỉ tăng lên khi có quyết định khóa hợp lệ (`launchLockSession`).
- `lastForegroundPackage`: Cập nhật theo đúng luồng chuyển cảnh.
- `isLockScreenVisible`: Đồng bộ chính xác theo vòng đời `LockScreenActivity`.
- `isChromeLockedForCurrentTransition`: Giữ nguyên chức năng triệt tiêu duplicate event.
- `COOLDOWN_MS = 1500L`: Giữ nguyên hằng số thời gian hạ nhiệt.
- `Stale Session Guard`: Giữ nguyên việc kiểm tra `sessionId < currentSessionId`.
- `BlockingShieldOverlay`: Duy trì hiển thị tại `[DECISION: LAUNCH]` và ẩn khi `LockScreenActivity` hiển thị hoặc thoát.
- **Không có bất kỳ State nào được thêm vào State Machine**.

---

## 16. Các Giới Hạn Đã Biết (Known Limitations)

1. **Dung sai lập lịch của hệ điều hành (Device Scheduling Tolerance)**: `Handler.postDelayed()` trên Android chịu dung sai lập lịch nhẹ từ kernel (thường trong khoảng $10 \to 50$ ms). Cơ chế Jitter Protection trong `UsageLimitWatcher` đã tính toán bù trừ hoàn hảo dung sai này.
2. **Hạn mức tính theo lịch trình (Schedule Transition)**: Phase 09-A tập trung độc lập vào Daily Usage Limit. Việc kích hoạt khóa khi vừa bước vào giờ Schedule vẫn do các sự kiện Accessibility đảm nhiệm.

---

## 17. Đề Xuất Cho Giai Đoạn Tiếp Theo (Phase 10)

1. **Schedule Real-Time Watcher (Phase 10-A)**: Áp dụng kiến trúc deadline-driven tương tự cho thời điểm bắt đầu lịch trình (`schedule.startHour:startMinute`) để tự động khóa ứng dụng khi đang dùng dở dở mà đồng hồ vừa điểm giờ cấm.
2. **Cảnh báo trước khi khóa (Pre-lock Head-Up Notification)**: Đặt callback phụ trước deadline 60 giây để phát thông báo cảnh báo người dùng lưu công việc trước khi màn hình khóa xuất hiện.
3. **Widget đếm ngược thời gian (Floating / Status Bar Indicator)**: Hiển thị thời gian còn lại trực tiếp trên giao diện để tăng tính kỷ luật tự giác.

---
*Báo cáo được lập tự động dựa trên kết quả kiểm thử thực tế độc lập trên mã nguồn và thiết bị vivo iQOO Neo 10.*
