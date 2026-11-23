ALTER TABLE public.account ALTER COLUMN id DROP DEFAULT;

-- 2. Convert the column type using a UUID generator function for existing rows
-- (Note: This will generate fresh UUIDs for all existing records)
ALTER TABLE public.account
ALTER COLUMN id TYPE uuid USING uuid_generate_v4();

-- 3. Set the new default value so future inserts auto-generate UUIDs
ALTER TABLE public.account
    ALTER COLUMN id SET DEFAULT uuid_generate_v4();