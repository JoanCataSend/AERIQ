/* AERIQ · acceso con Google mediante Firebase Authentication (SDK web oficial). */
'use strict';

const googleButton = document.getElementById('google-signin');
let firebaseConfigPromise = null;
let firebaseSdkPromise = null;

function googleToast(message) {
  if (typeof toast === 'function') return toast(message);
  const notice = document.getElementById('toast');
  if (notice) {
    notice.textContent = message;
    notice.classList.add('show');
    clearTimeout(googleToast.timer);
    googleToast.timer = setTimeout(() => notice.classList.remove('show'), 4200);
  }
}

function googleDestination(user) {
  if (typeof window.authDestination === 'function') return window.authDestination(user);
  if (!user) return 'login.html';
  if (user.mustChangePassword) return 'cambiar-clave.html';
  return ['admin', 'analista', 'comercial'].includes(user.role) ? 'portal.html' : 'perfil.html';
}

function googleErrorMessage(error) {
  const code = String(error?.code || '');
  if (code === 'auth/unauthorized-domain') {
    return 'Este dominio no está autorizado en Firebase. Añádelo en Authentication > Settings > Authorized domains.';
  }
  if (code === 'auth/operation-not-allowed') {
    return 'El proveedor Google no está habilitado en Firebase Authentication.';
  }
  if (code === 'auth/popup-blocked') {
    return 'El navegador ha bloqueado la ventana de Google. Permite las ventanas emergentes e inténtalo de nuevo.';
  }
  if (code === 'auth/popup-closed-by-user' || code === 'auth/cancelled-popup-request') {
    return 'Has cerrado la ventana de Google antes de completar el acceso.';
  }
  if (code === 'auth/account-exists-with-different-credential') {
    return 'Ese correo ya usa otro método de acceso en Firebase. Contacta con tu organización.';
  }
  if (code === 'auth/network-request-failed') {
    return 'No se ha podido conectar con Google. Revisa tu conexión.';
  }
  return error instanceof Error ? error.message : 'No se ha podido iniciar sesión con Google.';
}

async function loadFirebaseConfig() {
  if (!firebaseConfigPromise) {
    firebaseConfigPromise = fetch('auth.php?action=firebase-config', {
      credentials: 'same-origin',
      cache: 'no-store',
      headers: { Accept: 'application/json' }
    }).then(async response => {
      let data;
      try { data = await response.json(); } catch {
        throw new Error('El servidor no devolvió la configuración de Firebase.');
      }
      if (!response.ok) throw new Error(data.error || 'No se ha podido leer la configuración de Firebase.');
      return data;
    }).catch(error => {
      firebaseConfigPromise = null;
      throw error;
    });
  }
  return firebaseConfigPromise;
}

async function loadFirebaseSdk() {
  if (!firebaseSdkPromise) {
    firebaseSdkPromise = Promise.all([
      import('https://www.gstatic.com/firebasejs/10.12.5/firebase-app.js'),
      import('https://www.gstatic.com/firebasejs/10.12.5/firebase-auth.js')
    ]).then(([appModule, authModule]) => ({ appModule, authModule })).catch(error => {
      firebaseSdkPromise = null;
      throw error;
    });
  }
  return firebaseSdkPromise;
}

async function signInWithGoogle() {
  const config = await loadFirebaseConfig();
  if (!config.configured) {
    throw new Error('Google no está configurado en el servidor. Revisa server/FirebaseConfig.php o FirebaseConfig.example.php.');
  }

  const { appModule, authModule } = await loadFirebaseSdk();
  const app = appModule.getApps().length
    ? appModule.getApps()[0]
    : appModule.initializeApp(config.config);
  const auth = authModule.getAuth(app);
  auth.useDeviceLanguage();

  const provider = new authModule.GoogleAuthProvider();
  provider.setCustomParameters({ prompt: 'select_account' });
  const credential = await authModule.signInWithPopup(auth, provider);
  // Forzar un token nuevo evita que el servidor rechace por reutilización un
  // token cacheado de un intento anterior.
  const idToken = await credential.user.getIdToken(true);
  if (!window.AeriqAuth || typeof window.AeriqAuth.post !== 'function') {
    throw new Error('El cliente de sesión no se ha cargado. Recarga la página.');
  }
  const result = await window.AeriqAuth.post('google', { idToken });
  return result;
}

if (googleButton) {
  googleButton.addEventListener('click', async () => {
    if (googleButton.disabled) return;
    const label = googleButton.querySelector('span');
    const original = label ? label.textContent : '';
    googleButton.disabled = true;
    googleButton.setAttribute('aria-busy', 'true');
    if (label) label.textContent = 'Conectando con Google…';
    try {
      const result = await signInWithGoogle();
      location.href = googleDestination(result.user);
    } catch (error) {
      googleToast(googleErrorMessage(error));
    } finally {
      googleButton.disabled = false;
      googleButton.removeAttribute('aria-busy');
      if (label && original) label.textContent = original;
    }
  });
}
