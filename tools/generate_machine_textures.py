"""Draws the block and GUI textures for the deconstruction tables, node reactor and primal furnace.

Everything is generated from seeded noise and hand-placed pixels, so re-running the script reproduces the same PNGs.
It also writes the block models that reference them (including the essentia tube stubs with explicit UVs).

    python tools/generate_machine_textures.py
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parents[1] / "tc4-modern/src/main/resources/assets/tc4modern"
BLOCK = ASSETS / "textures/block"
GUI = ASSETS / "textures/gui"
MODELS = ASSETS / "models/block"


def rgb(value):
    return (value >> 16 & 255, value >> 8 & 255, value & 255, 255)


def shade(color, factor):
    return tuple(max(0, min(255, round(v * factor))) for v in color[:3]) + (255,)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


class Canvas:
    def __init__(self, width, height, seed):
        self.img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        self.px = self.img.load()
        self.rng = random.Random(seed)
        self.width, self.height = width, height

    def put(self, x, y, color):
        if 0 <= x < self.width and 0 <= y < self.height:
            self.px[x, y] = color if len(color) == 4 else tuple(color) + (255,)

    def get(self, x, y):
        return self.px[x, y]

    def rect(self, x0, y0, x1, y1, color):
        """Filled rectangle, inclusive corners."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, color)

    def tint(self, x0, y0, x1, y1, factor):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, shade(self.get(x, y), factor))

    def bevel(self, x0, y0, x1, y1, top_left, bottom_right):
        for x in range(x0, x1):
            self.put(x, y0, top_left)
        for y in range(y0, y1):
            self.put(x0, y, top_left)
        for x in range(x0 + 1, x1 + 1):
            self.put(x, y1, bottom_right)
        for y in range(y0 + 1, y1 + 1):
            self.put(x1, y, bottom_right)

    def save(self, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.img.save(path)


# ---- Palette ----------------------------------------------------------------------------------------------------

GREATWOOD = rgb(0x5A3E2B)
GREATWOOD_EDGE = rgb(0x8A6242)
GOLD = rgb(0xE8BA48)
GOLD_LIGHT = rgb(0xFFE38A)
GOLD_DARK = rgb(0x9C6E24)
BRASS = rgb(0xC9944A)
BRASS_LIGHT = rgb(0xEBC47C)
BRASS_DARK = rgb(0x7E5526)
SLATE = rgb(0x4A4858)
SLATE_DARK = rgb(0x2A2833)
THAUMIUM = rgb(0x6A549E)
THAUMIUM_LIGHT = rgb(0x9C86D2)
ARCANE_STONE = rgb(0x7A7884)
MORTAR = rgb(0x3A3842)
PRIMALS = [rgb(0xFFF07A), rgb(0x56C000), rgb(0xFF5A01), rgb(0x3CD4FC), rgb(0xE6E4F6), rgb(0x6E6E86)]

FLAME = {"r": rgb(0xC4341A), "o": rgb(0xFF8018), "y": rgb(0xFFD640), "w": rgb(0xFFFAC8)}
FLAME_SHAPE = [
    "......r.......",
    "......rr......",
    ".....rro...r..",
    ".....roor..r..",
    "....rrooor.rr.",
    "..r.rooooorrr.",
    "..rrroooyoorr.",
    ".rrooooyyooor.",
    ".rroooyyyyoorr",
    ".rrooyyyyyyoor",
    ".rrooyywwyyoor",
    ".rroyywwwwyoor",
    "..rooywwwwyor.",
    "...rrooooorr..",
]


# ---- Materials --------------------------------------------------------------------------------------------------

def planks(cv, x0, y0, x1, y1, base, plank=4, horizontal=True):
    """Planks with sine grain streaks, a dark seam per plank and one staggered butt joint."""
    rng = cv.rng
    length = (x1 - x0 + 1) if horizontal else (y1 - y0 + 1)
    first, last = (y0, y1) if horizontal else (x0, x1)
    for start in range(first, last + 1, plank):
        tone = rng.uniform(0.9, 1.08)
        joint = rng.randrange(5, length - 5) if length > 14 else None
        for across in range(start, min(start + plank, last + 1)):
            phase, freq = rng.uniform(0, 6.3), rng.uniform(0.22, 0.5)
            for along in range(length):
                grain = 1 + 0.07 * math.sin(along * freq + phase) + rng.uniform(-0.035, 0.035)
                color = shade(base, tone * grain)
                if across == start:
                    color = shade(base, 0.62)
                elif across == start + 1:
                    color = shade(color, 1.08)
                if joint is not None and along == joint and across != start:
                    color = shade(base, 0.7)
                cv.put(*((x0 + along, across) if horizontal else (across, y0 + along)), color)


def bricks(cv, x0, y0, x1, y1, base, mortar, width=8, height=4):
    """Running-bond masonry; every brick gets its own tone and a lit top-left edge."""
    for y in range(y0, y1 + 1):
        row, ry = divmod(y - y0, height)
        offset = width // 2 if row % 2 else 0
        for x in range(x0, x1 + 1):
            column, rx = divmod(x - x0 + offset, width)
            if ry == height - 1 or rx == width - 1:
                cv.put(x, y, shade(mortar, cv.rng.uniform(0.92, 1.06)))
                continue
            tone = random.Random(f"{x0},{y0},{row},{column}").uniform(0.86, 1.08)
            color = shade(base, tone * cv.rng.uniform(0.95, 1.05))
            if ry == 0 or rx == 0:
                color = shade(color, 1.13)
            elif ry == height - 2 or rx == width - 2:
                color = shade(color, 0.9)
            cv.put(x, y, color)


def speckle(cv, x0, y0, x1, y1, base, amount=0.07):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            cv.put(x, y, shade(base, 1 + cv.rng.uniform(-amount, amount)))


def ring(cv, cx, cy, radius, color, width=0.55, x0=0, y0=0, x1=15, y1=15):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if abs(math.hypot(x + 0.5 - cx, y + 0.5 - cy) - radius) < width:
                cv.put(x, y, color)


def flame(cv, x, y, factor=1.0):
    for row, line in enumerate(FLAME_SHAPE):
        for column, key in enumerate(line):
            if key != ".":
                cv.put(x + column, y + row, shade(FLAME[key], factor) if factor != 1.0 else FLAME[key])


# ---- Block textures ---------------------------------------------------------------------------------------------

def gold_corners(cv):
    for (x, y, dx, dy) in ((1, 3, 1, 1), (14, 3, -1, 1), (1, 14, 1, -1), (14, 14, -1, -1)):
        cv.put(x, y, GOLD_LIGHT if dy > 0 else GOLD)
        cv.put(x + dx, y, GOLD)
        cv.put(x, y + dy, GOLD_DARK if dy > 0 else GOLD)


def deconstruction_side(core, glow):
    cv = Canvas(16, 16, "decon_side")
    planks(cv, 0, 0, 15, 15, GREATWOOD, plank=4, horizontal=False)
    cv.tint(0, 3, 0, 14, 0.78)
    cv.tint(15, 3, 15, 14, 0.78)
    for x in range(16):
        cv.put(x, 0, shade(GREATWOOD_EDGE, 1.1 + cv.rng.uniform(-0.05, 0.05)))
        cv.put(x, 1, shade(GREATWOOD_EDGE, 0.9 + cv.rng.uniform(-0.05, 0.05)))
        cv.put(x, 2, shade(GREATWOOD, 0.45))
        cv.put(x, 15, shade(GREATWOOD, 0.5))
    gold_corners(cv)
    # A small gold-rimmed lens showing which variant this is.
    rim = [(7, 7), (8, 7), (6, 8), (9, 8), (6, 9), (9, 9), (7, 10), (8, 10)]
    for x, y in rim:
        cv.put(x, y, GOLD if y < 9 else GOLD_DARK)
    cv.put(7, 8, glow)
    cv.put(8, 8, core)
    cv.put(7, 9, core)
    cv.put(8, 9, shade(core, 0.75))
    cv.put(6, 7, shade(GREATWOOD, 0.55))
    cv.put(9, 7, shade(GREATWOOD, 0.55))
    return cv


def deconstruction_bottom():
    cv = Canvas(16, 16, "decon_bottom")
    planks(cv, 0, 0, 15, 15, shade(GREATWOOD, 0.85), plank=4, horizontal=True)
    cv.bevel(0, 0, 15, 15, shade(GREATWOOD, 0.5), shade(GREATWOOD, 0.4))
    return cv


def deconstruction_top(variant):
    cv = Canvas(16, 16, f"decon_top_{variant}")
    planks(cv, 0, 0, 15, 15, GREATWOOD_EDGE, plank=4, horizontal=True)
    if variant == "x":
        slab, line, light, core = rgb(0xCFC8B6), rgb(0xB07E26), GOLD, rgb(0xFFF4C0)
    else:
        slab, line, light, core = rgb(0x2E2230), rgb(0xE0561C), rgb(0xFFA040), rgb(0xFFE6A0)
    speckle(cv, 2, 2, 13, 13, slab, 0.06)
    for i in range(2, 14):
        cv.put(i, 2, shade(cv.get(i, 2), 0.72))
        cv.put(2, i, shade(cv.get(2, i), 0.8))
    ring(cv, 8, 8, 4.7, line, 0.5, 2, 2, 13, 13)
    if variant == "x":
        # Order: a diamond inside the circle.
        for x in range(4, 12):
            for y in range(4, 12):
                if abs(abs(x + 0.5 - 8) + abs(y + 0.5 - 8) - 3) < 0.5:
                    cv.put(x, y, line)
    else:
        # Fire: an upward triangle inside the circle.
        for y in range(5, 11):
            half = (y - 5) * 0.5 + 0.5
            for x in range(4, 12):
                edge = abs(x + 0.5 - 8)
                if y == 10 and edge <= half or half - 1 < edge <= half:
                    cv.put(x, y, line)
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
        cv.put(x, y, light)
    cv.put(7, 7, core)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        cv.put(x, y, GOLD)
    return cv


def casing_frame(cv, seed_tone=1.0):
    """Two-pixel arcane stone rim shared by the reactor faces, with rivets inside the corners."""
    for y in range(16):
        for x in range(16):
            if x < 2 or y < 2 or x > 13 or y > 13:
                c = shade(SLATE, seed_tone * cv.rng.uniform(0.9, 1.08))
                if x == 0 or y == 0:
                    c = shade(c, 1.2)
                if x == 15 or y == 15:
                    c = shade(c, 0.7)
                cv.put(x, y, c)
    cv.bevel(2, 2, 13, 13, SLATE_DARK, shade(SLATE, 1.25))


def node_reactor_side():
    cv = Canvas(16, 16, "reactor_side")
    casing_frame(cv)
    speckle(cv, 3, 3, 12, 12, THAUMIUM, 0.06)
    for x in range(3, 13):
        cv.put(x, 3, shade(cv.get(x, 3), 1.22))
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        cv.put(x, y, rgb(0xD8CEF0))
    glow = [rgb(0x3A2458), rgb(0x8A54D8), rgb(0xD6A8FF), rgb(0xF4E6FF), rgb(0xD6A8FF), rgb(0x8A54D8)]
    for x in (5, 7, 8, 10):
        for i, y in enumerate(range(5, 11)):
            cv.put(x, y, glow[i] if x in (7, 8) else shade(glow[i], 0.7))
        cv.put(x, 4, SLATE_DARK)
        cv.put(x, 11, shade(THAUMIUM_LIGHT, 1.0))
    return cv


def node_reactor_top():
    cv = Canvas(16, 16, "reactor_top")
    casing_frame(cv)
    speckle(cv, 3, 3, 12, 12, shade(THAUMIUM, 0.75), 0.05)
    # A glass window over the captured node: radial falloff twisted into a swirl.
    for y in range(3, 13):
        for x in range(3, 13):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            r = math.hypot(dx, dy)
            if r < 4.1:
                swirl = 0.5 + 0.5 * math.sin(math.atan2(dy, dx) * 2 + r * 1.6)
                t = max(0.0, 1 - r / 4.1)
                c = mix(rgb(0x2C1648), rgb(0xC060E8), t * 1.2 + swirl * 0.25)
                if r < 1.6:
                    c = mix(c, rgb(0xFFF4FF), 1 - r / 1.6 + 0.2)
                cv.put(x, y, c)
    ring(cv, 8, 8, 4.6, THAUMIUM_LIGHT, 0.5, 2, 2, 13, 13)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        cv.put(x, y, rgb(0xD8CEF0))
    return cv


def node_reactor_bottom():
    cv = Canvas(16, 16, "reactor_bottom")
    casing_frame(cv, 0.9)
    speckle(cv, 3, 3, 12, 12, shade(THAUMIUM, 0.8), 0.05)
    for y in range(4, 12, 2):
        for x in range(4, 12):
            cv.put(x, y, SLATE_DARK)
    ring(cv, 8, 8, 2.2, BRASS, 0.6, 3, 3, 12, 12)
    return cv


def primal_furnace_side():
    cv = Canvas(16, 16, "furnace_side")
    bricks(cv, 0, 0, 15, 15, ARCANE_STONE, MORTAR, 8, 4)
    for x in range(16):
        cv.put(x, 0, BRASS_LIGHT if x % 5 else BRASS_DARK)
        cv.put(x, 15, BRASS_DARK if x % 5 else rgb(0x4A3218))
    # Six primal gems set into a dark band.
    cv.rect(1, 3, 14, 5, shade(MORTAR, 0.7))
    for color, x in zip(PRIMALS, (2, 4, 6, 9, 11, 13)):
        cv.put(x, 4, color)
        cv.put(x, 3, shade(color, 0.55))
    # Arched furnace mouth with the fire inside.
    mouth = {7: (6, 9), 8: (5, 10), 9: (4, 11), 10: (4, 11), 11: (4, 11), 12: (4, 11), 13: (4, 11)}
    for y, (a, b) in mouth.items():
        cv.put(a - 1, y, rgb(0x24222A))
        cv.put(b + 1, y, rgb(0x24222A))
        for x in range(a, b + 1):
            heat = (y - 7) / 6
            c = mix(rgb(0x2A0A06), rgb(0xFF7A10), heat * 1.15)
            if y >= 12 and cv.rng.random() < 0.55:
                c = rgb(0xFFD040)
            cv.put(x, y, c)
    cv.rect(6, 6, 9, 6, rgb(0x24222A))
    cv.rect(3, 14, 12, 14, shade(MORTAR, 0.8))
    return cv


def primal_furnace_top():
    cv = Canvas(16, 16, "furnace_top")
    bricks(cv, 0, 0, 15, 15, ARCANE_STONE, MORTAR, 8, 4)
    cv.bevel(0, 0, 15, 15, BRASS_LIGHT, BRASS_DARK)
    cv.rect(3, 3, 12, 12, rgb(0x1C1A20))
    for y in range(4, 12):
        for x in range(4, 12):
            if x % 2 == 0:
                cv.put(x, y, mix(rgb(0xB03010), rgb(0xFFC040), cv.rng.random()))
    cv.bevel(3, 3, 12, 12, BRASS_DARK, BRASS_LIGHT)
    # Collar for the essentia tube that leaves through the top.
    cv.rect(6, 6, 9, 9, BRASS)
    cv.bevel(6, 6, 9, 9, BRASS_LIGHT, BRASS_DARK)
    cv.rect(7, 7, 8, 8, rgb(0x2A1A10))
    return cv


def primal_furnace_bottom():
    cv = Canvas(16, 16, "furnace_bottom")
    bricks(cv, 0, 0, 15, 15, shade(ARCANE_STONE, 0.85), MORTAR, 8, 4)
    cv.bevel(0, 0, 15, 15, BRASS, BRASS_DARK)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        cv.put(x, y, BRASS_LIGHT)
    return cv


def essentia_tube():
    """A 3x8 tube side at (0,0) and a 3x3 end cap at (4,0); the models map these with explicit UVs."""
    cv = Canvas(16, 16, "tube")
    brass = [BRASS_LIGHT, BRASS, BRASS_DARK]
    glass = [rgb(0xE4F0F4), rgb(0xA888DC), rgb(0x6A5A96)]
    for column in range(3):
        for row in range(8):
            cv.put(column, row, brass[column] if row in (0, 7) else glass[column])
        cv.put(column, 4, shade(glass[column], 1.12))
    cv.rect(4, 0, 6, 2, BRASS)
    cv.put(4, 0, BRASS_LIGHT)
    cv.put(6, 2, BRASS_DARK)
    cv.put(5, 1, rgb(0x6A3EA8))
    return cv


# ---- GUI textures -----------------------------------------------------------------------------------------------

INVENTORY = rgb(0xC6C6C6)


def window_frame(cv, width, height):
    """Outline with clipped corners and a lit inner rim, over whatever material is already painted."""
    outline = rgb(0x140E0A)
    for x in range(width):
        for y in range(height):
            corner = (x < 2 or x > width - 3) and (y < 2 or y > height - 3)
            if corner and (x in (0, width - 1) or y in (0, height - 1)):
                cv.put(x, y, (0, 0, 0, 0))
    for x in range(2, width - 2):
        cv.put(x, 0, outline)
        cv.put(x, height - 1, outline)
    for y in range(2, height - 2):
        cv.put(0, y, outline)
        cv.put(width - 1, y, outline)
    for x, y in ((1, 1), (width - 2, 1), (1, height - 2), (width - 2, height - 2)):
        cv.put(x, y, outline)
    for x in range(2, width - 2):
        cv.put(x, 1, shade(cv.get(x, 1), 1.35))
        cv.put(x, height - 2, shade(cv.get(x, height - 2), 0.6))
    for y in range(2, height - 2):
        cv.put(1, y, shade(cv.get(1, y), 1.3))
        cv.put(width - 2, y, shade(cv.get(width - 2, y), 0.65))


def stud(cv, x, y):
    cv.put(x, y, GOLD_LIGHT)
    cv.put(x + 1, y, GOLD)
    cv.put(x, y + 1, GOLD)
    cv.put(x + 1, y + 1, GOLD_DARK)


def recess(cv, x0, y0, x1, y1, factor):
    cv.tint(x0, y0, x1, y1, factor)
    cv.bevel(x0, y0, x1, y1, shade(cv.get(x0 + 1, y0 + 1), 0.45), shade(cv.get(x0 + 1, y0 + 1), 1.9))


def vanilla_slot(cv, x, y):
    cv.rect(x, y, x + 17, y + 17, rgb(0x8B8B8B))
    cv.bevel(x, y, x + 17, y + 17, rgb(0x373737), rgb(0xFFFFFF))
    cv.put(x + 17, y, rgb(0x8B8B8B))
    cv.put(x, y + 17, rgb(0x8B8B8B))


def dark_slot(cv, x, y, fill=rgb(0x221B16), light=rgb(0x7A6650)):
    cv.rect(x, y, x + 17, y + 17, fill)
    cv.bevel(x, y, x + 17, y + 17, shade(fill, 0.45), light)
    cv.put(x + 17, y, fill)
    cv.put(x, y + 17, fill)


def inventory_panel(cv, x0, y0, x1, y1, slots_x, slots_y, hotbar_y):
    cv.rect(x0, y0, x1, y1, INVENTORY)
    cv.bevel(x0, y0, x1, y1, rgb(0xFFFFFF), rgb(0x555555))
    for row in range(3):
        for column in range(9):
            vanilla_slot(cv, slots_x - 1 + column * 18, slots_y - 1 + row * 18)
    for column in range(9):
        vanilla_slot(cv, slots_x - 1 + column * 18, hotbar_y - 1)


def title_plaque(cv, width, material):
    cv.tint(6, 5, width - 7, 20, material)
    cv.bevel(6, 5, width - 7, 20, shade(cv.get(7, 6), 0.5), shade(cv.get(7, 6), 1.6))
    for x in range(8, width - 8):
        cv.put(x, 22, GOLD_DARK if x % 2 else GOLD)


def deconstruction_gui():
    width, height = 184, 204
    cv = Canvas(256, 256, "gui_decon")
    planks(cv, 0, 0, width - 1, height - 1, GREATWOOD, plank=8, horizontal=True)
    window_frame(cv, width, height)
    title_plaque(cv, width, 0.62)
    recess(cv, 9, 27, 174, 96, 0.55)
    # Input: a gold-framed socket feeding a chevron into the row of result cells.
    cv.rect(16, 34, 39, 57, GOLD_DARK)
    cv.bevel(16, 34, 39, 57, GOLD_LIGHT, shade(GOLD_DARK, 0.7))
    dark_slot(cv, 19, 37)
    for i in range(6):
        dark_slot(cv, 55 + i * 19, 36)
    for x in range(42, 52):
        cv.put(x, 45, GOLD)
        cv.put(x, 44, GOLD_DARK)
    for d in range(4):
        cv.put(51 - d, 45 - d, GOLD)
        cv.put(51 - d, 45 + d, GOLD)
        cv.put(50 - d, 45 - d, GOLD_DARK)
        cv.put(50 - d, 45 + d, GOLD_DARK)
    for x in range(14, 170):
        cv.put(x, 63, shade(cv.get(x, 63), 0.6))
        cv.put(x, 64, shade(cv.get(x, 64), 1.35))
    inventory_panel(cv, 6, 104, width - 7, height - 5, 12, 122, 180)
    for x, y in ((3, 3), (width - 5, 3), (3, height - 5), (width - 5, height - 5)):
        stud(cv, x, y)
    return cv


def primal_furnace_gui():
    width, height = 184, 216
    cv = Canvas(256, 256, "gui_furnace")
    bricks(cv, 0, 0, width - 1, height - 1, shade(ARCANE_STONE, 0.9), MORTAR, 16, 8)
    window_frame(cv, width, height)
    title_plaque(cv, width, 0.55)
    recess(cv, 8, 26, 175, 115, 0.42)
    dark_slot(cv, 19, 33, rgb(0x1E1C24), rgb(0x8A8698))
    dark_slot(cv, 19, 67, rgb(0x241A16), rgb(0xB0702E))
    # Fuel gauge socket: the flame's silhouette in embers until the sprite is drawn over it.
    cv.rect(20, 51, 35, 66, rgb(0x18161C))
    flame(cv, 21, 52, 0.2)
    # Brass feed pipe from the material slot into the progress channel.
    cv.rect(37, 40, 61, 41, BRASS)
    for x in range(37, 62):
        cv.put(x, 39, BRASS_DARK)
        cv.put(x, 40, BRASS_LIGHT)
    cv.rect(62, 34, 169, 44, rgb(0x15131A))
    cv.bevel(62, 34, 169, 44, BRASS_DARK, BRASS_LIGHT)
    cv.bevel(63, 35, 168, 43, rgb(0x0C0B10), rgb(0x2E2B36))
    for row in range(2):
        for column in range(6):
            dark_slot(cv, 62 + column * 18, 64 + row * 18, rgb(0x1E1C24), rgb(0x6E6A80))
    # One gem per primal aspect under the result columns.
    for color, column in zip(PRIMALS, range(6)):
        cx = 71 + column * 18
        for dx, dy in ((0, -2), (-1, -1), (0, -1), (1, -1), (-2, 0), (-1, 0), (0, 0), (1, 0), (2, 0), (-1, 1), (0, 1), (1, 1), (0, 2)):
            edge = abs(dx) + abs(dy) == 2
            c = shade(color, 0.55) if edge else color
            if (dx, dy) == (-1, -1):
                c = mix(color, rgb(0xFFFFFF), 0.6)
            cv.put(cx + dx, 106 + dy, c)
    inventory_panel(cv, 6, 118, width - 7, height - 5, 12, 134, 192)
    for x, y in ((3, 3), (width - 5, 3), (3, height - 5), (width - 5, height - 5)):
        stud(cv, x, y)
    # Sprites: progress fill (104x7) at (0,220) and the lit flame (14x14) at (110,220).
    for x in range(104):
        position = x / 103 * (len(PRIMALS) - 1)
        low = int(position)
        color = mix(PRIMALS[low], PRIMALS[min(low + 1, len(PRIMALS) - 1)], position - low)
        for y in range(7):
            factor = (1.35, 1.15, 1.0, 1.0, 0.92, 0.82, 0.7)[y]
            wave = 1 + 0.08 * math.sin(x * 0.55 + y * 0.9)
            cv.put(x, 220 + y, shade(color, factor * wave))
    flame(cv, 110, 220)
    return cv


# ---- Models -----------------------------------------------------------------------------------------------------

TUBES = {
    "node_reactor_tube_down": ([6.5, -8, 6.5], [9.5, 0, 9.5], "y", "up"),
    "node_reactor_tube_north": ([6.5, 6.5, -8], [9.5, 9.5, 0], "z", "south"),
    "node_reactor_tube_south": ([6.5, 6.5, 16], [9.5, 9.5, 24], "z", "north"),
    "node_reactor_tube_west": ([-8, 6.5, 6.5], [0, 9.5, 9.5], "x", "east"),
    "node_reactor_tube_east": ([16, 6.5, 6.5], [24, 9.5, 9.5], "x", "west"),
    "primal_furnace_tube_up": ([6.5, 16, 6.5], [9.5, 24, 9.5], "y", "down"),
}
AXIS_FACES = {"x": ("east", "west"), "y": ("up", "down"), "z": ("north", "south")}


def tube_model(start, end, axis, hidden):
    faces = {}
    for face in ("down", "up", "north", "south", "west", "east"):
        if face == hidden:
            continue  # flush against the machine's own face
        if face in AXIS_FACES[axis]:
            faces[face] = {"uv": [4, 0, 7, 3], "texture": "#pipe"}
            continue
        entry = {"uv": [0, 0, 3, 8], "texture": "#pipe"}
        # The side strip runs along v; faces whose v axis is across the tube need it turned.
        if axis == "x" or (axis == "z" and face in ("east", "west")):
            entry["rotation"] = 90
        faces[face] = entry
    return {
        "render_type": "minecraft:cutout",
        "textures": {"pipe": "tc4modern:block/essentia_tube", "particle": "tc4modern:block/essentia_tube"},
        "elements": [{"from": start, "to": end, "faces": faces}],
    }


def cube(top, side, bottom):
    return {"parent": "minecraft:block/cube_bottom_top",
            "textures": {"top": f"tc4modern:block/{top}", "side": f"tc4modern:block/{side}", "bottom": f"tc4modern:block/{bottom}"}}


def write_json(path, data):
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    textures = {
        "deconstruction_x_side": deconstruction_side(rgb(0xFFE070), rgb(0xFFFBE0)),
        "deconstruction_y_side": deconstruction_side(rgb(0xFF6A18), rgb(0xFFD080)),
        "deconstruction_bottom": deconstruction_bottom(),
        "deconstruction_x_top": deconstruction_top("x"),
        "deconstruction_y_top": deconstruction_top("y"),
        "node_reactor_side": node_reactor_side(),
        "node_reactor_top": node_reactor_top(),
        "node_reactor_bottom": node_reactor_bottom(),
        "primal_furnace_side": primal_furnace_side(),
        "primal_furnace_top": primal_furnace_top(),
        "primal_furnace_bottom": primal_furnace_bottom(),
        "essentia_tube": essentia_tube(),
    }
    for name, canvas in textures.items():
        canvas.save(BLOCK / f"{name}.png")
    deconstruction_gui().save(GUI / "deconstruction.png")
    primal_furnace_gui().save(GUI / "primal_furnace.png")

    write_json(MODELS / "deconstruction_x.json", cube("deconstruction_x_top", "deconstruction_x_side", "deconstruction_bottom"))
    write_json(MODELS / "deconstruction_y.json", cube("deconstruction_y_top", "deconstruction_y_side", "deconstruction_bottom"))
    write_json(MODELS / "node_reactor.json", cube("node_reactor_top", "node_reactor_side", "node_reactor_bottom"))
    write_json(MODELS / "primal_furnace.json", cube("primal_furnace_top", "primal_furnace_side", "primal_furnace_bottom"))
    for name, (start, end, axis, hidden) in TUBES.items():
        write_json(MODELS / f"{name}.json", tube_model(start, end, axis, hidden))


if __name__ == "__main__":
    main()
