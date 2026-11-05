"""Draws the writing tool item icons and the leather pen case GUI, reusing the helpers of generate_machine_textures.

Everything is seeded, so re-running reproduces the same PNGs. The machine textures are left alone.

    python tools/generate_writing_textures.py
"""
from generate_machine_textures import (
    ASSETS, BRASS, BRASS_DARK, BRASS_LIGHT, Canvas, GOLD, GOLD_DARK, GOLD_LIGHT, GUI, dark_slot, inventory_panel, mix,
    recess, rgb, shade, stud, title_plaque, window_frame,
)
import math

ITEM = ASSETS / "textures/item"


# ---- Item icons -------------------------------------------------------------------------------------------------
#
# Every icon lies on the 45 degree diagonal with its writing end at the bottom left. Pixels are addressed as
#   a = x - y + 15   how far along the tool (2 at the bottom left, 28 at the top right)
#   c = x + y - 15   how far across it (negative is the lit upper-left side)
# which share parity, so a shape is just a function of (a, c).

def along(colors, c):
    """Light, base and shadow stripes of a three pixel wide body."""
    return colors[0] if c < 0 else colors[2] if c > 0 else colors[1]


def icon(name, paint, outline_factor=0.42):
    cv = Canvas(16, 16, name)
    for y in range(16):
        for x in range(16):
            color = paint(x - y + 15, x + y - 15)
            if color is not None:
                cv.put(x, y, color)
    # Selective outline: each edge takes a darkened tone of the body it wraps, the way vanilla item art does.
    source = cv.img.copy().load()
    for y in range(16):
        for x in range(16):
            if source[x, y][3]:
                continue
            around = [source[i, j] for i, j in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1))
                      if 0 <= i < 16 and 0 <= j < 16 and source[i, j][3]]
            # Graphite points stay bare: outlining a one pixel point would clump it into an X.
            around = [color for color in around if color != GRAPHITE]
            if around:
                darkest = min(around, key=sum)
                cv.put(x, y, shade(darkest, outline_factor))
    return cv


AMBER = (rgb(0xFFD966), rgb(0xE8A92C), rgb(0xB2701A))
ERASER = (rgb(0xF7B6B0), rgb(0xE38A86), rgb(0xB9605F))
CEDAR = (rgb(0xF0D2A0), rgb(0xD9B27A), rgb(0xB48A58))
FERRULE = (rgb(0xFFF0B0), rgb(0xD8B04C), rgb(0x8E6A24))
GRAPHITE = rgb(0x34363E)


def pencil(a, c):
    """A sharpened hex pencil: graphite point, bare cedar cone, three amber facets, brass ferrule, pink eraser."""
    if a < 2 or a > 28 or abs(c) > 1:
        return None
    if a <= 4:
        return GRAPHITE if c == 0 else None
    if a <= 9:
        return along(CEDAR, c)
    if a <= 22:
        return along(AMBER, c)
    if a <= 25:
        return shade(along(FERRULE, c), 0.72) if a == 24 else along(FERRULE, c)
    if a == 28 and c != 0:
        return None
    return along(ERASER, c)


STEEL = (rgb(0xF4F7FA), rgb(0xBAC4CF), rgb(0x7D8896))
GRIP = (rgb(0x565C6C), rgb(0x3A3F4C), rgb(0x262A34))
CHROME = (rgb(0xFFFFFF), rgb(0xD6DEE6), rgb(0x8E98A6))


def mechanical_pencil(a, c):
    """Fine lead, chrome cone, knurled rubber grip, steel barrel with a pocket clip and a push button."""
    if a < 2 or a > 27:
        return None
    if c == -2:
        return (rgb(0x5E6876) if a == 18 else rgb(0xE8EEF4) if a % 4 == 0 else rgb(0xA4AEBC)) if 18 <= a <= 24 else None
    if abs(c) > 1:
        return None
    if a <= 3:
        return GRAPHITE if a == 2 else None
    if a <= 7:
        return along(CHROME, c)
    if a <= 13:
        return shade(along(GRIP, c), 1.35) if a % 4 == 0 else along(GRIP, c)
    if a == 14:
        return along(CHROME, c)
    if a <= 24:
        return along(STEEL, c)
    if a == 25:
        return along(GRIP, c)
    return along(CHROME, c) if c == 0 or a == 26 else None


RESIN = (rgb(0x4D74C2), rgb(0x22366F), rgb(0x121C42))
SECTION = (rgb(0x444A5C), rgb(0x20232E), rgb(0x12141B))
NIB = (rgb(0xFFF2A8), rgb(0xE8BA48), rgb(0xA87A24))


