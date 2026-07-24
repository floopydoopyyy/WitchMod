# Bewitchment (placeholder name) — CLAUDE.MD (Refinement Stage)

STATUS: Prototype + Phases A–F COMPLETE. Every attachment, item, and block is implemented and functional
(the per-phase build logs at the bottom record the stand-ins/simplifications each currently uses). **The
task is now REFINEMENT** — bringing each one from "functional prototype" up to its full, polished spec, one
at a time, hands-on with Oliver. The workflow and the tick-off checklists live in **Section 16**; the rest
of this document (Sections 1–15) is the reference for what "perfect" means per attachment. This file is the
single source of truth; where older notes conflict with it, this file wins.
Totals: **49 curses, 45 blessings, 11 neutrals, 15 globals, 15 modifiers.**
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
| Ward | Necklace; deflects spells back to sender, particle-points toward attacker; durability | Emerald, Cursed Essence, String |
| Scrying Mirror | Reveals your own active attachments | Glass, Cursed Essence, Diamond |
| Effigy | Forwards your curses to another player on click | Totem of Undying, Cursed Essence |
| Cursed Coin | Gambles a random curse OR blessing | Gold, Cursed Essence |
| Blessed Coin | Random blessing | Loot tables only |
| Executioner's Coin | Revives, inflicts random curse | Loot tables only |
| Jar | Right-click another player to capture an active curse; expensive; breaks on use; intentionally 2-player | Netherite Ingot, Cursed Essence, Glass |
| Cursed Jar | Pre-filled Jar variant, flavourtext-named | — |
| Amethyst Bell | Bell retexture; flips a random active effect, cooldown | — |

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

### Taxes — Emerald  ✅ REFINED
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
- The **Bewitching Table refuses** to apply Taxes ("the ritual fizzles").
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

### Fortune — Diamond
```
FORTUNE_BONUS_DROPS_MIN=1  FORTUNE_BONUS_DROPS_MAX=2  (additive AFTER enchant Fortune, never multiplicative)
Applies to ore-tag blocks only
```

### Peace — Poppy
```
PEACE_SPAWN_RATE_MULT=0.3  PEACE_RADIUS=48  PEACE_DETECTION_MULT=0.4
```

### Luck — Rabbit's Foot
```
LUCK_ATTRIBUTE_BONUS=5.0
```

### Full — Bread
```
FULL_HUNGER_FROZEN=true  FULL_SATURATION_FROZEN=true
```

### Army — Shield
Hostiles neutral to you; attack different-type entities that damage you.
```
ARMY_RADIUS=24  ARMY_DEFEND_DURATION=600  ARMY_SAME_TYPE_EXCLUDED=true
```

### Reflect — Turtle Shell
```
REFLECT_RETURN_VELOCITY_MULT=1.5  REFLECT_DAMAGE_NEGATED=true  REFLECT_ACCURACY=0.85
```

### Soul Bond — Totem of Undying
Nearest player becomes the bond victim (takes half your damage); treated as a CURSE on them
(counts toward their limit, shows Cursed status); continuously rebinds to nearest.
```
SOULBOND_DAMAGE_SHARE=0.5  SOULBOND_REBIND_INTERVAL=100  SOULBOND_MAX_RANGE=32  SOULBOND_VICTIM_IS_CURSED=true
```

### Bodyguard — Bone (CUSTOM ENTITY)
Armoured sunglasses-skeleton. WARNING (chat dialogue trees) → AGGRESSION (warning hits) →
ATTACKING (pursue until death or leash). Chats between all stages. Its death breaks the blessing
instantly.
```
BODYGUARD_HEALTH=60.0  BODYGUARD_DAMAGE=6.0  BODYGUARD_WARNING_RADIUS=8  BODYGUARD_AGGRESSION_RADIUS=4
BODYGUARD_WARNING_HIT_DAMAGE=1.0  BODYGUARD_LEASH_RANGE=32  BODYGUARD_DIALOGUE_COOLDOWN=200
List: data/bewitchment/text/bodyguard.json
```

