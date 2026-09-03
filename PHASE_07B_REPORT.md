# PHASE 07-B — BÁO CÁO TOÀN DIỆN: PREEMPTIVE ACCESSIBILITY SHIELD
**Dự án**: `self-discipline-poc-01`  
**Thiết bị thử nghiệm**: `vivo iQOO Neo 10` (Android 15, OriginOS 5, SoC Snapdragon 8 Gen 3, Màn hình 144Hz / 1260x2800)  
**Ngày thực hiện**: 03/09/2026  
**Trạng thái**: HOÀN THÀNH — NGHIỆM THU THỰC TẾ & BẢO LƯU ARCHITECTURE FREEZE  

---

## 1. MỤC TIÊU & TỔNG QUAN DỰ ÁN (EXECUTIVE SUMMARY & OBJECTIVE)

### 1.1 Bối cảnh
Ở Phase 07-A (Zero-Latency App Lock UX), chúng ta đã chứng minh:
- Tra cứu danh sách ứng dụng bị khóa trong bộ nhớ RAM (`TargetRepository`) diễn ra cực nhanh: $\approx 0.1 - 0.5$ ms.
- Thời gian từ `startActivity` đến khi `LockScreenActivity.onCreate` được gọi mất khoảng vài chục mili-giây ($\approx 25 - 35$ ms).
- Giao diện Jetpack Compose mất khoảng 1 frame ($\approx 7 - 12$ ms) để vẽ frame đầu tiên ($T_8$).
- Việc loại bỏ hiệu ứng chuyển cảnh của OriginOS (`FLAG_ACTIVITY_NO_ANIMATION`, `overrideActivityTransition`) đã cải thiện đáng kể cảm giác trễ.
- Đối với Google Chrome: Không để lộ bất kỳ nội dung nhạy cảm nào do Chrome mất nhiều thời gian khởi tạo WebEngine.
- **Vấn đề cốt lõi còn lại**: Đối với các ứng dụng siêu nhẹ, khởi động dạng warm-start như Máy tính (Calculator — `com.android.bbkcalculator`), giao diện của ứng dụng mục tiêu vẫn có thể nhấp nháy hiển thị trong một khoảnh khắc rất ngắn trước khi `LockScreenActivity` kịp hiển thị hoàn toàn.

### 1.2 Mục tiêu Phase 07-B
Khảo sát và hiện thực hóa giải pháp tấm khiên che phủ trước: **`BlockingShieldOverlay`**, sử dụng `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY`.
- **Mục đích**: Che phủ toàn bộ màn hình ngay khi State Machine đưa ra quyết định `[DECISION: LAUNCH]`, nhằm lấp đầy khoảng trống thời gian giữa lúc phát hiện sự kiện và lúc `LockScreenActivity` hoàn tất frame đầu tiên.
- **Nguyên tắc kỹ thuật trung thực**: `AccessibilityService` **KHÔNG THỂ** chặn đứng (intercept) Intent khởi chạy ứng dụng trước khi hệ điều hành Android xử lý Activity đích. Tấm khiên chỉ bảo vệ khoảng thời gian **SAU KHI** sự kiện `TYPE_WINDOW_STATE_CHANGED` được AccessibilityService tiếp nhận ($T_1$). Tuyệt đối không ngộ nhận đây là cơ chế "pre-launch interception" hay "can thiệp cấp kernel".

---

## 2. KIẾN TRÚC BLOCKING SHIELD OVERLAY (ARCHITECTURE)

Lớp `BlockingShieldOverlay` được thiết kế dưới dạng một component độc lập, do `AppDetectorAccessibilityService` trực tiếp quản trị:

