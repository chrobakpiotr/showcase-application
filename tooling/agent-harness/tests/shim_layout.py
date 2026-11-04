"""AH5-16b/c: the layout every delegating shim keeps, checked on its own terms inside Showcase, where the original
source is no longer present after adoption.

At module level a shim holds only imports that shadow none of its delegation layer and bind each name once (no star
import), its kept paths, the sys.path guard, aliases of the library's classes and constants, one delegate per library
function with that function's signature (`ns` aside) that passes its own parameters through in order with
`ns=_NS`, the namespace binding after all of them, and the script entry point.
"""
import ast
import inspect

PATH_GUARD = 'if str(HERE) not in sys.path:\n    sys.path.insert(0, str(HERE))'
ENTRY_POINT = "if __name__ == '__main__':\n    main()"


def library_functions(library):
    return sorted(n.name for n in ast.parse(inspect.getsource(library)).body
                  if isinstance(n, ast.FunctionDef) and n.name != 'bind')


def pass_through(alias, name, arguments):
    """The one call a delegate may make: its own parameters, in order, and the shim's namespace."""
    parts = ([a.arg for a in arguments.posonlyargs + arguments.args]
             + ([f'*{arguments.vararg.arg}'] if arguments.vararg else [])
             + [f'{a.arg}={a.arg}' for a in arguments.kwonlyargs]
             + ['ns=_NS'] + ([f'**{arguments.kwarg.arg}'] if arguments.kwarg else []))
    return f"{alias}.{name}({', '.join(parts)})"


def check_shim_layout(test, shim, library, aliases, paths, bindings=()):
    """Assert the layout above for `shim` (the consumer module) delegating to `library` (agent_harness.<module>).

    `bindings`: names the shim binds as a tuple of another library module's objects, `a, b = (_impl.a, _impl.b)`
    (the generate package's validation aliases); each must be that object, under its own name."""
    alias = '_' + library.__name__.rsplit('.', 1)[-1]
    binding = f'{alias}.bind(_transitions.ModuleNamespace(globals()))'
    functions = library_functions(library)
    tree = ast.parse(inspect.getsource(shim))
    layer = set(functions) | set(aliases) | set(paths) | set(bindings) | {'_NS'}
    defined, places, imported = [], {}, []
    for index, node in enumerate(tree.body):
        text = ast.unparse(node)
        if index == 0 and isinstance(node, ast.Expr) and isinstance(node.value, ast.Constant):
            continue  # the module docstring
        if isinstance(node, (ast.Import, ast.ImportFrom)):
            bound = [(a.asname or a.name).split('.')[0] for a in node.names]
            test.assertFalse(set(bound) & layer | ({'*'} & set(bound)), text)  # no shadowing, no star import
            imported += bound
            continue
        if isinstance(node, ast.If):
            test.assertIn(text, (PATH_GUARD, ENTRY_POINT))
            places[text] = index
            continue
        if isinstance(node, ast.FunctionDef):
            with test.subTest(node.name):
                test.assertIn(node.name, functions)
                test.assertEqual([], node.decorator_list)
                test.assertEqual(1, len(node.body))
                test.assertIsInstance(node.body[0], ast.Return)
                test.assertEqual(pass_through(alias, node.name, node.args), ast.unparse(node.body[0].value))
            defined.append(node.name)
            places.setdefault('layer', []).append(index)
            continue
        test.assertIsInstance(node, ast.Assign, text)
        test.assertEqual(1, len(node.targets), text)
        if isinstance(node.targets[0], ast.Tuple):
            names = [ast.unparse(e) for e in node.targets[0].elts]
            test.assertTrue(set(names) <= set(bindings), text)
            test.assertIsInstance(node.value, ast.Tuple, text)
            test.assertEqual([f'_impl.{n}' for n in names], [ast.unparse(e) for e in node.value.elts], text)
            defined += names
            continue
        name = ast.unparse(node.targets[0])
        if name in aliases:
            test.assertEqual(f'{alias}.{name}', ast.unparse(node.value))
            places.setdefault('layer', []).append(index)
        elif name == '_NS':
            test.assertEqual(binding, ast.unparse(node.value))
            places['_NS'] = index
        else:
            test.assertIn(name, paths, text)
        defined.append(name)
    test.assertEqual(sorted(functions + list(aliases) + list(paths) + list(bindings) + ['_NS']), sorted(defined))
    test.assertEqual(sorted(set(imported)), sorted(imported))  # a second import would rebind a name the library reads
    test.assertIn(PATH_GUARD, places)
    test.assertLess(max(places['layer']), places['_NS'])
    test.assertLess(places['_NS'], places[ENTRY_POINT])
    for name in aliases:
        with test.subTest(name):
            test.assertIs(getattr(library, name), getattr(shim, name))
    for name in functions:  # each delegate has the library function's signature, `ns` aside
        with test.subTest(name):
            signature = inspect.signature(getattr(library, name))
            signature = signature.replace(parameters=[p for p in signature.parameters.values() if p.name != 'ns'])
            test.assertEqual(signature, inspect.signature(getattr(shim, name)))
