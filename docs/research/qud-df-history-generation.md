# Research: how Caves of Qud and Dwarf Fortress generate histories

> For map #265 (the World Log and the myth of the Eternal Champion), ticket #274.
> Local code read on `develop` at `917e01d8`, 2026-10-21. Every external claim cites its source.
> Paths under `core/` are shortened from `core/src/main/java/com/bpm/minotaur/`.

## Summary

The two games take opposite approaches, and Tarmin2 already uses both.

- **Dwarf Fortress simulates.** It runs history forward year by year, tracks a small set of people as *historical figures*, records what they do as events, and lets you browse the result in Legends mode. Player fortresses and adventurers are written into the same record.
- **Caves of Qud writes accounts.** It picks a sultan's life events at random, invents each event's cause afterwards from the sultan's current state, and renders each event as a short text (a *gospel*) through a Tracery-like grammar. It also tells the same event a second time, in a more flattering voice, on tomb murals.

Tarmin2's Houses already pair a DF-style causal simulator (`HistorySimulator`: traits, grudges, `causeEventId`, casus belli) with a Qud-style text layer (`ChronicleGrammar`: `#symbol#` expansion, doctrine voices, FOR / AGAINST / NEUTRAL tellers). The grammar's two-sided telling is the feature Qud's own paper lists as future work.

**The gap is the player.** An Incarnation enters the history only as an ordinal: "the 7th seeker". It has no name, no origin, no record of its own and no epithet, and its bones say "Fallen Hero". The recommendations below fill that gap with techniques from both games, and they keep ADR 0004's replay rule intact.

---

## (a) Caves of Qud

Primary source: Jason Grinblat and C. Brian Bucklew, *Subverting Historical Cause & Effect: Generation of Mythic Biographies in Caves of Qud*, FDG '17, DOI 10.1145/3102071.3110574 ([PDF on freeholdgames.com](https://www.freeholdgames.com/papers/Generation_of_mythic_biographies_in_Cavesofqud.pdf)). It is cited as "Paper §n" below.

### History as artifact, not process

- The designers distinguish "history as a process" from "history as an artifact". They chose to "generate history in order to simulate historical accounts", accounts that "would originate from inherently limited perspectives and represent particular points of view" (Paper §1, §3).
- They set this explicitly against Dwarf Fortress, which "allows players to interrupt its simulated history at any point to start play, and so must remain agnostic as to what's past and what's present" (Paper §3).

### The generative model: state machine plus grammar

- Each sultan starts as an entity with "a few core properties (name, pronouns, birth year, birth region, location in birth region, and domain)". The system then "randomly chooses a life event ... from a pool of such events", resolves an outcome, modifies the sultan's properties, and writes a gospel "via a Tracery-like replacement grammar whose rules map sultan properties to text fragments". This repeats for "about twelve more events", followed by a generic death event if needed (Paper §4.2).
- The wiki's breakdown: an origin event ("Born as Heir" or "Found as Babe"), eight random core events from a pool of 17 types, events that make the sultan ruler if none did, region events, then a death event. It adds a "death reversal" if an earlier event already killed the sultan on paper ([Qud wiki: Sultan history](https://wiki.cavesofqud.com/wiki/Sultan_history)).
- At publication there were ten domains and nineteen events, and the system "was designed with this modularity in mind" (Paper §6).

### Ex post facto causality, the core trick

- "There's no logic behind the choice of events. ... Events themselves are chosen at random, though their gospels often profess causes. These rationalizations are generated inside the events and are mediated by the sultan's state" (Paper §4.3).
- The worked example is the template `Acting against #injustice#, #sultanName# led an army to the gates of #location#.`. If the sultan is allied with frogs, `#injustice#` becomes "the persecution of frogs". If no state fits, the event *creates* one by adding frogs to the sultan's allies: "the effect causes the cause" (Paper §4.3).
- Each event lists its candidate properties (allied factions, profession, domains). It rolls one, rerolls if the sultan has no value for it, and falls back to domains, which always have a value (Paper §4.5).

