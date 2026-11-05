# Qualification probe aliases (AH5-17 closure)

No new Harness release: the pin stays 0.30.0. `qualification/b_probes.py`, `q_lifecycle.py` and `q_probes.py` were byte-identical to the
library's modules, which own them since AH5-04c. They now forward to the library; `report.py` stays, for the reason in Harness
`docs/specs/AH5-17/closure-record.md`. The probes' behaviour tests moved to Harness; `tests/test_qualification_binding.py` is added.
Independent audit: PASS after one amended round. Hosted rehearsal 42082069127 with this change: success. No live qualification ran, and
no qualification report records these file bytes. Rollback reverts this commit.
