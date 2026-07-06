-- Flyway migration to make cuenta_id nullable in transacciones_ledger table
ALTER TABLE transacciones_ledger ALTER COLUMN cuenta_id DROP NOT NULL;
