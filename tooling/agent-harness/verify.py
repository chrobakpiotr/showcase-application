#!/usr/bin/env python3
"""Thin read-only CLI for the shared advisory verification planner."""
import argparse
import json

from verification.model import Family, InvalidPolicy
from verification.planner import build_plan
from verification.profile import load_profile


class SafeArgumentParser(argparse.ArgumentParser):
    def error(self, message):
        # argparse's default includes untrusted arguments in its diagnostics.
        print(json.dumps({'status': 'invalid-policy'}))
        raise SystemExit(2)


def main(argv=None):
    parser = SafeArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='operation', required=True)
    plan = sub.add_parser('plan', help='emit an advisory plan; grants no execution authority')
    plan.add_argument('--repo', required=True)
    plan.add_argument('--profile', required=True, help='trusted control-repository profile snapshot')
    plan.add_argument('--base-sha', required=True)
    plan.add_argument('--family-id', required=True)
    plan.add_argument('--policy-checkpoint', required=True)
    plan.add_argument('--origin-policy', choices=['integration', 'task-completion'], default='integration')
    plan.add_argument('--task-command', action='append', default=[])
    args = parser.parse_args(argv)
    try:
        profile = load_profile(args.profile)
        family = Family(args.family_id, args.base_sha, args.origin_policy, profile.content_hash, args.policy_checkpoint)
        result = build_plan(args.repo, profile, family, task_commands=args.task_command)
        print(json.dumps(result.to_record(), sort_keys=True))
        return 0
    except (InvalidPolicy, ValueError, OSError):
        print(json.dumps({'status': 'invalid-policy'}))
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
