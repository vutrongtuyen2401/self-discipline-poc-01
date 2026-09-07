# Cultivation UI Design System Foundation

## 1. Tổng Quan & Mục Tiêu (Overview & Objective)

Hệ thống **Cultivation UI Design System** được thiết kế riêng cho ứng dụng **Hệ Thống Tự Kỷ Luật Bản Thân** (`self-discipline-poc-01`), hòa quyện giữa ba yếu tố cốt lõi:
- **Xianxia / Cultivation (Tiên Hiệp / Tu Chân)**: Tông màu Huyền Mặc (đêm thâm trầm), Thanh Ngọc (linh khí bích ngọc), Kim Tinh / Hoàng Kim (cực phẩm, hoàn thành đại đạo), Chu Sa (phù triện phong ấn, kiêng kỵ), Tử Tiêu (linh phù liên kết).
- **RPG / Game Progression**: Phân cấp thẻ ngọc giản, thanh năng lượng linh lực phát quang mượt mà, lưới kho đồ / túi trữ vật thích ứng (adaptive grid), hiệu ứng hít thở linh lực nhẹ (subtle breathing glow), phản hồi xúc giác nảy nhẹ khi tương tác (press feedback).
- **Self Discipline (Tự Kỷ Luật)**: Giao diện thoáng đãng, cấu trúc thị giác phân minh, tập trung tối đa vào dòng chảy nhiệm vụ tuần tự (sequential quest flow), độ tương phản cao, chống xao nhãng.

> **Tuyên Bố Bản Quyền & Nguồn Gốc Mã Nguồn (License Caution)**:
> - Các repository tham khảo trên GitHub (`IdleFantasy`, `ASCENDANT / ClaudeFitness`, `NeoMud`, `eOr`) **CHỈ ĐƯỢC DÙNG LÀM TÀI LIỆU THAM KHẢO VỀ MẶT THIẾT KẾ VÀ KIẾN TRÚC GIAO DIỆN (REFERENCE ONLY)**.
> - Đặc biệt, `IdleFantasy` phát hành dưới giấy phép **GPL-3.0**. Dự án **TUYỆT ĐỐI KHÔNG sao chép bất kỳ dòng mã nguồn, module hay asset hình ảnh nào** từ các repository này vào mã nguồn sản phẩm.
> - Toàn bộ mã nguồn giao diện trong dự án được tự tay thiết kế và lập trình mới 100% bằng Jetpack Compose thuần túy, kế thừa nền tảng kỹ thuật Material 3 của Android.
> - **GitHub repositories are UI/architecture references only; no repository is a product dependency.**

---

## 2. Ma Trận Ánh Xạ Tham Khảo (Reference Mapping)

| Repository Tham Khảo | Mô Thức / Ý Tưởng Khảo Sát (Pattern Extracted) | Thành Phần Triển Khai Trong Dự Án (Implemented Component) | Nguyên Tắc Tách Bạch Bản Quyền |
| :--- | :--- | :--- | :--- |
| **IdleFantasy** (GPL-3.0) | Cấu trúc thẻ nhiệm vụ theo chuỗi (quest/progression), phân cấp trạng thái active vs completed. | `TaskCard`<br>`ProgressCard` | Tự viết mới 100% bằng Compose `Card` & `Canvas` gradient. Không dùng mã nguồn/asset của IdleFantasy. |
| **IdleFantasy** (GPL-3.0) | Bố cục thẻ vật phẩm / phần thưởng kho tàng (shop/item). | `ItemCard`<br>`CultivationDialog` | Thiết kế thẻ ngọc giản tối giản, phù hợp văn hóa tiên hiệp tu chân. |
| **ASCENDANT** / ClaudeFitness | Thanh đo tiến độ rèn luyện thể chất / tu thân (gamification progress). | `ProgressCard` | Sử dụng `animateFloatAsState` với dải màu Thanh Ngọc sang Kim Tinh. |
| **NeoMud** | Lưới ô túi trữ vật / kho trang bị (inventory grid). | `CultivationGrid`<br>`AppItemGridCard` | Lưới `LazyVerticalGrid` tự co giãn thích ứng (`GridCells.Adaptive`), hỗ trợ đa kích thước màn hình. |
| **eOr** | Trình bày biểu tượng và thông tin ô ứng dụng gọn gàng, cách điệu (artwork/grid). | `AsyncAppIcon`<br>`AppItemGridCard` | Tự xây dựng cache bất đồng bộ `AppIconMemoryCache` giải mã icon thật từ Android `PackageManager`. |

