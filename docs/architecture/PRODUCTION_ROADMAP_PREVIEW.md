# Production roadmap: first review

The new `docs/FASSWERK_PRODUCTION_READY_ROADMAP.md` is a separate plan with phases
0–23. Its numbering does not replace phases 1–15 of the existing refactoring plan.
This is a scope comparison, not the requested full production baseline or a claim
that the application is production ready.

## Existing evidence to reuse

- Refactoring phases 6–8 already provide server authorization, refresh replay
  protection, database locking, constraints, selected idempotent mutations and
  transactional rollback/concurrency tests. Production phases 1–4 and 10 must
  assess gaps per operation rather than replace those mechanisms wholesale.
- Phase 11 already covers non-root containers, read-only application filesystems,
  pinned bases, health ordering, shutdown and isolated runtime verification.
  Reuse this for production phase 12; deployment and restore evidence still need
  separate review.
- OpenAPI generation, backend PostgreSQL tests and critical mocked browser flows
  provide evidence for production phase 18. Mocked flows do not prove the complete
  browser-to-database lifecycle or payment recovery after a lost response.
- The existing backup/restore and monitoring runbooks are starting points for
  production phases 14, 15 and 21, not proof that restore has been exercised.

## Additional scope

Production phases 5–6 and 16 require explicit recovery and audit evidence.
Phases 7–9 introduce direct sales and separation of sales from occupancy; these
are domain/product work, not CI cleanup. Phase 20 adds data lifecycle review.
Performance work should follow measurements, as the roadmap itself requires.

## Sequence

Finish and assess the existing refactoring phases 12–15 first. Then perform the
new roadmap's phase 0 using current source and tests, including its required
workflow-by-workflow evidence and P0–P3 classification. Only that baseline should
claim which new phases are fully satisfied. No production roadmap implementation
or full phase-0 audit was started during the CI phase.
