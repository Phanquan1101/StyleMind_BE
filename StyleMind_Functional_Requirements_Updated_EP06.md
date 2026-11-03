# STYLEMIND – FUNCTIONAL REQUIREMENTS

## 1. Tổng quan

StyleMind là hệ thống thương mại điện tử chuyên bán **quần áo nữ**, tích hợp chức năng **AI Wardrobe Stylist**.

Shop chỉ kinh doanh các sản phẩm thuộc nhóm quần áo như:

- Áo
- Quần
- Váy
- Đầm
- Áo khoác
- Các loại trang phục nữ khác

Hệ thống **không tập trung bán phụ kiện** như:

- Mũ
- Túi
- Trang sức
- Giày
- Phụ kiện thời trang khác

Điểm nổi bật của StyleMind là mỗi Customer có một **My Wardrobe** – tủ đồ ảo chính chứa nhiều **Outfit**.

Có thể hình dung tương tự Google Drive: My Wardrobe là không gian lớn, bên trong có nhiều Outfit giống các folder nhỏ.

Customer có hai cách tạo Outfit:

- **Create with AI** – trò chuyện với AI Wardrobe Stylist để AI hỏi nhu cầu, tìm sản phẩm thật trong shop và phối thành Outfit.
- **Create Manually** – Customer tự chọn sản phẩm của shop và thêm vào Outfit.

Sau khi Outfit được tạo, Customer có thể mở lại để chỉnh sửa, nhờ AI hỗ trợ phối lại hoặc mua sản phẩm. Customer cũng có thể chia sẻ **toàn bộ My Wardrobe** (gồm các Outfit bên trong) bằng Public Link hoặc email được chỉ định và trao đổi qua Chat 1–1 với người được chia sẻ bằng email.

AI chỉ sử dụng sản phẩm thực tế của StyleMind và không được tự tạo sản phẩm, giá, variant hoặc hình ảnh không tồn tại.



---



# 2. Vai trò hệ thống

Hệ thống có hai role chính:

## CUSTOMER

Customer có thể:

- Đăng ký
- Đăng nhập
- Quản lý profile
- Quản lý địa chỉ
- Xem sản phẩm
- Tìm kiếm sản phẩm
- Quản lý giỏ hàng
- Xem My Wardrobe
- Tạo Outfit bằng AI Chat
- Tự tạo Outfit thủ công
- Thêm/xóa/thay sản phẩm trong Outfit
- Nhận gợi ý phối đồ từ AI
- Yêu cầu AI thay sản phẩm hoặc phối lại Outfit
- Lưu chiều cao và cân nặng để hỗ trợ gợi ý size
- Chọn sản phẩm từ Outfit
- Mua sản phẩm hoặc mua cả Outfit
- Share toàn bộ My Wardrobe bằng email, kể cả tới người chưa đăng ký
- Share toàn bộ My Wardrobe bằng Public Link
- Quản lý danh sách email đã được share và thu hồi/cấp lại quyền xem Wardrobe
- Chat 1–1 với người được share Wardrobe qua email
- Checkout
- Thanh toán
- Theo dõi đơn hàng
- Hủy đơn
- Đổi trả
- Nhận thông báo

## ADMIN

Admin có thể:

- Quản lý user
- Quản lý category
- Quản lý product
- Quản lý variant
- Quản lý hình ảnh theo màu sản phẩm
- Quản lý order
- Quản lý cancellation
- Quản lý return/refund
- Quản lý notification
- Quản lý dữ liệu phục vụ AI
- Xem dashboard



---



# 3. Authentication

## FR-01 – Đăng ký

Customer có thể đăng ký bằng:

- Email
- Password
- Họ tên



---



## FR-02 – OTP đăng ký

Hệ thống gửi OTP qua email để xác thực tài khoản.

OTP:

- Có thời hạn
- Có giới hạn số lần thử
- Có cooldown khi gửi lại
- Không lưu plain text



---



## FR-03 – Đăng nhập

Customer/Admin đăng nhập bằng email và password.



---



## FR-04 – JWT

Sau khi đăng nhập thành công, hệ thống cấp JWT.

JWT có thể chứa:

- userId
- email
- role



---



## FR-05 – Quên mật khẩu

Người dùng có thể:

- Nhập email
- Nhận OTP
- Xác thực OTP
- Đặt password mới



---



## FR-06 – Phân quyền

CUSTOMER và ADMIN có quyền truy cập khác nhau.



---



# 4. User Profile

## FR-07 – Xem profile

Customer có thể xem thông tin cá nhân.



---



## FR-08 – Cập nhật profile

Customer có thể cập nhật:

- Họ tên
- Phone
- Avatar
- Height
- Weight

Height và Weight được AI Wardrobe Stylist sử dụng để hỗ trợ gợi ý size. Style, Occasion, Color Preference và Budget không được lưu như preference cố định của Customer.



---



# 5. Address

## FR-09 – Xem danh sách địa chỉ

Customer có thể xem địa chỉ của mình.



---



## FR-10 – Thêm địa chỉ

Customer có thể thêm địa chỉ giao hàng.



---



## FR-11 – Cập nhật địa chỉ

Customer có thể cập nhật địa chỉ.



---



## FR-12 – Xóa địa chỉ

Customer có thể xóa địa chỉ.



---



## FR-13 – Địa chỉ mặc định

Customer có thể đặt một địa chỉ làm mặc định.



---



## FR-14 – Address Snapshot

Khi checkout, Order Service lưu snapshot địa chỉ.

Việc customer sửa địa chỉ sau này không làm thay đổi đơn hàng cũ.



---



# 6. Category

Category chỉ tập trung vào **quần áo nữ**.

Ví dụ:

## TOP

- T-Shirt
- Shirt
- Blouse
- Crop Top
- Sweater
- Hoodie

## BOTTOM

- Jeans
- Trousers
- Shorts
- Skirt

## DRESS

- Mini Dress
- Midi Dress
- Maxi Dress

## OUTERWEAR

- Blazer
- Cardigan
- Jacket
- Coat



---



## FR-15 – Xem Category

Customer có thể xem các category.



---



## FR-16 – Category cha/con

Category có thể có parent-child.



---



## FR-17 – Admin tạo Category

Admin có thể tạo category.



---



## FR-18 – Admin cập nhật Category

Admin có thể cập nhật category.



---



## FR-19 – Admin Active/Inactive Category

Admin có thể bật/tắt category.



---



# 7. Product

## FR-20 – Xem Product List

Customer có thể xem danh sách sản phẩm.



---



## FR-21 – Xem Product Detail

Thông tin product gồm:

- Tên
- Mô tả
- Category
- Giá
- Style
- Occasion
- Material
- Fit
- Pattern
- Các màu có sẵn
- Các size có sẵn
- Hình ảnh tương ứng



---



## FR-22 – Product Status

Product có:

- ACTIVE
- INACTIVE

Public chỉ nhìn thấy ACTIVE product.



---



# 8. Product Variant

Variant đại diện cho lựa chọn cụ thể của sản phẩm.

Ví dụ:

Áo Blouse A:

- Trắng / S
- Trắng / M
- Đỏ / S
- Đỏ / M
- Đen / S



---



## FR-23 – Variant Size

Variant có size.

Ví dụ:

- S
- M
- L
- XL



---



## FR-24 – Variant Color

