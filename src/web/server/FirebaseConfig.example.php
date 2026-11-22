<?php
// Este archivo se incluye desde FirebaseAuth.php; nunca debe servirse directo.
if (isset($_SERVER['SCRIPT_FILENAME']) && realpath((string)$_SERVER['SCRIPT_FILENAME']) === __FILE__) {
    http_response_code(404);
    exit;
}
// Configuración pública por defecto del proyecto airiq-e9d70.
// La apiKey web de Firebase es un identificador público, no una clave secreta.
// Si el servidor necesita otro proyecto, copia este archivo como FirebaseConfig.php
// (ignorado por Git) y cambia los valores. No incluyas aquí una clave de servicio.
return [
    'project_id'          => 'airiq-e9d70',
    'auth_domain'         => 'airiq-e9d70.firebaseapp.com',
    'api_key'             => 'AIzaSyDZsrtfkSg9legplBu3d48c93NxhYaQoVw',
    'app_id'              => '1:191907520858:web:1a521fb69a88bbd8a35363',
    'messaging_sender_id' => '191907520858',
    'storage_bucket'      => 'airiq-e9d70.firebasestorage.app',
    // Solo para una futura integración nativa de Android:
    'google_client_id'    => '191907520858-kv7oc6anonad66sh56k2dj6gmlej2278.apps.googleusercontent.com',
];
