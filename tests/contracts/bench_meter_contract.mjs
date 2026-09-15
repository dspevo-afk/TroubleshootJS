#!/usr/bin/env node
// DOM contracts only; real compiled-browser/input evidence is separate.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { createRequire } from 'node:module';
const { JSDOM } = createRequire(import.meta.url)(process.argv[2] || 'jsdom');
const dom = new JSDOM('<body><div id="dock-clip"><div class="tsj-workbench-top-dock"><div class="tsj-workbench-toolbar-host"><table class="tsj-meter-panel" data-mode="NONE"><tbody><tr><td><div class="tsj-meter-display">OL</div></td></tr></tbody></table></div></div></div><canvas width="1400" height="700"></canvas></body>', {url:'https://example.invalid', runScripts:'outside-only', pretendToBeVisual:true});
const w = dom.window, d = w.document, canvas = d.querySelector('canvas');
const panel = d.querySelector('table'), host = d.querySelector('.tsj-workbench-toolbar-host');
let checks = 0, captured = null, releases = 0, captureFails = false;
function check(ok, name) { assert.ok(ok, name); checks++; }
function near(actual, expected, name) { check(Math.abs(actual-expected) < 0.002, name+': '+actual+' vs '+expected); }
function rect(x,y,width,height) { return {left:x,top:y,x,y,width,height,right:x+width,bottom:y+height}; }
Object.defineProperties(w, {innerWidth:{value:1400,writable:true},innerHeight:{value:850,writable:true}});
canvas.getBoundingClientRect = () => rect(0,140,canvas.width,canvas.height);
host.getBoundingClientRect = () => rect(0,20,1400,112);
Object.defineProperties(host, {clientLeft:{value:2},clientTop:{value:2}});
host.scrollLeft = 3; host.scrollTop = 4;
Object.defineProperty(panel, 'offsetParent', {get:()=>host});
w.HTMLElement.prototype.setPointerCapture = function(id) { if(captureFails) throw Error('unavailable'); captured=id; };
w.HTMLElement.prototype.hasPointerCapture = id => captured===id;
w.HTMLElement.prototype.releasePointerCapture = id => { check(captured===id,'release exact captured pointer'); captured=null; releases++; };
const calls=[]; w.tsjProduct = {action(...args) { calls.push(args); return ''; }};
w.eval(fs.readFileSync(new URL('../../war/tsj-bench-instruments.js',import.meta.url),'utf8'));
const api = w.tsjBenchInstruments, owner = {}, other = {};
const snapshot = {token:1,hasBoard:true,screen:'WORKBENCH',ready:true,completed:false};
let view = {scale:.8,x:360,y:40,area:{x:0,y:0,width:1400,height:700},home:{x:-284,y:60,width:252,height:454}};
api.mount(snapshot); api.project(owner,canvas,view);
const grip=d.querySelector('.tsj-meter-grip'), display=d.querySelector('.tsj-meter-display');
const position=()=>({x:Number(panel.dataset.benchWorldX),y:Number(panel.dataset.benchWorldY)});
function box() { const s=Number(panel.style.getPropertyValue('--meter-scale')); return rect(parseFloat(panel.style.left)-1,parseFloat(panel.style.top)+18,252*s,454*s); }
function point(type,target,x,y,id=1,button=0,isPrimary=true) {
  const e=new w.MouseEvent(type,{bubbles:true,cancelable:true,clientX:x,clientY:y,button});
  Object.defineProperties(e,{pointerId:{value:id},isPrimary:{value:isPrimary}}); target.dispatchEvent(e);
}
function down(id=1) { const r=box(); point('pointerdown',grip,r.x+45,r.y+20,id); return {x:r.x+45,y:r.y+20}; }
function key(name,shiftKey=false) { grip.dispatchEvent(new w.KeyboardEvent('keydown',{key:name,shiftKey,bubbles:true,cancelable:true})); }
function visible() { const r=box(); check(r.left>=11.998 && r.top>=151.998 && r.right<=Math.min(w.innerWidth-12,canvas.width-16)+.002 && r.bottom<=Math.min(w.innerHeight-14,140+canvas.height-18)+.002,'entire meter including reserved contact margin stays on visible bench'); }
check(d.querySelector('#dock-clip').classList.contains('tsj-bench-dock-layer'),'native GWT clip wrapper explicitly releases meter paint without reparenting');
near(position().x,-284,'world home X'); near(position().y,60,'world home Y');
near(box().left,132.8,'initial canvas projection accounts for offset parent');
check(box().right<360,'meter initially left of PCB'); visible();
const initial=position(), before=box();
view={...view,x:410,y:50}; api.project(owner,canvas,view);
assert.deepEqual(position(),initial); checks++;
near(box().x-before.x,50,'camera pan carries meter, not HUD'); near(box().y-before.y,10,'vertical world projection');
let start=down(); check(captured===1,'header captures pointer');
point('pointermove',d,start.x+80,start.y+40,2); assert.deepEqual(position(),initial); checks++;
point('pointerdown',grip,start.x,start.y,2,0,false); check(captured===1,'second pointer cannot steal drag');
point('pointermove',d,start.x+80,start.y+40);
near(position().x,initial.x+100,'inverse projected drag X'); near(position().y,initial.y+50,'inverse projected drag Y');
point('pointerup',d,start.x+80,start.y+40); check(captured===null && !grip.classList.contains('is-dragging'),'release ends drag');
const moved=position(); point('pointerdown',display,300,300); point('pointermove',d,600,500); point('pointerup',d,600,500);
assert.deepEqual(position(),moved); checks++; check(calls.length===0,'moving meter never invokes electrical actions');
api.mount({...snapshot,token:2}); assert.deepEqual(position(),moved); checks++;
api.mount({...snapshot,screen:'MENU'}); check(panel.dataset.benchVisible==='false','menu hides bench object');
api.mount(snapshot); api.project(owner,canvas,view); assert.deepEqual(position(),moved); checks++;
grip.focus(); key('ArrowRight'); near(position().x,moved.x+25,'keyboard movement inverse projects 20 CSS px');
key('ArrowDown',true); near(position().y,moved.y+6.25,'fine keyboard movement is 5 CSS px');
key('Home'); near(position().x,-284,'Home restores left-side home');

