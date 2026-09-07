**HỆ THỐNG TỰ KỶ LUẬT BẢN THÂN**
**CANONICAL DESIGN / MASTER SPECIFICATION**
*Bản tổng hợp đầy đủ các quyết định thiết kế đã được chốt trong quá trình trao đổi.*
**Mục đích: làm nguồn sự thật chuẩn để đối chiếu code và ngăn dự án đi chệch thiết kế.**

| **Thông tin** | **Giá trị** |
| --- | --- |
| Tên sản phẩm | Hệ Thống Tự Kỷ Luật Bản Thân |
| AI Core | Khí Linh — chỉ là một module bên trong app, không phải toàn bộ app |
| Nền tảng | Android |
| Thiết bị test chính | iQOO Neo 10 — Android 15 |
| IDE chính | Antigravity IDE |
| Phương pháp phát triển | Vibe Coding / AI-assisted development |
| Môi trường đã sẵn sàng | Windows 11, Git, Java, Android SDK API 35, ADB, thiết bị thật đã kết nối |
| Trạng thái hiện tại | Môi trường đã hoàn tất; cần Design Audit code hiện tại và hoàn tất POC App Detection trước khi xây Core lớn |




# 1. QUY TẮC SỬ DỤNG TÀI LIỆU NÀY

Tài liệu này là Canonical Design / Single Source of Truth (nguồn sự thật duy nhất) của dự án.
Code hiện tại không có quyền tự trở thành thiết kế mới chỉ vì AI đã viết code đó.
Khi code lệch tài liệu: ghi nhận điểm lệch → xác định tài liệu hay code sai → sửa bên sai → test lại.
Không sửa tài liệu chỉ để hợp thức hóa một implementation sai. Chỉ cập nhật tài liệu khi có quyết định thiết kế mới.
Mọi task AI lớn phải xác định module, phạm vi, tiền đề, tác động và cách test trước khi triển khai.
Những điểm chưa được chốt phải được đánh dấu OPEN/CHƯA CHỐT và không được AI tự bịa thành hành vi chính thức.

# 2. TẦM NHÌN VÀ MỤC TIÊU SẢN PHẨM

Ứng dụng giúp người dùng tự kỷ luật bằng cơ chế phong ấn các ứng dụng được chọn. Muốn sử dụng ứng dụng đang bị phong ấn, người dùng phải đáp ứng các nhiệm vụ/điều kiện đã thiết lập. Sản phẩm được thể hiện như một “hệ thống” mang phong cách truyện tranh/tiên hiệp/tu tiên, nhưng logic nghiệp vụ phải độc lập với hiệu ứng giao diện.
Triết lý phát triển: vừa làm app vừa học. Người dùng muốn hiểu mỗi bước làm gì, tại sao cần, lợi/hại, trade-off và ảnh hưởng tới hệ thống; AI có thể viết phần lớn code nhưng không được biến dự án thành một “hộp đen”.

# 3. PHÂN RANH GIỚI SẢN PHẨM VÀ MODULE


| **Module** | **Vai trò** | **Trạng thái / ưu tiên** |
| --- | --- | --- |
| Hệ thống phong ấn | Core: kiểm soát truy cập app khác | CORE |
| Nhiệm Vụ Đường | Core: tạo, quản lý và hoàn thành nhiệm vụ | CORE |
| Bảo Khố | Core: danh sách app được quản lý/phong ấn | CORE |
| Tu Luyện | Mở rộng: Bí Cảnh + Tháp Thí Luyện | MỞ RỘNG |
| Thương Thành | Mở rộng: dùng điểm mua/quyền lợi | MỞ RỘNG |
| Túi Trữ Vật | Mở rộng: lưu item/voucher | MỞ RỘNG |
| Khí Linh | AI Core: hiểu, lập kế hoạch, suy luận, quản lý memory | MỞ RỘNG/AI CORE |
| Memory | Lưu sở thích, quy tắc, lịch sử liên quan AI | Nền tảng cho Khí Linh |
| Safety/Permission | Kiểm soát quyền, confirmation, phạm vi hành động | Nền tảng |



# 4. LUỒNG CORE CỦA ỨNG DỤNG

Người dùng thêm app vào Bảo Khố.
Người dùng tạo nhiệm vụ trong Nhiệm Vụ Đường.
Người dùng mở nhiệm vụ và chọn một hoặc nhiều app trong Bảo Khố làm “Phần thưởng”/đối tượng được mở khóa.
Ngay khi app được liên kết với nhiệm vụ, app bắt đầu bị phong ấn theo trạng thái và quy tắc hiện hành.
Khi người dùng mở app bị phong ấn, hệ thống kiểm tra trạng thái phong ấn và các nhiệm vụ liên quan.
Các nhiệm vụ được hiển thị tuần tự; hoàn thành nhiệm vụ hiện tại thì tự chuyển sang nhiệm vụ chưa hoàn thành tiếp theo.
Khi đủ điều kiện mở khóa, hệ thống cho phép truy cập app.
Tại 04:00, hệ thống reset theo chu kỳ ngày.

# 5. NHIỆM VỤ ĐƯỜNG — TOÀN BỘ QUY TẮC ĐÃ CHỐT

