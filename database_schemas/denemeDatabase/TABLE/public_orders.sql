CREATE TABLE "public"."orders" (
  "order_id" INTEGER NOT NULL,
  "user_id" INTEGER,
  "total_amount" NUMERIC NOT NULL,
  "order_date" TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
