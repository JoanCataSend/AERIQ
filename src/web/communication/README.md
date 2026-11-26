# Comunicación web AERIQ

| Archivo | Responsabilidad |
| --- | --- |
| `HttpErrorMapper.php` | Traduce categorías semánticas a códigos HTTP |
| `MailTransport.php` | Configura y envía correo con SMTP nativo, PHPMailer o `mail()` |

Los puntos de entrada y servicios invocan las operaciones de comunicación; los detalles de protocolo y configuración quedan en estos adaptadores.
