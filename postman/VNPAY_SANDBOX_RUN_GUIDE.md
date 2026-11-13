# VNPAY Sandbox Run Guide

## Configure the local sandbox

1. Copy `.env.example` to the ignored `.env` and set `STYLEMIND_VNPAY_TMN_CODE`, `STYLEMIND_VNPAY_HASH_SECRET`, and `STYLEMIND_VNPAY_RETURN_URL` with values issued for your VNPAY sandbox account. Never paste the Hash Secret into source control or chat.
2. Configure the sandbox merchant IPN to the public Gateway URL `https://<public-host>/payment/api/v1/payments/vnpay/ipn`. For local testing, expose the Gateway with an HTTPS tunnel and use that tunnel host in VNPAY's merchant portal.
3. Set `STYLEMIND_COMMERCE_SERVICE_TOKEN` for Payment and the matching `STYLEMIND_COMMERCE_PAYMENT_TOKEN` for Commerce. Keep `STYLEMIND_PAYMENT_EVENTS_ENABLED=false` until both values match; then enable it if order status should update automatically after IPN.
4. Apply Payment V5 and Commerce V17 using the normal migration process in disposable local/test databases. This guide does not authorize or perform migration of persistent data.

## Customer flow

Import `StyleMind-VNPAY-Sandbox.postman_collection.json` and set `gatewayUrl`, `accessToken`, `shippingAddressId`, `checkoutKey`, and `orderId` as appropriate.

1. Create an authenticated order using `POST {{gatewayUrl}}/commerce/api/v1/orders` with `payment_method: VNPAY` (or use the guest order endpoint and its session key).
2. Start/recover payment using `POST {{gatewayUrl}}/commerce/api/v1/orders/{{orderId}}/payment-attempt` with no body. For guests use `/commerce/api/v1/guest/orders/{{orderId}}/payment-attempt` and `X-Guest-Session-Key`.
3. Open the returned `paymentUrl` in a browser and complete a sandbox transaction using the test card/account details shown in the VNPAY sandbox account documentation.
4. VNPAY calls the configured IPN. Only a valid IPN settles Payment. The browser redirect does not settle an order. Read the order again from Commerce to confirm `PAID` after the Payment outbox event is delivered.

## Importable requests

The collection includes customer checkout, payment-attempt recovery and order status reads. The VNPAY IPN should come from the provider; do not mark an order paid by calling the callback with hand-crafted unsigned query values. Postman collection presence is handover material, not proof of a live sandbox transaction.

## Troubleshooting

- `VNPAY_NOT_CONFIGURED`: configure all three merchant variables and restart Payment.
- VNPAY IPN returns `97`: confirm the account's Hash Secret and TmnCode match the runtime configuration.
- Payment says paid but Commerce remains `PENDING_PAYMENT`: verify outbox delivery is enabled and the Payment/Commerce service tokens match.
- Amount rejected: sandbox order totals must be from 5,000 through 9,999,999,999 VND.
- Configure and expose the IPN endpoint over HTTPS; VNPAY cannot call a workstation-only localhost URL.
