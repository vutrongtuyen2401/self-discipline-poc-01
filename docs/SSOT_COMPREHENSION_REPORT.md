# SSOT COMPREHENSION REPORT
# PHASE 0A — SSOT ONBOARDING & COMPREHENSION

**Dự án:** HỆ THỐNG PHONG ẤN DỤC VỌNG (self-discipline-poc-01)  
**Tài liệu nguồn:** `docs/MASTER SSOT - HỆ THỐNG PHONG ẤN DỤC VỌNG.docx`  
**Phiên bản SSOT:** 1.0.0 — Canonical Single Source of Truth (SSOT)  
**Người tổng hợp:** AI Agent (Phase 0A — Read-Only)  
**Ngày tổng hợp:** 2026-09-10  

---

## TUYÊN BỐ ĐỌC TÀI LIỆU

Tôi đã đọc đầy đủ toàn bộ 629 đoạn văn của file
`docs/MASTER SSOT - HỆ THỐNG PHONG ẤN DỤC VỌNG.docx` (49,099 bytes, 30 phần).  
Báo cáo này là **bằng chứng hiểu biết** theo 18 bước checklist của Phase 0A.  
**KHÔNG có bất kỳ thay đổi mã nguồn nào được thực hiện trong phase này.**

---

## CHECKLIST 01 — DANH TÍNH SẢN PHẨM

| Trường | Giá trị |
|---|---|
| Tên chính thức | **HỆ THỐNG PHONG ẤN DỤC VỌNG** |
| Tên tiếng Anh | SEALING THE DESIRE SYSTEM |
| Phân loại | Product, Functional, UX, AI Agent, Architectural & Implementation Specification |
| Phiên bản SSOT | 1.0.0 |
| Quyền hạn | LUẬT TỐI CAO — Mã nguồn mâu thuẫn với SSOT → mã nguồn là defect |

### Triết lý cốt lõi:
> "Dục vọng không thể bị tiêu diệt bằng cách chối bỏ, mà phải được phong ấn và chỉ được giải phóng khi người tu luyện vượt qua khảo nghiệm xứng đáng."

Các ứng dụng giải trí không bị coi là "tội lỗi" mà là "linh đan diệu dược" — phần thưởng hợp pháp sau khi người dùng tích lũy đủ công đức và rèn luyện.

### Ba Thực Thể:
1. **Ký chủ (User):** Trung tâm hệ thống, sở hữu quyền tối cao phê duyệt mọi thay đổi lớn.
2. **Hệ thống (App):** Cơ chế vận hành toàn diện, khách quan, nghiêm minh — bảo đảm tính kỷ luật tuyệt đối.
3. **Khí Linh (AI Agent):** Hiện thân ý thức của Hệ thống — trợ tá trung thành, tuyệt đối không tiếm quyền Ký chủ hay bẻ cong quy tắc sản phẩm.

---

## CHECKLIST 02 — TẦM NHÌN VÀ VÒNG LẶP KỶ LUẬT

Hệ thống vận hành như một vòng lặp kỷ luật khép kín:

```
Phong ấn Ứng dụng (Bảo Khố)
       ↓
Nhiệm vụ Tông môn (Nhiệm Vụ Đường)
       ↓
Tu Luyện (Bí Cảnh & Tháp Thí Luyện) → Điểm D, Vật phẩm
       ↓
Thương Thành & Túi Trữ Vật → Vé 24h, Tự do tạm thời
       ↓
Trí Nhớ & Khí Linh AI → Tối ưu hóa lộ trình
```

### Phép ẩn dụ 6 thành phần:
| Thành phần | Vai trò |
|---|---|
| Khí Linh (AI Agent) | Não Bộ — phân tích, lập kế hoạch, định tuyến |
| Bộ Nhớ Linh Trí | Ký ức — lưu thói quen, quy tắc, lịch sử |
| Động cơ Tác vụ | Cánh Tay — thực thi chuỗi hành động nguyên tử |
| Hệ thống Kiểm soát An toàn | Ý Chí Kiềm Chế — ngăn vượt quyền, bảo vệ quy tắc |
| Dịch vụ Android (Accessibility/Overlay) | Giác Quan — cảm nhận trạng thái thiết bị |
| Giao Diện UI | Thân Thể Hiển Lộ — Hologram + Phù văn Tu tiên |

---

## CHECKLIST 03 — KIẾN TRÚC HỆ THỐNG (15 PHÂN HỆ)

