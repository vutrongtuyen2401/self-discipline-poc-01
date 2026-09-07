# Release Checklist — SelfDisciplinePoc01

Tài liệu kiểm tra thủ tục phát hành (Release Checklist) dành cho Release Owner và Đội ngũ Kỹ thuật trước khi xuất xưởng gói ứng dụng tới người dùng cuối.

---

### Bảng Kiểm Tra Phát Hành (Release Gate Verification)

| Trạng thái | Hạng mục kiểm tra | Ghi chú & Tiêu chuẩn nghiệm thu |
|:----------:|-------------------|---------------------------------|
| [x] | **Clean checkout** | Mã nguồn được checkout sạch sẽ từ nhánh chính (`main` / `master`), không có file uncommitted rác. |
| [x] | **Correct Git commit** | Commit hash xác định: `54304c3` (hoặc tag release tương ứng). |
| [x] | **Version verified** | `versionCode = 1`, `versionName = "1.0"` (kiểm tra tính đơn điệu không trùng lặp). |
| [x] | **Tests passed** | Chạy `./gradlew test` đạt **215/215 PASS** (0 failures, 0 errors, 0 skipped). |
| [x] | **Release APK built** | `./gradlew assembleRelease` tạo thành công `app-release-unsigned.apk` (6.76 MB). |
| [x] | **Release AAB built** | `./gradlew bundleRelease` tạo thành công `app-release.aab` (6.41 MB). |
| [ ] | **Production signing available** | Keystore phát hành chính thức (`release.keystore`) sẵn sàng tại CI Secret hoặc môi trường ký độc lập. |
| [ ] | **Certificate fingerprint verified** | Khóa công khai SHA-256 certificate fingerprint được xác thực khớp với bản đăng ký trên Google Play App Signing / Enterprise MDM. |
| [ ] | **APK signature verified** | `apksigner verify --verbose --print-certs` xác nhận pass cả v2 & v3 schemes. |
| [ ] | **AAB signing/upload configuration verified** | AAB được ký bằng upload key hoặc release key qua `jarsigner` trước khi tải lên Play Console. |
| [x] | **SHA-256 recorded** | Ghi nhận đầy đủ mã băm SHA-256 của các artifact vào bảng ma trận release. |
| [ ] | **Release notes prepared** | Soạn thảo ghi chú phát hành (What's New) mô tả các tính năng và bản vá. |
| [ ] | **Manual distribution approval** | Phê duyệt thủ công từ Product Owner / Release Manager trước khi nhấn nút xuất bản. |
| [x] | **Rollback artifact identified** | Xác định phương án rollback (Roll-forward Hotfix hoặc lưu trữ artifact known-good). |

---

### Hướng Dẫn Ký Số Thủ Công Cho Release Owner (Local / Air-gapped Signing)

Nếu không sử dụng CI/CD tự động mà ký trên máy bảo mật độc lập:

1. **Ký APK:**
   ```bash
   apksigner sign \
     --ks /path/to/release.keystore \
     --ks-key-alias <your-key-alias> \
     --v2-signing-enabled true \
     --v3-signing-enabled true \
     --out app-release-prod-signed.apk \
     app/build/outputs/apk/release/app-release-unsigned.apk
   ```

2. **Xác thực APK đã ký:**
   ```bash
   apksigner verify --verbose --print-certs app-release-prod-signed.apk
   ```

3. **Ký AAB (Dành cho Google Play):**
   ```bash
   jarsigner -verbose \
     -sigalg SHA256withRSA \
     -digestalg SHA-256 \
     -keystore /path/to/release.keystore \
     app/build/outputs/bundle/release/app-release.aab \
     <your-key-alias>
   ```
