# Puppeteer — vanilla mob checklist (Minecraft 1.21.1)

Every living mob in 1.21.1 (82, from the game's own entity registry; players excluded). Work down it: mark
the ones that aren't suitable `❌`, and the next batch to build `🔜`. Babies of a possessable mob are always
possessable too (half size; baby zombies get their +50% speed).

**Status key:** ✅ done · 🔜 next batch · ⬜ to review · ⏸ deferred (wanted, not built) · ❌ ruled out

**Never possessable (each gets its own message):** any other mod's creature ("foreign to the Puppeteer"), and
every one of THIS mod's creatures — its own entities (Tax Man, Bodyguard, Spaghetti Man, Snail, ...) and vanilla mobs
playing its characters (Guardian Angel, Solicitor, the Dweller's possessed, Woolliam). Ruled-out vanilla mobs (❌ below)
get a third message, and not-yet-built ones say "You can't possess a … (yet)".

The "idea" column is just a starting point for discussion — your call on all of it.

## Passive / farm (CREATURE)
| Mob | Status | Idea / notes |
|---|---|---|
| Pig | ✅ | Oink. Lured by carrots/potatoes/beetroot. |
| Cow | ✅ | Moo. Milkable by others. |
| Sheep | ✅ | Eat Grass (regrows wool). Shearable by others. |
| Chicken | ✅ | Lay Egg (25s) / hold: Explosive Egg (8s). Slow fall. |
| Mooshroom | ✅ | As cow; bowl → stew, bucket → milk; shears → mushrooms + you become a cow. |
| Rabbit | ✅ | Hop-only movement: low, vanilla-rabbit hops on the movement keys (steerable mid-air; sprint to bolt — longer hops); the jump key gives the full boosted jump. No right-click move; lured by carrots. **Killer bunny** variant: long, low, distance-eating chase hops, huge bites (18), and Maul (3s): a huge lunge that latches onto the first thing hit for a pinned, frenzied scrap. |
| Horse | ✅ | Controls like a ridden horse (sprint, hold jump to charge a leap, full-block step-up, sinks in water). Other players right-click to ride (no taming or saddle; you steer). Saddle and horse armour can be put on you. Basic kick. |
| Donkey | ✅ | As horse, a little slower with a lower leap; others can strap a chest on you and crouch + right-click to open the saddlebags. |
| Mule | ✅ | As donkey. |
| Llama | ✅ | A llama's pace, basic movement; right-click spits (the real spit — 1 damage, mostly an insult). |
| Trader Llama | ✅ | As llama. |
| Skeleton Horse | ✅ | As horse, and it goes underwater like the real one (no slowing down, never drowns). |
| Zombie Horse | ✅ | As horse. |
| Camel | ✅ | Nimble ride (1.5-block step-up, normal jump); two riders in the real camel's seats, you steer. Hold right-click to charge a dash (horse-jump-bar style), boosted over vanilla. |
| Goat | ✅ | Hold right-click: a ram that goes further, faster and harder the longer you charge — no damage, absurd knockback, doubles as travel. **Screaming goat** keeps its screams, and hold left-click (fully charged) for a wall-piercing, no-damage sonic shriek with truly insane knockback (30s cooldown). |
| Fox | ✅ | Fox pace with a bigger sprint; crouching is silent. Picks items up in its mouth (drop key lets go) and eats food it holds. Right-click: Pounce — a rush that steals what the first holder it reaches is holding (swapping in whatever you carried) — or, with nothing to steal, bites the first thing it reaches. |
| Wolf | ✅ | Swings pounce (like a real wolf's lunge-bite). Always-on scent sense: nearby mobs leave fading footprint trails only you see (a limited Bloodhound). Crouch = sit; others feed it its meat to heal it. **Frenzy** (on being hit): goes rabid — Darkness, a faster bite, the attacker's scent burned in bright red, and a weakened killer-bunny Maul on right-click for pursuit. |
| Cat | ✅ | No bite; left-click meows. Quick, and takes no fall damage. Scares creepers off (they deflate and flee). Crouch = sit; sitting on a chest blocks others opening it. Right-click = Scare (6s): flung backwards + up with a loud hiss — a nearby explosion or firework forces it. |
| Ocelot | ⬜ | Fast, scares creepers. |
| Parrot | ⬜ | Flight (flutter), mimic mob sounds. |
| Panda | ⬜ | Roll / sneeze (drops slime), lazy variants. |
| Polar Bear | ⬜ | Stand + swipe; strong. |
| Turtle | ⬜ | Very slow on land, fast in water; lay eggs. |
| Frog | ⬜ | Tongue-eat small slimes/magma cubes; big jump. |
| Tadpole | ⬜ | Tiny fish-like; grows into a frog? |
| Armadillo | ⬜ | Roll up (big damage reduction while curled). |
| Bee | ⬜ | Flight; sting (poison) then the bee "dies"? |
| Allay | ⬜ | Flight; collect items (but your inventory is stashed…). |
| Sniffer | ⬜ | Sniff up ancient seeds; very slow. |
| Strider | ⬜ | Walk on lava; shivers out of it. |
| Wandering Trader | ✅ | Treated like a villager: Hmm, others open your shop (your real trades; emeralds go to your till, paid out on leaving), hunted by zombies/illagers + defended by golems, panics when hurt. |

## Water
| Mob | Status | Idea / notes |
|---|---|---|
| Cod | ✅ | Leap / dash (4s). Flops and dries out on land. |
| Salmon | ✅ | As cod. |
| Pufferfish | ✅ | As cod; hits poison. |
| Tropical Fish | ✅ | As cod (keeps its pattern/colours). |
| Dolphin | ✅ | Very fast swimmer that breathes air (16× air). Inspire (5s): Dolphin's Grace to nearby players + water mobs. Eats fish (swim into it / be fed). No treasure-finding. Dries out on land. |
| Squid | ✅ | Slow swimmer, dies on land. Flee: dash + ink (blinds). |
| Glow Squid | ✅ | As squid; ink blinds AND makes them glow. |
| Axolotl | ✅ | Hold: play dead as long as you like (12s cooldown after) — lie still, regen fast, mobs drop aggro. Bites for 2. |
| Guardian | ✅ | Hold: a laser you aim yourself — ticks damage, ends in a burst; rallies guardians on hit. Swims fast; bounces about on land. Spikes out (thorns) when still. |
| Elder Guardian | ✅ | As guardian, bigger burst; curses nearby players with Mining Fatigue (and the face) every minute. |

## Zombies & undead (MONSTER)
| Mob | Status | Idea / notes |
|---|---|---|
| Zombie | ✅ | Rally. |
| Zombie Villager | ✅ | Rally (treated as a zombie). |
| Husk | ✅ | Rally (raises husks); hits cause Hunger; no sunburn. |
| Drowned | ✅ | Always holds a trident. Hold: Throw Trident (rallies drowned on hit). |
| Zombified Piglin | ✅ | Separate from zombies. Hit / get hit → the whole pack turns on it + Speed II. Convert (right-click a pig; radius). Fire immune. |
| Skeleton | ✅ | Draws a REAL bow (25% faster; archery blessings apply). Sunburn. |
| Stray | ✅ | As skeleton; arrows slow (Slowness 30s). |
| Bogged | ✅ | As skeleton; arrows poison (5s). |
| Wither Skeleton | ✅ | No move (vanilla has none). Hits inflict Wither; fire immune, no sunburn. |
| Zoglin | ✅ | Lone brute: faster, harder tusk toss, no fear. Lunge (5s): short wind-up, faster, turns more, ploughs through everything. |
| Phantom | ✅ | Free flight like the bat (no sprint-fly without the Flight blessing). Left-click bites (normal attack); right-click is a free Dive (skill shot, bonus damage). Burns in daylight. |
| Giant | ❌ | Unused vanilla mob. |

## Other hostiles (MONSTER)
| Mob | Status | Idea / notes |
|---|---|---|
| Creeper | ✅ | Hold: Explode. Charged via lightning / Comic Relief. |
| Spider | ✅ | Wall-climb (Spider blessing); swing = pounce, swing mid-jump = bigger air pounce. |
| Cave Spider | ✅ | As spider; bites poison. |
| Enderman | ✅ | Right-click: smart teleport (22 blocks, lands you on the ground; 4s charge). Crouch + right-click a block: pick up / place. Stared at → enraged: Speed, two charges, faster recharge, starers glow for you only. Dodges projectiles; water hurts. Hits for 7. |
| Endermite | ✅ | Right-click: Burrow — sink into the ground and move straight through blocks (look to steer, jump / sneak to rise / sink); obsidian-hard and unbreakable blocks stop it. Right-click again: surface to open air. |
| Silverfish | ✅ | Right-click stone: Embed (1.5s) — hide inside the block (untouchable, can look out); right-click again to burst out with a brood (one per 2s hidden, max 9) that goes for the closest mob. Right-click anywhere else: Call — wakes infested stone nearby AND infests more. Whatever you bite, nearby silverfish go for (no glow). |
| Slime | ✅ | Bounces to move (steerable mid-air), no fall damage, hits grow with its size. "Dies" → splits like the real one and you carry on as one of the offspring (the smallest really dies). No special move. |
| Magma Cube | ✅ | As slime, bounces higher, hits harder, fire- and lava-proof. |
| Blaze | ✅ | Free flight (no sprint-fly without the Flight blessing), slow fall; melee 6; hold right-click: wind-up then a volley of 3-6 fireballs (by charge). Lights up in a fight. Water, rain and snowballs hurt; fire proof. |
| Ghast | ✅ | Always flying, slowly; full 4x4x4 hitbox. Right-click: one fireball (3s). Hold left-click: charge (slowed, shaking, view narrows), release for a flamethrower-like stream of 5 scattered fireballs. Face + cry only as it fires. Fire / lava proof. |
| Witch | ✅ | Hotbar = her potion belt (scroll to pick): right-click throws it as a splash, hold for lingering; left-click drinks it. Her magic resistance; her own brews don't touch her. |
| Pillager | ✅ | Real crossbows on the hotbar (load + fire, every crossbow blessing), bottomless quiver, +25% bolt damage; no melee. |
| Vindicator | ✅ | A brute: big speed, big hits, a quicker swing; keeps its axe's enchantments. **Hold right-click = a committed DbD-style Lunge** (charge for bonus damage; a miss costs a 12-tick recovery). Named "Johnny": attacks anything nearby on its own, with bonus damage, speed, swing speed, extra hit sparks — and an enhanced lunge. |
| Evoker | ✅ | Right-click spells (one HUD line shows what letting go casts): tap — fang ring; hold — fang line; hold longer — 3 vexes (15s) that hunt whoever you last hit; at a sheep — wololo; at a villager — turn it into a witch. Left-click: Rallying Horn (20s) — every illager and witch nearby turns on your target, sped up. No melee. |
| Illusioner | ❌ | Unused vanilla mob. |
| Vex | ❌ | Flies through walls. |
| Ravager | ⬜ | Roar (knockback ring), ram. Big. |
| Piglin | ⬜ | Barter (no inventory though), crossbow/sword. |
| Piglin Brute | ⬜ | Strong axe. |
| Hoglin | ✅ | Lone brute: tusk toss, pushed away by warped fungus, babies bolt when hurt, zombifies outside the Nether. Lunge (9s): stop, charge with limited turning, first hit takes 10 and goes flying. |
| Shulker | ⬜ | Immobile; levitation bullets; teleport. |
| Breeze | ✅ | Free flight like the blaze; no melee; hostile mobs that come too close push you away on the wind; projectiles bounce off you. Left-click: wind charge. Hold right-click: a slow, huge gale (bigger radius, far more knockback). |
| Guardian / Elder Guardian | — | see Water. |
| Warden | ⬜ | KEPT on purpose (Oliver): hilarious, a headline feature. Sonic boom, sniff, darkness pulse. |

## Bosses
| Mob | Status | Idea / notes |
|---|---|---|
| Ender Dragon | ❌ | Boss. Refused with its own message. |
| Wither | ❌ | Boss. Refused with its own message. |

## Utility / villagers (MISC category in code)
| Mob | Status | Idea / notes |
|---|---|---|
| Villager | ✅ | Hmm (nearby villagers look and hmm back). Other players can trade with you (your real trades; emeralds go in your till, paid out on leaving). Hunted by zombies / illagers, defended by golems, panics when hurt; killed by a zombie → infected into a zombie villager puppet. |
| Iron Golem | ✅ | Huge weapon-cooldown swings that fling upward (like a real golem); hold right-click to offer a poppy (Strength + Resistance). No knockback, can sprint, repaired with iron. Golem-hating monsters hunt you. |
| Snow Golem | ✅ | Feeble chilling punch; tap: snowball (damage + a sharp, brief Slowness III); hold: wind-up, then a blizzard — 12 salvos of 30 enhanced snowballs (360!) in a wide 80° fan in just over a second, thrown hard and lobbed near and far, each bursting where it lands (splash damage + chill): blankets an area — at most 30 damage to any one target per blizzard. Melts in heat, hurt by water, snow trail, shearable pumpkin. |
| Bat | ✅ | Flies like the bat disguise; can't attack. |

_Counted from `net.minecraft.world.entity.EntityType` (1.21.1): 79 living-mob registrations + villager, iron golem
and snow golem (registered under MISC)._
