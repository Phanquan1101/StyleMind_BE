# Disposable recovery runner attempts

This file preserves the diagnostic attempts that preceded the final passing run in `disposable-recovery-summary.md`. All runs used unique Compose project names and volumes. Each project and volume was removed by an exact Compose project-scoped cleanup; no persistent database was involved.

| Attempt | Project suffix | Result | Classification |
|---|---|---|---|
| 1 | `3d0e10a4` | Stopped at the disposable Compose/readiness stage before creating an account. The runner reported a `RuntimeException`; it did not retain a more specific intermediate cause. | Test runner/readiness observation incomplete; no product behavior inferred. The exact project and volume were removed. |
| 2 | `208170c1` | 45 checks passed through reset, password login, token replay and unaffected-account refresh. The final aggregate assertion failed. | Test fixture defect: it counted A's active sessions after the script had already logged A in with the new password, thereby including the newly created legitimate session. No product defect was established. The assertion was moved to immediately after reset commit. The exact project and volume were removed. |
| 3 | `c91f2dd9` | 52 restart/recovery checks passed. | Intermediate passing run before adding the final password/email boundary probes. Exact project and volume removed. |
| Final | `586ce25b` | 54 checks passed, including 255-character email observations and 72/73-byte password checks. | Final disposable recovery result; details in `disposable-recovery-summary.md`. Exact project and volume removed. |

The failed runner assertions did not alter application code, schema, migrations, or the persistent PostgreSQL volume.
