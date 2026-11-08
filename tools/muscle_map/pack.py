# Orezanie a zabalenie renderov pre appku.
#   body_<view>.webp  -> realistický podklad (RGBA)
#   muscles_<view>.bin -> 'FTML' + int w + int h (big-endian) + w*h bajtov: 0 = nič, 1..17 = skupina (GROUPS v render_body.py)
import bpy, sys, os, struct
import numpy as np
SRC, DST = sys.argv[sys.argv.index("--")+1:][:2]
os.makedirs(DST, exist_ok=True)

def load(name):
    img = bpy.data.images.load(os.path.join(SRC, name))
    w, h = img.size
    a = np.array(img.pixels[:], dtype=np.float32).reshape(h, w, 4)[::-1]  # hore = riadok 0
    return a

def srgb(x):
    return 12.92 * x if x <= 0.0031308 else 1.055 * x ** (1 / 2.4) - 0.055
expected = np.array([round(255 * srgb((i + 1) / 32.0)) for i in range(17)])

views = ["front", "back"]

# Vizuálne orezanie (v pixeloch výrezu, výška postavy 1190 px):
#  - vzpriamovače idú v modeli celou chrbticou; "spodný chrbát" necháme len v bedrovej oblasti,
#  - svaly predkolenia majú šľachy až po prsty; "lýtka" končia pri členkoch.
G = ["abdominals","abductors","adductors","biceps","calves","chest","forearms","glutes","hamstrings",
     "lats","lower back","middle back","neck","quadriceps","shoulders","traps","triceps"]
LOWER_BACK_TOP_Y = int(os.environ.get('FT_LB_TOP', '370'))
LOWER_BACK_SLOPE = float(os.environ.get('FT_LB_SLOPE', '1.1'))
TENDON_LUM = float(os.environ.get('FT_TENDON_LUM', '0.72'))
CALVES_BOTTOM_Y = 1120
def clip_regions(idx):
    lb = G.index("lower back") + 1; ca = G.index("calves") + 1
    # horná hranica je špicatý oblúk (tvar bedrovej fascie): v strede LOWER_BACK_TOP_Y, do strán nižšie
    hh, ww = idx.shape
    yy, xx = np.mgrid[0:hh, 0:ww]
    top = LOWER_BACK_TOP_Y + LOWER_BACK_SLOPE * np.abs(xx - ww / 2.0)
    idx[(idx == lb) & (yy < top)] = 0
    idx[CALVES_BOTTOM_Y:][idx[CALVES_BOTTOM_Y:] == ca] = 0
base = {v: load(f"base_{v}.png") for v in views}
lab = {v: load(f"labels_{v}.png") for v in views}
lab["back_deep"] = load("deep_back.png")

alpha = np.zeros(base["front"].shape[:2], bool)
for v in views:
    alpha |= base[v][..., 3] > 0.02
ys, xs = np.nonzero(alpha)
M = 4
y0, y1 = max(0, ys.min() - M), min(alpha.shape[0], ys.max() + 1 + M)
x0, x1 = max(0, xs.min() - M), min(alpha.shape[1], xs.max() + 1 + M)
W, H = x1 - x0, y1 - y0
print("CROP", x0, y0, W, H)

scene = bpy.context.scene
s = scene.render.image_settings
s.file_format = 'WEBP'; s.color_mode = 'RGBA'; s.quality = 90
scene.view_settings.view_transform = 'Standard'

for v in views:
    b = base[v][y0:y1, x0:x1]
    img = bpy.data.images.new(f"out_{v}", W, H, alpha=True)
    img.pixels[:] = b[::-1].ravel()
    out = os.path.join(DST, f"body_{v}.webp")
    img.save_render(out, scene=scene)

    r = np.round(lab[v][y0:y1, x0:x1, 0] * 255).astype(int)
    a = lab[v][y0:y1, x0:x1, 3]
    d = np.abs(r[..., None] - expected[None, None, :])
    idx = d.argmin(axis=2) + 1
    idx[(d.min(axis=2) > 3) | (a < 0.5)] = 0
    clip_regions(idx)
    counts = np.bincount(idx.ravel(), minlength=18)
    print("GROUPS", v, counts.tolist())
    with open(os.path.join(DST, f"muscles_{v}.bin"), "wb") as f:
        f.write(b"FTML" + struct.pack(">ii", W, H) + idx.astype(np.uint8).tobytes())
    print("SIZE", v, os.path.getsize(out))

def write_labels(key, name):
    r = np.round(lab[key][y0:y1, x0:x1, 0] * 255).astype(int)
    a = lab[key][y0:y1, x0:x1, 3]
    d = np.abs(r[..., None] - expected[None, None, :])
    idx = d.argmin(axis=2) + 1
    idx[(d.min(axis=2) > 3) | (a < 0.5)] = 0
    clip_regions(idx)
    print("GROUPS", key, np.bincount(idx.ravel(), minlength=18).tolist())
    with open(os.path.join(DST, name), "wb") as f:
        f.write(b"FTML" + struct.pack(">ii", W, H) + idx.astype(np.uint8).tobytes())
write_labels("back_deep", "muscles_back_deep.bin")