Có khu vực “Nhiệm Vụ Đường”; nút thêm nhiệm vụ là nút nhỏ ở góc dưới bên phải.
Form thêm nhiệm vụ tối giản: chỉ cần tên nhiệm vụ + xác nhận.
Chạm vào một nhiệm vụ để mở giao diện chọn “Phần thưởng”.
“Phần thưởng” của nhiệm vụ thực chất là app được liên kết để mở khóa; nguồn lựa chọn lấy từ các app trong Bảo Khố mà người dùng muốn phong ấn.
Một nhiệm vụ có thể liên kết với nhiều app.
Một app có thể có nhiều nhiệm vụ; nếu app liên kết 2 nhiệm vụ thì tính là 2 nhiệm vụ riêng, không tự động gộp.
Khi người dùng hoàn thành một nhiệm vụ, nhiệm vụ đó biến khỏi danh sách nhiệm vụ chưa hoàn thành và hệ thống tự chuyển sang nhiệm vụ kế tiếp chưa hoàn thành.
Nhiệm vụ đã bị xóa trong lúc chuỗi đang chạy phải bị bỏ qua; hệ thống tự chuyển sang nhiệm vụ tiếp theo hợp lệ.
Không muốn spam thông báo “đã hoàn thành nhiệm vụ” cho từng bước; tiến trình có thể thể hiện trong bảng hệ thống/luồng nhiệm vụ.
Xác nhận hoàn thành nhiệm vụ nằm trong luồng khi người dùng bị chặn ở app đích, không phải một màn hình xác nhận độc lập ở Nhiệm Vụ Đường.
Nhiệm vụ phải được chính người dùng thực hiện/hoàn thành; ứng dụng không mặc định coi việc dùng voucher là hoàn thành nhiệm vụ.

# 6. BẢO KHỐ — TOÀN BỘ QUY TẮC

Bảo Khố là nơi người dùng thêm các ứng dụng muốn đưa vào cơ chế phong ấn.
Việc chọn app để phong ấn có hiệu lực ngay, không cần reload/restart mỗi lần.
Không hiển thị biểu tượng ổ khóa trên avatar app; trạng thái phong ấn biểu diễn bằng màu (đã thống nhất cách dùng màu để thể hiện trạng thái, ví dụ xanh cho trạng thái bị khóa).
Avatar app có phong cách giống item trong kho/túi đồ.
Xóa một app khỏi Bảo Khố phải loại app khỏi các nhiệm vụ liên quan.
Khi app được thêm lại vào Bảo Khố, app trở lại trạng thái “chưa phong ấn” trước khi người dùng liên kết nhiệm vụ mới.
Danh sách app dùng làm “Phần thưởng” của nhiệm vụ chỉ lấy từ app đang có trong Bảo Khố.

# 7. PHONG ẤN APP VÀ QUY TẮC MỞ KHÓA

Thiết kế nghiệp vụ cốt lõi: app được liên kết với nhiệm vụ thì bị phong ấn; khi người dùng mở app, hệ thống kiểm tra nhiệm vụ; sau khi đạt điều kiện, app được mở.
Một quyết định đã được đưa ra: cơ chế “hoàn thành 2/3 nhiệm vụ” được dùng làm hướng mở khóa. Tuy nhiên, cách tính chính xác cho từng số lượng nhiệm vụ (đặc biệt 1, 2, 3, 4… nhiệm vụ), làm tròn như thế nào, và các trường hợp đặc biệt chưa được đặc tả thành công thức cuối cùng trong cuộc trao đổi. Đây là OPEN và phải được chốt trước khi khóa implementation.

# 8. CHU KỲ NGÀY VÀ RESET 04:00

Reset hệ thống tại 04:00 mỗi ngày.
Không dùng cơ chế 24 giờ kể từ thời điểm hoàn thành.
Nhiệm vụ bắt đầu trước 04:00 thuộc chu kỳ cũ, kể cả khi hoàn thành sau 04:00.
Sau reset, nhiệm vụ trở lại trạng thái “Chưa hoàn thành” để chu kỳ mới có thể tính lại.

# 9. TU LUYỆN

“Tu Luyện” là tên module chứa các nội dung rèn luyện mở rộng, gồm Bí Cảnh và Tháp Thí Luyện.

## 9.1 Bí Cảnh

Mỗi nhiệm vụ Bí Cảnh cho 1 điểm.
Có thể mở rộng về sau với item ngẫu nhiên, nhưng không được tự làm thay đổi quy tắc điểm cơ bản nếu chưa có quyết định mới.

## 9.2 Tháp Thí Luyện

Mỗi tầng là một mốc thử thách; ví dụ tầng 1 = 10 chống đẩy, tầng 2 = 20 chống đẩy.
Mốc/tầng đã hoàn thành không được cho điểm lại.
Có phương án cho phép người dùng tự tạo tầng (Phương án A); trong phương án này có thể xóa tầng.
Nếu dùng công thức tăng tự động theo thứ tự, không áp dụng cơ chế xóa tầng tự do như Phương án A.
Thay đổi cấu hình/fomula chỉ áp dụng từ tầng chưa hoàn thành tiếp theo.
Tầng 4 là ngoại lệ đã được nêu; từ tầng 5 trở đi giữ công thức cũ theo quyết định trước đó.
Cần giữ quan hệ giữa công thức, tầng đã hoàn thành và trạng thái điểm khi implementation.

