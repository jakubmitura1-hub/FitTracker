# Náhľad: rovnaké skladanie ako MuscleMapView (podklad + farby z mapy svalov), obe postavy vedľa seba.
import bpy, sys, os, struct
import numpy as np
D, OUT = sys.argv[sys.argv.index("--")+1:][:2]
G = ["abdominals","abductors","adductors","biceps","calves","chest","forearms","glutes","hamstrings",
     "lats","lower back","middle back","neck","quadriceps","shoulders","traps","triceps"]
import json
PRIMARY = set(json.loads(sys.argv[-2])); SECONDARY = set(json.loads(sys.argv[-1]))
RED = np.array([0xE0,0x50,0x3F])/255; BLUE = np.array([0x4F,0x86,0xE0])/255
def lbl(n):
    b = open(os.path.join(D, n), "rb").read(); w, h = struct.unpack(">ii", b[4:12])
    return np.frombuffer(b[12:], np.uint8).reshape(h, w)
def body(n):
    img = bpy.data.images.load(os.path.join(D, n)); w, h = img.size
    return np.array(img.pixels[:], np.float32).reshape(h, w, 4)[::-1].copy()
cols = []
for v in ["front", "back"]:
    a = body(f"body_{v}.webp"); top = lbl(f"muscles_{v}.bin")
    deep = lbl("muscles_back_deep.bin") if v == "back" else None
    L = a[..., :3].mean(axis=2)
    state = np.zeros(18, int)
    for gi, g in enumerate(G, 1):
        state[gi] = 2 if g in PRIMARY else 1 if g in SECONDARY else 0
    s = state[top]
    if deep is not None:
        cover = np.isin(top, [G.index("traps")+1, G.index("lats")+1]) & (deep > 0)
        s = np.where(cover, np.maximum(s, state[deep]), s)
    s[top == 0] = 0
    k = np.clip(0.35 + 0.95 * L, 0, 1.2)[..., None]
    for lvl, c in ((1, BLUE), (2, RED)):
        m = s == lvl
        a[m, :3] = np.clip(c * k[m], 0, 1)
    cols.append(a)
H = cols[0].shape[0]; gap = np.zeros((H, 30, 4), np.float32)
out = np.concatenate([cols[0], gap, cols[1]], axis=1)
bg = np.array([0.08, 0.09, 0.11]); al = out[..., 3:4]
rgb = out[..., :3] * al + bg * (1 - al)
img = bpy.data.images.new("prev", out.shape[1], H, alpha=False)
img.pixels[:] = np.concatenate([rgb, np.ones((H, out.shape[1], 1))], axis=2)[::-1].ravel()
img.filepath_raw = OUT; img.file_format = 'PNG'; img.save()
print("SAVED", OUT)