---

## 3. Nguyên Tắc Thiết Kế Thị Giác (Visual Principles)

1. **Xianxia / Comic RPG nhưng Không Lòe Loẹt (Restrained & Elegant)**:
   - Tránh các hiệu ứng gradient dày đặc hoặc ánh sáng chói mắt kiểu quảng cáo game di động rẻ tiền.
   - Ưu tiên các khối thẻ xếp tầng nhiều lớp (layered cards), chiều sâu nhẹ (soft depth), viền linh phù mờ và hào quang tinh tế (`borderGlow`).
2. **Kế Thừa Nền Tảng Kỹ Thuật Material 3**:
   - Sử dụng Material 3 làm technical foundation; không thay thế toàn bộ hay xung đột với Android Compose framework.
   - Cung cấp semantic tokens thông qua Jetpack Compose `CompositionLocalProvider` (`LocalCultivationColors`, `LocalCultivationTypography`, v.v.).
3. **Typography Rõ Ràng, Dễ Đọc (High Readability)**:
   - Tiêu chuẩn kích thước chữ và khoảng cách dòng chuẩn mực, không dùng font chữ cổ trang phức tạp gây mỏi mắt hay cắt cụt văn bản.
4. **Icon-Centric & Real App Presentation**:
   - Mọi ứng dụng được hiển thị bằng biểu tượng thật từ hệ điều hành Android qua `AsyncAppIcon`.
   - **Canonical Design V2 (Mục 6)**: TUYỆT ĐỐI KHÔNG DÙNG ICON Ổ KHÓA trên ứng dụng Bảo Khố.

---

## 4. Danh Sách Tokens Ngữ Nghĩa (Semantic Tokens)

### 4.1. Color Tokens (`CultivationColors`)

| Token | Tên Tiên Hiệp | Giá Trị Dark Theme | Giá Trị Light Theme | Vai Trò & Mục Đích |
| :--- | :--- | :--- | :--- | :--- |
| `background` | Huyền Mặc / Bạch Vân | `#090D16` | `#F4F6FB` | Nền tảng không gian tu luyện |
| `surface` | Mặc Ngọc / Bạch Ngọc | `#101626` | `#FFFFFF` | Bề mặt thành phần, thanh điều hướng |
| `elevatedSurface` | Phù Đảo Nâng Cao | `#172036` | `#F8FAFC` | Bề mặt nổi bật, card được chọn |
| `cardBackground` | Linh Ngọc Thẻ Bài | `#131B2E` | `#FFFFFF` | Nền thẻ ngọc giản tiêu chuẩn |
| `celestialGold` | Hoàng Kim Kim Tinh | `#F59E0B` | `#D97706` | Điểm nhấn chính, nhiệm vụ active, nút chính |
| `spiritTeal` | Thanh Ngọc Linh Khí | `#14B8A6` | `#0D9488` | Bảo Khố, hoàn thành, năng lượng tiến trình |
| `etherealIndigo` | Tử Tiêu Phù Lục | `#6366F1` | `#4F46E5` | Phù hiệu liên kết, hào quang viền |
| `crimsonSeal` | Chu Sa Phong Ấn | `#F43F5E` | `#E11D48` | Gỡ bỏ, xóa, hủy bỏ nguy hại |
| `borderSubtle` | Viền Phù Văn Mờ | `#263554` | `#E2E8F0` | Viền thẻ mặc định thanh nhã |
| `borderGlow` | Hào Quang Linh Lực | `#596366F1` (35%) | `#336366F1` (20%) | Hào quang viền hộp thoại / thẻ đang làm |
| `textPrimary` | Bạch Ngọc / Mặc Sắc | `#F8FAFC` | `#0F172A` | Tiêu đề và văn bản chính độ tương phản cao |
| `textSecondary` | Ngân Sương / Mực Vừa | `#CBD5E1` | `#334155` | Phụ đề, mô tả nhiệm vụ |
| `textMuted` | Huyền Khôi / Mực Nhạt | `#718096` | `#64748B` | Ngày giờ, package name, thông số mờ |
| `statusSuccess` | Ngọc Bích Viên Mãn | `#10B981` | `#059669` | Hoàn thành toàn bộ, trạng thái xanh |
| `statusDanger` | Đan Sa Báo Động | `#F43F5E` | `#E11D48` | Lỗi nhập liệu, cảnh báo gỡ ứng dụng |