| # | Phân Hệ | Vai Trò | Bất Biến Chính |
|---|---|---|---|
| 1 | Khí Linh (AI Agent) | Phân tích NLP, định tuyến, sinh kế hoạch | Không tự đổi product rule; không tự cấp quyền Android |
| 2 | App Lock Engine | Chặn app qua Accessibility/Overlay | App chỉ khóa khi có ≥1 nhiệm vụ chưa hoàn thành và không có vé hiệu lực |
| 3 | Nhiệm Vụ Đường (Task Mgmt) | CRUD nhiệm vụ, liên kết app N:M | — |
| 4 | Cycle Engine (04:00) | Reset chu kỳ 04:00 AM, không thông báo | Không replay chu kỳ cũ khi offline |
| 5 | Vault Module (Bảo Khố) | Danh bạ app bị phong ấn | Điều kiện giải phóng 2/3 |
| 6 | Dungeon Module (Bí Cảnh) | Nhiệm vụ lặp hàng ngày, +1D/nhiệm vụ | Start-time semantics cho 04:00 AM |
| 7 | Tower Engine (Tháp Thí Luyện) | Tiến trình tu vi trường tồn | Tầng đã xong = IMMUTABLE, cấm farm lại điểm |
| 8 | Shop Engine (Thương Thành) | Đổi Điểm lấy Vé 24h | Trừ điểm độc lập với animation |
| 9 | Inventory (Túi Trữ Vật) | Sức chứa vô hạn, xếp chồng | Không bao giờ chặn nhận đồ |
| 10 | Memory Module | Bộ nhớ phiên tự hủy; bộ nhớ dài hạn 7 ngày | Chỉ tạo khi Ký chủ yêu cầu rõ ràng |
| 11 | Safety Engine | Chặn vượt quyền AI, xác nhận phá hủy dữ liệu | — |
| 12 | Privacy Guard | Lọc dữ liệu Local-only | Cấm gửi Device ID, danh bạ app đầy đủ lên Cloud |
| 13 | UI & System Panel | Render Hologram Tu tiên, Overlay chặn app | System Panel không phải Home Screen |
| 14 | Persistence (Room DB) | ACID transaction, AES-GCM backup | — |
| 15 | OS Services | Accessibility, WindowManager, TTS, WorkManager | — |

---

## CHECKLIST 04 — KIẾN TRÚC ĐIỀU HƯỚNG (NAVIGATION & IA)

### Cây Điều Hướng:
```
🏠 Trang chủ (Root Screen)
├── 🎯 Nhiệm Vụ Đường (Mission Hall)
│   ├── [Modal] Thêm Nhiệm Vụ Mới
│   ├── [View/Edit] Chỉnh sửa + Gắn App
│   └── [Batch] Xóa Hàng Loạt
├── 🏦 Bảo Khố (Vault)
│   ├── [Modal] Thêm App
│   └── [Detail] Chi tiết App + Yêu cầu Xóa
├── 🎒 Túi Trữ Vật (Inventory)
│   └── [Detail] Bộ đếm hạn dùng + Kích hoạt
├── 🏯 Tu Luyện (Cultivation Hub)
│   ├── 📜 Bí Cảnh (Dungeon)
│   └── 🗼 Tháp Thí Luyện (Tower)
├── 🏪 Thương Thành (Shop)
├── 🧠 Bộ Nhớ Linh Trí (Memory Manager)
└── ⚙️ Cài Đặt (Settings)

[LAYER ĐẶC BIỆT]
✦ Bảng Hệ Thống (System Panel) — System Overlay khi mở app bị khóa
```

### Quy Tắc Back Button (4 lớp ưu tiên):
1. **Ưu tiên 1 (tối cao):** Đóng Dialog / Hủy thao tác kéo thả dở dang
2. **Ưu tiên 2:** System Panel → đẩy về Home Android (`Intent.ACTION_MAIN`), KHÔNG lọt vào app bị khóa
3. **Ưu tiên 3:** Màn hình con → về màn hình cha
4. **Ưu tiên 4:** Màn hình gốc → thoát app ra Home điện thoại

---

## CHECKLIST 05 — ĐỘNG CƠ CHU KỲ 04:00

### Quy tắc cốt lõi:
- Mốc reset: **04:00:00 AM hàng ngày theo giờ thiết bị (Device Local Timezone)**
- **Tuyệt đối KHÔNG** phát chuông/rung/popup lúc 04:00 AM

### Hành vi cưỡng chế:
- Nếu Ký chủ đang mở app bị quản lý đúng 04:00 AM → hệ thống đánh giá lại, thu hồi quyền mở khóa, kích hoạt `Intent.ACTION_MAIN` đẩy về Home + hiện System Panel

### Offline Catch-up:
- Tắt nguồn qua nhiều ngày → khi bật lại, tính mốc 04:00 AM **gần nhất** làm chu kỳ hiện tại
- **Tuyệt đối không replay** tuần tự các chu kỳ đã bỏ lỡ

### Task Start-time Semantics:
- Nhiệm vụ Bí Cảnh bắt đầu **trước** 04:00 AM, hoàn thành **sau** 04:00 AM → tính điểm cho **chu kỳ CŨ**

### Bảng Reset vs Bất Biến lúc 04:00 AM:

| Loại Dữ Liệu | Reset? | Mô Tả |
|---|---|---|
| Trạng thái Nhiệm Vụ Tông môn | ✅ CÓ | COMPLETED → PENDING |
| Trạng thái Khóa Ứng Dụng | ✅ CÓ | Khóa lại toàn bộ (trừ app có vé còn hạn) |
| Tiến độ 2/3 Xóa App | ✅ CÓ | Xóa số đếm chu kỳ cũ |
| Vị trí dở dang chuỗi nhiệm vụ | ✅ CÓ | Bắt đầu lại từ đầu |
| Cấu hình chờ chu kỳ tiếp theo | ✅ CÓ | Kích hoạt cấu hình Pending |
| Tiến trình Tháp Thí Luyện | ❌ KHÔNG | Bảo tồn vĩnh cửu |
| Điểm Tích Lũy (Điểm D) | ❌ KHÔNG | Bảo lưu vĩnh viễn |
| Vật Phẩm & Vé trong Túi | ❌ KHÔNG | Bảo toàn, hạn dùng chạy độc lập |
| Thời gian Vé 24h đang kích hoạt | ❌ KHÔNG | Đếm ngược liên tục theo thời gian thực |
| Bộ Nhớ Linh Trí (Long-term) | ❌ KHÔNG | Vòng đời riêng 7 ngày / 6 tháng |

