# Bewitchment (placeholder name) — CLAUDE.MD (Refinement Stage)

STATUS: All curses/blessings/items/blocks are implemented and functional. **The task is now REFINEMENT** —
bringing each one from "functional prototype" up to its full, polished spec, one at a time, hands-on with
Oliver (though a new curse/blessing may still be added if a good idea comes up). The workflow + tick-off
checklists live in **§16**; **§5/§6** hold the design spec (behaviour + tunable config constants) for the
classic curses/blessings, and the statement curses + newer blessings are documented in their §16.3 entries.
This file is the single source of truth; where older notes conflict, this file wins.

NEUTRALS, GLOBALS, and Aura were **CUT** — the whole event subsystem (`events/` package, the Event registry,
`GlobalCharge`, the Afflicted status, `/bewitch event`) is deleted; a failed ritual now just fizzles.
`CurseMansplainer` was also cut. §7/§8/§10.4/§10.6 are stubbed accordingly.

*(This doc was deep-cleaned 2026-09-02: the Phase A–F build logs and the §16.3 dated iteration histories were
removed as post-build history; the checklists + specs remain. It went 443KB → ~208KB.)*

---

## 0. MASTER LISTS — read before touching code

### 0.1 NOT PROTOTYPED (⛔ NO CLASS EXISTS YET — these must be built from scratch)
Everything NOT on this list already has a working class from the prototype.

**Curses (8):**
Super Explosive, Claustrophobia, Moonwalker, Siren's Call, Loading Screen, Pacing, Trumpet,
Heavy Handed (restored, renamed from Uncareful — see its entry).

**Blessings (15):**
Thick Skinned, Farmer's Spirit, Brute, Blacksmith, Unseen, Silver Tongue, Hot Stuff, Bouncy,
Excavation, Angler, Laugh Track, Chat, Civilisation, Low Gravity — plus Last Stand's
blessing-consumption behavior if the prototype lacks it.

### 0.2 CUSTOM SOUND attachments (need modded SoundEvents; OGG list in Section 12)
**Curses (5):** Gassy ✅ done, Slippery Feet ✅ done, Loading Screen ✅ done, Pacing ✅ (theme mp3→ogg pending),
Trumpet ✅ (⚠ supplied ogg is STEREO — needs a mono re-export for positional audio). (Unhygienic ✅ done)
(Delusions was REMOVED from this list on Oliver's call — vanilla sounds suffice for it, see its entry.)
**Blessings (5):** Main Character, Last Stand, Brute, Bouncy, Laugh Track.
**System (2):** discovery chime, global warning sting.

### 0.3 CUSTOM UI attachments (need HUD/overlay work beyond the 3 main screens)
Gluttony (second hunger bar), Thirst Meter (droplet bar — FULLY FUNCTIONAL required),
Loading Screen (fullscreen fake-load overlay — FULLY FUNCTIONAL required),
Organised (extra inventory row), Chat (twitch overlay).

### 0.4 CUSTOM ENTITIES
Bodyguard (armoured sunglasses-skeleton, 3-state AI, dialogue),
Tax Man (playermodel entity, custom skin/nametag, loot + delivery AI),
Delusion fake players (client-side, real-skin mirroring, state machine).

---

## 1. What This Mod Is

Bewitchment is a comedic multiplayer Minecraft mod (Fabric/NeoForge 1.21.1) built around a
curse/blessing economy. Players use a **Bewitching Table** to spend **Cursed Essence** and a
**Sacrificial Item** (which selects the specific effect) on a target identified by **Player
Essence**. Effects are timed, invisible in detail to the victim, and discoverable — each unlocks
a **Compendium** page on first occurrence. **Neutrals** are small flavour events (also the landing
pad for ritual failures), **Globals** are rare server-wide events funded from a shared bank, and
**Backfires** punish failed rituals. Counterplay: **Ward** (personal reflect), **Warding Totem**
(area block), **Jar** (capture), **Ledger** (public who-cursed-whom log), **Purifying Water**
(timer burn-down).

Foundational vanilla change: **budding amethyst is collectable via Silk Touch.**

---

## 2. Core Rules (Main Foundations)

1. **NO STACKING.** The same attachment can never be active twice on one player. Reapplying an
   active attachment keeps whichever duration is LONGER (`ATTACHMENT_REAPPLY_POLICY = KEEP_LONGER`).
   Never additive, never refresh-to-shorter.
2. Discovery: an attachment is **discovered** the first time its effect occurs. Victim discovers on
   trigger; caster discovers the instant they successfully send it. On discovery the player is
   **alerted in chat** and the Compendium updates from rumour → discovered instantly.
3. **ABSOLUTE HARD CAP: at most `Limit` curses AND `Limit` blessings** per player at once — the configurable
   `maxActiveEffectsPerPlayer` (default 3). Enforced centrally in `EffectManager.apply`, so EVERY path — Table,
   jar splash, coins, effigy, commands, the Guardian's gift — refuses a NEW effect over the cap (reapplying an
   already-active one is fine). The SAME config also doubles as the backfire-RISK threshold at the Table.
   **Infectious / Very Infectious are EXEMPT** (a modifier internally classed as a curse — never counts, never
   blocked; its `applyExact` spread bypasses the cap too). Casting on an already-affected target still costs more.
4. Durations: random **30–60 min** unless an attachment states an override. Effects are curable/
   forwardable but **do NOT expire on death** (exception: Recovery Compass modifier).
5. Globals/Neutrals have **no item-based counterplay** by design — counter them through play.
6. Victims cannot see WHICH attachments they have, but carry a modded status effect
   (**Cursed** / **Blessed** / **Afflicted** for globals) showing particles ONLY on initial onset
   and expiry — never in between.
7. All numeric values are **named balancing constants** in per-attachment config
   (`config/attachments/<name>.json` or per-attachment record) — tunable without logic changes.
8. `(CUSTOM SOUND)` = at least one modded SoundEvent required. Register all SoundEvents at init
   with placeholder audio; OGGs supplied later (master list, Section 12).
9. Sacrificial item matching is **EXACT-ITEM**, never tag-based, except two explicit exceptions:
   Hype Man (`minecraft:music_discs` tag) and Party Time ("any candle" tag). (Main Character was formerly
   a third, type-matching "any Firework Star"; it moved to Fire Charge — a plain exact-item — when the
   Section 11 collision was resolved, so that exception is retired.)

---

## 3. Blocks

### Bewitching Table / Altar — Recipe: Diamond, Cursed Essence, Wood
Slots: **Player Essence** (target; empty = self), **Sacrificial Item** (selects effect),
**Cursed Essence** (currency/success), **Modifier** (optional).
Failures → Neutral event, curse onto caster (Mirror backfire), or (rare, high-risk) the table
exploding.
**Special: Redstone Dust in the Sacrificial slot** = random attachment applied to the target,
weighted toward lower-cost attachments (`REDSTONE_RANDOM_LOW_BIAS = 1.5` inverse-cost weighting).

### Block of Cursed Essence — Recipe: 9 Cursed Essence
Storage block; currency unit for the server-wide **Global bank**.

### Ledger — Recipe: Wood, Compendium
Lectern-style, read-only. Logs all nearby hex attempts sent/received, including blocked ones and
whether they landed.

### Warding Totem
Radius shield: players inside repel all curses/blessings but receive no feedback on blocks.

### Purifying Water — Recipe: Water Bucket, Amethyst, Diamond, Cursed Essence (+more TBD)
Unique fluid; bathing rapidly burns down attachment timers
(`PURIFY_TIMER_BURN_MULT = 20.0` — 1 real second removes 20 seconds of effect time).

---

## 4. Items

