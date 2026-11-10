# Catalog replacement and backup — executed 2026-11-10

The owner approved importing the selected 200 rounded-VND products into Docker PostgreSQL, retaining a tested backup, setting old products to INACTIVE, and grouping the new products under the source label **Livostyle Catalog**. Physical deletion was not selected.

## Executed database operation

Target: container `stylemind-task-postgres-1`, volume `stylemind-task_postgres-data`, database `stylemind_commerce`. The other task4 PostgreSQL container was only started for identification and returned to its original stopped state. Native Windows PostgreSQL was not modified. Docker Desktop and the selected PostgreSQL container were started to perform the authorized operation.

Committed result: **200 ACTIVE new products**, **381 INACTIVE old products**, **2,705 new variants**, **1,337 new shared image assets**, **18,865 variant/gallery links**. Seven category groups cover tops, pants/shorts, dresses, skirts/skorts, outerwear, sets, and one-pieces. Existing category slugs are reused without rewriting their records. IDs/SKUs are deterministic and new SKUs use `LIVO-` prefixes. Original catalog rows, variant statuses, inventory, images, carts, orders, reservations, and all noncatalog tables were preserved; only old product visibility changed.

New variant inventory starts at **0 on hand and 0 reserved**, following existing new-variant creation defaults. Source in-stock flags are not quantities and were not converted into invented stock. The imported catalog is visible but cannot be purchased until the owner supplies actual stock. No inventory behavior or schema was added.

The source provides product galleries, not per-color image identifiers. Each variant shares its product's complete gallery, with source order/alt text and the original featured image marked primary; no color match is inferred. Existing image-deletion code derives Cloudinary public IDs from DB asset UUIDs; these imported URLs use the separate `livostyle-200` namespace, so automatic remote asset deletion for this namespace is not established. This task did not change deletion APIs or delete Cloudinary files.

Prices use the previously prepared fixed 25,730 VND/USD reference, rounded half-up to 1,000 VND; unit prices range from 514,000 to 5,120,000 VND. Original USD data stays in the source artifacts. No schema migrations, Flyway repair, Identity/Payment DB reads, or service implementation changes were performed.

Observed migration difference: this existing DB records V9 guest-checkout and V10 inventory/image migrations, while the current repository contains V9 inventory/image and no V10. Those applied records were left untouched. A rebuild from the current source against this DB needs a separate migration-history reconciliation; it is not approved or repaired by this import. Runtime verification uses the existing Commerce Docker image.

Ignored root `.env`: changed only `STYLEMIND_CATALOG_SEED_ENABLED` to `false`, to prevent old tracked seed reactivation. Other environment entries were preserved. The old tracked `development-catalog.json` was not overwritten; do not re-enable its seed when keeping this new catalog.

## Actual backup and recovery evidence

Backup directory: `commerce-service/.seed-data/db-backups/2026-11-09T19-27-11-988Z/` (UTC timestamp; 2026-11-10 locally).

- `stylemind_commerce-before-catalog.dump`: full custom-format Commerce dump, **322,169 bytes**.
- SHA-256: `d63535d1eb38bacdd1080f278f237f8b73735ab3cae1ed2d3fed5fb7d6dc7f2f`.
- `restore-list.txt`: archive table-of-contents listing.
- `before-snapshot.json`: table row counts/digests, old catalog IDs/statuses; kept private and ignored.
- `applied-import.sql`: exact transactional import and guards.
- `recover-old-catalog.sql`: restore old product statuses and retire the new 200 products; deletes no rows.
- `manifest.json`: backup/import status and evidence.

The dump was restored into a newly created, network-isolated disposable PostgreSQL container. Every public-table count and row digest matched the source. The import was tested with rollback (full DB fingerprints unchanged), then commit (all counts/prices/images correct), followed by recovery of old catalog visibility. All these checks passed before applying to the real target. The validation container and its anonymous volume were removed after testing.

The live import locks the existing tables, checks the current DB against the verified backup fingerprints, and runs in one transaction. SQL assertions verify that all noncatalog data and all preexisting catalog attributes except product status remain unchanged. The preserved data includes the existing one order and one cart item. A second import of the same dataset is rejected rather than duplicating rows.

Full backups may contain private order/address/cart data. They are under Git-ignored `.seed-data`; do not commit or publish them. The dump does not include other services' databases, roles/passwords, Cloudinary binaries, or environment secrets. Keep the current ignored local credentials for normal access.

## Recover the old catalog later

From repository root, with Docker running:

```powershell
docker compose --project-name stylemind-task stop commerce-service
node scripts/import-clothing-200-docker.cjs --recover-old-catalog
docker compose --project-name stylemind-task up -d --no-deps --no-build commerce-service
```

The recovery command checks target/backup/payload identity, restores the old product statuses, and sets the new products INACTIVE. It retains all records and later order/cart references. The generated recovery SQL was actually tested on the restored copy; it has **not** been run on the real DB, which currently retains the new catalog.

The `.dump` also supports full DB restoration. Restore it into a separate database first; a full restore over the real DB would return all Commerce data to backup time, including orders/carts, unlike the visibility-only recovery above. No full restore over the real DB has been performed.

## Commands, files, and verification

