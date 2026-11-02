<?php
// Opcional: copia a MailConfig.php si quieres fijar el remitente explícitamente.
// No requiere contraseña, SMTP ni Composer. El remitente debe pertenecer a tu dominio.
// Si omites este archivo, AERIQ prueba con no-reply@SERVER_NAME del hosting.
return [
    'from_email' => 'no-reply@tu-dominio.es',
    'from_name'  => 'AERIQ',
];
