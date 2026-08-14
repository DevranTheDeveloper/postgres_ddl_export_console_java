CREATE TABLE "public"."orders" (
  "order_id" INTEGER DEFAULT nextval('orders_order_id_seq'::regclass) NOT NULL,
  "customer_id" INTEGER,
  "total_amount" NUMERIC NOT NULL,
  "order_status" CHARACTER VARYING(30) DEFAULT 'PENDING'::character varying,
  "ordered_at" TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
