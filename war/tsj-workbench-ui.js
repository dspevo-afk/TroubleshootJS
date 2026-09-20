/* Product presentation only. CircuitJS owns sessions, inventory and electrical truth. */
(function (window, document) {
  'use strict';
  var shell, overlay, dialog, content, heading, closeButton, modalStatus, shellStatus;
  var bridge, snapshot, discovery, backgroundObserver, queued = false;
  var auxiliary = '', renderedKey = '', lease = null, returnFocus = null, auxiliaryFocus = null;
  var savedContent = null, savedStart = null;
  var background = [], controls = {}, shopRows = [], shopCategories = [], shopSignature = '', shopCategory = '';
  var localMessage = '', settingsNotice = '', serial = 0, progressTimer = null;
  var toolbarHost = null, toolbarObserver = null, toolbarQueued = false, nativeTools = [];
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
  function icon(parent, name) {
    // Local, decorative interface glyphs never introduce additional controls.
    var paths = {
      board: 'M7 3H4V21H20V3H17 M8 3H16V9H8Z M8 15H12V19H8Z M16 13V18H20 M4 11H8',
      menu: 'M4 6H20 M4 12H20 M4 18H20',
      shop: 'M4 9V20H20V9 M3 9L5 3H19L21 9 M3 9Q6 13 9 9Q12 13 15 9Q18 13 21 9 M9 20V15H15V20',
      resources: 'M12 5Q7 2 3 4V20Q7 18 12 21Q17 18 21 20V4Q17 2 12 5V21',
      settings: 'M4 6H20 M4 12H20 M4 18H20 M8 3V9 M16 9V15 M10 15V21',
      retest: 'M20 8A9 9 0 1 1 16 4 M8 11L12 15L21 5',
      power: 'M9 3V8 M15 3V8 M7 8H17V11A5 5 0 0 1 7 11Z M12 16V21',
      view: 'M3 8V3H8 M16 3H21V8 M21 16V21H16 M8 21H3V16 M8 12H16 M12 8V16',
      ticket: 'M8 5H5V21H19V5H16 M8 3H16V7H8Z M8 12H16 M8 16H14',
      component: 'M7 7H17V17H7Z M9 3V7 M15 3V7 M9 17V21 M15 17V21 M3 9H7 M3 15H7 M17 9H21 M17 15H21',
      parts: 'M3 7H21V20H3Z M3 7L6 3H18L21 7 M9 7V11H15V7'
    };
    var svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('viewBox', '0 0 24 24'); svg.setAttribute('class', 'tsj-ui-icon');
    svg.setAttribute('aria-hidden', 'true'); svg.setAttribute('focusable', 'false');
    var path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    if (parent.tagName === 'BUTTON') {
      var label = parent.textContent;
      parent.replaceChildren(element('span', label, 'tsj-button-label'));
      if (!parent.hasAttribute('aria-label')) parent.setAttribute('aria-label', label);
      if (!parent.title) parent.title = label;
    }
    path.setAttribute('d', paths[name]); svg.appendChild(path); parent.insertBefore(svg, parent.firstChild);
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
    var bytes = new Uint8Array(8), i, j, carry;
    if (window.crypto && window.crypto.getRandomValues) window.crypto.getRandomValues(bytes);
    else for (i = 0; i < 8; i++) bytes[i] = Math.floor(Math.random() * 256);
    var negative = (bytes[0] & 128) !== 0;
    if (negative) {
      carry = 1;
      for (i = 7; i >= 0; i--) {
        carry += 255 - bytes[i]; bytes[i] = carry & 255; carry >>>= 8;
      }
    }
    // Exact base-256 to decimal conversion, never a 64-bit Number or BigInt dependency.
    var decimal = '0';
    for (i = 0; i < 8; i++) {
      carry = bytes[i]; var digits = decimal.split('');
      for (j = digits.length - 1; j >= 0; j--) {
        carry += Number(digits[j]) * 256;
        digits[j] = String(carry % 10); carry = Math.floor(carry / 10);
      }
      decimal = (carry ? String(carry) : '') + digits.join('');
    }
    return (negative ? '-' : '') + decimal;
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
    var note = append(parent, 'section', undefined, 'tsj-product-support');
    append(note, 'h3', 'About this desktop alpha');
    append(note, 'p', 'Selected low-voltage boards in the 5–20-part range, including a 16-part procedural control board with routed bottom copper. The 3-part indicator and 4-part protected indicator are introductory practice boards.');
    append(note, 'p', 'EASY and MEDIUM candidates are checked before play. HARD and PSYCHOTIC are unavailable. Mains circuits, larger boards and general multilayer routing are not supported. Session saves are not available.');
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
    closeNativeTools();
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
    // The bench receives the WORKBENCH snapshot just before this transition.
    // Re-sync after the overlay gate opens so its real instrument controls do
    // not remain disabled from the ticket/preparation screen.
    if (window.tsjBenchInstruments && snapshot) window.tsjBenchInstruments.mount(snapshot);
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
    var intro = append(parent, 'header', undefined, 'tsj-product-intro');
    append(intro, 'p', 'THE ELECTRONICS REPAIR GAME', 'tsj-product-eyebrow');
    append(intro, 'h3', 'Troubleshoot!', 'tsj-retro-title');
    append(intro, 'p', 'A customer. A faulty board. A bench full of possibilities.');
    var process = append(intro, 'ol', undefined, 'tsj-product-process');
    ['Examine', 'Measure', 'Isolate', 'Repair', 'Retest'].forEach(function (step) { append(process, 'li', step); });
    var setup = append(parent, 'div', undefined, 'tsj-product-setup');
    var form = append(setup, 'form', undefined, 'tsj-product-form tsj-product-new-board');
    append(form, 'h3', 'Start a service job');
    var choices = append(form, 'div', undefined, 'tsj-product-choices');
    var profile = field(choices, 'Difficulty', 'select');
    ['EASY', 'MEDIUM', 'HARD', 'PSYCHOTIC'].forEach(function (name) {
      var option = append(profile, 'option', name + (name === 'HARD' || name === 'PSYCHOTIC' ? ' — unavailable' : ''));
      option.value = name; option.disabled = name === 'HARD' || name === 'PSYCHOTIC';
    });
    var family = field(choices, 'Board family', 'select');
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
    append(form, 'p', 'Every listed family uses procedural placement and routing. New boards try up to four candidate seeds within shared preparation limits; only a fully checked, distinct board reaches its customer ticket.', 'tsj-product-hint');
    var exact = append(form, 'details', undefined, 'tsj-product-exact');
    append(exact, 'summary', 'Enter an exact seed');
    var seed = field(exact, 'Exact seed (signed decimal integer)');
    seed.type = 'text'; seed.value = '0'; seed.required = true;
    seed.maxLength = 20; seed.autocomplete = 'off'; seed.spellcheck = false;
    append(exact, 'p', 'An exact seed can fail preparation. It is never replaced with another seed.', 'tsj-product-hint');
    var launch = append(exact, 'button', 'Prepare exact seed', 'tsj-product-button'); launch.type = 'submit';
    form.addEventListener('submit', function (event) {
      event.preventDefault(); invoke(token, view, 'launch', family.value, seed.value, profile.value);
    });
    var replayDetails = append(parent, 'details', undefined, 'tsj-product-replay-disclosure');
    append(replayDetails, 'summary', 'Open a saved replay code');
    var replayForm = append(replayDetails, 'form', undefined, 'tsj-product-form tsj-product-replay');
    append(replayForm, 'h3', 'Return to a challenge');
    append(replayForm, 'p', 'Paste a current replay to reconstruct its initial board. Repairs and tray contents are not saved.', 'tsj-product-hint');
    var replay = field(replayForm, 'Open current replay', 'textarea'); replay.rows = 2; replay.required = true; replay.spellcheck = false;
    var open = append(replayForm, 'button', 'Prepare replay', 'tsj-product-button'); open.type = 'submit';
    replayForm.addEventListener('submit', function (event) { event.preventDefault(); invoke(token, view, 'replay', replay.value); });
    if (snapshot.hasBoard) actionButton(parent, 'Resume current board', 'resume');
    var links = append(parent, 'div', undefined, 'tsj-product-actions');
    button(links, 'Resources', function () { openAuxiliary('Resources'); });
    button(links, 'Settings', function () { openAuxiliary('Settings'); });
    var about = append(parent, 'details', undefined, 'tsj-product-about');
    append(about, 'summary', 'About this alpha'); support(about); identity(parent);
  }
  function ticket(parent) {
    var sheet = append(parent, 'article', undefined, 'tsj-product-ticket');
    append(sheet, 'p', 'SERVICE JOB', 'tsj-product-eyebrow');
    append(sheet, 'h3', 'Customer complaint'); append(sheet, 'p', snapshot.complaint, 'tsj-product-complaint');
    var retest = append(sheet, 'section', undefined, 'tsj-product-retest');
    append(retest, 'h3', 'Functional retest'); append(retest, 'p', snapshot.retestInstruction);
    append(parent, 'p', 'Examine the board and use the instruments to identify a repair. Switch off every supply and wait for stored energy to discharge before changing parts.', 'tsj-product-hint');
    var actions = append(parent, 'div', undefined, 'tsj-product-actions');
    controls.start = actionButton(actions, 'Accept ticket and start', 'resume');
    controls.start.classList.add('tsj-product-primary');
    actionButton(actions, 'Main menu', 'menu');
    button(actions, 'Resources', function () { openAuxiliary('Resources'); });
    identity(parent);
  }
  function results(parent) {
    var result = append(parent, 'section', undefined, 'tsj-product-ticket tsj-product-success');
    append(result, 'p', 'FUNCTION VERIFIED', 'tsj-product-eyebrow');
    append(result, 'h3', 'Ready to return to the customer.');
    append(result, 'p', 'The live board passed the customer’s functional checks.');
    append(result, 'p', 'You can keep using this board. Later changes do not alter the recorded retest result.');
    append(result, 'p', snapshot.message);
    var actions = append(parent, 'div', undefined, 'tsj-product-actions');
    actionButton(actions, 'New board / Main menu', 'menu').classList.add('tsj-product-primary');
    actionButton(actions, 'Replay this challenge', 'replay', snapshot.replay);
    actionButton(actions, 'Return to board', 'resume'); identity(parent);
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
    var intro = append(storefront, 'section', undefined, 'tsj-store-intro');
    append(intro, 'h3', 'The right part starts with the specification.');
    append(intro, 'p', 'Browse the full catalog. Match the rating and lead spacing, add to your Parts Tray, then install on the board.');
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
    var available = snapshot.screen === 'WORKBENCH' && snapshot.ready && snapshot.isolated;
    setText(controls.shopNote, available ? 'Supplies isolated · Ready to add parts to your tray.' :
      'Switch off every board supply and wait for settling before adding parts to your tray.');
    var looseCount = catalogs.reduce(function (total, catalog) { return total + catalog.looseCount; }, 0);
    setText(controls.shopTrayCount, 'Parts Tray · ' + looseCount + ' loose ' + (looseCount === 1 ? 'part' : 'parts'));
    shopRows.forEach(function (row) {
      row.acquire.disabled = !available;
    });
  }

  function resources(parent) {
    var sections = [
      ['Navigate the bench', 'Use Fit bench, Zoom + / −, and the top/bottom copper controls. The mouse wheel zooms; Shift-drag or middle-drag pans. Hold Space over the board for the inspection loupe; release it to return to the permanent view.'],
      ['Place probes', 'Select DCV, AC V~, Ohms, continuity, diode or scope/frequency mode. Left click places the red probe; right click places the black probe. Selecting the active mode again exits it. Use accessible pads, terminals or exposed copper.'],
      ['DC voltage', 'Measure between two electrical endpoints with the required supplies on. The sign is red relative to black. Observe the instrument’s reference-domain and readiness messages.'],
      ['AC voltage (V~)', 'AC RMS is a differential red-minus-black measurement. The red and black probe endpoints define the reference; interpret the result only as that differential measurement. Keep the required supplies on and follow any readiness, range or unsupported-measurement message.'],
      ['Scope and frequency (Hz)', 'The scope uses a red/black differential reference and solver-time samples, not screen-frame timing. An insufficient or aliased display is not a measurement. Treat clipped, unavailable or unsupported views as inconclusive and follow the instrument message.'],
      ['Ohms and continuity', 'Disconnect all board supplies and allow stored energy to discharge. In-circuit readings include parallel paths: a low resistance or continuity tone does not by itself identify a failed part. Isolate a lead or remove a part when needed to distinguish paths.'],
      ['Diode mode', 'Use an isolated, discharged board. The instrument applies a test current; polarity and parallel paths affect the reading. Reverse the probes to compare directions. Follow any settling or unsupported-measurement message.'],
      ['Repair and verify', 'Isolate every supply and wait for discharge. Remove the part to the Parts Tray, acquire an appropriate loose replacement from Shop, select it and install it into the empty slot. Restore the relevant supplies and inputs, then run the customer retest. A part swap alone does not establish that the customer’s problem is fixed.']
    ];
    append(parent, 'p', 'Measurement, navigation and repair references for the workbench.', 'tsj-product-hint');
    var guides = append(parent, 'div', undefined, 'tsj-product-guides');
    sections.forEach(function (section) {
      var guide = append(guides, 'section', undefined, 'tsj-product-guide');
      append(guide, 'h3', section[0]); append(guide, 'p', section[1]);
    });
    append(parent, 'h3', 'Resistor color reference');
    append(parent, 'p', 'Four bands: first two digits × multiplier, then tolerance. Five bands: first three digits × multiplier, then tolerance. Example: brown–black–red–gold = 1 kΩ ±5%. Read from the end opposite the spaced tolerance band.');
    var table = append(parent, 'table', undefined, 'tsj-product-reference');
    append(table, 'caption', 'Resistor bands: digit, multiplier and tolerance');
    var head = append(append(table, 'thead'), 'tr');
    ['Color', 'Digit', 'Multiplier', 'Tolerance'].forEach(function (label) { var th = append(head, 'th', label); th.scope = 'col'; });
    var body = append(table, 'tbody');
    [['Black','0','×1','—'],['Brown','1','×10','±1%'],['Red','2','×100','±2%'],['Orange','3','×1 k','—'],['Yellow','4','×10 k','—'],['Green','5','×100 k','±0.5%'],['Blue','6','×1 M','±0.25%'],['Violet','7','×10 M','±0.1%'],['Gray','8','×100 M','±0.05%'],['White','9','×1 G','—'],['Gold','—','×0.1','±5%'],['Silver','—','×0.01','±10%'],['None','—','—','±20%']].forEach(function (row) {
      var tr = append(body, 'tr'); tr.setAttribute('data-band', row[0].toLowerCase());
      row.forEach(function (value) { append(tr, 'td', value); });
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
    append(parent, 'p', 'Make the workbench comfortable to read. These preferences change interface presentation; board and instrument behavior stays the same.', 'tsj-product-hint');
    var note = append(parent, 'p', settingsNotice, 'tsj-product-notice'); note.setAttribute('role', 'status');
    [['highContrast', 'Higher contrast interface', 'Increase contrast for panels, text and controls.'], ['largeText', 'Larger interface text', 'Increase the size of text in menus and workbench controls.'], ['reducedMotion', 'Reduce interface motion', 'Keep interface transitions and scrolling immediate.']].forEach(function (item) {
      var label = append(parent, 'label', undefined, 'tsj-product-check');
      var input = append(label, 'input'); input.type = 'checkbox'; input.checked = settings[item[0]];
      var description = append(label, 'span');
      var title = append(description, 'strong', item[1]); title.id = 'tsj-preference-title-' + (++serial);
      var hint = append(description, 'small', item[2]); hint.id = 'tsj-preference-hint-' + serial;
      input.setAttribute('aria-labelledby', title.id); input.setAttribute('aria-describedby', hint.id);
      input.addEventListener('change', function () {
        settings[item[0]] = input.checked; applySettings();
        try {
          window.localStorage.setItem(settingsKey, JSON.stringify(settings));
          settingsNotice = 'Preferences saved in this browser.';
        } catch (error) { settingsNotice = 'Storage is unavailable. These preferences apply only to this session.'; }
        setText(note, settingsNotice);
      });
    });
    if (window.tsjBenchAudio) {
      var audioLabel = append(parent, 'label', undefined, 'tsj-product-check');
      var audioInput = append(audioLabel, 'input'); audioInput.type = 'checkbox';
      audioInput.checked = !window.tsjBenchAudio.getMuted();
      audioInput.setAttribute('aria-label', 'Relay contact sounds');
      var audioText = append(audioLabel, 'span');
      append(audioText, 'strong', 'Relay contact sounds');
      append(audioText, 'small', 'Mechanical clicks follow actual relay pickup and release.');
      audioInput.addEventListener('change', function () { window.tsjBenchAudio.setMuted(!audioInput.checked); });
    }
    append(parent, 'p', 'Session saves are not available in this alpha.');
  }

  function progressView(parent) {
    var panel = append(parent, 'section', undefined, 'tsj-generation-progress');
    append(panel, 'div', 'CIRCUIT CHECK', 'tsj-product-eyebrow');
    controls.progressLabel = append(panel, 'h3', 'Resolve circuit');
    controls.progress = append(panel, 'progress'); controls.progress.max = 100; controls.progress.value = 0;
    controls.progress.setAttribute('aria-label', 'Board preparation: completed verification stages');
    controls.progressCount = append(panel, 'strong', '0%');
    controls.progressDetail = append(panel, 'p', 'Starting the simulation...', 'tsj-progress-detail');
    controls.progressDetail.setAttribute('role', 'status');
    controls.progressSteps = append(panel, 'ol', undefined, 'tsj-progress-stages');
    ['Resolve', 'Build & test', 'PCB', 'Measurements', 'Ticket', 'Ready'].forEach(function (name) {
      append(controls.progressSteps, 'li', name);
    });
  }
  function updateProgress() {
    if (!controls.progress) return;
    var p = snapshot.progress;
    if (!p) {
      controls.progress.removeAttribute('value');
      setText(controls.progressCount, '');
      setText(controls.progressLabel, 'Starting circuit checks');
      setText(controls.progressDetail, 'Waiting for the current build to report its first completed step.');
      return;
    }
    controls.progress.value = p.percent;
    controls.progress.setAttribute('aria-valuetext', p.label + ', stage ' + p.phase + ' of ' + p.phases);
    setText(controls.progressLabel, p.label); setText(controls.progressCount, p.percent + '%');
    if (p.paused) {
      setText(controls.progressLabel, 'Preparation paused');
      setText(controls.progressDetail, 'This tab is in the background. Return here to continue the same checks; no work is discarded.');
      return;
    }
    var age = Math.floor(p.lastUpdateAgeMs / 1000);
    setText(controls.progressDetail, Math.floor(p.elapsedMs / 1000) + 's elapsed / ' + p.units +
      ' work units completed' + (age >= 3 ? ' / Last completed step ' + age + 's ago; working on the next check.' : ''));
    Array.prototype.forEach.call(controls.progressSteps.children, function (node, index) {
      node.classList.toggle('is-done', index + 1 < p.phase);
      node.classList.toggle('is-current', index + 1 === p.phase);
    });
  }

  function closeNativeTools() {
    nativeTools.forEach(function (tool) {
      if (tool.panel.isConnected && typeof tool.panel.hidePopover === 'function' && tool.panel.matches(':popover-open')) tool.panel.hidePopover();
    });
  }
  function positionNativeTool(tool) {
    var bounds = tool.trigger.getBoundingClientRect();
    // This positions a DOM tool panel only. It never touches the board viewport.
    var width = Math.min(340, Math.max(240, window.innerWidth - 24));
    tool.panel.style.setProperty('--tsj-tool-left', Math.max(12, Math.min(bounds.left, window.innerWidth - width - 12)) + 'px');
    tool.panel.style.setProperty('--tsj-tool-top', Math.min(bounds.bottom + 10, window.innerHeight - 120) + 'px');
  }
  function updateNativeTools() {
    if (!toolbarHost) return;
    nativeTools = nativeTools.filter(function (tool) { return toolbarHost.contains(tool.panel); });
    var labels = {
      'Bench power': ['Supplies', 'power'], 'Board view': ['View', 'view'],
      'Service ticket': ['Ticket', 'ticket'], 'Component': ['Component', 'component'],
      'Parts Tray': ['Parts tray', 'parts']
    };
    Array.prototype.forEach.call(toolbarHost.querySelectorAll('.tsj-component-panel'), function (panel) {
      var label = panel.getAttribute('aria-label'), presentation = labels[label];
      if (!presentation) return;
      var tool = nativeTools.filter(function (item) { return item.panel === panel; })[0];
      if (!tool) {
        // Keep each native table in its original GWT cell. The browser top layer
        // displays the same widget; no cloned controls or alternate owner exists.
        if (!panel.id) panel.id = 'tsj-native-tool-' + (++serial);
        panel.setAttribute('popover', 'auto'); panel.setAttribute('role', 'dialog');
        panel.classList.add('tsj-native-popover');
        var trigger = element('button', presentation[0], 'tsj-product-button tsj-tool-trigger');
        trigger.type = 'button'; trigger.title = label; trigger.setAttribute('aria-label', label);
        trigger.setAttribute('popovertarget', panel.id); trigger.setAttribute('aria-haspopup', 'dialog');
        icon(trigger, presentation[1]); panel.parentNode.insertBefore(trigger, panel);
        tool = { panel: panel, trigger: trigger }; nativeTools.push(tool);
        trigger.addEventListener('click', function () { positionNativeTool(tool); });
        panel.addEventListener('beforetoggle', function (event) {
          if (event.newState === 'open') {
            if (window.tsjWorkbenchOverlayOpen || panel.style.display === 'none') event.preventDefault();
            else positionNativeTool(tool);
          }
        });
      }
      tool.trigger.hidden = panel.style.display === 'none';
      if (tool.trigger.hidden && typeof panel.hidePopover === 'function' && panel.matches(':popover-open')) panel.hidePopover();
    });
    // GWT leaves layout rows behind for setVisible(false). Collapse only those
    // empty native rows so hidden legacy widgets cannot pad the instrument strip.
    if (!toolbarHost.tBodies[0]) return;
    Array.prototype.forEach.call(toolbarHost.tBodies[0].rows, function (row) {
      var visible = Array.prototype.some.call(row.cells[0].children, function (node) {
        return !node.hidden && node.style.display !== 'none';
      });
      row.classList.toggle('tsj-toolbar-empty', !visible);
    });
  }
  function scheduleNativeTools() {
    if (toolbarQueued) return;
    toolbarQueued = true;
    window.setTimeout(function () { toolbarQueued = false; updateNativeTools(); }, 0);
  }
  function mountShell() {
    var anchor = document.querySelector('.tsj-meter-panel');
    if (anchor && anchor.parentNode && shell.parentNode !== anchor.parentNode) {
      anchor.parentNode.insertBefore(shell, anchor);
      anchor.parentNode.classList.add('tsj-workbench-toolbar-cell');
      var table = anchor.parentNode.closest('table');
      if (table) {
        table.classList.add('tsj-workbench-toolbar-host');
        if (table !== toolbarHost) {
          if (toolbarObserver) toolbarObserver.disconnect();
          closeNativeTools(); nativeTools = []; toolbarHost = table;
          if (window.MutationObserver) {
            toolbarObserver = new window.MutationObserver(function (changes) {
              if (changes.some(function (change) {
                return change.type === 'attributes' || Array.prototype.some.call(change.addedNodes, function (node) { return node.nodeType === 1; }) ||
                  Array.prototype.some.call(change.removedNodes, function (node) { return node.nodeType === 1; });
              })) scheduleNativeTools();
            });
            toolbarObserver.observe(table, { childList: true, subtree: true, attributes: true, attributeFilter: ['style'] });
          }
        }
        updateNativeTools();
      }
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
    var brand = append(shell, 'header', undefined, 'tsj-workbench-brand');
    icon(brand, 'board');
    var brandText = append(brand, 'div');
    append(brandText, 'strong', 'TroubleshootJS');
    append(brandText, 'span', 'ELECTRONICS WORKBENCH');
    append(brand, 'span', 'ALPHA', 'tsj-alpha-badge');
    var nav = append(shell, 'nav', undefined, 'tsj-ui-strip'); nav.setAttribute('aria-label', 'Workbench navigation');
    controls.menu = button(nav, 'Main menu', function () { invoke(snapshot.token, 0, 'menu'); });
    icon(controls.menu, 'menu');
    controls.shop = button(nav, 'Shop', function () { openAuxiliary('Shop'); });
    icon(controls.shop, 'shop');
    icon(button(nav, 'Resources', function () { openAuxiliary('Resources'); }), 'resources');
    icon(button(nav, 'Settings', function () { openAuxiliary('Settings'); }), 'settings');
    controls.retest = button(nav, 'Run customer retest', function () { invoke(snapshot.token, 0, 'retest'); }, 'tsj-product-button tsj-product-primary');
    icon(controls.retest, 'retest');
    shellStatus = append(shell, 'p', undefined, 'tsj-product-notice'); shellStatus.setAttribute('role', 'status');
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
    var next = bridge.snapshot(auxiliary === 'Shop');
    if (snapshot && (snapshot.token !== next.token || snapshot.screen !== next.screen)) {
      closeNativeTools();
      auxiliary = ''; localMessage = ''; invalidateView(); renderedKey = '';
      savedContent = null; savedStart = null; auxiliaryFocus = null;
    }
    snapshot = next; mountShell();
    document.body.setAttribute('data-player-screen', snapshot.screen);
    if (window.tsjTrayDrawer) window.tsjTrayDrawer.sync(snapshot);
    if (window.tsjBenchInstruments) window.tsjBenchInstruments.mount(snapshot);
    if ((snapshot.screen === 'PREPARING' || snapshot.screen === 'RETEST') && progressTimer === null)
      progressTimer = window.setInterval(schedule, 300);
    else if (snapshot.screen !== 'PREPARING' && snapshot.screen !== 'RETEST' && progressTimer !== null) {
      window.clearInterval(progressTimer); progressTimer = null;
    }
    controls.retest.disabled = snapshot.screen !== 'WORKBENCH' || !snapshot.ready || snapshot.completed;
    controls.shop.disabled = snapshot.screen !== 'WORKBENCH';
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
      content.replaceChildren(); controls.shopList = null; controls.start = null; controls.progress = null;
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
        progressView(content);
        append(content, 'p', 'Testing the real circuit before it reaches your bench. Timing boards take longer. The bar tracks completed stages, not an estimated wait. Switching away pauses preparation until you return.', 'tsj-product-hint');
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
    if (page === 'PREPARING') updateProgress();
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
    closeNativeTools();
  });
  window.addEventListener('resize', closeNativeTools);
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();
}(window, document));
