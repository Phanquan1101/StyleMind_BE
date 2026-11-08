# Guest cart and checkout integration — 2026-11-02

This supersedes the initial local-cart limitations recorded in GUEST_CHECKOUT.md. The owner explicitly approved the limited Commerce change to accept the guest cart credential through a header.

## What changed

Guest storefront pages now load the existing public Commerce catalog, retaining server product/variant UUIDs, prices, quantities, categories and images. There is no fallback to demo products when the catalog is unavailable. Public catalog permissions are unchanged. Metadata absent from the server response is not fabricated from demo styles/occasions.

Guest cart reads/adds/updates/deletes call Gateway Commerce endpoints. Cart GET and DELETE send only `X-Guest-Session-Key`, never a key in the URL. POST and PUT retain the existing strict JSON bodies with `guest_session_key` and send the same header. A shared UUID key is generated once when needed and retained through ordering/login. Responses refresh the remote cart/header count. Quantity changes use PUT, with no additive POST retries after unknown results. Failed mutations read the cart again so an accepted operation is not silently duplicated. Controls prevent simultaneous mutations.

Checkout displays `items` and `total_vnd` from the confirmed backend response schema, rather than local cart prices. It creates an order only after products have been added to the server cart. Successful ordering refreshes the remote cart; guest order results/recovery remain guest scoped after login. Guest completion does not clear the separate account/demo cart. The original account checkout behavior remains in place.

Old demo cart items are kept separately and are not converted into real variants or silently purchased. The guest cart explains that those products must be selected again from the real storefront.

Commerce header resolution preserves existing query clients for compatibility, rejects conflicting guest header/query keys, and retains authenticated ownership precedence. Existing service/repository ownership checks remain unchanged. No migration, schema change, catalog security matcher, Identity authentication change, or Payment workflow change is included. DTO string logging redacts the key, and MVC payload debug dumps are suppressed while keeping the JSON contract unchanged.

## Files in this follow-up

FE additions:

- `src/shared/api/commerceApi.ts`: configured Gateway transport and timeout.
- `src/features/catalog/data/commerceCatalog.ts`: wire DTO adapter and public catalog pagination.
- `src/features/cart/data/guestCartApi.ts`: guest cart contract/operations/errors.
- `src/features/cart/model/GuestCommerceProvider.tsx`: remote guest catalog/cart state and serialized mutations.
- `src/features/cart/pages/GuestCartPage.tsx`: existing line/modal components with backend operations.
- `src/features/cart/GuestCommerceFlow.test.tsx`: integration tests against stateful mock Gateway responses.

FE updates:

- `src/app/providers/AppProviders.tsx`, `src/app/layouts/components/CustomerHeader.tsx`.
- `src/features/catalog/pages/ShopPage.tsx`, `src/features/home/pages/HomePage.tsx`.
- `src/features/product-detail/pages/ProductDetailPage.tsx`, `src/features/cart/pages/CartPage.tsx`.
- `src/features/checkout/data/guestCheckoutApi.ts`, `src/features/checkout/pages/GuestCheckoutPage.tsx`, `src/features/checkout/pages/GuestOrderPage.tsx`.
- `src/test/identityMockBackend.ts` and the catalog/cart/product/guest-checkout UI tests: backend DTO fixtures and asynchronous rendering assertions.

BE additions/updates in the sibling StyleMind_BE repository:

- `commerce-service/src/main/java/com/stylemind/commerce/validation/cart/CartRequestValidator.java`.
- `commerce-service/src/main/java/com/stylemind/commerce/dto/cart/CartResponse.java`.
- `commerce-service/src/main/resources/application.properties`.
- `commerce-service/src/test/java/com/stylemind/commerce/GuestCartHeaderTests.java`.
- `commerce-service/src/test/java/com/stylemind/commerce/BranchCartOrderPostgresIntegrationTests.java` (one added test; existing uncommitted changes preserved).
- `postman/StyleMind-Guest-Checkout.postman_collection.json`, `postman/GUEST_CHECKOUT_RUN_GUIDE.md`.

## Verification and limits

FE: 58 relevant tests across 12 files pass across targeted runs, covering the new six-test stateful catalog/cart flow plus guest checkout, account checkout, COD/SePay UI, auth, refresh, polling and smoke regressions. An initial concurrent full targeted run hit two timing failures under load; both checkout files then passed together (21 tests). This is not a claim that the entire repository suite is green; the earlier unrelated Admin failures remain outside this task.

BE command (from commerce-service):

```powershell
mvn.cmd -o '-Dtest=GuestCartHeaderTests,CheckoutAuthenticationHttpTests,BranchCartOrderPostgresIntegrationTests' '-Dstylemind.test.postgres-bin=C:\Program Files\PostgreSQL\18\bin' '-Dlogging.level.root=WARN' '-Dlogging.level.org.springframework=WARN' test
```

30 tests pass, zero failures/errors/skips: 17 existing/new PostgreSQL cart/order regressions, six checkout authentication HTTP tests, and seven header/redaction tests. The integration test uses the existing isolated native PostgreSQL helper under `commerce-service/target/isolated-postgres`; it creates/stops its own temporary database. No Compose, migrations or writes were run against the application's persistent database. Test evidence is in `commerce-service/target/guest-cart-verification.log` and Surefire reports.

Typecheck, production build and diff checks pass. Build retains the existing warning about a bundle above 500 kB. Postman structural validation passes: nine requests and ten scripts compile. This is separate from HTTP execution; no live Postman order/payment creation was performed.

Read-only runtime check: the catalog through localhost:4000 returned zero public products. A subsequent cart header probe could not connect within its timeout, so the newly running Commerce header behavior has not been verified on that endpoint. Rebuild/restart the owner-run Commerce instance to load the validator/config changes. Ensure actual ACTIVE products and purchasable variants exist through the existing backend catalog tooling; the FE account/Admin demo interface still uses its prior behavior and does not provision real catalog data. No real guest order was created and no live SePay callback was exercised during this follow-up.

The referenced backlog workbook at `C:\Users\FPTSHOP\Desktop\StyleMind_Task.xlsx` is not present on this machine; current source contracts, the guest run guide and the owner's explicit approval were used. Existing staged/unstaged/untracked work in both repositories was retained; no commit, push or branch change was made in this follow-up.


## Separate guest branch handoff

The owner subsequently requested pushing this backend feature on feature/guest-checkout, based on the separately pushed Payment JPA/mapper commit f5c8130. See [branch handoff evidence](GUEST_CHECKOUT_BRANCH_REPORT.md) for the exact feature delta and verification of the branch being published. Earlier runtime/FE observations above remain historical; this push does not modify sibling FE code or the owner database.
