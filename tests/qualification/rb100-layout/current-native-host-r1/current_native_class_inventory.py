"""Closed code-only inventory for the current native class roots.

The only admitted producer class is the externally fixed NO_DATA artifact.
This adapter does not admit DATA, factories, or normal-prior evidence. Inputs
and helper modules must already be authenticated by the caller; the bounded
reads and rechecks below are not an atomic namespace snapshot or a launch.
"""

import ntpath
import os


_CLASS_PREFIX = "com/lushprojects/circuitjs1/client/"
_DIRECTORY_COMPONENTS = ("com", "lushprojects", "circuitjs1", "client")
_NO_DATA_LEAF = "CohortTerminalTelemetryInputV2.class"
_PRODUCER_PREFIX = "CohortTerminalTelemetryInput"
_NO_DATA_BYTES = 1044
_NO_DATA_SHA256 = "cce09cb6580894aef7ff3553e8a63710cdd0936538b00f53e022c31b82ff68cb"
_CODE_ROLE = "CODE_CLASS"
_MAX_CLASSES = 10000
_MAX_CLASS_BYTES_TOTAL = 268435456
_EXPECTED_FRESH_CLASSES = 141
_EXPECTED_INHERITED_CLASSES = 2396
_READER_FUNCTIONS = (
    "_check_windows_local_lexical_path",
    "_inspect_path",
    "_read_file_once",
    "_path_identity",
    "_token_identity",
)
_IO_OWNER_FUNCTIONS = (
    "_assert_module_functions",
    "_read_with_reader",
    "_reader_token_identity",
)
_INVOCATION_FUNCTIONS = ("_absolute_windows_path",)
_ROW_KEYS = ("path", "bytes", "sha256", "major")
_HEX = "0123456789abcdef"
_FIRST_CLASS_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_$"
_OTHER_CLASS_CHARS = _FIRST_CLASS_CHARS + "0123456789"


class ClassInventoryError(ValueError):
    """Path-free local rejection from the class inventory adapter."""

    def __init__(self, code):
        self.code = code
        super().__init__(code)


def _reject(code):
    raise ClassInventoryError(code)


def _is_sha256(value):
    return (type(value) is str and len(value) == 64
            and all(char in _HEX for char in value))


def _valid_class_path(path):
    if type(path) is not str or not path.startswith(_CLASS_PREFIX):
        return False
    leaf = path[len(_CLASS_PREFIX):]
    if not leaf.endswith(".class"):
        return False
    name = leaf[:-6]
    if not name or name[0] not in _FIRST_CLASS_CHARS:
        return False
    return all(char in _OTHER_CLASS_CHARS for char in name[1:])


def _snapshot_rows(rows, count, reader_limit, label):
    if type(rows) not in (list, tuple) or len(rows) != count:
        _reject(label + "_ROWS")
    result = []
    exact_paths = set()
    folded_paths = set()
    for row in rows:
        if type(row) is not dict or set(row) != set(_ROW_KEYS):
            _reject(label + "_ROW_SHAPE")
        copied = dict(row)
        path = copied["path"]
        byte_count = copied["bytes"]
        digest = copied["sha256"]
        major = copied["major"]
        if not _valid_class_path(path):
            _reject(label + "_PATH")
        if (type(byte_count) is not int or byte_count <= 0
                or byte_count > reader_limit):
            _reject(label + "_BYTES")
        if not _is_sha256(digest):
            _reject(label + "_SHA256")
        if type(major) is not int or major != 51:
            _reject(label + "_MAJOR")
        folded = path.lower()
        if path in exact_paths or folded in folded_paths:
            _reject(label + "_DUPLICATE_PATH")
        exact_paths.add(path)
        folded_paths.add(folded)
        result.append(copied)
    return result




