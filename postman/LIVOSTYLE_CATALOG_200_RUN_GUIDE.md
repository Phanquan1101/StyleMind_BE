# Livostyle catalog — read-only verification

Import `LIVOSTYLE_CATALOG_200.postman_collection.json` into Postman. Set collection variable `baseUrl` to `http://localhost:8080`. Start the existing `stylemind-task` Commerce/Gateway containers with old catalog seeding disabled. Requests use the Gateway `/commerce` prefix and require no token for public reads.

The collection includes seven GET requests: two catalog pages, new product detail, hidden old product (404), malformed UUID (400), excessive page size (400), and anonymous Admin access (401). It changes no data. The two pages must report 200 total products; one product may have multiple variants and shared gallery images.

Structural validation checked JSON, seven read-only requests, base URL variables and test scripts. This is **not** evidence of actual Postman/Newman execution. Real HTTP execution is performed separately by:

```powershell
node scripts/verify-clothing-200-http.cjs
```

That script verifies 200 exact product IDs, 2,705 variant IDs/SKUs/VND prices, brand label, 18,865 gallery links/order/primary flags, inventory default 0, old product hiding and negative anonymous/input cases. Its result is saved under `artifacts/catalog-review/livostyle/database-http-verification.json` only on success.

After the approved Vietnamese edit, the script automatically uses the applied `clothing-200-db-payload-vi.json` and also compares all names/descriptions, size/color/material labels and image alt text. Real HTTP verification passed with `localized_vietnamese: true`; this remains separate from actual Postman/Newman execution.

Backup/recovery details and exact runtime evidence: [database handoff](../artifacts/catalog-review/livostyle/DATABASE_IMPORT_PLAN.md). No credentials are included in this collection or guide. No destructive/admin writes are present.
