# The Living War: asset prompts

The gaps the Living War's first phase ships stand-ins for (plan W9-W11, section 5). Each entry says
what stands in now, where the finished file goes, and a prompt to generate or brief it.

## Sound

Deliver as 16-bit OGG, mono, 44.1 kHz, loudness about -18 LUFS for loops and -16 LUFS for one-shots.
Loops must loop seamlessly. Drop them in `assets/sounds/war/` under the names given and they are
picked up with no code change, except where noted.

| File | Stands in now | Prompt |
| :-- | :-- | :-- |
| `crows_loop.ogg` | nothing | "Carrion crows over a battlefield after the fighting: a dozen crows cawing and squabbling at varying distances, wingbeats, no wind, no voices, 30-second seamless loop, dry outdoor field recording." Wire-up: aftermath fields (EncounterDirector `lay`). |
| `march_loop.ogg` | `war_drums_loop` | "A column of 15 armoured soldiers marching on dirt in step: heavy boots, chain mail and plate jingling, scabbards knocking, a creaking standard pole, no drums, no voices, 20-second seamless loop, passing at medium distance." Wire-up: a new bed for columns, alongside the drums (WarSoundscape `Bed`). |
| `camp_loop.ogg` | nothing | "A war camp at night: crackling campfires, low indistinct murmur of soldiers, a whetstone on a blade, a tent flap in the wind, the odd metal clank, 40-second seamless loop, no music." Wire-up: a bed for camp chunks. |
| `soldier_cries_1..4.ogg` | `monster_grunt_light` | "Distant soldiers in a melee: a single short pained cry, a battle shout, an order bellowed ('Hold the line!' unintelligible at distance), heard from 30 metres, slightly muffled. Four separate 1-2 second one-shots." Wire-up: the next-door one-shots (WarSoundscape `fight`). |
| `demon_screams_1..3.ogg` | `monster_roar` | "Inhuman screams of demonic soldiers dying in battle, guttural and wet, not animal, 1-2 seconds each, three variations." Replaces the dropped CC BY-NC `614068`. |
| `dust_fall.ogg` | nothing | "Grit and small stones trickling from a cave ceiling after a distant impact, close, 2 seconds." Wire-up: underground tremor (GameScreen `hearTheWar`). |

**Re-extract the SFX bundle.** `docs/game_assets/bt25_ultimatesfxforgames_softwarebundle` holds only
`__MACOSX` stubs for Medieval Fighting, Monsters Boss & Zombies, Foley, Ambience & SFX and Horror
Screamers. The full bundle very likely covers most of the table above.

## Art

| Asset | Stands in now | Prompt |
| :-- | :-- | :-- |
| `images/props/crow.png` (prop `crow`) | nothing | "Pixel-art sprite, 64x64, transparent background, a black carrion crow standing on the ground pecking, side view, dark fantasy palette with a dull red rim light, matching a gritty 16-bit dungeon crawler." Add to `props.json` as passable, then to `EncounterDirector.FIELD`. |
| `images/props/scorched_ground.png` (prop `scorched_ground`) | blood decals | "Pixel-art floor decal, 128x64, transparent background, a patch of burnt earth with ash, embers and charred grass, seen at a low angle, dark fantasy palette." Passable, flat (scaleY ~0.3). |
| `images/props/corpse_pile.png` (prop `corpse_pile`) | bone and skull piles | "Pixel-art sprite, 96x64, transparent background, a heap of fallen soldiers in dark mismatched armour with broken spears and a torn banner, no gore beyond dark stains, dark fantasy palette." |
