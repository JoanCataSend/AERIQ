-- Selecciona tu base AERIQ en phpMyAdmin antes de ejecutar. Solo lectura.
SELECT DATABASE() AS base_seleccionada;
SELECT COUNT(*) AS cuentas_que_se_conservan FROM usuarios;
SELECT COUNT(*) AS organizaciones_que_se_conservan FROM organizaciones;
SELECT COUNT(*) AS sensores_a_retirar FROM dispositivos;
SELECT COUNT(*) AS asignaciones_a_retirar FROM asignaciones;
SELECT COUNT(*) AS lecturas_simuladas_a_borrar FROM mediciones WHERE simulado=1;
SELECT COUNT(*) AS lecturas_reales_a_archivar FROM mediciones WHERE simulado=0;
