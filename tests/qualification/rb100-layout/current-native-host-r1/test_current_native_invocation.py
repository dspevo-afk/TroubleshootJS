"""Independent literal tests for the fixed V2 transport adapter; no data ingest."""
import argparse, ast, importlib.util, pathlib, re, subprocess, unittest
MODULE = None
ROLES = (("study", "CohortTerminalTelemetryStudy.java"),
         ("metadata", "CurrentAssemblyMetadata.java"),
         ("codec", "CurrentV2CanonicalCodec.java"),
         ("inputSetup", "CurrentV2InputSetup.java"),
         ("projections", "CurrentResultProjections.java"),
         ("inputProducer", "CohortTerminalTelemetryInputV2.java"))
SOURCES = {role: "C:\\fixture source #; space\\" + name for role, name in ROLES}
BASE = dict(classes_root="C:\\fixture run\\classes", inherited_classes_root="C:\\fixture code\\inherited",
            gwt_user="C:\\fixture deps\\gwt-user.jar", gwt_dev="C:\\fixture deps\\gwt-dev.jar")
CP = "C:\\fixture run\\classes;C:\\fixture code\\inherited;C:\\fixture deps\\gwt-user.jar;C:\\fixture deps\\gwt-dev.jar"
COMPILE = dict(BASE, javac="C:\\fixture JDK 8\\bin\\javac.exe", empty_sourcepath="C:\\fixture run\\empty",
               source_list="C:\\fixture run\\sources list #.txt")
NATIVE = dict(BASE, java="C:\\fixture JDK 8\\bin\\java.exe", run_id="aaaaaaaa-0000-4000-8000-000000000001",
              native_source_manifest_sha256="a"*64, wire_contract_sha256="b"*64,
              input_context_sha256="c"*64, output_directory="C:\\fixture run\\native output")
