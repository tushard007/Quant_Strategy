-- Upgrade the role CHECK constraint on existing installations (Hibernate does not widen it reliably).
DO $$
DECLARE role_check record;
BEGIN
    FOR role_check IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'app_user_account'::regclass AND contype = 'c'
          AND pg_get_constraintdef(oid) ~* '\mrole\M'
    LOOP
        EXECUTE format('ALTER TABLE app_user_account DROP CONSTRAINT %I', role_check.conname);
    END LOOP;
    ALTER TABLE app_user_account ADD CONSTRAINT app_user_account_role_check
        CHECK (role IN ('SUPERADMIN', 'ADMIN', 'USER'));
END $$;
-- Credentials are provisioned separately; never include passwords or password hashes in migrations.
