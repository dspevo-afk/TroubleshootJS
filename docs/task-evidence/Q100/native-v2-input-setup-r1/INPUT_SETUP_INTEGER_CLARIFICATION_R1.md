# Integer field constraint boundary

Root Source decision before fixture generation or execution. The operation
contract's single declared bounded field constraint includes its fixed minimum,
maximum and exact expected value. `integer()` consumes one cached unboxed
Integer and app constants; its combined predicate
`actual < min || actual > max || actual != expected` costs one unit. The
separate type guard and unboxing each cost one unit. Total is three, plus the
caller's separately charged getter. A field predicate may not conceal another
input observation, arithmetic operation, allocation or traversal.

This makes the pre-execution logical operation boundary explicit. It changes
no helper bytes, expected debit, input policy, cap or oracle. The independent
Root calculation and worksheet both charge the same three operations for each
integer field. Native execution remains NOT RUN at this Source decision.