| Item | Function | Recipe / Source |
|---|---|---|
| Cursed Essence | Currency | Smelt cursed items / Amethyst |
| Player Essence | Targeting | Bottle on a player or their current bed |
| Compendium | Discovery/rumour/tutorial/item-guide book UI, stored per world | Book + Cursed Essence |
| Voodoo Doll | Bound to a named player; forwards curses cast while it's in inventory; fails if target dead/offline; has durability; still Ledger-logged | Wool, Wood, Player Essence |
| Needle | Right-click on a Doll in inventory → direct damage to bound target; consumes Doll durability | Iron Ingot, Iron Nugget |
| Ward | Durability item (8) that BLOCKS any attachment cast by someone ELSE (self-casts pass); a coloured incoming-lash gets blocked with a shield clang, spending 1 durability. (Redefined from the old "deflect back to sender".) | Emerald, Cursed Essence, String |
| Scrying Mirror | Reveals your own active attachments | Glass, Cursed Essence, Diamond |
| Effigy | Forwards your curses to another player on click | Totem of Undying, Cursed Essence |
| Cursed Coin | Gambles a random curse OR blessing | Gold, Cursed Essence |
| Blessed Coin | Random blessing | Loot tables only |
| Executioner's Coin | Revives, inflicts random curse | Loot tables only |
| Jar | Empty: bottles **Player Essence** (crouch+look-down=own · right-click player=theirs · right-click bed=spawn-owner, offline-aware). Filled: a throwable splash — see §4.1 | Netherite Ingot, Cursed Essence, Glass |
| Cursed Jar | A Jar holding CURSES only (up to 3) — throwable splash (§4.1) | dynamic variant of Jar |
| Blessed Jar | A Jar holding BLESSINGS only (up to 3) — throwable splash (§4.1) | dynamic variant of Jar |
| Mixed Jar | A Jar holding ANY mix of curses/blessings (up to 3) — throwable splash (§4.1) | dynamic variant of Jar |
| Amethyst Bell | AOE gambling block: ring to reshape fate of all nearby players (gift a batch to the empty, re-roll everyone else's by power); greys out inactive for 30 min after each ring | — |

### 4.1 Jars — dynamic throwable splash (✅ IMPLEMENTED)
A jar is ONE dynamic item (`ItemJar`, shared logic in `JarContents`). The variant — **Cursed / Blessed / Mixed
/ empty Jar** — is DERIVED from its contents (`JarContents.itemFor`), so adding a blessing to a Cursed Jar
turns the stack into a Mixed Jar; full at `JarContents.MAX` (3). Stored as the `CAPTURED_EFFECTS` data
component (list of `CapturedEffect{effectId, remainingTicks}`); the tooltip lists the stored attachment names.
- **Filling — at the Bewitching Table.** A jar in the **target slot** (instead of a Player Essence) switches
  the ritual into FILL mode (`BewitchingTableRitual`): on success the rolled attachment is bottled into the
  jar (returned the correct, possibly-changed variant); a full jar is refused before ingredients are spent; a
  failed roll returns the jar UNCHANGED (only the essence + sacrificial item are the cost — the jar is never
  destroyed). This REPLACED capturing effects off a player (disliked — too-easy curse removal); jars are never
  captured off a person now.
- **Throwing** (`ItemJar.use` → `JarThrowEntity`, a bespoke `ThrowableItemProjectile`). On ANY impact
  `JarEffects.splash` applies ALL stored effects to every player in an over-sized radius (`jarSplashRadius`),
  with splash particles by kind (cursed = purple WITCH stars · blessed = warm END_ROD + yellow/white · mixed =
  all). **If it catches nobody → a LASH:** a homing particle trail surges to the nearest player within
  `lashRange` at `lashSpeed`, applying the effects on contact, expiring after `lashExpiryTicks` (run far enough
  and you're safe) — so you can't just chuck it away. **Destroyed in lava / fire / cactus → the same
  splash + lash** (a periodic `ServerTickEvent` hazard scan), so there's no clean way to bin one.
- **Commands** (op-gated): `/bewitch jar make <effect1> [effect2] [effect3]` builds a jar from ids;
  `/bewitch jar copy <player>` snapshots an online player's active attachments (with remaining durations).
  Player Essence has its own generator (essence only needs a UUID + name, so it works OFFLINE):
  `/bewitch essence player <player>` and `/bewitch essence uuid <uuid>` (name resolved from the server profile
  cache, or the UUID itself if never seen).
- Config: `jarSplashRadius=5.0  lashRange=24.0  lashSpeed=0.9  lashExpiryTicks=200`.

---

## 5. CURSES (49)

Format: Name — Sacrificial Item, then behavior, then constants.
All previously-specced constants carry over; entries below reflect the CURRENT item mappings and
any spec changes. `(NOT PROTOTYPED)` = still to build.

### Violence — Iron Sword
Your hand goes for whoever is closest, on its own. The camera really snaps onto them (`ServerPlayer.lookAt`
sends the look-at packet), the arm swings, and the hit is **vanilla's own `Player.attack`** — so enchantments,
knockback, crits, sweeping and sounds are all genuinely applied with the held item, not imitated.
**TWO distinct urges, deliberately behaving differently:**
- **Sight swings — INSTANT PRIORITY, never ramp.** Whatever you look straight at (within `SIGHT_CONE_DEGREES`
  AND in line of sight, so walls don't set it off) is in serious danger, and far more so if a shove would
  drop it — `SIGHT_CHANCE` vs `SIGHT_HAZARD_CHANCE`. **No camera hijack**, since you were already looking.
  Checked before impulsive urges every time.
- **Impulsive swings — ramping, camera IS hijacked** onto something you weren't looking at. Possible at any
  moment from `IMPULSIVE_BASE_CHANCE`, growing `IMPULSIVE_RAMP_PER_SECOND` per second since the last swing,
  clamped to `IMPULSIVE_CAP`. Anything that **loiters** at a ledge/hazard for `HAZARD_SUSTAIN_TICKS` (1.5s)
  overrides that ramp outright with `IMPULSIVE_HAZARD_CHANCE`. Loitering is tracked per-entity and is
  deliberately independent of where the cursed player stands.

**Target priority is TIERED, not merely weighted** — multiplied weights cannot deliver an "always"
guarantee, since a big enough product elsewhere eventually overtakes it. Each trait is worth strictly more
than everything beneath it combined, so the ordering is exact and stays exact:
| Weight | Trait |
|---|---|
| **+4** | **environmental kill — THE dominant term.** The shove puts them off a ledge, into lava/fire/magma/cactus/powder snow/berry bush/wither rose/dripstone, or beside lit TNT. ANY hazard shove outranks any non-hazard target, however wounded. |
| **+2** | wounded (`<= LOW_HEALTH_PERCENT` health) — below hazards, but above merely being a player, so a wounded mob outranks a healthy player standing safely |
| **+1** | player — the tie-breaker between otherwise equal targets |
Giving 7 (wounded player at a ledge) down to 0 (ordinary mob standing safely). **To re-rank, change those
three weights in `priorityTier` — nothing else needs touching.** Within a tier it's nearest-first, nudged by
`PLAYER_PRIORITY_MULT` and `HAZARD_BIAS_MULT`.
**Lit TNT is an ENTITY (`PrimedTnt`), not a block**, so it needs its own entity lookup — a block scan alone
silently misses it. Counted within `TNT_DANGER_RADIUS` of the shove line or of a loitering entity.
**`violenceRandomTargetChancePercent`** of impulsive urges discard the whole order and swing at someone at
random, so the curse never becomes perfectly predictable.
- **Multi-hit:** a small chance that one urge becomes a flurry of extra swings; if the target leaves
  mid-flurry it flails at the air.
- **It DOES consume your attack cooldown** (Oliver's call, overriding the earlier "hidden perk" idea): a
  stolen swing costs you the cooldown exactly like a real one, so it takes your next hit's damage with it
  rather than being a free extra attack. `Player.attack` resets that counter itself, so this needs no help —
  the refund exists only behind `violenceRefundsAttackCooldown=false`. Swings are still forced to full
  strength so they don't land as limp mid-cooldown taps; that half writes `attackStrengthTicker` (protected
  in LivingEntity) **by reflection**, since the project has no access transformer set up, and degrades
  gracefully if the field can't be resolved.
- Discovered on trigger (the first time your arm swings without you).
```
violenceCheckIntervalTicks=20   violenceTargetRange=3.5
violenceSightConeDegrees=20     violenceSightChancePercent=25   violenceSightHazardChancePercent=75
violenceImpulsiveBaseChancePercent=2  violenceImpulsiveRampPerSecond=0.5  violenceImpulsiveCapPercent=30
violenceImpulsiveHazardChancePercent=90  violenceHazardSustainTicks=30  violenceLowHealthPercent=25
violenceRandomTargetChancePercent=10
violencePlayerPriorityMultiplier=3.0  violenceHazardBiasMultiplier=4.0  violenceHazardShoveDistance=4
violenceLedgeDropMin=3  violenceMultiHitChancePercent=12  violenceMultiHitMin=2  violenceMultiHitMax=4
violenceMultiHitSpacingTicks=6  violenceFullStrengthSwings=true  violenceRefundsAttackCooldown=false
```

### Butterfingers — Milk Bucket  ✅ REFINED
Whatever's in your hands has a habit of just... slipping. Three triggers, all sharing **ONE** internal
cooldown so a run of hits can't strip you bare:
- **Passive** — rare, out of nowhere (`PASSIVE_CHANCE` per `PASSIVE_INTERVAL`).
- **On taking damage** — much likelier; a hit knocks it out of your hands (`LivingIncomingDamageEvent`).
- **On swinging a tool** — mining (`LeftClickBlock`) or attacking (`AttackEntityEvent`), the classic "threw
  my pickaxe into the lava". "Tool" = any held item with durability, so modded tools qualify automatically.

The shared cooldown IS the balance: the passive roll is deliberately rare, so in practice the curse gets you
mid-fight or mid-swing, when losing your gear hurts most. The cooldown is only consumed on an ACTUAL drop —
rolling a fumble with nothing to lose doesn't buy you grace. Fumble order is main hand → offhand → a random
hotbar slot (`DROP_FROM_HOTBAR`); `DROPS_WHOLE_STACK` throws the lot rather than one item.
Discovered on trigger (the first time something leaps out of your hands).
```
butterfingersCooldownTicks=600  butterfingersPassiveIntervalTicks=100  butterfingersPassiveChancePercent=3
butterfingersOnDamageChancePercent=35  butterfingersOnSwingChancePercent=15
butterfingersDropFromHotbar=true  butterfingersDropsWholeStack=true
```

### Explosive — Gunpowder  ✅ REFINED
You go off when you go down. Dying triggers a real `Level.explode` — full damage, knockback and block
damage, exactly what a creeper would hand out — **and your dropped inventory is destroyed in the blast**,
which is what makes dying with this genuinely expensive rather than merely loud. World damage is gated on
`mobGriefing` via `Level.ExplosionInteraction.MOB` (vanilla's own switch for that). The dying player stays
the explosion's source, so kills are attributed to them rather than to thin air.

**The blast is deferred by `explosiveDelayTicks` (min 1), and that is load-bearing, not cosmetic.**
Verified in bytecode: `LivingEntity.die` calls `onLivingDeath` (which fires `LivingDeathEvent`) at offset 2
but `dropAllDeathLoot` only at offset 164 — so an explosion fired straight from the death event goes off
while the items **do not exist yet**, leaving the whole inventory sitting neatly on the floor. One tick
later the drops are real entities and the blast destroys them like any other ground item.
Discovered on death.
```
explosivePower=4.0  explosiveCreatesFire=false  explosiveDelayTicks=1
```

### Super Explosive — TNT  ✅ REFINED
Every hit you take is a small, **flat** chance of simply going off — a real explosion with full damage, big
knockback and block damage gated on `mobGriefing` via `Level.ExplosionInteraction.MOB`. The chance never
ramps: it's a standing risk, not a building one. **No cooldown** (Oliver's call — "a consistent 5% chance");
the only guard is that explosion damage can't re-trigger it, or your own blast would chain off itself.

**You take only `SELF_DAMAGE_PERCENT` of your own blast.** You stand at dead centre, so at full damage it
would execute you every single time; 15% leaves it a serious hit rather than a death sentence, while
everyone else nearby takes it in full.

Both that and the knockback go through vanilla's own `ExplosionDamageCalculator` extension points —
`getEntityDamageAmount` and `getKnockbackMultiplier`, subclassing `EntityBasedExplosionDamageCalculator` so
player-caused block resistance stays vanilla. That's cleaner than intercepting the damage event afterwards:
the explosion remains an ordinary explosion in every other respect. Discovered on the first detonation.

**⚠ The explosion's source ENTITY must be `null`, never the player.** `Explosion` collects its victims via
`Level.getEntities(source, box)`, whose first argument is the entity to **EXCLUDE** — so naming the player as
the source silently drops them from their own blast and they take *no* damage and *no* knockback (this was a
real bug, caught in play: "the explosions do no damage instead of little"). The player rides on the damage
source (`damageSources().explosion(null, player)`, keeping kill attribution) and in the calculator (block
resistance + the self-damage scale) instead. Note also that vanilla's 10-tick invulnerability window still
applies, so the blast damage merges with the hit that set it off rather than stacking on top of it.
```
superExplosiveChancePercent=5  superExplosivePower=2.5
superExplosiveSelfDamagePercent=15  superExplosiveKnockbackMultiplier=2.5
```

### Popularity — Bell  ✅ REFINED
The "whole server chasing one guy" TikTok, made real. Two halves, both on a tick:
- **Conjured horde, biome- and environment-aware.** Extra hostiles are spawned in a ring
  (`SPAWN_RADIUS_MIN`..`MAX`) around the victim every `SPAWN_INTERVAL` (`SPAWN_ATTEMPTS` tries each), up to a
  live cap `MAX_MOBS`. A column scan near the victim's height finds either standing WATER → **Drowned**
  (so a submerged victim reliably gets water mobs regardless of the pond's biome — the earlier pure
  biome-list approach spawned none in a land-biome pond), or a standable ground spot → a weighted pick from
  the **local biome's `MobCategory.MONSTER` list** (husks in desert, strays in snow, ...). Placement uses the
  type's own `SpawnPlacements.getPlacementType(...).isSpawnPositionOk` for footing; the safety gate is a
  **light check** (`MAX_SPAWN_LIGHT`, vanilla's dark-enough threshold), so daylight and torches keep you
  clear. **NeutralMobs (endermen, zombified piglins) are never conjured.** Tagged `witchmod_popularity`, not
  persistence-locked → the crowd despawns after the curse ends.
- **Dedicated hunters.** Every hostile within `DETECTION_RADIUS` is set up ONCE (tag-guarded) as a committed
  hunter: FOLLOW_RANGE bumped to the detection radius, a high-priority `NearestAttackableTargetGoal<Player>`
  with `PROLONGED_TRACKING_TICKS` unseen-memory (mustSee=true, so it must SEE you to lock on but then hunts
  ~15s through walls after losing LOS — dogged, not x-ray), and — for zombies (incl. husk/drowned/zombie
  villager) with GROUND navigation — a custom `BreakDoorGoal` with an always-true predicate so they smash
  doors on ANY difficulty. (Drowned use water navigation, so `BreakDoorGoal`'s ctor rejects them — guarded
  with an `instanceof GroundPathNavigation` check after a crash proved it throws otherwise.)
  When a hunter currently has LOS it's re-aimed at you, so you stay the priority.
  **NeutralMobs are excluded** from enlistment too — force-aggroing endermen/zombified piglins is Neutral
  Aggression's job, and doing it here would make that curse pointless.
- **No instant-explosion creepers** — creepers aren't conjured specially and the horde is biome-driven now.
Discovered once a `DISCOVERY_HORDE_SIZE`+ crowd has amassed (not on the first spawn).
```
popularitySpawnIntervalTicks=45  popularitySpawnAttempts=2  popularityMaxMobs=34
popularityDiscoveryHordeSize=15  popularityProlongedTrackingTicks=300
popularitySpawnRadiusMin=6  popularitySpawnRadiusMax=20  popularityMaxSpawnLight=7
popularityDetectionRadius=48  popularityRetargetIntervalTicks=20
```

### Yap — Paper  ✅ REFINED
You can't stop talking. Every `INTERVAL_MIN`..`MAX` you blurt a line into **server chat** as if you'd typed
it (`chat.type.text` broadcast, not a nearby-only whisper like the prototype). Outbursts come from THREE
SEPARATE writable lists so multi-message rambles are **scripted, not randomly paired**:
- `singles` — one message.
- `doubles` — a 2-message combo, both lines sent in order, `MESSAGE_GAP` ticks apart.
- `triples` — a 3-message combo, in order.
The list is weighted (`SINGLE_WEIGHT`/`DOUBLE_WEIGHT`/`TRIPLE_WEIGHT`) so more messages = rarer.
**Event reactions** (separate `events` object) fire a much rarer line on a specific action, each on its own
long `EVENT_COOLDOWN`: `hurt` (taking damage), `attack` (dealing damage), `chest` (opening a chest/trapped/
ender via `AbstractChestBlock`), `death` (dying), `proximity` (a player within `PROXIMITY_RADIUS`). Each
event entry can be a string (single) or an array (a sequence). Loaded from `data/witchmod/text/yap.json` via
a `SimpleJsonResourceReloadListener` (reload-able with `/reload`), malformed entries skipped not fatal.
**Self-heal:** the ambient schedule lives in a transient map, so a curse that PERSISTED across a relog/world
reload (onApply never re-ran) would go silent forever — `onTick` re-schedules if it finds no record. Fixed
after "3 minutes, said nothing". Discovered on first outburst.
```
yapIntervalMinTicks=200  yapIntervalMaxTicks=600
yapSingleWeight=70  yapDoubleWeight=25  yapTripleWeight=5  yapMessageGapTicks=30
yapEventCooldownTicks=2400  yapProximityRadius=6.0
List: data/witchmod/text/yap.json  { "singles":[..], "doubles":[[a,b]..], "triples":[[a,b,c]..],
  "events": { "hurt":[..], "attack":[..], "chest":[..], "death":[..], "proximity":[..] } }
```

### Unhygienic — Rotten Flesh (CUSTOM SOUND)  ✅ REFINED
**RENAMED from "Green Aura" on Oliver's call.** Note the registry id was changed too (`green_aura` →
`unhygienic`), unlike the Phase-A renames which kept their ids: display names are derived from the id path
(`DiscoveryManager.titleCase(id.getPath())`), so the id *is* the visible name here. Any saved `green_aura`
instance is dropped as unknown — harmless, it was only ever a placeholder.

You reek. A green stink cloud leaks off you with a few flies orbiting your head, and:
- **Non-undead mobs flee** — anything within `MOB_FLEE_RADIUS` gets an `UnhygienicFleeGoal` (priority 2, so
  panic/float still outrank it) and paths away, re-picking its escape route as you follow. Get within
  `PANIC_RADIUS` and it switches to `PANIC_SPEED` — a proper run rather than an unhurried walk. **Undead are
  pointedly unbothered** (`EntityTypeTags.UNDEAD`) — they smell worse.
- **Nearby players drift away** — anyone inside `PLAYER_DRIFT_RADIUS` gets a small repeated push
  (`PLAYER_DRIFT_FORCE`) every 5 ticks, deliberately subtle enough to walk against. Needs `hurtMarked` or the
  velocity never reaches the client.
- **Particles**: a green `DustParticleOptions` haze for the gas — `ENTITY_EFFECT` was the obvious pick but
  renders as potion BUBBLES, which read as "brewing" rather than "stink". Flies are `MYCELIUM` specks, and
  the trick there is the MOTION, not the sprite: a clean circular orbit just looks like a ring of dust, so
  each fly follows a wobbling radius, uneven angular speed and its own bobbing height (sines at differing
  frequencies). Specks are spawned **stationary and only every `FLY_INTERVAL` ticks** — giving them a darting
  velocity every tick meant ~80 moving particles a second, which read as flung dust and was pure waste;
  dropped along a continuous path instead, consecutive specks land close enough that the eye interpolates
  them into one moving fly, at a fraction of the cost.
- **Sound**: `curse.unhygienic.flies` buzzes on a randomised `FLY_SOUND_MIN..MAX` gap — three OGG variants in
  one event, so vanilla picks between them.
The flee goal is bound to a specific player and checked with `isTargeting`, so a respawn/relog swap doesn't
leave mobs permanently unbothered (the Farmhand lesson).
```
unhygienicMobFleeRadius=8.0  unhygienicFleeSpeed=1.35  unhygienicPanicRadius=3.5  unhygienicPanicSpeed=2.0
unhygienicPlayerDriftRadius=3.0  unhygienicPlayerDriftForce=0.04  unhygienicParticleInterval=10
unhygienicFlyInterval=3  unhygienicFlyVolume=0.56
unhygienicFlySoundMinTicks=120  unhygienicFlySoundMaxTicks=400
Sounds: witchmod:curse.unhygienic.flies (fly1/fly2/fly3) ✅ SUPPLIED
```

### Repel — Water Bucket  ✅ REFINED
Dropped items and XP orbs **on the floor** within `RADIUS` slide slowly AWAY from you at `SPEED` — kept
below sprint speed so you can always chase your stuff down. They steer toward the nastiest reachable thing,
but ONLY in a direction that still points away from you (never back toward you to reach a ledge). Steering
priority, highest first: **lit TNT > lava > cacti > ledges > other players > (just away)** — each candidate
is taken only if its direction from the item is in the away half-space (`dot(dir, away) > 0`). Ledges are a
neighbouring column with `LEDGE_DROP_MIN` clear blocks down; TNT/players are entity scans, lava/cactus a
coarse block-grid scan. Velocity is SET each tick (not nudged) so the slide is steady and predictable, with
a per-tick `MAX_ENTITIES` cap. Discovered the moment anything slides.
**Was on the wrong item** (Slime Block) and repelled living entities — corrected to its spec item Water
Bucket (unused) and rebuilt around items/orbs. NOTE: within ~8 blocks vanilla's XP-magnet pulls orbs toward
you and partly fights the repel; items are unaffected and slide cleanly.
```
repelRadius=6.0  repelSpeed=0.07  repelHazardScanRadius=5.0  repelLedgeDropMin=2  repelMaxEntities=64
```

### Echoes — Echo Shard  ✅ REFINED
You keep hearing things that aren't there. Every `INTERVAL_MIN`..`MAX` the victim — and ONLY the victim —
hears an utterly ordinary game sound at a believable spot in the world.

**Believability is the entire design**, so two rules drive the implementation:
1. **Client-only but still positional.** Each sound is a `ClientboundSoundPacket` sent down that one player's
   connection (NOT `level.playSound`, which everyone nearby would hear). It's directional and attenuates
   exactly like a real sound, and nobody else can confirm it wasn't.
2. **Placement is checked against the world.** Footsteps/landings are snapped DOWN onto real ground
   (`groundedAt`, which returns null and picks another hallucination if there's nothing solid underfoot) and
   use that block's OWN step sound. Mining comes from inside a solid block several blocks below you and uses
   that block's hit/break sounds. Distant explosions are genuinely 24–48 blocks out.

Multi-part sequences are queued over several ticks, not fired at once: approaching sprint footsteps close
the distance step by step, mining is a run of hits at a randomised cadence ("various speeds") then the break.

Pool — a **weighted table** (`CurseEchoes.POOL`, one `Weighted(weight, method)` row each, so re-tuning or
adding a hallucination is a one-line change), weighted so background noises are common and the ones that make
you spin round stay rare: calm footsteps from a random bearing · sprinting footsteps closing in · mining
below · zombie/skeleton/spider ambient · chest or door opening (with a matching close a moment later) ·
distant explosion · someone landing (plus a step after, to sell it as a person) · fake chat · villager · TNT
primed · cave ambience · **someone swimming** · **someone eating, finished with a burp** · **an animal being
hurt nearby** (a swing lands, THEN it cries out — the order sells it; sheep/cow/pig/chicken) · **someone
whiffing at air** (the flat no-damage swing) · **someone fighting a zombie** (ambient, then swings alternating
with zombie grunts, sometimes ending in a death) · creeper priming then exploding behind · misc one-offs (bow,
enderman teleport, XP/item pickup, anvil, splash) · **a fake notification ping** (rarest).

**The ping** is a fake message-app chime, played at the VICTIM'S OWN position so it sits dead centre like a
real one out of their headphones rather than off in the world. It is deliberately a **single OGG with no
variants** (Oliver's call) — a real notification is identical every time, so randomising it would give the
game away instantly.

**Swimming is only ever placed in real water** (`findWaterNear` scans for a `FluidTags.WATER` block within 14
blocks and returns null otherwise) for the same reason footsteps are snapped to ground — a swimming sound on
dry land is an immediate tell.

**Fake chat** puts a line from `data/witchmod/text/echoes_chat.json` in the mouth of a player who is genuinely
online (never the victim), shown to the victim alone; skipped if nobody else is on. Any hallucination whose
conditions aren't met falls back to another rather than wasting the slot.
**Discovery is delayed** `DISCOVERY_DELAY_TICKS` after the FIRST hallucination, so the penny drops a moment
later instead of the alert giving the sound away.
```
echoesIntervalMinTicks=160  echoesIntervalMaxTicks=1300   // 8s .. 65s — the wide spread is the point, since
                                                          // a predictable rhythm identifies the fakes for you
echoesDiscoveryDelayTicks=40  echoesVolume=1.0
List: data/witchmod/text/echoes_chat.json  (plain JSON array of strings, /reload-able)
Sound: witchmod:curse.echoes.ping  ✅ SUPPLIED (single file, no variants by design)
```

### Delusions — Ender Pearl  ✅ REFINED
Fake players wearing the skins + nametags of people really online wander at the edge of your vision doing
ordinary player business — until one notices you looking. They are `RemotePlayer`s inserted into the VICTIM'S
OWN `ClientLevel` (nothing exists for another client to see or the server to be asked about); the server owns
only a spawn signal (`DELUSIONS_SIGNAL`). Skin + nametag mirror a real online player via a fresh-UUID
GameProfile + a `getSkin()` override (also picks slim/wide); `DATA_PLAYER_MODE_CUSTOMISATION`=0x7F. Behaviour
is driven at the INPUT level (WASD/mouse/sneak/sprint/jump/swing fed to vanilla `travel()`), so speed can
never exceed a real player's and the animations are genuine. ~15 states: idle · wandering · sprinting ·
sneaking · mining (real block-crack overlay) · punching · waving · jumping · dancing · twerking · spinning ·
flying · teleporting · observing (walks up, stands dead still + silent) · realisation (earned by being
watched, then vanishes or sprints at you). NOT pickable/collidable — the swing is ray-traced separately
(`DelusionManager.onAttack`). NO custom sounds (a bespoke sting would mark them as fake). The hard-won client
gotchas (isControlledByLocalInstance, once-per-tick calculateEntityAnimation, head/body split, look-ahead
jump) live in the class comments.
```
delusionsMaxConcurrent=2  delusionsSpawnIntervalMinTicks=1200  delusionsSpawnIntervalMaxTicks=3600
delusionsSpawnRangeMin=12  delusionsSpawnRangeMax=28  delusionsLifetimeMaxTicks=1200
delusionsDespawnDistance=48  delusionsStateSwapInterval=120
delusionsRealisationSeenTicks=60  delusionsRealisationChancePercent=15  delusionsChargeChancePercent=40
delusionsHitReach=4.0  delusionsViewConeDot=0.75  delusionsMirrorSelf=true  delusionsObserveDistance=4.0
Sounds: ALL VANILLA, nothing pending. Vanish = ENDERMAN_TELEPORT pitched up (which also ties it to the
  Ender Pearl it's cast with); footsteps/mining use the real block's own step/hit/break sounds; punching
  uses the flat PLAYER_ATTACK_NODAMAGE whiff; teleporting uses ENDERMAN_TELEPORT at both ends.
  No ambient "whisper" — a recognisable idle noise would mark them out as not-a-real-player.
```

### Gluttony — Cake (CUSTOM UI)  ✅ REFINED
Bigger model (SCALE +0.35 → 1.35x, physically wider) and a second hunger row — but the two rows are
**ONE BIG 40-POINT BAR**, not two separate meters. That was the whole refinement: they used to drain on
independent timers and be fed independently, which is why it felt disconnected.
- The extra row (drawn above vanilla's) is the **TOP half**. The vanilla half is held at `VANILLA_HOLD` while
  reserve remains: anything above that line is pushed UP into the reserve, anything below is pulled back
  DOWN out of it — so the upper half empties first and then the vanilla half, one long bar draining top-down.
- **⚠ `VANILLA_HOLD` is 19, deliberately ONE POINT SHORT of full.** Vanilla gates eating on
  `FoodData.needsFood()`, which is just `foodLevel < 20`, so holding the lower half at a true 20 makes the
  game think you're permanently full and **refuses every food item** until the reserve runs dry (a real bug,
  caught in play: "I am unable to eat until the second bar starts to tick down"). Sitting at 19 keeps you
  always able to eat and costs half a drumstick of display.
- **Starvation therefore takes twice as long to reach**: you only start starving once all 40 points are gone.
- **Eating fills vanilla first and the overflow spills UP into the extra row** rather than being wasted
  (measured as `nutrition - actualVanillaGain` on the use-Finish hook).
- **Sprint cuts out at `SPRINT_CUTOFF` of the COMBINED bar** — double vanilla's 6.
- The trade: food is eaten **`EAT_SPEED_PERCENT` faster** (the one mercy — there's a lot of eating to do) but
  every meal gives **`SATURATION_PENALTY_PERCENT` less saturation**, so nothing sticks and you keep grazing.
  Both measure the REAL gain from a Start/Finish snapshot, so they stay correct when vanilla clamps.
- **HUD takeover:** while active, vanilla's `FOOD_LEVEL` layer is CANCELLED (`RenderGuiLayerEvent.Pre`) and
  `GluttonyHudLayer` draws BOTH rows from the combined total (lower = `min(20, combined)`, upper = the rest).
  Necessary because the real vanilla value is held at 19, so drawing the lower row from it made the bar look
  permanently one point short and flicker as it dipped and was topped back up. Both rows mirror
  `Gui#renderFood` — green `*_hunger` sprites under the Hunger effect, jitter at zero saturation — and the
  takeover copies vanilla's visibility rules (hidden GUI, creative/spectator, riding a living mount).
- **⚠ The sprint cutoff MUST be enforced client-side** (`ClientCurseHandler`) by suppressing the sprint KEY.
  Sprinting is decided in `LocalPlayer.aiStep`, so a server-side `setSprinting(false)` is overwritten on the
  next client tick — and merely clearing the flag client-side lets the very next line of `aiStep` turn it
  back on. That's why the doubled cutoff appeared to do nothing and only vanilla's 6-point rule applied.
- **⚠ The SCALE modifier is TRANSIENT**, so a world reload drops it while the curse persists (you return the
  wrong size). `onApply` never re-runs, so `onTick` re-applies it if missing.
```
gluttonySprintCutoff=12  gluttonyEatSpeedPercent=30  gluttonySaturationPenaltyPercent=20
gluttonyModelScaleBonus=0.35   (extra row max 20 → combined bar 40)
```

### Gassy — Pufferfish (CUSTOM SOUND)  ✅ REFINED
You go off, and it MOVES you. Every `INTERVAL_MIN`..`MAX` you're launched in some direction at a randomised
velocity, biased toward whatever nearby would make it worse.

**The bias is a WEIGHTED DRAW, never a guarantee — that distinction is the whole balance of the curse.**
Backseat Driver's hazard scan uses strict priority (nastiest type always wins) because an AI deliberately
steering into danger should be reliable about it. This one must not be, or standing anywhere near lava
becomes a death sentence on a timer rather than a running joke. So:
- `RANDOM_DIRECTION_CHANCE` (35%) of farts ignore hazards outright and fire off anywhere.
- Even when a hazard IS chosen it's a weighted draw across everything found — weight = the hazard's own
  nastiness ÷ its distance, so near-and-nasty wins *more often* but a far-off cliff can still beat adjacent
  lava.
- `STRAIGHT_UP_CHANCE` (18%) go mostly vertical instead, with a slight lean so it isn't a clean elevator.
- Every directional fart still carries an upward kick (`VERTICAL_MIN..MAX`), so you always get some air.

Hazards recognised: lava · lit TNT · ledges (a column with a real drop) · fire/soul fire/lit campfires ·
magma · pointed dripstone · cacti · powder snow · sweet berry bushes · wither roses.
**Lit TNT is an ENTITY (`PrimedTnt`)**, so it needs its own lookup — a block scan silently misses the best
hazard going. The block scan is a coarse 2-block grid, affordable because it only runs when a fart actually
fires rather than every tick.

**Events force one out of you**, on a shared `EVENT_COOLDOWN` so a firework show doesn't punt you across the
map forty times:
- **Any explosion nearby** via `ExplosionEvent.Detonate` — TNT, creepers, beds, end crystals, all of it —
  and deliberately **independent of whether the blast damaged you**, since being startled doesn't require
  being hurt.
- **Fireworks**, which produce no `Explosion` and therefore have no event to hook. Instead nearby rockets are
  remembered each tick and one that has VANISHED by the next tick is read as having gone off; a firework that
  close disappearing for any other reason is vanishingly unlikely.
- **Taking a hit** (explosion damage uses the explosion chance, everything else the lower damage chance).
Event farts roll a far higher big-fart chance and carry `EVENT_VELOCITY_MULT` on top.

**Big farts** (`BIG_CHANCE`, ~8% spontaneous but ~55% on an event) use the separate louder OGG and multiply
velocity by `BIG_MULTIPLIER`. Velocity needs `hurtMarked` or the server never sends it and the victim doesn't
budge on their own screen. Discovered the first time you leave the ground under your own power.

**Particles:** a white `CLOUD` puff carrying the body of it, with a *minor* amount of green
`DustParticleOptions` mixed through (vanilla has no tintable smoke). The green is deliberately sparse — it
should read as a tinge, not as Unhygienic's full stink cloud, which is a different curse doing a different
job. Plus a directional `CLOUD` jet fired opposite the launch so it reads as thrust (count 0, which makes the
offsets a velocity).
**Note `VERTICAL_MIN/MAX` are a RATIO, not a speed** — the launch vector is normalised before the velocity is
applied, so raising them tilts farts upward without making them any stronger.
```
gassyIntervalMinTicks=400  gassyIntervalMaxTicks=1200
gassyVelocityMin=0.45  gassyVelocityMax=1.0  gassyBigFartChancePercent=8  gassyBigFartMultiplier=1.8
gassyRandomDirectionChancePercent=35  gassyHazardScanRadius=8  gassyLedgeDropMin=3
gassyStraightUpChancePercent=18  gassyVerticalMin=0.45  gassyVerticalMax=0.85
gassyOnDamageChancePercent=25  gassyOnExplosionChancePercent=60  gassyEventBigFartChancePercent=55
gassyEventVelocityMultiplier=1.5  gassyEventCooldownTicks=40  gassyExplosionHearRadius=12.0
Sounds: witchmod:curse.gassy.fart_small (fart1-4) ✅ SUPPLIED · witchmod:curse.gassy.fart_large (fartbig) ✅
```

### Farmhand — Wheat  ✅ REFINED
You can't build in peace. Every untamed `Animal` within `RADIUS` (**passive mobs only** — Oliver's call) gets
a `FarmhandBlockGoal` at **priority 1**, making getting-in-the-way its top task above wandering/grazing
(float/panic still sit at 0-1, so a hit makes it flinch then wander back). **Most of the herd packs into a
dense crowd hugging you** — concentric rings at `SURROUND_RADIUS` +0/+0.5/+1.0, so a big herd nests around
you instead of fighting for one spot — and only **1 in 4** peels off to stand on the block under your
crosshair (server-side raycast) and block placement/mining.
**Pace is deliberately lazy:** `SPEED_BONUS` +15% (a real MOVEMENT_SPEED modifier applied by the goal and
handed back in `stop()`), `NAV_SPEED` 1.05, and the `MoveControl` nudge fires ONLY once the path is done —
driving it every tick made the herd stampede rather than wander into the way.
**Discovery fires when an animal is planted IN FRONT of you** — within ~2.5 blocks and a 45° view cone — not
merely when one is recruited.

**Two bugs found by adding temporary diagnostics — the AI itself was never broken** (it logged `pathed=true`
on every mob throughout; three rounds of blind tuning had been chasing a non-existent fault):
1. *"Barely noticeable"* — the split was originally **2 in 3 chasing the CROSSHAIR**, which sits up to 5
   blocks away and moves whenever you glance elsewhere, so the herd spent its time walking to a spot several
   blocks off and was never actually underfoot. Flipping it to crowd-the-player is what made it land.
2. *"Was already applied but did nothing until I re-applied it"* — the goal captures a `ServerPlayer`, and
   that object is **replaced on respawn/relog**, leaving every animal holding a dead reference so `canUse()`
   failed forever. Recruitment now checks **who the goal is bound to** (`isTargeting`) and swaps stale goals,
   instead of only asking whether a goal exists.
**Recruitment is guarded by checking the animal's GOAL LIST, not a scoreboard tag** — goals are transient,
so a persistent tag would survive a world reload while the goal didn't, leaving old animals permanently
un-recruited (an earlier "pigs just ignore me" bug).
**Top-up spawning:** if fewer than `MIN_ANIMALS` are nearby, a spawn burst (`SPAWN_ATTEMPTS`, on a
`SPAWN_COOLDOWN`) conjures biome-appropriate creatures — but ONLY where vanilla itself would allow them
(`SpawnPlacements.isSpawnPositionOk` + `checkSpawnRules NATURAL`, i.e. grass/light/footing), matching "the
game has the correct conditions". Tamed/owned animals are exempt. Discovered when anything gets recruited.
```
farmhandRadius=24.0  farmhandNavSpeed=1.05  farmhandSpeedBonus=0.15  farmhandSurroundRadius=1.3
farmhandRecruitIntervalTicks=20
farmhandMinAnimals=3  farmhandSpawnCooldownTicks=300  farmhandSpawnAttempts=3
farmhandSpawnRadiusMin=6  farmhandSpawnRadiusMax=16
```

### Heavy — Iron Ingot  ✅ REFINED
You come down like a dropped anvil. You fall faster, you take more for it, and past a threshold you stop
landing and start *impacting* — a real explosion at the landing site that craters the ground, throws
everything nearby and hurts you too.

**Fall speed is the `Attributes.GRAVITY` attribute, not a downward shove.** That keeps acceleration,
terminal velocity and vanilla's own fall-distance bookkeeping intact — you're simply heavier, so a chunk of
the extra fall damage follows on its own before `FALL_DAMAGE_MULT` is even applied. (Same approach Bad
Swimmer uses; note the attribute is hard-capped at 1.0 by the game.) The modifier is TRANSIENT, so `onTick`
re-applies it if missing — otherwise a world reload silently returns you to falling normally.

**The crater scales with the drop, up to a hard cap.** The cap is the load-bearing half: uncapped, a fall
from build height would level a base. Scaling is measured on fall DISTANCE rather than by sampling impact
velocity on the exact landing tick — distance is monotonic in speed right up to terminal velocity, so it
gives the same felt result far more stably. Power lerps `POWER_MIN`→`POWER_MAX` across
`MIN_FALL`→`CAP_FALL`.

It's a genuine `Level.explode`, so block damage, knockback, sound and shockwave are all vanilla's, with
world damage gated on `mobGriefing` via `Level.ExplosionInteraction.MOB`. On top of that, an impact spray of
whatever block was actually landed on (`BlockParticleOption`), plus explosion and cloud puffs, all scaled by
the same factor.
**You take only `SELF_DAMAGE_PERCENT` of your own crater** — you're at dead centre AND about to eat
amplified fall damage, so a full share would execute you every single time.

**⚠ Same trap as Super Explosive: the explosion's source ENTITY must be `null`.** `Explosion` gathers victims
via `Level.getEntities(source, box)`, whose first argument is the entity to **EXCLUDE** — naming the player
there quietly leaves them out of their own crater, taking neither damage nor knockback. Attribution rides on
the damage source (`damageSources().explosion(null, player)`) instead.
**Crater cooldown (`CRATER_COOLDOWN`)** stops the blast CHAINING: a crater blows the ground out from under
you, so without a guard you drop into your own hole, crater again, and excavate yourself downward off a
single fall.

**⚠ Landing is detected on the TICK, from vanilla's own `fallDistance`, NOT from `LivingFallEvent` — and that
is what makes cratering work in CREATIVE.** `Player.causeFallDamage` returns immediately when `mayFly()` is
true (it routes to `PlayerFlyableFallEvent` instead), so a damage-driven crater simply never fires in
creative. Watching `fallDistance` fall back to zero while `onGround` is mode-independent. Flying resets it
too (`Player.travel` calls `resetFallDistance`), so a gentle creative descent doesn't crater — you have to
actually stop flying and drop. `LivingFallEvent` is still used, but ONLY for the damage multiplier.
Landing in water leaves `onGround` false, so no crater there either — which is the right call anyway.

**Discovery is on the FIRST FALL, not the first crater** (Oliver's call): you come down noticeably heavier
straight away, so holding the alert back until a crater would only be telling the victim what they already
know.
NOTE: the prototype's JUMP_STRENGTH penalty was a stand-in and is **removed** — it isn't in the spec, which
is about falling, not jumping.
**Anchor in water** (Oliver's addition): an extra `WATER_PULL` downward per tick while in water or lava, so
you sink like the dead weight you are. Needed as its own value because **vanilla divides gravity by 16 in
fluid**, so the GRAVITY attribute alone only gives a slightly brisker version of the same gentle bob.
Applied CLIENT-side off a synced `HEAVY_ACTIVE` flag for the same reason Bad Swimmer's pull is — player
movement is client-authoritative. Creative flight is exempt.
```
heavyFallSpeedMultiplier=1.8  heavyFallDamageMultiplier=1.75  heavyWaterPull=0.06
heavyCraterMinFall=8  heavyCraterCapFall=40  heavyCraterCooldownTicks=60  heavyJumpCompensation=1.35
heavyCraterPowerMin=1.5  heavyCraterPowerMax=4.0   // TNT is 4.0
heavyCraterSelfDamagePercent=25  heavyCraterKnockbackMultiplier=1.6
```

### Slippery Feet — Ice (CUSTOM SOUND)  ✅ REFINED
A joke at the expense of Minecraft's most reflexive habit: everyone sneaks up to an edge to look over,
trusting vanilla not to let them fall. This breaks that trust, with a slide whistle.

**TWO triggers, and nothing else — it never shoves you at random.** That restraint IS the design: a push
that could arrive anywhere is just an annoyance tax on walking about, whereas one that only ever happens at
an edge makes edges themselves frightening.
- **Standing** near a ledge/hazard — a very low roll (`STANDING_CHANCE`) every `CHECK_INTERVAL`. Background
  dread, not a real threat.
- **Crouching** over one — **guaranteed** after 1–2s. The grace period is **re-rolled every time you settle
  over an edge**, so it can never be counted out and waited through.
If neither holds, nothing happens at all.

"Ledge" = a neighbouring column with nothing solid at foot height AND a real drop beneath (so a step *up*
never counts). "Hazard" covers lava, fire/soul fire, magma, lit campfires, cacti, powder snow, sweet berry
bushes, wither roses and dripstone — being shoved one block sideways into a fire pit counts as much as being
shoved off a cliff. The shove is aimed at the nearest such column, with a small upward `PUSH_LIFT` so you
clear the lip instead of scraping down the block face; `hurtMarked` is required or the velocity never
reaches the client. Discovery on the first push — until your feet go, there's nothing to notice.
```
slipperyCheckIntervalTicks=20  slipperyStandingChancePercent=2
slipperyCrouchMinTicks=20  slipperyCrouchMaxTicks=40
slipperyLedgeDropMin=2  slipperyPushForce=0.35  slipperyPushLift=0.18
Sound: witchmod:curse.slippery.slide_whistle (slide1/2/3) ✅ SUPPLIED
```

### Magnet — Lodestone  ✅ REFINED
Everything thrown in your general direction finds you. Every projectile within `RADIUS` has its velocity
bent toward you each tick, so arrows that would have sailed past instead curve in. It exists to make
fighting at range miserable.

**Direction is STEERED, speed is PRESERVED.** The velocity is rotated toward the victim by `STEER_STRENGTH`
and then rescaled to its original magnitude, rather than having a pull added to it. Adding acceleration
would make every arrow hit harder the longer it flew — that turns a curse about accuracy into a curse about
damage, which isn't the joke and is far harder to balance.
The projectile's yaw/pitch are rewritten to match the new heading, or arrows visibly fly sideways along
their old one.

**Your own projectiles are excluded** (`AFFECTS_OWN=false`, Oliver's call). Having your own arrows boomerang
into your face isn't funny, it's unplayable — you'd be unable to use a bow at all rather than merely being
easy to hit.
Projectiles already stuck in a block (effectively zero velocity) are skipped, and a per-tick cap keeps an
arrow barrage from costing the server. Discovered the moment anything visibly bends toward you.
**It also pulls harmful THINGS your way, subtly** (Oliver's addition): primed TNT within `magnetRadius` gets a
small per-tick velocity nudge toward you (`magnetHazardPull`), so it creeps over while its fuse burns — a
creeping menace, not a yank (and it leaves TNT already on top of you alone).
```
magnetRadius=16.0  magnetSteerStrength=0.15  magnetAffectsOwn=false  magnetMaxProjectiles=32  magnetHazardPull=0.035
```

### Neutral Aggression — Spider Eye  ✅ REFINED
**RENAMED from "Neutral Mobs Attack Instantly" on Oliver's call** — and the registry id was changed with it
(`neutral_mobs_attack_instantly` → `neutral_aggression`), because display names derive from the id path
(`DiscoveryManager.titleCase`), so the id IS the visible name. Same situation as Unhygienic. Any saved
instance of the old id is dropped as unknown; harmless for a placeholder that never shipped.

Everything that would normally leave you alone has decided today is not that day. Anything neutral within
`RADIUS` turns on you: endermen, zombified piglins, wolves, iron golems, bees, polar bears — **and your own
tamed pets** (`TURNS_OWN_PETS`, on by default, because your own dog deciding it hates you is the best thing
this curse does).

**"Neutral" is vanilla's own `NeutralMob` interface, never a hardcoded list** — same principle as Allergic's
food categories, so modded neutral mobs are covered automatically and the boundary always matches what the
game itself considers neutral.
**⚠ SPIDERS are the one exception that must be added by hand.** A spider is a `Monster`, not a `NeutralMob`:
its daytime passivity comes from `SpiderTargetGoal.canUse()` refusing to acquire a target above light level
0.5, not from any anger system. So by the interface it's hostile, but by behaviour it is precisely what a
player means by neutral — it leaves you alone in daylight. Caught in play: "spiders aren't affected at all
and just don't attack in day still". CaveSpider extends Spider, so it's covered too. Spiders have no
persistent anger to set, so the target is simply re-applied each sweep, which also survives their own goals
clearing it.

**⚠ Persistent anger is the mechanism, NOT `setTarget`.** The prototype just called `setTarget` on every
nearby `Mob` (including cows, which have no attack goal at all), and a bare target is wiped within a tick or
two by the mob's own target-selection goals — `ResetUniversalAngerTargetGoal` and friends — so the aggro
flickered and died. Setting `setPersistentAngerTarget` + `startPersistentAngerTimer` is how vanilla itself
makes these mobs hostile (it's exactly what hitting an enderman does), so the hatred sticks, and decays on
vanilla's own schedule once the curse ends rather than needing to be cleaned up.

This curse is why **Popularity deliberately EXCLUDES neutral mobs** from its horde — force-aggroing endermen
and zombified piglins is this one's whole job. Discovered the first time something turns on you.
```
neutralAggroRadius=24.0  neutralAggroCheckIntervalTicks=40  neutralAggroTurnsOwnPets=true
```

### Dwarfism — Turtle Egg  ✅ REFINED
You're half the man you were: **half height, half health**, and small enough that the sensible way to get
anywhere is to climb on someone. Right-click a player or a villager to ride them.

**Both halves are attribute modifiers**, so nothing fights vanilla. `Attributes.SCALE` drives the bounding
box as well as the model, so shrinking genuinely lets you fit through **one-block gaps** rather than merely
looking like it; `MAX_HEALTH` is scaled the same way.
**⚠ Health must be CLAMPED on the way in.** Lowering the maximum doesn't lower current health, so without it
you'd sit at 20/10 — which vanilla draws as a full bar, hiding the entire downside of the curse.
**⚠ Both modifiers are TRANSIENT, so `onTick` re-applies them** — a reload would otherwise return you
full-sized and full-health with the curse still running. (Fourth curse in this project to hit that trap,
after Gluttony, Heavy and Bad Swimmer.)

Riding uses `startRiding(mount, force=true)` — force because a Player isn't normally a valid vehicle, though
nothing about it misbehaves. **The mount keeps full control of itself:** a rider only steers a vehicle that
reads rider input, and neither players nor villagers do, so this is pure indignity rather than a hijack. The
interact event is cancelled on success so the same click doesn't also open a villager's trade screen.
Dismounts on cure. Note the old spec's speed penalty is dropped — Oliver's spec is size, health and riding.
```
dwarfismModelScale=0.5  dwarfismHealthMultiplier=0.5
dwarfismCanRidePlayers=true  dwarfismCanRideVillagers=true
```

### Screensaver — Painting  ✅ REFINED
Every so often the game drops out of fullscreen, **slowly** shrinks to a window, bounces around the monitor
DVD-logo style for a while, then grows back and returns to fullscreen. Client-only; every stage no-ops if
the monitor can't be queried.

**It is EPISODIC, not constant.** The prototype forced the game permanently windowed and bounced forever,
which stops being funny within a minute and makes the game unplayable. Roughly `DUTY_PERCENT` (20%) of the
time is spent bouncing and the rest is left completely alone.
**The idle gap is DERIVED, not configured:** `gap = episode × (100 − duty) / duty`, plus ±25% jitter so
episodes don't arrive on a countable metronome. Retuning episode length therefore preserves the overall
rhythm without also having to retune the gap.

**Deliberately slow and LARGE.** Transitions are smoothstep-eased over `TRANSITION_TICKS` (4s each way) and
the shrunk window stays at `WINDOW_SCALE_PERCENT` (55%) of the monitor. A small window snapping about at
speed is a motion-sickness generator rather than a joke, and you still have to be able to play through it.
The shrink pulls toward the centre of the screen so it doesn't lurch sideways; the grow starts from wherever
it drifted to, not from where it began.

**⚠ Resizes are throttled to every other tick.** Each `glfwSetWindowSize` makes Minecraft rebuild its
framebuffer, so pushing one 20x a second for four seconds stutters; at 10/s the easing still reads smooth.
**⚠ Fullscreen is toggled via the OPTION** (`options.fullscreen().set(...)`), never `toggleFullScreen()` —
vanilla's own change callback does the toggle, so calling both double-toggles straight back (a real bug this
project already hit). The player's original state — fullscreen or not, plus windowed size and position — is
captured at episode start and restored at the end, including if the curse is cured mid-episode, so it never
permanently changes their setup.
```
screensaverEpisodeMinTicks=400  screensaverEpisodeMaxTicks=900   // 20s .. 45s
screensaverDutyPercent=20  screensaverTransitionTicks=80
screensaverWindowScalePercent=55  screensaverSpeedPx=2
```

### Minor Inconvenience — Cobweb  ✅ REFINED
You cannot go fullscreen, and your window is named something stupid every so often. Client-only.

**Fullscreen is refused, then the window is MAXIMISED** (Oliver's call). Both halves matter: the renamed
title bar is invisible in fullscreen, so the joke needs windowed — but merely dropping to whatever windowed
size they last had shrinks the play area, which is a real handicap rather than a minor inconvenience.
Maximised gives the whole screen AND a visible title bar.
Maximising is **re-asserted every tick until the GLFW `MAXIMIZED` attribute actually sticks** (then it stops,
so it isn't spammed), and is skipped while Screensaver is bouncing the window so the two curses don't fight.
The per-tick re-assert is load-bearing: exiting fullscreen makes vanilla RESTORE the previous (small)
windowed size one frame AFTER the option is set, which overwrote the old one-shot maximise and left you
staring at the desktop — re-asserting catches that. Fullscreen is refused via `options.fullscreen().set(false)`
— vanilla's change callback does the toggle, so calling `toggleFullScreen()` as well double-toggles back.

**Titles come from a writable list**, `assets/witchmod/text/window_titles.json` — a plain JSON array of
strings. **Note `assets/`, not `data/`**, the same call as the Loading Screen's tips and for the same
reason: purely client-side, so it needs no server→client syncing and reloads with an ordinary resource
reload rather than `/reload`. Re-read on each activation of the curse (not per rename — that would be
pointless churn at a 30s–2min cadence), so editing it and re-applying picks changes up without a restart.
Missing or malformed falls back to one title with a warning rather than crashing.
Rename interval is 30s–2min, deliberately faster than the old spec's 2400–9600 (2–8min), which was rare
enough that a victim might never see it change.
```
minorInconvenienceRenameMinTicks=600  minorInconvenienceRenameMaxTicks=2400
List: assets/witchmod/text/window_titles.json  ✅ CREATED (25 entries, freely editable)
```

### Social Outcast — Wither Rose  ✅ REFINED
Nobody's there. Other players and villagers simply aren't drawn on your client — **no model, no nametag** —
unless they get right on top of you (`REVEAL_DISTANCE`) or they hit you.

**The hiding is pure client-side render.** `RenderLivingEvent.Pre` is cancelled, which returns before
`LivingEntityRenderer.render` reaches its `super.render` call — and that call is what draws the nametag, so
one cancel hides both. Nothing about the world changes: they are still there, still solid, still able to
kill you. You just can't see them coming.

**⚠ The damage reveal MUST be server-driven.** Who dealt the damage is server-authoritative — the client is
told that it was hurt, not reliably by whom — so the set of revealed entity ids is tracked server-side and
synced down via `SOCIAL_OUTCAST_REVEALED`. The set is tiny (only whoever has hit you inside the window) and
is only re-synced when the SET changes, not on every hit that merely extends an existing timer.
The damage source's **direct entity** is used, so an arrow reveals the archer rather than the arrow.

**The window lapsing is what makes it a curse rather than a one-off.** A running fight keeps your attacker
on screen, but the moment they stop hitting you for `DAMAGE_REVEAL_TICKS` they vanish again — mid-fight,
while still hunting you.
**Hidden entities are SILENCED as well as invisible** (Oliver's call). Hearing footsteps and villager
mumbling out of thin air would give the illusion away instantly — and worse, would tell you exactly where
the person you can't see is standing, which is the opposite of the point. A `SoundInstance` carries a
position but not who made it, so `PlaySoundEvent` matches on ORIGIN: any PLAYERS/NEUTRAL/VOICE sound
starting within `OUTCAST_SOUND_MATCH_RADIUS` (2 blocks) of a currently-hidden entity is dropped. Approximate
by nature and deliberately so — a noise coming from exactly where an invisible person stands should be
suppressed whatever produced it. Render and audio share one `isOutcastHidden` check so they can never
disagree.

**Discovery fires when someone actually POPS INTO VIEW** (Oliver's call) — the out-of-range → in-range
transition — not when the curse lands. Until something appears out of nowhere there's nothing to notice; an
empty world just looks like an empty world. The server mirrors the client's proximity test rather than being
told about it, since it knows every position anyway.
NOTE the prototype did something entirely unrelated — it shoved nearby villagers away — and was replaced
wholesale.
```
outcastRevealDistance=4.0  outcastDamageRevealTicks=400  outcastHidesVillagers=true
```

### Floor Is Lava — Magma Block  ✅ REFINED
The ground remembers being lava, and it only notices you when you STOP. Stand still past the grace period
and you start burning — and it gets worse the longer you stand there.

**The grace period and the ramp do opposite jobs, and both are needed.** The grace (`GRACE_TICKS`, 5s) is
what keeps the curse playable — you can still craft, read a sign, dig through a chest. The **ramp**
(`RAMP_PER_BURN`) is what stops it being a flat tax you simply eat: the longer you ignore it the sharper it
bites, so it eventually forces you to move rather than merely costing health. The **cap** (`MAX_DAMAGE`)
then stops an AFK player being executed outright, which would be a disconnect rather than a joke.

Damage uses vanilla's own `HOT_FLOOR` source — the magma-block one, which is exactly the sacrificial item.
Fire resistance therefore protects: deliberate counterplay, not an oversight. Flames and smoke thicken
around the feet as the ramp climbs, so it reads as the floor heating up rather than as random damage.
Movement is measured as real **displacement**, so turning on the spot doesn't save you.
**Deliberately NOT paused while a GUI is open** — cowering in your inventory is precisely the behaviour this
curse exists to punish, and the grace period already covers legitimate crafting.
Discovered on the first burn.
```
floorIsLavaGraceTicks=100  floorIsLavaDamageIntervalTicks=20
floorIsLavaBaseDamage=1.0  floorIsLavaRampPerBurn=0.5  floorIsLavaMaxDamage=4.0
floorIsLavaMovementResetDistance=0.5
```

### Heavyweight — Iron Block  ✅ REFINED
The floor can't take you. Stand on anything with **air beneath it** — a second storey, a bridge, a ledge —
and it starts giving way under your weight.

**Break time scales with the block's own hardness**: leaves ~0.8s, dirt ~1.1s, stone ~2.4s, planks ~3.0s,
iron block ~6.8s, obsidian ~60s. (The first pass used a 60x multiplier, which put planks at 7s and obsidian
past two minutes — "very slow, even on blocks like leaves and especially wood+".) Obsidian stays a real
deterrent without being literally impossible. **Unbreakable blocks (hardness < 0 — bedrock, barriers) are
skipped entirely**, so nothing can ever chew through the world bottom.

**⚠ It must warn you, or it reads as a bug rather than a curse — but the warning is VISUAL, not audible.**
That split is deliberate (Oliver's call). Dust pours out of the underside for the WHOLE duration, thickening
as it goes, with fragments spitting off the top edge once it's close; the crack overlay creeps across via
`destroyBlockProgress` (server-side, so everyone sees it). The audio stays sparse and quiet — an early
version creaked constantly and loudly, which was both grating and easy to tune out.

**The COLLAPSE is where the noise goes.** `ZOMBIE_BREAK_WOODEN_DOOR` (the most "something just gave way"
sound vanilla has) layered with the block's own break sound, ~60 debris particles plus smoke, a **camera
shake**, and a **ragged hole** taken out of the surrounding floor (`COLLAPSE_RADIUS`/`COLLAPSE_CHANCE` —
below 100% so it isn't a neat square). Neighbours only go if they were themselves unsupported and
breakable. One block quietly disappearing didn't read as a collapse at all.
**Vanilla has no screen shake**, so it's hand-rolled: a synced `HEAVYWEIGHT_SHAKE_END` tick drives a random
jolt on yaw/pitch/roll in `ComputeCameraAngles`, decaying quadratically so it lands as one impact. Applied
to the camera ANGLES, never the player's real rotation, so it never actually changes where you're aiming.

Progress is tied to ONE block position and resets the moment you step off, so walking across a floor is
safe and only loitering is punished. The overlay is cleared on step-off, or the cracks would stick on that
block permanently (the same trap Delusions' mining state hit). World damage is gated on `mobGriefing`;
the break is credited to the victim, so it's their doing.
NOTE the prototype was unrelated — it granted attack-knockback and knockback-resistance attributes — and was
replaced wholesale.
```
heavyweightBaseBreakTicks=10  heavyweightHardnessMultiplier=25.0  heavyweightWarnFraction=0.45
heavyweightCollapseRadius=1  heavyweightCollapseChancePercent=60
heavyweightShakeTicks=12  heavyweightShakeStrength=3.5
```

### Thirst Meter — Water Bottle (CUSTOM UI — FULLY FUNCTIONAL UI REQUIRED)
Replica hunger bar with droplet icons; drains slower than hunger; refill via water bottles or
right-clicking water (raw water risks hunger/poison).
```
THIRST_MAX=20  THIRST_DRAIN_MULT=0.75  THIRST_BOTTLE_RESTORE=6  THIRST_RAW_WATER_RESTORE=4
THIRST_RAW_WATER_DEBUFF_CHANCE=0.25  THIRST_EMPTY_EFFECTS=[slowness_1,weakness_1]  THIRST_EMPTY_DAMAGE=0.0
```

### Bad Swimmer — Copper Ingot
You never learned to swim. Liquid stops acting like liquid: you drop straight through it as if it were
air, hit the bottom and **walk along it**. You cannot kick your way back up — the pull down far exceeds
what swimming gives you. Stepping up blocks underwater just works (it's ordinary walking on an ordinary
floor, so the normal step height applies). Breathing is untouched, so **drowning still applies**. Applies
to lava as well as water. Discovered on trigger (the first time you get in liquid and sink).

**⚠ You also cannot enter the SWIMMING state at all** (Oliver's call — sinking alone was too close to
Heavy's water anchor, and two curses shouldn't do the same thing). The horizontal front-crawl is simply
unavailable to you. Vanilla only enters that pose when `isSprinting()` is true in water, so the real lever
is killing the sprint at the KEY (`keySprint.setDown(false)`) client-side — a bare `setSwimming(false)`
would just be recomputed straight back the next tick. Same client-authoritative lesson as Gluttony's sprint
cutoff. **This is what distinguishes the two curses: Heavy makes you sink, Bad Swimmer makes you unable to
swim.**
**Single blocks are climbable underwater via extra STEP HEIGHT** (`badSwimmerStepBonus`, 0.6 → 1.1), not via
a weaker pull. That distinction matters: underwater there is no impulse jump to boost — past the fluid-jump
threshold vanilla routes jumping to `jumpInFluid()`, a gentle sustained thrust — so the only way to clear a
block by *rising* is to swim up, the one thing this curse forbids. Weakening the pull enough to hop a ledge
would hand back a slow ascent to the surface as well. Raising the step separates them cleanly: you can WALK
over terrain on the bottom, and you still cannot go UP.

**Mostly real vanilla physics** — two attribute modifiers toggled on only while in liquid:
- `Attributes.GRAVITY` ×16 — vanilla divides gravity by 16 underwater (that's the gentle bob), so
  multiplying it back restores air-like sinking. NOTE the attribute is hard-capped at 1.0 by the game, so
  anything above ~12 lands on that cap.
- `Attributes.WATER_MOVEMENT_EFFICIENCY` = 1.0 — lerps water acceleration/friction to exactly the LAND
  values, so you walk instead of swim. Vanilla halves this while airborne and gives it in full when
  `onGround`, which lands perfectly: you sink, then walk properly once standing on the bottom.
Both come off the moment you leave liquid, so normal falling is unaffected.

**Attributes alone are NOT enough** — vanilla's `getFluidFallingAdjustedMovement` is gated on
`!isSprinting()`, so fluid gravity is skipped ENTIRELY while sprinting: a sprint-swim switched the whole
curse off, leaving no middle ground between "can't stay up at all" and "swimming works fine". So a **constant
downward pull is applied client-side** (player movement is client-authoritative) which bites in BOTH states,
plus a one-shot **entry plunge** as you break the surface. Reference point for tuning: a full swim-up tops
out around **0.048/tick**, so the pull sits just under it — ascending is possible but a losing battle.
Never applied while flying, so creative flight is untouched.
```
BAD_SWIMMER_GRAVITY_MULTIPLIER=16.0  BAD_SWIMMER_WATER_EFFICIENCY=1.0  badSwimmerStepBonus=0.5
BAD_SWIMMER_CONSTANT_PULL=0.04       BAD_SWIMMER_ENTRY_PLUNGE=0.4
```

### Pests — Cobblestone  ✅ REFINED
Every block you break has something living in it. A per-block chance that **1–3 silverfish** pour out and
come straight for you (`setTarget` on the victim, so they know whose fault it is). **ANY block**, not just
stone — the ore bonus from the old spec is dropped.

**⚠ `MAX_NEARBY` is a SAFETY guard, not balance.** Silverfish call MORE silverfish out of surrounding stone
when they're hit, so a curse that adds them every few blocks mined can snowball into a swarm the victim
can't recover from and a server would rather not tick. Above the ceiling the spawn is simply skipped until
the crowd thins.
Note the chance looks low because **mining is such a high-frequency action** — at 8% per block, ordinary
tunnelling still produces a steady trickle. Discovered the first time something crawls out.

**Also fixed a latent item collision:** Pests was on `STONE` and Basement Dweller on `COBBLESTONE`, but §5
assigns Pests → **Cobblestone** and Basement Dweller → **Grass Block**. Both were simply on the wrong items;
Grass Block was unused, so Basement Dweller moved to its spec-correct item and freed Cobblestone. Fixed
inside Pests' own refinement, so not a lone item change.
```
pestsChancePercent=8  pestsMinPerTrigger=1  pestsMaxPerTrigger=3
pestsMaxNearby=12  pestsNearbyRadius=16.0
```

### Allergic — Sweet Berries  ✅ REFINED
ONE of THREE diets is rolled at application (the victim is never told which — they find out by eating).
Eating anything the diet forbids **blinds and poisons** you and you keep only a fraction of the hunger
**and** saturation it would have given.
- **Vegetarian** — cannot eat meat.
- **Carnivore** — cannot eat natural (plant) food.
- **Clean eater** — cannot eat magic food, AND gets **no positive effects from potions**.

**Classification uses in-game categories, never hardcoded item lists, so modded foods work automatically:**
meat = the vanilla `minecraft:meat` item tag; magic = any food whose food component grants effects (or a
potion that grants effects); natural = the remainder (edible, but neither meat nor magic). Water bottles
grant no effects, so they stay drinkable for clean eaters (and still feed the Thirst Meter).
```
allergicBlindnessSeconds=8  allergicPoisonSeconds=6  allergicPoisonLevel=1
allergicNutritionPercent=50   // % of hunger AND saturation actually kept
```
Nutrition clawback measures the REAL gain (snapshot on use-Start, corrected on use-Finish) so it stays
correct even when you were nearly full and vanilla clamped the gain.

**Scrying Mirror interaction:** the mirror names your exact diet instead of leaving you to discover it by
eating. Implemented generically — `Effect.scryingDetail(target)` is an overridable hook (empty by default)
that the mirror appends to an effect's line, so any future attachment can expose instance detail the same
way without the items package knowing about specific curses.

### Comic Relief — Lightning Rod  ✅ REFINED
A reference to the genre of clip where someone is quietly having a bad time and the sky finishes the job.
While you're below `LOW_HEALTH` there's a small chance per check of a comedically-timed bolt that kills you
**outright** — a bolt's normal 5 hearts wouldn't be the joke and might not even finish you, so the damage is
applied directly and the bolt itself is `setVisualOnly(true)` (so it can't also set fire to half the
neighbourhood). If you've a pile of dropped items nearby (`ITEM_PILE_MIN`+), the bolt may take **those**
instead, vaporising them outright.

**The odds are deliberately small.** The whole joke rests on it being unexpected; a bolt you can see coming
is just weather. Everything is multiplied by `THUNDER_MULTIPLIER` (2.5) while it's actually thundering —
the one time the game has already primed you to expect lightning.

**The posthumous strike is intentional and rare.** Dying earns a small chance of one more bolt landing on
the spot `POSTHUMOUS_DELAY` later — long enough that the death screen is already up — which torches the
drops you left behind. Kicking someone while they're down is the entire bit. Deferred through a pending
list ticked from `CurseEventHandler`, the same way Explosive defers its blast.
Discovered on ANY bolt this curse causes, including the ones that only hit your items.
**Item corrected to Lightning Rod** — the prototype was on Trident, which isn't its spec item (nothing else
used either, so no collision either way).
```
comicReliefCheckIntervalTicks=100  comicReliefLowHealthThreshold=6.0
comicReliefStrikeChancePercent=3.0  comicReliefItemStrikeChancePercent=4.0
comicReliefItemPileMin=5  comicReliefItemScanRadius=8.0
comicReliefPosthumousChancePercent=2.0  comicReliefPosthumousDelayTicks=40
comicReliefThunderMultiplier=2.5
```

### Ugly — Carved Pumpkin  ✅ REFINED
Your face isn't your own. For the duration, EVERY client — including the victim's — renders them wearing one
of the mod's ugly skins.

**📁 SKINS GO IN `assets/witchmod/textures/entity/ugly/` — just drop PNGs in.** Any valid `.png` in that
folder is listed at runtime; no registration, no code change. 64x64 standard player-skin format.
A filename ending `_slim` (e.g. `gremlin_slim.png`) is treated as Alex-armed; anything else is Steve-armed.
`readme.txt` in the folder repeats all of this. F3+T re-scans (a client reload listener is registered), so
adding a skin mid-session works.
**⚠ FILENAMES MUST BE LOWERCASE WITH NO SPACES.** Minecraft refuses any resource path containing anything
but `a-z 0-9 _ - .`, and drops it BEFORE this mod ever sees it — `purple guy!.png` was silently ignored in
testing for exactly this reason. Rejected files show up in the log as `Invalid path in pack: ... ignoring`,
and the mod logs `[Ugly] Loaded N ugly skin(s)` so the count can be sanity-checked against the folder.

**The server picks a NUMBER, not a skin** — it can't pick a skin, since the folder is a client resource the
server never sees and players may add to. It rolls a plain value; each client reduces it modulo however many
skins IT can list, over a **sorted** list, so every client independently lands on the same face for that
player without the server knowing anything about the files.

**The swap works by reflectively replacing `PlayerInfo.skinLookup`** — the field
`AbstractClientPlayer.getSkin()` ultimately reads from. Swapping it changes the skin *everywhere at once*
(world model, first-person hands, inventory doll, tab list) rather than only where a render hook could
reach. It's a private FINAL field, but non-static finals are writable once `setAccessible(true)` succeeds,
and this project already uses reflection (`LivingEntity.attackStrengthTicker`) rather than take on mixin
infra. The original lookup is stored per player and restored the moment the curse ends.

**⚠ It works on OTHER players because NeoForge syncs entity attachments to every player TRACKING the
entity**, not just its owner (`AttachmentSync.syncEntityUpdate` → `getPlayersWatching` + the player itself).
**Everything is re-asserted every client tick**, which is what makes it survive relogging, dying, changing
dimension and other players wandering into view long after the curse landed — there is deliberately no
one-off "apply" moment to miss.
```
(no config values — the skin list is whatever is in the folder)
```

### Audit — Emerald  ✅ REFINED  (renamed from "Taxes"; id `taxes` → `audit`)
Somebody has noticed how much you're carrying. The victim is periodically assessed — chests nearby, items on
the floor, their own pockets — and once there's enough VALUE within `SCAN_RADIUS`, the **Tax Man** turns up
and starts confiscating. The mod's first custom entity.

**He walks the property rather than vacuuming it.** He paths to the victim first, then goes container to
container: navigating to each, **actually opening the lid** (vanilla's own chest block-event, id 1 — the
same mechanism a real player triggers, not a fake animation), pausing over it while taking a stack every
`COLLECT_INTERVAL`, closing it, moving on. He watches the victim whenever he isn't mid-search. That
physicality is the whole difference between reading as a person and reading as a script — the first pass
took everything from a standstill and looked, in Oliver's words, lazy.
**"Immovable" means unpushable/unkillable, NOT motionless** — `hurt`/`isPushable`/`doPush`/`push` are all
neutered, but he has real pathfinding and a walking speed.

**Search order is fixed and deliberate: chests → floor items → the victim's inventory.** That ordering IS
the counterplay. Storage is hit first, so keeping valuables where you live is the worst option; your person
is raided last, so travelling light genuinely works. Nearby ender chests are included, and after
`ENDER_CHEST_AFTER` fruitless sweeps he places **his own ender chest** to reach the one stash you thought
was out of his jurisdiction — then takes it with him when he leaves.

**Taken by VALUE, not item count** (`TaxValues` / `taxesItemValues`): iron+copper 1, gold 2, emerald 3,
diamond 4, netherite 16, storage blocks ~9x their ingot; anything else in the tag falls back to
`taxesDefaultItemValue`, so modded ores work automatically. Without weighting, "take 40 items" is trivial
from one angle and devastating from another. The cap is deliberately small — an irritation, not a robbery.

Valuables are the datapack tag `witchmod:valuables` — every ore line and its ingots/gems/blocks,
deliberately excluding redstone and coal. Chat is keyed by state from `data/witchmod/text/taxman.json`
(`arrive`, `searching`, `taking`, `satisfied`, `empty_handed`, `ender_chest`, `bank_full`, `gave_up`).

**⚠⚠ THE TAX BANK'S CAPACITY IS A MEMORY LIMIT, NOT A BALANCE ONE, AND MUST NEVER VOID ITEMS ⚠⚠**
`TaxBank` is a `SavedData` list that persists for the life of the world and is drained ONLY by the Tax Man
*blessing* — which may not be cast for weeks. Without a ceiling it grows unbounded. It is therefore sized
far larger than one visit's haul (`taxesBankCapacityStacks`, 512) so it comfortably holds many taxations,
and when it fills the correct behaviour everywhere is to **STOP TAKING**:
- `deposit()` returns false and stores nothing; the Tax Man leaves the items exactly where they are and
  departs with a `bank_full` line.
- The **Bewitching Table refuses** to apply Audit ("the ritual fizzles").
- **`/bewitch apply` refuses** it, and warns from `taxesBankWarnAtPercent` (80%) that it is filling up.
Discarding items to make room would be a silent, unrecoverable loss of somebody's diamonds.
**Any future code touching the bank must preserve this invariant.**
```
taxesCheckIntervalTicks=100  taxesScanRadius=12  taxesMinValueTrigger=24  taxesHaulValueCap=40
taxesCooldownTicks=6000  taxesCollectIntervalTicks=20  taxesArriveTicks=60  taxesLeaveTicks=60
taxesGiveUpSweeps=8  taxesEnderChestAfterSweeps=3  taxesChatRadius=24.0
taxesDefaultItemValue=1  taxesItemValues=[...]  taxesBankCapacityStacks=512  taxesBankWarnAtPercent=80
Tag:  data/witchmod/tags/item/valuables.json
Text: data/witchmod/text/taxman.json
Art:  assets/witchmod/textures/entity/tax_man.png  ⏳ PENDING (64x64 player skin; renders as the
      missing-texture checker until supplied)
```

### Sticky — Honey Bottle  ✅ REFINED
Everything you're holding is stuck to you. You can't drop items and you can't take your armour off.
**Containers are deliberately untouched** — chests, furnaces, barrels all work exactly as normal, so it's an
inconvenience rather than a lockout.

**Armour is RESTORED, not locked.** Vanilla gates removal inside `ArmorSlot.mayPickup`, which checks for
Curse of Binding and has no hook. Actually enchanting the victim's gear was considered and **rejected**: if
the curse ever ended abnormally they'd be left with permanently bound armour — an unrecoverable mess in
exchange for a temporary joke. Instead `LivingEquipmentChangeEvent` catches the removal and puts it back.
**⚠ The restore only fires if the removed piece can actually be FOUND** (on the cursor, or in the inventory
where a shift-click put it). That one condition is what keeps it safe: armour that BROKE has gone nowhere,
so nothing is found and nothing is restored — otherwise this would quietly grant infinite-durability armour.
Death is guarded separately (`isAlive`/`isDeadOrDying`), so **death drops behave normally**.
**Swaps count as removal too** — otherwise swapping in a leather cap would trivially pop the diamond helmet
off. The piece you tried to put on goes back to your cursor rather than being eaten.

**Dropping is blocked at both ends:** the drop KEY client-side (the primary mechanism — the stack never
leaves its slot), and `ItemTossEvent` server-side as the authoritative backstop.
**⚠⚠ CANCELLING `ItemTossEvent` DELETES THE ITEM ON ITS OWN.** NeoForge's `CommonHooks.onPlayerTossEvent`
has already pulled the stack out of the inventory before the event fires, and cancelling only skips spawning
the `ItemEntity` — nothing puts it back. (Older Forge DID re-add it; 21.1 does not, and assuming otherwise
destroyed items in play.) The handler therefore hands it back explicitly with `placeItemBackInInventory`,
which cannot void: with no room it drops the stack instead. **Losing the curse for one item beats deleting
somebody's netherite** — any future code cancelling a toss must preserve that.

**Feedback:** a honey-block squelch + slime hit, and a few `FALLING_HONEY`/`ITEM_SLIME` drips, on both
triggers. Rate-limited to one every 12 ticks, because holding Q or clicking repeatedly at a helmet is
exactly what a frustrated victim does — one squelch reads as the curse resisting, twenty a second reads as
a broken mod.
**Item corrected to Honey Bottle** (the prototype was on Honey Block, and did something unrelated —
vacuuming up nearby items). Note §11's soft flag: Honey Bottle = Sticky, Honeycomb = a modifier; distinct.
```
stickyBlocksDropping=true  stickyBlocksArmourRemoval=true
```

### Backseat Driver — Saddle  ✅ REFINED
While riding ANYTHING (horse/pig/boat/minecart/modded), an AI can seize the wheel: rider control is cut
dead and it makes FAST for the stupidest thing in range, careening around wandering if nothing qualifies.
- **Hazard priority (type beats proximity; nearest-first only within a type):**
  `LIT TNT > LEDGE > LAVA > HOSTILE MOB > CACTUS > WATER`. Encoded as the `Hazard` enum's declaration
  order in `CurseBackseatDriver` — reorder the enum to reorder the priority, nothing else to change.
  TNT and hostiles are entity scans; ledge/lava/cactus/water are a coarse block-grid scan.
- **Ramping chance:** grows the longer you ride without a takeover, clamped to a ceiling. Spotting a
  hazard nearby **immediately raises that ceiling**, so takeovers cluster around danger.
- **Ends instantly on dismount**, but bailing early gives a SHORTER cooldown than sitting through it —
  escaping only brings the next one sooner.
- **The mount moves ITSELF — never shoved with forced velocity.** (First attempt set `setDeltaMovement`
  every tick; it slid to its destination and hovered over hazards because that bypasses the mount's own
  movement, gravity accumulation and collision.) A ridden mount takes its facing from the RIDER's yaw and
  its throttle from the rider's forward input (`AbstractHorse.getRiddenRotation/getRiddenInput`), so the
  hijack **steers the rider** client-side off the synced `BACKSEAT_DRIVE_YAW` (eased, max 8°/tick) and holds
  full throttle — the animal then walks/gallops there under its own code, with real gait, gravity, step-up
  and collision. Speed comes from a genuine Speed effect on the mount, not teleporting.
- Mounts that are NOT rider-steered (a pig without a carrot on a stick — `getControllingPassenger()` returns
  null, so they ignore rider input entirely) are instead sent via their own `navigation.moveTo`, so they
  also genuinely walk there.
```
backseatEpisodeSeconds=10  backseatCooldownSeconds=60  backseatEarlyExitCooldownSeconds=20
backseatChanceGrowthPerSecond=1  backseatChanceCapPercent=25  backseatHazardChanceCapPercent=70
backseatHazardScanRadius=12  backseatSpeedBoostLevel=2  backseatNavSpeedMultiplier=1.6
```

### Clumsy — Egg  ✅ REFINED
You keep getting it slightly wrong: a block you place comes out facing the wrong way, lands one over from
where you aimed, or turns out to be a different block off your hotbar.

**The chance RAMPS.** It starts at `BASE` (1%) and climbs `PER_BLOCK` (1.5%) with every block placed cleanly,
up to `MAX` (31%) — then resets the instant something goes wrong. A long uninterrupted build gets steadily
more precarious rather than being a flat per-block tax; the ramp maxes out at 20 clean blocks.

**Which mistake depends on the block.** A block with an orientation (stairs, door, log, furnace — detected
by having `HORIZONTAL_FACING`/`FACING`/`AXIS`/`ROTATION_16`) rolls **60% orientation / 32% location / 8%
wrong-block**; a plain cube can only go **65% location / 35% wrong-block**. If the chosen slip can't apply
(nothing else placeable in the hotbar, nowhere to misplace it) it falls back to wrong-location, so a
triggered slip is never wasted.

**Runs off `BlockEvent.EntityPlaceEvent` and mutates the RESULT** rather than intercepting placement — vanilla
places the block and does all the item accounting, then this turns/moves/swaps it. That's the safe choice:
- **orientation** overwrites the state in place with a random `rotate()` (retried until it actually differs,
  since some blocks are symmetric under some turns) — same block, same item, no accounting at all;
- **location** removes it (no drop) and re-places at a valid empty neighbour;
- **wrong-block** removes it, **refunds** the block you meant to place, **consumes** a different hotbar block,
  and places that instead — counts move exactly as a real fumble would, nothing created or destroyed.
Discovered on the first slip.
```
clumsyBaseChancePercent=1.0  clumsyPerBlockChancePercent=1.5  clumsyMaxChancePercent=31.0
clumsyOrientalOrientationPercent=60  clumsyOrientalLocationPercent=32  clumsyOrientalWrongBlockPercent=8
clumsyPlainLocationPercent=65  clumsyPlainWrongBlockPercent=35
```

### Oversharer — Empty Map  ✅ REFINED
Every `INTERVAL_MIN`..`MAX` the victim blurts a piece of personal info into server chat — sent as
`chat.type.text`, so it appears exactly as if THEY typed it — wrapped in a goofy line from a writable list
rather than stated flatly. Its point is to make a distant player **trackable**.

**11 leak categories, each a keyed list in `data/witchmod/text/oversharer.json`** (`/reload`-able): `coords`,
`y`, `biome`, `spawn` (bed, or world spawn), `held`, `armour`, `health`, `facing` (compass direction —
useful for predicting a target's movement), `dimension`, `standing` (block underfoot), `xp`. Each line has a
`{value}` placeholder the curse fills in (`{player}` also supported). Beyond the spec's list I added facing,
dimension, health, standing-block and xp — facing/dimension/health being the genuinely useful tracking intel.

A category is only picked if the file HAS lines for it AND a value can be computed, so a leak is disabled
just by emptying its list, and new categories can be added for any key the curse already computes. Discovered
on the first overshare.
```
oversharerIntervalMinTicks=1800  oversharerIntervalMaxTicks=6000   // 1.5min .. 5min, jittered
List: data/witchmod/text/oversharer.json  { "coords":[..], "biome":[..], ... }  ✅ 11 categories supplied
```

### Broken Bonds — Lead  ✅ REFINED
**RENAMED from the prototype's "Sick Of You"** — registry id `sick_of_you` → `broken_bonds`, since display
names derive from the id path (`DiscoveryManager.titleCase`), so the id IS the visible name. Same situation
as Unhygienic and Neutral Aggression; any saved instance of the old id drops as unknown (harmless, never
shipped). The prototype was also unrelated (it puffed angry-villager particles at nearby *villagers*) and was
replaced wholesale.

Your animals are quietly deciding they've had enough of you. Every owned tamed mob nearby carries a hidden
**hate meter**, and when it maxes out the animal untames on the spot, throws you off if you were riding it,
and storms a good distance away — **keeping all its other NBT** (name tag, collar dye, everything but the
ownership itself), so what walks off is recognisably the pet you lost.

**⚠ Covers BOTH taming systems.** Wolves/cats/parrots are `TamableAnimal`; horses/donkeys/mules/llamas are
`AbstractHorse` and owned through an entirely separate mechanism. The scan filters on `Animal` +
`OwnableEntity` (the interface both implement, exposing `getOwnerUUID()`), and the untame is split —
`setTame(false,false)` for tamables, `setTamed(false)` for horses. Filtering on `TamableAnimal` alone (the
first attempt) silently missed every horse.

**The meter is proximity + interaction, with decay and jitter:**
- **Proximity** builds it, scaled by closeness (`PROXIMITY_GAIN × (1 − dist/radius)`) — right next to you is
  full rate, across the radius barely registers. Deliberately slow.
- **Following** (standing, not sitting — i.e. trailing you around) adds `FOLLOW_GAIN` on top.
- **Riding** adds `RIDE_GAIN`, the fastest way to lose a friend.
- Every change carries ±`RANDOMNESS` jitter so the exact snap moment isn't predictable.
- Any tracked pet NOT in range this sweep **decays** at `DECAY` (similar to the build rate), forgotten at 0 —
  so leaving a pet alone genuinely calms it.

**The snap:** `setTame(false, false)` + `setOwnerUUID(null)` (no taming side-effects, ownership only), rider
ejected, then a time-limited `BrokenBondsFleeGoal` at priority 0 walks it away from where it happened for
`FLEE_TICKS`. Discovered on the first snap. Angry-villager particles wisp off a pet once its meter passes
half, as a hint something's brewing.
```
brokenBondsRadius=16.0  brokenBondsCheckIntervalTicks=20  brokenBondsLimit=100.0
brokenBondsProximityGain=2.0  brokenBondsFollowGain=1.5  brokenBondsRideGain=4.0
brokenBondsDecay=2.0  brokenBondsRandomness=0.3
brokenBondsFleeTicks=200  brokenBondsFleeDistance=12.0  brokenBondsFleeSpeed=1.3
```

### Insomniac — Phantom Membrane  ✅ REFINED
You can't sleep. Trying to get into bed just doesn't work — you get one of a few silly excuses from a
writable list (shown above the hotbar, not chat) instead.

**Hooks `CanPlayerSleepEvent`, and only overrides when vanilla would ACTUALLY have let you sleep** (its own
problem is null). So a daytime bed, monsters nearby or an obstructed bed still show vanilla's normal reason
rather than a curse line — the curse only steals the one case where you'd otherwise have slept. It blocks
with `BedSleepingProblem.OTHER_PROBLEM`, which carries no vanilla message, so the curse's line stands alone.
**Because you never actually sleep, vanilla's own phantom "time since rest" counter keeps climbing** exactly
as for anyone who stayed up — no extra bookkeeping needed. Discovered on the first denied bedtime.
```
List: data/witchmod/text/insomniac.json  (plain JSON array of strings, /reload-able)  ✅ 12 lines supplied
```

### Flat Footed — Goat Horn  ✅ REFINED
**RENAMED from the prototype's "Loud"** — registry id `loud` → `flat_footed`, since display names derive from
the id path (`DiscoveryManager.titleCase`), so the id IS the visible name. The prototype was unrelated (it
blared a `RAID_HORN` on a timer) and was replaced. Any saved `loud` instance drops as unknown; harmless.

Your footsteps are deafening, so you're trivially trackable — even sneaking, which is only *somewhat*
quieter, never silent.

**Amplified real footsteps, not an ambient loop.** The curse tracks `walkDist` and, every `STEP_DISTANCE`
(1.8) blocks on the ground, plays the step sound of the block you're actually standing on at `VOLUME` (3.0,
vs vanilla's ~0.15) — so it's your genuine footstep, just enormous, which reads as *you* rather than a
generic noise. Sneaking multiplies volume by `SNEAK_VOLUME_MULT` (0.45).
**Camera shudder:** each footfall sets a synced `FLAT_FOOTED_SHAKE_END` tick on every OTHER player within
`SHAKE_RADIUS`, read in `ComputeCameraAngles` (shared with Heavyweight's shake via one `applyShake` helper),
a slight decaying jolt on the camera angles only. **Mob hearing:** hostiles within `DETECTION_RADIUS` get a
transient `+DETECTION_BONUS` FOLLOW_RANGE so they pick you up from further off — scanned on a wider ring than
it boosts and stripped from mobs in the outer band, so the transient modifier doesn't leak onto every mob
that ever passed by. Discovered on the first thunderous step.
```
flatFootedStepDistance=1.8  flatFootedVolume=3.0  flatFootedSneakVolumeMultiplier=0.45
flatFootedShakeRadius=8.0  flatFootedShakeStrength=0.6  flatFootedShakeTicks=5
flatFootedDetectionBonus=8.0  flatFootedDetectionRadius=24.0
```

### Wonky — Feather  ✅ REFINED
**RENAMED from the prototype's "Pidgeon Toed"** — id `pidgeon_toed` → `wonky` (display names derive from the
id path, so the id IS the visible name). Prototype was a flat -25% movement-speed modifier, replaced.

You can't walk in a straight line. A subtle sideways wander is added to your STRAFE (`leftImpulse`) whenever
you're actually moving, from a slow sine so it's a lazy weave rather than jitter — quietly disorienting, not
an obvious shove. Sprinting multiplies it (`SPRINT_MULTIPLIER`), since you commit harder to a bad line.

Client-side off the synced `WONKY_ACTIVE` flag (player movement is client-authoritative, so a server nudge
would just be corrected away). Only applied when there's real movement input, so standing still and pure
camera aim are untouched. Discovered on apply.
```
wonkyDriftStrength=0.18  wonkySprintMultiplier=2.2  wonkyPeriodTicks=34.0
```

### Stick Drift — Fishing Rod  ✅ REFINED
A joke about controllers. When the curse lands it rolls ONCE a mode (camera or movement) and a fixed
direction to drift toward, and both stay constant for the whole duration — a worn stick doesn't develop a
new fault mid-session.

**It drifts in EPISODES**, random intensity for random length, with the controller-drift twist:
**intensity × duration is held roughly constant** (`DURATION_PRODUCT ÷ intensity`, clamped) — a hard drift is
brief, a gentle one drags on. The direction is always the one rolled at apply.

- **Movement mode** adds a phantom stick input (`cos/​sin(angle)` on strafe/forward) at `MOVE_SCALE`, so your
  feet slide a consistent way. Unlike Wonky's weave this is a *constant* pull while the episode runs — the
  stick is stuck, not wandering.
- **Camera mode** rotates your ACTUAL yaw/pitch each tick by `CAMERA_SCALE` degrees — really moving your aim,
  not just the render view (a shake wouldn't drift where you're pointing), exactly like a drifting stick.

Server owns only the schedule (mode, angle, current episode intensity/end — all synced); the drift is applied
client-side since both movement and aim are client-authoritative. Discovered on apply.
```
stickDriftCameraChancePercent=50  stickDriftGapMinTicks=40  stickDriftGapMaxTicks=200
stickDriftIntensityMin=0.2  stickDriftIntensityMax=1.0  stickDriftDurationProduct=60.0
stickDriftDurationMinTicks=20  stickDriftDurationMaxTicks=300
stickDriftMoveScale=0.7  stickDriftCameraScale=2.0
```

### Basement Dweller — Grass Block  ✅ REFINED
Daylight doesn't agree with you. Standing in **direct sunlight** burns you — a hat takes the edge off but
never fully saves you (`HELMET_MULT`, any head slot). "Direct sun" = daytime, clear sky straight up, and not
raining on that spot.

Custom `witchmod:sunburn` damage type — **fatal on every difficulty, bypasses armour, no knockback/no impact**
(a slow cook, not a shove; armour shouldn't stop sunburn, so the only mitigation is the hat multiplier).
Death: *"%s could not handle the sun"*. Each burn hisses (GENERIC_BURN) so the source is obvious. **Discovered on the first burn.**
Item corrected to Grass Block during Pests' refinement (it was on Cobblestone — see Pests).
**The gap between burns RAMPS** (Oliver's call): it starts slow at `basementStartIntervalTicks` (80) and decays
linearly to `basementEndIntervalTicks` (15) over `basementRampTicks` (500 = 25s) of continuous sun, tracked
per-player from the moment you step out; leaving the sun resets it. The hat multiplier still stretches the
current ramped interval. **Subtle heat particles** (SMOKE + occasional SMALL_FLAME) wisp off you while exposed.
See the shared `EnvBurn` helper + the §Combined-curse leeway note under Claustrophobia.
```
basementDamage=1.0  basementStartIntervalTicks=80  basementEndIntervalTicks=15  basementRampTicks=500
basementHelmetIntervalMultiplier=2.5  // hat SLOWS burns, not softens (multiplies the ramped interval)
```

### Claustrophobia — Cobbled Deepslate  ✅ REFINED (NEW — was never prototyped)
The walls are too close. Being shut indoors with **no sky above you** wears at you — the gentler mirror of
Basement Dweller: less damage, and NO hat mitigation (a helmet does nothing about the ceiling). It bites
whenever you can't see the sky, **night included** — the problem is the roof, not the sun.

Custom `witchmod:cave_dread` damage type — same properties as sunburn (fatal everywhere, bypasses armour, no
knockback/impact). Each bite thumps a faint WARDEN_HEARTBEAT **plus a subtle 'crushing' thud (a quiet ZOMBIE_ATTACK_WOODEN_DOOR)** so
the source is clear and oppressive. Death: *"%s let the walls get too close"*. Discovered on the first bite.
**The interval RAMPS** like Basement Dweller: `claustrophobiaStartIntervalTicks` (70) decaying to
`claustrophobiaEndIntervalTicks` (25) over `claustrophobiaRampTicks` (200 = 10s), per-player, reset on
un-boxing. A **ONE-OFF** ASH+SMOKE puff fires when you first get boxed in (NOT a constant stream).

**⚙ Combined-curse leeway (shared `EnvBurn` helper).** Both damage curses now track a per-player ramp from
condition-entry. While a player carries **BOTH** Basement Dweller AND Claustrophobia, suffering both at once
would be brutal, so `EnvBurn` folds in a leeway, read LIVE off `EffectManager.isActive` so it swaps off the
instant either curse is removed: a `envCombinedGraceTicks` (70 = 3.5s) grace before ANY damage starts on
entering a condition, the gap between hits ×`envCombinedIntervalMultiplier` (1.3), and the decay window
×`envCombinedDecayMultiplier` (1.45).
```
claustrophobiaDamage=0.5  claustrophobiaStartIntervalTicks=70  claustrophobiaEndIntervalTicks=25  claustrophobiaRampTicks=200
envCombinedGraceTicks=70  envCombinedIntervalMultiplier=1.3  envCombinedDecayMultiplier=1.45
```

### Claustrophobia — Cobbled Deepslate (SUPERSEDED — see refined entry above)
Being indoors (no sky access) hurts; milder than Basement Dweller.
```
CLAUSTRO_DAMAGE=0.5  CLAUSTRO_DAMAGE_INTERVAL=40  CLAUSTRO_GRACE=200
```

### Glass Cannon — Glass Block  ✅ REFINED
You hit hard and you break like one. Every hit you take lands for `TAKEN_MULT` (**200%, any source**), and
every MELEE hit you land deals `DEALT_MULT` (**150%**).

**Done on `LivingIncomingDamageEvent`, not via attributes.** The prototype used ATTACK_DAMAGE + ARMOR
modifiers, which only approximate it — an armour cut isn't a clean ×2, and an attack-damage modifier misses
enchantment/crit contributions. Multiplying the damage AMOUNT is exact: taken applies to *any* incoming
source; dealt is restricted to melee precisely by requiring the hit's **direct entity to be the attacker**
(a fired arrow's direct entity is the arrow, so ranged doesn't count).
Runs in its own handler ahead of the mod's per-player damage logic, since it fires for any damaged entity —
not just a cursed player, but a cursed player's melee VICTIM too. Both directions get a glass shatter + `CRIT`
particles for emphasis (`GLASS_BREAK` on you, `GLASS_HIT` on your target).
```
glassCannonDamageTakenMultiplier=2.0  glassCannonDamageDealtMultiplier=1.5
```

### Mansplainer — Written Book (a signed book)  ❌ CUT (Oliver's call)
**REMOVED** — too close to the other chat-based curses (Yap, Oversharer, Echoes' fake chat). Its class
`CurseMansplainer` still exists and is registered, but it is NOT being refined and should be considered
retired; the count drops accordingly. If a slot is ever needed, its Written Book sacrificial item is free.

### Moonwalker — End Stone  ✅ REFINED
You cannot walk **forward**. W does nothing; back, left and right all work normally, so it's a
shuffling-backwards nuisance rather than a full input scramble. Client-side off the synced
`MOONWALKER_ACTIVE` flag (movement is client-authoritative) — the client zeroes any positive
`forwardImpulse` and clears the W key while the curse is set.

**Halved duration** via a new general `Effect.durationMultiplier()` hook (0.5 here), applied centrally in
`EffectManager.apply` so EVERY cast path (table, command, coin, effigy, mirror-backfire) honours it — being
unable to advance is punishing enough that a full 30–60min would be miserable. Discovery is on apply (it's
client-only, so the server can't cleanly catch the first blocked W-press, and pressing W and going nowhere
gives it away instantly anyway).
NOTE the earlier prototype swapped W/S — Oliver flagged that as "not the planned version"; only forward is
blocked now.
```
(no config — durationMultiplier is 0.5 in code; movement handled client-side)
```

### Siren's Call — Heart of the Sea  ✅ REFINED (NEW — was never prototyped)
The sea is calling and staying dry aches. A hidden **longing** meter builds whenever the victim is out of
water and drains fast the moment they're back in it, escalating in three stages:
1. **Ache** (`STAGE1`) — Mining Fatigue, plus a "You yearn for the water..." cue on the action bar.
2. **Heaviness** (`STAGE2`) — Slowness, but only on LAND (in water you're where you want to be).
3. **The march** (`STAGE3`) — movement is HIJACKED and you're walked to the nearest water like you're
   mind-controlled.

The hijack is the same trick Backseat Driver uses: the server finds the nearest water (coarse-sampled scan
within `WATER_SEARCH_RADIUS`), syncs a bearing (`SIREN_PULL_YAW`) + active flag, and the client eases the
victim's yaw onto it and forces forward — client-side because movement is client-authoritative. A small
server-side `PULL_FORCE` velocity tug rides on top so ice or a fall still drifts you seaward. Sits BELOW the
Backseat block so a mounted victim's mount steering still wins.
**⚠ `nearestWater` only targets REACHABLE water:** it skips any water whose block above is solid (sealed
under a platform) and any water within ~2 blocks horizontally, ranking by horizontal distance. (Bug fix:
water trapped under the platform you're standing on gave an unreachable, near-straight-down target, so the
yaw flipped around and you walked in a goofy circle. No qualifying water now = no pull, no spin.)

**Drowned protect their own** (`DROWNED_SOOTHE_RADIUS`): one nearby multiplies the longing gain down
(`DROWNED_GAIN_MULT`) AND is stripped of the victim as a target — the sea's creatures don't harm one it's
claimed. Longing is a transient server-side meter (resets on apply). Discovered on the FIRST ache, not on
apply.
```
sirenCheckIntervalTicks=10  sirenLongingMax=100.0  sirenDryGain=0.4  sirenWaterDrain=4.0
sirenStage1Threshold=35  sirenStage2Threshold=65  sirenStage3Threshold=88
sirenWaterSearchRadius=24  sirenPullForce=0.05
sirenDrownedSootheRadius=12.0  sirenDrownedGainMultiplier=0.25
```

### Loading Screen — Glistering Melon (CUSTOM UI — FULLY FUNCTIONAL) (CUSTOM SOUND)  ✅ REFINED
Bethesda joke. **EVERY** door/trapdoor/fence gate you open covers your entire screen with a fake loading
screen — **no chance roll and no cooldown**. That's deliberate: the curse is completely avoidable (you
choose when to touch a door), so certainty is what gives it teeth.
- **Input is dead** for the duration — movement, jumping, sneaking, attack/use/pick, and mouse-look. Menus
  are deliberately still allowed. Mouse-look is blocked in TWO places because look is applied per FRAME but
  ticks are per 50ms: the real rotation is pinned each tick (so nothing is banked up and applied on
  release) AND `ComputeCameraAngles` pins the render view (so it doesn't visibly jitter between ticks).
- **Duration is random 1.5–5s**, then two ways to make it worse:
  - **Restart** (`RESTART_CHANCE`): on reaching the end, the bar visibly whips back to zero and loads for
    another 1–3s.
  - **Stutter** (`STUTTER_CHANCE`, rolled once a second): the bar freezes in place for 1–3s as if the game
    has hung. The animation keeps playing, which is what sells it.
- **Hard safety cap** `MAX_TOTAL_TICKS`: input is locked, so this guarantees you can never be stuck longer
  than 20s however the rolls land.
- Discovered on trigger.

**The server owns nothing but a signal.** It sets `LOADING_SCREEN_SESSION` to a fresh random long; the
client sees the value CHANGE, seeds its RNG from it, and runs everything itself. This is required, not
stylistic — the length is dynamic (stutters/restarts extend it) and the input lock must release on exactly
the frame the bar completes, so an authoritative server end-tick would drift out of sync with the animation.
Setting the session to `0` (cure/expiry) dismisses a screen in progress.
```
loadingScreenMinTicks=30  loadingScreenMaxTicks=100          // 1.5s .. 5s
loadingScreenRestartChancePercent=50  loadingScreenRestartMinTicks=20  loadingScreenRestartMaxTicks=60
loadingScreenStutterChancePercent=40  loadingScreenStutterMinTicks=20  loadingScreenStutterMaxTicks=60
loadingScreenMaxTotalTicks=400        loadingScreenTipScrollSpeed=6.0   // px/tick; 120px/sec
loadingScreenFrameCount=36  loadingScreenFrameWidth=128  loadingScreenFrameHeight=152
loadingScreenSheetVertical=true  loadingScreenFrameTicks=2
Tips list: assets/witchmod/text/loading_tips.json  (plain JSON array of strings — see note in 13.6)
Music: 4 hold-music tracks, weighted 375/375/240/10 => 37.5% / 37.5% / 24% / 1% (the goofy one, at half
volume). Played CLIENT-SIDE ONLY via the local SoundManager (so nobody else hears it) and stopped the
instant the screen ends, however it ended — start/stop are paired inside LoadingScreenState.finish().
loadingScreenMusic{1,2,3,Goofy}Weight  loadingScreenMusicVolume=1.0  loadingScreenGoofyVolume=0.5
Sounds: witchmod:curse.loading.music_1 / music_2 / music_3 / music_goofy  ✅ SUPPLIED
Art: assets/witchmod/textures/gui/hud/loading_animation.png  ✅ SUPPLIED (36 frames, 128x152, vertical)
```

### Pacing — Tropical Fish (CUSTOM SOUND)  ✅ REFINED
One Piece joke: a "dramatic moment" **time-stop**. Trigger chance RAMPS the longer it's gone without one
(pre-charged high on apply, resets each time), rolled per combat hit; if it maxes out with no hit it fires
on a non-combat tick.
- **The moment** freezes the victim + **everything** within `FREEZE_RADIUS`: all made invulnerable, velocity
  zeroed (`hurtMarked` so the client sees them stop dead), mobs `setNoAi`, players given heavy Slowness AND a
  client-side input lock — so the victim can no longer swing/use/interact mid-moment.
  **⚠ The freeze is RE-ASSERTED every tick and the radius RE-SWEPT every 5 ticks.** A one-shot freeze at the
  moment's start silently misses everything that wanders in over the next 5–30s, and doesn't survive
  knockback or anything else nudging an entity — which read in play as "the AI pause is unreliable and often
  just does not pause involved AI". Non-players are also pinned back to their anchor position each tick, so
  it's a true stop; players are left alone (client-authoritative movement, so pinning only rubber-bands).
  **The freeze IS capped, at `MAX_INVOLVED` = 12** (Oliver's call). The cap is a SAFETY limit, not a
  presentation one: everything frozen is also made invulnerable and pinned every tick, so an uncapped sweep
  set off inside a mob farm could hurt a server badly. 12 is high enough that a normal scene freezes whole —
  the old value of 5 was low enough that the 6th mob onward visibly kept moving through a time-stop.
  **⚠ NO Jump Boost.** It used to apply amplifier 128 labelled "level 129 = no jump", but
  `getJumpBoostPower()` is `0.1 × (amplifier + 1)` — so that was **+12.9 jump power, a launch pad**, which
  fired whenever the freeze caught someone mid-jump ("rare bug where being paused mid jump launches you into
  the air"). The client input lock already blocks jumping, so the effect was redundant as well as harmful.
  Slowness sits at amplifier 10 — already enough to clamp movement speed to zero, and well clear of the byte
  boundaries where extreme amplifiers get unpredictable.
- **Duration is random 5–30s, biased short** (`min + (max-min)·r^LOW_BIAS_EXPONENT`), so most are brief with
  the odd long one.
- **Camera** cuts every `SHOT_TICKS`: the first **2–4 cuts are the victim** (orbit angles, back view), then
  it cuts to nearby frozen entities — each a FRONT-view **face zoom** — shuffled so the spotlight spreads
  around (variety bias toward whoever hasn't been shown) rather than lingering on the victim. Every shot gets
  a small **dutch-angle roll** for drama. The mob-vision shader problem is solved NOT by avoiding mobs but by
  calling `GameRenderer.shutdownEffect()` right after each `setCameraEntity` to a mob — verified that
  `checkEntityPostEffect` only re-runs on the next `setCameraEntity`, so the shader stays off for the shot.
  (Earlier the camera only ever orbited the victim to dodge the shader; now it can feature mobs safely.)
- **Every player caught in the freeze** gets the cinematic too, not just the victim: the synced
  `PACING_FOCUS_ID` points each included client's camera at the victim.
- **Sound (client-side, local SoundManager so it's per-client):** `curse.pacing.theme` plays for the whole
  moment and is cut off the instant it ends; `curse.pacing.click` plays quietly at every camera cut, with a
  1-in-`THE_ONE_PIECE_CHANCE` chance of being replaced by `curse.pacing.theonepiece`.
- Discovered on the first moment.
```
pacingMinSeconds=5  pacingMaxSeconds=30  pacingLowBiasExponent=2.0
pacingFreezeRadius=8.0  pacingMaxInvolved=5  pacingShotTicks=14
pacingTheOnePieceChance=250  pacingClickVolume=1.0  pacingThemeVolume=1.0
Sounds: witchmod:curse.pacing.theme ✅ / .click ✅ / .theonepiece ✅  (all OGG, in place)
```

### Trumpet — Cookie (CUSTOM SOUND)  ✅ REFINED
A cartoon fat-trumpet scores your every step — the old gag of a large character entering to a trumpet. A
looping "walking in" trumpet plays whenever you MOVE, cuts out the instant you stop, speeds up slightly
while you sprint, and goes completely silent while you crouch. In play it gives your position away to
anyone in earshot unless you move quietly (crouch).

**All the audio is CLIENT-side, and that's what makes it work.** The server owns only a synced
`TRUMPET_ACTIVE` flag (synced to TRACKERS like Ugly, so everyone nearby hears it — not just the victim); the
loop is a looping tickable `SoundInstance` (`client/TrumpetSoundInstance`) each nearby client spins up at the
cursed player, managed by `client/TrumpetSoundManager`. This is required, not stylistic:
- **Instant stop.** A fire-and-forget `level.playSound` can't be stopped mid-blare — stop moving 1s into a
  5s toot and it keeps going for 4 more. A tickable instance `stop()`s itself the moment you stop/crouch.
- **Seamless loop.** The engine sets OpenAL `AL_LOOPING` on the fully-buffered (`"stream": false`) sound, so
  it loops perfectly at the buffer boundary — the file just has to be authored to loop (it is, ~4.94s).
- **Sprint speed-up with no hiccup.** Verified in `SoundEngine.tick`: `getPitch()` is read and re-applied to
  the live channel EVERY tick, so bumping pitch to `trumpetSprintPitch` while sprinting speeds the loop up
  without restarting it (pitch = playback speed; clamped by the engine to [0.5, 2.0]).
- **"Is walking"** = the entity's own `walkAnimation.speed()` (synced/computed for remote players too, since
  it drives their leg animation) above `trumpetWalkThreshold`. Crouch = `isCrouching()`, sprint =
  `isSprinting()` — both synced, so remote observers judge it correctly.

**⚠ The OGG MUST be MONO.** OpenAL only positions/attenuates mono sounds; a stereo file plays globally at
constant volume, which kills both the distance falloff and the whole "gives away your position"
directionality. The supplied `trumpettheme.ogg` is **stereo (48kHz)** — installed as-is so it's testable now,
but it plays non-directionally until re-exported to mono. `trumpetVolume` doubles as the audible RANGE
(linear attenuation ≈ volume×16 blocks, so 1.0 ≈ 16 blocks) once it's mono. Discovery is on apply (audio is
client-authoritative; a trumpet blaring on your first step gives itself away regardless).
NOTE the spec's separate `stop_sting` is dropped — the design is an instant cut, no flourish.
```
trumpetVolume=1.0   trumpetSprintPitch=1.15   trumpetWalkThreshold=0.03
Sound: witchmod:curse.trumpet.walk_loop  ✅ SUPPLIED (⚠ stereo — needs a MONO re-export for positional audio)
```

### Heavy Handed — Flint  ✅ REFINED
**RENAMED from "Uncareful" on Oliver's call** — registry id `uncareful` → `heavy_handed`, since display
names derive from the id path (`DiscoveryManager.titleCase`), so the id IS the visible name (same as
Unhygienic/Neutral Aggression/etc.). Any saved `uncareful` instance drops as unknown; harmless.

You're rough with your gear: tools and armour lose durability **`DURABILITY_MULT` (4x)** as fast. (Oliver
first thought this was an alias for Clumsy, then kept it as its own curse — it's a distinct mechanic. Item
stays **Flint**, which nothing else uses.)

**No event lets you modify a durability hit's amount, so this WATCHES instead:** each tick it records the
damage value of the six slots that actually wear — both hands + the four armour pieces — and when one rises
(the item was just used) it re-applies the shortfall as EXTRA wear via `ItemStack.hurtAndBreak`. Going
through `hurtAndBreak` means Unbreaking still mitigates it and a piece that crosses its limit breaks
properly. **The recorded value is taken AFTER the top-up**, so the extra isn't mistaken for fresh damage the
next tick (which would compound it). Idle inventory items are never touched — they don't wear anyway.
Discovered on the first extra wear.
```
heavyHandedDurabilityMultiplier=4.0
```

---

## 6. BLESSINGS (45)

### Fortune — Diamond  ✅ REFINED
Ore-tag blocks (`Tags.Blocks.ORES`, so modded ores count) give **0..max EXTRA drops** on top of the normal
roll, on `BlockDropsEvent` (which hands over the already-Fortune-enchanted drop list — so it's **additive,
never multiplicative**, and stacks cleanly with enchantment Fortune). The extra count is a **triangular roll
peaked at `fortuneExtraMode` (1)**, so most breaks give one bonus. It grows the resource stack in place and
**skips a silk-touched ore-BLOCK drop** so it can't be duplicated. Discovers on the first extra drop.
```
fortuneExtraMin=0  fortuneExtraMode=1  fortuneExtraMax=3   (additive AFTER enchant Fortune; ore-tag blocks only)
```

### Peace — Poppy  ✅ REFINED
The opposite of Popularity. **Fewer spawns:** most hostile NATURAL/CHUNK spawns within `peaceRadius` are
cancelled via `FinalizeSpawnEvent` (monsters only; `peaceSpawnRateMultiplier` = fraction allowed).
**Shrunken detection:** a hostile only notices you at `peaceDetectionMultiplier` of its normal follow range,
enforced each sweep by dropping too-distant aggro. Discovers when a hostile inside its normal aggro range,
in line of sight, conspicuously isn't hunting you.
```
peaceSpawnRateMultiplier=0.3  peaceRadius=48  peaceDetectionMultiplier=0.4  peaceCheckIntervalTicks=20
```

### Luck — Rabbit's Foot  ✅ REFINED
Greatly amplified vanilla LUCK attribute (`ADD_VALUE` modifier, transient + self-healed on reload), nudging
loot-table rolls your way. Discovers the first time it could matter: reeling in a fish (`ItemFishedEvent`) or
opening a loot-tabled chest (detected by its still-pending `getLootTable()` at right-click).
```
luckAttributeBonus=5.0
```

### Fullness — Bread  ✅ REFINED  (renamed from "Full"; id `full` → `fullness`)
Hunger — and the hidden saturation behind it — drains at `fullnessDrainRate` (20%) of normal. No event
scales hunger drain, so it WATCHES the outputs on `PlayerTickEvent.Post` (after vanilla's `FoodData.tick`,
which drops one thing per tick): saturation (a float) refunded 80% of any natural drop; food (whole numbers)
undone and banked, releasing one real point only once `1/rate` have accrued — exact 20%. Eating (positive
deltas) untouched. Discovers on the first slowed drain.
```
fullnessDrainRate=0.2
```

### Army — Shield  ✅ REFINED
Nearby hostile MONSTER mobs (never `NeutralMob`s) go neutral toward you — acquisition is **vetoed at the
source** via `LivingChangeTargetEvent` (their goals can't lock on), with their damage cancelled as a
backstop, so they genuinely can't hurt you (replaced a per-tick target-clear that flickered). Being hit by a
GENUINE aggressor (a player, a golem — not a pacified hostile, so the swarm never cascades onto its own)
rallies every nearby hostile onto it for `armyDefendDurationTicks`, re-aimed each sweep; `armySameTypeExcluded`
spares the attacker's own kind. Discovers when a hostile in normal range + LoS isn't attacking you.
```
armyRadius=24  armyDefendDurationTicks=600  armyCheckIntervalTicks=5  armySameTypeExcluded=true
```

### Reflect — Turtle Shell  ✅ REFINED
Projectiles are caught on `ProjectileImpactEvent` (before damage) and sent **precisely back at the shooter,
`reflectVelocityMultiplier` faster** — no homing, so it's dodgeable. The impact is cancelled (no damage,
keeps flying), the projectile is **re-ownered to you** (can't re-hit you, CAN hurt the shooter) and nudged
clear of your hitbox. Your own and owner-less shots are ignored. Discovers on the first reflect.
```
reflectVelocityMultiplier=1.5  reflectInaccuracy=0.0
```

### Soul Bond — Gold Block  ✅ REFINED  (item Totem of Undying → Enchanted Golden Apple → Gold Block, 2026-09-06)
The nearest LivingEntity within `soulBondRadius` (mob/pet/player; armour stands + spectators excluded) gets a
custom **Soul Bound** MobEffect (own gold icon, `visible=false` so no vanilla swirls — it has its own constant
golden particles) and takes `soulBondDamageShare` (40%) of every hit YOU take (you eat 60%); a golden trail
flicks to whoever paid. **It STICKS** — only re-picks once the current bound leaves `soulBondRadius` entirely,
so it commits to a victim rather than snapping to whoever hit you last (not a worse Thorns). In a 1v1 it
latches onto your opponent; a trailing pet becomes the bound. Custom `witchmod:soul_bond` damage type
(bypasses armour, no knockback), attributed to the caster, **never re-shared** (no ping-pong). Old "counts as
a curse on them" idea dropped.
```
soulBondRadius=16  soulBondDamageShare=0.4  soulBondRebindIntervalTicks=20  soulBondParticleIntervalTicks=4
Effect: witchmod:soul_bound (icon supplied) · Damage type: witchmod:soul_bond (bypasses_armor/no_knockback/no_impact)
```

### Bodyguard — Bone (CUSTOM ENTITY)  ✅ REFINED
A comical hired-muscle **skeleton in black-dyed leather and sunglasses** (`SunglassesLayer` head box +
placeholder texture; body baked into a `HumanoidModel` with proper held-item arm posing) bound to you as its
**anchor**. Three states, narrated to nearby players:
- **WARNING** — a non-anchor player OR villager within `bodyguardWarningRadius` gets told, by name, to leave
  (random multi-line dialogue trees).
- **AGGRESSION** — they crowd within `bodyguardAggressionRadius`: low-damage warning hits (villagers are
  SHOVED, not hurt, so the iron golem isn't provoked). Lingering as an intruder for `bodyguardPatienceTicks`
  AND after ≥`bodyguardWarningsBeforeAttack` spoken warnings → it **draws an iron sword** and commits. ⚠ The
  patience clock builds while the SAME intruder is present at ALL (WARNING or AGGRESSION) and resets only when
  they leave or a new one takes over — NOT on a WARNING⇄AGGRESSION flip. (Bug fix: it used to reset the moment
  a warning-shove bumped them out of AGGRESSION range, so it only ever attacked while you actively crowded it.)
- **ATTACKING** — anything that strikes the anchor OR the bodyguard is chased at full `bodyguardDamage` until
  dead or dragged beyond `bodyguardLeashRange`, then it stands down.

It's a `PathfinderMob` (not `Monster`/`Enemy` — golems ignore it, no sun burn), all movement manual so it can
**never target the anchor** and never retaliates against them. **Teleports to the anchor** wolf-style beyond
`bodyguardTeleportDistance`. **Its death does NOT break the blessing** — a replacement is hired 6 min later
(`bodyguardRespawnTicks`; `fell`/`respawn` lines in bodyguard.json). Chat is local (`bodyguardChatRadius`,
per-player) from `bodyguard.json` (state-keyed trees, `{player}` = intruder). **No duplication:** transient
(`shouldBeSaved()`→false) + a self-healing `CANONICAL` anchor→bodyguard registry (older copies discard
themselves) + a dedupe scan on summon.
```
bodyguardHealth=60  bodyguardDamage=6  bodyguardArmor=12  bodyguardSpeed=0.34  bodyguardFollowDistance=4
bodyguardTeleportDistance=12  bodyguardWarningRadius=8  bodyguardAggressionRadius=4  bodyguardLeashRange=32
bodyguardWarningHitDamage=1.0  bodyguardWarningHitIntervalTicks=30  bodyguardPatienceTicks=100
bodyguardWarningsBeforeAttack=2  bodyguardChatRadius=24  bodyguardDialogueCooldownTicks=100  bodyguardDialogueLineGapTicks=30
List: data/witchmod/text/bodyguard.json  ·  Sunglasses: assets/witchmod/textures/entity/bodyguard/sunglasses.png (see §13.7)
```

### Payday — Gold Ingot  ✅ REFINED  (renamed from "Tax Man" the blessing; id `tax_man` → `payday`)
The Tax Man CHARACTER (still named "Tax Man" in-world) turns up owing YOU. Reuses `TaxManEntity` in a
delivery mode: he waits for a safe, idle moment (no `Enemy` within `taxmanSafeRadius`; you still for
`taxmanIdleTicks`), walks up, drops the goods in front of you, and leaves — one-time use. Hands over the whole
`TaxBank` (from the Audit curse) or, if empty, a random gift (1–10 emeralds / 1–8 gold / 0–3 diamonds biased
to 0). Payout computed on the entity at hand-off so the bank only drains on a real delivery; the delivery Tax
Man is transient and the blessing tells "paid & left" from "vanished before arriving" (relog → re-send).
```
taxmanSafeRadius=16  taxmanIdleTicks=60
taxmanGiftEmeraldsMax=10  taxmanGiftGoldMax=8  taxmanGiftDiamondsMax=3
```

### Hype Man — Any Music Disc (tag: minecraft:music_discs — EXPLICIT TAG EXCEPTION)  ✅ REFINED
Your every move makes nearby players gush about you in chat, by name, sometimes unhinged. Purely comedic, no
mechanical effect — hence cheap. Triggers: **combat** (`AttackEntityEvent`), **pickup**
(`ItemEntityPickupEvent.Post`), **loot** (opening a `ChestMenu`), **building** (`BlockEvent.EntityPlaceEvent`,
complimenting your builds), and **nearby** (ambient "sighting" tick). All route through
`BlessingHypeMan.praise`, which shares ONE cooldown (`hypemanCooldownTicks`) so a busy moment can't wall chat,
rolls `hypemanChance`, and speaks the line in the mouth of a **random nearby player**, broadcast to everyone
within `hypemanRadius`. Lines from the writable `data/witchmod/text/hypeman.json`, keyed by trigger, `{player}`
→ your username.

**⚠ The no-player fallback is ONLY for the "nearby" sighting (Oliver's call).** The specific-action praises
(combat/pickup/loot/building) require a genuine nearby player and stay silent when you're alone — only the
sighting carries on with nobody around, attributed to a made-up name from the shared
`data/witchmod/text/usernames.json` (via `Usernames`, also intended for the Chat blessing's fake chatters).

**Tag exception (Rule 9):** implemented generically via a new `Effect.sacrificialTag()` hook that
`SacrificialItems.findEffect` checks only as a fallback after exact-item matching — so ANY music disc selects
Hype Man at the Table (and `sacrificialItem()` still returns Music Disc 13/Cat for the icon). Party Time
(candles) can use the same hook when refined.
```
hypemanRadius=16  hypemanCooldownTicks=160  hypemanChance=0.6  hypemanAmbientIntervalTicks=120
List: data/witchmod/text/hypeman.json  (keys: combat, pickup, loot, building, nearby)
Names: data/witchmod/text/usernames.json  (shared fallback name pool; also for the Chat blessing)
```

### Workman — Obsidian  ✅ REFINED  (id `tools_dont_use_durability` → `workman`; item Netherite Scrap → Obsidian, no collision)
Tools AND armour take no durability for the duration. **Implemented by WATCHING and healing, not by stamping
items `Unbreakable`:** each tick the six wearing slots (both hands + four armour pieces) are checked and any
wear one just took is put straight back (the exact inverse of Heavy Handed). Chosen for safety — it modifies
no item component, so an abnormal end can never leave permanently-unbreakable gear (same reasoning as Sticky
restoring armour rather than binding it), and healing every tick means nothing ever accumulates wear or hits
zero to break. Idle inventory items are ignored (they don't wear). No config values.
```
(no config — watch-and-heal the 6 equipment slots)
```

### Pickpocket — String  ✅ REFINED
Light fingers. Standing right up against another player (`pickpocketRadius`, ~1.5) has a `pickpocketBaseChance`
per attempt (`pickpocketCheckIntervalTicks`) to quietly lift a random item into your inventory — **greatly
higher when you're BEHIND them** (`pickpocketBehindMultiplier`, computed from the victim's look vs the
direction to you). It **prefers their backpack over their hotbar** — a WEIGHTED pick (`pickpocketHotbarWeight`
vs 1.0), not a hard exclusion, so what they're holding is rarely taken. Only the 36 main+hotbar slots are
fair game (armour/offhand left alone). A quiet thief-only pickup sound plays on a successful lift. **Fails if
YOUR inventory is full** (`add` fits nothing → nothing is moved, no item ever lost). Creative/spectator
victims are skipped.
```
pickpocketRadius=1.5  pickpocketCheckIntervalTicks=100  pickpocketBaseChance=0.05
pickpocketBehindMultiplier=4.0  pickpocketHotbarWeight=0.1
```

### Windfall — Wind Charge  ✅ REFINED
An ambient "ooo, what's that" moment: as you go about your day a **mostly**-useful item
(`windfallBeneficialChance`, occasionally junk) comes drifting THROUGH your view — spawned 4–6 blocks off to
one side, aimed a few blocks ahead of you at ~eye height, and pushed on a steady breeze that crosses your
path so it **slides across in front of you** rather than dropping on your head. `setNoGravity(true)` so it
glides horizontally instead of falling; a short pickup grace so it can't be hoovered up instantly; a small
cloud puff and **no sound cue** (Oliver's call).
- **Passing, not a handout:** it never spawns on top of you, and before spawning it requires clear,
  fluid-free air at the spawn point and along the first ~3 blocks of drift — otherwise it skips and retries,
  which is what stopped the earlier "weird interaction over a covered pool" (items phasing through a
  platform).
- **Instant cleanup:** every tick, any drifting item you've walked more than `windfallWalkAwayDistance` from,
  or that's older than `windfallItemLifespanTicks`, is `discard()`ed immediately (not left as ground
  clutter); items still airborne when the blessing ends are cleared too. Its vanilla `lifespan` is set to max
  so removal is solely mod-managed.
- **Frequency:** a random per-player countdown, and it happens whether or not you're moving — but the timer
  advances only every OTHER tick while standing still, so stationary windfalls come at HALF the walking rate.
Pools are curated in-code (beneficial vs junk) rather than a loot table, for now.
```
windfallIntervalMinTicks=340  windfallIntervalMaxTicks=3000   // 17s .. 2.5min
windfallBeneficialChance=0.85  windfallItemLifespanTicks=300  windfallWalkAwayDistance=12.0
```

### Immortality — Nether Star  ✅ REFINED  (item Ghast Tear → Nether Star, Oliver's call)
Death doesn't take you — a lethal blow instead dissolves you into a hovering cloud of gold→white particles
that slowly gathers itself back OUT OF THE AIR before you pop back into existence right where you fell —
**the vanilla respawn screen never appears** (cancelling `LivingDeathEvent` → `BlessingImmortality.beginRecovery`
is what dodges it). Repeatable with a **linear** growing rebuild (`base + increment×prior deaths` → **8s /
18s / 28s**), and the blessing **breaks after `immortalityMaxUses` (3)** deaths.
- **The rebuild** runs from `onTick`: rooted in place (position pinned to the death spot + client
  movement-input lock via `ClientCurseHandler.isImmortalityRebuilding`), health knit up from 1→max across the
  recovery, air kept full, fire cleared.
- **Particle body, not the model.** While rebuilding, the player's MODEL is hidden for everyone
  (`RenderLivingEvent.Pre` cancelled for any player mid-rebuild — the recovery flags are synced to trackers,
  so onlookers see it too) and a shimmering gold→white particle SILHOUETTE fills their hitbox instead. Motes
  also stream INWARD from ~1.6–3.6 blocks out (spawned with a velocity aimed at the body centre, vanilla's
  `count==0` = "spawn one particle with this velocity") so it reads as gathering yourself from the air. Mix
  whitens as it completes; a bright `END_ROD`/`FLASH`/totem burst + `PLAYER_LEVELUP` on the pop back.
- **Discovery is on the SAVE** (Rule 2) — `discoversOnTrigger()` + `markDiscoveredByVictim` in `beginRecovery`,
  not when the blessing is cast.
- **The drawback — you can't reclaim your own drops.** The whole inventory spills out on death, each item
  entity tagged with your UUID (`IMMORTALITY_DROP_OWNER` attachment); anyone else can loot it freely, but
  YOUR pickup is vetoed (`BlessingEventHandler.onImmortalityDropPickup` → `setCanPickup(FALSE)`). Die in the
  open and you may stand up to find your things gone.
- **Can't be finished off mid-rebuild** — incoming damage cancelled while `isRecovering`. Deliberately NOT
  `setInvulnerable` (that flag would leak permanently if you logged out mid-rebuild).
- The overlay (`ImmortalityRecoveryOverlay`) is now an EVEN gold→white wash that pulses and fades over the
  last fifth — the earlier version's edge-gradient vignette read as "less yellow in the middle" and was dropped.
- State: synced `IMMORTALITY_RECOVERY_START`/`_END` (client lock + model-hide + overlay progress) and a
  persisted, copy-on-death `IMMORTALITY_USES` counter. Death spot kept in a server-side map.
```
immortalityRecoveryBaseTicks=160  immortalityRecoveryIncrementTicks=200  immortalityMaxUses=3   // 8s, 18s, 28s
```

### Sixth Sense — Compass  ✅ REFINED
A prickle at the back of your neck: on a randomised gap you get a detailed ACTION-BAR hint (8-point compass
direction + rough distance) about something nearby — another **player** (named), an ordinary **structure**
(village/mineshaft/shipwreck/ruined portal/ocean ruins/stronghold/trial chamber), or a **rare biome**
(mushroom fields, ice spikes, cherry grove, deep dark…). One category is sensed per fire (random order, first
that resolves), so no single search repeats every tick.
- **Rare-structure OVERRIDE:** on every scheduled sense it FIRST checks for a high-value structure nearby —
  **ancient city, end city, bastion remnant, nether fortress, buried treasure** — and if one's in range,
  announces it (gold text) and then waits the longer `sixthSenseRareCooldown`. So you're reliably told when
  something valuable is close, without it drowning the ordinary hints. Each rare structure has its own
  `witchmod:rare_*` structure tag (in `data/witchmod/tags/worldgen/structure/`) so it can be named; they're
  dimension-aware (end city/fortress only resolve in their dimension). Structure search via
  `ServerLevel.findNearestMapStructure`, biomes via `findClosestBiome3d`.
```
sixthSenseIntervalMinTicks=600  sixthSenseIntervalMaxTicks=1400   // 30s..70s ordinary hints
sixthSenseRareCooldownMinTicks=1800  sixthSenseRareCooldownMaxTicks=3000   // 90s..150s after a rare hint
sixthSenseRareRadiusChunks=12  sixthSenseStructureRadiusChunks=8  sixthSensePlayerRange=64  sixthSenseBiomeRadius=2048
```

### Iron Stomach — Raw Chicken  ✅ REFINED
Cast-iron guts: eating a "bad" food (rotten flesh, raw chicken, pufferfish, poisonous potato, spider eye —
a curated set, not effect-reflection, which changed shape across 1.21.x) gives **no penalty** (its
Hunger/Poison/Nausea are stripped on the eat) AND **more hunger + saturation** than it would for anyone else.
Handled on `LivingEntityUseItemEvent.Finish` in `BlessingEventHandler`; discovers on the first bad meal.
Bonus is added on top of the vanilla gain from the food's own nutrition/saturation.
```
ironStomachHungerMultiplier=3.0  ironStomachSaturationMultiplier=1.5   // bad foods only
```

### Iron Lung — Kelp  ✅ REFINED
You breathe anywhere — underwater AND buried in blocks — done with the mod's own lung-work, NOT a lazy
vanilla Water Breathing effect (so it can't be milked off and doesn't clutter the effect bar).
- **Underwater:** `onTick` (PlayerTickEvent.Post, after vanilla decrements air) tops the air supply back up
  to full every tick, so the bubble bar never drains and drowning never begins.
- **In blocks:** `in_wall` (suffocation) damage is cancelled in `BlessingEventHandler`, with `drown` cancelled
  too as a backstop for the odd tick before the air top-up runs.
No config values.
```
(no config — air topped up each tick for underwater + in_wall/drown damage cancelled for blocks)
```

### Anchor — Chain
```
ANCHOR_KNOCKBACK_IMMUNE=true (all sources incl. explosions)
```

### Twinkletoes — Hay Bale
```
TWINKLETOES_FALL_DAMAGE_IMMUNE=true
```

### Personal Trainer — XP Bottle
```
TRAINER_VILLAGER_XP_MULT=3.0
```

### Studious — Book
```
STUDIOUS_XP_MULT=2.5
```

### Twist of Fate — End Crystal  (Nether Star → Ghast Tear → End Crystal; see §11)
Moved off Nether Star (Immortality's item) to Ghast Tear, then to **End Crystal** (2026-08-24) when Flight
took Ghast Tear — End Crystal was free, so both stay castable under exact-item matching. Behaviour unchanged.
```
TWIST_NEGATE_CHANCE=0.12 (per incoming damage event, particle burst)
```

### Organised — Shulker Shell (CUSTOM UI)
```
ORGANISED_EXTRA_SLOTS=9  ORGANISED_DROP_ON_EXPIRE=true
```

### Nightowl — Glow Berries
```
NIGHTOWL_NO_FOG=true (incl. water/lava)  NIGHTOWL_DARKNESS_IMMUNE=true (blindness+darkness nullified client-side)
NIGHTOWL_GAMMA_OVERRIDE=full-bright equivalent
```

### Dexterous — Spectral Arrow  ✅ REFINED  (renamed from "Steady Hands"; id `steady_hands`→`dexterous`)
A rock-steady, quick draw: bows/crossbows are extremely accurate and charge quicker — AND (2026-09-02) the
same use-tick speedup now also applies to EATING, DRINKING and raising a SHIELD (the hook's item filter was
widened to `UseAnim` EAT/DRINK/BLOCK). ⚠ the shield's block activation is a fixed 5-tick gate, not
use-duration driven, so the shield half may need a mixin to visibly speed up — flagged for refinement.
- **Accuracy:** every arrow/firework you fire is re-aimed (`shootFromRotation`) to your exact line with a tiny
  `steadyHandsInaccuracy` spread, on `EntityJoinLevelEvent`. This also **compresses multishot** — the three
  pellets fly together so they can ALL bite one target.
- **Charge quicker:** while drawing a bow / loading a crossbow, `steadyHandsChargeSpeedupTicks` extra charge
  ticks are credited each use-tick (`LivingEntityUseItemEvent.Tick.setDuration`) — ~2× at the default 1.
- **Works for crossbow FIREWORKS** (they're re-aimed too, and the crossbow load is sped up). Logic in
  `ProjectileBlessingHandler`; discovers on the first straight shot. Prototype's flat Strength dropped.
```
steadyHandsInaccuracy=0.35  steadyHandsChargeSpeedupTicks=1
```

### Hawk Guy — Target Block  ✅ REFINED  (renamed from prototype "Locked In"; id `locked_in` → `hawk_guy`)
Every projectile you loose subtly HOMES on whoever you were aiming at. The instant it spawns
(`EntityJoinLevelEvent`), a **cone raycast** from you (`hawkGuyAcquireCone` half-angle, `hawkGuyAcquireRange`,
with line-of-sight) picks the most-aligned living entity as the intended target and stores its id on the
projectile (`HAWKGUY_TARGET` attachment). Each tick (`EntityTickEvent.Post`) the projectile bends toward that
target's centre by `hawkGuyHomingStrength`, **speed preserved** (subtle, not a heat-seeker — mirrors the
Magnet steer), giving up if the target dies or gets past `hawkGuyMaxHomingRange`. Works for **arrows and
crossbow FIREWORKS** (any Projectile you own except a fishing bobber). Logic in `ProjectileBlessingHandler`;
discovers on the first shot that acquires a target. Prototype's flat Haste dropped. Registry id renamed so
the display reads "Hawk Guy" (names derive from the id path).
```
hawkGuyHomingStrength=0.09  hawkGuyAcquireConeDegrees=20  hawkGuyAcquireRange=24  hawkGuyMaxHomingRange=64
```

### Hawk Guy — Target Block
```
HAWKGUY_HOMING_STRENGTH=0.08  HAWKGUY_ACQUIRE_CONE=20  HAWKGUY_ACQUIRE_RANGE=24
```

### Main Character — Fire Charge (CUSTOM SOUND)  [Section 11 collision RESOLVED — moved off Firework Star]
```
MAINCHAR_BUFF_PER_ENEMY={strength:+0.5dmg, speed:+2%}  MAINCHAR_MAX_STACKS=8  MAINCHAR_COMBAT_TIMEOUT=200
Sounds: bewitchment:blessing.mainchar.theme, bewitchment:blessing.mainchar.theme_intense (>=4 stacks)
```

### Last Stand — Enchanted Golden Apple (CUSTOM SOUND)
Fatal damage → revive with huge brief buffs in a knockback explosion. **Consumes the blessing.**
```
LASTSTAND_REVIVE_HEALTH=6.0  LASTSTAND_BUFFS={strength_2,speed_2,resistance_2}  LASTSTAND_BUFF_DURATION=300
LASTSTAND_EXPLOSION_KNOCK=2.0  LASTSTAND_EXPLOSION_RADIUS=6 (knockback only, no damage)  LASTSTAND_USES=1
Sound: bewitchment:blessing.laststand.rise
```

### Jesus — Lily Pad
```
JESUS_WALK_ON_WATER=true  JESUS_CROUCH_SUBMERGES=true  JESUS_WORKS_ON_LAVA=false
```

### Thick Skinned — Armadillo Scute (NOT PROTOTYPED)
Small tick damage ignored; damage must exceed 2 to apply.
```
THICKSKIN_DAMAGE_FLOOR=2.0   // single events <= 2.0 fully negated (per spec: "must exceed more than 2")
```

### Farmer's Spirit — Carrot (NOT PROTOTYPED)
```
FARMSPIRIT_RADIUS=12  FARMSPIRIT_GROWTH_TICK_MULT=5.0
Applies: crops, saplings, nether wart, sweet berries, cocoa
```

### Brute — Iron Helmet (NOT PROTOTYPED) (CUSTOM SOUND)
Straight-line sprint ramps speed to a cap; at cap, collisions launch entities and you smash
through blocks at HP cost.
```
BRUTE_RAMP_TIME=60  BRUTE_MAX_SPEED_MULT=2.2  BRUTE_TURN_TOLERANCE=15
BRUTE_LAUNCH_FORCE=2.0  BRUTE_LAUNCH_DAMAGE=4.0  BRUTE_BLOCK_BREAK_HARDNESS_MAX=3.0
BRUTE_HP_COST_PER_BLOCK=1.0  BRUTE_RESPECTS_GRIEFING=true
Sounds: bewitchment:blessing.brute.ramp, bewitchment:blessing.brute.smash
```

### Blacksmith — Copper Block (NOT PROTOTYPED)
```
BLACKSMITH_ANVIL_COST_MULT=0.4  BLACKSMITH_TOO_EXPENSIVE=disabled
```

### Unseen — Ink Sac (NOT PROTOTYPED)
Fully invisible (armour/held included) to clients beyond proximity radius; fades, no hard pop.
```
UNSEEN_REVEAL_DISTANCE=8  UNSEEN_FADE_BAND=4  UNSEEN_BREAKS_ON_ATTACK=false
```

### Silver Tongue — Emerald Block (NOT PROTOTYPED)
```
SILVERTONGUE_TRADE_COST=minimum (all trades floor to vanilla minimum)
```

### Hot Stuff — Coal (NOT PROTOTYPED)
```
HOTSTUFF_SPEED_MULT=5.0  HOTSTUFF_LOOK_RANGE=8
Applies: furnace, blast_furnace, smoker, campfire
```

### Bouncy — Slime Ball (NOT PROTOTYPED) (CUSTOM SOUND)
```
BOUNCY_RESTITUTION=0.7  BOUNCY_FALL_DAMAGE_IMMUNE=true  BOUNCY_ENTITY_BOUNCE_FORCE=1.2
Sounds: bewitchment:blessing.bouncy.boing_small, bewitchment:blessing.bouncy.boing_large
```

### Excavation — Iron Pickaxe (NOT PROTOTYPED)
```
EXCAVATION_HASTE_STACK_TIME=60  EXCAVATION_MAX_HASTE=4  EXCAVATION_DECAY_GRACE=40
```

### Angler — Salmon (NOT PROTOTYPED)
```
ANGLER_BITE_TIME_MULT=0.25  ANGLER_TREASURE_BONUS=+0.05
```

### Laugh Track — Cocoa Beans (NOT PROTOTYPED) (CUSTOM SOUND)
Chat messages trigger a GLOBAL laugh track.
```
LAUGHTRACK_SCOPE=server-wide  LAUGHTRACK_COOLDOWN=200  LAUGHTRACK_VARIANTS=3
Sounds: bewitchment:blessing.laughtrack.laugh_1 / laugh_2 / laugh_3 (variant 3 = single sad "ha")
```

### Chat — Purple Wool (NOT PROTOTYPED) (CUSTOM UI)
Personal fake Twitch chat overlay reacting to gameplay; can surface REAL useful info (nearby
structures, chests, players/entities, low durability).
```
CHAT_MESSAGE_INTERVAL_MIN=100  CHAT_MESSAGE_INTERVAL_MAX=600  CHAT_USEFUL_INFO_CHANCE=0.10
CHAT_OVERLAY_LINES=5
List: data/bewitchment/text/twitch_chat.json
```

### Civilisation — Raw Beef (NOT PROTOTYPED)
Coyote time + edge bias for parkour.
```
CIVILISATION_COYOTE_TICKS=5  CIVILISATION_EDGE_MAGNETISM=0.05
```

### Low Gravity — Eye of Ender (NOT PROTOTYPED)
```
LOWGRAV_JUMP_MULT=1.6  LOWGRAV_FALL_SPEED_MULT=0.6  LOWGRAV_FALL_DAMAGE_MULT=0.4
```

---

## 7. NEUTRALS — ❌ CUT ENTIRELY (code deleted)

Neutrals (small flavour events, and the old ritual-failure landing pad) were cut. A failed ritual now just
fizzles. The `events/` package and every neutral class are deleted.

## 8. GLOBALS — ❌ CUT ENTIRELY (code deleted)

Server-wide events + the shared Global bank were cut. The whole subsystem (Event registry, `GlobalCharge`,
the Afflicted status, `/bewitch event`) is deleted.

## 9. MODIFIERS (15)

Applied in the Table's Modifier slot. Deltas multiply onto base cost/duration/success/backfire.

| Modifier | Cost | Duration | Success | Backfire | Notes |
|---|---|---|---|---|---|
| Clock | +15% | fixed 45 min | — | — | overrides the random 35–60 min base roll; still IGNORED by duration-override curses (e.g. Moonwalker, which halves it) |
| Netherstar | +50% | — | forced 100% (removes failure entirely) | — | |
| Prismarine Shard | -15% | — | — | — | |
| Dragon's Breath | +30% | splash copies at 1/4 duration | — | +10% | spreads to players near the CASTER |
| Netherite Ingot | +40% | — | — | +10% | bypasses Ward/Jar; NOT Warding Totem |
| Ink Sac | +35% | — | — | +5% | effect hidden until its discovery event fires; then shows with timer |
| Glow Ink Sac | -10% | — | — | — | target told in chat exactly what they got |
| Rabbit's Foot | +10% | — | — | -15% | |
| Echo Shard | +10% | — | — | — | delays ONSET by 5–10 min (changed from delay-tell) |
| Goat Horn | -5% | — | — | — | horn sound when the curse lands |
| Sugar | -10% | -50% | +10% | — | |
| Honeycomb | +20% | 25% of rolled duration | 100% IF attachment is not Major-tier+ | 0% (forced) | no effect on non-time-based events |
| Quartz | -20% | — | — | — | only for Compendium-discovered spells |
| Compass | +5% | fixed 40 min | — | — | overrides the random roll |
| Recovery Compass | +15% | — | — | — | attachment does NOT persist after death (the exception to Rule 4) |
| Eye of Ender ("Test the Waters") | +10% | — | — | — | pure diagnostic: when a cast is BLOCKED, chat-reports every protection the target carries (a held Ward / a Warding Totem's radius / bathing in Holy Water; falls back to "some other protection" for a raw `/effect protected`). Cross-slot with Low Gravity's Eye of Ender sacrificial item — separate slots, no clash. |

REMOVED as modifiers (now sacrificial items / table mechanics): Gunpowder (→ Explosive curse),
Milk Bucket (→ Butterfingers curse), Redstone Dust (→ random-attachment table mechanic).

---

## 10. COSTS, WEIGHTS & FORMULAS

### 10.1 Formulas
```
successChance  = min(95%, 30% + 65% * (essenceSpent / baseCost))     // Netherstar forces 100%
backfireChance = max(0%, 25% - (essenceSpent / baseCost) * 25%)      // modifiers add deltas after
```
Cursed Essence is the CURRENCY you invest: more essence in the slot ⇒ higher success, lower backfire (0% at
full cost). After modifier deltas, a TIER clamp applies (`ModifierCalculator.applyTierBackfire`): low-tier
attachments never backfire; high-tier keep a small backfire floor even at full essence; mid-tier follow the
curve down to 0.

> ### ⚙ ATTACHMENT POWER LEVEL (0–100) — hand-editable, two files
> Each effect has a real **power level (0–100)** (`data/PowerLevels`, exposed as `Effect.powerLevel()`), a
> plain `curses`/`blessings` → id → number map. **TWO files, fallback order on-disk → bundled → 50:**
> - **`src/main/resources/witchmod-power-levels.json`** — the BUNDLED default that SHIPS in the jar. Edit
>   these + rebuild → every fresh install starts from them. This is where curated numbers live for release.
> - **`<instance>/config/witchmod-power-levels.json`** — the per-instance override, auto-generated (seeded
>   from the bundled defaults) and re-read at mod setup / server start / `/reload`. Editing it + `/reload`
>   applies live; it always WINS over the bundled default. ⚠ Dev has TWO separate ones — `run/server/config/`
>   (cost/probability) and `run/client/config/` (Compendium display); `run/` is git-ignored.
> Both are MERGED on load: known values kept, new effects appear at 50, removed ids pruned. It drives the
> Compendium power pips + the coin/gamble power bias TODAY; the essence price/backfire systems below still key
> off **`baseCost`** and should be repointed at `powerLevel()` when those are next tuned: It drives the Compendium power pips + the coin/gamble power bias TODAY; the essence
> price/backfire systems below still key off **`baseCost`** and should be repointed at `powerLevel()` when
> those are next tuned:
> - **Backfire tiering** (`ModifierCalculator.applyTierBackfire`): low/high tier decided by
>   `backfireLowTierCost` (≤20 ⇒ never backfires) / `backfireHighTierCost` (≥50 ⇒ keeps
>   `backfireHighTierFloorPercent` floor).
> - **Backfire explosion power + backfire damage-type amount** (`BewitchingTableRitual.doBackfire`): scaled
>   0..1 across `backfireStrengthCostMin`..`backfireStrengthCostMax` baseCost, lerped between
>   `backfireExplosionPowerMin/Max` and `backfireDamageMin/Max`.

### 10.2 Curse costs (updated names; NEW entries marked)
```
Violence - Moderate - 45
Butterfingers - Moderate - 30
Explosive - Major - 65
Super Explosive - Major - 60          (NEW)
Popularity - Moderate - 32
Yap - Minor - 20
Green Aura - Minor - 20
Repel - Minor - 25
Echoes - Minor - 15
Delusions - Minor - 17
Gluttony - Minor - 25
Gassy - Minor - 20
Farmhand - Minor - 17
Heavy - Minor - 25
Slippery Feet - Minor - 20
Magnet - Minor - 25
Neutral Aggression - Minor - 25
Dwarfism - Minor - 20
Screensaver - Minor - 15
Minor Inconvenience - Minor - 15
Thirst Meter - Minor - 17
Social Outcast - Moderate - 35
Floor Is Lava - Major - 55
Heavyweight - Minor - 25
Bad Swimmer - Minor - 20
Pests - Minor - 17
Allergic - Minor - 20
Comic Relief - Minor - 25
Ugly - Minor - 20
Audit - Major - 80
Sticky - Moderate - 30
Backseat Driver - Minor - 17
Clumsy - Minor - 20
Oversharer - Moderate - 32
Broken Bonds - Minor - 20
Insomniac - Minor - 17
Flat Footed - Minor - 25
Wonky - Minor - 15
Stick Drift - Minor - 17
Basement Dweller - Moderate - 40
Claustrophobia - Moderate - 30        (NEW)
Glass Cannon - Moderate - 30
Mansplainer - Minor - 17
Moonwalker - Minor - 20               (NEW)
Siren's Call - Moderate - 35          (NEW)
Loading Screen - Minor - 15           (NEW)
Pacing - Minor - 17                   (NEW)
Trumpet - Minor - 15                  (NEW)
Heavy Handed - Moderate - 40          (RESTORED; renamed from Uncareful)
```

### 10.3 Blessing costs (x1.4 flavor multiplier baked in; NEW entries marked)
```
Fortune - Minor - 35
Peace - Minor - 21
Luck - Minor - 28
Full - Minor - 24
Army - Minor - 35
Reflect - Minor - 28
Soul Bond - Major - 100 (manual surcharge applied)
Bodyguard - Moderate - 42
Payday - Minor - 35
Hype Man - Minor - 28
Workman - Minor - 28   (item: Obsidian)
Pickpocket - Minor - 35
Windfall - Minor - 24
Immortality - Major - 100 (manual surcharge applied)
Sixth Sense - Minor - 24
Iron Stomach - Minor - 24
Iron Lung - Minor - 24
Anchor - Minor - 24
Twinkletoes - Minor - 24
Personal Trainer - Minor - 24
Studious - Minor - 28
Twist Of Fate - Minor - 28
Organised - Minor - 24
Nightowl - Minor - 21
Steady Hands - Minor - 28
Hawk Guy - Moderate - 42
Main Character - Minor - 35   (Fire Charge — Section 11 collision resolved)
Last Stand - Moderate - 50            (NEW; single-use)
Jesus - Minor - 28
Thick Skinned - Minor - 24            (NEW)
Farmer's Spirit - Minor - 24          (NEW)
Brute - Moderate - 42                 (NEW)
Blacksmith - Minor - 24               (NEW)
Unseen - Moderate - 42                (NEW)
Silver Tongue - Minor - 35            (NEW)
Hot Stuff - Minor - 21                (NEW)
Bouncy - Minor - 28                   (NEW)
Excavation - Minor - 28               (NEW)
Angler - Minor - 21                   (NEW)
Laugh Track - Minor - 21              (NEW)
Chat - Minor - 28                     (NEW)
Civilisation - Minor - 24             (NEW)
Low Gravity - Minor - 24              (NEW)
```

### 10.4 Neutral weights — ❌ N/A (Neutrals cut, §7).

### 10.5 Backfire weights
```
Mirror (curse redirected to caster) - 3
Essence Backlash (damage + essence loss) - 2
Table Tantrum (personal Table cooldown) - 1
Essence Leak (essence drops for others to grab) - 2
Marked (temporarily cheaper for others to curse you) - 2
```

### 10.6 Global bank costs — ❌ N/A (Globals cut, §8).

## 11. ⚠ COLLISION & GAP REPORT (report only — NEVER fix alone)

Sacrificial matching is exact-item (Rule 9). **All historical collisions are RESOLVED** — every effect has a
distinct sacrificial item. Report any NEW collision here; never resolve one alone.

**Soft flags** (safe under exact-item matching, but adjacent — render both icons clearly in the Compendium):
White Bed (Narcolepsy) vs Red Bed (Homebody) · Black Wool (Carelessness) vs Purple Wool (Chat) · Water Bottle
(Thirst Meter) vs Glass Bottle/Jar (Player Essence) · Raw Beef (Civilisation) vs Cooked Beef · Redstone Block
vs Redstone Dust (the random-attachment table mechanic).

**Cross-slot (the Modifier slot is separate — informational):** Echo Shard, Compass, Goat Horn, Rabbit's
Foot, Nether Star, Ink Sac, Bell, Paper, Slime Ball/Block, Amethyst Shard, Wither Rose all double as both a
sacrificial item and a modifier — distinct slots, no clash. Honey Bottle=Sticky vs Honeycomb=modifier.

**Tag exceptions must never be widened** beyond their tag: music discs (Hype Man), planks (Builder), corals
(Ocean's Blessing), signs (Splitscreen).

## 12. CUSTOM SOUND EVENTS — MASTER OGG TODO (19)

(Delusions' two entries — `vanish` and `whisper` — were CUT on Oliver's call: vanilla sounds suffice, and
for that curse they're actively better, since every noise it makes should be one the victim has heard from
real players. Count dropped 21 → 19.)

```
CURSES
witchmod:curse.unhygienic.flies           ✅ DONE — 3 ambient fly-buzz variants
witchmod:curse.echoes.ping                ✅ DONE — fake notification chime (SINGLE file, no variants by design)
witchmod:curse.gassy.fart_small           ✅ DONE — 4 everyday variants
witchmod:curse.gassy.fart_large           ✅ DONE — the rare big one (single file by design)
witchmod:curse.slippery.slide_whistle     ✅ DONE — 3 descending slide-whistle variants
witchmod:curse.loading.music_1/2/3/goofy   ✅ DONE — 4 hold-music tracks (weighted 37.5/37.5/24/1%)
witchmod:curse.pacing.theme               ✅ dramatic theme (⚠ supplied as .mp3 — export to OGG)
witchmod:curse.pacing.click               ✅ DONE — quiet camera-cut click
witchmod:curse.pacing.theonepiece         ✅ DONE — 1/250 revelation replacing a click
witchmod:curse.trumpet.walk_loop          ✅ SUPPLIED — fat-trumpet walking loop (⚠ file is STEREO; must be
                                          re-exported MONO for OpenAL to position/attenuate it)
(curse.trumpet.stop_sting — CUT; the design is an instant cut with no stop flourish)
witchmod:curse.cutaway.train_warning / train_roll   ✅ SUPPLIED — "I like trains" line (plays in full) then the roll
witchmod:curse.cutaway.ball_throw / bowling_strike  ✅ SUPPLIED — throw + strike1/strike2 impact variants
witchmod:curse.cutaway.drifting                     ✅ SUPPLIED — Tokyo Drifting screech
witchmod:curse.cutaway.helicopter                   ✅ SUPPLIED — looped rotors (client native loop)
witchmod:curse.cutaway.tractorbeam / ufo_enter      ✅ SUPPLIED — looped beam hum + the UFO-arrival sting
witchmod:curse.cutaway.annoying_music     ⏳ PENDING — the annoying-music track (currently loops curse.loading.music_goofy)

BLESSINGS
bewitchment:blessing.mainchar.theme          looping battle theme
bewitchment:blessing.mainchar.theme_intense  high-stack variant
bewitchment:blessing.laststand.rise          revive fanfare/shockwave
bewitchment:blessing.brute.ramp              rising rumble while accelerating
bewitchment:blessing.brute.smash             impact hit
bewitchment:blessing.bouncy.boing_small      small bounce
bewitchment:blessing.bouncy.boing_large      big bounce
bewitchment:blessing.laughtrack.laugh_1      laugh track 1
bewitchment:blessing.laughtrack.laugh_2      laugh track 2
bewitchment:blessing.laughtrack.laugh_3      single sad "ha"

GLOBALS (no custom sounds currently required — Russian Roulette reuses vanilla charged-creeper hiss)

STATUS/SYSTEM
bewitchment:system.discovery              chat-alert chime on discovery
bewitchment:system.global_warning         global event build-up warning sting
witchmod:system.afflicted     ✅ SUPPLIED — plays on ANY curse/blessing onset (StatusEffectSync)
witchmod:system.blessed       ✅ SUPPLIED — a beat AFTER afflicted, for a blessing
witchmod:system.cursed        ✅ SUPPLIED — a beat AFTER afflicted, for a curse
witchmod:system.big_hit       ✅ SUPPLIED — layered on the hit sound by Giant / Heavy Hitter
witchmod:ritual.fail          ✅ SUPPLIED — the Bewitching Table's distinct FAILURE sting
witchmod:curse.narcolepsy.snore  ✅ SUPPLIED — intermittent varied-pitch snores while asleep
witchmod:curse.cutaway.parade_drum + .parade_march  ✅ SUPPLIED — the two Parade tracks (drumparade + marchparade), played TOGETHER at 55% vol
```

---

## 13. UI SPECIFICATION

### 13.1 Bewitching Table screen
Crafting-table-shaped GUI: triangle of 3 slots (top = Player Essence, lower-left = Cursed Essence,
lower-right = Sacrificial Item); Modifier slot left of the triangle; purple probability bar under
the triangle scaling with essence inserted.

### 13.2 Compendium
Book UI with a side selection ("bookmarks") jumping to: Tutorial | Items/Blocks | Rumours
(Curses/Blessings) | Rumours (Events) | Rumours (Backfires) | Modifiers.
**Every attachment/event has its own RUMOUR page (placeholder entries until discovered) and a
DISCOVERED page with its own image inside the book.** Discovery flips rumour → discovered
instantly, with the chat alert (Rule 2).
Page images: `assets/bewitchment/textures/gui/compendium/pages/<attachment_id>.png`

### 13.3 Ledger
Read-only book-via-lectern UI; entries appended automatically from the hex log.

### 13.4 Attachment UI (fully functional required)
- **Thirst Meter**: droplet-icon replica hunger bar.
- **Loading Screen**: fullscreen fake-load overlay (black + spinner + tip).
- Also: Gluttony second hunger row, Organised extra inventory row, Chat twitch overlay.

### 13.5 GUIDE: Custom art, colours & textures for the Bewitching Table UI

How Minecraft container GUIs get their look, and where YOUR art goes:

1. **The background texture.** The whole Table screen is one PNG:
   `assets/bewitchment/textures/gui/container/bewitching_table.png`
   Convention: a 256x256 image where the actual GUI panel occupies the top-left region
   (typically 176 wide x 166+ tall — ours will be ~176x180 to fit the bar). Everything —
   panel background, slot outlines, decorative art — is painted INTO this PNG. The screen class
   just blits it: `guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight)`.
   To restyle the whole table: repaint this one file. Purple wood? Bone trim? Runes around the
   triangle? All just pixels here.

2. **Slot positions are code, slot LOOKS are texture.** Each slot is registered in the menu class
   at an (x,y) — e.g. Player Essence at (80, 20), Cursed Essence at (56, 62), Sacrificial at
   (104, 62), Modifier at (26, 41). The 18x18 slot frames you see are drawn in the PNG at those
   same coordinates. Want hexagonal or rune-ring slot art instead of vanilla squares? Draw it in
   the PNG around those coordinates — the code doesn't care what the frame looks like, only where
   items render.

3. **The probability bar is a two-state sprite.** Standard technique: draw the EMPTY bar into the
   background PNG, and draw the FULL (purple) bar elsewhere in the same 256x256 sheet (unused
   space, e.g. at (0, 180)). At render time, blit a partial-width slice of the full bar over the
   empty one: `blit(TEXTURE, x, y, 0, 180, (int)(barWidth * successChance), barHeight)`.
   Recolouring the bar = repainting that strip. Gradient, bubbling essence animation (multiple
   frames side-by-side + frame cycling), anything.

4. **Text colours.** Titles/labels are drawn in code with `drawString(font, text, x, y, 0xRRGGBB)`.
   Keep a small palette constants class (`BewitchmentColors`) so the purple used in the bar, the
   title text, and particle tints all reference one place.

5. **Recommended workflow for first-time GUI art:**
   a. Export vanilla's `crafting_table.png` GUI from the jar as a size/layout reference.
   b. Lay out your panel at 176-wide in any pixel editor (Aseprite/GIMP); keep vanilla's grey
      panel first, get slots aligned with code coordinates, THEN restyle.
   c. Test with `F3+T` (resource reload) — no rebuild needed for texture-only changes.
   d. Only after layout is stable, do the decorative pass (runes, wood grain, glow).

6. **Where everything lives:**
```
assets/bewitchment/textures/gui/container/bewitching_table.png   (table screen)
assets/bewitchment/textures/gui/compendium/book.png              (compendium base)
assets/bewitchment/textures/gui/compendium/pages/*.png           (per-attachment images)
assets/bewitchment/textures/gui/hud/thirst_icons.png             (droplet bar sprites)
assets/bewitchment/textures/gui/hud/gluttony_icons.png           (second hunger row)
assets/bewitchment/textures/gui/hud/loading_screen.png           (spinner + frame)
assets/bewitchment/textures/gui/hud/twitch_overlay.png           (chat blessing frame)
```

---

### 13.6 GUIDE: The Loading Screen curse — animation sheet, bar, and how to design the whole screen

The screen is already FULLY FUNCTIONAL with no art: the panel and bar are drawn from plain fills, and the
animation slot renders as the missing-texture checker until a PNG exists. Everything below is a drop-in.

**1. The animation ("GIF"). Minecraft cannot play GIFs — you supply a SPRITE SHEET and the code flips
through it.** ✅ SUPPLIED — the shipped sheet is Oliver's opening/closing chest:
```
File:    assets/witchmod/textures/gui/hud/loading_animation.png
Shipped: 36 frames of 128 x 152, stacked VERTICALLY  =>  a 128 x 5472 PNG
         (an Aseprite "{name}-Sheet.png" export; opens fully then shuts, so it loops seamlessly)
```
- **Both layouts are supported** — set `loadingScreenSheetVertical` to `true` for a single column (Aseprite's
  default export) or `false` for one left-to-right row. Frame 0 comes first either way.
- **Frames do NOT have to be square** (the chest isn't — it's taller than it is wide). The four numbers are
  config, not code: `loadingScreenFrameCount`, `loadingScreenFrameWidth`, `loadingScreenFrameHeight`,
  `loadingScreenFrameTicks`. **Change the sheet, change those to match**, and remember the sheet's total
  size must be exactly `frameWidth x (frameHeight * frameCount)` for a vertical strip.
- `loadingScreenFrameTicks=2` holds each frame 2 ticks => **10 fps**, so the 36-frame chest runs one full
  open-and-shut every 3.6s — deliberately close to a typical fake load. Set 1 for 20fps/1.8s. For smoother
  motion raise the frame count rather than dropping frame ticks below 1.
- Use a **transparent background** — it's drawn onto black — and note the frame is blitted at its NATIVE
  pixel size, centred horizontally, sitting `ANIMATION_GAP` (10px) above the bar. A frame much taller than
  ~200px will crowd small GUI scales.
- The animation is driven off wall-clock ticks, so it **keeps playing during a stutter** — that's what makes
  a frozen bar read as "hung" rather than "broken".

**2. The loading bar and palette.** All code-drawn — no texture needed. Current look (Oliver's call, an
Xbox-360 homage): 240x10 bar, 2px outer rim `#B4B4B0` (light grey), dark well `#3A3A3A`, fill `#92C83E`
(the 360 green), on a soft off-white page `#E6E4DF`, with `#2E2E2E` text and `#5A5A5A` tips. Text is drawn
**without a shadow** — a drop shadow reads as mud on a light background.
The "Loading" caption cycles `.` → `..` → `...` every `DOT_CYCLE_TICKS`, driven off wall-clock ticks so it
keeps ticking over even while the bar is frozen mid-stutter (which is what sells a stutter as the game
hanging rather than the overlay dying). It's centred on the WIDEST form so it can't twitch sideways.
To make the bar a texture instead, follow the two-state sprite technique in 13.5 §3 — paint the empty bar
and the full bar into one sheet and blit a partial-width slice of the full one over the empty one, using
`LoadingScreenState.progress()` (0..1) as the fraction.

**3. The tips.** `assets/witchmod/text/loading_tips.json` — a **plain JSON array of strings**, nothing else:
```json
["TIP: first line", "TIP: second line"]
```
Write freely; the file is re-read at the START of every loading screen, so editing it plus F3+T shows up
immediately with no restart. If the file is missing or malformed the screen falls back to one generic tip
and logs a warning rather than crashing. One tip scrolls right-to-left along the bottom; when it fully
leaves the screen the next one starts.
**Note the path is `assets/`, not `data/`** as the other text lists in Section 17 use. That's deliberate:
this list is consumed purely client-side by the overlay, so putting it in assets means it needs no
server→client syncing and reloads with the normal resource-pack reload.

**4. Designing the screen as a whole.** Layout is in `client/LoadingScreenOverlay`, all of it plain numbers
you can move:
Everything is **horizontally centred**, and vertical positions are measured **from the centre** so the
layout holds together at any window size/GUI scale. Change one number, move one element:
| Element | Where | Constant |
|---|---|---|
| Page | whole screen, slightly TRANSLUCENT white | `BACKGROUND` (alpha `0xDC`) |
| Animation + shadow | centred; its BASE sits at `h/2 + 18` | `ANIMATION_BASE`, `SHADOW` |
| "Loading..." | centred, `h/2 + 60` | `TEXT_Y` |
| Bar | centred, `h/2 + 78` | `BAR_Y`, `BAR_WIDTH/BAR_HEIGHT` |
| Bar accent | muted sage rim + 1px highlight on the fill | `BAR_RIM`, `BAR_HIGHLIGHT` |
| Live percentage | just right of the bar | `FAINT_COLOUR` |
| Hairline rule | `h - 40`, inset both sides | `RULE_FROM_BOTTOM`, `RULE_COLOUR` |
| Tip marquee | `h - 26`, clipped to a scissor strip | `TIP_FROM_BOTTOM`, `loadingScreenTipScrollSpeed` |
Design notes (all Oliver's calls after seeing it in-game): the page is deliberately **not opaque** — the
world showing faintly through is what makes it interesting, so it needs no painted-on decoration. The green
is confined to the bar (a bold accent stripe elsewhere on the page fought with it), and the rim is a muted
sage so the accent stays quiet next to the fill. The **BAR is what must read as centred**, so it's centred
exactly and the percentage hangs off its right. The animation is anchored by its BASE, not its top, so a
taller sheet grows upward instead of shoving the bar around (and it's clamped off the top edge on tiny GUIs).
For a fuller Bethesda parody, the natural additions are a background image behind the animation (blit
before the other elements) and a "PRESS ANY KEY" prompt that flashes but does nothing — input is already
dead, so it would be pure theatre, which is the joke.

## 14. Gamerules / Config
```
Grace Period      - new players cannot be targeted for a customisable window
Curses            - on/off
Blessings         - on/off
Globals           - on/off
Ward Durability   - wards can be made non-decaying so players can 'opt out'
Backfires         - on/off
Limit             - max simultaneous attachments per player (default 3)
tntRainWorldDamage- false by default (TNT Rain world damage opt-in)
```

## 15. Command Tree

**OVERHAULED 2026-08-29** — regrouped into intuitive families (was a flat, scattered set of ~10 top-level
verbs). All node builders live in `commands/BewitchCommand.java`; every effect action defaults its target to
the command source when `[targets]` is omitted, and shares one set of terse helpers (`self`/`targets`/
`effect`/`effectArg`/`duration`/`giveOrDrop`). Op-gated (permission level 2).

```
# --- effect management (the hot path — kept top-level and short) ---
/bewitch apply  {effect} [targets] [duration]     cast as YOU (you are the caster; a Ward/Totem lets a self-cast through)
/bewitch dummy  {effect} [targets] [duration]     cast anonymously — caster "dummy" in the Ledger; a Ward/Totem BLOCKS it
/bewitch remove {effect} [targets]                strip one effect (effect-first, matching apply; was reversed)
/bewitch clear  [targets] [all|curses|blessings]  strip everything, or one category (default = all)

# --- discovery (Compendium) ---
/bewitch discovery add|remove effect {id} [targets]
/bewitch discovery add|remove modifier {id} [targets]
/bewitch discovery add|remove all [targets]

# --- give: generate a bound/filled mod item ---
/bewitch give jar make {e1} [e2] [e3]             a filled jar from effect ids (§4.1)
/bewitch give jar copy {player}                   a jar snapshotting a player's active effects
/bewitch give essence {player}                    a Player Essence bound to an online player
/bewitch give essence uuid {uuid}                 …bound to a UUID (works OFFLINE)
/bewitch give voodoo  {player}                    a Voodoo Doll bound to an online player
/bewitch give voodoo  uuid {uuid}                 …bound to a UUID (OFFLINE)

# --- debug/testing ---
/bewitch debug force  {effect} [targets] [arg]    force an effect's signature event / named sub-event (Effect.debugForce; §16.2b)
/bewitch debug voodoo {interaction} {player}      force a voodoo interaction (stab/throw/squeeze/feed/ignite/freeze/wet/lightning/shake/potion/arrow/fishing)
/bewitch debug ritualfx {kind} [target]           fire a Ritual Table outcome FX at your feet (success_blessing|success_curse|fizzle|backfire|backfire_mirror); [target] lashes to a player
/bewitch debug voodootrack                        point your held Voodoo Doll tracker at the nearest NON-player entity for 30s (testing)

# --- standalone ---
/bewitch organised                                open your Organised-blessing 9-slot stash
```

**What changed in the overhaul:**
- `apply` + `dummy` now share ONE handler (`applyEffect(..., boolean dummy)`); dummy passes a null caster
  (so Ward/Totem block it) and logs `"dummy"` with the real `success`/`blocked`/`refused` result. Both report
  "… on N player(s) — M landed."
- `remove` reordered to `{effect} [targets]` (was `{targets} {effect}`) and now defaults to self, matching apply.
- `clear` gained an explicit `all` literal (still the default with no category).
- The scattered `jar` / `essence` / `voodoo make` item-generators are collapsed under **`give`**; the redundant
  `essence player` / `voodoo make player` literals are gone (just pass the player, or `uuid {uuid}`).
- The Voodoo `force` interaction moved out of the `voodoo` (make) family into **`debug voodoo`**, un-mixing
  "make a doll" from "force an interaction".
- The CUT event system's `/bewitch event` + `/bewitch forcestop` are gone (removed with the events package).
- **Removed the placeholder error messages**, incl. the debug one that pointed at "CLAUDE.md §16.4"
  (now just "X has no forcible debug event.") and trimmed the verbose tax-bank wording.

`debug force` WIRED coverage (Effect.debugForce, §16.2b): haunted (dread + sub-events), bedrock_moment
(~19 sub-events + passive info), cutaway_gag (all gags + `villager`), splitscreen, narcolepsy, and every
event on Oliver's list. Client-timed ones (minor_inconvenience, screensaver) re-assert their flag; sixth_sense
forces the real sense next tick; the rest fire immediately.

Note: `/bewitch clear` is backed by `EffectManager.removeAll(target, @Nullable category)` (fires each
effect's onRemove for clean teardown). The Thirst + Gluttony HUD bars hide in creative/spectator
(gated on `gameMode.canHurtPlayer()`, matching vanilla's own survival-HUD gate).

---

## 16. REFINEMENT WORKFLOW (current stage)

> ### 🎯 CURRENT GOAL — the class-by-class CODE/COMMENT CLEANUP pass
> Every attachment, item and block is built, signed off and working — the refinement stage (§16.3) is
> essentially done, its only open `- [ ]` being Purifying Water, held purely for its texture. The PRIMARY JOB
> now is the **finalize-stage cleanup**, done ONE class at a time against the **§16.4 checklist** (in batches
> by package). For each class:
> - **Code:** is anything UNNECESSARY (dead code, unused fields/methods/imports) — cut it. Can logic be
>   REUSED rather than duplicated — pull it to a shared helper. Is it as OPTIMISED as it needs to be — hot
>   paths, per-tick work, allocations, redundant scans/lookups.
> - **Structure + networking:** check the server↔client communication. Nothing may CRASH on EITHER end (no
>   client-only class touched server-side, side guards correct, attachments synced not assumed), and the
>   server must never be CLOGGED by excessive packets (per-player/per-tick particle or sync spam → batch,
>   throttle, or move to a client render off a synced flag — the Amethyst Bell / Guardian FX pattern).
> - **Comments:** rewrite to the house rules below.
> Tick a class off in §16.4 the moment it's cleaned. The remaining non-code work is the pending ART/AUDIO
> assets (§12/§17) and a full in-game playtest — both Oliver's.

> ### ✍️ COMMENT STYLE — the house rules (apply during the §16.4 cleanup, and to all NEW code now)
> - **ALL comments are vague and lowercase.** Mention only important functionality — never restate what the
>   code plainly does, no long multi-line rationale essays. Keep a short line only where intent is genuinely
>   non-obvious, or where the code is intricate specifically to counteract something (a vanilla quirk, a sync/
>   threading trap, an ordering hazard) — say briefly what and why.
> - **The class blurb** at the top says what the class does and WHY — still all lowercase, no bloat.
> - **Remove every reference to CLAUDE / CLAUDE.MD** from code and comments — none should remain in `src/`.
> (The codebase is currently heavily over-commented in mixed case; §16.4 is where that gets brought to these
> rules, class by class.)

### 16.1 The loop — ONE thing at a time, hands-on
1. **Pick** the next unchecked entry from the checklists in 16.3 (Oliver chooses, or Claude suggests the
   next). Only ONE entry is "in progress" at any moment.
2. **Assess.** Claude re-reads that entry's full spec (its Section 5/6/7/8/4/3 entry + constants) AND its
   current implementation, then proposes concrete, specific improvements to close the gap — behaviour,
   missing mechanics, feel/timing, edge cases, and turning any hardcoded numbers into named config values.
3. **Iterate in-game.** Oliver tests it in-game against the spec; Claude refines the suggestions and the
   implementation, round by round, until Oliver is satisfied with the functionality.
4. **Sign off.** When Oliver says it's good, flip its `- [ ]` to `- [x]` and append `— <one-line note>` of
   what "done" means for it (e.g. what was changed, any deliberate deviation, any asset still pending).
5. **Next.** Do not start the next entry until the current one is checked off. Do NOT batch-refine.

### 16.2 Order & rules
- **Order:** all CURSES → all BLESSINGS → all NEUTRALS → all GLOBALS → then ITEMS → then BLOCKS.
  (Modifiers, Section 9, get refined opportunistically alongside the Table/items — no separate checklist.)
- Refine to the REAL spec. Where a custom sound / UI / entity / text-list / skin asset is still missing
  (Sections 12/13/17), get as close as possible with what exists and note the pending asset in the sign-off
  line rather than blocking.
- Every numeric value stays a **named, config-exposed constant** (rule 7 / the Phase E config) — refining an
  attachment includes lifting any still-hardcoded numbers into config.
- Preserve the no-stacking / discovery / status-wrapper guarantees (they live at shared chokepoints — don't
  re-scatter them while refining a single attachment).
- Keep this section's checklist current the moment Oliver signs off — it is the live source of progress.

### 16.2b ⚙ DEBUG-FORCE CONTRACT (mandatory for anything with a discrete moment)
There is a debug command sector: **`/bewitch debug force <effect> [targets] [arg]`** (op-gated, in `BewitchCommand.debugNode`)
which calls **`Effect.debugForce(ServerPlayer target, @Nullable String arg)`** and echoes the returned feedback line per
target. This exists so events can be triggered on demand for hands-on testing without waiting for their natural conditions.

**RULE — when refining/adding ANY curse, blessing, neutral, global or event that has a discrete, observable moment
(a spawn, a scare, a chat line, a screen effect, a stat, an attack…), you MUST override `debugForce` so that moment is
forcible.** Guidelines:
- Return a **non-null feedback String** describing what happened. If a precondition genuinely can't be met (no nearby
  entity, not in water, etc.), do as much as possible and SAY SO in the message ("no ridable nearby — nothing to fling").
  Return `null` ONLY when the effect truly has no discrete forcible event.
- If the real trigger lives in a private/internal method, expose a small package-visible or static entry the override
  (and the natural trigger) both call — don't duplicate logic.
- Multi-event attachments (The Dweller, Bedrock Moment) dispatch on `arg` = the sub-event name (e.g. `force witchmod:the_dweller @s chase`,
  `force witchmod:bedrock_moment @s helicopter`); with no `arg` they should pick a sensible default or list the names.
- The Dweller additionally accepts `arg` = a dread value to **set the dread stat** (clamped to the curse's own limits).
- Client-driven moments (Loading Screen, Screensaver, Minor Inconvenience, Stick Drift, etc.) force by setting their
  synced signal/flag from the server side of `debugForce`.
- **Future "debug modes" with alternate selectors** (e.g. targeting villagers as stand-in players) are planned for
  curses/blessings not yet built — the `arg` channel is the hook for that when those land.

### 16.3 Refinement checklists

**CURSES** — `witchmod:` ids. Full specs for the classic curses live in §5; the statement curses (Dense,
Giant, Solicitor, The Snail, Haunted, Bedrock Moment, Splitscreen, Cutaway Gag, Carelessness, Narcolepsy)
are documented ONLY here. Renamed ids: Neutral Aggression=`neutral_aggression`, Flat Footed=`flat_footed`,
Wonky=`wonky`, Broken Bonds=`broken_bonds`, Heavy Handed→Klutz=`klutz`, Explosive→Martyrdom=`martyrdom`,
Super Explosive→Volatile=`volatile`, The Dweller→Haunted=`haunted` (entity `spaghetti_man`).
- [x] Violence — spec in §5.
- [x] Butterfingers — spec in §5. (Freed Slime Ball → moved to Milk Bucket.) **stuck_fingers synergy (+Sticky):**
      a fumble is caught (swing + `CurseSticky.squelch`, nothing dropped) — Sticky counters Butterfingers.
- [x] Martyrdom (Explosive) — spec in §5.
- [x] Volatile (Super Explosive) — spec in §5.
- [x] Popularity — spec in §5.
- [x] Yap — spec in §5. **streamer_brain synergy (+Chat):** `yapStreamerChancePercent` of ambient outbursts become
      "talking to chat" lines from the yap.json `events.streamer` list.
- [x] Unhygienic — spec in §5.
- [x] Repel — spec in §5.
- [x] Echoes — spec in §5.
- [x] Delusions — spec in §5.
- [x] Gluttony — spec in §5.
- [x] Gassy — spec in §5.
- [x] Farmhand — spec in §5.
- [x] Dense — **Iron Block. Heavy + Heavyweight MERGED into one curse** (both retired). Fall faster + crater
      on a hard landing (old Heavy) AND any floor with air beneath gives way when you loiter (old Heavyweight).
      `CurseDense` delegates to the kept-but-unregistered `CurseHeavy`/`CurseHeavyweight` logic; their client
      flags (`HEAVY_ACTIVE` water anchor, `HEAVYWEIGHT_SHAKE_END` shake) + `heavy*`/`heavyweight*` config still
      drive them. Old `heavy`/`heavyweight` saved instances drop as unknown (harmless).
- [x] Slippery Feet — spec in §5.
- [x] Magnet — spec in §5. Now also SUBTLY pulls primed TNT toward you (`magnetHazardPull`), not just projectiles.
      **magnetic_storm synergy (+Thunder):** during a thunderstorm under open sky, real lightning is periodically
      drawn onto you (`magnetStorm*`).
- [x] Neutral Aggression — spec in §5.
- [x] Dwarfism — spec in §5.
- [x] Screensaver — spec in §5.
- [x] Minor Inconvenience — spec in §5. Fix (2026-09-02): the maximise is now RE-ASSERTED each tick until the
      GLFW `MAXIMIZED` attribute sticks (guarded off while Screensaver bounces), because exiting fullscreen made
      vanilla restore the small windowed size a frame later and overwrite the one-shot maximise — so it fills
      the whole monitor now instead of showing the desktop.
- [x] Thirst Meter — spec in §5. FULLY FUNCTIONAL UI (droplet bar, dehydration effect, 24 knobs). Holy
      (Purifying) Water is now a CLEAN drink (no raw-water risk, `CurseThirstMeter.drinkHoly`) that also washes
      `holyWaterDrinkDrainTicks` (5 min) off EVERY active attachment (`EffectManager.reduceAllDurations`) with an
      END_ROD sparkle — identified via `HolyWater.isProtectingFluidAt` in `consumedByThirst`.
- [x] Social Outcast — spec in §5 (+ chat isolation: an outcast only reads a message from within
      `outcastRevealDistance`, else a muffled "Someone says something…"; per-recipient re-send in `CurseEventHandler`).
- [x] Giant — **Wheat Seeds.** `SCALE` ×3 (model + hitbox), `MOVEMENT_SPEED` ×0.9, `ATTACK_SPEED` ×0.7,
      `ENTITY_INTERACTION_RANGE` ×2 — all transient + re-asserted each tick. Take 75% less / deal 80% more MELEE
      (damage event). STOMP: walking INTO something crushes it (`witchmod:stomp`, bypasses armour) + launch,
      per-victim cooldown. Base melee hits land heavy (`giantHitKnockback` + sweep/crit FX). 10 knobs.
- [x] Floor Is Lava — spec in §5.
- [x] Heavyweight — ⚠ MERGED into **Dense** (retired). See Dense.
- [x] Bad Swimmer — spec in §5.
- [x] Pests — spec in §5.
- [x] Allergic — spec in §5.
- [x] Comic Relief — spec in §5. Both strikes are debug-forcible now: `force @s` = the low-health bolt, `force @s items` = the item-pile smite.
      **smiting synergy (+Thunder):** the killing bolt becomes a quick multi-strike barrage (`comicReliefSmiteBolts` extra visual bolts via the `SMITES` list; kills the same, loot untouched).
- [x] Ugly — spec in §5.
- [x] Audit — spec in §5.
- [x] Sticky — spec in §5.
- [x] Backseat Driver — spec in §5.
- [x] Clumsy — spec in §5.
- [x] Oversharer — spec in §5.
- [x] Broken Bonds — spec in §5.
- [x] Insomniac — spec in §5.
- [x] Flat Footed — spec in §5.
- [x] Wonky — spec in §5.
- [x] Stick Drift — spec in §5.
- [x] Basement Dweller — spec in §5. Damage interval now RAMPS 80→15 ticks over 25s (per-player, resets on leaving sun) + subtle heat particles while exposed.
- [x] Claustrophobia — spec in §5. Interval RAMPS 70→25 ticks over 10s; crushing thud (quiet zombie-on-door hit) per bite + a ONE-OFF ash puff on entering, not constant.
- [x] Glass Cannon — spec in §5.
- [ ] Mansplainer — ❌ CUT.
- [x] Moonwalker — spec in §5.
- [x] Siren's Call — spec in §5.
- [x] Loading Screen — spec in §5. FULLY FUNCTIONAL.
- [x] Pacing — spec in §5. ⏳ `pacingtheme` is a .mp3, needs OGG export.
- [x] Trumpet — spec in §5. ⚠ supplied OGG is STEREO — needs a MONO re-export for positional audio.
- [x] Klutz (Heavy Handed) — spec in §5. Item Flint.
- [x] Solicitor — **Bundle.** A named vanilla `WanderingTrader` hounds you with terrible `MerchantOffers`,
      pitching them in local chat (`data/witchmod/text/solicitor.json`, player-style `<Name> line`). Follows +
      teleports if it falls behind; tagged (`witchmod_solicitor`+`solowner_<uuid>`) so `CurseEventHandler` finds
      it. KILL it → a fresh one spawns instantly (`killed` line). COMPLETE a trade → it hides 1.5–10 min
      (`sqrt(r)`-long). Persists across reload via a tag scan. 9 knobs.
- [x] The Snail — **Nautilus Shell.** CUSTOM ENTITY (`SnailEntity`, code-baked model/renderer): a tiny,
      immortal, AI-less snail the curse drives. It owns a VIRTUAL position advanced every tick (even unloaded)
      and only materialises within `snailMaterialiseRadius`. Chase speed = `snailBaseSpeedBlocksPerSecond` ×
      (1 + dist×`snailDistanceScale`) capped at `snailMaxSpeed` (slow near, fast far). Touch → `Level.explode`
      + lethal hit, then reappears far off. ⏳ `textures/entity/snail.png` PENDING. ~10 knobs.
- [x] Haunted (The Dweller) — **Oak Boat.** A Lethal-Company-Ghost-Girl statement curse. CUSTOM ENTITY
      `spaghetti_man` (lanky code-baked humanoid, emissive eyes) — a REAL server entity whose render is gated
      client-side and whose sound/particles are sent to the victim's connection only, so to the uncursed it
      never existed. **MULTI-VICTIM (2026-09-03):** the render gate (`ClientCurseHandler.canSeeDweller`) now
      also shows a dweller/watcher-eyes to ANY player who currently carries Haunted (synced `DWELLER_ACTIVE>=0`),
      so several cursed players share the nightmare instead of an entity that half-renders — but the lethal
      finale stays keyed to each chase's own `target`, so a haunted bystander is never the one exploded.
      ONE-WAY **dread** meter (persisted via
      `DWELLER_ANGER`), climbing in dark/alone/night/enclosed and via boredom, reduced only by dying to the
      chase. Scene loop: FOREPLAY → LULL (quiet, `tickMood`/hallucination ambience) → TELL → STALK (a tiered
      passive WATCH — FAR/MEDIUM/CLOSE/window/mob-stare — that vanishes if you hold its gaze `dwellerStareVanishTicks`,
      approach within `dwellerWatchVanishDistance`, or hit it) → SPIKE (double-take vanish, or bridges into a
      LUNGE / CHASE) → RELEASE. **CHASE** = real physics-mode `GroundPathNavigation` sprint
      (`dwellerChaseMoveSpeed`) that runs/jumps/climbs/swims after you, smashes doors/glass, teleports only as a
      rare last resort; ramps speed; mid-chase dodgeable LEAP; touch = finale (custom `witchmod:haunted` damage,
      bypasses invulnerability so creative dies too; gore burst, drops your severed head + redstone). **The
      finale now also goes off with a real explosion** (`dwellerFinaleExplosionPower`, previously an orphaned
      config — null source so the victim isn't excluded from their own blast; `ExplosionInteraction.NONE` so it's
      boom + knockback + collateral to nearby others but NO terrain damage, Oliver's call), and **colliding with
      ANY passive entity mid-chase** (`dwellerPassiveCollisionKill`) triggers the same finale — nowhere is safe
      once it hunts. Heartbeat
      is the single readable cue (per-beat `heat`). Events (all debug-forcible, most auto-fire only in LULL):
      shadow_pass, interaction (opens doors/containers), knock (delayed shatter), break (carves a shape),
      explode_mob (gibs an untamed mob), watching_eyes, sizeup, possession, behind, mimic (a stalker-staring
      impostor that drops the mask), lights-out, snuff_light, bed vigil, ambient footsteps/mob-unease. Client:
      dread-scaled fog + desaturation shader, red chase overlay, camera shake, white-flash jumpscare. Custom
      sounds wired (`curse/dweller/`: mood/wind/laugh/scream/breath/breathing/step/pop/bang/splatter/enraged/knock).
      ~90 config knobs (many `dweller*` orphans from culled events remain, harmless/removable).
- [x] Bedrock Moment — **Crying Obsidian.** Major (cost 110), no benefit: plagues your game with elaborate
      fake "bugs", a parody of Bedrock-jank clips. PASSIVES (event/scan-driven): Bluetooth (damage withheld then
      dumped ~1s late), Delay (late fall damage), Aimbot (homing skeleton arrows), Pause (your projectiles
      freeze), Fling (mounting hurls the mount), Ghost Blocks, Silent Creeper, Hotbar Drift, Food/Hit Reg,
      Air Swimming, Sleep Cancel. ACTIVE 3-tier weighted pool (`bedrockTier1/2/3Weight`): Nightcore, Sound Delay,
      Phantom Durability, Chunk Reject, Rubberband, Mitosis, Blitz, Server Lag, Pop, Inventory Shuffle,
      Helicopter (spins a noAi mob up), Tickspeed (ticks mobs extra), Marketplace popup, Perspective Flip,
      Language Error (client-only ClientLanguage swap), Speed Blitz, T-Pose/Pause, Float, Hungry, Vibrant
      (saturation shader), Input Lag, Sprint Reset, Ghost Item, Charged-Creeper Boat, Desync Drowning, Fake Kick
      (fake "Connection Lost" screen), Fake BSOD (fullscreen image + glitch pre-phase + broadcast "left the
      game"). Split Screen was pulled out into its own curse. ~70 config knobs; all `debugArgs`-listed. Sounds
      are vanilla placeholders.
- [x] Splitscreen — **any SIGN (`ItemTags.SIGNS`).** Drags the nearest free player into a shared console-style
      split screen (Soul-Bond stickiness: held until they leave `splitscreenRange`). Symmetric; both sides'
      synced `SPLITSCREEN_*` attachments driven by the curse. Phases: ENTERING/ACTIVE/EXITING with a fake
      "Entering/Exiting splitscreen…" loading screen. Shared-sign quirk: either opening a sign freezes both;
      taking damage force-closes it. Right-side PANEL shows the partner's **LIVE POV** — a second
      `renderLevel` pass into a `TextureTarget` (throttled ~20fps; Fabulous→Fancy forced during the split to
      avoid a strobe). `splitscreenLivePov=true` (signed off). Debug: `… splitscreen @s villager`. 4 knobs.
- [x] Cutaway Gag — **Spyglass.** Family-Guy cutaway: after `cutawayCooldownSeconds` (350s) min, a ramping
      per-second chance CUTS AWAY to a random other player — you're frozen spectating from a computed overhead
      vantage (server relocates your invisible, still-hittable body; client pins first-person aim at the victim;
      teleport-home persisted in `CUTAWAY_RETURN`, foolproof `recoverIfStranded`). After a beat a random GAG hits
      the victim, then a hit snaps you back. ~24 gags (all `debugForce`-able, no-repeat over last 3): creeper,
      anvil, golem, Woolliam, driveby, Dream (custom `DreamEntity` player-mimic, `witchmod:dream` dmg), jumped,
      snail, hole, abduction (UFO + tractor beam), launch, Tokyo drift, pied piper, fake TNT, aquarium, I Like
      Trains (`witchmod:train`), bowling (piercing, crowd-weighted, `witchmod:bowling`), the bouncer, annoying
      music, bouncy, marriage (4 paths — normal/objected/explode/what — with a cinematic camera + guests +
      petals, editable `marriage.json`), speed, helicopter, parade (marches across frame, `witchmod:parade`,
      both parade tracks at 55% vol). Cinematic overlay (letterbox + title card from `cutaway_titles.json`,
      hides gameplay HUD layers incl. F3). Positional looped sounds via `CutawayLoopSound`. In-flight cutaway
      driven from `CurseEventHandler.onServerTick` so debug-forced ones fire/end. 28 knobs. ⏳ helicopter.ogg
      needs a seamless-loop re-export; a few gag stand-ins noted (golem wall-burst, abduction anchor).
- [x] Carelessness — **Black Wool.** Client-render only: the vanilla health layer is cancelled and every heart
      drawn identical + pure black (`CarelessnessHud`), so you can't read your health (real value untouched).
      Off synced `CARELESSNESS_ACTIVE`.
- [x] Narcolepsy — **White Bed.** Every 35s–5.5min (biased long; idle accelerates it) you drop asleep 4–12s:
      input dead, screen darkens (vignette), a MASH-to-wake bar that constantly drains. Physics stay vanilla
      (gravity/fall damage apply — no server pose); the lying-down animation is faked CLIENT-side for OTHER
      viewers only (`renderOtherSleepers`), first-person + look-pin for the sleeper. Rarer DEEP sleeps
      (`narcolepsyDeepChancePercent`) last longer + need more mashing. Fast heal + snores + rising `witchmod:sleep_z`
      particles while asleep. Debug: `deep`/`verydeep`/`thirdperson`. Translatable prompts. 4+ knobs.

_New prototype batch (2026-09-02). Some signed off; the rest ⏳ awaiting Oliver's next in-game test._
- [x] Lightweight — **Snowball.** Knockback dealt TO you ×`lightweightKnockbackMultiplier` (2.0), stacking
      with the attacker's own Knockback (multiplies `LivingKnockBackEvent` on the victim, after enchants).
      Discovers on the first amplified launch.
- [x] Munchies — **Pumpkin Pie.** Food gives only `munchiesSaturationPercent` (25%) of its saturation, but
      you eat `munchiesEatSpeedPercent` (40%) faster — the eat-speed STACKS with Gluttony's. Discovers on the
      first meal.
- [x] Spotlight — **Glowstone.** Constant `GLOWING` + nearby hostiles get a stacking `spotlightDetectionBonus`
      FOLLOW_RANGE boost (own modifier id, stacks with Flat Footed). The light PILLAR — a rotating white END_ROD
      double-helix + firework motes, starting `spotlightBeamGap` above your head — is now rendered CLIENT-side
      off synced `SPOTLIGHT_ACTIVE`, so a constant particle stream never floods the server. Debug-forcible.
- [x] Hiccups — **Dried Kelp.** Random hops + hics (`hiccupsMin/MaxGapTicks` 120–360), fits rare
      (`hiccupsFitChancePercent` 7); each briefly freezes movement via a synced INPUT LOCK (not Slowness, so
      your FOV never dips) for `hiccupsFreezeTicks`. Modded `curse.hiccups.hiccup` sound (7 variants); no
      particle. Discovers on the first hiccup; debug-forcible.
- [x] Body Swapping — **Chorus Fruit.** Every `bodySwapMin/MaxGapTicks` you swap places with a sort-of-nearby
      player (band `bodySwapMinDistance`..`bodySwapRadius`); the enderman pop plays AFTER the teleport at each
      arrival spot, so both recipients hear it. Debug `force @s` swaps you with the nearest VILLAGER.
- [x] Left Handed — **Shears.** Client: flips the rendered main hand (forces the `mainHand` option, saved +
      restored), scrambles typed chat into letter spam (commands pass, so you can still cure it). Aim sway is
      now a PATTERNED sine weave (`leftHandedDriftDegrees` 2.5) that bites while aiming/USING anything (any
      `isUsingItem` — bow, trident, spyglass — or a loaded crossbow), plus a slight `leftHandedProjectileBloom`
      spread added (server-side, steer-only) to anything you throw/shoot. Off synced `LEFT_HANDED_ACTIVE`.
- [x] Ice Skates — **Packed Ice.** Client: only the BRAKING is icy — normal top speed while you hold a move
      key, but on release most momentum is retained (`iceSkatesSlip` 0.965), with a longer/faster skid off a
      SPRINT (`iceSkatesSprintSlip`/`Cap`). Sneak grips. Off `ICE_SKATES_ACTIVE`.
- [x] Vertigo — **Scaffolding.** Client, height-scaled (overhauled 2026-09-02): a CAMERA sway begins at
      `vertigoCameraStartY` (90) and grows with height to full at `vertigoMaxY` (320); from `vertigoMovementStartY`
      (120) up, a MOVEMENT sway (Wonky-style strafe weave) ALSO kicks in and grows. View-camera part never
      changes your aim. Discovers on the first sway. Off `VERTIGO_ACTIVE`.
- [x] Channels — **Note Block.** Client MIXIN (`mixin/ListenerMixin`, the mod's first + only mixin): negates
      the sound listener's `up` vector so `forward×up` flips → L/R audio swapped. Positional sounds only. Off
      `CHANNELS_ACTIVE`. ✅ SIGNED OFF 2026-09-11.
- [x] Narrator — **Written Book.** A smug TTS narrator reads out your life + occasional real hints, via
      Minecraft's OWN bundled `com.mojang.text2speech` (safe accessibility TTS, NOT a shelled process). Lines
      from the writable `data/witchmod/text/narrator.json` (50 categories, `{player}` = username); the victim +
      players within `narratorHearRadius` hear it (flat/non-positional). EVENT hooks (server):
      break/mine_ore/place/attack/kill/hurt/low_health/pickup/chest/death/crafting/**mounting**/**sleeping**
      (CanPlayerSleepEvent)/**griefing** (flint&steel or fire charge on a block)/**nether**+**end** (dimension
      change, always narrated)/**chat** (ServerChatEvent)/**cursed**+**blessed** (an effect lands on the victim)/
      **inflict_curse**+**inflict_blessing** (the victim casts one on someone else — hooked in `EffectManager.apply`)/
      **holy_water** (bathing in Purifying Water — hooked in `HolyWaterHandler`). SITUATIONAL (server, checked
      every 40t in `onTick`, priority order): creeper_behind, lava_fire, falling, pressure_plate (standing on a
      plate/tripwire), swimming, crouched, sprinting, player_crouched_near, low_durability (any held/worn item
      ≥85% worn), then a chance-gated **SURROUNDINGS tier** (`narratorEnvChancePercent`=30, one random applicable:
      thundering/raining/night/high_up/deep_underground/dark (gated on EFFECTIVE light ≤`narratorDarkLightLevel`
      so daylight never triggers a "place a torch" jab)/near_hostiles/near_villager/near_animals/low_hunger —
      commentary on the WORLD not the player, kept low so it never starves idle/ambient), then idling (~10s in one
      block), **returned** (moved off after idling). CLIENT-only (synced `NARRATOR_ACTIVE` flag + a C2S report):
      **tabbed_out** (window loses focus, plain GLFW focus query — not malware), **returned** (focus regained),
      **paused** (Esc menu while still focused), **whiff** (`LeftClickEmpty` — swinging at nothing), and
      **`tabbed_out_long`** (once away > `narratorTabSpamSeconds`=30,
      reported every `narratorTabSpamIntervalTicks`=60 to harass you CONSTANTLY until you come back — it bypasses
      the escalation anti-spam AND, while tabbed out, situational commentary is suppressed so this is the dominant
      voice, ambient still trickling through). The discrete client events (tabbed_out/returned/paused) always
      narrate (no event-chance roll). ⚠ While active it SUPPRESSES vanilla `pauseOnLostFocus` (stashed + restored
      on cure), else single-player pauses the sim the instant the window loses focus and the whole tab-out gag
      (incl. `returned`) never runs.
      **⚙ TIMING + ANTI-SPAM:** the next line may only start a random **0..`narratorPostLineGapMaxTicks`** (0–2s)
      AFTER the previous is estimated to FINISH (so lines flow one after another, no fixed floor); ON TOP, a
      sustained SAME-category run (e.g. swimming) escalates that gap by `narratorRepeatGapTicks` per repeat (cap
      `narratorRepeatGapMaxTicks`), and each individual line may only be spoken `narratorVariantCap`=3 times per
      run before it's retired — once every variant is spent that category goes SILENT until a DIFFERENT reaction
      resets the run. **SUBTITLE:** every spoken line is also shown on the action bar (`NarratorClient.tickSubtitle`,
      sized `narratorSubtitleTicksPerChar`, removed the instant it lapses) so non-Windows players can read it.
      **`linux_mac_callout`**: with `narratorCalloutChancePercent` a line's subtitle is REPLACED (non-Windows only)
      by an OS jab (each client decides by its own `Util.getPlatform()`). ⚠ TTS platform-dependent (great on
      Windows/macOS, silent on Linux without speech-dispatcher). Debug `force @s [category]`. ✅ SIGNED OFF
      2026-09-03 (Oliver may still add/polish lines in narrator.json, which is freely editable + `/reload`-able).
      **Synergy categories:** `insomnia` (narrated_sleeplessness, +Insomniac — replaces `sleeping` on a denied bedtime)
      and `trumpet_walk`/`trumpet_sprint` (narrated_trumpet, +Trumpet — situational, after crouched).

**BLESSINGS** — full specs for the classic blessings are in §6; the new ones (Builder onward, plus the
refined Hot Stuff / Bouncy / Chat / Low Gravity) are documented ONLY here. Renamed ids: Workman=
`tools_dont_use_durability`, Personal Trainer=`trainer`, Hawk Guy=`locked_in`, Full→Fullness=`fullness`,
Steady Hands→Dexterous=`dexterous`.
- [x] Fortune — spec in §6.
- [x] Peace — spec in §6.
- [x] Luck — spec in §6.
- [x] Fullness — spec in §6.
- [x] Army — spec in §6.
- [x] Reflect — spec in §6. **Duelists synergy (`Synergies.DUELISTS`, +Gladiator):** a reflected shot's return
      speed is ×`reflectGladiatorVelocityMultiplier` on top, so it flies further.
- [x] Soul Bond — spec in §6.
- [x] Bodyguard — spec in §6. Its death no longer breaks the blessing — a replacement is hired
      `bodyguardRespawnTicks` (6min) later (`fell`/`respawn` lines).
- [x] Payday — spec in §6.
- [x] Hype Man — spec in §6.
- [x] Workman — spec in §6.
- [x] Pickpocket — spec in §6.
- [x] Windfall — spec in §6.
- [x] Immortality — spec in §6. Adds a CONSTANT rebuild shine chime (`AMETHYST_BLOCK_CHIME` every
      `immortalityShineSoundIntervalTicks` (8), pitch rising with progress toward the level-up pop).
- [x] Sixth Sense — spec in §6. The high-priority RARE-structure override (ancient city / end city / bastion /
      nether fortress / **stronghold** (moved here from the ordinary list) / buried treasure — checked first
      each sense, gold text, longer cooldown, dimension-aware via `witchmod:rare_*` structure tags).
- [x] Iron Stomach — spec in §6. Bad foods (rotten flesh/raw chicken/pufferfish/poisonous potato/spider eye)
      give no penalty + bonus hunger/saturation; signed off alongside Sixth Sense.
- [x] Iron Lung — spec in §6.
- [x] Anchor — spec in §6. VERY subtle 'braced' feedback when a shove is resisted — a quiet `CHAIN_HIT` tink
      (vol 0.18) + a few `CRIT` sparks at the feet.
- [x] Twinkletoes — spec in §6. A soft `CLOUD` landing puff at the feet on a fall-damage cancellation, count
      scaling with fall distance.
- [x] Personal Trainer — spec in §6. Trades boost the VILLAGER's XP (`trainerVillagerXpMultiplier`, 25× for
      blatancy) + coach nearby villagers (`trainerNearbyRadius`); level-up scheduled the vanilla way (private
      timer+flag via cached reflection) at `trainerLevelUpDelayTicks` (near-instant) — needed because vanilla
      checks the threshold on the base XP before the event. Green `HAPPY_VILLAGER` + `VILLAGER_CELEBRATE` at each
      villager; the traded villager's XP bar is re-pushed live (showProgress hard-true, which fixed it vanishing).
- [x] Studious — spec in §6. Multiplies the collected orb's value on `PlayerXpEvent.PickupXp` (before the
      Mending/XP split, so it's reliable even with Mending gear — `XpChange` silently missed Mending-eaten XP).
      `AMETHYST_BLOCK_CHIME` + subtle green motes, rate-limited to 6 ticks.
- [x] Twist of Fate — spec in §6. End Crystal. Bad-luck-protection PITY (`twistPityIncrement` up to
      `twistMaxChance`, resets on a dodge); a brief post-dodge invuln (`twistInvulnTicks`); a light dodge flourish
      (yellow dust + enchant + end-rod) with the `system.blessed` sound at 1.3× pitch; brief cooldown (`twistCooldownTicks` 60).
      **Rebirth synergy (`Synergies.REBIRTH`, +Immortality/Last Stand):** a resurrect calls `refreshOnRevive`
      (clears the dodge cooldown + maxes pity), so you come back ready to dodge again.
- [x] Organised — spec in §6. The 9-slot stash opens from an editable 16×16 texture button (`OrganisedButton`,
      `assets/witchmod/textures/gui/organised_button.png` + a bg) below the inventory panel, off synced
      `ORGANISED_ACTIVE`; the stash screen has a "◀ Inventory" back button. No keybind; `/bewitch organised` works too.
- [x] Nightowl — spec in §6. No fog (client) + full-bright gamma (client) + immune to Blindness/Darkness
      (server veto + per-tick strip backstop). Vision, not movement.
- [x] Dexterous (renamed from Steady Hands; id `steady_hands`→`dexterous`) — **Spectral Arrow.** Bow/crossbow
      accuracy + faster charge (spec in §6), also speeds eating / drinking (use-tick hook). **(2026-09-11)** the
      shield-raise gate — a fixed `>= 5` constant in `LivingEntity.isBlocking()`, not use-duration driven — is
      now shortened by **`ShieldRaiseMixin`** (the mod's 2nd mixin, common-side) to `dexterousShieldDelayTicks`
      (2) off the synced `DEXTEROUS_ACTIVE` flag. **All lingering `steady_hands`/`SteadyHands` references were
      renamed to `dexterous`** (class `BlessingDexterous`, `Blessings.DEXTEROUS`, attachment `dexterous_active`,
      config `dexterous*`, power-level + Compendium keys; the orphan `steady_hands` lang entries were removed).
- [x] Hawk Guy — spec in §6. Homing strength SCALES with distance — gentle up close (`hawkGuyHomingStrength`
      0.3 at ≤`hawkGuyHomingNearDistance` 10) lerping to a much more blatant `hawkGuyHomingStrengthFar` (0.7)
      at ≥`hawkGuyHomingFarDistance` 32, so long-range shots curve in hard; acquire range bumped 24→40. ALSO:
      Forgiveness's projectile assist now looks ahead by SPEED (`forgivenessProjectileLookaheadTicks`) so it
      actually catches fast arrows, not just slow lobs.
- [x] Main Character — spec in §6. Firework Rocket. Cooler particles (firework + end-rod/soul-flame aura,
      firework+crit burst on hit entities), music volume ~0.52; hostiles only count while TARGETING you so the
      mode leaves promptly when the fight ends (4s sticky kept); music loops (buffered) + stops on tier 0.
      **HORDE tier (3):** `mainCharHordeThreshold` (10) hostiles aggroed onto you — from ANY source, not just
      Popularity — grants big buffs (Strength, Resistance II, Regen) + `mainCharCooldownReductionT3`/`KnockbackT3`.
- [x] Last Stand — spec in §6. A fatal blow grants ~2s TOTAL invulnerability (`lastStandInvulnTicks`) as a
      comeback window + Speed II/Fire-Res + knockback burst; NOT consumed per use — an escalating cooldown
      (`lastStandBaseCooldownTicks` 5min, DOUBLING each revive) up to `lastStandMaxUses` (3), then it breaks.
      Uses/cooldown persisted (`LAST_STAND_*` attachments); Scrying Mirror shows revives-left + cooldown.
- [x] Jesus — spec in §6. Walk on water (crouch to submerge); DEPTH STRIDER on your boots is re-purposed into a
      surface MOVEMENT_SPEED bonus (`jesusDepthStriderSpeedPerLevel`, +20%/level). Surface detection keys off
      not-submerged + water underfoot (not server `onGround()`, which flickered for a water-walker).
- [x] Thick Skinned — spec in §6. Single hits ≤`thickSkinnedDamageFloor` (2.0) fully negated + scute-tan
      deflection particles, a shield-clang, and a tiny screenshake when a hit bounces off.
- [x] Farmer's Spirit — spec in §6. Circular growth radius; crops climb one stage at a time (quick, not
      instant — `farmersSpiritIntervalTicks` 4, `farmersSpiritAttemptsPerPass` 20). PRODUCERS (pumpkin/melon
      stems, sugar cane, cactus, bamboo) get a burst of RANDOM TICKS (`farmersSpiritProduceTicks`) so they pump
      out fruit/stalks and can be farmed. Client-side COMPOSTER ring at the radius (near any crops/farmland) +
      green motes on growable plants, off synced `FARMERS_SPIRIT_ACTIVE` (self-heals in onTick). **Baby growth
      (base):** nearby BABY animals grow up quicker (`farmersSpiritBabyGrowthTicks` shaved off their age each
      pass); the **growth_spurt synergy (+Drive)** multiplies that by `farmersSpiritDriveGrowthMultiplier`.
- [x] Brute — spec in §6. NO-TUNNEL rework: on running into a block while charged you EXPLODE
      it (`blockAhead` = your bounding box extended forward, so any block leg-height→head, even one, is caught;
      `blocksMotion()`; explode at the block, `source=target` so small self HP cost + others more, breaks unless
      blast-resistant) and the CHARGE RESETS to 0 + forward momentum killed — you must re-build the sprint before
      the next smash, so no continuous tunnelling. Smash cooldown removed (the recharge gates it — also killed the
      earlier `Long.MIN_VALUE` overflow that had stopped smashing entirely). Charge survives jump/turn
      (`bruteSprintGraceTicks`), knockback immunity while charged, 2.6× speed, per-entity cleave cooldown.
      Smashing is GROUNDED-ONLY (`target.onGround()`), so running into a block on the ground smashes it but
      jumping up onto / landing on a block does not. Debug removed.) **Speed synergy (`Synergies.SPRINTING`,
      Brute+Speed):** a speed blessing makes the charge come up quicker (`bruteSpeedChargeBonus` extra
      charge/tick), plough harder (`bruteSpeedKnockbackMultiplier`) and hit for `bruteSpeedBonusDamage` more.
- [x] Blacksmith — spec in §6. Anvil "Too Expensive!" cap lifted + accumulated prior-work penalty wiped on the
      live anvil menu (`onAnvilUpdate`), so repairs/combines stay cheap.
- [x] Unseen — spec in §6. Adds a `witchmod:unseen` MobEffect (placeholder eye-slash icon, no swirls) shown
      WHILE cloaked so you know your stealth state; cloak/uncloak puff + a soft sound (cloak = a reversed-wind
      OGG `blessing.unseen.cloak`, reveal = SPELL_FAIL, both 1.6× pitch @ 0.36 vol). Mob-target veto + up-close
      reveal shake-off. (⏳ awaiting test: cloak/uncloak sound is `system.spell_fail` at 1.6× pitch; a
      `witchmod:unseen` MobEffect (icon-only, placeholder editable icon at
      `assets/witchmod/textures/mob_effect/unseen.png`) shows WHILE you're actually cloaked so you know you're stealthed.)
- [x] Silver Tongue — spec in §6. Opening a merchant knocks each offer's cost down by `silverTongueDiscount`
      via the special-price diff (drives both the shown price and what you pay); re-applied per open, doesn't leak.
- [x] Hot Stuff — **Coal.** A lit furnace/blast furnace/smoker you're LOOKING at (within `hotStuffLookRange`)
      cooks `hotStuffSpeedMultiplier` (now **10×**) faster (runs the furnace's serverTick extra times; fuel burns
      proportionally). Campfires deferred. **scorched_forge synergy (+Floor is Lava):** ×`hotStuffScorchedMultiplier` on top.
- [x] Bouncy — **MOVED TO A CURSE** (`CurseBouncy`, Slime Ball). Rubber physics (rebound/build-height,
      walk/sprint entity bounces, melee bounce-off) + `JUMP_STRENGTH` +50% (`bouncyJumpBonus`, transient).
      **Wall-bounce fix:** the rebound threshold was a hardcoded 0.15 blocks/tick — ABOVE sprint speed (~0.13),
      so running into a wall never bounced; now the config `bouncyWallMinSpeed` (0.07, below sprint) gates it.
      **Low-gravity synergy (`Synergies.BOUNCINESS`):** `CurseBouncy.onTick` sets `BOUNCY_ACTIVE` to 2 while the
      synergy is live and the client reads it to scale restitution + landing cap by `bouncyLowGravRestitutionMultiplier`.
- [x] Excavation — spec in §6. Mining-speed ramps to a higher cap (`excavationMaxBonus` 6.0) over a bit longer
      (`excavationRampPerBlock` 0.15), then fades to 0 over ~5s of not mining; subtle CRIT sparks while digging,
      count scaling with the current speed.
- [x] Angler — spec in §6. Fast bites + comical surprise pulls; a subtle splash mote around the bobber (quartered).
- [x] Laugh Track — spec in §6. Server-wide laugh track on chat messages (cooldown-gated, 3 variants incl. a
      single sad "ha").
- [x] Chat — **Purple Wool.** Full personal Twitch overlay (`ChatOverlayLayer`): server-driven lines from
      `twitch_chat.json` (30+ categories), viewer count, hype bar/train, subs (cashed out at stream end for
      emeralds), badges, emote images/gifs, dead-chat phase. An internal entertainment SCORE fed by many actions
      drives it. Persists through death. ~35 knobs.
- [x] Coyote (renamed from Civilisation; id `coyote`) — spec in §6. Walk-off coyote jump + edge magnetism onto
      a landable ledge ahead. (The tried landing "ledge catch" was removed — it felt bad.)
- [x] Low Gravity — **Eye of Ender.** `GRAVITY` ×`lowGravityGravityMultiplier` (0.55) + `JUMP_STRENGTH` ×1.6
      + fall damage ×`lowGravityFallDamageMultiplier` (0.4). Both modifiers transient + self-healed. 3 knobs.
- [x] Builder — **any Planks (tag exception).** Removes the client place (`rightClickDelay`) + break
      (`destroyDelay`) cooldowns via reflection off synced `BUILDER_ACTIVE`. Self-heals through death.
- [x] Berserker — **Iron Axe.** Consecutive LANDED hits speed up your attack cooldown
      (`berserkerReductionPerHit` 7% each, cap `berserkerMaxReduction` 80%) via a growing `ATTACK_SPEED`
      modifier. Resets on a MISS (client `BerserkerMissPayload`) or after `berserkerResetSeconds`. Explicit
      `berserkerMaxStacks` (14) ceiling; rolling ember particles scale with stacks, a gain-burst per stack, a
      smoke+fizzle on reset. `addStacks(player, n)` is the shared hook for the frenzy/parry_frenzy synergies.
- [x] Enchanter — **Lapis Lazuli.** Table perks: XP cost softened (`enchanterXpReduction`, gated to the
      enchant menu), offered levels bumped (`enchanterLevelBonus`, gated to a nearby Enchanter), and BAD
      enchants stripped off the result (Smite/BoA/protections/Piercing/curses). 2 knobs.
- [x] Pacifier — **Allium.** Anti-grief aura: per-tick sweep discards primed TNT/TNT-minecarts, deflates +
      pacifies creepers, discards fireballs/hurting projectiles, snuffs spreading fire — each with an END_ROD
      fizzle. 3 knobs.
- [x] Ocean's Blessing — **any Coral (`witchmod:corals` tag).** Modest baseline swim (client, `oceansSwimBoost`);
      the big speed is DOLPHINS — draws + steers nearby dolphins to follow, a close one grants Dolphin's Grace
      ×`oceansDolphinMultiplier`. Wet aggressive mobs pacified. No water breathing. 8 knobs.
- [x] Gladiator — **Golden Sword.** Right-click a sword/axe → a tight parry window; a frontal hit is negated
      (CLANG) and you riposte for `gladiatorRiposteDamageMultiplier`× at half weapon cooldown; a whiff = full
      cooldown + a lockout. PERFECT parry (within `gladiatorPerfectTicks`) = bigger + stuns + FX. Projectile
      parries reflect where you look with a soft assist-aim. Small crosshair HUD gauge (`GladiatorParryLayer`),
      block-guard weapon pose in both views (no mixins), camera shake, leeway/retroactive-parry window. 5 custom
      sounds. ~19 knobs.
- [x] Cow — **Leather.** Right-click empty (bucket) to milk YOURSELF → milk bucket; others can milk you too.
      Joke only.
- [x] Tank — **Cobbled Deepslate.** `MAX_HEALTH` +`tankBonusHealth` (+20, an extra bar; a real attribute, no
      effect UI) + slowed natural regen (`LivingHealEvent` ×`tankRegenMultiplier`). Transient + self-healed. 2 knobs.
- [x] Spider — **Fermented Spider Eye.** Client wall-climb off synced `SPIDER_ACTIVE`: push into a wall to
      climb (`spiderClimbSpeed`), sneak to cling, JUMP off a wall (`spiderWallJump*`); fall damage zeroed. 4 knobs.
      **spider_disguise synergy (+Disguise):** the disguise renders as a spider (`DISGUISE_TYPE` 4).
- [x] Ninja — **Black Dye.** `MOVEMENT_SPEED`+`ATTACK_SPEED` modifiers, a client mid-air DOUBLE JUMP off synced
      `NINJA_ACTIVE` (smoke FX), a swing woosh, sprint smoke trail. Attributes self-heal.
- [x] Backstabbing — **Nether Brick.** A MELEE hit from BEHIND multiplies FINAL damage
      ×`backstabDamageMultiplier` (1.6) with reduced knockback. Rear arc far more generous for mobs
      (`backstabMobDot`) than players (`backstabPlayerDot`).
- [x] Prop Hunt — **Flower Pot.** Crouch + stand still `propHuntStillTicks` → disguise as the block below
      (synced `PROPHUNT_BLOCK`/`PROPHUNT_ANCHOR`; client cancels player render, draws that block for everyone).
      Persists through walking; crouch re-anchors/re-samples; a real action pops it. See the **concealment**
      synergy (+Disguise) in §18.2 for the prop→animal→player cascade.
- [x] Blessing of Speed — **Sugar.** Its OWN `MOVEMENT_SPEED` +60% ONLY while sprinting (stacks with Speed
      potions/Ninja) + a spark trail. FOV zoom trimmed to ~30% of the natural sprint zoom (`onSpeedFov`).
      Discovers on first SPRINT. 1 knob.
- [x] Forgiveness — **Name Tag.** Entity hitboxes ~40% bigger FOR YOU ONLY: a melee near-miss connects
      (`onForgivenessAssist` enlarged raycast) and your projectiles curve onto an about-to-pass entity
      (`ProjectileBlessingHandler.steer`). Synced `FORGIVENESS_ACTIVE`. 2 knobs.
- [x] Drive — **Golden Carrot.** Nearby adult animals (and villagers) have their post-breed love-lockout
      cleared each tick (`setAge(0)`), so you can breed a herd/village as fast as you feed them. **Auto-breed
      trait:** once you STOP breeding them yourself for `driveAutoBreedIdleTicks` (30s — tracked from the last
      real post-breed lockout it cleared), nearby same-type pairs start breeding on their own with no food
      (`driveAutoBreedIntervalTicks`/`driveAutoBreedChancePercent`, one pair per attempt via `getBreedOffspring`).
      **Nature synergy (`Synergies.NATURE`, Drive+Farmhand):** the idle gate is skipped so auto-breeding runs
      constantly and each baby is instantly recruited into the Farmhand nuisance (`CurseFarmhand.recruit`). 4 knobs.
- [x] Vein Miner — **Iron Ore.** Break an ore/log → the whole vein/tree comes down (26-neighbour flood-fill,
      cap `veinMinerMaxBlocks` 64) at half durability per extra block. Fortune/Silk work both ways
      (`dropResources` with the tool + fires `BlockDropsEvent`). `BUSY` ThreadLocal guards recursion.
- [x] Collector — **Barrel.** Dropped items drift to you + XP magnet on steroids. GRACE period
      (`collectorGraceTicks`) before a fresh drop homes; CROUCH shrinks the radius hard
      (`collectorCrouchRadiusMultiplier`). 7 knobs.
- [x] Restock — **Chest.** On a place/consume (a USE, not a move) it tops the hotbar/offhand slot back up to a
      full stack from the backpack. A type-change/empty is ignored.
- [x] Sanguine — **Red Dye.** Natural regen ×0.2 BUT 30% LIFESTEAL of all damage you deal (melee + projectile,
      `LivingDamageEvent.Post`) with a blood-stream FX.
- [x] Homebody — **Red Bed.** Within `homebodyRadius` of your spawn (bed/anchor, else world spawn) you get
      Regeneration + Haste (refreshed each tick).
- [x] Sonar — **Sculk Sensor.** Every `sonarIntervalTicks` (140s) a 2.5s CHARGE then a pulse
      (`blessing.sonar.ping`) reveals entities within `sonarRadius` (70): GLOW + your-eyes-only radar-GREEN
      pointers. Crouched targets only within 60% radius; each hit shaves the timer. Next-ping shows in the
      Scrying Mirror. 7 knobs.
- [x] Heavy Hitter — **Mace.** Melee knockback doubled (`heavyHitterKnockbackMultiplier`) — marks the victim on
      the damage hit, then `LivingKnockBackEvent` multiplies the final strength (stacks with Knockback). PLUS a
      flat `heavyHitterFlatDamage` (2.0) added to any melee damage you deal (`onHeavyHitterDamage`). 2 knobs.
- [x] Speed Demon — **Carrot on a Stick.** Any ridable mount doubled: living mounts via a transient
      `MOVEMENT_SPEED` modifier, minecarts via an extra per-tick `move()`, boats client-side
      (`SpeedDemonClient`, synced `SPEED_DEMON_ACTIVE`). Speed trail. 1 knob.
- [x] Flight — **Ghast Tear.** Hold JUMP (past `flightHoldTicks`) to rise (`flightRiseSpeed`, ~1s accel
      buildup), draining a yellow energy bar above the XP bar (`FlightBarLayer`). Drain by rise mode (push-up
      vs glide ×`flightGlideDrainMultiplier`) + a small airborne drain; refills only once landed (after
      `flightRegenDelayTicks`). Sprint + a move key GLIDES forward (`flightSprintHorizontal`). A hit spends
      `flightDamageCost` + a lockout; 0 energy = grounded until 20%. Fall damage applies. Shimmer FX. ~10 knobs.
      **Elytra synergy:** while gliding on an elytra, holding jump adds a SMALL lift (`flightElytraLiftMult` 0.2 —
      just enough to stop losing height, not a climb) and holding SPRINT (no move key needed) adds a decent
      forward push along your look (`flightElytraSprintSpeed`) + a slight FOV zoom (`flightElytraFov`). Either
      push drains the bar at ×`flightElytraDrainMult` (2.0); gliding with NEITHER push REFUELS it
      (`flightGlideRegenPerTick`) — Flight's niche as an elytra enhancer.
- [x] Thunder — **Trident.** A static charge builds while you're NOT swinging, through 4 tiers; your next MELEE
      hit discharges it — burns the target + arcs chain-lightning (`thunderChainBase` + 30% of the hit, cap 12,
      `thunderChainRange`), scaling with tier (T4 = a visual bolt + 6 bonus dmg + wide chains). Discharge on
      `LivingDamageEvent.Post`; charge resets to 0 before chains fire (re-entrancy safe). Synced `THUNDER_TIER`
      aura. Debug forces a tier. 10 knobs.
- [x] Spelunking — **Torch.** Every `spelunkingIntervalTicks` it scans `spelunkingRadius` (7) for ore-tag
      blocks and marks each with a your-eyes-only colour-coded dust mote (per-player particle packet) + FULL-
      BRIGHT GLOW/END_ROD so they show through stone in the dark. 2 knobs.
- [x] Safety — **Respawn Anchor.** Crouch + still + look DOWN to channel `safetyChannelTicks` (10s) → teleport
      to spawn (gold FX at both ends). NOT consumed — goes on `safetyUseCooldownTicks` (6min) cooldown. Moving/
      hit cancels (short cooldown). 2 knobs. Debug forces the teleport.
- [x] Disguise — **Armor Stand.** Costumed as a cow/sheep/pig (consistent per-UUID; client render + hidden
      nametag + occasional ambient sounds + copied leg anim). Hostiles pacified (`LivingChangeTargetEvent` veto
      + aggro clear). Getting HIT, dealing damage, or getting within `disguiseBreakRadius` BREAKS it (synced
      `DISGUISE_TYPE`→-1); un-hit for `disguiseReturnTicks` restores. 2 knobs. ⏳ wide dummy model.
      **vampire_bat synergy (+Sanguine):** the costume becomes a BAT (`DISGUISE_TYPE` 3, `setResting(false)` so it
      flaps not T-poses) with free flight (`abilities.mayfly`, survival only), lost the instant the disguise
      breaks/ends. Flight GLIDES: holding sprint adds a forward push (`flightElytraSprintSpeed`) + FOV zoom
      (`flightElytraFov`) — client `DisguiseClient`.
      **spider_disguise synergy (+Spider):** the costume becomes a SPIDER (`DISGUISE_TYPE` 4); bat wins if both.
      **cow_costume synergy (+Cow):** forced to a cow (type 0). **silver_villager synergy (+Silver Tongue):** a
      VILLAGER (type 5) + a `silverVillagerRefundChancePercent` trade refund. Form priority bat > spider > villager > cow > base.
      **concealment synergy (+Prop Hunt):** prop takes priority, and a hit/proximity cascades prop→animal→player
      (`concealCascade`) with a brief unrendered puff-of-smoke flash (`CONCEAL_FLASH_END`, client render cancel) each step.
- [x] Confusion — **Rabbit Hide.** Throws off exact clones of yourself (`CloneEntity`, 1-HP, owner skin +
      nametag, wanders/swings/looks around) — up to `confusionMaxClones`, rarely when there's an audience.
      Nearby hostiles auto-aggro onto clones; a clone POPS to dust on any hit (no damage event). Dust streams
      from you to each new clone. 3 knobs. **wild_decoys synergy (+Disguise):** `CloneRenderer` reads the owner's
      synced form and draws each clone as the prop block / disguise mob (and vanishes with the owner's conceal flash).
_New blessing batch (2026-09-02) — built, compiles + boots clean; ⏳ awaiting Oliver's in-game test._
- [x] Leader — **Golden Helmet.** OTHER players within `leaderRadius` (never you) get Regeneration +
      Resistance (`leaderRegenAmplifier`/`leaderResistanceAmplifier`), re-applied each tick, with subtle
      HAPPY_VILLAGER motes so they know; a constant client-side golden radius RING off synced `LEADER_ACTIVE`.
- [x] Underdog — **Wooden Sword.** At/below `underdogHalfPercent` (50%) health → Strength/Speed/Resistance I;
      at/below `underdogQuarterPercent` (25%) → level II + Regeneration. Nothing while healthy.
- [x] Bloodhound — **Sniffer Egg.** A bright amber FOOTSTEP trail on the ground where every living thing (incl.
      invisible) within `bloodhoundRadius` walks (`bloodhoundFootstepMinMove`), remembered and RE-SENT every 4
      ticks so it LINGERS `bloodhoundLingerTicks` (~5s), your-eyes-only per-player particles (cap 400), so you
      can follow trails / track invisible mobs. The useless on-entity purple scent haze was dropped.
- [x] Guardian Angel — ✅ SIGNED OFF 2026-09-11. **Golden Apple (moved off Elytra — too expensive). STATEMENT BLESSING (overhauled 2026-09-04 from the old buffs/heal/defend/
      kidnap allay).** A managed vanilla **Allay** (noAi, setPos-driven from `onTick`, KILLABLE → returns after
      `guardianRespawnTicks`), now with a THREE-TIER behaviour set, resolved by priority every tick — REACTIVE
      always wins, then INTERACTIONS (a movement/mood `Mode`), then a slow AMBIENT gift trickle (hushed while
      you sneak/sleep). Utility only, no chat/combat.
      - Lifecycle: a managed vanilla Allay tagged `guardian_<uuid>`, `setPersistenceRequired`, `noPhysics`
        (passes through walls). Its max health is `guardianAllayHealth` (10 = 5 hearts) and it **fast-regens
        (1 hp/tick, subtle pink dust)** once un-hit for `guardianAllayRegenDelay` (60t). **On relog it ADOPTS
        the existing tagged allay** (+ a dedupe scan) instead of spawning a fresh one — fixes the orphaned
        AI-less allay that used to accumulate each rejoin. Killable → returns after `guardianRespawnTicks`.
      - **Start / expire animations**: a rising golden spiral gathers as it arrives (`BEACON_ACTIVATE`); it
        gathers into light and winks out on cure/expiry (`BEACON_DEACTIVATE` + FLASH).
      - **STRENGTH (always-on):** **Grace aura** — out of combat it keeps `guardianAbsorptionHearts` (4) of
        Absorption topped up + speeds regen (icons + subtle golden feet motes, no sound). **Guardian Sacrifice**
        (`guardianSacrifice`) — a KILLING blow is negated (`LivingDamageEvent.Pre`): the allay dies in your place,
        you survive at 1 HP with Resistance III / Regen III / Absorption / Fire-Res, returning after the respawn
        cooldown — a real second life. **Lock-on protection** — the MOMENT a hostile targets you it's kidnapped or
        shoved back before it hits you. **Zap** — while you're actively attacking ANY entity (`AttackEntityEvent`),
        it fires a **CHAIN zap** — a blue-dust bolt that arcs through up to `guardianZapChainMax` foes
        (`guardianZapChainRange` apart), `guardianZapDamage` (1♥) to the first and ×`guardianZapChainFalloff` each
        jump, brief stun on each — so it WHITTLES a crowd rather than gatling one target; less frequent
        (`guardianZapCooldown` 55t), pitched `blessing.guardian.zap` sound (0.7 vol) at the allay. Custom
        `witchmod:guardian_zap` damage type (no-knockback/no-impact) credited to the OWNER, so kills read
        "%s was smitten by <owner>'s guardian angel". **Guiding
        light** — in a crowd (≥`guardianGuidingMinEnemies`) it GLOWS the highest-health foe (players included);
        you deal +`guardianGuidingBonus` (1.5♥) to it, with a gold hit-burst; the **holy-water chime**
        (`AMETHYST_BLOCK_CHIME` @1.5 pitch) plays when the lock CHANGES (reused for Mark + Planter, which lacked
        sound). **Blinding light** — freezes mid-air and builds an intensifying light
        swirl for 3s, then LAUNCHES itself at the nearest foe: a bang + 6 damage + knockback on impact that stuns
        + breaks aggro (mobs) or blinds 12s + slows 2s (players), `guardianBlindingCooldown`. The "grab an
        attacker" slot (on hit / on lock-on) branches on YOUR health: **Lift** (≥40% health) — flies at the
        attacker and drops Levitation 10 for 3s (a light, funny deterrent); **Kidnap** (<40% health) — hauls
        them UP slowly + comically (`guardianKidnapDropHeight` 28, ×0.75 rise speed) and drops them, INTO lava/
        fire if any is within `guardianKidnapLavaRadius` (released `guardianKidnapHazardHeight` (7) ABOVE it for
        counterplay room); a kidnapped **player can't dismount to escape** (EntityMountEvent cancelled while
        held; state cleared BEFORE the release stopRiding so they're never trapped). Anti-grief still always
        kidnaps (creepers/TNT, ignoring health). **Escort** — badly hurt (≤`guardianEscortHealthPercent`) with
        danger near → it CARRIES you off to safety. Lift/kidnap/blinding/escort all **fly to the target first**.
      - **Emotes** (personality bursts above its head): happy on buffs/gift/fireworks, hearts on cleanse, and an
        ANGRY sulk when you hit a villager or your own pet.
      - Movement: the allay is `noPhysics=true` (re-asserted each tick), so it NEVER collides with or shoves
        you/blocks, and every owner-centred goal runs through `keepClear` to hold ≥`ROOM` (1.9) horizontal
        clearance — it never sits on/inside you. Default is a **dynamic FOLLOW** — a wide layered-sine 3-D wander
        that TRAVELS around you (not a rigid orbit), occasionally switching sides. Shapes are drawn WIDE (heal
        star r2.8, orbits r2.2–3.0) to read clearly. Move is eased (fraction of remaining distance, speed-capped);
        active modes get a speed multiplier (PINGPONG ×2.4). The particle TRAIL is sparse (every 4 ticks for
        shape-tracers, else 7 — enough to sketch the figure, not a stream); idle/still modes leave none. A subtle
        rotating 3-dot **golden HALO** hovers just above its head (every 3 ticks, suppressed while sneaking) — no
        custom model.
      - **REACTIVE (each on its own cooldown; the urgent ones become movement Modes):** Anti-grief (a lit/close
        creeper is KIDNAPPED away, primed TNT is flung — `guardianAntigriefRadius`, high priority), Dampen (puts
        out your fire), **Anti-fire** (a fire BLOCK within ~3 → flies over and splashes it out), Catch (a fatal
        fall → PHYSICALLY scoops you up via `startRiding` and flies you to the nearest hazard-free landing;
        SKIPPED if you already survive falls — Twinkletoes / Flight / Spider / Slow Falling), Air
        save (Water Breathing + refill when drowning), **Cleanse** (circles you, purges 1–2 HARMFUL potion
        effects), **Heal** (below HALF health → flies a fast five-point STAR around you knitting `guardianHealRate`
        hp/s; a HIT cancels it for 2s), Phantom purge, Curse purge (`guardianCursePurgeChance`), **Hunger** (below
        40% food → tops you up + brief saturation), Fishing (hovers bobber + hurries bite, STACKS w/ Angler),
        Arrow retrieval, Restock (floor items you ALREADY carry drift back), Mining (hard block/run → Haste II),
        **Knockback** (surrounded by ≥`guardianSurroundCount` → PINBALLS between them flinging them away with
        `guardianKnockbackForce` to clear space), Kidnap (a hostile/player that HITS you is hauled up
        `guardianKidnapDropHeight` and dropped — INTO lava within `guardianKidnapLavaRadius`; a kidnapped player
        force-`startRiding`s the allay; `guardianKidnapCooldown`, else Frantic), **Fireworks** (killing the LAST
        nearby hostile → chance to dart around firing custom blue+yellow rockets).
      - **INTERACTIONS (movement Mode):** Sleep-watch (calm circle above you asleep) → **Bed guardian** (if
        hostiles are near while you sleep, darts around SHOVING every nearby entity away), Boredom (idle ~10s →
        perches above one shoulder and SETTLES still), Stealth (crouch → hangs low, trails+ambient suppressed),
        Dance (spam-crouch), Curiosity (bee/villager/flower), Frantic (hit + can't kidnap).
      - **Light-keeper + Ore highlight (frequent, not the slow scheduler):** while it's dark/night it FLIES a
        torch out to a dark spot (`guardianTorchIntervalTicks` between errands, `guardianTorchSpacing` apart) and
        PLACES it on arrival — one at a time, physically delivered, not teleport-spawned; caves stay lit over
        time. Going near a diamond / ancient-debris / emerald ore makes it fly over and set off blue+yellow
        fireworks (deduped per block). ⚠ true per-entity dynamic LIGHT isn't vanilla-possible without a
        dynamic-lighting mod/mixin — the torches are the workaround.
      - **Server optimisation (many guardians):** the constant halo + trail render CLIENT-side
        (`ClientCurseHandler.tickGuardianFx`) off a synced `GUARDIAN_RENDER` kind-code — no per-tick particle
        packets, and the trail no longer lags the model. The heavy entity/block SCANS (lock-on, zap, anti-grief,
        anti-fire, phantom, arrows, restock, blinding) run on a **per-player-staggered 5-tick beat**
        (`(now+entityId)%5`), and guiding/dedupe/ore are staggered too — so a server full of guardians isn't
        scanning every one every tick; the cheap time-critical checks (dampen, catch, air, fishing) stay per-tick.
      - **AMBIENT gifts (scheduled):** the three "on-you" gifts (Buffs / XP / Durability) are SCHEDULED with a
        `GIFT_WINDUP` (44t) **gather** — the allay spirals inward with thickening light before it lands, so they
        never feel random; the fly-to-a-target gifts already read as deliberate. Buffs (1–3 random effects
        `guardianBuffMin/MaxSeconds` 15–120s), XP
        bottles, Torch in the dark, Mark (nearest entity → Glowing), Durability (25% to all tools), Caretaker
        (heal/Regen pets), **Planter** (bonemeals a nearby plant straight to maturity), **Push** (gently flings
        nearby passive animals about, "playing"), and the RARE **blessing gift** — `guardianRareGiftChance`,
        ONCE per blessing (persisted `GUARDIAN_GIFT_USED`, shown in the Scrying Mirror), a random LOW-power
        blessing for 30–60min.
      - Debug: all events forcible + TAB-COMPLETE via `Effect.debugArgs()` — `dampen|antifire|air|catch|escort|
        phantom|purge|cleanse|hunger|heal|antigrief|kidnap|lift|knockback|blinding|guiding|sacrifice|fireworks|arrows|
        frantic|dance|curiosity|buffs|xp|torch|lightkeeper|ore|mark|durability|pets|mining|restock|planter|push|
        gift`. ~46 knobs. Sacrificial item **Golden Apple** (cheaper than the old Elytra). Sound
        `witchmod:blessing.guardian.zap` ✅ SUPPLIED. ✅ SIGNED OFF (Oliver may still add "more events").
        Deliberately NOT done: a custom angel MODEL/renderer (kept the vanilla Allay + halo shimmer — a bigger
        art/entity job if Oliver wants it). "More events to come" (Oliver).
- [x] Photosynthesis — **Sunflower.** In direct daylight you slowly regen + gain hunger; standing in WATER
      while sunlit makes it stronger (Regen II). The sunny opposite of Basement Dweller. 1 knob.

**NEUTRALS & GLOBALS — ❌ CUT ENTIRELY.** The whole event system was deleted: the `events/` package, the
Event registry, the Afflicted status subsystem (`AfflictionManager`/`ActiveAfflictions`/`afflicted` effect),
`/bewitch event`+`forcestop`, the globals config, the ritual's neutral-failure fallback (now a plain
fizzle), and the Compendium's Events section. The Mirror BACKFIRE survives (always inline in the ritual). The
retired `CurseMansplainer` was also deleted.

**ITEMS (Section 4)** — start only after every attachment above is checked off. (§4 has the spec; the notes
below are current implementation status.)
- [x] Cursed Essence — currency (smelt cursed items / amethyst). **Table now accepts a Block of Cursed Essence
      in the essence slot worth 9 each** (so you can invest >64 at once; `BewitchingTableRitual.essenceValue`),
      and it **only spends what the odds need** (essence == cost = the 95% cap) — the rest is refunded as
      blocks + loose remainder, so a whole stack is never swallowed. `RitualSlot`/client success-bar share the
      block-aware value. Compendium shows the SMELT recipe FIRST (over the block-uncraft). Signed off.
- [x] Player Essence — `PlayerEssenceItem`, `stacksTo(1)`, glint when bound. Tooltip names the target
      (offline-aware) + online/offline line. Per-UUID COLOUR (client item-model property `witchmod:essence_colour`
      → one of 16 recolour textures). Three JAR gestures (`PlayerEssenceEventHandler`): crouch+look-down = own ·
      right-click a "clean" player = theirs (a cursed/blessed target does the Jar's capture instead) · right-click
      a BED = its spawn-owner, offline-aware via `BedSpawnRegistry`.
- [x] Compendium — custom `CompendiumScreen`: a two-page book with a Chapters sidebar (Curses/Blessings/
      Modifiers/Items/Blocks/Rituals), one entry per page (name, category, sacrificial-item icon, 0–100 power
      pips read from `PowerLevels`, scrollable description; recipe/durability for items/blocks pulled live from
      the RecipeManager). Undiscovered = RUMOUR pages (hidden item, aged parchment, `.rumour` hint). Discovery
      synced via `DISCOVERED_EFFECTS`/`DISCOVERED_MODIFIERS`. Descriptions/rumours/intros are editable
      `witchmod.compendium.*` lang keys (placeholders for now — Oliver writes the real copy).
- [x] Voodoo Doll — spec in §4. PASSIVE redirect: while a bound doll is in your inventory, any CURSE you cast
      at the Table is forwarded onto the doll's target instead (1 durability; `voodooDollDurability` 12; offline/
      dead → no forward). Bind = Player Essence off-hand + use the doll (or a `VoodooBindRecipe` in the grid).
      Sympathetic interactions (all `/bewitch debug voodoo`-able): needle stab (armour-SCALING `witchmod:voodoo`
      dmg), throw + fishing-rod (real fling/yank of the victim), squeeze (ramping choke), feed (off-hand food),
      lightning (real bolt), water/rain, freeze (powder snow), inventory pin, shake (Nausea), potion-cloud,
      arrow-stick (real arrow dmg). A bound doll also works in the Table target slot like an essence. **TRACKER
      (2026-09-06):** holding a bound doll points a subtle WITCH mote toward the target when they're ONLINE, in
      the same dimension and within `voodooTrackRadius` (180) — sent your-eyes-only, server-side, so it works at
      any range. ⏳ tracker awaits late-stage multiplayer testing but signed off on Oliver's call.
- [x] Needle — right-click with a bound doll → the armour-scaling `witchmod:voodoo` jab (see Voodoo Doll).
- [x] Ward — a durability BLOCKER: stops ANY attachment cast on you by someone ELSE (self-casts pass). On a
      block, `ItemWard.onBlock` fires a colour-coded incoming LASH (purple curse / gold blessing) caught with a
      SHIELD_BLOCK clang, spending 1 of 8 durability (gated on `wardDurabilityDecays`). **(2026-09-08)** holding a
      ward now also grants the **Protected** MobEffect (icon only, no particles) so you can see it's working —
      `ItemWard.inventoryTick` refreshes it. This routes the ward block THROUGH `EffectManager`'s protected branch:
      `isMagicProtected` short-circuits, but a held ward → `onWardBlock` (spends durability + lash) unless Netherite
      `bypassWard`; a non-ward Protected (totem/holy water) → the unbypassable fizzle. Ward wears out per block →
      the Protected icon fades with it.
- [x] Scrying Mirror — right-click self, or a player, to reveal active curses/blessings as a styled
      `ScryingOverlay` panel (name + seconds-left + `scryingDetail` specifics), and instantly DISCOVERS what it
      reveals. `scryingDetail` implemented on ~25 effects (some flavour via `@`-prefixed lang keys). **UI restyle
      (2026-09-08):** the `ScryingOverlay` panel now uses the Compendium's parchment palette (brown frame + cream
      page + ink text) to match the other UIs; long names are ellipsis-CLIPPED (leaving room for the timer) and
      long detail lines WRAP (`font.split`) with the panel sized to the real content height, so nothing trails off
      the edge any more. **Recipe = 3× cobbled deepslate columns L/R, diamond centre, cursed essence above+below it.**
- [x] Effigy — short SEQUENCE (`SpellSequences.effigy`): on a PLAYER, lifts your curses off, streams a
      colour-coded ribbon across, lands them on the victim (durations preserved) with `spell_glint`+`lash_spawn`.
      **(2026-09-11) The debug villager path was REMOVED** — it only forwards onto players now. **Recipe = a
      Totem of Undying surrounded by cobbled deepslate with cursed essence above the centre totem.**
- [x] Cursed / Blessed / Executioner's Coin — `ItemGambleCoin` + `SpellSequences.coin`: rolls first
      (`CoinGamble.roll`), plays `coin_flip`, BREAKS into 1–3 colour-coded puffs, then applies to the user a
      beat later. Table-cast coin path (`castCoin`) unchanged. **Recipes (2026-09-08): Blessed = iron block
      surrounded by cursed essence · Cursed = gold block surrounded by cursed essence · Executioner's = any
      copper block (incl. all waxed/weathered variants) surrounded by cursed essence** — so Blessed/Executioner's
      are now craftable, not loot-only.
- [x] Jar / Cursed Jar / Blessed Jar / Mixed Jar — spec in §4.1. One dynamic `ItemJar`; the variant is derived
      from contents; fills at the Table (target slot) up to `JarContents.MAX` (3) and refuses a full jar; throw =
      splash-all + lash. Splash goes through `EffectManager.apply`, so it respects the 3-per-category hard cap.
      Signed off — no further functionality needed. **Recipe = iron ingot** (was netherite). The three TYPED
      placeholder variants (Cursed/Blessed/Mixed) now **auto-fill** with one of the predetermined NAMED jars of
      their variant (weighted by rarity, via `NamedJars.pickForKind` + `fillStack`) the moment they turn up empty
      in a player's inventory (`ItemJar.inventoryTick`) — so a jar pulled from the creative menu becomes a real
      "jar of X" and is throwable (the plain empty JAR is left alone — it's the essence tool).
- [x] Amethyst Bell — **REWORKED 2026-09-09 into an AOE multiplayer gambling block** (ring FX overhauled
      2026-09-11). Right-click RINGS it: the bell swings (`blockEvent`), a dramatic toll rolls, a bright central
      burst fires (NO fireworks), and an **expanding pink shockwave** ripples out along the ground (animated over
      16 ticks in `AmethystBellBlockEntity.tick`). Each player within `AOE_RADIUS` (8) hears the golden-apple-to-
      a-zombie-villager toll (`ZOMBIE_VILLAGER_CURE`) and their **fate is DECIDED at ring time**
      (`AmethystBellEffects.decide` → SWAP / ADD / FIZZLE). Then over a **3-second ramp** (`APPLY_RAMP_TICKS` 60)
      the player is progressively "consumed" by purple/amethyst dust (quadratic ramp) with a **ramping camera
      shake** (reuses `AMETHYST_BELL_SHAKE_END`, window scaled by progress), before `AmethystBellEffects.apply`
      resolves it. A pre-decided FIZZLE instead runs a **much lighter 1.5s ramp** (`FIZZLE_RAMP_TICKS` 30) then
      poofs with no effects. Outcomes:
      • **No attachments** → 30% chance to be GIFTED a batch (50%/35%/15% for 1/2/3), each a random category
        (50/50 curse/blessing) at a rolled power band (40% low 0-40 · 45% mid 41-70 · 15% high 71-85), 30-60 min.
      • **Has attachments** → each is SWAPPED for another of the SAME category at a similar power (weighted by
        nearness to a target power, so slight randomness), with a 15% HIGH roll (bumps power up toward 90) and a
        15% LOW roll (crashes it, even to 0); the remaining duration is preserved (`applyExact`).
      Power uses `Effect.powerLevel()`; effects are picked by inverse-square distance to the target power.
      Protection is BYPASSED on purpose (`ApplyOptions.DIRECT_HIT`, like a thrown jar) — it's an area ritual;
      the per-category hard cap is still respected on adds. Internal states (infectious) are never re-rolled.
      **After ANY ring EVERY bell in the dimension goes INACTIVE for 30 min** (`bellInactiveTicks`): a genuine
      per-dimension cooldown (2026-09-12) — you can't dodge it by walking to a second bell or by breaking and
      replacing one. Inactive bells are fully unresponsive (right-click does nothing) and the renderer swaps the
      bell body to `entity/amethyst_bell_inactive.png` (a desaturated bell). The recharge is an ABSOLUTE
      game-tick, **persisted + reload-/replace-proof**: the authority is a **per-dimension** `AmethystBellData`
      SavedData holding ONE `until` tick (was a per-position map); each bell's block entity mirrors it into
      `inactiveUntil` (synced via `getUpdateTag`/`getUpdatePacket`, reconciled on a throttled server tick so ALL
      bells grey/un-grey together) purely for the client render. `setPlacedBy` greys a freshly-placed bell if the
      dimension is still recharging.
      **FX are CLIENT-rendered (2026-09-11) so a ring never lags the server:** the central burst + expanding
      shockwave run in `AmethystBellBlockEntity.tick` via `addParticle` (started by the ring block-event, which
      fires both sides), and the per-player "consume" purple dust renders in `ClientCurseHandler.tickBellConsume`
      off the synced `AMETHYST_CONSUME_END`/`_FIZZLE` attachments (set once at ring). The server sends NO bell
      particles — it only drives the ramping camera shake and resolves the gamble. No fireworks; no END_ROD in
      the shockwave (flash / pink+amethyst dust / witch swirls only). Messages translatable
      (`witchmod.amethyst_bell.gifted`/`.rerolled`/`.nothing`).
      **(2026-09-12) The AOE/recharge/ramp/shake/power/chance/weight numbers are now all `Config` knobs**
      (`bellAoeRadius`, `bellInactiveTicks`, `bellApplyRampTicks`, `bellFizzleRampTicks`, `bellShake*`,
      `bellAddChancePercent`, `bellAdd{One,Two}Weight`, `bellPower{Low,Mid}Weight`, `bellSwap*`); gift duration
      reuses the ritual's `ritualMin/MaxDurationTicks`. Desaturated texture supplied by Oliver.
- [x] Recovery Compass (modifier) — removed from the creative tab (it's backed by the vanilla item). Still
      usable as a modifier.

**BLOCKS (Section 3)** — start only after every item above is checked off.
- [x] Bewitching Table (**displays as "Ritual Table"**) — ✅ SIGNED OFF 2026-09-11 (Oliver still owns the
      texture art). Working: cast via a C2S
      `RitualCastPayload`; textured UI (`bewitching_table.png` + per-slot sprites); live success bar; red
      wrong-item slots; Cast disabled until valid; essence-as-currency success/backfire with tier-aware backfire
      (`applyTierBackfire`); SUCCESS or FAILURE outcomes (failure is usually "nothing", real backfire only on the
      tier-scaled chance); six backfire penalties (`doBackfire`); honest apply reporting (only celebrates a real
      landing). Custom BLOCK MODEL: a deepslate pillar + top slab with carpet drapes + gated decorative candles
      (multipart `CAPPED` blockstate — placing a block on top drops the candles), all 5 editable 16×16 textures.
      **Essence slot takes Blocks of Cursed Essence (=9 each) and only spends what the odds need** — block spend
      rounds UP to whole blocks (no loose refund), loose essence refunds exactly (§4 Cursed Essence). **Outcome FX
      overhauled 2026-09-06 and moved CLIENT-side** (tiny `RitualFxPayload` → `RitualFxClient`; server keeps only
      the sounds) so heavy particle events don't lag weak servers: SUCCESS = the sacrificial item floats up over
      the table ringed by 3 accelerating gold/purple orbs, then a lash streaks off toward the target and, after a
      1–4s delay, a second lash homes INTO the target (a "magic missile"; self-casts get no lash). FIZZLE = a weak
      red+smoke puff. BACKFIRE = a messy red burst from the table (PURPLE when it's the `backfire_mirror` — the
      attachment landing on the caster). **(2026-09-11) The dead server-side `RitualFx` circle/lash class was DELETED.**
      **Duration (2026-09-08):** a cast now rolls a RANDOM base 35–60 min (config `ritualMin/MaxDurationTicks`;
      spread Infectious uses `ritualInfectiousDurationTicks` — all lifted into `Config` 2026-09-12) instead of
      a flat 45; the **Clock** modifier now overrides it to a fixed 45 min (via `Modifier.fixedDurationTicks`,
      like Compass's fixed 40 — its old flat +5–15 min bonus is retired). **User-facing text is now translatable**
      (`witchmod.ritual.*` lang keys — every table/coin/backfire message), and the caster gets **hit feedback**:
      on a land, "succeeds — X on Y! (lasts ~N min)"; on a block, "Blocked — Y is protected"; on grace/disabled,
      "won't take on Y". **Eye of Ender modifier ("Test the Waters")** chat-reports what shielded a blocked cast
      (`reportProtections` → Ward / Warding Totem / Holy Water). NEXT: Oliver edits textures + in-game tuning of the FX.
- [x] Block of Cursed Essence — ✅ SIGNED OFF 2026-09-11. A 9× Cursed Essence storage block; also accepted in
      the Ritual Table essence slot worth 9 each (`BewitchingTableRitual.essenceValue`), craftable both ways
      (`cursed_essence_block.json` / `cursed_essence_from_block.json`). NOTE: §3's "Global bank currency unit"
      description is STALE — globals were cut; it's now just storage + bulk essence.
- [x] Ledger — right-click opens a custom scrollable `LedgerScreen` (parchment, newest-first) of nearby ritual
      activity (caster→target, effect+modifier, result, "Xm ago"; scribbled Paper casts obfuscated), range
      `Config.LEDGER_RANGE` (24). Every successful cast pulses ENCHANT particles into nearby Ledgers
      (`LedgerFeedback`) + `ledger.write`. DIRECTIONAL (a `FACING` block); renders a 3D purple book on top like a
      lectern (`LedgerBlockEntity`+`LedgerRenderer`). Reskinned onto editable `block/ledger_*` textures.
- [x] Warding Totem — a slim themed amethyst obelisk (blackstone/deepslate + a crowning amethyst crystal spike
      crown + ragged core band; editable textures; right-click toggles `enabled` on/off). Applies the global
      **Protected** MobEffect to players within `Config.WARDING_TOTEM_RANGE` (32) of an ENABLED totem; the gate
      is `EffectManager.isMagicProtected` so `/effect protected` works the same. Blocks OTHERS' magic (self-casts
      + direct jar splashes pass; lashes/voodoo blocked; blocked casts fire a force-field dome + Ledger-log
      `blocked`). **Fix (2026-09-06): totems re-register on `ChunkEvent.Load`** (scanning non-empty sections) —
      the in-memory registry was empty after a restart/`/reload`, so nobody got shielded. The Protected effect is
      applied with NO particles of its own (showParticles=false, icon only); the only player particles are a
      one-shot ring of **magenta** motes + a chime when you CROSS INTO or OUT OF the radius (or it's toggled).
      Item tooltip (`item.witchmod.warding_totem.desc`).
- [ ] Purifying Water (Holy Water) — ⏳ IN PROGRESS. CUSTOM light-blue shimmering fluid (editable still/flow
      PNGs + star sparkles, signed off); minimal flow (`levelDecreasePerBlock` 4), lowest-priority (won't
      replace foreign fluids), no infinite source, no drowning; in the vanilla water tag (real swim physics).
      Bathing (`HolyWaterHandler`): shine particles + burns down attachment timers (`PURIFY_DRAIN_TICKS_PER_TICK`,
      synced so the on-screen timer shrinks) + is a SANCTUARY (applies Protected). Hurts undead; cleanses filled
      jars + bound voodoo dolls (`HolyWaterItems`). Cauldrons: a `PURIFYING_WATER_CAULDRON` (bucket in/out); an
      amethyst shard in a WATER cauldron consecrates it (stacking `PURIFY_SHARD_CHANCE`). Bucket tooltip
      (`item.witchmod.purifying_water_bucket.desc` — "washes away any curse or blessing"); **fully-submerged
      visibility is much clearer than ordinary water** (`onRenderFog` pushes the fog planes out when the camera's
      fluid is `PURIFYING_WATER_TYPE`). **(2026-09-08) The crafting recipe was REMOVED** (`purifying_water_bucket.json`
      deleted) — holy water is made ONLY by imbuing a water cauldron with an amethyst shard. The Compendium's item
      page explains this in its recipe slot (`witchmod.compendium.holy_water.recipe_note`, drawn by
      `CompendiumScreen` when the bucket has no recipe). **(2026-09-11) Functionally COMPLETE — kept `- [ ]`
      only because the fluid TEXTURE is Oliver's own art job (his call); no code work remains.**

---

### 16.4 CLASS-BY-CLASS CLEANUP CHECKLIST

The live source of progress for the §16 cleanup goal. Every non-trivial class in `src/main/java` (336 of
them; `package-info` excluded). Work a package at a time; tick a class `- [x]` once it has been: unnecessary
code cut · reusable logic shared · hot paths/packets checked (no crash either side, no server clog) · comments
brought to the house rules · CLAUDE/CLAUDE.MD refs removed. Append a terse `— note` on anything non-obvious
you changed or deliberately left.
_(Section-header counts are the class totals; none ticked yet.)_

#### Root (WitchMod, Config, client entrypoints)  (4)
- [x] ClientConfig — blurb → house style.
- [x] Config — blurb + the two `//` section comments → house style; all CLAUDE/Phase/section refs purged. Kept the ~1100 `.comment()` strings as full sentences (they're user-facing TOML docs, not code comments).
- [x] WitchMod — MDK template comments stripped, blurb added, comments lowercased; removed the "HELLO from server starting" / "common setup complete" template logs.
- [x] WitchModClient — MDK template noise + "HELLO FROM CLIENT SETUP"/username logs removed (dropped the now-unused Minecraft import), blurb added, comments → house style.

#### network  (1)
- [x] WitchModNetwork — comments → house style; every packet doc now says c2s/s2c + intent; noted c2s payloads are re-checked server-side.

#### mixin  (2)
- [x] ListenerMixin — blurb trimmed to house style.
- [x] ShieldRaiseMixin — blurb trimmed to house style.

#### ui (Ritual Table screen/menu)  (4)
- [x] BewitchingTableMenu — blurb corrected (cast is c2s payload; clickMenuButton a fallback) + comments lowercased.
- [x] BewitchingTableScreen — blurb/comments → house style.
- [x] RitualSlot — comments → house style.
- [x] WitchModMenus — blurb → house style.

#### data (registries, EffectManager, attachments, text lists)  (48) ✅ ALL DONE
_Comment/blurb → house style; all CLAUDE/section/Phase/master-spec refs purged. text-list loaders condensed to one-line blurbs. Effect + EffectManager + Modifier + WitchModAttachments blurbs/refs fully rewritten; the dense registry/one-liner docs (WitchModSounds, WitchModDamageTypes, etc.) leading-lowercased. Compiles clean._
- [x] ActiveEffectInstance
- [x] ActiveEffects
- [x] BedSpawnRegistry
- [x] BodyguardLines
- [x] CapturedEffect
- [x] CoinGamble
- [x] DelayedCasts
- [x] DiscoveryManager
- [x] EchoesChatMessages
- [x] Effect — base-class docs fully lowercased + trimmed.
- [x] EffectCategory
- [x] EffectCostTier
- [x] EffectManager — blurb/apply doc rewritten (no-stacking + cap note kept), refs purged, dropped detached orphan comments.
- [x] EffectUtil
- [x] GracePeriod
- [x] HypeManMessages
- [x] InsomniacMessages
- [x] LedgerLog
- [x] MarriageLines
- [x] Modifier
- [x] ModifierCalculator
- [x] ModifierItems
- [x] NarratorLines
- [x] OrganisedStash
- [x] OversharerMessages
- [x] PacingManager
- [x] PlayerEssenceData
- [x] PowerLevels
- [x] SacrificialItems
- [x] SolicitorLines
- [x] SpellSequences
- [x] StatusEffectSync
- [x] TaxBank
- [x] TaxManLines
- [x] TaxValues
- [x] TwitchChat
- [x] Usernames
- [x] WitchModAttachments — blurb added; CLAUDE/Phase refs purged; one-liner attachment docs leading-lowercased.
- [x] WitchModDamageTypes
- [x] WitchModDataComponents
- [x] WitchModDataReload
- [x] WitchModEventHandler
- [x] WitchModMobEffects
- [x] WitchModParticles
- [x] WitchModRegistries
- [x] WitchModSounds — blurb + refs; one-liner docs leading-lowercased.
- [x] WitchModTags
- [x] YapMessages

#### blocks (Ritual Table, Amethyst Bell, Ledger, Warding Totem, Holy Water)  (20) ✅ ALL DONE
_Blurbs/comments → house style, all CLAUDE/section/Phase/master-spec refs purged. The 4 bell blurbs (written pre-rule) rewritten lowercase; fixed a stale "all particles server-sent" line in AmethystBellEffects (they're client-rendered now). Compiles clean._
- [x] AmethystBellBlock
- [x] AmethystBellBlockEntity
- [x] AmethystBellData
- [x] AmethystBellEffects
- [x] BewitchingTableBlock
- [x] BewitchingTableBlockEntity
- [x] BewitchingTableRitual — blurb + all CLAUDE/section refs purged; docs leading-lowercased.
- [x] HolyWater
- [x] HolyWaterCauldron
- [x] HolyWaterHandler
- [x] HolyWaterItems
- [x] LedgerBlock
- [x] LedgerBlockEntity
- [x] LedgerFeedback
- [x] PurifyingWaterBlock
- [x] PurifyingWaterFluid
- [x] WardingTotemBlock
- [x] WitchModBlockEntities
- [x] WitchModBlocks
- [x] WitchModFluids

#### items  (21) ✅ ALL DONE
_Blurbs/comments → house style, all CLAUDE/section refs purged, one-liner docs leading-lowercased. Compiles clean._
- [x] BoundPlayerItem
- [x] EssenceTooltipClient
- [x] ExecutionersCoinEventHandler
- [x] ItemCompendium
- [x] ItemEffigy
- [x] ItemGambleCoin
- [x] ItemJar
- [x] ItemNeedle
- [x] ItemScryingMirror
- [x] ItemVoodooDoll
- [x] ItemWard
- [x] JarContents
- [x] JarEffects
- [x] PlayerEssenceEventHandler
- [x] PlayerEssenceItem
- [x] VoodooBindRecipe
- [x] VoodooDollHazards
- [x] VoodooDollInteractions
- [x] WardEffects
- [x] WitchModItems
- [x] WitchModRecipes

#### entities  (10) ✅ ALL DONE
_Blurbs → lowercase house style, all master-spec/section refs purged, one-liner docs leading-lowercased. Compiles clean._
- [x] BodyguardEntity
- [x] CloneEntity
- [x] DreamEntity
- [x] JarThrowEntity
- [x] SnailEntity
- [x] SpaghettiManEntity
- [x] TaxManEntity
- [x] WatcherEyesEntity
- [x] WitchModEntities
- [x] WitchModEntityAttributes

#### effects (registries + event handlers)  (5)  ✅ ALL DONE
- [x] BlessingEventHandler
- [x] Blessings
- [x] CurseEventHandler
- [x] Curses
- [x] ProjectileBlessingHandler

#### effects/curses  (70)  ✅ ALL DONE
- [x] CurseAllergic
- [x] CurseAudit
- [x] CurseBackseatDriver
- [x] CurseBadSwimmer
- [x] CurseBasementDweller
- [x] CurseBodySwapping
- [x] CurseBouncy
- [x] CurseBrokenBonds
- [x] CurseButterfingers
- [x] CurseCarelessness
- [x] CurseChannels
- [x] CurseClaustrophobia
- [x] CurseClumsy
- [x] CurseComicRelief
- [x] CurseCutawayGag
- [x] CurseDelusions
- [x] CurseDense
- [x] CurseDwarfism
- [x] CurseEchoes
- [x] CurseExplosive
- [x] CurseFarmhand
- [x] CurseFlatFooted
- [x] CurseFloorIsLava
- [x] CurseGassy
- [x] CurseGiant
- [x] CurseGlassCannon
- [x] CurseGluttony
- [x] CurseHeavy
- [x] CurseHeavyHanded
- [x] CurseHeavyweight
- [x] CurseHiccups
- [x] CurseIceSkates
- [x] CurseInfectious
- [x] CurseInsomniac
- [x] CurseLeftHanded
- [x] CurseLightweight
- [x] CurseLoadingScreen
- [x] CurseMagnet
- [x] CurseMinorInconvenience
- [x] CurseMoonwalker
- [x] CurseMunchies
- [x] CurseNarcolepsy
- [x] CurseNarrator
- [x] CurseNeutralAggression
- [x] CurseOversharer
- [x] CursePacing
- [x] CursePests
- [x] CursePopularity
- [x] CurseRepel
- [x] CurseScreensaver
- [x] CurseSirensCall
- [x] CurseSlipperyFeet
- [x] CurseSnail
- [x] CurseSocialOutcast
- [x] CurseSolicitor
- [x] CurseSplitscreen
- [x] CurseSpotlight
- [x] CurseStickDrift
- [x] CurseSticky
- [x] CurseSuperExplosive
- [x] CurseThirstMeter
- [x] CurseTrumpet
- [x] CurseUgly
- [x] CurseUnhygienic
- [x] CurseVertigo
- [x] CurseVeryInfectious
- [x] CurseViolence
- [x] CurseWonky
- [x] CurseYap
- [x] EnvBurn

#### effects/curses/bedrock  (3)  ✅ ALL DONE
- [x] BedrockEvent
- [x] BedrockEvents
- [x] CurseBedrockMoment

#### effects/curses/dweller  (5)  ✅ ALL DONE
- [x] CurseTheDweller
- [x] DwellerEvent
- [x] DwellerEvents
- [x] LightsOutEvent
- [x] WhisperEvent

#### effects/goals  (3)  ✅ ALL DONE
- [x] BrokenBondsFleeGoal
- [x] FarmhandBlockGoal
- [x] UnhygienicFleeGoal

#### effects/blessings  (76)  ✅ ALL DONE
- [x] BlessingAnchor
- [x] BlessingAngler
- [x] BlessingArmy
- [x] BlessingBackstabbing
- [x] BlessingBerserker
- [x] BlessingBlacksmith
- [x] BlessingBloodhound
- [x] BlessingBodyguard
- [x] BlessingBrute
- [x] BlessingBuilder
- [x] BlessingChat
- [x] BlessingCollector
- [x] BlessingConfusion
- [x] BlessingCow
- [x] BlessingCoyote
- [x] BlessingDexterous
- [x] BlessingDisguise
- [x] BlessingDrive
- [x] BlessingEnchanter
- [x] BlessingExcavation
- [x] BlessingFarmersSpirit
- [x] BlessingFlight
- [x] BlessingForgiveness
- [x] BlessingFortune
- [x] BlessingFullness
- [x] BlessingGladiator
- [x] BlessingGuardianAngel
- [x] BlessingHawkGuy
- [x] BlessingHeavyHitter
- [x] BlessingHomebody
- [x] BlessingHotStuff
- [x] BlessingHypeMan
- [x] BlessingImmortality
- [x] BlessingIronLung
- [x] BlessingIronStomach
- [x] BlessingJesus
- [x] BlessingLastStand
- [x] BlessingLaughTrack
- [x] BlessingLeader
- [x] BlessingLowGravity
- [x] BlessingLuck
- [x] BlessingMainCharacter
- [x] BlessingNightowl
- [x] BlessingNinja
- [x] BlessingOceansBlessing
- [x] BlessingOrganised
- [x] BlessingPacifier
- [x] BlessingPayday
- [x] BlessingPeace
- [x] BlessingPhotosynthesis
- [x] BlessingPickpocket
- [x] BlessingPropHunt
- [x] BlessingReflect
- [x] BlessingRestock
- [x] BlessingSafety
- [x] BlessingSanguine
- [x] BlessingSilverTongue
- [x] BlessingSixthSense
- [x] BlessingSonar
- [x] BlessingSoulBond
- [x] BlessingSpeed
- [x] BlessingSpeedDemon
- [x] BlessingSpelunking
- [x] BlessingSpider
- [x] BlessingStudious
- [x] BlessingTank
- [x] BlessingThickSkinned
- [x] BlessingThunder
- [x] BlessingTrainer
- [x] BlessingTwinkletoes
- [x] BlessingTwistOfFate
- [x] BlessingUnderdog
- [x] BlessingUnseen
- [x] BlessingVeinMiner
- [x] BlessingWindfall
- [x] BlessingWorkman

#### client (renderers, HUDs, overlays, sound instances)  (64)  ✅ ALL DONE
_Blurbs/comments → house style, all CLAUDE/master-spec/Phase/section refs purged, one-liner + inline docs leading-lowercased (ALL-CAPS-safe). The 7 ref-carrying blurbs (BodyguardRenderer, LoadingScreenOverlay, DelusionPlayer, ScreensaverState, ClientCurseHandler, HudBars, package-info) rewritten by hand. Compiles clean._
- [x] AmethystBellRenderer
- [x] BedrockClientBugs
- [x] BodyguardModel
- [x] BodyguardRenderer
- [x] CarelessnessHud
- [x] ChatOverlayLayer
- [x] ClientCurseHandler
- [x] CloneRenderer
- [x] CompendiumScreen
- [x] CutawayLoopSound
- [x] CutawayTitles
- [x] DelusionManager
- [x] DelusionPlayer
- [x] DisguiseClient
- [x] DreamRenderer
- [x] DwellerBreathingSound
- [x] DwellerMimicManager
- [x] FakeBsodScreen
- [x] FakeKickScreen
- [x] FlightBarLayer
- [x] FlightClient
- [x] GladiatorClientHandler
- [x] GladiatorParryLayer
- [x] GluttonyHudLayer
- [x] HudBars
- [x] ImmortalityRecoveryOverlay
- [x] LedgerRenderer
- [x] LedgerScreen
- [x] LoadingScreenOverlay
- [x] LoadingScreenState
- [x] MainCharSoundInstance
- [x] MainCharSoundManager
- [x] MarketplaceAdScreen
- [x] NarcolepsyClient
- [x] NarratorClient
- [x] NightcoreSoundInstance
- [x] OrganisedButton
- [x] ReviveFlashOverlay
- [x] RitualFxClient
- [x] ScreensaverState
- [x] ScryingOverlay
- [x] SirenShaderOverlay
- [x] SleepZParticle
- [x] SnailModel
- [x] SnailRenderer
- [x] SnailSoundInstance
- [x] SnailSoundManager
- [x] SpaghettiManEyesLayer
- [x] SpaghettiManModel
- [x] SpaghettiManRenderer
- [x] SpeedDemonClient
- [x] SplitscreenClient
- [x] SplitscreenPov
- [x] SunglassesLayer
- [x] TaxManRenderer
- [x] ThirstHudLayer
- [x] TrumpetSoundInstance
- [x] TrumpetSoundManager
- [x] UglySkinManager
- [x] VoodooClient
- [x] WatcherEyesEyesLayer
- [x] WatcherEyesModel
- [x] WatcherEyesRenderer
- [x] WindowTitles

---

## 17. HUMAN ACTION ITEMS (living list — Claude Code appends during work)

**Art — Items:** Cursed Essence, Player Essence, Compendium, Voodoo Doll, Needle, Ward, Scrying
Mirror, Effigy, Cursed/Blessed/Executioner's Coins, Jar, Cursed Jar, Amethyst Bell retexture.

**Art — Blocks:** Bewitching Table, Block of Cursed Essence, Ledger, Warding Totem,
Purifying Water (fluid texture + animation).

**Art — GUI (Section 13.5 guide).** Status as of Phase D — every HUD/overlay is BUILT and FULLY FUNCTIONAL
with placeholder visuals drawn in code; each is a drop-in swap once the PNG exists (no code/JSON change
needed beyond pointing the layer at the texture). Namespace note: paths below are written with the
`bewitchment` placeholder namespace, but the actual mod namespace is `witchmod` — real assets go under
`assets/witchmod/textures/...`.
- `bewitching_table.png` — Table screen background. STILL PLACEHOLDER: `BewitchingTableScreen.renderBg`
  draws the panel/slots/bar/button as `GuiGraphics.fill` rectangles. Swap = one `blit` in `renderBg`.
- `compendium` book + per-attachment page images — the Compendium is a written-book (no custom page art
  yet); per-attachment page images are a later enhancement, not blocking.
- `thirst_icons.png` — 10-icon droplet set (full/half/empty). Currently `ThirstHudLayer` draws blue
  filled rectangles. Swap = `blitSprite`/`blit` in `ThirstHudLayer`.
- `gluttony_icons.png` — NOT NEEDED as custom art: `GluttonyHudLayer` reuses the vanilla `hud/food_*`
  sprites (per Oliver's call, it's literally a second hunger bar). Only make a custom sheet if a distinct
  look is wanted later.
- `loading_screen.png` — spinner + frame. Currently `LoadingScreenOverlay` draws a black fill + a text
  spinner + tip. Swap the spinner/frame in that layer.
- `twitch_overlay.png` — Chat panel frame. Currently `ChatOverlayLayer` draws a translucent purple panel +
  text lines. Swap the panel frame in that layer.
- (No sprite needed for the Organised extra slots — it opens as a vanilla 1-row chest.)

**Art — Skins/Entities:** Ugly skin pool (>=3, at
`assets/bewitchment/textures/entity/skins/ugly/`), Tax Man skin, Bodyguard sunglasses-skeleton
texture, Woolliam (vanilla sheep + name tag — no art), Delusion fake players (reuse real skins —
no art).

**Audio:** 24 OGGs per Section 12.

**Writable text lists (data/bewitchment/text/):** yap, echoes_chat, window_titles, oversharer,
mansplainer (+ Mansplaining tips), insomniac, loading_tips, bodyguard, hypeman, twitch_chat,
letters. Loot: windfall.json. Trades: useless.json.

**Decisions owed (Section 11):** ~~Firework Star collision (Main Character vs Celebration).~~ RESOLVED —
Main Character moved to Fire Charge; Celebration keeps the Firework Star. (Nothing owed here now.)

---

## 18. NAMED JAR DROPS + ATTACHMENT SYNERGIES

Two related systems added after the §16.4 cleanup (both follow the house comment rules).

### 18.1 Named jars (`com.oliver.witchmod.loot`)
Predetermined "Jar of X" presets — a named jar holding a FIXED set of effects — that drop from loot chests
and certain mob kills. The whole set lives in `NamedJars` as one-line `register(path, rarity, effects...)`
entries (rarity 1 = common .. 10 = rarest; it ONLY sets the weighted pick, never whether a jar drops). The
jar reuses the existing `ItemJar` (variant derived from contents by `JarContents`), tagged with the
`NAMED_JAR` data component so `ItemJar.getName` shows its custom name; effects roll a random ritual-length
duration at drop time. Rarity is internal — NOT shown in the tooltip.
- **Weighting:** `weight = jarRarityWeightBase ^ (5.5 - rarity)` — a GENTLE, CENTRED nudge (mid-rarity neutral, the
  extremes only lean slightly), since the pool is large. `jarRarityWeightBase` default 1.12 (≈ ±65% across the whole
  range); 1.0 = uniform. Per-jar rarity can be overridden in config (`jarRarityOverrides`, `"id=rarity"`).
- **Chest drops:** a Global Loot Modifier (`JarLootModifier` + the `witchmod:loot_modifiers/*.json` bound to
  each chest's loot table by `neoforge:loot_table_id`) with a config "one in N" per chest type
  (`jarChest*OneIn` — ancient city likeliest). `WitchModLootModifiers` registers the serializer.
- **Mob drops:** `JarDropHandler` (`LivingDropsEvent`, gated on a recent player hit) — witches
  `jarWitchDropOneIn` (200), other undead `jarUndeadDropOneIn` (2000), via `isInvertedHealAndHarm`.
- **Command:** `/bewitch give namedjar <id>` (tab-completed) for testing/creative. Master toggle
  `jarDropsEnabled`.
- **Collecting any jar discovers its contents:** `JarCollectHandler` (`ItemEntityPickupEvent.Post`) marks
  every stored effect discovered for the picker, so picking a jar up fills those compendium pages.
- Adding a jar = one `register(...)` line + one `jar.witchmod.<id>` lang entry.
- **Variant presets:** `registerVariants(path, rarity, set1, set2, …)` makes ONE jar (one pick weight) that rolls
  one of several interchangeable effect sets at fill time — e.g. Jar of Thievery is Pickpocket + {Unseen | Prop
  Hunt | Disguise}. The first set is the representative used to derive its cursed/blessed/mixed variant, so keep
  the sets the same kind.

### 18.2 Attachment synergies (`com.oliver.witchmod.synergy`)
An extra behaviour that exists ONLY while ONE player carries BOTH of two effects, and vanishes the instant
either is removed. `Synergies` is the single registry (each a `Synergy` = two effect holders + a
description); the participating effect gates its bonus on `Synergy.activeFor(player)` — a LIVE
`EffectManager.isActive` check each tick, so there is nothing to persist and the on/off is always current.
Add a synergy in `Synergies`, then have one (or both) effects query it. Current synergies:
- **speedy_backseat** — Backseat Driver + Speed Demon → hijacked mounts get `backseatSpeedDemonBonusLevel`
  extra Speed.
- **viral_infestation** — Pests + Popularity → the per-block silverfish chance ×`pestsPopularityMultiplier`.
- **aggro_horde** — Popularity + Neutral Aggression → Popularity's horde also conjures neutral mobs
  (endermen, ...) since Neutral Aggression turns them on you.
- **rallying_leader** — Leader + Army → Leader's Regen/Resistance buff also lifts the mobs Army rallies.
- **swap_decoys** — Body Swapping + Confusion → a swap can trade you with your own clones
  (`bodySwapCloneChancePercent`).
- **rank_gas** — Gassy + Unhygienic → a fart kicks up an extra stink cloud (`stenchPushRadius`,
  `stenchPlayerPush`) that shoves nearby players and speeds non-undead mobs into a run.
- **tempered_glass** — Glass Cannon + Thick Skinned → Glass Cannon skips doubling any hit small enough for
  Thick Skinned to negate (checked on the pre-double amount, so nullification wins regardless of handler order).
- **fortunate_winds** — Windfall + Luck → Windfall skips its junk pool and, at `windfallHighTierChance`, floats
  a special HIGH_TIER item (diamonds, golden apples, totems, ...). `debug force witchmod:windfall @s high`
  forces one; a plain force still drifts an ordinary windfall.
- **gut_trouble** — Hiccups + Gassy → a hiccup FIT can also let a fart slip (`hiccupsFartChancePercent` per
  fit-hiccup). `debug force witchmod:hiccups @s fit` fires a whole fit (farts too, with Gassy active).
- **nature** — Drive + Farmhand → Drive's spontaneous auto-breed runs constantly (the idle gate is skipped) and
  every baby it produces is instantly recruited into the Farmhand nuisance (`CurseFarmhand.recruit`).
- **sprinting** — Brute + Speed → Brute charges quicker (`bruteSpeedChargeBonus` extra charge/tick), ploughs
  harder (`bruteSpeedKnockbackMultiplier`) and hits for `bruteSpeedBonusDamage` more.
- **bounciness** — Bouncy + Low Gravity → floor/wall/ceiling rebounds are `bouncyLowGravRestitutionMultiplier`
  stronger (client reads it off the `BOUNCY_ACTIVE == 2` flag Bouncy sets while the synergy is live).
- **duelists** — Gladiator + Reflect → a reflected projectile's return speed is ×`reflectGladiatorVelocityMultiplier`,
  so it flies further before dropping.
- **rebirth** — (Immortality OR Last Stand) + Twist of Fate → each resurrect calls `BlessingTwistOfFate.refreshOnRevive`
  (clears the dodge cooldown + maxes pity). Immortality gates on `Synergies.REBIRTH`; Last Stand does a direct
  `isActive(TWIST_OF_FATE)` check (its own pairing).
- **growth_spurt** — Farmer's Spirit + Drive → Farmer's Spirit's baby-growth is multiplied by
  `farmersSpiritDriveGrowthMultiplier` (the base growth exists without Drive; Drive just multiplies it).
- **vampire_bat** — Sanguine + Disguise → while disguised the costume becomes a BAT (`DISGUISE_TYPE` 3) and you get
  free creative-style flight (server grants `abilities.mayfly`). Flight is revoked the instant the disguise breaks
  or either effect ends; you CANNOT sprint-fly (client clears sprint while bat-flying).
- **stuck_fingers** — Butterfingers + Sticky → a would-be fumble is CAUGHT: the arm does the swing animation and
  `CurseSticky.squelch` plays, but nothing leaves your hands. An effective counter.
- **smiting** — Comic Relief + Thunder → Comic Relief's killing bolt becomes a rapid barrage of `comicReliefSmiteBolts`
  extra VISUAL bolts (staggered `comicReliefSmiteSpacingTicks`, jittered on the spot). Kills the same way; loot untouched.
- **magnetic_storm** — Magnet + Thunder → in a thunderstorm under open sky, a real lightning bolt is periodically
  drawn onto you (`magnetStormIntervalTicks`/`magnetStormChancePercent`), in `CurseMagnet.onTick`.
- **narrated_sleeplessness** — Narrator + Insomniac → the sleep-deny narrates the `insomnia` category instead of `sleeping`.
- **narrated_trumpet** — Narrator + Trumpet → the narrator's situational check adds `trumpet_walk`/`trumpet_sprint`
  categories (after crouched, since crouch silences the trumpet), keyed off `walkAnimation.speed()`/`isSprinting()`.
- **concealment** — Prop Hunt + Disguise → the prop takes priority (the animal is suppressed while you're a block);
  a hit or hostile proximity steps you down `concealCascade` prop→animal→player, and each change puffs you briefly
  unrendered (armour and all, `CONCEAL_FLASH_END` synced, `concealFlashTicks`) like an Unseen vanish. Incoming-damage
  hook + `onTick` proximity both cascade; a deliberate ACTION still reveals you fully (no flash).
- **spider_disguise** — Spider + Disguise → the disguise becomes a SPIDER (`DISGUISE_TYPE` 4) instead of livestock
  (bat wins if Sanguine is also on you). Purely the costume+ambient; Spider's own wall-climb does the rest.
- **wild_decoys** — Confusion + Disguise → the clones COPY your current form: `CloneRenderer` reads the owner's
  synced `PROPHUNT_BLOCK` / `DISGUISE_TYPE` / `CONCEAL_FLASH_END` and draws each decoy as the prop block or the
  disguise mob, reverting to the player look the instant your form breaks, and vanishing while you flash-hide. No
  new sync (all derived from the owner's existing synced disguise state).
- **streamer_brain** — Yap + Chat → `yapStreamerChancePercent` of Yap's ambient outbursts are swapped for a "talking
  to chat" line from the yap.json `events.streamer` list.
- **thieving_shadow** — Pickpocket + Unseen → pickpocket chance ×`pickpocketUnseenMultiplier` when behind a mark.
- **thieving_decoy** — Pickpocket + Disguise/Prop Hunt → ×`pickpocketDisguiseMultiplier` when behind a mark while
  wearing a form (checked off the `PROPHUNT_BLOCK`/`DISGUISE_TYPE` attachments; supersedes the unseen boost).
- **cow_costume** — Cow + Disguise → the disguise is forced to a cow (`DISGUISE_TYPE` 0).
- **silver_villager** — Silver Tongue + Disguise → the disguise becomes a VILLAGER (`DISGUISE_TYPE` 5) and a
  completed trade has `silverVillagerRefundChancePercent` to refund its cost (action-bar `witchmod.silver_tongue.refund`).
- **comedic_timing** — Yap + Laugh Track → a yapped line calls `BlessingEventHandler.triggerLaughTrack` (the laugh
  fires as if you'd typed it; the ServerChatEvent path was refactored into that shared method).
- **homecoming** — Homebody + Safety → a Safety teleport grants Regeneration `homecomingRegenSeconds`/`homecomingRegenAmplifier`.
- **slick_feet** — Ice Skates + Slippery Feet → Slippery Feet's crouch grace is halved.
- **greased_mount** — Ice Skates + Speed Demon → a second stacking mount-speed modifier (`speedDemonIceBonus`,
  own id) + the minecart nudge; stacks with the base boost and Backseat Driver's Speed. (Mount "slipperiness" deferred.)
- **layered_hide** — Thick Skinned + Tank → the negation floor is +`layeredHideFloorBonus` (threshold +1).
- **dead_weight** — Dense + Bad Swimmer → the client sink pull is ×`deadWeightPullMultiplier` (drop like a stone).
- **scorched_forge** — Floor is Lava + Hot Stuff → Hot Stuff's furnace speed ×`hotStuffScorchedMultiplier`
  (base Hot Stuff speed was also doubled 5×→10× on its own).
- **dizzy_heights** — Wonky + Vertigo → vertigo's onset height ×`dizzyHeightsStartMultiplier` (lower) and its
  camera+movement sway ×`dizzyHeightsSwayMultiplier` (stronger, incl. standing still); client reads WONKY_ACTIVE.
- **frenzy** — Berserker + Violence → Violence's impulsive swing chance + cap ×`violenceFrenzyChanceMultiplier`,
  and each landed hit banks 2 berserker stacks (free, since Violence never misses).
- **parry_frenzy** — Berserker + Gladiator → a successful parry banks `berserkerGladiatorParryStacks` (3) stacks.

Berserker itself was bumped: explicit `berserkerMaxStacks` ceiling (14), higher `berserkerMaxReduction` (0.80),
rolling ember particles scaling with stacks, a gain-burst on each stack, and a smoke+fizzle on reset.

The shared `EnvBurn` combined-curse leeway (Basement Dweller + Claustrophobia, §5) predates this package and
stays where it is; new cross-effect relationships go through `Synergies`.

### 18.3 Companionship banter + Bodyguard fixes
The "Jar of Companionship" (Bodyguard + Guardian Angel + Solicitor) bundles three managed entities that
already coexist (the Bodyguard only treats players/villagers as intruders, so the Solicitor — a
`WanderingTrader` — and the Guardian Allay are never targeted). On top of that:
- **Banter** (`CompanionshipBanter`, a `PlayerTickEvent.Post` driver) fires idle chatter between them on a
  `companionshipBanter{Min,Max}Ticks` gap, but only for categories whose speakers are present — so it stops
  the instant one is removed. Lines are data: `data/witchmod/text/companionship.json` (`/reload`-able), keyed
  by category (`stray_bodyguard`, `stray_solicitor`, `argue_bodyguard`, `argue_solicitor`,
  `stray_{bodyguard,solicitor}_guardian`, `full_house_{bodyguard,solicitor}`). A bare string is a one-liner; a
  `[opener, reply]` pair is an exchange — the opener speaks now, the other party replies after
  `companionshipArgueDelayTicks`. Placeholders: `{player}` `{solicitor}` `{bodyguard}` `{angel}`. The Bodyguard
  speaks via `BodyguardEntity.speakBanter`, the Solicitor via `CurseSolicitor.speakBanter`.
  - **Death reactions** (`CompanionshipBanter.reactToDeath`, at `companionshipDeathReactChancePercent`, kept
    infrequent): the surviving companion occasionally remarks when one of the trio is killed — categories
    `solicitor_died` (bodyguard speaks), `bodyguard_died` (solicitor speaks), `guardian_died_{bodyguard,solicitor}`.
    Wired from the solicitor/bodyguard/guardian death hooks (`onSolicitorKilled`, `onBodyguardDeath`,
    `onGuardianAllayDeath`).
  - `CurseSolicitor.onApply` was hardened to clear any leftover trade-hide timer and re-adopt an existing trader
    (dedupe) rather than possibly leaving a Companionship-applied player without one.
- **Auto-aggro on threats** — the Bodyguard commits to ATTACKING anything that strikes the anchor (via
  `onBodyguardAnchorHit` → `escalateToAttacking(attacker, true)`) and now pursues a genuine attacker on the
  longer `bodyguardThreatLeashRange` (vs the normal `bodyguardLeashRange` it uses for mere intruders), so the
  aggro actually sticks instead of standing down the moment they back off.
- The sunglasses model box was nudged off the face (z −4.6 → −4.9) so it no longer clips in.

---

*(Phases A–F build logs removed 2026-09-02 during the CLAUDE.md deep-clean — they were post-prototype
history. Everything they built is live and documented above; the §16.3 checklist is the source of progress.)*
