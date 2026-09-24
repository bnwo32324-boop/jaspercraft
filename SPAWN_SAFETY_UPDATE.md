# JasperCraft spawn safety update

The reproduced failure was a real server-side spawn error: after the `sparse-v5` regeneration, `world/level.dat` still recorded the Overworld spawn as `(0, 0, 0)`. The previous relocation listener loaded that coordinate and reused its stale Y value, so a first arrival could be admitted at Y=0 and fall into the void before authentication settled.

The server now derives a landing from the generated terrain and validates a solid, non-hazardous floor with clear feet and head space. It searches only a bounded 24-block radius, persists the repaired world-spawn height, and applies the result to both first-epoch relocation and any unsafe saved login position—even if that account was already marked as relocated.

Every joining player receives a 15-second arrival guard spanning the AuthMe/SSO handoff. Scheduled checks, movement interception, and a last-resort void-damage cancellation all converge on the same validated landing. Unsafe respawn locations are corrected as well. Diagnostics are emitted as `SPAWN_GUARD_READY`, `SPAWN_GUARD_SELECT`, `SPAWN_GUARD_RESCUE`, `SPAWN_GUARD_RESPAWN`, and `SPAWN_GUARD_VOID_PREVENTED`; aggregate counters appear in `/wasteland status`.