# 10. THƯƠNG THÀNH

Điểm tích lũy từ Tu Luyện được dùng trong Thương Thành.
Một cơ chế đã chốt: 1 điểm có thể đổi 24 giờ mở khóa.
Chạm avatar để chọn item; nếu mua 1 item thì không cần confirmation riêng.
Có điều khiển tăng/giảm số lượng ở cuối panel thông tin.
Khi mua: kiểm tra đủ điểm → trừ điểm → animation item vào Túi → animation cập nhật số điểm.
Nếu không đủ điểm: hiện thông báo kiểu hệ thống tu tiên.
Không được mua vượt số điểm hiện có.

# 11. TÚI TRỮ VẬT

Item hiển thị dạng ô avatar, không có text cố định bên dưới; chạm vào item để xem thông tin.
Grid tự thay đổi số cột theo kích thước màn hình.
Item có tuổi thọ 15 ngày; hết hạn thì mất.
Voucher gần hết hạn đổi màu đỏ.
Ưu tiên animation khi item hiếm hoặc khi item được đưa vào Túi.

# 12. VOUCHER / VÉ MỞ KHÓA

Nếu app được mở bằng voucher, nhiệm vụ không hiển thị trong luồng mở app và việc mở bằng voucher không được tính là hoàn thành nhiệm vụ.
Nếu app đang được mở bằng voucher rồi bị xóa khỏi Bảo Khố, voucher đang dùng bị hủy.
Voucher còn nằm trong Túi vẫn được giữ.
Voucher còn lại bắt đầu chu kỳ 15 ngày từ thời điểm bị hủy/được giải phóng theo quy tắc đã thống nhất.
Khi voucher hết hạn, hệ thống kiểm tra nhiệm vụ; nếu còn nhiệm vụ thì khóa app ngay và hiển thị nhiệm vụ.

# 13. UI/UX VÀ PHONG CÁCH

Phong cách: hệ thống trong truyện tranh/tiên hiệp/tu tiên.
Progress message theo tinh thần “Ký chủ đã hoàn thành 1/tổng số nhiệm vụ”.
Hoàn thành tất cả: “Chúc mừng ký chủ đã hoàn thành nhiệm vụ”.
Ký chủ muốn hạn chế notification spam sau mỗi nhiệm vụ; ưu tiên hiệu ứng trạng thái trong UI.
Hiệu ứng mong muốn: item phát sáng, popup đặc biệt cho item hiếm, animation item tuần tự, animation điểm, âm thanh sự kiện, giọng nữ phong cách loli cho câu hoàn thành toàn bộ nhiệm vụ.
Hiệu ứng không được trở thành logic nghiệp vụ và không được làm người dùng mất khả năng thao tác với trạng thái thật.

# 14. KIẾN TRÚC KỸ THUẬT ĐÃ ĐỊNH HƯỚNG


| **Tầng** | **Vai trò** |
| --- | --- |
| UI | Compose; Home, Nhiệm Vụ Đường, Bảo Khố, Cài đặt và các màn hình module |
| Business/Domain | Task, App Lock, Daily Cycle, Reward, dependency và trạng thái nghiệp vụ |
| Data | Local database + preferences; về sau có đồng bộ cloud theo nhu cầu |
| Android System | App detection, quyền Android, Accessibility nếu PoC xác nhận, notifications, device state |
| AI Core | Khí Linh: hiểu yêu cầu, planner, memory, ra quyết định, orchestration |
| Safety/Permission | Kiểm tra quyền hạn, confirmation, phạm vi hành động, giới hạn AI |


Stack định hướng: Kotlin + Jetpack Compose. Không cần over-engineer ngay từ đầu; tăng mức abstraction khi độ phức tạp thực sự xuất hiện.

# 15. AI: KIẾN TRÚC CLOUD + ON-DEVICE

Đã chốt sử dụng mô hình kết hợp Cloud + On-device. AI Router có thể chọn xử lý cục bộ cho việc nhẹ/riêng tư hoặc dùng Cloud cho yêu cầu phức tạp. Khi mất mạng, phần chức năng có thể chạy offline không được tự nhiên dừng chỉ vì AI Cloud không khả dụng.

# 16. MEMORY: KIẾN TRÚC CLOUD + ON-DEVICE

Đã chốt Memory kết hợp; dữ liệu riêng tư/nhạy cảm ưu tiên lưu trên thiết bị, dữ liệu cần đồng bộ có thể dùng Cloud. Memory, History và Rule không phải là cùng một thứ: History ghi lại việc đã xảy ra; Memory lưu thông tin có giá trị dùng về sau; Rule là quy tắc ưu tiên/quyết định.


# 17. KHÍ LINH — QUY TẮC RA QUYẾT ĐỊNH ĐÃ CHỐT

Phần này tổng hợp các câu hỏi A/B/C/D đã hỏi và lựa chọn của Ký chủ. Đây là phần quan trọng nhất để tránh mất thiết kế trong code.

