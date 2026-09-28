from pathlib import Path
import hashlib
s = Path(__file__).resolve().parent
draft = s/'scratch/owned-zero-row-draft'
test = draft/'candidate/tests/contracts/Q30OwnedLuZeroRowContractTest.java'
raw = test.read_bytes()
assert hashlib.sha256(raw).hexdigest() == '797e0240b84977891e1f1852bed4cfdcbf11e6f13032abebdc482d32ce7a3544'
saved = draft/'r1-native-test.java'
assert not saved.exists()
saved.write_bytes(raw)
before = b'System.out.println("Q30 owned LU zero-row contracts assertions=" + count);'
after = b'System.out.println("PASS: Q30 owned LU zero-row contracts assertions=" + count);'
assert raw.count(before) == 1
raw = raw.replace(before, after)
test.write_bytes(raw)
new_hash = hashlib.sha256(raw).hexdigest()
prep = (s/'prepare_owned_zero_row_pair.py').read_text()
prep = prep.replace('797e0240b84977891e1f1852bed4cfdcbf11e6f13032abebdc482d32ce7a3544', new_hash)
prep = prep.replace('owned-zero-row-source-pair-path.txt','owned-zero-row-r2-source-pair-path.txt')
assert not (s/'prepare_owned_zero_row_pair_r2.py').exists()
(s/'prepare_owned_zero_row_pair_r2.py').write_text(prep)
gates = (s/'run_owned_zero_row_gates.py').read_text()
gates = gates.replace('owned-zero-row-source-pair-path.txt','owned-zero-row-r2-source-pair-path.txt')
gates = gates.replace("'owned-zero-row-gates.json'", "'owned-zero-row-gates-r2.json'")
gates = gates.replace("'native-owned-zero-row-candidate'", "'native-owned-zero-row-candidate-r2'")
gates = gates.replace("label = 'owned-zero-row-' + arm", "label = 'owned-zero-row-r2-' + arm")
assert not (s/'run_owned_zero_row_gates_r2.py').exists()
(s/'run_owned_zero_row_gates_r2.py').write_text(gates)
note = ('\nR2 root repair: initial native gate FAILed because the new test omitted the maintained PASS: output prefix. All 164 assertions ran with exit 0, but the driver correctly rejected its unqualified output. Only that print prefix changes; no assertions or production source change. Initial fixture/log/source test are retained. Final test SHA-256: ' + new_hash + '.\n')
with (draft/'NOTES.md').open('a') as out: out.write(note)
print(new_hash)