def _validate_scope(fresh, inherited, jar_class_names):
    if type(jar_class_names) is not frozenset or any(
            type(name) is not str for name in jar_class_names):
        _reject("JAR_CLASS_NAMES")

    fresh_paths = {row["path"] for row in fresh}
    inherited_paths = {row["path"] for row in inherited}
    fresh_folded = {path.lower() for path in fresh_paths}
    inherited_folded = {path.lower() for path in inherited_paths}
    if fresh_paths & inherited_paths or fresh_folded & inherited_folded:
        _reject("FRESH_INHERITED_INTERSECTION")
    jar_paths_folded = {path.lower() for path in jar_class_names}
    if ({path.lower() for path in fresh_paths | inherited_paths}
            & jar_paths_folded):
        _reject("JAR_CLASS_INTERSECTION")

    no_data_count = 0
    for row in fresh:
        leaf = row["path"][len(_CLASS_PREFIX):]
        if leaf.lower().startswith(_PRODUCER_PREFIX.lower()):
            if leaf != _NO_DATA_LEAF:
                _reject("FRESH_PRODUCER_CLASS_DENIED")
            if (row["bytes"] != _NO_DATA_BYTES
                    or row["sha256"] != _NO_DATA_SHA256
                    or row["major"] != 51):
                _reject("FRESH_NODATA_BINDING")
            no_data_count += 1
    if no_data_count != 1:
        _reject("FRESH_NODATA_REQUIRED")
    for row in inherited:
        leaf = row["path"][len(_CLASS_PREFIX):]
        if leaf.lower().startswith(_PRODUCER_PREFIX.lower()):
            _reject("INHERITED_PRODUCER_CLASS_DENIED")

    class_count = len(fresh) + len(inherited)
    class_bytes = sum(row["bytes"] for row in fresh + inherited)
    if class_count > _MAX_CLASSES or class_bytes > _MAX_CLASS_BYTES_TOTAL:
        _reject("INVENTORY_BUDGET")
    return class_count, class_bytes


def _inspect(reader, invocation_owner, path, expected_kind):
    lexical_path = invocation_owner._absolute_windows_path(path)
    inspected = reader._inspect_path(lexical_path, expected_kind)
    if type(inspected) is not tuple or len(inspected) != 3:
        _reject("INSPECT_RESULT")
    try:
        real_path = ntpath.normcase(ntpath.normpath(str(inspected[1])))
    except (OSError, TypeError, ValueError):
        _reject("INSPECT_RESULT")
    if not real_path or not ntpath.isabs(real_path):
        _reject("INSPECT_RESULT")

    raw_identity = inspected[2]
    projected_identity = reader._path_identity(raw_identity)
    if type(raw_identity) is not tuple or not raw_identity:
        _reject("INSPECT_IDENTITY")
    leaf = raw_identity[-1]
    if (type(leaf) is not tuple or len(leaf) != 2
            or type(leaf[1]) is not tuple or len(leaf[1]) != 7
            or any(type(value) is not int for value in leaf[1])):
        _reject("INSPECT_IDENTITY")
    snapshot = leaf[1]
    if snapshot[0] < 0 or snapshot[1] <= 0:
        _reject("INSPECT_IDENTITY")
    stable_id = (snapshot[0], snapshot[1])
    return {"path": real_path, "identity": stable_id,
            "projected": projected_identity}


def _roots_overlap(left, right):
    if left["identity"] == right["identity"] or left["path"] == right["path"]:
        return True
    left_drive = ntpath.normcase(ntpath.splitdrive(left["path"])[0])
    right_drive = ntpath.normcase(ntpath.splitdrive(right["path"])[0])
    if left_drive != right_drive:
        return False
    try:
        common = ntpath.normcase(ntpath.commonpath((left["path"], right["path"])))
    except (OSError, TypeError, ValueError):
        _reject("ROOT_PATH_INVALID")
    return common == left["path"] or common == right["path"]