### 4.2. Typography Tokens (`CultivationTypography`)
- `headingHero`: 26sp, Bold, Line height 32sp. Dành cho Tiêu đề phân hệ (Nhiệm Vụ Đường, Bảo Khố).
- `titleCard`: 18sp, SemiBold, Line height 24sp. Dành cho Tên nhiệm vụ, tiêu đề card, hộp thoại.
- `subtitle`: 14sp, Normal, Line height 20sp. Dành cho lời dẫn xưng hô Ký chủ.
- `bodyText`: 15sp, Normal, Line height 22sp. Dành cho nội dung giải thích, thông báo.
- `bodySecondary`: 13sp, Normal, Line height 18sp. Dành cho văn bản phụ.
- `labelRune`: 12sp, SemiBold, Letter spacing 0.8sp. Dành cho nhãn phù văn nổi bật ("NHIỆM VỤ ĐANG THỰC HIỆN", "VIÊN MÃN CÔNG ĐỨC").
- `caption`: 11sp, Normal. Dành cho ngày giờ, package name, nhãn badge.
- `button`: 14sp, SemiBold, Letter spacing 0.4sp. Dành cho nhãn nút bấm hành động.
- `statNumber`: 22sp, Bold, Line height 28sp. Dành cho số lượng thống kê tiến độ.

### 4.3. Shape Tokens (`CultivationShapes`)
- `card`: Góc bo 14dp thanh thoát.
- `cardElevated`: Góc bo 16dp cho thẻ nổi bật.
- `itemCell`: Góc bo 12dp cho ô app trong lưới Bảo Khố.
- `button`: Góc bo 10dp cho các nút bấm tu luyện.
- `chip`: Góc bo 6dp cho nhãn phù văn và huy hiệu liên kết.
- `dialog`: Góc bo 18dp cho bảng thông cáo ngọc giản.
- `avatar`: Góc bo 10dp cho khung biểu tượng ứng dụng.
- `circular`: Góc bo 50% (CircleShape) cho huy hiệu số, tick hoàn thành.

### 4.4. Spacing & Accessibility Tokens (`CultivationSpacing`)
- `xxs`: 2dp, `xs`: 4dp, `sm`: 8dp, `md`: 12dp, `lg`: 16dp, `xl`: 20dp, `xxl`: 24dp.
- `gridGap`: 12dp.
- `cardPadding`: 16dp.
- `touchTargetMin`: **48dp** (Tuân thủ nghiêm ngặt tiêu chuẩn Accessibility của Android).

### 4.5. Elevation Tokens (`CultivationElevation`)
- `none`: 0dp.
- `subtle`: 2dp.
- `card`: 2dp.
- `cardElevated`: 4dp.
- `dialog`: 8dp.

---

## 5. Danh Sách Reusable Core Components

1. **`CultivationCard`**:
   - Thẻ hiển thị nhiều tầng ngọc giản.
   - Các biến thể: `STANDARD`, `ELEVATED`, `ACTIVE_GLOW` (tích hợp viền thở hào quang linh lực), `OUTLINED`.
2. **`CultivationButton`**:
   - Nút bấm hành động tối ưu cảm ứng.
   - Các biến thể: `PRIMARY` (Hoàng Kim), `SECONDARY` (Thanh Ngọc), `GHOST` (Viền mờ tối giản), `DANGER` (Chu Sa phong ấn).
   - Tích hợp hiệu ứng nảy nhẹ (`pressScaleEffect`) và hỗ trợ trạng thái xoay loading.
