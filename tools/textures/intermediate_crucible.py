"""Original 16x16 textures for the intermediate crucible and the ancient thaumium ingot.

Usage: draw.py <output texture root (assets/tc4modern/textures)> <preview png>
"""
import os
import sys
from PIL import Image

root, preview = sys.argv[1], sys.argv[2]


def hexa(value):
    value = value.lstrip('#')
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def from_map(rows, palette):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    image = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                image.putpixel((x, y), hexa(palette[ch]))
    return image


# Fiery iron: dark outline, red body, orange highlights, glowing seams and brass rivets.
FIRE = {
    'k': '#2a0a06',  # outline / deepest shadow
    'd': '#5e1a0e',  # dark red
    'r': '#8e2a14',  # body red
    'm': '#b23d1a',  # mid red-orange
    'l': '#d55a22',  # light orange
    'h': '#f08a34',  # hot highlight
    'y': '#ffc35a',  # glow
    'b': '#e2b04a',  # brass rivet
    'B': '#8a5a1e',  # rivet shadow
}

CRUCIBLE_SIDE = [
    'kkkkkkkkkkkkkkkk',
    'khhhhhhhhhhhhhhk',
    'kllllllllllllllk',
    'kddddddddddddddk',
    'krmmmlmmmmlmmmrk',
    'krbmmmmmmmmmmbrk',
    'krBmmmmmmmmmmBrk',
    'krmmmmmmmmmmmmrk',
    'krrmmmmmmmmmmrrk',
    'krrrmmmmmmmmrrrk',
    'kdrrrrrrrrrrrrdk',
    'kdyddddddddddydk',
    'kddddddddddddddk',
    'kkddkkkkkkkkddkk',
    'kkrdk......kdrkk',
    'kkkkk......kkkkk',
]

CRUCIBLE_TOP = [
    'kkkkkkkkkkkkkkkk',
    'khhhhhhhhhhhhhhk',
    'khlllllllllllldk',
    'khlkkkkkkkkkkldk',
    'khlk........kldk',
    'khlk........kldk',
    'khlk........kldk',
    'khlk........kldk',
    'khlk........kldk',
    'khlk........kldk',
    'khlk........kldk',
    'khlk........kldk',
    'khlkkkkkkkkkkldk',
    'khlddddddddddddk',
    'kdddddddddddddrk',
    'kkkkkkkkkkkkkkkk',
]

CRUCIBLE_INNER = [
    'dddddddddddddddd',
    'drrrrrrrrrrrrrrd',
    'drmmrrrrrrrrmmrd',
    'drmrrrrrrrrrrmrd',
    'drrrrdrrrrdrrrrd',
    'drrrrrrrrrrrrrrd',
    'drrrrrrrrrrrrrrd',
    'ddrrrrrdrrrrrrdd',
    'drrrrrrrrrrrrrrd',
    'drrrrrrrrrrrrrrd',
    'drrrdrrrrrrdrrrd',
    'ddrrrrrrrrrrrrdd',
    'dddrrrrrrrrrrddd',
    'ddddrrrrrrrrdddd',
    'dkddddddddddddkd',
    'kkkkkkkkkkkkkkkk',
]

CRUCIBLE_BOTTOM = [
    'kkkkkkkkkkkkkkkk',
    'kddddddddddddddk',
    'kdrrrrrrrrrrrrdk',
    'kdrmmmmmmmmmmrdk',
    'kdrmllllllllmrdk',
    'kdrmlhhhhhhlmrdk',
    'kdrmlhyyyyhlmrdk',
    'kdrmlhyyyyhlmrdk',
    'kdrmlhyyyyhlmrdk',
    'kdrmlhyyyyhlmrdk',
    'kdrmlhhhhhhlmrdk',
    'kdrmllllllllmrdk',
    'kdrmmmmmmmmmmrdk',
    'kdrrrrrrrrrrrrdk',
    'kddddddddddddddk',
    'kkkkkkkkkkkkkkkk',
]

# Netherite body (grey-brown) with thaumium's violet veins and highlights.
INGOT = {
    'o': '#16101a',  # outline
    't': '#6e6168',  # top face
    'u': '#85767e',  # top face light
    'h': '#c9b6e6',  # violet highlight
    'v': '#9a62d6',  # violet vein
    'w': '#6b3ea3',  # dark violet
    'f': '#4b4047',  # front face
    'g': '#3a3138',  # front shade
    's': '#271f25',  # side
}

INGOT_MAP = [
    '................',
    '................',
    '................',
    '..........oooo..',
    '.......ooouuhso.',
    '....ooouuttvtsso',
    '..oouttttvtttsso',
    '.ohtttvvtttttsso',
    '.ohutttttvttssso',
    '.offvfffffffssso',
    '.ogfffwfffvfsso.',
    '.ogggfffffffso..',
    '..oogggwgggfo...',
    '....ooooooooo...',
    '................',
    '................',
]

textures = {
    'block/intermediate_crucible_side.png': from_map(CRUCIBLE_SIDE, FIRE),
    'block/intermediate_crucible_top.png': from_map(CRUCIBLE_TOP, FIRE),
    'block/intermediate_crucible_inner.png': from_map(CRUCIBLE_INNER, FIRE),
    'block/intermediate_crucible_bottom.png': from_map(CRUCIBLE_BOTTOM, FIRE),
    'item/ancient_thaumium_ingot.png': from_map(INGOT_MAP, INGOT),
}
for name, image in textures.items():
    path = os.path.join(root, *name.split('/'))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)

sheet = Image.new('RGBA', (len(textures) * 20, 16), (60, 60, 60, 255))
for i, image in enumerate(textures.values()):
    sheet.paste(image, (i * 20, 0), image)
sheet.resize((sheet.width * 12, sheet.height * 12), Image.NEAREST).save(preview)
print('wrote', ', '.join(textures))
