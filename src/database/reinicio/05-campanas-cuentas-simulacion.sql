-- Paso 05. Asigna campaña a las cuentas de prueba que quedaron sin ella tras el paso 04.
-- Importar en phpMyAdmin con la base seleccionada. Se puede repetir sin problema.
-- Marta y admincamp (admins de campaña) y Pau (operador) -> «Simulación · paseo por el campus».
-- Si una cuenta no existe en tu base, simplemente no se toca.
-- Lucía (operadora) -> «Simulación · playa de Gandia», donde está su Orb.
SET NAMES utf8mb4;

UPDATE usuarios u
JOIN campanas c ON c.organizacion_id=u.organizacion_id AND c.nombre='Simulación · paseo por el campus'
SET u.campana_id=c.id
WHERE u.email IN ('analista.simulacion@aeriq.example','admincamp.simulacion@aeriq.example','operador.simulacion@aeriq.example');

UPDATE usuarios u
JOIN campanas c ON c.organizacion_id=u.organizacion_id AND c.nombre='Simulación · playa de Gandia'
SET u.campana_id=c.id
WHERE u.email='operadora.simulacion@aeriq.example';

-- Resumen: cada cuenta con su rol y su campaña. El admin principal no tiene campaña (ve todas).
SELECT u.email, r.nombre AS rol, COALESCE(c.nombre,'— (todas)') AS campana
FROM usuarios u JOIN roles r ON r.id=u.rol_id LEFT JOIN campanas c ON c.id=u.campana_id
ORDER BY r.id DESC, u.email;