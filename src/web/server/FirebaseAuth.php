<?php
declare(strict_types=1);

// Las librerías solo se cargan desde auth.php/mobile-auth.php. Si alguien pide
// este archivo directamente, no debe ejecutarse ni revelar rutas internas.
if (isset($_SERVER['SCRIPT_FILENAME']) && realpath((string)$_SERVER['SCRIPT_FILENAME']) === __FILE__) {
    http_response_code(404);
    exit;
}

/**
 * Firebase Authentication (proveedor Google) y vinculación con usuarios AERIQ.
 *
 * El admin crea al operador en la tabla usuarios. Google no crea cuentas nuevas:
 * solo puede entrar un correo que ya exista. En el primer acceso se guarda el UID
 * de Firebase en identidades_externas (proveedor 'firebase') y, si la invitación
 * sigue vigente, la cuenta pendiente se activa. El resto de accesos se resuelven
 * por ese UID estable.
 */

function firebase_config(): array {
    static $config = null;
    if (is_array($config)) return $config;

    $defaults = [];
    $defaultFile = __DIR__ . '/FirebaseConfig.example.php';
    if (is_file($defaultFile)) {
        $defaults = require $defaultFile;
        if (!is_array($defaults)) $defaults = [];
    }
    $overrides = [];
    $file = __DIR__ . '/FirebaseConfig.php';
    if (is_file($file)) {
        $overrides = require $file;
        if (!is_array($overrides)) $overrides = [];
    }
    // Un archivo privado antiguo con valores vacíos o PEGA_AQUI no debe romper
    // la configuración pública por defecto del proyecto.
    $saved = array_merge($defaults, array_filter(
        $overrides,
        static fn($value) => is_string($value) && $value !== '' && !str_starts_with($value, 'PEGA_AQUI')
    ));

    $config = [
        'project_id'          => trim((string)($saved['project_id'] ?? (getenv('AERIQ_FIREBASE_PROJECT_ID') ?: ''))),
        'auth_domain'         => trim((string)($saved['auth_domain'] ?? (getenv('AERIQ_FIREBASE_AUTH_DOMAIN') ?: ''))),
        'api_key'             => trim((string)($saved['api_key'] ?? (getenv('AERIQ_FIREBASE_API_KEY') ?: ''))),
        'app_id'              => trim((string)($saved['app_id'] ?? (getenv('AERIQ_FIREBASE_APP_ID') ?: ''))),
        'messaging_sender_id' => trim((string)($saved['messaging_sender_id'] ?? (getenv('AERIQ_FIREBASE_SENDER_ID') ?: ''))),
        'storage_bucket'      => trim((string)($saved['storage_bucket'] ?? (getenv('AERIQ_FIREBASE_STORAGE_BUCKET') ?: ''))),
        'google_client_id'    => trim((string)($saved['google_client_id'] ?? (getenv('AERIQ_GOOGLE_CLIENT_ID') ?: ''))),
    ];
    return $config;
}

function firebase_placeholder(string $value): bool {
    return $value === '' || str_starts_with($value, 'PEGA_AQUI');
}

/** Datos públicos que necesita el SDK web de Firebase. Nunca se envía una clave privada. */
function firebase_public_config(): array {
    $c = firebase_config();
    return [
        'projectId'          => $c['project_id'],
        'authDomain'         => $c['auth_domain'],
        'apiKey'             => firebase_placeholder($c['api_key']) ? '' : $c['api_key'],
        'appId'              => firebase_placeholder($c['app_id']) ? '' : $c['app_id'],
        'messagingSenderId'  => firebase_placeholder($c['messaging_sender_id']) ? '' : $c['messaging_sender_id'],
        'storageBucket'      => $c['storage_bucket'],
    ];
}

function firebase_web_configured(): bool {
    $c = firebase_public_config();
    return $c['projectId'] !== '' && $c['authDomain'] !== '' && $c['apiKey'] !== '' && $c['appId'] !== '';
}

function firebase_project_id(): string {
    $project = (string)firebase_config()['project_id'];
    if (!preg_match('/^[a-z0-9][a-z0-9-]{2,62}$/D', $project)) {
        throw new AeriqError('Falta configurar el proyecto de Firebase en server/FirebaseConfig.php o server/FirebaseConfig.example.php.', 503);
    }
    return $project;
}

