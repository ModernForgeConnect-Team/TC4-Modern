"""Draws the ancient thaumium armour, tool and smithing template textures (original pixel art).

Palette taken from tc4modern's own ancient_thaumium_ingot.png so the set matches the ingot.
Usage: python -I tools/draw_ancient_thaumium_textures.py tc4-modern/src/main/resources/assets/tc4modern/textures <preview dir>
"""
import random
import sys
from pathlib import Path
from PIL import Image

# Dark to light, then the ingot's lavender highlight, its vivid purple, and a brighter glint.
P = [(0x0e, 0x0a, 0x12), (0x1c, 0x14, 0x24), (0x2c, 0x22, 0x34), (0x38, 0x2b, 0x42), (0x46, 0x3c, 0x52),
     (0x54, 0x48, 0x64), (0x66, 0x59, 0x7a), (0x9a, 0x86, 0xc4), (0xa4, 0x6c, 0xe0), (0xd2, 0xbc, 0xf4)]
OUT, LIGHT, ACCENT, GLINT = 0, 7, 8, 9


def rgba(i):
    return P[i] + (255,)


# ---- Item icons: a mask, shaded automatically with light from the top left -------------------------------------

def shade_icon(mask, accents=()):
    """'#' is shaded metal, 'd' a dark inside (the helmet's opening), 'h'/'k' wood, '.' empty."""
    h, w = len(mask), len(mask[0])
    on = lambda x, y: 0 <= x < w and 0 <= y < h and mask[y][x] in '#d'
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    xs = [x for y in range(h) for x in range(w) if on(x, y)]
    ys = [y for y in range(h) for x in range(w) if on(x, y)]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    for y in range(h):
        for x in range(w):
            if mask[y][x] in HANDLE:
                img.putpixel((x, y), HANDLE[mask[y][x]])
                continue
            if not on(x, y):
                continue
            edge = not (on(x - 1, y) and on(x + 1, y) and on(x, y - 1) and on(x, y + 1))
            if edge:
                # Lit edges a shade lighter than shadowed ones.
                tone = 1 if (not on(x, y - 1) or not on(x - 1, y)) else OUT
            else:
                t = ((x - x0) / max(1, x1 - x0) + (y - y0) / max(1, y1 - y0)) / 2
                tone = round(6 - t * 3)  # 6 at the top left down to 3, so it never reads as outline
                if not on(x - 1, y - 1) or not on(x, y - 2) or not on(x - 2, y):
                    tone = LIGHT if (not on(x, y - 2) or not on(x - 2, y)) and t < 0.55 else max(tone, 5)
            if mask[y][x] == 'd':
                tone = 1
            img.putpixel((x, y), rgba(tone))
    for x, y, tone in accents:
        img.putpixel((x, y), rgba(tone))
    return img


HELMET = [
    "................",
    "................",
    "................",
    ".....######.....",
    "....########....",
    "...##########...",
    "...##########...",
    "...###dddd###...",
    "...##dddddd##...",
    "...##dddddd##...",
    "...##......##...",
    "................",
    "................",
    "................",
    "................",
    "................",
]
CHESTPLATE = [
    "................",
    "..####....####..",
    ".#####....#####.",
    ".######..######.",
    ".##############.",
    ".##############.",
    "..############..",
    "...##########...",
    "...##########...",
    "...##########...",
    "...##########...",
    "....########....",
    ".....######.....",
    "................",
    "................",
    "................",
]
LEGGINGS = [
    "................",
    "................",
    "..############..",
    "..############..",
    "..############..",
    "..#####..#####..",
    "..#####..#####..",
    "..#####..#####..",
    "..#####..#####..",
    "..#####..#####..",
    "..#####..#####..",
    "..#####..#####..",
    "..#####..#####..",
    "................",
    "................",
    "................",
]
BOOTS = [
    "................",
    "................",
    "................",
    "...####..####...",
    "...####..####...",
    "...####..####...",
    "...####..####...",
    "...####..####...",
    "..#####..#####..",
    ".######..######.",
    ".######..######.",
    "................",
    "................",
    "................",
    "................",
    "................",
]


