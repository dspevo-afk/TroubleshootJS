// Run with the selected in-app Browser tab. These helpers use only public UI.
// The disabled-entry source canary must fail at requirePublicControl; no
// controller, page-evaluation click, or developer-route fallback exists here.
export async function requirePublicControl(tab, label) {
  const control = tab.playwright.getByRole('button', { name: label, exact: true });
  if (await control.count() !== 1 || !await control.isVisible()) {
    throw new Error(`FAIL:task48-player:required control missing: ${label}`);
  }
  if (!await control.isEnabled()) {
    throw new Error(`FAIL:task48-player:required control disabled: ${label}`);
  }
  return control;
}

export async function activatePublicControl(tab, label) {
  const control = await requirePublicControl(tab, label);
  await control.click();
  return tab.playwright.domSnapshot();
}

export async function assertNormalPlayerPrivacy(tab) {
  const exposure = await tab.playwright.evaluate(() => {
    const root = document.documentElement;
    const text = document.body.innerText;
    const attributes = Array.from(document.querySelectorAll('*')).flatMap(element =>
      Array.from(element.attributes).map(attribute => `${attribute.name}=${attribute.value}`)
    ).join('\n');
    return { text, attributes, report: root.getAttribute('data-tsj-task48-report') };
  });
  if (exposure.report !== null || /tsj-block-v1|driver-rg-open|load-rload-open|RESISTOR_OPEN|diagnostic-proof|data-tsj-task48-report/.test(
      exposure.text + '\n' + exposure.attributes)) {
    throw new Error('FAIL:task48-player:private proof or hidden-fault identity exposed');
  }
  return { status: 'PASS', scope: 'normal visible text, DOM attributes and Task48 report absence' };
}
