# Bulk test list — built but not yet tested in-game

Everything here compiled and built, but none of it has been run. Oliver is testing it in one go at the end.
Tick each box as it passes. Anything that fails: note it under the item, then fix it and re-test.

Debug: `/bewitch debug force <effect> [targets] [arg]` (DESIGN.md §16.2b). For the puppeteer, `force … puppeteer @s`
possesses the nearest possessable mob.

## 1. Structure (should behave exactly as before)
- [ ] **Effect hooks** (curses and blessings moved out of the two big event handlers). Spot-check:
  - [ ] Immortality and Last Stand deaths (Immortality wins if you have both)
  - [ ] Thick Skinned / Twist of Fate with Glass Cannon
  - [ ] Bedrock Moment's Bluetooth (a held-back hit: nothing reacts until it lands)
  - [ ] Gluttony + Munchies eating together
  - [ ] Laugh Track + Yap (comedic timing)
- [ ] **Effect state** (`EffectState`): Chat's score survives death, relogging and a world restart; cooldowns carry over
- [ ] **Puppeteer package split**:
  - [ ] possess and leave a few mobs
  - [ ] damage in and out; a chicken's or cat's fall
  - [ ] a held move and a lunge
  - [ ] HUD lines (evoker, piglin, enderman)
  - [ ] Johnny's bonuses now apply (they never did before)

## 2. General
- [ ] **Imposter**: attack a mob (a swing, a shot or a special move) — every mob of that type within 16 turns on you
- [ ] **Stepping out never leaves you in a block**: leave an endermite mid-burrow, a vex inside a wall, a burrowed
      warden; log out and back in mid-burrow too. A big mob (a warden) is put back where it fits
- [ ] The possession bar fills right for the longer warden possession too
- [ ] A frog's tongue and leap now play through (its pose is only set on a change)
- [ ] The evoker's belt still scrolls
- [ ] Display weapons carry no stats: the vindicator and piglin brute have a **real attack cooldown** (no spam)

## 3. Changed mobs
- [ ] **Piglin brute** (new): hold for the Lunge, tap to Rally, gold/chest alerts, zombifies outside the Nether, ignores gold
- [ ] **Evoker**:
  - [ ] hold to charge (arms up), release when full; fangs/vexes take 1s, wololo 2s; releasing early fizzles
  - [ ] slowed while charging; arms stay up after the cast
  - [ ] **horn sounds like a raid's** (64 blocks, from its direction)
- [ ] **Ocelot**: no particles when fleeing
- [ ] **Guardian / Elder**: zooms in toward the burst; the burst knocks back, flashes, and kicks your view
- [ ] **Spider / Cave spider / Wolf**: one victim per pounce, including the mid-air pounce
- [ ] **Wolf**: Maul is held right-click, only while frenzied: slowed and zoomed while charging, the lunge actually
      flies, then the scrap
- [ ] **Vex**:
  - [ ] every swing is a real charge with the red texture, and barely steers
  - [ ] it dies when you leave it
  - [ ] its timer can be set to 0 (off)
- [ ] **Allay**: the model holds its item out (arms up)
- [ ] **Ravager**: slowed while charging the roar
- [ ] **Pufferfish**: tap to leap, hold to puff up (half, then full); touching it stings and poisons; it deflates in steps
- [ ] **Pig**:
  - [ ] a saddle goes on and shows
  - [ ] others ride it; a carrot on a stick steers it
  - [ ] right-clicking the stick boosts it, and the stick wears

## 4. New mobs
- [ ] **Panda**:
  - [ ] left-click sneezes (wind-up, the cloud, maybe a slimeball, nearby pandas jump) and recoils you
  - [ ] right-click rolls; both use vanilla's animations; drawn by bamboo
- [ ] **Strider**:
  - [ ] walks on lava (fast) and floats up out of it; fire-proof
  - [ ] cold: shivers, slows, purple look; water and rain hurt it
  - [ ] saddle, then a warped fungus on a stick steers and boosts; the only mount across lava
- [ ] **Parrot**:
  - [ ] slow flight, slow fall, no fall damage
  - [ ] with music near, hold to dance (regeneration)
  - [ ] tap to perch on a player's or humanoid's shoulder; you hop off when they jump, fall, swim or get hit
  - [ ] tap a mob to mimic it, even through walls
  - [ ] mobs glow through walls for you alone
  - [ ] left-click squawks; a cookie kills it
