CREATE TABLE "public"."products" (
  "id" INTEGER DEFAULT nextval('products_id_seq'::regclass) NOT NULL,
  "product_name" CHARACTER VARYING(100) NOT NULL,
  "price" NUMERIC NOT NULL,
  "stock_count" INTEGER DEFAULT 0
);
