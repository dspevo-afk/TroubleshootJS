import assert from 'node:assert/strict';
import fs from 'node:fs';
const source = fs.readFileSync(new URL('../../war/tsj-workbench-ui.js', import.meta.url), 'utf8');
const body = source.slice(source.indexOf('  function randomSeed() {'), source.indexOf('  function identity(parent)'));
assert.ok(body.includes('Uint8Array(8)'));
const choose = new Function('window', body + '\nreturn randomSeed();');
let checks = 0, state = 0x5a1302fd;
function check(bytes) {
  let expected = 0n;
  for (const byte of bytes) expected = (expected << 8n) | BigInt(byte);
  expected = BigInt.asIntN(64, expected).toString();
  const actual = choose({ crypto: { getRandomValues(target) {
    assert.equal(target.length, 8); target.set(bytes); return target;
  } } });
  assert.equal(actual, expected); checks++;
}
for (const value of [0n, 1n, -1n, 2n**63n-1n, -(2n**63n), 2n**53n+1n, -(2n**53n+1n)]) {
  let n=BigInt.asUintN(64,value); const bytes=new Uint8Array(8);
  for (let i=7;i>=0;i--) { bytes[i]=Number(n&255n); n>>=8n; }
  check(bytes);
}
for (let i=0;i<4096;i++) {
  const bytes=new Uint8Array(8);
  for (let j=0;j<8;j++) { state=(Math.imul(state,1664525)+1013904223)>>>0; bytes[j]=state>>>24; }
  check(bytes);
}
assert.ok(!/\bBigInt\s*\(/.test(body), 'Production conversion must not depend on BigInt availability');
console.log(`PASS: Quick Play signed entropy contracts vectors=${checks}`);
