# Effective source-binding and envelope addendum R2

Frozen before P6 overlay generation, helper completion, builds or native execution.
PLAN31547c75, PROTOCOL4b795d77 and original envelope ADDENDUM_R1
88a510191f4c81413971f398257aaf0ab08ba6031f26a66206fd4e1a7eb424bb remain
preserved. This effective addendum incorporates all R1 envelope requirements.

Correct the source name/hash association in PROTOCOL_R1: actual PcbNetRouter.java
is129081 bytes, SHA
92347537ca59629f91d6a72596cd59d6520cff2deebb3681f22d6d9ec82c21ec.
That is the row in the frozen1084-source baseline and both immutable B/current C.
The169802-byte SHA759831c91345aedab9e78d8c83785019f7e59287a18f4f99f7af14876776c474
belongs to repository PcbLayerRoutingPrototype.java, not PcbNetRouter.java.
Its qualified BASE native overlay remains the existing separate router role,
SHA70cacaacc97cb3f01a1ad6221753e4d80a05479ba6663cd5dc6474a00dc4fb21.

Root's earlier protocol/worker assignment mislabeled that source hash. The leaf
cross-checked the actual frozen baseline and stopped before overlay generation;
no file, executable input or native result was accepted under the wrong binding.
Use92347537... as the surgical PcbNetRouter overlay builder's exact source pin.
All other signatures, fragments, ordering, native-role/compiler census, payload
fields, caps, physics and validation requirements remain unchanged.

The P6 manifest's addendumPath/addendumSha256 now bind this effective R2 file.
Also pin the preserved original R1 and protocol files in the exact extra-input set.
Parent P5 input path is input-pins-r7.json SHA
1e570d555c919755d82e5e0f0c728425c7aa313c3bd2c123ebfd874832a7b636;
qualification-r7.json SHA
301fca39736eca2b21c40b8dc92ca01e0c58b45493b103d3a8d3bee53624922e,
capture root capture-r3 and control receipt control-r3/data/op-1.jsonl.
These are actual immutable parent records, never synthetic parent contexts.
