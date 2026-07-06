BEGIN;

-- Break the cyclic constraint from empresas -> plan_cuentas
UPDATE empresas 
SET cuenta_contable_caja_id = NULL, 
    cuenta_contable_caja_ahorro_id = NULL, 
    cuenta_contable_bancos_id = NULL, 
    cuenta_contable_capital_id = NULL 
WHERE id IN (29, 30);

-- Delete from all child tables
DELETE FROM transacciones_ledger WHERE empresa_id IN (29, 30);
DELETE FROM asientos_detalle WHERE cabecera_id IN (SELECT id FROM asientos_cabecera WHERE empresa_id IN (29, 30));
DELETE FROM asientos_cabecera WHERE empresa_id IN (29, 30);

DELETE FROM cuotas_amortizacion WHERE credito_id IN (SELECT id FROM credito WHERE empresa_id IN (29, 30));
DELETE FROM transacciones_credito WHERE credito_id IN (SELECT id FROM credito WHERE empresa_id IN (29, 30));
DELETE FROM credito WHERE empresa_id IN (29, 30);

DELETE FROM historial_transacciones WHERE cuenta_id IN (SELECT id FROM cuentas_ahorros WHERE socio_id IN (SELECT id FROM socio WHERE empresa_id IN (29, 30)));
DELETE FROM cuentas_ahorros WHERE socio_id IN (SELECT id FROM socio WHERE empresa_id IN (29, 30));

DELETE FROM socios_credenciales WHERE socio_id IN (SELECT id FROM socio WHERE empresa_id IN (29, 30));
DELETE FROM socio WHERE empresa_id IN (29, 30);

DELETE FROM cajas_ventanilla WHERE empresa_id IN (29, 30);
DELETE FROM agencias WHERE empresa_id IN (29, 30);

DELETE FROM otp_verificacion WHERE empresa_id IN (29, 30);
DELETE FROM usuarios_admin WHERE empresa_id IN (29, 30);

-- Now delete plan_cuentas
DELETE FROM plan_cuentas WHERE empresa_id IN (29, 30);

-- Finally delete the companies
DELETE FROM empresas WHERE id IN (29, 30);

COMMIT;
