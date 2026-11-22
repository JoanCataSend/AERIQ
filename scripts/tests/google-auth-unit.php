<?php
declare(strict_types=1);

/**
 * Pruebas sin red ni base de datos para la parte de Firebase/Google.
 *
 * Uso:
 *   php scripts/tests/google-auth-unit.php
 *
 * Comprueba la configuración pública y la validación de claims. La verificación
 * de una firma real y el alta de una cuenta necesitan un token de Google real y
 * una base de datos; esas pruebas se describen en doc/pruebas-google.md.
 */

$root = dirname(__DIR__, 2);
require_once $root . '/src/web/server/AuthCore.php';
require_once $root . '/src/web/server/FirebaseAuth.php';

$passed = 0;
$failed = 0;

function check(bool $condition, string $label): void {
    global $passed, $failed;
    if ($condition) {
        $passed++;
        echo "OK   {$label}\n";
        return;
    }
    $failed++;
    echo "FALLO {$label}\n";
}

function expect_auth_error(callable $fn, int $status, string $label): void {
    try {
        $fn();
    } catch (AeriqError $error) {
        check($error->status === $status, $label . ' (' . $error->status . '=' . $status . ')');
        return;
    } catch (Throwable $error) {
        check(false, $label . ' (excepción inesperada: ' . $error->getMessage() . ')');
        return;
    }
    check(false, $label . ' (no lanzó error)');
}

$config = firebase_public_config();
check(firebase_web_configured(), 'La configuración web de Firebase está completa');
check(($config['projectId'] ?? '') === 'airiq-e9d70', 'El proyecto público es airiq-e9d70');
check(($config['authDomain'] ?? '') === 'airiq-e9d70.firebaseapp.com', 'El authDomain es el de Firebase');
check(!isset($config['google_client_id']), 'La configuración pública no expone el client ID nativo');
check(str_starts_with((string)$config['apiKey'], 'AIza'), 'La apiKey web pública está presente');

$now = time();
$valid = [
    'aud' => 'airiq-e9d70',
    'iss' => 'https://securetoken.google.com/airiq-e9d70',
    'exp' => $now + 600,
    'iat' => $now,
    'sub' => 'firebase-uid-de-prueba',
    'firebase' => ['sign_in_provider' => 'google.com'],
    'email_verified' => true,
    'email' => 'Operador@Example.com',
    'name' => 'Operador',
];

try {
    $claims = firebase_validate_payload($valid);
    check($claims['uid'] === 'firebase-uid-de-prueba', 'Un token válido devuelve el UID');
    check($claims['email'] === 'operador@example.com', 'El correo se normaliza a minúsculas');
    check($claims['exp'] === $valid['exp'], 'La caducidad se conserva para anti-repetición');
} catch (Throwable $error) {
    check(false, 'Un token válido no debe fallar: ' . $error->getMessage());
}

expect_auth_error(
    fn() => firebase_validate_payload(array_replace($valid, ['aud' => 'otro-proyecto'])),
    401,
    'Se rechaza una audiencia de otro proyecto'
);
expect_auth_error(
    fn() => firebase_validate_payload(array_replace($valid, ['iss' => 'https://securetoken.google.com/otro-proyecto'])),
    401,
    'Se rechaza un emisor de otro proyecto'
);
expect_auth_error(
    fn() => firebase_validate_payload(array_replace($valid, ['exp' => $now - 60])),
    401,
    'Se rechaza un token caducado'
);
expect_auth_error(
    fn() => firebase_validate_payload(array_replace($valid, ['iat' => $now + 600])),
    401,
    'Se rechaza un token con fecha futura'
);
expect_auth_error(
    fn() => firebase_validate_payload(array_replace($valid, ['firebase' => ['sign_in_provider' => 'password']])),
    401,
    'Se rechaza un proveedor distinto de google.com'
);
expect_auth_error(
    fn() => firebase_validate_payload(array_replace($valid, ['email_verified' => false])),
    401,
    'Se rechaza un correo no verificado'
);
expect_auth_error(
    fn() => firebase_validate_payload(array_replace($valid, ['sub' => ''])),
    401,
    'Se rechaza un token sin UID'
);
expect_auth_error(
    fn() => firebase_validate_payload(array_replace($valid, ['email' => 'no-es-un-correo'])),
    401,
    'Se rechaza un correo con formato inválido'
);
expect_auth_error(
    fn() => firebase_verify_id_token('no-es-un-jwt'),
    401,
    'Se rechaza un token que no es JWT'
);

echo "\n{$passed} comprobaciones correctas, {$failed} fallidas.\n";
exit($failed === 0 ? 0 : 1);
