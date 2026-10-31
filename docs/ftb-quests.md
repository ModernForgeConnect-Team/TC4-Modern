# FTB Quests integration

Targets Forge 1.20.1 and FTB Quests 2001.4.22, FTB Library 2001.2.13, FTB Teams 2001.3.0 and Architectury 9.1.12. These dependencies are optional and are not bundled. They are enabled in the development runtime by default; disable them with `-PwithFtbQuests=false`.

The editor uses FTB Library's searchable icon list, populated from the installed original research and aspect registries, including addons. Research uses its original name and item/texture icon. Aspects use localized names and original textures tinted with their registered colors. The selected identifiers are saved unchanged, including case-sensitive research keys; unavailable addon identifiers are retained when editing existing quests. Catalogs are cached per runtime/language to avoid repeatedly converting every research icon.

## Task and reward data

Research task (in a quest's `tasks` list):

```snbt
{ id: "1000000000000001", type: "tc4modern:research", research: "NITOR" }
```

Research-pool aspect task:

```snbt
{ id: "1000000000000002", type: "tc4modern:aspect", aspect: "aer", amount: 10, consume: false }
{ id: "1000000000000003", type: "tc4modern:aspect", aspect: "ignis", amount: 5, consume: true }
```

Aspect reward (in `rewards`):

```snbt
{ id: "1000000000000004", type: "tc4modern:aspect", aspect: "terra", amount: 20 }
```

Use unique FTB object IDs in actual quest files. Amounts must be 1..32767. Unknown research/aspects never satisfy a task. Invalid rewards are logged and their claim flag is reset so they can be retried after configuration is fixed.

Research tasks check the actual unlocked registry entry every 20 ticks. They do not grant research or accept a note item. Non-consuming aspect tasks complete when one player holds the configured amount; completion remains recorded by FTB even if the player later spends those points. Consuming tasks require a manual click and the entire remaining requirement in that submitter's pool. An insufficient pool is unchanged. Existing partial task progress reduces the remaining charge. Completed tasks do not charge again. FTB dependency, sequential-task and locked-team checks apply.

Progress follows FTB Teams. Consumption affects the submitting player and rewards affect the claiming player. FTB controls claim deduplication, sharing, automatic claiming and resets/repeatability. Pool grants cap at 32767, following the public TC4 pool API. No Vis, essentia or inventory item amounts are involved.

All original player reads/writes use public MFCL `withPlayer` transactions on the server thread. The common mod entry loads the integration only when FTB Quests is present; optional FTB types are kept out of automatic event subscriber signatures.

## Validation

Library unit tests cover checking unlocked research without changing it, exact pool consumption, insufficient balances, unknown tags, invalid amounts and grant caps.

Selector regression tests use FTB's real `EnumConfig.addInfo` tooltip path. They cover unconfigured research, an empty registry, missing addon targets, restoring defaults and absent display values. Empty or leading-dash research IDs use a separate valid UI identifier because FTB's `NameMap` rejects these IDs as defaults; applying that UI choice preserves the original stored research key. This prevents the default-tooltip null pointer reported with FTB Library 2001.2.13.

The opt-in `-PftbQuestsSmokeTest` client probe creates a disposable flat world. With `-PwithFtbQuests=true` it exercises real FTB task/reward registration, original research completion, consuming/non-consuming tasks, locked teams, insufficient/unknown aspects, duplicate submissions/claims, configuration network round trips and the actual searchable selection screens. Reports are written to `logs/tc4modern/ftb-quests-smoke.json` and selection screenshots to `screenshots/ftb-*-selection.png` under the specified game directory. Without FTB, the probe only checks successful startup to the title screen.
