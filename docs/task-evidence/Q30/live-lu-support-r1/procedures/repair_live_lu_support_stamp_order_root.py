from pathlib import Path
s=Path(__file__).resolve().parent
p=s/'live-lu-support-r1-preparation/candidate/src/com/lushprojects/circuitjs1/client/CirSim.java'
b=p.read_bytes();nl=b'\r\n' if b'\r\n' in b else b'\n'
start=b'            if (luStructuralSupport != null) {'
end=b'\t    circuitMatrix[i][j] += x;'
a=b.index(start);z=b.index(end,a)+len(end)
old=b[a:z];hook=old[:-len(end)].rstrip(b'\r\n')
new=end+nl+hook
p.write_bytes(b[:a]+new+b[z:])
p=s/'wire_live_lu_support_r1_root.py';t=p.read_text(encoding='utf-8')
old="replace('\\t    circuitMatrix[i][j] += x;','''            if (luStructuralSupport != null) {"
new="replace('\\t    circuitMatrix[i][j] += x;','''\\t    circuitMatrix[i][j] += x;\n            if (luStructuralSupport != null) {"
assert t.count(old)==1;t=t.replace(old,new)
old="            }\n\\t    circuitMatrix[i][j] += x;''')";new="            }''')"
assert t.count(old)==1;p.write_text(t.replace(old,new),encoding='utf-8')
src=s/'scratch/scratch/live-lu-support-r1-root-wiring-review.md'
dst=s/'scratch/live-lu-support-r1-root-wiring-review.md';assert not dst.exists();dst.write_bytes(src.read_bytes())
print('Mapped coordinate support recorded after successful cell write and before accumulated finite check; review preserved/copied.')
