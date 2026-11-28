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
 * Diseño lógico: (: Text --> AeriqPbio() --> Text
 * Descripción: Describe la operación realizada y su resultado.
 */
const AeriqPbio = (() => {
  const API = 'api.php'; // Mismo origen que la GUI, evita CORS.
  /**
   * Diseño lógico: query: Text --> pedir() --> Text
   * Descripción: Describe la operación realizada y su resultado.
   */
  async function pedir(query = '') {
    const controller = new AbortController();
    /**
     * Diseño lógico: (() => controller.abort(), 9000) → timeout() → resultado de la operación; según doc/frontend_business_logic_design.md.
     * Descripción: Describe la operación realizada y su resultado.
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
   * Diseño lógico: filtros: Text --> listarMedidas() --> [ Text ]
   * Descripción: Procesa listar medidas y devuelve el resultado correspondiente.
   */
  function listarMedidas(filtros={}) {
    const params = new URLSearchParams({accion:'medidas'});
    for (const [name,value] of Object.entries(filtros)) if (value !== '' && value != null) params.set(name, String(value));
    return pedir('?' + params.toString());
  }
  /**
   * Diseño lógico: listarMapaDemo() --> [ Text ]
   * Descripción: Procesa listar mapa demo y devuelve el resultado correspondiente.
   */
  function listarMapaDemo(){ return pedir('?accion=mapa-demo'); }
  /**
   * Diseño lógico: listarDispositivos() --> [ Text ]
   * Descripción: Procesa listar dispositivos y devuelve el resultado correspondiente.
   */
  function listarDispositivos() { return pedir('?accion=dispositivos'); }
  /**
   * Diseño lógico: listarTiposMedida() --> [ Text ]
   * Descripción: Procesa listar tipos medida y devuelve el resultado correspondiente.
   */
  function listarTiposMedida() { return pedir('?accion=tipos'); }
  /**
   * Diseño lógico: probarConexion() --> Text
   * Descripción: Procesa probar conexion y devuelve el resultado correspondiente.
   */
  function probarConexion() { return pedir('?accion=health'); }
  /**
   * Diseño lógico: dispositivoId: Text, tipoMedidaId: Text --> buscarUltimaMedida() --> Text
   * Descripción: Procesa buscar ultima medida y devuelve el resultado correspondiente.
   */
  function buscarUltimaMedida(dispositivoId,tipoMedidaId){
    const p=new URLSearchParams({accion:'ultima',dispositivoId,tipoMedidaId:String(tipoMedidaId)});
    return pedir('?' + p.toString());
  }
  return {listarMedidas, listarMapaDemo, listarDispositivos, listarTiposMedida, probarConexion, buscarUltimaMedida};
})();
