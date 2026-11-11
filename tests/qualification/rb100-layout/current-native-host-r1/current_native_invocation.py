"""Pure, bounded invocation recipes for the current V2 native host.

These functions only map caller-supplied values into source-list bytes and
argument tuples. They do not perform admission, authenticate provenance, or
establish filesystem identity. The ASCII and path checks bound transport only;
they make no filesystem, lifetime, or reparse-point claims.
"""

import ntpath


SOURCE_ROLES = (
    ("study", "CohortTerminalTelemetryStudy.java"),
    ("metadata", "CurrentAssemblyMetadata.java"),
    ("codec", "CurrentV2CanonicalCodec.java"),
    ("inputSetup", "CurrentV2InputSetup.java"),
    ("projections", "CurrentResultProjections.java"),
    ("inputProducer", "CohortTerminalTelemetryInputV2.java"),
)

_MAX_PATH_CHARS = 32767
_MAX_COMMAND_LINE_CHARS = 32766  # Reserve the terminating UTF-16 NUL.
_LOWER_HEX = "0123456789abcdef"
_WINDOWS_INVALID_COMPONENT_CHARS = '<>:"|?*'


class InvocationError(ValueError):
    """A stable, path-free error raised for invalid invocation inputs."""


def _ascii_text(value):
    if type(value) is not str or not value or len(value) > _MAX_PATH_CHARS:
        raise InvocationError("INVALID_TEXT")
    if any(ord(char) < 0x20 or ord(char) > 0x7E for char in value):
        raise InvocationError("INVALID_TEXT")
    if '"' in value:
        raise InvocationError("INVALID_TEXT")
    return value


def _absolute_windows_path(value):
    value = _ascii_text(value)
    if len(value) > _MAX_PATH_CHARS or "/" in value:
        raise InvocationError("INVALID_PATH")
    if value.startswith(("\\\\?\\", "\\\\.\\")):
        raise InvocationError("INVALID_PATH")

    if (len(value) >= 3 and value[0].isalpha() and value[1] == ":"
            and value[2] == "\\"):
        components = value[3:].split("\\")
        if not components or components == [""]:
            raise InvocationError("INVALID_PATH")
    elif value.startswith("\\\\"):
        components = value[2:].split("\\")
        # Require at least server, share, and a task-owned child path.
        if len(components) < 3:
            raise InvocationError("INVALID_PATH")
    else:
        raise InvocationError("INVALID_PATH")

    for component in components:
        if (not component or component in (".", "..")
                or component.endswith((".", " "))
                or any(char in component for char in _WINDOWS_INVALID_COMPONENT_CHARS)):
            raise InvocationError("INVALID_PATH")
    if ntpath.normpath(value) != value:
        raise InvocationError("INVALID_PATH")
    return value


def _tool_path(value, expected_basename):
    value = _absolute_windows_path(value)
    if ntpath.basename(value).lower() != expected_basename:
        raise InvocationError("INVALID_TOOL")
    return value


def _classpath(paths):
    checked = []
    seen = set()
    for index, path in enumerate(paths):
        path = _absolute_windows_path(path)
        if index >= 2 and ntpath.splitext(path)[1].lower() != ".jar":
            raise InvocationError("INVALID_JAR")
        if ";" in path:
            raise InvocationError("INVALID_CLASSPATH")
        key = ntpath.normcase(path)
        if key in seen:
            raise InvocationError("DUPLICATE_CLASSPATH")
        seen.add(key)
        checked.append(path)
    return ";".join(checked)


def _source_list_path(value):
    value = _absolute_windows_path(value)
    if ntpath.splitext(value)[1].lower() != ".txt":
        raise InvocationError("INVALID_SOURCE_LIST")
    return value


def _sha256(value):
    value = _ascii_text(value)
    if len(value) != 64 or any(char not in _LOWER_HEX for char in value):
        raise InvocationError("INVALID_SHA256")
    return value


