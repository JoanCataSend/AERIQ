/*
 * Fichero: aeriq-client.js
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/web.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */

'use strict';
const Aeriq = (() => {
  /**
   * Diseño lógico: endpoint: Text, accion: Text, payload: PayloadJSON, filtros: Filtros --> request() --> RespuestaAPI
   * Descripción: Envía una petición HTTP con los datos y filtros indicados, valida la respuesta JSON y propaga los errores del servicio.
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
  /**
   * Diseño lógico: accion: Text, filtros: FiltrosPanel --> consultar_panel() --> ResultadoPanel
   * Descripción: Consulta el resumen, las lecturas o las solicitudes del portal con los filtros indicados.
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
  /**
   * Diseño lógico: ambito: Text, procedencia: Text, horas: N --> consultar_mapa() --> ResultadoMapa
   * Descripción: Elige la ruta pública o autenticada y solicita el mapa para el ámbito, la procedencia y el periodo indicados.
   */
  function consultar_mapa(scope, source='real', hours=24) {
    const endpoint=scope==='publico'?'public.php':'portal.php';
    const mode=source==='simulada'?'simulated':source;
    return request(endpoint,'map',undefined,{mode,hours});
  }
  /**
   * Diseño lógico: solicitud: Solicitud --> enviar_solicitud() --> ConfirmacionSolicitud
   * Descripción: Envía los datos de una solicitud comercial al servicio público y devuelve su confirmación.
   */
  function enviar_solicitud(payload) { return request('public.php','request',payload); }
  /**
   * Diseño lógico: consultar_servicios() --> CatalogosSolicitud
   * Descripción: Obtiene los servicios y catálogos disponibles para el formulario público.
   */
  function consultar_servicios() { return request('public.php','services'); }
  /* Compatibilidad con vistas antiguas: traduce los selectores de interfaz al contrato lógico del mapa. */
  /**
   * Diseño lógico: modo: Text, procedencia: Text, horas: N --> map() --> ResultadoMapa
   * Descripción: Adapta los selectores antiguos de la interfaz al contrato de consulta del mapa.
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