class InvocationTests(unittest.TestCase):
    def reject(self, operation, *args, **kwargs):
        with self.assertRaises(MODULE.InvocationError) as caught:
            operation(*args, **kwargs)
        text = str(caught.exception)
        self.assertRegex(text, r"^[A-Z][A-Z0-9_]{0,95}$")
        self.assertNotIn("fixture", text)
    def test_source_order_literal_and_input_unchanged(self):
        before=dict(SOURCES); actual=MODULE.source_list_bytes(dict(reversed(list(SOURCES.items()))))
        expected=(b'"C:/fixture source #; space/CohortTerminalTelemetryStudy.java"\n'
                  b'"C:/fixture source #; space/CurrentAssemblyMetadata.java"\n'
                  b'"C:/fixture source #; space/CurrentV2CanonicalCodec.java"\n'
                  b'"C:/fixture source #; space/CurrentV2InputSetup.java"\n'
                  b'"C:/fixture source #; space/CurrentResultProjections.java"\n'
                  b'"C:/fixture source #; space/CohortTerminalTelemetryInputV2.java"\n')
        self.assertEqual(actual, expected); self.assertEqual(SOURCES,before)
        self.assertIs(type(actual),bytes); self.assertEqual(MODULE.SOURCE_ROLES,ROLES)
    def test_compile_recipe_independent_literal(self):
        got=MODULE.compile_invocation(**COMPILE)
        self.assertEqual(got,("C:\\fixture JDK 8\\bin\\javac.exe",
          ('-source','7','-target','7','-encoding','UTF-8','-Xlint:-options','-Xlint:unchecked',
           '-classpath',CP,'-sourcepath','C:\\fixture run\\empty','-d','C:\\fixture run\\classes',
           '@C:\\fixture run\\sources list #.txt'),60000))
        self.assertIs(type(got),tuple);self.assertIs(type(got[1]),tuple)
    def test_native_recipe_independent_literal(self):
        got=MODULE.native_invocation(**NATIVE)
        self.assertEqual(got,("C:\\fixture JDK 8\\bin\\java.exe",('-Xmx1024m','-cp',CP,
          'com.lushprojects.circuitjs1.client.CohortTerminalTelemetryStudy',
          'COHORT_TERMINAL_TELEMETRY_V2','aaaaaaaa-0000-4000-8000-000000000001',
          'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
          'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
          'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',
          'C:\\fixture run\\native output'),480000))
        self.assertIs(type(got),tuple);self.assertIs(type(got[1]),tuple)
    def test_no_profile_clock_or_oracle_extension(self):
        for key,value in [('profile','GENERIC_ROUTING_R7_REFERENCE_V1'),('started',1),('fixture_oracle_sha256','d'*64)]:
            with self.subTest(key=key),self.assertRaises(TypeError):MODULE.native_invocation(**dict(NATIVE,**{key:value}))
        argv=MODULE.native_invocation(**NATIVE)[1];self.assertEqual(len(argv),10)
        self.assertNotIn('GENERIC_ROUTING_R7_REFERENCE_V1',argv)
    def test_hash_role_mapping_without_authority_claim(self):
        values=dict(NATIVE,native_source_manifest_sha256='d'*64,wire_contract_sha256='e'*64,input_context_sha256='f'*64)
        self.assertEqual(MODULE.native_invocation(**values)[1][6:9],('d'*64,'e'*64,'f'*64))
        # Equal digest strings are not rejected on spelling alone; caller binds their origins.
        values=dict(NATIVE,native_source_manifest_sha256='c'*64,wire_contract_sha256='c'*64)
        self.assertEqual(MODULE.native_invocation(**values)[1][6:9],('c'*64,'c'*64,'c'*64))
    def test_source_role_set_and_basename(self):
        wrong=[None,[],{},dict(SOURCES,extra='C:\\fixture\\x.java')]
        missing=dict(SOURCES);missing.pop('codec');wrong.append(missing)
        for value in wrong:
            with self.subTest(value_type=type(value).__name__):self.reject(MODULE.source_list_bytes,value)
        for role,name in ROLES:
            with self.subTest(role=role):self.reject(MODULE.source_list_bytes,dict(SOURCES,**{role:'C:\\fixture\\wrong.java'}))
        self.reject(MODULE.source_list_bytes,dict(SOURCES,inputProducer='C:\\fixture\\current-input.java'))
    def test_source_quote_control_relative_noncanonical(self):
        bad=[None,1,True,'',r'relative\CohortTerminalTelemetryStudy.java',
             r'C:relative\CohortTerminalTelemetryStudy.java',r'\rooted\CohortTerminalTelemetryStudy.java',
             r'C:/fixture/CohortTerminalTelemetryStudy.java',
             r'C:\fixture\..\CohortTerminalTelemetryStudy.java',
             r'C:\fixture\\CohortTerminalTelemetryStudy.java',
             r'C:\fixture.\CohortTerminalTelemetryStudy.java',
             r'C:\fixture \CohortTerminalTelemetryStudy.java',
             '\\\\?\\C:\\fixture\\CohortTerminalTelemetryStudy.java',
             '\\\\.\\C:\\fixture\\CohortTerminalTelemetryStudy.java',
             'C:\\bad"parent\\CohortTerminalTelemetryStudy.java',
             'C:\\bad\nparent\\CohortTerminalTelemetryStudy.java',
             'C:\\bad\rparent\\CohortTerminalTelemetryStudy.java',
             'C:\\bad\x00parent\\CohortTerminalTelemetryStudy.java',
             'C:\\bad\tparent\\CohortTerminalTelemetryStudy.java',
             'C:\\caf\u00e9\\CohortTerminalTelemetryStudy.java',
             'C:\\'+('x'*32768)+'\\CohortTerminalTelemetryStudy.java']
        for index,value in enumerate(bad):
            with self.subTest(index=index):self.reject(MODULE.source_list_bytes,dict(SOURCES,study=value))
    def test_unc_transport_literal(self):
        paths={role:'\\\\server\\share\\source space #;\\'+name for role,name in ROLES}
        self.assertTrue(MODULE.source_list_bytes(paths).startswith(b'"//server/share/source space #;/CohortTerminalTelemetryStudy.java"\n'))
    def test_classpath_no_empty_semicolon_or_alias(self):
        for key in BASE:
            for value in ['',None,False,'C:\\bad;injected\\x.jar','C:\\bad\nentry\\x.jar']:
                for call,base in [(MODULE.compile_invocation,COMPILE),(MODULE.native_invocation,NATIVE)]:
                    with self.subTest(key=key,value=value,call=call.__name__):self.reject(call,**dict(base,**{key:value}))
        for base,call in [(COMPILE,MODULE.compile_invocation),(NATIVE,MODULE.native_invocation)]:
            self.reject(call,**dict(base,inherited_classes_root=BASE['classes_root'].upper()))
            self.reject(call,**dict(base,gwt_dev=BASE['gwt_user'].upper()))
    def test_tool_argfile_and_jar_leaf_constraints(self):
        self.reject(MODULE.compile_invocation,**dict(COMPILE,javac='C:\\fixture\\not-javac.exe'))
        self.reject(MODULE.native_invocation,**dict(NATIVE,java='C:\\fixture\\not-java.exe'))
        self.reject(MODULE.compile_invocation,**dict(COMPILE,source_list='C:\\fixture\\sources.java'))
        self.reject(MODULE.compile_invocation,**dict(COMPILE,gwt_user='C:\\fixture\\gwt-user.txt'))
        self.reject(MODULE.native_invocation,**dict(NATIVE,gwt_dev='C:\\fixture\\gwt-dev.exe'))
    def test_native_identifiers_exact_types_and_grammar(self):
        for key in ['native_source_manifest_sha256','wire_contract_sha256','input_context_sha256']:
            for value in [None,False,64,'a'*63,'a'*65,'A'*64,'g'*64,'a'*63+'\n']:
                with self.subTest(key=key,value_type=type(value).__name__):self.reject(MODULE.native_invocation,**dict(NATIVE,**{key:value}))
        for value in [None,False,'','not-a-uuid','AAAAAAAA-0000-4000-8000-000000000001','aaaaaaaa-0000-4000-8000-000000000001\n']:
            with self.subTest(run_id_type=type(value).__name__):self.reject(MODULE.native_invocation,**dict(NATIVE,run_id=value))
    def test_raw_output_is_directory_role_not_suffix_authority(self):
        value='C:\\fixture run\\directory;with hash #.json'
        self.assertEqual(MODULE.native_invocation(**dict(NATIVE,output_directory=value))[1][-1],value)
        # File/directory identity is deliberately not guessed from a suffix or asserted by this mapper.
        for value in ['',None,'C:\\fixture\nresult',r'C:/fixture/output',r'C:\fixture\..\output']:
            with self.subTest(value=value):self.reject(MODULE.native_invocation,**dict(NATIVE,output_directory=value))
    def test_launch_aggregate_bound_and_direct_windows_serialization(self):
        for recipe in [MODULE.compile_invocation(**COMPILE),MODULE.native_invocation(**NATIVE)]:
            self.assertLessEqual(len(subprocess.list2cmdline([recipe[0],*recipe[1]])),32766)
        long_root='C:\\'+('segment\\'*1100)
        many=dict(classes_root=long_root+'fresh',inherited_classes_root=long_root+'inherited',
                  gwt_user=long_root+'user.jar',gwt_dev=long_root+'dev.jar')
        self.reject(MODULE.compile_invocation,**dict(COMPILE,**many))
        self.reject(MODULE.native_invocation,**dict(NATIVE,**many))
        # Argfile content is separate from command-line size; no invented 32K
        # bound on the combined six Source paths inside a file.
        paths={role:long_root+name for role,name in ROLES}
        self.assertGreater(len(MODULE.source_list_bytes(paths)),32766)
    def test_pure_source_no_io_imports_or_entrypoint(self):
        tree=ast.parse(pathlib.Path(MODULE.__file__).read_bytes())
        imports=[]
        for node in ast.walk(tree):
            if isinstance(node,ast.Import):imports.extend(x.name for x in node.names)
            elif isinstance(node,ast.ImportFrom):imports.append(node.module)
        self.assertTrue(set(imports)<= {'ntpath','re'})
        self.assertFalse(any(isinstance(n,ast.Name) and n.id in {'open','exec','eval','__import__'} for n in ast.walk(tree)))
        # Top-level library statements may declare/import constants/functions/
        # classes; they may not dispatch an entrypoint or call arbitrary code.
        for node in tree.body:
            self.assertIsInstance(node,(ast.Expr,ast.Import,ast.ImportFrom,ast.Assign,ast.FunctionDef,ast.ClassDef))
            if isinstance(node,ast.Expr): self.assertIsInstance(node.value,ast.Constant)
            if isinstance(node,ast.Assign): self.assertFalse(any(isinstance(x,ast.Call) for x in ast.walk(node)))
            if isinstance(node,ast.FunctionDef):
                self.assertFalse(node.decorator_list)
                self.assertFalse(node.args.defaults)
                self.assertTrue(all(x is None for x in node.args.kw_defaults))
            if isinstance(node,ast.ClassDef):
                self.assertFalse(node.decorator_list)
                self.assertFalse(node.keywords)
                self.assertFalse(any(isinstance(x,ast.Call) for x in ast.walk(node)))
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--module-source',required=True);parser.add_argument('-v','--verbose',action='store_true');args=parser.parse_args()
    spec=importlib.util.spec_from_file_location('current_native_invocation_under_test',args.module_source)
    MODULE=importlib.util.module_from_spec(spec);spec.loader.exec_module(MODULE)
    suite=unittest.defaultTestLoader.loadTestsFromTestCase(InvocationTests)
    result=unittest.TextTestRunner(verbosity=2 if args.verbose else 1).run(suite)
    raise SystemExit(0 if result.wasSuccessful() else 1)
