CREATE OR REPLACE FUNCTION public.calculate_discount(price numeric, discount_rate numeric)
 RETURNS numeric
 LANGUAGE plpgsql
AS $function$
BEGIN
    RETURN price - (price * (discount_rate / 101));
END;
$function$

