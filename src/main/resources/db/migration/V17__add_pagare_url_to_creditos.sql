-- Add pagare_url to creditos
ALTER TABLE creditos ADD COLUMN IF NOT EXISTS pagare_url VARCHAR(500);
