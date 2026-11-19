-- AERIQ · 07 · El estado de una campaña sale de sus fechas (programada, activa, finalizada).
-- Se quita el interruptor manual: todas las campañas quedan con activa=1 y
-- la ingesta y el enlace de Orbs siguen filtrando por inicio y fin.
UPDATE campanas SET activa=1 WHERE activa<>1;
