# Catalog Dataset and Global Inventory Contract

Status: owner-approved in the catalog dataset conversation. This document records the implementation scope; it does not approve unrelated inventory, return, refund, QC or restock features.

## Seed catalog

- The tracked manifest contains 600 women/unisex products: 300 tops and 300 pants, 2,400 variants total.
- Brand is derived from the source product name, with `StyleMind Select` as fallback.
- Tops use sizes S/M/L/XL. Jeans, trousers and shorts use 26/28/30/32; other pants use S/M/L/XL.
- Price bands are: T-shirts/tops 199,000; shirts 249,000; leggings/jeggings 249,000; shorts 279,000; track pants 329,000; trousers 399,000; jeans 449,000 VND.
- One product/color image asset is shared by all four size variants. Cloudinary URLs are stored in the manifest, so another developer does not need the local dataset files.
- Seeding is opt-in with `STYLEMIND_CATALOG_SEED_ENABLED=true`, is idempotent, and does not reset existing inventory.

## Global inventory

- Inventory is global per variant; there is no branch inventory.
- Seeded variants start with on-hand 20 and reserved 0. Variants created through the admin API start with 0.
- Public/admin variant responses expose non-negative `available_quantity = on_hand_quantity - reserved_quantity`.
- Cart operations validate availability but do not reserve.
- COD checkout atomically decrements on-hand stock.
- SePay checkout atomically reserves for 15 minutes. Confirmation consumes on-hand and reserved quantities. An expiry scan releases the reservation and cancels an order that remains `PENDING_PAYMENT`.
- An approved cancellation restores a consumed allocation or releases an active reservation exactly once.
- Variant rows are locked in UUID order inside the checkout transaction to prevent overselling and reduce deadlock risk.

## Public shopping catalog

- Guests can read active Categories and Brands, list/detail active Products, read legacy image content, and batch-lookup active Variants without a JWT.
- Admin mutation and inactive-record APIs remain protected by `ROLE_ADMIN`; Branch reads retain their existing authenticated contract.
- Product list/detail excludes products without an active variant. Price bounds must be satisfied by the same active variant.

## Explicitly out of scope / TBD

- Branch-level inventory, returns, refunds, QC and restock remain outside current scope.
- Financial refund behavior after a paid-order cancellation remains TBD; this implementation only restores inventory after cancellation approval.
