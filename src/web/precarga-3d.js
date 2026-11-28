/*
 * Fichero: precarga-3d.js
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/web.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */

/* AERIQ · Precarga discreta del visor 3D y del modelo del AERIQ.
   Se incluye en las páginas que no muestran el modelo para que, al abrir la ficha del AERIQ,
   el visor y el archivo GLB ya estén en la caché del navegador. No se ejecuta con ahorro
   de datos ni en conexiones 2G, porque el modelo pesa unos 59 MB. */
'use strict';
(function(){
  const connection=navigator.connection;
  if(connection&&(connection.saveData||/(^|-)2g$/.test(connection.effectiveType||'')))return;
  /**
   * Diseño lógico: preload() --> Text
   * Descripción: Describe la operación realizada y su resultado.
   */
  function preload(){
    const viewer=document.createElement('link');
    viewer.rel='prefetch';viewer.as='script';viewer.crossOrigin='anonymous';
    viewer.href='https://ajax.googleapis.com/ajax/libs/model-viewer/3.5.0/model-viewer.min.js';
    document.head.appendChild(viewer);
    fetch('assets/aeriq_sensor_3d.glb',{credentials:'same-origin',priority:'low'}).then(r=>r.ok?r.arrayBuffer():null).catch(()=>{});
  }
  /**
   * Diseño lógico: later() --> Text
   * Descripción: Procesa later y devuelve el resultado correspondiente.
   */
  const later=()=>('requestIdleCallback' in window)?requestIdleCallback(preload,{timeout:4000}):setTimeout(preload,1500);
  if(document.readyState==='complete')later();else addEventListener('load',later,{once:true});
})();
