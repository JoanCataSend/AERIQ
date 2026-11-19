-- AERIQ · 08 · El dispositivo ya no se llama «Orb» sino «AERIQ».
-- Cambia los textos que la web lee de la base de datos (servicios, roles, permisos,
-- modelo y nombres de dispositivos). Las mediciones y asignaciones no se tocan.
UPDATE servicios SET nombre=REPLACE(nombre,'Orb','AERIQ'), descripcion=REPLACE(descripcion,'Orb','AERIQ');
UPDATE roles SET descripcion=REPLACE(descripcion,'Orb','AERIQ');
UPDATE permisos SET descripcion=REPLACE(descripcion,'Orb','AERIQ');
UPDATE modelos_dispositivo SET nombre=REPLACE(nombre,'Orb','AERIQ');
UPDATE dispositivos SET nombre=REPLACE(nombre,'Orb','AERIQ') WHERE nombre LIKE '%Orb%';