---

## CHECKLIST 06 — BẢO KHỐ (VAULT ENGINE & APP LOCK LOGIC)

### Khởi tạo:
- App mới thêm vào Bảo Khố → mặc định `MANAGED_UNLOCKED` (CHƯA KHÓA)
- App chỉ bị khóa khi liên kết với ≥1 nhiệm vụ chưa hoàn thành

### Quan hệ N:M:
- 1 nhiệm vụ liên kết nhiều app; 1 app liên kết nhiều nhiệm vụ

### UX In-place:
- Chọn/bỏ chọn app phần thưởng → cập nhật ngay tại chỗ, không reload màn hình

### Quy tắc Xóa App khỏi Bảo Khố (2/3 Rule):

| N (số nhiệm vụ liên kết) | Required (hoàn thành) | Ghi chú |
|---|---|---|
| 1 | 1 | 100% |
| 2 | 1 | Đặc xá N=2 (50%) |
| ≥3 | ⌈2N/3⌉ | Công thức chuẩn |

**Nếu đủ điều kiện:** App mở khóa ngay, gỡ khỏi tất cả nhiệm vụ. Nhiệm vụ mất app → "Chưa có phần thưởng".  
**Thêm lại app cũ:** Trở về trạng thái Chưa Khóa, **tuyệt đối không** tự khôi phục liên kết nhiệm vụ cũ.

### Ngoại lệ In-Use:
- App đang foreground mà bị thay đổi cấu hình → không khóa ngay, áp dụng từ chu kỳ tiếp theo (04:00 AM)

### Pending Next Cycle:
- Chỉ 1 bản ghi cấu hình chờ cho mỗi nhiệm vụ; cái mới ghi đè cái cũ; Ký chủ có thể Hủy bất cứ lúc nào trước 04:00 AM

---

## CHECKLIST 07 — NHIỆM VỤ ĐƯỜNG (MISSION HALL)

### Thực thể Nhiệm Vụ:
```
task_id (UUID)
title (String)
status (PENDING / COMPLETED)
reward_app_ids (List)
order_index (Int)
created_cycle (Timestamp)
pending_next_cycle_rewards (List, nullable)
```

### Quy Tắc Chỉnh Sửa:
- **Đổi tên:** Hiệu lực tức thì, không cần xác nhận
- **Xóa:** Bắt buộc Dialog xác nhận; sau xóa app không còn nhiệm vụ ràng buộc → mở khóa ngay
- **Hoàn Tác (Undo):** Nút trực tiếp trên thẻ (có xác nhận); app liên quan bị tính toán lại và khóa lại ngay nếu không đủ điều kiện mở

### Luồng Chặn Mở App (Blocked-App Flow):

> **⚠️ QUAN TRỌNG:** Xác nhận hoàn thành nhiệm vụ **KHÔNG** phải màn hình danh sách độc lập — nó diễn ra bên trong luồng đánh chặn khi Ký chủ mở app bị khóa.

```
User bấm mở app bị khóa
        ↓
Bảng Hệ Thống (System Panel) xuất hiện
        ↓
Hiển thị Nhiệm Vụ 1 (theo thứ tự)
        ↓
User xác nhận hoàn thành (hệ thống tin tưởng Ký chủ)
        ↓
"Ký chủ đã hoàn thành 1/X"
        ↓
Tự động → Nhiệm Vụ 2 → ... → Nhiệm Vụ cuối
        ↓
"Chúc mừng ký chủ đã hoàn thành nhiệm vụ!" (giọng Khí Linh Anime)
        ↓
User bấm Xác Nhận → Mở khóa App
```

**Ghi nhớ vị trí dở dang:** Thoát giữa chừng → lần sau tiếp tục từ vị trí bỏ dở. Reset lúc 04:00 AM.

---

## CHECKLIST 08 — QUY TẮC MỞ KHÓA 2/3

### Công Thức Chuẩn Mực:

```
Required(1) = 1
Required(2) = 1   ← Ngoại lệ đặc xá N=2
Required(N) = ⌈2N/3⌉  với N ≥ 3
```

### Bảng Tra Cứu Toàn Diện:

| N | Required | Tỷ lệ | Ghi chú |
|---|---|---|---|
| 1 | 1 | 100.0% | Hoàn thành 1 → Mở khóa |
| 2 | 1 | 50.0% | Đặc xá N=2 |
| 3 | 2 | 66.7% | |
| 4 | 3 | 75.0% | |
| 5 | 4 | 80.0% | |
| 6 | 4 | 66.7% | |
| 7 | 5 | 71.4% | |
| 8 | 6 | 75.0% | |
| 9 | 6 | 66.7% | |
| 10 | 7 | 70.0% | |

