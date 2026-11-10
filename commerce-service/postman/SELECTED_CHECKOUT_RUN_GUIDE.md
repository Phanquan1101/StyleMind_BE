# Chọn món trong giỏ và mua ngay

Chỉ chạy với môi trường và dữ liệu kiểm thử; các yêu cầu tạo đơn làm thay đổi đơn hàng, giỏ và tồn kho. Import `selected-checkout.postman_collection.json`, cấu hình Gateway, token tài khoản hoặc khóa phiên khách, địa chỉ, biến thể và ID dòng giỏ thuộc đúng người dùng. Không đưa token hay khóa thật vào collection chia sẻ.

1. Thêm ít nhất hai biến thể vào giỏ thử nghiệm; lấy `cartItemId` từ API giỏ.
2. Gọi preview selected: chỉ món chọn được trả về. Đặt selected với `checkoutKey` mới; kiểm tra món chưa chọn vẫn trong giỏ và đơn chỉ chứa món đã chọn.
3. Gọi preview buy now và buy now với một biến thể còn hàng và `checkoutKey` mới. Kiểm tra đơn chỉ chứa biến thể đó và giỏ không thay đổi.
4. Lặp với khách dùng `X-Guest-Session-Key`; thử khóa phiên khác, ID của giỏ khác, danh sách rỗng/trùng, biến thể tắt và số lượng vượt tồn kho. Kiểm tra không tạo đơn và không sửa giỏ khi bị từ chối.
5. Nếu yêu cầu đặt đơn bị mất kết nối, dùng request recover và đúng `checkoutKey` đã gửi; tài khoản/khóa khách khác phải nhận 404. Không tạo khóa mới chỉ vì chưa nhận được phản hồi. Trạng thái đã đặt không đồng nghĩa đã thanh toán.

Các endpoint cũ đặt toàn bộ giỏ giữ nguyên. Test tự động dùng PostgreSQL cô lập; collection này cần được chạy riêng để xác nhận HTTP thực tế và chưa tự chứng minh thanh toán đã được xử lý.