```
┌─────────────────────────────────────────────────────────────┐
│              AppDetectorAccessibilityService                │
│                                                             │
│  AccessibilityEvent (T1)                                    │
│         │                                                   │
│         ▼                                                   │
│  State Machine Evaluation (T2)                              │
│         │                                                   │
│    [DECISION: LAUNCH]                                       │
│         ├───────────────────────────────┐                   │
│         ▼                               ▼                   │
│  BlockingShieldOverlay.show()    startActivity()            │
│  - WindowManager.addView() (T6)  - Intent sent to ATMS      │
│  - First Draw Pass (T7)                 │                   │
│  - Touch Absorbed (100%)                │                   │
│         │                               ▼                   │
│         │                     LockScreenActivity            │
│         │                     - onCreate()                  │
│         │                     - Compose first frame (T8)    │
│         │                     - onResume()                  │
│         ▼                               │                   │
│  Handoff: hide() ◄──────────────────────┘                   │
│  (Window removed safely)                                    │
└─────────────────────────────────────────────────────────────┘
```

### Các đặc tính kỹ thuật quan trọng của `BlockingShieldOverlay`:
1. **Window Type**: `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY`.
   - Cửa sổ lớp accessibility overlay có quyền hiển thị đè lên trên tất cả các cửa sổ thông thường (kể cả System dialogs) mà **không đòi hỏi quyền `SYSTEM_ALERT_WINDOW`** (người dùng không cần cấp thêm quyền "Draw over other apps").
2. **Window Flags**:
   - `FLAG_LAYOUT_IN_SCREEN`: Phủ tràn toàn bộ màn hình thực tế.
   - `FLAG_LAYOUT_NO_LIMITS`: Bỏ qua các giới hạn inset viền màn hình để che phủ 100% diện tích (kể cả vùng Status Bar và Navigation Bar).
   - `FLAG_NOT_FOCUSABLE`: Không cướp Input Focus của hệ thống bàn phím, tránh làm gián đoạn vòng đời Activity phía dưới.
3. **Độ đục và Màu sắc (Visual Sync)**:
   - `PixelFormat.OPAQUE`: Tối ưu hóa pipeline SurfaceFlinger, tránh blend alpha gây tốn tài nguyên GPU.
   - Nền màu trắng nguyên khối (`#FFFFFF`), đồng bộ chính xác tuyệt đối với màu nền của `LockScreenActivity`, tạo hiệu ứng thị giác liền mạch, không bị giật màu khi bàn giao giao diện.
4. **Hấp thụ thao tác cảm ứng (Touch Absorption)**:
   - `setOnTouchListener { _, _ -> true }` và `isClickable = true`: Hấp thụ 100% các cú chạm ngón tay của người dùng trong khoảnh khắc chuyển tiếp, triệt tiêu hoàn toàn nguy cơ người dùng click nhầm vào nội dung ứng dụng bị khóa bên dưới.
5. **Cầu chì an toàn (Safety Timeout Fuse)**:
   - Một bộ đếm thời gian an toàn 2000 ms (`mainHandler.postDelayed(timeoutRunnable, 2000L)`) luôn được kích hoạt khi `show()`. Nếu có bất kỳ sự cố bất thường nào khiến `LockScreenActivity` không hiển thị được, tấm khiên sẽ tự động gỡ bỏ sau 2 giây, ngăn ngừa triệt để lỗi "kẹt màn hình trắng" làm tê liệt điện thoại.
6. **Bảo đảm không crash (Robust Error Handling)**:
   - Mọi tương tác với `WindowManager` (`addView`, `removeViewImmediate`) đều nằm trong khối `try-catch` kiểm soát nghiêm ngặt các ngoại lệ `BadTokenException`, `IllegalArgumentException`, `IllegalStateException`, `RuntimeException`.

---

## 3. VÒNG ĐỜI & ĐIỀU PHỐI OVERLAY (OVERLAY LIFECYCLE & SAFE HANDOFF)

Tấm khiên là một hiệu ứng giao diện phụ thuộc (UI-protection side effect), **tuyệt đối không phải là một State mới** trong State Machine của POC 05.

