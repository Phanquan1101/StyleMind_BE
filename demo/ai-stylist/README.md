# Dựng AI Stylist cho thành viên mới

Gói này dựng **local demo cô lập**, gồm PostgreSQL, Qdrant, Neo4j mới và Commerce, Stylist, Gateway thật. Bootstrap dùng Gemini HTTP fixture offline; không đọc API key, không gọi Google, không reset meter. Các service đang chạy của bạn không bị dừng hay đổi cổng.

## Lệnh chạy

Cần Docker Desktop đang chạy Linux Engine, Java 21 trở lên, Maven 3.9, Node.js 22 trở lên và Windows PowerShell 5.1/PowerShell 7 trong PATH. Lần đầu cần mạng để tải Docker images, Maven dependencies và kiểm tra ảnh sản phẩm.

Clone lần đầu:

```powershell
git clone https://github.com/Phanquan1101/StyleMind_BE.git
cd StyleMind_BE
git switch feature/ep-07-ai-stylist
```

Đã có repo thì cập nhật đúng branch team đã bàn giao, giữ lại công việc riêng trước khi pull:

```powershell
git pull --ff-only
```

Từ thư mục gốc repository:

```powershell
docker info
powershell -NoProfile -File .\scripts\bootstrap-ai-stylist-demo.ps1 -VerifyOffline
```

Script tự chạy Docker Compose, build JAR trong thư mục riêng, áp dụng **migrations hiện tại bằng chính ứng dụng**, seed catalog, restore vector và chạy `KnowledgeArtifactImporter` sẵn có. Không cần chạy importer embedding. Các file mới phải được owner đưa vào commit bàn giao trước khi thành viên có thể nhận qua `git pull`; script không commit/push.

Chạy lại cùng lệnh an toàn: catalog dùng `ON CONFLICT DO NOTHING`, Neo4j importer dùng `MERGE`, Qdrant kiểm tra đúng dữ liệu rồi giữ nguyên. Nếu dữ liệu cùng ID khác nội dung, script báo lỗi thay vì reset/ghi đè. `-VerifyOffline` tái sử dụng request kiểm chứng đã lưu, không tạo thêm request mỗi lần chạy.

Mặc định project là `stylemind-ai-demo-team`, cổng riêng:

| Thành phần | Địa chỉ |
|---|---|
| PostgreSQL | `localhost:25433` |
| Qdrant | `http://localhost:26333` |
| Neo4j Bolt | `bolt://localhost:27687` |
| Commerce offline demo | `http://localhost:28082` |
| Stylist offline demo | `http://localhost:28085` |
| Gateway offline demo | `http://localhost:28080` |

Kết quả ở `stylist-service/target/team-demo/stylemind-ai-demo-team/readiness.json`; log từng ứng dụng ở cùng thư mục. Credentials local được sinh ngẫu nhiên một lần và giữ trong thư mục `target` bị Git ignore. Đừng xóa thư mục này khi còn dùng volumes của project vì nó giữ credentials và PID của demo.

Để dùng một bộ dữ liệu mới khác, chọn project và dải cổng khác, không cần xóa bộ cũ:

```powershell
powershell -NoProfile -File .\scripts\bootstrap-ai-stylist-demo.ps1 -ProjectName stylemind-ai-demo-fresh -PortOffset 22000 -VerifyOffline
```

## Dùng dữ liệu với IntelliJ và frontend của team

Giữ file `.env` team đã cung cấp. Chạy thêm tùy chọn sau để ghi **cấu hình dữ liệu demo vào đúng `.env` gốc**, không tạo `.env.stylist`:

```powershell
powershell -NoProfile -File .\scripts\bootstrap-ai-stylist-demo.ps1 -VerifyOffline -ConfigureRootEnv
```

Chỉ các biến database Commerce/Stylist, Qdrant, policy và Neo4j/knowledge được cập nhật. API key, live flag, meter, JWT/JWKS và cấu hình các service khác giữ nguyên. Tùy chọn này chủ động chuyển hai service local của bạn sang dữ liệu demo; không dùng với cấu hình shared/production.

Trong IntelliJ, `CommerceServiceApplication` và `StylistServiceApplication` nạp `.env` gốc bằng EnvFile, rồi stop/run lại để nhận cấu hình. Run Identity/Gateway và các service khác theo cấu hình local hiện tại của team. Các ứng dụng IntelliJ dùng cổng thông thường 8082/8085/8080; bootstrap offline dùng 28082/28085/28080 nên không xung đột. FE vẫn gọi Gateway local theo `.env` FE của team.

Gateway offline chỉ dùng JWT thử nghiệm được sinh trong quá trình kiểm chứng; nó không cung cấp chức năng đăng nhập thay Identity và không phải endpoint để đăng nhập tài khoản cá nhân trên FE. Bootstrap không tự chuyển ứng dụng sang Gemini Live. Nếu chạy Gemini Live sau này, giữ quy trình key/meter/quota đã được owner cấp riêng.

## Dữ liệu được đóng gói

