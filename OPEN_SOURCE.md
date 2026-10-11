# JasperCraft open-source notices

JasperCraft includes code that its authors share under open-source licences. This page lists it and where its source is.
Everything below is in this public repository.

## Mutant Creatures Legacy (AGPL-3.0)

The mutant mobs, their items and their animations are a port of **Mutant Creatures Legacy** (mod id `mutantbeasts`) by
ACGaming, IcarussOne and PessiMysterio (Elite Modding Team), a continuation of Mutant Creatures by shcott21/thehippomaster21
and the Mutant Beasts port by Chumbanotz, licensed under the GNU Affero General Public License v3. Our port is licensed the
same way. Its complete source, including every change we made:

- Server plugin (the mod's own code on a Forge compatibility layer):
  [`server/custom-plugins/JasprMutants`](server/custom-plugins/JasprMutants)
- Browser client part (models, animations, particles, items): [`client-mods/mutants`](client-mods/mutants), built by
  [`scripts/build-mutants-client.cjs`](scripts/build-mutants-client.cjs) and
  [`scripts/build-mutants-pack.cjs`](scripts/build-mutants-pack.cjs)
- Build tools for the server plugin and how it was ported: [`scripts/build-mutants-plugin.sh`](scripts/build-mutants-plugin.sh),
  [`MUTANTS_PORT_NOTES.md`](MUTANTS_PORT_NOTES.md), [`MUTANTS_PROTOCOL.md`](MUTANTS_PROTOCOL.md)
- Licence text: [`LICENSE`](server/custom-plugins/JasprMutants/LICENSE) and notice:
  [`NOTICE`](server/custom-plugins/JasprMutants/NOTICE) (also at <https://www.gnu.org/licenses/agpl-3.0.html>)
- Original mod: <https://github.com/Elite-Modding-Team/MutantCreaturesLegacy>

The newer Mutant Skeleton sounds of the original mod are All Rights Reserved, so JasperCraft does not use them: it plays the
mod's own older sounds (its legacy sound options), and vanilla sounds where no older sound exists.

## Mo' Bends (MIT)

Player and mob animations are a port of Mo' Bends by GoblinBob, under the MIT licence:
[`client-mods/mobends/THIRD_PARTY_NOTICES.txt`](client-mods/mobends/THIRD_PARTY_NOTICES.txt)

## Overloaded Armor Bar (MIT)

The armour bar above 20 points is a port of Overloaded Armor Bar, under the MIT licence:
[`client-mods/armor-bar/THIRD_PARTY_NOTICES.txt`](client-mods/armor-bar/THIRD_PARTY_NOTICES.txt)
