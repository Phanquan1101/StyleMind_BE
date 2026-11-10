# Vietnamese catalog content — applied 2026-11-10

The owner requested Vietnamese product content, then asked for a method other than translating word by word. All 200 selected products were edited locally using curated Vietnamese titles and structured source facts. No product text was sent to Google Translate or another translation service. An initial proposed external translation request was rejected before execution; the task was completed through local editorial rewriting instead.

## What changed

- Vietnamese names and factual descriptions for 200 products.
- Vietnamese color/material labels for 2,705 variants. S/M/L/XL and numeric codes remain unchanged; the descriptive `One Size` label becomes `Một cỡ`.
- Vietnamese alt descriptions for all 1,337 product-gallery images, reflected in 18,865 variant-image rows. Alt text identifies the product and image number, without inventing a color/view match.
- Category display labels `Set đồ` and `Jumpsuit / đồ liền` become `Bộ đồ` and `Đồ liền`. Category IDs/slugs are unchanged. The owner-chosen source label `Livostyle Catalog` and literal garment print names such as “Strike Out”/Nashville remain proper names.

Descriptions are concise editorial replacements, not sentence-for-sentence translations of sales copy. They use the original garment title, fit, material information, care instructions, source stretch/lining statements, available option labels, and measurement tables. International fiber names (polyester, nylon, viscose, etc.), measurement units, proper names and machine codes are retained where appropriate. Original English source files remain intact.

198 source HTML measurement tables were retained: independent validation checks all **4,044 data cells**, preserving numerical values, fractions and size codes; descriptive labels and `in` unit abbreviations are localized. The two source products without tables receive no invented measurements. Nine source records have ambiguous/inconsistent material percentage totals or separate component compositions that cannot safely be combined; their descriptions retain material types and explicitly say the percentages need confirmation, instead of inventing corrected percentages. See `vietnamese-content-report.json`.

Prices, SKU/UUIDs, inventory, order/cart data, image URLs/order/primary flags, statuses, source brand and schema are unchanged. All 381 old inactive products are retained. No service source or migration was edited, no application rebuild was needed, and the running backend serves the updated DB text immediately.

## Files and reproduce/check commands

Added:

- `scripts/localize-clothing-200-vi.cjs`: local batch editor using checked vocabulary and structured source measurements.
- `scripts/apply-catalog-vietnamese.cjs`: full backup, isolated restore/update/rollback/recovery tests, then guarded text-only transaction.
- `scripts/verify-catalog-vi-data.cjs`: independent measurement/value preservation, field bounds, unchanged nontext payload and distinct color-option validation.
- `vietnamese-titles.json`, `vietnamese-colors.json`: curated wording.
- `clothing-200-db-payload-vi.json`: final localized import/display payload.
- `clothing-200-db-payload-vi-previous.json`: initial localized version retained as a guard/recovery baseline for a minor measurement-label refinement.
- `vietnamese-content-report.json`, `vietnamese-measurement-evidence.json`: scope/limitations and original measurement cells.
- `vietnamese-database-initial-report.json`, `vietnamese-database-report.json`: successful live update/backup evidence for initial edit and refinement.
- `vietnamese-backup.json`: backup pointers; dumps/SQL themselves are private and Git-ignored.
- `VIETNAMESE_CONTENT_GUIDE.md`: this handoff.

Updated `scripts/verify-clothing-200-http.cjs` to select the applied Vietnamese payload and verify description, size/color/material and alt text in addition to existing IDs, prices and URLs. Updated the review README/Postman guide and runtime verification report. Original English import payload and its SHA-256 are retained so the earlier old-catalog recovery mechanism remains available.

```powershell
node scripts/localize-clothing-200-vi.cjs
node scripts/verify-catalog-vi-data.cjs
node scripts/verify-clothing-200-http.cjs
```

Generation and verification may be repeated. The DB apply is guarded against the exact prior text; it is a completed operation, not a general-purpose reset command. Do not rerun it against already-edited text without preparing a new baseline.

## Executed verification

- Local editor completed for 200 products, 2,705 variants and 1,337 image captions; final descriptions are at most 824 Unicode code points.
- `node scripts/verify-catalog-vi-data.cjs`: PASS, 198 tables / 4,044 cells, no remaining English sales headings/table prose, no collapsed distinct source color choices, and unchanged nontext payload.
- Each successful backup was actually restored to a newly created PostgreSQL container with no network or published ports; every public table's row count/digest matched. Update rollback returned the entire restored DB to its original fingerprints; edit then text recovery did the same. Disposable containers and anonymous volumes were removed.
- The live transaction locks existing tables, checks exact prior text, and compares protected fingerprints of every public table before/after. Only specified text fields may change; old inactive product rows are also checked in full.
- Real Gateway HTTP: PASS over all 200 products, 2,705 variants and 18,865 gallery links, comparing complete names/descriptions, all size/color/material values and alt text to the final Vietnamese payload. Prices/URLs and negative 404/400/401 cases also passed. See `database-http-verification.json` (`localized_vietnamese: true`).
- No Java service behavior changed, so the previously passed Java regression suites were not repeated for this data-only edit. The isolated DB regression and real HTTP checks above test the changed content. Postman JSON remains structurally validated; no Postman/Newman runtime execution is claimed.
- `git diff --check` passed. No commit/push or staging of unrelated changes. Existing staged governance/Payment changes are preserved. The backlog workbook path remains unavailable and was not edited.

## Backup / undo

Original English content backup (restore tested):

`commerce-service/.seed-data/db-backups/vietnamese-2026-11-10T12-25-35-724Z/before-vietnamese.dump`

SHA-256: `bc73df323616747f9e4693d7bef4a7ff1d8141b68d8dba0ad9b6afaa2774b604`.

Final measurement-label refinement backup (restore tested):

`commerce-service/.seed-data/db-backups/vietnamese-2026-11-10T12-29-49-462Z/before-vietnamese.dump`

SHA-256: `e889ad2aa00145d8c78de57d71a0b26529227c1368aa9135b2e7b6b91a468101`.

For text-only undo, first execute the final backup folder's `restore-before-localization.sql`, then the original English backup folder's `restore-english-text.sql`. This restores the exact intermediate baseline required by each guard. Both recovery transitions were tested on isolated copies; neither was executed on the live DB after completion. These scripts preserve nontext data and reject unexpected intervening text edits. They are private local files, not public endpoints.

Full dumps contain Commerce business/customer data and must remain private. Restore full dumps into a separate database first; restoring over the live DB would also roll back later Commerce activity. No live full-DB restore has been performed. These are local results, not production-readiness evidence.