### Domains as narrative glue

- Each sultan gets "an archetypal unit of culture we call a domain", such as glass, ice, stars, might or chance, and gains 0 to 2 more over their life (Paper §3).
- "Almost all gospel patterns include symbols that represent domains", so "the domains act as narrative threads that tie together the events of a sultan's life" (Paper §4.4). Antixerpur's domain is ice, so she is found as a babe with icicles in her hands, fights bans on ice practices, wields a frosty hammer, and leaves sites "devastated by icy winds" (Paper §4.4, Table 1).
- Shared state creates micro-narratives. A gospel in which she allies with cats is followed by one in which she fights "to liberate cats". The battle was not caused by the alliance, but the gospel says it was, and players read it as cause and effect, through apophenia (Paper §4.4).
- Grammar rules live in nested JSON keyed by domain, for example `ice.practices`. A small query language picks from them: `< domains.sultan$domains[random].practices.!random >` (Paper §4.5). Mods can add `HistorySpice.json` files that merge with the base file ([Qud wiki: Version history](https://wiki.cavesofqud.com/wiki/Version_history)).

### Many accounts of one event

- Every event has a gospel. Tomb inscriptions in the Tomb of the Eaters retell the same events in "a different tone", often praising the sultan more and omitting "shortcomings or moral problems". Details can change too: the gospel says Oyuram lost a relic "in a foolhardy bet", and the tomb says he lost it "during the ritual of the Empyrean Vault" ([Sultan history](https://wiki.cavesofqud.com/wiki/Sultan_history)).
- The paper names fully conflicting accounts as future work: "events could generate multiple, conflicting gospels from different perspectives. The history of a city's siege ... might read quite differently from the perspective of the sieged" (Paper §6).

### Discovery and the journal

- Players meet gospels on shrines, paintings and engravings and from NPCs. Each one "is inscribed in a journal and sorted chronologically", and "over time ... a biographical narrative coheres" (Paper §4.1).
- Gospels are traded as currency in the **water ritual**: sharing a secret gains 50 reputation, sharing gossip gains 100, and receiving a secret costs 50 ([Qud wiki: Water ritual](https://wiki.cavesofqud.com/wiki/Water_ritual)). Other sources are trash divining and examining artifacts ([Sultan history](https://wiki.cavesofqud.com/wiki/Sultan_history)).
- History has physical traces. Places sultans visit become historic sites, and a named item lost at a battle is placed at that site with matching properties. Frostycus Catsfriend, for example, is "a hammer that deals frost damage and grants bonus reputation with cats" (Paper §4.6).

### The player's own deeds

- The journal records the player's accomplishments, each with **two texts**: a plain "Accomplishment" and a mythic "Mural Accomplishment" engraved on murals. On starting the game, the plain text is "On the (day) of (month), you arrived at (village)." The mural text is "On the auspicious (day) of (month), =name= arrived in (village) and began their prodigious odyssey through Qud." Visiting a village is recorded plainly as a visit, but the mural says `=name=` *founded* it ([Qud wiki: Journal](https://wiki.cavesofqud.com/wiki/Journal)).
- Death gets a plain entry and no mural version, and one accomplishment reads "You died and were entombed in the burial chamber of Resheph" ([Journal](https://wiki.cavesofqud.com/wiki/Journal)).

### Names

- Sultan names, events and lore are generated per world ([Qud wiki: World generation](https://wiki.cavesofqud.com/wiki/World_generation)). Cognomens such as "Starskipper" come from events and later name cults and relics ([Sultan history](https://wiki.cavesofqud.com/wiki/Sultan_history)). I did not find a primary description of Qud's name-template file (`Naming.xml`), so this note makes no claims about it.

### Talks

Grinblat's GDC 2018 talk "Procedurally Generating History in Caves of Qud" covers the same method, generating events and rationalizing them afterwards, and compares it with Dwarf Fortress and *Epitaph* ([GDC Vault 1024990](https://gdcvault.com/play/1024990/Procedurally-Generating-History-in-Caves)). Bucklew and Grinblat's GDC 2019 talk "End-to-End Procedural Generation in Caves of Qud" covers village histories, cultures and storytelling traditions ([GDC Vault 1025914](https://gdcvault.com/play/1025914)). GDC Vault was down for maintenance during this research, so only the session descriptions, as quoted in search results, were available.

---

## (b) Dwarf Fortress

### World generation runs history forward

- A history-length setting sets how many years are simulated before play. Longer histories mean more sites, figures and events, and fewer surviving megabeasts. The simulation covers civilizations spreading and warring, and figures marrying, having children, dying and succeeding each other, including "succession conflicts and armed usurpations" ([DF wiki: World gen (DF2014)](https://dwarffortresswiki.org/index.php/DF2014:World_gen); [v0.31:World gen](https://dwarffortresswiki.org/index.php/v0.31:World_gen)).
- Tarn Adams calls world generation "probably the most striking" part of the game, and says he could not "think of any other game before 2006 that tries to make a history" ([Kill Screen interview](https://www.killscreen.com/road-two5six-tarn-and-zach-adams/)).
- The design method: Tarn and Zach wrote "stories about events that happened in the game universe", broke them "down into core elements", and implemented the "low-hanging fruit". The rule is "Create actors with motivations, and let them go" ([Game Developer: The Making of Dwarf Fortress](https://gamedeveloper.com/design/interview-the-making-of-dwarf-fortress)).
- They want "expansive logs" rather than high-score lists: "We really like to keep records of plays." An early idea was that an adventurer returning to an abandoned fortress would find "the goblets you've made, and journals it generates" (same interview).

### Historical figures: only the notable are tracked

- Only a small part of the population is tracked in full, as **historical figures** ("histfigs"). These include nobility and their entourage, megabeasts and villains, and "all fortress citizens ... as they appear". Animals that kill a civilization member also become histfigs, as does anyone named that an adventurer meets ([DF wiki: Historical figure](https://dwarffortresswiki.org/index.php/Historical_figure)).
- A histfig carries family links, titles and "heroic feats such as kill lists" (same page).
- **Epithets come from kills.** "After five notable kills, a creature receives an epithet", usually two words and often odd, such as "the Hardy Ring-Cobra of Dashing" ([DF wiki: Name](https://dwarffortresswiki.org/index.php/Name)).

### Legends mode: one record, written by every mode

- Legends mode browses "histories of each civilization and any figure who has lived or died in the generated world", with events grouped "by age", battle reports, duels, artifacts with their kills, and written works ([DF wiki: Legends](https://dwarffortresswiki.org/index.php/Legends)).
- "Any noticeable achievement made by the player in Fortress or Adventurer mode is recorded and is viewable in Legends mode" (same page). The world persists across games, so a world's meadows can "contain the ruins of your previous failures" ([Kill Screen](https://www.killscreen.com/road-two5six-tarn-and-zach-adams/)).
- History can be hidden. With "Reveal all historical events" off, most of it stays hidden "until uncovered by brave adventurers", by talking to people and examining coins, statues, artifacts and books ([Legends](https://dwarffortresswiki.org/index.php/Legends)).
- **Ages** are named from the state of the world. The Age of Myth lasts while living powers and megabeasts number at least two-thirds of their starting count, the Age of Legends while they number at least one-third, and the Age of Heroes while more than three remain. Ages have no gameplay effect and serve only to categorize Legends ([DF wiki: Ages](https://dwarffortresswiki.org/index.php/Ages)).
- Legends can be exported as an XML dump, which the wiki notes is incomplete ([DF wiki: XML dump](https://dwarffortresswiki.org/index.php/XML_dump)).

### How knowledge spreads: rumours

- A rumour is knowledge of a specific historical event. Witnesses start rumours, and listeners pass them on. An adventurer can spread one "by bringing up incidents in conversations, or bragging about some feat in front of witnesses" ([DF wiki: Rumor](https://www.dwarffortresswiki.org/index.php/Rumor)).
- Historical figures learn of sites, people and artifacts by seeing them or by hearing rumours, and they need that knowledge to form opinions of the adventurer ([DF wiki: Knowledge](https://dwarffortresswiki.org/index.php/Knowledge)). Reputation affects the quests offered, which companions join, and whether bandits attack ([DF wiki: Fame](https://dwarffortresswiki.org/index.php/Fame)).

### The dead keep their names

- A dead citizen may return as a ghost if not buried or memorialized, and keeps its name ("Urist McGhost, Ghostly Cheesemaker"). Burying the body or building a memorial puts it to rest. Ghosts can also haunt under false identities, which needs a slab engraved with the true one ([DF wiki: Ghost](https://dwarffortresswiki.org/index.php/Ghost)).
- An engraved memorial slab names a dead histfig. Reading it "reveals information on the arrival and death of the creature" ([DF wiki: Slab](https://dwarffortresswiki.org/index.php/Slab); [Memorial](https://dwarffortresswiki.org/index.php/Memorial)).
- Engravings of Well-crafted quality and up usually depict earlier historical events ([DF wiki: Engraving](https://dwarffortresswiki.org/index.php/Engraving)).

### Names from languages

- `language_words.txt` defines English words and their parts of speech. Each `language_<RACE>.txt` translates them with `T_WORD`, for example `[T_WORD:ABBEY:kulet]`. Words are grouped into **symbols** such as ARTIFICE and FLOWERY, and each civilization's entity raw selects or culls symbols to set the words it favours ([DF wiki: Language words](https://dwarffortresswiki.org/index.php/Language_words.txt); [Language token](https://dwarffortresswiki.org/index.php/Language_token)).
- A first name is one untranslated word of the civilization's language. A last name compounds two words, most often noun-noun, adjective-noun or verb-noun, and is shown translated. Words from the civilization's preferred symbols are more likely ([Name](https://dwarffortresswiki.org/index.php/Name)).

Tarn Adams also wrote chapter 13, "Emergent Narrative in Dwarf Fortress", in *Procedural Storytelling in Game Design* (Short and Adams, eds., CRC Press, 2019) ([publisher](https://www.routledge.com/Procedural-Storytelling-in-Game-Design-1st-Edition/Short-Adams/p/book/9781138595309)). I did not read it, and nothing above relies on it.

---

## (c) What Tarmin2 already implements

| Technique | Source game | Where it lives in Tarmin2 |
|---|---|---|
| Forward simulation over centuries, seeded and deterministic | DF | `HistorySimulator.prehistory`, 300 years (`PREHISTORY_YEARS`), with an RNG per season |
| Tracked named figures with traits, births, deaths and fates | DF histfigs | `Figure` (traits, `Fate`, spouse, hostage), `Trait` biases lords' choices |
| Causal links between events | DF | `HistoryEvent.causeEventId`, `Grudge`, `CasusBelli`, wars tied to `declaredEventId` |
| Succession, usurpation and disputed seats | DF | `EventType.SUCCESSION` / `USURPATION` / `DISPUTED_SUCCESSION` |
| Megabeasts, and their deaths changing the world | DF | `Megabeast`, `MEGABEAST_*` events |
| Mortal settlements living on under the houses | DF sites | `MortalSimulator`, `Settlement`, `Mortal` (keepers inheriting seats in families, ADR 0005) |
| Player deeds written into the same history | DF Legends | `PlayerDeed` → `HistorySimulator.applyDeed`, replayed from the save (ADR 0004) |
| Hidden history, unlocked by found objects | DF and Qud | `FragmentKind` (PAGE, PROCLAMATION, BANNER, VOID_GLYPH), `unlockedEvents` |
| Learned entries sorted by date | Qud journal | `Annals` (the Archive Lectern), grouped into centuries |
| Tracery-like grammar in JSON, with `#symbol#` expansion | Qud | `ChronicleGrammar` + `assets/data/chronicle_grammar.json` |
| Voice-specific vocabulary (comparable to a domain) | Qud domains | `voices` (liturgical, martial, chronicler, feral, courtly, mercantile), keyed by `Doctrine.voice` |
| Several contradictory tellings of one event | Qud tomb vs gospel, and Qud's future work | `Chronicler` FOR / AGAINST / NEUTRAL, and `Annals.Entry.ownSide` / `otherSide` |
| Different epithets depending on who is speaking | Qud cognomens, DF epithets | `Epithets.of`: "the Steadfast" to the lord's own house, "the Usurper" to everyone else |
| Rationalized motives, chosen from state | Qud §4.3 (partly) | `{cb}` expands a `CasusBelli` phrase per bias. The cause is real (simulated), not invented afterwards |
| News that travels (rumours) and loud events | DF rumours | `Headlines.weight`, `Knell.tolls` |
| Names from culture-specific word lists | DF symbols (lite) | `NameForge.given` (doctrine heads plus endings), `Allegiance.givenName` / `familyName` (10 each) |
| Developer dump for reading many histories | Qud debug tools | `ChronicleDump` |

In short, Tarmin2 is DF-style in *simulation* and Qud-style in *text*. Its one step past Qud is that every event already has a FOR and an AGAINST telling.

## (d) The gaps

1. **The Incarnation is anonymous in the history.** `SEEKER_FELL` stores only `detail` (the ordinal) and `houseA` (the killer). The grammar says "the {ordinal} seeker" (`chronicle_grammar.json`, `SEEKER_FELL`), and `SLAIN_BY_PLAYER` says "a seeker from the mortal world". Nothing links an event to a life.
2. **No life entity.** There is nothing like a histfig or a sultan for a prisoner: no name, origin, home, crime or birth season. `PlayerDeed` carries no life index, so deeds cannot be grouped by life.
3. **No per-life record or selection.** The game has a salience ranking (`Headlines.weight`), but nothing picks the ~8 notable lines of a life, and milestones that are not history events (deepest stratum, Heavies slain, Mastery ranks, Arts) are never recorded.
4. **Bias stops at tone.** FOR and AGAINST templates differ in wording and epithet. They never differ in *facts*: who killed the prisoner, why the prisoner came, or what was lost. Qud's tomb and gospel accounts do differ in facts.
5. **Telling is per event, never per life.** No House sums up a whole life or death, and no epitaph exists.
6. **The player's epithet is global.** `Epithets.player` counts every `SLAIN_BY_PLAYER` in the world ("the Seeker", "the Bloodied", "Lordsbane", "Gashbreaker"). It is neither per life nor per House.
7. **Killers are not promoted.** A monster that kills an Incarnation is not given a name, an epithet or a future in the history, as DF does for animals that kill.
8. **Bones say "Fallen Hero".** `BonesData.playerName` defaults to it, and `GhostPlayerMonster`, `BonesAwakenModal` and `Hud` all display it.
9. **No thread across lives.** Nothing plays the role of a Qud domain to tie one soul's lives together, and no eras are keyed to Tarmin's Hunger, as DF names Ages from megabeast counts.
10. **Name pools are small.** `Allegiance` has 10 given and 10 family names each. Fifty lives drawn from one allegiance would repeat given names often.

---

## (e) Recommendations

All of these keep ADR 0004 intact. Generated facts derive from `(history seed, life ordinal, reroll count)`. What the player does is stored as ordered records (a separate life store, per #268). Prose is rendered at read time and never saved.

### 1. Prisoner identities: the Qud initialization step on a DF site

- **A `Life` entity, a small histfig.** Fields: ordinal, name, origin (`RAIDED` / `TITHED`), home settlement id, a crime (tithed only) or a trade (raided), a **thread** (see §4), birth season (the live season of rebirth), and later a death. Generate all of it from a `Random` keyed on `seed ^ LIFE_SALT ^ ordinal * K ^ reroll`, as `MortalSimulator` keys on season. Then the reroll is simply `reroll + 1`.
- **Home is a real site.** Draw the home from `world.settlements`, which already have names, allegiances and keeper families. Draw the name from that settlement's `Allegiance` lists, as DF draws from the civilization's language. A raided prisoner from a Goblin Clans settlement reads differently from a tithed Refugee Council convict without any new data. Sometimes give the prisoner a keeper family's surname, so towns can later say "my cousin".
- **The origin event is invented afterwards (Qud §4.3).** At rebirth, record the cause: a new history event, `PRISONER_TAKEN`, for a raid on the home settlement by the Antlered Host, or a tithe paid by a kingdom. The raid "happened" because a prisoner needed one. Over 50 lives, the settlements the Host keeps raiding gather a history of their own, and towns can mention it.
- **Crimes come from a grammar table.** Add `crimes` and `trades` symbols to `chronicle_grammar.json`, with allegiance-flavoured variants (comparable to Qud's `domain.practices`), so a crime reads in its culture's voice. Keep everything ASCII and within `UiGlyphs.sanitize`.
- **Bigger name pools, DF style.** Rather than longer flat lists, compose names: a given name plus a two-part byname (adjective + noun, noun + noun) from per-allegiance word lists. This is DF's compound last name, and it multiplies variety cheaply.

### 2. Per-life records: DF's selectivity with Qud's two texts

- **Store facts, not prose.** Each record line is `{lifeOrdinal, kind, ids…, season, turn}`. Render it at read time, as `Annals` already renders events. This keeps the save small and lets the grammar improve after a save is written.
- **Promote by threshold, as DF does for histfigs.** Write a line only for: deeds the history already records (`SLAIN_BY_PLAYER`, `MEGABEAST_SLAIN`, `SEAL_GRANTED`, `OATH_BROKEN` and so on, tagged with the life ordinal), and milestone firsts (a new deepest stratum for this life, the 1st, 5th and 10th Heavy, each Mastery rank, a new Art, a seal). Rank lines with a `weight` in the style of `Headlines.weight`, and keep the top ~8 by weight, shown in chronological order, as `Headlines.pick` does.
- **Two texts for each line (Qud's Accomplishment and Mural Accomplishment).** The **World Log** shows the plain line ("Reached the 7th stratum."). The **chronicle** shows the mythic line, told by a House. Both come from one fact, through different grammar keys: `LIFE_<KIND>.PLAIN` and the existing `NEUTRAL` / `FOR` / `AGAINST`.
- **Death is the one fixed fact.** Store the killer (monster type, plus house id when relevant), place (stratum or surface name), turn and season. The epitaph is a grammar line picked by the killer and the life's thread. Following Qud, death gets a plain line and no mythic one *of its own*: the Houses' accounts of the death are the myth (§3).
- **Killers become figures (DF).** A monster that kills an Incarnation is promoted: it is given a name from its house's doctrine (`NameForge.given`) and recorded as the killer. If it kills again, it earns an escalating epithet ("Twice-Fed", "the Champion's Bane"), in the spirit of DF's five-kill epithet. #265 already decided this. The DF precedent supports it, and it makes later chronicles say *who*.

### 3. A House's biased, possibly contradictory telling of a life

- **Extend `Chronicler` from events to lives.** Add `Chronicler.ofLife(world, life, houseId)`. A House tells a life FOR itself (the House profited or claims the kill), AGAINST (the prisoner hurt it), or NEUTRAL (a rumour). Reuse `title(voice)` and the doctrine `voices` unchanged.
- **Split facts into fixed and mutable.** The fixed facts are the life's name, year, death and the Bridge stone it paid. These never change, so the World Log stays trustworthy. The mutable facts are *who killed them*, *why they came*, *what they were* (crime or innocence) and *how they died*. Each telling may take these from the teller's own state, which is Qud's ex post facto move:
  - **Claimed kills.** A House that gains prestige from a seeker's death (`SEEKER_FELL` already adds `prestige += 3` to the killer) claims deaths it did not cause. Its chronicler names itself as the killer whenever the true killer had no house, or was its enemy. The World Log shows the truth, and the reader sees the lie.
  - **Motives rationalized from state.** As Qud's `#injustice#` reads the sultan's allies, the House's `#motive#` reads its own grudges and doctrine: a liturgical House says the prisoner came "as an offering", a martial one says "as a challenger", and an AGAINST House the prisoner wronged says "as a thief" and cites the actual `SLAIN_BY_PLAYER` event.
  - **Crime retold.** A tithed prisoner's real crime is a fact. A FOR House inflates it ("a murderer of children"), and the prisoner's home allegiance softens it. The Houses draw from `crimes` with a bias suffix, just as `casus` already has bias lists.
- **Silence is a telling too (DF's knowledge).** A House tells a life only if it *knows* it: it was involved (killed the prisoner, lost a figure to them, swore them an oath) or the Knell tolled for one of the life's deeds. Everything else is a rumour (NEUTRAL) or absent. This matches DF's witness-based rumours and keeps each House's chronicle partial.
- **Found, not given.** The World Log is always readable, because it is the soul's own memory. The Houses' tellings of a life arrive through fragments, as events do today. Add a `FragmentKind` such as `EPITAPH` or `DIRGE` whose `tells` set holds the life events.

### 4. Tying the soul's lives together across a long history

- **A thread per life, a motif per soul.** Give each Incarnation a Qud-style **domain**, here called a *thread*: an archetype such as salt, bells, debt, fire or bone, drawn at birth. Templates for that life's lines include `#thread.*#` symbols, so a "salt" prisoner (a salt-pan convict) gets salt-flavoured deeds and a salt-flavoured epitaph. The soul's constant title is the meta-thread: optionally, one soul motif chosen at a new game shows up in every life's epitaph, so 50 lives read as one myth.
- **Recurring cast.** Promoted killers (§2), Houses whose lords a life slew, and settlements the Host keeps raiding (§1) recur across lives, giving Qud-style micro-narratives without real causality. "Vesk the Twice-Fed, who ate the 12th and 19th of the Champion's lives."
- **Eras keyed to Tarmin's Hunger (DF Ages).** Name eras of the World Log from Hunger thresholds, as DF names Ages from surviving megabeasts: for example, "the Age of Stones" while the Bridge is under a third built, "the Age of the Span" under two-thirds, and "the Last Crossing" from there to 50. This is pure presentation, but the log reads as a history with a direction, and it answers part of the open question about the Hunger and the World Log.
- **Graves and ghosts with names (DF).** Replace `"Fallen Hero"` with the life's name and epithet in `BonesData`, `GhostPlayerMonster`, `BonesAwakenModal` and `Hud`. A DF-style memorial (a grave marker at the Shelter, or reading an epitaph at the bones) can "lay" a ghost and add a World Log line, the way DF slabs reveal "the arrival and death of the creature".
- **"N lives unremembered" as DF's hidden history.** Older lives beyond the record cap, or before the feature, collapse into one entry (decided in #268). DF's "reveal all history = off" is the precedent: a fragment can later restore one forgotten life, if the design wants that.
- **A coherence harness (Qud's debug practice).** Grinblat and Bucklew credit their satisfaction with coherence to "time spent honing it" by generating many sultans quickly (Paper §5). Extend `ChronicleDump` to simulate N lives against one seed: identities, records, each House's telling, and the World Log. Golden-file a fixed seed, as the history's determinism tests already do.

## Caveats

- **GDC Vault was down** (redirected to maintenance) for both Grinblat talks, so only their published descriptions are cited. The FDG '17 paper is the primary Qud source, read in full.
- **Some DF wiki pages were rate-limited (HTTP 429)** during this research. Claims for World gen, Slab/Memorial, Rumor, Knowledge, Fame and Ages rest on those pages' text as surfaced through search, not a full read. Several DF wiki pages are version-specific: Ages thresholds differ between v0.31, v0.34 and v50, and the v50 thresholds are cited.
- I did not read Tarn Adams's chapter in *Procedural Storytelling in Game Design*, or a primary description of Qud's `Naming.xml`. Nothing here depends on them.
- §(e) is recommendation, not decision. The grilling tickets on map #265 own the choices, especially how the chronicle tells a life (still unspecified).
