# Required status checks deployment

Status: **PREPARED / REMOTE RULESET CHANGE PENDING HUMAN AUTHORIZATION**

The repository currently has active ruleset `Admin rules` (`21939086`). The observed
ruleset has no `required_status_checks` rule and has an `always` bypass for repository
role `5`.

The real aggregate job names observed on the `main` push at
`16450b6c9b42a8db8458a0d866fde7db182cabd1` are:

- `CI quality gate` from `.github/workflows/ci.yml`
- `Agentic SDD quality gate` from `.github/workflows/agentic-sdd.yml`

Those two aggregate contexts are the desired required checks. Individual implementation
jobs remain dependencies of their aggregate; the ruleset does not need to duplicate the
whole job graph.

The desired rule fragment is checked in at:

```text
tooling/quality/github-required-status-policy.json
```

The recommended bypass policy is no always-bypass actor. If an emergency bypass is
required operationally, define and review that exception explicitly instead of leaving
the current administrator role as an unconditional bypass.

## Deployment boundary

This repository change does **not** mutate GitHub ruleset settings. Applying the ruleset
is a separate human-authorized deployment action.

After deployment, verify through GitHub API/UI that:

1. ruleset `21939086` contains `required_status_checks`;
2. both aggregate contexts above are required;
3. the agreed bypass policy is reflected by `bypass_actors`;
4. a pull request with a negative aggregate gate cannot be merged through the normal
   path;
5. a positive pull request shows both aggregate checks as successful.

Do not mark S22-06 CLOSED until the remote ruleset is verified after deployment.
