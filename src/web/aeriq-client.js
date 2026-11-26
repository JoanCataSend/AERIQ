'use strict';
/**
 * Crea el adaptador de dominio web: traduce las operaciones lógicas a rutas y gestiona transporte, CSRF y respuestas.
 * Diseño lógico: (sin parámetros) → Aeriq() → cliente de operaciones de dominio; según doc/frontend_business_logic_design.md.
 */
const Aeriq = (() => {
  /**
   * Gestiona petición.
   * Diseño lógico: (endpoint, action, payload, filters = {}) → request() → Promise; según doc/frontend_business_logic_design.md.
   */
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
      // `field` indica qué campo del formulario hay que revisar (errores 422 de public.php).
      throw Object.assign(new Error(data.error || `HTTP ${response.status}`), {status:response.status, field:data.field});
    }
    return data;
  }
  /*
   * --------------------
   * Diseño lógico: accion: AccionPanel, filtros: FiltrosPanel --> consultar_panel() --> resultado: ResultadoPanel
   * Descripción: consulta una vista lógica; la cookie de sesión y el servidor aportan el contexto de autorización.
   * --------------------
   */
  /**
   * Diseño lógico: (action, filters={}) → consultar_panel() → resultado de la operación; según doc/frontend_business_logic_design.md.
   */
  function consultar_panel(action, filters={}) {
    const routes={resumen:'dashboard',lecturas:'readings',solicitudes:'requests'};
    if(!routes[action])throw new Error('Consulta de panel no disponible.');
    const query={...filters};
    if(action==='lecturas'&&Object.prototype.hasOwnProperty.call(query,'type')) {
      const magnitudes={ozono:14,temperatura:12,todas:'all'};
      if(Object.prototype.hasOwnProperty.call(magnitudes,query.type))query.type=magnitudes[query.type];
    }
    if(typeof query.export==='boolean') { if(query.export)query.export=1; else delete query.export; }
    return request('portal.php',routes[action],undefined,query);
  }
  /*
   * --------------------
   * Diseño lógico: ambito: AmbitoMapa, procedencia: Procedencia, horas: N --> consultar_mapa() --> resultado: ResultadoMapa
   * Descripción: selecciona la ruta pública o autenticada; el servidor resuelve el acceso de la sesión.
   * --------------------
   */
  /**
   * Diseño lógico: (scope, source='real', hours=24) → consultar_mapa() → resultado de la operación; según doc/frontend_business_logic_design.md.
   */
  function consultar_mapa(scope, source='real', hours=24) {
    const endpoint=scope==='publico'?'public.php':'portal.php';
    const mode=source==='simulada'?'simulated':source;
    return request(endpoint,'map',undefined,{mode,hours});
  }
  /*
   * --------------------
   * Diseño lógico: solicitud: DatosSolicitud --> enviar_solicitud() --> confirmacion: ConfirmacionSolicitud
   * Descripción: envía una solicitud comercial al endpoint público.
   * --------------------
   */
  /**
   * Diseño lógico: (payload) → enviar_solicitud() → resultado de la operación; según doc/frontend_business_logic_design.md.
   */
  function enviar_solicitud(payload) { return request('public.php','request',payload); }
  /*
   * --------------------
   * Diseño lógico: --> consultar_servicios() --> catalogos: CatalogosSolicitud
   * Descripción: obtiene servicios y catálogos del formulario público.
   * --------------------
   */
  /**
   * Diseño lógico: (sin parámetros) → consultar_servicios() → resultado de la operación; según doc/frontend_business_logic_design.md.
   */
  function consultar_servicios() { return request('public.php','services'); }
  /* Compatibilidad con vistas antiguas: traduce los selectores de interfaz al contrato lógico del mapa. */
  /**
   * Diseño lógico: (mode,source='all',hours=24) → map() → resultado de la operación; según doc/frontend_business_logic_design.md.
   */
  function map(mode,source='all',hours=24) {
    const mine=source==='mine';
    return consultar_mapa(mine?'organizacion':'publico',mode==='simulated'?'simulada':'real',hours);
  }
  return {
    consultar_panel,
    consultar_mapa,
    enviar_solicitud,
    consultar_servicios,
    portal:(action,payload,filters)=>request('portal.php',action,payload,filters),
    map,
    submit:enviar_solicitud,
    services:consultar_servicios
  };
})();