---

## CHECKLIST 09 — TU LUYỆN: BÍ CẢNH (DUNGEON)

- **Tách biệt hoàn toàn** với Nhiệm Vụ Đường (dù trùng tên cũng không liên kết trạng thái)
- **Phần thưởng:** Mỗi nhiệm vụ hoàn thành = **+1 Điểm Tích Lũy (+1D)**
- **Kỳ ngộ ngẫu nhiên:** Có cơ hội rơi vật phẩm/vé (cho phép rơi trùng, tự gộp vào Túi)
- **Start-time:** Bắt đầu trước 04:00 AM, hoàn thành sau 04:00 AM → tính cho chu kỳ CŨ
- **Chống farm:** Khóa nút ngay khi bấm để chống click đúp

---

## CHECKLIST 10 — TU LUYỆN: THÁP THÍ LUYỆN (TOWER)

### Bản chất:
> **QUY TẮC CỐT TỬ:** Tháp Thí Luyện **KHÔNG** phải công cụ tính điểm đơn thuần — đây là **trục tiến trình tu vi trường tồn**.  
> Tiến trình Tháp **TUYỆT ĐỐI KHÔNG RESET** lúc 04:00 AM.  
> Tầng đã hoàn thành = **IMMUTABLE** — cấm chỉnh sửa, cấm farm lại điểm.

### Hai Phương Thức Tạo Tháp:

**1. Thủ Công (Manual):**
- Tự thiết kế từng tầng với thử thách độc lập
- Chỉ xóa được tầng chưa hoàn thành
- Xóa tầng giữa → đánh số lại tự động (Renumbering) cho tầng phía sau

**2. Công Thức (Formula Mode):**
- Form: Tên Tháp, Nhiệm Vụ, Giá Trị Khởi Đầu, Increment, Unit (tự gợi ý, cho phép sửa), Preview
- Áp dụng **Lazy Generation** (sinh lười) ở tầng kỹ thuật
- Sinh theo: `challenge = Start + (i-1) * Increment`

### Quy Tắc Thưởng Điểm:

| Loại tầng | Công thức | Ví dụ |
|---|---|---|
| Tầng Lẻ (1,3,5,7,...) | Cố định **1D** | Tầng 1 = 1D |
| Tầng Chẵn (2,4,6,8,...) | `floor/2 + 1` | Tầng 2=2D, Tầng 4=3D, Tầng 6=4D, Tầng 8=5D, Tầng 10=6D |

### Exception vs Propagate (chỉ áp dụng Tháp Công Thức):

- **Option A — Ngoại Lệ Cục Bộ (Exception):** Chỉ tầng được chọn mang giá trị mới (`is_exception=true`). Công thức gốc giữ nguyên; tầng phía sau vẫn tịnh tiến theo công thức gốc.
- **Option B — Lan Truyền (Propagate):** Tầng được chọn nhận giá trị mới; toàn bộ tầng **chưa** hoàn thành phía sau được tính lại từ mốc mới. Tầng đã xong bất biến.
- **Ràng buộc:** Cấm xóa lẻ từng tầng trong Tháp Công Thức.

---

## CHECKLIST 11 — THƯƠNG THÀNH (SHOP ENGINE)

- **Đơn vị:** 1 đơn vị giá = **24 giờ mở khóa tự do**; Ký chủ tùy chỉnh giá theo từng app
- **Tương tác:** Bấm Avatar app → tăng số lượng (1 click = 1 vé, 2 clicks = 2 vé,...)
- **Xác nhận mua:**
  - Mua 1 vé → Trừ điểm ngay, **không cần confirm**
  - Mua ≥2 vé → **Bắt buộc Dialog xác nhận**
- **Toàn vẹn giao dịch:** Trừ điểm + thêm vé phải commit nguyên tử ACID **TRƯỚC hoặc ĐỘC LẬP** với animation. Hoạt ảnh số chạy lùi (Running number).
- **Hạn dùng 15 ngày:** Khi app bị xóa khỏi Bảo Khố → toàn bộ vé của app trong Túi nhận hạn dùng 15 ngày từ thời điểm xóa đầu tiên. Thêm lại trong 15 ngày → vé trở lại bình thường. Xóa lại lần 2 → giữ mốc 15 ngày ban đầu.

---

## CHECKLIST 12 — TÚI TRỮ VẬT (INVENTORY)

- **Sức chứa vô hạn:** Không bao giờ giới hạn ô chứa hay chặn giao dịch vì đầy túi
- **Xếp chồng (Stacking):** Gộp ô cho vật phẩm cùng loại và **CÙNG TRẠNG THÁI HẠN DÙNG**. Nếu khác hạn dùng → tách thành ô riêng
- **Cảnh báo hạn dùng:** Vé còn ≤3 ngày → viền/ô chuyển màu ĐỎ. Hết hạn → tự xóa
- **Sử dụng vé:** Dùng nhiều vé cùng lúc (+24h mỗi vé); ưu tiên vé hạn gần nhất trước. Khi hết hạn vé → kiểm tra ngay nhiệm vụ → còn chưa xong → khóa app lại ngay

---

## CHECKLIST 13 — KHÍ LINH AI AGENT