function firebase_b64url(string $value): string {
    $value = strtr($value, '-_', '+/');
    $padding = strlen($value) % 4;
    if ($padding !== 0) $value .= str_repeat('=', 4 - $padding);
    $decoded = base64_decode($value, true);
    if ($decoded === false) throw new AeriqError('El token de Google no tiene un formato válido.', 401);
    return $decoded;
}

function firebase_http_get(string $url): string {
    $context = stream_context_create([
        'http' => [
            'timeout' => 8,
            'ignore_errors' => true,
            'header' => "Accept: application/json\r\nUser-Agent: AERIQ/1.0\r\n",
        ],
        'ssl' => ['verify_peer' => true, 'verify_peer_name' => true],
    ]);
    $body = @file_get_contents($url, false, $context);
    $status = 0;
    if (isset($http_response_header[0]) && preg_match('/\s(\d{3})\s/', $http_response_header[0], $match)) {
        $status = (int)$match[1];
    }
    if (is_string($body) && $body !== '' && ($status === 0 || $status === 200)) return $body;

    if (function_exists('curl_init')) {
        $ch = curl_init($url);
        curl_setopt_array($ch, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_FOLLOWLOCATION => false,
            CURLOPT_CONNECTTIMEOUT => 5,
            CURLOPT_TIMEOUT => 8,
            CURLOPT_SSL_VERIFYPEER => true,
            CURLOPT_SSL_VERIFYHOST => 2,
            CURLOPT_HTTPHEADER => ['Accept: application/json', 'User-Agent: AERIQ/1.0'],
        ]);
        $result = curl_exec($ch);
        $code = (int)curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
        curl_close($ch);
        if (is_string($result) && $result !== '' && $code === 200) return $result;
    }

    throw new AeriqError('No se ha podido comprobar la sesión con Google. Inténtalo de nuevo.', 503);
}

/** Certificados públicos de Firebase, cacheados para no consultarlos en cada acceso. */
function firebase_certificates(bool $refresh = false): array {
    $project = firebase_project_id();
    $cache = __DIR__ . '/.firebase-certs-' . substr(hash('sha256', $project), 0, 16) . '.json';
    if (!$refresh && is_file($cache)) {
        $data = json_decode((string)@file_get_contents($cache), true);
        if (is_array($data) && ($data['expires'] ?? 0) > time() && is_array($data['certs'] ?? null)) {
            return $data['certs'];
        }
    }

    $json = firebase_http_get('https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com');
    $certs = json_decode($json, true);
    if (!is_array($certs) || !$certs) {
        throw new AeriqError('Google no ha devuelto certificados válidos. Inténtalo de nuevo.', 503);
    }

    $written = @file_put_contents($cache, json_encode([
        'expires' => time() + 3600,
        'certs' => $certs,
    ]), LOCK_EX);
    if ($written !== false) @chmod($cache, 0600);
    return $certs;
}

/**
 * Verifica localmente el ID token de Firebase con la clave pública de Google.
 * Comprueba firma RS256, emisor, proyecto, caducidad y que el proveedor sea Google.
 */
function firebase_verify_id_token(string $token): array {
    if ($token === '' || strlen($token) > 12000) {
        throw new AeriqError('No se ha recibido un token de Google válido.', 401);
    }
    $parts = explode('.', $token);
    if (count($parts) !== 3) throw new AeriqError('El token de Google no es válido.', 401);

    $header = json_decode(firebase_b64url($parts[0]), true);
    $payload = json_decode(firebase_b64url($parts[1]), true);
    if (!is_array($header) || !is_array($payload)) {
        throw new AeriqError('El token de Google no es válido.', 401);
    }
    if (($header['alg'] ?? '') !== 'RS256') {
        throw new AeriqError('El token de Google usa un algoritmo no admitido.', 401);
    }

    $kid = (string)($header['kid'] ?? '');
    if ($kid === '') throw new AeriqError('El token de Google no identifica su firma.', 401);
    $certs = firebase_certificates();
    if (!isset($certs[$kid])) $certs = firebase_certificates(true);
    if (!isset($certs[$kid])) throw new AeriqError('Google ha renovado sus certificados. Vuelve a intentarlo.', 401);

    if (!function_exists('openssl_verify')) {
        throw new AeriqError('PHP necesita la extensión OpenSSL para verificar el acceso con Google.', 503);
    }
    $signature = firebase_b64url($parts[2]);
    $valid = openssl_verify($parts[0] . '.' . $parts[1], $signature, (string)$certs[$kid], OPENSSL_ALGO_SHA256);
    if ($valid !== 1) throw new AeriqError('La firma del token de Google no es válida.', 401);

    return firebase_validate_payload($payload);
}

