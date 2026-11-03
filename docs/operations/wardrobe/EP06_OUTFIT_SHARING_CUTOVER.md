# EP06 — Outfit visibility cutover

Scope đã được Owner duyệt tại [contract](../../planning/wardrobe/EP06_OUTFIT_SHARING_CONTRACT_PROPOSAL.md) và [V6](../../planning/wardrobe/EP06_TASK04_OUTFIT_VISIBILITY_DATABASE_SCOPE.md). Tài liệu này là handover; agent **chưa áp migration lên DB lâu dài hoặc deploy**.

V6 thêm visibility mặc định PRIVATE. Outfit đã có public token chuyển PUBLIC, giữ nguyên token và bổ sung enabled_at từ updated_at nếu cần. Outfit còn lại chuyển PRIVATE, kể cả chỉ từng cấp quyền email. Tất cả rows outfit_share được giữ để đối chiếu nhưng không cấp quyền nữa. Không tự tạo Friendship, mời email, đổi Chat hoặc mở FRIENDS.

Trước triển khai, phối hợp frontend dùng settings/friend-outfit APIs và nhận diện 410 từ API grant/feed cũ. Không suy link active từ token non-null: dùng visibility/public_enabled. Chỉ hành động chọn mode của Owner thay đổi visibility; gửi email Friends không đổi Outfit.

Triển khai các reader mới cùng V6 trong một đợt có điều phối: dừng toàn bộ Wardrobe instance cũ trước khi chạy migration và khởi động reader mới. **Không chạy lẫn binary cũ/mới, không downgrade binary cũ**: reader cũ chỉ kiểm tra token/email grant và bỏ qua visibility. Thủ tục backup/restore/rollback theo môi trường triển khai cần được nhóm vận hành chốt trước thực hiện; không sửa checksum V1–V5 hoặc reset Flyway history để rollback.

V6 dừng trước ALTER nếu public token hiện có blank hoặc dài hơn 128 ký tự. Khi gặp lỗi phải rà dữ liệu và xin quyết định riêng; không tự thay token/xóa dữ liệu. Public OFF/ON dùng lại token cũ, không tự TTL hoặc rotate. Đổi chế độ chỉ chặn truy cập về sau; backend không thu hồi bytes đã tải hoặc URL ảnh sản phẩm catalog.

Sau triển khai kiểm tra: default PRIVATE, Owner-only write, all-friends VIEW, guest PUBLIC, OFF/ON cùng token, PRIVATE bị ẩn cả count/page, legacy 410, unfriend/refriend và CRUD/Chat regression. Dùng account/Outfit thử nghiệm riêng, không chạy collection mutation vào dữ liệu cá nhân.

Các bước trên chưa được chạy trên môi trường lâu dài. Local unit/HTTP/PostgreSQL/Newman evidence tại [báo cáo](../../qa/wardrobe/EP06_TASK04_07_OUTFIT_SHARING_IMPLEMENTATION_REPORT.md); browser/frontend acceptance vẫn riêng.
