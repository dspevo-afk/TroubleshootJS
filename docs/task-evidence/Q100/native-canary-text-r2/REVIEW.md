# Review scope and limits

Root reviewed the integrated component source, exact-path metadata fees, both
emission passes, final native/Python results, independent bytes and process
receipts. A separate read-only review verified default callback sequencing,
shared Java path/type helpers, independent Python parity, metadata precharges,
depth64 and value-only visit semantics. The review performed no execution.

The first Python draft lacked required container checks and had different
depth/visit rules. It was archived and corrected before the selected gate;
the final8 tests run on dab163d6/7bb31d36. No old-draft PASS is reused.

The serialization checkpoint binds object identity and Meter, not all mutable
contents. A same-length still-valid value change is not detected. Production
integration must own the entire body exclusively through validation/count/emit,
without exposing it to a callback or retained mutable graph. The tested invalid
post-prepare257-character insertion rejects before stdout. That test does not
prove deep immutability, typed owner mode or full host lifecycle.

The raw pinned Java fragments retain their final CRLF bytes. Package attributes
preserve all bytes and recognize CR as an end-of-line marker while retaining
blank-at-eol, blank-at-eof and space-before-tab checks. No Java/Python/transformer
input was normalized to resolve Git's initial CR-at-eol whitespace report.