def fountain_pen(a, c):
    """A split gold nib with a breather hole, black section, gold rings, lacquered resin barrel and gold finial."""
    if a < 2 or a > 28:
        return None
    if a <= 9:
        reach = 0 if a <= 3 else 1 if a <= 7 else 2 if a == 8 else 1
        if abs(c) > reach:
            return None
        if c == 0 and 4 <= a <= 6:
            return rgb(0x5A3A18)
        if c == 0 and a == 8:
            return rgb(0x2A1A10)
        return along(NIB, c)
    if abs(c) > 1:
        return None
    if a <= 12:
        return along(SECTION, c)
    if a in (13, 14, 25, 28):
        return along(NIB, c)
    if c == -1 and a == 19:
        return rgb(0xC4D6F4)  # lacquer glint
    return along(RESIN, c)


GLASS = (rgb(0xF2F8FC), rgb(0xC8DAE6), rgb(0x8EA6B8))
INK = (rgb(0x86B6F2), rgb(0x2C5FC8), rgb(0x173A8E))


def pen_refill(a, c):
    """A short cartridge: steel collar and needle, ink showing through clear walls up to a meniscus, sealed plug."""
    if a < 4 or a > 26:
        return None
    if a <= 5:
        return along(CHROME, c) if c == 0 else None
    if abs(c) > 1:
        return None
    if a <= 7:
        return along(CHROME, c)
    if a == 8:
        return along(GRIP, c)
    if a <= 19:
        return rgb(0xD8E8FA) if c == -1 and a in (11, 15) else along(INK, c)
    if a == 20:
        return along((rgb(0xB0D0F4), rgb(0x7AA4E0), rgb(0x4C74B4)), c)
    if a <= 23:
        return along(GLASS, c)
    if a == 26 and c != 0:
        return None
    return along(SECTION, c)


LEAD = (rgb(0xB4BAC6), rgb(0x747A88), rgb(0x565C6A))
BAND = (rgb(0xE86A52), rgb(0xB83C2C), rgb(0x7E2418))


def pencil_lead(a, c):
    """Three upright leads of staggered length, tied around the middle with a red paper band. Upright keeps them crisp."""
    x, y = (a + c) // 2, (c - a + 30) // 2
    if 8 <= y <= 10 and 4 <= x <= 11:
        return BAND[0] if y == 8 or x == 4 else BAND[2] if y == 10 or x == 11 else BAND[1]
    for left, top, bottom in ((4, 4, 12), (7, 2, 13), (10, 5, 12)):
        if x in (left, left + 1) and top <= y <= bottom:
            if y == top:
                return LEAD[1] if x == left else None  # pointed tip
            return LEAD[0] if x == left else LEAD[2] if y == bottom else LEAD[1]
    return None


ITEMS = {
    "pencil": pencil,
    "mechanical_pencil": mechanical_pencil,
    "fountain_pen": fountain_pen,
    "pen_refill": pen_refill,
    "pencil_lead": pencil_lead,
}


# ---- GUI --------------------------------------------------------------------------------------------------------

LEATHER = rgb(0x5A2E26)
THREAD = rgb(0xD8BE8A)
PARCHMENT = rgb(0xDCCB9E)
# Keep these in step with WritingToolMenu and WritingToolScreen.
WIDTH, HEIGHT = 184, 204
GAUGE_X, GAUGE_Y, GAUGE_W, GAUGE_H = 80, 42, 88, 7
CRADLE_X, CRADLE_Y = 65, 45
SPRITE_V = 208


def leather(cv, x0, y0, x1, y1, base):
    """Soft mottling from crossed sine waves, plus fine grain and the odd darker pore."""
    rng = cv.rng
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            mottling = math.sin(x * 0.17 + y * 0.05) + math.sin(y * 0.23 - x * 0.08 + 1.7)
            tone = 1 + 0.035 * mottling + rng.uniform(-0.035, 0.035)
            if rng.random() < 0.04:
                tone *= 0.84
            cv.put(x, y, shade(base, tone))


def stitches(cv, width, height):
    def dash(x, y, position):
        if position % 5 < 3:
            cv.put(x, y, shade(THREAD, 1.0 if position % 5 else 0.82))
        else:
            cv.put(x, y, shade(cv.get(x, y), 0.72))  # the needle hole pulls the leather in
    for x in range(8, width - 8):
        dash(x, 3, x)
        dash(x, height - 4, x)
    for y in range(8, height - 8):
        dash(3, y, y)
        dash(width - 4, y, y)


def parchment(cv, x0, y0, x1, y1):
    """A note slip whose edges are nibbled by a pixel or two, with a soft drop shadow under it."""
    rng = cv.rng
    top = [rng.random() < 0.09 for _ in range(x1 - x0 + 1)]
    bottom = [rng.random() < 0.09 for _ in range(x1 - x0 + 1)]
    for y in range(y0 + 1, y1 + 2):
        cv.put(x1 + 1, y, shade(cv.get(x1 + 1, y), 0.55))
    for x in range(x0 + 1, x1 + 2):
        cv.put(x, y1 + 1, shade(cv.get(x, y1 + 1), 0.55))
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if (y == y0 and top[x - x0]) or (y == y1 and bottom[x - x0]):
                continue
            edge = min(x - x0, x1 - x, y - y0, y1 - y)
            aged = 1 - 0.09 * max(0, 3 - edge) / 3
            cv.put(x, y, shade(PARCHMENT, aged * (1 + rng.uniform(-0.022, 0.022))))
    # Ruled line under the capacity readout.
    for x in range(x0 + 4, x1 - 3):
        cv.put(x, y0 + 12, shade(cv.get(x, y0 + 12), 0.78))


