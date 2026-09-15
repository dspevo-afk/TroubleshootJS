/* Bench presentation delegates live measurements to the GWT instrument owner. */
(function (window, document) {
  'use strict';
  var panel, grip, dial, note, observer, latest, scene = null, state = null;
  // A board owns its placement; menu/retest/flip refreshes do not reset it.
  var placements = new WeakMap();
  var modes = ['NONE', 'DC_VOLTAGE', 'RESISTANCE', 'CONTINUITY', 'DIODE'];
  var names = ['Off', 'DC voltage', 'Resistance', 'Continuity', 'Diode test'];
  var angles = [-150, -55, 55, -120, 120];
  function add(parent, tag, text, cls) {
    var node = document.createElement(tag); node.textContent = text || '';
    if (cls) node.className = cls; parent.appendChild(node); return node;
  }
  function control(parent, text, label, callback, cls) {
    var node = add(parent, 'button', text, cls); node.type = 'button';
    node.setAttribute('aria-label', label); node.title = label;
    if (callback) node.addEventListener('click', callback);
    return node;
  }
  function select(index) {
    if (!latest || !window.tsjProduct || index < 0 || index >= modes.length) return;
    var result = window.tsjProduct.action(latest.token, 0, 'meter', modes[index], '', '');
    note.textContent = result || 'Click a mode, or turn the dial. Left / right click: red / black probe.';
    sync();
  }
  function sync() {
    if (!panel || !dial) return;
    var index = modes.indexOf(panel.getAttribute('data-mode')); if (index < 0) index = 0;
    dial.style.setProperty('--dial-angle', angles[index] + 'deg');
    dial.setAttribute('aria-valuenow', String(index)); dial.setAttribute('aria-valuetext', names[index]);
    dial.setAttribute('aria-disabled', String(!latest || !latest.ready || latest.completed));
    panel.querySelector('.tsj-meter-off').setAttribute('aria-pressed', String(index === 0));
  }
  function style(name, value) {
    if (panel.style[name] !== value) panel.style[name] = value;
  }
  function geometry() {
    if (!scene || !scene.canvas.isConnected) return null;
    var r = scene.canvas.getBoundingClientRect(), c = scene.view;
    var rx = r.width / scene.canvas.width, ry = r.height / scene.canvas.height;
    var dock = document.querySelector('.tsj-workbench-toolbar-host');
    var top = dock ? dock.getBoundingClientRect().bottom : 0;
    var bounds = {
      left: Math.max(8, r.left + c.area.x * rx + 12),
      top: Math.max(top + 8, r.top + c.area.y * ry + 12),
      right: Math.min(window.innerWidth - 12, r.left + (c.area.x + c.area.width) * rx - 16),
      bottom: Math.min(window.innerHeight - 14, r.top + (c.area.y + c.area.height) * ry - 18)
    };
    if (!(rx > 0 && ry > 0 && c.scale > 0 && bounds.right > bounds.left && bounds.bottom > bounds.top)) return null;
    // Normal scale is physical camera scale. Only shrink an oversized instrument
    // to fit the visible bench; never restrict the PCB's close inspection zoom.
    var scale = Math.min(c.scale * Math.min(rx, ry),
      (bounds.right - bounds.left) / c.home.width, (bounds.bottom - bounds.top) / c.home.height);
    return { rect: r, rx: rx, ry: ry, bounds: bounds, scale: scale,
      width: c.home.width * scale, height: c.home.height * scale };
  }
  function toWorld(clientX, clientY, g) {
    var c = scene.view;
    return { x: ((clientX - g.rect.left) / g.rx - c.x) / c.scale,
      y: ((clientY - g.rect.top) / g.ry - c.y) / c.scale };
  }
  function layout() {
    if (!panel) return;
    var g = geometry(), visible = !!(g && latest && latest.hasBoard && latest.screen !== 'MENU');
    var visibility = String(visible);
    if (panel.getAttribute('data-bench-visible') !== visibility) panel.setAttribute('data-bench-visible', visibility);
    if (!g || !state) { finishDrag(); return; }
    var c = scene.view;
    if (!state.position) state.position = { x: c.home.x, y: c.home.y };
    var x = g.rect.left + (c.x + state.position.x * c.scale) * g.rx;
    var y = g.rect.top + (c.y + state.position.y * c.scale) * g.ry;
    var projectedX = x, projectedY = y;
    x = Math.max(g.bounds.left, Math.min(x, g.bounds.right - g.width));
    y = Math.max(g.bounds.top, Math.min(y, g.bounds.bottom - g.height));
    // At an edge, re-seat on the visible bench in WORLD coordinates. This is
    // visibility recovery, not a stored screen offset or a restriction on pan.
    if (x !== projectedX || y !== projectedY) state.position = toWorld(x, y, g);
    // The native widget keeps its GWT parent/handlers. Absolute coordinates are
    // converted from the canvas into that containing block, never window-fixed.
    var parent = panel.offsetParent, pr = parent ? parent.getBoundingClientRect() : {left:0, top:0};
    var px = pr.left + (parent ? parent.clientLeft - parent.scrollLeft : -window.scrollX);
    var py = pr.top + (parent ? parent.clientTop - parent.scrollTop : -window.scrollY);
    style('left', (x - px).toFixed(3) + 'px'); style('top', (y - py).toFixed(3) + 'px');
    style('right', 'auto'); style('bottom', 'auto');
    var scaleText = String(g.scale);
    if (panel.style.getPropertyValue('--meter-scale') !== scaleText) panel.style.setProperty('--meter-scale', scaleText);
    panel.setAttribute('data-bench-world-x', String(state.position.x));
    panel.setAttribute('data-bench-world-y', String(state.position.y));
  }
  function moveWorld(x, y) {
    if (!state || !isFinite(x) || !isFinite(y)) return;
    state.position = { x: x, y: y }; layout();
  }
  function resize() { finishDrag(); layout(); }
  function finishDrag(event) {
    if (!state || !state.drag) return;
    if (event && event.pointerId !== undefined && event.pointerId !== state.drag.id) return;
    var id = state.drag.id; state.drag = null; grip.classList.remove('is-dragging');
    if (grip.hasPointerCapture && grip.hasPointerCapture(id)) grip.releasePointerCapture(id);
  }
  function beginDrag(event) {
    if (event.button !== 0 || event.isPrimary === false || !state || state.drag ||
        !latest || latest.screen !== 'WORKBENCH' || window.tsjWorkbenchOverlayOpen) return;
    var g = geometry(); if (!g) return;
    event.preventDefault(); event.stopPropagation(); grip.focus({preventScroll:true});
    var pointer = toWorld(event.clientX, event.clientY, g);
    state.drag = { id: event.pointerId, x: pointer.x - state.position.x, y: pointer.y - state.position.y };
    // Window listeners also cover browsers/devices that cannot capture this pointer.
    try { grip.setPointerCapture(event.pointerId); } catch (unavailable) { /* Window fallback. */ }
    grip.classList.add('is-dragging');
  }
  function dragMove(event) {
    if (!state || !state.drag || state.drag.id !== event.pointerId) return;
    if (window.tsjWorkbenchOverlayOpen) { finishDrag(); return; }
    var g = geometry(); if (!g) { finishDrag(); return; }
    event.preventDefault(); event.stopPropagation();
    var pointer = toWorld(event.clientX, event.clientY, g);
    moveWorld(pointer.x - state.drag.x, pointer.y - state.drag.y);
  }
  function build(found) {
    panel = found; panel.classList.add('tsj-bench-meter');
    // Let the native GWT-owned widget paint into the bench without reparenting it.
    var dock = panel.closest('.tsj-workbench-top-dock');
    if (dock && dock.parentElement) dock.parentElement.classList.add('tsj-bench-dock-layer');
    panel.setAttribute('role', 'group'); panel.setAttribute('aria-label', 'Workbench multimeter');
    var cell = panel.querySelector('tbody > tr > td');
    grip = control(cell, 'TSJ MM-90', 'Move multimeter: drag, or use arrow keys. Home resets its position.', null, 'tsj-meter-grip');
    add(cell, 'span', 'DIGITAL MULTIMETER', 'tsj-meter-brand');
    var extras = add(cell, 'div', '', 'tsj-meter-extras');
    [['HOLD','Hold'], ['RANGE','Manual range'], ['MIN/MAX','Minimum / maximum']].forEach(function (item) {
      var b = control(extras, item[0], item[1] + ' / not implemented', null, 'tsj-meter-placeholder'); b.disabled = true;
    });
    var light = control(extras, 'LIGHT', 'Toggle meter backlight', function () {
      var on = !panel.classList.contains('is-backlit'); panel.classList.toggle('is-backlit', on);
      light.setAttribute('aria-pressed', String(on));
    }, 'tsj-meter-light'); light.setAttribute('aria-pressed', 'false');
    dial = add(cell, 'div', '', 'tsj-meter-dial'); dial.tabIndex = 0;
    dial.setAttribute('role', 'slider'); dial.setAttribute('aria-label', 'Multimeter function selector');
    dial.setAttribute('aria-valuemin', '0'); dial.setAttribute('aria-valuemax', '4');
    dial.setAttribute('aria-orientation', 'horizontal');
    add(dial, 'span', '', 'tsj-meter-knob');
    control(cell, 'OFF', 'Turn multimeter off', function () { select(0); }, 'tsj-meter-off');
    var future = add(cell, 'div', '', 'tsj-meter-future');
    [['V~','AC voltage'], ['A','Current'], ['mA','Milliamps'], ['µA','Microamps'],
      ['CAP','Capacitance'], ['Hz','Frequency'], ['%','Duty cycle'], ['°C/°F','Temperature']].forEach(function (item) {
      var b = control(future, item[0], item[1] + ' / not implemented', null, 'tsj-meter-placeholder'); b.disabled = true;
    });
    add(cell, 'span', 'GRAY FUNCTIONS: NOT IMPLEMENTED', 'tsj-meter-future-label');
    var jacks = add(cell, 'div', '', 'tsj-meter-jacks');
    [['10A','current'], ['COM','common'], ['V Ω','volts']].forEach(function (item) {
      var jack = add(jacks, 'span', item[0], 'tsj-meter-jack ' + item[1]); jack.setAttribute('aria-hidden', 'true');
    });
    note = add(cell, 'p', 'Click a mode, or turn the dial. Left / right click: red / black probe.', 'tsj-meter-note');
    note.setAttribute('role', 'status');
    grip.addEventListener('pointerdown', beginDrag);
    window.addEventListener('pointermove', dragMove, true);
    ['pointerup', 'pointercancel'].forEach(function (name) { window.addEventListener(name, finishDrag, true); });
    grip.addEventListener('lostpointercapture', finishDrag);
    grip.addEventListener('blur', function () { finishDrag(); });
    dial.addEventListener('pointerdown', function (event) {
      if (event.button !== 0) return; event.preventDefault(); dial.focus();
      var r = dial.getBoundingClientRect(), x = event.clientX-r.left-r.width/2, y=event.clientY-r.top-r.height/2;
      var angle = Math.atan2(x, -y) * 180 / Math.PI, index = 0, best = 361;
      angles.forEach(function (a, i) { var delta = Math.abs(((angle-a+540)%360)-180); if(delta<best){best=delta;index=i;} });
      select(index);
    });
    document.addEventListener('keydown', function (event) {
      if (!panel.contains(event.target) || window.tsjWorkbenchOverlayOpen) return;
      var index = Math.max(0, modes.indexOf(panel.getAttribute('data-mode'))), handled = false;
      if (event.target === dial) {
        if (event.key === 'ArrowRight' || event.key === 'ArrowUp') { select((index+1)%modes.length); handled=true; }
        if (event.key === 'ArrowLeft' || event.key === 'ArrowDown') { select((index+modes.length-1)%modes.length); handled=true; }
        if (event.key === 'Home') { select(0); handled=true; }
        if (event.key === 'End') { select(4); handled=true; }
      } else if (event.target === grip) {
        if (event.key === 'Escape') { finishDrag(); handled=true; }
        if (event.key === 'Home' && state) { finishDrag(); state.position=null; layout(); handled=true; }
        var delta = event.shiftKey ? 5 : 20, g = geometry();
        if (/^Arrow(Left|Right|Up|Down)$/.test(event.key) && state && g) {
          finishDrag();
          moveWorld(state.position.x+(event.key==='ArrowRight'?delta:event.key==='ArrowLeft'?-delta:0)/(scene.view.scale*g.rx),
            state.position.y+(event.key==='ArrowDown'?delta:event.key==='ArrowUp'?-delta:0)/(scene.view.scale*g.ry)); handled=true;
        }
      }
      if ((event.key === 'Enter' || event.key === ' ') && event.target.tagName === 'BUTTON') {
        if (!event.repeat && !event.target.disabled) event.target.click(); handled=true;
      }
      if (handled) event.preventDefault(); event.stopImmediatePropagation();
    }, true);
    ['click','dblclick','contextmenu','pointerdown','pointerup','pointermove','mousedown','mouseup','mousemove','wheel','keyup','keypress'].forEach(function (name) {
      panel.addEventListener(name, function (event) { event.stopPropagation(); });
    });
    if (window.MutationObserver) {
      observer = new window.MutationObserver(sync);
      observer.observe(panel, { attributes: true, attributeFilter: ['data-mode'] });
    }
    window.addEventListener('resize', resize); window.addEventListener('scroll', layout, true);
    window.addEventListener('blur', function () { finishDrag(); });
    document.addEventListener('visibilitychange', function () { if (document.hidden) finishDrag(); });
    layout();
  }
  window.tsjBenchInstruments = {
    mount: function (snapshot) {
      latest = snapshot;
      var found = document.querySelector('.tsj-meter-panel');
      if (!found) return;
      if (!panel) build(found);
      if (snapshot.screen !== 'WORKBENCH' || window.tsjWorkbenchOverlayOpen) finishDrag();
      sync(); layout();
    },
    project: function (owner, canvas, view) {
      if (!scene || scene.owner !== owner) {
        finishDrag(); state = placements.get(owner);
        if (!state) { state = { position: null, drag: null }; placements.set(owner, state); }
      }
      scene = { owner: owner, canvas: canvas, view: view };
      if (window.tsjWorkbenchOverlayOpen) finishDrag();
      layout();
    },
    suspend: function (owner) {
      if (scene && scene.owner === owner) { finishDrag(); scene = null; layout(); }
    }
  };
}(window, document));
