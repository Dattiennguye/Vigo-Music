# Vigo Music

App nghe nhạc Android (Kotlin + Jetpack Compose + Media3), giao diện kem – cam đào theo ảnh mẫu: đĩa than xoay, bìa đĩa kính mờ, thẻ playlist, thanh phát dạng sóng âm.

## Tính năng
- **Giới thiệu (onboarding)** 3 trang, có nút Bỏ qua và Bắt đầu nghe.
- **Thư viện**: nhập nhiều file nhạc cùng lúc (mp3, m4a, flac, wav, ogg, opus…). File được **chép vào bộ nhớ riêng của app**, nên vẫn nghe được khi đã xóa file gốc và khi không có mạng. Tự đọc tên bài, nghệ sĩ, album và ảnh bìa trong file.
- Tìm kiếm không dấu ("lac troi" ra "Lạc Trôi"), lọc Tất cả / Yêu thích / Tải từ web / Mới thêm, phát ngẫu nhiên.
- **Trình phát**: phát nền khi tắt màn hình, điều khiển trên thông báo và màn hình khóa, tự dừng khi rút tai nghe. Có trộn bài, lặp lại (tất cả / một bài), hàng chờ, chạm hoặc kéo trên sóng âm để tua.
- **Tìm & tải**: trình duyệt trong app dùng Google. Bấm vào link file nhạc, nhấn giữ link, hoặc bấm nút tải của trang web thì app hỏi "Tải về thư viện?" rồi lưu luôn. Có nút **Dán link** để tải trực tiếp từ một đường dẫn, kèm bảng tiến trình tải (có hủy).
- **Playlist**: tạo, đổi tên, xóa, thêm/bỏ bài. Có playlist **Yêu thích** tự động.
- Mở hoặc chia sẻ file nhạc từ app khác (Zalo, Files…) sang Vigo Music: bài được nhập rồi phát ngay.
- Đổi tên bài và xóa bài khỏi máy.

## Giới hạn (nói rõ)
- Chỉ tải được **file âm thanh trực tiếp**. Nhạc trên YouTube, Spotify, Zing MP3, Apple Music… được bảo vệ hoặc phát dạng luồng nên **không tải được**. Hãy dùng nguồn bạn có quyền tải.
- Nếu link dẫn tới một trang web chứ không phải file nhạc, app báo lỗi và không lưu gì.

## Build APK
Mỗi lần push lên `main`, GitHub Actions (workflow **Build Vigo Music APK**) sẽ build. Vào tab **Actions**, mở lần chạy mới nhất rồi tải artifact **Vigo-Music-apk**. Bên trong có `Vigo-Music.apk` để cài trực tiếp.

Build trên máy: `gradle :app:assembleRelease` (JDK 17, Android SDK 35).

APK được ký bằng khóa debug để cài thử. Khi lên Google Play cần thay bằng khóa phát hành riêng.

## Cấu trúc
```
data/    Library (thư viện + playlist, lưu JSON), Importer (nhập file, đọc metadata), Downloader (tải từ web)
player/  PlaybackService (Media3 phát nền), PlayerHub (cầu nối UI ↔ trình phát)
ui/      Onboarding, Thư viện, Tìm & tải (WebView), Playlist, Trình phát, các thành phần đĩa than / sóng âm
```