```powershell
node scripts/build-clothing-200-import.cjs
node scripts/import-clothing-200-docker.cjs --rehearse
node scripts/import-clothing-200-docker.cjs --apply
```

These commands were executed successfully in that order. Rehearsal/apply are guarded one-time operations for the original 381-product baseline, not a general reset command. Do not rerun them against the already-imported DB.

Files added for this operation: `scripts/build-clothing-200-import.cjs`, `scripts/import-clothing-200-docker.cjs`, `scripts/verify-clothing-200-http.cjs`, `clothing-200-db-payload.json`, `clothing-200-db-plan.json`, `database-import-state.json`, `postman/LIVOSTYLE_CATALOG_200.postman_collection.json`, and its run guide. `database-http-verification.json` is written only after successful real HTTP checks. This document and the review README/governance context are updated with the handoff. Existing Payment edits and prior dataset artifacts are preserved.

### Final verification

- Initial `mvn -f commerce-service/pom.xml verify` failed because the host JVM sent timezone `Asia/Saigon`, which the disposable PostgreSQL containers rejected (71 executed, 44 errors). This was not reported as a pass.
- `mvn -f commerce-service/pom.xml '-DargLine=-Duser.timezone=UTC' verify`: **BUILD SUCCESS, 71 tests, 0 failures, 0 errors, 0 skipped**. No service source changes were needed. Logs: ignored `commerce-service/target/catalog-import-regression-utc.log`.
- Only after the regression gate completed, existing-image Commerce was recreated with seed disabled via `docker compose --project-name stylemind-task up -d --no-deps --no-build commerce-service`; existing Gateway was started. No other business services were started. The correct DB was already running; no new persistent DB volume was created.
- `node scripts/verify-clothing-200-http.cjs`: **PASS**, real Gateway HTTP checks over all 200 returned products, 2,705 variants and 18,865 gallery links, exact source IDs/prices/URLs, correct label/default inventory, old product 404, malformed UUID/page size 400, anonymous Admin 401. Report: `database-http-verification.json`.
- Initial startup health polling timed out while services were starting; a subsequent direct check confirmed Gateway `/actuator/health` **200 UP** and `/commerce/actuator/health` **200 UP, DB UP**. Catalog HTTP verification itself passed.
- Postman collection structure: **7 read-only GET requests validated**. No Postman/Newman runtime execution was claimed; actual runtime requests used the Node verifier above.
- `git diff --check`: passed. Backup paths and `.env` are Git-ignored. No reset, checkout, clean, commit or push occurred.

The requested workbook path is unavailable; no workbook was modified. No production readiness claim.

## Earlier native backup helper

## Backup tool prepared

`scripts/backup-commerce-db.cjs` supports a local native PostgreSQL check and custom-format full-Commerce backup. It reads process environment, ignored `.env.catalog-db`, then existing `.env` (in precedence order). Required override keys for a native target:

```dotenv
STYLEMIND_CATALOG_DB_HOST=127.0.0.1
STYLEMIND_CATALOG_DB_PORT=5432
STYLEMIND_CATALOG_DB_USERNAME=<existing Commerce database user>
STYLEMIND_CATALOG_DB_PASSWORD=<existing password; do not put in chat>
# Optional when PostgreSQL tools are elsewhere:
STYLEMIND_CATALOG_PG_BIN=C:\Program Files\PostgreSQL\18\bin
```

Database is deliberately fixed to `stylemind_commerce`. The script does not change credentials. Run from the repo root:

```powershell
node scripts/backup-commerce-db.cjs --check
node scripts/backup-commerce-db.cjs --backup
```

Backups go under ignored `commerce-service/.seed-data/db-backups/<UTC timestamp>/`: custom archive, archive listing, and manifest with SHA-256 and source server identity. Full database backups may contain private order/address/cart data, so do not commit or attach them publicly. An archive listing/hash proves the file exists and can be read; it is **not** an actual restore rehearsal. The helper intentionally labels this distinction in its manifest.

## Preparation sequence used

1. Inspect actual schema/Flyway history and incoming foreign keys, catalog counts, cart/order/reservation references; never infer physical schema solely from source.
2. Take a full Commerce backup; rehearse restoring it into an isolated database before deleting/replacing catalog data. Record the dump hash and restore checks. Never restore over the live DB just to test the backup.
3. Prepare deterministic new IDs/SKUs, source-to-DB mappings and exact image associations. Preserve all referenced old IDs. Avoid silently assigning a color image based only on gallery order. New inventory quantities follow existing approved creation behavior until the owner provides quantities.
4. Test generated import and rollback behavior on the isolated restored database. Verify exactly 200 new product records, 2,705 variants, integer VND prices, valid image references and unchanged order/cart ownership/data.
5. Quiesce conflicting catalog writers/seed restarts as needed. Take the final pre-change backup. Apply only the selected catalog replacement in a transaction; abort on schema/count/reference mismatch. No schema migration, cross-service query or automatic order/cart deletion.
6. Verify DB and real public HTTP behavior, retain the recovery files and document what was actually changed. Existing Cloudinary assets are never deleted by catalog replacement.

The earlier native helper was not used for this Docker backup. Native authentication probes failed before any native DB changes; no credentials or DB contents were printed. The Docker tools above use the selected container's configured credentials without exposing them.