### 3.1 Thời điểm Kích hoạt (`show`)
Chỉ được gọi ngay sau khi State Machine xác định đủ điều kiện khóa:
```kotlin
// AppDetectorAccessibilityService.kt
Log.i(TAG, "[DECISION: LAUNCH] ĐỦ ĐIỀU KIỆN LAUNCH...")
...
// Hiển thị Shield Overlay ngay lập tức để bảo vệ màn hình
blockingShieldOverlay?.show(t1, t2, launchSessionId, targetPackage)

// Gửi Intent khởi chạy LockScreenActivity
val options = ActivityOptions.makeCustomAnimation(applicationContext, 0, 0)
startActivity(lockIntent, options.toBundle())
```

### 3.2 Handoff An toàn (`hide`)
Tấm khiên được đảm bảo gỡ bỏ sạch sẽ trong tất cả các trường hợp:
1. **Handoff chuẩn xác**: Khi `LockScreenActivity` bước vào `onResume()` và đã hiển thị đầy đủ trên màn hình (`handleLockScreenResumed`).
2. **Người dùng thoát khóa**: Khi người dùng nhấn nút Home hoặc nút Exit (`handleLockScreenStopped`, `handleLockScreenExited`).
3. **Mở ứng dụng an toàn**: Khi cửa sổ chuyển sang một package không bị khóa (như Launcher, Settings).
4. **startActivity thất bại**: Nếu hàm `startActivity` ném ra ngoại lệ, `hide("start_activity_failed")` được gọi ngay lập tức.
5. **Dịch vụ ngắt kết nối / Hủy**: Khi `onInterrupt()` hoặc `onDestroy()` của `AppDetectorAccessibilityService` diễn ra, hàm `cleanup()` được gọi để dọn sạch tài nguyên.

---

## 4. PHƯƠNG PHÁP ĐO ĐẠC ĐỘ TRỄ (MEASUREMENT METHODOLOGY)

Hệ thống ghi nhận dấu thời gian bằng đồng hồ đơn điệu độ chính xác nano-giây (`SystemClock.elapsedRealtimeNanos()`):
- $T_1$: Thời điểm `AppDetectorAccessibilityService.onAccessibilityEvent` tiếp nhận sự kiện `TYPE_WINDOW_STATE_CHANGED`.
- $T_2$: Thời điểm State Machine thẩm định xong và đưa ra quyết định `[DECISION: LAUNCH]`.
- $T_6$: Thời điểm gọi `WindowManager.addView` yêu cầu hiển thị `BlockingShieldOverlay`.
- $T_7$: Thời điểm frame đầu tiên của `BlockingShieldOverlay` được vẽ lên màn hình (bắt bằng single-shot `ViewTreeObserver.addOnDrawListener`).
- $T_8$: Thời điểm frame đầu tiên của `LockScreenActivity` hoàn thành trên màn hình.

### Các đại lượng độ trễ then chốt:
1. **`EventToShieldReqMs`** ($T_6 - T_1$): Thời gian từ lúc nhận sự kiện đến khi yêu cầu mở Shield Overlay.
2. **`EventToShieldFirstFrameMs`** ($T_7 - T_1$): Tổng thời gian từ lúc nhận sự kiện đến khi Shield Overlay che kín màn hình.
3. **`ShieldReqToFirstFrameMs`** ($T_7 - T_6$): Thời gian SurfaceFlinger / WindowManager dựng frame của Shield Overlay.
4. **`EventToLockscreenFrameMs`** ($T_8 - T_1$): Thời gian từ lúc nhận sự kiện đến khi `LockScreenActivity` sẵn sàng hoàn toàn.
5. **`ShieldAdvantageMs`** ($T_8 - T_7$): Khoảng thời gian Shield Overlay xuất hiện **sớm hơn** so với `LockScreenActivity`.

---

## 5. KẾT QUẢ THỬ NGHIỆM TRÊN GOOGLE CHROME (20 CHU KỲ HỢP LỆ)

Dữ liệu thực nghiệm thu thập từ file `scratch/phase07b_chrome.csv` trên thiết bị `iQOO Neo 10`:

