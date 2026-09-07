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
| **OPEN-01** | Công thức giải phong ấn "2/3 nhiệm vụ" | **OPEN** | Cơ chế “hoàn thành 2/3 nhiệm vụ” được dùng làm hướng mở khóa. Tuy nhiên, cách tính chính xác cho từng số lượng nhiệm vụ (đặc biệt 1, 2, 3, 4… nhiệm vụ), làm tròn lên hay làm tròn xuống, và các trường hợp đặc biệt chưa được đặc tả thành công thức cuối cùng. | Phase 20 đã hoàn thành tầng tích hợp `TaskAppEnforcementAdapter` giữa Mission/Vault và App Lock Core: Phân loại 3 nhóm app (`NON_VAULT_APP`, `VAULT_APP_UNLINKED`, `VAULT_APP_WITH_TASKS`), bảo vệ ranh giới cứng OPEN-01: dù hoàn thành 100% nhiệm vụ, trạng thái vẫn là `PENDING_OPEN_01` và `LOCKED_PENDING_BUSINESS_RULE`. Tuyệt đối không tự ý phát minh công thức 2/3, không rounding, không unlock. | **CHƯA XÁC ĐỊNH (RANH GIỚI AN TOÀN ĐÃ CÔ LẬP)** | **TUYỆT ĐỐI KHÔNG** tự chọn công thức toán học (`ceil(2N/3)`, `floor(2N/3)`, hay `round`). Phải chờ Ký chủ xác nhận bảng quy đổi cụ thể cho từng N. |
| **OPEN-02** | Công thức điểm & Phần thưởng cuối cùng | **OPEN** | Công thức điểm/mốc cuối cùng trong hệ thống phần thưởng nếu áp dụng quy tắc mốc “1đ, 2đ, 3đ…” đã từng nêu trong quá trình thảo luận nhưng chưa được mô tả đủ rõ thành công thức toán học hoàn chỉnh. | Code hiện tại hoàn toàn chưa có hệ thống điểm, bảng tích lũy hay phần thưởng (0 code). | **CHƯA CÓ** | **TUYỆT ĐỐI KHÔNG** tự ý tạo hệ thống điểm tu vi, combo chuỗi ngày hay trừ điểm vi phạm. Chờ Ký chủ phê duyệt công thức thưởng. |
| **OPEN-03** | Công thức chi tiết Tháp Thí Luyện (Ngoại lệ Tầng 4) | **OPEN** | Mỗi tầng Tháp là một mốc thử thách (ví dụ Tầng 1 = 10 cái, Tầng 2 = 20 cái). Tầng 4 là ngoại lệ đã được nêu; từ tầng 5 trở đi giữ công thức cũ. Tuy nhiên, quy tắc nhảy bậc chính xác ở Tầng 4 và công thức sinh tầng tự động chưa được chốt văn bản chi tiết. | Code hiện tại hoàn toàn chưa có phân hệ Tháp Thí Luyện (0 code). | **CHƯA CÓ** | **TUYỆT ĐỐI KHÔNG** tự suy diễn logic độ khó tầng 4 hoặc thuật toán sinh tầng. Giữ nguyên mô hình thiết kế mở. |
| **OPEN-04** | Chi tiết kỹ thuật App Lock sau POC | **OPEN** | Sau khi hoàn thành kiểm chứng POC trên Android 15 / iQOO Neo 10 (OriginOS 5), cần xác định chi tiết ranh giới kỹ thuật: các quyền hệ thống phụ trợ (UsageStats, Overlay, Battery Optimization), cơ chế chống kill tiến trình, và trải nghiệm người dùng khi bị chặn. | Code hiện tại đã xây dựng hạ tầng Accessibility + BlockingShieldOverlay + LockScreenActivity rất vững chắc (Phase 05–15). | **TECHNICAL FOUNDATION** *(Chưa gắn nghiệp vụ)* | Nền tảng thực thi hiện tại chỉ là Technical Foundation. Chờ Ký chủ xác nhận phạm vi quyền và trải nghiệm chặn khi chuyển sang Product Lock. |
| **OPEN-05** | Lược đồ Cơ sở Dữ liệu & Chiến lược Di chuyển (Migration) | **OPEN** | Chi tiết schema cơ sở dữ liệu quan hệ chính thức (Room DB: TaskEntity, AppEntity, TaskAppCrossRef, VoucherEntity...) và chiến lược migration/tương thích từ DataStore hiện tại sang SQLite/Room. | Phase 17–19 sử dụng Room Database (`AppDatabase`, `version = 1`, `AppDao`, `TaskDao`, `TaskAppCrossRefDao`, `DailyTaskCompletionDao`) cho Core Task và Vault Domain. Không thay đổi schema, không destructive migration. DataStore tiếp tục duy trì 100% cho App Lock runtime. | **PROPOSAL (TECHNICAL FOUNDATION)** *(OPEN-05 REMAINS OPEN)* | **BẮT BUỘC:** Room Database hiện tại là Nền tảng kỹ thuật phục vụ Checkpoint CP3/CP4/CP5/CP6, **KHÔNG ĐƯỢC ĐÓNG OPEN-05**. Schema chính thức và chiến lược di trú toàn diện cần Ký chủ phê duyệt trước khi áp dụng cho toàn bộ sản phẩm. |
| **OPEN-06** | Chính sách Lưu trữ & Đồng bộ Cloud (Memory/History) | **OPEN** | Chính sách lưu trữ (retention policy), thời hạn dọn dẹp lịch sử, ranh giới dữ liệu nào đồng bộ lên Cloud và dữ liệu nào bắt buộc giữ On-Device để bảo vệ quyền riêng tư. | Code hiện tại chỉ có in-memory ring buffer (200 sự kiện kỹ thuật trong `DiagnosticLogger.kt`), 100% on-device, không có network/cloud. | **CHƯA CÓ** *(Mới có in-memory log kỹ thuật)* | Tạm thời chỉ thiết kế interface cục bộ, không triển khai Cloud sync hay gửi dữ liệu ra ngoài khi chưa có chính sách retention từ Ký chủ. |
| **OPEN-07** | State Machine Giao diện / Animation / Audio Tokens | **OPEN** | Các trạng thái UI, hiệu ứng phát sáng item, popup item hiếm, hoạt ảnh chuyển động điểm/item, và tệp âm thanh (giọng nữ loli câu chúc mừng hoàn thành) cần được chuẩn hóa thành State Machine và bảng Audio Tokens cụ thể trước khi polish. | Giao diện đã có phong cách tiên hiệp tối giản cho Nhiệm Vụ Đường và Bảo Khố, điều hướng 3 tab (`MainActivity.kt`). Chưa có audio/animation assets. | **CHƯA CÓ** | Không viết code hiệu ứng đồ họa tiên hiệp giả lập khi chưa có bộ token và tài nguyên multimedia chính thức từ Ký chủ. |

---

## QUY TRÌNH ĐÓNG MỘT MỤC OPEN (CLOSING PROCEDURE)

Khi Ký chủ đưa ra quyết định chính thức cho một mục OPEN:
1. Ghi nhận quyết định mới vào [`docs/DESIGN_DECISIONS.md`](file:///c:/Code/self-discipline-poc-01/docs/DESIGN_DECISIONS.md).
2. Cập nhật đặc tả chi tiết vào [`docs/CANONICAL_DESIGN_V2.md`](file:///c:/Code/self-discipline-poc-01/docs/CANONICAL_DESIGN_V2.md) (ghi rõ ngày cập nhật và nội dung sửa đổi).
3. Đổi trạng thái mục tương ứng trong tệp này từ `OPEN` sang `RESOLVED` kèm liên kết tới quyết định thiết kế.
4. Ghi nhận sự kiện vào [`docs/CHANGELOG.md`](file:///c:/Code/self-discipline-poc-01/docs/CHANGELOG.md).
