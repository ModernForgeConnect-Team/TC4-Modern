# Giant magical saplings

The mega, sacred and enchanted sacred variants are available for both greatwood and silverwood. Four original saplings craft a mega sapling of the same wood. Sacred and enchanted sacred variants can appear in dungeon and stronghold library chests. The enchanted variant has an item glint.

New trees use the synchronous generation algorithm and parameters from MFR 1.7.10: first validate the location and enumerate leaf nodes, then place leaves, branches and the trunk. Mega uses scales 4–6, sacred 6–9, and enchanted sacred uses the 250-block massive generator. The variant mapping follows the intended sizes rather than the reversed mapping in that MFR branch. The original candidate density is uncapped. Generated materials are the original TC4 logs and leaves, plus six-sided bark wood on branch and trunk ends.

Silverwoods generate a random number of original pure nodes between zero and floor(tree height × 3 / 10), with randomized trunk positions. Trees already growing when this version is installed retain their previous generation state and node rules.

The original generator runs synchronously and can load many chunks. Sacred and especially enchanted sacred trees can take a long time and cause severe lag; the original unsafe variants can overwrite existing blocks, including in the trunk footprint. Mega uses MFR's safe-growth check. The 1.7.10 world height and block APIs are adapted to the current world's build limits and block update system.