def _run_id(value):
    value = _ascii_text(value)
    if (len(value) != 36 or value[8] != "-" or value[13] != "-"
            or value[18] != "-" or value[23] != "-"):
        raise InvocationError("INVALID_RUN_ID")
    if any(char not in _LOWER_HEX for index, char in enumerate(value)
           if index not in (8, 13, 18, 23)):
        raise InvocationError("INVALID_RUN_ID")
    return value


def source_list_bytes(source_paths):
    """Build the six quoted UTF-8 argfile lines in fixed role order."""
    if type(source_paths) is not dict or len(source_paths) != len(SOURCE_ROLES):
        raise InvocationError("INVALID_SOURCE_ROLES")
    if any(type(role) is not str for role in source_paths):
        raise InvocationError("INVALID_SOURCE_ROLES")
    expected_roles = {role for role, _basename in SOURCE_ROLES}
    if set(source_paths) != expected_roles:
        raise InvocationError("INVALID_SOURCE_ROLES")

    lines = []
    seen = set()
    for role, basename in SOURCE_ROLES:
        path = _absolute_windows_path(source_paths[role])
        if ntpath.basename(path) != basename:
            raise InvocationError("INVALID_SOURCE_BASENAME")
        key = ntpath.normcase(path)
        if key in seen:
            raise InvocationError("DUPLICATE_SOURCE_PATH")
        seen.add(key)
        lines.append('"' + path.replace("\\", "/") + '"\n')
    return "".join(lines).encode("utf-8")


def _invocation(executable, argv, limit_ms):
    # Values are printable ASCII; current tokens have no quotes or trailing
    # backslash, so two quote characters per token conservatively bound the
    # direct Windows argv serialization without implementing another encoder.
    tokens = (executable,) + argv
    if any('"' in token or token.endswith("\\") for token in tokens):
        raise InvocationError("INVALID_ARGUMENT_TRANSPORT")
    upper_bound = sum(len(token) + 2 for token in tokens) + len(argv)
    if upper_bound > _MAX_COMMAND_LINE_CHARS:
        raise InvocationError("COMMAND_LINE_LIMIT")
    return executable, argv, limit_ms


def compile_invocation(*, javac, classes_root, inherited_classes_root,
                       gwt_user, gwt_dev, empty_sourcepath, source_list):
    """Return the fixed JDK compiler executable, argv tuple, and 60s budget."""
    javac = _tool_path(javac, "javac.exe")
    classpath = _classpath((classes_root, inherited_classes_root, gwt_user, gwt_dev))
    empty_sourcepath = _absolute_windows_path(empty_sourcepath)
    source_list = _source_list_path(source_list)
    argv = (
        "-source", "7", "-target", "7", "-encoding", "UTF-8",
        "-Xlint:-options", "-Xlint:unchecked", "-classpath", classpath,
        "-sourcepath", empty_sourcepath, "-d", _absolute_windows_path(classes_root),
        "@" + source_list,
    )
    return _invocation(javac, argv, 60000)


def native_invocation(*, java, classes_root, inherited_classes_root,
                      gwt_user, gwt_dev, run_id, native_source_manifest_sha256,
                      wire_contract_sha256, input_context_sha256,
                      output_directory):
    """Return the fixed V2 native executable, argv tuple, and 480s budget."""
    java = _tool_path(java, "java.exe")
    classpath = _classpath((classes_root, inherited_classes_root, gwt_user, gwt_dev))
    run_id = _run_id(run_id)
    native_source_manifest_sha256 = _sha256(native_source_manifest_sha256)
    wire_contract_sha256 = _sha256(wire_contract_sha256)
    input_context_sha256 = _sha256(input_context_sha256)
    output_directory = _absolute_windows_path(output_directory)
    argv = (
        "-Xmx1024m", "-cp", classpath,
        "com.lushprojects.circuitjs1.client.CohortTerminalTelemetryStudy",
        "COHORT_TERMINAL_TELEMETRY_V2", run_id,
        native_source_manifest_sha256, wire_contract_sha256,
        input_context_sha256, output_directory,
    )
    # Hash validation checks syntax and slot placement only; it does not prove
    # digest provenance or detect a same-shape digest substituted by the caller.
    return _invocation(java, argv, 480000)
