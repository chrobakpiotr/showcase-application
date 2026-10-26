# Unresolved lifecycle history marker

`SDD-001` and `AH5-04B-QUAL-001` have unresolved authoritative lifecycle history. Harness treats both as `recovery-required` through the existing `state_recovery_required` binding, even when the repository snapshot is otherwise consistent or the feature has no canonical state. The marker does not create an incident, attempt, lease, snapshot, or lifecycle record.

The host-local registry is stored outside `.agent-state`, under
`~/.local/state/agent-harness/unknown-state/`, in a file keyed by the SHA-256
of the repository's Git common-directory identity. The directory must be a
real owner-only `0700` directory and the registry a regular owner-owned `0600`
file. The versioned JSON record names the repository identity and sorted
feature IDs. Missing registry data does not clear the two built-in unresolved
markers; malformed, unreadable, or mismatched registry data fails closed.

The registry is not an agent-controlled recovery interface. Provider launch
profiles configure denial for the registry directory, and the runner does not
add it to Codex writable roots. Harness must independently verify enforcement
against both supported library pins before relying on this boundary. The
lifecycle status command remains available for diagnosis and reports
`recovery_reason=unresolved-state-marker` without inventing an incident.

Do not remove either feature from the registry or change the built-in marker
set as routine cleanup. Removal requires a separate, documented recovery
decision based on authoritative evidence. No such removal operation is
provided by the CLI.