Variant có màu sắc.

Ví dụ:

- White
- Black
- Red
- Beige
- Pink



---



## FR-25 – Variant Price

Variant có thể có giá riêng nếu cần.



---



## FR-26 – Variant Status

Variant có thể:

- ACTIVE
- INACTIVE



---



# 9. Không quản lý tồn kho

StyleMind trong phạm vi project **không triển khai Inventory/Warehouse Management**.

Không có các chức năng:

- Quản lý kho
- Nhập kho
- Xuất kho
- Số lượng tồn
- Low stock
- Warehouse
- Inventory transaction

Việc lựa chọn sản phẩm chỉ kiểm tra:

- Product ACTIVE
- Variant ACTIVE



---



# 10. Product Image theo màu

Product không sử dụng một bộ ảnh cố định cho mọi màu.

Ảnh phải phụ thuộc vào **màu sản phẩm mà người dùng đang chọn**.

Ví dụ:

Áo có:

- Trắng
- Đỏ
- Đen

Khi customer chọn:

**RED**

hệ thống phải hiển thị ảnh:

**Áo màu đỏ**

Khi chuyển sang:

**BLACK**

hệ thống phải thay ảnh thành:

**Áo màu đen**



---



## FR-27 – Variant Color Image

Mỗi màu có thể có một hoặc nhiều hình ảnh riêng.

Ví dụ:

Product: Áo Blouse

WHITE

\- white-front.jpg

\- white-back.jpg

RED

\- red-front.jpg

\- red-back.jpg

BLACK

\- black-front.jpg

\- black-back.jpg



---



## FR-28 – Dynamic Product Image

Frontend phải tự động thay đổi hình ảnh khi customer chọn màu khác.



---



## FR-29 – Admin Upload Image By Color

Khi upload ảnh, Admin phải xác định ảnh thuộc màu nào.



---



## FR-30 – Main Image By Color

Mỗi màu có thể có ảnh chính riêng.



---



# 11. Search & Filter

## FR-31 – Search Product

Customer có thể tìm sản phẩm bằng keyword.



---



## FR-32 – Filter Category

Customer có thể lọc category.



---



## FR-33 – Filter Price

Customer có thể lọc khoảng giá.



---



## FR-34 – Filter Color

Customer có thể lọc màu.



---



## FR-35 – Filter Size

Customer có thể lọc size.



---



## FR-36 – Filter Style

Customer có thể lọc style.



---



## FR-37 – Filter Occasion

Customer có thể lọc occasion.



---



# 12. Cart

## FR-38 – Guest Cart

Guest có thể thêm sản phẩm vào cart.



---



## FR-39 – User Cart

Customer có giỏ hàng riêng sau khi login.



---



## FR-40 – Add To Cart

Customer chọn:

- Product
- Color
- Size
- Quantity

sau đó Add To Cart.



---



## FR-41 – Update Cart

Customer có thể cập nhật quantity.



---



## FR-42 – Remove Cart Item

Customer có thể xóa item.



---



## FR-43 – Clear Cart

Customer có thể clear cart.



---



## FR-44 – Merge Guest Cart

Sau login, Guest Cart có thể merge vào User Cart.



---

---



# 13. My Wardrobe & Outfit Management

My Wardrobe là chức năng trọng tâm của StyleMind.

Mỗi Customer có một **My Wardrobe** chính. Bên trong My Wardrobe có nhiều **Outfit**.

Có thể hình dung:

MY WARDROBE

│

├── Outfit 1 – Đi học

├── Outfit 2 – Đi cafe cuối tuần

├── Outfit 3 – Đi làm

└── Outfit 4 – Đi chơi

Một Outfit gồm một hoặc nhiều sản phẩm quần áo thực tế của StyleMind.

My Wardrobe có thể được chia sẻ toàn bộ theo cơ chế PRIVATE / PROTECTED / PUBLIC ở Phần 18; quyền xem của người nhận áp dụng cho tất cả Outfit thuộc Wardrobe, kể cả Outfit được tạo thêm sau khi chia sẻ.

Customer có thể tạo Outfit theo hai cách:

- Create with AI
- Create Manually



---



## FR-45 – View My Wardrobe

Customer có thể xem My Wardrobe và toàn bộ Outfit thuộc sở hữu của mình.

Giao diện My Wardrobe hiển thị các Outfit dưới dạng card/folder nhỏ, tương tự cách Google Drive hiển thị các folder bên trong Drive.



---



## FR-46 – Create Outfit Manually

Customer có thể tạo một Outfit mà không cần AI.

Customer tự tìm sản phẩm trong catalog StyleMind và thêm sản phẩm vào Outfit.



---



## FR-47 – Outfit Name

Mỗi Outfit có tên.

Ví dụ:

- Đi học hằng ngày
- Đi cafe cuối tuần
- Đi làm Minimal
- Hẹn hò
- Đi chơi



---



## FR-48 – View Outfit Detail

Customer có thể mở một Outfit để xem:

- Outfit name
- Các sản phẩm trong Outfit
- Product image
- Product name
- Color
- Size đã chọn nếu có
- Price
- Link Product Detail



---



## FR-49 – Add Product Manually To Outfit

Customer có thể tự thêm sản phẩm của shop vào Outfit hiện tại.

Sản phẩm do Customer thêm thủ công phải được xem như một phần của Outfit và AI phải đọc được sản phẩm này khi hỗ trợ phối đồ sau đó.



---



## FR-50 – Remove Product From Outfit

Owner có thể xóa sản phẩm khỏi Outfit của mình.



---



## FR-51 – Change Outfit Product Variant

Owner có thể thay đổi Color/Size của sản phẩm trong Outfit nếu variant tương ứng đang ACTIVE.



---



## FR-52 – Delete Outfit

Owner có thể xóa Outfit khỏi My Wardrobe.

Việc xóa Outfit không làm xóa Product khỏi catalog StyleMind.



---



# 14. Body Information For AI

Height và Weight là thông tin được lưu để AI hỗ trợ gợi ý size.

StyleMind **không lưu Style, Occasion, Color Preference, Budget hoặc Clothing Preference như preference cố định của Customer**.

Những thông tin đó được AI hỏi lại theo từng lần tạo hoặc chỉnh Outfit.



---



## FR-53 – Customer Height

Customer có thể cung cấp chiều cao.

Ví dụ:

Height: 160 cm



---



## FR-54 – Customer Weight

Customer có thể cung cấp cân nặng.

Ví dụ:

Weight: 52 kg



---



## FR-55 – Ask Height And Weight When Missing

Khi Customer dùng AI Stylist mà chưa có Height/Weight, AI có thể hỏi Customer cung cấp thông tin này.

Sau khi Customer cung cấp, hệ thống lưu Height/Weight để sử dụng cho những lần recommendation tiếp theo.



---



## FR-56 – Update Height And Weight

Customer có thể cập nhật Height/Weight bất cứ lúc nào.

AI phải sử dụng thông tin mới nhất cho recommendation mới.

Việc thay đổi Height/Weight không tự động sửa Outfit cũ.



---



## FR-57 – Size Recommendation

AI có thể kết hợp:

- Height
- Weight
- Product size information/size chart nếu có

để **gợi ý size phù hợp**.

Size Recommendation chỉ mang tính gợi ý; hệ thống không được khẳng định tuyệt đối size chỉ dựa trên Height/Weight.