### Intent Resolution:
- 1 cách hiểu vượt trội → Thực thi ngay
- ≥2 cách hiểu tương đương → Hỏi lại ngắn gọn
- Câu lệnh quá ngắn/thiếu thông số → Hỏi tham số còn thiếu, **không đoán mò**

### Lệnh ưu tiên cao nhất:
- **Lệnh Dừng (Stop):** Dừng ngay các bước chưa chạy, để bước nguyên tử dở dang hoàn tất an toàn rồi dừng hẳn. Không tự ý chạy lại.
- **Lệnh Hủy (Cancel):** Hủy bước chưa làm; **không rollback** các bước độc lập đã hoàn thành.

### Bảng Phân Quyền AI:

| Hành Động | Tự Làm | Cần Confirm | Tuyệt Đối Cấm |
|---|---|---|---|
| Phân tích ngữ cảnh & ý định | ✅ | ❌ | ❌ |
| Đề xuất kế hoạch tu luyện | ✅ | ❌ | ❌ |
| Retry lỗi mạng/API (max 3 lần) | ✅ | ❌ | ❌ |
| Tự sửa lỗi nội bộ an toàn | ✅ | ❌ | ❌ |
| Tạo/Chỉnh sửa Nhiệm vụ | ❌ | ✅ | ❌ |
| Gắn/Gỡ App khỏi Nhiệm vụ | ❌ | ✅ | ❌ |
| Mua hàng trong Thương Thành | ❌ | ✅ | ❌ |
| Sử dụng Vé trong Túi | ❌ | ✅ | ❌ |
| Xóa Nhiệm vụ / Xóa App Vault | ❌ | ✅ | ❌ |
| Xóa toàn bộ Bộ nhớ Linh Trí | ❌ | Gõ "XÓA TẤT CẢ" | ❌ |
| Tự ý bẻ cong Quy tắc 2/3 | 🚫 | 🚫 | **TUYỆT ĐỐI CẤM** |
| Tự ý mở khóa app bỏ qua nhiệm vụ | 🚫 | 🚫 | **TUYỆT ĐỐI CẤM** |
| Tự cấp quyền Accessibility Android | 🚫 | 🚫 | **TUYỆT ĐỐI CẤM** |
| Tự ý thay đổi Logic Reset 04:00 | 🚫 | 🚫 | **TUYỆT ĐỐI CẤM** |
| Tự gửi dữ liệu nhạy cảm ra ngoài | 🚫 | 🚫 | **TUYỆT ĐỐI CẤM** |
| Giả lập thành công khi action lỗi | 🚫 | 🚫 | **TUYỆT ĐỐI CẤM** |

---

## CHECKLIST 14 — BỘ NHỚ LINH TRÍ (MEMORY LIFECYCLE)

- **Bộ nhớ phiên (Session):** Tồn tại trong RAM, **tự xóa sạch** khi đóng app, không tự chuyển thành bộ nhớ lâu dài
- **Bộ nhớ dài hạn (Long-term):** Chỉ tạo khi Ký chủ **yêu cầu rõ ràng**; mặc định 7 ngày, tự gia hạn nếu hữu ích, tối đa 6 tháng (chạm trần → hỏi ý kiến Ký chủ)
- **Giao diện:** Xem, sửa, tìm kiếm, xóa từng thẻ. Xóa tất cả → bắt buộc nhập đúng: `XÓA TẤT CẢ`

---

## CHECKLIST 15 — LỚP BẢO MẬT & PRIVACY GUARD

### Local-First:
- Toàn bộ logic khóa app, SQLite, đếm giờ, chu kỳ 04:00 chạy **100% offline**

### Privacy Guard Gate:

| Loại | Quy tắc |
|---|---|
| **Local-Only (Cấm gửi)** | Device ID, IMEI, MAC, danh sách toàn bộ app trong máy, nội dung tin nhắn/thông báo Accessibility, backup files, Keystore keys |
| **Cloud-Allowed** | Câu lệnh Ký chủ đã khử định danh, tên nhiệm vụ, thông số tu vi để nhập vai |

### Hybrid Architecture:
- Offline/mất key → chuyển về Rule-based intent, app cốt lõi vẫn dùng bình thường
- Có key & online → mở full tính năng đàm thoại AI

### Khi toàn bộ key lỗi:
> "【Linh Trí Hệ Thống】Không thể kết nối với nguồn năng lượng AI. Vui lòng kiểm tra API Key hoặc kết nối mạng." + Nút vào cài đặt API  
> **Cấm silent switch** sang provider chưa cấu hình.

---

## CHECKLIST 16 — XỬ LÝ LỖI, GIỌNG NÓI, BARGE-IN

### Error Handling:
- Retry tối đa 3 lần với Exponential Backoff
- Lỗi nội bộ an toàn 100% → tự sửa và ghi log
- Không thể phục hồi → thông báo trung thực, **không bao giờ claim thành công ảo**
- Lỗi bước 2 → giữ nguyên kết quả bước 1 đã commit

### Voice & Barge-in:
- **Barge-in:** Phát hiện giọng nói Ký chủ qua VAD → lập tức ngắt toàn bộ âm thanh Khí Linh (Stop TTS)
- **Timeout 4 giây:** Ký chủ dừng nói quá 4s → đóng mic, đưa văn bản nhận dạng vào ô chat để gõ tiếp
- **Sự kiện nền:** Chờ Ký chủ nói xong mới thông báo (chỉ lỗi nghiêm trọng mới ngắt lời ngay)

