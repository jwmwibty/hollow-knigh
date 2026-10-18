#!/usr/bin/env python3
"""
verify_map.py — static reachability check for the connected world map.

Parses the room layouts straight out of the Kotlin sources, rebuilds the
world exactly like GameWorld.loadContinuousWorld() does (same offsets, same
internal-wall stripping), then checks with a jump-physics model that:

  * the spawn can reach every boss arena and every bench with SINGLE jumps
  * every platform is reachable somehow (double-jump/dash routes flagged)
  * every bench / soul cache stands on real ground
  * every enemy stands on (or hovers just over) real ground
  * no spawn / bench / cache point sits inside a hazard

Jump model (from Player.kt): run speed 260, jump velocity -540,
gravity 1250  ->  single-jump apex ~116.6px. Max horizontal gap for a
given rise is computed from the projectile flight time and then shrunk
by a 15% safety margin, so anything this script calls single-jumpable
is comfortably jumpable in game.

Usage:
    python3 tools/verify_map.py                # checks only
    python3 tools/verify_map.py --render map.png   # + draw a map preview

Exit code 0 = all hard checks passed. Warnings do not fail the build.
"""
import math
import re
import sys
from collections import deque
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GAME = ROOT / "app/src/main/java/com/devdgames/hollowknightbutmine/game"

NUM = r"(-?\d+(?:\.\d+)?)f?"
RECT_RE = re.compile(rf"(?:addSlab|addCutStone|addBone)\(RectF\(\s*{NUM},\s*{NUM},\s*{NUM},\s*{NUM}\)")
SOLID_RE = re.compile(rf"\bsolid\(\s*{NUM},\s*{NUM},\s*{NUM},\s*{NUM}\)")
HAZARD_RE = re.compile(rf"\bhazard\(\s*{NUM},\s*{NUM},\s*{NUM},\s*{NUM}\)")
BOUNDS_RE = re.compile(rf"bounds = RectF\(\s*{NUM},\s*{NUM},\s*{NUM},\s*{NUM}\)")
BENCH_RE = re.compile(rf"\bbench\(\s*{NUM},\s*{NUM}\)")
CACHE_RE = re.compile(rf"\bcache\(\s*{NUM},\s*{NUM}\)")
ENEMY_RE = re.compile(
    r"EnemySpawn\(x = (-?\d+(?:\.\d+)?)f, y = (-?\d+(?:\.\d+)?)f"
    r"(?:, boss = (true|false))?(?:, type = EnemyType\.(\w+))?"
)
ROOM_ID_RE = re.compile(r'id = "(\w+)"')
PLACEMENT_RE = re.compile(r'rooms\.getValue\("(\w+)"\),\s*(-?\d+(?:\.\d+)?)f')

# Player physics (Player.kt)
RUN = 260.0
JUMP_V = 540.0
GRAV = 1250.0
SAFETY = 0.85

FLYING = {"FLYER", "BROODMOTHER"}
ENEMY_W = {
    "CRAWLER": 46, "FLYER": 38, "SPITTER": 44, "SHIELDER": 50,
    "MOSS_CHARGER": 68, "BROODMOTHER": 82, "OSSUARY_TITAN": 96, "COLONNADE_KNIGHT": 54,
}


def fnum(s):
    return float(s)


class Rect:
    __slots__ = ("l", "t", "r", "b", "room")

    def __init__(self, l, t, r, b, room=""):
        self.l, self.t, self.r, self.b, self.room = l, t, r, b, room

    @property
    def w(self):
        return self.r - self.l

    @property
    def h(self):
        return self.b - self.t

    def shifted(self, dx):
        return Rect(self.l + dx, self.t, self.r + dx, self.b, self.room)

    def contains_x(self, x, pad=2.0):
        return self.l - pad <= x <= self.r + pad

    def __repr__(self):
        return f"Rect({self.l:.0f},{self.t:.0f},{self.r:.0f},{self.b:.0f} {self.room})"