| Chỉ số độ trễ | Min | Trung bình (Avg) | Median (P50) | P90 | P99 / Max |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **EventToShieldReqMs ($T_1 \to T_6$)** | 0.32 ms | 0.55 ms | 0.46 ms | 0.96 ms | 1.18 ms |
| **EventToShieldFirstFrameMs ($T_1 \to T_7$)** | 23.35 ms | 29.97 ms | 27.43 ms | 35.13 ms | 85.30 ms |
| **ShieldReqToFirstFrameMs ($T_6 \to T_7$)** | 22.64 ms | 29.42 ms | 26.71 ms | 34.62 ms | 84.12 ms |
| **EventToLockscreenFrameMs ($T_1 \to T_8$)** | 42.24 ms | 52.06 ms | 50.00 ms | 69.65 ms | 108.80 ms |
| **Thời gian duy trì Shield (Shield Lifetime)** | 41.78 ms | 51.50 ms | 49.66 ms | 69.32 ms | 107.62 ms |
| **Tỷ lệ hiển thị LockScreen thành công** | **20/20 (100%)** | | | | |
| **Tỷ lệ trùng lặp Intent (Duplicate Launch)** | **0/20 (0%)** | | | | |

---

## 6. KẾT QUẢ THỬ NGHIỆM TRÊN MÁY TÍNH (CALCULATOR — 20 CHU KỲ HỢP LỆ)

Dữ liệu thực nghiệm thu thập từ file `scratch/phase07b_calculator.csv` trên thiết bị `iQOO Neo 10`:

| Chỉ số độ trễ | Min | Trung bình (Avg) | Median (P50) | P90 | P99 / Max |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **EventToShieldReqMs ($T_1 \to T_6$)** | 0.34 ms | 0.47 ms | 0.46 ms | 0.56 ms | 1.00 ms |
| **EventToShieldFirstFrameMs ($T_1 \to T_7$)** | 24.95 ms | 30.05 ms | 30.95 ms | 34.02 ms | 34.29 ms |
| **ShieldReqToFirstFrameMs ($T_6 \to T_7$)** | 24.55 ms | 29.58 ms | 30.55 ms | 33.54 ms | 33.81 ms |
| **EventToLockscreenFrameMs ($T_1 \to T_8$)** | 42.09 ms | 46.02 ms | 44.47 ms | 52.87 ms | 53.38 ms |
| **Thời gian duy trì Shield (Shield Lifetime)** | 41.72 ms | 45.56 ms | 44.07 ms | 52.39 ms | 52.89 ms |
| **Tỷ lệ hiển thị LockScreen thành công** | **20/20 (100%)** | | | | |
| **Tỷ lệ trùng lặp Intent (Duplicate Launch)** | **0/20 (0%)** | | | | |

---

## 7. SO SÁNH ĐỐI CHIẾU: BASELINE (PHASE 07-A) VS SHIELD (PHASE 07-B)

| Tiêu chí đánh giá | Phase 07-A (Baseline) | Phase 07-B (Blocking Shield Overlay) | Đánh giá cải thiện |
| :--- | :---: | :---: | :--- |
| **Thời điểm che phủ màn hình đầu tiên** | $T_8 \approx 45 - 55$ ms | $T_7 \approx 27 - 30$ ms | **Nhanh hơn $\approx 15 - 20$ ms (~1.5 đến 2 frames tại 120Hz)** |
| **Cơ chế che chắn** | Toàn bộ phụ thuộc vào `LockScreenActivity` | Che chắn sớm bằng cửa sổ `TYPE_ACCESSIBILITY_OVERLAY` | Phân tách hiển thị thị giác khỏi khởi tạo Activity |
| **Nguy cơ rò rỉ thao tác chạm (Touch Leaks)** | Người dùng có thể vô tình tap vào target trong ~45ms đầu | **0% rò rỉ** — Tấm khiên hấp thụ 100% touch events ngay từ frame $T_7$ | **Khắc phục hoàn toàn rủi ro thao tác người dùng** |
| **Độ ổn định của LockScreenActivity** | 100% | 100% | Giữ nguyên vẹn độ tin cậy của State Machine |
| **Tác động tới Session State Machine** | Không thay đổi | **Không thay đổi (Zero Impact)** | Tuân thủ tuyệt đối Frozen Core |