| **ID** | **Tình huống / câu hỏi** | **Lựa chọn đã chốt** | **Quy tắc thiết kế** |
| --- | --- | --- | --- |
| Q1 | Một lần confirmation có thể bao phủ nhiều hành động trực tiếp liên quan? | D | Confirmation chỉ có hiệu lực với phạm vi hành động đã xác nhận; hành động mới phát sinh phải được đánh giá lại. |
| Q2 | Yêu cầu nhiều cách thực hiện, không có cách rõ ràng hơn? | C | Tự chọn dựa trên ngữ cảnh; không hỏi chỉ vì có nhiều phương án. |
| Q3 | Ký chủ sửa cách lựa chọn, ví dụ chọn theo độ khó thay vì ưu tiên? | C | Áp dụng ngay và xem đây là phản hồi về sở thích cho các tình huống tương tự. |
| Q4 | Ký chủ nói “lần này…” nhưng không muốn đổi mặc định? | C | Hiểu là ngoại lệ tạm thời, không đổi Memory mặc định. |
| Q5 | Không nói tạm thời/lâu dài khi đưa ra lựa chọn? | B | Mặc định xem là sở thích mới, nhưng chỉ một lựa chọn đơn lẻ vẫn không đủ để tự động ghi đè quy tắc chính thức khi có xung đột; xem các quy tắc sau. |
| Q6 | Memory cũ khác yêu cầu hiện tại? | D | Ưu tiên yêu cầu hiện tại; xem xét cập nhật Memory theo ngữ cảnh, không chống lại yêu cầu hiện tại bằng Memory. |
| Q7 | Nếu cùng một lựa chọn lặp lại nhiều lần trong tình huống tương tự? | D | Tự nhận ra xu hướng và cập nhật Memory khi đủ bằng chứng. |
| Q8 | Nếu bằng chứng chưa đủ, ví dụ 2 lần A/2 lần B? | A | Giữ Memory cũ và không làm gì. |
| Q9 | Một câu đơn lẻ trái Memory có tự đổi Memory không? | B | Không. Một câu đơn lẻ không đủ để tự động thay đổi Memory lâu dài. |
| Q10 | Nhiều lựa chọn lặp lại tạo quy tắc mới thì lưu thế nào? | B | Lưu quy tắc tạm thời, quan sát thêm, đủ chắc chắn mới nâng thành chính thức. |
| Q11 | Quy tắc tạm thời mâu thuẫn quy tắc chính thức? | C | Đánh giá phù hợp/độ tin cậy/lịch sử; chưa đủ cơ sở thì giữ quy tắc cũ. |
| Q12 | Ký chủ nói “Từ giờ ưu tiên B”? | B | Cập nhật ngay quy tắc chính thức. |
| Q13 | Chỉ thị “trong xử lý lỗi dữ liệu, từ giờ ưu tiên B”? | B | Tạo/cập nhật quy tắc chuyên biệt; không thay quy tắc chung. |
| Q14 | Nhiều quy tắc chuyên biệt cùng áp dụng? | C | Quy tắc cụ thể và phù hợp trực tiếp hơn được ưu tiên. |
| Q15 | Hai quy tắc cùng mức độ cụ thể nhưng mâu thuẫn? | C | Phân tích cấp độ, độ tin cậy, lịch sử, bối cảnh, hậu quả trước khi hỏi. |
| Q16 | Phân tích xong vẫn không thể phân giải xung đột? | D | Hỏi Ký chủ và đưa đề xuất của Khí Linh. |
| Q17 | Ký chủ đã chọn trong một xung đột, có biến thành quy tắc ngay không? | B | Áp dụng lần này và ghi nhận làm dữ liệu cho tương lai; không tự biến thành quy tắc chính thức từ một lần. |
| Q18 | Sau đó Ký chủ nói ngược lại nhưng không nói đổi quy tắc? | C | Coi là ngoại lệ tiềm năng, tiếp tục quan sát. |
| Q19 | Ký chủ nói rõ “lần này là ngoại lệ”? | B | Áp dụng ngoại lệ, không đổi quy tắc chung. |
| Q20 | Ký chủ nói “từ giờ trường hợp này luôn…”? | B | Tạo/cập nhật quy tắc chuyên biệt chính thức. |
| Q21 | Ký chủ nói “từ giờ luôn…”? | A | Thay đổi quy tắc chung ngay. |
| Q22 | Quy tắc chung mới mâu thuẫn quy tắc chuyên biệt cũ? | C | Giữ quy tắc chuyên biệt nếu nó cụ thể hơn, trừ khi chỉ thị mới rõ ràng bao phủ/thay thế nó. |
| Q23 | Có thể tự xử lý vấn đề để đạt mục tiêu? | C | Tự sửa nếu chắc chắn lỗi/cách sửa; ghi lịch sử; hành động lớn vẫn theo permission/confirmation. |
| Q24 | Tự chọn giữa phương án thành công 70% nhanh và 95% lâu? | C | Cân bằng mục tiêu + an toàn + tỷ lệ thành công + thời gian + tài nguyên + ngữ cảnh. |
| Q25 | Phương án đã chọn thất bại? | D | Tự phân tích, thử phương án thay thế; chỉ hỏi khi hết phương án hoặc phát sinh quyết định lớn. |
| Q26 | Nếu phải chuyển phương án giữa chừng? | D | Tự xử lý nhưng lưu toàn bộ nhật ký kỹ thuật để xem lại khi cần. |
| Q27 | Phát hiện lỗi sau khi tự làm? | C | Nếu chắc chắn và an toàn thì tự sửa; ghi lỗi + cách sửa vào lịch sử. |
| Q28 | Phát hiện lỗi sau khi yêu cầu đã kết thúc? | D | Nếu lỗi thuộc yêu cầu và sửa trong quyền tự chủ, tự sửa, không cần làm phiền Ký chủ; ghi lịch sử. |
| Q29 | Đã có yêu cầu mới trong lúc phát hiện lỗi cũ? | C + quy tắc bổ sung | Nếu sửa an toàn và không ảnh hưởng yêu cầu mới thì làm song song; nếu xung đột thì ưu tiên yêu cầu mới. |
| Q30 | Nhiều yêu cầu mới liên tiếp? | D | Việc độc lập chạy song song nếu có thể; nếu không thể thì theo thứ tự ra lệnh. |
| Q31 | Ký chủ nói “làm việc D trước”? | D | D ưu tiên cao nhất; tác vụ cũ chỉ tiếp tục nếu không cản trở D. |
| Q32 | Tác vụ nền chạy khi Ký chủ hỏi chuyện? | C | Tác vụ nền tiếp tục; Khí Linh xử lý hội thoại đồng thời nếu hệ thống cho phép. |
| Q33 | Tác vụ nền hoàn thành trong lúc Ký chủ đang nói? | C | Không ngắt lời; đợi Ký chủ nói xong, rồi chỉ thông báo nếu đáng chú ý. |
| Q34 | Tác vụ nền lỗi trong lúc Ký chủ đang nói? | C | Lỗi thường chờ; lỗi nghiêm trọng có thể ngắt lời nếu trì hoãn gây hậu quả đáng kể. |
| Q35 | Tác vụ nền lỗi nhưng tự sửa được? | D | Tự sửa + chạy lại + ghi lịch sử; chỉ báo khi ảnh hưởng đáng kể/không tự khắc phục/cần quyền. |
| Q36 | Sau sửa lỗi, kết quả khác dự kiến? | C | Đánh giá; nếu vẫn phù hợp mục tiêu thì chấp nhận; nếu làm đổi mục tiêu thì dừng và xử lý theo confirmation. |
| Q37 | Có A→B và C độc lập? | D | A→B tuần tự; C chạy song song nếu có thể. |
| Q38 | A thất bại, B phụ thuộc A? | B + điều kiện | Tự khắc phục A; A thành công thì B tiếp tục; A không hoàn thành thì hủy B. |
| Q39 | B bị hủy do A thất bại, lưu lịch sử thế nào? | D | Giữ B trong lịch sử với trạng thái hủy + nguyên nhân + quan hệ A→B. |
| Q40 | Chuỗi A→B→C→D: A thất bại không khắc phục được? | D | Hủy toàn bộ chuỗi phía sau theo thứ tự. |
| Q41 | Khôi phục chuỗi bị hủy? | C | Khôi phục toàn bộ, nhưng chỉ chạy mỗi tác vụ khi tiền đề được đáp ứng. |
| Q42 | Yêu cầu “khôi phục từ B” trong A→B→C→D? | D | Khôi phục từ đầu chuỗi A rồi tới B→C→D. |
| Q43 | Khôi phục nhiều chuỗi độc lập? | C | Các chuỗi độc lập có thể song song; bên trong mỗi chuỗi giữ dependency order. |
| Q44 | Đang khôi phục mà Ký chủ nói “dừng chuỗi”? | C | Dừng an toàn: tác vụ không thể dừng thì hoàn thành; phần chưa chạy dừng; giữ trạng thái để tiếp tục. |
| Q45 | Ký chủ nói “hủy chuỗi”? | B | Hủy chuỗi; tác vụ chưa chạy bị hủy, tác vụ đang chạy kết thúc/dừng an toàn; không tự tiếp tục; lịch sử vẫn giữ. |
| Q46 | Sau khi hủy, Ký chủ nói “tiếp tục chuỗi”? | C | Tiếp tục từ trạng thái cuối cùng trước khi hủy nếu còn hợp lệ. |
| Q47 | Dữ liệu đã thay đổi trong thời gian chuỗi bị hủy? | C | Tự kiểm tra; tương thích thì dùng dữ liệu hiện tại, xung đột thì xử lý phần xung đột trước. |
| Q48 | Xung đột có nhiều cách xử lý? | C | Đánh giá mục tiêu, toàn vẹn dữ liệu, ảnh hưởng, trạng thái và chọn phương án phù hợp. |
| Q49 | Không đủ thông tin để biết dữ liệu nào đúng? | C | Không đoán; tạm dừng phần xung đột và hỏi Ký chủ. |
| Q50 | Cách hỏi khi bắt buộc hỏi? | C | Tự tổng hợp thông tin cần thiết + đề xuất + câu hỏi ngắn gọn đủ để quyết định. |
| Q51 | Ký chủ không trả lời câu hỏi bắt buộc? | A | Duy trì câu hỏi chờ và nhắc lại khi cần; không tự ý chọn thay. |
| Q52 | Ký chủ nói “bỏ qua đi” đối với câu hỏi đang chờ? | D | Theo quyết định đã chốt: xóa câu hỏi và lịch sử liên quan, kết thúc yêu cầu đó. |
| Q53 | Bỏ qua B nhưng B là tiền đề? | C | Xóa/bỏ B và xử lý các tác vụ phụ thuộc theo quan hệ phụ thuộc; không mặc định ảnh hưởng tác vụ độc lập. |
| Q54 | B có C,D phụ thuộc nhưng một cái thực sự không cần B? | C | Đánh giá từng tác vụ; chỉ hủy tác vụ thực sự cần B. |
| Q55 | C có nhiều tiền đề A+B bắt buộc; A bỏ qua? | C | Tự khắc phục A; nếu A không hoàn thành thì hủy C. |
| Q56 | C có A+B bắt buộc và C khác chỉ tùy chọn? | C | Nếu tiền đề bắt buộc đủ thì chạy; điều kiện tùy chọn không chặn. |
| Q57 | Không biết điều kiện là bắt buộc hay tùy chọn? | C | Tự phân tích ngữ cảnh và quy tắc; chỉ hỏi nếu không thể xác định đủ tin cậy. |
| Q58 | Đã đánh giá sai điều kiện và tác vụ chạy? | C | Đánh giá trạng thái; nếu dừng an toàn thì dừng; nếu không, hoàn thành rồi xử lý hậu quả. |
| Q59 | Tự khắc phục có hậu quả lớn? | C | Đánh giá tổng thể rủi ro/hậu quả và chọn phương án an toàn nhất trong quyền tự chủ. |
| Q60 | Đánh đổi rủi ro thấp/thành công thấp vs rủi ro cao/thành công cao? | D | Hỏi Ký chủ và đưa đề xuất; không tự quyết thay khi đánh đổi lớn. |
| Q61 | Ký chủ từng có nguyên tắc ưu tiên tương tự? | C | Kiểm tra tương đồng; nếu thực sự cùng nguyên tắc thì tự áp dụng, nếu khác đáng kể thì hỏi. |
| Q62 | Lặp lại cùng kiểu lựa chọn nhiều lần? | B | Hình thành quy tắc ưu tiên từ lựa chọn lặp lại. |
| Q63 | Hình thành quy tắc mới từ hành vi? | B | Quy tắc tạm thời trước, quan sát rồi mới chính thức. |
| Q64 | Quy tắc tạm thời mâu thuẫn quy tắc chính thức? | C | Phân tích; chưa đủ cơ sở thì giữ quy tắc chính thức. |
| Q65 | Ký chủ nói rõ đổi quy tắc? | B | Cập nhật ngay quy tắc chính thức. |
| Q66 | Quy tắc chuyên biệt vs quy tắc chung? | C | Quy tắc chuyên biệt cụ thể hơn được ưu tiên trừ khi chỉ thị mới bao phủ/thay thế. |
| Q67 | Hai quy tắc ngang nhau? | C | Tự phân tích cấp độ/độ tin cậy/lịch sử/bối cảnh/hậu quả trước khi hỏi. |
| Q68 | Không phân giải được sau phân tích? | D | Hỏi Ký chủ + đề xuất. |
| Q69 | Ký chủ giải quyết xung đột một lần? | B | Áp dụng lần này và ghi nhận làm dữ liệu tương lai, không tự chính thức hóa. |
| Q70 | Lựa chọn ngược lại nhưng không tuyên bố đổi quy tắc? | C | Xem như ngoại lệ tiềm năng, quan sát thêm. |
| Q71 | Nói “lần này là ngoại lệ”? | B | Ngoại lệ có chủ đích; không thay đổi quy tắc chung. |
| Q72 | Nói “từ giờ trường hợp này luôn…”? | B | Tạo/cập nhật quy tắc chuyên biệt chính thức. |
| Q73 | Nói “từ giờ luôn…”? | A | Cập nhật quy tắc chung ngay. |
| Q74 | Quy tắc chung mới mâu thuẫn quy tắc chuyên biệt? | C | Giữ chuyên biệt cụ thể hơn trừ khi chỉ thị mới rõ ràng thay thế phạm vi đó. |
| Q75 | Phần còn lại của Khí Linh? | Tự quyết định | Ký chủ giao quyền tự quyết phần chưa được chốt; chỉ hỏi khi thực sự không thể suy ra ý định hoặc vượt quyền tự chủ. |



