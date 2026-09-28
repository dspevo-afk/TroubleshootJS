# Current GWT LU code-generation inspection

Read-only inspection of the current working source and already-emitted GWT 2.7 WAR. No source edits, build, browser run, or fresh CPU profile were performed.

## Artifact identity

- `war/circuitjs1/circuitjs1.nocache.js`: 7,827 bytes, SHA-256 `297eb4191a524e1559017102aed8b171375e138b32cba6f5509173c252a42928`. Its loader references all five current permutations, including the Safari permutation below.
- Chromium/Safari permutation: `war/circuitjs1/D8EC226C25D4AA4ABAB72F40C573A486.cache.js`, 3,094,827 bytes, SHA-256 `ea008e422c642e888078a646380ca67ab5a6d72d65245104bd5d60558241d420`.
- Its map: `war/WEB-INF/deploy/circuitjs1/symbolMaps/D8EC226C25D4AA4ABAB72F40C573A486.symbolMap`, 6,723,005 bytes, SHA-256 `33e7065fcce99c912a1fd253caeb9a1089a42c3f60df7840a94b7de15e0ea650`. `compilation-mappings.txt` maps D8… to `user.agent safari`.
- Symbol-map attribution: `fVb` = `CirSim.lu_factor(double[][], int, int[], LuFactorizationWorkspace)` at `CirSim.java:8025` (map line 7491); `gVb` = `CirSim.lu_solve(double[][], int, int[], double[])` at line 8117 (map line 7492); `EVb` = `requireFiniteStamp(double)` at line 3211 (map line 7576); `UWb` = workspace `clearPivot()` at line 7995 (map line 7946); `VWb` = workspace `reset(int)` at line 7974 (map line 7947).
- The factor extraction is brace-balanced and is the mapped four-argument workspace overload, not the three-argument wrapper. It contains the matrix finite scan, pivot search/swaps, lower-row and upper-column collection, nested sparse update, and `finally` cleanup.

## Extracted current Safari permutation functions

```javascript
function fVb(a,b,c,d){lQb();var e,f,g,h,i,j,k,m,n,o,p,q,r,s,t,u,v,w,A;try{VWb(d,b);for(h=0;h!=b;h++){s=true;q=a[h];for(i=0;i!=b;i++){EVb(q[i]);q[i]!=0&&(s=false)}if(s)return false}for(j=0;j!=b;j++){k=0;m=-1;for(h=j;h!=b;h++){p=a[h][j];A=p<=0?0-p:p;if(A>=k){k=A;m=h}}if(j!=m){q=a[m];a[m]=a[j];a[j]=q}c[j]=m;a[j][j]==0&&(a[j][j]=1.0E-18);if(j!=b-1){n=1/a[j][j];EVb(n);for(h=j+1;h!=b;h++){q=a[h];if(q[j]==0)continue;w=q[j]*n;EVb(w);q[j]=w;w!=0&&(d.d[d.c++]=q)}o=a[j];for(i=j+1;i!=b;i++)o[i]!=0&&(d.f[d.e++]=i);t=d.d;r=d.c;v=d.e;e=d.f;for(f=0;f<r;f++){q=t[f];g=q[j];for(u=0;u<v;u++){i=e[u];w=q[i]-g*o[i];EVb(w);q[i]=w}}UWb(d)}}return true}finally{UWb(d);d.a=0}}
function gVb(a,b,c,d){lQb();var e,f,g,h,i,j,k;for(f=0;f!=b;f++){i=c[f];j=d[i];d[i]=d[f];d[f]=j;if(j!=0)break}e=f++;for(;f<b;f++){i=c[f];k=d[i];d[i]=d[f];h=a[f];for(g=e;g<f;g++)k-=h[g]*d[g];d[f]=k}for(f=b-1;f>=0;f--){k=d[f];h=a[f];for(g=f+1;g!=b;g++)k-=h[g]*d[g];d[f]=k/h[f]}}
```

The helper bodies establish the residual checks/workspace cleanup:

```javascript
function EVb(a){if(!(a>=Aef&&a<=Bef))throw new hoe((uoe(),qoe),'Nonfinite CircuitJS matrix stamp')}
function UWb(a){var b;for(b=0;b<a.c;b++)a.d[b]=null;a.c=0;a.e=0}
function VWb(a,b){var c,d;UWb(a);a.a=0;if(b<0)throw new oGb('negative LU size');if(b>a.b){d=Hs(gt,s6e,38,b,0,2);c=Hs(ht,M7e,0,b,7,1);a.d=d;a.f=c;a.b=b}a.a=b}
```

## Comparison and limits

The five loader-selected permutations all have the same measured emitted lengths/call-site counts: factor 657 characters, solve 275; four factor call sites to the finite helper, one reset call, and two clear-pivot calls; zero `new Number/Double/Integer/Long` constructs and zero `~~`/`|0` integer-normalization tokens in either kernel. Their obfuscated symbols differ, but the factor/solve structure is the same. `appendLowerRow`/`appendUpperColumn` are inlined as direct indexed stores. `Math.abs` is emitted inline as `p<=0?0-p:p`. Matrix and workspace access is direct JS array indexing; arithmetic uses JS numbers. No virtual/interface dispatch appears in the numeric loops. The solve loops have no helper calls inside them.

The concrete hot-loop call cost is the nonvirtual `requireFiniteStamp` call: it occurs during the full `n×n` finite scan and for each elimination update (plus pivot/lower-column checks). The mapped helper is a direct comparison against finite bounds; only its failure branch constructs/throws the error. Workspace reset/clear helpers and the `try/finally` cleanup add bounded calls/array-reference clearing around pivots/factor calls, not a hidden object/iterator layer. The per-method class-init guard self-replaces with the no-op function after initialization. GWT has emitted a compact indexed-loop kernel, not a heavyweight abstraction; this inspection gives no reason for a JSNI/WASM replacement. V8 may inline helper calls at runtime; that was not measured for this bundle.

The prior CDP profile ranked LU factorization at 34,258 ms inclusive / 23,061 ms self and LU solve at 7,798 ms self (`docs/task-evidence/Q30/cdp-cpu-profile/README.md:21`). That profile used strong name `95F8651A7DE48234E5EE60A722D38AE9` and function `XUb`, while the current loader selects the different strong names above. Retain the prior profile as qualitative context only; these notes make no current-build timing or speedup claim. The only narrow future codegen experiment suggested by the source is to measure the finite-helper hot-call cost while preserving every finite check and the solver oracle; no change is recommended without that measurement.
