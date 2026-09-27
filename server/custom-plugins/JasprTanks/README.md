# JasprTanks

Mobile players get double health and a vehicle they swap at any time: a Tank (auto-step, horse speed, infinite TNT cannon, four riders) or a flying Orbital Sentinel (drone strikes, tap-to-grab items, tap-to-follow a friend). See `TANK_UPDATE.md` at the repository root for behaviour, safety rules and tests.

- `src/chat/jaspr/tanks/TanksPlugin.java`: enrolment, tank parts, cannon, riders and blast rules.
- `Nms.java`: client-only mount packets, step height, explosion owner and hidden-objective packets (Paper 1.12.2 internals).
- `Aim.java`, `Device.java`, `Settings.java`: pure math, browser classes and clamped config (unit-tested).
- `pack/`: generated hull, turret and iron-axe selector models (`node scripts/build-tank-assets.cjs`).
- Build: `scripts/build-tanks-plugin.ps1` writes `candidate/tanks/JasprTanks.jar`; the live copy is `server/plugins/JasprTanks.jar`.
