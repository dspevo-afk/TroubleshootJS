# Inherited bootstrap and geometry-call accounting

Before any T execution. Reuse the established R fixed owner bootstrap:
Parent(), Parent.meter(started), SetupMeter(parent,nativeMeter). A usable Meter
does not exist before that bootstrap. The first-main clock still includes all
of it; do not pretend allocations before Meter creation were precharged later.
This is the inherited logical owner model, not primitive VM allocation counting.
Every new wrapper metadata/API/array/list/map operation after SetupMeter must be
precharged before use, with no budget increase or uncharged metrics sidechannel.

The existing R geometry helper owns fingerprint-content/hash charges. Its
reflection argument metadata includes reserveFingerprintCopies Class[3]+args[3]
and hashString Class[1]+args[1], eight slots total per geometry invocation.
Precharge those eight caller metadata slots and explicit bounded caller logical
inspection/API operations before the unchanged helper. Do not duplicate its
actual fingerprint-content/hash charges or implement another serializer.

R9 header uses a scalar switch, not an allocated key array. New references is
an ordered MAP exactly OPEN,BLOCKED (size2), not a list. sourceReview is a MAP
size7. Preserve corresponding existing header inspection precharges, update
only keyset/schema/status/necessary bound checks; do not invent an array or
replace the established body/tree/count/publisher/byte/copy/fee owners.

No new run, routing injection, fake original, full-D/R7/H1, electrical or Player
claim. All budgets and exclusions unchanged; root owns final acceptance.