- [ ] **Polar bear**:
  - [ ] swipe with a cooldown; the model rears up
  - [ ] cubs can't attack
  - [ ] fast, swims fast, never freezes
  - [ ] right-click growls
  - [ ] a hurt cub nearby: a trail to the attacker and a frenzy
  - [ ] real bears hunt a fox puppet and defend a cub puppet
- [ ] **Turtle**:
  - [ ] fast in water, never drowns; slow on land
  - [ ] left-click calls
  - [ ] hold (on land, adult) to dig and lay eggs, more the longer you hold; long cooldown; they hatch on sand
- [ ] **Armadillo**:
  - [ ] armadillo pace; drawn by spider eyes
  - [ ] sheds scutes every 5-10 min; a brush takes one
  - [ ] threats (undead, sprinting or riding players) glow for you, with a warning in the corner
  - [ ] hold to curl up: rooted, damage cut, little knockback, peeks out now and then, vanilla's roll/unroll animations
- [ ] **Bee**:
  - [ ] slow flight; drawn by flowers
  - [ ] left-click stings (Poison II), once: the stinger's gone and it dies before long
  - [ ] right-click a flower: nectar (the model shows pollen)
  - [ ] right-click a hive or nest: go inside (hidden, untouchable); right-click to come out of its front
  - [ ] going in with nectar adds honey
  - [ ] roused when hit, when a nearby bee is angry, or when your hive is broken: a trail to the culprit, a speed burst,
        the angry look; real bees take your side
- [ ] **Tadpole**: no moves; flops and dries out on land; drawn by slimeballs; axolotls hunt it (and fish puppets)
- [ ] **Frog**:
  - [ ] big jumps, soft landings; drawn by slimeballs; no bite
  - [ ] right-click Eat (11s): the tongue grabs a slime or magma cube of any size (no splitting), or any mob at 3 hearts
        or less, and swallows it
  - [ ] a magma cube leaves a froglight of your frog's colour
- [ ] **Sniffer**:
  - [ ] slow; drawn by torchflower seeds
  - [ ] left-click a block: sniff; left-click the air: call (both animated, no cooldown)
  - [ ] hold right-click over grass or dirt: the dig animation and dust, then the seed comes up, then it rises and is happy
  - [ ] letting go early cancels with no cooldown; the seed puts it on a 40s cooldown
  - [ ] it won't dig the same spot twice; a baby can't dig
- [ ] **Shulker**:
  - [ ] it can't walk
  - [ ] left-click (2s): a bullet that homes on your target (levitation), or flies straight; fizzles past its max range (32)
  - [ ] hold right-click: shell shut (armour, no knockback, projectiles glance off), 10s max then a 4s cooldown
  - [ ] tap right-click: teleport to the block you're looking at (8s), and getting hit shortens the cooldown
  - [ ] the model's shell opens and closes
- [ ] **Warden** — possessing:
  - [ ] it struggles: heartbeat speeding up, tendrils clicking faster, head thrashing, sculk souls, Darkness on everyone
        near, then its roar; the bar is longer (7s); `puppeteerWardenStruggle = false` makes it a normal possession
  - [ ] stepping out: the warden stays (never digs away or despawns) and isn't instantly furious with you
- [ ] **Warden** — blind:
  - [ ] a dark haze: the world's a dim outline you can find your way by, no sky; it clears when something's within 12
        blocks; creatures further out aren't drawn at all unless marked or sniffed (their footprints still are)
  - [ ] the black-and-blue shader: only the faintest glint at the very corners on each heartbeat, a slight ripple; red
        stays red
  - [ ] with post shaders off: fog only, no errors; leaving restores your view (and any other shader comes back)
  - [ ] you hear your own heartbeat, faster as you get angrier; Darkness and Blindness don't affect you
- [ ] **Warden** — hearing:
  - [ ] footsteps leave glowing footprints (left, right) that fade; other sounds make a ring; only sounds that gave points
        send a bead to you; all visible through walls, and calmer than before (`wardenSenseIntensity` in client.toml)
  - [ ] walking/landing gives 1 point, sprinting/eating/shooting/breaking/chests 2; then that same action gives nothing
        for 1-5s (a different action still does)
  - [ ] setting off a sculk sensor near the warden gives a point on top; a shrieker going off marks everyone near it
  - [ ] hitting the warden, or walking into it, marks you at once
  - [ ] a mark drains faster when you're out of the warden's line of sight
  - [ ] sneaking makes no sound; wool blocks it; a thrown snowball's landing shows but blames no one
  - [ ] pips over the noticed; points fade if they stay quiet
  - [ ] 3 points marks: red glow, red halo and column, a red pillar when far away; it roars (not more than every 20s)
  - [ ] the marked player: a sting, red flash, heartbeat, red edges, a countdown that grows (and shakes) when they make
        noise; the noticed see pips; it all clears when the mark runs out or the warden leaves
  - [ ] its tendrils twitch and it makes its listening sound when it hears someone