---



## FR-58 – Body Information Privacy

Height/Weight là dữ liệu riêng của Customer.

Không hiển thị Height/Weight khi Outfit được share cho người khác hoặc khi Outfit được Public.



---



# 15. AI Chat – Create Outfit

Customer có thể chọn **Create with AI** từ My Wardrobe.

AI Chat đóng vai trò **AI Wardrobe Stylist**, không phải chatbot hỏi đáp chung.

AI có nhiệm vụ trò chuyện với Customer để hiểu nhu cầu, tìm sản phẩm thật trong StyleMind và phối thành Outfit.



---



## FR-59 – Open AI Stylist

Customer có thể mở AI Stylist để tạo Outfit mới.



---



## FR-60 – AI First Message

Khi mở AI Stylist, AI chủ động bắt đầu conversation.

Ví dụ:

> Chào bạn, hôm nay bạn cần mình giúp phối đồ như thế nào?



---



## FR-61 – AI Collect Outfit Requirement

AI hỏi Customer để thu thập các thông tin cần thiết.

Có thể gồm:

- Occasion/Purpose
- Style
- Màu mong muốn
- Màu không mong muốn
- Budget
- Loại quần áo mong muốn
- Fit mong muốn
- Additional Requirement

AI không bắt buộc phải hỏi tất cả theo một form cố định. AI có thể hỏi theo conversation tự nhiên.



---



## FR-62 – Outfit Preference Is Session/Outfit Context

Các thông tin như:

- Style
- Occasion
- Color Preference
- Budget
- Additional Requirement

không được mặc định là preference lâu dài của Customer.

AI sử dụng chúng trong context của conversation/Outfit hiện tại.

Ví dụ Customer hôm nay có thể muốn Minimal nhưng lần sau có thể muốn Streetwear.



---



## FR-63 – AI Ask Missing Information

Nếu thông tin chưa đủ để phối đồ, AI phải hỏi thêm Customer trước khi tạo recommendation.

Ví dụ:

- “Bạn muốn mặc bộ này để đi đâu?”
- “Bạn thích phong cách nào?”
- “Có màu nào bạn không muốn mặc không?”
- “Bạn muốn bộ đồ trong khoảng ngân sách bao nhiêu?”



---



## FR-64 – AI Analyze Requirement

AI phân tích conversation để xác định requirement hiện tại.

Ví dụ:

Occasion: Cafe

Style: Minimal + Feminine

Preferred Colors: White, Beige

Excluded Color: Red

Budget: 1.000.000

Additional Requirement: Không thích áo quá bó



---



## FR-65 – AI Search Product

AI tìm sản phẩm phù hợp trong catalog/index của StyleMind.



---



## FR-66 – AI Only Uses Shop Products

AI chỉ được gợi ý sản phẩm thực tế đang tồn tại trong StyleMind.

Sản phẩm/variant được recommendation phải đang ACTIVE.



---



## FR-67 – AI Must Not Hallucinate Product Data

AI không được tự tạo:

- Product
- ProductId
- Variant
- Price
- Image
- Color không tồn tại
- Size không tồn tại

Backend/AI Service phải validate recommendation với Product Service trước khi trả kết quả cuối cùng cho Customer.



---



## FR-68 – AI Generate Outfit Recommendation

Sau khi có đủ requirement, AI tìm và phối các sản phẩm thành một Outfit Recommendation.

Ví dụ:

White Basic Blouse

\+

Beige Wide-leg Pants

\+

Beige Cardigan



---



## FR-69 – Outfit Explanation

AI có thể giải thích ngắn:

- Vì sao các sản phẩm phù hợp với nhau
- Vì sao phù hợp Style
- Vì sao phù hợp Occasion
- Vì sao phù hợp với yêu cầu Customer



---



## FR-70 – AI Ask For Confirmation

Sau khi tạo recommendation, AI phải hỏi Customer có hài lòng với Outfit hay không.

Ví dụ:

> Bạn thấy bộ này ổn chưa? Nếu chưa, hãy cho mình biết bạn chưa thích điểm nào để mình chỉnh lại.



---



## FR-71 – Collect Outfit Feedback

Nếu Customer chưa hài lòng, AI phải thu thập thêm feedback.

Ví dụ:

- Không thích váy
- Áo quá nữ tính
- Muốn màu tối hơn
- Muốn rẻ hơn
- Muốn rộng hơn
- Muốn đổi thành quần



---



## FR-72 – AI Analyze Feedback

AI phân tích feedback mới cùng với requirement trước đó để xác định phần cần thay đổi.

AI không được bỏ toàn bộ context cũ nếu Customer chỉ yêu cầu chỉnh một phần.



---



## FR-73 – Replace Specific Product

Nếu Customer chỉ không thích một sản phẩm, AI có thể giữ các sản phẩm còn lại và tìm sản phẩm thay thế phù hợp.

Ví dụ:

Customer: Tôi thích áo nhưng không thích váy.



AI giữ áo hiện tại

↓

Search BOTTOM phù hợp

↓

Đề xuất quần thay cho váy



---



## FR-74 – Regenerate Outfit

Customer có thể yêu cầu AI phối lại toàn bộ Outfit dựa trên context hiện tại.



---



## FR-75 – Confirm Outfit

Outfit Recommendation chỉ được lưu chính thức vào My Wardrobe sau khi Customer xác nhận.

AI không tự động lưu một recommendation mà Customer chưa đồng ý.



---



## FR-76 – Name Outfit

Sau khi Customer Confirm, hệ thống yêu cầu đặt tên hoặc AI có thể đề xuất tên cho Outfit.

Customer có thể chấp nhận hoặc đổi tên.



---



## FR-77 – Save Outfit To My Wardrobe

Sau khi Confirm và đặt tên, Outfit được lưu vào My Wardrobe của Customer.



---



# 16. AI Assistant For Existing Outfit

AI không chỉ tạo Outfit mới.

Customer có thể mở lại một Outfit đã lưu và yêu cầu AI hỗ trợ chỉnh sửa/phối thêm.



---



## FR-78 – Open AI From Existing Outfit

Owner có thể chọn **Ask AI** trong một Outfit.



---



## FR-79 – AI Uses Current Outfit Context

Khi mở AI từ một Outfit, AI phải biết:

- Outfit ID
- Outfit name
- Các Product hiện tại
- Color/Size đã chọn
- Height/Weight mới nhất của Customer
- Conversation hiện tại



---



## FR-80 – AI Knows User-Added Products

AI phải đọc được cả:

- Product do AI recommendation
- Product do Customer tự thêm thủ công

AI không được bỏ qua sản phẩm thủ công khi phối đồ.



---



## FR-81 – Add Complementary Product

Customer có thể yêu cầu AI tìm thêm sản phẩm phù hợp với Outfit hiện tại.

Ví dụ:

> Phối thêm cho tôi một cái áo khoác.

AI sử dụng các item hiện tại để tìm Outerwear phù hợp.



---



## FR-82 – Replace Product In Existing Outfit

Customer có thể yêu cầu AI đổi một sản phẩm trong Outfit hiện tại.

Ví dụ:

> Đổi áo này thành màu đen.

AI tìm sản phẩm/variant phù hợp nhưng chỉ cập nhật Outfit sau khi Customer xác nhận.



---



## FR-83 – Confirm AI Changes