---

## 8. PHÂN TÍCH THỰC TẾ VỀ MỨC ĐỘ HIỂN THỊ CỦA MỤC TIÊU (VISUAL EXPOSURE REALITY)

Theo chỉ thị nghiêm ngặt của dự án, chúng ta sử dụng đúng thuật ngữ kỹ thuật khách quan: **"TARGET EXPOSURE REDUCED" (Mức độ lộ diện của ứng dụng mục tiêu đã được giảm thiểu đáng kể)**, tuyệt đối không gọi là "zero visual latency".

### 8.1 Đối với Google Chrome (`com.android.chrome`)
- **Kết quả**: **Target Exposure Eliminated (Hoàn toàn không lộ nội dung)**.
- **Lý do**: Thời gian để Chrome dựng frame đầu tiên dài hơn 150 ms (do tải engine Blink và khởi tạo tab). Tấm khiên che phủ ở mili-giây thứ ~27 và `LockScreenActivity` xuất hiện ở mili-giây thứ ~50, trước khi Chrome kịp render bất kỳ pixel nào.

### 8.2 Đối với Máy tính warm-start (`com.android.bbkcalculator`)
- **Kết quả**: **Target Exposure Reduced (Đã giảm thiểu nhưng vẫn còn nhấp nháy thoáng qua)**.
- **Phân tích cơ chế kiến trúc Android**:
  1. Khi người dùng bấm biểu tượng Calculator từ Launcher, hệ điều hành Android (`system_server` / `ActivityTaskManagerService`) lập tức đưa cửa sổ Calculator đã có sẵn trong RAM lên màn hình ($T_0$).
  2. Sự kiện `TYPE_WINDOW_STATE_CHANGED` chỉ được Android bắn ra **sau khi** Activity đích đã được đưa vào trạng thái hiển thị ($T_1$).
  3. Khoảng thời gian từ $T_0$ đến $T_1$ là độ trễ nội tại của IPC Binder trong Android OS.
  4. Do đó, dù `BlockingShieldOverlay` hiển thị cực nhanh chỉ trong **~27 - 30 ms** sau $T_1$, mắt người vẫn có thể nhận thấy một vệt nhấp nháy (glimpse) của Calculator ở giai đoạn $T_0 \to T_1$.
  5. **Kết luận kỹ thuật**: Không một cơ chế Accessibility Overlay nào có thể triệt tiêu được giai đoạn $T_0 \to T_1$ vì AccessibilityService là một cơ chế phản ứng theo sau (reactive), không phải cơ chế chặn trước (pre-emptive interception).

---

## 9. KẾT QUẢ HẤP THỤ THAO TÁC CẢM ỨNG & ĐIỀU HƯỚNG (TOUCH & NAVIGATION)

1. **Thao tác chạm màn hình (Touch Absorption)**:
   - Trong quá trình tấm khiên che phủ, toàn bộ cử chỉ nhấn, vuốt, chạm đa điểm đều bị `BlockingShieldOverlay` bắt trọn và trả về `true`.
   - Các nút bấm bên dưới của ứng dụng mục tiêu (ví dụ: các phím số máy tính, thanh địa chỉ Chrome) hoàn toàn không nhận được bất kỳ event cảm ứng nào.
2. **Thao tác điều hướng hệ thống (Navigation Safety)**:
   - Phím **Home**: Người dùng có thể nhấn Home bất cứ lúc nào, hệ điều hành lập tức đưa người dùng về Launcher và tấm khiên tự động biến mất an toàn.
   - Cử chỉ **Back**: Hoạt động bình thường.
   - **Recent Apps / Đa nhiệm**: Không bị ảnh hưởng.
   - Không xuất hiện bất kỳ hiện tượng "treo cảm ứng" hay "khóa nhầm màn hình".

---

## 10. KIỂM THỬ ĐƠN VỊ (UNIT TESTING VERIFICATION)

