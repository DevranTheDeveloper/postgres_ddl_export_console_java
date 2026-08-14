CREATE TABLE "public"."payments" (
  "payment_id" INTEGER DEFAULT nextval('payments_payment_id_seq'::regclass) NOT NULL,
  "order_id" INTEGER,
  "payment_method" CHARACTER VARYING(50) NOT NULL,
  "amount" NUMERIC NOT NULL,
  "paid_at" TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
