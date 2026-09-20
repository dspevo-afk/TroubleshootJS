/* Bottom parts drawer presentation. */
(function (window, document) {
  'use strict';
  var root, handle, scroll, spacer, owner, canvas, state, change, latest, closeTimer;
  var dismissed = false, pointerX = -1, pointerY = -1;
  function active() { return !!(canvas && canvas.isConnected && latest && latest.screen === 'WORKBENCH' && !window.tsjWorkbenchOverlayOpen); }
  function rect() { return canvas.getBoundingClientRect(); }
  function cancelClose() { if (closeTimer) window.clearTimeout(closeTimer); closeTimer = null; }
  function request(open, offset) {
    if (!change || !state) return;
    cancelClose();
    if (!open && state.dragging) return;
    change(open, offset === undefined ? state.scroll : Math.round(offset));
  }
  function leave() {
    cancelClose();
    if (active() && state.open) {
      var r = rect(), top = r.bottom - state.height * r.height / canvas.height;
      if (pointerX >= r.left && pointerX <= r.right && pointerY >= top && pointerY <= r.bottom) return;
    }
    if (state && state.open && !state.dragging && !root.contains(document.activeElement))
      closeTimer = window.setTimeout(function () { closeTimer = null; request(false); }, 180);
  }
  function build() {
    root = document.createElement('section'); root.className = 'tsj-tray-drawer'; root.hidden = true;
    root.setAttribute('aria-label', 'Physical parts tray');
    handle = document.createElement('button'); handle.type = 'button'; handle.className = 'tsj-tray-handle';
    handle.setAttribute('aria-label', 'Show or hide parts tray');
    handle.title = 'Hover at the bottom edge to open. Drag parts into or out of the tray. Escape closes it.';
    scroll = document.createElement('div'); scroll.className = 'tsj-tray-scroll'; scroll.tabIndex = 0;
    scroll.id = 'tsj-tray-scroll'; scroll.setAttribute('role', 'region'); scroll.setAttribute('aria-label', 'Scroll parts tray horizontally');
    handle.setAttribute('aria-controls', scroll.id);
    spacer = document.createElement('div'); scroll.appendChild(spacer); root.appendChild(handle); root.appendChild(scroll);
    document.body.appendChild(root);
    handle.addEventListener('pointerenter', function (e) { if (e.pointerType !== 'touch' && active()) { dismissed = false; request(true); } });
    handle.addEventListener('click', function () { if (active()) { dismissed = state.open; request(!state.open); } });
    handle.addEventListener('keydown', function (e) {
      if (e.key === 'ArrowUp') { e.preventDefault(); dismissed = false; request(true); }
    });
    scroll.addEventListener('scroll', function () {
      var ratio = canvas ? rect().width / canvas.width : 1;
      if (active() && Math.abs(scroll.scrollLeft / ratio - state.scroll) > .5) request(true, scroll.scrollLeft / ratio);
    });
    scroll.addEventListener('wheel', function (e) {
      if (!active() || state.width <= canvas.width) return;
      e.preventDefault(); scroll.scrollLeft += Math.abs(e.deltaX) > Math.abs(e.deltaY) ? e.deltaX : e.deltaY;
    }, {passive:false});
    root.addEventListener('focusin', cancelClose);
    root.addEventListener('focusout', leave);
    window.addEventListener('pointermove', function (e) {
      pointerX = e.clientX; pointerY = e.clientY;
      if (!active()) return;
      var r = rect(), top = r.bottom - (state.open ? state.height * r.height / canvas.height : 24);
      if (e.clientX >= r.left && e.clientX <= r.right && e.clientY >= top && e.clientY <= r.bottom) {
        cancelClose(); if (!dismissed) request(true);
      } else { dismissed = false; leave(); }
    }, {passive:true});
    window.addEventListener('pointerup', function () { if (state && state.open) leave(); }, {passive:true});
    window.addEventListener('blur', function () { request(false); });
    document.addEventListener('keydown', function (e) {
      if (active() && state.open && e.key === 'Escape') {
        dismissed = true; if (root.contains(document.activeElement)) handle.focus();
        request(false); e.preventDefault(); e.stopPropagation();
      }
    }, true);
    ['click','dblclick','contextmenu','mousedown','mouseup','wheel'].forEach(function (name) {
      root.addEventListener(name, function (e) { e.stopPropagation(); });
    });
  }
  function layout() {
    if (!root || !state) return;
    var enabled = active(); root.hidden = !enabled;
    if (!enabled) { window.tsjTrayDrawer.top = null; cancelClose(); return; }
    var r = rect(), height = state.open ? state.height * r.height / canvas.height : 24;
    var values = {left:r.left+'px', top:(r.bottom-height)+'px', width:r.width+'px', height:height+'px'};
    Object.keys(values).forEach(function (k) { if (root.style[k] !== values[k]) root.style[k] = values[k]; });
    root.classList.toggle('is-open', state.open);
    root.classList.toggle('is-dragging', state.dragging);
    handle.setAttribute('aria-expanded', String(state.open));
    var text = (state.open ? 'PARTS TRAY' : '\u25b2 PARTS TRAY') + ' \u00b7 ' + state.count + (state.count === 1 ? ' part' : ' parts');
    if (handle.textContent !== text) handle.textContent = text;
    scroll.hidden = !state.open || state.width <= canvas.width;
    var width = state.width * r.width / canvas.width + 'px'; if (spacer.style.width !== width) spacer.style.width = width;
    if (Math.abs(scroll.scrollLeft - state.scroll * r.width / canvas.width) > .5) scroll.scrollLeft = state.scroll * r.width / canvas.width;
    window.tsjTrayDrawer.top = r.bottom - height;
  }
  window.tsjTrayDrawer = {
    top: null,
    sync: function (snapshot) { latest = snapshot; layout(); },
    present: function (next, target, view, callback) {
      if (!root) build();
      if (owner !== next) { cancelClose(); owner = next; change = callback; dismissed = false; }
      canvas = target; state = view; layout();
      if (!active() && state.open) request(false);
    },
    suspend: function (previous) {
      if (owner === previous) {
        cancelClose(); owner = null; canvas = null; state = null; change = null;
        if (root) root.hidden = true; this.top = null;
      }
    }
  };
  window.addEventListener('resize', layout);
  window.addEventListener('scroll', function (e) { if (e.target !== scroll) layout(); }, true);
}(window, document));
