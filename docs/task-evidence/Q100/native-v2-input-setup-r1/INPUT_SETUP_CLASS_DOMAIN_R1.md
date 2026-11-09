# Selected factory concrete container domain

Root Source clarification before any execution. The pinned current generator
constructs exact java.util.LinkedHashMap and java.util.ArrayList containers.
This finite source-bound adapter may require those exact concrete classes,
matching the codec's exact built-in dict/list policy; arbitrary Map/List
implementations or subclasses are not an additional compatibility promise.
Factory's declared return type stays Map<String,Object>. Integer, Boolean and
String scalar classes stay exact; null remains an ordinary JSON value only
where field semantics permit it. No global cap, copy, allocation or unit
change: the node classification control already owns this type test. The
implementation worksheet and canaries must disclose/test the selected domain.
This supplies no current DATA, host, normal assembly or genuine authority.