Mọi thay đổi AI đề xuất cho Outfit đã lưu phải được Owner Confirm trước khi cập nhật Outfit.



---



## FR-84 – AI Chat History

Hệ thống có thể lưu lịch sử chat phục vụ việc tiếp tục conversation.

AI Chat History là dữ liệu riêng của Owner và không được hiển thị cho Viewer của Shared/Public Outfit.



---



# 17. Purchase From Outfit

Outfit không chỉ để tham khảo.

Các item trong Outfit liên kết trực tiếp với Product thực tế của shop.



---



## FR-85 – View Product From Outfit

Customer/Viewer có thể bấm vào item trong Outfit để xem Product Detail nếu Product còn ACTIVE.



---



## FR-86 – Select Variant From Outfit

Owner/Customer mua hàng có thể chọn:

- Color
- Size

trước khi Add To Cart.



---



## FR-87 – Add Outfit Item To Cart

Customer có thể thêm một item trong Outfit vào Cart.



---



## FR-88 – Buy Now From Outfit

Customer có thể chọn Buy Now từ một item trong Outfit.



---



## FR-89 – Add Multiple Outfit Items To Cart

Customer có thể chọn nhiều item trong Outfit và Add To Cart cùng lúc.



---



## FR-90 – Buy Entire Outfit

Customer có thể thêm toàn bộ item hợp lệ trong Outfit vào Cart.



---



# 18. Wardrobe Sharing

StyleMind hỗ trợ chia sẻ **toàn bộ My Wardrobe**, không giới hạn ở một Outfit riêng lẻ.

Mỗi Customer có một My Wardrobe chính, bên trong có nhiều Outfit. Khi một người có quyền xem Wardrobe, người đó có thể xem danh sách các Outfit và các sản phẩm bên trong (theo quyền VIEW), bao gồm cả Outfit được Owner tạo thêm sau khi chia sẻ.

Có ba chế độ Visibility:

- **PRIVATE:** Chỉ Owner được xem. Quyền share qua email được giữ lại nhưng tạm khóa xem Wardrobe và Chat.
- **PROTECTED:** Chỉ Owner và tài khoản đăng nhập bằng email đã được Owner chia sẻ, có quyền còn hiệu lực, được xem. Email Viewer được Chat 1–1 với Owner.
- **PUBLIC:** Bất kỳ ai có Public Link hợp lệ được xem Wardrobe, kể cả Guest; người chỉ có Public Link không được Chat. Email Viewer có quyền share còn hiệu lực vẫn được xem và Chat 1–1 với Owner.

Chia sẻ qua email và chia sẻ bằng Public Link là hai cơ chế khác nhau. **PUBLIC không tự động cấp quyền Chat.**

Quyền đối với Wardrobe được quản lý theo từng email được chia sẻ hoặc trạng thái Public Link, không tạo quyền chỉnh sửa cho Viewer.



---



## FR-91 – Share Entire My Wardrobe

Owner có thể chia sẻ toàn bộ My Wardrobe, thay vì chọn chia sẻ từng Outfit.

Một quyền truy cập Wardrobe hợp lệ cho phép Viewer xem các Outfit thuộc Wardrobe đó cùng Outfit Item và thông tin Product cần thiết.

Permission được áp dụng ở phạm vi `wardrobe_id`, không phải chỉ `outfit_id`.

Việc chia sẻ Wardrobe không cấp quyền xem Cart, Order, Address, Height/Weight, AI Chat History hoặc các dữ liệu cá nhân không liên quan.



---



## FR-92 – Share Wardrobe By Email

Owner nhập **Email được chia sẻ** để cấp quyền VIEW cho một người cụ thể.

Ví dụ:

Owner: Lan

Wardrobe: My Wardrobe của Lan

Email được chia sẻ: `mai@gmail.com`

Permission: VIEW

Quan hệ chia sẻ là **một chiều**: Lan chia sẻ cho Mai không có nghĩa Lan tự động được xem Wardrobe của Mai.

Owner có thể chia sẻ cho người chưa có tài khoản StyleMind. Hệ thống lưu lời chia sẻ theo email và không yêu cầu recipient Customer ID phải tồn tại tại thời điểm gửi lời mời.



---



## FR-93 – Wardrobe Share Invitation Email

Sau khi hệ thống ghi nhận yêu cầu Share By Email, Notification Service gửi email cho người nhận, bao gồm:

- Tên/người chia sẻ.
- Thông báo được mời xem My Wardrobe.
- Link trực tiếp tới My Wardrobe được chia sẻ.
- Hướng dẫn đăng nhập hoặc đăng ký StyleMind nếu cần.

Ví dụ:

Lan đã chia sẻ My Wardrobe với bạn.

[VIEW WARDROBE]

Việc lưu quyền chia sẻ và việc gửi email phải được xử lý đúng trạng thái thực tế; không thông báo email đã gửi thành công khi thao tác gửi chưa thành công.



---



## FR-94 – Restricted Email Share And Registration

Chỉ tài khoản có **email đã được xác thực** trùng với email được Owner cấp quyền và có Share Permission còn hiệu lực mới được xem Wardrobe theo cơ chế PROTECTED.

Nếu người nhận đã có tài khoản, họ đăng nhập bằng đúng email được chia sẻ để truy cập.

Nếu người nhận chưa có tài khoản, họ có thể mở email mời, đăng ký StyleMind bằng chính email đó, xác thực email rồi đăng nhập để xem Wardrobe khi quyền share vẫn còn hiệu lực.

Nếu email mời bị chuyển tiếp cho người khác, việc có link không đủ để vượt qua kiểm tra email trong chế độ PROTECTED.



---



## FR-95 – Wardrobe Viewer Permission

Viewer của Wardrobe chỉ có quyền **VIEW** đối với nội dung được chia sẻ.

Viewer được:

- Xem danh sách Outfit trong My Wardrobe được chia sẻ.
- Xem Outfit Detail, các item, ảnh sản phẩm, tên sản phẩm, màu, size nếu có và giá.
- Xem Product Detail nếu Product còn ACTIVE.
- Mua sản phẩm bằng tài khoản của chính mình theo nghiệp vụ mua hàng hiện hành.

Viewer không được:

- Tạo, sửa, đổi tên hoặc xóa Outfit trong Wardrobe của Owner.
- Add/Remove Product hoặc đổi Variant trong Outfit của Owner.
- Dùng Ask AI để thay đổi Outfit của Owner.
- Thay đổi Visibility hoặc quản lý Share Permission của Owner.
- Xem dữ liệu riêng tư hoặc lịch sử AI Stylist của Owner.

Quyền Chat, nếu có, được cấp riêng theo email share hợp lệ theo Phần 32, không nằm trong quyền VIEW công khai.



---



## FR-96 – Wardrobe-Level Access Isolation

Backend phải kiểm tra quyền xem theo **người truy cập + Wardrobe + Visibility + Share Permission** tại mỗi tài nguyên liên quan.

- **OWNER:** Được xem và quản lý My Wardrobe của mình.
- **PRIVATE:** Chỉ Owner được xem; các quyền email-share còn lưu nhưng tạm không có hiệu lực truy cập và Chat.
- **PROTECTED:** Chỉ Owner và tài khoản có email đã xác thực, được share và chưa bị revoke, được xem.
- **PUBLIC:** Người có Public Link hợp lệ được xem; chỉ Email Viewer có share còn hiệu lực mới có thêm quyền Chat.