| File | Nguồn và mục đích |
|---|---|
| `commerce-manifest.json` | 10 product IDs khác nhau, 5 TOP + 5 BOTTOM; mỗi product đúng một variant từ development seed 381 sản phẩm |
| `commerce-seed.sql` | SQL 10 sản phẩm đã chuẩn bị; chỉ thêm `ON CONFLICT DO NOTHING`, không copy tài khoản, đơn hàng hay DB cá nhân |
| `qdrant-points.json` | 10 embedding Gemini thật đã tạo ngày 09/11/2026 và 1 index manifest; giữ nguyên IDs, payload, vector 1536 chiều, Cosine |
| `catalog-policy.json` | Mapping TOP/BOTTOM đã duyệt cho local smoke/demo distinct10; không phải mapping production |
| `knowledge-artifact.json` | Artifact **fixture demo gốc** mà runtime đang yêu cầu: `synthetic-wave2-http-knowledge / fixture-v1` |
| `bundle.json` | SHA-256 các file, 10 SHA-256 vector float32 và nguồn gốc để phát hiện thiếu/sai dữ liệu |

Các vector export khớp từng SHA-256 trong `reports/gemini-10-product-demo/real10-embedding-verification.json`. Bootstrap không tạo vector mới và không thay vector catalog bằng synthetic. Trong bước Generate offline, **query embedding và generation response là HTTP fixture**; đây là kiểm chứng tích hợp, không chứng minh chất lượng tìm kiếm semantic hay lời tư vấn Gemini Live.

Nguồn knowledge thật sự hiện có trong source là `writeArtifacts()` của `stylist-service/scripts/wave2-disposable-gateway-harness.cjs`. File được khôi phục nguyên metadata `synthetic-fixture-only`, timestamps và `synthetic://wave2-http-fixture`; không gán reviewer là owner/leader, không sửa nhãn để giả làm tri thức thời trang đã duyệt. Importer hiện có kiểm tra metadata rồi `MERGE` theo artifact ID/version và entry ID, nên Neo4j mới vượt validation và chạy lại không duplicate. Để demo tri thức thời trang thật, leader cần bàn giao artifact có nội dung, nguồn và review thật; package này không tuyên bố đã có phần đó.

Gói phục vụ phân phối nội bộ team theo chỉ đạo owner. Không kèm API key, `.env`, private key, lịch sử hội thoại hoặc dump database cá nhân. Việc dùng output Gemini không tự cấp quyền với catalog/ảnh gốc; repo hiện chưa có bằng chứng cấp quyền phân phối công khai toàn bộ dataset/ảnh. Owner cần xác nhận quyền đó trước khi publish bundle ra ngoài team. Tham khảo [điều khoản Gemini API](https://ai.google.dev/gemini-api/terms); bundle không cấp lại license dataset hay ảnh.

## Readiness và xử lý lỗi

`-VerifyOffline` kiểm tra:

1. Migrations Commerce/Stylist chạy đủ trên PostgreSQL mới.
2. Commerce API trả đúng 10 sản phẩm/10 variants ACTIVE, có số lượng khả dụng, tên/màu/giá và ảnh HTTP 200.
3. Qdrant có đúng 11 points, gồm 10 vectors khác nhau; dimension, Cosine, IDs, toàn bộ payload và hashes khớp bundle.
4. Neo4j có đúng 1 artifact + 1 entry fixture gốc.
5. Gateway thật nhận create-session → intake `RECEIVED` → Generate `SUCCEEDED` → GET detail có TOP/BOTTOM, giá và ảnh.
6. Recommendation đã lưu PostgreSQL; owner khác không đọc được; Generate replay không gọi lại provider.

Nếu thiếu file/checksum sai, script dừng trước khi khởi động Docker. Nếu existing IDs/vector/payload khác bundle, script dừng và không reset. Nếu cổng bận hoặc Java không lên, đọc log được chỉ ra; chọn project/dải cổng khác, đừng dừng nhầm service cá nhân. Nếu ảnh từ Cloudinary không truy cập được, readiness báo lỗi thật; kiểm tra mạng hoặc nhờ chủ catalog cung cấp URL hợp lệ.

## Persistence và dừng demo

Compose demo có named volumes riêng: `<project>_postgres-data`, `<project>_qdrant-data`, `<project>_neo4j-data`, `<project>_neo4j-logs`. Restart container không làm mất seed/vector/knowledge. Compose gốc hiện chỉ khai báo named volume PostgreSQL; Qdrant chưa mount storage và Neo4j đang có anonymous volume. Không tự chuyển volume cho container hiện tại; muốn nâng cấp môi trường đó phải bảo toàn dữ liệu trước. Gói demo mới không chỉnh Compose gốc và không đụng volumes hiện có.

Chỉ khi bạn muốn dừng các tiến trình **do bootstrap tạo**:

```powershell
powershell -NoProfile -File .\scripts\bootstrap-ai-stylist-demo.ps1 -StopApps
```

Dừng Docker demo, vẫn giữ dữ liệu:

```powershell
docker compose --env-file .\stylist-service\target\team-demo\stylemind-ai-demo-team\compose.env -f .\demo\ai-stylist\compose.yml -p stylemind-ai-demo-team stop
```

Khởi động lại bằng lệnh bootstrap ban đầu. Không cần `down -v` hay xóa collection.

## Export lại (chỉ maintainer)

Team không cần chạy bước này. `scripts/export-ai-stylist-demo.cjs` chỉ đọc Qdrant loopback và đối chiếu với bằng chứng embedding thật đã lưu, không gọi Gemini:

```powershell
node .\scripts\export-ai-stylist-demo.cjs http://127.0.0.1:6333
```

Đây là thao tác bảo trì bundle, cần có collection gốc và file knowledge gốc từ runtime cũ. Máy mới dùng bundle đã bàn giao, không export lại và không cần truy cập máy owner.
