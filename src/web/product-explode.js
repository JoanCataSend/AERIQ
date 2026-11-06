/* AERIQ · Vista explosionada del Orb en la portada · anime.js v4 sincronizado con el scroll.
   Sin anime.js, sin la sección o con movimiento reducido se queda la vista abierta estática del CSS. */
'use strict';
(function(){
  const section=document.querySelector('.explode-section');
  if(!section)return;
  const q=s=>section.querySelector(s),qa=s=>[...section.querySelectorAll(s)];
  const stage=q('.explode-stage'),visual=q('.explode-visual'),track=q('.explode-track');
  const closed=q('.explode-closed'),lid=q('.explode-lid'),bottom=q('.explode-bottom'),front=q('.explode-front');
  const shell=qa('.explode-bottom, .explode-front, .explode-shadow');
  const parts=qa('.explode-part'),labels=qa('.explode-label'),leaders=qa('.explode-leader');
  const byPart=(list,id)=>list.find(n=>n.dataset.part===id);
  const reduce=window.matchMedia?.('(prefers-reduced-motion: reduce)');
  const debug=/[?&]explode-debug\b/.test(location.search);
  if(debug)section.classList.add('is-debug');

  // Unidades del timeline: 1000 = todo el recorrido fijado. Un paso por componente, en el orden del DOM.
  const TOTAL=1000,STEPS=[380,500,620,740,860];
  let tl=null,observer=null,activeIndex=-2,lastSize='';

  // Cajas de maquetación (sin transformaciones) relativas al escenario.
  function layoutBox(el){
    let x=0,y=0;const w=el.offsetWidth,h=el.offsetHeight;
    for(let n=el;n&&n!==stage;n=n.offsetParent){x+=n.offsetLeft;y+=n.offsetTop}
    return {x,y,w,h};
  }
  // Estado cerrado de una pieza: data-closed="cx cy ancho" en fracciones de la caja del producto cerrado.
  function closedState(part){
    const c=layoutBox(closed),p=layoutBox(part);
    const [cx,cy,cw]=(part.dataset.closed||'.5 .5 .2').split(' ').map(Number);
    return {x:c.x+cx*c.w-(p.x+p.w/2),y:c.y+cy*c.h-(p.y+p.h/2),scale:p.w?cw*c.w/p.w:1,rise:c.h*.5};
  }
  const shellOffset=el=>layoutBox(closed).y-layoutBox(el).y;
  // anime.refresh() interpreta los números sin unidad de x/y como %, así que se devuelven en px.
  const px=n=>n+"px";

  // Líneas guía: del borde de la etiqueta al ancla de la pieza (data-anchor="ax ay" en fracciones de la pieza).
  function layoutLeaders(){
    const v=layoutBox(visual);let drawn=0;
    leaders.forEach(leader=>{
      const id=leader.dataset.part,part=byPart(parts,id),label=byPart(labels,id);
      const card=label?.querySelector('.explode-label-card');
      if(!part||!card||!card.offsetWidth||!part.offsetWidth)return;
      const p=layoutBox(part),c=layoutBox(card),[ax,ay]=(part.dataset.anchor||'.5 .5').split(' ').map(Number);
      const x1=(label.dataset.side==='left'?c.x+c.w:c.x)-v.x,y1=c.y+22-v.y;
      const x2=p.x+ax*p.w-v.x,y2=p.y+ay*p.h-v.y;
      leader.style.left=x1+'px';leader.style.top=y1+'px';
      leader.style.width=Math.hypot(x2-x1,y2-y1)+'px';
      leader.style.transform=`rotate(${Math.atan2(y2-y1,x2-x1)}rad)`;
      drawn++;
    });
    section.classList.toggle('has-leaders',drawn>0);
  }

  function setActive(progress){
    const t=progress*TOTAL;let index=-1;
    STEPS.forEach((start,i)=>{if(t>=start+5)index=i});
    if(index===activeIndex)return;
    activeIndex=index;
    labels.forEach((label,i)=>label.classList.toggle('is-active',i===index));
    parts.forEach((part,i)=>part.classList.toggle('is-active',i===index));
  }

  function build(){
    if(tl||typeof anime==='undefined'||reduce?.matches)return;
    const {createTimeline,onScroll}=anime;
    section.classList.add('is-animated');
    layoutLeaders();
    tl=createTimeline({autoplay:false,defaults:{ease:'inOutSine'},onUpdate:self=>setActive(self.progress)});
    tl.add({duration:TOTAL},0);
    // 1 · Reposo: el render del producto cerrado se funde con las capas apiladas.
    tl.add(closed,{opacity:{from:1,to:0},duration:30,ease:'linear'},15);
    // 2 · La tapa sube y la carcasa inferior baja un poco (la pared frontal la acompaña).
    tl.add(lid,{y:{from:()=>px(shellOffset(lid)),to:0},duration:190,ease:'inOutCubic'},40);
    tl.add(shell,{y:{from:()=>px(shellOffset(bottom)),to:0},duration:300},40);
    parts.forEach(part=>{
      // Suben con la tapa, centradas y tapadas por la pared frontal; después se separan.
      tl.add(part,{opacity:{from:0,to:1},duration:30,ease:'linear'},40);
      tl.add(part,{
        x:{from:()=>px(closedState(part).x),to:()=>px(closedState(part).x)},
        y:{from:()=>px(closedState(part).y),to:()=>px(closedState(part).y-closedState(part).rise)},
        scale:{from:()=>closedState(part).scale,to:()=>closedState(part).scale},
        duration:170,ease:'inOutCubic'
      },55);
      tl.add(part,{x:{to:0,ease:'outCubic'},y:{to:0,ease:'inOutQuad'},scale:{to:1},duration:150},230);
    });
    // 3 · Ya fuera de la carcasa, la copia de la pared frontal deja de hacer falta.
    tl.add(front,{opacity:{from:1,to:0},duration:15,ease:'linear'},232);
    // Los números de las piezas (solo visibles en móvil) aparecen cuando ya están separadas.
    tl.add(qa('.explode-part-num'),{opacity:{from:0,to:1},duration:40,ease:'linear'},330);
    // 4 · Un paso por componente: etiqueta, línea guía y un pequeño realce de la pieza.
    parts.forEach((part,i)=>{
      const id=part.dataset.part,start=STEPS[i];
      const card=byPart(labels,id)?.querySelector('.explode-label-card'),leader=byPart(leaders,id);
      tl.add(part,{scale:[{to:1.06,duration:40},{to:1,duration:60}]},start);
      if(leader){
        tl.add(leader.querySelector('.explode-leader-line'),{scaleX:{from:0,to:1},duration:60,ease:'outCubic'},start);
        tl.add(leader.querySelector('.explode-leader-dot'),{opacity:{from:0,to:1},duration:20,ease:'linear'},start+40);
      }
      if(card)tl.add(card,{opacity:{from:0,to:1},y:{from:14,to:0},duration:50,ease:'outCubic'},start+10);
    });
    // 5 · Con el LED RGB, el logo se enciende (fundido limitado a la zona del logo y resplandor).
    const rgbStart=STEPS[parts.findIndex(p=>p.dataset.part==='rgb')]??STEPS[STEPS.length-1];
    tl.add(q('.lid-on'),{opacity:{from:0,to:1},duration:60,ease:'linear'},rgbStart+10);
    tl.add(q('.explode-glow'),{opacity:{from:0,to:1},duration:70,ease:'linear'},rgbStart+10);
    // Pinta los estados iniciales de todos los hijos y enlaza el progreso al scroll del track.
    tl.init();
    observer=onScroll({target:track,enter:{target:'top',container:'top'},leave:{target:'bottom',container:'bottom'},sync:.6});
    observer.link(tl);
    if(debug)window.aeriqExplode={tl,observer,closedState,layoutBox};
  }

  function destroy(){
    observer?.revert();tl?.revert();
    observer=tl=null;activeIndex=-2;
    section.classList.remove('is-animated');
    labels.forEach(l=>l.classList.remove('is-active'));parts.forEach(p=>p.classList.remove('is-active'));
    layoutLeaders();
  }

  // Al cambiar el tamaño del escenario se recalculan las líneas y los valores dependientes de la maqueta.
  function onResize(){
    const size=`${visual.offsetWidth}x${visual.offsetHeight}`;
    if(size===lastSize)return;
    lastSize=size;
    layoutLeaders();
    if(tl){const time=tl.currentTime;tl.refresh();tl.seek(0,true);tl.seek(time,true)}
  }

  function init(){
    layoutLeaders();
    build();
    lastSize=`${visual.offsetWidth}x${visual.offsetHeight}`;
    if('ResizeObserver' in window)new ResizeObserver(onResize).observe(visual);
    else window.addEventListener('resize',onResize);
    reduce?.addEventListener?.('change',()=>reduce.matches?destroy():build());
    // Las imágenes diferidas pueden llegar después del primer cálculo de las líneas.
    window.addEventListener('load',()=>{lastSize='';onResize()},{once:true});
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init);else init();
})();
