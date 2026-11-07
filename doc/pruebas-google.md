# Pruebas de Google Authentication

Base real: `https://ldiamur.upv.edu.es/src/web/`. El despliegue no debe crear una carpeta adicional en la raíz web.

## Automáticas (sin cuenta de Google)

```powershell
# No necesita base de datos ni red; comprueba configuración y claims.
php scripts/tests/google-auth-unit.php

# Ejecutar después de desplegar el ZIP. Solo envía un token inválido.
python scripts/tests/google-auth-live.py
```

La prueba unitaria comprueba:

1. La configuración pública de Firebase está completa y es del proyecto `airiq-e9d70`.
2. Se acepta un payload válido con proveedor `google.com` y correo verificado.
3. Se rechazan audiencia, emisor, caducidad, fecha futura, proveedor, correo no verificado, UID y correo inválidos.
4. Un texto que no es JWT se rechaza antes de consultar certificados.

La prueba live comprueba:

1. `auth.php?action=session` responde JSON y entrega CSRF.
2. `auth.php?action=firebase-config` responde 200 y `configured:true`.
3. `login.html` carga el botón y `google-auth.js`.
4. El frontend usa el SDK oficial y fuerza un ID token nuevo.
5. El backend rechaza un token inválido con 401.
6. `server/FirebaseConfig.php`, su ejemplo y `server/FirebaseAuth.php` no se sirven como código o credenciales.

## Matriz manual con cuentas reales

Prepara una cuenta de Google por caso y usa el correo exacto de `usuarios.email`. No se debe crear ninguna cuenta desde Google.

| Caso | Preparación | Resultado esperado |
| --- | --- | --- |
| Invitación vigente | Admin invita a `operador1@…`; la invitación no ha caducado | Entra, se guarda `firebase_uid`, se activa y la contraseña temporal deja de servir |
| Correo no registrado | Cuenta Google `desconocido@…` que no existe en `usuarios` | HTTP 403; no se crea ninguna fila |
| Cuenta desactivada | `activo=0` y sin invitación pendiente | HTTP 403; no se inicia sesión |
| Invitación caducada | `debe_cambiar_clave=1` y `invitacion_expira_en` en el pasado | HTTP 403; se debe recuperar el acceso |
| Correo no verificado | Token cuyo `email_verified` sea falso | HTTP 401; no se vincula |
| Token inválido o alterado | Cambiar un carácter del `idToken` | HTTP 401; no se vincula |
| Token caducado | Usar un token con `exp` vencido | HTTP 401; no se vincula |
| Repetición | Reenviar el mismo `idToken` ya consumido | HTTP 409; el frontend debe pedir un token nuevo |
| Login tradicional | Cuenta con contraseña verificada | Entra igual que antes; no se altera `firebase_uid` |
| Ruta real | Abrir `https://ldiamur.upv.edu.es/src/web/login.html` | El botón carga y el POST va a `/src/web/auth.php` |

Para observar los códigos HTTP, abre las herramientas de red del navegador o revisa el registro de Plesk. No publiques tokens completos en capturas o incidencias.

## Comprobación del endpoint

```text
https://ldiamur.upv.edu.es/src/web/auth.php?action=session
https://ldiamur.upv.edu.es/src/web/auth.php?action=firebase-config
```

La segunda debe devolver un JSON con `"configured":true` y `"projectId":"airiq-e9d70"`. Si devuelve 405 o no existe `google-auth.js`, el servidor sigue ejecutando la versión anterior.

## Límites

Una prueba automática no puede validar la firma de un token de Google sin un token real emitido por Firebase. La matriz manual es obligatoria para confirmar el consentimiento de Google, el dominio autorizado y el alta de una invitación real.