---

## CHECKLIST 17 — UI/UX & HÌNH THÁI KHÍ LINH

### Theme:
- **Dark Cosmic + Cultivation Fog**
- Màu chủ đạo: **Xanh lam kỹ thuật số (Cyan/Neon Blue)**
- Màu nhấn: **Tím linh lực (Mystic Purple)**

### Hệ Thống Phẩm Cấp (Rarity):

| Hạng | Hiệu Ứng |
|---|---|
| **Thường (Trắng/Xám)** | Popup nhẹ nhàng, tiếng click số hóa |
| **Hiếm (Xanh lam)** | Hào quang xanh phát sáng, hạt năng lượng nhẹ |
| **Sử Thi (Tím)** | Nhiều tầng hào quang tím, rung chấn nhẹ, linh lực hội tụ |
| **Huyền Thoại (Vàng Hoàng Kim)** | Screen Dimming, điểm sáng bùng nổ hoàng kim, bảng Hệ Thống công bố, giọng thiếu nữ Anime Echo/Reverb: "Chúc mừng ký chủ, đã nhận được vật phẩm Huyền Thoại!", có nút Skip |

### Khí Linh — Quả cầu phát sáng (giai đoạn hiện tại):
- Luôn phát sáng, nhấp nháy thở nhẹ (`Gentle Pulse`)
- Chạm 1 lần → hiện thoại chữ: "Ký chủ làm gì vậy..." (tự tắt sau 3s)
- Chạm 2 lần → mở giao diện chat nếu AI sẵn sàng
- Đứng yên tại vị trí Ký chủ đặt; chỉ di chuyển trong sự kiện đặc biệt

### System Panel:
- Là **System Overlay** độc lập đè lên app bị khóa, **KHÔNG PHẢI Home Screen**
- Che phủ hoàn toàn, cấm chạm xuyên thấu (No touch-through)
- Bấm Back → về Home Android

---

## CHECKLIST 18 — DECISION REGISTRY & OPEN ITEMS

### Sổ Bộ Quyết Định Nội Bộ (FINAL):

| Mã | Chủ Đề | Quyết Định | Trạng Thái |
|---|---|---|---|
| DEC-001 | Gốc Chu Kỳ Hàng Ngày | 04:00:00 AM theo giờ thiết bị; không thông báo | FINAL |
| DEC-002 | Điều Kiện Xóa App Vault | ≥⌈2N/3⌉ nhiệm vụ (N=2 cần 1) | FINAL |
| DEC-003 | Tháp Thí Luyện | Vĩnh cửu, không reset 04:00; bất biến; thưởng chẵn/lẻ cố định | FINAL |
| DEC-004 | Cơ Chế Mua Hàng | 1 vé không confirm; ≥2 vé bắt buộc confirm; trừ điểm độc lập animation | FINAL |
| DEC-005 | Hạn Dùng 15 Ngày | Áp dụng khi xóa app; neo chặt vào mốc xóa đầu tiên | FINAL |
| DEC-006 | Tính Cách Khí Linh | Giọng thiếu nữ anime trong trẻo có echo; chủ động khi có sự kiện đáng chú ý | FINAL |
| DEC-007 | Bản Thể Khí Linh | Quả cầu phát sáng nhấp nháy; chạm 1 lần hiện "Ký chủ làm gì vậy..." | FINAL |
| DEC-008 | Cơ Chế Barge-in | Ưu tiên tuyệt đối giọng Ký chủ; ngắt ngay TTS khi cất lời | FINAL |
| DEC-009 | Công Thức Điểm Cũ Bí Cảnh | Đề xuất 1-3-1-3 hay 1-2-1-3 đã bị thay thế bởi +1D cố định | **SUPERSEDED** |

### Các Vấn Đề Chưa Khép Lại (OPEN — TUYỆT ĐỐI KHÔNG TỰ QUYẾT):

| ID | Chủ Đề | Hiện Trạng | Các Lựa Chọn | Ghi Chú |
|---|---|---|---|---|
| **OPEN-001** | Bể Rơi Đồ Bí Cảnh (Loot Pool) | Chỉ chốt: rơi ngẫu nhiên có thể trùng; chưa liệt kê danh mục ngoài Vé 24h | A: Chỉ Vé 24h app trong Bảo Khố; B: Bổ sung bình đan dược (+5D, +10D) | Chờ Ký chủ chốt danh mục |
| **OPEN-002** | Timeout Phiên Thoại | Đã chốt im lặng tự đóng phiên; chưa chốt số giây cụ thể | A: 10s; B: 15s; C: 30s | Chờ Ký chủ chọn |
| **OPEN-003** | Tần Suất Đồng Bộ Cloud Memory | Đã chốt Local-first; chưa chốt thời điểm đồng bộ | A: Tự động sau mỗi thay đổi khi có Wifi; B: Thủ công khi bấm nút | Chờ Ký chủ chọn chiến lược |

---

## PHẦN BẤT BIẾN TOÀN CỤC (SYSTEM INVARIANTS)

