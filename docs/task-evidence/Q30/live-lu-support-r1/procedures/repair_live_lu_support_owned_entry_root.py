from pathlib import Path
s=Path(__file__).resolve().parent
p=s/'live-lu-support-r1-preparation/candidate/src/com/lushprojects/circuitjs1/client/CirSim.java'
b=p.read_bytes();nl=b'\r\n' if b'\r\n' in b else b'\n'
old=nl.join([b'    void analyzeCircuitOwned(SolverExecutionBoundary.Operation operation) {',b'        invalidateLuStructuralSupport();',b'        solverExecutor.check(operation);'])
new=nl.join([b'    void analyzeCircuitOwned(SolverExecutionBoundary.Operation operation) {',b'        solverExecutor.check(operation);',b'        invalidateLuStructuralSupport();'])
assert b.count(old)==1;p.write_bytes(b.replace(old,new))
# Keep the one-time wiring recipe consistent with the reviewed final source.
p=s/'wire_live_lu_support_r1_root.py';t=p.read_text(encoding='utf-8')
old="replace('    void analyzeCircuitOwned(SolverExecutionBoundary.Operation operation) {',\n'''    void analyzeCircuitOwned(SolverExecutionBoundary.Operation operation) {\n        invalidateLuStructuralSupport();''')"
new="replace('''    void analyzeCircuitOwned(SolverExecutionBoundary.Operation operation) {\n        solverExecutor.check(operation);''',\n'''    void analyzeCircuitOwned(SolverExecutionBoundary.Operation operation) {\n        solverExecutor.check(operation);\n        invalidateLuStructuralSupport();''')"
assert t.count(old)==1;p.write_text(t.replace(old,new),encoding='utf-8')
print('Owned analysis checks operation before invalidating its support certificate.')
