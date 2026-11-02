# Handoff — 43 Media Player

**Ngày cập nhật:** 2026-11-02  
**Ngôn ngữ làm việc:** Tiếng Việt  
**Repository:** https://github.com/Antony-collika/43-Media-player  
**Branch hiện tại:** `Phase-2-audio-domain`  
**Commit hiện tại:** `785a17d feat: add shared media domain and audio repository`

## 1. Tóm tắt điều hành

Dự án đang hợp nhất nền video của Nosved Player Lite với các tính năng audio của Alvand Player thành **một ứng dụng Android duy nhất**, dùng Kotlin, Jetpack Compose, Media3 và **một ExoPlayer duy nhất**.

Tình trạng hiện tại:

- Roadmap lớn Phase 1: hoàn thành.
- Roadmap lớn Phase 2: hoàn thành phần tách playback ownership ra service/controller; đã làm sớm trong branch `Phase-1`.
- Roadmap lớn Phase 3: đang triển khai.
- Milestone kỹ thuật `Phase-2A`: hoàn thành.
- Milestone kỹ thuật `Phase-2B`: đã có nền tảng repository audio độc lập, nhưng chưa nối vào UI/audio playback flow.
- Audio player hoàn chỉnh **chưa hoàn thành**.
- Video player hiện hữu chưa bị thay đổi trong milestone mới và cần tiếp tục giữ ổn định.

## 2. Ground truth đã đối chiếu với code

Repository đã được clone và kiểm tra trực tiếp từ branch `Phase-1` trước khi triển khai. Các sự kiện nền tảng thực tế:

```text
ee53197  Update README.md
09b3439  refactor: move playback ownership to media service
ec0b185  ci: build debug apk on github actions
6b765b3  ci: use preinstalled github android sdk
785a17d  feat: add shared media domain and audio repository
```

Kiến trúc playback hiện tại:

```text
PlaybackService owns ExoPlayer and MediaSession
        |
        v
PlayerManager / MediaController
        |
        v
VideoViewModel and Compose UI
```

`VideoRepository` vẫn là repository riêng cho video. `PlaybackService` vẫn là owner duy nhất của ExoPlayer. Chưa có AudioViewModel, Music screen, audio queue UI hoặc audio-specific playback UX.

Các điểm còn tồn tại cần nhớ:

- `MainActivity` hiện vẫn có logic `pauseVideo()` trong `onPause()`. Không được tuyên bố background audio đã hoàn tất.
- Runtime decoder switching chưa được rebuild player an toàn trong service.
- Audio boost/effects chưa được chuyển hoàn chỉnh vào service và chưa có audio session integration đáng tin cậy.
- Manifest hiện đã có `READ_MEDIA_AUDIO`, nhưng permission không đồng nghĩa với audio library đã hoàn tất.
- Manifest vẫn còn `MANAGE_EXTERNAL_STORAGE`; cần đánh giá lại khi hoàn thiện permission/Play Store UX, không tự ý xóa trong milestone media domain.
- Intent `ACTION_VIEW` hiện chủ yếu khai báo cho `video/*`; audio intent chưa hoàn chỉnh.

## 3. Những gì đã hoàn thành trong commit hiện tại

Commit `785a17d` trên branch `Phase-2-audio-domain` đã thêm:

```text
app/src/main/java/com/devson/nvplayerlite/media/MediaKind.kt
app/src/main/java/com/devson/nvplayerlite/media/MediaEntry.kt
app/src/main/java/com/devson/nvplayerlite/media/MediaItemMapper.kt
app/src/main/java/com/devson/nvplayerlite/repository/AudioMediaStoreRepository.kt
app/src/test/java/com/devson/nvplayerlite/media/MediaItemMapperTest.kt
```

Chi tiết:

- `MediaKind` phân biệt `AUDIO` và `VIDEO`.
- `MediaEntry` là domain model dùng chung, không dùng `Video` cho audio và không dùng `Song` cho video.
- `MediaItemMapper` chuyển `MediaEntry` sang Media3 `MediaItem`, gồm media ID, URI, MIME type và metadata title/artist/album/artwork.
- `AudioMediaStoreRepository` query `MediaStore.Audio.Media` độc lập với `VideoRepository`, gồm URI, title/display name, artist, album, album art URI, duration, MIME type và folder/path khi Android cung cấp.
- Unit test khóa hành vi cơ bản của mapper.

Chưa làm trong commit này:

- Chưa thêm Music tab/card.
- Chưa hiển thị danh sách audio.
- Chưa tap audio để phát từ UI.
- Chưa thêm audio player screen hoặc mini-player.
- Chưa nối audio repository vào ViewModel/DI/application flow.
- Chưa thay đổi background playback behavior.
- Chưa làm queue, repeat, shuffle, lyrics, sleep timer, equalizer hoặc Android Auto.

## 4. Kiểm tra build và artifact

GitHub Actions đã pass toàn bộ cho commit hiện tại:

- Run: https://github.com/Antony-collika/43-Media-player/actions/runs/41389941111
- Commit được build: `785a17d0caa52e92552c5f731260ac2348bebe1c`
- Build debug APK: pass.
- Upload debug APK: pass.
- Upload build reports: pass.

Artifact được tạo theo tên workflow:

```text
43-media-player-debug-apk-<run_number>
android-build-reports-<run_number>
```