| Mã | Bất Biến |
|---|---|
| INV-APP-001 | App bị khóa khi và chỉ khi có ≥1 nhiệm vụ chưa hoàn thành trong chu kỳ VÀ không có vé hiệu lực |
| INV-APP-002 | App mới thêm vào Bảo Khố chưa gán nhiệm vụ → bắt buộc ở trạng thái Chưa Khóa |
| INV-TASK-001 | Nhiệm vụ không gán app phần thưởng → **không được tính vào mẫu số N** khi tính 2/3 |
| INV-CYCLE-001 | 04:00 AM **tuyệt đối không** làm suy giảm tiến trình Tháp Thí Luyện và Điểm tích lũy |
| INV-CYCLE-002 | Nhiệm vụ bắt đầu trước 04:00 AM, hoàn thành sau → bắt buộc tính thưởng cho chu kỳ cũ |
| INV-TOWER-001 | Tầng Tháp đã hoàn thành = IMMUTABLE; cấm chỉnh sửa và xóa bỏ |
| INV-TOWER-002 | Cấm tuyệt đối farm lại điểm ở các tầng Tháp đã qua |
| INV-SHOP-001 | Điểm và vé biến động theo nguyên tắc ACID **trước** hoặc độc lập với animation |
| INV-INVENTORY-001 | Túi Trữ Vật sức chứa vô hạn; không bao giờ chặn nhận đồ vì đầy túi |
| INV-PRIVACY-001 | Cấm truyền backup file hoặc ID phần cứng ra ngoài khi Ký chủ chưa duyệt |
| INV-AI-001 | Khí Linh không bao giờ tự ý mở khóa app hoặc xóa dữ liệu mà không có xác nhận |

---

## STATE MACHINES

### App Lock State Machine:
```
[Thêm vào Bảo Khố] → UNMANAGED_UNLOCKED
                            |
                    [Gán nhiệm vụ đầu tiên]
                            ↓
                    MANAGED_LOCKED ←─────────────────────────┐
                         |              |                     |
               [Đủ 2/3 Task]    [Kích hoạt Vé]             |
                    ↓                   ↓                    |
           UNLOCKED_BY_TASK   UNLOCKED_BY_TOKEN              |
                    |                   |                    |
           [04:00 AM Reset]    [Hết hạn Vé 24h]            |
                    |                   |                    |
                    └───────────────────┘                    |
                    (Nếu còn nhiệm vụ chưa xong) ───────────┘
```

### Task State Machine:
```
[Tạo] → PENDING ←──────────────────────┐
             |                          |
   [Xác nhận hoàn thành]    [Hoàn Tác / Reset 04:00]
             ↓                          |
         COMPLETED ─────────────────────┘
```

### Tower Floor State Machine:
```
[Khởi tạo] → UNLOCKED (Tầng hiện tại)
                     |
          [Chinh phục thành công]
                     ↓
          COMPLETED (IMMUTABLE — vĩnh viễn)
```

---

## EDGE CASES SPECIFICATION

| Mã | Tình Huống | Hành Vi Chuẩn Mực |
|---|---|---|
| EC-001 | App bị xóa khi nhiệm vụ liên kết vẫn còn | Nhiệm vụ giữ nguyên; app gỡ khỏi phần thưởng → "Chưa có phần thưởng"; app mở khóa ngay |
| EC-002 | Nhiệm vụ bị xóa khi app liên quan đang khóa | Tính toán lại; nếu không còn nhiệm vụ ràng buộc → mở khóa app ngay |
| EC-003 | Vé 24h hết hạn đúng lúc Ký chủ đang dùng app | Kích hoạt Overlay chặn + System Panel ngay |
| EC-004 | Tắt nguồn qua 5 ngày | Tính mốc 04:00 AM gần nhất; không replay 4 ngày cũ |
| EC-005 | Thay đổi múi giờ | 04:00 AM tự động tính theo múi giờ mới của thiết bị |
| EC-006 | Sập nguồn giữa animation nhận Huyền Thoại | Giao dịch đã commit DB từ trước; bật lại → đồ đã có trong Túi |
| EC-007 | Chọn Propagate tại Tầng 6 Tháp công thức | Tầng 6 nhận giá trị mới; tầng 7+ tính lại; tầng 1-5 (đã xong) bất biến |
| EC-008 | Vé của app bị xóa qua ngày 16 | Tự xóa khỏi Túi (hết 15 ngày) |
| EC-009 | Nhập sai cú pháp xóa toàn bộ Bộ nhớ | Từ chối; chỉ chấp nhận chính xác: `XÓA TẤT CẢ` |
| EC-010 | Mất mạng khi Khí Linh đang lập kế hoạch | Báo lỗi ngắn gọn, chuyển offline, không treo app |

---

## ACCEPTANCE CRITERIA (TIÊU CHUẨN NGHIỆM THU)

### Kịch bản 1: Quy Tắc Mở Khóa 2/3 (N=4)
- Given: TikTok liên kết 4 nhiệm vụ, đang MANAGED_LOCKED
- When: Hoàn thành lần lượt Nhiệm vụ 1, 2, 3
- Then:
  - K=2: Required=⌈2×4/3⌉=3. Vì 2<3 → TikTok vẫn khóa
  - K=3: Vì 3≥3 → TikTok mở khóa ngay (`MANAGED_UNLOCKED`)

