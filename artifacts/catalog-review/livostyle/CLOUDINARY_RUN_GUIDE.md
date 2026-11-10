# Cloudinary — selected 200 products

The owner requested Cloudinary image hosting for the selected dataset only. No DB import, schema change, new public endpoint or pricing/stock behavior is included.

## Files

- `scripts/upload-clothing-200.cjs`: signed bulk uploader with resumable progress and non-overwriting deterministic public IDs.
- `scripts/upload-clothing-200.test.cjs`: isolated tests for configuration, source restrictions, signing, response validation, sanitized failures and output preservation.
- `scripts/verify-clothing-cloudinary.cjs`: independent structure/source-preservation validation and optional live CDN HEAD checks.
- `cloudinary-upload.json`: source URL to Cloudinary asset mapping and progress. Contains public metadata only, no keys or signatures.
- `cloudinary-summary.json`: completion/partial status, upload totals and failures.
- `clothing-200-cloudinary.json`: generated only once every source gallery image has a valid upload response; original product data with Cloudinary gallery/featured URLs.
- `cloudinary-http-verification.json`: created by the optional live HEAD check for every uploaded image.
- `CLOUDINARY_RUN_GUIDE.md`: this handoff.

Original `clothing-200.json` stays intact. Gallery order and alt text are preserved. No gallery image is assigned to a variant color by inference; per-color image association remains unresolved in the source data.

## Run from repository root

Requires Node.js with built-in fetch, FormData and AbortSignal.timeout (verified locally with Node 25.2.1). Reads only `CLOUDINARY_URL` and optional `CLOUDINARY_FOLDER` from process environment or ignored root `.env`, with environment precedence. Never paste credentials into committed files or command arguments.

```powershell
node --test scripts/upload-clothing-200.test.cjs
node scripts/upload-clothing-200.cjs
node scripts/upload-clothing-200.cjs --upload --limit=1
node scripts/upload-clothing-200.cjs --upload
node scripts/verify-clothing-cloudinary.cjs
node scripts/verify-clothing-cloudinary.cjs --http
```

The default uploader run is a dry run and makes no network requests. `--upload` performs the owner-requested external uploads. `--limit=N` limits pending image attempts, useful for a canary. Re-run the same upload command after interruption: successfully recorded images are skipped. If a request succeeded remotely before progress was saved, the same public ID and `overwrite=false` prevent replacing the image. Response public ID, secure CDN path, dimensions, and signature are validated before recording success. Three workers upload images; transient failures retry at most three times. No remote provider error bodies are printed or saved.

Public ID prefix: `stylemind/products/livostyle-200/<source_product_id>/<SHA256-of-source-image-URL>`, under the configured base folder. Existing old-catalog assets are untouched. No asset deletion, image transformation add-on, database connection or application seed update is performed.

The uploader uses a local exclusive lock. After an abnormal process termination, check that no upload process remains before removing only `cloudinary-upload.lock`; do not delete progress mappings. A checkpoint with another source hash/account/folder is rejected.

## Verification and limits

The six isolated Node tests pass without network/DB/secrets. Real upload/runtime results are recorded separately in the JSON summary and HTTP verification file; unit tests alone do not establish real upload success. No Docker/Compose or application regression suite is run for this standalone import-preparation tool, since no service/API behavior changed. The instructed workbook path is unavailable in this environment; no workbook was modified.

Uploaded images are public product photos on Cloudinary. Source prices remain USD, source stock flags remain a snapshot, and source license attribution is retained in `SOURCE_LICENSE.txt`. Cloudinary upload and CDN success do not prove visual image-to-product or image-to-color accuracy. DB import, VND pricing, final category/brand mapping, and variant image association remain separate work.

Protocol references: [Cloudinary Upload API](https://cloudinary.com/documentation/image_upload_api_reference), [upload signatures and responses](https://cloudinary.com/documentation/upload_images).

## Observed result — 2026-11-10 (Asia/Saigon)

- `node --test scripts/upload-clothing-200.test.cjs`: 6 passed, 0 failed, 0 skipped.
- Dry run: exactly 200 products and 1,337 unique source image URLs.
- Canary upload: 1 verified signed Cloudinary response; subsequent full upload resumed that checkpoint.
- Full upload: COMPLETE, 1,337 images, 0 failures, 294,047,229 bytes stored as reported by Cloudinary.
- Re-running the completed uploader: COMPLETE with the same 1,337 recorded assets; no pending uploads or duplicated records.
- `node scripts/verify-clothing-cloudinary.cjs --http`: structure/source preservation PASS; 1,337/1,337 CDN HEAD checks passed with image content types, 0 failures.
- Original 200 products and 2,705 variants are preserved in `clothing-200-cloudinary.json`; only gallery and featured image URL fields change.
- `git diff --check`: passed. `git check-ignore .env`: confirmed ignored. No secret/config file was changed.

External state touched: newly hosted selected product images in the existing configured Cloudinary account, under the `stylemind/products/livostyle-200` public ID prefix. Existing catalog images were not overwritten/deleted. Database/environment touched: no database, Docker, schema, application configuration or application seed changes. Existing Payment edits and earlier dataset artifacts were preserved.
