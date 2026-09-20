#!/usr/bin/env node
/* DOM contracts only. Production pointer/scroll/geometry checks are separate. */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const {JSDOM}=require(process.argv[2] || 'jsdom');
const dom=new JSDOM('<!doctype html><body><canvas width="1000" height="600"></canvas></body>',{runScripts:'outside-only',pretendToBeVisual:true});
const w=dom.window,d=w.document,c=d.querySelector('canvas');
d.body.classList.add('tsj-workbench-ui');
const css=d.createElement('style');css.textContent=fs.readFileSync(new URL('../../war/tsj-tray-drawer.css',import.meta.url),'utf8');d.head.appendChild(css);
c.getBoundingClientRect=()=>({x:0,y:112,left:0,top:112,right:1000,bottom:712,width:1000,height:600});
let checks=0; const check=(condition,label)=>{assert.ok(condition,label);checks++;};
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
w.eval(fs.readFileSync(new URL('../../war/tsj-tray-drawer.js',import.meta.url),'utf8'));
const owner={}, second={}; let state={open:false,scroll:0,width:1000,height:172,count:0,dragging:false};
const calls=[];
function update(open,offset) {calls.push({open,offset});state={...state,open,scroll:offset};w.tsjTrayDrawer.present(owner,c,state,update);}
w.tsjTrayDrawer.sync({screen:'WORKBENCH'}); w.tsjTrayDrawer.present(owner,c,state,update);
const root=d.querySelector('.tsj-tray-drawer'),handle=d.querySelector('.tsj-tray-handle'),bar=d.querySelector('.tsj-tray-scroll');
function move(x,y,buttons=0) {w.dispatchEvent(new w.MouseEvent('pointermove',{clientX:x,clientY:y,buttons,bubbles:true}));}
check(!root.hidden&&!root.classList.contains('is-open'),'initially only the edge handle is present');
check(w.getComputedStyle(handle).pointerEvents==='auto','idle drawer handle accepts pointer input');
check(root.style.width==='1000px'&&root.style.top==='688px','edge handle spans the canvas bottom');
move(600,700);
check(state.open&&root.classList.contains('is-open'),'bottom hover opens drawer');
check(handle.getAttribute('aria-expanded')==='true'&&w.tsjTrayDrawer.top===540,'accessible expanded state and meter keepout match');
check(bar.hidden,'empty drawer has no fake scrollbar');
move(600,600); await sleep(220);
check(state.open,'hover inside actual tray persists');
w.dispatchEvent(new w.MouseEvent('pointerup',{clientX:600,clientY:600}));await sleep(220);
check(state.open,'click or drop inside tray does not close it');
move(600,400); await sleep(220);
check(!state.open,'leaving drawer closes after the short exit grace');
move(600,700);state={...state,dragging:true};w.tsjTrayDrawer.present(owner,c,state,update);
check(root.classList.contains('is-dragging')&&w.getComputedStyle(handle).pointerEvents==='none'&&
  w.getComputedStyle(bar).pointerEvents==='none','active drag passes pointer and mouseup through drawer chrome');
move(600,200,1);await sleep(220);
check(state.open,'dragging a physical part out keeps its source drawer visible');
state={...state,dragging:false,count:20,width:3708};w.tsjTrayDrawer.present(owner,c,state,update);
check(!root.classList.contains('is-dragging')&&w.getComputedStyle(handle).pointerEvents==='auto',
  'finishing a drag restores drawer controls');
move(600,600);check(!bar.hidden,'overflow inventory exposes a real horizontal scrollbar');
bar.scrollLeft=500;bar.dispatchEvent(new w.Event('scroll'));
check(state.scroll===500&&state.open,'native scroll updates the projection offset');
check(bar.firstElementChild.style.width==='3708px','scroll content represents every inventory cell');
handle.focus();d.dispatchEvent(new w.KeyboardEvent('keydown',{key:'Escape',bubbles:true,cancelable:true}));
check(!state.open&&handle.getAttribute('aria-expanded')==='false','Escape closes and keeps an accessible focus target');
handle.dispatchEvent(new w.KeyboardEvent('keydown',{key:'ArrowUp',bubbles:true,cancelable:true}));
check(state.open,'keyboard arrow opens the same drawer');
move(600,600);w.tsjWorkbenchOverlayOpen=true;w.tsjTrayDrawer.present(owner,c,state,update);
check(root.hidden&&!state.open&&w.tsjTrayDrawer.top===null,'modal hides drawer and clears meter keepout');
w.tsjWorkbenchOverlayOpen=false;w.tsjTrayDrawer.sync({screen:'MENU'});
check(root.hidden,'menu has no bottom drawer hit layer');
w.tsjTrayDrawer.sync({screen:'WORKBENCH'});
w.tsjTrayDrawer.suspend(second);check(!root.hidden,'foreign owner cannot detach current drawer');
w.tsjTrayDrawer.suspend(owner);check(root.hidden&&w.tsjTrayDrawer.top===null,'exact detach revokes drawer');
const count=calls.length;move(10,700);check(calls.length===count,'detached listeners cannot call retired controller');
dom.window.close();console.log('PASS tray drawer DOM contracts '+checks);
