# WitchMod ("Jinx" / "Bewitchment"): CLAUDE.md

A comedic multiplayer **NeoForge 1.21.1** mod built around a curse/blessing economy. Players spend
**Cursed Essence** plus an exact **Sacrificial Item** (which picks the effect) at a **Bewitching Table**, and
target another player through their **Player Essence**. Effects are timed and hidden from the victim. Each one
unlocks a Compendium page when first discovered. Counterplay comes from Ward, Warding Totem, Jar, Ledger,
Purifying Water and Holy Water.

**The full design spec, checklists and progress live in [docs/DESIGN.md](docs/DESIGN.md) (~4k lines). It is
the single source of truth.** Read only the section you need, using the index below. Don't load the whole file.

## Status: finalize-stage cleanup
Every curse, blessing, item and block is built and signed off. The current job is the **class-by-class
code/comment cleanup** in DESIGN.md §16 (goal box) and §16.4 (checklist), done one class at a time and
batched by package. Tick each class off in §16.4 once it's cleaned. Pending art and audio are Oliver's
job (§12, §17). Neutrals, Globals, Aura and the whole `events/` subsystem were **cut**, so don't
reintroduce them.

## Build & run
- Windows project. Use `./gradlew` (Git Bash) or `gradlew.bat`. Gradle 9.2.1 wrapper, NeoGradle userdev 7.1.27,
  NeoForge 21.1.230, Parchment 2024.11.17. Java **21** toolchain. The foojay resolver downloads JDK 21
  automatically if only a newer JDK is installed (this machine has Temurin 25 only, and that's fine).
- `./gradlew build` compiles and produces `build/libs/witchmod-<version>.jar`
- `./gradlew runClient` / `runServer` / `runData` / `runGameTestServer`. Each run uses its own working directory
  under `run/<name>/`, which is gitignored. `runServer` needs `eula=true` in `run/server/eula.txt`.
- No unit tests (`src/test` doesn't exist). You verify in-game, usually through the debug-force command below.
- Datagen output goes to `src/generated/resources/`. Hand-written assets are in `src/main/resources/assets/witchmod/`.
- In IntelliJ, open the **folder that contains `build.gradle`** as the project. Opening any other folder gives
  "does not contain a Gradle build".

## Code map (`src/main/java/com/oliver/witchmod/`, ~365 classes)
| Package | What lives there |
|---|---|
| `WitchMod`, `WitchModClient` | mod entry points (`MODID = "witchmod"`), client-only setup |
| `Config` / `ClientConfig` | server/common config (every balance number) / per-player client config (accessibility sliders, opt-outs) |
| `data/` | core systems: `Effect` (base class), `EffectManager` (**the chokepoint**: apply/remove/cap/no-stack/opt-out/grace), `DiscoveryManager`, `ModifierCalculator`, registries, attachments, sounds, tags, text line pools |
| `effects/` | `Curses` / `Blessings` registries plus `curses/`, `blessings/` (one class per effect), `goals/` (mob AI), shared event handlers |
| `blocks/` | Bewitching Table (+ `BewitchingTableRitual` = cast logic), Ledger, Warding Totem, Purifying/Holy Water, Amethyst Bell |
| `items/`, `loot/`, `recipe/` | items (jars, coins, ward, voodoo, grenade…), named-jar loot, custom recipes |
| `entities/` | Bodyguard, Tax Man, Spaghetti Man (Haunted), etc. |
| `client/`, `ui/` | HUD layers, overlays, renderers, screens (Table, Compendium, Ledger) |
| `network/WitchModNetwork` | **all** payloads (s2c and c2s) and their handlers, in one file |
| `synergy/` | attachment-pair synergies (DESIGN.md §18.2) |
| `commands/` | `/bewitch …` command tree (DESIGN.md §15) |
| `mixin/` | `witchmod.mixins.json` |

Adding an effect means subclassing `data.Effect` (`onApply`/`onTick`/`onRemove`/`debugForce`, etc.), registering it in
`Curses`/`Blessings`, adding its config constants to `Config`, and adding a DESIGN.md §16.3 entry.

## Hard rules (details in DESIGN.md §2, §16)
1. **No stacking.** Reapplying an effect keeps the longer duration. Hard cap per player (`maxActiveEffectsPerPlayer`,
   default 3 curses + 3 blessings). Both are enforced **only** in `EffectManager.apply`, so never re-scatter them.
2. **Every numeric value is a named, config-exposed constant** in `Config`. No hardcoded balance numbers.
3. Sacrificial items match by **exact item**. The only tag exceptions are Hype Man (music discs) and Party Time (candles).
4. **Debug-force contract:** anything with a discrete observable moment must override `Effect.debugForce` and return
   a feedback string. Trigger it with `/bewitch debug force <effect> [targets] [arg]` (§16.2b).
5. **Side safety:** never touch client-only classes server-side. Don't spam packets per tick; use synced flags
   with client-side rendering instead (the Amethyst Bell / Guardian FX pattern).
6. **Comment style:** comments are lowercase and brief, and only explain non-obvious intent or workarounds.
   Class blurbs say what and why. **No references to CLAUDE/CLAUDE.md anywhere in `src/`.**
7. DESIGN.md §11 (collision/gap report): report issues there, never fix them on your own. Add human to-dos to §17.
8. Workflow: one item at a time, hands-on with Oliver. He tests in-game, then signs off. Don't batch-refine (§16.1).

## DESIGN.md index
§0 master lists · §1 overview · §2 core rules · §3 blocks · §4 items (jars, grenade) · §5 curses (49) ·
§6 blessings (45) · §9 modifiers · §10 costs/formulas · §11 collision report · §12 sound OGG list ·
§13 UI spec/art guides · §14 config & client config · §15 commands · §16 workflow + checklists (§16.3 per-effect,
§16.4 class cleanup) · §17 human action items · §18 named jars, synergies, companionship banter ·
§19 secret attachments (Pandora's Box, Cornucopia) + Shadow + Puppeteer.
Search with `grep -n "^### <Name>" docs/DESIGN.md` to jump to an effect's spec.

## Gotchas
- Ids differ from display names in places (e.g. Explosive=`martyrdom`, Super Explosive=`volatile`,
  The Dweller=`haunted` with entity `spaghetti_man`, Taxes=`audit`). See the top of DESIGN.md §16.3.
- The mod namespace is `witchmod`. Older notes that say `bewitchment` mean `witchmod`.
- **Never `getData` a SYNCED attachment in an event that can fire during entity construction** (e.g.
  `EntityEvent.Size`, fired from `Entity.<init>`): on a missing attachment `getData` creates + syncs it, and a
  `ServerPlayer` has no connection yet → NPE → every login fails with "Invalid player data". Check `hasData` first.
- **Secret attachments** (`Effect.special()`) must stay out of every random roll: anything that picks a random effect
  filters with `SpecialAttachments.inRandomPools`. Pandora's Box / Cornucopia's held effects are cap-exempt
  (`EffectRoulette.owns`) — keep that in mind when touching the cap or `activeCount`.
- **Changing a config default does NOT update existing config files.** Oliver's dev world reads
  `run/client/config/witchmod/*.toml`; after changing a default, update the value there too (or tell him), or his
  testing silently uses the old number. (This happened with the Shadow delay and Puppeteer cooldown.)
- Commit new files. `data/ClientOptOut.java` was once left out of a push, which broke the build on another PC.
  It was rebuilt from spec on 2026-09-28.
