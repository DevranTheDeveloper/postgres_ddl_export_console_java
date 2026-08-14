CREATE TABLE "public"."products" (
  "product_id" INTEGER DEFAULT nextval('products_product_id_seq'::regclass) NOT NULL,
  "title" CHARACTER VARYING(150) NOT NULL,
  "sku" CHARACTER VARYING(50) NOT NULL,
  "price" NUMERIC NOT NULL,
  "stock_quantity" INTEGER DEFAULT 0
);