# Tools, laid out as vanilla tools lie (handle from the bottom left, head at the top right), each pixel named:
# metal 'k' outline, 'p' lit purple edge, 'L' highlight, 'm' body, 'g'/'G' gem and glint;
# handle 'q' outline, 'w' dark wood, 'r' its reddish highlight.
SWORD = [
    ".............ppp",
    "............pLLk",
    "...........pLmLk",
    "..........pLmLk.",
    ".........pmgmk..",
    "........pmGmk...",
    "..pp...pmkmk....",
    "..pmp.pmkmk.....",
    "...pmpmkmk......",
    "...pmmkmk.......",
    "....pmmk........",
    "...qrqmkk.......",
    "..qrq.kkkk......",
    "ppmq....kk......",
    "pkk.............",
    "kkk.............",
]
PICKAXE = [
    "................",
    "................",
    ".....ppppp......",
    "....pLLLmmppk...",
    ".....pkkkmmgk...",
    ".........kmGk...",
    "........qrqmmk..",
    ".......qrq.kmk..",
    "......qrq..kLk..",
    ".....qrq...kLk..",
    "....qwq....kLk..",
    "...qwq......k...",
    "..qwq...........",
    ".qrq............",
    ".qq.............",
    "................",
]
AXE = [
    "................",
    ".........pp.....",
    "........pLLp....",
    ".......pLmmp....",
    "......pmmmGmg...",
    "......kmmmmmk...",
    ".......kkmmkmk..",
    "........qrqmmk..",
    ".......qrq.kk...",
    "......qrq.......",
    ".....qwq........",
    "....qwq.........",
    "...qwq..........",
    "..qrq...........",
    "..qq............",
    "................",
]
SHOVEL = [
    "................",
    "................",
    "..........ppp...",
    ".........pLLLk..",
    "........pLmgmk..",
    ".......pmmGmmk..",
    "........kmmmk...",
    ".......qrqmk....",
    "......qrq.k.....",
    ".....qrq........",
    "....qwq.........",
    "...qwq..........",
    ".qqwq...........",
    ".qrq............",
    "..qq............",
    "................",
]
HOE = [
    "................",
    ".......ppp......",
    "......pLLLp.....",
    ".......kkmLkkg..",
    ".........kmmGk..",
    "..........kmmk..",
    ".........qrqk...",
    "........qrq.....",
    ".......qrq......",
    "......qrq.......",
    ".....qwq........",
    "....qwq.........",
    "...qwq..........",
    "..qrq...........",
    "..qq............",
    "................",
]
HANDLE = {'h': (0x6b, 0x4a, 0x2a, 255), 'k': (0x3a, 0x26, 0x16, 255)}
TOOL = {'k': P[OUT], 'p': (0x5a, 0x42, 0x70), 'L': P[LIGHT], 'm': P[5], 'g': P[ACCENT], 'G': P[GLINT],
        'q': (0x1c, 0x12, 0x14), 'w': (0x3a, 0x30, 0x36), 'r': (0x7a, 0x44, 0x3e)}


def paint_icon(mask):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(mask):
        for x, c in enumerate(row):
            if c != '.':
                img.putpixel((x, y), TOOL[c] + (255,))
    return img