Quyền truy cập phải áp dụng nhất quán cho Wardrobe, Outfit, Outfit Item, hình ảnh/tài nguyên riêng tư và shared chat context. Người không có quyền không được vượt qua kiểm tra bằng cách gọi API trực tiếp theo ID hoặc URL.



---



## FR-97 – Manage Shared People (Manage Friends)

Owner có thể mở **Manage Friends / People with Access** để xem danh sách các email đã được chia sẻ My Wardrobe và trạng thái quyền của từng email.

Ví dụ:

People with access

- `mai@gmail.com` – VIEW – ACTIVE
- `hoa@gmail.com` – VIEW – ACTIVE

Manage Friends **không phải tính năng kết bạn hai chiều như Facebook**: không có gửi lời mời kết bạn, Accept/Reject Friend Request hoặc Unfriend theo mô hình mạng xã hội.

Danh sách này được quản lý theo quyền Share Wardrobe, không mặc định phát sinh quan hệ bạn bè tương hỗ giữa hai Customer.



---



## FR-98 – Revoke And Re-share Wardrobe Access

Owner có thể Remove Access của một email cụ thể mà không ảnh hưởng các email khác.

Khi Owner revoke:

- Email đó mất quyền xem Wardrobe theo chia sẻ riêng.
- Email đó mất quyền truy cập và gửi tin nhắn trong Chat 1–1 liên quan đến Wardrobe.
- Hệ thống **không xóa lịch sử Chat**.
- Nếu Wardrobe đang PUBLIC, người bị revoke vẫn có thể xem như Public Viewer qua Public Link nhưng **không được Chat**.

Khi Owner chia sẻ lại cùng email:

- Quyền xem và Chat được khôi phục khi Visibility cho phép.
- Người nhận có thể mở và **tiếp tục ChatSession cũ** cùng lịch sử trước đây sau khi đăng nhập hợp lệ.
- Không tạo quan hệ chia sẻ trùng lặp cho cùng Wardrobe và email.



---



## FR-99 – Public Wardrobe Link

Owner có thể bật **PUBLIC** cho My Wardrobe và tạo/sao chép Public Link trỏ đến toàn bộ Wardrobe.

Ví dụ đường dẫn minh họa:

`/stylemind/shared/wardrobe/{token}`

Bất kỳ người nào có link hợp lệ đều có thể xem Wardrobe và các Outfit được phép hiển thị, kể cả người chưa đăng nhập.

Public Link không cấp quyền chỉnh sửa Wardrobe hoặc Chat với Owner.

Người được chia sẻ qua email vẫn có quyền Chat khi Wardrobe PUBLIC nếu Share Permission còn hiệu lực và tài khoản đã xác thực đúng email.



---



## FR-100 – Disable Public Access And Visibility Changes

Owner có thể chuyển Wardrobe từ PUBLIC sang PROTECTED hoặc PRIVATE. Khi không còn PUBLIC, Public Link không được tiếp tục cấp quyền truy cập công khai.

Khi chuyển sang PRIVATE:

- Những email được chia sẻ trước đó **không bị xóa hoặc tự động revoke**.
- Email Viewer tạm thời không xem được Wardrobe hoặc Chat.
- Lịch sử Chat vẫn được lưu.

Khi chuyển PRIVATE → PROTECTED, các email vẫn có quyền share ACTIVE được xem và tiếp tục Chat cũ. Email từng bị Owner chủ động REVOKE **không tự được khôi phục** chỉ nhờ thay đổi Visibility.

Khi PUBLIC, Email Viewer ACTIVE vẫn được Chat; người chỉ có Public Link chỉ được xem.



---



## FR-101 – Shared With Me

Customer đăng nhập có thể có khu vực **Shared With Me** để xem các **My Wardrobe** đã được người khác chia sẻ cho email của mình.

Một Wardrobe được chia sẻ tương ứng với quyền xem toàn bộ Outfit trong Wardrobe đó khi Visibility và Share Permission cho phép.

Những lời chia sẻ gửi đến email chưa đăng ký chỉ trở thành quyền truy cập của Customer sau khi người nhận đăng ký, xác thực đúng email và đăng nhập.



---



## FR-102 – Shared Wardrobe Privacy

Shared/Public Wardrobe chỉ hiển thị dữ liệu cần thiết cho việc xem Wardrobe, Outfit và Product.

Không hiển thị:

- Height.
- Weight.
- AI Chat History.
- User feedback trong AI Chat.
- Address.
- Cart.
- Order và thông tin thanh toán.
- Tin nhắn Chat 1–1 của những người khác.
- Dữ liệu cá nhân không cần thiết.

Lịch sử **AI Wardrobe Stylist** là dữ liệu riêng của Owner, độc lập với **Chat with Friends**. Shared/Public Viewer không được xem lịch sử AI Stylist.



---



# 19. Checkout

## FR-103 – Checkout

Customer checkout các sản phẩm trong Cart.



---



## FR-104 – Chọn địa chỉ

Customer chọn shipping address.



---



## FR-105 – Chọn Payment Method

Customer chọn:

- COD
- SePay/VietQR



---



## FR-106 – Backend tính tổng

Backend tính:

- Product subtotal
- Shipping
- Discount nếu có
- Total

Frontend không được tự quyết định giá.



---



## FR-107 – Price At Purchase

Order Item lưu giá tại thời điểm mua.



---



# 20. Payment

## FR-108 – COD

Customer có thể thanh toán COD.



---



## FR-109 – SePay

Customer có thể thanh toán bằng QR.



---



## FR-110 – Payment Webhook

Payment Service nhận webhook từ SePay.



---



## FR-111 – Validate Payment

Payment Service kiểm tra:

- Transaction
- Nội dung chuyển khoản
- Amount



---



## FR-112 – Duplicate Webhook

Không xử lý cùng transaction nhiều lần.



---



# 21. Order

## FR-113 – Order List

Customer xem danh sách Order.



---



## FR-114 – Order Detail

Customer xem:

- Products
- Variant
- Quantity
- Price
- Address
- Payment
- Total
- Status



---



## FR-115 – Order Status

Order có thể gồm:

- PENDING
- PAYMENT_PENDING
- PAID
- CONFIRMED
- PROCESSING
- SHIPPED
- COMPLETED
- CANCELLED
- EXPIRED
- FAILED



---



## FR-116 – Order State Machine

Order chỉ được chuyển trạng thái theo flow hợp lệ.



---



# 22. Cancellation

## FR-117 – Customer Cancel Request

Customer có thể yêu cầu hủy Order nếu trạng thái cho phép.



---



## FR-118 – Cancel Reason

Customer phải nhập lý do.



---



## FR-119 – Admin Review

Admin có thể:

- Approve
- Reject



---



# 23. Return & Refund

## FR-120 – Return Request

Customer có thể yêu cầu đổi/trả sản phẩm.



---



## FR-121 – Return Reason

Customer nhập:

- Item
- Quantity
- Reason
- Description
- Evidence nếu cần



---



## FR-122 – Admin Review Return

Admin có thể:

- Approve
- Reject



---



## FR-123 – Refund

Nếu đủ điều kiện, hệ thống hỗ trợ Refund.



---



# 24. Notification

## FR-124 – Notification List

