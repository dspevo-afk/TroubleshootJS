#!/usr/bin/env node
/* Focused UI adapter contracts, using jsdom 26.x supplied externally:
 * node tests/contracts/u04_ui_contract.mjs <path-to-jsdom/lib/api.js>
 * DOM/layout shims do NOT certify actual browser focus, pointer or GWT behavior.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { JSDOM } = require(process.argv[2] || 'jsdom');
const source = fs.readFileSync(new URL('../../war/tsj-workbench-ui.js', import.meta.url), 'utf8');
let checks = 0;
function check(value, message) { assert.ok(value, message); checks++; }
const pause = () => new Promise(resolve => setTimeout(resolve, 12));
const initial = () => ({ token: 1, screen: 'MENU', message: '', notice: '', epoch: 'tsj-alpha/1', build: 'test-build', hasBoard: false,
  families: [{ id: 'led', name: 'Indicator board', profile: 'EASY' }, { id: 'control', name: 'Control board', profile: 'MEDIUM' }] });
async function fixture({ storage, deny = false, noBridge = false, trackFocusListeners = false } = {}) {
  const dom = new JSDOM('<!doctype html><body><main aria-hidden="false"><table id="native-toolbar"><tbody><tr><td><table class="tsj-meter-panel"><tbody><tr><td>Multimeter</td></tr></tbody></table></td></tr></tbody></table></main><aside inert="kept" aria-hidden="true"></aside></body>', { url: 'https://example.invalid/circuitjs.html', runScripts: 'outside-only', pretendToBeVisual: true });
  const w = dom.window, d = w.document;
  // jsdom has no layout. Visibility below is a unit-test approximation only.
  w.HTMLElement.prototype.getClientRects = function () {
    // Match the real Chromium canary: closed-details fields can retain boxes.
    return this.closest('[hidden]') ? [] : [{}];
  };
  if (storage !== undefined) w.localStorage.setItem('tsj.presentation.v1', storage);
  if (deny) Object.defineProperty(w, 'localStorage', { get() { throw new Error('storage denied'); } });
  let state = initial(), view = 0, snapshots = 0, available = true;
  const calls = [], closed = [];
  const focusListenersAdded = [], focusListenersRemoved = [];
  if (trackFocusListeners) {
    const add = d.addEventListener.bind(d), remove = d.removeEventListener.bind(d);
    d.addEventListener = function (name, callback, options) {
      if (name === 'focusin') focusListenersAdded.push(callback);
      return add(name, callback, options);
    };
    d.removeEventListener = function (name, callback, options) {
      if (name === 'focusin') focusListenersRemoved.push(callback);
      return remove(name, callback, options);
    };
  }
  const api = {
    available() { return available; },
    snapshot() { snapshots++; return structuredClone(state); },
    openView() { return ++view; },
    closeView(token) { closed.push(token); if (token === view) view++; },
    action(token, lease, name, ...args) {
      const accepted = token === state.token && (name !== 'acquire' || lease === view);
      calls.push({ token, lease, name, args, accepted });
      return accepted ? (name === 'acquire' ? 'Part added to Parts Tray.' : '') : 'This control belongs to an earlier session or closed view.';
    }
  };
  if (!noBridge) w.tsjProduct = api;
  w.eval(source); await pause();
  return { w, d, calls, closed, api, focusListenersAdded, focusListenersRemoved, snapshots: () => snapshots, state: () => state,
    setAvailable(value) { available = value; },
    async update(change) { state = { ...state, ...change }; w.tsjProductRefresh(); await pause(); },
    async flush() { await pause(); }, close() { w.close(); } };
}
function byText(d, text) { const result = [...d.querySelectorAll('button')].find(node => node.textContent === text && !node.closest('[hidden], [inert]')); assert.ok(result, 'Missing button: ' + text); return result; }
function click(f, text) { byText(f.d, text).click(); }
function key(f, name, shiftKey = false) { f.d.activeElement.dispatchEvent(new f.w.KeyboardEvent('keydown', { key: name, shiftKey, bubbles: true, cancelable: true })); }
function literalRect(node, left, right, top = 0, bottom = 20) {
  node.getBoundingClientRect = () => ({ left, right, top, bottom, width: right - left, height: bottom - top });
}
function literalScrollport(node, left, width, scrollWidth, initial = 0) {
  literalRect(node, left, left + width);
  let scrollLeft = initial;
  Object.defineProperties(node, {
    clientLeft: { configurable: true, value: 0 },
    clientWidth: { configurable: true, value: width },
    scrollWidth: { configurable: true, value: scrollWidth },
    scrollLeft: { configurable: true, get: () => scrollLeft, set: value => { scrollLeft = value; } }
  });
  return { value: () => scrollLeft, set: value => { scrollLeft = value; } };
}
function focusin(f, target, focusVisible) {
  const matches = target.matches.bind(target);
  target.matches = selector => selector === ':focus-visible' ? focusVisible : matches(selector);
  target.dispatchEvent(new f.w.FocusEvent('focusin', { bubbles: true }));
}

const f = await fixture();
check(f.w.tsjWorkbenchOverlayOpen, 'initial menu blocks board');
check(f.d.querySelector('main').hasAttribute('inert'), 'actual inert attribute applied to background');
check(f.d.querySelector('[role="dialog"]').contains(f.d.activeElement), 'focus enters dialog');
check(f.d.querySelector('[role="dialog"]').getAttribute('aria-labelledby') === 'tsj-product-heading', 'dialog is labelled');
check(f.d.querySelector('#native-toolbar').classList.contains('tsj-workbench-toolbar-host') && !f.d.querySelector('.tsj-meter-panel').classList.contains('tsj-workbench-toolbar-host') && f.d.querySelector('.tsj-workbench-shell').nextElementSibling === f.d.querySelector('.tsj-meter-panel'), 'native table shell mounts before the meter and styles the containing top dock without replacing its widgets');
check([...f.d.querySelectorAll('.tsj-workbench-shell svg')].length === 6 && [...f.d.querySelectorAll('.tsj-workbench-shell svg')].every(icon => icon.getAttribute('aria-hidden') === 'true' && icon.getAttribute('focusable') === 'false' && !icon.querySelector('text, title, a, use, image')), 'product identity and all five navigation glyphs are decorative and add no focus stops or external resources');
check([...f.d.querySelectorAll('.tsj-ui-strip button')].map(button => button.textContent).join('|') === 'Main menu|Shop|Resources|Settings|Run customer retest', 'decorative navigation retains all existing visible action names and order');
check([...f.d.querySelectorAll('.tsj-ui-strip button')].every(button => button.getAttribute('aria-label') === button.textContent && button.title === button.textContent && button.querySelector('.tsj-button-label')), 'all navigation actions retain full accessible names and tooltips when compact CSS hides their visual labels');
check([...f.d.querySelectorAll('option')].filter(n => n.disabled).map(n => n.value).join(',') === 'HARD,PSYCHOTIC', 'advanced profiles unavailable');
// Session controls now precede the board setup form. Start from its first
// visible keyboard control so this still tests the actual reverse-wrap edge.
const menuFirst = byText(f.d, 'Load session'), disclosure = f.d.querySelector('.tsj-product-identity');
menuFirst.focus(); key(f, 'Tab', true);
check(f.d.activeElement === disclosure.querySelector('summary'), 'collapsed replay fields cannot trap reverse menu focus');
key(f, 'Tab'); check(f.d.activeElement === menuFirst, 'collapsed disclosure summary wraps to first menu control');
disclosure.open = true; menuFirst.focus(); key(f, 'Tab', true);
check(f.d.activeElement === disclosure.querySelectorAll('textarea')[1], 'expanded replay identity remains keyboard accessible');
key(f, 'Tab'); check(f.d.activeElement === menuFirst, 'expanded replay identity wraps within menu');
disclosure.open = false;
const profile = f.d.querySelector('select'); profile.value = 'MEDIUM'; profile.dispatchEvent(new f.w.Event('change'));
check(f.d.querySelectorAll('select')[1].value === 'control', 'family options follow public profile catalog');
click(f, 'New board');
check(f.calls.at(-1).name === 'random' && f.calls.at(-1).args[0] === 'control' && f.calls.at(-1).args[2] === 'MEDIUM', 'normal New board uses Java random admission, never exact launch');
const entropyText = f.calls.at(-1).args[1];
check(typeof entropyText === 'string' && /^-?(0|[1-9]\d*)$/.test(entropyText) && String(BigInt(entropyText)) === entropyText && BigInt(entropyText) >= -(1n << 63n) && BigInt(entropyText) < (1n << 63n), 'random admission receives canonical exact signed 64-bit decimal entropy');
const seed = f.d.querySelector('input[type=text]'); seed.value = '-9223372036854775808'; seed.focus(); seed.setSelectionRange(1, 6);
f.w.tsjProductRefresh(); f.w.tsjProductRefresh(); await f.flush();
check(seed === f.d.querySelector('input[type=text]') && seed.value === '-9223372036854775808' && seed.selectionStart === 1, 'unchanged screen preserves field and selection');
f.d.querySelector('form').dispatchEvent(new f.w.Event('submit', { bubbles: true, cancelable: true }));
check(f.calls.at(-1).name === 'launch' && f.calls.at(-1).args[1] === '-9223372036854775808', 'deliberate full signed-long seed uses exact launch verbatim');
check(f.calls.at(-1).args[0] === 'control' && f.calls.at(-1).args[2] === 'MEDIUM', 'typed family/profile are passed');
const replay = f.d.querySelectorAll('form textarea')[0]; replay.value = 'unknown/epoch|bad-family|9223372036854775808';
f.d.querySelectorAll('form')[1].dispatchEvent(new f.w.Event('submit', { bubbles: true, cancelable: true }));
check(f.calls.at(-1).name === 'replay' && f.calls.at(-1).args[0] === replay.value, 'replay delegated unchanged for owner validation');
await f.update({ screen: 'PREPARING', token: 2, message: 'Preparing' });
check(!f.d.querySelector('progress').hasAttribute('value') && !f.d.querySelector('.tsj-generation-progress').textContent.includes('0%'), 'missing compiled progress is indeterminate, not a false zero');
const progressNode=f.d.querySelector('progress');
await f.update({progress:{percent:16,phase:2,phases:6,label:'Build and test healthy circuit',units:2,elapsedMs:1200,lastUpdateAgeMs:0,paused:false}});
check(progressNode.value===16 && f.d.querySelector('.tsj-generation-progress').textContent.includes('16%'), 'reported completed work updates the actual progress bar');
await f.update({progress:{percent:58,phase:4,phases:6,label:'Verify measurements and repairs',units:50,elapsedMs:4000,lastUpdateAgeMs:0,paused:false}});
check(f.d.querySelector('progress')===progressNode && progressNode.value===58, 'progress updates without replacing controls or restarting the screen');
await f.update({progress:{percent:58,phase:4,phases:6,label:'Verify measurements and repairs',units:50,elapsedMs:9000,lastUpdateAgeMs:5000,paused:true}});
check(progressNode.value===58 && f.d.querySelector('.tsj-generation-progress').textContent.includes('Preparation paused'), 'background suspension is explicit and retains completed progress');

key(f, 'Escape'); check(f.w.tsjWorkbenchOverlayOpen && f.d.querySelector('main').hasAttribute('inert'), 'Escape cannot expose board during preparation');
click(f, 'Cancel preparation'); check(f.calls.at(-1).name === 'cancel' && f.calls.at(-1).token === 2, 'explicit cancel uses current session');
const beforeKey = f.calls.length; byText(f.d, 'Cancel preparation').focus(); key(f, 'Enter');
check(f.calls.length === beforeKey + 1 && f.calls.at(-1).name === 'cancel', 'Enter activates focused dialog button once');
await f.update({ screen: 'TICKET', token: 3, hasBoard: true, ready: true, complaint: '<img src=x onerror=alert(1)> complaint', retestInstruction: 'Check customer output', replay: 'tsj-alpha/1|led|0|EASY' });
check(f.d.querySelectorAll('img').length === 0 && f.d.querySelector('.tsj-product-content').textContent.includes('<img'), 'public text cannot inject HTML');
click(f, 'Accept ticket and start'); check(f.calls.at(-1).name === 'resume', 'ticket transitions through owner action');
const resistorCategory = 'RESISTOR';
// Exercise the largest current resistor catalog: 73 recipes in each of two fits.
const resistorEntries = ['Medium', 'Wide'].flatMap((fit, fitIndex) => Array.from({ length: 73 }, (_, index) => ({
  id: 'spec-' + (fitIndex * 73 + index), label: (index === 0 ? '330' : String(index + 100)) + ' Ohm +/-5% · ' + fit + ' lead spacing'
})));
const catalogs = [{ id: resistorCategory, title: 'Resistors', entries: resistorEntries, looseCount: 0 },
  ...[['CAPACITOR', 'Capacitors', '10 uF, 25 V'], ['DIODE', 'Diodes', '1N4148'], ['LED', 'LEDs', 'Red LED'],
    ['NPN_TRANSISTOR', 'NPN transistors', 'General purpose NPN'], ['NMOS_TRANSISTOR', 'NMOS transistors', 'Logic level NMOS'],
    ['RELAY', 'Relays', '12 V coil']].map(([id, title, label]) => ({ id, title, entries: [{ id: 'spec-0', label }], looseCount: 0 }))];
const benchMounts = [];
f.w.tsjBenchInstruments = { mount(snapshot) {
  benchMounts.push({ screen: snapshot.screen, overlayOpen: !!f.w.tsjWorkbenchOverlayOpen });
} };
const cards = () => [...f.d.querySelectorAll('.tsj-store-card')];
const visibleCards = () => cards().filter(card => !card.hidden);
const searchFor = value => { const search = f.d.querySelector('input[type=search]'); search.value = value; search.dispatchEvent(new f.w.Event('input')); return search; };
await f.update({ screen: 'WORKBENCH', token: 4, isolated: true, catalogs, message: 'Customer retest did not pass. Continue troubleshooting.', notice: 'Previous ticket notice.', privateOriginal: 'PRIVATE-ORIGINAL', fault: 'PRIVATE-FAULT' });
check(!f.w.tsjWorkbenchOverlayOpen && !f.d.querySelector('main').hasAttribute('inert'), 'workbench releases background');
check(benchMounts.length >= 2 && benchMounts.at(-2).screen === 'WORKBENCH' && benchMounts.at(-2).overlayOpen &&
  benchMounts.at(-1).screen === 'WORKBENCH' && !benchMounts.at(-1).overlayOpen,
  'ticket-to-workbench transition re-syncs bench controls after the overlay gate opens');
check(f.d.querySelector('main').getAttribute('aria-hidden') === 'false', 'preexisting aria-hidden restored exactly');
check(f.d.querySelector('aside').getAttribute('inert') === 'kept' && f.d.querySelector('aside').getAttribute('aria-hidden') === 'true', 'preexisting inert/aria attributes preserved');
click(f, 'Shop');
const shopStatus = () => f.d.querySelector('[role=dialog] > [role=status]');
check(shopStatus().textContent === '' && !f.d.querySelector('[role=dialog]').textContent.includes('Customer retest did not pass') && !f.d.querySelector('[role=dialog]').textContent.includes('Previous ticket notice'), 'opening Shop leaves status empty instead of presenting stale retest or ticket feedback');
check(f.d.querySelector('.tsj-store-address').textContent === 'store.copperline.example/components' && f.d.querySelector('.tsj-store-supplier h3').textContent === 'Copperline Supply Co.', 'local website address and original supplier identity restored');
check(f.d.querySelector('[role=dialog]').classList.contains('tsj-store-dialog'), 'storefront uses its own spacious dialog presentation');
check(visibleCards().length === 152 && byText(f.d, 'All parts').getAttribute('aria-pressed') === 'true', 'All parts initially displays every entry, including all 146 resistor recipe/fit choices');
check(cards().map(card => card.querySelector('h4').textContent).join('|') === catalogs.flatMap(catalog => catalog.entries.map(entry => entry.label)).join('|'), 'all card specifications retain exact public labels and canonical catalog order');
check(cards().every(card => { const art = card.querySelector('svg'); return art && art.getAttribute('aria-hidden') === 'true' && art.getAttribute('focusable') === 'false' && !art.querySelector('text, image, use, a'); }), 'all supported types have decorative local component art without nameplates, external assets or extra focus stops');
check(new Set(catalogs.map(catalog => cards().find(card => card.querySelector('.tsj-store-type').textContent === catalog.title).querySelector('svg').innerHTML)).size === 7, 'each component category has a distinct recognizable type silhouette');
check(f.d.querySelectorAll('.tsj-store select, .tsj-store a, .tsj-store iframe, .tsj-store img').length === 0 && !/RLOAD|Channel A|PRIVATE-|Cart:|\$/.test(f.d.querySelector('.tsj-store').textContent), 'Shop has no destination dropdown, private state, invented economy or external site dependency');
for (const catalog of catalogs) {
  click(f, catalog.title);
  check(visibleCards().length === catalog.entries.length && visibleCards().every(card => card.querySelector('.tsj-store-type').textContent === catalog.title), 'category browsing shows every ' + catalog.title + ' entry');
  visibleCards()[0].querySelector('button').click();
  check(f.calls.at(-1).accepted && f.calls.at(-1).args.join('|') === catalog.id + '|' + catalog.entries[0].id + '|', catalog.title + ' purchase uses its exact public category/specification even when another type reuses the specification handle');
}
click(f, 'All parts'); const search = searchFor('330'); search.focus();
check(visibleCards().length === 2 && visibleCards().some(card => card.textContent.includes('Medium lead spacing')) && visibleCards().some(card => card.textContent.includes('Wide lead spacing')), 'search finds both physical fits without hiding an available recipe');
const mediumAcquire = visibleCards()[0].querySelector('button'), oldAcquire = visibleCards()[1].querySelector('button');
const content = f.d.querySelector('.tsj-product-content'); content.scrollTop = 210;
await f.update({ catalogs: f.state().catalogs.map((catalog, index) => ({ ...catalog, looseCount: index === 0 ? 2 : index === 1 ? 3 : 0 })) });
check(search === f.d.querySelector('input[type=search]') && search.value === '330' && f.d.activeElement === search && content.scrollTop === 210, 'real count refresh preserves search, focus and scroll position');
check(visibleCards().length === 2 && visibleCards()[1].querySelector('button') === oldAcquire, 'count-only refresh preserves filtered cards and their live callbacks');
check(f.d.querySelector('.tsj-store-tray').textContent === 'Parts Tray · 5 loose parts', 'tray total is derived from all actual inventory categories');
oldAcquire.click(); check(f.calls.at(-1).name === 'acquire' && f.calls.at(-1).args.join('|') === 'RESISTOR|spec-73|' && f.calls.at(-1).accepted, 'wide fit card sends exact current type/spec and lease with no destination');
mediumAcquire.click(); check(f.calls.at(-1).args.join('|') === 'RESISTOR|spec-0|' && f.calls.at(-1).accepted, 'same nominal value in a different physical fit sends its distinct specification');
check(shopStatus().textContent === 'Part added to Parts Tray.' && shopStatus().getAttribute('aria-live') === 'polite', 'purchase success is announced immediately from the acquisition action response');
await f.update({ notice: 'Earlier unrelated board notice.' });
check(shopStatus().textContent === 'Part added to Parts Tray.' && f.d.querySelector('.tsj-store-tray').textContent === 'Parts Tray · 5 loose parts', 'snapshot refresh retains relevant action feedback and never invents inventory increments');
searchFor('WIDE 330'); check(visibleCards().length === 1 && visibleCards()[0].textContent.includes('Wide lead spacing'), 'case-insensitive search combines type/specification terms');
searchFor('not-a-component'); check(visibleCards().length === 0 && !f.d.querySelector('.tsj-store-empty').hidden, 'empty search has a visible recovery instruction');
click(f, 'Clear search'); check(visibleCards().length === 152 && f.d.activeElement === search, 'clear search restores all cards and returns focus to search');
click(f, 'Resistors'); searchFor('330'); search.focus();
await f.update({ catalogs: f.state().catalogs.map((catalog, index) => index === 0 ? { ...catalog, title: 'Axial resistors' } : catalog) });
check(visibleCards().length === 2 && visibleCards().every(card => card.querySelector('.tsj-store-type').textContent === 'Axial resistors') && search.value === '330' && f.d.activeElement === search && byText(f.d, 'Axial resistors').getAttribute('aria-pressed') === 'true', 'catalog change updates public titles while preserving search and category');
const previousAcquire = f.calls.at(-1), callsBeforeDetachedAcquire = f.calls.length;
oldAcquire.click(); check(!oldAcquire.isConnected && f.calls.length === callsBeforeDetachedAcquire && f.calls.at(-1) === previousAcquire && previousAcquire.accepted,
  'removed catalog cards cannot issue actions through detached controls or replace the preceding accepted result');
content.append(oldAcquire); oldAcquire.click();
check(!f.calls.at(-1).accepted, 'changed catalog revokes callbacks holding earlier ephemeral specification handles');
oldAcquire.remove();
check(shopStatus().textContent === 'This control belongs to an earlier session or closed view.' && shopStatus().getAttribute('aria-live') === 'polite', 'rejected acquisition announces its actual action error');
const closingAcquire = byText(f.d, 'Add to Parts Tray');
click(f, 'Close'); check(!f.d.querySelector('[role=dialog]').classList.contains('tsj-store-dialog'), 'closing Shop removes its presentation class');
click(f, 'Shop'); check(shopStatus().textContent === '', 'reopening Shop clears old acquisition feedback without restoring prior board notices');
closingAcquire.click(); check(!f.calls.at(-1).accepted, 'closed/reopened Shop revokes retained acquisition callback');
check(visibleCards().length === 152 && f.d.querySelector('input[type=search]').value === '', 'reopening Shop starts with the full catalog');
await f.update({ isolated: false }); check(cards().every(card => card.querySelector('button').disabled), 'powered board blocks all acquisition while retaining catalog browsing');
await f.update({ isolated: true, ready: false }); check(cards().every(card => card.querySelector('button').disabled), 'unsettled board blocks all acquisition');
await f.update({ ready: true, completed: true });
check(cards().every(card => !card.querySelector('button').disabled),
  'completed board permits acquisition while isolated and ready');
await f.update({ completed: false });
await f.update({ ready: true });
searchFor('330');
const focusNodes = [...f.d.querySelector('[role=dialog]').querySelectorAll('button, input, select')].filter(n => !n.disabled && !n.closest('[hidden]'));
focusNodes[0].focus(); key(f, 'Tab', true); check(f.d.activeElement === focusNodes.at(-1), 'Shift-Tab wraps within dialog');
key(f, 'Tab'); check(f.d.activeElement === focusNodes[0], 'Tab wraps within dialog');
f.w.dispatchEvent(new f.w.Event('blur')); check(!f.w.tsjWorkbenchOverlayOpen, 'window blur closes Shop and releases its lease');
click(f, 'Shop'); const previousOwnerButton = byText(f.d, 'Add to Parts Tray');
await f.update({ token: 5, screen: 'MENU' }); previousOwnerButton.click(); check(!f.calls.at(-1).accepted, 'owner transition rejects old callback');
click(f, 'Resources'); check(f.d.querySelector('.tsj-product-reference').textContent.includes('Silver'), 'generic resistor reference present');
check(f.d.querySelector('.tsj-product-content').textContent.includes('Fit bench') && !/Fit selection|Fit board/.test(f.d.querySelector('.tsj-product-content').textContent), 'navigation reference follows the current Fit bench action without obsolete per-selection guidance');
check(shopStatus().textContent === 'Earlier unrelated board notice.', 'other auxiliary screens retain their existing snapshot notice behavior');
key(f, 'Escape'); check(f.w.tsjWorkbenchOverlayOpen && f.d.querySelector('#tsj-product-heading').textContent.includes('Desktop alpha'), 'closing reference returns to blocking menu');
const draftSeed = f.d.querySelector('input[type=text]'); draftSeed.value = '9223372036854775807';
const resourceTrigger = byText(f.d, 'Resources'); resourceTrigger.focus(); resourceTrigger.click(); key(f, 'Escape');
check(f.d.activeElement === resourceTrigger && f.d.querySelector('input[type=text]') === draftSeed && draftSeed.value === '9223372036854775807', 'auxiliary close preserves menu draft and exact return-focus control');
click(f, 'Settings'); const checkboxes = f.d.querySelectorAll('input[type=checkbox]'); checkboxes[0].click();
check([...checkboxes].map(input => f.d.getElementById(input.getAttribute('aria-labelledby')).textContent).join('|') === 'Higher contrast interface|Larger interface text|Reduce interface motion' && [...checkboxes].every(input => f.d.getElementById(input.getAttribute('aria-describedby')).textContent.length > 0), 'settings keep their accessible names and expose the new explanatory text as descriptions');
const stored = JSON.parse(f.w.localStorage.getItem('tsj.presentation.v1'));
check(stored.version === 1 && stored.highContrast === true && Object.keys(stored).length === 4, 'settings persist only versioned validated presentation fields');
check(f.d.body.classList.contains('tsj-high-contrast'), 'presentation setting applied to HTML');
check(!f.calls.some(call => /physics|tolerance|solver|voltage|settings/.test(call.name)), 'settings never call electrical bridge actions');
await f.flush(); const count = f.snapshots(); await new Promise(resolve => setTimeout(resolve, 45)); check(f.snapshots() === count, 'no perpetual snapshot/rebuild timer after queued work drains');
await f.update({ token: 6, screen: 'WORKBENCH', ready: true, completed: true });
check(byText(f.d, 'Run customer retest').disabled && !byText(f.d, 'Shop').disabled,
  'completed job keeps retest terminal while the returned board remains usable');
await f.update({ completed: false, catalogs: [] }); click(f, 'Shop');
check(cards().length === 0 && f.d.querySelector('.tsj-store-grid').textContent.includes('no available components') && f.d.querySelector('.tsj-store-tray').textContent === 'Parts Tray · 0 loose parts', 'an empty live catalog has no invented products or inventory');
await f.update({ catalogs: [{ id: 'CAPACITOR', title: 'Capacitors', entries: [{ id: 'spec-live', label: '<img src=x onerror=alert(1)> 10 uF' }], looseCount: 1 }] });
check(visibleCards().length === 1 && visibleCards()[0].querySelector('h4').textContent.includes('<img') && f.d.querySelectorAll('.tsj-store img').length === 0, 'a newly available catalog renders its public label as text without HTML injection');
visibleCards()[0].querySelector('button').click();
check(f.calls.at(-1).accepted && f.calls.at(-1).args.join('|') === 'CAPACITOR|spec-live|', 'newly available card uses the current catalog lease');
f.close();

// Native popover visibility/focus is qualified in the real production browser.
// These DOM checks prove the adapter keeps GWT widgets/owners and closes tools
// at modal/session boundaries; the small spies do not simulate browser input.
const top = await fixture();
await top.update({ screen: 'WORKBENCH', token: 2, hasBoard: true, ready: true });
const nativeHost = top.d.querySelector('#native-toolbar'), nativeLabels = ['Bench power', 'Board view', 'Service ticket', 'Component', 'Parts Tray'];
let nativeActions = 0;
const originalPanels = nativeLabels.map(label => {
  const cell = nativeHost.tBodies[0].insertRow().insertCell();
  const panel = top.d.createElement('table'); panel.className = 'tsj-component-panel'; panel.setAttribute('aria-label', label);
  const nativeControl = top.d.createElement('button'); nativeControl.textContent = 'Existing native action';
  nativeControl.addEventListener('click', () => nativeActions++);
  panel.createTBody().insertRow().insertCell().append(nativeControl); cell.append(panel);
  return { panel, cell, nativeControl };
});
await top.flush();
const triggers = () => [...top.d.querySelectorAll('.tsj-tool-trigger')];
check(triggers().length === 5 && triggers().map(button => button.getAttribute('aria-label')).join('|') === nativeLabels.join('|'), 'top strip exposes each supplied public native tool once in native order');
check(originalPanels.every(({panel,cell,nativeControl}) => panel.parentNode === cell && panel.contains(nativeControl) && panel.getAttribute('popover') === 'auto' && panel.getAttribute('role') === 'dialog' && cell.querySelector('.tsj-tool-trigger').getAttribute('popovertarget') === panel.id), 'native popovers retain original GWT parent cells, exact widgets and declarative invoker relationships');
check(triggers().every(button => button.title === button.getAttribute('aria-label') && button.getAttribute('aria-haspopup') === 'dialog' && button.querySelector('svg').getAttribute('aria-hidden') === 'true' && button.querySelector('.tsj-button-label')), 'compact tool icons preserve full public names, tooltips and dialog semantics independently of their abbreviated visual labels');
originalPanels[0].nativeControl.click();
check(nativeActions === 1 && top.calls.length === 0, 'the existing native handler remains the only action owner after top-tool presentation');
const componentTool = originalPanels[3], componentTrigger = componentTool.cell.querySelector('.tsj-tool-trigger');
componentTool.panel.style.display = 'none'; await top.flush();
check(componentTrigger.hidden && componentTool.cell.parentNode.classList.contains('tsj-toolbar-empty'), 'Java-hidden selection panel has no visible top trigger or empty layout row');
componentTool.panel.style.display = ''; await top.flush();
check(!componentTrigger.hidden && !componentTool.cell.parentNode.classList.contains('tsj-toolbar-empty') && componentTool.cell.querySelector('.tsj-tool-trigger') === componentTrigger, 'Java-visible selection reuses its same trigger and original widget');
triggers()[0].click();
check(top.calls.length === 0 && originalPanels[0].panel.style.getPropertyValue('--tsj-tool-left') !== '', 'tool invocation only positions the real DOM panel and never issues an electrical or session action');
const nativeOpen = new Set(), closedNative = [];
originalPanels.forEach(({panel}) => {
  const originalMatches = panel.matches.bind(panel);
  panel.matches = selector => selector === ':popover-open' ? nativeOpen.has(panel) : originalMatches(selector);
  panel.hidePopover = () => { nativeOpen.delete(panel); closedNative.push(panel); };
});
nativeOpen.add(originalPanels[0].panel); click(top, 'Resources');
check(closedNative.includes(originalPanels[0].panel) && top.d.querySelector('main').hasAttribute('inert'), 'opening an existing modal closes native top-layer tools before inerting the workbench');
const guardedToggle = new top.w.Event('beforetoggle', { cancelable: true }); Object.defineProperty(guardedToggle, 'newState', { value: 'open' });
originalPanels[1].panel.dispatchEvent(guardedToggle);
check(guardedToggle.defaultPrevented, 'a retained native trigger cannot open a tool above an active session modal');
click(top, 'Close'); nativeOpen.add(originalPanels[1].panel); top.w.dispatchEvent(new top.w.Event('blur'));
check(closedNative.includes(originalPanels[1].panel), 'losing window focus closes an open native tool');
nativeOpen.add(originalPanels[2].panel); await top.update({ token: 3 });
check(closedNative.includes(originalPanels[2].panel), 'session owner change closes retained native top-layer tools');
const oldToolId = componentTool.panel.id;
componentTool.cell.parentNode.remove(); await top.flush();
check(triggers().length === 4 && !top.d.getElementById(oldToolId), 'GWT removal drops the original panel and its trigger without retaining a duplicate surface');
const replacementCell = nativeHost.tBodies[0].insertRow().insertCell(), replacement = top.d.createElement('table');
replacement.className = 'tsj-component-panel'; replacement.setAttribute('aria-label', 'Component'); replacement.createTBody().insertRow().insertCell().textContent = 'New owner'; replacementCell.append(replacement); await top.flush();
check(triggers().length === 5 && replacement.id !== oldToolId && replacement.parentNode === replacementCell, 'a successor native panel receives a fresh invoker while staying in its new GWT cell');
const nativeSnapshotCount = top.snapshots(); originalPanels[0].nativeControl.textContent = 'Refreshed native reading'; await top.flush();
check(top.snapshots() === nativeSnapshotCount && originalPanels[0].cell.querySelector('.tsj-tool-trigger') === triggers()[0], 'native reading refresh neither polls the simulator snapshot nor rebuilds controls');
top.close();

// Focus reveal is a local-scrollport adapter only. This shim checks literal
// rectangles and focus-visible gating; Chromium below remains the clip/input oracle.
const focusUI = await fixture({ trackFocusListeners: true });
await focusUI.update({ screen: 'WORKBENCH', token: 2, hasBoard: true, ready: true });
const focusShell = focusUI.d.querySelector('.tsj-workbench-shell');
const shellPort = literalScrollport(focusShell, 0, 319, 500);
const mainScroll = focusUI.d.querySelector('main'), rootScroll = focusUI.d.documentElement;
mainScroll.scrollLeft = 29; rootScroll.scrollLeft = 13; focusUI.d.body.scrollLeft = 17;
const settingsButton = byText(focusUI.d, 'Settings');
literalRect(settingsButton, 316, 354);
focusin(focusUI, settingsButton, true);
check(shellPort.value() === 39, 'keyboard-visible header control moves the local shell just enough to reveal its full 316..354 rect with a four-pixel margin');
check(mainScroll.scrollLeft === 29 && rootScroll.scrollLeft === 13 && focusUI.d.body.scrollLeft === 17, 'header focus reveal never changes ancestor or document scroll positions');
shellPort.set(0); focusin(focusUI, settingsButton, false);
check(shellPort.value() === 0, 'non-focus-visible pointer focus does not shift a partially visible control during pointerdown');
shellPort.set(3); literalRect(settingsButton, -12, 20); focusin(focusUI, settingsButton, true);
check(shellPort.value() === 0, 'left-edge reveal clamps local scroll at zero');
shellPort.set(0); literalRect(settingsButton, 4, 315); focusin(focusUI, settingsButton, true);
check(shellPort.value() === 0, 'control already inside both four-pixel margins causes no scroll');
literalRect(settingsButton, 900, 950); focusin(focusUI, settingsButton, true);
check(shellPort.value() === 181, 'right-edge reveal clamps to the local scroll range');

const focusHost = focusUI.d.querySelector('#native-toolbar');
const focusPanelCell = focusHost.tBodies[0].insertRow().insertCell();
const focusPanel = focusUI.d.createElement('table'); focusPanel.className = 'tsj-component-panel'; focusPanel.setAttribute('aria-label', 'Board view');
const popoverChild = focusUI.d.createElement('button'); popoverChild.textContent = 'Existing board action';
focusPanel.createTBody().insertRow().insertCell().append(popoverChild); focusPanelCell.append(focusPanel); await focusUI.flush();
const ribbonPort = literalScrollport(focusHost.tBodies[0], 0, 319, 700);
const boardViewTrigger = focusUI.d.querySelector('.tsj-tool-trigger[title="Board view"]');
check(!!boardViewTrigger, 'native ribbon exposes the existing Board view owner');
literalRect(boardViewTrigger, 320, 360); focusin(focusUI, boardViewTrigger, true);
check(ribbonPort.value() === 45, 'keyboard-visible native trigger moves only the ribbon scrollport to expose its full rect');
ribbonPort.set(12); literalRect(popoverChild, 760, 800); focusin(focusUI, popoverChild, true);
check(ribbonPort.value() === 12, 'native popover descendants never drive ribbon scrolling');
const meterChild = focusUI.d.createElement('button'); meterChild.textContent = 'Meter action';
focusUI.d.querySelector('.tsj-meter-panel').append(meterChild);
literalRect(meterChild, 760, 800); focusin(focusUI, meterChild, true);
check(ribbonPort.value() === 12, 'meter descendants never drive ribbon scrolling');

const replacementHost = focusUI.d.createElement('table'); replacementHost.id = 'native-toolbar-replacement';
const replacementMeterCell = replacementHost.createTBody().insertRow().insertCell();
replacementMeterCell.append(focusUI.d.querySelector('.tsj-meter-panel'));
const replacementToolCell = replacementHost.tBodies[0].insertRow().insertCell();
const replacementPanel = focusUI.d.createElement('table'); replacementPanel.className = 'tsj-component-panel'; replacementPanel.setAttribute('aria-label', 'Board view');
replacementPanel.createTBody().insertRow().insertCell().textContent = 'Replacement native tool'; replacementToolCell.append(replacementPanel);
focusHost.replaceWith(replacementHost); focusUI.w.tsjProductRefresh(); await focusUI.flush();
const replacementPort = literalScrollport(replacementHost.tBodies[0], 0, 319, 700, 7);
const replacementTrigger = focusUI.d.querySelector('.tsj-tool-trigger[title="Board view"]');
literalRect(replacementTrigger, 320, 360); focusin(focusUI, replacementTrigger, true);
check(replacementPort.value() === 52 && ribbonPort.value() === 12, 'focus reveal follows the replacement GWT toolbar owner and leaves the retired ribbon unchanged');
check(focusUI.focusListenersAdded.length === 1 && focusUI.focusListenersRemoved.length === 0, 'one document focus listener serves the current bridge and toolbar');
const retainedFocusCallback = focusUI.focusListenersAdded[0];
focusUI.setAvailable(false);
check(focusUI.w.tsjProductTeardown(focusUI.api), 'revoking the current bridge tears down its UI owner');
check(focusUI.focusListenersRemoved.length === 1 && focusUI.focusListenersRemoved[0] === retainedFocusCallback, 'teardown removes the exact focus listener owned by the retired bridge');
const unchangedAfterRelease = replacementPort.value();
retainedFocusCallback({ target: replacementTrigger });
check(replacementPort.value() === unchangedAfterRelease, 'a retained retired-owner callback is inert after release');
const successorApi = {
  available: () => true,
  snapshot: () => ({ ...focusUI.state(), token: 3 }),
  openView: () => 1, closeView: () => {}, action: () => ''
};
focusUI.w.tsjProduct = successorApi; focusUI.w.tsjProductRefresh(); await focusUI.flush();
const successorShell = focusUI.d.querySelector('.tsj-workbench-shell');
const successorPort = literalScrollport(successorShell, 0, 319, 500);
const successorSettings = byText(focusUI.d, 'Settings');
literalRect(successorSettings, 316, 354); focusin(focusUI, successorSettings, true);
check(successorPort.value() === 39 && focusUI.focusListenersAdded.length === 2 && focusUI.focusListenersRemoved.length === 1,
  'successor bridge installs one fresh focus owner and reveals locally without duplicate listeners');
focusUI.close();

const denied = await fixture({ deny: true }); click(denied, 'Settings'); denied.d.querySelector('input[type=checkbox]').click();
check(denied.d.querySelector('.tsj-product-content').textContent.includes('only to this session'), 'storage denial is truthful and nonfatal'); denied.close();
const malformed = await fixture({ storage: '{"version":1,"highContrast":"false","largeText":false,"reducedMotion":false}' });
check(!malformed.d.body.classList.contains('tsj-high-contrast'), 'malformed storage cannot enable preferences');
click(malformed, 'Settings'); check(malformed.d.querySelector('.tsj-product-content').textContent.includes('Defaults apply'), 'malformed storage shows recovery notice'); malformed.close();
const debug = await fixture({ noBridge: true }); check(!debug.d.querySelector('.tsj-workbench-shell'), 'absent product bridge leaves verifier/debug DOM untouched');
debug.w.tsjProduct = debug.api; debug.d.body.appendChild(debug.d.createElement('span')); await debug.flush();
check(!!debug.d.querySelector('.tsj-workbench-shell'), 'MutationObserver discovers late bridge'); debug.close();
console.log('PASS U04 UI adapter contracts: ' + checks + ' assertions. jsdom only; real GWT/browser input qualification remains separate.');