def parse_room(path: Path):
    src = path.read_text()
    room_id = ROOM_ID_RE.search(src).group(1)
    bm = BOUNDS_RE.search(src)
    bounds = Rect(*(fnum(v) for v in bm.groups()), room_id)
    plats = [Rect(*(fnum(v) for v in m.groups()), room_id) for m in RECT_RE.finditer(src)]
    plats += [Rect(*(fnum(v) for v in m.groups()), room_id) for m in SOLID_RE.finditer(src)]
    hazards = [Rect(*(fnum(v) for v in m.groups()), room_id) for m in HAZARD_RE.finditer(src)]
    benches = [(fnum(a), fnum(b), room_id) for a, b in BENCH_RE.findall(src)]
    caches = [(fnum(a), fnum(b), room_id) for a, b in CACHE_RE.findall(src)]
    enemies = []
    for m in ENEMY_RE.finditer(src):
        x, y, boss, typ = m.groups()
        enemies.append({"x": float(x), "y": float(y), "boss": boss == "true",
                        "type": typ or "CRAWLER", "room": room_id})
    sx = re.search(r"spawnX = (-?\d+(?:\.\d+)?)f", src)
    sy = re.search(r"spawnY = (-?\d+(?:\.\d+)?)f", src)
    return {
        "id": room_id, "bounds": bounds, "platforms": plats, "hazards": hazards,
        "benches": benches, "caches": caches, "enemies": enemies,
        "spawn": (float(sx.group(1)), float(sy.group(1))),
    }


def max_gap_single(rise: float) -> float:
    """Max horizontal gap clearable landing `rise` px higher (rise<0 = lower)."""
    disc = JUMP_V * JUMP_V - 2 * GRAV * rise
    if disc < 0:
        return -1.0
    t_land = (JUMP_V + math.sqrt(disc)) / GRAV
    return RUN * t_land * SAFETY - 8.0


def max_gap_double(rise: float) -> float:
    if rise > 205:
        return -1.0
    return 350.0 - rise * 0.75


def h_gap(a: Rect, b: Rect) -> float:
    return max(0.0, max(b.l - a.r, a.l - b.r))


def build_graph(plats, allow_double=False):
    n = len(plats)
    adj = [[] for _ in range(n)]
    for i, a in enumerate(plats):
        for j, b in enumerate(plats):
            if i == j:
                continue
            gap = h_gap(a, b)
            rise = a.t - b.t  # positive: b is higher
            if gap == 0.0 and rise <= 0:
                # drop onto a lower overlapping platform (walk off edge + drift)
                adj[i].append(j)
                continue
            mg = max_gap_single(rise)
            if allow_double:
                mg = max(mg, max_gap_double(rise))
            if mg >= 0 and gap <= mg:
                adj[i].append(j)
    return adj


def bfs(adj, start):
    seen = {start}
    q = deque([start])
    while q:
        u = q.popleft()
        for v in adj[u]:
            if v not in seen:
                seen.add(v)
                q.append(v)
    return seen


def platform_under(plats, x, y, tol=4.0):
    """Platform whose top is exactly at feet-y `y` under x, else None."""
    for i, p in enumerate(plats):
        if p.contains_x(x) and abs(p.t - y) <= tol:
            return i
    return None


def platform_below(plats, x, y, max_drop=260.0):
    best, best_top = None, None
    for i, p in enumerate(plats):
        if p.contains_x(x) and y - 2 <= p.t <= y + max_drop:
            if best_top is None or p.t < best_top:
                best, best_top = i, p.t
    return best


