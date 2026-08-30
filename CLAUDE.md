# Bewitchment (placeholder name) — CLAUDE.MD (Refinement Stage)

STATUS: Prototype + Phases A–F COMPLETE. Every attachment, item, and block is implemented and functional
(the per-phase build logs at the bottom record the stand-ins/simplifications each currently uses). **The
task is now REFINEMENT** — bringing each one from "functional prototype" up to its full, polished spec, one
at a time, hands-on with Oliver. The workflow and the tick-off checklists live in **Section 16**; the rest
of this document (Sections 1–15) is the reference for what "perfect" means per attachment. This file is the
single source of truth; where older notes conflict with it, this file wins.
Totals: **curses + blessings + 15 modifiers.** (⚠ NEUTRALS and GLOBALS were **CUT entirely** on Oliver's call —
Sections 7, 8, 10.4 and 10.6 below are RETAINED FOR HISTORY ONLY and no longer describe shipping content. Their
whole code subsystem — the `events/` package, `BewitchmentEvent`/`EventCategory`/`GlobalCharge`, the Event
registry, the Afflicted status + `AfflictionManager`/`ActiveAfflictions`, the `/bewitch event`+`forcestop`
commands, the ritual's neutral-failure path, and the Compendium's Events section — has been DELETED. A failed
ritual now just fizzles. Also DELETED: the retired `CurseMansplainer` (§5, was CUT).)
(Aura was CUT on Oliver's call — too similar to another curse. Its class, registration, cost, sounds and
checklist entry are all removed; the count dropped 50 → 49.)

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
3. **Limit: 3** active curses/blessings per player (config: `Limit`). Exceeding risks backfire.
   Casting on an already-affected target costs more each time (escalating cost).
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
| Amethyst Bell | Bell retexture; flips a random active effect, cooldown | — |

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
**NO CUSTOM SOUNDS** (Oliver's call — the spec's `curse.delusions.vanish`/`.whisper` are retired). Vanilla
sounds carry it, and they're the *better* choice here rather than a compromise: every noise a delusion makes
is one the victim has heard a thousand times from real players, which is exactly what makes it pass.
There's someone out there. There isn't. Fake players wearing the skins and nametags of people really on
the server wander around at the edge of your vision going about ordinary player business — until one
notices you looking.

**They are `RemotePlayer`s inserted straight into the victim's own `ClientLevel`, and that is the whole
architecture.** No other client is told about them and the server has no entity to tick, so there is nothing
anyone could walk over and confirm. The server owns ONLY a schedule: `DELUSIONS_SIGNAL` is `0` while the
curse is off and gets a fresh random value each time one is due — the client sees the change and does the
rest (same split as Loading Screen). That keeps duration and discovery authoritative without leaking the
illusion.

**Skin and nametag come free from vanilla, by two different routes.** The nametag is just `Player.getName()`
→ the GameProfile name, so the profile is built with the mirrored player's NAME but a **fresh random UUID** —
reusing their real one would collide with the genuine entity in the level's uuid lookup. That breaks the
skin lookup (which is UUID-based), so `getSkin()` is overridden to return the mirrored `PlayerInfo`'s skin
directly. That single override also settles the body type, since `EntityRenderDispatcher.getRenderer` picks
the slim or wide player renderer from `getSkin().model()`. The victim's own skin is in the pool
(`delusionsMirrorSelf`), and unavoidably so when they're the only one online.
**⚠ `DATA_PLAYER_MODE_CUSTOMISATION` must be set to `0x7F` manually** — which skin overlay layers to draw is
normally SYNCED from the server and defaults to `0`, so without it every delusion renders with no hat,
jacket or sleeve layer (i.e. visibly bald and wearing the wrong clothes).

**Behaviour is mimicked at the INPUT level, not the animation level** — which the spec asks for and which is
also the only sane way to do it, since `PlayerModel.setupAnim` rewrites every limb each frame and hand-posed
bones would need a model mixin. Each state drives only what a real player's inputs drive — a heading (WASD),
a look angle (the mouse), sneak, sprint, jump, arm swings — and vanilla's animation code does the rest. So
**twerking really is crouch spam, waving really is repeated arm swings, spinning really is yanking the mouse
round**, which is exactly what a player doing those things looks like.

**Movement is vanilla's own `travel()`, fed fake key presses — nothing is hand-rolled.** Each state sets only
`zza`/`xxa` (WASD as a throttle plus a strafe, since `moveRelative` rotates the input by the entity's yaw),
the look angles, `jumping`, sneak and sprint; `aiStep` hands those to `travel()`, which supplies
acceleration, friction, inertia, gravity, terminal velocity, the 0.6-block step-up, collision and fluids.
**Speed is never a number in the file** — it comes from `Attributes.MOVEMENT_SPEED`, and `setSprinting(true)`
applies vanilla's real +30% sprint modifier, so a delusion physically cannot move at a speed a real player
couldn't. Sneak uses vanilla's own 0.3 input factor.
An earlier version set velocity directly and called `move()`, which opted out of that whole pipeline: no
acceleration ramp, no friction, gravity and step-up reimplemented badly. Caught in play as three separate
complaints with one cause — "they often walk on air, get stuck and walk WAYYY faster than any normal
Minecraft player".
**⚠ Two vanilla gotchas, both of which fully disable movement if missed:**
- **`isControlledByLocalInstance()` MUST return true.** `LivingEntity.travel` wraps its ENTIRE body in that
  check, and the default resolves to `!level.isClientSide` — so on the client `travel()` runs every tick and
  does nothing. Real remote players don't care (positions arrive in packets); a delusion is simulated wholly
  by this one client, so it genuinely IS locally controlled. Caught in play: "they now do not move at all".
- **`LivingEntity.aiStep` can't just be called instead.** On the client it damps velocity by 0.98 every tick
  (drift correction for packet-lerped entities), which would quietly make every delusion slower than the real
  thing. So `aiStep` is overridden to run only jump + `travel()`. (`noJumpDelay` is private, so the 10-tick
  jump cooldown is mirrored locally.)
**⚠ `calculateEntityAnimation` must be guarded to ONCE per tick.** `travel()` ends by calling it, and
`RemotePlayer.tick()` calls it again. That's harmless for a real remote player (whose `travel()` never runs —
which is exactly why RemotePlayer has that line) but a delusion runs both, and `WalkAnimationState.update`
advances the limb phase on EVERY call. Two calls per tick = arms and legs cycling at double the rate the
distance covered warrants, which is an instant tell. Caught in play: "you can tell it's a clone due to how
fast the arm and leg movements are compared to the speed".

**Terrain:** vanilla's step-up only clears 0.6 of a block, so a full block stops a walker dead — players
*jump*, and they start the jump before they touch it. So a look-ahead probe (full block at foot height,
clear air above) fires the jump early, with vanilla's own `horizontalCollision` as the backstop for corners
and odd shapes. Only if a whole 12-tick window passes with under 0.6 blocks covered is it treated as a wall
rather than a step, and the delusion turns away.
**Sporadic by design:** walking is deliberately irregular — unprompted pauses to look at something, jogs
breaking out mid-walk, idle hops over nothing, constant course drift. The irregularity does as much work as
the speed does; a perfectly straight line at a perfect speed still reads as a bot.
The one hand-written piece is the **head/body split**: a real player's body lags their head and snaps once
the twist passes ~50°, and without it a delusion reads as a rotating statue.

States: idle (small aimless glances) · wandering · sprinting · sneaking · **mining** (arm swings on the
vanilla cadence, the block's own hit/break sounds, and the cracking overlay really creeps across it via the
client-side `destroyBlockProgress` — cleared on exit or it sticks for the session) · punching · waving ·
jumping · dancing · twerking · spinning · flying (hovers ~4 blocks up and drifts) · teleporting (particles
and the sound at BOTH ends, plus `setOldPosAndRot` or it visibly *slides* across the gap) ·
**observing** · realisation.

