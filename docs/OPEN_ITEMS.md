# DANH MỤC CÁC VẤN ĐỀ CHƯA CHỐT (OPEN ITEMS)

**Dự án:** Hệ Thống Tự Kỷ Luật Bản Thân (`self-discipline-poc-01`)  
**Tài liệu tham chiếu chuẩn:** [`docs/CANONICAL_DESIGN_V2.md`](file:///c:/Code/self-discipline-poc-01/docs/CANONICAL_DESIGN_V2.md) (Mục 28)  
**Ngày thiết lập:** 04/09/2026 (Phase 16)  
**Nguyên tắc tối cao:**  
- **RULE 6:** Các mục OPEN bắt buộc phải duy trì trạng thái `OPEN`.  
- **RULE 7:** AI tuyệt đối **KHÔNG ĐƯỢC TỰ Ý BỊA ĐẶT** hành vi hoặc công thức nghiệp vụ thay thế khi Ký chủ chưa đưa ra quyết định chính thức.  
- **Xử lý Implementation hiện tại:** Nếu mã nguồn hiện tại đang có một cách xử lý tạm thời đối với vấn đề OPEN, cách xử lý đó chỉ được đánh dấu là **CHƯA XÁC ĐỊNH**, tuyệt đối **KHÔNG ĐƯỢC TỰ BIẾN THÀNH CANONICAL RULE**.

---

## BẢNG QUẢN LÝ CÁC MỤC OPEN CHÍNH THỨC

| Mã ID | Tên Vấn Đề | Trạng Thái Canonical | Mô Tả Thiết Kế Chưa Chốt | Hiện Trạng Code Hiện Tại | Đánh Giá Trạng Thái Code | Ràng Buộc Bắt Buộc |
|:---:|:---|:---:|:---|:---|:---:|:---|
| **OPEN-01** | Công thức giải phong ấn "2/3 nhiệm vụ" | **CLOSED** | Cơ chế mở khóa theo tỷ lệ nhiệm vụ liên kết có hiệu lực hoàn thành trong ngày (chu kỳ 04:00). | Đã hoàn thành implement ở Phase 23 theo Quyết định Sản phẩm chính thức của Ký chủ: `TaskUnlockPolicy` với công thức integer arithmetic `required = (2 * N + 2) / 3`, bảng kiểm chứng $N = 0 \dots 10$, điều kiện mở khóa `completedTasks >= requiredCompletedTasks AND N > 0`. Tích hợp hoàn chỉnh vào `TaskAppEnforcementAdapter` với snapshot cache cho Main Thread của Accessibility Service. | **IMPLEMENTED (CLOSED)** | Tham chiếu: [`docs/DESIGN_DECISIONS.md`](file:///c:/Code/self-discipline-poc-01/docs/DESIGN_DECISIONS.md) (Mục 10). Quyết định chính thức bởi Ký chủ. |
| **OPEN-02** | Công thức điểm & Phần thưởng cuối cùng | **OPEN** | Công thức điểm/mốc cuối cùng trong hệ thống phần thưởng nếu áp dụng quy tắc mốc “1đ, 2đ, 3đ…” đã từng nêu trong quá trình thảo luận nhưng chưa được mô tả đủ rõ thành công thức toán học hoàn chỉnh. | Code hiện tại hoàn toàn chưa có hệ thống điểm, bảng tích lũy hay phần thưởng (0 code). | **CHƯA CÓ** | **TUYỆT ĐỐI KHÔNG** tự ý tạo hệ thống điểm tu vi, combo chuỗi ngày hay trừ điểm vi phạm. Chờ Ký chủ phê duyệt công thức thưởng. |
| **OPEN-03** | Công thức chi tiết Tháp Thí Luyện (Ngoại lệ Tầng 4) | **OPEN** | Mỗi tầng Tháp là một mốc thử thách (ví dụ Tầng 1 = 10 cái, Tầng 2 = 20 cái). Tầng 4 là ngoại lệ đã được nêu; từ tầng 5 trở đi giữ công thức cũ. Tuy nhiên, quy tắc nhảy bậc chính xác ở Tầng 4 và công thức sinh tầng tự động chưa được chốt văn bản chi tiết. | Code hiện tại hoàn toàn chưa có phân hệ Tháp Thí Luyện (0 code). | **CHƯA CÓ** | **TUYỆT ĐỐI KHÔNG** tự suy diễn logic độ khó tầng 4 hoặc thuật toán sinh tầng. Giữ nguyên mô hình thiết kế mở. |
| **OPEN-04** | Quyết định sản phẩm kỹ thuật App Lock sau POC (Technical App Lock product decision) | **CLOSED** | Xác định ranh giới sản phẩm: Technical App Lock (Schedule & Daily Limit) là Scoped Product Behavior (Hàng rào Cấm Tuyệt Đối - Hard Ceiling Guardrails) có độ ưu tiên cao nhất, kết hợp với giải pháp kỹ thuật Accessibility Service + Window Overlay trên Android 15. | Đã hoàn thành audit và đóng tại Phase 26: Khẳng định hạ tầng Accessibility + Overlay + LockScreen là Core Enforcement Engine chính thức; Schedule & Daily Limit có ưu tiên cấm tuyệt đối so với Business Unlock; phạm vi quyền Android giới hạn ở Accessibility và Overlay, không cần DeviceAdmin. | **DECIDED (CLOSED)** | Tham chiếu: [`docs/DESIGN_DECISIONS.md`](file:///c:/Code/self-discipline-poc-01/docs/DESIGN_DECISIONS.md) (Mục 12). Quyết định chính thức Phase 26. |
| **OPEN-05** | Lược đồ Cơ sở Dữ liệu & Chiến lược Di chuyển (Migration) | **OPEN** | Chi tiết schema cơ sở dữ liệu quan hệ chính thức (Room DB: TaskEntity, AppEntity, TaskAppCrossRef, VoucherEntity...) và chiến lược migration/tương thích từ DataStore hiện tại sang SQLite/Room. | Phase 17–19 sử dụng Room Database (`AppDatabase`, `version = 1`, `AppDao`, `TaskDao`, `TaskAppCrossRefDao`, `DailyTaskCompletionDao`) cho Core Task và Vault Domain. Không thay đổi schema, không destructive migration. DataStore tiếp tục duy trì 100% cho App Lock runtime. | **PROPOSAL (TECHNICAL FOUNDATION)** *(OPEN-05 REMAINS OPEN)* | **BẮT BUỘC:** Room Database hiện tại là Nền tảng kỹ thuật phục vụ Checkpoint CP3/CP4/CP5/CP6, **KHÔNG ĐƯỢC ĐÓNG OPEN-05**. Schema chính thức và chiến lược di trú toàn diện cần Ký chủ phê duyệt trước khi áp dụng cho toàn bộ sản phẩm. |
| **OPEN-06** | Chính sách Lưu trữ & Đồng bộ Cloud (Memory/History) | **OPEN** | Chính sách lưu trữ (retention policy), thời hạn dọn dẹp lịch sử, ranh giới dữ liệu nào đồng bộ lên Cloud và dữ liệu nào bắt buộc giữ On-Device để bảo vệ quyền riêng tư. | Hiện trạng code không có Cloud sync, log chẩn đoán kỹ thuật chỉ lưu in-memory ring buffer (200 sự kiện), không truyền dữ liệu ra ngoài. Quyết định sản phẩm: **OPEN**, bảo lưu hướng kiến trúc dài hạn hybrid Cloud + On-device theo Canonical Design V2 Mục 30. | **CHƯA CÓ** *(Mới có in-memory log kỹ thuật)* | Tạm thời chỉ thiết kế interface cục bộ, không triển khai Cloud sync hay gửi dữ liệu ra ngoài; không tự ý đóng băng kiến trúc thành "vĩnh viễn 100% on-device" khi chưa có quyết định chính thức từ Ký chủ. |
| **OPEN-07** | State Machine Giao diện / Animation / Audio Tokens | **OPEN** | Các trạng thái UI, hiệu ứng phát sáng item, popup item hiếm, hoạt ảnh chuyển động điểm/item, và tệp âm thanh (giọng nữ loli câu chúc mừng hoàn thành) cần được chuẩn hóa thành State Machine và bảng Audio Tokens cụ thể trước khi polish. | Giao diện đã có phong cách tiên hiệp tối giản cho Nhiệm Vụ Đường và Bảo Khố, điều hướng 3 tab (`MainActivity.kt`). Chưa có audio/animation assets. | **CHƯA CÓ** | Không viết code hiệu ứng đồ họa tiên hiệp giả lập khi chưa có bộ token và tài nguyên multimedia chính thức từ Ký chủ. |

---

## QUY TRÌNH ĐÓNG MỘT MỤC OPEN (CLOSING PROCEDURE)

Khi Ký chủ đưa ra quyết định chính thức cho một mục OPEN:
1. Ghi nhận quyết định mới vào [`docs/DESIGN_DECISIONS.md`](file:///c:/Code/self-discipline-poc-01/docs/DESIGN_DECISIONS.md).
2. Cập nhật đặc tả chi tiết vào [`docs/CANONICAL_DESIGN_V2.md`](file:///c:/Code/self-discipline-poc-01/docs/CANONICAL_DESIGN_V2.md) (ghi rõ ngày cập nhật và nội dung sửa đổi).
3. Đổi trạng thái mục tương ứng trong tệp này từ `OPEN` sang `RESOLVED` kèm liên kết tới quyết định thiết kế.
4. Ghi nhận sự kiện vào [`docs/CHANGELOG.md`](file:///c:/Code/self-discipline-poc-01/docs/CHANGELOG.md).
