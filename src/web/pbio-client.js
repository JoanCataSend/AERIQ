/*
 * Fichero: pbio-client.js
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/web.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */


/* Cliente de dominio web AERIQ. Basado en src/frontend_business_logic/web/LogicaFake.js.
   La GUI usa AeriqPbio; la red queda encapsulada aquí. */
'use strict';
/**
 * Crea el adaptador de compatibilidad PBIO y mantiene sus consultas detrás de la API web.
 * Diseño lógico: (sin parámetros) → AeriqPbio() → cliente PBIO; según doc/frontend_business_logic_design.md.
 */
const AeriqPbio = (() => {
  const API = 'api.php'; // Mismo origen que la GUI, evita CORS.
  /**
   * Gestiona pedir.
   * Diseño lógico: (query = '') → pedir() → Promise; según doc/frontend_business_logic_design.md.
   */
  async function pedir(query = '') {
    const controller = new AbortController();
    /**
     * Gestiona timeout.
     * Diseño lógico: (() => controller.abort(), 9000) → timeout() → resultado de la operación; según doc/frontend_business_logic_design.md.
     */
    const timeout = setTimeout(() => controller.abort(), 9000);
    try {
      const response = await fetch(API + query, {headers: {'Accept':'application/json'},
        credentials:'same-origin', cache:'no-store',signal:controller.signal});
      const type = response.headers.get('content-type') || '';
      if (!type.includes('application/json')) throw new Error('El servidor no devolvió JSON. ¿Está activa la API PHP?');
      const data = await response.json();
      if (!response.ok) throw new Error(data?.error || `HTTP ${response.status}`);
      return data;
    } finally { clearTimeout(timeout); }
  }
  /**
   * Lista medidas.
   * Diseño lógico: (filtros={}) → listarMedidas() → resultado de la operación; según doc/frontend_business_logic_design.md.
   */
  function listarMedidas(filtros={}) {
    const params = new URLSearchParams({accion:'medidas'});
    for (const [name,value] of Object.entries(filtros)) if (value !== '' && value != null) params.set(name, String(value));
    return pedir('?' + params.toString());
  }
  /**
   * Lista mapa demo.
   * Diseño lógico: (sin parámetros) → listarMapaDemo() → resultado de la operación; según doc/frontend_business_logic_design.md.
   */
  function listarMapaDemo(){ return pedir('?accion=mapa-demo'); }
  /**
   * Lista dispositivos.
   * Diseño lógico: (sin parámetros) → listarDispositivos() → resultado de la operación; según doc/frontend_business_logic_design.md.
   */
  function listarDispositivos() { return pedir('?accion=dispositivos'); }
  /**
   * Lista tipos medida.
   * Diseño lógico: (sin parámetros) → listarTiposMedida() → resultado de la operación; según doc/frontend_business_logic_design.md.
   */
  function listarTiposMedida() { return pedir('?accion=tipos'); }
  /**
   * Gestiona probar conexion.
   * Diseño lógico: (sin parámetros) → probarConexion() → resultado de la operación; según doc/frontend_business_logic_design.md.
   */
  function probarConexion() { return pedir('?accion=health'); }
  /**
   * Gestiona buscar ultima medida.
   * Diseño lógico: (dispositivoId,tipoMedidaId) → buscarUltimaMedida() → resultado de la operación; según doc/frontend_business_logic_design.md.
   */
  function buscarUltimaMedida(dispositivoId,tipoMedidaId){
    const p=new URLSearchParams({accion:'ultima',dispositivoId,tipoMedidaId:String(tipoMedidaId)});
    return pedir('?' + p.toString());
  }
  return {listarMedidas, listarMapaDemo, listarDispositivos, listarTiposMedida, probarConexion, buscarUltimaMedida};
})();
