# VNPAY Sandbox Payment Contract

**Owner authorization:** 2026-11-13, user confirmed the exact migration scope below and requested VNPAY sandbox integration.

## Scope

- Add `VNPAY` as an additional payment method. COD and SePay remain supported.
- Use VNPAY Pay API 2.1.0 and the sandbox payment URL by default. This does not authorize production configuration or live payment traffic.
- Commerce creates an order in `PENDING_PAYMENT`; Payment creates a 15-minute attempt and returns a hosted `paymentUrl`.
- A VNPAY server-to-server IPN is the only provider callback that may settle an attempt. It must pass the HMAC-SHA512 signature and merchant-code checks. Both `vnp_ResponseCode` and `vnp_TransactionStatus` must equal `00`; the reference, exact integer VND amount, attempt method/status and expiry are checked before settlement.
- A valid successful IPN commits the Payment ledger and outbox in one transaction. The existing PaymentConfirmed event flow delivers it to Commerce, which applies only to an owner/method/amount-matching `PENDING_PAYMENT` order and remains idempotent.
- The browser Return URL is configured by the operator and is informational only. It never settles an order.
- VNPAY amounts are limited to 5,000 through 9,999,999,999 VND, matching the current sandbox API's 12-digit amount-in-cents field.
- Expired pending VNPAY orders are handled by Commerce's existing online-order expiry processing. Cancellation after payment, refunds, retries after expiry and manual exception resolution are not expanded by this feature.

## Approved schema changes

- Payment V5 (`V1`–`V4` remain immutable): permit `VNPAY` and enforce the existing 15-minute online attempt expiry rule.
- Commerce V17 (`V1`–`V16` remain immutable): permit `VNPAY` on orders and Payment event inbox rows.
- These are source migrations only. They were not applied to a persistent database by this task.

## Runtime configuration

Store the merchant TmnCode, Hash Secret, return URL and service-to-service event credentials in ignored `.env` or the deployment secret manager. Do not commit or log them. VNPAY's merchant portal must be configured with the externally reachable Gateway IPN URL:

`https://<public-host>/payment/api/v1/payments/vnpay/ipn`

`STYLEMIND_PAYMENT_EVENTS_ENABLED` remains `false` by default. Enable it only after configuring the existing Commerce event credential on both services.

## Evidence and limits

Integration mechanics follow [VNPAY's sandbox payment guide](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html) and [VNPAY FAQ](https://sandbox.vnpayment.vn/apis/docs/faqs/). No VNPAY account, credential, transaction, persistent database or production endpoint was used by this implementation.