- [ ] **Warden** — anything you hit (swing or boom) is marked at once
- [ ] **Warden** — left-click swing: vanilla's swing animation, big damage on a cooldown, knockback, disables a raised
      shield; more damage and knockback on the marked
- [ ] **Warden** — the belt (scroll; not mid-charge):
  - [ ] Shriek: hold — its chest swells, slight slow and zoom, boom at 1.7s down your aim through walls, hits all on the
        line, view kicks; extra on the marked; letting go early fizzles (no cooldown); 5s cooldown
  - [ ] Sniff: hold — its sniff animation, the cone shows and widens, and anyone inside it already sends scent drifting
        to you; let go: the sweep, everyone in it (through walls) glows teal with a scent thread back to you and gains a
        point; full at 2s; 12s cooldown
  - [ ] sniffing someone already marked puts their mark back to full, however quiet they've been
  - [ ] Dig: hold 5s — rooted, untouchable, the dig animation and dust; in first person you look down, the view wobbles
        and your eyes sink to the floor (nothing covers the screen); letting go early climbs out (5s cooldown)
  - [ ] dig into water or lava, or fall a few blocks while digging or burrowed: thrown straight out — no climb, no points
        to anyone, the full cooldown
  - [ ] after digging down and back up, the Shriek and Sniff still go exactly where you aim
  - [ ] burrowed: invisible (no shadow or name), untouchable, view at the floor but seeing much further, fast, dust and
        digging sounds where you go (quieter when still), still hearing; players stop at 2 points; right-click (or 30s)
        emerges
  - [ ] burrowed you're a block wide and high: you slip through 1-block gaps; digging out under a low ceiling moves you
        to the nearest spot you fit and you climb out there (never stuck hidden)
  - [ ] emerging: your view lifts back up as you rise; the animation and dust, rooted and untouchable; everyone within 8 gains a point; Darkness; 40s cooldown
- [ ] **Warden** — Darkness every 6s to survival players within 30 (not while burrowed); heavy footsteps; mood sounds
- [ ] **Warden** — others see every animation: swing, boom, roar, sniff, dig, emerge, tendrils, heart glow and heartbeat

## 5. Not puppeteer
- [ ] **Aim Assist** (new blessing, Crossbow of any charge, power 70) — `/bewitch debug force aim_assist` once applied:
  - [ ] crosshair on or near a hostile mob / player: your mouse slows down over it
  - [ ] it strafes or jumps (or you strafe past it): your aim moves with it; standing still, nothing pulls you
  - [ ] turning hard away lets go at once — it never locks on
  - [ ] works up close with a sword as well as at range with a bow or crossbow; far targets have a bigger zone
  - [ ] cows / calm wolves / golems aren't helped until you hit one (then for 10s); angry neutral mobs always are
  - [ ] nothing through walls; never your own pets or teammates

## 6. Changes after the first warden test (2026-10-11)
- [ ] **Flying**: bat, phantom, ghast, vex, allay and bee can't land and walk; parrot, bee, ghast, blaze, breeze, vex and
      allay can't sprint-fly however you sprint (double-tap, toggle, held key)