Toàn bộ 18 bài kiểm thử đơn vị độc lập đã vượt qua 100% (`BUILD SUCCESSFUL`):
- **10 bài kiểm thử State Machine & Target Management**:
  - `testChromeLaunch_NewTransition_LocksAndLaunchesActivity`
  - `testChromeAlreadyLocked_SkipsLaunch`
  - `testCooldownActive_SkipsLaunch`
  - `testNonChromeApp_ResetsTransitionLock`
  - `testRapidReopen_WithinCooldown_IsBlocked`
  - `testTargetRepository_AddTarget_PersistsAndCaches`
  - `testTargetRepository_RemoveTarget_PersistsAndCaches`
  - `testTargetRepository_ToggleTarget_PersistsAndCaches`
  - `testTargetRepository_DefaultsToChrome`
  - `testTargetRepository_Clear_RestoresDefaults`
- **8 bài kiểm thử chuyên biệt cho `BlockingShieldOverlayTest`**:
  - `testInitialState_IsNotShown`
  - `testShow_WhenNotShown_CallsWindowManagerAddView`
  - `testShow_WhenAlreadyShown_DoesNotDuplicateAddView`
  - `testHide_WhenShown_CallsWindowManagerRemoveView`
  - `testHide_WhenNotShown_DoesNothing`
  - `testCleanup_WhenShown_RemovesViewAndResetsState`
  - `testCleanup_WhenCalledRepeatedly_IsSafe`
  - `testWindowManagerFailure_HandledGracefullyWithoutCrash`

---

## 11. BỘ KIỂM THỬ HỒI QUY 22 KỊCH BẢN THỰC TẾ (REAL-DEVICE REGRESSION SUITE)

Bộ kiểm thử gồm 22 kịch bản tự động hóa hoàn toàn chạy trực tiếp trên thiết bị `vivo iQOO Neo 10` thông qua script `run_22_regressions.ps1`:

| STT | Tên kịch bản kiểm thử | Trạng thái | Ghi chú kỹ thuật |
| :---: | :--- | :---: | :--- |
| **1** | `1_Chrome_First_Launch` | **PASS** | Khởi chạy Chrome lần đầu kích hoạt khóa và hiển thị `LockScreenActivity`. |
| **2** | `2_Exit_Home` | **PASS** | Nhấn thoát đưa người dùng về đúng màn hình chính `Launcher`. |
| **3** | `3_Rapid_Reopen_Less_1s` | **PASS** | Mở lại Chrome trong vòng < 1 giây vẫn kích hoạt khóa an toàn. |
| **4** | `4_Back_Gesture` | **PASS** | Cử chỉ Back đưa người dùng về Launcher bình thường. |
| **5** | `5_Internal_Events_No_Duplicate` | **PASS** | Các sự kiện nội bộ khi LockScreen đang mở nhận diện đúng `[DECISION: SKIP]`, không khởi chạy Activity trùng lặp. |
| **6** | `6_Stale_Callback_Ignored` | **PASS** | Callback trễ hoặc sai Session ID bị chặn bởi Stale Session Guard. |
| **7** | `7_Calculator_Target_Lock` | **PASS** | Ứng dụng Máy tính được cấu hình trong target list bị khóa chuẩn xác. |
| **8** | `8_Remove_Calculator_Normal` | **PASS** | Xóa Máy tính khỏi target list, mở lại Máy tính bình thường. |
| **9** | `9_Disable_Chrome_Normal` | **PASS** | Tắt trạng thái khóa của Chrome, mở Chrome bình thường không bị chặn. |
| **10** | `10_Enable_Chrome_Lock` | **PASS** | Bật lại Chrome trong target list, Chrome lập tức bị khóa. |
| **11** | `11_Process_Restart_Persist` | **PASS** | Khởi động lại process của ứng dụng, danh sách target từ DataStore phục hồi trọn vẹn. |
| **12** | `12_Pm_Clear_Default_Chrome` | **PASS** | Xóa toàn bộ dữ liệu ứng dụng (`pm clear`), mục tiêu Chrome mặc định được khôi phục. |
| **13** | `13_Chrome_Repeated_20_Cycles` | **PASS** | 20 chu kỳ lặp lại với Chrome: 100% hiển thị LockScreen, 0% lỗi. |
| **14** | `14_Calculator_Repeated_20_Cycles` | **PASS** | 20 chu kỳ lặp lại với Calculator: 100% hiển thị LockScreen, 0% lỗi. |
| **15** | `15_Screen_Off_On_Target` | **PASS** | Tắt màn hình, mở lại màn hình, mở ứng dụng đích kích hoạt khóa chuẩn xác. |
| **16** | `16_Rapid_Home_Target` | **PASS** | Về Home rồi mở ngay lập tức ứng dụng mục tiêu được khóa ổn định. |
| **17** | `17_Rapid_Target_Home_Target` | **PASS** | Mở target -> về Home -> mở lại target: State Machine chuyển trạng thái chính xác. |
| **18** | `18_Service_Interruption` | **PASS** | Tắt và bật lại Accessibility Service trong cài đặt hệ thống, hệ thống tiếp tục hoạt động trơn tru. |
| **19** | `19_Service_Rebind` | **PASS** | Service rebind sau khi ứng dụng bị force-stop: Tự động gắn kết lại `BlockingShieldOverlay`. |
| **20** | `20_Launch_Failure_Safety_Timeout` | **PASS** | Cầu chì 2000ms bảo vệ: Không để lại bất kỳ cửa sổ overlay nào bị kẹt trên màn hình. |
| **21** | `21_Target_Crash_During_Handoff` | **PASS** | Ứng dụng mục tiêu bị crash đột ngột trong khi chuyển giao: Giao diện trở về an toàn, không treo máy. |
| **22** | `22_Rapid_Repeated_Target_Launches` | **PASS** | Mở ứng dụng mục tiêu dồn dập liên tiếp nhiều lần: State Machine xử lý tuần tự không xung đột. |

