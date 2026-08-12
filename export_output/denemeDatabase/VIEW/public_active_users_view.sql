CREATE OR REPLACE VIEW "public"."active_users_view" AS
 SELECT id,
    username,
    email,
    created_at
   FROM users
  WHERE status = 'ACTIVE'::status_enum;
