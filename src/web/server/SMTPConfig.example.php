<?php
/*
 * Fichero: SMTPConfig.example.php
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/web/server.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
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