**Observing** (Oliver's addition) walks in at an ordinary pace and then just watches from
`OBSERVE_DISTANCE`, standing perfectly still and silent — no swinging, no fidgeting, no sound at all. It's
deliberately the quietest state in the set, because every other one is a person being *oblivious* to you and
this is the one that isn't. The stillness is the behaviour.

**Realisation** is earned, never rolled: a `seenScore` builds while the delusion is in the victim's view
cone AND in line of sight, and decays at half rate when they look away. Past `REALISATION_SEEN_TICKS` it
rolls once a second; on success it turns to face them (over 8 ticks — a snap reads as a teleport), stares,
then either evaporates where it stands or **sprints straight at them** and pops on arrival.

**Hit/collision:** they are deliberately **NOT pickable** (`isPickable()` → false) and not collidable.
Vanilla's crosshair must never target one, because attacking it would make the client send the server an
interact packet naming an entity id it has never heard of. The swing is instead ray-traced against
delusions separately in `DelusionManager.onAttack` and the interaction is cancelled (arm still swings), and
"collision" is a proximity check. `pushEntities()` is a no-op for the same reason — shoving the real player
would be corrected by the server instantly.
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
```
magnetRadius=16.0  magnetSteerStrength=0.15  magnetAffectsOwn=false  magnetMaxProjectiles=32
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
Maximising is re-asserted only on the EDGE (when they try fullscreen again), never every tick, so it doesn't
fight Screensaver's shrink if both curses are active at once. Fullscreen is refused via
`options.fullscreen().set(false)` — vanilla's change callback does the toggle, so calling
`toggleFullScreen()` as well double-toggles straight back.

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
```
basementDamage=1.0  basementDamageIntervalTicks=12  basementHelmetIntervalMultiplier=2.5  // hat SLOWS burns, not softens
```

### Claustrophobia — Cobbled Deepslate  ✅ REFINED (NEW — was never prototyped)
The walls are too close. Being shut indoors with **no sky above you** wears at you — the gentler mirror of
Basement Dweller: less damage, and NO hat mitigation (a helmet does nothing about the ceiling). It bites
whenever you can't see the sky, **night included** — the problem is the roof, not the sun.

Custom `witchmod:cave_dread` damage type — same properties as sunburn (fatal everywhere, bypasses armour, no
knockback/impact). Each bite thumps a faint WARDEN_HEARTBEAT (distinct from Basement Dweller's sizzle) so
the source is clear. Death: *"%s let the walls get too close"*. Discovered on the first bite.
**Both damage curses track the next-tick per player** rather than a fixed modulo, so Basement Dweller's hat
can lengthen its interval cleanly and stepping in/out of the trigger resets it correctly.
```
claustrophobiaDamage=0.5  claustrophobiaDamageIntervalTicks=16
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

### Soul Bond — Totem of Undying  ✅ REFINED
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

### Company — Bone Meal
```
COMPANY_ACQUIRE_RADIUS=16  COMPANY_DROP_INTERVAL_MIN=2400  COMPANY_DROP_INTERVAL_MAX=7200
COMPANY_REACQUIRE=true
Loot: data/bewitchment/loot/company_gifts.json
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

### Steady Hands — Spectral Arrow  ✅ REFINED
A rock-steady draw: bows/crossbows are extremely accurate and charge quicker.
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

## 7. NEUTRALS (11) — ❌ CUT ENTIRELY (code deleted; section kept for history only)

High chance of landing on the caster when a hex fails; also directly forcible like curses.

| Neutral | Item | Spec | Constants |
|---|---|---|---|
| Wooliam | White Wool | Sheep named Woolliam poofs into existence nearby | WOOLIAM_SPAWN_RANGE=6, persistent name tag |
| Disguise | Leather | You appear as a cow to all clients | DISGUISE_DURATION=6000 |
| Anvil | Anvil | Highly damaged anvil spawns well above the victim's head | ANVIL_SPAWN_HEIGHT=12, ANVIL_DAMAGE_STATE=very_damaged |
| Letter | Book and Quill | Note with random text appears | List: data/bewitchment/text/letters.json |
| Creeper | Dark Green Dye | Creeper (or charged) spawns behind player | CREEPER_CHARGED_CHANCE=0.15, CREEPER_SPAWN_DIST=3 |
| Useless Trade | Stick | Wandering trader with terrible trades spawns | Trade pool: data/bewitchment/trades/useless.json |
| Moovin | Cooked Beef | Gravity-free cow spawns nearby | MOOVIN_RISE_SPEED=0.02, despawns at Y+64 |
| Celebration | Firework Star | Firework burst around the player | CELEBRATION_ROCKETS=8, CELEBRATION_DURATION=100 |
| Mansplaining | Iron Nugget | One kind-of-useless tip in chat | List shares mansplainer.json tip pool |
| Damage | Diamond Sword | Damage chunk scaled by essence spent; never kills if targeted | DAMAGE_PER_ESSENCE=0.2, DAMAGE_FLOOR_HP=1.0 |
| Mirror | (BACKFIRE ONLY — no item) | The curse applies to the caster instead | system-triggered |

---

## 8. GLOBALS (15) — ❌ CUT ENTIRELY (code deleted; section kept for history only)

Funded from the shared **Global bank** (Blocks of Cursed Essence). Very low base odds. Every global
gets a chat build-up/warning naming the caster; time-based globals apply the **Afflicted** status.
"Who?" has been CUT from the roster.

| Global | Item | Spec | Constants |
|---|---|---|---|
| Inventory Shuffle | Potato | All players' inventories scrambled | shuffle within each player (not across) |
| Russian Roulette | Dragon's Breath | Everyone hears a charged-creeper hiss behind them; ONE is real | ROULETTE_FUSE=30 |
| Player Shuffle | Popped Chorus Fruit | Nametags + skins swapped per-client | PLAYERSHUFFLE_DURATION=12000 (~10 min) |
| Hot Potato | Baked Potato | Modded status effect ticks down; at 0 the carrier explodes and instantly dies (world dmg per mobGriefing); passed ONLY by hitting other players | HOTPOTATO_TIMER=3600, HOTPOTATO_PASS_COOLDOWN=40 |
| Spot Shuffle | Chorus Fruit | All player positions swapped after a brief warning | SPOTSHUFFLE_WARNING=100 |
| Gravity Flip | Purpur Block | Warning, then gravity inverts 10–20s, then reverts; Y-height cap on lift | GRAVFLIP_DURATION_MIN=200, MAX=400, GRAVFLIP_Y_CAP=+32 above start |
| Party Time | Any Candle (TAG EXCEPTION) | All non-boss mobs jump happily, no attacking, confetti, ~1 min | PARTY_DURATION=1200 |
| Auction | Gold Block | A valuable item is taken from someone's inventory; players bid XP levels in chat; hard cap | AUCTION_LEVEL_CAP=40, AUCTION_BID_WINDOW=600 |
| Apocalypse | Zombie Head | Hostile spawn rates greatly increased | APOC_SPAWN_MULT=5.0, APOC_DURATION=6000 |
| Aporkalypse | Porkchop | All spawns replaced with pigs; pigs can duplicate; hard cap | APORK_DUPE_CHANCE=0.05/min, APORK_PIG_CAP=200 |
| TNT Rain | Creeper Head | TNT rains around all players; NO world damage unless config enables | TNTRAIN_RATE=1 per 60t per player, TNTRAIN_DURATION=1200, config: tntRainWorldDamage=false |
| Silence | Dandelion | Hostile spawn rates greatly reduced | SILENCE_SPAWN_MULT=0.1, SILENCE_DURATION=12000 |
| Firework Show | Firework Rocket | Fireworks + confetti around all players ~3–4 min | SHOW_DURATION=4200 |
| Floor Is Lava (global) | Lava Bucket | Standing on grass-type blocks deals armour-ignoring damage of a special damage type | GFIL_DAMAGE=1.0/20t, damage type: bewitchment:lava_floor (bypasses armour), GFIL_DURATION=2400 |
| Gamble | Redstone Block | Every player receives a random blessing/curse/neutral | uses standard pools |

---

## 9. MODIFIERS (15)

Applied in the Table's Modifier slot. Deltas multiply onto base cost/duration/success/backfire.

| Modifier | Cost | Duration | Success | Backfire | Notes |
|---|---|---|---|---|---|
| Clock | +15% | +2 to +10 min (flat, random) | — | — | IGNORED by duration-override curses (e.g. Moonwalker) |
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

> ### ⚠ ATTACHMENT STRENGTH / TIER TODO (placeholder — replace when tuning per-attachment power)
> Attachments have **no strength/tier value yet**. Until they do, these systems use **`baseCost` as a
> PLACEHOLDER proxy** and must be revisited when we go through each attachment to price/tune it:
> - **Backfire tiering** (`ModifierCalculator.applyTierBackfire`): low/high tier decided by
>   `backfireLowTierCost` (≤20 ⇒ never backfires) / `backfireHighTierCost` (≥50 ⇒ keeps
>   `backfireHighTierFloorPercent` floor).
> - **Backfire explosion power + backfire damage-type amount** (`BewitchingTableRitual.doBackfire`): scaled
>   0..1 across `backfireStrengthCostMin`..`backfireStrengthCostMax` baseCost, lerped between
>   `backfireExplosionPowerMin/Max` and `backfireDamageMin/Max`.
> When a real per-attachment strength/tier field lands, point all of the above at it instead of baseCost.

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
Company - Minor - 24
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

### 10.4 Neutral weights (failure-pool selection)
```
Wooliam - 1 | Disguise - 2 | Anvil - 2 | Letter - 1 | Creeper - 2 | Useless Trade - 1
Moovin - 1 | Celebration - 1 | Mansplaining - 1 | Damage - 3
```

### 10.5 Backfire weights
```
Mirror (curse redirected to caster) - 3
Essence Backlash (damage + essence loss) - 2
Table Tantrum (personal Table cooldown) - 1
Essence Leak (essence drops for others to grab) - 2
Marked (temporarily cheaper for others to curse you) - 2
```

### 10.6 Global bank costs (Blocks of Cursed Essence)
```
Party Time - 2 | Firework Show - 2 | Silence - 4 | Floor Is Lava - 4 | Gravity Flip - 6
Inventory Shuffle - 8 | Player Shuffle - 8 | Spot Shuffle - 8
Apocalypse - 10 | Aporkalypse - 10 | TNT Rain - 10
Hot Potato - 12 | Russian Roulette - 12 | Auction - 12 | Gamble - 15
("Who?" removed from roster)
```

---

## 11. ⚠ COLLISION & GAP REPORT (report only — NEVER fix alone)

Sacrificial matching is exact-item (Rule 9), so collisions only matter within/between pools that
share the Sacrificial Item slot (curses + blessings certainly; neutrals and globals are also
table-forcible, so treat all four pools as one selection space until ruled otherwise).

**HARD — RESOLVED during Immortality refinement (was a LIVE collision once Immortality moved to Nether Star):**
0e. **Nether Star — Immortality vs Twist of Fate.** Oliver moved Immortality onto Nether Star; Twist of Fate
   already held it, so the two would have collided under exact-item matching. Resolved by SWAPPING (Oliver's
   call): Immortality → **Nether Star**, Twist of Fate → **Ghast Tear** (Immortality's old item). Both stay
   castable; no third item needed. (The Netherstar MODIFIER is a separate slot, so it's unaffected.)

**HARD — RESOLVED (Oliver's say-so, after Bad Swimmer freed Iron Ingot):**
0. **Heavy and Heavyweight were both on the wrong sacrificial items** — a leftover from how the old
   prototype shuffled around the Iron Block clash. The full spec-correct chain is now applied:
   | Curse | Was | Now (spec) |
   |---|---|---|
   | Bad Swimmer | Iron Ingot | **Copper Ingot** (fixed during its own refinement; Copper Ingot was unused) |
   | Heavy | Iron **Block** | **Iron Ingot** |
   | Heavyweight | **Netherite** Block | **Iron Block** |
   Verified afterwards by listing every `() -> Items.X` sacrificial item across `effects/` + `events/` and
   checking for duplicates: **none**, so the shuffle introduced no new clash. **Netherite Block is now
   unused** and free for a future attachment.

**HARD — RESOLVED during Unhygienic refinement (was a LIVE collision, both castable items identical):**
0d. **Rotten Flesh — Unhygienic (curse) vs Iron Stomach (blessing).** Both were on `Items.ROTTEN_FLESH`.
   Spec assigns Unhygienic → **Rotten Flesh** and Iron Stomach → **Raw Chicken** (§6); Iron Stomach was
   simply on the wrong item, so it moved to its spec-correct Raw Chicken (unused), freeing Rotten Flesh.
   Fixed inside Unhygienic's own refinement, so not a lone item change.

**HARD — RESOLVED during Yap refinement (was a LIVE collision, both castable items identical):**
0c. **Paper — Yap (curse) vs Windfall (blessing).** Both were on `Items.PAPER`, uncastable-collision under
   Rule 9. Spec assigns Yap → **Paper** and Windfall → **Wind Charge** (§6). Windfall was simply on the wrong
   item; moved it to its spec-correct Wind Charge (which was unused), freeing Paper for Yap. Fixed inside
   Yap's own refinement, so not a lone item change.

**HARD — RESOLVED during Butterfingers refinement (was a LIVE collision, both castable items identical):**
0b. **Slime Ball — Butterfingers (curse) vs Bouncy (blessing).** Both classes really were on `SLIME_BALL`,
   which under exact-item matching (Rule 9) made one of them uncastable at the Table. Spec assigns
   Butterfingers → **Milk Bucket** (free: the old Moovin clash was resolved by moving Moovin to Cooked Beef),
   so Butterfingers moved to its spec-correct item and **Bouncy keeps Slime Ball**. Fixed as part of its own
   refinement, so this is not a lone item change.

**HARD — RESOLVED:**
1. ~~**Firework Star** — Main Character (blessing) vs Celebration (neutral).~~ RESOLVED (Oliver's call):
   **Main Character → Fire Charge**; Celebration keeps the Firework Star (it's inherently about fireworks).
   Fire Charge is used by nothing else, and dropping Main Character off any firework item also retires the
   "match any Firework Star regardless of components" type-match exception. Implemented in
   `BlessingMainCharacter`.
   **UPDATE (Oliver's call, later): Main Character moved Fire Charge → ANY FIREWORK ROCKET** (`Items.FIREWORK_ROCKET`;
   "any" is automatic since exact-item matching ignores components). Fire Charge is now free again.
   ⚠ **Future overlap flagged:** Firework Rocket is §8/§11's intended sacrificial item for the **Firework Show
   global**. There is NO live collision today (globals aren't table-castable yet — see Phase C log), but if
   global-by-sacrificial-item is ever wired, Main Character and Firework Show will clash on Firework Rocket and
   one will need to move. Not fixing now, per report-only Rule 11.

**RESOLVED this revision:** Book clash (Mansplainer → Written Book; Studious keeps Book);
Firework Rocket clash (Main Character → Firework Star; Firework Show keeps Rocket); Brute item
assigned (Iron Helmet — nothing else uses it); Heavy Handed restored (Flint — nothing else uses it).

**RESOLVED in earlier revisions (no action needed):** Heavy/Heavyweight Iron Block clash (Heavy →
Iron Ingot); Gunpowder Explosive/Creeper-neutral clash (Creeper → Dark Green Dye); Milk Bucket
Butterfingers/Moovin clash (Moovin → Cooked Beef); Copper Ingot Bad Swimmer/Useless-Trade clash
(Useless Trade → Stick); Heart of the Sea Siren's-Call/Gamble clash (Gamble → Redstone Block);
Eye of Ender Low-Gravity/Spot-Shuffle clash (Spot Shuffle → Chorus Fruit); Recovery Compass double
definition (settled: does-not-persist-after-death version).

**Cross-slot notes (Modifier slot is separate — informational only):**
Echo Shard (Echoes / modifier), Compass (Sixth Sense / modifier), Goat Horn (Flat Footed /
modifier), Rabbit's Foot (Luck / modifier), Nether Star (Twist of Fate / Netherstar modifier),
Ink Sac (Unseen / modifier), Dragon's Breath (Russian Roulette global / modifier), Honey-family
items are distinct (Honey Bottle=Sticky vs Honeycomb=modifier).

**HARD — RESOLVED at add-time (Speed Demon, 2026-08-21):** Speed Demon was requested on **Lightning Rod**,
which is Comic Relief's spec item — a live collision under exact-item matching. Resolved by moving Speed Demon
to **Carrot on a Stick** (free, and thematic — it's literally the mount-steering item). Lightning Rod stays
Comic Relief's.

**Soft flags:**
- Written Book (Mansplainer) vs Book and Quill (Letter neutral): distinct items (signed vs
  unsigned), close enough to confuse players; Compendium should render both icons clearly.
- White Bed (Narcolepsy) vs Red Bed (Homebody): distinct beds, safe under exact-item matching, adjacent
  enough to confuse; render both icons clearly.
- Black Wool (Carelessness) vs Purple Wool (Chat): distinct wools, safe under exact-item matching only.
- Purple Wool (Chat) vs White Wool (Wooliam): safe under exact-item matching only.
- Water Bottle (Thirst Meter) vs Glass Bottle (Player Essence collection): distinct items, adjacent
  enough to confuse; Compendium should render both icons clearly.
- Raw Beef (Civilisation) vs Cooked Beef (Moovin): distinct, fine, listed for awareness.
- Redstone Block (Gamble) vs Redstone Dust (random-attachment mechanic): distinct, adjacent naming.
- Tag exceptions (music discs tag, candles tag) must never be widened to other entries. (The former
  "any firework star" type-match is retired — Main Character is Fire Charge now, exact-item.)

---

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

Phases A–F are DONE (build logs at the bottom). Every attachment, item, and block already works. The job
now is **refinement**: take each one from "functional prototype" up to its full, polished spec (Sections
1–15) — the real behaviour, the right feel, the edge cases, the config-exposed constants — retiring the
prototype stand-ins the build logs list.

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

**CURSES (49)** — `witchmod:` ids in the build logs; note the renamed ones (Neutral Aggression =
`neutral_aggression` (RENAMED), Flat Footed = `flat_footed` (RENAMED), Wonky = `wonky` (RENAMED), Broken Bonds = `broken_bonds` (RENAMED)).
- [x] Violence — real `Player.attack` (enchants/knockback/crits/sweep) with a real `lookAt` camera turn. TWO
      urge types: sight swings (20° cone + line of sight, 25%/s, 75%/s if the shove would kill, instant priority,
      no camera hijack) and impulsive swings (camera hijacked, ramps 2%+0.5/s to 30%, overridden to 90% by
      anything loitering 1.5s at a hazard). Priority is TIERED not weighted (+4 hazard / +2 wounded / +1 player)
      so hazards always dominate; lit TNT needed its own PrimedTnt ENTITY lookup. 10% random-target roll for
      variety. Costs the attack cooldown (Oliver's call, reversing the "free swing" perk). 18 config knobs.
- [x] Butterfingers — three triggers on ONE shared cooldown: rare passive (3%/5s), on-damage (35%), and
      on-tool-swing (15%, hooking both mining and attacking; "tool" = any held item with durability, so modded
      tools work). The cooldown is only consumed on an ACTUAL drop, so empty hands can't buy you grace. Fumble
      order main hand → offhand → random hotbar slot, whole stack by default. Discovers on first fumble.
      **Also fixed a LIVE collision** — it shared Slime Ball with the Bouncy blessing, making one uncastable;
      moved to its spec item Milk Bucket. 7 config knobs.
- [x] Explosive — real `Level.explode` on death with full damage/knockback/block damage, mobGriefing-gated via
      `ExplosionInteraction.MOB`, dying player kept as the source for kill attribution. **The blast is deferred
      one tick, which is load-bearing:** verified in bytecode that `die()` fires `LivingDeathEvent` at offset 2
      but `dropAllDeathLoot` only at offset 164, so an immediate blast goes off before the items exist and
      leaves the whole inventory on the floor. Discovers on death. 3 config knobs.
- [x] Super Explosive — flat 5% per hit taken (no ramp, no cooldown, guarded against explosion-recursion) →
      real `Level.explode`, mobGriefing-gated. You take only 15% of your own blast and everyone else takes it
      full, via a subclassed `ExplosionDamageCalculator` (`getEntityDamageAmount`/`getKnockbackMultiplier`),
      knockback ×2.5. **Bug caught in play:** the source entity MUST be `null` — `Explosion` excludes its source
      from `getEntities`, so passing the player dropped them from their own blast entirely (no damage/knockback);
      attribution now rides the damage source instead. 4 config knobs.
- [x] Popularity — the "whole server chasing one guy" bit. Conjured horde spawned in a ring using vanilla-ish
      rules: water column → Drowned, ground → biome MONSTER-list pick (husks/desert etc), light-level safety gate,
      NeutralMobs never conjured, cap 34, gradual ramp, despawns after. Dedicated hunters: FOLLOW_RANGE bumped +
      high-priority NearestAttackableTargetGoal with 15s unseen-memory (must SEE you to lock on, then dogged
      through walls), zombies (ground-nav) break doors on any difficulty via always-true BreakDoorGoal. Neutral
      mobs excluded from aggro (Neutral Aggression's job). Discovers at a 15+ crowd. 12 config knobs.
- [x] Yap — periodic uncontrollable server-chat outbursts from a writable `data/witchmod/text/yap.json`
      (reload-able). THREE separate lists — singles / doubles / triples — so multi-message rambles are scripted
      combos sent one line after another (gap-spaced), not randomly paired; weighted so more messages are rarer.
      Sent as real player chat (`chat.type.text`). Discovers on first outburst. **Freed Paper by moving the
      Windfall blessing to its spec item Wind Charge** (was a live Paper collision — see §11). 6 config knobs.
- [x] Unhygienic (RENAMED from Green Aura, id `green_aura` → `unhygienic` since display names derive from the
      id) — green `ENTITY_EFFECT` stink cloud + `ASH` flies on an advancing orbit; non-undead mobs get an
      `UnhygienicFleeGoal` and run (undead unbothered via `EntityTypeTags.UNDEAD`); nearby players get a subtle
      repeated push away (`hurtMarked` so it syncs); three fly OGGs in one sound event on a randomised gap.
      **Freed Rotten Flesh by moving Iron Stomach to its spec item Raw Chicken** (see §11). 7 config knobs.
- [x] Repel — dropped items + XP orbs on the floor within radius slide slowly away (below sprint speed, so
      catchable), steering toward hazards but ONLY ever in the away half-space (never back toward you). Priority
      lit TNT > lava > cacti > ledges > other players > just away. Velocity SET each tick, per-tick entity cap.
      Discovers when anything slides. Fixed wrong item (Slime Block → spec's Water Bucket) and old behaviour
      (repelled living entities). 5 config knobs. Note: vanilla XP-magnet partly fights orb repel within ~8 blocks.
- [x] Echoes — client-only POSITIONAL hallucinations via `ClientboundSoundPacket` down the victim's connection
      (not `level.playSound`, which others would hear). Placement validated against the world: footsteps/landings
      snap onto real ground and use that block's own step sound, mining comes from inside solid rock below and
      uses its hit/break sounds, distant blasts are genuinely distant. Sequences queued over ticks — sprint steps
      close the distance, mining runs at a randomised cadence. **19 types in a weighted table** (`POOL`, one row
      each, so adding/retuning one is a one-line change): the above plus swimming (only ever placed in REAL
      water), eating-then-burping, an animal being hurt (swing lands THEN it cries out), whiffing at air, a
      full zombie fight, fake chat from a real online player (writable `echoes_chat.json`), and a rare fake
      notification `ping` played at the victim's own position — a single OGG with no variants on purpose, since
      a real chime never varies. Interval widened to 8–65s so the rhythm can't be used to spot the fakes.
      Discovery delayed 2s after the first one. 4 config knobs.
- [x] Delusions — client-only `RemotePlayer`s inserted into the victim's own `ClientLevel` (so nothing exists
      for another client to see or the server to be asked about); the server owns only a spawn signal. Skin +
      nametag mirror a real online player via a fresh-UUID GameProfile plus a `getSkin()` override (which also
      picks slim/wide); `DATA_PLAYER_MODE_CUSTOMISATION` set manually or they render bald. 15 behaviours driven
      at the INPUT level — twerking is crouch spam, waving is arm swings, spinning is mouse yank — plus
      **observing** (Oliver's addition: walks up, then stands dead still and silent). Realisation is earned by
      being watched, not rolled. **Three movement bugs, one cause:** hand-rolled velocity instead of vanilla's
      pipeline (fixed by feeding `zza`/`xxa` to `travel()`, so speed comes from MOVEMENT_SPEED and can't exceed
      a real player's); then `isControlledByLocalInstance()` had to return true or `travel()` silently no-ops
      client-side; then `calculateEntityAnimation` had to be guarded to once per tick or limbs cycle at double
      rate. Terrain now cleared by a look-ahead jump. NO custom sounds (Oliver's call) — vanilla is the better
      choice, since a bespoke sting would be the one thing marking them as fake. 15 config knobs.
- [x] Gluttony — the two hunger rows are now ONE 40-point bar, not two independent meters: vanilla drains and
      is topped up from the extra row each tick (so the top half empties first), eating overflow spills UP into
      the extra row, starvation only at a fully empty 40, sprint cutoff measured on the COMBINED bar at double
      vanilla's threshold. Plus the trade — eats 30% faster but gains 20% less saturation (both measured off a
      real Start/Finish snapshot). Model still 1.35x wider. 4 config knobs.
- [x] Gassy — random farts launch you at a randomised velocity, biased toward hazards but **never reliably**:
      35% ignore hazards outright, 18% go mostly straight up, and even a hazard pick is a weighted draw
      (nastiness ÷ distance) rather than nearest-worst-always. That distinction is the balance — strict
      priority like Backseat Driver's would make standing near lava a death sentence on a timer. Ten hazard
      types incl. lit TNT (an ENTITY, so its own lookup) and ledges. Events force one out of you: any explosion
      via `ExplosionEvent.Detonate` (fires whether or not the blast hurt you), taking a hit, and **fireworks —
      which produce no `Explosion` at all**, so nearby rockets are remembered per tick and one that has
      vanished by the next is read as a detonation. Event farts are ~55% big and carry 1.5x velocity, sharing
      one cooldown. Big farts use the separate OGG at 1.8x. Vertical min/max are a RATIO, not a speed (the
      vector is normalised before velocity), so tilting them upward doesn't add power. White CLOUD puff with a
      deliberately minor green dust tinge — heavier green would read as Unhygienic. 17 config knobs.
      Sounds ✅ supplied (fart1-4 + fartbig).
- [x] Farmhand — untamed animals within a wide radius get a `FarmhandBlockGoal` (priority 2, below panic/flee)
      that re-paths to just ahead of the victim so they stand in your way and wander back after interruptions;
      dormant on curse end via an isActive check. Tops up with biome-appropriate creatures spawned only where
      vanilla allows (isSpawnPositionOk + checkSpawnRules). Tamed exempt. Discovers on first recruit. Repel push
      also bumped +20% (0.084) this pass. 8 config knobs.
- [x] Dense (MERGED, 2026-08-22) — Iron Block. **Heavy + Heavyweight combined into ONE curse** (both removed).
      You fall faster + crater the ground on a hard landing (the old Heavy) AND any floor with air beneath it
      gives way under your weight when you loiter (the old Heavyweight). Implemented as `CurseDense` DELEGATING
      to the retired `CurseHeavy`/`CurseHeavyweight` behaviour classes (kept unregistered as logic holders);
      their client flags (`HEAVY_ACTIVE` water anchor, `HEAVYWEIGHT_SHAKE_END` camera shake) + `heavy*`/
      `heavyweight*` config still drive them, and their internal discovery calls were pointed at `Curses.DENSE`.
      Item Iron Block (frees Iron Ingot). ⚠ old `heavy`/`heavyweight` saved instances drop as unknown (harmless).
- [ ] Slippery Feet
- [ ] Magnet
- [x] Neutral Aggression — **renamed** from "Neutral Mobs Attack Instantly", id `neutral_mobs_attack_instantly`
      → `neutral_aggression` (display names derive from the id path, so the id IS the visible name — same as
      Unhygienic). Rewritten from the prototype, which was wrong twice over: it called `setTarget` on every
      nearby `Mob` including passive animals that have no attack goal, and a bare `setTarget` is wiped within
      a tick or two by the mob's own target-selection goals so the aggro flickered and died. Now filters on
      vanilla's own `NeutralMob` interface (so modded neutrals work automatically, per the Allergic principle)
      and uses `setPersistentAngerTarget` + `startPersistentAngerTimer` — the same route vanilla uses when you
      hit an enderman — so the hatred sticks and decays on vanilla's schedule after the curse. Your own tamed
      pets turn on you too (configurable). **SPIDERS added by hand** after Oliver caught them being immune:
      they're `Monster`, not `NeutralMob` — their daylight passivity is `SpiderTargetGoal` refusing to acquire
      above light 0.5 — so by interface they're hostile but by behaviour they're exactly what "neutral" means.
      3 config knobs.
- [ ] Dwarfism
- [ ] Screensaver
- [ ] Minor Inconvenience
- [x] Thirst Meter — FULLY FUNCTIONAL, with the supplied droplet art (plus dehydration variants) mirroring
      `Gui#renderFood`, hidden AND frozen in creative/spectator. Drain uses vanilla's EXHAUSTION model, not a
      timer: idle 20s/droplet, more for walking, much more for sprinting/swimming/mining/fighting — the curse
      is aimed at activity. Hidden **saturation** is spent before the visible bar (the grace period that stops
      a freshly-filled bar ticking down instantly). **Dehydration** is a real custom MobEffect — Hunger for
      thirst, ×4 drain (5s/droplet), `visible=false` so no particles but keeps its icon; brought on by hot
      biomes (base temp ≥1.0) or overexertion on a low bar. Refills: water bottle 6, potion 3, raw water 5,
      raw food 2, natural food 4, other food 1, all with a hand swing; raw water/cauldrons/**wet sponges**
      (which dry out) carry a poison-or-dehydration risk. Cauldrons lose a level. Eating at full hunger is
      forced through by intercepting the click, since `Player.canEat` refuses otherwise. ≤2 blocks sprinting;
      0 kills via a **custom damage type** that is fatal on every difficulty, bypasses armour and is tagged
      `no_knockback`/`no_impact` so it doesn't shove you. Both bars full = bonus regen. 24 config knobs.
- [x] Social Outcast — players and villagers simply aren't rendered (no model, no nametag — one
      `RenderLivingEvent.Pre` cancel returns before the `super.render` that draws the tag) unless within
      `revealDistance` or they've hit you recently. The damage reveal MUST be server-driven (who dealt damage
      is server-authoritative), so revealed ids are synced down and only re-synced when the SET changes.
      Hidden entities are **silenced** too — matched on sound ORIGIN, since a SoundInstance doesn't say who
      made it — or footsteps would pinpoint someone you can't see. Discovery fires when someone actually POPS
      INTO VIEW, not on application. 3 config knobs.
      **CHAT pass (2026-08-19):** the isolation now reaches CHAT — a Social Outcast victim only READS another
      player's message if they're within `outcastRevealDistance` (same dimension); further away they get a muffled
      "§7§oSomeone says something...§r" so they know chat HAPPENED but not who/what. Done in `CurseEventHandler`
      by cancelling the vanilla `ServerChatEvent` broadcast and re-sending PER RECIPIENT (the only way to vary a
      message per-viewer), and ONLY when at least one outcast is online (otherwise chat passes through vanilla
      untouched). The sender + non-outcast + close-enough recipients see the normal "<name> message".
- [x] Giant (NEW) — Seeds (Wheat Seeds, free item). `Attributes.SCALE` ×3 (model AND hitbox — you no longer fit
      one-block gaps), `MOVEMENT_SPEED` ×0.9 (10% slower), `ATTACK_SPEED` ×0.7 (30% longer swing), and
      `ENTITY_INTERACTION_RANGE` ×2 (double reach, so the tall player can still clobber things at its feet) — all
      TRANSIENT + re-asserted each tick (the reload trap). Take 75% less from ANY source / deal 80% more MELEE via
      the damage event (`onGiantDamage` in CurseEventHandler, like Glass Cannon). STOMP: a per-tick sweep crushes
      anything it TOUCHES — you just have to walk INTO them (fair, since the giant is slow), not stand on them —
      with the custom `witchmod:stomp` damage (bypasses armour, own death message "%s was stomped flat") + a big
      launch to fling them clear (per-victim cooldown). Base MELEE hits also land HEAVY: extra `giantHitKnockback`
      + a burst of sweep/crit/explosion FX (`onMeleeHit` via AttackEntityEvent) so the hit reads big. 10 config knobs.
- [x] Floor Is Lava — stand still past a 5s grace and you burn, ramping +0.5 per burn to a 4.0 cap. Grace
      keeps it playable (craft, read a sign); the ramp stops it being a flat tax you eat; the cap stops an AFK
      player being executed. Vanilla's own `HOT_FLOOR` source (the magma-block one, matching the sacrificial
      item), so fire resistance is deliberate counterplay. Flames thicken as it climbs; movement measured as
      real displacement so turning on the spot won't save you; deliberately NOT paused in GUIs. 6 config knobs.
- [x] Heavyweight — ⚠ MERGED INTO **Dense** (2026-08-22, see the Dense entry above); the standalone `heavyweight`
      curse is retired. Original behaviour below for reference:
      blocks with air beneath give way under you, scaling on hardness (leaves 0.8s, dirt 1.1s,
      stone 2.4s, planks 3.0s, obsidian ~60s); unbreakable blocks skipped so nothing chews through the world
      bottom. Warning is **visual** (crack overlay + dust pouring from the underside, thickening, fragments
      off the top edge) with the audio sparse and quiet — an early build creaked constantly and was grating.
      The COLLAPSE carries the noise: zombie door-smash + the block's break sound, ~60 debris particles, a
      hand-rolled **camera shake** (vanilla has none) applied to camera angles only, and a ragged hole in the
      surrounding floor. Progress is per-block and resets on step-off, clearing the overlay. 7 config knobs.
- [x] Bad Swimmer — liquid stops holding you up: GRAVITY ×16 + WATER_MOVEMENT_EFFICIENCY 1.0 (real vanilla
      physics — you sink then walk the bottom, ordinary step-up applies) PLUS a client-side constant 0.04/tick pull
      and a 0.4 entry plunge. The pull exists because vanilla skips fluid gravity ENTIRELY while sprinting, which
      made swimming a total exemption; it now bites in both states so staying up is a real fight (a full swim-up
      tops out ~0.048/tick, so the pull sits just under it). Drowning still applies; lava included; discovers on
      first sink; creative flight explicitly exempt. 4 config knobs.
- [x] Pests — a per-block chance (8%) that 1-3 silverfish pour out of ANY block you mine and come straight
      for you. `MAX_NEARBY` is a SAFETY ceiling rather than balance: silverfish call MORE silverfish out of
      stone when hit, so without it a mining session snowballs into a swarm the victim cannot escape. Also
      **fixed a latent item collision** — Pests was on Stone and Basement Dweller on Cobblestone, but the spec
      assigns Pests → Cobblestone and Basement Dweller → Grass Block (unused); both were simply on the wrong
      items, so moving Basement Dweller freed Cobblestone. 5 config knobs.
- [x] Allergic — 3 rolled diets (Vegetarian/Carnivore/Clean Eater) via vanilla categories (`minecraft:meat` tag + effect-granting = magic + the remainder = natural) so modded foods work; forbidden food ⇒ Blindness+Poison and only 50% of the REAL hunger/saturation gain; Clean Eaters get no positive potion effects; Scrying Mirror names the exact diet (new `Effect.scryingDetail` hook); victim discovers on first bad reaction. All 4 numbers config-exposed.
- [ ] Comic Relief
- [ ] Ugly
- [x] Audit (renamed from Taxes) — trigger now requires nearby chest/floor loot (not your pockets); snappier arrive/loot/leave timings; ender chest reworked to walk-open-rifle-close properly.
- [ ] Sticky
- [x] Backseat Driver — rebuilt to steer the RIDER (mount reads rider yaw + forward input) so the animal
      walks under its own movement code instead of being shoved by velocity (which slid/hovered); non-rider-steered
      mounts use their own navigation; real Speed effect for the bolt; hazard priority Lit TNT > ledge > lava >
      hostile > cactus > water; chance ramps while riding, cap jumps near hazards; ends instantly on dismount with
      a shorter cooldown for bailing early; discovers on first takeover. 8 config knobs.
- [ ] Clumsy
- [ ] Oversharer
- [ ] Broken Bonds
- [ ] Insomniac
- [ ] Flat Footed
- [ ] Wonky
- [ ] Stick Drift
- [ ] Basement Dweller
- [ ] Claustrophobia
- [ ] Glass Cannon
- [ ] Mansplainer
- [ ] Moonwalker
- [x] Siren's Call — hidden LONGING meter that builds while dry and drains in water, through SIX stages:
      Unease@12 (bubbles/drip, no penalty) · Yearning@28 (Mining Fatigue I + cue; discovery moment) ·
      Restlessness@45 (Mining Fatigue II, faster cue, Nausea flickers) · Heaviness@60 (Slowness I on land) ·
      The Sea's Grip@78 (Slowness II + intermittent resistible pull-bursts toward water) · The March@92
      (continuous movement hijack to the nearest water + magenta mind-control shader). Water GRACE: you must
      stay submerged `sirenWaterGraceTicks` (3s) before the longing starts to fall, and the magenta shader
      fades to 0 across exactly that window, so the screen is clear by the time it drops. The march reuses
      Backseat Driver's rider-steer (synced yaw + forced forward) since movement is client-authoritative,
      plus a small server velocity tug; shader is a fullscreen `SirenShaderOverlay` at `SIREN_SHADER *
      sirenShaderMaxAlpha` with edge vignette. Nearby Drowned soothe (×0.25 gain) and won't target the victim.
      18 config knobs.
- [x] Loading Screen — FULLY FUNCTIONAL. Every door/trapdoor/fence gate, no roll and no cooldown (it's
      avoidable, so certainty is the point). Random 1.5–5s, 40%/s stutter freezes and a 50% fake restart, hard
      20s cap. Input dead except menus — mouse-look pinned in BOTH the tick (real rotation) and
      `ComputeCameraAngles` (render), since look is applied per frame. Server owns only a random session id;
      the client runs the whole animation, because its length is dynamic and the input lock must release on the
      exact frame the bar ends. Chest sprite sheet (36 frames, 128×152, vertical), weighted client-only hold
      music (37.5/37.5/24/1%, goofy at half volume) that cuts off however the screen ends, writable tips,
      translucent-white page, centred layout, live percentage. 20 config knobs. Pending: nothing.
- [x] Pacing — time-stop dramatic moment: ramping trigger, random 5–30s biased short, freezes victim + up to 5
      nearby (invulnerable + zeroed velocity + noAi; players get Slowness/Jump lock AND a client input lock so
      they can't swing/use mid-moment). Camera cuts between orbit angles + hero shots of nearby entities, ALWAYS
      orbiting the victim-player (never `setCameraEntity` a mob — avoids spider/creeper vision shaders, Oliver's
      fix). Every player caught in it gets the cinematic via synced `PACING_FOCUS_ID`. Client-side theme (whole
      moment, cut off at end) + click per cut with a 1/250 `theonepiece` swap. 9 config knobs. Pending: the
      supplied `pacingtheme` is a .mp3 and needs an OGG export before the theme audio plays.
- [x] Trumpet — cartoon fat-trumpet loop while WALKING, cuts out instantly when you stop, speeds up while
      sprinting (live pitch), silent while crouching (the counterplay — quiet = hidden). All client-side: the
      server owns a synced `TRUMPET_ACTIVE` flag (synced to trackers, so everyone nearby hears it), and each
      client spins up a looping tickable `SoundInstance` at the cursed player. Instant stop + seamless
      `AL_LOOPING` buffer loop + per-tick pitch — none of which a fire-and-forget `playSound` can do.
      "Walking" = `walkAnimation.speed()` > threshold. Discovers on apply. 3 config knobs. ⚠ supplied OGG is
      STEREO so it plays non-positionally — needs a MONO re-export for the position-giveaway to work.
- [x] Heavy Handed → **Klutz** (renamed 2026-08-22, id `heavy_handed` → `klutz`, since the display name derives
      from the id path; "Heavy Handed" was too confusable with Heavy/Heavyweight). Originally renamed from Uncareful.
      tools/armour wear 4x as fast. No event modifies a durability
      hit's amount, so it WATCHES: records the damage value of the six wearing slots (both hands + 4 armour)
      each tick and, when one rises, re-applies the shortfall as EXTRA wear via `ItemStack.hurtAndBreak` (so
      Unbreaking still mitigates and a piece that crosses its limit breaks properly). Recorded AFTER the
      top-up so the extra isn't compounded next tick. Idle inventory untouched. Item stays Flint. Discovers
      on first extra wear. 1 config knob.
- [x] Solicitor (NEW) — Bundle (⚠ Emerald Block requested but is Silver Tongue's). A named vanilla `WanderingTrader`
      hounds you with terrible `MerchantOffers` and pitches them in local chat (names + dialogue from
      `data/witchmod/text/solicitor.json`, /reload-able). It follows you (re-navigate + teleport if it falls too far
      behind); marked with scoreboard tags (`witchmod_solicitor` + `solowner_<uuid>`) so `CurseEventHandler` finds it
      and its owner. KILL it → a fresh one spawns INSTANTLY (new name + cheeky `killed` line, via `LivingDeathEvent`).
      Actually COMPLETING a trade (`TradeWithVillagerEvent`) → it discards and hides for 1.5–10 min, `sqrt(r)`-biased
      LONG. Persists/re-adopts across reload via a tag scan. 9 config knobs.
- [x] The Snail (NEW) — Nautilus Shell. CUSTOM ENTITY (`SnailEntity` + code-baked `SnailModel`/`SnailRenderer`, no
      Blockbench): a tiny, immortal (`hurt`→false), AI-less snail the curse drives entirely. It owns a VIRTUAL
      position (advanced every tick even when unloaded — the "illusion of being chased") and only materialises the
      real entity within `snailMaterialiseRadius`. Chase SPEED is the balance: `snailBaseSpeedBlocksPerSecond` (its
      speed when on top of you) × (1 + dist×`snailDistanceScale`) capped at `snailMaxSpeed` — slow near, fast far.
      Touch (`snailTouchDistance`) → `Level.explode` + guaranteed lethal hit, then it reappears far off. Creepy
      slime-squish that quickens as it nears; discovers on first hearing. ⏳ `textures/entity/snail.png` PENDING (renders
      missing-texture until supplied). ~10 config knobs.
- [x] The Dweller (NEW — statement curse) — Oak Boat. **RESUMED 2026-08-14 — ENCOUNTER-CYCLE REWORK.**
      Oliver's verdict was "everything feels off — pacing, scares." Diagnosis: it had become a slot machine
      (1957-line class, 97 knobs, ~30 mini-events fired from a weighted pool every few seconds, 3 competing
      counterplay systems, 8-factor dread) — all payoff, no anticipation, no rhythm, no single identity. Rewrote
      the CORE LOOP around a five-beat scene arc with ONE creature identity (the unobserved weeping-angel stalker):
      **LULL** (genuine quiet — one faint far-off wrongness at most, heartbeat silent) → **TELL** (`dwellerTell{Min,Max}Ticks`
      ~3–6s anticipation: WARDEN_LISTENING cue, heartbeat starts + quickens across the beat, one mid-beat sign =
      cold-breath snowflakes + a distant wrongness) → **STALK** (it manifests and does its ONE thing: freezes while
      watched, creeps only while unobserved; heartbeat tempo = proximity) → **SPIKE** (resolved inside STALK: exactly
      ONE payoff — it reached you = shove+scream+Darkness / at tier 3 tips into the CHASE; OR you kept it at bay = it's
      simply gone (double-take), ~30% of the time via one curated set-piece) → **RELEASE** (`dwellerRelease{Min,Max}Ticks`
      the exhale, heartbeat fades) → back to LULL. The heartbeat is now the SINGLE readable cue, driven off a per-beat
      `state.heat` (0..1) so what you hear always matches the scene. **Slot machine GONE:** the ~30-event pool is
      replaced by `oneSetPiece` — a tight curated shortlist (shadow-pass, door-creak, knock; +lights-out/face-flash/
      item-poltergeist at tier 2+) fired only as the occasional spike ending, NOT on an always-on timer. Constant
      hallucinations / random flickers / contagion auto-firing were removed from `tickPersistent` (they were the noise
      that drowned the real scares); all those events still exist and stay debug-forcible, just not auto-fired. Phase
      enum DORMANT/MANIFEST → LULL/TELL/STALK/RELEASE(/CHASE); chase-end routes to LULL. Debug: `force …the_dweller @s`
      = a stalk; `tell`/`stalk`/`chase`/`hallucinate` + any event id still work; a number still sets dread. Compiles +
      boots. ⏳ NEXT (in-game with Oliver): tune lull/tell/stalk/release lengths + the dread ramp for feel; decide
      whether the light-banish counterplay (CUT this pass — replaced by the single "hold your gaze" rule) should
      return; re-add a bedside-vigil beat; prune the now-unused config knobs.
      **GHOST-GIRL PASS (2026-08-15 — philosophy + chase rework):** re-pointed the whole curse at Lethal Company's
      Ghost Girl — an entity only the victim sees that gets steadily more aggressive and, at MAX dread, turns any
      look into a lethal chase. (1) **Dread is now ONE-WAY** — `dreadDelta` is monotonic (light/company only slow the
      climb, never reverse it; base floor 0.25×), the age-floor is gone, and the ONLY thing that reduces dread is
      DYING TO THE CHASE (`onVictimDeath` resets to 0 iff `phase==CHASE`; any other death leaves it intact). (2)
      **Max-dread look-trigger** — in STALK at tier 3, holding eye contact for `dwellerMaxDreadLookTicks` (8) sends it
      aggressive → the hunt (also triggers if its unobserved creep reaches you). Below max it behaves as the
      cycle rework (double-take vanish / close-encounter spike). (3) **Bedside vigil reimplemented** — asleep, it
      looms over you (`bedVigil`): vigil heartbeat + `dwellerBedBreakChance` roll each ~1s to smash the bed and
      throw you out (+dread). (4) **CHASE fully rewritten** as a SLOW relentless obstruction (`dwellerGhostChaseSpeed`
      2.6 b/s, below walking): obstacle-aware pursuit (`navigableStep` steers around walls through open space and
      `tryBreakAhead` SMASHES doors/glass in its path to keep coming), ground-snapped so it walks terrain/steps;
      when it truly can't close for `dwellerChaseStuckTicks` (24) it TELEPORTS closer as a fallback; touch =
      instant-death finale (wipes dread). Removed the light/company escape and the survive-dread-drop entirely —
      outrunning it just makes it `loseTrail` back to watching at STILL-MAX dread (anti-softlock only, no dread
      reduction), so it re-hunts the instant you look again. New knobs: dwellerGhostChaseSpeed, dwellerMaxDreadLookTicks,
      dwellerChaseStuckTicks (all fresh keys, so no stale-config trap). Compiles + boots. ⏳ NEXT (in-game): tune
      chase speed/stuck-teleport feel + the dread climb rate; the pathing is a robust greedy-steer+break+blink
      (not full A*) — upgrade to `navigation.createPath` if it reads dumb in tight bases.
      **MIMIC reworked (2026-08-15) — its own event, not a Delusions re-cast.** Old mimic just re-applied the
      Delusions curse (a wandering hallucination). Now it's a bespoke victim-only vignette implying the Dweller
      is wearing a familiar face: server bumps a new synced `DWELLER_MIMIC` session value (+ a listening cue +
      small dread), and the client `DwellerMimicManager` spawns ONE impostor that just STANDS and STARES at you —
      unnaturally still, no player-business — then DROPS THE MASK: it dissolves into LARGE_SMOKE + SCULK_SOUL/
      charge-pop with the Warden roar/heartbeat, implying the stalker was wearing it. Reveal is EARNED (catch it
      staring back for ~0.6s) or times out at `dwellerMimicBurstTicks`. Reuses `DelusionPlayer` ONLY for the
      skin-mirroring render, pinned via a new `setStalkerStare` mode (faces you, zero throttle, no state machine);
      picks a real online player to mirror (yourself only if alone). Server clears the session after the vignette
      (+ on curse remove). Still forcible: `/bewitch debug force witchmod:the_dweller @s mimic`. Compiles + boots.
      ⏳ the reveal is particles+scream (no second model) — could swap to an actual Dweller-model reveal later.
      **CUSTOM SOUNDS pass (2026-08-15) — all supplied OGGs wired** (`assets/witchmod/sounds/curse/dweller/`,
      registered in `WitchModSounds` + `sounds.json`): **mood** (10 variants) = non-diegetic ambience on a
      dread-scaled timer (`tickMood`) with a hard 5s floor so it never spams — mostly centred (in-your-head),
      ~35% at a nearby block; **wind** (3) = replaces the teleport/blink SFX (played at both ends) + an ambient
      red-herring gust; **laugh** (3) = fires on every dread boundary via `laughOnTierUp` — 100%/full-vol on a
      full tier, 50%/half-vol on a half tier; **scream** (chase1/2) = at the dweller's spot when a chase begins
      + a rare distant quiet red herring; **breath** = subtle warning when it's close BEHIND you unseen, and
      arms a turn-to-face jumpscare (turn round and it's inches away → loud breath + heat spike); **breathing** =
      a real client loop (`DwellerBreathingSound`, synced `DWELLER_BREATHING` flag) at the dweller, volume by
      proximity, only audible close; **step** (3) = footsteps AT the dweller on a human stride cadence during a
      chase; **pop** (2) = loud jolt right behind you (in the mood roll); **bang+splatter** = together on death
      (bang at 50% vol) and the head+redstone are now PLACED as blocks on the nearest ground (`placeRemains`,
      item-drop fallback); **enraged** = the mimic mask-drop reveal. Fog + desaturation curves changed to
      quadratic ease-in so dread reads far more drawn-out (stays subtle through low/mid, bites near the top).
      Compiles + boots.
      **Cleanup pass (2026-08-15):** killed the "flickering + white dust" — it was the TELL beat's SNOWFLAKE
      burst + screen flicker + `ambientDread` (ash dust + startle). TELL sign is now AUDIO only (a low mood swell
      behind you); the LULL's `ambientDread` call is retired entirely (the LULL is truly quiet — `tickMood` is the
      only ambience). Ambience made sparser: `tickMood` gap ~30s calm → ~13s max, floored at 10s. Placeholder
      sounds swapped in auto-fired paths: close-encounter + faceflash now use the custom scream/breath (not
      ENDERMAN_*), and the chase dropped the WARDEN_NEARBY stings (custom footsteps carry the range now; heartbeat
      stays). Chase speed 2.6 → 3.5 b/s (also updated the on-disk run configs, since NeoForge keeps stale values).
      **Finale = instant death even in creative:** the touch now concludes the chase + wipes dread deterministically
      AND kills via `fellOutOfWorld` (bypasses invulnerability) so a creative player dies too. `ambientDread`/
      `tickRandomFlicker` are now unused (left in place, debug-forcible events still call their own logic).
      ⏳ bloat events (possession/isolation/phantoms/grab/explode/ceiling/eyes/watch/redstoneghost/contagion) are
      debug-only, not auto-fired — flagged for deletion pending Oliver's pick.
      **Event cull + 4 reworks (2026-08-16):** DELETED possession, isolation, phantoms (fake attackers), phantomGrab,
      ceiling-crawler, redstoneGhost (+file), and contagion — methods, State fields (possessed/phantomIds/contagionCd/
      watchIds), the tick/clear plumbing, and their registry entries; `onPhantomAttacked`→`onDwellerAttacked` (just
      swallows a swing at the stalker now). REWORKED 4 to actually be USED: **watching_eyes** — `tickDarkEyes` auto-fires
      the glowing-eyes ring on a 20–50s timer while you're in the dark; **distant_watch** — a new STALK mode
      (`state.distantWatch`, rolled in `enterStalk`, ~85%→25% as dread rises) where it stands FAR and never creeps,
      the primary low-dread experience; **explode_mob** — now UNTAMED mobs only (skips pets/owned/tamed), a real
      MOB-gated blast + bang as a jumpscare/danger; **lunge** — now fired from `tickStalk` when you stare too long
      during a high-dread (tier-2) watch, slowed to ~10 frames and launched FROM the watch spot (not a teleport-in),
      custom scream+breath. Registry ids all clarified to snake_case (shadow_pass/door_creak/face_flash/item_poltergeist/
      bang_behind/back_peek/fake_charge/snuff_light/break_block/…). Kept debug-only jumpscares: bang_behind, back_peek,
      peekaboo, boo, ambush, fake_charge, snuff_light, break_block, whisper, mimic. Compiles + boots clean. ⏳ the
      orphaned config knobs (dwellerPossess*/dwellerPhantom*/dwellerContagion*/dwellerWatch*) are now unused — harmless,
      removable in a Config pass.
      **PACING OVERHAUL (2026-08-16) — "climbs too quick, no setup for payoff":** (1) dread ramp slowed ~10x —
      `dwellerBaseAngerPerSecond` 0.30→0.03, dark 0.55→0.035, isolation 0.75→0.03, night/enclosed 0.15→0.01,
      watchAnger 1.4→0.4; a dread session now peaks in ~13–18 min (dark+alone), not ~4. (2) NEW **FOREPLAY** opening
      phase (`beginForeplay`/`tickForeplay`, `dwellerForeplay{Min,Max}Ticks` 40s–3min): NO dread/fog/desaturation,
      music untouched, near-silence, only the odd VERY distant watch — forcible via `debug force … the_dweller @s
      foreplay`. (3) **Dread is now REVERSIBLE counterplay** (was one-way): `dreadDelta` subtracts light/company/
      daylight AND a big `dwellerIgnoreCalmPerSecond` (0.06) while it's manifest and you're NOT looking at it, so net
      dread can fall — but the accelerants keep it climbing if you can't stay safe; only dying to the chase still
      hard-resets to 0. (4) chases roll a 20–35s length (`dwellerChaseMin/MaxTicks` 400/700) then break off; downtime
      held at 50–180s (`dwellerDormant{Min,Max}Ticks` 1000/3600, no longer collapsing at max dread). (5) fog cubic +
      desaturation cubic so both stay subtle far longer. Code defaults + the on-disk run configs both patched (stale-
      value trap). Compiles + boots clean.
      **Watch tiers + chase-dread-scaling pass (2026-08-16):** the STALK is now ALWAYS a passive tiered WATCH (no
      creep): **FAR** 30-50 (needs LOS), **MEDIUM** 18-29 (needs LOS), **CLOSE** 8-16 (LOS optional), band chosen by
      `pickWatchTier` scaling with dread (FAR when calm → CLOSE near max); foreplay = 75/22/3 far/med/close. HARD
      rules on every watch: stare >3s (`dwellerStareVanishTicks` 60) → vanish (or LUNGE bridge at tier 2 / CHASE
      bridge at max), get within `dwellerWatchVanishDistance` (4) → vanish, HIT it (`onDwellerAttacked`) → vanish.
      Watches are passive; aggression only via the lunge/chase bridges. Chase now scales with dread: **cooldown**
      (the no-new-chase window) shrinks from `dwellerChaseCooldownTicks` 400 at the chase floor to
      `dwellerChaseMinCooldownTicks` 100 (5s) at max via `chaseCooldownFor` (set in `loseTrail` — was missing!);
      **duration** low-end creeps up with dread; **speed** ×(1+`dwellerChaseSpeedDreadBonus` 0.45×frac); **teleports**
      rarer at high dread (stuck threshold ×(1+frac)). Footsteps fixed to a real stride (`dwellerChaseStride` 1.15,
      subtract-not-reset). Client: subtle RED chase overlay (`onDwellerChaseOverlay`, eased, scales with proximity,
      flat tint + top/bottom vignette) + chase fog eased back to ≥18 blocks so you can see it coming. Compiles + boots.
      **Sound + gore + event cull pass (2026-08-16):** DELETED the redundant flash-jumpscares (peekaboo, back_peek,
      boo, ambush, fake_charge, face_flash) — "cool name, just a flash + generic sound". Kept + cleaned the distinct
      ones. **bang_behind** now uses the custom BANG + a real camera SHAKE (new synced `DWELLER_SHAKE_END` +
      `dwellerShake{Ticks,Strength}` wired into `applyShake`), no screen flicker. **shadow_pass** = footsteps + wind
      (no sculk). **lunge** = scream/breath + shake. Purged the LAST placeholder sculk/enderman/wool/warden-tendril
      sounds (resolveSpike vanish→wind, eyes-open→mood, hallucination steps→step/mood). **explode_mob** reworked into
      a GIB: kills the mob, `bloodyBurst` (blood + BANG@50% + SPLATTER, **NO** vanilla explosion/boom sound) + a
      hand-rolled damaging shockwave. **finale** now shares `bloodyBurst` (dropped `level.explode` entirely, so no
      boom on death either) and kills via a NEW custom `witchmod:the_dweller` damage type (JSON + bypasses_armor/
      no_knockback/**bypasses_invulnerability** tags so creative dies too + `death.attack.witchmod.the_dweller`
      message). Deleted dead methods (creepToward/stealStep/tickRandomFlicker/ambientDread). Each WATCH TIER is now
      debug-forcible: `watch_far` / `watch_medium` / `watch_close` (+ `watch` = dread-scaled). Compiles + boots clean.
      **Watch/counterplay/pacing pass (2026-08-16):** (1) watches now RUN out of LOS then vanish (footsteps trailing
      off) instead of a straight poof — 78% FAR / 60% MEDIUM / 28% CLOSE (`vanishWatch`). (2) NEW **window_watch**
      (enterStalk ~25% when a wall/window exists, + debug `window_watch`): peers through the window, vanishes the
      instant you get a clear line on it. (3) NEW **mob_stare** (low-tier auto during LULL + debug): nearby passive
      mobs/villagers go silent + freeze + stare 3-8s (`mobStare`/`tickWatch`/`clearWatch`, re-added). (4) LOOK-AWAY
      vanish: once you've seen a watch, looking away rolls a one-shot `lerp(dread, 0.70, 0.05)` chance to be gone.
      (5) INTERACT penalty (`dwellerInteractDread` 7): swinging at the stalker (`onDwellerAttacked`) or getting within
      the vanish distance adds bonus dread — ignoring is the counterplay. (6) JUMPSCARE (`dwellerJumpscare`): camera
      shake + a bright white flash (client `onDwellerFlash` off synced `DWELLER_FLASH_END`) then Darkness 3-5s — fired
      by lunge @90% and the mimic reveal at a dread-scaled chance. (7) explode_mob now ALSO plays the real explosion
      sound at the mob (death still never booms). (8) snuff_light breaks EVERY light source in a 7-block radius at once
      WITH drops (`isLightSource`). (9) dread climbs faster (base 0.03→0.06, dark 0.035→0.06, iso 0.03→0.05, watchAnger
      0.4→1.2) so tiering up is actually reachable. (10) debug `tier0/1/2/3` jump straight to a tier. Sculk reveal
      particles swapped to smoke. Code defaults + on-disk run configs patched. Compiles + boots clean.
      **Boredom + world-interaction + fixes pass (2026-08-16):** (1) BOREDOM dread system (`dreadDelta`): at tiers 0-1,
      if no encounter for `dwellerBoredomDelayTicks` (4s) → `dwellerBoredomBoostPerSecond` (0.12) extra dread, so the
      long quiet LULLs still push the tiers up; an ADDITIONAL `dwellerTier0StuckBoostPerSecond` (0.15) once stuck at
      tier 0 past `dwellerTier0StuckTicks` (4.5min). Tracked via `state.sinceStalk` (reset in enterStalk) + `tier0Ticks`.
      (2) LUNGE now visibly LURCHES the whole way across the room (2.6 b/t) — the fix was a `lungeTicks` guard that
      suspends the within-4 / stare vanish rules so it isn't dispelled mid-charge (that's why the movement never
      showed). (3) NO DUPLICATES: `ensureEntity` discards any other dweller bound to the victim; `mimicEvent` refuses
      if a dweller is out or a mimic's already up; `enterStalk` refuses while a mimic session runs. (4) The LULL now
      runs `tickAmbientEvents` — the world stirs between encounters on a ~6–23s dread-scaled timer: ATMOSPHERIC (door
      creak, knock, circling footsteps, cold breath, mood swell) and INTERACTIVE with a dread cost (item poltergeist,
      single-light snuff via `snuffOneLight`) — so the lull isn't dead air. (5) BED event fixed: sleeping at tier 1+
      now enters the bedside vigil immediately from `tickLull` (sleep was too brief to line up with a scheduled beat).
      New config auto-adds; compiles + boots clean.
      **Sizeup + break + chase-AI overhaul pass (2026-08-19):** (1) REMOVED the cold-breath ambient branch. (2) EXPLODE
      (`explode_mob`) now also auto-fires from tier 1 at ~2% per ambient roll (very rare) and, on top of the gib, SPOOKS
      every mob within 18 blocks (via a shared `spookMob` helper). (3) New circling-footsteps VARIANTS (`ambientFootsteps`):
      a ring circling you, a burst RUNNING at you from a bearing (fast/loud, then stops dead), or slow quiet steps
      SNEAKING up from behind. (4) New **break** event (`breakShapeEvent`, id `break`): shatters a small SHAPE — plus/cross,
      vertical line, or a ring/square-outline (`carveShape`) — out of a wall nearby but OUT of your line of sight, with
      drops (recoverable); only `isCarvable` ordinary blocks (mineable tags/planks/logs/glass, never bedrock/valuables).
      (5) `ambientMobUnease` now has a 30% chance the panic is CONTAGIOUS — the whole herd AND nearby HOSTILE mobs in an
      8-block radius of the chosen animal bolt at once. (6) New **sizeup** event (tier 2/3, `enterSizeUp`/`tickSizeUp`, id
      `sizeup`, auto-rolled in `enterStalk` at 22%/35% for tier 2/3): it manifests RIGHT IN FRONT of you (`frontSpot`),
      passive and staring; get too close OR hold its gaze past `dwellerSizeupStareTicks` (34) and its aggression boils
      over → `sizeUpAggress` (a chase bridge, else a lunge). (7) BRIDGES: watches (stare-vanish + within-4 crowd), sizeups,
      and lunges can now BRIDGE straight into a chase FROM the creature's current spot — `maybeBridgeChase`, chance
      `lerp(dread, dwellerBridgeChaseMinChance 0.05, dwellerBridgeChaseMaxChance 0.55)`, gated by the chase cooldown;
      `beginChase` keeps the placed entity's position instead of resetting behind you when one's already out. (8) CHASE AI
      OVERHAUL — the mover (`chaseMove`/`standYAt`/`tryClimbColumn`, replacing `navigableStep`) now genuinely CLIMBS: it
      steps up one block onto ledges/stairs (`dwellerChaseClimbRate` 0.55/tick) and scrambles up columns to follow you
      when you're above, settling gently down drops, still smashing doors/glass ahead — so the teleport is now a rare
      LAST-RESORT fallback only (`dwellerChaseStuckTicks` 24→70, and only when >5 blocks away). (9) SPEED RAMP: chase speed
      still scales with dread but now also BUILDS across the hunt — `lerp(dwellerChaseRampStart 0.7 → dwellerChaseRampEnd
      1.15)` over `dwellerChaseRampTicks` (120), so it starts a touch slow and winds up slightly faster than the base
      dread-scaled speed; footsteps stay distance-accumulated (so they quicken with the speed) and the step PITCH lifts
      with the ramp. All new events forcible via `/bewitch debug force witchmod:the_dweller @s <sizeup|break>`. 8 new
      config knobs (chase ramp ×3, climb rate, stuck-ticks, bridge min/max, sizeup stare). Compiles + boots clean.
      **Model + water + boats + lunge/knock/watch pass (2026-08-19):** (1) WATCH now spawns OUT of your current
      view cone (`outOfViewSpot`, 70-180° off your look, LOS-checked for FAR/MEDIUM) so it never pops up dead ahead
      — you have to turn and find it. (2) `vanishWatch` run-and-hide chance greatly raised (CLOSE .28→.75, MEDIUM
      .60→.90, FAR .78→.97). (3) LUNGE reworked from pending-driven to a per-tick DRIVEN charge (`tickLunge`/
      `driveLunge`, `dwellerLungeSpeed` 1.2/tick, slower than the old 2.6) that visibly closes the gap and fires the
      jumpscare the INSTANT it arrives — no teleporty snap, no awkward pause at the destination. (4) KNOCK reworked
      + new `curse.dweller.knock` sound (knock1-4 supplied): ONE knock at the doomed blocks, then a random 1-6s
      silent pause (`dwellerKnockShatterMaxTicks` 70→120), then ALL targeted blocks shatter AT ONCE with
      ZOMBIE_BREAK_WOODEN_DOOR + a camera-shake FLINCH for EVERY nearby player (`shakeNearbyPlayers`) — a shared
      jumpscare. (5) MID-CHASE LUNGE at high dread (`dwellerChaseLungeMinFrac` 0.66): a LOCKED, dodgeable launch at
      you with a slowed RAVAGER_ROAR, two intelligent types — `distancegain` (straight hurl, general + close
      finisher) and `climb` (upward arc when you're camping height, `dwellerChaseLungeClimbHeight` 3) — via
      `startChaseLunge`/`driveChaseLunge`; touch = finale, miss = drop back onto ground and resume the walk. (6)
      Entity CANNOT board boats (`startRiding`→false) and any boat it TOUCHES explodes (`MindDwellerEntity.tick`
      scans Boats in its box → `level.explode` MOB-gated + discard). (7) WATER: `surfaceY`/`standYAt` now treat the
      WATER SURFACE as standable, so it stalks/chases/lunges standing ON the water — you can be haunted lost at sea.
      (8) MODEL rework — stretched, enderman-like human proportions (long thin legs 26 / torso 20 / neck 4, arms 28
      DRAPING past the hips toward the floor, small 6³ head), all code-baked; ANIMATIONS: idle is freakishly twitchy
      — the head LOLLS side to side (roll) with sudden sharper cocks + nervous yaw/pitch flickers, the long arms give
      subtle jerks, all scaled away when walking; chase stride kept controlled/human-ish (not a beastly flail at
      speed). (9) New COLOUR-CODED UV texture sheet generated (Node PNG encoder) matching the new layout — 64×64
      in-game placeholder replaces `mind_dweller.png`, plus an 8× gridded reference in Downloads
      (`mind_dweller_uv_reference.png`); eyes texture untouched (head UV unchanged). ~13 new config knobs (lunge ×2,
      chase-lunge ×7, knock max). Compiles + boots clean. ⏳ the colour-coded texture is a paint-over placeholder.
      **Interaction/knock/mood/hallucination + haunting pass (2026-08-19):** (1) `door_creak` RENAMED to
      **`interaction`** and rebuilt — it "did not work" because it rarely found a door near its random samples;
      now `interactionEvent` scans a generous ±7×±4 box for ALL interactables (doors/trapdoors/fence gates + chests/
      trapped/ender chests/barrels), touches 1 (rarely 2-4), and actually OPENS containers with the real lid
      animation (`blockEvent`/barrel OPEN state) then closes them a beat later. Debug id + pool entry renamed
      (`DwellerEvents.INTERACTION`). (2) KNOCK selection widened (±8×±4, 110 tries, new `isKnockable` = doors/
      trapdoors/glass/fences + carvable) and the shatter now spews the block's OWN crack DUST (`BlockParticleOption`)
      and layers the custom BANG on top of the zombie-door-break. (3) MOOD stings play **~40% less often** (gap
      ×1.67, floor 340t) via a new `playMoodSound`, which also has a **28% chance to be a low WIND gust** (vol 0.6,
      pitch 0.4-0.75) for an ominous "the air moved" flavour. (4) HALLUCINATION pool greatly expanded (borrowing
      Echoes sounds): a fake zombie FIGHT, an animal hurt after a swing, mob ambients, villager, cave ambience,
      swimming, enderman teleport, TNT-fuse→blast, a creeper priming+exploding behind you, anvil, arrow, big-fall+
      step — plus the originals. (5) Auto-hallucinations RE-ENABLED (paced by `hallucinationGap`, only during LULL/
      RELEASE) so the world feels genuinely HAUNTED/cursed with creeping impending doom, without drowning the real
      scares. No new config. Compiles + boots clean.
      **Persistence/creative/window/bed/chase-physics pass (2026-08-19):** (1) DREAD now PERSISTS across relog/
      world-reload/normal death via a new serialized `DWELLER_ANGER` attachment (copyOnDeath) — the transient State
      seeds its anger from it on (re)creation, writes it back each tick, and resuming skips foreplay; only chase-death
      still resets it. Fixes "relog resets my dread". (2) CREATIVE: the finale touch now does its full burst + ends
      the encounter + wipes dread but SKIPS the kill (`!target.isCreative()`). (3) BED insta-kill FIXED: `bedVigil`
      returns whether it broke the bed and the encounter ENDS cleanly (`enterRelease`) on break/timeout, so the
      figure no longer lingers at ~1.4 blocks where the within-4 proximity rule was bridging into a touch-kill after
      you were thrown out of bed. (4) WINDOW WATCH is now real: `windowWatchSpot` finds actual GLASS (block or pane)
      near you and stands the figure pressed against the FAR side of it, only leaving once you get a clear line of
      sight; it's EXEMPT from the within-4 vanish (it's meant to be close), enter chance 25→40%. The old peripheral-
      non-LOS helper became `hiddenSpot` (used by the run-and-hide flee). (5) Frequency reduced: ambient events ~12–39s
      (was 6–23s), mood ×2.2 (was ×1.67), and the on-disk hallucination gap was stale at 100/460t (5–23s — the audio
      "spam") → fixed to 1000/3400t. (6) CHASE OVERHAUL — no more levitating setPos glide: the entity switches into
      REAL PHYSICS mode for a hunt (`enterPhysicsMode`: clears noAi/noGravity/noPhysics, sets a MOVEMENT_SPEED
      attribute, `GroundPathNavigation` with canFloat+canOpenDoors) and genuinely SPRINTS after you via vanilla
      pathfinding — running the floor, jumping single blocks, climbing stairs, rounding corners, floating across
      water, obeying gravity/collision. Teleport is now a true rare last-resort (only when it truly can't path AND
      >5 blocks away). The mid-chase LUNGE is now a real dodgeable physics LEAP (a velocity impulse + gravity arc,
      AI frozen for the window so moveControl can't fight it; climb variant leaps UP to counter height-camping).
      Footsteps ride the ACTUAL move distance so cadence quickens with the ramping sprint. Removed the
      `chaseMove`/`standYAt`/`tryClimbColumn`/`tryBreakAhead` glide helpers. 4 new config knobs (dwellerChaseMoveSpeed,
      dwellerChaseSprint, dwellerChaseLungeClimbUp; dwellerChaseClimbRate/GhostChaseSpeed/RampStart/RampEnd now
      unused). Compiles + boots clean.
      **Bed-foot + boat-safety + chase-amp pass (2026-08-19):** (1) the bed-break vigil now places the figure at the
      FOOT of the bed (`bedFootSpot`, using the bed's FACING/PART) so a sleeper actually SEES it looming, not off to
      the side. (2) the entity's boat-explosion is gated on `!noPhysics` (chase/physics mode only), so it can NEVER
      fire during the floaty bed/watch illusion — the bed event can't explode. (3) chase SPEED greatly amped:
      `dwellerChaseMoveSpeed` 0.14→0.4 (0.14 was ~a slug on the mob speed scale; 0.4 is a hard relentless sprint,
      clearly faster than a fleeing player) — on-disk config patched. (4) footsteps confirmed played AT the entity's
      position, louder (0.85) and pitched with the pace, and since cadence is distance-driven they now quicken with
      the faster sprint. Compiles + boots clean.
      **Dread-persist / creative / bed / chase-tuning pass (2026-08-19):** (1) DREAD now PERSISTS across relog/world-
      reload/normal death via a new serialized `DWELLER_ANGER` attachment (copyOnDeath) — the transient State seeds
      its anger from it and writes it back each tick; only a chase-death resets it. (2) CREATIVE finale: the touch
      does its full burst + ends the encounter + wipes dread but SKIPS the kill (`!target.isCreative()`). (3) BED
      insta-kill FIXED: `bedVigil` now returns whether it broke the bed and the encounter ENDS cleanly (`enterRelease`)
      on break/timeout, so the figure no longer lingers at ~1.4 blocks where the within-4 rule bridged into a touch-
      kill; the figure is placed at the FOOT of the bed (`bedFootSpot`); and the entity's boat-explosion is gated to
      chase/physics mode (`!noPhysics`) so a bed vigil can NEVER explode. (4) WINDOW WATCH is now real
      (`windowWatchSpot` finds actual glass block/pane near you and stands the figure pressed against the far side,
      exempt from the within-4 vanish, leaving only on a clear LOS); the old peripheral-non-LOS helper became
      `hiddenSpot`. (5) CHASE start is now FAR by default (`dwellerChaseStartDistance` 13) with a dread-scaling
      CLOSECHASE chance at the old 6 (`dwellerCloseChase*`); a SAFETY floor (`dwellerChaseMinStartDistance` 5) pulls a
      too-close bridge (sizeup/close watch) BACK so it can't be an instant touch-kill. (6) Chase speed −15%
      (`dwellerChaseMoveSpeed` 0.4→0.34); it now SWIMS fast through water (`dwellerChaseWaterSpeed`, direct drive +
      buoyancy) instead of getting stuck, and won't teleport while in water. (7) CHASE CHAOS: ambient/mood/
      hallucination events run at 4× (`chaosStep`) during a hunt and ambient world-events fire mid-chase too. (8)
      More CAMERA SHAKE on jumpscares that lacked it (close-encounter, mood pop, mob-gib, creeper-blast hallucination).
      (9) Dweller EYE glow now FLICKERS (mostly lit, brief blink-offs); the WATCHER EYES entity now BLINKS
      (staggered per entity) and already tracks your movement each tick. Compiles + boots clean.
      **Possession + Behind events (2026-08-21, tier 2+):** two new `DwellerEvent`s, ticked from `tickPersistent`.
      **Possession** (`possessionEvent`/`tickPossession`, auto-rolls 1-in-`dwellerPossessChanceDenom` at tier 2+):
      grabs a nearby passive UNTAMED mob, `setNoAi` + snaps it VIOLENTLY around in place (full random yaw/pitch
      each tick, no interpolation, NO particles — 2026-08-22 tuning) for ~1.5–4s, then marches it
      straight at you via navigation; on reaching you it either FLASH-vanishes the spaghetti man at the mob's spot
      (`flashVanish` — a sudden-appearance jumpscare, only if no manifest is already out) or the mob BURSTS
      (`explodePossessed`, the explode-event gib + shockwave). **Behind** (`tickBehind`/`behindGlimpse`): a fast
      one-tick turn (> `dwellerBehindTurnDegrees` 70°) rolls `dwellerBehindChance` (25%) to briefly reveal him a few
      blocks along your new look — a `dwellerBehindTicks` (12) glimpse via a temp entity + Pending discard — as if
      he'd been watching. Both debug-forcible (`… haunted @s possession` / `behind`). 7 config knobs.
      **Stuck-AI fix (2026-08-22):** possessed mobs now carry a `witchmod_possessed` scoreboard tag; every
      exit path (`releasePossessed` on give-up, the flash/explode resolve) restores `setNoAi(false)` + drops
      the tag, `possessionEvent` `freeStuckPossessed`-scans and frees any leftover BEFORE starting a new one,
      and the curse's `onRemove` frees any too — so a possession can never permanently brick a mob's AI.
      **Possession visibility fix (2026-08-23):** `tickPossession`/`tickBehind` are now ticked from `onTick`
      BEFORE the FOREPLAY early-return (not `tickPersistent`, which the FOREPLAY phase skips), so a possession —
      including a debug-forced one during foreplay — always advances instead of a mob freezing with its AI off.
      The remaining "no twitch, phase 2 never starts" was a noAi-mob SYNC problem (the same one the Helicopter
      event hit): the twitch now forward-interpolates rotation (`*O` prevs set to the current values, then the
      live rotation jumped) + `hurtMarked` each tick so the client actually sees the snap, and the MARCH adds a
      direct 0.14/tick velocity toward you (plus navigation) so it visibly closes even if pathfinding stalls on
      the just-cleared brain. ✅ CONFIRMED working in-game (2026-08-23); the temporary chat diagnostic was removed.
      **FINALISED — rename pass (2026-08-19):** the curse is renamed **`the_dweller` → `haunted`** (registry id, so
      it displays as "Haunted" and is cast/forced via `witchmod:haunted`; the custom damage type + its
      `data/witchmod/damage_type/`, the three `damage_type` tags, and the death message key all moved to
      `witchmod:haunted` too — death line now "%s was caught by the Spaghetti Man"). The ENTITY is renamed
      **`mind_dweller` → `spaghetti_man`** everywhere: the 4 client/entity classes (`SpaghettiManEntity`/`…Model`/
      `…Renderer`/`…EyesLayer`), the `SPAGHETTI_MAN` registry holder + id, the `spaghetti_man` model-layer location,
      the `spaghetti_man.png`/`spaghetti_man_eyes.png` textures, and the lang name ("Spaghetti Man"). Internal code
      names that aren't user-facing were left (the `CurseTheDweller` class, the `effects/curses/dweller/` package,
      `DwellerEvents`, `Curses.THE_DWELLER` field, the `curse.dweller.*` sound events + `dweller_*` subtitle keys).
      ALSO fixed a TRUMPET bug: the loop could play CONSTANTLY (not just while walking) when `walkAnimation.speed()`
      got stuck non-zero — added a real-displacement gate (`TrumpetSoundManager.isMoving`, tracks per-player
      position each tick, cuts the loop after `STILL_LIMIT` ticks of no actual movement) so it self-corrects.
      Compiles + boots clean, no registry/datapack warnings.
      **Watch tiers + chase-dread-scaling pass (2026-08-16):** the STALK is now ALWAYS a passive tiered WATCH (no
      creep): **FAR** 30-50 (needs LOS), **MEDIUM** 18-29 (needs LOS), **CLOSE** 8-16 (LOS optional), band chosen by
      `pickWatchTier` scaling with dread (FAR when calm → CLOSE near max); foreplay = 75/22/3 far/med/close. HARD
      rules on every watch: stare >3s (`dwellerStareVanishTicks` 60) → vanish (or LUNGE bridge at tier 2 / CHASE
      bridge at max), get within `dwellerWatchVanishDistance` (4) → vanish, HIT it (`onDwellerAttacked`) → vanish.
      Watches are passive; aggression only via the lunge/chase bridges. Chase now scales with dread: **cooldown**
      (the no-new-chase window) shrinks from `dwellerChaseCooldownTicks` 400 at the chase floor to
      `dwellerChaseMinCooldownTicks` 100 (5s) at max via `chaseCooldownFor` (set in `loseTrail` — was missing!);
      **duration** low-end creeps up with dread; **speed** ×(1+`dwellerChaseSpeedDreadBonus` 0.45×frac); **teleports**
      rarer at high dread (stuck threshold ×(1+frac)). Footsteps fixed to a real stride (`dwellerChaseStride` 1.15,
      subtract-not-reset). Client: subtle RED chase overlay (`onDwellerChaseOverlay`, eased, scales with proximity,
      flat tint + top/bottom vignette) + chase fog eased back to ≥18 blocks so you can see it coming. Compiles + boots.
      **Event cull + 4 reworks (2026-08-16):** DELETED possession, isolation, phantoms (fake attackers), phantomGrab,
      ceiling-crawler, redstoneGhost (+file), and contagion — methods, State fields (possessed/phantomIds/contagionCd/
      watchIds), the tick/clear plumbing, and their registry entries; `onPhantomAttacked`→`onDwellerAttacked` (just
      swallows a swing at the stalker now). REWORKED 4 to actually be USED: **watching_eyes** — `tickDarkEyes` auto-fires
      the glowing-eyes ring on a 20–50s timer while you're in the dark; **distant_watch** — a new STALK mode
      (`state.distantWatch`, rolled in `enterStalk`, ~85%→25% as dread rises) where it stands FAR and never creeps,
      the primary low-dread experience; **explode_mob** — now UNTAMED mobs only (skips pets/owned/tamed), a real
      MOB-gated blast + bang as a jumpscare/danger; **lunge** — now fired from `tickStalk` when you stare too long
      during a high-dread (tier-2) watch, slowed to ~10 frames and launched FROM the watch spot (not a teleport-in),
      custom scream+breath. Registry ids all clarified to snake_case (shadow_pass/door_creak/face_flash/item_poltergeist/
      bang_behind/back_peek/fake_charge/snuff_light/break_block/…). Kept debug-only jumpscares: bang_behind, back_peek,
      peekaboo, boo, ambush, fake_charge, snuff_light, break_block, whisper, mimic. Compiles + boots clean. ⏳ the
      orphaned config knobs (dwellerPossess*/dwellerPhantom*/dwellerContagion*/dwellerWatch*) are now unused — harmless,
      removable in a Config pass.
      **[Superseded history below — the pre-rework passes.]** A horror parody, OVERHAULED from a whack-a-mole first pass into a
      proper stalker. CUSTOM ENTITY `MindDwellerEntity` (code-baked `MindDwellerModel` — a LANKY HUMANOID: small head on a
      long neck, narrow torso, long arms; idle = a head that slowly TRACKS you plus a sudden roll-axis head-COCK twitch on
      an irregular schedule; + `MindDwellerRenderer` with an emissive glowing-eyes layer, no Blockbench), driven ENTIRELY
      by `CurseTheDweller`. ONLY the victim sees it (`RenderLivingEvent.Pre` cancelled for anyone but the synced `VICTIM`
      UUID) and hears/feels it (sounds + particles go down the victim's connection only via `ClientboundSoundPacket` /
      `ClientboundLevelParticlesPacket`).
      **Presence comes and goes — it is ABSENT far more than present early on.** A 3-phase machine: DORMANT (no entity at
      all — long silences broken only by ambient dread: heartbeats, footsteps behind you, a knock, cold-spot ash motes;
      rarer/quieter at tier 0) → MANIFEST (a single appearance) → CHASE (finale). Absence length HALVES per tier
      (`dwellerDormant*` × 0.45^tier); appearances grow longer per tier (`dwellerManifest*`).
      **It stalks like a WEEPING ANGEL, the inverse of the old flee-on-sight:** while manifest it creeps toward you
      (`dwellerCreepSpeed`, ×tier) ONLY while unobserved and FREEZES the instant you look — so looking PINS it and every
      glance away it's closer. At tiers 0–1, staring it down for `dwellerStareVanishTicks` makes it simply not be there
      (the double-take); at tiers 2–3 it holds ground. Watching it also stokes anger (`dwellerWatchAngerPerSecond`) on top
      of the base creep — the objective is still not to look, but it no longer teleports. Reaching you unseen = a close
      encounter: a shove + scream (+ Darkness at tier 2) then it's gone; at tier 3 it launches the chase.
      Manifest SPOTS vary by tier: far glimpse · peripheral (corner of your eye) · window-watch (wall between you) ·
      straight-in-view (you turn and it's THERE, tier 2+) · bedside when sleeping. Staged mini-EVENTS during a
      manifestation: bedside vigil (+bed-break, kicks you out), snuff a nearby light (torch/lantern/candle/campfire →
      creeping dark), break a door/glass, detonate a nearby animal/villager as a warning, a disembodied knock, and FAKE
      charges that lunge then stop dead. CHASE: rushes through walls (`dwellerChaseSpeed`); touch = finale → bloody
      eruption (red dust + crimson spore + Warden death cry), drops redstone + your OWN severed head (`PLAYER_HEAD` w/ your
      profile), 1000 dmg, CONSUMES the curse. Client: fog closes in from tier 1 (tier 0 left normal), and a shipped
      self-contained desaturate post-shader (`assets/witchmod/shaders/…/dweller.*`, `loadEffect`, try/catch) drains colour
      from tier 1 — tier 0 stays full-colour so the rare early glimpse has nothing to soften it. Placeholder Warden/
      Enderman sounds (custom OGGs later, per Oliver). Boots clean. ~30 config knobs. ⏳ custom sounds + nicer entity/eyes
      textures (dark generated placeholders shipped) pending.
      **Additions pass:** (1) GRADUAL roll-in — a synced continuous `DWELLER_DREAD` float (anger fraction) drives a
      smoothstep fog close-in AND a `DreadAmount` uniform on the desaturate shader (set each tick via reflection into
      `GameRenderer.postEffect` → `PostChain.setUniform`), so colour/fog fade in smoothly instead of snapping per tier.
      (2) MUSIC KILL — from stage ≥ 2, a client `PlaySoundEvent` cancels all MUSIC/RECORDS sounds (jukeboxes, background
      score) + `musicManager.stopPlaying()` each tick (title screen can't be gated per-player). (3) KNOCK event — schedules
      2–3 knock sounds at nearby doors/glass then a delayed SHATTER (a per-victim `Pending` DelayedAct queue ticked in all
      phases). (4) WATCH event — if ≥ `dwellerWatchMinMobs` passive animals are near, they all `setNoAi` + freeze (velocity
      zeroed, re-faced at you each tick) and stare for a random window, even when hit; restored after. (5) WATCHERS — a NEW
      victim-only entity `WatcherEyesEntity` (invisible body + emissive red eyes layer, render-cancelled for non-victims
      like the Dweller); a dark-only event rings several pairs of glowing eyes around you at distance, re-facing you, that
      blink out if you approach. (6) HALLUCINATIONS — visual/player hallucinations REUSE the Delusions curse (applied at
      onApply for the duration; KEEP_LONGER so it stacks with an existing Delusions), and a self-contained AUDITORY
      hallucination pool (footsteps closing in, mining+break, chest open/close, block place, door, fight swings, eat+burp,
      item/xp pickup, villager, sculk) plays positional victim-only sounds around you on `dwellerHallucination*` gaps — all
      independent of Echoes so they stack too. ~20 more config knobs.
      **Mini-horror-mod overhaul:** progression is now a FLUID multi-factor DREAD engine computed every tick in
      `dreadDelta` — it RISES in the dark (light ≤ `dwellerDarkLevel`), while alone (no player within `dwellerCompanyRadius`),
      at night, and while sealed underground; and it FALLS (real COUNTERPLAY) in bright light (≥ `dwellerLightLevel`), near
      other players, and in open daylight. A slowly-rising inevitable FLOOR (`dwellerDreadFloor*`, capped ~60%) guarantees
      progression but perfect play caps just short of the hunt, so the worst horrors are EARNED by wandering the dark alone.
      Tiers 0–3 derive from dread; the client desaturation/fog already ramp off the synced `DWELLER_DREAD`. **AI:** the
      creep now speeds up when far/slows when near, and — key counterplay — WON'T close past `dwellerLightLurkDistance`
      while you're lit (it lurks at the edge of your light), and won't manifest close in bright light; staring it down FROM
      THE LIGHT builds a `dwellerLightRepel` "banish" that drives it off and DROPS dread (in the dark, staring only
      freezes/double-takes it). **The HUNT is now SURVIVABLE** (not a guaranteed kill): reach bright light or other players
      and hold `dwellerChaseEscapeLightTicks`, or simply outlast `dwellerChaseMaxTicks`, and it breaks off exhausted —
      dread drops by `dwellerChaseSurviveDreadDrop` and a `dwellerChaseCooldown` enforces calm before it can hunt again;
      only being CAUGHT is fatal (the finale). Chase speed ramps slightly the longer it runs. **New events:** Lights-Out
      (snuff every nearby light at once + Darkness + screen flicker), Phantom Grab (yanks you toward it), Cold Breath
      (Slowness + snowflakes when it's close), Shadow-Pass (hard screen FLICKER via synced `DWELLER_FLICKER` + fast
      footsteps circling), Whisper (right at your ear), plus a proximity heartbeat that quickens as it looms. Client fog
      handles the flicker (slams to ~2.5 blocks for a beat). ~24 more config knobs. Boots clean.
      **Event-roster expansion:** the manifest mini-events are now a tier-gated WEIGHTED POOL (`Cand`/`runWeighted`, retries
      past events whose preconditions fail). Added (horror + comedy): Whisper-Line (creepy/funny action-bar text in YOUR
      name, 20-line pool), Redstone Ghost (phantom button/lever/dispenser clicks), Peekaboo (split-second full-view flash),
      Door Creak (a door/trapdoor/gate swings itself open — both halves kept in step), Cold Draft (nudge + snowflakes),
      Levitate/Mass-Levitation (a floating cow / whole herd, gravity restored via a `Pending`), Fake TNT (a fuse that
      fizzles — "…dud."), Item Poltergeist (your dropped items fling into the air), Fake-Out (scary build-up → harmless
      sheep/villager/egg + "…huh."), Boo! (point-blank flash + scream + shove), Ceiling Crawler (on the ceiling looking
      down), The Tilt (Nausea). 5 more config knobs (`dwellerFloat*`, `dwellerTiltTicks`, `dwellerFakeTntFuseTicks`).
      **Ring-overhaul pass:** REMOVED whisper-line, cold-draft, levitate, fake-TNT, fake-out, cold-breath, the-tilt. NEW
      events: **Possession** (2 branches — hostiles/neutrals INSTANTLY enrage with Strength+Speed+particles via
      `setPersistentAngerTarget`/`setTarget`; passives freeze and TWITCH violently 2–5s then rampage — manually navigated +
      manually damaging via `mobAttack(mob)` so death messages name the mob, and they attack the NEAREST player incl.
      bystanders); **Isolation** (frequent mid-tier — applies the reused **Social Outcast** curse so other players/villagers
      go unrendered beyond ~5 blocks, pushing you away from company); **Mimic** (refreshes the reused **Delusions**
      fake-player system); **Phantom Attack** (victim-only fake attackers = reused `MindDwellerEntity`, rush you; striking one
      (via `AttackEntityEvent`→`onPhantomAttacked`) or being reached vanishes it + brief Blindness + dread points).
      **Progression retuned:** base dread 0.30→0.18, isolation 0.25→0.75 (slower overall, MUCH faster alone). **Death is now
      a reset valve, not an ending:** `onVictimDeath` (LivingDeathEvent) drops dread ONE tier, TWO if mid-chase; the chase
      finale no longer removes the curse (Ring-like relentlessness) and is now a REAL explosion (`dwellerFinaleExplosionPower`,
      no terrain dmg) that hurts nearby others. **Contagion** ("presence is a danger to others"): at tier ≥ 2, nearby OTHER
      players catch Darkness + dread cues; combined with the victim visibly swinging at victim-only phantoms, being around
      the afflicted is dangerous and unnerving — the Ring dynamic. ~25 more config knobs. Boots clean.
      **Pacing + chase-escalation + jumpscare pass:** hallucinations made RARE (gap 700–2400t, tier-0 ×2.2) so they're
      impactful not constant. Manifest DISTANCE now slides CONTINUOUSLY far→close with dread (`manifestDistance` off
      `dreadFrac`) instead of tier steps, and `dormantDuration` takes a continuous `dreadFrac` with a `1-0.82·f²` curve so
      close watches start slow and only steepen toward the hunt — a smooth bridge watch→intense-watch→chase. **Chases
      escalate across a curse:** `chaseCount` persists; the FIRST hunt is a slow (`dwellerChaseSpeedFirst` 4 b/s) telegraphed
      hazard with proximity WARNING cues (WARDEN_NEARBY_CLOSE/CLOSER/CLOSEST + quickening heartbeat) and NO teleports; each
      later hunt is faster (`+dwellerChaseSpeedPerChase` up to the cap) with ever-more-frequent TELEPORT switch-ups (blinks
      to `dwellerChaseTeleportDistance` on a `chaseTeleportInterval` that shrinks per hunt). **New JUMPSCARES:** low-tier
      Startle (bang behind + flicker; also injected into ambient absence), BackPeek (it's right behind you); mid Face-Flash
      (point-blank flash) + Lunge-Scare (rushes to your face over 6 ticks then screams); high Ambush (strobing point-blank
      appearances). Mini-events now fire at tier 0 too. Finale drops now SCATTER (random pop + facing) amid a much bigger
      blood burst (~500 particles + jets). 6 more config knobs. Boots clean.
      **Anchoring + atmosphere pass:** FIXED the "hovers in the air / spawns on the world surface in caves" bug — dropped the
      heightmap for a `surfaceY` that scans DOWN from just above your feet for the first motion-blocking block with air over
      it (falls back to your Y, never the sky), so it anchors to the LOCAL cave floor/bridge/ledge at your height; used by
      both `groundSnap` and `placeAt` (so watches AND the creep stay grounded). Far watches are now NOTICEABLE: `pickManifestSpot`
      biases toward a `visibleInViewSpot` (an in-view spot with a genuinely CLEAR line of sight to you, 8 retries) — heavily
      at low dread (~80%), easing to stalking-out-of-view (~15%) at high dread. Scarier atmosphere: a continuous oppressive
      HEARTBEAT (`tickHeartbeat`) that's silent while calm+absent and quickens with dread AND proximity (`dwellerHeartbeat*`);
      stray self-triggered light FLICKERS at tier 2+ (`dwellerRandomFlickerChance`); and — the panic touch — at tier ≥ 2 in the
      dark it will LURCH a step closer even while you're staring straight at it ("it moved and I didn't blink"; bright light
      still holds it). 5 more config knobs. Boots clean.
      **Two-bug pass (2026-08-19):** (1) STICKY TNT (passive) — any lit `PrimedTnt` you touch CLINGS to you
      (`stickTnt` in onTick force-rides it on you), so it goes off in your face; forcible via `stickytnt` (spawns a
      fused TNT already stuck). Ejected on curse remove. (2) BEDROCK COOLDOWNS (the one BENEFICIAL bug, tier-2
      pool) — quarters your weapon swing cooldown for 8-28s via a temporary `ATTACK_SPEED` ×4 modifier
      (`cooldownsEvent`/`tickCooldowns`, buff stripped when the window ends + on remove), mimicking Bedrock's
      no-cooldown swinging. Both registered in `BedrockEvents` (so debug-forcible); 3 new config knobs.
      **PER-EVENT-CLASS REFACTOR (2026-08-11 — STRUCTURE COMPLETE, compiles + boots):** `CurseTheDweller` + `CurseBedrockMoment`
      moved into dedicated packages `effects/curses/dweller/` + `effects/curses/bedrock/` (Curses.java + CurseEventHandler refs
      updated). Each is now driven by an event interface + registry: `DwellerEvent`/`DwellerEvents` and `BedrockEvent`/
      `BedrockEvents`. EVERY discrete event is its own class/registry entry (`id()` + `run(curse, target, State, level)`), and
      the weighted pool AND `/bewitch debug force` BOTH dispatch through the registry (via an `add(pool, weight, EVENT, …)`
      helper and `Events.byId(arg)`), so adding/forcing an event is uniform. `State` + the shared helpers are PACKAGE-VISIBLE.
      Three Dweller events are FULLY extracted into their own files with the logic inlined (`LightsOutEvent`, `WhisperEvent`,
      `RedstoneGhostEvent`) as the reference; the remaining ~20 Dweller events and all ~18 Bedrock active events are registry
      entries that DELEGATE to their (now isolated + package-visible) trigger methods on the curse — so each event's body can
      be inlined into its class incrementally with ZERO call-site churn. The state-machine core (dread engine, manifest/chase/
      creep, tick/clear of watch/eyes/possessed/phantoms) and the PASSIVE Bedrock bugs (aimbot/pause/delay/ghost/silent-creeper/
      hotbar-drift, hook-driven) intentionally stay on the main class. NEXT (optional polish): inline the delegating events'
      bodies into their classes one at a time.
- [x] Bedrock Moment (NEW — statement curse) — Crying Obsidian (free item, no collision). One of the most OVERLOADED,
      VERY expensive curses with no benefit: plagues the victim's game with pointlessly elaborate "bugs", a parody of old
      short-form Bedrock-jank clips. `CurseBedrockMoment` (MAJOR, cost 110). PASSIVE (event/scan-driven): **Bluetooth** (all
      incoming damage withheld+stored during a window, dumped ~1s after via `LivingIncomingDamageEvent` hook in
      CurseEventHandler → `onIncomingDamage`, guarded by an `APPLYING` set so the re-applied hit passes through);
      **Delay** (fall damage withheld and re-applied 0.5–4s late, distinct from Bluetooth); **Aimbot** (skeleton arrows near
      you get ×velocity + home in, scanned each 2 ticks); **Pause** (your own projectiles freeze 0.3–3s then resume, via a
      per-projectile `Pending`); **Fling** (mounting a rideable has a chance to hurl it along a random axis). ACTIVE
      (weighted pool on a `bedrockEvent*` timer): **Charged Creeper Boat** (creeper charged via `thunderHit` + a visual
      LightningBolt, ridden in a Boat driven at you each tick, despawns if it drifts past), **Blitz** (creepers get big
      Speed + instant no-windup detonation at close range), **Mitosis** (nearby mobs duplicate via `getType().create`, cap-
      guarded), **Rubberbanding** (saves your spot, `connection.teleport`s you back a few times), **Server Lag** (pins all
      nearby non-player entities for ~0.8s then releases — fake tick-lag), **Pop** (nearby armour stands/item frames/
      paintings break), **Nightcore** (synced `BEDROCK_NIGHTCORE` window → client wraps every `PlaySoundEvent` in
      `NightcoreSoundInstance` for higher pitch), **Inventory Shuffle** (Fisher-Yates over non-hotbar slots), **Desync
      Drowning** (rare — air set to 0, drown damage on LAND until you submerge to reset), **Chunk Rejection** (synced
      `BEDROCK_CHUNK_REJECT` window → client slams render distance to 2 so chunks unload/reload), **Helicopter** (a passive
      mob is anchored, spins + slowly rises 4–15s, then launches up), **Tickspeed** (nearby mobs get big Speed+Haste). ~30
      config knobs. Boots clean. Sounds are vanilla placeholders.
      **Tuning pass:** Charged Creeper Boat dropped to RARE (weight 3, like drowning) + much faster (`bedrockCreeperBoatSpeed`
      3.0, shock-not-kill); Desync Drowning now ends on touching water (`isInWater`), auto-expires after `bedrockDrownTicks`
      (~20s), and is cleared on death (`onDeath` via the LivingDeathEvent hook); Helicopter now has an on-GROUND spin grace
      (`bedrockHelicopterGroundTicks`) before it rises and spins far faster (`bedrockHelicopterSpin` 62°/tick).
      **Spin-fix (after Helicopter was widened from `Animal` to any non-boss `Mob`):** the mob's own AI was rewriting
      its rotation every tick, so it stopped spinning. `helicopter()` now `setNoAi(true)` on the chosen mob (restored
      `setNoAi(false)` at launch), and `tickHelicopter` forward-interpolates the yaw (`yRotO/yBodyRotO/yHeadRotO = prev`)
      so the client winds forward instead of wiggling across the ±180 wrap.
      **Batch 2 (8 more bugs):** PASSIVE — **Ghost Blocks** (a placed block pops out after `bedrockGhostBlock*`, item still
      spent; via the CurseEventHandler `EntityPlaceEvent` hook → `onBlockPlaced`), **Silent Creeper** (client
      `PlaySoundEvent` swallows any sound whose path contains "creeper" while `BEDROCK_ACTIVE`≥0), **Hotbar Drift** (client
      randomly re-selects your hotbar slot + `ServerboundSetCarriedItemPacket`). ACTIVE windows (synced end-ticks, client-
      rendered) — **Sound Delay** (client holds one-shot sounds in a queue and replays them `bedrockSoundDelayAmount` late,
      guarded by an identity passthrough set), **Phantom Durability** (`RenderGuiLayerEvent.Post` on HOTBAR draws jittering
      durability bars over damageable hotbar items), **Perspective Flip** (rare — forces `THIRD_PERSON_FRONT`, restores
      after), **Split Screen** (server picks nearest other player→mob→self, syncs `BEDROCK_SPLIT_ID`; client
      `setCameraEntity` steals that POV — true split needs render mixins so it's a POV-steal), **Marketplace popup** (server
      bumps a `BEDROCK_MARKETPLACE` nonce; client opens `MarketplaceAdScreen` — a non-pausing, no-Esc `Screen` you must
      click the ✕ on with the cursor while the world keeps running; ad art = `assets/witchmod/textures/gui/marketplace/
      ad_<n>.png`, 256×256, count `bedrockMarketplaceAdCount`, 3 placeholders generated). ~13 more config knobs + 7 synced
      attachments. Boots clean.
      **Event-tiering pass:** the ~18 ACTIVE events are now drawn from a 3-TIER weighted pool — tier 1 very common
      (Nightcore, Sound Delay, Phantom Durability, Chunk Reject, Rubberband, Bluetooth), tier 2 medium (Mitosis, Blitz,
      Server Lag, Pop, Inventory Shuffle, Helicopter, Tickspeed, Marketplace, Perspective), tier 3 rare (Creeper Boat,
      Drowning) — weights `bedrockTier1/2/3Weight` (12/5/2) retune the whole balance. **Split Screen REMOVED entirely**
      (event, `splitScreen()`, pool entry, onRemove clears, the client POV-steal handler, `bedrockSplit*` config, and the
      `BEDROCK_SPLIT_END/ID` attachments) — the standalone Splitscreen curse supersedes it. Added `debugArgs()` so every
      forcible name (all active events + the passive-info keys) tab-completes under `/bewitch debug force`.
      **New-events pass (client half in `client/BedrockClientBugs`):** TIER 1 — **Ghost Item** (held item renders as a
      random other item over the hotbar slot), **Input Lag** (movement applied `bedrockInputLagDelayTicks` late via a
      buffered `MovementInputUpdateEvent`), **Texture Flicker** (translucent magenta/black missing-texture checker on a
      SLOW 6-on/24-off blink — deliberately not a strobe), **Sprint Reset** (sprint cut for 5 of every 24 ticks). TIER 2 —
      **Ghost Block Phase** (`bedrockGhostPhase*` 4–16s: placed blocks force-reject, broken blocks are cancelled server-side
      so they reappear — via the `onBlockPlaced`/`onBlockBreak` hooks), **Language Error** (swaps to Pirate `en_pt` / Welsh
      `cy_gb` for the window; ⚠ uses `reloadResourcePacks` twice so it's heavy — a jarring reload, thematic but tunable),
      **Speed Blitz** (freeze `bedrockSpeedBlitzFreezeTicks`, recording your attempted moves, then a 3× velocity replay).
      PASSIVES — **Food Reg** (`bedrockFoodRegChancePercent` 14% an eaten food gives no hunger, in `onUseItemFinish`),
      **Hit Reg** (`bedrockHitRegChancePercent` 6% a melee hit is cancelled + whiffs to `PLAYER_ATTACK_NODAMAGE`, in
      `onAttackEntity`). TIER 3 — **Fake Kick** (`FakeKickScreen`: a convincing "Connection Lost" + realistic netty/timeout
      reason + Back-to-Server-List button; entirely fake, dismissed by button/Esc/~12s timeout), **Fake BSOD**
      (`FakeBsodScreen` renders `assets/witchmod/textures/gui/bsod.png` 1024×640 full-screen, forces fullscreen, pauses
      ONLY Minecraft's own audio, and the server broadcasts the exact vanilla "left the game" message to everyone).
      All tiered into the weighted pool; ~14 more config knobs + 9 synced windows. Boots clean.
      **Tuning pass:** **Tickspeed** overhauled — instead of Speed/Haste potions it now literally TICKS the affected
      entities `bedrockTickspeedMultiplier` (4) extra times each server tick (`tickTickspeed`, tracked in State), so they
      genuinely run at high tickspeed like a server-lag clip; NO sound. Stripped the gratuitous UI sounds off **Bluetooth**
      (note bell), **Tickspeed** (note pling) and **Fling** (slime jump). Event frequency ~30% more common
      (`bedrockEventMin/MaxTicks` 120/380 → 84/266). **Helicopter** now targets ANY non-boss `Mob` (was passive `Animal`
      only; excludes Ender Dragon / Wither). **Language Error** now rolls any joke language (Pirate/LOLCAT/Upside-down/
      Welsh/Anglish/Toki Pona, falling back to Pirate if a code isn't on the build).
      **Fake BSOD believability overhaul:** 4 images (`bsod_standard` + `bsod_funny1/2/3`, ~1860×910) — 50% standard,
      50% a random funny one. A ~0.75s GLITCH pre-phase (`BSOD_GLITCH_TICKS`) sells "the machine is coping": MC's own
      audio cuts, the window JITTERS via GLFW (windowed only), the screen stutter-flickers black (~90ms buckets, 1/3 of
      frames — a struggle, not a fast strobe), and movement is dampened ×0.15 + cut in stutters. Then it forces fullscreen
      and the chosen image ROLLS DOWN from the top (`FakeBsodScreen` scissor reveal over ~0.55s) and holds. Everything
      (fullscreen, window pos, audio) is restored when the window ends.
      **Refinement batch:** BSOD glitch pre-phase halved (`BSOD_GLITCH_TICKS` 15→7). **Rubberband** has a 55% EXTRA-QUICK
      variant (2× the yanks at ⅓ the gap). **Language Error** now swaps WITHOUT a resource reload — `ClientLanguage.loadFrom`
      + `Language.inject` (pure client render, nothing sent to the server, real setting untouched), and rolls any joke
      language. **Texture Flicker REPLACED by Vibrant** — a saturation-boost post shader (`shaders/post/bedrock_vibrant`,
      applied via `gameRenderer.loadEffect`, "Bedrock's punchier colours"); the old magenta-checker overlay is gone.
      **Fling** now catapults in a random 3D direction with lift (was an axis-aligned slide that only worked straight up).
      **Tickspeed** already ticks entities extra (prior pass). NEW passives: **Air Swimming** (`bedrockAirSwim*`, client
      window — while swimming, a chance to keep swimming through AIR like water for 3–25s; buoyant + swims toward look),
      **Sleep Cancel** (`bedrockSleepCancelChance`, booted out of a bed while sleeping via `stopSleeping`). NEW tier 2:
      **T-Pose** (nearby non-boss mobs `setNoAi` + frozen 0.3–5s — ⏳ literal arms-out pose needs render mixins the project
      avoids, so it's a hard freeze), **Float** (nearby entities lose gravity but keep pathfinding, drifting toward you).
      NEW tier 3: **Hungry** (`bedrockHungry*` 2–11s — holding right-click "eats" any held item with eat FX; finishing the
      ~1.6s eat consumes one via a `BedrockHungryEatPayload` C2S; ⏳ the exact arm animation on non-food can't be forced
      without item mixins, sold with sound+particles). All tiered + `debugArgs`-listed; ~13 more config knobs + 2 windows.
      **Follow-up fixes:** **T-Pose → Pause** (id `pause`; the old projectile-pause passive's debug key became `projpause`):
      nearby non-boss mobs pause (AI off + frozen) for 0.3–5s, then a `bedrockPauseCatchupMultiplier` (10× — higher than
      tickspeed) catch-up burst for 1–4s where they lurch to catch up; **hitting a paused mob force-ends the pause early**
      (`onEntityHurt` via a `LivingIncomingDamageEvent` hook → `releasePause`). **Air Swimming** rewritten to be REAL
      swimming — forces the swim/crawl pose (`setPose(SWIMMING)` + `setSwimming`/`setSprinting`) and strokes you in your
      full look direction when moving forward, near-neutral buoyancy otherwise (was just reduced gravity). **Language**
      now picks only from joke languages actually present on the build (it was falling back to Pirate every time) via a
      fresh `RandomSource`.
- [x] Solicitor (NEW) — Bundle (⚠ Emerald Block requested but is Silver Tongue's). A named vanilla `WanderingTrader`
      hounds you with terrible `MerchantOffers` and pitches them in local chat (names + dialogue from
      `data/witchmod/text/solicitor.json`, /reload-able). It follows you (re-navigate + teleport if it falls too far
      behind); marked with scoreboard tags (`witchmod_solicitor` + `solowner_<uuid>`) so `CurseEventHandler` finds it
      and its owner. KILL it → a fresh one spawns INSTANTLY (new name + cheeky `killed` line, via `LivingDeathEvent`).
      Actually COMPLETING a trade (`TradeWithVillagerEvent`) → it discards and hides for 1.5–10 min, `sqrt(r)`-biased
      LONG. Persists/re-adopts across reload via a tag scan. 9 config knobs. Chat is now player-style `<Name> line` (no
      quotes; same green-name/cream-line colours). Also: **Bedrock Moment** rubberbanding retuned — silent (no teleport
      sound), 10 yanks at a 12t gap (was 3 at 55t) so it reads as frantic lag; **Screensaver** now boots straight back
      out of fullscreen if you toggle it mid-bounce (during SHRINKING/BOUNCING), so the effect keeps up.
- [x] Splitscreen (NEW — statement curse) — ANY SIGN (tag exception `ItemTags.SIGNS`, like Hype Man). Pulled out of
      Bedrock Moment into its own curse. `CurseSplitscreen`: drags the nearest eligible player into a shared, console-style
      split screen with SOUL-BOND-like STICKINESS — grabs the nearest free player within `splitscreenRange` (22) and holds
      them until they leave range, then ends (both play normally until someone returns; only ever 2 players). Symmetric: the
      curse (on the victim) drives BOTH players' synced attachments (`SPLITSCREEN_PARTNER`/`_PHASE`/`_LOAD_END`/`_SIGN_LOCK`/
      `_SIGN_CLOSE`). Phases 0 off · 1 ENTERING · 2 ACTIVE · 3 EXITING; enter/exit are a fake "Entering/Exiting splitscreen…"
      loading screen (`splitscreenLoad{Min,Max}Ticks`, 0.4–2s, à la the Loading Screen curse). SHARED-SIGN quirk: the client
      reports its open sign editor via a `SplitscreenSignPayload` C2S; while EITHER has a sign open both freeze
      (`SPLITSCREEN_SIGN_LOCK` → client zeroes movement of the non-editor), and taking damage (`CurseEventHandler`
      `LivingIncomingDamageEvent` → `onDamaged`) bumps `SPLITSCREEN_SIGN_CLOSE` to force-close the sign for both. Client
      (`SplitscreenClient`): the loading overlay, the sign freeze/force-close, and a right-side split PANEL. **UI call
      (Oliver's latitude):** a fully-shared screen isn't possible (separate hotbars/inventories/health), so each keeps their
      OWN HUD and the partner's view is a side PANEL. The panel's LIVE partner POV (`SplitscreenPov`) is a real second
      `LevelRenderer.renderLevel` pass from the partner's `Camera` into a `TextureTarget`, blitted in — but it's EXPERIMENTAL
      and `splitscreenLivePov` DEFAULTS OFF (a botched second pass can corrupt GL state, and it's untestable headless); the
      shipped default is a robust FALLBACK panel (partner name + live coords + facing). Debug: `/bewitch debug force
      witchmod:splitscreen @s villager` pairs you with the nearest VILLAGER (one-sided, so you can test the render/panel solo
      — you see its POV; `exit` ends it; no arg pairs the nearest player). Boots clean. 4 config knobs.
      **LIVE POV — WORKING (Oliver's in-game iteration, 2026-08-11):** the second render pass no longer leaks over the
      main screen — the fix was reflectively pointing `Minecraft.mainRenderTarget` at the off-screen `TextureTarget` for
      the duration of the pass, since `renderLevel` grabs the main target internally (otherwise the partner's world
      splattered transparently over your real view). It's throttled to a ~20fps video-feed cadence (`REFRESH_INTERVAL_MS`,
      re-blitting the cached frame between refreshes) so it isn't rendering the world twice every frame. **Fabulous is
      handled, not banned:** its whole-screen transparency chain fights the second pass into a dangerous strobe, so
      `SplitscreenClient.enforceSafeGraphics` flips the player Fabulous→Fancy each tick the split is engaged (undoing any
      mid-curse re-select) and hands Fabulous back when it ends, while `SplitscreenPov` additionally refuses to render on
      any single frame where the mode is still momentarily Fabulous — belt-and-suspenders so a strobe frame can never slip
      through. Minor known cosmetic: a faint horizon/cloud streak in the panel on Fancy (harmless; a real player target
      fills its own chunks in). ✅ signed off by Oliver, live POV verified acceptable (`splitscreenLivePov=true`).
- [x] Cutaway Gag (NEW — statement curse) — SPYGLASS (free item, no collision — the "spectating" fit). Family-Guy
      cutaway made real: after a hard `cutawayCooldownSeconds` (350s) minimum, a low-but-slowly-ramping per-second chance
      (`cutawayBaseChancePercent` + `cutawayRampPerSecondPercent`, capped at `cutawayMaxChancePercent`) CUTS AWAY to a
      random other online player; you're frozen (movement + interaction locked client-side off the synced
      `CUTAWAY_TARGET`) spectating them while — after a `cutawayGagDelay*` 2–6s beat watching the oblivious victim — a
      stupid gag is inflicted; a hit has a high chance (`cutawayHitEndChancePercent`) to snap you back, but the gag's
      effects still stick. **Overhead-camera architecture:** to see a distant player the client needs their chunks loaded
      + entity tracked, so the server saves the watcher's spot, relocates their INVISIBLE, gravity-less (still HITTABLE,
      so "being hit ends it" works from the chaos) body to a computed OVERHEAD VANTAGE (clear LOS to the victim,
      `cutawaySpectateDistance`/`cutawayCameraHeight`; top-down fallback), and the client (`ClientCurseHandler.tickCutaway`)
      keeps the camera on the watcher in FIRST PERSON and pins the aim at the victim — a steady overhead standstill, NOT a
      third-person attach (which was buggy / couldn't see the event). Teleports back on end. Same-dimension victims only.
      **Death-gated:** dying mid-cutaway strobed against the respawn screen, so `onWatcherDeath` (LivingDeathEvent) snaps
      out without teleporting the dead body back, and a victim dying/logging off ends it too; relog/restart self-clears.
      **Flight-safe:** a flying watcher keeps their own gravity (we only `setNoGravity` non-flyers) and abilities are
      re-synced (`onUpdateAbilities`) on both ends, since toggling gravity on a flyer was breaking their descent after.
      **23 gags** (`CurseCutawayGag`), each auto-forcible by name via debug: creeper behind ·
      anvil above head · aggro'd IronGolem (⏳ wall-bursting is a stand-in — golems can't break blocks; aggro + Speed only) ·
      Woolliam (a "Woolliam" sheep — plain custom name, NOT always-visible-tag — that duplicates each ~15t up to
      `cutawayWoolliamCap`) · Driveby (a group of Speed-3
      skeletons that spawn beside the victim, shoot, and despawn when the cutaway ends = "slide away") · Dream (a real
      custom `DreamEntity` player-MIMIC — rendered as a vanilla `PlayerModel` wearing `ugly/dream.png`, jogs up at
      player speed and punches with the custom `witchmod:dream` damage type for a bespoke death message, despawns at end) ·
      Jumped (a pack of Strength/Speed/Resistance zombies) · fake Snail (a real immortal `SnailEntity` +
      the snail music, spawned on real ground near the victim, hand-slid toward them at `cutawaySnailSpeed` each tick
      since it's AI-less, music left to the existing `SnailSoundManager` loop which stops itself when it despawns at end
      — NOT a one-shot, which droned on) · Hole (audible warnings then a 3×3×`cutawayHoleDepth` column dug with drops —
      only chosen when the ground below is mainly natural, via a mineable/dirt/sand tag check) · Abduction (Levitation +
      Slowness + Glowing + a spinning END_ROD saucer rim + GLOW dome + widening green tractor-beam column — ⏳ custom
      UFO sounds pending, pitched beacon hum for now; true horizontal movement-anchor would need victim-side client work) ·
      Launch (slime block + piston placed below, old block dropped, victim catapulted up at `cutawayLaunchPower`).
      **Batch 2:** Tokyo Drifting (forced into a CHERRY boat driven along the ground at `cutawayTokyoSpeed` on a curving
      heading) · Pied Piper (nearby passive `Animal`s navigate to the victim; spawns pigs/cows/sheep/chickens to fill
      `cutawayPiedPiperMin`) · Fake TNT (`cutawayFakeTntCount` TNT drop from above; fakes get a 1000-tick fuse and FIZZLE
      at ~70t with smoke+extinguish, `cutawayFakeTntRealChancePercent` (30%) are REAL and detonate) · Aquarium
      (`cutawayAquariumCount` squid/cod/salmon/puffer/tropical spawn NO-gravity + wander in air, then `finishGag` drops
      their gravity so they fall and flop like normal) · I Like Trains (lays a straight RAIL line through the victim,
      sends `cutawayTrainCount` villager/cow minecarts slamming down it; a cart within 1.6 blocks deals the custom
      `witchmod:train` damage + flings — ⏳ custom sound pending) · Bowling (a no-gravity BLACK_CONCRETE "ball" hurled at
      the victim; impact = custom `witchmod:bowling` damage + fling + Slowness/Weakness stun — ⏳ custom sound pending, cube
      stand-in for a round ball) · The Bouncer (a real `BodyguardEntity` navigates to the victim, shoves them around, and
      trash-talks in local chat) · Annoying Music (synced `CUTAWAY_MUSIC_END` window; client loops a track and nothing
      else — ⏳ dedicated track pending, goofy Loading hold-music placeholder) · Bouncy (borrows the Bouncy blessing's
      `BOUNCY_ACTIVE` flag on the victim for the duration, restored after — uncontrollable bouncing + its boing sounds) ·
      Marriage (a RED_CARPET aisle, a `SolicitorLines.randomName` villager spouse, victim frozen + invulnerable, HEART
      particles and a scripted vows chat). Two new datapack damage types (`witchmod:train`/`bowling`, bypass armour,
      custom death messages) + the `CUTAWAY_MUSIC_END` synced attachment. Per-gag end cleanup via `finishGag` (clears
      placed rails/carpet, restores aquarium gravity + marriage invuln/freeze + bouncy flag + music). Discovers on the
      first cutaway. `debugForce`: `/bewitch debug force witchmod:cutaway_gag @s <args>` — any combo of `villager` (cut
      away to the nearest VILLAGER, testable SOLO — the "debug modes use villagers" pattern) and a gag name (any of the 21
      lowercased, e.g. `tokyo_drifting`, `pied_piper`, `fake_tnt`, `i_like_trains`, `the_bouncer`, `annoying_music`,
      `marriage`); no arg = random player + random gag, forcing again mid-cutaway ends it. The whole pipeline runs on
      `LivingEntity` victims so villagers work everywhere. Reads as a CURSE (watcher immobilised/relocated/vulnerable +
      others griefed). Boots clean. 28 config knobs. ⏳ pending: Oliver's in-game tuning + custom sounds (trains, bowling,
      abduction, annoying music) + the golem/Dream/abduction-anchor stand-ins noted above.
      **Cinematic polish pass:** the spectate view is now a clean hover — `hideGui` on + first-person HAND cancelled
      (`RenderHandEvent`) for the duration. **Victim coords are hidden:** the F3 DEBUG_OVERLAY layer is cancelled while
      spectating so the watcher can't read the victim's position off it. Each cut opens with a Family-Guy TITLE CARD
      (random "Meanwhile…/Cut to…" line + "with <victim>" subtitle) and a PACING_CLICK camera-cut sound, sent only to the
      watcher. **Gag picker no longer repeats** — a per-watcher `LAST_GAG` bias never fires the same gag back-to-back (fixed
      the "abduction every time" feel) and still skips Hole off non-natural ground. Gag DELAY shortened to 0.5–2.5s; Fake
      TNT real-chance 30%→15%. **Abduction overhauled:** a fixed high UFO at `cutawayAbductionHeight` (40) with a
      fast-spinning saucer, a full-height tapering green tractor beam, and Levitation `cutawayAbductionLevitation` (9) for
      `cutawayAbductionLiftTicks` (60) so the victim is yanked way up fast — and the particles STOP the instant the lift
      ends (no more lingering column after the drop).
      **Cinematic-fix pass (22nd gag + bug batch):** added **Speed** (victim gets Speed 50 for the duration). FIXED "text
      doesn't appear" — the intro was a vanilla TITLE packet hidden by `hideGui`; dropped `hideGui` entirely (it also hid
      CHAT, killing marriage vows / bouncer lines) and instead cancel the individual gameplay HUD layers
      (`CUTAWAY_HIDDEN_LAYERS`: crosshair/hotbar/health/food/xp/armor/air/held-name/jump/vehicle/effects/F3) while CHAT +
      the overlay stay. Added a real CINEMATIC OVERLAY (`onCutawayCinematic`, `RenderGuiEvent.Post`): small letterbox bars
      top+bottom + a "Meanwhile…" TITLE CARD from the writable `assets/witchmod/text/cutaway_titles.json`
      (`CutawayTitles`, F3+T-reloadable); `sendCutawayIntro` is now just the PACING_CLICK cut sound. The cinematic ENGAGES
      the instant `CUTAWAY_TARGET` is set (before the victim's chunks load) so there's always an immediate cue instead of an
      occasional frozen "nothing happens". **The Bouncer** now speaks in the EXACT Bodyguard voice (`BodyguardLines.pickTree`
      warning/aggression + the `<Bodyguard> …` plain format + `bodyguardChatRadius`). **Tokyo Drifting**: victim is
      force-re-seated each tick (no escape) + a 25% roll to run 2.5× faster. **Fake TNT** fakes are also tracked as temps so
      an early exit can't leave a live fuse ticking to a real blast. **Launch** breaks its slime block + piston a beat after
      firing you off. Boots clean.
      **Dream + polish pass:** **Dream** is now a real player-mimic (custom `DreamEntity` = a vanilla `PlayerModel` in the
      `ugly/dream.png` skin, `DreamRenderer`, registered entity/attributes) that chases at player speed and punches with the
      new datapack `witchmod:dream` damage type (bespoke death message), replacing the Vindicator stand-in. **Golem** is now
      tracked and has its Speed/Strength buffs STRIPPED in `finishGag` (a plain aggro'd golem afterwards). The no-repeat
      picker now remembers the **last 3 gags** (`RECENT_GAGS` deque, `RECENT_GAG_MEMORY=3`) so the same ones stop recurring.
      `/bewitch debug force` now **tab-suggests each effect's valid arg names** (new `Effect.debugArgs()`; Cutaway returns
      `villager`, `exit`, and all gag names, suggested per whitespace token so `villager <gag>` completes). Boots clean.
      **Foolproofing pass + Helicopter:** new **Helicopter** gag (23rd) — forces the victim into a SPRUCE boat that spins
      fast (`cutawayHelicopterSpin`) with a quickly-ramping ascent (`cutawayHelicopterRiseMax`), re-seated each tick.
      **I Like Trains** rebuilt to be SUDDEN + foolproof: the carts `noPhysics` (noclip) and are hand-driven at a hard
      2.9 b/t each tick (rail friction/walls/gaps can't slow or stop them), spawn close, generous 2.5-block hit radius.
      **Snail** slowed (`cutawaySnailSpeed` 0.09) and now on touch does a victim-only (no terrain/collateral) explosion for
      `cutawaySnailDamage` (12) then ENDS the gag (via a new `endNow` flag ticked in `onTick`). **Anvil** now actually
      hurts (`setHurtsEntities`, dropped from 8 up). **Bouncy** now really FLINGS the victim (a strong random launch every
      8t + boing) so they ping around, not just the client flag. **Bowling** ball is `noPhysics` (noclips terrain) so it
      always connects. **Driveby** skeletons are hand-fired every 7t (~3× the normal bow cadence). **Hole** rolls a 1.5–2s
      warning (`holeWarnTicks`, smoke + thuds) before dropping. **Marriage** no longer LAUNCHES the villager — the amp-128
      Jump ("no jump") was actually +12.9 jump power (the Pacing trap); removed, victim pinned (zero velocity) each tick.
      **Woolliam** spawns 2 sheep per interval and the cap default doubled (8→16). Boots clean.
      **Dream rework + custom sounds pass:** **Dream** is now a real PLAYER-MIMIC — a custom `DreamEntity`
      (`entities/DreamEntity`, a `PathfinderMob` rendered by `client/DreamRenderer` as a vanilla `PlayerModel` wearing
      `assets/witchmod/textures/entity/ugly/dream.png`, reusing the vanilla PLAYER layer so no custom layer def) that jogs
      up at player speed with a `MeleeAttackGoal` and punches with the new datapack `witchmod:dream` damage type (bespoke
      death messages), replacing the Vindicator stand-in. Registered entity + attributes + renderer. **Golem** buffs are
      stripped in `finishGag` (tracked in `flock`) so it's a plain aggro'd golem after the gag. **Gag no-repeat** now
      remembers the last 3 (`RECENT_GAGS` deque, `RECENT_GAG_MEMORY=3`) instead of just the last one. **Debug** tab-suggests
      valid arg names via a new `Effect.debugArgs()` (Cutaway returns `villager`, `exit`, and every gag name) wired into
      `BewitchCommand`'s `arg`. **Custom sounds wired** (in `assets/witchmod/sounds/curse/cutaway/`): Bowling throw
      (`ball_throw`) + impact (`bowling_strike`, 2 variants) · Tokyo `drifting` · Helicopter loop + Abduction tractor-beam
      loop (native-looping client instances driven off a synced `CUTAWAY_LOOP` id, stopped on gag/cutaway end) · Abduction
      `ufo_enter` one-shot before the beam · **I Like Trains sequenced** — `train_warning` ("I like trains") plays IN FULL
      first, then after `cutawayTrainWarningTicks` (34) the train spawns + `train_roll` plays at its location. Boots clean.
      **Tuning + positional-audio pass:** ALL cutaway sounds now play at the EVENT (victim) location, never on the
      watcher — the looped ones (helicopter 0 / tractor beam 1 / annoying music 2) use a new POSITIONAL
      `client/CutawayLoopSound` (an `AbstractTickableSoundInstance` that follows the victim and stops on `CUTAWAY_LOOP`
      clear), replacing the UI-attached loops + the `CUTAWAY_MUSIC_END` window. **I Like Trains**: speed tripled to
      `cutawayTrainSpeed` (8.7 b/t), warning shortened 40→34t, carts PINNED to the rail line (Y + perpendicular via
      `setPos`) so they stay anchored to the tracks. **Helicopter**: boat KEPT after the gag (in `flock`, not `temps`);
      spin RAMPS via delta-yaw with `yRotO`=prev so the client interpolates FORWARD instead of wiggling across the ±180
      wrap, up to `cutawayHelicopterSpin` (62°/t); loop plays at the boat. **Bowling**: stun +2s (100t) and a 50%-each
      chance to bowl AGAIN 1–3s later (max 4, `throwBowlingBall`/`bowlCount`/`nextBowlTick`). **Marriage** overhauled —
      random ceremony script pools (open/vows/pronounce/objections), 35% chance the spouse is a silly creature, a random
      objection, and a random comical ending (kiss / cold-feet bolt / "zombie all along"). **Tokyo drift** screech
      repeats (every 22t) at widely varied pitch + lower volume, from the boat. **Bouncy** flings mostly HORIZONTAL
      (h≈1.4–2.0, up≈0.2–0.35) — pinball, not catapult. Boots clean.
      **Final tuning pass:** **I Like Trains** slowed ~20% (`cutawayTrainSpeed` 8.7→7.0) so it's more visible.
      **Creeper** gets Speed I on the approach. **Bowling** is now DODGEABLE — the ball flies STRAIGHT along its launch
      direction (no more homing) at a steady 1.4 b/t, whiffs past if you step aside, and a miss (ball >26 blocks off)
      resolves the bowl too (still rolls the 50% follow-up). **Marriage** text is now fully editable in
      `data/witchmod/text/marriage.json` (`/reload`-able) via a new `data/MarriageLines` (SimpleJson listener, keys
      `open`/`vows`/`pronounce`/`objections`, `{v}`/`{s}` placeholders) — the inline pools were removed. Boots clean.
      **Polish pass 2:** Marriage's two OUTCOME lines are now editable too (marriage.json keys `kiss`/`bolt`); the
      "zombie all along" outcome was REMOVED, leaving **kiss** (hearts + level-up fanfare) and **cold-feet bolt** (the
      spouse legs it down the aisle) — `marriageOutcome` is now `rng.nextInt(2)`. **Bowling** knockback cut to a tiny
      nudge (0.25) — the stun carries the impact, feels heavier. Boots clean.
      **Stuck-forever / reload-strand fixes:** the return position was only in a TRANSIENT `SAVED` map, so a
      relog/world-reload mid-cutaway stranded the watcher at the overhead vantage. Now ALSO persisted in a new
      serialized `CUTAWAY_RETURN` attachment (dim+pos+yaw/pitch+invis/nograv), written at cutaway start, cleared at
      end; the relog-recovery path and `endCutaway`'s no-SAVED fallback both teleport HOME from it via a shared
      `restoreTo`. `endCutaway` now clears `CUTAWAY_TARGET`/`CUTAWAY_LOOP` FIRST (camera un-sticks + looped SFX stop
      even if a later step throws), and the gag start/tick step in `onTick` is exception-guarded so a misbehaving gag
      can never abort the end-of-cutaway logic (was the "stuck spectating forever + helicopter loop droning on" cause).
      Also fixed the user-edited `marriage.json` (trailing comma after the last `open` line broke the whole file, so
      the marriage gag silently lost its lines). ⏳ helicopter.ogg still needs a seamless-loop re-export (the loop
      itself now always stops on gag/cutaway end; the poor loop is the audio file, not the code).
      **Debug-drive fix:** `/bewitch debug force witchmod:cutaway_gag` starts a cutaway WITHOUT applying the curse, so
      the gag (driven by the curse's `onTick`) never fired and you watched forever. The in-flight cutaway is now
      progressed every server tick from `CurseEventHandler.onServerTick` via `CurseCutawayGag.driveActiveCutaway`,
      independent of whether the curse is applied — so debug-forced cutaways fire + end, and a cutaway whose curse
      was removed still cleans up. `onTick` returns early while `CUTAWAY_TARGET>=0` (the tick driver owns it), so
      it's never double-ticked.
      **Marriage overhaul — FOUR paths + cinematic camera** (`startMarriage`/`tickMarriage`, ceremony length
      `cutawayMarriageTicks` 260 so it always completes): (0) **normal** — pronounce → romantic push-in → kiss with
      hearts + level-up + bell → slow orbit of the newlyweds; (1) **objected** — a player-MIMIC (a `DreamEntity`
      named after a random ONLINE player, villager fallback) bursts in on an anvil-land/goat-scream, "I OBJECT!!!",
      strides at the couple with chat lines (marriage.json `objector`), hard cuts between an objector face-zoom and
      the couple's shocked hop, then the wedding is called OFF (`objected`) and the spouse bolts; (2) **explode** —
      ominous creep-in on the spouse (TNT-prime hiss) then it just detonates (`level.explode` MOB-gated,
      `cutawayMarriageExplodePower` 2.0) with the deadpan "<name> exploded for some reason"; (3) **what** — the
      officiant line becomes just "§7[Officiant] what" and the spouse is 50/50 flung away or turned into a strider.
      **Camera:** new synced `CUTAWAY_LOOK` lets a gag aim the spectate camera at something OTHER than the victim
      (the objector, the doomed spouse); the camera POSITION is the watcher's body, teleported each tick via
      `applyCam`/`camShot` (hard cuts + smooth pushes + orbits) around the couple. Reset on teardown
      (endCutaway/clearSpectateState/finishGag). Debug can force a path: `/bewitch debug force witchmod:cutaway_gag
      @s villager object|explode|what|normal`. ⏳ pending: Oliver's in-game camera tuning + any dedicated objection
      sting (goat-scream/anvil placeholders for now).
      **Slow + rich overhaul pass:** ceremony length `cutawayMarriageTicks` 260→**440** (~22s) with the whole timeline
      re-spaced deliberately slow (gentle sub-0.05 camera pushes over long holds instead of quick cuts). Added a
      real **Officiant** villager (conducts + speaks the [Officiant] lines), **6 seated guests** down both sides of
      the aisle (villagers/allay/cat/wandering-trader, NoAi) who **hop + cheer** at the kiss and **gasp** at the
      objection/explosion (`guestsReact`), and constant **cherry-blossom petals** drifting over the aisle
      (`marriagePetals`). Each path's climax now lands much later (normal kiss @310 with a "You may kiss..." beat +
      guest celebration; objection burst @224 with a longer 7-block dramatic walk-in, slower stride + face/reaction
      cuts, off @330; explode builds smoke+ticks to @262; "what" @216 with an added "anyway" @285). All added
      entities go in `temps` (despawn at cutaway end). Boots clean.
      **Pacing + anti-teleport pass:** the OBJECTION was still too fast/unreadable, so the marriage now runs a
      PER-PATH length (`cutawayMarriageTicks` base 500; objection +220=720, explode −20, what −60) with every beat
      re-spaced ~50+ ticks apart: objection = record-scratch @300 → burst-in from 9 blocks @350 → objector lines
      @405/505/620 alternating with held couple-reaction cuts @455/565 (camera cut to the objector at 500/590 just
      before each line) → wedding OFF @665, over a very slow 0.03-block/tick menacing walk-in and 0.06 camera
      pushes. Intro beats also spread (0/45/135/205/265). **⚠ FOOLPROOF ANTI-TELEPORT GUARD** (the gag must never
      become "teleport to any player"): the return position is persisted in `CUTAWAY_RETURN`, and because
      `CUTAWAY_TARGET` is sync-only (resets to -1 on relog so the tick driver skips them), a new
      `CurseCutawayGag.recoverIfStranded` runs every server tick for any NON-spectating player AND on
      `PlayerLoggedInEvent` — if a return tag is still stored (relog / server restart / death / curse stripped),
      it teleports them back to their real pre-cutaway spot and clears state. They can never be left at the
      overhead vantage next to whoever they cut to. Boots clean.
      **Stale-config fix:** explode/"what" were ending abruptly with NO event because the per-path length was
      `configBase ± offset` and the on-disk `cutawayMarriageTicks` was still the original 260 (NeoForge keeps
      existing config values when the code default changes), so explode(240)/what(200) ended before their beats
      at t=300+. Now each path floors to the ticks its choreography actually needs — `Math.max(base, {normal 500,
      object 720, explode 480, what 440})` — so the config can only EXTEND a path, never cut it off. Works
      regardless of a small/stale config value.
      **Bowling rework + Parade gag (2026-08-21):** **Bowling** is now crowd-weighted — `pickGag` counts
      LivingEntities within 6 blocks of the victim and, past `cutawayBowlingClusterMin` (3), returns Bowling on a
      `cluster × cutawayBowlingClusterWeight` (0.12, capped 70%) roll, so a group makes it far likelier. The ball
      now **PIERCES**: instead of stopping on the victim it flies through and bowls over EVERY entity in its path
      (`bowlPin` — a scatter-launch along the ball's travel + up, damage + brief stun), tracked in `bowledIds` so
      each pin is hit once; it only ends when it's flown past the whole cluster. All other bowling behaviour
      (dodgeable straight flight, up-to-4 sequential bowls) is unchanged. NEW gag **Parade** (`startParade`/
      tickGag `PARADE`, `cutawayParadeCount` 14): a column of varied entities (`PARADE_TYPES`) spawns to one side
      and MARCHES dead-straight across the victim's front — all `setNoAi(true)`, moved BY HAND each tick
      (`move()` + manual gravity + a hop when `horizontalCollision`) so they walk over ANY terrain instead of
      floating/sticking (2026-08-22 fix), spawn-grounded via the heightmap — then despawn (temps) with the
      cutaway. **Confetti** rains over the column. **Both** parade tracks play together (2026-08-22) —
      `curse.cutaway.parade_drum` + `curse.cutaway.parade_march` at the marching column, at 55% volume (45%
      quieter), heard by everyone nearby, cut via `ClientboundStopSoundPacket` in `finishGag`. Anything the
      column **tramples** is knocked away + hurt with the custom `witchmod:parade` damage type
      (`cutawayParadeDamage` 6, bypasses armour, death "%s was trampled by the parade"). Debug-forcible
      (`… cutaway_gag @s villager parade`).
      **Camera framing (2026-08-23):** the parade is now framed to the SPECTATOR camera, not the victim's own
      facing (which was arbitrary relative to where the cutaway camera looks from). `startParade` takes the
      `watcher` and builds "forward" from the camera→victim direction; the column marches perpendicular to THAT
      (so it sweeps across the frame) and sits BETWEEN the camera and the victim (`victim − forward×2.5`) so it
      fills the middle of frame, close and unmissable, instead of shrinking off behind the victim.
      **Broken-parade fix (2026-08-24):** it spawned marchers at the SURFACE heightmap, so for an underground
      victim the column appeared up on the surface near the spectator camera — and the trample loop then hurt
      the SPECTATOR. Now marchers spawn at the victim's own Y (gravity settles them onto the real floor) and the
      trample explicitly excludes the `watcher` — the cutaway must never damage the spectator.
      **Wedding music (2026-08-15):** `curse.dweller`... no — `curse.cutaway.wedding` OGG plays ONCE at the
      ceremony (SoundSource.RECORDS, `level.playSound` so all nearby incl. the spectator hear it; the track
      outlasts the scene) and is cut dead via `ClientboundStopSoundPacket` to everyone nearby when an absurd
      twist fires (objection @350 / explode creep @300 / "what" @300) or the wedding ends (`finishGag`).
- [x] Carelessness (NEW, 2026-08-21) — Black Wool. Purely a client-render curse: the vanilla health layer is
      cancelled (`client/CarelessnessHud`, `RenderGuiLayerEvent.Pre` on `PLAYER_HEALTH`) and every heart is drawn
      IDENTICAL + pure black (vanilla heart sprite tinted black over the container), so you genuinely can't read
      your health — the real value + all mechanics untouched. Off synced `CARELESSNESS_ACTIVE`. Discovers on apply.
- [x] Narcolepsy (NEW, 2026-08-21) — White Bed (soft-adjacent to Homebody's Red Bed). Every 35s–5.5min (gap
      `sqrt(random)`-biased toward the LONGER half) you drop asleep where you stand for 4–12s: server sets the
      sleeping pose (`setSleepingPos`+`setPose(SLEEPING)`, re-asserted each tick so vanilla's sleep bookkeeping
      can't cancel it) + a synced `NARCOLEPSY_SLEEP_END`. Client (`client/NarcolepsyClient`) kills ALL input
      (movement/interaction/look pinned), washes the screen near-black, and shows a **MASH TO WAKE bar that
      constantly DRAINS** (`DECAY` 0.022/tick) so you must hammer the movement keys faster than it falls — a real
      little struggle; filling it sends `NarcolepsyWakePayload` → `CurseNarcolepsy.wakeEarly`. Debug-forcible. 4
      config knobs.
      **Polish (2026-08-22):** the lying-down pose is now re-asserted CLIENT-side too (`setSleepingPos`+
      `setPose(SLEEPING)` on the local player each tick) so the model actually lies down, and the view is
      FORCED to first person for the sleep (saved/restored on wake). The dark overlay is now a **vignette**
      (faint centre, dark edges via concentric frames) rather than a flat wash. **Fast healing** while asleep
      (`heal(1.0F)` every 7 ticks — 30% slower than the first pass) and **intermittent snores** at varied pitch
      (`curse.narcolepsy.snore`, every ~2.25s).
      **Camera-glitch fix (2026-08-22):** the client no longer forces the pose locally or re-asserts first
      person each tick, and the server dropped its per-tick velocity correction — those were battling the
      server and juddering the camera. The pose is now server-only (syncs to the local model too via the
      sleeping-pos), first person is set once on nod-off + restored on wake. **Taking a hit** while asleep now
      fills 40% of the mash bar (client-side health-drop detect), and **left/right click** count toward mashing
      alongside WASD/space. Damage lands normally while asleep (no invulnerability).
      **Physics-intact rework (2026-08-23):** the server no longer sleeps the player AT ALL — no `setSleepingPos`/
      `setPose` anywhere server-side — so gravity, fall damage, collision and knockback all stay fully vanilla
      while "asleep" (Oliver: "still be affected by gravity and take fall damage etc"). The sleep is now purely a
      synced end-tick + client effects: input is dead, the screen darkens, the mash bar runs. The lying-down
      ANIMATION is faked entirely CLIENT-side and only for OTHER viewers — `NarcolepsyClient.renderOtherSleepers`
      iterates `mc.level.players()` and, for any other player whose `NARCOLEPSY_SLEEP_END` is in the future,
      sets `setSleepingPos`+`SLEEPING` pose on their RemotePlayer (the server keeps them standing and never sends
      a pose change, so the fake holds until they wake; tracked in a `FAKED` set to restore on wake). The LOCAL
      player gets NO pose at all — that's what finally killed the camera flicker (the earlier server-driven pose
      was the flicker's source); the first-person + look-pin stays for a steady view. Healing every 7 ticks +
      snores unchanged.
      **Depth + idle + Zs + third-person-debug pass (2026-08-23):** (1) **Rarer deep sleeps** — each sleep rolls a
      DEPTH (0 normal / 1 deep `narcolepsyDeepChancePercent` 24% / 2 very deep, a further `narcolepsyVeryDeepChancePercent`
      33% of those): deeper sleeps LAST longer (×1.4 / ×1.8) and need MORE mashing (client scales per-press gain
      ×0.62/×0.42 and the drain ×1.15/×1.30), with a darker wash + a "DEEP SLEEP — MASH(!/ HARD!)" prompt. Depth is
      one synced int `NARCOLEPSY_DEPTH` (low digit = tier, +10 = the debug flag). (2) **Idle accelerates the
      countdown** — standing still past `narcolepsyIdleAccelTicks` (60 = 3s) shaves an extra tick off the next-sleep
      timer each tick, so the countdown runs at DOUBLE speed while idle (a narcoleptic nods off sooner sitting still);
      tracked via a per-player last-position/idle-ticks map. (3) **Sleep Zs** — a new custom particle
      `witchmod:sleep_z` (`WitchModParticles` + `client/SleepZParticle`, a rising/​swaying/​fading `TextureSheetParticle`
      off `textures/particle/zparticle.png`) is emitted server-side from the sleeper's head every 11 ticks, so every
      viewer sees them. (4) **Slightly stronger decay** (0.022 → 0.026). (5) **Third-person debug** —
      `/bewitch debug force witchmod:narcolepsy @s thirdperson` (also `deep`/`verydeep`) starts a sleep, forces
      THIRD_PERSON_BACK, and poses the LOCAL player lying (re-asserted each tick — vanilla's `updatePlayerPose` keeps
      SLEEPING while a sleeping-pos is set) so you can watch your own animation; restored on wake. `debugArgs` lists
      the three. First custom particle in the mod (registry + provider + definition JSON + atlas texture).
      **Translatable text (2026-08-23):** the three on-screen prompts are now `Component.translatable`
      (`witchmod.narcolepsy.mash` / `.mash_deep` / `.mash_very_deep` in the lang file) so they're editable.

**BLESSINGS (44 listed; header says 45 — reconcile if a 45th is intended)** — renamed ids: Workman =
`tools_dont_use_durability`, Personal Trainer = `trainer`, Hawk Guy = `locked_in`.
- [x] Fortune — ore-tag blocks give 0..max EXTRA drops via BlockDropsEvent, additive after enchant Fortune (triangular roll peaked at 1), grows the resource stack, skips silk-touched ore blocks. Discovers on first extra.
- [x] Peace — opposite of Popularity: FinalizeSpawnEvent cancels most hostile natural spawns nearby; hostiles only aggro at 40% of normal follow range (too-distant aggro dropped each sweep). Discovers when a hostile in range+LoS isnt hunting you.
- [x] Luck — amplified vanilla LUCK attribute (transient ADD_VALUE, self-healed). Discovers on first fish (ItemFishedEvent) or opening a loot-tabled chest (pending getLootTable on right-click).
- [x] Fullness (renamed from Full; id full→fullness) — hunger+hidden saturation drain at 20%. Watches outputs on PlayerTickEvent.Post: saturation refunded 80% of drops, food undone+banked releasing one per 1/rate. Eating untouched. Discovers on first slowed drain.
- [x] Army — nearby hostile MONSTER mobs (not NeutralMobs) go neutral: acquisition vetoed via LivingChangeTargetEvent + damage cancelled backstop; getting hit by a genuine aggressor rallies the horde onto it (re-aimed each sweep), sparing its own kind. Discovers when a hostile in range+LoS isnt attacking you.
- [x] Reflect — projectiles caught on ProjectileImpactEvent, sent precisely back at the shooter 1.5x faster (no homing); impact cancelled, re-ownered to you (cant re-hit you, can hurt shooter), nudged clear. Own/owner-less ignored. Discovers on first reflect.
- [x] Soul Bond — nearest LivingEntity (mob/pet/player) gets custom soul_bound MobEffect + golden particles and takes 40% of hits you take (you eat 60%); golden trail to whoever paid. STICKS until the bound leaves radius (not a worse Thorns). Custom soul_bond damage type, never re-shared.
- [x] Bodyguard (CUSTOM ENTITY) — black-leather sunglasses skeleton bound to you: WARNING (players+villagers by name) → AGGRESSION (warning hits/shoves; patience+2 warnings → draws sword) → ATTACKING (anything that hits you/it, until dead or past leash). Teleports to you, never targets/retaliates the anchor. No dupes (transient + CANONICAL self-heal). Local chat from bodyguard.json.
      **Respawn pass (2026-08-23):** its death NO LONGER breaks the blessing — instead a replacement is hired
      `bodyguardRespawnTicks` (7200 = 6min) later. The death hook (`onBodyguardDeath`) schedules it + announces a
      `fell` line; `onTick` waits out a per-anchor `RESPAWN_AT` timer then `summon`s a fresh one + speaks a
      `respawn` line. Both keys are editable in `bodyguard.json` (broadcast in the entity's `<Bodyguard>` voice to
      players within `bodyguardChatRadius`).
- [x] Payday (renamed from Tax Man blessing) — see §6 entry.
- [x] Hype Man — nearby players praise you by name in chat: combat (AttackEntityEvent), pickup, loot (ChestMenu open), building (block place), and ambient sighting. Specific actions need a real nearby player; only the sighting has a no-player fallback using a made-up name from the shared usernames.json. Shared cooldown + chance, writable hypeman.json. Any music disc via Effect.sacrificialTag() Rule-9 hook. No mechanical effect.
- [x] Workman (renamed from Tools Dont Use Durability; item Netherite Scrap→Obsidian) — tools AND armour take no durability: watches the 6 equipment slots and heals any wear straight back (inverse of Heavy Handed), so no permanent Unbreakable component and nothing can break. No config.
- [x] Pickpocket — being very close to a player has a chance (x4 when behind) to lift a random item into your inventory, weighted away from their hotbar; quiet thief-only sound; fails if your inventory is full; skips creative/spectator. Item String. 5 knobs.
- [x] Windfall — a mostly-beneficial item drifts in on the wind near you every 2-6min (first one sooner), spawned upwind with a downwind drift + cloud gust + wind sound; puffs away after 30s if ignored. Curated beneficial/junk pools in code. Item Wind Charge. 4 knobs.
- [ ] Immortality
- [ ] Sixth Sense
- [ ] Iron Stomach
- [x] Iron Lung — breathe underwater (air topped up to full each tick, so no depletion/drowning) AND in blocks (in_wall suffocation damage cancelled in BlessingEventHandler; drown cancelled as backstop). No vanilla Water Breathing effect. Item Kelp, no config.
- [ ] Anchor
- [ ] Twinkletoes
- [ ] Personal Trainer
- [ ] Studious
- [ ] Twist of Fate
- [ ] Company
- [ ] Organised
- [ ] Nightowl
- [ ] Steady Hands
- [ ] Hawk Guy
- [ ] Main Character
- [ ] Last Stand
- [ ] Jesus
- [ ] Thick Skinned
- [ ] Farmer's Spirit
- [ ] Brute
- [ ] Blacksmith
- [ ] Unseen
- [ ] Silver Tongue
- [x] Hot Stuff — Coal. A lit furnace/blast furnace/smoker you're LOOKING at (within `hotStuffLookRange` 8) cooks
      **5× faster** (`hotStuffSpeedMultiplier` 5.0): `AbstractFurnaceBlockEntity.serverTick` is run the extra times,
      so fuel burns proportionally faster too (fuel-per-item unchanged, just quicker). Discovers on first boost.
      (Campfires deferred — awkward cook-tick signature.)
- [x] Bouncy — **MOVED TO A CURSE** (2026-08-19, Oliver's call). `BlessingBouncy` → `CurseBouncy` (curses package,
      `EffectCategory.CURSE`, registered `Curses.BOUNCY`, all `Blessings.BOUNCY` refs repointed). Same rubber
      physics (rebound/build-height client-side, walk/sprint entity bounces, melee "get off me") PLUS a new
      `JUMP_STRENGTH` +50% (`bouncyJumpBonus`, transient + re-asserted) so you spring higher while afflicted.
      Item stays Slime Ball.
- [ ] Excavation
- [ ] Angler
- [ ] Laugh Track
- [x] Chat — full personal Twitch overlay. Server-driven ChatLinePayload lines rendered by `client/ChatOverlayLayer`
      (LIVE header, exponential viewer count, hype bar, sub count, `[SUB]/[MOD]/[VIP]` badges, coloured banners for
      subs/donations/raids). 30+ category `twitch_chat.json` (/reload-able) + `TwitchChat` loader. Internal
      entertainment SCORE fed by many actions (PvP kill=highest, kill/clutch/ore/tame/crit/fish/trade/eat/build/mine,
      taking big hits, fall fails), decaying fast when idle; a combo streak builds a multiplier and chained highlights
      fire a HYPE TRAIN (sub surge). SUBS accrue from the viewer count (exp. base→max), capped, cashed out at stream's
      end for EMERALDS ONLY (1 per `chatSubsPerEmerald`). DEAD-chat phase you must crawl out of (hysteresis) with its
      own pool. Static emote images + animated gif sprite-sheets (`textures/gui/chat/`) spammed by hype; dying WIPES a
      big chunk of interest and spams dealwithit/trolldance. Eased "shown hype" so climbs/drops are gradual. Persists
      through death (respawn re-syncs the wrapper; CHAT_OVERLAY/CHAT_SUBS copyOnDeath). ~35 config knobs.
- [ ] Civilisation
- [x] Low Gravity — REFINED off the Jump-Boost/Slow-Falling prototype to real physics: a `GRAVITY` attribute
      multiplier (`lowGravityGravityMultiplier` 0.55) gives the constant floaty rise+slow-fall, a `JUMP_STRENGTH`
      multiplier (1.6) launches you higher, and fall damage is cut (`lowGravityFallDamageMultiplier` 0.4) on
      `LivingFallEvent`. Both modifiers transient + self-healed in onTick. Item Eye of Ender. 3 config knobs.
- [x] Builder (NEW) — Any Planks (planks-tag sacrificial exception, like Hype Man). Removes the client-side place
      (`Minecraft.rightClickDelay`) and break (`MultiPlayerGameMode.destroyDelay`) cooldowns via reflection off the
      synced `BUILDER_ACTIVE` flag, so you build/tear-down at click speed. Self-heals through death. No config.
- [x] Berserker (NEW) — Iron Axe. Consecutive LANDED hits progressively speed up your attack cooldown
      (`berserkerReductionPerHit` 7% each, `berserkerMaxReduction` 70% cap) via a growing `ATTACK_SPEED` modifier;
      hits counted on `AttackEntityEvent`. Resets fully on a MISS (client reports air-swings via a C2S
      `BerserkerMissPayload`) or after `berserkerResetSeconds` (4.5s) with no hit (onTick timeout). Stacks held
      server-side per-player; modifier self-heals. 3 config knobs.
- [x] Enchanter (NEW) — Lapis Lazuli. Three table perks, all event-driven: XP cost softened via
      `PlayerXpEvent.LevelChange` (gated to `EnchantmentMenu` open, `enchanterXpReduction` 0.8 → small costs free);
      offered levels bumped (`EnchantmentLevelSetEvent`, gated to a nearby Enchanter, `enchanterLevelBonus` +3); and
      BAD enchants stripped off the result (`PlayerEnchantItemEvent`) — Smite, Bane of Arthropods, Blast/Projectile/
      Fire Protection, Piercing, and any curse (via `EnchantmentTags.CURSE`). 2 config knobs.
- [x] Pacifier (NEW) — Allium (⚠ Poppy was requested but is Peace's item; using Allium, a free flower — swappable).
      Anti-grief aura: a per-tick sweep discards primed TNT + TNT minecarts, deflates & pacifies creepers
      (`setSwellDir(-1)`+`setTarget(null)`), discards fireballs, and snuffs spreading fire, each with a protective
      END_ROD+smoke fizzle + extinguish hiss. Entity/projectile sweep runs EVERY tick (fast fire charges),
      block sweep on the interval. Harmful projectiles = `AbstractHurtingProjectile` (fire charges/fireballs/wind
      charges/wither skulls). 3 config knobs (entity radius, block radius, interval).
- [x] Ocean's Blessing (NEW) — Any Coral (new `witchmod:corals` item tag, tag-exception like Hype Man). MODEST baseline
      forward swim (client-side off synced `OCEANS_ACTIVE`, `oceansSwimBoost` 0.025, capped at `oceansMaxSpeed`); the big
      speed comes from DOLPHINS — `onTick` draws every dolphin in a wide range (`oceansDolphinAttractRadius`) and steers
      them to follow, and a close dolphin grants Dolphin's Grace → ×`oceansDolphinMultiplier` (4.0) swim boost. Aggressive
      mobs pacified while you're wet (`onTick` clears aggro + `LivingChangeTargetEvent` veto). NO water breathing. 8 knobs.
- [x] Gladiator (NEW) — Golden Sword (⚠ Iron Axe was requested but is Berserker's; Golden Sword, free + arena-themed).
      Right-click a sword/axe → 0.5s parry window (`RightClickItem`); a frontal hit during it is fully negated (CLANG) and
      after a short delay you riposte for `gladiatorRiposteDamageMultiplier` (1.5×) weapon damage + extra knockback (woosh
      on swing, IMPACT on land), spending only HALF your weapon cooldown (attackStrengthTicker reflection). A whiff = woosh
      + FULL weapon cooldown + the longer cooldown (3s success / 4.5s whiff). Held shield always wins (`isBlocking()`).
      Synced parry/cooldown ticks drive a SMALL, translucent crosshair HUD bar just under the attack indicator
      (`GladiatorParryLayer`: slightly-yellow ready, gold window, grey recharge). Weapon poses into a block guard in
      BOTH views while parrying, no mixins: third person via a sword/axe `IClientItemExtensions.getArmPose`→`BLOCK`
      (synced to trackers, so others see it), first person via a `RenderHandEvent` tilt (`GladiatorClientHandler`).
      Whiff woosh plays on parry START, whiff, and the riposte swing. 5 custom sounds (parry/whiff/riposte/perfect/reflect).
      Window 0.2s (tight), cooldowns 2.8s/3.05s, modest knockback. HUD gauge is a tiny pixel-art SHIELD that fills with
      the stage and AUTO-HIDES after a second full (reappears on parry/recharge). Projectile parries get a wider both-ends
      window (`gladiatorProjectileLeewayTicks`) than melee, surviving the window's whiff. Riposte clears i-frames so it bites.
      RISK: while parrying your hand is LOCKED client-side off synced `GLADIATOR_LOCK_END` (no switch/swing/use/pick/drop/swap
      — slot reverted, attack/use cancelled), +0.1s on a whiff. Weapon swing cooldown is then imposed via the attack ticker:
      SWORD timing on a successful riposte/reflect (`gladiatorSwordCooldownTicks`), slower AXE timing on a whiff
      (`gladiatorAxeCooldownTicks`), applied to whatever weapon is held. HUD shield lowered + auto-hides after 0.65s full.
      LEEWAY: an unparried melee hit is remembered (`LivingDamageEvent.Post`) and a parry pressed within
      `gladiatorLeewayTicks` (4 = ~0.18s) AFTER it retroactively succeeds, refunding the damage (`player.heal`) — timing
      isn't clunky. PERFECT parry (danger caught within `gladiatorPerfectTicks` of the guard): `gladiatorPerfectDamageMultiplier`
      1.75× (normal 1.4×), STUNS the foe (Slowness 6 + Weakness, `gladiatorPerfectStunTicks`) so knockback throws them, a
      shine sound layers on top, and END_ROD/FIREWORK/FLASH FX fire. Impact FX everywhere: directional spark spray toward
      the attacker, whiff fumble-puff, all with a synced camera SHAKE scaled per outcome (perfect>normal>whiff, reuses
      `applyShake`). PROJECTILE parries (`ProjectileImpactEvent`): a frontal shot in-window is reflected exactly where you're
      LOOKING at `gladiatorReflectSpeed` with a SOFT assist-aim toward an entity in your view cone (`gladiatorReflectAim*`,
      not a hard lock), re-ownered, with a subtle `reflect` clash + spark trail. Melee path uses the source's DIRECT entity
      so ranged never triggers a melee riposte. The parry weapon-cooldown is mirrored to the CLIENT ticker (synced
      `GLADIATOR_WEAPON_READY`) so the attack indicator actually shows it. ~19 config knobs.
- [x] Cow (NEW) — Leather. Right-click nothing with an empty bucket to milk YOURSELF (→ milk bucket), and other players can
      milk you the same way (`RightClickItem` self + `EntityInteract` other; `ItemUtils.createFilledResult` + cow-milk sound).
      Purely a joke, no other effect. Discovers on first milk.
- [x] Tank (NEW) — Cobbled Deepslate (⚠ was requested as Deepslate then corrected; freed by moving Claustrophobia →
      plain Deepslate). `MAX_HEALTH` attribute modifier (`tankBonusHealth` +20 = an extra bar) — NOT a Health Boost effect,
      so no status-effect UI anywhere; transient + self-healed. Natural regen significantly slowed via `LivingHealEvent`
      (small heals ≤1 HP ×`tankRegenMultiplier` 0.35). 2 knobs.
- [x] Spider (NEW) — Fermented Spider Eye (⚠ Spider Eye is Neutral Aggression's). Client-side wall-climb off synced
      `SPIDER_ACTIVE`: push into a wall to climb at `spiderClimbSpeed` (0.3), sneak to CLING/hang, and JUMP off a wall
      (`spiderWallJump*`) to kick up-and-away — wall-jump between walls; fall damage zeroed. 4 knobs.
- [x] Ninja (NEW, statement) — Black Dye (⚠ Ink Sac is Unseen's). MOVEMENT_SPEED + ATTACK_SPEED attribute modifiers
      (`ninjaSprintSpeed`/`ninjaAttackSpeed`), a client mid-air DOUBLE JUMP off synced `NINJA_ACTIVE` (smoke-ring + POOF FX),
      the parry-whiff woosh on every swing at `ninjaSwingPitch` 1.7, and a sprint smoke trail. Attributes self-heal.
- [x] Backstabbing (NEW) — Nether Brick (⚠ Echo Shard requested but is Echoes'; Nether Brick was the fallback). A MELEE hit
      from BEHIND (`LivingIncomingDamageEvent`, direct entity = attacker) multiplies the FINAL damage ×`backstabDamageMultiplier`
      1.6 (stacks with crits/enchants) with reduced knockback (`LivingKnockBackEvent`, per-victim tick mark). Rear arc is far
      MORE generous for mobs (`backstabMobDot`) than players (`backstabPlayerDot`) since mob AI faces you. Riposte sound ×1.2.
- [x] Prop Hunt (NEW) — Flower Pot. Crouch + stand still `propHuntStillTicks` (1s) → disguise as the block below (state id
      synced via `PROPHUNT_BLOCK`); `client/ClientCurseHandler` cancels `RenderPlayerEvent.Pre` and draws that block via
      `renderSingleBlock`, for everyone. The disguise PERSISTS through walking/jumping (block follows you); CROUCH re-anchors
      onto the grid + re-samples the block underfoot (borrow other blocks). Only a real ACTION (attack/mine/use/place/interact)
      pops it back. Exact grid height: the anchor CELL is computed from the SUPPORT block and synced as a `BlockPos`
      (`PROPHUNT_ANCHOR`), so the render is never sunk by the feet resting below the integer. POP + POOF on each transition.
- [x] Blessing of Speed (NEW, 2026-08-19) — Sugar. Its OWN `MOVEMENT_SPEED` +60% modifier applied ONLY while
      sprinting (toggled in onTick), so it's NOT the Speed effect and STACKS with Speed potions + other blessings
      (e.g. Ninja). Cool FIREWORK + ELECTRIC_SPARK spark trail while sprinting. **FOV zoom is TRIMMED, not
      cancelled** (2026-08-21): `onSpeedFov` keeps ~30% of the natural sprint zoom (`1 + (natural-1)*0.3`) off
      synced `SPEED_ACTIVE`, so sprinting still reads as fast without the nauseating full zoom for that speed.
      Discovers on first SPRINT (not on apply). 1 config knob.
- [x] Forgiveness (NEW, 2026-08-19) — Name Tag. Entity hitboxes are effectively ~40% bigger BUT ONLY FOR YOU:
      (a) a MELEE near-miss connects — client `LeftClickEmpty` handler (`onForgivenessAssist`) does an enlarged
      raycast and attacks the best candidate; (b) YOUR projectiles curve onto an entity whose enlarged box they
      were about to pass through (`ProjectileBlessingHandler.forgivenessTarget` → `steer`). Inflate = max(config,
      bbWidth×0.2) so tiny/baby mobs get a real boost. Synced `FORGIVENESS_ACTIVE` flag for the client half.
      Discovers on the first assisted SHOT (not on apply). 2 config knobs.
- [x] Drive (NEW, 2026-08-19) — Golden Carrot. Nearby ADULT animals within `driveRadius` have their post-breeding
      love-lockout cleared each tick (`setAge(0)` for age>0; babies left alone), so you can breed a herd as fast as
      you can feed them. Occasional heart particles. Discovers on the first cleared cooldown (not on apply). 1 knob.
- [x] Vein Miner (NEW, 2026-08-19) — **IRON ORE** (was Diamond Pickaxe). Break an ORE or LOG and the whole
      connected vein/tree comes down (26-neighbour flood-fill, `veinMinerMaxBlocks` cap 64), at HALF durability per
      extra block. **Fortune works BOTH ways**: `Block.dropResources(...tool)` runs the loot table with the tool
      (enchant Fortune/Silk) AND fires NeoForge `BlockDropsEvent` with the breaker set (so the Fortune BLESSING's
      hook boosts them too). Cool break FX — per-block crack dust + CRIT + HAPPY_VILLAGER, ENCHANT + FLASH flourish
      at the origin. `BUSY` ThreadLocal guards recursion. `BlockEvent.BreakEvent` hook.
- [x] Collector (NEW, 2026-08-19) — Barrel. Nearby dropped items drift to you; XP magnet on steroids (huge radius/
      speed). **GRACE period** (`collectorGraceTicks` 60 = 3s via `item.tickCount`) so fresh drops settle before
      homing; **CROUCH** shrinks the radius hard (`collectorCrouchRadiusMultiplier` 0.15) to grab specific drops. 7 knobs.
- [x] Restock (NEW, 2026-08-19) — Chest. Fires on USE not on moves: watches the hotbar/offhand for a slot whose
      SAME item DROPPED in count (a place/consume) and tops it back up to a full stack from the backpack — so
      placing torches keeps your hand at 64 while you have spares. A type-change/empty (a MOVE) is ignored, fixing
      the phantom-refill-on-reorganise bug.
- [x] Sanguine (NEW, 2026-08-19) — Red Dye. Natural regen throttled to 20% BUT 30% LIFESTEAL of all damage you deal
      (melee + projectile, `LivingDamageEvent.Post`). Cool FX: a stream of blood dust drawn FROM the victim (wound +
      DAMAGE_INDICATOR) INTO you (HEART), with a subtle AMETHYST_BLOCK_CHIME "restored" cue (replacing the drink glug).
      Discovers on first lifesteal.
- [x] Homebody (NEW, 2026-08-19) — Red Bed. Within `homebodyRadius` of your spawn point (bed/anchor if set here,
      else overworld shared spawn) you get Regeneration + Haste (ambient, refreshed each tick). Discovers near home.
- [x] Sonar (NEW, 2026-08-19) — Sculk Sensor (⚠ Spectral Arrow is Steady Hands'). Every `sonarIntervalTicks`
      (**140s**) it spends a **2.5s CHARGE** (`sonarBuildupTicks` 50) — a radar-green ring gathering in + a rising
      hum — then a pulse (custom `blessing.sonar.ping`) reveals entities within `sonarRadius` **70**: GLOW **2.5s**
      (`sonarGlowTicks` 50) + your-eyes-only pointers/blips at each in a coherent **radar-GREEN** palette (down-to-
      earth, not rainbow, not blue). CROUCHed players caught only within 60% radius; each hit shaves 1s off the
      timer. **Next-pulse time shows in the Scrying Mirror** (`scryingDetail`). Debug-forcible. 7 config knobs.
- [x] Heavy Hitter (NEW, 2026-08-21) — Mace. Your melee knockback is doubled (`heavyHitterKnockbackMultiplier`
      2.0). Same mark-then-boost pattern as Backstabbing: the melee hit (`LivingIncomingDamageEvent`, direct
      entity = attacker) marks the victim for the tick, and `LivingKnockBackEvent` multiplies the FINAL strength
      — which already includes Knockback-enchant contribution — so it stacks MULTIPLICATIVELY with Knockback.
      Discovers on first hit. 1 config knob.
- [x] Speed Demon (NEW, 2026-08-21) — Carrot on a Stick (⚠ Lightning Rod requested but is Comic Relief's).
      ANY ridable mount is doubled: **living mounts** via a transient `MOVEMENT_SPEED` ×`speedDemonMountMultiplier`
      (2.0) modifier (ADD_MULTIPLIED_TOTAL), stripped on dismount/swap/end (tracks the boosted mount id per
      rider); **minecarts** (server-authoritative, delta clamped to a hardcoded max) via an EXTRA per-tick
      `move()` of (mult-1)× their travel, which the clamp doesn't touch; **boats** (client-authoritative — a
      server boost wouldn't stick) via `client/SpeedDemonClient` scaling the ridden boat's velocity each client
      tick, off the synced `SPEED_DEMON_ACTIVE` flag. A spark/cloud/firework **speed trail** flings off any
      boosted mount while it's moving (boat spray is client-side). Discovers on first boosted mount. 1 config knob.
- [x] Flight (NEW, 2026-08-23) — **Ghast Tear** (Oliver's pick; RESOLVED the knock-on collision by moving
      **Twist of Fate → End Crystal** (free). The earlier Wind Charge / Sugar collisions are also resolved:
      Flight left Wind Charge back to Windfall, Confusion took Rabbit Hide (free) so Sugar stays Blessing of
      Speed's — all sacrificial items are distinct again). **Refinements:** the energy bar sits well above the health/hunger row
      (was clipping); drain cut 60% (`flightDrainPerTick` 0.007→0.0028); a ~1s acceleration BUILDUP from a slow
      start to a slightly-lower max rise (`flightRiseSpeed` 0.42→0.34, `flightAccelTicks` 20); a subtle MAGICAL
      shimmer (END_ROD + ENCHANT) close in around you while rising (was a low CLOUD jet); **fall damage now
      applies** (cancel removed); hitting 0 energy DEPLETES you — grounded (bar greyed, `FLIGHT_ACTIVE` 2) until
      it refills to 20%; and the bar auto-HIDES after 2s at full charge unused (`FlightClient.barHidden`).
      **Tuning (2026-08-24):** shimmer raised +1.5 blocks (to your upper body); a `flightRegenDelayTicks` (8 = 0.4s)
      pause after flight ends before the bar refills; drain +15% (`flightDrainPerTick` 0.0028→0.0032); and
      **sprint + a movement key GLIDES** — `flightSprintRiseMultiplier` (0.4) cuts the rise and you shoot forward
      along your look at `flightSprintHorizontal` (0.62 b/t). ⚠ note the on-disk run configs were STALE
      (flightDrainPerTick/RiseSpeed/spelunking* kept old defaults) — patched, so the earlier tuning finally applies.
      **Drain model (2026-08-24):** the client reports a rise MODE (0 none / 1 push-up / 2 glide) via the
      `FlightRisePayload`; the server drains per mode — plain push-up = `flightDrainPerTick`, glide = ×
      `flightGlideDrainMultiplier` (1.5, "50% more than the push up"). Plus a constant `flightAirborneDrain`
      (0.0008) just for being off the ground even when not rising, so you can't loiter aloft — the bar refills
      ONLY once you land (past the regen delay).
      **Jump vs fly (2026-08-24):** a quick TAP of jump is now just a normal jump — flight only engages once jump
      is HELD past `flightHoldTicks` (4 = 0.2s), and the airborne drain only starts after `flightAirborneGraceTicks`
      (12 = 0.6s) off the ground, so a natural jump costs nothing and you can hop about + refill freely.
      Creative-style flight tied to a resource: hold JUMP to rise
      (`flightRiseSpeed`), draining a slim yellow energy bar drawn just above the XP bar (`client/FlightBarLayer`,
      lighter glow at the filled end). The rise is client-authoritative (`client/FlightClient` sets the y-velocity
      + resets fall distance, reporting the rising state to the server via a `FlightRisePayload` C2S only on
      change); the SERVER owns the synced `FLIGHT_ENERGY`/`FLIGHT_ACTIVE`/`FLIGHT_LOCKOUT_END`, draining while
      rising and refilling otherwise. Taking a hit spends `flightDamageCost` (20%) and knocks you out of flight for
      `flightLockoutTicks` (0.6s); fall damage is cancelled while active. 5 config knobs.
- [x] Thunder (NEW, 2026-08-23) — Trident. A static charge builds while you're NOT swinging, through 4 tiers
      (3/5.5/8/13s); your next MELEE hit discharges it, burning the target and arcing chain-lightning (30% of the
      hit damage per arc, capped flat at 12). T1 burn+1 chain · T2 burn+2 chains (each burns) · T3 2 chains that
      each arc once more to a fresh foe · T4 a real (visual-only) lightning bolt + 6 bonus damage + 2 chains that
      each arc to 2 more. Chains use `spawnChains` (start count + per-chain children + depth by tier), each jump
      to the nearest un-hit `LivingEntity` within `thunderChainRange`. The discharge hook is `LivingDamageEvent.Post`
      in `BlessingEventHandler` (direct entity == the player = melee); re-entrancy is safe because `discharge`
      resets the charge to 0 BEFORE the chain hurts fire (so the re-triggered discharge sees tier 0 and returns).
      A synced `THUNDER_TIER` drives the aura; a `TRIDENT_THUNDER` cue + flash fires on reaching T4.
      **Refinements (2026-08-23):** the ambient per-tick crackle was removed (it was obtrusive) — now just a
      SINGLE glint (`GLOW`+spark) + the ready sound at max charge. Chains are cooler + scale with charge: a
      jagged jittering arc (`beam` forks more at higher tiers, adds WAX_OFF/END_ROD) and an `impact` burst at
      each struck entity (sparks + WAX_ON, END_ROD at T3, a FLASH at T4). Chain damage is now a flat base
      (`thunderChainBase` 2) PLUS the 30%, then capped at 12. 10 config knobs. Debug: force a tier (`… thunder @s 4`).
- [x] Spelunking (NEW, 2026-08-23) — Torch. A miner's sixth sense: every `spelunkingIntervalTicks` it scans the
      `spelunkingRadius` box for ore-tag blocks and, for YOUR EYES ONLY (per-player `ClientboundLevelParticlesPacket`,
      the Sonar trick), marks each with a colour-coded dust mote that shows through stone (cyan diamond / green
      emerald / gold / red redstone / blue lapis / orange copper / tan iron / grey coal / amber other). Reworked
      to a SMALL constant radius (`spelunkingRadius` 14→7, `spelunkingIntervalTicks` 60→40) so it's a steady
      close glow with BIGGER, denser motes (dust scale 1.2→1.7, a cluster + a short rising wisp per ore) so
      they're actually noticeable. `markOresAround` is shared but Sonar no longer calls it (Oliver's call — ores
      removed from the Sonar sweep). Each ore also gets FULL-BRIGHT GLOW + END_ROD motes (unaffected by block
      light) so they stay visible in a pitch-black cave where the tinted dust would be too dark. 2 config knobs.
- [x] Safety (NEW, 2026-08-23) — Respawn Anchor. Crouch + stand still + look DOWN to channel a 10s
      (`safetyChannelTicks`) return: an action-bar countdown, a rising END_ROD sparkle column + beacon hum, then a
      FLASH and you're teleported to your spawn point. **NOT consumed** (Oliver's call) — you keep the blessing but
      it goes on a `safetyUseCooldownTicks` (7200 = 6min) cooldown after a successful trip, with a gold flourish
      + chime + "ready" cue the moment it comes back. Moving
      or taking a hit cancels it and imposes a `safetyCooldownTicks` (6s) cooldown. Gold-toned to match blessings.
      2 config knobs. Debug forces the teleport. **Refinements (2026-08-23):** looking down is now only the
      ACTIVATION requirement — once channelling you can look anywhere (continue needs crouch + still); a little
      movement LEEWAY (~0.14 blocks/tick tolerance + 5 grace ticks); purple particles replaced with a gold dust
      column + tightening ring; and a much bigger gold/END_ROD/totem burst + chime at BOTH the departure and
      arrival points.
- [x] Disguise (NEW, 2026-08-23) — Armor Stand. You're costumed as a cow/sheep/pig (consistent per-UUID). Hostiles
      stay passive — vetoed at the source via a `LivingChangeTargetEvent` gate on any `Enemy` targeting a currently-
      disguised player, plus a per-tick aggro clear. Getting HIT (`BlessingEventHandler`) or getting within
      `disguiseBreakRadius` of a hostile BREAKS the costume (a POOF + flip `DISGUISE_TYPE` to -1 → your real model
      + nametag show); you must go un-hit for `disguiseReturnTicks` (12s) for it to return. The mob render + hidden
      nametag are client-side (`client/DisguiseClient`: cancels `RenderPlayerEvent.Pre`, renders a cached dummy
      Cow/Sheep/Pig positioned + rotated to the player, its legs animated from the player's per-tick movement). 2
      config knobs. ⏳ the dummy uses the WIDE model + a simple walk-anim copy — polish (slim skins, arm-swing) later.
      **Refinements (2026-08-23):** the costume also SOUNDS right (occasional cow/sheep/pig ambient noises), and
      DEALING damage breaks it too (an `AttackEntityEvent` hook), not just taking a hit.
- [x] Confusion (NEW, 2026-08-23) — Rabbit Hide (free item; was briefly Sugar but that collided with Blessing
      of Speed, so it moved to Rabbit Hide). **Refinements (2026-08-23):** nearby hostiles now AUTO-aggro onto the clones (idle or player-
      targeting ones get pointed at a clone); clones POP INSTANTLY into dust on any hit with NO damage event
      (`hurt` discards + returns false — no combat/knockback/hitmarker) and a subtle "pff" (WOOL_BREAK, not the
      teleport sound); and a stream of dust runs FROM you TO each new clone so it looks like it peels off you
      rather than appearing from thin air. You throw off exact clones of yourself — a real `CloneEntity`
      (`entities/CloneEntity`, a 1-HP `PathfinderMob` rendered as a PlayerModel wearing the OWNER'S resolved skin +
      the owner's nametag via `client/CloneRenderer`) that wanders, swings at nearby monsters and looks around
      ("fake actions"), so onlookers and mobs can't tell which is you. One hit pops it in a flash of dust
      (`die`/lifetime → POOF). `BlessingConfusion` spawns up to `confusionMaxClones` on `confusionIntervalTicks`,
      but only RARELY when there's no audience (other players / hostile / angry-neutral mobs) to fool; it also tugs
      some nearby hostiles onto the clones each ~1s. 3 config knobs.
- [x] Photosynthesis (NEW, 2026-08-23) — Sunflower. In direct daylight (sky access, day, not raining) you slowly
      regenerate + gain a little hunger; standing in WATER while sunlit makes it stronger (Regen II + more hunger).
      The sunny opposite of Basement Dweller. 1 config knob.

**NEUTRALS & GLOBALS — ❌ CUT ENTIRELY (2026-08-20).** The whole event system was removed: the `events/`
package (all neutral + global classes), `BewitchmentEvent`/`EventCategory`/`GlobalCharge`, the Event registry,
the Afflicted status subsystem (`AfflictionManager`/`ActiveAfflictions`/the `afflicted` mob effect + its
`StatusEffectSync` wrapper), the `/bewitch event` and `/bewitch forcestop` commands + their lang keys, the
`globalsEnabled`/`globalBaseChancePercent`/`globalRechargeHours` config, the ritual's neutral-failure fallback
(now a plain fizzle), and the Compendium's "Rumours: Events" section. The Mirror BACKFIRE (a random curse onto
the caster) survives — it was always inline in the ritual, never part of the events package. Also deleted the
retired `CurseMansplainer` (§5, was CUT).

**ITEMS (Section 4)** — start only after every attachment above is checked off.
- [ ] Cursed Essence
- [x] Player Essence (2026-08-21) — FINALISED (prereq for the ritual). Own class `PlayerEssenceItem extends
      BoundPlayerItem`, `stacksTo(1)`, an enchant GLINT while bound (`isFoil`). Tooltip names the target by
      username (`item.witchmod.player_essence.target`) even offline + a client-only ONLINE/OFFLINE line (reads the
      tab list via `EssenceTooltipClient`); an UNBOUND essence shows the editable funny `no_target` lang line and
      uses the plain `player_essence` texture. **Per-player COLOUR** tied to UUID forever: a client item-model
      property `witchmod:essence_colour` returns `floorMod(uuid.hashCode(),16)/16`, and `models/item/player_essence.json`
      overrides select one of 16 recolour textures in `textures/item/player_essence/` (index 0 = the default). Three
      acquisition gestures with the empty JAR (`WitchModItems.JAR`, not Glass Bottle), each streaming SOUL/ENCHANT
      particles TOWARD the user + a unique sound (`PlayerEssenceEventHandler`): (a) crouch + look down (pitch ≥45°)
      + use → your OWN essence; (b) right-click a PLAYER → theirs — BUT context-sensitive: if the target is
      actively cursed/blessed (or the jar holds a capture) the Jar does its normal CAPTURE/release instead, only a
      "clean" target yields essence (`ItemJar.capture` was widened to take curses OR blessings); (c) right-click a
      BED → whoever's spawn is set there, RANDOM if several, and OFFLINE-aware via the new `BedSpawnRegistry`
      SavedData (fed by `PlayerSetSpawnEvent`) + an online fallback. The ritual already reads `BOUND_PLAYER` from
      the essence slot, so this is end-to-end.
- [x] Compendium (2026-08-24, in-game visual test pending) — REBUILT from a Written-Book dump into its own
      custom `client/CompendiumScreen`: right-click the item to open a two-page book with a **Chapters** sidebar
      (Curses / Blessings) — clicking a chapter jumps to its start. **One attachment per page**, each showing its
      name, a Curse/Blessing label, a big framed **sacrificial item** icon (2×, with a hover tooltip), a **Power**
      pip row, and the description. All of it is read LIVE from the effect registry — `Effect.sacrificialItem()`
      (real ItemStack) and a new `Effect.powerLevel()` (placeholder 3/5 for every effect until the strength pass;
      the UI reads it live so nothing needs re-touching when real values land). Entries are the whole
      `EFFECT_REGISTRY` split by category and sorted by name. Descriptions are per-id lang keys
      `witchmod.compendium.<id>.desc` (129 placeholder entries added, all editable); the item shows a
      `item.witchmod.compendium.desc1..2` tooltip. The OLD book (tutorial / items&blocks / rumours / events /
      backfires / modifiers sections + the `WrittenBookContent` builder) was PURGED — only Curses/Blessings now.
      **Rumours + power + polish (2026-08-24):** `DISCOVERED_EFFECTS` is now `.sync`ed so the client UI knows
      discovered vs undiscovered. Undiscovered entries are RUMOURS — the item is hidden behind `rumour.png`
      (`textures/gui/rumour.png`), the page is aged (darker parchment, muted grey accent, "Rumoured" label,
      "Cast with: ???"), and a custom hint `witchmod.compendium.<id>.rumour` (129 editable placeholders) shows
      instead of the description. Each chapter sorts **discovered first, then rumours**, sub-sorted by name.
      **Power is 0..100** now (`Effect.powerLevel()` placeholder 50): the 5 pips fill in fifths and the exact
      number shows in brackets under them. The screen-wide DARKENING was fixed properly — `renderBackground` is a
      no-op (no vanilla blur) and the dim is drawn as four strips only AROUND the panel, so nothing ever covers
      the book (the earlier full-screen alpha-fill landed in the translucent pass and painted over it).
      ⏳ NEXT: Oliver's in-game visual pass + writing the real descriptions/rumour hints into the lang file.
      **Items + Blocks chapters (2026-08-24):** two more sidebar chapters — **Items** (white accent) and **Blocks**
      (grey) — built by iterating `BuiltInRegistries.ITEM` for the `witchmod` namespace and splitting on `BlockItem`.
      Each entry shows the name, the item IMAGE (2× icon), **durability** where an effect's power sits (if the item
      is damageable), and its **crafting recipe** — pulled LIVE from the client `RecipeManager`
      (`getAllRecipesFor(CRAFTING)`, matched by result item) and drawn as a 3×3 grid → arrow → result, with hover
      tooltips on every ingredient + the result (`gridOf` reads `ShapedRecipe` width/height, else fills sequentially).
      Recipes aren't final, so **placeholder shaped recipes** were added for the ones lacking them (amethyst_bell,
      warding_totem, blessed_coin, executioners_coin, cursed/blessed/mixed_jar, player_essence) — the UI grabs
      whatever recipe exists, so real ones drop in with no code change; an item with no crafting recipe shows
      "Recipe: not yet known".
      **Rituals chapter + discovery command (2026-08-27):** a 6th chapter **Rituals** (red accent `0xFFCC5A55`) —
      NOT an entry list but 9 written how-to pages (`overview / table / sacrifice / essence / targeting / modifiers /
      outcome / counterplay / discovery`), each an intro-style page (title + scrollable paragraphs) reusing
      `drawIntro`; built directly in `init()` with NO auto-prepended intro. Text is REAL teaching content (not
      placeholder) in `witchmod.compendium.ritual.<topic>.title/.text` (own "Compendium - Rituals" lang group,
      paragraph breaks via `\n\n`), editable. NEW command **`/bewitch discovery {add|remove} {effect <id>|modifier
      <id>|all} [targets]`** (`discoveryNode` in `BewitchCommand`) toggles discoveries for effects AND modifiers,
      or all at once, defaulting to the caster — backed by new `DiscoveryManager.setEffectDiscovered` /
      `setModifierDiscovered` (silent add/remove); modifier arg tab-completes from `Modifier.id()`.
      **Chapter intro pages (2026-08-27):** every chapter now opens with an INTRODUCTORY page (a special
      `Entry.intro`) — a centred title, a decorative rule, then scrollable multi-paragraph text explaining what
      the chapter is. Prepended to each chapter list in `init()` (`introEntry`); `drawSide` branches to `drawIntro`.
      Text comes from `witchmod.compendium.intro.<chapter>.title` + `.text` (10 placeholder keys, own "Compendium —
      Intros" lang group); the `.text` value supports PARAGRAPHS via blank lines (`\n\n`), which `Font.split` spaces
      out, and it scrolls via the existing `drawScrollingText`. Placeholder copy for now — Oliver writes the real text.
      **Colours + descriptions + 2-recipe framework (2026-08-24):** Items are AQUA (§3, `0xFF00AAAA`) and Blocks
      BLUE (§9, `0xFF5555FF`) accents. Each item/block page now also shows an editable **description**
      (`witchmod.compendium.<itemid>.desc`, 21 placeholders added) — which doubles as the acquisition hint for the
      NON-craftable ones. The recipe display is generalised: an entry holds up to **two** recipes (framework for
      cursed_essence's block-craft + amethyst-smelt), pulled from BOTH `RecipeType.CRAFTING` and `RecipeType.SMELTING`;
      a `‹ 1/2 ›` toggle flips between them, crafting renders as a 3×3 grid → result and smelting as input → "smelt"
      → result. **Cursed/Blessed/Mixed Jar + Player Essence are deliberately NOT craftable** (their placeholder recipe
      JSONs were deleted) — their page shows "— not craftable —" and the description explains how to obtain them.
      **Scrolling + full-doc framework (2026-08-26):** each page's DESCRIPTION now scrolls (per-page-slot offset,
      mouse-wheel over the page, a slim scrollbar when it overflows), while the power/durability line stays fixed at
      the top and the recipe (or "— not craftable —" note) is PINNED to the bottom — so a long documentation blurb
      reads all the way through AND the crafting recipe is always visible (in-game docs matter; downloaders rarely
      read anything outside the game). Text is clipped via `enableScissor`; scroll resets on page/chapter change.
      **Lang file reorganised by category (2026-08-26):** `en_us.json` is now grouped — creative tab / blocks /
      fluids / items / entities / status effects / death messages / commands / keybinds / config / scry / narcolepsy /
      safety / subtitles / then Compendium UI, Curses, Blessings, Items&Blocks — each group separated by a blank line
      (valid JSON, no comment keys), alphabetical within a group so each id's `.desc`+`.rumour` sit together. A script
      verified every value is byte-for-byte the SAME (only order changed).
      **Modifiers chapter + discovery (2026-08-26):** a 5th chapter **Modifiers** (green accent `0xFF66C070`). Each
      modifier ALWAYS shows its item name + icon (unlike effect rumours which hide the icon behind `rumour.png`) — only
      the DESCRIPTION is a rumour until discovered. Discovery is its OWN track: a new synced+copyOnDeath
      `DISCOVERED_MODIFIERS` attachment (Set<ResourceLocation>, ids `witchmod:<modifier.id()>`), marked by
      `DiscoveryManager.markModifierDiscovered` the moment a ritual is cast USING that modifier (hooked in
      `BewitchingTableRitual.cast` right after `clearAll`, so success OR failure counts). `Modifier` gained `id()` +
      `ModifierItems.itemFor(m)` (forward map) for the UI. Lang: `witchmod.compendium.mod.<id>.desc` + `.rumour`
      placeholders for all 16 modifiers (own "Compendium — Modifiers" lang group). **NEW Paper modifier** added
      (Items.PAPER; doubles as Yap's sacrificial item — different slot, no collision): `scribblesLedger()` flag →
      every Ledger entry from a Paper cast is logged `scribbled=true` (new `LedgerLog.Entry` field + `log(...)`
      overload) and `LedgerBlock.formatEntry` renders those as an OBFUSCATED unreadable scrawl + "(scribbled out)".
      ⏳ Most modifier BEHAVIOURS beyond the numeric cost/duration/success/backfire deltas are still just enum flags
      (splashToNearby / bypassesWardAndJar / hidesStartTell / revealsEffectToTarget / delaysTell / loudTriggerTell /
      reappliesShortenedEffectOnCure) not yet consumed at cast time — a follow-up behaviour pass.
      **Modifier + coin + infectious batch (2026-08-27):**
      • **Clock** reworked to a FLAT random +5–15 min of total time (`flatDurationBonusTicks(rng)` on `Modifier`,
        added in `ModifierCalculator.applyDuration` which now takes a `RandomSource`); its old +25% duration delta is
        gone (cost stays +15%). Compass still overrides duration entirely; Clock/Bell add flat on top otherwise.
      • **Bell** (NEW modifier, Items.BELL — cross-slot with Popularity's sacrificial Bell, like Paper): flat +15 min
        (`flatDurationBonusTicks`) AND `announcesCast()` → on a successful cast it broadcasts "🔔 X cursed/blessed Y
        with Z!" to the whole server (`BewitchingTableRitual.announceCast`, also fires per-effect on coin casts).
      • **Coins are now sacrificial items** that gamble 1–3 effects (`data/CoinGamble`, shared by the coin item-use
        AND the Table). Blessed = 1–3 blessings biased fewer+lower-power; Cursed = 1–3 curses biased fewer+lower, with
        a 5% "bad day" → 3× 80+-power curses; Executioner = 1–3 curses/blessings, uniform. `ItemGambleCoin` now takes
        a `CoinGamble.Type` (all three coins gamble on use; EXECUTIONERS_COIN kept its revive-on-death handler too).
        The ritual has a self-contained `castCoin` path (own target resolution, fixed `CoinGamble.BASE_COST` 55,
        fizzles with no backfire on failure); `RitualSlot`/`BewitchingTableScreen` accept coins so the slot isn't red
        and the success bar shows. Power bias reads `powerLevel()` (placeholder 50 → currently uniform; "bad day" has
        no 80+ curses yet so it falls back to 3 random curses).
      • **Slime Ball / Slime Block are MODIFIERS** (Oliver's clarification — "infectious is a modifier, not a curse"),
        so no clash with Bouncy's sacrificial Slime Ball. They apply a HIDDEN internal attachment on success
        (`Modifier.infectiousLevel()` 1/2 → `applyInfectious` in the ritual): **Infectious** (`CurseInfectious`, ~1h)
        makes the target's attachments a HOT POTATO — hitting a player moves ALL their attachments (this state too)
        onto the victim with timers preserved, leaving the attacker clean; **Very Infectious** (`CurseVeryInfectious`)
        instead COPIES the other attachments onto whoever you hit for 10s while you keep yours. Both are internal
        (`Effect.selectable()` = false → excluded from sacrificial matching, coin rolls, and the Compendium curses
        chapter) and hidden (discoversOnTrigger, never marked). Spread lives in `CurseEventHandler.onInfectiousAttack`
        (uses new `EffectManager.activeSnapshot` / `holderOf` / `applyExact` — an exact-duration, guard-bypassing,
        no-multiplier placement for physical transfers). Slime Ball cost +60%, Slime Block +100%.
      • Lang: `witchmod.compendium.mod.{bell,slime_ball,slime_block}.desc/.rumour` placeholders added (19 modifiers now).
      Note Clock's flat bonus + Bell now use the `applyDuration(base, mod, rng)` signature (one caller: the ritual).
      **Remaining-modifier behaviour pass + 2 new (2026-08-27):** every previously-inert modifier flag is now consumed,
      plus two new modifiers. Shared foundation: `ActiveEffectInstance` gained a per-instance `display` (0 normal /
      1 hidden / 2 disguised) honoured by `StatusEffectSync` (via an `effectiveCategory` helper — hidden shows NO
      wrapper, disguised shows the OPPOSITE), cleared by `EffectManager.revealDisplay` which `DiscoveryManager`
      calls on first discovery; and `EffectManager.apply` gained an `ApplyOptions(bypassWard, display)` overload
      (the 4-arg delegates). Modifiers wired in `BewitchingTableRitual`:
      • **Amethyst Shard** (NEW, Items.AMETHYST_SHARD) — if the target already carries a SAME-category effect, cost
        ×0.70 (`EffectManager.hasActiveOfCategory`). • **Wither Rose** (NEW, Items.WITHER_ROSE, cost +15%) — the
        effect reads as the OPPOSITE category (fake blessing/curse) until discovered, via `display=DISGUISED` +
        suppressed immediate victim-discovery. • **Ink Sac** — `display=HIDDEN`: no wrapper/tell until the effect's
        real discovery moment reveals it. • **Netherite Ingot** — `NETHERITE_BYPASS_CHANCE` (80%) to bypass the Ward
        (Totem still applies) via `ApplyOptions.bypassWard`. • **Echo Shard** — onset delayed 5–10 min: the ritual
        schedules the cast in the new `DelayedCasts` (server-tick queue, resolves players by UUID at fire time)
        instead of applying now. • **Goat Horn** — a didgeridoo-horn blares at the target on land. • **Glow Ink Sac**
        — the target is told in chat exactly what they got (+ marks it discovered for them). • **Dragon's Breath** —
        a quarter-duration splash onto OTHER players within `DRAGONS_BREATH_RADIUS` (6) of the CASTER, with dragon-
        breath particles. • **Quartz** — refuses the cast unless the caster has already discovered that spell.
        • **Honeycomb** — success forced to 100% unless the effect is MAJOR-tier (its `forcesBackfireZero` was
        already live). • **Recovery Compass** — the applied effect is added to a new NON-copyOnDeath
        `NON_PERSISTENT_EFFECTS` set, stripped in the `CurseEventHandler` death hook before the respawn copy (the
        Rule-4 exception). One-modifier-per-cast keeps these mutually exclusive, so no combination logic. The
        Redstone-random + backfire-random pools now filter `selectable()` so they can't roll the hidden Infectious
        states. Lang placeholders added for amethyst_shard + wither_rose (21 modifiers now).
- [ ] Voodoo Doll — ⏳ FIRST PASS (2026-08-24, in-game test pending). Rebuilt from the prototype stand-in (which
      right-clicked a RANDOM curse onto the bound player) into the spec's PASSIVE redirect: while a bound doll is
      in your inventory, any CURSE you cast **at the Bewitching Table** is forwarded onto the doll's bound player
      instead of your intended target (redirect, not copy — Oliver's calls). Hooked in `BewitchingTableRitual.cast`
      right after the target is resolved; a successful forward spends 1 doll durability (breaks at 0) and
      Ledger-logs `doll_forward`. Offline/dead bound target → no forward + a note, curse proceeds normally. Binding
      unchanged (Player Essence in the OFF-hand + use the doll; consumes the essence); using a bound doll with no
      essence just reports its target (`ItemVoodooDoll.findBoundDoll`). **Command casts are NOT forwarded**
      (redirect is Table-only) but `/bewitch apply` now tells a caster holding a bound doll that a natural cast
      would have redirected it (Oliver's call). ⏳ NEXT: Oliver tests + tunes (durability count, feedback wording).
      **Sympathetic-magic interactions (2026-08-24, in-game test pending):** (1) **Needle stab** — right-clicking
      the Needle with a bound doll in inventory jabs the target with a new custom `witchmod:voodoo` damage type
      (in `bypasses_armor`) that SCALES WITH their armour (`voodooNeedleBaseDamage` + armourPoints ×
      `voodooNeedleArmorScale` — their armour makes it worse), with a satisfying CRIT+ANVIL smack + CRIT/
      DAMAGE_INDICATOR/WITCH FX on the victim; consumes the Needle + `voodooNeedleDollCost` (2) doll durability.
      (2) **Hazards** (`VoodooDollHazards`, a periodic item-entity scan like the jars): a bound doll dropped in
      FIRE/LAVA burns its owner (`voodooLavaFireTicks`) and is destroyed; sat in POWDER SNOW rapidly freezes the
      owner (non-destructive). ⏳ MORE interactions planned (see Oliver's idea list).
      **Interaction batch + half-armour + config-durability (2026-08-24, test pending):** voodoo damage is now
      only HALF-reduced by armour — `ItemVoodooDoll.voodooHurt` applies `voodooUnprotectedFraction` (0.5) of the
      hit ignoring armour and the rest through the vanilla armour formula (armour helps, but half as much). Doll
      max durability is now **configurable + low** (`voodooDollDurability` 12, applied via the MAX_DAMAGE component
      on bind). Tooltip shows the bound username + a client-side **online/offline** line (voodoo needs them
      online). New interactions: **Throw** (drop a bound doll → the victim is flung the same way, big durability
      cost — `ItemTossEvent`); **Squeeze** (hold right-click → ramping voodoo tick-damage + slow with rising
      clicks, per-tick durability, use-cooldown); **Feeding** (food in the OFF-hand + use the doll → the victim
      gains that food's hunger/saturation, effects STRIPPED so rotten flesh is "edible", food consumed);
      **Lightning** (a bound doll struck by lightning calls a bolt on the victim + destroys the doll —
      `EntityStruckByLightningEvent`); **Water/rain/cauldron** (victim goes visibly wet + their fire is put out,
      no cost — in `VoodooDollHazards`). Debug: `/bewitch voodoo make player|uuid <p>` gives a bound doll;
      `/bewitch voodoo force <stab|throw|squeeze|feed|ignite|freeze|wet|lightning> <player>` forces one on a
      target.
      **Polish + remaining interactions (2026-08-24):** voodoo damage now does NO knockback (added to the
      `no_knockback` tag) and only HALF-armour (as above). **All chat text removed** — every interaction is
      conveyed by particles + sounds instead (`voodooHurt` bundles the smack FX; bind = chime+witch spark; offline
      = a soft fizzle). **Pin + squeeze jolt the CASTER's camera** (`VOODOO_SHAKE_END` synced end-tick wired into
      `applyShake`) and **squeezing slows the caster** (`voodooSqueezeSelfSlow`). NEW ways in: **inventory pin** —
      pick up the Needle in the GUI and right-click it onto the doll (`overrideOtherStackedOnMe`), same jab as
      in-hand; **Shake** — whipping your camera while holding a bound doll (client `VoodooClient` → `VoodooShakePayload`)
      gives the victim a brief Nausea wobble; **Potion-cloud** — a lingering potion over the grounded doll lands its
      effects on the victim (reads `AreaEffectCloud.potionContents` reflectively — no public getter); **Arrow-stick**
      — an arrow shot into the grounded doll hurts the victim and visibly sticks out of them (`setArrowCount`);
      **crafting-menu binding** — a custom `VoodooBindRecipe` (doll + a bound Player Essence anywhere in the grid →
      a bound doll, essence consumed); **doll-as-Player-Essence in the Table** — a bound doll works in the target
      slot like an essence, RETURNED with a small `voodooTableDollCost` durability hit instead of being consumed
      (also allowed by `RitualSlot.isCorrect`). Durability is configurable + low (`voodooDollDurability` 12, set via
      the MAX_DAMAGE component on bind).
      **Fishing rod + polish (2026-08-24):** ALL interactions are now `/bewitch voodoo force`-able (added shake,
      potion, arrow, fishing). The choke/squeeze damage rate is slower (`voodooSqueezeTickInterval` 8→14). NEW
      **Fishing Rod** interaction (`onFishingRod`, `PlayerInteractEvent.RightClickItem`): use a rod while aiming at
      a bound doll lying in front of you and the victim is YANKED your way, HARD — `voodooFishingForce` (3.6, much
      more than a throw) for a HUGE `voodooFishingDollCost` (8) durability hit (shared `ItemVoodooDoll.fling`).
      Damage FX trimmed to just CRIT + purple WITCH (dropped the DAMAGE_INDICATOR spray as excessive).
- [x] Needle — right-click with a bound Voodoo Doll in inventory → the armour-scaling `witchmod:voodoo` jab (see
      the Voodoo Doll entry). Consumes the needle + doll durability, smack sound + FX on the victim.
- [x] Ward (2026-08-24, in-game test pending) — REDEFINED from "deflect the curse back to the caster" to a
      durability BLOCKER: it stops ANY attachment (curse OR blessing) cast on you by someone else — your own
      casts pass through. In `EffectManager.apply` the ward hook now blocks for any category (returns false, no
      redirect); the hook signature became `WardBlock(target, caster, isCurse)`. On a block, `ItemWard.onBlock`
      fires a moving coloured LASH via `WardEffects` (a `ServerTickEvent` lash list like the jars): it streaks IN
      from the caster's direction toward you — PURPLE dust+witch for a curse, warm GOLD dust+end-rod for a
      blessing — then flares and is blocked with an ENCHANTED_HIT ring + FLASH + a subtle `SHIELD_BLOCK` clang,
      spending 1 of its 8 durability (durability 16→8; gated on `wardDurabilityDecays`). No chat text — feedback
      is purely the lash + block FX. Found in main inventory or off-hand. Editable description tooltip via
      `item.witchmod.ward.desc1..3` in the lang file.
- [x] Scrying Mirror (2026-08-24, in-game test pending) — overhauled from a chat dump into a styled on-screen
      PANEL (`client/ScryingOverlay`, a GUI layer): right-click to reveal YOUR active curses/blessings, or
      right-click a PLAYER (`interactLivingEntity`) to reveal THEIRS. Each row is a coloured tag (purple curse /
      gold blessing) + name + seconds-left + the effect's `scryingDetail` specifics; fades after ~8.5s. The
      server gathers the list and pushes a `ScryPayload` (ScryEntry list) to the viewer; using the mirror also
      **instantly discovers** whatever it reveals (`markDiscoveredByVictim` on the subject). Added `scryingDetail`
      to Stick Drift (which stick + drift direction) and Siren's Call (longing %) alongside the existing Allergic
      (diet) + Sonar (next ping). Editable description tooltip via `item.witchmod.scrying_mirror.desc1..3`.
      `DiscoveryManager.titleCase` made public for the entry names.
      **Scry-detail batch (2026-08-24):** added `scryingDetail` to 21 more effects — Audit (complete/pending),
      Backseat Driver (takeover cooldown), Cutaway Gag (can-trigger/cooldown), Narcolepsy (asleep/cooldown),
      Pacing (moment/cooldown), Screensaver (generic — episodes are client-timed), Solicitor (here/lying-low),
      Berserker (% faster swings), Bodyguard (on-duty + marked target / replacement incoming), Disguise (which
      mob + active/broken), Immortality (reviving Xs / N revives left), Payday (paid/en-route), Pickpocket (mark
      in reach?), Prop Hunt (which block), Safety (ready/cooldown), Sixth Sense (ready/recharging), Thunder
      (charge tier /4), Twist of Fate (dodge cooldown), Windfall (imminent/cooldown). **Editable FLAVOUR** for
      Siren's Call (yearning band → `witchmod.scry.siren.*`) and Haunted (dread tier → `witchmod.scry.dweller.tier0..3`)
      via a new "@key" convention in `ScryingOverlay` (a detail starting `@` is rendered as a translatable lang key).
      ALSO: **Explosive → Martyrdom** and **Super Explosive → Volatile** (registry ids `explosive`→`martyrdom`,
      `super_explosive`→`volatile`, so the display names change; old saved instances drop as unknown, harmless).
      And **Drive** now clears adult VILLAGERS' post-breed lockout too (village baby-boom).
- [ ] Effigy
- [ ] Cursed Coin
- [ ] Blessed Coin
- [ ] Executioner's Coin
- [ ] Jar
- [ ] Cursed Jar
- [x] Amethyst Bell — FULLY IMPLEMENTED animated bell (2026-08-28, in-game test pending). Now a real
      `AmethystBellBlockEntity` + `client/AmethystBellRenderer` that SWINGS the bell exactly like vanilla's
      `BellRenderer` (shared `ModelLayers.BELL` part, the same decaying-sine swing off the BE's shake state,
      synced to clients via a `blockEvent` — `triggerEvent`, id 1). Floor/stand ONLY (a `Block implements
      EntityBlock`, never wall/ceiling) in TWO orientations (a `HORIZONTAL_AXIS` property; blockstate axis=x → model,
      axis=z → y:90). Right-click RINGS: swing + a dramatic toll (`amethyst_bell.ring`) + an amethyst spark ring/
      glints/glow burst, and (on cooldown-gated) still flips one active effect for another of the same category.
      The STAND `models/block/amethyst_bell.json` (5 cubes): two POLISHED DEEPSLATE uprights (pushed 1px out to
      x1-3 / x13-15) each with a POLISHED BLACKSTONE foot (bottom 3px, y0-3), and a top beam that overhangs the
      uprights by 3px each side (x-2 → x18); uses vanilla `polished_deepslate`/`polished_blackstone` directly.
      The BELL body is `textures/entity/amethyst_bell.png` (vanilla gold bell → amethyst via a grayscale→purple
      ramp, Oliver will edit). The ITEM is now a flat 2D icon (`models/item/amethyst_bell.json` = `item/generated`
      + `textures/item/amethyst_bell.png`, a copy of the vanilla bell item for Oliver to edit). Placement gives
      TWO orientations off `HORIZONTAL_AXIS` (N-S facing → axis=z, E-W → axis=x); the beam overhang makes the two
      obvious. ⏳ the bell body's vertical alignment on the beam may want a small nudge in-game.
      **Polish (2026-08-28):** top beam overhang pulled back to 1px each side (x0-16); the ring FX dropped the
      off-theme blue enchant/glow-squid particles for on-brand amethyst DUST + purple WITCH sparks + white glints;
      ringing now sets a synced `AMETHYST_BELL_SHAKE_END` on players within 10 blocks for a VERY subtle camera
      jolt (SHAKE_TICKS 6 / SHAKE_STRENGTH 0.4, via the shared `applyShake` in `ClientCurseHandler`); the bell's
      action-bar messages are colour-coded (cooldown YELLOW with seconds-left, nothing-to-flip GRAY, flip
      LIGHT_PURPLE). The Bewitching Table's action-bar errors are likewise now all RED with direct "what's wrong"
      wording (No Sacrificial Item / Invalid Sacrificial Item / Target offline …), and the jar-bottle success is AQUA.
      **New sounds wired (2026-08-28):** 5 supplied OGGs registered + in `sounds.json`/subtitles —
      `amethyst_bell.ring` (bell toll), `ledger.write` (LedgerFeedback now plays a subtle pencil-scratch into each
      ledger instead of the amethyst chime), and the ritual outcomes now REACT to their sound: `ritual.success` (a
      magic jingle, curse-tinted lower / blessing brighter, replacing the level-up), `ritual.backfire` (a firework
      BANG — new `Outcome.BACKFIRE` with FIREWORK+FLASH+coloured-dust burst, played before `doBackfire`), and
      `ritual.fizzle` (the quick fizzle for "nothing happens", from ritualfail.ogg; `Outcome.FAILURE`→`FIZZLE`,
      grey-smoke puff). Coin/refused failures use FIZZLE too.
- [x] Recovery Compass (modifier item) — removed from the creative tab (2026-08-23): it's a modifier backed by
      vanilla's own `minecraft:recovery_compass`, so listing it duplicated the vanilla item. Still fully usable
      as a modifier in the Table's modifier slot.

**BLOCKS (Section 3)** — start only after every item above is checked off.
- [ ] Bewitching Table — ⏳ IN PROGRESS (ritual pass 2, 2026-08-21):
      • **SLOT-SWAP BUG FIXED** — the menu added its ritual slots out of container-index order, so the client
        screen (which indexes `menu.slots` by the `SLOT_*` constants) read the sacrificial + essence slots
        swapped while the server read them correctly. `addSlot` calls are now in container-index order
        (0 target · 1 sacrificial · 2 essence · 3 modifier); slots repositioned into a ritual layout (focus in
        the middle, essence feeding up from below, target + modifier up top).
      • **Textured UI** — the whole panel is now an editable PNG
        `assets/witchmod/textures/gui/container/bewitching_table.png` (256×256; panel + inset slot wells +
        rune-lines joining the slots to the central focus + faint per-slot symbol watermarks). Generator kept
        at `scratchpad/GenTableTex.java` if it needs regenerating. Oliver edits the PNG to taste. The success
        bar, cast button and red slot highlights draw procedurally ON TOP.
      • **Success bar works** (green fill = live success chance, moving shimmer, thin red backfire cap, % text),
        **red slots** for wrong items (slots accept anything now — `mayPlace`→true, `RitualSlot.isCorrect`
        drives the red + shift-click routing + cast validity), **Cast disabled** (greyed, click swallowed) until
        every slot is correct and a Sacrificial Item is present — mirrored as a server-side refusal.
      • **Cursed Essence = currency**: the existing §10.1 formulas already reward investment (more essence →
        higher success, lower backfire, backfire→0 at full cost). Added **tier-aware backfire**
        (`ModifierCalculator.applyTierBackfire`): LOW-tier attachments never backfire (0%), HIGH-tier keep a
        small floor even at full essence, mid-tier use the curve. ⚠ tier is a PLACEHOLDER keyed off baseCost —
        see the "Attachment strength/tier TODO" note below.
      • **Only SUCCESS or FAILURE now** (the third "fizzle/nothing" outcome is gone): a failed roll always
        plays the distinct FAILURE fx (grey/soul smoke + `VILLAGER_NO`), and is **most likely just "nothing
        happens"** — a real penalty only fires on the (tier-scaled) backfire chance, which is 0 for low-tier.
        Success plays a clear positive fx (`PLAYER_LEVELUP` + upward enchant/totem particles), curse vs blessing
        tinted, so success/failure is unmistakable both ways.
      • **Six backfire PENALTIES** when one does fire (`BewitchingTableRitual.doBackfire`, weighted): table
        explodes (power scales with strength, block damage gated on mobGriefing via `ExplosionInteraction.MOB`,
        source null so the caster is caught) · the same attachment onto the caster · a name-tagged **Woolliam**
        sheep · a random low-biased curse onto the caster · inventory shuffle · a chunk of the new
        `witchmod:ritual_backfire` damage type (bypasses armour, scales with strength). Config knobs `backfire*`.
      • **Per-slot textures** (like the enchant table's lapis slot): the background PNG is now just the panel +
        rune-lines + ritual circle, and each slot outline is its OWN 18×18 sprite the screen blits per slot —
        `slot.png` (player inventory) + `slot_target/sacrifice/essence/modifier.png` (the ritual slots, each
        with its symbol). All under `assets/witchmod/textures/gui/container/`, freely editable. Generator kept
        at `scratchpad/GenTableTex2.java`.
      • **UI stretched** to 176×228 with the ritual cluster pushed down so the "Bewitching Table" title clears
        the ritual circle, and the bar/cast-button/inventory re-spaced so nothing collides.
      • **⚠ CAST TRIGGER REWORKED (2026-08-21)** — the Cast button no longer uses the vanilla container-button
        plumbing (`clickMenuButton` / `handleInventoryButtonClick`), which was silently failing in play (a cast
        did nothing, items not even consumed, only the ambient "ready" spark showing). It now sends a dedicated
        C2S `WitchModNetwork.RitualCastPayload(pos)` — the same reliable payload path Berserker/Splitscreen use
        — and the server handler runs `BewitchingTableRitual.cast` directly against the block entity at that
        pos (proximity-checked), then `broadcastChanges()` re-syncs the emptied slots. `clickMenuButton` is left
        in place but unused.
      • **⚠⚠ THE REAL ROOT-CAUSE BUG (fixed 2026-08-21):** `BewitchingTableBlockEntity.clearContent()` did
        `items.clear()` **then** looped `items.add(EMPTY)` — but `items` is a FIXED-SIZE `NonNullList.withSize`
        (Arrays.asList-backed), and `NonNullList.clear()` ALREADY resets every slot to the default EMPTY while
        keeping the size. The redundant `add()` threw `UnsupportedOperationException` on EVERY cast, at
        `table.clearAll()` — which runs BEFORE the roll/apply. So every ritual: emptied the slots via clear()
        (items "consumed"), then threw, aborting before anything was applied; the payload handler swallowed the
        exception silently. Commands never hit it (they don't call clearAll). Fix: `clearContent()` is just
        `items.clear()` (the same pattern `loadAdditional` already documents). This is why NO amount of trigger/
        apply reworking helped — the cast always threw at the same line regardless.
      • **Honest apply reporting** — `EffectManager.apply` now returns whether the effect actually LANDED, and
        the ritual only celebrates a real application. Previously a refusal (grace period / warding totem /
        category disabled) returned silently while the ritual still played success FX + consumed items — which
        read as "success but nothing applied". A refused cast now shows the fail FX + the real reason, and a
        success names the effect ("The ritual succeeds — Fortune!"). A server log line
        (`[ritual] … APPLIED/REFUSED`) records every cast for diagnosis. Failure particles are red (never green)
        so success vs failure can't be confused; the block's ambient "ready" motes are neutral purple too.
      • **Success VISUALS (`RitualFx`, ticked from a `ServerTickEvent`):** a rotating, rising ritual circle of
        coloured particles climbs around the block on success (purple witch motes for a curse, warm END_ROD +
        yellow/pale stars for a blessing — matching the Blessed Jar), with a final chime. When the victim is
        cast from AFAR (>4 blocks, same dimension), a directional **lash** streaks INTO them from the compass
        direction of the table, so they see the curse/blessing "enter" and get a subtle hint of its source.
      • **Onset particles are category-coloured** (`StatusEffectSync`): a curse's onset throws purple witch
        motes, a blessing's the warm yellow/white stars — no longer always purple.
      NEXT: Oliver edits the textures + in-game tuning; then improvements.
      **NEW CUSTOM BLOCK MODEL (2026-08-26):** the old cube+`bewitching_table.png` container-style look is
      SUPERSEDED. `models/block/bewitching_table.json` is now a hand-built shape (enchanting-table-inspired): a
      deepslate PILLAR base (10×10 column, y0–11) + a full-16×16 TOP SLAB overhang (y11–16). The TOP FACE is one
      carpet tile with FLAT purple corner squares (`bewitching_table_top`), and carpet FLAPS drape over the middle
      of each side (y12–16). **All UVs are 1:1** (1 texel = 1 model pixel) so it stays true 16×16 style — the first
      pass sampled whole tiles onto small faces and read as too-dense (Oliver's note). Driven by 5 hand-editable
      16×16 PNGs in `textures/block/` (`bewitching_table_pillar` / `_pillar_top` / `_slab_side` / `_top` /
      `_carpet`); layout notes in `bewitching_table_TEXTURES.txt`. Placeholder art
      generated for now — Oliver replaces it in Aseprite (F3+T reload, no rebuild). Old
      `bewitching_table_front/side/top.png` are unreferenced and deletable; the container-GUI `bewitching_table.png`
      (§13.1 screen background) is a SEPARATE asset and unaffected.
      **Drape + slot tweaks (2026-08-26):** the carpet drapes were widened to 6px and the model is now
      `render_type: minecraft:cutout` with the drape's bottom-outer corners painted transparent in
      `bewitching_table_carpet.png` (pixels 5/10 × 14/15) so the hem reads ROUNDED. In the Table SCREEN, the
      Sacrificial slot was nudged down 1px (menu slot y 34→35; the screen draws each slot frame from `slot.x/y`, so
      the frame + item move together).
- [ ] Block of Cursed Essence
- [x] Ledger — FULLY IMPLEMENTED (2026-08-27, in-game test pending). Right-click opens a custom `client/LedgerScreen`
      (scrollable, parchment-styled, newest-first) listing nearby ritual activity — caster → target, effect (+modifier
      in green), and result · "Xm ago"; Paper-scribbled entries render obfuscated. Server range-filters via a new
      configurable **`Config.LEDGER_RANGE`** (24, in blocks): `LedgerLog.Entry` gained a `GlobalPos pos` (the ritual
      SITE — the table) + `Optional<String> modifier`, and `LedgerLog.entriesNear(center, range)` does the filter; the
      block sends a `WitchModNetwork.LedgerPayload` (range + list) → `LedgerScreen.open`. **Particle feedback**
      (`LedgerFeedback`): every successful cast pulses ENCHANT glyphs flowing FROM the table INTO every Ledger within
      range (+ coloured dust trail + amethyst chime); ledger positions are tracked via a static registry filled on
      place / right-click, cleared on break. **Modifier detection**: the ritual passes the modifier name into every
      ledger entry (all 10 log calls threaded `eventPos, modifierName`; command casts pass a pos too). **Model**: still
      the vanilla LECTERN shape but now reskinned onto editable witchmod textures — `models/block/ledger.json` overrides
      the lectern's `base/bottom/front/sides/top` texture vars with `witchmod:block/ledger_*` (5 placeholder PNGs,
      dark-arcane-wood + purple, hand-editable; Oliver does the real reskin).
      **3D purple book (2026-08-28):** the Ledger now renders a 3D BOOK on top exactly like a lectern's — a real
      `LedgerBlockEntity` + `client/LedgerRenderer` that copies the vanilla `LecternRenderer` transform verbatim
      (`FACING.getClockWise().toYRot()` → `YP(-f)`, `ZP(67.5)`, `bookModel.setupAnim(0,0.1,0.9,1.2)`, shared
      `ModelLayers.BOOK`), textured with `entity/ledger_book.png` — the vanilla enchanting-table book recoloured to
      purple via a grayscale→purple ramp. To orient the book, the block is now DIRECTIONAL (a `FACING` property like
      the lectern, blockstate y-variants north/east/south/west, faces the placer); it's a `Block implements
      EntityBlock` so the block model still renders normally with the BER-drawn book on top.
- [x] Warding Totem — FULLY IMPLEMENTED (2026-08-28, in-game test pending). Remodelled slim: a blackstone
      base/collars + a slender column and a crowning AMETHYST GEM (a 45°-rotated cutout crystal), all on editable
      witchmod textures (`warding_totem_stone` / `warding_totem_gem`). **Range configurable** (`Config.WARDING_TOTEM_RANGE`,
      32). Functionality: a `@EventBusSubscriber` server tick (every 10t) applies the NEW subtle **Protected**
      MobEffect (`WitchModMobEffects.PROTECTED`, invisible swirl + icon, `textures/mob_effect/protected.png`) to
      every player within range of a placed totem; `WardingTotemBlock.isProtected` (position registry from
      place/break, range from config) is the authoritative gate in `EffectManager.apply` — ANY curse/blessing/
      voodoo on a protected player is blocked. Blocked attempts now FLARE a magic force-field dome
      (`EffectManager.setTotemHook` gained an `onBlock` consumer → `WardingTotemBlock.onBlocked`: a sphere of
      amethyst dust + END_ROD + a soft chime/shield sound); a faint field also shimmers occasionally while
      protected. Block textures + the effect icon are hand-editable placeholders.
      **Blocks-others + ambient + cinematic + dummy pass (2026-08-28, in-game test pending):**
      (1) **Blocks OTHERS' magic only** — the gate is now `caster != target` (self-casts pass through). The totem
      hook became a `WardBlock` `(target, caster, curse)` and `EffectManager` gained `wouldTotemBlock(target,
      caster)` so callers can tell a totem-block from an ordinary refusal. A null caster (jar / coin / `/bewitch
      dummy`) counts as external and is blocked. (2) **Jars work but don't lash a protected player** —
      `JarEffects.tickLashes` skips `WardingTotemBlock.isProtected` players when picking a lash target, so a lash
      fizzles rather than pointlessly bursting on a shielded player; a jar that catches a protected player still
      splashes but each stored effect is refused by the totem gate and Ledger-logged `blocked`. (3) **Full-height
      block model** — `warding_totem.json` is now a proper 16px block (blackstone base/body/collar + a 45°-rotated
      gem topping out at y16), so it keeps vanilla's blocky proportions instead of overhanging the block. (4)
      **Protected is now AMBIENT + invisible + no HUD icon** (`MobEffectInstance(..., true, false, false)`) — it
      only appears in the inventory effects list, never as a swirl or icon, and the per-tick particle shimmer on
      the PLAYER was removed (people idle near totems). The BLOCK now breathes the ambient purple particles itself
      via `animateTick` (gem, ~every 3rd tick); the player only flares on an actual block. (5) **Cinematic block**
      — `onBlocked(target, caster, curse)` fires the colour-coded incoming lash from the caster's direction
      (`WardEffects.startLash`, purple curse / gold blessing) caught by a bigger force-field dome + chime/shield
      clang; a null-caster block skips the directional lash (just the dome). The **Ledger records blocked vs not**
      everywhere: the Table ritual logs `blocked` (via `wouldTotemBlock`) vs `refused`, jars log `jar`/`blocked`,
      and the dummy command logs `success`/`blocked`/`refused`. (6) **NEW `/bewitch dummy <effect> [targets]
      [duration]`** — applies an effect with a null caster (so a totem blocks it, unlike a self-cast) and logs it
      in the Ledger as cast by **"dummy"** with the real success/blocked result. Compiles + boots clean.
- [ ] Purifying Water

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
letters. Loot: windfall.json, company_gifts.json. Trades: useless.json.

**Decisions owed (Section 11):** ~~Firework Star collision (Main Character vs Celebration).~~ RESOLVED —
Main Character moved to Fire Charge; Celebration keeps the Firework Star. (Nothing owed here now.)

---

### Phase A build log (Claude Code — post-prototype transition)

**Done:** all 22 previously NOT-PROTOTYPED attachments now have classes, are registered, and are
command-startable (`/bewitch apply witchmod:<id> <selector>`). Built to the same "loosely-functional,
vanilla-backed" bar as the rest of the prototype — NOT the full hyper-specific specs. Curse count is now
43+7 = **50** (matches the Section 5 header). Blessing count is 29+15 = **44**; the Section 6 header says
45 but only 44 are actually listed in Section 6, so 44 is almost certainly correct (same header-vs-list
miscount this doc already had for "Curses (41)" vs 43). Flagging for a reconcile — if a 45th blessing was
intended, it's missing from the Section 6 list and needs naming.

**New curse classes (7):** super_explosive, claustrophobia, moonwalker, sirens_call, loading_screen,
pacing, trumpet. **New blessing classes (15):** thick_skinned, farmers_spirit, brute, blacksmith, unseen,
silver_tongue, hot_stuff, bouncy, excavation, angler, laugh_track, chat, civilisation, low_gravity,
last_stand. (Registry ids use the spec's current names; note the older prototype ids kept for the renamed
ones — `pidgeon_toed`=Wonky (RENAMED to wonky), `pidgeon_toed`=Wonky,
`locked_in`=Hawk Guy, `tools_dont_use_durability`=Workman, `trainer`=Personal
Trainer — kept stable to avoid breaking saved data; display names are a lang concern.)

**Prototype stand-ins (real behavior deferred to later phases — flagged in each class's javadoc):**
- Moonwalker → Slowness (real: reversed forward input, client-side, Phase D).
- Loading Screen → brief Blindness on a timer (real: fullscreen fake-load overlay on door use — Phase D UI
  + custom sound).
- Pacing → brief hard Slowness "freeze" (real: camera-hijack cutaways — Phase D + custom sound).
- Trumpet → periodic vanilla flute note (real: movement-gated fat-trumpet loop — custom OGG).
- Siren's Call → mining fatigue/slowness while dry, cleared in water (real: staged longing stat + physical
  water-pull + passive Drowned).
- Brute → Strength+Speed while active (real: sprint ramp → launch/smash + custom sounds).
- Unseen → vanilla Invisibility (real: proximity-based full-render fade incl. armour/held).
- Silver Tongue → Hero of the Village (real: floor all trade prices to vanilla minimum).
- Hot Stuff → Fire Resistance placeholder (real: nearby-furnace smelting-speed boost — needs a
  cook-progress accessor).
- Angler → Luck (real: shorter fishing bite time + treasure bonus).
- Excavation → flat Haste (real: mining-driven Haste stacks with decay grace).
- Civilisation → Jump Boost (real: parkour coyote-time + edge magnetism).
- Chat → periodic canned action-bar line (real: client Twitch-chat overlay surfacing real info — Phase D).
- Laugh Track → server-wide vanilla `villager.yes` on chat, no cooldown (real: 3 custom laugh OGGs +
  200t cooldown).
- Bouncy → fall-damage immunity only (real: restitution/bounce physics + custom boing sounds).

**Event-driven attachments** got real hooks (not just tick/attribute): Super Explosive
(`LivingIncomingDamageEvent`, guarded against explosion-recursion) in a new `effects/CurseEventHandler`;
Thick Skinned (damage floor ≤2 negated), Bouncy (`LivingFallEvent`), Blacksmith (`AnvilUpdateEvent` cost
×0.4), Laugh Track (`ServerChatEvent`), and Last Stand (`LivingDeathEvent`, single-use revive + buffs,
consumes the blessing) all in `effects/BlessingEventHandler` alongside the existing Immortality hook.

**⚠ Collisions / spec-deviations noticed while building (report-only per Section 11 — need Oliver's call):**
1. **Explosive was on the wrong sacrificial item.** The prototype `CurseExplosive` used TNT, but Section 5
   assigns Explosive→Gunpowder and Super Explosive→TNT. Since two effects on one item makes one uncastable
   at the table, I corrected Explosive→Gunpowder (its spec-correct item). This is the one spec-alignment
   change I made to existing code, not just new classes — flag if that wasn't wanted.
2. **Modifier list still contains three items Section 9 says were REMOVED as modifiers:** Gunpowder
   (→ Explosive curse), Milk Bucket (→ Butterfingers curse), Redstone Dust (→ random-attachment table
   mechanic). `Modifier.java`/`ModifierItems.java` still register all three as modifiers. Because the
   modifier slot and sacrificial slot are separate, this isn't a hard runtime conflict yet, but it
   contradicts the master spec. NOT fixed in Phase A (it's Phase C/E items-and-modifiers cleanup) — logged
   here so it isn't lost.
3. **Firework Star (Main Character vs Celebration)** remains the unresolved Section 11 HARD flag — untouched
   (Phase F), still needs Oliver's decision. Section 17 suggests "celebration uses red wool."

**Verified:** compiles clean; dedicated server boots to Done with all 50 curses + 44 blessings registered,
zero duplicate-id or registration errors. Interactive `/bewitch apply` spot-check of the new attachments is
the next human test step (headless server can't easily self-issue the command).

---

### Phase B build log (Claude Code — status-effect layer hardening)

All three Phase B requirements done, all landing on existing single chokepoints (no new scattering):

1. **NO-STACKING / KEEP_LONGER** — implemented in `EffectManager.apply()`, the one method every application
   path already routes through (verified: table, `/bewitch`, Effigy, Voodoo Doll, Amethyst Bell, Jar,
   Gamble Coin, Executioner's Coin, Global Gamble, Mirror backfire — and it's the only caller of
   `ActiveEffects.put`). A reapply now keeps `max(existingRemaining, newDuration)` — never additive, never
   refreshed to a shorter time. The effect's own vanilla sub-effects are re-sized to the kept duration, and
   caster attribution follows the winning duration (a longer/equal reapply takes ownership; otherwise the
   original caster stands).
2. **Particles ONLY on onset/expiry** — the Cursed/Blessed/Afflicted wrappers were previously applied with
   `visible=true`, which made vanilla render their swirl particles *every tick* (violating Rule 6). Now
   applied `visible=false` (particles off) + `showIcon=true` (the victim still sees they're Cursed/Blessed/
   Afflicted, just not which specific attachments). `StatusEffectSync` bursts the witch-particle cloud
   manually, now **per-category** on the absent→present onset and present→absent expiry only — so curing
   all curses while keeping a blessing bursts once (curse expiry) and correctly leaves the Blessed wrapper
   untouched.
3. **Dynamic duration tracking (add/cure/extend/shorten)** — `StatusEffectSync.sync()` already recomputed
   each category's max-remaining on every mutation; the gap was that vanilla's `addEffect` keep-longer
   merge refuses to *shorten* an existing effect, so a burn-down (Purifying Water) wouldn't shrink the
   wrapper. Fixed by removing-then-re-adding the wrapper each sync so its duration matches the real
   max-remaining exactly, in both directions.

**Verified:** compiles clean. Client boot check + in-world spot-test (reapply an active curse with a
shorter duration → it doesn't shrink; watch the wrapper particles fire only at onset and expiry, not in
between) is the human test step.

---

### Phase C build log (Claude Code — items & blocks / ritual pipeline) — PARTIAL

Done this pass (all compile clean):
- **Creative tab** (user-requested, not in spec): replaced the MDK example tab with a real `witchmod` tab
  iconed by Cursed Essence, listing every item (Section 4) + block (Section 3) + the Purifying Water bucket.
  (The MDK `example_item`/`example_block` are still registered but no longer in any WitchMod tab — a later
  cleanup could delete them; they're the source of the two remaining `example_*` missing-model warnings.)
- **Redstone Dust random-attachment mechanic** (Section 3): Redstone in the Sacrificial slot now casts a
  RANDOM curse/blessing weighted by inverse cost (`weight = 1/cost^1.5`, `REDSTONE_RANDOM_LOW_BIAS`), so
  cheaper attachments are favoured. `RitualSlot` accepts Redstone in the sacrificial slot;
  `BewitchingTableRitual.pickRandomLowBiased` does the weighted roll. (The Table screen's probability-bar
  preview shows nothing for Redstone since the effect is random until cast — acceptable, minor.)
- **Modifier-list cleanup** (Section 9 — resolves the Phase A flag): removed Gunpowder, Redstone Dust, and
  Milk Bucket as modifiers from `Modifier.java` + `ModifierItems.java`. They're sacrificial items / the
  Redstone table mechanic now, not modifiers.
- **"Who?" global removed** (Section 8 — cut from roster): deleted `GlobalWho` + its registration. Global
  count is now 15, matching the Section 8 header.

**Global "bank" — clarified by Oliver and built.** The "global bank" was NOT a currency reservoir; it means
all globals share ONE server-wide cooldown whose success chance ramps from ~0 up to a base ceiling over a
few hours, identical for every player, resetting when a global fires. Cursed Essence stays a normal item
(no deposit/withdraw, no `GlobalBankCosts` — Section 10.6's block costs are obsolete under this model).
Implemented as `data/GlobalCharge` (a `SavedData` on the overworld's `DimensionDataStorage`, so it's one
value server-wide and persists): `currentSuccessChance = globalBaseChancePercent% * chargeFactor`, where
`chargeFactor` is 0→1 across `globalRechargeHours` of overworld game time. Both are new config values.
- **Trigger wiring:** `/bewitch event global <name>` (bare, no selector) is now a "natural attempt" — it
  rolls the current shared chance and only fires on success, then `reset()`s the charge for everyone. The
  form WITH a selector (`/bewitch event global <name> <player>`) is an operator force-fire (bypasses the
  roll, still resets) for attribution/testing.
- **Remaining Section 11 HARD flag** (Firework Star, Main Character vs Celebration) is Phase F, still owed.
- Note: this makes Section 8/10.6's "funded from the shared Global bank / bank costs in Blocks of Cursed
  Essence" wording stale — the doc text should be reconciled to the shared-cooldown model when convenient.
- **Not yet wired:** triggering globals from the Bewitching Table via their sacrificial items (Section 11
  says globals are table-forcible). The `GlobalCharge` roll is reusable for that when the Table-global path
  is built; right now the command is the trigger surface.

---

### Phase D build log (Claude Code — UI/HUD) — IN PROGRESS

The three main *screens* (Ledger, Table, Compendium) were already built in Phase 5. Phase D is the HUD/
overlay layer (Section 0.3/13.4) + the client-side curses — all of which need a server→client sync path
the mod didn't have.

**Key infrastructure win:** NeoForge 21.1 attachments support **auto-sync** (`AttachmentType.Builder.sync(
StreamCodec)`), so per-player HUD state can be synced to the owning client with ZERO hand-rolled payload/
networking code — the client just reads the attachment. This is the reusable pattern for every Phase D bar.

**Thirst Meter (first slice — spec-marked FULLY FUNCTIONAL) — DONE:**
- `WitchModAttachments.THIRST` — an auto-synced `int` attachment. `-1` = curse inactive (bar hidden),
  `0..20` = active thirst.
- `CurseThirstMeter` rewritten from its placeholder to drive it: onApply → full; onTick drains 1/10s while
  dry, sips +1/2s while standing in water (25% chance of a Hunger debuff — the "raw water risk"), empties to
  Slowness+Weakness (no damage); onRemove → `-1` to hide the bar. Self-heals the value on relog.
- Refill: finishing a **Water Bottle** (+6) via `LivingEntityUseItemEvent.Finish` in `CurseEventHandler`.
- `client/ThirstHudLayer` (a `LayeredDraw.Layer` registered above `FOOD_LEVEL` via `RegisterGuiLayersEvent`)
  reads the synced attribute and draws a 10-droplet bar above hunger. No `thirst_icons.png` yet, so droplets
  are placeholder filled rectangles (blue = filled) — a drop-in `blit` swap once art lands; the value/
  behaviour are fully functional.
- Simplifications flagged in-class: the spec's "right-click a water source" is realized as the passive
  in-water sip (raw water isn't a right-clickable block); sacrificial item stays Glass Bottle (the spec's
  "Water Bottle" can't be isolated under exact-item matching — Section 11 soft flag).
- **Verified:** compiles clean. Client boot + in-world test (`/bewitch apply witchmod:thirst_meter @s` →
  droplet bar appears above hunger, drains over time, refills from water bottles / standing in water,
  empties to Slowness+Weakness; wears off → bar disappears) is the human test step.

**Gluttony (second slice — CUSTOM UI) — DONE:**
- `WitchModAttachments.GLUTTONY_HUNGER` — auto-synced `int`, same `-1`/`0..20` convention as Thirst.
- `CurseGluttony` rewritten from its placeholder: onApply → full extra bar + a `Attributes.SCALE` +0.35
  modifier (the "bigger model", 1.35x); onTick drains the extra bar (~1.5x a normal bar), gives periodic
  Hunger when empty, and cuts sprinting off while normal food ≤ 12 (double the vanilla threshold);
  onRemove → `-1` + removes the scale modifier. Eating any food tops up the extra bar (via the same
  `LivingEntityUseItemEvent.Finish` hook, using the food component's nutrition).
- `client/GluttonyHudLayer` — a second hunger row (brown placeholder rectangles, distinct from Thirst blue).
- **Bar stacking (per Oliver's note about both being active at once):** new `client/HudBars` owns the
  right-side stack layout — bars stack upward from just above hunger, each bar's row = count of active bars
  below it. Order bottom→top: Gluttony (row 0, it's a hunger row) then Thirst (row 1 when Gluttony is also
  active, row 0 otherwise). `ThirstHudLayer` now asks `HudBars` for its row instead of a hardcoded y, so the
  two never overlap regardless of which are active. Future right-side bars extend `HudBars` in one place.
- **Verified:** compiles clean; client boot check + in-world test (`/bewitch apply witchmod:gluttony @s` →
  second hunger row appears + model grows; drains over time; eat to refill; sprint cuts out at low food;
  apply thirst_meter too → the two bars stack, no overlap) is the human test step.

**Gluttony bar now uses real vanilla hunger sprites.** Per Oliver, `GluttonyHudLayer` blits the actual
`hud/food_empty|half|full` sprites with the same logic as `Gui#renderFood`, so it's literally a second
hunger bar visually (not the earlier placeholder rectangles). Thirst stays placeholder blue (no vanilla
"thirst" sprite exists).

**Loading Screen (third slice — FULLY FUNCTIONAL) — DONE:**
- Establishes the **one-shot trigger-sync** pattern: an auto-synced `LOADING_SCREEN_END_TICK` (long)
  attachment. The server sets it to `gameTime + 60` when the trigger fires; the client renders the overlay
  while the synced world game time is below it — no hand-rolled payload, reusing attachment auto-sync +
  the fact that world game time is itself synced to the client.
- **Trigger:** `PlayerInteractEvent.RightClickBlock` in `CurseEventHandler` — opening a block tagged
  `DOORS`/`TRAPDOORS`/`FENCE_GATES` while cursed rolls a 50% chance on a 400-tick per-player cooldown
  (transient UUID-keyed map) to flash the screen for 60 ticks.
- **Overlay:** `client/LoadingScreenOverlay` (registered `registerAboveAll` so it covers the whole HUD) —
  black fullscreen + a text spinner + a random useless tip (stable for the flash, seeded off the end tick).
- `CurseLoadingScreen` rewritten from its blindness placeholder to just declare cost/item + dismiss any
  overlay on cure/expiry.
- PROTOTYPE gaps flagged: custom ambience sound (Section 12) + the writable `loading_tips.json` (small
  hardcoded tip pool for now) + a real spinner texture are deferred.
- **Verified:** compiles clean; client boot check + in-world test (`/bewitch apply witchmod:loading_screen
  @s`, then open doors repeatedly → ~50% of the time a black fake-loading overlay flashes for ~3s with a
  spinner and a tip; respects the cooldown) is the human test step.

**Chat (Twitch overlay) — DONE:** `CHAT_OVERLAY` synced flag (set by `BlessingChat` onApply/onRemove);
`client/ChatOverlayLayer` renders a translucent purple side-panel of fake Twitch messages, generated
client-side from a hardcoded pool on a timer (purely cosmetic, so the active flag is all that's synced).
Real-info surfacing + a frame texture + the writable `twitch_chat.json` are deferred.

**Organised (extra inventory row) — DONE (with a documented shape choice):** the spec is "9 extra inventory
slots, drop on expire." Rather than inject a literal extra row into the vanilla inventory screen (which
needs a mixin), the 9 slots are a per-player stash: a serialized `ORGANISED_ITEMS` attachment (copied on
death since blessings persist), opened as a **plain vanilla 1-row chest** via `data/OrganisedStash` (a
`SimpleContainer` that saves back to the attachment on close — no custom Menu/Screen needed) through a new
`/bewitch organised` command. `BlessingOrganised.onRemove` drops all 9 (ORGANISED_DROP_ON_EXPIRE); it also
keeps the prototype's bonus auto-consolidate-stacks tick. A survival **keybind** to open the stash is the
intended follow-up (currently command-only) — logged in Human Action Items.

**Client-side curses — DONE.** All four now run genuinely client-side, driven off auto-synced flags on the
local player (`MOONWALKER_ACTIVE`, `SCREENSAVER_ACTIVE`, `MINOR_INCONVENIENCE_ACTIVE`, and a
`PACING_END_TICK` for the triggered one) — the server just flips the flags; the effects live in
`client/ClientCurseHandler` (`@EventBusSubscriber(Dist.CLIENT)`, game bus).
- **Moonwalker** — `MovementInputUpdateEvent`: negates `forwardImpulse` and swaps up/down, so forward/back
  are reversed. Genuinely functional (not the Phase A Slowness placeholder anymore).
- **Screensaver** — `ClientTickEvent.Post`: bounces the actual OS window around the monitor DVD-logo style
  via GLFW (`glfwGetWindowPos`/`glfwSetWindowPos` + primary-monitor video mode), in episodes (~200t bounce
  per ~600t cycle). No-ops when fullscreen or the monitor can't be queried (per spec).
- **Minor Inconvenience** — `ClientTickEvent.Post`: renames the OS window title to silly strings
  (`Window.setTitle`) on an interval; restores "Minecraft" when the curse ends. PROTOTYPE gaps: the
  fullscreen-block half of the spec + the writable `window_titles.json` are deferred (hardcoded pool).
- **Pacing** — REFACTORED to the real One Piece "dramatic moment" spec (per Oliver). Server side
  (`data/PacingManager`): the trigger chance RAMPS 0→1 over ~2 min since the last moment (`PACING_CHARGE_START`
  attachment), pre-charged to ~0.85 on curse apply so it starts high; each combat hit rolls `charge *
  MAX_HIT_CHANCE`; if the ramp maxes out without a hit it auto-fires on a non-combat server tick. A moment is
  a **time-stop**: the victim + up to 5 nearby living entities are frozen (invulnerable + Mob `setNoAi`, zero
  velocity; players get near-total Slowness as a best-effort since a player can't be truly frozen
  server-side) and restored on a `ServerTickEvent` after `FREEZE_TICKS`. Client side (`ClientCurseHandler`):
  the camera is hijacked into **third-person** + `setCameraEntity`, cutting between angles around the frozen
  victim (camera entity = victim, varied yaw) then to the **faces of nearby entities** (camera entity =
  that entity, yaw = its facing + 180 → sits in front of its face; works for players too).
  - **Feasibility note (told to Oliver):** true free-camera positioning (flying the camera to arbitrary
    points) is NOT possible via NeoForge events — `Camera.setup` overwrites the camera position *after* the
    modifiable `ComputeCameraAngles` event, so only yaw/pitch are controllable. The third-person +
    `setCameraEntity` approach achieves the spec (orbit victim / face nearby entities) with public API only;
    a literal free-fly camera would need a `Camera` mixin (build-infra the project doesn't have yet).
  - PROTOTYPE gaps: custom sting sound (Section 12) deferred; the freeze can't 100% pause other *players*
    (invuln + slowness best-effort).

**Force-windowed for the window curses (per Oliver):** Screensaver and Minor Inconvenience force the game
OUT of fullscreen while active — they're invisible/no-op in fullscreen, and being annoying is the point.
Implementation gotcha (fixed): the fix is JUST `mc.options.fullscreen().set(false)`. Vanilla's fullscreen
`OptionInstance` change-callback (Options.java) already calls `window.toggleFullScreen()` itself, so the
first attempt (which ALSO called `toggleFullScreen()`) double-toggled straight back to fullscreen and did
nothing visible. Setting the option alone is the whole fix.

**Phase D is now functionally complete** — all Section 0.3 CUSTOM UI overlays (Thirst, Gluttony, Loading
Screen, Organised, Chat) and all the client-side curses are built. Remaining Phase D polish is art (the
placeholder→PNG swaps catalogued in Section 17) and the deferred bits noted per-effect above.