# 18. KHÍ LINH — MODEL QUYỀN TỰ CHỦ

Ký chủ giao mục tiêu → Khí Linh tự chọn cách tốt nhất để đạt mục tiêu.
Khí Linh không bị giới hạn vào một workflow cứng nếu có phương án tốt hơn phù hợp mục tiêu.
Tự chủ không đồng nghĩa được vượt quyền, xóa dữ liệu lớn, đổi mục tiêu hoặc thực hiện hành động ngoài phạm vi confirmation.
Mọi hành động mới phát sinh ngoài phạm vi confirmation phải được đánh giá lại.
Khi không đủ thông tin để suy ra intent, không tự đoán; tạm dừng phần xung đột và hỏi.
Khi bắt buộc hỏi, câu hỏi phải ngắn, đủ thông tin và có đề xuất.

# 19. KHÍ LINH — MEMORY VÀ HỌC SỞ THÍCH


| **Loại** | **Ý nghĩa** | **Quy tắc** |
| --- | --- | --- |
| History | Ghi lại việc đã xảy ra | Có thể rất chi tiết; không đồng nghĩa Memory |
| Memory | Thông tin/sở thích có giá trị dùng về sau | Không đổi chỉ vì một lựa chọn đơn lẻ trái quy tắc |
| Temporary Rule | Xu hướng mới chưa đủ chắc | Quan sát thêm trước khi chính thức |
| Official Rule | Quy tắc đã được xác lập | Chỉ đổi ngay khi Ký chủ chỉ thị rõ hoặc bằng chứng đủ mạnh theo cơ chế |
| Exception | Ngoại lệ có chủ đích | Áp dụng lần đó, không làm đổi quy tắc chung |



