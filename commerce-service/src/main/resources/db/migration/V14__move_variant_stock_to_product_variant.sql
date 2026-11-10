-- Keep a single, sellable quantity on each colour/size variant.
-- V10 remains immutable because it may already be recorded in Flyway history.
ALTER TABLE public.product_variant
    ADD COLUMN quantity int NOT NULL DEFAULT 0,
    ADD CONSTRAINT product_variant_quantity_check CHECK (quantity >= 0);

-- A pending SePay reservation is already unavailable to new checkouts, so carry
-- only the sellable amount forward. Expiry/cancellation restores from order_item.
UPDATE public.product_variant variant
SET quantity = inventory.on_hand_quantity - inventory.reserved_quantity
FROM public.variant_inventory inventory
WHERE inventory.variant_id = variant.variant_id;

DROP TABLE public.inventory_reservation;
DROP TABLE public.variant_inventory;