def chevron(cv, x0, x1, y):
    for x in range(x0, x1):
        cv.put(x, y, GOLD)
        cv.put(x, y - 1, GOLD_DARK)
    for d in range(4):
        cv.put(x1 - 1 - d, y - d, GOLD)
        cv.put(x1 - 1 - d, y + d, GOLD)
        cv.put(x1 - 2 - d, y - d, GOLD_DARK)
        cv.put(x1 - 2 - d, y + d, GOLD_DARK)


def cradle(cv, cx, cy):
    """A dark round pad holding the tool, its brass rim lit from the upper left like every other bevel."""
    for y in range(cy - 12, cy + 13):
        for x in range(cx - 12, cx + 13):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            radius = math.hypot(dx, dy)
            if radius < 9.9:
                cv.put(x, y, mix(rgb(0x2A1E22), rgb(0x150F12), (dx + dy + 10) / 20))
            elif radius < 11:
                lit = (dx + dy) / radius
                cv.put(x, y, BRASS_LIGHT if lit < -0.75 else BRASS_DARK if lit > 0.75 else BRASS)


def silhouette(cv, x0, y0, source):
    """Bakes an item icon as a dim shape to mark what the empty refill slot takes."""
    for y in range(16):
        for x in range(16):
            r, g, b, alpha = source.get(x, y)
            if alpha and (r + g + b) > 150:
                cv.put(x0 + x, y0 + y, rgb(0x4A3C30))


def writing_tool_gui(icons):
    cv = Canvas(256, 256, "gui_writing_tool")
    leather(cv, 0, 0, WIDTH - 1, HEIGHT - 1, LEATHER)
    window_frame(cv, WIDTH, HEIGHT)
    stitches(cv, WIDTH, HEIGHT)
    title_plaque(cv, WIDTH, 0.6)
    recess(cv, 9, 27, 174, 96, 0.5)
    # Refill socket, feeding the tool on its cradle, feeding the capacity gauge.
    cv.rect(16, 34, 39, 57, GOLD_DARK)
    cv.bevel(16, 34, 39, 57, GOLD_LIGHT, shade(GOLD_DARK, 0.7))
    dark_slot(cv, 19, 37)
    chevron(cv, 42, 52, 45)
    cradle(cv, CRADLE_X, CRADLE_Y)
    cv.rect(GAUGE_X - 2, GAUGE_Y - 2, GAUGE_X + GAUGE_W + 1, GAUGE_Y + GAUGE_H + 1, rgb(0x15131A))
    cv.bevel(GAUGE_X - 2, GAUGE_Y - 2, GAUGE_X + GAUGE_W + 1, GAUGE_Y + GAUGE_H + 1, BRASS_DARK, BRASS_LIGHT)
    cv.bevel(GAUGE_X - 1, GAUGE_Y - 1, GAUGE_X + GAUGE_W, GAUGE_Y + GAUGE_H, rgb(0x0C0B10), rgb(0x2E2B36))
    parchment(cv, 14, 62, 169, 93)
    inventory_panel(cv, 6, 104, WIDTH - 7, HEIGHT - 5, 12, 122, 180)
    for x, y in ((3, 3), (WIDTH - 5, 3), (3, HEIGHT - 5), (WIDTH - 5, HEIGHT - 5)):
        stud(cv, x, y)
    # Sprites: gauge fills in ink (row 0) and graphite (row 1), then the empty slot marks for leads and refills.
    bands = (1.35, 1.15, 1.0, 1.0, 0.92, 0.82, 0.68)
    for x in range(GAUGE_W):
        for y in range(GAUGE_H):
            ink = mix(rgb(0x2C5FC8), rgb(0x5C92EC), 0.5 + 0.5 * math.sin(x * 0.4 + y * 0.7))
            cv.put(x, SPRITE_V + y, shade(ink, bands[y]))
            hatch = 1.14 if (x + y) % 4 == 0 else 1.0
            cv.put(x, SPRITE_V + 8 + y, shade(rgb(0x6E7482), bands[y] * hatch))
    silhouette(cv, 92, SPRITE_V, icons["pencil_lead"])
    silhouette(cv, 110, SPRITE_V, icons["pen_refill"])
    return cv


def main():
    icons = {name: icon(name, paint) for name, paint in ITEMS.items()}
    for name, canvas in icons.items():
        canvas.save(ITEM / f"{name}.png")
    writing_tool_gui(icons).save(GUI / "writing_tool.png")


if __name__ == "__main__":
    main()