def main():
    render_path = None
    if "--render" in sys.argv:
        render_path = sys.argv[sys.argv.index("--render") + 1]

    # Placement offsets from GameWorld.kt (single source of truth).
    gw = (GAME / "GameWorld.kt").read_text()
    placements = [(m.group(1), float(m.group(2))) for m in PLACEMENT_RE.finditer(gw)]
    offset_of = dict(placements)
    order = [p[0] for p in placements]

    rooms = {}
    for f in (GAME / "env").glob("*Room.kt"):
        if f.name == "Room.kt":
            continue
        r = parse_room(f)
        rooms[r["id"]] = r

    failures, warnings = [], []

    # Rebuild world geometry like GameWorld.loadContinuousWorld().
    world_plats, world_hazards = [], []
    world_left = None
    world_right = None
    for idx, rid in enumerate(order):
        r = rooms[rid]
        off = offset_of[rid]
        b = r["bounds"]
        if idx == 0:
            world_left = b.l
        if idx == len(order) - 1:
            world_right = off + b.r
        for p in r["platforms"]:
            is_lw = p.h > 800 and abs(p.l - b.l) < 1
            is_rw = p.h > 800 and abs(p.r - b.r) < 1
            is_outer = (idx == 0 and is_lw) or (idx == len(order) - 1 and is_rw)
            if (not is_lw and not is_rw) or is_outer:
                world_plats.append(p.shifted(off))
        world_hazards += [h.shifted(off) for h in r["hazards"]]
    world_plats.append(Rect(world_left - 80, -500, world_left, 900, "world"))
    world_plats.append(Rect(world_right, -500, world_right + 100, 900, "world"))

    print(f"World: x {world_left:.0f}..{world_right:.0f} "
          f"({world_right - world_left:.0f}px), {len(world_plats)} platforms, "
          f"{len(world_hazards)} hazards, biomes: {', '.join(order)}")

    # Spawn
    first = rooms[order[0]]
    sx, sy = first["spawn"][0] + offset_of[order[0]], first["spawn"][1]
    start_idx = platform_below(world_plats, sx, sy)
    if start_idx is None:
        failures.append(f"spawn ({sx:.0f},{sy:.0f}) has no platform below")
        print(" FAIL: " + failures[-1])
        return 1

    single = bfs(build_graph(world_plats, allow_double=False), start_idx)
    double = bfs(build_graph(world_plats, allow_double=True), start_idx)
    print(f"Reachable: {len(single)}/{len(world_plats)} platforms single-jump, "
          f"{len(double)}/{len(world_plats)} with double-jump/dash")

    def point_in_hazard(x, y):
        return any(h.l <= x <= h.r and h.t <= y <= h.b for h in world_hazards)

    # Benches: on ground + single-jump reachable.
    all_benches = []
    for rid in order:
        for bx, by, _ in rooms[rid]["benches"]:
            all_benches.append((bx + offset_of[rid], by, rid))
    for bx, by, rid in all_benches:
        pi = platform_under(world_plats, bx, by)
        if pi is None:
            failures.append(f"bench ({bx:.0f},{by:.0f} {rid}) not on a platform top")
        elif pi not in single:
            failures.append(f"bench ({bx:.0f},{by:.0f} {rid}) not reachable with single jumps")
        if point_in_hazard(bx, by):
            failures.append(f"bench ({bx:.0f},{by:.0f} {rid}) inside a hazard")
    print(f"Benches: {len(all_benches)} checked")

    # Caches: on ground + reachable (double-jump allowed, flagged).
    all_caches = []
    for rid in order:
        for cx, cy, _ in rooms[rid]["caches"]:
            all_caches.append((cx + offset_of[rid], cy, rid))
    for cx, cy, rid in all_caches:
        pi = platform_under(world_plats, cx, cy)
        if pi is None:
            failures.append(f"soul cache ({cx:.0f},{cy:.0f} {rid}) not on a platform top")
        elif pi not in double:
            failures.append(f"soul cache ({cx:.0f},{cy:.0f} {rid}) unreachable even with double jump")
        elif pi not in single:
            warnings.append(f"soul cache ({cx:.0f},{cy:.0f} {rid}) needs double-jump/dash (intended for secrets)")
        if point_in_hazard(cx, cy):
            failures.append(f"soul cache ({cx:.0f},{cy:.0f} {rid}) inside a hazard")
    print(f"Soul caches: {len(all_caches)} checked")

    # Enemies: standing / hovering over ground; bosses single-jump reachable.
    n_enemies = 0
    for rid in order:
        off = offset_of[rid]
        for e in rooms[rid]["enemies"]:
            n_enemies += 1
            ex, ey = e["x"] + off, e["y"]
            if e["type"] in FLYING:
                pi = platform_below(world_plats, ex, ey, max_drop=320)
                if pi is None:
                    warnings.append(f"{e['type']} ({ex:.0f},{ey:.0f} {rid}) hovers over void")
            else:
                pi = platform_under(world_plats, ex, ey)
                if pi is None:
                    failures.append(f"{e['type']} ({ex:.0f},{ey:.0f} {rid}) not standing on a platform top")
                    continue
                plat = world_plats[pi]
                need = ENEMY_W.get(e["type"], 46)
                if plat.w < need:
                    warnings.append(f"{e['type']} ({ex:.0f},{ey:.0f} {rid}) on narrow platform ({plat.w:.0f}px < body {need}px)")
            if e["boss"] and pi is not None and pi not in single:
                failures.append(f"boss {e['type']} ({ex:.0f},{ey:.0f} {rid}) arena not reachable with single jumps")
    print(f"Enemies: {n_enemies} checked")

    # World exit: the rightmost boss arena must be single-jump reachable.
    last = rooms[order[-1]]
    last_boss = [e for e in last["enemies"] if e["boss"]]
    if last_boss:
        e = last_boss[0]
        pi = platform_below(world_plats, e["x"] + offset_of[order[-1]], e["y"])
        if pi is None or pi not in single:
            failures.append("final boss arena not reachable from spawn with single jumps")

    # Platforms unreachable even with double jump (excluding boundary walls:
    # they are vertical blockers, not meant to be stood on).
    for i, p in enumerate(world_plats):
        if p.room == "world" or p.h > 800:
            continue
        if i not in double:
            failures.append(f"platform {p} unreachable even with double jump")
        elif i not in single:
            warnings.append(f"platform {p} needs double-jump/dash")

    # Return trip: from the final boss arena the player must be able to
    # walk/jump all the way back to the spawn shore (no one-way traps).
    adj_single = build_graph(world_plats, allow_double=False)
    rev = [[] for _ in range(len(world_plats))]
    for u, vs in enumerate(adj_single):
        for v in vs:
            rev[v].append(u)
    if last_boss:
        e = last_boss[0]
        end_idx = platform_below(world_plats, e["x"] + offset_of[order[-1]], e["y"])
        can_return = bfs(rev, end_idx) if end_idx is not None else set()
        if start_idx not in can_return:
            failures.append("no single-jump path back from final boss arena to spawn (one-way trap)")
        else:
            print("Return trip: final boss arena -> spawn OK (no one-way traps)")

    if render_path:
        render(render_path, world_plats, world_hazards, all_benches, all_caches,
               rooms, order, offset_of, world_left, world_right, single, double)

    print()
    for w in warnings:
        print(f" WARN: {w}")
    for f in failures:
        print(f" FAIL: {f}")
    if failures:
        print(f"\n{len(failures)} failure(s), {len(warnings)} warning(s)")
        return 1
    print(f"\nAll hard checks passed. ({len(warnings)} warning(s))")
    return 0