def template():
    """A dark stone tablet, its rim catching the light, with a thaumic eye rune cut into it."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    rnd = random.Random(4)
    for y in range(1, 15):
        for x in range(2, 14):
            corner = (x in (2, 13)) and (y in (1, 14))
            if corner:
                continue
            if x in (2, 13) or y in (1, 14):
                tone = 1 if (x == 2 or y == 1) else OUT
            elif x == 3 or y == 2:
                tone = 6
            elif x == 12 or y == 13:
                tone = 2
            else:
                tone = 3 + (rnd.random() < 0.25) - (rnd.random() < 0.15)
            img.putpixel((x, y), rgba(tone))
    rune = [
        "...88...",
        "..8..8..",
        ".8.77.8.",
        "8.7997.8",
        "8.7997.8",
        ".8.77.8.",
        "..8..8..",
        "...88...",
    ]
    for ry, row in enumerate(rune):
        for rx, c in enumerate(row):
            if c != '.':
                img.putpixel((4 + rx, 4 + ry), rgba(int(c)))
    return img


# ---- Armour layers: the standard 64x32 biped armour layout -----------------------------------------------------

def box_faces(u, v, w, h, d):
    """Face rectangles (x, y, width, height) of a model box textured at (u, v)."""
    return {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h), 'back': (u + d + w + d, v + d, w, h),
    }


FACE_TONE = {'top': 6, 'front': 5, 'right': 4, 'left': 4, 'back': 3, 'bottom': 2}


class Layer:
    def __init__(self):
        self.img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
        self.rnd = random.Random(7)

    def plate(self, face, rect, rows=None, seams=()):
        """Paints a face (or only its rows [a, b) counted from the face top) as metal plate."""
        x, y, w, h = rect
        a, b = rows or (0, h)
        base = FACE_TONE[face]
        for yy in range(a, b):
            for xx in range(w):
                tone = base
                if yy == a and face not in ('top', 'bottom'):
                    tone = LIGHT if face == 'front' else 6  # the plate's lit upper rim
                elif yy == b - 1 or xx in (0, w - 1):
                    tone = max(1, base - 2)  # plate edges
                elif yy in seams:
                    tone = max(1, base - 1)
                elif self.rnd.random() < 0.12:
                    tone = base + self.rnd.choice((-1, 1))
                self.img.putpixel((x + xx, y + yy), rgba(tone))

    def put(self, x, y, tone):
        self.img.putpixel((x, y), rgba(tone))

    def clear(self, x, y, w, h):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.img.putpixel((xx, yy), (0, 0, 0, 0))


def gem(layer, x, y):
    """A two-pixel vivid purple stone with a glint and a lavender setting below."""
    layer.put(x, y, ACCENT); layer.put(x + 1, y, GLINT)
    layer.put(x, y + 1, LIGHT); layer.put(x + 1, y + 1, ACCENT)


def layer1():
    L = Layer()
    head = box_faces(0, 0, 8, 8, 8)
    for face, rect in head.items():
        if face == 'bottom':
            continue
        L.plate(face, rect, seams=(3,))
    # Face opening in the front: forehead band and cheek guards stay.
    fx, fy, _, _ = head['front']
    L.clear(fx + 2, fy + 4, 4, 4)
    L.clear(fx + 1, fy + 6, 1, 2); L.clear(fx + 6, fy + 6, 1, 2)
    gem(L, fx + 3, fy + 1)
    body = box_faces(16, 16, 8, 12, 4)
    for face, rect in body.items():
        if face in ('top', 'bottom'):
            continue
        L.plate(face, rect, seams=(4, 8))
    bx, by, _, _ = body['front']
    # A rune diamond on the chest.
    for dx, dy, t in ((3, 2, ACCENT), (4, 2, ACCENT), (2, 3, ACCENT), (5, 3, ACCENT), (3, 3, GLINT), (4, 3, LIGHT),
                      (2, 4, ACCENT), (5, 4, ACCENT), (3, 4, LIGHT), (4, 4, GLINT), (3, 5, ACCENT), (4, 5, ACCENT)):
        L.put(bx + dx, by + dy, t)
    arm = box_faces(40, 16, 4, 12, 4)
    for face, rect in arm.items():
        if face == 'bottom':
            continue
        L.plate(face, rect, rows=None if face == 'top' else (0, 9), seams=(3,))
    ax, ay, _, _ = arm['front']
    L.put(ax + 1, ay + 1, ACCENT); L.put(ax + 2, ay + 1, GLINT)
    leg = box_faces(0, 16, 4, 12, 4)
    # Boots: the lower part of the legs and the soles.
    for face, rect in leg.items():
        if face == 'top':
            continue
        L.plate(face, rect, rows=None if face == 'bottom' else (7, 12))
    lx, ly, _, _ = leg['front']
    L.put(lx + 1, ly + 8, ACCENT); L.put(lx + 2, ly + 8, GLINT)
    return L.img


def layer2():
    L = Layer()
    body = box_faces(16, 16, 8, 12, 4)
    # The belt: the lower part of the body.
    for face, rect in body.items():
        if face in ('top', 'bottom'):
            continue
        L.plate(face, rect, rows=(8, 12))
    bx, by, _, _ = body['front']
    L.put(bx + 3, by + 9, ACCENT); L.put(bx + 4, by + 9, GLINT)
    leg = box_faces(0, 16, 4, 12, 4)
    for face, rect in leg.items():
        if face in ('top', 'bottom'):
            continue
        L.plate(face, rect, rows=(0, 10), seams=(4,))
    lx, ly, _, _ = leg['front']
    gem(L, lx + 1, ly + 5)  # knee
    return L.img


def preview(images, path, scale=12, gap=8):
    w = sum(i.width * scale for i in images) + gap * (len(images) + 1)
    h = max(i.height * scale for i in images) + gap * 2
    sheet = Image.new('RGBA', (w, h), (0xc8, 0xc0, 0xb0, 255))
    x = gap
    for i in images:
        big = i.resize((i.width * scale, i.height * scale), Image.NEAREST)
        sheet.alpha_composite(big, (x, gap))
        x += big.width + gap
    sheet.save(path)


def main():
    textures, previews = Path(sys.argv[1]), Path(sys.argv[2])
    icons = {
        'ancient_thaumium_helmet': shade_icon(HELMET, [(7, 4, ACCENT), (8, 4, GLINT)]),
        'ancient_thaumium_chestplate': shade_icon(CHESTPLATE, [(7, 6, ACCENT), (8, 6, ACCENT), (7, 7, GLINT), (8, 7, ACCENT)]),
        'ancient_thaumium_leggings': shade_icon(LEGGINGS, [(4, 3, ACCENT), (5, 3, GLINT), (10, 3, ACCENT), (11, 3, GLINT)]),
        'ancient_thaumium_boots': shade_icon(BOOTS, [(4, 5, ACCENT), (5, 5, GLINT), (10, 5, GLINT), (11, 5, ACCENT)]),
        'ancient_thaumium_template': template(),
        'ancient_thaumium_sword': paint_icon(SWORD),
        'ancient_thaumium_pickaxe': paint_icon(PICKAXE),
        'ancient_thaumium_axe': paint_icon(AXE),
        'ancient_thaumium_shovel': paint_icon(SHOVEL),
        'ancient_thaumium_hoe': paint_icon(HOE),
    }
    (textures / 'item').mkdir(parents=True, exist_ok=True)
    for name, img in icons.items():
        img.save(textures / 'item' / (name + '.png'))
    armor = textures / 'models' / 'armor'
    armor.mkdir(parents=True, exist_ok=True)
    l1, l2 = layer1(), layer2()
    l1.save(armor / 'ancient_thaumium_layer_1.png')
    l2.save(armor / 'ancient_thaumium_layer_2.png')
    preview(list(icons.values()), previews / 'icons_preview.png', scale=12)
    preview([l1, l2], previews / 'armor_preview.png', scale=8)


if __name__ == '__main__':
    main()