Workflow hiện tự động chạy cho `main`, `Phase-1` và `phase-*` viết thường. Branch `Phase-2-audio-domain` dùng chữ `P` hoa nên run vừa rồi được kích hoạt bằng `workflow_dispatch`. Khi tạo branch phase mới, hoặc dùng tên lowercase `phase-*`, hoặc dispatch workflow thủ công.

Sandbox không có Android SDK local nên local Gradle build dừng ở bước tìm SDK; đây không phải lỗi source. GitHub Actions là nguồn xác nhận build chính thức của dự án.

## 5. Tiến độ so với toàn bộ kế hoạch

| Phần kế hoạch | Nội dung | Trạng thái |
|---|---|---|
| Roadmap lớn Phase 1 | Chọn Nosved làm codebase, fork, giữ version catalog, build sạch | Hoàn thành |
| Roadmap lớn Phase 2 | Đưa ExoPlayer/MediaSession vào PlaybackService, UI dùng MediaController | Hoàn thành sớm trong branch `Phase-1` |
| Roadmap lớn Phase 3 | Audio library, audio model, audio UI, phát audio | Đang triển khai |
| Roadmap lớn Phase 4 | Queue, playlist, lyrics, sleep timer, effects, widget, Android Auto | Chưa bắt đầu đầy đủ |
| Roadmap lớn Phase 5 | Intent, permissions, UX audio/video, hoàn thiện sản phẩm | Chưa bắt đầu |

Các milestone kỹ thuật:

| Milestone | Nội dung | Trạng thái |
|---|---|---|
| Phase-2A | Media domain chung | Hoàn thành trong `785a17d` |
| Phase-2B | Audio MediaStore repository | Nền repository đã có; chưa tích hợp UI |
| Phase-2C | Music/Videos entry point, audio list, audio playback UI tối thiểu | Tiếp theo |
| Phase-2D | Mapper/queue dùng chung trong service/controller | Mapper đã có; queue integration chưa làm |
| Phase-2E | Background audio, notification, media button, lock screen, focus | Chưa làm |
| Phase-2F | Port tính năng Alvand theo thứ tự queue → playlist → lyrics → effects → widget/Auto | Chưa làm |

## 6. Phần tiếp theo được khuyến nghị

### Bước tiếp theo: Phase-2C — Audio entry point và UI tối thiểu

Phạm vi nên giữ nhỏ và có thể rollback:

1. Tạo `AudioViewModel` dùng `AudioMediaStoreRepository`.
2. Thêm state loading/empty/error/list cho audio library.
3. Thêm entry point `Music` cạnh `Videos` trong home/navigation hiện tại, không viết lại video navigation.
4. Hiển thị danh sách audio với title, artist, duration và artwork nếu có.
5. Khi tap audio, dùng `PlayerManager`/`MediaController` hiện có để set một `MediaItem` hoặc queue audio; tuyệt đối không tạo ExoPlayer thứ hai.
6. Khi current item là audio-only, không render video surface.
7. Thêm màn hình hoặc mini-player tối thiểu để play/pause, seek cơ bản và hiển thị title/artwork.
8. Giữ video behavior hiện tại và không đưa subtitle, gesture, orientation hoặc PlayerView vào service.

### Thứ tự file có thể triển khai

```text
ui/screens/home hoặc navigation hiện có
viewmodel/AudioViewModel.kt
ui/screens/audio/AudioListScreen.kt
ui/screens/audio/components/AudioListItem.kt
player/PlayerManager.kt hoặc facade dùng chung nếu cần API queue nhỏ
```

Không nên làm trong cùng commit Phase-2C:

- Lyrics.
- Equalizer/BassBoost/LoudnessEnhancer.
- Android Auto.
- Widget.
- Room playlist schema.
- Runtime decoder recreation.
- Refactor toàn bộ Video model/database.

### Sau Phase-2C

- Phase-2D: chuẩn hóa queue ở `PlaybackService`/controller, không để queue riêng trong ViewModel.
- Phase-2E: sửa lifecycle để audio-only tiếp tục khi Activity background; xác định chính sách rõ ràng cho video audio khi rời app; kiểm tra notification, lock screen, headset, Bluetooth, audio focus và becoming noisy.
- Phase-2F: port queue/repeat/shuffle trước, sau đó playlist/Room, artwork nâng cao, lyrics, sleep timer, audio effects, widget và Android Auto.
- Roadmap Phase 5: hoàn thiện permissions theo nhu cầu, audio/video intents, SAF URI persistence và UX hợp nhất.

## 7. Nguyên tắc bắt buộc cho người tiếp theo

- Làm việc tiếp từ branch `Phase-2-audio-domain`, không sửa trực tiếp `main`.
- Giữ Media3 version line hiện tại của Nosved; không trộn dependency Media3 1.5.1 của Alvand.
- Chỉ có một ExoPlayer trong `PlaybackService`.
- Activity, Composable và ViewModel không được tự tạo hoặc release ExoPlayer.
- Không dùng `Video` làm model audio cuối cùng.
- Không coi có `READ_MEDIA_AUDIO` là đã có audio player.
- Mỗi milestone phải build pass trên GitHub Actions trước khi chuyển tiếp.
- Khi báo cáo, luôn ghi rõ commit, branch, link Actions, artifact và phần chưa làm.
- Nếu cần kiểm tra trên điện thoại, tải debug APK từ mục **Artifacts** của GitHub Actions.

## 8. Mẫu báo lỗi khi chủ dự án test APK

```text
APK/run:
Cài APK: OK/Fail
Mở app: OK/Crash
Thao tác gây lỗi:
Thiết bị / Android version:
Ảnh màn hình hoặc log:
```
