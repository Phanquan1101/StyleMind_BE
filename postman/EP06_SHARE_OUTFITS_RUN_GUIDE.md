# EP06 — Share Outfit: PRIVATE / FRIENDS / PUBLIC

Contract đã được Owner duyệt: [nghiệp vụ/API](../docs/planning/wardrobe/EP06_OUTFIT_SHARING_CONTRACT_PROPOSAL.md), [V6](../docs/planning/wardrobe/EP06_TASK04_OUTFIT_VISIBILITY_DATABASE_SCOPE.md). Chỉ chủ sở hữu đổi chế độ. Gửi/chấp nhận lời mời kết bạn không tự đổi PRIVATE; FRIENDS cho **tất cả bạn bè hiện tại** xem. Viewer chỉ xem, không sửa Outfit.

Import hai file:

- [Collection v2.1 — 32 requests](StyleMind-EP06-Share-Outfits.postman_collection.json).
- [Environment mẫu](StyleMind-Outfit-Sharing.postman_environment.example.json).

Chạy trên dữ liệu kiểm thử riêng. Collection tạo rồi xóa **một Outfit của A**; không xóa Outfit có sẵn. A và B phải là hai CUSTOMER đã kết bạn; C là CUSTOMER khác chưa kết bạn với A. Điền local variables `baseUrl` (mặc định Gateway `http://localhost:8080`), `accessTokenA/B/C`, `friendAccountA/B` (account ID đúng của A/B) và `variantId` của sản phẩm Commerce đang mua được. A nên có dưới 100 Outfit và ít hơn 100 Outfit hiển thị cho bạn bè để các assertion một trang đầy đủ có ý nghĩa.

Đặt `allowOutfitSharingTest=true` sau khi kiểm tra fixture, rồi chạy **toàn collection theo thứ tự**, dừng khi lỗi. Các biến `outfitId`, `publicToken`, `publicEnabledAt`, `ownedItemId` được tạo trong local environment; không điền ID Outfit cũ để chạy cleanup riêng. Nếu dừng giữa chừng, chỉ Owner xóa đúng Outfit test được tạo bởi request đầu. Không export environment đã điền JWT hoặc public token.

Collection kiểm tra PRIVATE mặc định, Owner A/B/C isolation, FRIENDS listing/detail, PRIVATE không có trong page, validation, viewer không rename, PUBLIC guest, OFF/ON dùng cùng link, PUBLIC no-op, public-link alias, DELETE alias không đổi FRIENDS, retired 410 và xóa Outfit. Assertions dùng phép so sánh boolean cho token, không in token vào summary. Test HTTP không chứng minh browser UI đã tích hợp.

## API hiện hành

| Route qua prefix `/wardrobe-community/api/v1` | Nội dung |
| --- | --- |
| GET/PATCH `/me/outfits/{outfitId}/sharing` | Owner settings; PATCH duy nhất `{"visibility":"PRIVATE|FRIENDS|PUBLIC"}` với **một** giá trị enum chính xác |
| GET `/me/friends/{friendAccountId}/outfits?page=0&size=20` | Current friend; chỉ FRIENDS/PUBLIC; size 1..100; sort updated_at DESC, outfit_id DESC |
| GET `/me/friends/{friendAccountId}/outfits/{outfitId}` | Đúng bạn và đúng owner Outfit; mode FRIENDS/PUBLIC |
| GET `/public/outfits/{token}` | Không cần JWT; chỉ hoạt động khi mode hiện tại PUBLIC |
| PUT/DELETE `/me/outfits/{outfitId}/public-link` | Alias bật PUBLIC / nếu PUBLIC thì về PRIVATE; giữ token |

Owner summary/detail có thêm `visibility`; `public_token` còn khi inactive không có nghĩa link đang mở. Friend DTO không có token/email/owner_account_id. Chuyển PRIVATE/FRIENDS chặn public link ngay; bật PUBLIC lại dùng link cũ. Unfriend chặn friend route cả hai chiều nhưng không ảnh hưởng link PUBLIC. Kết bạn lại khôi phục khả năng xem FRIENDS/PUBLIC, PRIVATE vẫn ẩn.

API email-grant `/me/outfits/{id}/shares...` và feed `/me/shared-outfits...` trả `410 OUTFIT_EMAIL_SHARING_RETIRED`. Không dùng chúng để gửi lời mời mới. Dùng [Friends invitation](EP06_FRIEND_INVITATIONS_RUN_GUIDE.md) và API visibility riêng. Không tự mở Outfit khi gửi mời.

Swagger service `/swagger-ui/index.html`, spec `/v3/api-docs`; qua Gateway dùng prefix `/wardrobe-community`. Dùng **Authorize → bearerAuth** cho route CUSTOMER; public reader không cần Authorize. Malformed body/UUID/page: 400; thiếu JWT: 401; sai role: 403; ngoài quyền/hidden/wrong-parent: 404; cơ chế cũ: 410; dependency unavailable: 503. Các response chia sẻ dùng no-store.

## Kiểm thử tự động cô lập

```powershell
.\scripts\verify-ep06-outfit-sharing.ps1 -Mode Focused
.\scripts\verify-ep06-outfit-sharing.ps1 -Mode Regression
.\scripts\verify-ep06-chat-runtime.ps1 -SkipRegression
```

Chỉ dùng `-SkipRegression` ngay sau gate pass cùng production source. Runtime chạy bốn JAR thật (Identity, Notification, Wardrobe, Gateway), PostgreSQL/Mailpit cô lập, Login/JWKS/SMTP thật trong fixture và Newman. **Commerce catalog là HTTP fixture**, storage ảnh mặc định HTTP fake; không coi đây là Commerce end-to-end hoặc lượt xác minh cloud mới. Không đọc `.env`, không Compose hay persistent DB. Các nguồn test dùng fixture secrets tạm; summary không chứa giá trị secret.

JSON parse/script compilation là kiểm tra cấu trúc; kết quả actual HTTP, commands, số tests/assertions và giới hạn ghi riêng tại [báo cáo implementation](../docs/qa/wardrobe/EP06_TASK04_07_OUTFIT_SHARING_IMPLEMENTATION_REPORT.md). [Hướng dẫn cutover](../docs/operations/wardrobe/EP06_OUTFIT_SHARING_CUTOVER.md).
