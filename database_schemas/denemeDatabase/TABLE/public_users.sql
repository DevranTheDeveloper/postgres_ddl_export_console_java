CREATE TABLE "public"."users" (
  "id" INTEGER DEFAULT nextval('user_seq'::regclass) NOT NULL,
  "username" CHARACTER VARYING(50) NOT NULL,
  "email" CHARACTER VARYING(100) NOT NULL,
  "status" USER-DEFINED DEFAULT 'ACTIVE'::status_enum,
  "created_at" TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