# 20. KHÍ LINH — LỊCH SỬ, NHẬT KÝ KỸ THUẬT VÀ TỰ PHỤC HỒI

Lịch sử kỹ thuật lưu toàn bộ quá trình khi AI tự đổi phương án, lỗi, nguyên nhân, lần thử, kết quả và dependency chain.
Giao diện mặc định không cần hiển thị toàn bộ log; có thể mở chi tiết khi Ký chủ muốn kiểm tra.
Lỗi có thể tự sửa an toàn thì tự sửa; lỗi không chắc chắn hoặc có ảnh hưởng lớn thì không tự ý.
Sửa lỗi sau khi yêu cầu đã “xong” vẫn có thể được coi là continuation của yêu cầu nếu nằm trong scope và quyền tự chủ.
Không để rollback làm thiệt hại lớn hơn lỗi ban đầu; đánh giá trạng thái thực tế trước.

# 21. KHÍ LINH — QUẢN LÝ TÁC VỤ, DEPENDENCY, SONG SONG

Việc độc lập được chạy song song nếu hệ thống cho phép.
Việc phụ thuộc phải chờ tiền đề.
Nếu không thể chạy song song, giữ thứ tự Ký chủ đã giao.
Yêu cầu mới được chỉ rõ “làm trước” có ưu tiên cao nhất; tác vụ cũ chỉ tiếp tục nếu không cản trở.
Các tác vụ nền không chặn hội thoại nếu có thể xử lý đồng thời.
Không ngắt lời Ký chủ vì hoàn thành tác vụ nền, trừ lỗi nghiêm trọng có thể gây hậu quả đáng kể.
Khi tác vụ tiền đề thất bại và không khắc phục được, tác vụ phụ thuộc phải hủy; các tác vụ độc lập vẫn tiếp tục.

