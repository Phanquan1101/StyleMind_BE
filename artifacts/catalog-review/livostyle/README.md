# Livostyle clothing source selection

Owner selection: all women's clothing, including dresses, skirts, coats and outfit sets. This is a review dataset, not an importable application seed.

Selected 2,273 source products and 17,256 original variants. Excluded 636 products outside the Clothing taxonomy and 29 products with explicit male/child title wording. Retained unisex clothing for review. Source category, size, color, materials, USD prices, image links and other fields are preserved. Out-of-stock variants are retained; source availability is not a stock quantity.

38 groups share titles; these have not been merged because a shared title does not establish duplicate identity. Six retained titles trigger a broad gender/age wording review (including garment terms such as baby rib/baby doll). Image matching and remote URL availability have not been verified. The source taxonomy is not proof that each item is correctly categorized or intended for women.

## Files created or updated by the review workflow

- `scripts/review-livostyle-catalog.ps1` (repository root): reproducible read-only ZIP processing and export.
- `report.json`: whole-source structural report and ZIP SHA-256.
- `product-review.json`: whole-source index and scope classification.
- `issues.json`: structural issues; currently empty.
- `duplicate-titles.json`: whole-source duplicate-title groups.
- `clothing-products.json`: full selected source records, including original image URLs and variants.
- `excluded-products.json`: excluded source index with exclusion reasons.
- `clothing-duplicate-titles.json`: selected duplicate-title groups and source IDs.
- `clothing-gender-review.json`: retained items flagged for wording review.
- `clothing-summary.json`: selection totals, category counts and limitations.
- `SOURCE_LICENSE.txt`: license copied verbatim from the supplied archive.
- `README.md`: this handoff.

## Reproduce and validation