3. **`CultivationDialog`**:
   - Hộp thoại bảng thông cáo ngọc giản với viền phát quang linh lực tím Tử Tiêu mờ, hỗ trợ nút xác nhận và hủy.
4. **`AsyncAppIcon`**:
   - Tải biểu tượng ứng dụng thật bất đồng bộ qua `Dispatchers.IO` kết hợp bộ nhớ đệm `AppIconMemoryCache` (`LruCache<String, ImageBitmap>`).
   - Đảm bảo 100% không chặn Main Thread (không jank/stutter).
   - Có cơ chế fallback hiển thị chữ cái đầu ngọc phù trang nhã nếu app không tồn tại hoặc lỗi tải icon.
5. **`CultivationGrid`**:
   - Lưới co giãn tự động theo kích thước màn hình thiết bị (`GridCells.Adaptive(minSize = 140.dp)`), hỗ trợ mượt mà cả màn hình dọc, xoay ngang và tablet.
6. **`AppItemGridCard`**:
   - Ô ứng dụng Bảo Khố phong cách túi đồ tu tiên.
   - Hiển thị biểu tượng thật (`AsyncAppIcon`), tên ứng dụng, huy hiệu số lượng nhiệm vụ liên kết, nút "Gỡ" Chu Sa.
   - **Tuân thủ nghiêm ngặt Canonical Design V2 Mục 6: Tuyệt đối không hiển thị icon ổ khóa**.
7. **`AppItemSelectableRow`**:
   - Hàng ứng dụng trong dialog chọn liên kết với checkbox viền Kim Tinh sáng.
8. **`TaskCard`**:
   - Thẻ nhiệm vụ tự kỷ luật theo chuỗi.
   - Phân cấp rõ rệt giữa nhiệm vụ đang tiến hành (`isCurrentActive = true` có nhãn phù văn và viền vàng hào quang) và nhiệm vụ chờ thực hiện.
   - Hiển thị chip các ứng dụng liên kết kèm biểu tượng thật.
9. **`ProgressCard`**:
   - Thẻ tiến trình tu luyện hôm nay với thanh năng lượng gradient mượt mà và huy hiệu tỷ lệ hoàn thành.
10. **`ItemCard`**:
    - Thẻ vật phẩm / kho báu tu tiên nhỏ gọn.

---

## 6. Animation Primitives & Tách Bạch Business Logic

- **Các nguyên thủy chuyển động (Animation Primitives)**:
  - `rememberBreathingAlpha`: Hiệu ứng nhịp thở linh lực phát quang chậm rãi (chu kỳ 2000ms, alpha từ 0.3f đến 0.85f).
  - `pressScaleEffect`: Phản hồi xúc giác co nhẹ (scale down 0.96f khi bấm, phục hồi 1.0f khi nhả).
  - `animateProgress`: Chuyển động thanh tiến trình linh lực mượt mà bằng `FastOutSlowInEasing`.
- **Nguyên tắc phân định ranh giới (Separation of Concerns)**:
  - Animation **CHỈ LÀ TẦNG HIỂN THỊ (PRESENTATION ONLY)**.
  - Mọi thay đổi trạng thái nghiệp vụ (hoàn thành nhiệm vụ, xóa nhiệm vụ, liên kết ứng dụng) phải diễn ra độc lập trong Domain / Data layer trước hoặc song song với animation.
  - **Tuyệt đối không để animation quyết định việc hoàn thành nhiệm vụ, mở khóa ứng dụng hay tính toán chu kỳ 04:00**.

---

## 7. Canonical Governance & Checkpoint Status

- **Frozen Core**: Toàn bộ hệ thống App Lock (`AppDetectorAccessibilityService`, `LockScreenActivity`, `BlockingShieldOverlay`, `UsageTracker`, `ScheduleEvaluator`, DataStore persistence) được giữ nguyên vẹn 100%, không bị ảnh hưởng.
- **OPEN Items**: Các mục `OPEN-01` đến `OPEN-07` tiếp tục giữ nguyên trạng thái `OPEN`. Không tự ý phát minh công thức mở khóa hay tính điểm thưởng.
- **Checkpoints**:
  - `CP4`: DONE
  - `CP6`: DONE
  - `CP7`: DONE
  - `CP8`: Technical UI foundation established.
