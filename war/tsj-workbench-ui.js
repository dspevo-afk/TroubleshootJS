(function (window, document) {
  'use strict';

  var state = {
    activeTab: 'TOOLS',
    category: 'Resistors',
    selectedResistor: null,
    cartCount: 0
  };
  var initialized = false;
  var shell;
  var strip;
  var overlay;
  var overlayContent;
  var tabButtons = {};
  var resistorValues = [
    { label: '100 Ω', bands: ['brown', 'black', 'brown', 'gold'] },
    { label: '220 Ω', bands: ['red', 'red', 'brown', 'gold'] },
    { label: '330 Ω', bands: ['orange', 'orange', 'brown', 'gold'] },
    { label: '470 Ω', bands: ['yellow', 'violet', 'brown', 'gold'] },
    { label: '680 Ω', bands: ['blue', 'gray', 'brown', 'gold'] },
    { label: '1 kΩ', bands: ['brown', 'black', 'red', 'gold'] },
    { label: '2.2 kΩ', bands: ['red', 'red', 'red', 'gold'] },
    { label: '4.7 kΩ', bands: ['yellow', 'violet', 'red', 'gold'] },
    { label: '10 kΩ', bands: ['brown', 'black', 'orange', 'gold'] },
    { label: '47 kΩ', bands: ['yellow', 'violet', 'orange', 'gold'] },
    { label: '100 kΩ', bands: ['brown', 'black', 'yellow', 'gold'] }
  ];
  var categories = [
    'Resistors', 'Capacitors', 'Diodes', 'LEDs', 'Transistors', 'MOSFETs'
  ];

  function createElement(tag, className, text) {
    var element = document.createElement(tag);
    if (className) {
      element.className = className;
    }
    if (text !== undefined && text !== null) {
      element.appendChild(document.createTextNode(text));
    }
    return element;
  }

  function addClass(element, className) {
    if (!element || !className) {
      return;
    }
    if ((' ' + element.className + ' ').indexOf(' ' + className + ' ') === -1) {
      element.className += (element.className ? ' ' : '') + className;
    }
  }

  function removeClass(element, className) {
    var pattern;
    if (!element || !className) {
      return;
    }
    pattern = new RegExp('(^|\\s)' + className + '(?=\\s|$)', 'g');
    element.className = element.className.replace(pattern, ' ').replace(/^\s+|\s+$/g, '');
  }

  function hasClass(element, className) {
    return !!element && (' ' + element.className + ' ').indexOf(' ' + className + ' ') !== -1;
  }

  function button(text, className) {
    var control = createElement('button', className || '', text);
    control.type = 'button';
    return control;
  }

  function setTabState(name) {
    var tabName;
    state.activeTab = name;
    for (tabName in tabButtons) {
      if (tabButtons.hasOwnProperty(tabName)) {
        if (tabName === name) {
          addClass(tabButtons[tabName], 'is-active');
          tabButtons[tabName].setAttribute('aria-selected', 'true');
        } else {
          removeClass(tabButtons[tabName], 'is-active');
          tabButtons[tabName].setAttribute('aria-selected', 'false');
        }
      }
    }
  }

  function closeOverlay() {
    removeClass(overlay, 'is-open');
    overlay.setAttribute('aria-hidden', 'true');
    setTabState('TOOLS');
    if (tabButtons.TOOLS && tabButtons.TOOLS.focus) {
      tabButtons.TOOLS.focus();
    }
  }

  function openOverlay(name) {
    setTabState(name);
    addClass(overlay, 'is-open');
    overlay.setAttribute('aria-hidden', 'false');
    renderOverlay(name);
  }

  function makeAxialThumbnail(bands) {
    var thumbnail = createElement('span', 'tsj-axial');
    var leftLead = createElement('span', 'tsj-axial-lead');
    var body = createElement('span', 'tsj-axial-body');
    var rightLead = createElement('span', 'tsj-axial-lead');
    var i;
    thumbnail.setAttribute('aria-hidden', 'true');
    body.appendChild(createElement('span', 'tsj-axial-band tsj-band-' + bands[0]));
    body.appendChild(createElement('span', 'tsj-axial-band tsj-band-' + bands[1]));
    body.appendChild(createElement('span', 'tsj-axial-band tsj-band-' + bands[2]));
    body.appendChild(createElement('span', 'tsj-axial-band tsj-band-' + bands[3]));
    thumbnail.appendChild(leftLead);
    thumbnail.appendChild(body);
    thumbnail.appendChild(rightLead);
    return thumbnail;
  }

  function updateSelection(cards, selectionNote) {
    var i;
    for (i = 0; i < cards.length; i++) {
      if (cards[i].value === state.selectedResistor) {
        addClass(cards[i].card, 'is-selected');
        cards[i].button.setAttribute('aria-pressed', 'true');
      } else {
        removeClass(cards[i].card, 'is-selected');
        cards[i].button.setAttribute('aria-pressed', 'false');
      }
    }
    if (state.selectedResistor) {
      selectionNote.textContent = 'Selected for this mock store: ' + state.selectedResistor +
        ' ±5%. This selection does not change the workbench.';
    } else {
      selectionNote.textContent = 'Select a resistor to review it in this mock store.';
    }
  }

  function focusSelectedCategory(catalog) {
    var categoryButtons = catalog.querySelectorAll('.tsj-store-category');
    var i;
    for (i = 0; i < categoryButtons.length; i++) {
      if (categoryButtons[i].textContent === state.category) {
        categoryButtons[i].focus();
        return;
      }
    }
  }

  function renderCatalog(catalog, cartCountLabel, restoreCategoryFocus) {
    var heading = createElement('div', 'tsj-store-catalog-heading');
    var title = createElement('h3', '', state.category);
    var detail = createElement('p', '', 'Local presentation preview');
    var categoryRow = createElement('div', 'tsj-store-category-row');
    var selectionNote;
    var grid;
    var cards = [];
    var i;
    var categoryButton;

    catalog.innerHTML = '';
    heading.appendChild(title);
    heading.appendChild(detail);
    catalog.appendChild(heading);

    for (i = 0; i < categories.length; i++) {
      (function (categoryName) {
        categoryButton = button(categoryName, 'tsj-store-category');
        if (categoryName === state.category) {
          addClass(categoryButton, 'is-selected');
          categoryButton.setAttribute('aria-pressed', 'true');
        } else {
          categoryButton.setAttribute('aria-pressed', 'false');
        }
        categoryButton.addEventListener('click', function () {
          state.category = categoryName;
          renderCatalog(catalog, cartCountLabel, true);
        });
        categoryRow.appendChild(categoryButton);
      }(categories[i]));
    }
    catalog.appendChild(categoryRow);

    if (state.category !== 'Resistors') {
      var placeholder = createElement('div', 'tsj-store-placeholder');
      placeholder.appendChild(createElement('strong', '', 'Catalog coming soon'));
      placeholder.appendChild(document.createTextNode(
        'This fictional supplier view is reserved for a future catalog.'
      ));
      catalog.appendChild(placeholder);
      if (restoreCategoryFocus) {
        focusSelectedCategory(catalog);
      }
      return;
    }

    grid = createElement('div', 'tsj-store-grid');
    for (i = 0; i < resistorValues.length; i++) {
      (function (product) {
        var card = createElement('article', 'tsj-store-card');
        var productButton = button('', 'tsj-store-product');
        var addButton = button('Add to mock cart', 'tsj-store-add');
        var name = createElement('span', 'tsj-store-product-name', product.label);
        var detailText = createElement('span', 'tsj-store-product-detail', 'Axial · ±5%');
        cards.push({ card: card, button: productButton, value: product.label });
        productButton.appendChild(makeAxialThumbnail(product.bands));
        productButton.appendChild(name);
        productButton.appendChild(detailText);
        productButton.setAttribute('aria-label', 'Select ' + product.label + ' resistor, plus or minus 5 percent');
        productButton.addEventListener('click', function () {
          state.selectedResistor = product.label;
          updateSelection(cards, selectionNote);
        });
        addButton.addEventListener('click', function (event) {
          if (event && event.stopPropagation) {
            event.stopPropagation();
          }
          state.cartCount += 1;
          cartCountLabel.textContent = String(state.cartCount);
        });
        card.appendChild(productButton);
        card.appendChild(addButton);
        grid.appendChild(card);
      }(resistorValues[i]));
    }
    catalog.appendChild(grid);
    selectionNote = createElement('p', 'tsj-store-selection');
    catalog.appendChild(selectionNote);
    updateSelection(cards, selectionNote);
    if (restoreCategoryFocus) {
      focusSelectedCategory(catalog);
    }
  }

  function renderStore() {
    var inner = createElement('div', 'tsj-store-inner');
    var content = createElement('div', 'tsj-store-content');
    var supplier = createElement('div', 'tsj-store-supplier');
    var supplierCopy = createElement('div', '');
    var supplierTitle = createElement('h3', '', 'Copperline Supply Co.');
    var supplierText = createElement('p', '', 'A fictional parts counter for this local UI preview.');
    var cart = createElement('div', 'tsj-store-cart');
    var cartLabel = createElement('span', '', 'Cart: ');
    var cartCountLabel = createElement('strong', '', String(state.cartCount));
    var cartNote = createElement('p', 'tsj-store-cart-note', 'Mock cart only');
    var toolbar = createElement('div', 'tsj-store-toolbar');
    var back = button('←', '');
    var forward = button('→', '');
    var refresh = button('↻', '');
    var address = createElement('div', 'tsj-store-address', 'https://store.copperline.example/components');
    var catalog = createElement('div', 'tsj-store-catalog');
    var fakeStatus = createElement('p', 'tsj-store-selection', 'Local mock browser · no board connection');

    back.setAttribute('aria-label', 'Back');
    forward.setAttribute('aria-label', 'Forward');
    refresh.setAttribute('aria-label', 'Refresh');
    back.addEventListener('click', function () { fakeStatus.textContent = 'Back is a local mock control.'; });
    forward.addEventListener('click', function () { fakeStatus.textContent = 'Forward is a local mock control.'; });
    refresh.addEventListener('click', function () { fakeStatus.textContent = 'Catalog refreshed locally.'; });
    toolbar.appendChild(back);
    toolbar.appendChild(forward);
    toolbar.appendChild(refresh);
    toolbar.appendChild(address);

    supplierCopy.appendChild(supplierTitle);
    supplierCopy.appendChild(supplierText);
    cart.appendChild(cartLabel);
    cart.appendChild(cartCountLabel);
    cart.appendChild(cartNote);
    supplier.appendChild(supplierCopy);
    supplier.appendChild(cart);
    inner.appendChild(supplier);
    inner.appendChild(fakeStatus);
    renderCatalog(catalog, cartCountLabel);
    inner.appendChild(catalog);
    content.appendChild(inner);
    overlayContent.appendChild(toolbar);
    overlayContent.appendChild(content);
  }

  function renderPlaceholder(name, message) {
    var inner = createElement('div', 'tsj-store-inner');
    var placeholder = createElement('div', 'tsj-store-placeholder');
    placeholder.appendChild(createElement('strong', '', name + ' preview'));
    placeholder.appendChild(document.createTextNode(message));
    inner.appendChild(placeholder);
    overlayContent.appendChild(inner);
  }

  function renderOverlay(name) {
    var header = createElement('div', 'tsj-ui-dialog-header');
    var copy = createElement('div', '');
    var heading;
    var title;
    var subtitle;
    var close = button('×', 'tsj-ui-close');
    overlayContent.innerHTML = '';
    if (name === 'SHOP') {
      title = 'Parts Store';
      subtitle = 'A bounded local mock browser for browsing component examples.';
    } else if (name === 'RESOURCES') {
      title = 'Resources';
      subtitle = 'Reference material will live here in a future pass.';
    } else {
      title = 'Settings';
      subtitle = 'Workbench preferences are intentionally not wired yet.';
    }
    heading = createElement('h2', '', title);
    heading.setAttribute('id', 'tsj-ui-dialog-title');
    copy.appendChild(heading);
    copy.appendChild(createElement('p', '', subtitle));
    close.setAttribute('aria-label', 'Close');
    close.addEventListener('click', closeOverlay);
    header.appendChild(copy);
    header.appendChild(close);
    overlayContent.appendChild(header);
    if (name === 'SHOP') {
      renderStore();
    } else if (name === 'RESOURCES') {
      renderPlaceholder('Resources', 'Service notes, measurement references, and repair guides will appear here.');
    } else {
      renderPlaceholder('Settings', 'Display and interaction preferences will appear here without changing simulation state.');
    }
    close.focus();
  }

  function selectTab(name) {
    if (name === 'TOOLS') {
      closeOverlay();
    } else {
      openOverlay(name);
    }
  }

  function buildShell() {
    var nav = createElement('nav', 'tsj-ui-strip');
    var tabNames = ['TOOLS', 'SHOP', 'RESOURCES', 'SETTINGS'];
    var i;
    nav.setAttribute('role', 'tablist');
    nav.setAttribute('aria-label', 'Workbench navigation');
    for (i = 0; i < tabNames.length; i++) {
      (function (name) {
        var tab = button(name, 'tsj-ui-tab');
        tab.setAttribute('role', 'tab');
        tab.setAttribute('aria-selected', name === 'TOOLS' ? 'true' : 'false');
        tab.addEventListener('click', function () { selectTab(name); });
        tabButtons[name] = tab;
        nav.appendChild(tab);
      }(tabNames[i]));
    }
    shell.appendChild(nav);
    strip = nav;
    overlay = createElement('div', 'tsj-ui-overlay');
    overlay.setAttribute('aria-hidden', 'true');
    overlay.setAttribute('role', 'presentation');
    overlay.addEventListener('click', function (event) {
      if (event.target === overlay) {
        closeOverlay();
      }
    });
    var dialog = createElement('section', 'tsj-ui-dialog');
    dialog.setAttribute('role', 'dialog');
    dialog.setAttribute('aria-modal', 'true');
    dialog.setAttribute('aria-labelledby', 'tsj-ui-dialog-title');
    overlayContent = dialog;
    overlay.appendChild(dialog);
    shell.appendChild(overlay);
    setTabState('TOOLS');
  }

  function findWorkbenchAnchor() {
    return document.querySelector('.tsj-meter-panel');
  }

  function markPresentationContext() {
    if (!findWorkbenchAnchor()) {
      return false;
    }
    addClass(document.body, 'tsj-workbench-ui');
    return true;
  }

  function getDialogFocusableElements() {
    var candidates = overlayContent.querySelectorAll(
      'a[href], button, input, select, textarea, [tabindex]'
    );
    var focusable = [];
    var i;
    var candidate;
    var tabIndex;
    for (i = 0; i < candidates.length; i++) {
      candidate = candidates[i];
      tabIndex = candidate.getAttribute('tabindex');
      if (candidate.disabled || tabIndex === '-1' || candidate.type === 'hidden') {
        continue;
      }
      focusable.push(candidate);
    }
    return focusable;
  }

  function isInsideDialog(element) {
    var node = element;
    while (node) {
      if (node === overlayContent) {
        return true;
      }
      node = node.parentNode;
    }
    return false;
  }

  function trapDialogFocus(event) {
    var focusable;
    var first;
    var last;
    var active;
    if (!overlay || !overlayContent || !hasClass(overlay, 'is-open') ||
        !(event.key === 'Tab' || event.keyCode === 9)) {
      return;
    }
    focusable = getDialogFocusableElements();
    if (!focusable.length) {
      return;
    }
    first = focusable[0];
    last = focusable[focusable.length - 1];
    active = document.activeElement;
    if (event.shiftKey && (active === first || !isInsideDialog(active))) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && (active === last || !isInsideDialog(active))) {
      event.preventDefault();
      first.focus();
    }
  }

  function positionStrip() {
    var anchor = findWorkbenchAnchor();
    var rect;
    var right;
    if (!strip || !anchor || !anchor.getBoundingClientRect) {
      return;
    }
    rect = anchor.getBoundingClientRect();
    right = window.innerWidth - rect.right + 10;
    strip.style.right = (right > 6 ? right : 6) + 'px';
    strip.style.top = (rect.top + 10) + 'px';
  }

  function initialize() {
    if (initialized || !document.body) {
      return;
    }
    initialized = true;
    shell = createElement('div', 'tsj-workbench-shell');
    document.body.appendChild(shell);
    buildShell();
    markPresentationContext();
    positionStrip();
    window.addEventListener('resize', positionStrip);
  }

  function startLegacyInitializationFallback() {
    var attempts = 0;
    var maxAttempts = 60;
    var timer = window.setInterval(function () {
      attempts += 1;
      if (markPresentationContext()) {
        positionStrip();
        window.clearInterval(timer);
      } else if (attempts >= maxAttempts) {
        window.clearInterval(timer);
      }
    }, 250);
  }

  function start() {
    var observer;
    initialize();
    if (!document.body) {
      return;
    }
    if (markPresentationContext()) {
      positionStrip();
      return;
    }
    if (!window.MutationObserver) {
      startLegacyInitializationFallback();
      return;
    }
    observer = new window.MutationObserver(function () {
      if (markPresentationContext()) {
        positionStrip();
        observer.disconnect();
      }
    });
    observer.observe(document.body, { childList: true, subtree: true });
    window.setTimeout(function () { observer.disconnect(); }, 15000);
  }

  document.addEventListener('keydown', function (event) {
    if (overlay && hasClass(overlay, 'is-open')) {
      trapDialogFocus(event);
      if (event.key === 'Escape' || event.keyCode === 27) {
        closeOverlay();
      }
    }
  });

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }
}(window, document));
