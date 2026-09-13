/* Product presentation only. CircuitJS owns sessions, inventory and electrical truth. */
(function (window, document) {
  'use strict';
  var shell, overlay, dialog, content, heading, closeButton, modalStatus, shellStatus;
  var bridge, snapshot, discovery, backgroundObserver, queued = false;
  var auxiliary = '', renderedKey = '', lease = null, returnFocus = null, auxiliaryFocus = null;
  var savedContent = null, savedStart = null;
  var background = [], controls = {}, shopRows = [], shopCategories = [], shopSignature = '', shopCategory = '';
  var localMessage = '', settingsNotice = '', serial = 0;
  var settingsKey = 'tsj.presentation.v1';
  var settings = { version: 1, highContrast: false, largeText: false, reducedMotion: false };

  function element(tag, text, className) {
    var node = document.createElement(tag);
    if (text !== undefined) node.textContent = text;
    if (className) node.className = className;
    return node;
  }
  function append(parent, tag, text, className) {
    var node = element(tag, text, className); parent.appendChild(node); return node;
  }
  function button(parent, text, callback, className) {
    var node = append(parent, 'button', text, className || 'tsj-product-button');
    node.type = 'button'; node.addEventListener('click', callback); return node;
  }
  function setText(node, value) {
    value = value || '';
    if (node && node.textContent !== value) node.textContent = value;
  }
  function field(parent, title, tag) {
    var label = append(parent, 'label', title, 'tsj-product-field');
    var input = append(label, tag || 'input');
    input.id = 'tsj-product-field-' + (++serial);
    return input;
  }
  function readOnly(parent, title, value) {
    var input = field(parent, title, 'textarea');
    input.readOnly = true; input.rows = 2; input.value = value || 'No board selected.';
    input.spellcheck = false; return input;
  }
  function status(message) {
    localMessage = message || '';
    setText(modalStatus, auxiliary === 'Shop' ? localMessage : localMessage || (snapshot && (snapshot.notice || snapshot.message)));
    setText(shellStatus, localMessage || (snapshot && (snapshot.notice || snapshot.message)));
  }
  function invoke(token, view, name, a, b, c) {
    // Each changing control captures its owner and modal lease at creation time.
    var message = bridge.action(token, view || 0, name, a || '', b || '', c || '');
    status(message); schedule();
  }
  function actionButton(parent, text, name, a, b, c) {
    var token = snapshot.token, view = lease;
    return button(parent, text, function () { invoke(token, view, name, a, b, c); });
  }
  function randomSeed() {
    // Six random bytes are exactly representable. Never convert user seeds to Number.
    if (window.crypto && window.crypto.getRandomValues) {
      var bytes = new Uint8Array(6), value = 0;
      window.crypto.getRandomValues(bytes);
      for (var i = 0; i < bytes.length; i++) value = value * 256 + bytes[i];
      return String(value);
    }
    return String(Math.floor(Math.random() * 281474976710656));
  }
  function identity(parent) {
    var details = append(parent, 'details', undefined, 'tsj-product-identity');
    append(details, 'summary', 'Replay and bug-report identity');
    append(details, 'p', 'Replay reconstructs the initial challenge. It does not save repairs or inventory. Only the current replay format is supported.');
    readOnly(details, 'Current replay', snapshot.replay);
    readOnly(details, 'Interpretation / build', snapshot.epoch + ' / ' + snapshot.build);
    append(details, 'p', 'You can select and copy this information for a bug report. Nothing is sent automatically.');
  }
  function support(parent) {
    append(parent, 'p', 'Limited desktop alpha: selected low-voltage boards in the 5–20-part range, including a 16-part procedural control board with routed bottom copper. The 3-part indicator and 4-part protected indicator are introductory practice boards.');
    append(parent, 'p', 'EASY and MEDIUM candidates are checked before play. HARD and PSYCHOTIC are unavailable. Mains circuits, larger boards and general multilayer routing are not supported. Session saves are not available.');
  }

  function invalidateView() {
    if (lease !== null && bridge) bridge.closeView(lease);
    lease = null;
  }
  function inertBackground() {
    Array.prototype.forEach.call(document.body.children, function (node) {
      if (node === overlay || background.some(function (saved) { return saved.node === node; })) return;
      background.push({ node: node, inert: node.getAttribute('inert'), aria: node.getAttribute('aria-hidden') });
      node.setAttribute('inert', ''); node.setAttribute('aria-hidden', 'true');
    });
  }
  function restoreBackground() {
    if (backgroundObserver) backgroundObserver.disconnect();
    background.forEach(function (saved) {
      if (saved.inert === null) saved.node.removeAttribute('inert');
      else saved.node.setAttribute('inert', saved.inert);
      if (saved.aria === null) saved.node.removeAttribute('aria-hidden');
      else saved.node.setAttribute('aria-hidden', saved.aria);
    });
    background = [];
  }
  function focusable() {
    return Array.prototype.filter.call(dialog.querySelectorAll('button, input, select, textarea, a[href], summary, [tabindex]'), function (node) {
      // Chromium can report rectangles for fields inside a closed disclosure.
      var closedDetails = node.closest('details:not([open])');
      return !node.disabled && node.tabIndex >= 0 && node.getClientRects().length > 0 && !node.closest('[inert]') &&
        (!closedDetails || (node.tagName === 'SUMMARY' && node.parentElement === closedDetails));
    });
  }
  function focusDialog() { (focusable()[0] || dialog).focus(); }
  function showOverlay() {
    if (!window.tsjWorkbenchOverlayOpen) returnFocus = document.activeElement;
    overlay.classList.add('is-open'); overlay.hidden = false;
    window.tsjWorkbenchOverlayOpen = true;
    // Focus before aria-hiding the previous focused subtree.
    dialog.focus(); inertBackground();
    if (window.MutationObserver) {
      if (!backgroundObserver) backgroundObserver = new window.MutationObserver(inertBackground);
      backgroundObserver.observe(document.body, { childList: true });
    }
  }
  function hideOverlay() {
    invalidateView(); restoreBackground();
    overlay.classList.remove('is-open'); overlay.hidden = true;
    window.tsjWorkbenchOverlayOpen = false;
    if (returnFocus && returnFocus.isConnected && !returnFocus.disabled && !returnFocus.closest('[inert]')) returnFocus.focus();
    else if (controls.menu) controls.menu.focus();
    returnFocus = null;
  }
  function openAuxiliary(name) {
    auxiliaryFocus = document.activeElement;
    if (!auxiliary && snapshot.screen !== 'WORKBENCH') {
      // Keep the actual menu/ticket controls while a reference view is open.
      // This preserves entered text, selections and the exact return-focus node.
      savedContent = Array.prototype.slice.call(content.childNodes);
      savedStart = controls.start;
    }
    auxiliary = name; localMessage = ''; renderedKey = ''; render();
  }
  function closeAuxiliary() {
    if (!auxiliary) return;
    auxiliary = ''; invalidateView(); renderedKey = ''; render();
    if (window.tsjWorkbenchOverlayOpen && auxiliaryFocus && auxiliaryFocus.isConnected && dialog.contains(auxiliaryFocus)) auxiliaryFocus.focus();
    auxiliaryFocus = null;
  }

  function menu(parent) {
    append(parent, 'p', 'Choose a board, read the customer ticket, then examine, measure, isolate, repair and retest.');
    var form = append(parent, 'form', undefined, 'tsj-product-form');
    var profile = field(form, 'Difficulty', 'select');
    ['EASY', 'MEDIUM', 'HARD', 'PSYCHOTIC'].forEach(function (name) {
      var option = append(profile, 'option', name + (name === 'HARD' || name === 'PSYCHOTIC' ? ' — unavailable' : ''));
      option.value = name; option.disabled = name === 'HARD' || name === 'PSYCHOTIC';
    });
    var family = field(form, 'Board family', 'select');
    function updateFamilies() {
      var selected = family.value;
      family.replaceChildren();
      snapshot.families.forEach(function (item) {
        if (item.profile !== profile.value) return;
        var option = append(family, 'option', item.name); option.value = item.id;
        if (item.id === selected) option.selected = true;
      });
    }
    profile.addEventListener('change', updateFamilies); updateFamilies();
    var token = snapshot.token, view = lease;
    button(form, 'New board', function () {
      // Entropy is not a board seed. The Java launch owner selects and publishes
      // the actual seed through its qualified envelope for this family/profile.
      invoke(token, view, 'random', family.value, randomSeed(), profile.value);
    }, 'tsj-product-button tsj-product-primary');
    append(form, 'p', 'New board chooses from the current seed pool. The chosen seed appears in Replay and bug-report identity after preparation.');
    var seed = field(form, 'Exact seed (signed decimal integer)');
    seed.type = 'text'; seed.value = '0'; seed.required = true;
    seed.maxLength = 20; seed.autocomplete = 'off'; seed.spellcheck = false;
    append(form, 'p', 'Prepare exact seed attempts exactly the integer you enter. An exact seed can fail preparation; it is never replaced with another seed.');
    var launch = append(form, 'button', 'Prepare exact seed', 'tsj-product-button'); launch.type = 'submit';
    form.addEventListener('submit', function (event) {
      event.preventDefault(); invoke(token, view, 'launch', family.value, seed.value, profile.value);
    });
    var replayForm = append(parent, 'form', undefined, 'tsj-product-form');
    var replay = field(replayForm, 'Open current replay', 'textarea'); replay.rows = 2; replay.required = true; replay.spellcheck = false;
    var open = append(replayForm, 'button', 'Prepare replay', 'tsj-product-button'); open.type = 'submit';
    replayForm.addEventListener('submit', function (event) { event.preventDefault(); invoke(token, view, 'replay', replay.value); });
    if (snapshot.hasBoard) actionButton(parent, 'Resume current board', 'resume');
    var links = append(parent, 'div', undefined, 'tsj-product-actions');
    button(links, 'Resources', function () { openAuxiliary('Resources'); });
    button(links, 'Settings', function () { openAuxiliary('Settings'); });
    support(parent); identity(parent);
  }
  function ticket(parent) {
    append(parent, 'h3', 'Customer complaint'); append(parent, 'p', snapshot.complaint);
    append(parent, 'h3', 'Functional retest'); append(parent, 'p', snapshot.retestInstruction);
    append(parent, 'p', 'Examine the board and use the instruments to identify a repair. Switch off every supply and wait for stored energy to discharge before changing parts.');
    controls.start = actionButton(parent, 'Accept ticket and start', 'resume');
    actionButton(parent, 'Main menu', 'menu');
    button(parent, 'Resources', function () { openAuxiliary('Resources'); });
    identity(parent);
  }
  function results(parent) {
    append(parent, 'p', 'The live board passed the customer’s functional checks.');
    append(parent, 'p', snapshot.message);
    actionButton(parent, 'New board / Main menu', 'menu');
    actionButton(parent, 'Replay this challenge', 'replay', snapshot.replay);
    actionButton(parent, 'Return to board', 'resume'); identity(parent);
  }
  function shop(parent) {
    var storefront = append(parent, 'div', undefined, 'tsj-store');
    var toolbar = append(storefront, 'div', undefined, 'tsj-store-toolbar');
    append(toolbar, 'span', 'Local shop', 'tsj-store-local');
    append(toolbar, 'div', 'store.copperline.example/components', 'tsj-store-address');
    var supplier = append(storefront, 'header', undefined, 'tsj-store-supplier');
    var brand = append(supplier, 'div');
    append(brand, 'p', 'ELECTRONIC COMPONENTS', 'tsj-store-eyebrow');
    append(brand, 'h3', 'Copperline Supply Co.');
    controls.shopTrayCount = append(supplier, 'p', undefined, 'tsj-store-tray');
    append(storefront, 'p', 'Match the specification and lead spacing. Add to your Parts Tray, then install on the board.', 'tsj-store-intro');
    var browse = append(storefront, 'div', undefined, 'tsj-store-browse');
    controls.shopNav = append(browse, 'nav', undefined, 'tsj-store-categories');
    controls.shopNav.setAttribute('aria-label', 'Component categories');
    var searchRow = append(browse, 'div', undefined, 'tsj-store-search');
    controls.shopSearch = field(searchRow, 'Search components');
    controls.shopSearch.type = 'search'; controls.shopSearch.placeholder = 'Type or specification, e.g. 330';
    controls.shopSearch.autocomplete = 'off';
    controls.shopSearch.addEventListener('input', filterShop);
    button(searchRow, 'Clear search', function () {
      controls.shopSearch.value = ''; filterShop(); controls.shopSearch.focus();
    });
    controls.shopNote = append(storefront, 'p', undefined, 'tsj-product-notice tsj-store-note');
    controls.shopResults = append(storefront, 'p', undefined, 'tsj-store-results');
    controls.shopResults.setAttribute('role', 'status');
    controls.shopList = append(storefront, 'div', undefined, 'tsj-store-grid');
    controls.shopEmpty = append(storefront, 'p', 'No components match. Try another search or choose All parts.', 'tsj-store-empty');
    shopSignature = ''; shopRows = []; shopCategories = []; shopCategory = ''; updateShop();
  }
  function filterShop() {
    // Filtering only changes presentation; every recipe comes from the live catalog.
    var words = controls.shopSearch.value.toLowerCase().trim().split(/\s+/);
    var shown = 0;
    shopRows.forEach(function (row) {
      row.card.hidden = !!((shopCategory && row.type !== shopCategory) || words.some(function (word) {
        return row.search.indexOf(word) < 0;
      }));
      if (!row.card.hidden) shown++;
    });
    shopCategories.forEach(function (category) {
      category.button.setAttribute('aria-pressed', String(category.id === shopCategory));
    });
    setText(controls.shopResults, 'Showing ' + shown + ' of ' + shopRows.length + ' components');
    controls.shopEmpty.hidden = shown !== 0 || shopRows.length === 0;
  }
  function componentIllustration(parent, type) {
    // Decorative type silhouettes, not package drawings or electrical nameplates.
    // In particular the resistor has no value bands and LEDs have no color claim.
    var shapes = {
      RESISTOR: ['M10 32H36 M84 32H110', 'M41 22H79Q84 22 84 27V37Q84 42 79 42H41Q36 42 36 37V27Q36 22 41 22Z'],
      CAPACITOR: ['M49 44V59 M71 44V59', 'M40 16Q40 9 60 9Q80 9 80 16V41Q80 48 60 48Q40 48 40 41Z', 'M40 16Q60 24 80 16'],
      DIODE: ['M10 32H39 M81 32H110', 'M43 23H77Q81 23 81 27V37Q81 41 77 41H43Q39 41 39 37V27Q39 23 43 23Z', 'M72 24V40'],
      LED: ['M53 45V59 M67 45V56', 'M44 41V28A16 16 0 0 1 76 28V41Z', 'M40 43H80 M51 27Q51 20 58 19'],
      NPN_TRANSISTOR: ['M49 39V58 M60 39V58 M71 39V58', 'M40 20Q60 3 80 20V39H40Z'],
      NMOS_TRANSISTOR: ['M49 43V59 M60 43V59 M71 43V59', 'M50 24V9H70V24 M43 24H77V44H43Z', 'M63 16A3 3 0 1 1 57 16A3 3 0 1 1 63 16'],
      RELAY: ['M38 46V57 M49 46V57 M60 46V57 M71 46V57 M82 46V57', 'M31 14H89V46H31Z', 'M42 31H46C46 18 56 18 56 31C56 18 66 18 66 31C66 18 76 18 76 31H80']
    };
    if (!shapes[type]) return;
    var svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('viewBox', '0 0 120 64'); svg.setAttribute('class', 'tsj-store-component');
    svg.setAttribute('aria-hidden', 'true'); svg.setAttribute('focusable', 'false');
    shapes[type].forEach(function (shape, index) {
      var path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      path.setAttribute('d', shape); path.setAttribute('class', index === 1 ? 'tsj-store-component-body' : 'tsj-store-component-line');
      svg.appendChild(path);
    });
    parent.appendChild(svg);
  }
  function updateShop() {
    if (!controls.shopList) return;
    var catalogs = snapshot.catalogs || [];
    var signature = JSON.stringify(catalogs.map(function (catalog) {
      return [catalog.id, catalog.title, catalog.entries];
    }));
    if (signature !== shopSignature) {
      // Ephemeral recipe handles must not survive a changed catalog interpretation.
      if (shopSignature) { invalidateView(); lease = bridge.openView(); }
      shopSignature = signature; shopRows = []; shopCategories = [];
      controls.shopList.replaceChildren(); controls.shopNav.replaceChildren();
      if (!catalogs.some(function (catalog) { return catalog.id === shopCategory; })) shopCategory = '';
      function categoryButton(id, title) {
        var node = button(controls.shopNav, title, function () { shopCategory = id; filterShop(); });
        shopCategories.push({ id: id, button: node });
      }
      categoryButton('', 'All parts');
      catalogs.forEach(function (catalog) {
        categoryButton(catalog.id, catalog.title);
        catalog.entries.forEach(function (entry) {
          var card = append(controls.shopList, 'article', undefined, 'tsj-store-card');
          componentIllustration(card, catalog.id);
          append(card, 'p', catalog.title, 'tsj-store-type');
          var title = append(card, 'h4', entry.label);
          title.id = 'tsj-store-spec-' + (++serial); card.setAttribute('aria-labelledby', title.id);
          var token = snapshot.token, view = lease;
          var acquire = button(card, 'Add to Parts Tray', function () {
            invoke(token, view, 'acquire', catalog.id, entry.id);
          }, 'tsj-product-button tsj-store-add');
          acquire.setAttribute('aria-label', 'Add to Parts Tray: ' + catalog.title + ', ' + entry.label);
          shopRows.push({ type: catalog.id, card: card, acquire: acquire, search: (catalog.title + ' ' + entry.label).toLowerCase() });
        });
      });
      if (!shopRows.length) append(controls.shopList, 'p', 'This board has no available components.');
      filterShop();
    }
    var available = snapshot.screen === 'WORKBENCH' && snapshot.ready && snapshot.isolated && !snapshot.completed;
    setText(controls.shopNote, snapshot.completed ? 'This board is complete. Start another board to acquire parts.' :
      available ? 'Supplies isolated · Ready to add parts to your tray.' : 'Switch off every board supply and wait for settling before adding parts to your tray.');
    var looseCount = catalogs.reduce(function (total, catalog) { return total + catalog.looseCount; }, 0);
    setText(controls.shopTrayCount, 'Parts Tray · ' + looseCount + ' loose ' + (looseCount === 1 ? 'part' : 'parts'));
    shopRows.forEach(function (row) {
      row.acquire.disabled = !available;
    });
  }

  function resources(parent) {
    var sections = [
      ['Inspect and navigate', 'Use Fit board or Fit selection, Zoom + / −, and the top/bottom copper controls. The mouse wheel zooms; Shift-drag or middle-drag pans. Hold Space over the board for the inspection loupe; release it to return to the permanent view.'],
      ['Place probes', 'Select DCV, Ohms, continuity or diode mode. Left click places the red probe; right click places the black probe. Selecting the active mode again exits it. Use accessible pads, terminals or exposed copper.'],
      ['DC voltage', 'Measure between two electrical endpoints with the required supplies on. The sign is red relative to black. Observe the instrument’s reference-domain and readiness messages.'],
      ['Ohms and continuity', 'Disconnect all board supplies and allow stored energy to discharge. In-circuit readings include parallel paths: a low resistance or continuity tone does not by itself identify a failed part. Isolate a lead or remove a part when needed to distinguish paths.'],
      ['Diode mode', 'Use an isolated, discharged board. The instrument applies a test current; polarity and parallel paths affect the reading. Reverse the probes to compare directions. Follow any settling or unsupported-measurement message.'],
      ['Repair and verify', 'Isolate every supply and wait for discharge. Remove the part to the Parts Tray, acquire an appropriate loose replacement from Shop, select it and install it into the empty slot. Restore the relevant supplies and inputs, then run the customer retest. A part swap alone does not establish that the customer’s problem is fixed.']
    ];
    sections.forEach(function (section) { append(parent, 'h3', section[0]); append(parent, 'p', section[1]); });
    append(parent, 'h3', 'Resistor color reference');
    append(parent, 'p', 'Four bands: first two digits × multiplier, then tolerance. Five bands: first three digits × multiplier, then tolerance. Example: brown–black–red–gold = 1 kΩ ±5%. Read from the end opposite the spaced tolerance band.');
    var table = append(parent, 'table', undefined, 'tsj-product-reference');
    append(table, 'caption', 'Resistor bands: digit, multiplier and tolerance');
    var head = append(append(table, 'thead'), 'tr');
    ['Color', 'Digit', 'Multiplier', 'Tolerance'].forEach(function (label) { var th = append(head, 'th', label); th.scope = 'col'; });
    var body = append(table, 'tbody');
    [['Black','0','×1','—'],['Brown','1','×10','±1%'],['Red','2','×100','±2%'],['Orange','3','×1 k','—'],['Yellow','4','×10 k','—'],['Green','5','×100 k','±0.5%'],['Blue','6','×1 M','±0.25%'],['Violet','7','×10 M','±0.1%'],['Gray','8','×100 M','±0.05%'],['White','9','×1 G','—'],['Gold','—','×0.1','±5%'],['Silver','—','×0.01','±10%'],['None','—','—','±20%']].forEach(function (row) {
      var tr = append(body, 'tr'); row.forEach(function (value) { append(tr, 'td', value); });
    });
    support(parent); identity(parent);
  }
  function applySettings() {
    document.body.classList.toggle('tsj-high-contrast', settings.highContrast);
    document.body.classList.toggle('tsj-large-text', settings.largeText);
    document.body.classList.toggle('tsj-reduced-motion', settings.reducedMotion);
  }
  function loadSettings() {
    try {
      var raw = window.localStorage.getItem(settingsKey);
      if (raw !== null) {
        var stored = JSON.parse(raw), keys = ['version', 'highContrast', 'largeText', 'reducedMotion'];
        if (!stored || Array.isArray(stored) || Object.keys(stored).length !== keys.length || stored.version !== 1 ||
            keys.slice(1).some(function (key) { return typeof stored[key] !== 'boolean'; })) throw new Error('Invalid presentation format');
        settings = stored;
      }
      settingsNotice = 'Presentation preferences are stored in this browser when you change them.';
    } catch (error) {
      settingsNotice = 'Saved preferences could not be read. Defaults apply for this session; a change will attempt to save them.';
    }
    applySettings();
  }
  function settingsPage(parent) {
    append(parent, 'p', 'These preferences affect interface presentation only. Board physics, geometry, probe targets and instrument behavior stay controlled by the simulator.');
    var note = append(parent, 'p', settingsNotice, 'tsj-product-notice'); note.setAttribute('role', 'status');
    [['highContrast', 'Higher contrast interface'], ['largeText', 'Larger interface text'], ['reducedMotion', 'Reduce interface motion']].forEach(function (item) {
      var label = append(parent, 'label', undefined, 'tsj-product-check');
      var input = append(label, 'input'); input.type = 'checkbox'; input.checked = settings[item[0]];
      append(label, 'span', item[1]);
      input.addEventListener('change', function () {
        settings[item[0]] = input.checked; applySettings();
        try {
          window.localStorage.setItem(settingsKey, JSON.stringify(settings));
          settingsNotice = 'Preferences saved in this browser.';
        } catch (error) { settingsNotice = 'Storage is unavailable. These preferences apply only to this session.'; }
        setText(note, settingsNotice);
      });
    });
    append(parent, 'p', 'Session saves are not available in this alpha.');
  }

  function mountShell() {
    var anchor = document.querySelector('.tsj-meter-panel');
    if (anchor && anchor.parentNode && shell.parentNode !== anchor.parentNode) {
      anchor.parentNode.insertBefore(shell, anchor);
      anchor.parentNode.classList.add('tsj-workbench-sidebar-cell');
      var table = anchor.closest('table'); if (table) table.classList.add('tsj-workbench-sidebar-host');
    } else if (!shell.isConnected) document.body.appendChild(shell);
    shell.classList.toggle('tsj-product-floating', shell.parentNode === document.body);
  }
  function initialize() {
    bridge = window.tsjProduct;
    if (!bridge || !document.body) return false;
    if (shell) return true;
    document.body.classList.add('tsj-workbench-ui');
    loadSettings();
    shell = element('div', undefined, 'tsj-workbench-shell');
    var nav = append(shell, 'nav', undefined, 'tsj-ui-strip'); nav.setAttribute('aria-label', 'Workbench navigation');
    controls.menu = button(nav, 'Main menu', function () { invoke(snapshot.token, 0, 'menu'); });
    controls.shop = button(nav, 'Shop', function () { openAuxiliary('Shop'); });
    button(nav, 'Resources', function () { openAuxiliary('Resources'); });
    button(nav, 'Settings', function () { openAuxiliary('Settings'); });
    controls.retest = button(nav, 'Run customer retest', function () { invoke(snapshot.token, 0, 'retest'); }, 'tsj-product-button tsj-product-primary');
    shellStatus = append(nav, 'p', undefined, 'tsj-product-notice'); shellStatus.setAttribute('role', 'status');
    overlay = element('div', undefined, 'tsj-ui-overlay'); overlay.hidden = true;
    dialog = append(overlay, 'section', undefined, 'tsj-ui-dialog'); dialog.tabIndex = -1;
    dialog.setAttribute('role', 'dialog'); dialog.setAttribute('aria-modal', 'true'); dialog.setAttribute('aria-labelledby', 'tsj-product-heading');
    var header = append(dialog, 'header', undefined, 'tsj-ui-dialog-header');
    heading = append(header, 'h2'); heading.id = 'tsj-product-heading';
    closeButton = button(header, 'Close', closeAuxiliary, 'tsj-product-button tsj-ui-close');
    modalStatus = append(dialog, 'p', undefined, 'tsj-product-notice'); modalStatus.setAttribute('role', 'status'); modalStatus.setAttribute('aria-live', 'polite');
    content = append(dialog, 'div', undefined, 'tsj-product-content');
    document.body.appendChild(overlay); mountShell();
    ['click', 'dblclick', 'contextmenu', 'pointerdown', 'pointerup', 'mousedown', 'mouseup', 'wheel'].forEach(function (name) {
      overlay.addEventListener(name, function (event) { event.stopPropagation(); });
    });
    return true;
  }
  function render() {
    if (!initialize()) return;
    var next = bridge.snapshot();
    if (snapshot && (snapshot.token !== next.token || snapshot.screen !== next.screen)) {
      auxiliary = ''; localMessage = ''; invalidateView(); renderedKey = '';
      savedContent = null; savedStart = null; auxiliaryFocus = null;
    }
    snapshot = next; mountShell();
    controls.retest.disabled = snapshot.screen !== 'WORKBENCH' || !snapshot.ready || snapshot.completed;
    controls.shop.disabled = snapshot.screen !== 'WORKBENCH' || snapshot.completed;
    controls.menu.disabled = snapshot.screen === 'PREPARING' || snapshot.screen === 'RETEST';
    setText(shellStatus, localMessage || snapshot.notice || snapshot.message);
    var page = auxiliary || (snapshot.screen === 'WORKBENCH' ? '' : snapshot.screen);
    dialog.classList.toggle('tsj-store-dialog', page === 'Shop');
    var key = page + ':' + snapshot.token;
    if (!page) {
      if (window.tsjWorkbenchOverlayOpen) hideOverlay();
      renderedKey = key; return;
    }
    if (key !== renderedKey) {
      invalidateView(); lease = bridge.openView();
      content.replaceChildren(); controls.shopList = null; controls.start = null;
      closeButton.hidden = !auxiliary;
      var titles = { MENU: 'TroubleshootJS · Desktop alpha', PREPARING: 'Preparing board', TICKET: 'Customer ticket', RETEST: 'Running customer retest', RESULTS: 'Customer retest passed', ERROR: 'Board preparation failed' };
      setText(heading, titles[page] || page);
      if (!auxiliary && savedContent) {
        savedContent.forEach(function (node) { content.appendChild(node); });
        controls.start = savedStart; savedContent = null; savedStart = null;
      }
      else if (page === 'MENU') menu(content);
      else if (page === 'TICKET') ticket(content);
      else if (page === 'RESULTS') results(content);
      else if (page === 'Shop') shop(content);
      else if (page === 'Resources') resources(content);
      else if (page === 'Settings') settingsPage(content);
      else if (page === 'PREPARING') {
        append(content, 'p', 'The simulator is proving healthy behavior, a meaningful symptom and diagnostic access before publishing the board. Preparation may take a minute or more.');
        actionButton(content, 'Cancel preparation', 'cancel');
      } else if (page === 'RETEST') append(content, 'p', 'Checking the live board against the customer’s required behavior.');
      else {
        append(content, 'p', 'The previous board, if any, is retained. Use the main menu to choose another seed or enter a supported current replay.');
        actionButton(content, 'Main menu', 'menu');
        if (snapshot.hasBoard) actionButton(content, 'Resume previous board', 'resume');
        identity(content);
      }
      renderedKey = key; showOverlay(); content.scrollTop = 0; focusDialog();
    }
    if (page === 'Shop') updateShop();
    if (controls.start) controls.start.disabled = !snapshot.ready;
    setText(modalStatus, page === 'Shop' ? localMessage : localMessage || snapshot.notice || snapshot.message);
  }
  function schedule() {
    if (queued) return;
    queued = true;
    window.setTimeout(function () {
      queued = false; render();
      if (shell && discovery) { discovery.disconnect(); discovery = null; }
    }, 0);
  }
  window.tsjProductRefresh = schedule;
  function start() {
    if (!document.body) return;
    schedule();
    if (!window.tsjProduct && window.MutationObserver) {
      discovery = new window.MutationObserver(function () { if (window.tsjProduct) schedule(); });
      discovery.observe(document.body, { childList: true, subtree: true });
    }
  }
  document.addEventListener('keydown', function (event) {
    if (!window.tsjWorkbenchOverlayOpen) return;
    if (event.key === 'Escape') {
      event.preventDefault(); closeAuxiliary(); // Blocking session screens require an explicit action.
    } else if ((event.key === 'Enter' || event.key === ' ') &&
        document.activeElement.tagName === 'BUTTON' && dialog.contains(document.activeElement)) {
      // GWT also handles document keys. Activate once here and suppress its
      // native duplicate, including repeated keydown while Space is held.
      event.preventDefault();
      if (!event.repeat && !document.activeElement.disabled) document.activeElement.click();
    } else if (event.key === 'Tab') {
      var nodes = focusable(), index = nodes.indexOf(document.activeElement);
      if (!nodes.length) { event.preventDefault(); dialog.focus(); }
      else if (event.shiftKey && index <= 0) { event.preventDefault(); nodes[nodes.length - 1].focus(); }
      else if (!event.shiftKey && (index < 0 || index === nodes.length - 1)) { event.preventDefault(); nodes[0].focus(); }
    }
    event.stopImmediatePropagation();
  }, true);
  ['keypress', 'keyup'].forEach(function (name) {
    document.addEventListener(name, function (event) { if (window.tsjWorkbenchOverlayOpen) event.stopImmediatePropagation(); }, true);
  });
  document.addEventListener('focusin', function (event) {
    if (window.tsjWorkbenchOverlayOpen && dialog && !dialog.contains(event.target)) focusDialog();
  }, true);
  window.addEventListener('blur', function () {
    // Closing Shop revokes retained acquisition callbacks across lost window focus.
    if (auxiliary === 'Shop') closeAuxiliary();
  });
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();
}(window, document));
