// Use from the supported Browser runtime: collectA01({browser, baseUrl, outDir, ...}).
// This module owns only the tabs it creates. It never launches or kills a browser.
import fs from 'node:fs/promises';
import path from 'node:path';

export async function collectA01({browser, baseUrl, outDir, corpus = 'pilot',
    sourceFingerprint, buildFingerprint, round = 0, timeoutMs = 30000, forcedFailure = false,
    debug = true}) {
  if (!['pilot', 'holdout'].includes(corpus) || !Number.isInteger(round) || round < 0 || round > 9)
    throw new Error('Invalid preregistered corpus or round');
  const base = new URL(baseUrl);
  if (!['127.0.0.1', 'localhost', '[::1]'].includes(base.hostname) || base.protocol !== 'http:')
    throw new Error('A01 collection requires the owned local production preview');
  for (const digest of [sourceFingerprint, buildFingerprint])
    if (!/^[a-f0-9]{64}$/.test(digest || '')) throw new Error('Missing source/build identity');
  if (!Number.isInteger(timeoutMs) || timeoutMs < 1000 || timeoutMs > 60000)
    throw new Error('Invalid bounded collection deadline');
  await fs.mkdir(outDir, {recursive:true});
  const tag = `${corpus}-${round}${forcedFailure ? '-negative' : ''}${debug ? '' : '-debug-off'}`;
  const output = path.join(outDir, tag + '.json');
  // Never overwrite a prior attempt, including a failure.
  const handle = await fs.open(output, 'wx');
  const receipt = {protocol:'TSJ-A01-COLLECTION-1', corpus, round, debug, forcedFailure,
    sourceFingerprint, buildFingerprint, status:'INFRASTRUCTURE_FAILURE',
    startedUtc:new Date().toISOString(), report:null, terminal:null, tabClosed:false,
    cacheProtocol:'Fresh document/application per round; OS, HTTP and browser-process caches not cleared.'};
  let tab;
  let primary;
  try {
    tab = await browser.tabs.new();
    const url = new URL('/circuitjs.html', base);
    url.search = new URLSearchParams({tsjChallenge:'led',seed:'3',tsjMeasureA01:'true',
      tsjA01Corpus:corpus,tsjA01Round:String(round),tsjA01Source:sourceFingerprint,
      tsjA01Build:buildFingerprint,tsjDebug:String(debug),tsjA01Fail:String(forcedFailure)}).toString();
    receipt.url = url.href;
    await tab.goto(url.href);
    // Confirm the loaded page through the browser's DOM surface before reading its receipt.
    await tab.playwright.domSnapshot();
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      const state = await tab.playwright.evaluate(() => ({
        terminal: document.documentElement.getAttribute('data-tsj-verification'),
        report: document.documentElement.getAttribute('data-tsj-a01-report'),
        cleanup: document.documentElement.getAttribute('data-tsj-a01-cleanup'),
        artifactIdentity: {
          protocol: document.documentElement.getAttribute('data-tsj-preview-identity-protocol'),
          sourceDigest: document.documentElement.getAttribute('data-tsj-preview-source-digest'),
          scriptDigest: document.documentElement.getAttribute('data-tsj-preview-script-digest'),
          webDigest: document.documentElement.getAttribute('data-tsj-preview-web-digest'),
          executionDigest: document.documentElement.getAttribute('data-tsj-preview-execution-digest'),
          fileCount: Number(document.documentElement.getAttribute('data-tsj-preview-file-count'))
        },
        normalReady: Array.from(document.querySelectorAll('button')).some(b => b.textContent === 'Retest Customer'),
        scripts: Array.from(document.querySelectorAll('script[src]')).map(s => s.getAttribute('src')),
        browser: {
          userAgent: typeof navigator !== 'undefined' && navigator.userAgent ?
            navigator.userAgent : null,
          userAgentReason: typeof navigator !== 'undefined' && navigator.userAgent ?
            null : 'browser evaluator did not expose navigator.userAgent',
          viewport: {width: window.innerWidth, height: window.innerHeight},
          devicePixelRatio: window.devicePixelRatio,
          memory: typeof performance !== 'undefined' && performance.memory ? {
            status: 'AVAILABLE',
            usedBytes: performance.memory.usedJSHeapSize,
            totalBytes: performance.memory.totalJSHeapSize,
            limitBytes: performance.memory.jsHeapSizeLimit
          } : {
            status: 'UNAVAILABLE',
            reason: typeof performance === 'undefined' ?
              'browser evaluator did not expose performance.memory' :
              'performance.memory is unavailable in this browser context'
          }
        }
      }));
      receipt.terminal = state.terminal;
      receipt.cleanup = state.cleanup;
      receipt.artifactIdentity = state.artifactIdentity;
      receipt.scripts = state.scripts;
      receipt.browser = state.browser;
      if (!debug) {
        // Caller separately verifies this normal page has finished initial loading.
        if (state.report || /(?:PASS|FAIL|RUNNING):a01/.test(state.terminal || ''))
          throw new Error('A01 developer report leaked without debug');
        if (state.normalReady) { receipt.status = 'DEBUG_OFF_OBSERVED'; break; }
        await new Promise(resolve => setTimeout(resolve, 250));
        continue;
      }
      if (/^(PASS|FAIL):a01/.test(state.terminal || '')) {
        receipt.report = state.report ? JSON.parse(state.report) : null;
        receipt.status = state.terminal.startsWith('PASS:') ? 'PASS' : 'FAIL';
        if (!receipt.report) throw new Error('A01 terminal result omitted its versioned report');
        if (receipt.report.status !== receipt.status)
          throw new Error('A01 terminal/report status mismatch');
        if (receipt.status === 'PASS' && state.cleanup !== 'PASS')
          throw new Error('A01 report completed without cleanup PASS');
        break;
      }
      await new Promise(resolve => setTimeout(resolve, 250));
    }
    if (receipt.status === 'INFRASTRUCTURE_FAILURE') throw new Error('A01 terminal result deadline expired');
  } catch (err) {
    primary = err;
    // A retained collection exception is diagnostic evidence, never a passing
    // qualification outcome.  Preserve the terminal/report/cleanup fields so
    // the independent checker can explain the contradiction.
    receipt.status = 'INFRASTRUCTURE_FAILURE';
    receipt.error = String(err.message || err);
  } finally {
    try { if (tab) { await tab.close(); receipt.tabClosed = true; } }
    catch (err) { receipt.cleanupError = String(err.message || err); receipt.status = 'INFRASTRUCTURE_FAILURE'; primary ||= err; }
    receipt.endedUtc = new Date().toISOString();
    receipt.durationMs = Date.parse(receipt.endedUtc) - Date.parse(receipt.startedUtc);
    await handle.writeFile(JSON.stringify(receipt, null, 2) + '\n');
    await handle.close();
  }
  if (primary) throw new Error(`${receipt.error || receipt.cleanupError}; attempt retained at ${output}`);
  return {output, status:receipt.status, terminal:receipt.terminal, tabClosed:receipt.tabClosed};
}