Customer có thể xem Notification.



---



## FR-125 – Unread Notification

Customer xem số Notification chưa đọc.



---



## FR-126 – Mark Read

Customer có thể đánh dấu đã đọc.



---



## FR-127 – Email Notification

Hệ thống có thể gửi email cho:

- OTP
- Reset Password
- Order Confirmation
- Payment
- Order Status
- Cancellation
- Return
- Refund
- **Wardrobe Share Invitation**



---



## FR-128 – Wardrobe Share Email Link

Email share My Wardrobe phải chứa link trực tiếp tới My Wardrobe của Owner.

Link email không tự cấp quyền cho người khác: ở chế độ PROTECTED, hệ thống phải xác thực email tài khoản được chia sẻ và Share Permission còn hiệu lực. Người chưa đăng ký có thể đăng ký/xác thực bằng đúng email để truy cập.



---



# 25. Admin Product Management

## FR-129 – Create Product

Admin tạo Product.



---



## FR-130 – Update Product

Admin sửa Product.



---



## FR-131 – Active/Inactive Product

Admin quản lý trạng thái Product.



---



## FR-132 – Manage Variant

Admin quản lý:

- Color
- Size
- Price
- Status



---



## FR-133 – Manage Image By Color

Admin upload ảnh tương ứng với từng màu.



---



## FR-134 – Manage Fashion Metadata

Admin quản lý:

- Style
- Occasion
- Pattern
- Fit
- Material
- Color



---



# 26. AI Product Synchronization

## FR-135 – Product Created

Product mới được sync sang AI Service/index.



---



## FR-136 – Product Updated

Product thay đổi được cập nhật lại trong AI index.



---



## FR-137 – Product Inactive

Product/Variant INACTIVE không được AI recommendation.



---



## FR-138 – Product Deleted

Product bị xóa phải được xóa khỏi AI index.



---



# 27. Admin AI Management

Admin có thể quản lý dữ liệu phục vụ AI.



---



## FR-139 – Style Management

Admin quản lý Style.

Ví dụ:

- Feminine
- Minimal
- Elegant
- Romantic
- Casual
- Streetwear



---



## FR-140 – Occasion Management

Admin quản lý Occasion.



---



## FR-141 – Compatibility Rules

Hệ thống có thể lưu các rule hỗ trợ AI như:

- Loại áo nào phù hợp loại quần nào
- Màu nào phù hợp nhau
- Outfit nào phù hợp Occasion
- Style nào phù hợp các loại trang phục nào



---



# 28. Dashboard

Admin có thể xem:

- Tổng User
- Tổng Product
- Tổng Order
- Revenue
- Order Status
- Payment
- Return
- Refund

AI/Wardrobe Dashboard có thể xem:

- Tổng số Outfit được tạo
- Số Outfit tạo bằng AI
- Số Outfit tạo thủ công
- Số lần AI Generate Outfit
- Số lần AI Replace Product
- Số sản phẩm AI đề xuất được Add To Cart
- Số Order có nguồn từ AI Recommendation
- Số lượt chia sẻ My Wardrobe bằng email
- Số My Wardrobe đang bật Public Link

Không dùng metric “số Wardrobe được tạo” vì mỗi Customer có một My Wardrobe chính.



---



# 29. Core Flow Của StyleMind

## 29.1. Flow tạo Outfit bằng AI

CUSTOMER

   ↓

MY WARDROBE

   ↓

CREATE OUTFIT

   ↓

CREATE WITH AI

   ↓

AI FIRST MESSAGE

   ↓

AI HỎI NHU CẦU

   ↓

CUSTOMER TRẢ LỜI

   ↓

AI KIỂM TRA HEIGHT / WEIGHT

   ↓

AI ANALYZE REQUIREMENT

   ↓

SEARCH PRODUCT CỦA SHOP

   ↓

VALIDATE PRODUCT / VARIANT

   ↓

AI GENERATE OUTFIT

   ↓

AI GIẢI THÍCH

   ↓

"BẠN CÓ ƯNG OUTFIT NÀY KHÔNG?"

   ↓

   ├── NO → USER FEEDBACK → AI REVISE → HỎI LẠI

   │

   └── YES → NAME OUTFIT → SAVE TO MY WARDROBE



---



## 29.2. Flow tự tạo Outfit

CUSTOMER

   ↓

MY WARDROBE

   ↓

CREATE OUTFIT

   ↓

CREATE MANUALLY

   ↓

SEARCH PRODUCT

   ↓

ADD PRODUCT

   ↓

ADD MORE PRODUCT nếu muốn

   ↓

NAME OUTFIT

   ↓

SAVE

Sau đó Customer vẫn có thể mở Outfit và chọn **Ask AI** để nhờ AI phối thêm/chỉnh sửa.



---



## 29.3. Flow chỉnh Outfit bằng AI

MY WARDROBE

   ↓

OPEN EXISTING OUTFIT

   ↓

ASK AI

   ↓

AI LOAD CURRENT OUTFIT CONTEXT

   ↓

AI BIẾT CẢ ITEM DO USER TỰ THÊM

   ↓

USER YÊU CẦU THAY / THÊM / PHỐI LẠI

   ↓

AI SEARCH PRODUCT

   ↓

AI RECOMMEND CHANGE

   ↓

OWNER CONFIRM

   ↓

UPDATE OUTFIT



---



## 29.4. Flow Share My Wardrobe By Email

OWNER

   ↓

OPEN MY WARDROBE

   ↓

SHARE BY EMAIL → ENTER RECIPIENT EMAIL

   ↓

CREATE / RE-ACTIVATE WARDROBE VIEW PERMISSION

   ↓

NOTIFICATION SERVICE → SEND EMAIL + WARDROBE LINK

   ↓

RECIPIENT CLICK LINK

   ↓

ALREADY REGISTERED?

   ├── YES → LOGIN WITH VERIFIED SHARED EMAIL

   └── NO → REGISTER + VERIFY SHARED EMAIL → LOGIN

   ↓

VALIDATE VISIBILITY + ACTIVE SHARE PERMISSION

   ↓

VIEW ENTIRE MY WARDROBE + ALL OUTFITS

   ↓

CHAT 1–1 WITH OWNER (nếu PROTECTED/PUBLIC và email share vẫn ACTIVE)

Người được share qua email có quyền VIEW, không có quyền chỉnh sửa Outfit hoặc Wardrobe của Owner.



---



## 29.5. Flow Public My Wardrobe

OWNER

   ↓

OPEN MY WARDROBE

   ↓

SET VISIBILITY = PUBLIC

   ↓

GENERATE / ENABLE PUBLIC WARDROBE LINK

   ↓

OWNER COPY / SHARE LINK

   ↓

ANYONE WITH VALID LINK

   ↓

VIEW ENTIRE MY WARDROBE (READ ONLY)

   ↓

NO CHAT FOR PUBLIC-LINK-ONLY VIEWER

Email Viewer có Share Permission ACTIVE vẫn được Chat 1–1 với Owner ngay cả khi Wardrobe đang PUBLIC.



---



## 29.6. Flow Manage Friends / Revoke

OWNER → MY WARDROBE → PEOPLE WITH ACCESS

   ↓

VIEW SHARED EMAILS

   ↓

SELECT EMAIL → REMOVE ACCESS

   ↓

REVOKE EMAIL SHARE

   ↓