/**
 * Valida las claims de un token ya firmado. Se separa de la verificación de
 * firma para poder probar caducidad, emisor, audiencia y proveedor sin red.
 */
function firebase_validate_payload(array $payload, int $now = 0): array {
    $project = firebase_project_id();
    if ($now <= 0) $now = time();

    if (($payload['aud'] ?? '') !== $project) {
        throw new AeriqError('El token pertenece a otro proyecto de Firebase.', 401);
    }
    if (($payload['iss'] ?? '') !== 'https://securetoken.google.com/' . $project) {
        throw new AeriqError('El emisor del token de Google no es válido.', 401);
    }
    if (!is_numeric($payload['exp'] ?? null) || (int)$payload['exp'] <= $now - 30) {
        throw new AeriqError('La sesión de Google ha caducado. Vuelve a intentarlo.', 401);
    }
    if (!is_numeric($payload['iat'] ?? null) || (int)$payload['iat'] > $now + 300) {
        throw new AeriqError('La fecha del token de Google no es válida.', 401);
    }

    $uid = (string)($payload['sub'] ?? '');
    if ($uid === '' || strlen($uid) > 128) {
        throw new AeriqError('El token de Google no identifica al usuario.', 401);
    }
    if (($payload['firebase']['sign_in_provider'] ?? '') !== 'google.com') {
        throw new AeriqError('Inicia sesión con el proveedor Google.', 401);
    }
    if (($payload['email_verified'] ?? false) !== true || !is_string($payload['email'] ?? null)) {
        throw new AeriqError('Google no ha verificado ese correo electrónico.', 401);
    }
    $email = strtolower(trim((string)$payload['email']));
    if (strlen($email) > 254 || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
        throw new AeriqError('Google no ha devuelto un correo electrónico válido.', 401);
    }

    return [
        'uid'     => $uid,
        'email'   => $email,
        'name'    => is_string($payload['name'] ?? null) ? $payload['name'] : '',
        'picture' => is_string($payload['picture'] ?? null) ? $payload['picture'] : '',
        'exp'     => (int)$payload['exp'],
    ];
}

/**
 * Une la identidad de Google con la cuenta que ya creó el admin.
 * No registra correos nuevos: sin invitación previa se rechaza el acceso.
 */
