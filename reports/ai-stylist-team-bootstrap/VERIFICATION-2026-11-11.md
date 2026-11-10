# Kiểm chứng bootstrap AI Stylist — 11/11/2026

Phạm vi: local demo độc lập; không Gemini Live, không đổi business logic/schema/migrations, không commit/push. Các thay đổi có sẵn trong `docker-compose.yml`, `StylistGenerationServiceImpl.java` và các report khác được giữ nguyên.

## Kết quả thực thi

| Kiểm tra | Kết quả |
|---|---|
| Fresh project `stylemind-ai-demo-clean1111` | PASS; trước chạy có 0 containers và 0 volumes của project |
| PostgreSQL mới | PASS; Commerce 10 migrations, Stylist 3 migrations do chính ứng dụng áp dụng |
| Commerce thật | PASS; đúng 10 product IDs, 10 variant IDs, ACTIVE, available_quantity > 0, 10 ảnh HTTP 200 |
| Qdrant mới | PASS; 10 vectors Gemini thật khác nhau + 1 manifest, 1536/Cosine; toàn bộ IDs/payload/hash khớp bundle và bằng chứng gốc |
| Neo4j mới | PASS cho fixture demo gốc; 1 KnowledgeArtifact + 1 KnowledgeEntry, import bằng importer hiện có |
| Generate offline qua Gateway thật | PASS; session 201 → intake 201/RECEIVED → generate 200/SUCCEEDED → detail 200 TOP+BOTTOM |
| Persistence | PASS; recommendation được lưu PostgreSQL |
| Owner isolation | PASS; token khác owner đọc request nhận 404 |
| Generate replay | PASS; không gọi provider thêm |
| Bootstrap lặp lại sau restart ứng dụng | PASS; project `stylemind-ai-demo-fresh1111`, số sản phẩm/vector/knowledge không tăng, request/recommendation cũ được tái sử dụng |
| Bundle sai checksum | PASS; bị từ chối trước Docker/provider |
| Meter | Không đổi: embedding 29, countTokens 45, generation 10, blocked 4 |
| `.env` owner | Không đổi trong các lượt kiểm chứng; `-ConfigureRootEnv` là lựa chọn riêng để chuyển dữ liệu IntelliJ |

Lệnh fresh cuối:

```powershell
powershell -NoProfile -File scripts/bootstrap-ai-stylist-demo.ps1 -ProjectName stylemind-ai-demo-clean1111 -PortOffset 22000 -VerifyOffline
```

Request `212e3931-b956-4683-a9a0-5966ba1415ee`, recommendation `8e061554-385c-427c-9503-468d90cd1305`. Generate mất 3028 ms theo log Stylist. Bộ offline trả áo Femella 249.000đ và quần Nike 329.000đ, tổng 578.000đ. Đây là lựa chọn HTTP fixture, không phải lựa chọn Gemini Live.

Gateway kiểm chứng được giữ chạy tại `http://127.0.0.1:30080`; Commerce 30082, Stylist 30085. Dữ liệu dùng named volumes riêng của project. Các ứng dụng IntelliJ cũ ở 8080/8085 vẫn chạy với PID cũ.

## Bằng chứng và giới hạn

- `fresh-environment-2026-11-11.json`: counts, images, migrations, recommendation và fixture statistics của môi trường mới.
- `repeat-after-app-restart.json`: kết quả lặp lại, fixture calls bằng 0 trong lượt replay.
- `negative-checksum.json`: lỗi thiếu tính toàn vẹn bị từ chối trước startup.
- `verification-summary-2026-11-11.json`: đối soát meter, môi trường ban đầu trống và `.env` không đổi.
- `demo/ai-stylist/bundle.json`: checksum artifact và từng vector, nguồn embedding thật ngày 09/11.

Build Maven dùng `-Dmaven.test.skip=true`; không tuyên bố đã chạy lại toàn bộ unit/regression suites. PASS ở trên là kiểm chứng runtime thực tế của phạm vi bootstrap. Node syntax check, PowerShell parse và `git diff --check` đã đạt. Planning guard báo source drift có sẵn ở các source/contract cũ; không cập nhật hay thay đổi planning fingerprints.

Knowledge giữ nguyên metadata `synthetic-fixture-only`/`synthetic://wave2-http-fixture`; không có bằng chứng human review cho tri thức thời trang thật. Query embedding và generation trong kiểm chứng là fixture; các vector catalog đã restore là vector Gemini thật. Quyền phân phối công khai dataset/ảnh chưa được xác lập từ repository; gói được chuẩn bị cho nội bộ team theo chỉ đạo owner. Owner cần đưa các file mới vào commit bàn giao để team nhận được qua `git pull`.
