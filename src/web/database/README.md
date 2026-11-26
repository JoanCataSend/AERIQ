# Persistencia web AERIQ

| Archivo | Responsabilidad |
| --- | --- |
| `SqlGateway.php` | Conexión, ejecución SQL parametrizada, transacciones e identificador de inserción |
| `AuthRepository.php` | Lectura y escritura de cuentas, sesiones móviles, identidades externas, límites y códigos de correo |

Los casos de uso de autenticación y Firebase no construyen consultas ni acceden a `mysqli`: delegan en `AuthRepository`. `AeriqCore.php` ya usa el gateway para la ejecución y las transacciones, pero todavía conserva consultas de dominio en sus funciones; deben trasladarse a repositorios específicos antes de afirmar que la capa de persistencia está completamente aislada.