- [ ] **Young follow**: a baby cow/pig/sheep... near your grown animal puppet follows you (not when a real adult's nearer);
      cod/salmon/tropical fish school after a fish puppet
- [ ] **Grudges**: hit a mob as a puppet, step out: it goes for the mob you left, not you
- [ ] **Stepping out**: 4s of invisibility
- [ ] **Health**: a big puppet (over 40 max health) shows one heart and a number, no overlap with its name
- [ ] **Crouch + right-click leaves** even while rooted (shulker, curled armadillo, digging sniffer/warden)
- [ ] **Parrot**: crouching sits; its mob glows are only on your screen (check from a second player)
- [ ] **Bee**: after stinging it visibly weakens and dies after ~15s
- [ ] **Frog**: eats a silverfish / chicken at full health, and any mob at 3 hearts or less; small targets are easy to hit
- [ ] **Sniffer**: left-click at the air — a loud call; nearby sniffers answer and walk over
- [ ] **Turtle**: laying eggs, the model paddles the sand like vanilla's
- [ ] **Shulker**:
  - [ ] the model stays square on its block; only the head turns as you look around
  - [ ] scroll switches Teleport / Shell (action bar); Teleport is a tap, Shell a hold
  - [ ] two teleport charges, each back after 9s (hits shorten the wait); a faint shimmer marks the landing face
  - [ ] whoever your bullet would lock onto is outlined purple and sparkles (only on your screen)
  - [ ] tap left-click: a bullet; hold: the lid opens, sparks draw in, view narrows — let go full for 5 bullets at the
        target and those near it, Levitation II; 10s cooldown
- [ ] **Breeze**: tap left-click a wind charge, hold for the gale; right-click a quick slide (2s; see section 7)

## 7. Changes after the second round (2026-10-11)
- [ ] **Hawk Guy**: shots no longer bury themselves in a wall to reach someone behind cover
- [ ] **Amethyst Bell**: a swapped effect keeps the old one's time; one under 10 min sometimes becomes 30-45 min
- [ ] **Every puppet**: footsteps are the mob's (silent for fliers / fish / slimes); no sprinting dust (disguises too); no
      flames on the hidden player; fire-proof puppets have no fire over the screen
- [ ] **Two players can't possess the same mob**; possess a mob right after someone stepped out of it, then step out:
      it walks about normally (never left without AI); restart mid-possession: nothing stays frozen
- [ ] **Shulker**: teleport onto a wall / ceiling and it clings there (model too); look at the sky: it goes as far as it can;
      fits through 1-block gaps
- [ ] **Breeze**: hold jump to draw breath, let go to leap high (animated); hovering while charging a gale; right-click
      slides quickly (2s)
- [ ] **Guardians**: a faint aim assist while beaming; a calmer burst; elder Call brings 2 / 1-2 / 1; guardians drift after
      the elder; an elder beaming in the air hovers; guardians' beams hurt the elder's mark more; hitting a guardian
      while a rally/mark is on doesn't turn the rest; possessing an elder: its beam on you, thrashing, then its curse
- [ ] **Snow golem**: every snowball hurts; the blizzard's bursts are wider and stronger
- [ ] **Vindicator**: a longer lunge, 3s cooldown, stops on the first thing with a swing; Johnny: no cooldown, further;
      arms folded when idle
- [ ] **Evoker**: spells fire the moment they're charged; Vanish (invisible + fast, +4s to all cooldowns); vexes take
      longer to charge, fangs a touch quicker; nearby witches throw you potions; a thin horn calls pillagers
      (sometimes a vindicator)
- [ ] **Strider**: flowing lava doesn't slow or push it
- [ ] **Warden**: visible in third person after digging back up; Roar on the belt (3s wind-up, slowed; a point to all who
      see it; a burst of speed)
- [ ] **Enderman**: pick up a full chest (crouch + right-click), put it down elsewhere — items intact; die / step out /
      log out holding it: it's put down nearby; teleport through a wall when aiming at it; the landing spot shimmers;
      starers are purple, and landing near one is free
- [ ] **Phantom**: belt — Dive (easy to land, strong; the phantoms around dive too), Teleport (hold: the sky over the
      nearest sleepless player, the flock with you; 5 min shared), Call (2 / 1 / 0); the sleepless are green and take more

## 8. Changes after the third round (2026-10-13)
- [ ] **Holy water**: under it, a thicker white fog and a soft white glow over the view (flowing water now matches still)
- [ ] **Cooldown after leaving a puppet**: ~50s after a weak mob, more for big ones (110s after a warden / elder / ravager)
- [ ] **No swing spam**: holding right-click as any puppet doesn't flail the arm (vindicator, piglin brute — either hand —,
      witch: no drinking animation either)
- [ ] **Inventory**: possess, crash / kill the server mid-possession, log back in: your inventory is back, no witch
      potions left over; nothing a puppet holds ever drops on the ground
- [ ] **Phantom**: Dive a bit slower and longer, 6s cooldown, slower climbing; what you dive at is green for you and the
      phantoms near keep swooping at it; Teleport: a 15s hold — you hang still (model shaking for others, your view
      trembling), souls round you and round your quarry, then the sky over them; the sleepless are green
- [ ] **Evoker**: Vanish hides the evoker model itself; the horn's pillagers turn up all around, further off
- [ ] **Witch**: a bit more zoom charging a throw; once it's a lingering throw, a bubble and a faint coloured wisp at its
      hand; lingering cooldown a little shorter
- [ ] **Warden**: a sniff let go early only shows them briefly (a full one still a good while)
- [ ] **Vindicator / piglin brute**: longer charge, much slower while charging and just after; the brute's lunge is slower
      but longer
- [ ] **Elder guardian**: possessing one: it thrashes in place (no spinning off its axis); you don't get the curse face