def _directory_names(path, maximum_entries):
    try:
        with os.scandir(path) as entries:
            names = []
            for entry in entries:
                names.append(entry.name)
                if len(names) > maximum_entries:
                    _reject("DIRECTORY_ENTRY_LIMIT")
    except OSError:
        _reject("DIRECTORY_SCAN")
    if (any(type(name) is not str for name in names)
            or len(names) != len(set(names))):
        _reject("DIRECTORY_NAMES")
    return set(names)


def _ancestor_projection(identity):
    if type(identity) is not tuple or not identity:
        _reject("PATH_IDENTITY_SHAPE")
    projected = []
    for row in identity:
        if (type(row) is not tuple or len(row) != 2
                or type(row[0]) is not str or type(row[1]) is not tuple
                or len(row[1]) not in (4, 7)
                or any(type(value) is not int for value in row[1])):
            _reject("PATH_IDENTITY_SHAPE")
        snapshot = row[1]
        projected.append((row[0], (snapshot[0], snapshot[1], snapshot[2], snapshot[-1])))
    return tuple(projected)


def _require_child_identity(parent, child):
    parent_rows = _ancestor_projection(parent["projected"])
    child_rows = _ancestor_projection(child["projected"])
    if (len(child_rows) != len(parent_rows) + 1
            or child_rows[:len(parent_rows)] != parent_rows):
        _reject("PARENT_IDENTITY")


def _scan_tree(reader, invocation_owner, root, root_fact, rows, allow_no_data):
    identities = {"": root_fact["projected"]}
    current_path = root
    current_relative = []
    parent_fact = root_fact
    for component in _DIRECTORY_COMPONENTS:
        if _directory_names(current_path, 2) != {component}:
            _reject("DIRECTORY_CLOSURE")
        current_path = ntpath.join(current_path, component)
        current_relative.append(component)
        fact = _inspect(reader, invocation_owner, current_path, "directory")
        _require_child_identity(parent_fact, fact)
        identities["\\".join(current_relative)] = fact["projected"]
        parent_fact = fact

    expected_leaves = {row["path"][len(_CLASS_PREFIX):] for row in rows}
    if allow_no_data and _NO_DATA_LEAF not in expected_leaves:
        _reject("FRESH_NODATA_REQUIRED")
    actual_leaves = _directory_names(current_path, len(expected_leaves) + 1)
    for leaf in actual_leaves:
        if leaf.lower().startswith(_PRODUCER_PREFIX.lower()):
            if not allow_no_data or leaf != _NO_DATA_LEAF:
                _reject("CLIENT_PRODUCER_CLASS_DENIED")
    if actual_leaves != expected_leaves:
        _reject("CLASS_DIRECTORY_CLOSURE")
    for row in rows:
        leaf = row["path"][len(_CLASS_PREFIX):]
        class_path = ntpath.join(current_path, leaf)
        fact = _inspect(reader, invocation_owner, class_path, "file")
        _require_child_identity(parent_fact, fact)
        identities[row["path"].replace("/", "\\")] = fact["projected"]
    return identities


def _validate_class_bytes(raw):
    if (type(raw) is not bytes or len(raw) < 8
            or raw[:4] != b"\xca\xfe\xba\xbe"
            or raw[6:8] != b"\x00\x33"):
        _reject("CLASS_HEADER")


def _read_inventory(reader, io_owner, invocation_owner, root, rows,
                    initial_identities, maximum, phase):
    tokens = {}
    for row in rows:
        full_path = ntpath.join(root, row["path"].replace("/", "\\"))
        full_path = invocation_owner._absolute_windows_path(full_path)
        raw, token = io_owner._read_with_reader(
            reader, full_path, maximum, row["bytes"], row["sha256"],
            _CODE_ROLE, phase)
        _validate_class_bytes(raw)
        token_identity = io_owner._reader_token_identity(
            reader, token, _CODE_ROLE, phase + "-token")
        path_identity = reader._path_identity(token[1])
        relative = row["path"].replace("/", "\\")
        if path_identity != initial_identities[relative]:
            _reject("CLASS_PATH_IDENTITY")
        tokens[row["path"]] = token_identity
    return tokens