### Tax Man — Gold Ingot
Delivers your banked taxed valuables. Waits for safety (no hostiles) + slight idle; Tax Man walks
up, drops haul, despawns; blessing breaks.
```
TAXMAN_SAFE_RADIUS=16  TAXMAN_IDLE_REQUIRED=100  TAXMAN_DELIVERY_CAP=512  TAXMAN_WALK_IN_DISTANCE=12
```

### Hype Man — Any Music Disc (tag: minecraft:music_discs — EXPLICIT TAG EXCEPTION)
Actions trigger nearby-player praise: combat, pickups, chest looting, existing nearby.
```
HYPEMAN_TRIGGER_COOLDOWN=900  HYPEMAN_TRIGGER_CHANCE=0.35  HYPEMAN_RADIUS=16
List: data/bewitchment/text/hypeman.json
```

### Workman — Netherite Scrap
```
WORKMAN_TOOLS_NO_DURABILITY=true  WORKMAN_ARMOR_NO_DURABILITY=true
```

### Pickpocket — String
```
PICKPOCKET_RADIUS=1.5  PICKPOCKET_CHECK_INTERVAL=100  PICKPOCKET_BASE_CHANCE=0.05
PICKPOCKET_BEHIND_MULT=4.0  PICKPOCKET_HOTBAR_WEIGHT=0.1
```

### Windfall — Wind Charge
```
WINDFALL_INTERVAL_MIN=2400  WINDFALL_INTERVAL_MAX=7200  WINDFALL_BENEFICIAL_WEIGHT=0.85
Loot: data/bewitchment/loot/windfall.json  (drifts by with wind particles; despawns after 600t if ignored)
```

### Immortality — Ghast Tear
Death → recovery state: items drop, immobile, slow rebuild, respawn at death spot. Respawn time
grows per use; breaks after third.
```
IMMORTALITY_RECOVERY_BASE=600  IMMORTALITY_RECOVERY_GROWTH=2.0  IMMORTALITY_MAX_USES=3
IMMORTALITY_VULNERABLE=true  IMMORTALITY_DROPS_ITEMS=true
```

### Sixth Sense — Compass
```
SIXTHSENSE_INTERVAL=1200  SIXTHSENSE_STRUCTURE_RANGE=128  SIXTHSENSE_PLAYER_RANGE=64
Vagueness: cardinal direction only, actionbar
```

### Iron Stomach — Raw Chicken
```
IRONSTOMACH_NEGATIVE_FOOD_EFFECTS=cancelled  IRONSTOMACH_HUNGER_MULT=1.25  IRONSTOMACH_SATURATION_MULT=1.5
```

### Iron Lung — Kelp
```
IRONLUNG_BREATHE_IN_BLOCKS=true  IRONLUNG_BREATHE_IN_WATER=true  (wall-clip damage still blocked)
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

### Twist of Fate — Nether Star
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

### Steady Hands — Spectral Arrow
```
STEADYHANDS_SPREAD_MULT=0.2  STEADYHANDS_CHARGE_TIME_MULT=0.6  STEADYHANDS_MULTISHOT_SPREAD=0.3
(multishot pellets can hit one target multiple times)
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

## 7. NEUTRALS (11)

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

## 8. GLOBALS (15)

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
Taxes - Major - 80
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
Tax Man - Minor - 35
Hype Man - Minor - 28
Workman - Minor - 28
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