def render(path, plats, hazards, benches, caches, rooms, order, offset_of,
           world_left, world_right, single, double):
    try:
        from PIL import Image, ImageDraw
    except ImportError:
        print("Pillow not available, skipping render (pip install pillow)")
        return
    W, H = 2600, 760
    top, bottom = -250.0, 750.0
    img = Image.new("RGB", (W, H), (7, 12, 18))
    d = ImageDraw.Draw(img)
    sx = W / (world_right - world_left)
    sy = (H - 90) / (bottom - top)

    def X(wx):
        return (wx - world_left) * sx

    def Y(wy):
        return 55 + (wy - top) * sy

    band_colors = [(10, 38, 34), (17, 20, 38), (24, 44, 42)]
    for i, rid in enumerate(order):
        b = rooms[rid]["bounds"]
        off = offset_of[rid]
        d.rectangle([X(off + b.l), 40, X(off + b.r), H - 30], fill=band_colors[i % 3])
        d.text((X(off + b.l) + 10, 44), rid, fill=(190, 220, 210))
    for hz in hazards:
        d.rectangle([X(hz.l), Y(hz.t), X(hz.r), Y(hz.b)], fill=(180, 60, 55))
    for i, p in enumerate(plats):
        if p.h > 420:
            continue
        col = (200, 225, 215) if i in single else ((240, 200, 90) if i in double else (255, 80, 80))
        d.rectangle([X(p.l), Y(p.t), max(X(p.r), X(p.l) + 1.5), max(Y(p.b), Y(p.t) + 1.5)], fill=col)
    for bx, by, _ in benches:
        x, y = X(bx), Y(by)
        d.polygon([(x, y - 8), (x + 6, y), (x, y + 8), (x - 6, y)], fill=(255, 214, 130))
    for cx, cy, _ in caches:
        d.ellipse([X(cx) - 4, Y(cy - 42) - 4, X(cx) + 4, Y(cy - 42) + 4], fill=(120, 240, 210))
    for rid in order:
        off = offset_of[rid]
        for e in rooms[rid]["enemies"]:
            x, y = X(e["x"] + off), Y(e["y"])
            if e["boss"]:
                d.ellipse([x - 7, y - 14, x + 7, y], fill=(242, 92, 108))
                d.text((x - 14, y - 28), "BOSS", fill=(242, 120, 130))
            else:
                d.ellipse([x - 2.5, y - 5, x + 2.5, y], fill=(170, 170, 200))
    d.text((10, 8), "Verdant Chasm - world map (white = single-jump route, amber = double-jump, red = hazard)", fill=(230, 238, 240))
    d.text((10, H - 22), "gold diamond = bench   cyan dot = soul cache   (vertical axis stretched)", fill=(160, 185, 190))
    img.save(path)
    print(f"Rendered {path}")


if __name__ == "__main__":
    sys.exit(main())
