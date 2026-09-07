# TÀI LIỆU ĐÓNG BĂNG RANH GIỚI SẢN PHẨM (PRODUCT BASELINE SPECIFICATION)

**Dự án:** Hệ Thống Tự Kỷ Luật Bản Thân (`self-discipline-poc-01`)  
**Tài liệu tham chiếu chuẩn:** [`docs/CANONICAL_DESIGN_V2.md`](file:///c:/Code/self-discipline-poc-01/docs/CANONICAL_DESIGN_V2.md)  
**Ngày thiết lập:** 07/09/2026 (Phase 25)  
**Trạng thái:** **PRODUCT BASELINE FROZEN (ĐÓNG BĂNG SAU OPEN-01)**  

---

## 1. MỤC ĐÍCH & Ý NGHĨA

Tài liệu này xác lập ranh giới sản phẩm chính thức tại thời điểm hoàn tất kiểm chứng **OPEN-01 (Task-based Unlock)** sau Phase 24 và thực hiện Governance Correction tại Phase 25.
- Mục đích: Định hình rõ ràng những gì đã được Ký chủ chốt (Product Decision) và những gì chỉ là Nền tảng Kỹ thuật (Technical Foundation).
- Nguyên tắc: Ngăn chặn tuyệt đối việc tự ý suy diễn hoặc mở rộng business rules cho các mục chưa chốt (OPEN-02..07).

---

## 2. LUỒNG NGHIỆP VỤ SẢN PHẨM HIỆN TẠI (CURRENT PRODUCT FLOW)

Hệ thống vận hành chính xác theo luồng end-to-end đã được kiểm chứng:

```
[Bảo Khố (Vault App)]
         ↓
[Liên kết App ↔ Task (Linkage)]
         ↓
[Nhiệm Vụ Đường: Hoàn thành Task]
         ↓
[Đạt ngưỡng: ceil(2*N/3)]
         ↓
[Mở khóa nghiệp vụ (Business Unlock: ALLOW)]
         ↓
[Mốc 04:00:00 (Giờ Dần)]
         ↓
[Chu kỳ nghiệp vụ mới (New Business Cycle)]
         ↓
[Tái đánh giá: Khóa lại (Re-evaluate: LOCK)]
```

---

## 3. CÁC HÀNH VI NGHIỆP VỤ ĐÃ ĐƯỢC QUYẾT ĐỊNH (DECIDED PRODUCT BEHAVIORS)

Tất cả các hành vi dưới đây là **Quyết định Sản phẩm chính thức (Official Product Decisions)**, đã được hiện thực hóa và kiểm chứng trên thiết bị thật:

1. **Công thức Mở Khóa theo Nhiệm Vụ (OPEN-01 — CLOSED):**
   - Áp dụng cho một Vault App có $N$ nhiệm vụ đang có hiệu lực (active, không bị lưu trữ, không bị xóa) được liên kết trong chu kỳ nghiệp vụ hiện tại:
     $$\text{requiredCompletedTasks} = \left\lceil \frac{2 \times N}{3} \right\rceil$$
   - Hiện thực hóa bằng số học nguyên thuần túy:
     $$\text{required} = (2 \times N + 2) / 3$$
   - Bảng kiểm chứng chuẩn hóa ($N = 0 \dots 10$):
     * $N = 0 \rightarrow 0$ (Bắt buộc $N > 0$ mới có thể mở) $\rightarrow$ `LOCK` (`LOCKED_BY_VAULT_NO_TASK`).
     * $N = 1 \rightarrow 1$ (1/1 $\rightarrow$ `ALLOW`).
     * $N = 2 \rightarrow 2$ (2/2 $\rightarrow$ `ALLOW`).
     * $N = 3 \rightarrow 2$ (2/3 $\rightarrow$ `ALLOW`).
     * $N = 4 \rightarrow 3$ (3/4 $\rightarrow$ `ALLOW`).
     * $N = 5 \rightarrow 4$ (4/5 $\rightarrow$ `ALLOW`).
     * $N = 6 \rightarrow 4$ (4/6 $\rightarrow$ `ALLOW`).
     * $N = 7 \rightarrow 5$ (5/7 $\rightarrow$ `ALLOW`).
     * $N = 8 \rightarrow 6$ (6/8 $\rightarrow$ `ALLOW`).
     * $N = 9 \rightarrow 6$ (6/9 $\rightarrow$ `ALLOW`).
     * $N = 10 \rightarrow 7$ (7/10 $\rightarrow$ `ALLOW`).
   - Điều kiện mở khóa: $\text{completedTasks} \ge \text{required} \quad \text{VÀ} \quad N > 0 \implies \text{ALLOW}$. Nếu không thỏa mãn $\implies \text{LOCK}$.
2. **Chu kỳ Ngày Nghiệp Vụ 04:00 (Business Day Boundary):**
   - Mốc reset ngày là `04:00:00` sáng (Giờ Dần).
   - Chỉ các lượt hoàn thành ghi nhận trong chu kỳ ngày hiện tại mới được tính vào `completedTasks`.
   - Task bắt đầu trước 04:00 thuộc chu kỳ ngày cũ.
3. **Quan hệ Độc lập Many-to-Many (Task $\leftrightarrow$ App):**
   - Một task hoàn thành áp dụng đồng thời cho tất cả các app được liên kết.
   - Một app có nhiều task: mỗi task được tính độc lập trong mẫu số $N$ và tử số `completedTasks`.
4. **Độ Ưu Tiên Tuyệt Đối Của Khóa Kỹ Thuật (Technical Precedence):**
   - Technical App Lock (`PolicyEngine`: Schedule & Daily Limit) luôn có ưu tiên tối thượng.
   - Nếu Technical Lock cấm (`LOCK`) $\implies \text{Final Action} = \text{LOCK}$, không bao giờ bị bypass bởi Business Unlock.
5. **Voucher Không Tính Là Task Completion:**
   - Voucher (vé mở khóa) là cơ chế riêng thuộc phân hệ Thương Thành/Túi Trữ Vật, tuyệt đối không được gộp vào bộ đếm hoàn thành nhiệm vụ.
6. **Xóa & Thêm lại vào Bảo Khố (Cascade Linkage Cleanup):**
   - Khi xóa app khỏi Bảo Khố: toàn bộ liên kết trong `TaskAppCrossRef` bị xóa sạch (task và lịch sử completion được giữ nguyên).
   - Khi thêm lại app vào Bảo Khố: không tự động phục hồi các liên kết cũ, app trở về trạng thái $N=0 \rightarrow$ `LOCK`.

7. **Technical App Lock — Scoped Product Behavior (OPEN-04 — CLOSED):**
   - Lịch trình Schedule & Giới hạn sử dụng Daily Limit chính thức trở thành Hành vi Sản phẩm Có Phạm vi Giới hạn (Scoped Product Behavior), đóng vai trò **"Hàng Rào Bảo Vệ Cứng / Chính Sách Cấm Tuyệt Đối" (Hard Ceiling Guardrails / Absolute Ban Policy)**.
   - Thẩm quyền tối thượng: Khi vi phạm khung giờ cấm hoặc vượt trần thời lượng dùng trong ngày $\implies \text{LOCK}$ (`LOCKED_BY_POLICY`), không một tiến trình nhiệm vụ hay voucher nào có thể bypass.
   - Khẳng định giải pháp kỹ thuật: Accessibility Service (`TYPE_WINDOW_STATE_CHANGED`) + Window Overlay (`TYPE_APPLICATION_OVERLAY`) chính thức là Core Enforcement Engine của hệ thống trên Android 15 / OriginOS 5.

---

## 4. CÁC ĐIỂM CHƯA ĐƯỢC QUYẾT ĐỊNH (UNDECIDED / OPEN ITEMS)

Các hạng mục dưới đây bắt buộc phải duy trì trạng thái **`OPEN`**, tuyệt đối không được tự ý triển khai code hay suy diễn nghiệp vụ:

| Mã ID | Vấn Đề Chưa Quyết Định (Undecided Item) | Trạng Thái | Ràng Buộc Nghiêm Ngặt |
| :---: | :--- | :---: | :--- |
| **OPEN-02** | Công thức điểm Tu Luyện & Phần thưởng cuối cùng (Point / Reward final formula) | **OPEN** | Không tự tạo hệ thống điểm tu vi, combo ngày hay trừ điểm. |
| **OPEN-03** | Công thức chi tiết Tháp Thí Luyện & Ngoại lệ Tầng 4 (Tower detailed formula / Floor 4 exception) | **OPEN** | Không tự viết thuật toán sinh tầng hay logic độ khó tầng 4. |
| **OPEN-05** | Lược đồ DB chính thức & Chiến lược Di chuyển (Official DB schema & migration strategy) | **OPEN** | Room DB hiện tại chỉ là Nền tảng Kỹ thuật (Technical Foundation) phục vụ POC, chưa phải schema chính thức đã chốt. |
| **OPEN-06** | Chính sách Lưu trữ & Đồng bộ Cloud (Memory / Cloud retention & sync policy) | **OPEN** | Giữ 100% On-device, không tự ý viết Cloud sync hay gửi dữ liệu ra ngoài. |
| **OPEN-07** | State Machine Giao diện / Animation / Audio Tokens (UI state machine / animation / audio tokens) | **OPEN** | Không tự ý nhúng voice assets giả lập hoặc tự định nghĩa animation state machine ngoài Foundation hiện có. |

---

## 5. PHÂN ĐỊNH RẠCH RÒI: PRODUCT BEHAVIOR VS TECHNICAL FOUNDATION

Tuân thủ nghiêm ngặt **RULE 10** trong `docs/DESIGN_DECISIONS.md`:

```
┌────────────────────────────────────────────────────────────────────────┐
│                   HÀNH VI SẢN PHẨM ĐÃ QUYẾT ĐỊNH                       │
│                        (PRODUCT BEHAVIOR)                              │
├────────────────────────────────────────────────────────────────────────┤
│ • Nhiệm Vụ Đường: Quản lý task, chuỗi tuần tự, hoàn thành task.        │
│ • Bảo Khố: Thêm/gỡ app phong ấn (giao diện không icon ổ khóa).         │
│ • Liên kết Task ↔ App: Quan hệ Many-to-Many.                           │
│ • Giải phong ấn theo Task: Công thức ceil(2N/3) với N > 0 (OPEN-01).   │
│ • Chu kỳ ngày 04:00: Reset lượt hoàn thành mỗi ngày.                   │
│ • Technical App Lock: Hard Ceiling Guardrail (OPEN-04).                │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │ được hỗ trợ bởi
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      NỀN TẢNG KỸ THUẬT HỖ TRỢ                          │
│                       (TECHNICAL FOUNDATION)                           │
├────────────────────────────────────────────────────────────────────────┤
│ • Accessibility Service: AppDetectorAccessibilityService bắt event.    │
│ • Blocking Shield Overlay & LockScreenActivity: Cơ chế chặn tầng OS.   │
│ • Technical Policy Engine: ScheduleEvaluator, UsageTracker.            │
│ • Snapshot Cache: Tra cứu O(1) in-memory an toàn cho Main Thread.      │
│ • Room Persistence hiện tại: SQLite schema tạm thời cho POC/Core.      │
│ • DiagnosticLogger: Ring buffer 200 sự kiện chẩn đoán trong RAM.       │
│ • Cultivation UI Design System: Hệ thống Semantic Tokens & Components. │
└────────────────────────────────────────────────────────────────────────┘
```

> [!CAUTION]
> **Nguyên tắc cốt tử:** Nền tảng Kỹ thuật (Technical Foundation) được xây dựng để phục vụ việc kiểm chứng và chạy thử nghiệm, **tuyệt đối không được tự động coi là Quyết định Sản phẩm (Product Decision)** hoặc tự biến các mục OPEN tương ứng thành `DONE` khi Ký chủ chưa đưa ra quyết định chính thức.

