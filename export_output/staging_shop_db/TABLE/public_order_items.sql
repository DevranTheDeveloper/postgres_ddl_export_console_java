CREATE TABLE "public"."order_items" (
  "item_id" INTEGER DEFAULT nextval('order_items_item_id_seq'::regclass) NOT NULL,
  "order_id" INTEGER,
  "product_id" INTEGER,
  "quantity" INTEGER NOT NULL,
  "unit_price" NUMERIC NOT NULL
);
