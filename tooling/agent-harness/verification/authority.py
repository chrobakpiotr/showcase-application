"""Lifecycle-bound immutable Verification Execution Plan helpers."""
from __future__ import annotations

import hashlib
import pathlib

from .serialization import canonical


def execution_plan_record(feature_id: str, feature_fingerprint: str, generation: int,
                          profile_id: str, profile, plan, *, task_id: str, task_attempt: int,
                          task_commands=(), origin_binding: str):
    family = {
        'id': plan.family.id, 'base_sha': plan.family.base_sha,
        'origin_policy': plan.family.origin_policy, 'profile_hash': plan.family.profile_hash,
        'policy_checkpoint': plan.family.policy_checkpoint,
        'candidate_identity': plan.family.candidate_identity,
        'final_changed_surface_id': plan.family.final_changed_surface_id,
    }
    obligations = []
    for decision in plan.decisions:
        node, gate = decision.node, decision.node.gate
        identity = {'family_id': family['id'], 'gate_id': node.id,
                    'profile_gate_id': node.profile_gate_id,
                    'occurrence': node.occurrence, 'ordinal': node.ordinal}
        obligation_id = 'verification-obligation-v1:sha256:' + hashlib.sha256(canonical(identity)).hexdigest()
        obligations.append({'obligation_id': obligation_id, **identity,
            'command': gate.command, 'command_hash': gate.command_hash, 'cwd': gate.cwd,
            'dependencies': list(node.dependencies), 'critical': gate.critical,
            'retry_policy': gate.retry_policy, 'retry_controls': list(gate.retry_controls),
            'sandbox': gate.sandbox})
    units = []
    for obligation in obligations:
        member_ids = [obligation['obligation_id']]
        unit_id = 'verification-unit-v1:sha256:' + hashlib.sha256(canonical(member_ids)).hexdigest()
        units.append({'unit_id': unit_id, 'obligation_ids': member_ids})
    body = {'schema_version': 1, 'feature_id': feature_id,
        'task_id': task_id, 'task_attempt': task_attempt,
        'feature_fingerprint': feature_fingerprint, 'lifecycle_generation': generation,
        'family': family, 'profile_id': profile_id, 'profile_hash': profile.content_hash,
        'policy_checkpoint': plan.family.policy_checkpoint,
        'candidate_identity': family['candidate_identity'],
        'final_changed_surface_id': family['final_changed_surface_id'],
        'origin_binding': origin_binding, 'obligations': obligations,
        'execution_units': units,
        'task_commands': [dict(command=item['command'], cwd=item.get('cwd', '.'))
                          if isinstance(item, dict) else {'command': item, 'cwd': '.'}
                          for item in task_commands]}
    body['plan_id'] = 'verification-plan-v1:sha256:' + hashlib.sha256(canonical(body)).hexdigest()
    return body


def publish_and_accept(repository: pathlib.Path, feature_dir: pathlib.Path, record: dict,
                       *, expected_generation: int):
    """Subordinate immutable publication followed by authoritative state CAS."""
    from .store import VerificationStore
    import harness
    store = VerificationStore(repository)
    store.publish_plan_record(record)
    return harness.accept_verification_plan(feature_dir, record,
                                            expected_generation=expected_generation)


def resolve_accepted(repository: pathlib.Path, plan_id: str) -> dict:
    import harness
    return harness.resolve_accepted_verification_plan(pathlib.Path(repository), plan_id)