function firebase_link_account(array $claims): array {
    $email = auth_email($claims['email'] ?? '');
    $uid = (string)($claims['uid'] ?? '');
    if ($uid === '') throw new AeriqError('El token de Google no identifica al usuario.', 401);

    $db = conexionBD();
    $db->begin_transaction();
    try {
        $user = aeriq_one("SELECT u.* FROM identidades_externas i JOIN usuarios u ON u.id=i.usuario_id WHERE i.proveedor='firebase' AND i.uid_externo=? LIMIT 1", [$uid]);
        $linkedNow = false;

        if ($user) {
            // Cuenta ya vinculada: el UID manda aunque Google cambie el correo mostrado.
            if ((int)$user['debe_cambiar_clave'] === 1) {
                if (empty($user['invitacion_expira_en']) || strtotime((string)$user['invitacion_expira_en']) <= time()) {
                    throw new AeriqError('La invitación ha caducado. Contacta con tu organización.', 403);
                }
                // Google sustituye la contraseña temporal; la credencial enviada por correo deja de servir.
                $randomHash = password_hash(bin2hex(random_bytes(16)), PASSWORD_DEFAULT);
                aeriq_sql('UPDATE usuarios SET password_hash=?,debe_cambiar_clave=0,activo=1,verificado_en=COALESCE(verificado_en,UTC_TIMESTAMP()),invitacion_expira_en=NULL WHERE id=?', [$randomHash, (int)$user['id']]);
                // La contraseña temporal deja de servir también en móvil.
                aeriq_sql('DELETE FROM sesiones_moviles WHERE usuario_id=?', [(int)$user['id']]);
            } elseif (!(int)$user['activo']) {
                throw new AeriqError('Cuenta desactivada. Contacta con tu organización.', 403);
            }
        } else {
            // Primer acceso con Google: debe coincidir con un correo invitado por el admin.
            $user = auth_find($email);
            if (!$user) {
                throw new AeriqError('Tu correo de Google no está registrado en AERIQ. Pide a tu organización que te invite.', 403);
            }
            if (aeriq_one("SELECT id FROM identidades_externas WHERE usuario_id=? AND proveedor='firebase'", [(int)$user['id']])) {
                throw new AeriqError('Ese correo ya está vinculado a otra cuenta de Google. Contacta con tu organización.', 409);
            }
            if (!(int)$user['activo'] && !(int)$user['debe_cambiar_clave']) {
                throw new AeriqError('Cuenta desactivada. Contacta con tu organización.', 403);
            }
            if ((int)$user['debe_cambiar_clave'] === 1 && (empty($user['invitacion_expira_en']) || strtotime((string)$user['invitacion_expira_en']) <= time())) {
                throw new AeriqError('La invitación ha caducado. Contacta con tu organización.', 403);
            }
            $linkedNow = true;
            // Si la cuenta estaba pendiente, Google sustituye la contraseña temporal;
            // si ya era una cuenta activa con contraseña propia, se conserva.
            $pending = (int)$user['debe_cambiar_clave'] === 1;
            $passwordHash = $pending
                ? password_hash(bin2hex(random_bytes(16)), PASSWORD_DEFAULT)
                : (string)$user['password_hash'];
            // Las claves únicas (proveedor, uid) y (usuario, proveedor) impiden dos vínculos simultáneos.
            try {
                aeriq_sql("INSERT INTO identidades_externas(usuario_id,proveedor,uid_externo) VALUES(?,'firebase',?)", [(int)$user['id'], $uid]);
            } catch (mysqli_sql_exception $e) {
                if ($e->getCode() === 1062) throw new AeriqError('No se ha podido vincular la cuenta de Google. Inténtalo de nuevo.', 409);
                throw $e;
            }
            aeriq_sql('UPDATE usuarios SET password_hash=?,debe_cambiar_clave=0,activo=1,verificado_en=COALESCE(verificado_en,UTC_TIMESTAMP()),invitacion_expira_en=NULL WHERE id=?', [$passwordHash, (int)$user['id']]);
            if ($pending) aeriq_sql('DELETE FROM sesiones_moviles WHERE usuario_id=?', [(int)$user['id']]);
        }

        $fresh = aeriq_one('SELECT * FROM usuarios WHERE id=? LIMIT 1', [(int)$user['id']]);
        if (!$fresh) throw new AeriqError('La cuenta de AERIQ ya no está disponible.', 404);
        if (empty($fresh['verificado_en'])) {
            aeriq_sql('UPDATE usuarios SET verificado_en=UTC_TIMESTAMP() WHERE id=?', [(int)$fresh['id']]);
            $fresh['verificado_en'] = gmdate('Y-m-d H:i:s');
        }
        auth_login_allowed($fresh);
        $db->commit();
        return ['user' => $fresh, 'linked' => $linkedNow];
    } catch (Throwable $e) {
        $db->rollback();
        throw $e;
    }
}

/**
 * Marca un ID token como consumido para impedir que una petición capturada se
 * pueda reutilizar durante la hora de validez del token. La clave es un hash
 * SHA-256; el token original nunca se guarda en la base de datos.
 */
function firebase_replay_claim(string $token, int $expires): string {
    $key = hash('sha256', 'google-id-token|' . $token);
    $expiresAt = gmdate('Y-m-d H:i:s', max(time() + 60, $expires));
    try {
        aeriq_sql('INSERT INTO limites_acceso(clave_hash,ventana_en,intentos) VALUES(?,?,1)', [$key, $expiresAt]);
    } catch (mysqli_sql_exception $e) {
        if ((int)$e->getCode() === 1062) {
            throw new AeriqError('Ese acceso de Google ya se ha utilizado. Vuelve a iniciar sesión.', 409);
        }
        throw $e;
    }
    return $key;
}

/** Libera la marca si el acceso no llegó a completarse para que el usuario pueda reintentar. */
function firebase_replay_release(string $key): void {
    if ($key === '') return;
    aeriq_sql('DELETE FROM limites_acceso WHERE clave_hash=?', [$key]);
}
