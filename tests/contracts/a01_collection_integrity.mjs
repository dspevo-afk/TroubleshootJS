import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {pathToFileURL} from 'node:url';

const {collectA01} = await import(pathToFileURL(
  path.resolve('scripts/collect-a01.mjs')).href);

const sourceFingerprint = 'a'.repeat(64);
const buildFingerprint = 'b'.repeat(64);
const artifactIdentity = {
  protocol: 'troubleshootjs-execution-provenance-v1',
  sourceDigest: 'c'.repeat(64),
  scriptDigest: 'd'.repeat(64),
  webDigest: 'e'.repeat(64),
  executionDigest: 'f'.repeat(64),
  fileCount: 1
};

function browserFor(state) {
  return {
    tabs: {
      new: async () => ({
        goto: async () => {},
        close: async () => {},
        playwright: {
          domSnapshot: async () => {},
          evaluate: async () => ({
            ...state,
            report: JSON.stringify(state.report),
            artifactIdentity,
            scripts: ['circuitjs1/circuitjs1.nocache.js'],
            browser: {
              userAgent: 'a01-collection-test',
              userAgentReason: null,
              viewport: {width: 1280, height: 720},
              devicePixelRatio: 1,
              memory: {status: 'UNAVAILABLE', reason: 'focused test'}
            }
          })
        }
      })
    }
  };
}

async function collectFailure(label, state, directory) {
  const output = path.join(directory, label);
  await assert.rejects(
    collectA01({
      browser: browserFor(state),
      baseUrl: 'http://127.0.0.1:8899/',
      outDir: output,
      sourceFingerprint,
      buildFingerprint,
      timeoutMs: 1000
    }),
    /attempt retained/
  );
  const receipt = JSON.parse(await fs.readFile(output + '/pilot-0.json', 'utf8'));
  assert.equal(receipt.status, 'INFRASTRUCTURE_FAILURE');
  assert.equal(receipt.tabClosed, true);
  assert.equal(receipt.report.status, 'PASS');
  assert.equal(receipt.terminal, state.terminal);
  assert.equal(receipt.cleanup, state.cleanup);
  assert.match(receipt.error, /A01/);
}

const scratch = await fs.mkdtemp(path.join(os.tmpdir(), 'tsj-a01-collection-'));
try {
  await collectFailure('cleanup-failed', {
    terminal: 'PASS:a01',
    report: {status: 'PASS'},
    cleanup: 'FAIL',
    normalReady: true
  }, scratch);
  await collectFailure('terminal-report-contradiction', {
    terminal: 'FAIL:a01:contradiction',
    report: {status: 'PASS'},
    cleanup: 'PASS',
    normalReady: true
  }, scratch);

  const genuineFailureOutput = path.join(scratch, 'genuine-failure');
  const genuineFailure = await collectA01({
    browser: browserFor({
      terminal: 'FAIL:a01:solver',
      report: {status: 'FAIL'},
      cleanup: 'PASS',
      normalReady: true
    }),
    baseUrl: 'http://127.0.0.1:8899/',
    outDir: genuineFailureOutput,
    sourceFingerprint,
    buildFingerprint,
    timeoutMs: 1000
  });
  assert.equal(genuineFailure.status, 'FAIL');
  const retained = JSON.parse(await fs.readFile(
    path.join(genuineFailureOutput, 'pilot-0.json'), 'utf8'));
  assert.equal(retained.status, 'FAIL');
  assert.equal(retained.report.status, 'FAIL');
  assert.equal(retained.tabClosed, true);
} finally {
  // This directory is task-owned disposable test state.
  await fs.rm(scratch, {recursive: true, force: true});
}

console.log(JSON.stringify({
  status: 'PASS',
  protocol: 'TSJ-A01-COLLECTION-INTEGRITY-REGRESSIONS-1',
  cases: [
    'cleanup-failed receipt is retained as infrastructure failure',
    'terminal/report contradiction is retained as infrastructure failure',
    'genuine failed report remains retained as FAIL'
  ]
}, null, 2));