def resolve_execution(repository: pathlib.Path, plan_id: str, *, unit_id: str | None = None):
    """Resolve exact accepted authority and rebuild the executable plan from it."""
    import sys
    import harness
    from .candidate import CandidateSealError, seal_candidate
    from .model import Family
    from .planner import build_plan
    from .profile import load_profile
    from .store import StoreError

    repository = pathlib.Path(repository).resolve(strict=True)
    record = resolve_accepted(repository, plan_id)
    feature_dir = repository / 'docs' / 'specs' / record['feature_id']
    family_record = record['family']
    profile_root = pathlib.Path(__file__).resolve().parent.parent / 'verification-profiles'
    profile_path = (profile_root / (record['profile_id'] + '.json')).resolve(strict=True)
    if profile_path.parent != profile_root.resolve(strict=True):
        raise StoreError('ACCEPTED_PLAN_UNAVAILABLE')
    profile = load_profile(profile_path)
    if (profile.content_hash != record['profile_hash'] or
            family_record.get('profile_hash') != record['profile_hash'] or
            record.get('origin_binding') != family_record.get('origin_policy') or
            family_record.get('policy_checkpoint') != record.get('policy_checkpoint') or
            family_record.get('candidate_identity') != record.get('candidate_identity') or
            family_record.get('final_changed_surface_id') != record.get('final_changed_surface_id')):
        raise StoreError('PLAN_BINDING_MISMATCH')
    try:
        seal = seal_candidate(repository, family_record['base_sha'], {
            'family_id': family_record['id'], 'profile_hash': record['profile_hash'],
            'policy_checkpoint': record['policy_checkpoint'],
            'origin_policy': family_record['origin_policy']},
            trusted_runtime_root=None)
    except CandidateSealError:
        raise StoreError('PLAN_BINDING_MISMATCH') from None
    if (seal.candidate_identity != record['candidate_identity'] or
            seal.changed_surface_id != record['final_changed_surface_id']):
        raise StoreError('PLAN_BINDING_MISMATCH')
    family = Family(family_record['id'], family_record['base_sha'], family_record['origin_policy'],
                    record['profile_hash'], record['policy_checkpoint'],
                    record['candidate_identity'], record['final_changed_surface_id'])
    commands = record['task_commands']
    plan = build_plan(repository, profile, family, task_commands=commands)
    expected = execution_plan_record(record['feature_id'], record['feature_fingerprint'],
        record['lifecycle_generation'], record['profile_id'], profile, plan,
        task_id=record['task_id'], task_attempt=record['task_attempt'],
        task_commands=commands, origin_binding=record['origin_binding'])
    if expected != record:
        raise StoreError('PLAN_BINDING_MISMATCH')
    obligation_by_id = {item['obligation_id']: item for item in record['obligations']}
    units = {item['unit_id']: item for item in record['execution_units']}
    if unit_id is not None and unit_id not in units:
        raise StoreError('PLAN_BINDING_MISMATCH')
    selected_units = [units[unit_id]] if unit_id is not None else list(record['execution_units'])
    selected_obligations = {obligation for unit in selected_units for obligation in unit['obligation_ids']}
    if selected_obligations != {key for key in obligation_by_id if unit_id is None}:
        if unit_id is None:
            raise StoreError('PLAN_BINDING_MISMATCH')
    obligation_order = [item['obligation_id'] for item in record['obligations']]
    selected_indexes = {obligation_order.index(key) for key in selected_obligations}
    decisions = tuple(item for index, item in enumerate(plan.decisions) if index in selected_indexes)
    if len(decisions) != len(selected_indexes):
        raise StoreError('PLAN_BINDING_MISMATCH')
    return record, profile, __import__('dataclasses').replace(plan, decisions=decisions), selected_units


def prepare_task_plan(repository: pathlib.Path, feature_dir: pathlib.Path, task_id: str,
                      task_attempt: int, base_sha: str, task_commands):
    """Trusted orchestration publishes and lifecycle-accepts post-seal authority."""
    import harness
    from .candidate import CandidateSealError, seal_candidate
    from .model import Family
    from .planner import build_plan
    from .profile import load_profile
    from .store import StoreError

    repository = pathlib.Path(repository).resolve(strict=True)
    feature_dir = pathlib.Path(feature_dir).resolve(strict=True)
    doc = harness.load_validated(feature_dir)
    state = harness.load_state(feature_dir, doc)
    task_state = state.get('tasks', {}).get(task_id)
    if (not isinstance(task_state, dict) or task_state.get('status') != 'running' or
            task_state.get('attempts') != task_attempt):
        raise StoreError('ACCEPTED_PLAN_UNAVAILABLE')
    profile_id = 'showcase'
    profile_path = pathlib.Path(__file__).resolve().parent.parent / 'verification-profiles/showcase.json'
    profile = load_profile(profile_path)
    family_id = f'{feature_dir.name.lower()}-{task_id.lower()}-attempt-{task_attempt}'
    policy_checkpoint = profile.content_hash
    origin_binding = 'task-completion'
    try:
        seal = seal_candidate(repository, base_sha, {
            'family_id': family_id, 'profile_hash': profile.content_hash,
            'policy_checkpoint': policy_checkpoint, 'origin_policy': origin_binding})
    except CandidateSealError as exc:
        raise StoreError(exc.reason) from None
    family = Family(family_id, base_sha, origin_binding, profile.content_hash,
                    policy_checkpoint, seal.candidate_identity, seal.changed_surface_id)
    commands = [dict(command=item['command'], cwd=item.get('cwd', '.'))
                if isinstance(item, dict) else {'command': item, 'cwd': '.'} for item in task_commands]
    plan = build_plan(repository, profile, family, task_commands=commands)
    record = execution_plan_record(doc['feature'], harness.feature_fingerprint(feature_dir),
        state.get('feature_generation', 1), profile_id, profile, plan,
        task_id=task_id, task_attempt=task_attempt, task_commands=commands,
        origin_binding=origin_binding)
    publish_and_accept(repository, feature_dir, record,
                       expected_generation=state.get('feature_generation', 1))
    return record