### Kịch bản 2: Tháp Công Thức (Start=10, Increment=10, Unit="lần")
- T1 = 10 lần → ⭐1D; T2 = 20 lần → ⭐2D; T3 = 30 lần → ⭐1D; T4 = 40 lần → ⭐3D
- Sửa T2 (đã xong) → hệ thống từ chối (IMMUTABLE)

### Kịch bản 3: Toàn Vẹn Giao Dịch Khi Sập Nguồn
- Có 10D, mua 1 Vé TikTok 24h giá 2D
- Trừ 2D thành công → ngắt nguồn giữa animation
- Khởi động lại → Số dư 8D + 1 Vé TikTok 24h trong Túi. Không mất điểm oan.

---

## TRACEABILITY MATRIX (TÓM TẮT)

| Requirement ID | Phân Hệ | Giao Diện | Schema |
|---|---|---|---|
| PROD-001 | Core Architecture | Theme / Particles | system_meta |
| CYCLE-001 | Cycle Engine | None (Background) | cycle_history |
| VAULT-003 | Vault Module | Vault Detail Dialog | vault_apps, task_rewards |
| TOWER-002 | Tower Engine | Tower Detail View | tower_floors |
| TOWER-004 | Tower Engine | Tower Floor Item | tower_floors.reward |
| SHOP-002 | Shop Module | Purchase Dialog | inventory_items |
| INV-002 | Inventory Module | Inventory Item Badge | inventory_items.expire_at |
| AI-002 | Khí Linh AI Agent | Voice / Chat Overlay | agent_plans |
| SAFE-001 | Safety Engine | Confirmation Dialog | system_logs |

---

## RANH GIỚI TRIỂN KHAI (IMPLEMENTATION BOUNDARY)

### BẮT BUỘC THỰC THI:
1. ✅ Chuẩn xác Logic Chu Kỳ 04:00 AM (không replay; task bắt đầu trước 04:00 → chu kỳ cũ)
2. ✅ Nguyên tắc khóa app: Chỉ khóa khi có nhiệm vụ chưa hoàn thành; mở khóa ngay khi hết ràng buộc
3. ✅ Quy tắc 2/3 khi xóa app khỏi Bảo Khố
4. ✅ 2 chế độ Tháp (Thủ công & Công thức), Exception vs Propagate, bất biến tầng đã xong
5. ✅ Giao dịch Thương Thành nguyên tử, tách rời animation và Room
6. ✅ Hạn dùng 15 ngày vé khi app bị xóa
7. ✅ Barge-in giọng nói và timeout 4 giây

### TUYỆT ĐỐI CẤM TRIỂN KHAI:
1. 🚫 Công thức điểm cũ Bí Cảnh (1-3-1-3 hay 1-2-1-3) — SUPERSEDED
2. 🚫 AI tự ý mở khóa app hoặc xóa dữ liệu không có xác nhận
3. 🚫 Màn hình xác nhận nhiệm vụ dạng danh sách độc lập (chỉ trong luồng System Panel)
4. 🚫 Giới hạn sức chứa Túi Trữ Vật
5. 🚫 Tự khôi phục liên kết nhiệm vụ cũ khi app xóa rồi thêm lại
6. 🚫 Farm lại điểm ở tầng Tháp đã vượt qua

### CHỜ QUYẾT ĐỊNH (giữ nguyên, không hardcode):
- OPEN-001: Danh mục loot drop Bí Cảnh
- OPEN-002: Số giây timeout phiên thoại
- OPEN-003: Chiến lược đồng bộ Cloud Memory

### TỰ DO KỸ THUẬT:
- Cấu trúc bảng Room DB, câu lệnh SQL
- Thư viện render animation (Lottie/Canvas/Compose)
- Kiến trúc (Clean Architecture/MVVM/MVI)
- Cấu hình Lazy Generation cho Tháp công thức

---

## KẾT LUẬN PHASE 0A

**Trạng thái:** ✅ COMPREHENSION COMPLETE

Tôi đã đọc và hiểu đầy đủ toàn bộ nội dung 30 phần (630 đoạn văn) của MASTER SSOT phiên bản 1.0.0.

**Những điểm cốt lõi cần ghi nhớ:**
1. Quy tắc 2/3 với đặc xá N=2 và công thức `⌈2N/3⌉` cho N≥3
2. INV-TASK-001: Nhiệm vụ không gán app phần thưởng **không tính vào mẫu số N**
3. DEC-009 = SUPERSEDED — công thức 1-3-1-3 và 1-2-1-3 đã bị thay thế bởi +1D cố định
4. 3 OPEN items (OPEN-001, OPEN-002, OPEN-003) chưa được phép tự quyết
5. Tháp Thí Luyện là trục tiến trình vĩnh cửu — IMMUTABLE sau khi hoàn thành tầng
6. System Panel là Overlay, Back Button = `Intent.ACTION_MAIN` về Home Android
7. Mọi giao dịch phải commit ACID TRƯỚC animation
8. Privacy Guard: Device ID, IMEI, MAC, danh sách app → KHÔNG gửi Cloud

**SSOT có thẩm quyền tối cao. Mọi implementation drift so với SSOT là defect.**