**Soft flags:**
- Written Book (Mansplainer) vs Book and Quill (Letter neutral): distinct items (signed vs
  unsigned), close enough to confuse players; Compendium should render both icons clearly.
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
```
/bewitch apply {attachment} {selector}         (default selector = caster; supports duration override for testing)
/bewitch remove {selector} {attachment}        (default selector = caster)
/bewitch clear [targets] [curses|blessings]    (added: strips ALL attachments, or just one category; default target = caster)
/bewitch event {neutral|global} {eventname} {selector}
        (neutral default selector = caster; global needs none, but a given selector marks that
         player as initiator for Ledger attribution; supports duration override where time-based)
/bewitch forcestop {neutral|global} {selector} (unstoppable-by-design events silently no-op)
/bewitch organised                             (added, Phase D: opens the Organised blessing's 9-slot stash)
```
Note: `/bewitch clear` is backed by `EffectManager.removeAll(target, @Nullable category)` (fires each
effect's onRemove for clean teardown). The Thirst + Gluttony HUD bars now hide in creative/spectator
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
- [ ] Heavy
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
- [x] Floor Is Lava — stand still past a 5s grace and you burn, ramping +0.5 per burn to a 4.0 cap. Grace
      keeps it playable (craft, read a sign); the ramp stops it being a flat tax you eat; the cap stops an AFK
      player being executed. Vanilla's own `HOT_FLOOR` source (the magma-block one, matching the sacrificial
      item), so fire resistance is deliberate counterplay. Flames thicken as it climbs; movement measured as
      real displacement so turning on the spot won't save you; deliberately NOT paused in GUIs. 6 config knobs.
- [x] Heavyweight — blocks with air beneath give way under you, scaling on hardness (leaves 0.8s, dirt 1.1s,
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
- [ ] Taxes
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
- [x] Heavy Handed (renamed from Uncareful) — tools/armour wear 4x as fast. No event modifies a durability
      hit's amount, so it WATCHES: records the damage value of the six wearing slots (both hands + 4 armour)
      each tick and, when one rises, re-applies the shortfall as EXTRA wear via `ItemStack.hurtAndBreak` (so
      Unbreaking still mitigates and a piece that crosses its limit breaks properly). Recorded AFTER the
      top-up so the extra isn't compounded next tick. Idle inventory untouched. Item stays Flint. Discovers
      on first extra wear. 1 config knob.

**BLESSINGS (44 listed; header says 45 — reconcile if a 45th is intended)** — renamed ids: Workman =
`tools_dont_use_durability`, Personal Trainer = `trainer`, Hawk Guy = `locked_in`.
- [ ] Fortune
- [ ] Peace
- [ ] Luck
- [ ] Full
- [ ] Army
- [ ] Reflect
- [ ] Soul Bond
- [ ] Bodyguard
- [ ] Tax Man
- [ ] Hype Man
- [ ] Workman
- [ ] Pickpocket
- [ ] Windfall
- [ ] Immortality
- [ ] Sixth Sense
- [ ] Iron Stomach
- [ ] Iron Lung
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
- [ ] Hot Stuff
- [ ] Bouncy
- [ ] Excavation
- [ ] Angler
- [ ] Laugh Track
- [ ] Chat
- [ ] Civilisation
- [ ] Low Gravity

**NEUTRALS (11)**
- [ ] Wooliam
- [ ] Disguise
- [ ] Anvil
- [ ] Letter
- [ ] Creeper
- [ ] Useless Trade
- [ ] Moovin
- [ ] Celebration
- [ ] Mansplaining
- [ ] Damage
- [ ] Mirror (backfire-only)

**GLOBALS (15)**
- [ ] Inventory Shuffle
- [ ] Russian Roulette
- [ ] Player Shuffle
- [ ] Hot Potato
- [ ] Spot Shuffle
- [ ] Gravity Flip
- [ ] Party Time
- [ ] Auction
- [ ] Apocalypse
- [ ] Aporkalypse
- [ ] TNT Rain
- [ ] Silence
- [ ] Firework Show
- [ ] Floor Is Lava (global)
- [ ] Gamble

**ITEMS (Section 4)** — start only after every attachment above is checked off.
- [ ] Cursed Essence
- [ ] Player Essence
- [ ] Compendium
- [ ] Voodoo Doll
- [ ] Needle
- [ ] Ward
- [ ] Scrying Mirror
- [ ] Effigy
- [ ] Cursed Coin
- [ ] Blessed Coin
- [ ] Executioner's Coin
- [ ] Jar
- [ ] Cursed Jar
- [ ] Amethyst Bell
- [ ] Recovery Compass (modifier item)

**BLOCKS (Section 3)** — start only after every item above is checked off.
- [ ] Bewitching Table
- [ ] Block of Cursed Essence
- [ ] Ledger
- [ ] Warding Totem
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
