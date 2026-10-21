-- US-02.05 follow-up: optional body weight on the Identity style profile (EP-05 Wardrobe sharing context).
-- Same physical shape as height_cm: nullable numeric without a database bound; the accepted range
-- (20 to 300 kg, at most one fractional digit) is enforced by the request validator.

ALTER TABLE public.style_profile
    ADD COLUMN weight_kg numeric;