STOP PRIVATE WARDROBE ACCESS + STOP CHAT FOR RECIPIENT

   ↓

KEEP EXISTING CHAT HISTORY

Nếu Wardrobe đang PUBLIC, người bị revoke vẫn có thể xem qua Public Link như Public Viewer, nhưng không thể Chat.



---



## 29.7. Flow Chat With Friends

EMAIL VIEWER (VALID EMAIL SHARE)

   ↓

OPEN SHARED WARDROBE → CHAT WITH OWNER

   ↓

VALIDATE LOGIN + EMAIL SHARE + VISIBILITY

   ↓

FIND EXISTING 1–1 CHAT SESSION OR CREATE ONE

   ↓

LOAD PRIVATE CHAT HISTORY

   ↓

SEND / RECEIVE MESSAGES

   ↓

OWNER CAN REPLY FROM MESSAGES AREA

Người được share theo email và Owner có một hội thoại riêng; người được share khác hoặc Public Viewer không thể xem hội thoại đó. AI Chat History không nằm trong Chat với bạn bè.



---



## 29.8. Flow Restore Visibility / Re-share

OWNER SETS WARDROBE = PRIVATE

   ↓

SUSPEND VIEW + CHAT FOR ALL EMAIL VIEWERS, KEEP SHARES AND HISTORY

   ↓

OWNER SETS WARDROBE = PROTECTED

   ↓

RESTORE VIEW + CHAT FOR NON-REVOKED EMAIL SHARES

Hoặc nếu Owner đã REVOKE một email:

OWNER RE-SHARES SAME EMAIL

   ↓

RE-ACTIVATE SHARE

   ↓

OPEN EXISTING CHAT SESSION + CONTINUE PREVIOUS MESSAGES



---



# 30. Những chức năng KHÔNG có / Giới hạn Scope

Theo scope hiện tại:

**Không có nhiều Wardrobe độc lập cho một Customer.**

Mỗi Customer có một My Wardrobe chính, bên trong có nhiều Outfit.

**Không có Fashion Preference cố định ở User Profile.**

Chỉ lưu Height/Weight phục vụ recommendation size. Style, Occasion, Color, Budget và yêu cầu phối đồ được hỏi theo từng conversation/Outfit.

**Không có Inventory/Warehouse Management.**

Không quản lý:

- Kho hàng
- Nhập kho
- Xuất kho
- Tồn kho

**Không bán Accessories.**

Không tập trung vào:

- Mũ
- Túi
- Trang sức
- Giày
- Các phụ kiện khác

AI chỉ phối Outfit bằng **quần áo mà shop kinh doanh**.

**Không cho Viewer sửa Outfit của Owner trong scope hiện tại.**

Share permission chỉ là VIEW.

**Chia sẻ được thực hiện ở cấp My Wardrobe, không phải từng Outfit riêng lẻ trong phạm vi nghiệp vụ EP-06 đã cập nhật.**

Owner có thể chọn PRIVATE, PROTECTED (chia sẻ qua email) hoặc PUBLIC (link công khai). Người xem chỉ có quyền VIEW; quyền Chat 1–1 chỉ dành cho Email Viewer có Share Permission còn hiệu lực khi Visibility cho phép.

**Không có tính năng kết bạn hai chiều kiểu mạng xã hội.**

Manage Friends chỉ quản lý những email được Owner chia sẻ My Wardrobe; không có Add Friend, Accept/Reject Friend Request hay Unfriend theo mô hình Facebook.

**Không có Chat nhóm hoặc Chat tự do ngoài ngữ cảnh Wardrobe được chia sẻ.**

Chat with Friends là cuộc trò chuyện riêng 1–1 giữa Owner và Email Viewer. Người chỉ có Public Link không được Chat.

**Không public Height/Weight hoặc AI Chat History.**



---



# 31. Điểm cốt lõi của StyleMind

StyleMind không đơn giản là:

**E-commerce + AI Chatbot.**

Mà chức năng đặc trưng là:

CUSTOMER VÀO MY WARDROBE

        ↓

CHỌN CREATE WITH AI HOẶC CREATE MANUALLY

        ↓

NẾU DÙNG AI → AI CHỦ ĐỘNG HỎI NHU CẦU

        ↓

AI HIỂU OCCASION / STYLE / COLOR / BUDGET / YÊU CẦU

        ↓

AI SỬ DỤNG HEIGHT / WEIGHT ĐỂ HỖ TRỢ GỢI Ý SIZE

        ↓

AI TÌM QUẦN ÁO THẬT CỦA SHOP

        ↓

AI PHỐI OUTFIT

        ↓

AI HỎI CUSTOMER CÓ ƯNG KHÔNG

        ↓

NẾU CHƯA ƯNG → NHẬN FEEDBACK → CHỈNH OUTFIT

        ↓

NẾU ƯNG → CONFIRM → ĐẶT TÊN → LƯU OUTFIT

        ↓

CUSTOMER CÓ THỂ MỞ LẠI OUTFIT ĐỂ EDIT / ASK AI

        ↓

CUSTOMER CÓ THỂ TỰ THÊM PRODUCT VÀ AI PHẢI BIẾT PRODUCT ĐÓ

        ↓

CUSTOMER CÓ THỂ MUA ITEM / MUA CẢ OUTFIT

        ↓

CUSTOMER CÓ THỂ SHARE TOÀN BỘ MY WARDROBE QUA EMAIL HOẶC PUBLIC LINK

        ↓

PRIVATE: CHỈ OWNER XEM / PROTECTED: EMAIL VIEWER XEM + CHAT / PUBLIC: LINK VIEWER CHỈ XEM

        ↓

EMAIL VIEWER ĐƯỢC CHAT RIÊNG 1–1 VỚI OWNER KHI SHARE CÒN HIỆU LỰC

        ↓

OWNER CÓ THỂ REVOKE VÀ RE-SHARE; LỊCH SỬ CHAT ĐƯỢC GIỮ ĐỂ TIẾP TỤC SAU KHI CHIA SẺ LẠI





---



# 32. EP-06 – Manage Friends & Chat With Friends

Phần này bổ sung các yêu cầu chức năng mới liên quan đến Manage Friends và Chat with Friends. Các yêu cầu chia sẻ My Wardrobe, chế độ Visibility và quyền Viewer được cập nhật ở **Phần 18 (FR-91 đến FR-102)**.

## 32.1. Manage Friends – Quản lý người được chia sẻ

Trong phạm vi StyleMind, Manage Friends là danh sách các địa chỉ email mà Owner đã chia sẻ My Wardrobe. Đây **không phải** quan hệ kết bạn hai chiều.

## FR-142 – View Shared Email List

Owner có thể mở Manage Friends / People with Access để xem các email đã được cấp quyền VIEW cho My Wardrobe, cùng trạng thái quyền hiện tại.

Danh sách này được lấy từ quan hệ chia sẻ Wardrobe, không yêu cầu Friend Request hoặc một Customer đã đăng ký trước đó.



---



## FR-143 – Prevent Invalid And Duplicate Sharing

Hệ thống không cho Owner chia sẻ My Wardrobe cho chính email của mình.

Không được tạo hai quan hệ chia sẻ logic trùng cho cùng một Wardrobe và cùng một email người nhận (kể cả khi nhập khác cách viết hoa/thường theo quy tắc chuẩn hóa email thống nhất).

