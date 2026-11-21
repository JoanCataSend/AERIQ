<?php
// Opcional: copia a MailConfig.php si quieres fijar el remitente explícitamente.
// No requiere contraseña, SMTP ni Composer. El remitente debe pertenecer a tu dominio.
// Si omites este archivo, AERIQ prueba con no-reply@SERVER_NAME del hosting.
return [
    'from_email' => 'no-reply@tu-dominio.es',
    'from_name'  => 'AERIQ',
    // Dirección pública de la web para los enlaces y el logo de los correos (invitación, recuperar, solicitudes).
    // Si se omite, se usa la variable de entorno AERIQ_PUBLIC_URL o la carpeta del script en el servidor.
    // 'public_url' => 'https://jcatsen.upv.edu.es/AERIQ/',
];
