# Persistencia web AERIQ

| Archivo | Responsabilidad |
| --- | --- |
| `SqlGateway.php` | Conexión, ejecución SQL parametrizada, transacciones e identificador de inserción |
| `AuthRepository.php` | Lectura y escritura de cuentas, sesiones móviles, identidades externas, límites y códigos de correo |
| `AeriqRepository.php` | Operaciones de persistencia de entidades AERIQ, transacciones e informes relacionados |
| `AeriqQueryCatalog.php` | Consultas y fragmentos SQL organizados por operación de aplicación |

Los servicios de aplicación delegan la ejecución y las transacciones en repositorios; las sentencias se mantienen en el catálogo SQL. Los casos de uso de autenticación y Firebase delegan además sus operaciones en `AuthRepository`.
