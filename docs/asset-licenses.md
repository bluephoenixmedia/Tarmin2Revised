# Third-Party Asset Provenance & Licensing

> Records the packs under `docs/game_assets/`, which is gitignored (the raw
> packs are far too large to track). This file lives outside that directory so
> the provenance record itself is version-controlled.

**Last audited**: September 2026

Most packs in this directory ship without a licence file inside the download.
That absence is **not** a licensing gap: these packs were acquired through
Humble Bundle purchases, which granted the downloads and the commercial-use
entitlement. Entitlement lives in the purchase account, not in the zip.

This file records that provenance so a future audit of the directory alone does
not wrongly conclude the assets are unlicensed.

---

## Purchased via Humble Bundle (commercial use licensed)

Entitlement documented by purchase; no licence file ships in the download.

| Pack | Contents |
| :--- | :--- |
| `POLYGON_Goblin_War_Camp_SourceFiles_v3` | Synty - camp, gibbets, bones, siege props |
| `POLYGON_Viking_Realm_SourceFiles_v3` | Synty - halls, thrones, runestones, shields |
| `POLYGON_Generic_SourceFiles_v3` | Synty - modular base, ivy, vines, decals |
| `POLYGON_Adventure_Pack_SourceFiles_v6` | Synty - village, nature, adventurers |
| `POLYGON_NatureBiomes_AlpineMountain_SourceFiles_v3` | Synty - stalactites, ice, fog gradients |
| `POLYGON_Particle_FX_SourceFiles_v2` | Synty - particle textures and FX meshes |
| `POLYGON_Prototype_SourceFiles_v4` | Synty - greybox kit |
| `SIMPLE_Props_Items_Icons_Source_Files_v2` | Synty - items and icons |
| `SIMPLE_Buildings_SourceFiles` | Synty - modern buildings (unused) |
| `INTERFACE_Apocalypse_HUD_Source_Sprites_v3` | Synty - HUD sprites |
| `SIDEKICK_Goblin_Fighters_*.unitypackage` | Synty - modular goblins (needs Unity to extract) |
| `DungeonProps-1.4.5` | Cartoon dungeon props and tileset |
| `DELUXE_PNG` | Battler creatures #041-#060, front and side |
| `MP1` | Battler creatures #001-#040, front and side |
| `RPG_Items_Retro_Itchio` | 596 retro 16x16 item icons |
| `Golems_Free_Version`, `Mecha-stone Golem 0.1` | Pixel golem sprites |
| `Undead executioner`, `Hobgoblin`, `Rat` | Pixel creature sprites |
| `mino_v1.1_free`, `boss_demon_slime_FREE_v1.0`, `free_cthulu_v1` | Pixel creature sprites |
| `apprenticesstudy`, `fantasy-camp-scatter-terrain-*` | STL scatter terrain |
| `x768p_HQ_Uncompressed_Frames` | High-res rendered warrior frames |
| `Free Characters with Animations For FPS game` | 64x64 front-facing FPS enemies |
| `monsters/Bosses & Minions Pack_3.5` | Boss + 4-minion battler groups |

---

## Explicit licences shipped in-pack

| Pack | Terms | Attribution required |
| :--- | :--- | :--- |
| `Kenney Game Assets 1 version 42` | CC0 | No (credit "Kenney / kenney.nl" appreciated) |
| `EVil Wizard 2` | CC0 | No |
| `Free Pixel Effects Pack` | Public domain | No |
| `BearFX Explosions` | Royalty-free, commercial OK, no resale | No |
| `Super Pixel Effects Gigapack (Free Version) v2.0.0` | Commercial OK, no redistribution | **Yes** - "Will Tice / unTied Games" |
| `HAS IconPack (v.1.2)` | Modify and use commercially | **Yes** - "Aleksandr Makarov" |
| `TT_Battlers_3_Wild_Beasts` | Free and commercial, no resale | **Yes** - "Megatiles; Lunatic Red, Inazuma" |
| `monsters/Bringer-Of-Death` | Personal and commercial, no resale | Appreciated - "Clembod" |
| `waechter-19` | Free to use and edit | **Yes** - "Nekomata - created by @Jitsu" |
| `waechter-20` | Free, commercial allowed | **Yes** - "TheRealFusion" |
| `explosion pack 1` | See `public-license.pdf` in-pack | Verify before shipping |

### Credits block required at ship

```
Art assets:
  Kenney (kenney.nl)
  Super Pixel Effects Gigapack - Will Tice / unTied Games
  HAS IconPack - Aleksandr Makarov
  TT Battlers 3: Wild Beasts - Megatiles (Lunatic Red, Inazuma)
  Bringer of Death - Clembod
  Nekomata - @Jitsu
  waechter-20 - TheRealFusion
```

---

## NOT covered - do not ship

| Pack | Reason |
| :--- | :--- |
| `monsters/Battra` | Source-engine (`.mdl`/`.vtf`) fan work of a **Toho-owned** kaiju. No bundle covers this; it is third-party IP regardless of how it was obtained. Unusable on both technical and IP grounds. **Delete.** |
| `Sci-Fi SFX Pack` | The bundled `README.txt` forbids use in "anything that utilizes AI-generated content in any capacity." Also wrong genre. Excluded by the author's own stated terms. |

---

## In-repo art (not third-party)

`docs/game_assets/old/` is this project's own previous monster sprite set -
~1545x1536 front-facing renders including `tarmin.png` and `tarmin_god.png`,
alongside a libGDX TexturePacker `pack.json`. No third-party licence applies.

---

## Housekeeping

- `POLYGON_Generic_SourceFiles_v3`, `... (1)` and `... (2)` are byte-identical
  duplicates (~584 files each). Two can be deleted.
- `__MACOSX/` AppleDouble trees in `DungeonProps-1.4.5` and `explosion pack 1`
  are dead weight.
- 18 files in `HAS IconPack` carry broken `.png1`-`.png9` extensions.