def observe_current_code_classes(*, reader, io_owner, invocation_owner,
                                 fresh_root, inherited_root, fresh_rows,
                                 inherited_rows, jar_class_names):
    """Inventory the fixed N51/N50 code-only trees; the caller owns authority.

    The single permitted fresh producer class is the fixed NO_DATA artifact.
    No DATA source, generated factory, normal-prior admission, or launch is
    recognized here. Root exclusivity is a caller precondition, not a claim of
    atomic protection against a malicious namespace changing between checks.
    """
    io_owner._assert_module_functions(
        io_owner, _IO_OWNER_FUNCTIONS, "class-inventory-owner-bindings")
    io_owner._assert_module_functions(
        reader, _READER_FUNCTIONS, "class-inventory-reader-bindings")
    io_owner._assert_module_functions(
        invocation_owner, _INVOCATION_FUNCTIONS,
        "class-inventory-invocation-bindings")

    maximum = reader.MAX_ROLE_BYTES
    if type(maximum) is not int or maximum <= 0:
        _reject("READER_LIMIT")
    fresh = _snapshot_rows(
        fresh_rows, _EXPECTED_FRESH_CLASSES, maximum, "FRESH")
    inherited = _snapshot_rows(
        inherited_rows, _EXPECTED_INHERITED_CLASSES, maximum,
        "INHERITED")
    class_count, class_bytes = _validate_scope(
        fresh, inherited, jar_class_names)

    fresh_path = invocation_owner._absolute_windows_path(fresh_root)
    inherited_path = invocation_owner._absolute_windows_path(inherited_root)
    fresh_root_fact = _inspect(reader, invocation_owner, fresh_path, "directory")
    inherited_root_fact = _inspect(
        reader, invocation_owner, inherited_path, "directory")
    if _roots_overlap(fresh_root_fact, inherited_root_fact):
        _reject("ROOT_ALIAS_OR_OVERLAP")

    fresh_initial = _scan_tree(
        reader, invocation_owner, fresh_path, fresh_root_fact, fresh, True)
    inherited_initial = _scan_tree(
        reader, invocation_owner, inherited_path, inherited_root_fact,
        inherited, False)

    fresh_tokens = _read_inventory(
        reader, io_owner, invocation_owner, fresh_path, fresh, fresh_initial,
        maximum, "class-inventory-first-read")
    inherited_tokens = _read_inventory(
        reader, io_owner, invocation_owner, inherited_path, inherited,
        inherited_initial, maximum, "class-inventory-first-read")
    fresh_tokens_again = _read_inventory(
        reader, io_owner, invocation_owner, fresh_path, fresh, fresh_initial,
        maximum, "class-inventory-recheck")
    inherited_tokens_again = _read_inventory(
        reader, io_owner, invocation_owner, inherited_path, inherited,
        inherited_initial, maximum, "class-inventory-recheck")
    if fresh_tokens_again != fresh_tokens or inherited_tokens_again != inherited_tokens:
        _reject("CLASS_TOKEN_CHANGED")

    fresh_final_root = _inspect(reader, invocation_owner, fresh_path, "directory")
    inherited_final_root = _inspect(
        reader, invocation_owner, inherited_path, "directory")
    if _roots_overlap(fresh_final_root, inherited_final_root):
        _reject("ROOT_ALIAS_OR_OVERLAP")
    fresh_final = _scan_tree(
        reader, invocation_owner, fresh_path, fresh_final_root, fresh, True)
    inherited_final = _scan_tree(
        reader, invocation_owner, inherited_path, inherited_final_root,
        inherited, False)
    if fresh_final != fresh_initial or inherited_final != inherited_initial:
        _reject("TREE_CHANGED")

    return {
        "freshClasses": sorted(fresh, key=lambda row: row["path"]),
        "inheritedClasses": sorted(inherited, key=lambda row: row["path"]),
        "classCount": class_count,
        "classBytes": class_bytes,
    }
