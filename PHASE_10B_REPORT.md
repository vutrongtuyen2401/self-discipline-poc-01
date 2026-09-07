# BÁO CÁO KẾT QUẢ TRIỂN KHAI PHASE 10-B
## Xử lý Xung đột Chính sách & Củng cố Quyền ưu tiên (Policy Conflict & Precedence Hardening)

---

### 1. Mục tiêu và Nguyên tắc Cốt lõi (Phase 10-B)

Phase 10-B tập trung vào việc củng cố sự tương tác xác định (deterministic interaction), giải quyết xung đột chính sách và đảm bảo tính sở hữu nguyên nhân khóa (`LockReason ownership`) giữa chính sách Khung giờ (`Schedule`) và Giới hạn sử dụng theo ngày (`Daily Limit`) mà **không giới thiệu thêm bất kỳ State Machine mới nào** và **giữ nguyên vẹn 100% ngữ nghĩa Frozen Core (Phases 05–10-A)**.

#### Quy tắc Quyền ưu tiên Hiệu lực (Effective Policy Precedence):
1. **Target bị vô hiệu hóa (`app.enabled == false`)** $\to$ `ALLOW` (bất kể khung giờ hay thời gian đã dùng).
2. **Khung giờ khóa đang kích hoạt (`hasSchedule && isWithinSchedule`)** $\to$ Hạn chế (`LOCK`).
3. **Giới hạn ngày đã cạn kiệt (`hasLimit && todayUsage >= limitMillis`)** $\to$ Hạn chế (`LOCK`).
4. **Không có khung giờ & không có giới hạn (`!hasSchedule && !hasLimit`)** $\to$ Mặc định khóa 24/7 (`LOCK`).
5. **Trường hợp còn lại** $\to$ `ALLOW`.

#### Nguyên tắc Sở hữu Nguyên nhân Khóa (Reason Ownership):
- `ScheduleWatcher` chỉ phát tín hiệu `SCHEDULE_DEADLINE` khi điều kiện khung giờ thực sự đang kích hoạt (`decision == LOCK && isScheduleActive`).
- `UsageLimitWatcher` chỉ phát tín hiệu `DAILY_LIMIT` khi điều kiện giới hạn ngày thực sự đã cạn kiệt (`decision == LOCK && isLimitExhausted`).
- **Không tự ý gán nhãn lại nguyên nhân** chỉ vì `PolicyEngine` trả về `LOCK`.
- Khi nhiều nguyên nhân hạn chế xuất hiện đồng thời:
  - Chỉ duy nhất **1 LockSession** được tạo ra.
  - Bảo lưu chính xác nguyên nhân của trigger đến trước.
  - Tuyệt đối không tạo phiên khóa trùng lặp (`duplicate LockSession`).
  - Các chốt chặn hiện hữu của `launchLockSession()` (Rule A, Rule B, Rule C) giữ quyền thẩm quyền tối cao.

---

### 2. Các điểm củng cố kỹ thuật

1. **`UsageLimitWatcher.kt` (Bảo lưu quyền sở hữu nguyên nhân):**
   - Tại `handleDeadlineFired`, trước khi phát sinh `onLimitReached`, watcher kiểm tra:
     ```kotlin
     val isLimitExhausted = limit != null && limit.enabled && (usageTracker.getTodayUsage(capturedPackage) >= limit.limitMillis)
     ```
   - Chỉ khi `decision == PolicyDecision.LOCK && isLimitExhausted` thì mới phát tín hiệu `onLimitReached(capturedPackage)`.
   - Nếu `PolicyEngine` trả về `LOCK` nhưng do khung giờ đang khóa (giới hạn ngày chưa hết), watcher không gán nhãn nhầm thành `DAILY_LIMIT`.