# 22. KHÍ LINH — TRẠNG THÁI DỪNG / HỦY / KHÔI PHỤC


| **Trạng thái** | **Ý nghĩa** |
| --- | --- |
| Dừng (Pause) | Tạm ngưng an toàn, giữ trạng thái để tiếp tục sau. |
| Hủy (Cancel) | Chấm dứt chuỗi, không tự tiếp tục; lịch sử vẫn giữ. |
| Tiếp tục (Resume) | Khôi phục từ trạng thái cuối trước khi dừng/hủy nếu còn hợp lệ. |
| Xóa lịch sử | Một hành động riêng; không đồng nghĩa với hủy. |
| Khôi phục chuỗi | Giữ quan hệ dependency, chỉ chạy tác vụ khi tiền đề hợp lệ. |
| Xung đột dữ liệu | Dùng dữ liệu hiện tại nếu tương thích; nếu xung đột thì xử lý xung đột trước; không tự ghi đè dữ liệu mới chỉ vì chuỗi cũ. |
| Không đủ thông tin | Tạm dừng phần xung đột và yêu cầu Ký chủ quyết định. |
| Bỏ qua yêu cầu đang chờ | Theo quyết định đã chốt: kết thúc yêu cầu, xóa câu hỏi và lịch sử liên quan. |



# 23. AN TOÀN, PERMISSION VÀ CONFIRMATION

Confirmation có phạm vi. Một lần xác nhận không cấp quyền vô hạn cho các hành động phát sinh.
Hành động đơn giản/rủi ro thấp trong quyền tự chủ có thể không cần hỏi.
Hành động lớn, thay đổi phạm vi, có khả năng mất dữ liệu hoặc vượt quyền phải đánh giá lại permission/confirmation.
Khi hành động có đánh đổi rủi ro lớn và không có quy tắc ưu tiên, hỏi Ký chủ và đưa đề xuất.
Không hỏi lại intent khi intent đã rõ; chỉ hỏi khi thông tin không đủ để quyết định an toàn.

# 24. MÔI TRƯỜNG KỸ THUẬT ĐÃ THIẾT LẬP


| **Thành phần** | **Trạng thái** | **Ghi chú** |
| --- | --- | --- |
| Windows 11 | OK | Máy phát triển |
| Antigravity IDE | OK | IDE chính cho Vibe Coding |
| Android Studio | OK | Dùng hỗ trợ Android SDK/công cụ khi cần |
| Java | OK | JDK 24.0.1; chưa tự ý thay thế, sẽ kiểm tra tương thích khi build |
| Git | OK | 2.55.0.windows.5 |
| Android SDK | OK | C:\Users\<WindowsUser>\AppData\Local\Android\Sdk |
| Android 15 / API 35 | OK | Đã cài |
| Platform-Tools / ADB | OK | ADB 1.0.41 / 37.0.1-15733141; PATH đã sửa |
| iQOO Neo 10 | OK | Đã kết nối ADB và làm thiết bị test chính |



