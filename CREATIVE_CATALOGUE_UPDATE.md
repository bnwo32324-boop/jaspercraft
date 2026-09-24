# JasperCraft Creative Catalogue

Deployed September 8, 2026. This release adds every currently defined JasperCraft custom gameplay item to Minecraft's native Creative inventory without resetting the world.

## Contents

- 87 custom entries: 35 guns, 24 melee weapons, 16 armor pieces, 4 materials, 3 consumables, 3 supplies, and 2 artifacts.
- Guns, melee weapons, and armor appear in Combat.
- Consumables appear in Food.
- Materials, supplies, and artifacts appear in Miscellaneous.
- Every entry is available through Search by custom name, stable item ID, category, and related keywords.
- The catalogue adapter already supports a Building Blocks destination for future custom blocks; no fake blocks were added because the server does not currently define any.

## Integrity and security

Browser entries are inert request templates. Taking one from Creative sends its stable ID to Paper, where `JasprApocalypse` validates that the player is authenticated and in Creative mode. Paper then replaces the template with a fresh canonical tier-5 item, including server-authored serials, magazine state, model data, and anti-forgery metadata. Survival, signed-out, and unknown requests are rejected and logged.

The generated client catalogue is derived from the server's actual Java item definitions, so additions cannot silently disappear from the menu without failing the catalogue tests.

## Verification

- Minecraft 1.12.2's real Mojangson parser accepted all 87 generated templates.
- Native tab/search placement, suspension/resume behavior, reversible byte patching, and cache pinning passed.
- Isolated Paper integration passed 2,386 assertions with zero failures, including all catalogue factories, serialization, issuance, authentication, game-mode guards, anti-forgery checks, firearms, melee equipment, armor, and lifecycle cleanup.
- Public `classes.js` matches SHA-256 `F1FF88D8F4D76C324954ADF248211A8A98AFE54A61DB96A7F9EF8404F12FEF17`.
- Live `JasprApocalypse.jar` matches SHA-256 `CFA27434AAC744D71884FCF17AF70FA12C8C0265191101836E10A2A1A55143DD`.
- Live `JasprHorrorBiomes.jar` matches SHA-256 `CC486A27B8E3CD24215BADE2F79038362B3AC4F984D1B6B0B1B9B4C60B917495`.
- All 252 protected world/player/plugin-data files were unchanged during the offline installation, and all 78 inventory/stat/advancement files remained unchanged after startup.

Live address: <https://jaspr.chat/jaspercraft/>