---

## 12. TUÂN THỦ NGUYÊN TẮC FROZEN CORE (STRICT PRESERVATION)

Chúng tôi xác nhận và cam kết tuân thủ 100% nguyên tắc kiến trúc đóng băng:
1. **Không can thiệp vào State Machine**:
   - Các biến trạng thái cốt lõi: `currentSessionId`, `lastForegroundPackage`, `isLockScreenVisible`, `isChromeLockedForCurrentTransition`, `lastLockLaunchTimestamp`, `COOLDOWN_MS = 1500L`, `Stale Session Guard` được giữ nguyên vẹn 100% ngữ nghĩa.
   - Tấm khiên `BlockingShieldOverlay` chỉ là một side-effect giao diện, hoàn toàn không tạo thêm trạng thái hay luồng rẽ nhánh mới trong State Machine.
2. **LockScreenActivity giữ nguyên vẹn cấu trúc**:
   - `singleTop`, `exported=false`, `FLAG_ACTIVITY_NEW_TASK`, `FLAG_ACTIVITY_SINGLE_TOP`.
   - Vòng đời `onResume` / `onStop` / `exit` được duy trì chuẩn xác.
3. **DataStore & Target Management**:
   - `TargetRepository`, DataStore cache và tính năng quản lý dynamic target được giữ nguyên vẹn không bị sửa đổi ngoài mục tiêu thử nghiệm.

---

## 13. CÁC PHÁT HIỆN ĐẶC THÙ TRÊN HỆ ĐIỀU HÀNH ORIGINOS 5 (VIVO)

1. **OriginOS Window Animations**:
   - Khi áp dụng `ActivityOptions.makeCustomAnimation(context, 0, 0)` kết hợp với `FLAG_ACTIVITY_NO_ANIMATION`, OriginOS đã loại bỏ hoàn toàn hiệu ứng phóng to cửa sổ (zoom transition), giúp giảm đáng kể cảm giác trễ hình ảnh.