# 25. POC-01 — ANDROID APP DETECTION

Mục tiêu: kiểm chứng trên Android 15 + iQOO Neo 10 khả năng nhận biết người dùng chuyển/mở một app khác.
Accessibility Service là ứng viên kỹ thuật để kiểm chứng, chưa được coi là quyết định implementation cuối trước khi PoC thành công.
POC không chứa nhiệm vụ, Bảo Khố, Tu Luyện, Thương Thành hay AI.
Chỉ khi POC xác nhận khả thi và hành vi đủ ổn định mới chốt nền tảng cho App Lock Core.
Bài học: rủi ro kỹ thuật lớn phải được kiểm chứng sớm trước khi xây cả hệ thống.

# 26. QUY TRÌNH VIBE CODING ĐÃ CHỐT

Xác định task nhỏ và mục tiêu rõ.
Đọc tài liệu canonical và xác định scope.
Giải thích trước: làm gì, tại sao, lợi/hại, ảnh hưởng.
AI Agent triển khai trong Antigravity.
Build và test trên iQOO Neo 10.
Đọc lỗi, xác định nguyên nhân, không sửa mù.
Review thay đổi với Git.
Commit khi đạt trạng thái ổn định.
Không giao AI “xây toàn bộ app” mà thiếu đặc tả/checkpoint.

# 27. CHECKPOINTS ĐỂ NGĂN DỰ ÁN ĐI CHỆCH


| **Checkpoint** | **Điều kiện đạt** |
| --- | --- |
| CP0 — Toolchain | SDK/API35, ADB, Git, IDE, thiết bị test hoạt động |
| CP1 — POC Detection | Xác minh được cơ chế phát hiện app trên Android 15/iQOO |
| CP2 — Skeleton | Project Android tối giản build/run được trên máy thật |
| CP3 — Core Task | Tạo và quản lý nhiệm vụ đúng đặc tả |
| CP4 — App Lock | Phong ấn + phát hiện app + màn hình chặn hoạt động |
| CP5 — Daily Cycle | Reset 04:00 đúng quy tắc |
| CP6 — Vault | App ↔ Task relations đúng thiết kế |
| CP7 — Expansion | Chỉ sau khi Core ổn định mới mở Tu Luyện/Thương Thành/Túi |
| CP8 — AI Core | Khí Linh được tích hợp sau khi Core/contract ổn định |



# 28. CÁC ĐIỂM ĐÃ TỪNG THẢO LUẬN NHƯNG CHƯA ĐỦ CHI TIẾT ĐỂ KHÓA CODE

OPEN-01: Công thức chính xác của “2/3 nhiệm vụ” cho mọi số lượng nhiệm vụ và cách làm tròn.
OPEN-02: Công thức điểm/mốc cuối cùng trong hệ thống phần thưởng nếu áp dụng quy tắc mốc “1d, 2d, 3…” đã từng nêu nhưng chưa mô tả đủ rõ thành công thức.
OPEN-03: Công thức chi tiết của Tháp Thí Luyện trước/sau ngoại lệ tầng 4.
OPEN-04: Chi tiết kỹ thuật App Lock sau POC-01; cần xác định quyền/API/hành vi Android 15/iQOO thực tế.
OPEN-05: Chi tiết schema Database chính thức và migration strategy.
OPEN-06: Chính sách retention/đồng bộ Cloud của Memory và History.
OPEN-07: Các trạng thái UI/animation/audio cần được biến thành state machine/tokens cụ thể trước khi polish.

# 29. DESIGN AUDIT — CÁCH XỬ LÝ CODE ĐANG ĐI CHỆCH

Đóng băng việc thêm feature mới trong phần Core cho tới khi audit xong.
Đối chiếu code hiện tại với tài liệu theo từng module.
Mỗi điểm lệch phân loại: Đúng / Thiếu / Sai / Thừa / Chưa xác định.
Nếu code sai → sửa code. Nếu design thật sự đã được Ký chủ thay đổi → cập nhật tài liệu trước rồi sửa code.
Không cho AI tự “lấp chỗ trống” bằng nghiệp vụ mới.
Sau khi audit, tạo một CHANGELOG/DECISION LOG để mọi thay đổi sau này có lý do rõ ràng.

# 30. TÓM TẮT MỘT TRANG

Sản phẩm: Hệ Thống Tự Kỷ Luật Bản Thân. Core: Bảo Khố + Nhiệm Vụ Đường + Hệ thống phong ấn + chu kỳ 04:00. Mở rộng: Tu Luyện, Bí Cảnh, Tháp Thí Luyện, Thương Thành, Túi Trữ Vật, Voucher, Khí Linh. Kỹ thuật: Android, Kotlin/Compose, Antigravity làm IDE/Vibe Coding, Git, Android SDK API 35, test trên iQOO Neo 10 Android 15. AI: Cloud + On-device, Memory hybrid. Khí Linh có quyền tự chủ cao nhưng bị giới hạn bởi Permission/Confirmation và scope; AI tự xử lý, tự phục hồi, quản lý dependency, chỉ hỏi khi không đủ thông tin hoặc quyết định vượt quyền. Dự án hiện cần Design Audit + POC App Detection trước khi tiếp tục mở rộng code.