2. **Chống trùng lặp phiên khóa khi kích hoạt đồng thời (Simultaneous Trigger Protection):**
   - Cả `ScheduleWatcher` và `UsageLimitWatcher` đều gọi vào điểm vào phiên khóa dùng chung `launchLockSession()`.
   - Rule B (`!isNewTransition && isChromeLockedForCurrentTransition`) và Rule A (`isLockScreenVisible`) lập tức chặn và bỏ qua trigger thứ hai nếu trigger thứ nhất đã kích hoạt phiên khóa trong cùng quá trình chuyển đổi.
   - Nguyên nhân của trigger đầu tiên được giữ nguyên tuyệt đối trong `LockSession`.

3. **Chuyển giao trạng thái khi Khung giờ kết thúc (Schedule Ends while Limit Exhausted):**
   - Khi khung giờ kết thúc nhưng giới hạn ngày đã hết, `PolicyEngine` tiếp tục trả về `LOCK`.
   - Ứng dụng mục tiêu tiếp tục bị khóa, **tuyệt đối không mở khóa nhầm** chỉ vì khung giờ vừa kết thúc.

---

### 3. Kết quả Kiểm thử Đơn vị (Unit Tests)

Bộ kiểm thử đơn vị bao gồm toàn bộ 20 kịch bản quy định của Phase 10-B tại `PolicyPrecedenceAndConflictTest.kt`, nâng tổng số test của dự án lên **132 unit tests**, đạt tỷ lệ **100% PASS** (0 failures, 0 errors).

| STT | Suite Kiểm thử | Số Tests | Thất bại | Lỗi | Thời gian thực thi |
|:---:|:---|:---:|:---:|:---:|:---:|
| 1 | `PolicyPrecedenceAndConflictTest` [NEW Phase 10-B] | 20 | 0 | 0 | 0.075s |
| 2 | `ScheduleWatcherTest` | 26 | 0 | 0 | 0.017s |
| 3 | `ScheduleEvaluatorTest` | 30 | 0 | 0 | 0.007s |
| 4 | `UsageLimitWatcherTest` | 16 | 0 | 0 | 0.012s |
| 5 | `UsageTrackerTest` | 11 | 0 | 0 | 0.007s |
| 6 | `TargetRepositoryTest` | 11 | 0 | 0 | 0.382s |
| 7 | `BlockingShieldOverlayTest` | 8 | 0 | 0 | 0.016s |
| 8 | `PolicyEngineTest` | 8 | 0 | 0 | 0.003s |
| 9 | `MainScreenViewModelTest` | 2 | 0 | 0 | 0.006s |
| **Tổng cộng** | **9 Test Suites** | **132** | **0** | **0** | **~0.53s** |