2. **OriginOS NotificationShade & Keyguard**:
   - Trên OriginOS 5, khi màn hình tắt thông qua phím nguồn cứng (`input keyevent 26`), hệ thống sẽ kích hoạt giao diện bảo mật NotificationShade. Để kiểm thử tự động không bị kẹt, hệ thống cần vuốt mở khóa từ dưới đáy màn hình (`input swipe 630 2400 630 400 150`) trước khi phát lệnh điều hướng.
3. **Hiệu năng WindowManager của OriginOS**:
   - Thao tác `WindowManager.addView` đối với cửa sổ `TYPE_ACCESSIBILITY_OVERLAY` trên chip Snapdragon 8 Gen 3 được hoàn tất cực kỳ nhanh chóng: chỉ mất trung bình **~29 ms** để SurfaceFlinger render frame đầu tiên.

---

## 14. GIỚI HẠN NỀN TẢNG ANDROID & KẾT LUẬN THỰC NGHIỆM

1. **Bản chất của Accessibility Service**:
   - Android thiết kế `AccessibilityService` nhằm mục đích hỗ trợ người khuyết tật thông qua việc **quan sát các sự kiện giao diện đã xảy ra** (`TYPE_WINDOW_STATE_CHANGED`), chứ không phải một tường lửa ứng dụng (Application Firewall) có khả năng can thiệp trước khi Intent được hệ thống thực thi.
2. **Khoảng trống thời gian $T_0 \to T_1$**:
   - Dù chúng ta tối ưu mã nguồn đến mức tiệm cận 0 mili-giây, khoảng cách thời gian giữa lúc hệ điều hành nhận lệnh mở ứng dụng từ Launcher ($T_0$) cho đến lúc phát sự kiện sang AccessibilityService ($T_1$) là bất khả xâm phạm ở tầng người dùng (userland).
   - Do đó, đối với các ứng dụng có sẵn trong RAM (warm-start) và có giao diện cực kỳ đơn giản (như Calculator), hiện tượng nhấp nháy giao diện trước khi AccessibilityService nhận được event là một giới hạn phần cứng/nền tảng của Android.
3. **Nguyên tắc "Biết dừng đúng lúc"**:
   - Thử nghiệm Phase 07-B đã đạt được mức tối ưu tối đa mà API tiêu chuẩn của Android cho phép mà không cần quyền Root hay can thiệp hệ thống trái phép.
   - Việc tiếp tục thêm các thủ thuật chắp vá (hack) phức tạp hơn sẽ chỉ làm tăng độ mong manh (fragility) và rủi ro crash của ứng dụng.

---

## 15. ĐỀ XUẤT HƯỚNG ĐI CHO CÁC GIAI ĐOẠN TIẾP THEO

1. **Nghiệm thu Phase 07-B**:
   - Giữ lại `BlockingShieldOverlay` trong mã nguồn vì component này mang lại 2 giá trị thiết thực lớn:
     - Giúp che phủ màn hình sớm hơn $\approx 15 - 20$ ms so với chỉ dùng `LockScreenActivity`.
     - **Triệt tiêu 100% rủi ro thao tác chạm nhầm** của người dùng lên ứng dụng mục tiêu trong thời gian chuyển tiếp.
2. **Dừng thêm các giải pháp can thiệp thị giác mỏng manh**:
   - Chấp nhận giới hạn kỹ thuật của Android đối với warm-start glimpse của các ứng dụng siêu nhẹ.
3. **Chuyển trọng tâm sang các tính năng giá trị cao tiếp theo của POC**:
   - **Phase 08**: Tích hợp Schedule / Time Limits (khóa theo khung giờ hoặc hạn mức thời gian sử dụng trong ngày).
   - **Phase 09**: Phòng chống gỡ cài đặt / Vô hiệu hóa ứng dụng (Anti-tamper / Uninstall prevention).
   - **Phase 10**: Cải thiện trải nghiệm Self-Discipline (Nhiệm vụ vượt qua cám dỗ, nhập mã cam kết, bài tập thở trước khi mở khóa).
