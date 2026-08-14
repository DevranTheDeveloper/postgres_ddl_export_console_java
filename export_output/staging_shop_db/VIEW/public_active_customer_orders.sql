CREATE OR REPLACE VIEW "public"."active_customer_orders" AS
 SELECT c.full_name,
    c.email,
    o.order_id,
    o.total_amount,
    o.order_status
   FROM customers c
     JOIN orders o ON c.customer_id = o.customer_id;
