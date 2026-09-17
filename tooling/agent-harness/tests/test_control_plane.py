import importlib.util
import pathlib
import unittest
from unittest import mock

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / 'control_plane.py'
spec = importlib.util.spec_from_file_location('sdd_control_plane', MODULE_PATH)
cp = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(cp)


class ControlPlaneTest(unittest.TestCase):
    def test_github_issue_normalizes_to_read_only_intent(self):
        item = cp.normalize_github({
            'number': 42, 'title': 'Add cart', 'state': 'open', 'body': 'Need a cart',
            'html_url': 'https://github.com/o/r/issues/42', 'labels': [{'name': 'feature'}]
        })
        self.assertEqual('github', item['source'])
        self.assertEqual('GH-42', item['key'])
        self.assertIn('not an accepted specification', cp.render_intent(item))

    def test_jira_adf_description_is_flattened(self):
        item = cp.normalize_jira({
            'id': '100', 'key': 'SHOP-42',
            'fields': {
                'summary': 'Add cart', 'status': {'name': 'To Do'}, 'labels': ['backend'],
                'description': {'type': 'doc', 'content': [
                    {'type': 'paragraph', 'content': [{'type': 'text', 'text': 'Acceptance context'}]}
                ]}
            }
        }, 'https://example.atlassian.net')
        self.assertEqual('SHOP-42', item['key'])
        self.assertIn('Acceptance context', item['description'])
        self.assertEqual('https://example.atlassian.net/browse/SHOP-42', item['url'])

    def test_remote_fetch_is_explicit_get_and_uses_no_redirect_opener(self):
        seen = {}

        class Response:
            def __enter__(self): return self
            def __exit__(self, *args): return False
            def read(self): return b'{"number":1}'

        class Opener:
            def open(self, req, timeout):
                seen['method'] = req.get_method()
                seen['url'] = req.full_url
                seen['timeout'] = timeout
                return Response()

        with mock.patch.object(cp.urllib.request, 'build_opener', return_value=Opener()) as build:
            doc = cp.https_json('https://example.test/item', {'Authorization': 'Bearer secret'})
        self.assertEqual({'number': 1}, doc)
        self.assertEqual('GET', seen['method'])
        self.assertEqual('https://example.test/item', seen['url'])
        self.assertEqual(20, seen['timeout'])
        self.assertIsInstance(build.call_args.args[0], cp.NoRedirect)


if __name__ == '__main__':
    unittest.main()
