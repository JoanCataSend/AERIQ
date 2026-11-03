'use strict';
const Aeriq = (() => {
  async function request(endpoint, action, payload, filters = {}) {
    const params = new URLSearchParams({action, ...filters});
    const headers = {Accept:'application/json'};
    if (payload !== undefined && endpoint === 'portal.php') {
      headers['X-CSRF-Token'] = (await AeriqAuth.session()).csrf;
    }
    if (payload !== undefined) headers['Content-Type'] = 'application/json';
    const response = await fetch(`${endpoint}?${params}`, {
      method: payload === undefined ? 'GET' : 'POST', headers,
      credentials:'same-origin', cache:'no-store',
      ...(payload === undefined ? {} : {body:JSON.stringify(payload)})
    });
    let data;
    try { data = await response.json(); } catch { throw new Error('El servidor no devolvió JSON.'); }
    if (!response.ok) {
      if (response.status === 428) location.href = 'cambiar-clave.html';
      throw new Error(data.error || `HTTP ${response.status}`);
    }
    return data;
  }
  return {
    portal:(action,payload,filters)=>request('portal.php',action,payload,filters),
    map:(mode,source='all',hours=24)=>request(source==='mine'?'portal.php':'public.php','map',undefined,{mode,hours}),
    submit:(payload)=>request('public.php','request',payload)
  };
})();
