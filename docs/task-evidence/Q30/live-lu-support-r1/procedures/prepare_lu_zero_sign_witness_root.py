from pathlib import Path
import hashlib,json
s=Path(__file__).resolve().parent;sha=lambda b:hashlib.sha256(b).hexdigest()
base=s/'scratch/pivot-lower-fusion-r2-preparation';raw=(base/'source-audit.json').read_bytes()
assert sha(raw)=='0d27e176dc46afb4db91316570f4674e52b51ce2b462c64f6b6a37b98cbec197'
records=json.loads(raw)['armInputs']['candidate'];root=s/'live-lu-zero-sign-witness-r1/reference';assert not root.exists()
for e in records:
    raw=(base/'candidate'/e['path']).read_bytes();assert (len(raw),sha(raw))==(e['bytes'],e['sha256'])
    p=root/e['path'];p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw)
test='''package com.lushprojects.circuitjs1.client;
import java.lang.reflect.Method;
public final class LuAcceptedZeroSignWitnessContractTest {
    public static void main(String[] args) throws Exception {
        double[][] full = {{5,0,1,0},{0,4,0,1},{5,0,2,0},{0,1,0,3}};
        double[][] historical = new double[4][];
        for (int i=0;i<4;i++) historical[i]=full[i].clone();
        int[] actualPivots = new int[4], historicalPivots = new int[4];
        Method original = LuFactorizationChecks.class.getDeclaredMethod("originalFactor", double[][].class, int.class, int[].class);
        original.setAccessible(true);
        require(CirSim.lu_factor(full,4,actualPivots), "accepted full LU rejected");
        require(((Boolean)original.invoke(null,historical,Integer.valueOf(4),historicalPivots)).booleanValue(), "historical LU rejected");
        int differences=0, assertions=2;
        for(int i=0;i<4;i++) {
            require(actualPivots[i]==historicalPivots[i], "pivot disagreement");assertions++;
            for(int j=0;j<4;j++) {
                long a=Double.doubleToRawLongBits(full[i][j]);
                long b=Double.doubleToRawLongBits(historical[i][j]);
                if(a!=b) {
                    differences++;
                    require(i==3 && j==2 && a==0L && b==Long.MIN_VALUE, "unexpected factor difference");
                }
                require(full[i][j]==historical[i][j], "numeric factor differs");assertions++;
            }
        }
        require(differences==1,"expected one independently derived signed-zero difference");assertions++;
        System.out.println("WITNESS: unchanged accepted full LU [3][2] rawBits=0; historical Crout rawBits=8000000000000000; pivots and all numerical factors equal");
        System.out.println("PASS: LU accepted zero-sign oracle witness assertions="+assertions);
    }
    private static void require(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
'''
p=root/'tests/contracts/LuAcceptedZeroSignWitnessContractTest.java';p.write_text(test,encoding='utf-8')
p=root/'scripts/verify-current-contracts.ps1';b=p.read_bytes();nl=b'\r\n' if b'\r\n' in b else b'\n'
needle=b"        @{ Name = 'Q30LuPivotCollectionContractTest'; Marker = 'Q30 LU pivot collection contracts ' },"
addition=b"        @{ Name = 'LuAcceptedZeroSignWitnessContractTest'; Marker = 'LU accepted zero-sign oracle witness assertions=' },"
assert b.count(needle)==1;p.write_bytes(b.replace(needle,needle+nl+addition))
result={'status':'UNCHANGED_ACCEPTED_PRODUCTION_SOURCE_PLUS_WITNESS_ONLY','baseSourceAuditSha256':'0d27e176dc46afb4db91316570f4674e52b51ce2b462c64f6b6a37b98cbec197','productionSourceUnchanged':True,'newTestSha256':sha((root/'tests/contracts/LuAcceptedZeroSignWitnessContractTest.java').read_bytes()),'CirSimSha256':sha((root/'src/com/lushprojects/circuitjs1/client/CirSim.java').read_bytes()),'limits':'A single independently derived signed-zero witness, not optimization acceptance.'}
(root.parent/'source-audit.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result))