From the repository root, using Windows PowerShell:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/review-livostyle-catalog.ps1 -ZipPath 'C:\Users\Admin\Downloads\womens-fashion-catalog-open-data-main.zip'
```

ExecutionPolicy Bypass applies to this process only. No system policy was changed. The script treats archive contents as data and never executes bundled code.

Executed the export successfully. An independent Node JSON check verified selected product/variant totals, unique selected IDs/SKUs, positive source variant prices, allowed category paths, excluded male/child title wording, disjoint selected/excluded IDs and a total of 2,938 source products. `git diff --check` passed. Application regression/HTTP tests were not run because application behavior was not changed.

No database connection, Docker, migration, existing seed update, upload, or persistent data mutation occurred. Existing Payment changes were preserved. Source workbook path previously specified by the owner was unavailable; this data selection is based on the explicit conversation scope and is not a backlog implementation.

Before replacing the application catalog, resolve the VND pricing rule, final category/brand mapping, image hosting and variant/image association, actual inventory quantities, and treatment of existing catalog IDs/references. These are unresolved; this export invents none of them. No new inventory or other business behavior is authorized by this selection.

## Owner-requested 200-product subset

Run `node scripts/select-clothing-200.cjs` from the repository root. This creates `clothing-200.json` (full original source records) and `clothing-200-summary.json` (selection rules, counts, source hash and selected IDs). The source license above also applies to this subset.

Selected exactly 200 distinct normalized titles: 65 tops, 40 pants/shorts, 40 dresses, 15 skirts/skorts, 20 outerwear, 15 outfit sets, and 5 one-pieces. The selection retains 2,705 original variants, of which 2,399 were marked in stock in the source snapshot. Out-of-stock variants remain preserved. These are 200 products, not 200 variants.

Each selected product has a nonblank description, at least one HTTPS image with source dimensions at least 600 by 600, and at least one source in-stock variant with size/color values. Image entries and size/color diversity affect ranking; source subcategories are rotated. No remote images were fetched, visually matched or hosted. Male/child/unisex title wording is excluded from this subset. Full original fields and variants are preserved without fabricated values.

Executed `node scripts/select-clothing-200.cjs` successfully. Independent Node assertions checked 200 products, unique normalized titles/IDs/SKUs, full source-record preservation, positive source prices, image dimensions, source availability/size/color eligibility, and summary counts. `git diff --check` passed. No application or database changes; application regression and HTTP execution are not applicable to this data-only subset.

## Cloudinary-hosted version

The owner subsequently requested image hosting. See [CLOUDINARY_RUN_GUIDE.md](CLOUDINARY_RUN_GUIDE.md) for exact changed files, commands, runtime evidence and limitations. [clothing-200-cloudinary.json](clothing-200-cloudinary.json) contains all 200 original source records with Cloudinary gallery/featured URLs: 1,337 images uploaded and 1,337 CDN HEAD checks passed. This remains source data for review/import preparation, not an application seed or a database import.

## Rounded VND version — owner requested 2026-11-10

Created `scripts/convert-clothing-200-vnd.cjs`, `clothing-200-cloudinary-vnd.json`, and `clothing-200-vnd-summary.json`; updated this README. Original source/Cloudinary JSON files remain intact.

Run `node scripts/convert-clothing-200-vnd.cjs`. The fixed conversion reference is 25,730 VND per USD, based on the USD selling entry dated 2026-11-09 in [Vietcombank's published table](https://www.vietcombank.com.vn/To-chuc/Trang-chu-DCTC/KHTC---Ti-gia---DCTC), observed on 2026-11-10. This is a recorded reference, not a live updating rate. Owner requested conversion and rounded amounts; the technical interpretation is nearest 1,000 VND, half-up at exact ties. No markup, tax, shipping or promotional logic is introduced.

The output contains 200 products and 2,705 variants. Each variant has integer `unit_price_vnd` and nullable `compare_at_price_vnd`; original explicitly named USD fields remain as source evidence. Product `price` uses `currency: VND` and `min_vnd`/`max_vnd` derived from converted variant prices. `source_price` preserves the original USD product price object. IDs, SKUs, ordering, source availability, image URLs and other fields remain unchanged. Converted unit prices range from 514,000 to 5,120,000 VND.

Executed the conversion successfully. Independent Node assertions passed for 200/2,705 totals, exact half-up boundaries (50 USD becomes 1,287,000 VND), invalid values, every price being a multiple of 1,000, product min/max matching variants, compare-at null handling and full preservation of non-price fields/Cloudinary URLs. `git diff --check` passed. No application code/API behavior changed, so application regression and HTTP execution were not run for this file conversion. No DB, Docker, seed, schema, external Cloudinary assets or environment configuration was touched. DB import and treatment of the existing catalog remain undecided.

## Subsequent approved Docker DB import — 2026-11-10

The owner later selected Docker PostgreSQL, hiding old products with INACTIVE, and the source label Livostyle Catalog. The import is now executed: 200 new ACTIVE products/2,705 variants; all 381 old products retained as INACTIVE. A full pre-import Commerce backup was successfully restored and tested in isolation before the real import. Catalog-recovery instructions, backup path/hash, complete files/commands/results, source/DB migration-history mismatch and stock/image limitations are in [DATABASE_IMPORT_PLAN.md](DATABASE_IMPORT_PLAN.md).

Actual verification: 71/71 Commerce regression tests passed with JVM test timezone UTC; real Gateway HTTP verified 200 products, 2,705 variants, 18,865 gallery links and negative authorization/input cases. Old seed is disabled in ignored `.env` and the recreated existing Commerce image. New stock defaults to 0, so purchase requires actual quantities. These later DB changes supersede the earlier preparation-only state; original source artifacts remain unchanged.

## Vietnamese editorial content — applied 2026-11-10

The selected 200 live products now use locally edited Vietnamese names/descriptions, color/material/one-size labels and image captions. All 198 existing source measurement tables and their 4,044 data cells were checked; no missing measurements or unsupported material percentages were invented. Prices, IDs, inventory, image URLs and old inactive products are preserved. See [the Vietnamese content handoff](VIETNAMESE_CONTENT_GUIDE.md) for exact files, actual backup/restore and HTTP results, limitations and recovery steps. Original English sources remain available; the applied display payload is `clothing-200-db-payload-vi.json`.
