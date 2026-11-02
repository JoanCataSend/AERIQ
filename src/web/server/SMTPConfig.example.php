<?php
// COPIAR como SMTPConfig.php SOLO en Plesk, NUNCA subir el archivo real a GitHub.
return [
    'host' => 'smtp.tudominio.es',
    'port' => 587,
    'username' => 'no-reply@tudominio.es',
    'password' => 'CONTRASENA_SMTP_PRIVADA',
    'encryption' => 'tls', // tls para 587, ssl para 465
    'from_email' => 'no-reply@tudominio.es',
    'from_name' => 'AERIQ',
];
