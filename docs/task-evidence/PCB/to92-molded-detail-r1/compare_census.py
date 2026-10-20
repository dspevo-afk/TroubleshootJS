"""Forward to the exact previously accepted strict reader after candidate audit."""
import json,runpy,sys
from pathlib import Path
HERE=Path(__file__).resolve().parent
assert runpy.run_path(str(HERE/'candidate_inputs.py'))['record']()==json.loads((HERE/'candidate-inputs.json').read_text())
runpy.run_path(str(HERE.parent/'capacitor-lid-detail-r1/compare_census.py'),run_name='__main__')
