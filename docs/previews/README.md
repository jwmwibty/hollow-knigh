# Review previews

These images were rendered **on a desktop JVM**, not captured on a device or emulator.

They come from the game's real code: `GameWorld`, `AreaScene`, `FloraArt`, `BossArt` and the boss
brains run unmodified, and every `canvas.drawXxx` call is replayed through a small Java2D stand-in
for `android.graphics`. Geometry, colours, timing and game logic are therefore the real ones;
anti-aliasing, text metrics and additive glow blending differ slightly from Android's renderer.

| File | Shows |
|---|---|
| `grass_before_after.jpg` | the same three places on `master` (top of each pair) and on this branch (bottom): Verdant Path, Fungal Hollows, Old Waterways |
| `boss_keener.jpg` | the Keener: wail wind-up, gale wave, vanish-and-drop marker, the gold "exposed" window |
| `boss_shardback.jpg` | Shardback: charge wind-up, wall crash with marked shards, shard-rain markers, geode roll |

What these do **not** prove: frame rate or feel on a phone, touch-control ergonomics, or how the
art looks on a real display. See the PR description for the full verification statement.