#### Danh mục 20 Kịch bản Unit Test Phase 10-B đã kiểm chứng:
1. `test01_scheduleOnly_locksDueToSchedule`: Khóa thuần do khung giờ.
2. `test02_limitOnly_locksDueToLimit`: Khóa thuần do giới hạn ngày.
3. `test03_scheduleInactive_limitExhausted_reasonIsDailyLimit`: Khung giờ không kích hoạt, giới hạn cạn kiệt $\to$ `DAILY_LIMIT`.
4. `test04_scheduleActive_limitExhausted_firstTriggerOwnsReason`: Cả hai cùng hạn chế $\to$ trigger đầu tiên giữ quyền sở hữu nguyên nhân.
5. `test05_scheduleEnds_limitRemainsExhausted_remainsLocked`: Khung giờ kết thúc nhưng giới hạn vẫn cạn kiệt $\to$ duy trì `LOCK`.
6. `test06_limitExhausted_whileScheduleInactive_emitsDailyLimit`: Giới hạn cạn kiệt ngoài khung giờ $\to$ `DAILY_LIMIT`.
7. `test07_limitExhausted_whileScheduleActive_exactlyOneLockSession`: Giới hạn cạn kiệt trong khung giờ $\to$ đúng 1 LockSession.
8. `test08_simultaneousScheduleAndLimitTrigger_exactlyOneLaunchLockSession`: Hai callback kích hoạt đồng thời $\to$ đúng 1 lần `launchLockSession()`.
9. `test09_scheduleUpdate_activeToInactive_whileLimitExhausted_remainsLocked`: Cập nhật khung giờ hết hiệu lực trong khi giới hạn vẫn cạn $\to$ duy trì `LOCK`.
10. `test10_limitUpdate_exhaustedToAvailable_whileScheduleActive_remainsLocked`: Cập nhật giới hạn tăng lên trong khi khung giờ đang chạy $\to$ duy trì `LOCK`.
11. `test11_foregroundSwitchDuringRace_cancelsBothWatchers`: Đổi ứng dụng trong lúc chờ deadline $\to$ hủy timer cả 2 watcher, không khóa màn hình Home.
12. `test12_screenOffDuringRace_pausesBothWatchers`: Tắt màn hình $\to$ tạm dừng cả 2 watcher, không khóa mù khi bật lại màn hình.
13. `test13_serviceStopDuringRace_stopsBothWatchers`: Dừng service $\to$ dừng cả 2 watcher, loại bỏ callback muộn.
14. `test14_staleScheduleWatcherCallback_ignored`: Callback ScheduleWatcher cũ bị loại bỏ bởi token thế hệ.
15. `test15_staleUsageLimitWatcherCallback_ignored`: Callback UsageLimitWatcher cũ bị loại bỏ bởi token thế hệ.
16. `test16_duplicateLaunchProtection`: Kiểm chứng Rule A & Rule B ngăn chặn khởi chạy màn hình khóa trùng lặp.
17. `test17_lockReasonOwnership_scheduleCannotEmitDailyLimit_andViceVersa`: ScheduleWatcher không phát `DAILY_LIMIT` và ngược lại.
18. `test18_targetDisabled_alwaysAllow`: Target bị disable luôn là `ALLOW`.
19. `test19_scheduleDisabled_onlyLimitEvaluated`: Schedule bị disable chỉ đánh giá giới hạn ngày.
20. `test20_dailyLimitDisabled_onlyScheduleEvaluated`: Daily limit bị disable chỉ đánh giá khung giờ.

---

### 4. Kết quả Kiểm thử Thiết bị thực (vivo iQOO Neo 10 / Android 15 / OriginOS 5)

Kịch bản hồi quy tự động trên thiết bị thực (`scratch/run_phase10b_regressions.ps1`) đã thực thi đầy đủ 12 kịch bản yêu cầu:

| Mã Kịch bản | Tên Kịch bản Kiểm thử | Trạng thái Màn hình / Focus | Hành vi Thực tế ghi nhận | Kết quả |
|:---:|:---|:---|:---|:---:|
| **10B-01** | Schedule-Only Lock | `LockScreenActivity` | Mở ứng dụng trước giờ $\to$ ALLOW; Đến mốc rollover phút $\to$ khóa với `SCHEDULE_DEADLINE` | **PASS** |
| **10B-02** | Daily-Limit-Only Lock | `LockScreenActivity` | Mở ứng dụng $\to$ ALLOW; Hết 10s $\to$ khóa thời gian thực với `DAILY_LIMIT` | **PASS** |
| **10B-03** | Schedule Inactive + Exhausted Limit | `LockScreenActivity` | Khung giờ ở tương lai, hết hạn mức 10s $\to$ khóa với `DAILY_LIMIT`, Schedule không phát nhầm | **PASS** |
| **10B-04** | Schedule Active + Exhausted Limit | `LockScreenActivity` | Cả hai cùng thỏa mãn $\to$ khóa ngay lập tức, đúng 1 phiên launch | **PASS** |
| **10B-05** | Boundaries Close Together | `LockScreenActivity` | Giới hạn 8s và khung giờ 15s kề nhau $\to$ giới hạn kích hoạt trước, khung giờ đến sau bị Rule B chặn trùng lặp, đúng 1 phiên | **PASS** |
| **10B-06** | Schedule Ends While Limit Exhausted | `LockScreenActivity` | Khung giờ kết thúc lúc :00, mở lại lúc :05 $\to$ tiếp tục bị khóa do giới hạn ngày đã cạn kiệt | **PASS** |
| **10B-07** | Policy Update Conflict | `LockScreenActivity` | Tắt khung giờ trong khi hạn mức đã hết $\to$ ứng dụng vẫn duy trì trạng thái khóa | **PASS** |
| **10B-08** | Foreground Switch During Deadline | `com.bbk.launcher2` (Home) | Rời ứng dụng về Home trước khi hết hạn $\to$ màn hình Home an toàn, không bị khóa đè | **PASS** |
| **10B-09** | Screen OFF/ON During Deadline | Screen Off/On logs | Tắt màn hình tạm dừng đếm $\to$ bật màn hình không khóa mù, chờ sự kiện cửa sổ mới | **PASS** |
| **10B-10** | Service Reconnect Near Deadline | `LockScreenActivity` | Dừng service và kết nối lại $\to$ watcher khởi động sạch sẽ, cơ chế 24/7 khôi phục tức thì | **PASS** |
| **10B-11** | Chrome Target Regression | `LockScreenActivity` | Chrome mở trước mốc $\to$ ALLOW; Đến mốc khung giờ $\to$ khóa tức thì với BlockingShieldOverlay | **PASS** |
| **10B-12** | Calculator Regression | `LockScreenActivity` | Calculator mặc định khóa 24/7 (Phase 06 baseline) hoạt động chính xác | **PASS** |

**Tổng kết thiết bị thực:** **12 / 12 KỊCH BẢN ĐẠT (100% PASS)**.

---

### 5. Số liệu Thời gian Quan sát (Observed Timing - Không cam kết cứng)

Dựa trên dữ liệu thực tế trích xuất từ logcat CSV trên thiết bị vivo iQOO Neo 10 (Snapdragon 8 Gen 3):
- **Độ trễ từ sự kiện đến lúc quyết định launch (`event_to_launch_ms`)**: Dao động từ `0.34ms` đến `1.01ms`.
- **Độ trễ gắn lớp chắn BlockingShieldOverlay (`t6 - t1`)**: Dao động từ `22.72ms` đến `35.12ms`.
- **Độ trễ hiển thị khung hình đầu tiên của LockScreenActivity (`event_to_firstFrame_ms`)**: Dao động từ `43.31ms` đến `125.43ms`.
- Toàn bộ các mốc thời gian đều phản ánh kết quả đo đạc thực nghiệm thực tế, tuân thủ nguyên tắc không cam kết thời gian giả định.

---

### 6. Xác nhận Tính Toàn vẹn của Frozen Core (Frozen Core Invariants)

1. **State Machine trong `AppDetectorAccessibilityService`**:
   - `lastForegroundPackage`, `lastLockLaunchTimestamp`, `isChromeLockedForCurrentTransition`, `isLockScreenVisible`, `currentSessionId` hoàn toàn được giữ nguyên không đổi về kiểu dữ liệu hay logic vận hành.
   - Hàm `launchLockSession` duy trì đầy đủ Rule A, Rule B và Rule C.
2. **Enum `LockReason`**:
   - Chỉ gồm đúng 3 giá trị: `ACCESSIBILITY_EVENT`, `DAILY_LIMIT`, `SCHEDULE_DEADLINE`. Tuyệt đối không thêm hay đổi tên enum khác.
3. **Cấu trúc Watcher độc lập**:
   - `ScheduleWatcher` và `UsageLimitWatcher` tồn tại độc lập, không bị gộp thành `CombinedPolicyWatcher`.
   - `PolicyEngine` giữ vững vai trò là cơ quan thẩm quyền duy nhất quyết định `LOCK` hoặc `ALLOW`.
