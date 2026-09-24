# JasperCraft network diagnostic — 2026-09-08

## anon_0706 session evidence

- Jaspr identity: `anon_0706` (`f85d968a-d8e5-408d-bf9a-c4a3cc4deef2`).
- Main game connection: 2026-09-08 19:24:07Z–19:35:27Z (11m 20s).
- Browser WebSocket opened in 533 ms; the old Node gateway connected to its local Python relay in 12 ms, after which Paper authenticated the player normally.
- Traffic: 422,429 bytes client-to-server and 122,370,876 bytes server-to-client (about 1.44 Mbit/s sustained).
- Paper authenticated the player, observed uncancelled look and movement events, and continued normal gameplay. There was no `Can't keep up` event during the session.
- A second player path transferred 151,900,608 bytes in 13m 41s and later 61,113,565 bytes in 3m 51s, so the excessive traffic was systemic rather than account-specific.
- Cloudflared separately logged repeated QUIC/UDP failures on this host, including a Windows socket queue/buffer exhaustion event. No tunnel error was logged at anon_0706's exact join minute, so this was a confirmed architecture weakness but not proof of the reported 2,000 ms sample by itself.

## Diagnosis

The 2,000 ms report was not the geographic round-trip time between the player and Paper. The evidence is most consistent with application-path queueing: very large terrain/chunk bursts occupied the ordered WebSocket/TCP stream, delaying small Minecraft keepalive packets behind bulk world data. The prior architecture also relayed every frame through two local user-space proxies, while Cloudflare's connector used an unstable UDP/QUIC path on this Windows host.

Historical logging did not record server-authoritative ping samples or socket queue depth, so a single reported 2,000 ms value cannot be reconstructed exactly. The new telemetry closes that gap.

## Deployed corrections

- The live game data path is now Cloudflare → Jaspr gateway → Paper. The Python service remains the lifecycle/status control plane and legacy relay, but no longer handles normal Jaspr.chat game bytes.
- Cloudflared is pinned to IPv4 HTTP/2, avoiding the host's failing UDP/QUIC path.
- Paper chunk sends were reduced from 81 to 4 per tick, chunk generation from 10 to 2 per tick, and autosave writes from 24 to 8 chunks per tick.
- Paper explosion optimization is enabled for the server's TNT-zombie workload.
- EaglerXServer actively emits WebSocket keepalives during otherwise idle periods.
- Jaspr logs 15-second per-connection throughput, edge country/colo, backpressure, queue depth, pauses and drain events.
- Paper records five-second per-player ping plus mean/p95/max tick duration in `plugins/TestServerControl/network-diagnostics.jsonl`, with session summaries, a 150 ms high-ping alert, and a 100 ms recovery threshold.
- A privileged player can run `/jasprnet [player]` for a live ping/tick snapshot.
- Run `scripts/report-network-diagnostics.ps1 -Player anon_0706 -Hours 24` for a correlated summary.
