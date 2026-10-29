-- Commerce order lookup and status history slice for US-03.09.

CREATE TABLE public.order_status_history (
    history_id uuid PRIMARY KEY,
    order_id uuid NOT NULL REFERENCES public.customer_order (order_id) ON DELETE CASCADE,
    from_status text,
    to_status text NOT NULL,
    actor_user_id uuid,
    changed_at timestamptz NOT NULL,
    reason text
);

CREATE INDEX customer_order_customer_placed_idx
    ON public.customer_order (customer_id, placed_at DESC, order_id DESC);

CREATE INDEX customer_order_status_placed_idx
    ON public.customer_order (order_status, placed_at DESC, order_id DESC);

CREATE INDEX order_status_history_order_changed_idx
    ON public.order_status_history (order_id, changed_at ASC, history_id ASC);
