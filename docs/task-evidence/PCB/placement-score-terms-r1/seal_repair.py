"""Re-archive exact one-byte fixture repair after preserving the completed failure."""
import hashlib
import json
from pathlib import Path
HERE = Path(__file__).resolve().parent
copy_root = Path(json.loads((HERE / 'local-state.json').read_text())['scratch'])
name = 'PcbPlacementScoreTermsContractTest.java'
old = (HERE / 'attempt1' / name).read_bytes()
active = (copy_root / 'tests/contracts' / name).read_bytes()
before = b'observation.context.algorithmCandidate==1,'
after = b'observation.context.algorithmCandidate==0,'
assert old.count(before) == 1 and active == old.replace(before, after)
assert (HERE / 'native-inputs.json').read_bytes() == (HERE / 'attempt1/native-inputs.json').read_bytes()
(HERE / name).write_bytes(active)
(HERE / 'native-inputs.json').unlink()
print('PASS exact one-byte source-derived fixture repair; failed seal retained in attempt1')
