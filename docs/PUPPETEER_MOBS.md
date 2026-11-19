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

**Every puppet:** attack a mob and the rest of its kind nearby sees through you — they all turn on you (you've been
revealed as an imposter).

## Passive / farm (CREATURE)
| Mob | Status | Idea / notes |
|---|---|---|
| Pig | ✅ | Oink. Lured by carrots/potatoes/beetroot. Takes a saddle; saddled, others can climb on — a rider holding a carrot on a stick steers you and boosts you with it, just like a real pig. |
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
| Wolf | ✅ | Swings pounce (like a real wolf's lunge-bite) — the leap bites one thing per pounce. Always-on scent sense: nearby mobs leave fading footprint trails only you see (a limited Bloodhound). Crouch = sit; others feed it its meat to heal it. **Frenzy** (on being hit): goes rabid — Darkness, a faster bite, the attacker's scent burned in bright red, and **hold right-click to charge a Maul** (slowed, view zooms in; a fuller charge lunges further) that latches on for a weakened killer-bunny scrap. |
| Cat | ✅ | No bite; left-click meows. Quick, and takes no fall damage. Scares creepers off (they deflate and flee). Crouch = sit; sitting on a chest blocks others opening it. Right-click = Scare (6s): flung backwards + up with a loud hiss — a nearby explosion or firework forces it. |
| Ocelot | ✅ | Fast, no bite, no fall damage, scares creepers off. Left-click meows; right-click: Flee (8s) — a huge burst of speed, like a wild ocelot bolting (no particles, as vanilla). |
| Parrot | ✅ | Free but slow flight; no bite, no fall damage. With music near, hold right-click to dance (fast regeneration). Otherwise right-click a player or humanoid mob to perch on their shoulder (off again if they fall, jump, swim or get hit), or right-click any mob — even through walls — to mimic it. Sees every mob nearby through walls (just for you). Left-click squawks. A cookie still kills it. |
| Panda | ✅ | No bite. Left-click: **Sneeze** — vanilla's wind-up and sneeze (the cloud, maybe a slimeball, nearby pandas jump), and it sends you back a step. Right-click: **Roll** the way you're looking (vanilla's tumble). Both animate on the model as the real panda's do. Lured by bamboo. |
| Polar Bear | ✅ | Left-click swipe on a short cooldown (rears up like the real one, a bit more damage); cubs can't attack. Fast, a strong swimmer, never freezes. Right-click: growl. A cub hurt nearby (real or a puppet): you see who did it and go into a frenzy — harder hits, half the cooldown. Real polar bears hunt you if you're a fox. |
| Turtle | ✅ | Fast in water (never runs out of air), slow on land. Left-click: its call. Hold right-click on land: dig a nest and lay eggs — the longer you hold, the more eggs (up to 4); long cooldown. They hatch on sand, like the real ones. |
| Frog | ✅ | Big jumps, soft landings, lured by slimeballs, no bite. Right-click: Eat (11s) — its tongue swallows any slime or magma cube, or any mob at 3 hearts or less, whole (a magma cube leaves a froglight). |
| Tadpole | ✅ | No moves: a little fish — flops and dries out on land; lured by slimeballs. Axolotls hunt it. |
| Armadillo | ✅ | An armadillo's pace; lured by spider eyes; no bite. Sheds a scute every 5-10 minutes, and a brush takes one. Anything that would scare it glows for you, with a warning. Hold right-click: curl up (no cooldown) — rooted, damage cut and little knockback as vanilla's, peeking out now and then, with vanilla's animations. |
| Bee | ✅ | Slow flight; drawn by flowers. Left-click stings (Poison II) — once: then it dies before long. Right-click a flower for nectar; right-click a hive to go inside (and again to come out); going in with nectar adds honey. Hit, a nearby bee angry, or your hive broken: you see who did it and get a burst of speed. |
| Allay | ✅ | Free flight (no sprint-fly); can't hit anything. Right-click: take one random item from a container you're looking at (not ender chests), else pick up the nearest ground item into its hands (shown in first person, and held out on the model like a real allay's), or drop what it's holding. Only while a jukebox plays nearby (the HUD shows it then), hold right-click: Mitosis — split off a real allay copy of yourself, carrying a copy of your item (25s). |
| Sniffer | ✅ | Slow and lumbering; lured by torchflower seeds. Left-click: sniff (at a block) or call (at the air). Hold right-click: dig — vanilla's whole dig animation and dust, and up comes an ancient seed (40s cooldown). |
| Strider | ✅ | No move. Walks on lava (fast there), fire-proof; out of the warm it shivers (the cold look) and slows. Water and rain hurt it. Saddle it and a rider with a warped fungus on a stick steers and boosts it, like a pig — the only mount that crosses lava. Lured by warped fungus. |
| Wandering Trader | ✅ | Treated like a villager: Hmm, others open your shop (your real trades; emeralds go to your till, paid out on leaving), hunted by zombies/illagers + defended by golems, panics when hurt. |

## Water
| Mob | Status | Idea / notes |
|---|---|---|
| Cod | ✅ | Leap / dash (4s). Flops and dries out on land. |
| Salmon | ✅ | As cod. |
| Pufferfish | ✅ | As cod (tap right-click: leap); hits poison. **Hold right-click: puff up** like a real one — half, then full — and anything touching you is stung (damage + poison by how puffed you are). |
| Tropical Fish | ✅ | As cod (keeps its pattern/colours). |
| Dolphin | ✅ | Very fast swimmer that breathes air (16× air). Inspire (5s): Dolphin's Grace to nearby players + water mobs. Eats fish (swim into it / be fed). No treasure-finding. Dries out on land. |
| Squid | ✅ | Slow swimmer, dies on land. Flee: dash + ink (blinds). |
| Glow Squid | ✅ | As squid; ink blinds AND makes them glow. |
| Axolotl | ✅ | Hold: play dead as long as you like (12s cooldown after) — lie still, regen fast, mobs drop aggro. Bites for 2. |
| Guardian | ✅ | Hold: a laser you aim yourself (forgiving aim) — rapid damage ticks, the view closing in as it builds to a hard-hitting burst (knockback, a crack and a flash, a kick to your view); rallies guardians on hit. Swims fast; bounces about on land. Spikes out (thorns) when still. |
| Elder Guardian | ✅ | As guardian, a much bigger, wider burst; left-click Call (13.5s) raises guardians around you; curses nearby players with Mining Fatigue (and the face) every minute. |

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
| Phantom | ✅ | Flies; bites. Belt (scroll): Dive (tap — bends onto what's ahead, hits hard, marks it: green to you, and the phantoms near keep swooping at it), Teleport (hold 15s, still and shaking — you and the flock appear in the sky over the nearest sleepless player; 5 min, shared), Call (2 phantoms if none near, 1 if a few). The sleepless are green to you and take more damage. |
| Giant | ❌ | Unused vanilla mob. |

## Other hostiles (MONSTER)
| Mob | Status | Idea / notes |
|---|---|---|
| Creeper | ✅ | Hold: Explode. Charged via lightning / Comic Relief. |
| Spider | ✅ | Wall-climb (Spider blessing); swing = pounce, swing mid-jump = bigger air pounce — the leap bites one thing per pounce (a swing that already landed counts). |
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
| Vindicator | ✅ | A brute: big speed, big hits, a quicker swing (on a real attack cooldown); keeps its axe's enchantments. **Hold right-click = a committed DbD-style Lunge** (charge for bonus damage; a miss costs a 12-tick recovery). Named "Johnny": attacks anything nearby on its own, with bonus damage, speed, swing speed, extra hit sparks — and an enhanced lunge. |
| Evoker | ✅ | Scroll to pick a spell from its belt — fang ring, fang line, or 3 vexes (13.5s) that hunt whoever you last hit; **hold right-click to charge it** (arms up, vanilla's timings: 1s, a wololo 2s; slowed while charging) and let go to cast. At a sheep — wololo; at a villager — turn it into a witch. Left-click: Rallying Horn (10s) — sounds like a raid's horn; every illager and witch nearby turns on your target, sped up. No melee. |
| Illusioner | ❌ | Unused vanilla mob. |
| Vex | ✅ | Temporary (30s timer — configurable, or off). Dies when you leave it. Free flight (no sprint-fly), phasing through blocks up to obsidian-hard. **Every swing: a vex charge** (2s) — a giggle, then a committed, barely-steerable dash down your aim with the angry red look, its only way to hit (9). Right-click: Laugh. |
| Ravager | ✅ | Fast and big. Hold right-click: Roar (6s) — slowed, view zooms and rattles as it charges, then vanilla's roar, much harder: big damage and knockback to everything close (raiders are never hurt). Left-click: a delayed head-bite (12) that tosses the victim and sets nearby illagers on it. Tramples leaves. |
| Piglin | ✅ | Keeps its sword (melee) or crossbow (left-click fires); both show in first person. Right-click: Rally — nearby piglins and brutes go for your target. Kill a hoglin: hold right-click to dance (10s, Regeneration V). Zombifies outside the nether. Scared off by soul fire and zombified piglins. Can't hurt anyone in gold until they hit you (swings miss, bolts pass through). Someone opening a chest or breaking gold nearby: an alert and a trail to them only you see. Given gold, you must barter — rooted, admiring it, until the loot comes out (being hit cancels it). |
| Piglin Brute | ✅ | The vindicator's kit: big hits with its golden axe (enchantments kept), **hold right-click = the committed Lunge** (same settings and miss recovery). **Tap right-click: Rally** — nearby piglins and brutes go for your target (own cooldown). Told when someone opens a chest or breaks gold nearby, with a trail to them. Zombifies outside the nether. Ignores gold, fears nothing (as the real brute). |
| Hoglin | ✅ | Lone brute: tusk toss, pushed away by warped fungus, babies bolt when hurt, zombifies outside the Nether. Lunge (9s): stop, charge with limited turning, first hit takes 10 and goes flying. |
| Shulker | ✅ | Doesn't walk; sits on the grid, head turning. Left-click: a bullet (2s) — hold for a volley of 5 at your target and those near it (stronger levitation). Right-click belt (scroll): Teleport (2 charges, 9s each; the spot and your bullet's target shown to you) or Shell (hold; 10s max). |
| Breeze | ✅ | Free flight like the blaze; no melee; hostile mobs that come close are pushed away. Left-click: tap a wind charge, hold a gale. Right-click: a short dash (5s). Deflects projectiles. |
| Guardian / Elder Guardian | — | see Water. |
| Warden | ✅ | The headline. Possessing one is a struggle that ends in its roar. Blind: dark blue-black fog that opens only when something's close, a black-and-blue sight shader; it SEES sounds (footprints, ripples, trails, through walls). Sounds give points (subtle 1, obvious 2, then a 1-5s grace per action; sculk sensors +1; shriekers, hitting it or walking into it mark at once); 3 marks a creature red at any distance with a countdown on their screen (draining faster out of sight). Left-click: its swing (shield-breaking, more on the marked). Scroll belt, hold right-click: Shriek (sonic boom, 5s), Sniff (a widening cone that shows scent from everyone in it as you hold, a point to all caught, 12s), Dig (burrow, tunnel about fast and unseen, emerge; 40s). Darkness pulse. Every vanilla animation. |

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