for (const eventName of ['pointercancel','lostpointercapture','blur']) {
  start=down(); check(grip.classList.contains('is-dragging'),'drag starts before '+eventName);
  if(eventName==='blur') w.dispatchEvent(new w.Event('blur'));
  else point(eventName,eventName==='lostpointercapture'?grip:d,start.x,start.y);
  check(!grip.classList.contains('is-dragging') && captured===null,eventName+' clears capture and drag');
  const saved=position(); point('pointermove',d,start.x+100,start.y+100); assert.deepEqual(position(),saved); checks++;
}
start=down(); point('pointerup',d,start.x,start.y,99); check(captured===1,'unrelated pointer release cannot cancel drag');
key('Escape'); check(captured===null,'Escape cancels drag');
start=down(); api.mount({...snapshot,screen:'RETEST'}); check(captured===null,'retest cancels drag'); api.mount(snapshot);
start=down(); w.dispatchEvent(new w.Event('resize')); check(captured===null,'resize cancels drag');
captureFails=true; start=down(); point('pointermove',d,start.x+16,start.y+8); point('pointerup',d,start.x+16,start.y+8);
near(position().x,-264,'window fallback retains drag without pointer capture'); captureFails=false;
for(const [x,y] of [[-100000,-100000],[100000,100000],[-100000,100000],[100000,-100000]]) {
  start=down(); point('pointermove',d,x,y); point('pointerup',d,x,y); visible();
}
key('Home'); const home=position();
api.project(other,canvas,view); near(position().x,-284,'new board starts at left-side home');
key('ArrowRight'); const otherPosition=position(); api.suspend(owner);
check(panel.dataset.benchVisible==='true','stale owner cannot hide current meter');
api.project(owner,canvas,view); assert.deepEqual(position(),home); checks++;
api.project(other,canvas,view); assert.deepEqual(position(),otherPosition); checks++;
start=down(); api.suspend(other); check(captured===null && panel.dataset.benchVisible==='false','owner suspension cancels and hides');
api.project(other,canvas,view); assert.deepEqual(position(),otherPosition); checks++;
w.innerWidth=640; w.innerHeight=480; canvas.width=640; canvas.height=340;
view={...view,scale:8,x:-3000,y:-2000,area:{x:0,y:0,width:640,height:340}};
api.project(other,canvas,view); visible(); check(Number(panel.style.getPropertyValue('--meter-scale'))<1,'oversized zoom shrinks to visible bench, not offscreen');
view={...view,x:1e7,y:-1e7}; api.project(other,canvas,view); visible();
check(Number.isFinite(position().x) && Number.isFinite(position().y),'extreme camera recovery keeps finite world state');
const electricalPosition=position(); d.querySelector('.tsj-meter-off').click();
check(calls.length===1 && calls[0][2]==='meter' && calls[0][3]==='NONE','real meter action bridge retained');
d.querySelector('.tsj-meter-light').click(); check(panel.classList.contains('is-backlit'),'backlight still operates');
assert.deepEqual(position(),electricalPosition); checks++;
const css=fs.readFileSync(new URL('../../war/tsj-bench-instruments.css',import.meta.url),'utf8');
check(css.includes('position: absolute') && !css.includes('position: fixed'),'meter uses canvas-projected absolute layout, not fixed HUD');
check(css.includes('cursor: grab') && css.includes('cursor: grabbing') && css.includes('touch-action: none'),'header drag affordance and touch ownership retained');
check(releases>=10,'capture cleanup exercised repeatedly');
dom.window.close(); console.log('bench meter contracts '+checks+' PASS');