Nếu email đã bị revoke và Owner chia sẻ lại, hệ thống kích hoạt lại quan hệ chia sẻ trước đó thay vì tạo bản ghi trùng.



---



## FR-144 – Manage Sharing Access Securely

Chỉ Owner mới có quyền xem danh sách quản lý email được share, cấp quyền, revoke hoặc cấp lại quyền cho My Wardrobe của mình.

Người được share, Public Viewer và những Customer khác không được thay đổi Share Permission của Owner.

Việc revoke một email không ảnh hưởng đến các email khác và không xóa Chat History đã lưu.



---



## 32.2. Chat with Friends – Trò chuyện theo Wardrobe

Chat với bạn bè cho phép **Owner trò chuyện riêng 1–1 với người được cấp quyền chia sẻ qua email** về trang phục trong My Wardrobe của Owner.

Khi Email Viewer mở Wardrobe được chia sẻ hợp lệ, giao diện có nút **Chat với chủ tủ đồ**. Owner có mục **Tin nhắn / Messages** riêng để xem và trả lời từng cuộc trò chuyện mà không cần mở Wardrobe.

Chat này tách biệt hoàn toàn với AI Wardrobe Stylist và lịch sử AI Chat của Owner.

## FR-145 – Open Chat From Shared Wardrobe

Email Viewer đã đăng nhập bằng email được xác thực, có Share Permission ACTIVE và Wardrobe đang PROTECTED hoặc PUBLIC có thể mở Chat 1–1 với Owner từ giao diện Shared Wardrobe.

Người chỉ có Public Link, người bị revoke và người được share nhưng Wardrobe đang PRIVATE không được mở hoặc sử dụng Chat với Owner.



---



## FR-146 – Chat Session Linked To Wardrobe

Mỗi ChatSession gắn với một Wardrobe và chỉ có hai người tham gia: Owner và một Email Viewer có quyền hợp lệ.

Một Wardrobe có thể có nhiều ChatSession riêng biệt, ứng với các Email Viewer khác nhau.

Hệ thống không tạo ChatSession trùng cho cùng Wardrobe và cùng người nhận khi mở lại cuộc trò chuyện.



---



## FR-147 – Chat Participant Authorization

Chỉ hai thành viên của ChatSession mới được truy cập nội dung hội thoại.

Mỗi thao tác đọc lịch sử hoặc gửi tin nhắn của Email Viewer phải kiểm tra quyền chia sẻ hiện tại, không chỉ dựa vào lần kiểm tra trước đó.

Người xem Public Link không thể tham gia Chat chỉ nhờ biết link Wardrobe hoặc ID cuộc hội thoại.



---



## FR-148 – Send Chat Message

Owner và Email Viewer có quyền hợp lệ có thể gửi tin nhắn trong ChatSession 1–1 của mình khi Wardrobe đang PROTECTED hoặc PUBLIC.

Hệ thống ghi nhận người gửi, nội dung, thời điểm và thứ tự tin nhắn; không cho gửi nếu người gửi không thuộc Session hoặc quyền Chat không còn hợp lệ.

Phương thức truyền tin (REST/WebSocket) và loại nội dung ngoài văn bản chưa được xác nhận trong nghiệp vụ hiện tại.



---



## FR-149 – View Chat History

Hệ thống lưu và hiển thị lịch sử tin nhắn trong từng ChatSession theo đúng thứ tự.

Email Viewer chỉ xem được lịch sử Chat giữa chính mình và Owner khi quyền Chat còn hợp lệ. Không hiển thị tin nhắn của những Email Viewer khác hoặc AI Chat History của Owner.



---



## FR-150 – Handle Repeated Sends And Message Ordering

Backend phải duy trì thứ tự tin nhắn ổn định khi hai người nhắn tin đồng thời.

Cùng một thao tác gửi bị Client retry không được tạo tin nhắn trùng; hai thao tác gửi khác nhau vẫn có thể chứa cùng nội dung.



---



## FR-151 – Pause Chat When Wardrobe Is Private

Khi Owner chuyển Wardrobe sang PRIVATE, Email Viewer tạm thời mất quyền xem Wardrobe, gửi tin và đọc lịch sử Chat.

Hệ thống giữ nguyên email share ACTIVE, ChatSession và ChatMessage. Khi Owner chuyển lại PROTECTED, Email Viewer chưa bị revoke được quyền xem và tiếp tục Chat cũ.



---



## FR-152 – Revoke Chat Access Without Deleting History

Khi Owner revoke một email đã được share, người nhận mất quyền gửi và truy cập lịch sử Chat liên quan đến Wardrobe, kể cả khi Wardrobe đang PUBLIC.

ChatSession và ChatMessage phải được giữ lại, không bị xóa theo thao tác Revoke.

Nếu Wardrobe PUBLIC, người bị revoke chỉ còn quyền xem như Public Viewer qua link hợp lệ, **không có quyền Chat**.



---



## FR-153 – Resume Existing Chat After Re-share

Khi Owner chia sẻ lại cho đúng email từng bị revoke và Wardrobe đang PROTECTED hoặc PUBLIC, người nhận được quyền mở lại hội thoại 1–1 cũ cùng lịch sử trước đó và tiếp tục Chat.

Không tự động tạo cuộc hội thoại mới thay cho Session cũ.



---



## FR-154 – Owner Messages Area

Owner có khu vực **Messages / Tin nhắn** để xem danh sách các cuộc trò chuyện 1–1 liên quan đến My Wardrobe và trả lời người được chia sẻ đang có quyền Chat.

Các hội thoại được phân biệt theo từng người nhận. Người nhận A không được xem cuộc trò chuyện giữa Owner và người nhận B.



---



## FR-155 – Chat Privacy And Wardrobe Authorization

Backend phải kiểm tra quyền truy cập Shared Chat Context dựa trên Customer, ChatSession, Wardrobe Visibility và trạng thái Share Permission.

Không tiết lộ Chat History trong các API Shared/Public Wardrobe. Chỉ trả lịch sử hội thoại qua chức năng Chat có kiểm tra quyền riêng.



---



## 32.3. Điểm cần thống nhất khi triển khai

Các nội dung sau **chưa được chốt** bằng quyết định nghiệp vụ và không nên tự coi là yêu cầu bắt buộc:

- **Friendship migration trong task cũ:** Task yêu cầu unique customer pairs nhưng nghiệp vụ mới không có kết bạn hai chiều và hỗ trợ email chưa đăng ký. Cần nhóm xác nhận điều chỉnh task thành quản lý email WardrobeShare hoặc định nghĩa chính thức vai trò riêng của Friendship.
- **Chat transport:** REST polling, WebSocket hoặc kết hợp.
- **Chat content:** Chỉ tin nhắn văn bản hay thêm ảnh, link Outfit/Product.
- **Share mới khi PRIVATE:** Owner có thể thêm email đang PRIVATE hay phải chuyển sang PROTECTED/PUBLIC.
- **Public Link lifecycle:** Hết hạn, tạo lại hoặc đổi token như thế nào.
- **Owner đọc lịch sử Chat khi PRIVATE / sau revoke:** Cần chốt rõ điều kiện xem lịch sử cũ đối với Owner.

Các phần khác của tài liệu Functional Requirements được giữ nguyên khi không liên quan đến thay đổi EP-06.
