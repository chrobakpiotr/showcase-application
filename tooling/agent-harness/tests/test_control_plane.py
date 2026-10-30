import importlib.util
import json
import pathlib
import subprocess
import tempfile
import threading
import unittest
from unittest import mock

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / 'control_plane.py'
spec = importlib.util.spec_from_file_location('sdd_control_plane', MODULE_PATH)
cp = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(cp)


class ControlPlaneTest(unittest.TestCase):
    def test_snapshot_writer_uses_shared_lifecycle_lock_for_directory_temp_and_rename(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp) / 'repo'; root.mkdir()
            subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
            prior_repo = cp.REPO
            cp.REPO = root
            state = cp.operational_state.directory(root, 'control-plane')
            acquired = threading.Event(); release = threading.Event()
            finished = threading.Event(); errors = []
            def lifecycle_writer():
                with cp.repository_state_lock.lifecycle_lock(root):
                    acquired.set()
                    release.wait(3)
            def save():
                try:
                    cp.save_snapshot({'source': 'github', 'key': 'GH-1', 'title': 'A task'})
                except BaseException as exc:
                    errors.append(exc)
                finally:
                    finished.set()
            holder = threading.Thread(target=lifecycle_writer); holder.start()
            try:
                self.assertTrue(acquired.wait(2))
                saver = threading.Thread(target=save); saver.start()
                self.assertFalse(finished.wait(0.15), 'snapshot writer bypassed the lifecycle lock')
                self.assertFalse(state.exists(), 'snapshot directory was created before lock acquisition')
                release.set()
                self.assertTrue(finished.wait(3))
                saver.join(timeout=1)
                self.assertEqual([], errors)
                output = state / 'github-GH-1.json'
                self.assertEqual({'source': 'github', 'key': 'GH-1', 'title': 'A task'}, json.loads(output.read_text()))
                self.assertEqual([output.name], [entry.name for entry in state.iterdir()])
            finally:
                release.set(); holder.join(timeout=2)
                cp.REPO = prior_repo

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

