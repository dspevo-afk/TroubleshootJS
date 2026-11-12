"""Bounded byte facts for current native source and tool inputs."""


MAX_SOURCE_BYTES = 4194304
MAX_PRODUCER_SOURCE_BYTES = 1048576
MAX_TOOL_BYTES = 8388608
_TOOL_ROLES = (
    ("nativeJava", "java.exe"),
    ("nativeJavac", "javac.exe"),
)
_SPEC_KEYS = ("path", "expectedBytes", "maxBytes", "sha256")
_READER_FUNCTIONS = (
    "_check_windows_local_lexical_path",
    "_inspect_path",
    "_read_file_once",
    "_path_identity",
    "_token_identity",
)
_OBSERVER_FUNCTIONS = (
    "_reader_api",
    "_validate_fact_spec",
    "_read_fact",
    "_read_fact_map",
    "_recheck_fact_map",
)


class NativeByteFactsError(ValueError):
    """Path-free local rejection from the byte-facts adapter."""

    def __init__(self, code):
        self.code = code
        super().__init__(code)


def _reject(code):
    raise NativeByteFactsError(code)


def _source_role_names(invocation_owner):
    try:
        roles = invocation_owner.SOURCE_ROLES
    except AttributeError:
        _reject("SOURCE_ROLES_INVALID")
    if type(roles) is not tuple or len(roles) != 6:
        _reject("SOURCE_ROLES_INVALID")
    names = []
    basenames = []
    for row in roles:
        if (type(row) is not tuple or len(row) != 2
                or type(row[0]) is not str or not row[0]
                or type(row[1]) is not str or not row[1]):
            _reject("SOURCE_ROLES_INVALID")
        names.append(row[0])
        basenames.append(row[1])
    if len(set(names)) != len(names) or len(set(basenames)) != len(basenames):
        _reject("SOURCE_ROLES_INVALID")
    return roles, tuple(names)


def _snapshot_specs(specs, names, label):
    if type(specs) is not dict or set(specs) != set(names):
        _reject(label + "_MAP_INVALID")
    snapshot = {}
    for name in names:
        row = specs[name]
        if type(row) is not dict or set(row) != set(_SPEC_KEYS):
            _reject(label + "_ROW_INVALID")
        snapshot[name] = dict(row)
    return snapshot


def observe_native_code_tools(*, reader, io_owner, observer,
                              invocation_owner, source_specs, tool_specs,
                              include_identity_tokens=False):
    """Read and recheck byte facts for exactly the current six sources and two tools.

    Modules and spec authority must already be authenticated by the caller.
    Binding checks here preserve helper ownership; they do not establish source
    provenance or an atomic snapshot across the individual reads.

    Opt-in token maps retain the observer's original immutable identity triples
    for a trusted caller's later _recheck_fact_map call. They contain no bodies
    or live handles and are not persisted authority or a launch capability.
    """
    if type(include_identity_tokens) is not bool:
        _reject("IDENTITY_TOKENS_FLAG")
    io_owner._assert_module_functions(
        io_owner, ("_assert_module_functions",), "native-byte-facts-owner")
    io_owner._assert_module_functions(
        reader, _READER_FUNCTIONS, "native-byte-facts-reader")
    io_owner._assert_module_functions(
        observer, _OBSERVER_FUNCTIONS, "native-byte-facts-observer")
    io_owner._assert_module_functions(
        invocation_owner, ("source_list_bytes", "_tool_path"),
        "native-byte-facts-invocation")

    source_roles, source_names = _source_role_names(invocation_owner)
    tool_names = tuple(name for name, _basename in _TOOL_ROLES)
    source_snapshot = _snapshot_specs(source_specs, source_names, "SOURCE")
    tool_snapshot = _snapshot_specs(tool_specs, tool_names, "TOOL")

    for name in tool_names:
        observer._validate_fact_spec(
            name, tool_snapshot[name], maximum_limit=MAX_TOOL_BYTES,
            code="NATIVE_TOOL_SPEC")
    for name in source_names:
        limit = (MAX_PRODUCER_SOURCE_BYTES if name == "inputProducer"
                 else MAX_SOURCE_BYTES)
        observer._validate_fact_spec(
            name, source_snapshot[name], maximum_limit=limit,
            code="NATIVE_SOURCE_SPEC")

    source_paths = {
        name: source_snapshot[name]["path"] for name in source_names
    }
    invocation_owner.source_list_bytes(source_paths)
    for name, basename in _TOOL_ROLES:
        invocation_owner._tool_path(tool_snapshot[name]["path"], basename)

    api = observer._reader_api(reader)
    tool_facts, tool_tokens, _tool_retained = observer._read_fact_map(
        api, tool_snapshot, category="TOOL", maximum_limit=MAX_TOOL_BYTES,
        required_names=tool_names)
    source_facts, source_tokens, _source_retained = observer._read_fact_map(
        api, source_snapshot, category="SOURCE", maximum_limit=MAX_SOURCE_BYTES,
        required_names=tuple(name for name, _basename in source_roles))

    observer._recheck_fact_map(
        api, tool_snapshot, tool_tokens, category="TOOL",
        maximum_limit=MAX_TOOL_BYTES, names=tool_names)
    observer._recheck_fact_map(
        api, source_snapshot, source_tokens, category="SOURCE",
        maximum_limit=MAX_SOURCE_BYTES,
        names=tuple(name for name, _basename in source_roles))

    result = {"toolFacts": tool_facts, "sourceFacts": source_facts}
    if include_identity_tokens:
        result["sourceTokens"] = dict(source_tokens)
        result["toolTokens"] = dict(tool_tokens)
    return result
