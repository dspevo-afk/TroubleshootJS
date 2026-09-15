/* Bench presentation delegates live measurements to the GWT instrument owner. */
(function (window, document) {
  'use strict';
  var panel, grip, dial, note, observer, drag, position = null, latest;
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
  function move(x, y) {
    var rect = panel.getBoundingClientRect();
    var dock = document.querySelector('.tsj-workbench-toolbar-host');
    var top = dock ? dock.getBoundingClientRect().bottom + 8 : 150;
    x = Math.max(8, Math.min(x, window.innerWidth - rect.width - 8));
    y = Math.max(top, Math.min(y, Math.max(top, window.innerHeight - rect.height - 8)));
    position = { x: x, y: y };
    panel.style.left = x + 'px'; panel.style.top = y + 'px';
    panel.style.right = 'auto'; panel.style.bottom = 'auto';
  }
  function resize() {
    if (!panel) return;
    var dock = document.querySelector('.tsj-workbench-toolbar-host');
    var top = dock ? dock.getBoundingClientRect().bottom : 142;
    var scale = Math.max(.55, Math.min(1, (window.innerHeight - top - 20) / 454));
    panel.style.setProperty('--meter-scale', String(scale));
    var rect = panel.getBoundingClientRect();
    move(position ? position.x : window.innerWidth - rect.width - 20,
      position ? position.y : window.innerHeight - rect.height - 18);
  }
  function finishDrag(event) {
    if (!drag) return;
    if (event && event.pointerId !== undefined && event.pointerId !== drag.id) return;
    drag = null; grip.classList.remove('is-dragging');
  }
  function build(found) {
    panel = found; panel.classList.add('tsj-bench-meter');
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
    grip.addEventListener('pointerdown', function (event) {
      if (event.button !== 0) return; event.preventDefault();
      var rect = panel.getBoundingClientRect(); drag = {id:event.pointerId, x:event.clientX-rect.left, y:event.clientY-rect.top};
      grip.setPointerCapture(event.pointerId); grip.classList.add('is-dragging');
    });
    grip.addEventListener('pointermove', function (event) {
      if (drag && drag.id === event.pointerId) move(event.clientX-drag.x, event.clientY-drag.y);
    });
    ['pointerup', 'pointercancel', 'lostpointercapture'].forEach(function (name) { grip.addEventListener(name, finishDrag); });
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
        if (event.key === 'Home') { position=null; resize(); handled=true; }
        var delta = event.shiftKey ? 5 : 20;
        if (/^Arrow/.test(event.key)) {
          var r = panel.getBoundingClientRect();
          move(r.left+(event.key==='ArrowRight'?delta:event.key==='ArrowLeft'?-delta:0),
            r.top+(event.key==='ArrowDown'?delta:event.key==='ArrowUp'?-delta:0)); handled=true;
        }
      }
      if ((event.key === 'Enter' || event.key === ' ') && event.target.tagName === 'BUTTON') {
        if (!event.repeat && !event.target.disabled) event.target.click(); handled=true;
      }
      if (handled) event.preventDefault(); event.stopImmediatePropagation();
    }, true);
    ['click','dblclick','contextmenu','pointerdown','pointerup','mousedown','mouseup','wheel','keyup','keypress'].forEach(function (name) {
      panel.addEventListener(name, function (event) { event.stopPropagation(); });
    });
    if (window.MutationObserver) {
      observer = new window.MutationObserver(sync);
      observer.observe(panel, { attributes: true, attributeFilter: ['data-mode'] });
    }
    window.addEventListener('resize', resize); window.addEventListener('blur', function () { finishDrag(); });
    resize();
  }
  window.tsjBenchInstruments = { mount: function (snapshot) {
    latest = snapshot;
    var found = document.querySelector('.tsj-meter-panel');
    if (!found) return;
    if (!panel) build(found);
    sync();
  }};
}(window, document));
