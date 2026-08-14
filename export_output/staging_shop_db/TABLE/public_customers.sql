CREATE TABLE "public"."customers" (
  "customer_id" INTEGER DEFAULT nextval('customers_customer_id_seq'::regclass) NOT NULL,
  "full_name" CHARACTER VARYING(100) NOT NULL,
  "email" CHARACTER VARYING(150) NOT NULL,
  "phone" CHARACTER VARYING(20),
  "created_at" TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
