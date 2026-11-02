/* Cliente de dominio web AERIQ. Basado en src/frontend_business_logic/web/LogicaFake.js.
   La GUI usa AeriqPbio; la red queda encapsulada aquí. */
'use strict';
const AeriqPbio = (() => {
  const API = 'api.php'; // Mismo origen que la GUI, evita CORS.
  async function pedir(query = '') {
    const controller = new AbortController();
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
  function listarMedidas(filtros={}) {
    const params = new URLSearchParams({accion:'medidas'});
    for (const [name,value] of Object.entries(filtros)) if (value !== '' && value != null) params.set(name, String(value));
    return pedir('?' + params.toString());
  }
  function listarMapaDemo(){ return pedir('?accion=mapa-demo'); }
  function listarDispositivos() { return pedir('?accion=dispositivos'); }
  function listarTiposMedida() { return pedir('?accion=tipos'); }
  function probarConexion() { return pedir('?accion=health'); }
  function buscarUltimaMedida(dispositivoId,tipoMedidaId){
    const p=new URLSearchParams({accion:'ultima',dispositivoId,tipoMedidaId:String(tipoMedidaId)});
    return pedir('?' + p.toString());
  }
  return {listarMedidas, listarMapaDemo, listarDispositivos, listarTiposMedida, probarConexion, buscarUltimaMedida};
})();
