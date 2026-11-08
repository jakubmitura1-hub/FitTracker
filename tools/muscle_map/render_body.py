# Krok 2: render postavy pre MuscleMapView v čistej scéne (factory startup).
#   base   -> realistický podklad (svaly sivobiele, šľachy svetlé, kosti), priehľadné pozadie
#   labels -> mapa svalov: každá skupina má vlastnú presnú farbu, bez vyhladzovania hrán
# blender --background --factory-startup --python render_body.py -- <body.blend> <out_dir> <front|back> <base|labels> <w> <h>
import bpy, sys, os, math, re, time
from mathutils import Vector

T0 = time.time()
def log(*a):
    print(f'[{time.time()-T0:6.1f}s]', *a, flush=True)

argv = sys.argv[sys.argv.index("--") + 1:]
BODY, OUT, VIEW, MODE = argv[0], argv[1], argv[2], argv[3]
RES_X, RES_Y = int(argv[4]), int(argv[5])

# Poradie = index skupiny v mape (1..17), musí sedieť s MuscleMapView.LABEL_ORDER
GROUPS = ["abdominals", "abductors", "adductors", "biceps", "calves", "chest", "forearms",
          "glutes", "hamstrings", "lats", "lower back", "middle back", "neck", "quadriceps",
          "shoulders", "traps", "triceps"]

# Anglický názov objektu (bez .l/.r) -> skupina. Prvá zhoda vyhráva.
RULES = [
    (r"pectoralis major", "chest"),
    (r"deltoid", "shoulders"),
    (r"trapezius", "traps"),
    (r"sternocleidomastoid|splenius|scalenus|levator scapulae|platysma", "neck"),
    (r"biceps brachii|brachialis muscle|coracobrachialis", "biceps"),
    (r"triceps brachii|anconeus", "triceps"),
    (r"brachioradialis|flexor carpi|extensor carpi|palmaris longus|flexor digitorum superficialis|"
     r"extensor digitorum\.?$|extensor digitorum$|extensor digiti minimi|pronator teres|"
     r"abductor pollicis longus|extensor pollicis|flexor digitorum profundus|flexor pollicis longus|supinator|extensor indicis", "forearms"),
    (r"rectus abdominis|abdominal oblique|pyramidalis|transversus abdominis", "abdominals"),
    (r"latissimus dorsi|teres major", "lats"),
    (r"rhomboid|infraspinatus|teres minor", "middle back"),
    (r"iliocostalis lumborum|iliocostalis thoracis|longissimus thoracis|spinalis thoracis|"
     r"multifidus lumborum|quadratus lumborum|serratus posterior inferior", "lower back"),
    (r"gluteus maximus", "glutes"),
    (r"gluteus medius|gluteus minimus|tensor fasciae latae", "abductors"),
    (r"adductor (longus|brevis|magnus)|gracilis|pectineus", "adductors"),
    (r"rectus femoris|vastus|sartorius", "quadriceps"),
    (r"biceps femoris|semitendinosus|semimembranosus", "hamstrings"),
    (r"gastrocnemius|soleus|plantaris|tibialis anterior|fibularis (longus|brevis)|extensor digitorum longus|extensor hallucis longus|flexor digitorum longus|flexor hallucis longus", "calves"),
]
# Plochy, ktoré v skutočnosti pôsobia ako šľacha/väzivo, hoci majú svalový materiál
TENDON_NAMES = re.compile(r"tract|aponeurosis|linea alba|retinaculum|ligament|tendon", re.I)

def group_of(name):
    base = re.sub(r"\.(l|r)$", "", name).strip("()").lower()
    for pat, g in RULES:
        if re.search(pat, base):
            return g
    return None

# --- prázdna scéna
for o in list(bpy.data.objects):
    bpy.data.objects.remove(o, do_unlink=True)
scene = bpy.context.scene

with bpy.data.libraries.load(BODY, link=False) as (src, dst):
    dst.objects = list(src.objects)
objs = [o for o in dst.objects if o is not None]
objs = [o for o in objs if o.type == 'MESH' and o.get("ft_kind")]
for o in objs:
    scene.collection.objects.link(o)
    o.hide_render = False      # v Z-Anatomy majú svaly render vypnutý
    o.hide_viewport = False
bpy.context.view_layer.update()  # prepočíta matrix_world načítaných objektov
log("loaded", len(objs))

# --- materiály
def mat(name, color, rough=0.5):
    m = bpy.data.materials.new(name)
    m.diffuse_color = (*color, 1)
    m.roughness = rough
    m.metallic = 0.0
    return m

if MODE == "base":
    MUSCLE = mat("FT_muscle", (0.56, 0.54, 0.53), 0.55)
    TENDON = mat("FT_tendon", (0.90, 0.89, 0.86), 0.3)
    BONE = mat("FT_bone", (0.74, 0.72, 0.68), 0.6)
else:
    BLACK = mat("FT_none", (0, 0, 0))
    GROUP_MATS = {g: mat("FT_g_" + g, ((i + 1) / 32.0, 0, 0)) for i, g in enumerate(GROUPS)}

DEEP_GROUPS = {"middle back", "lower back"}
unmapped = set()
for o in objs:
    kind = o.get("ft_kind", "muscle")
    slots = (o.get("ft_slots") or "").split(",")
    g = group_of(o.name) if kind == "muscle" else None
    if MODE == "deep" and g in ("traps", "lats"):
        o.hide_render = True
    if kind == "muscle" and g is None:
        unmapped.add(re.sub(r"\.(l|r)$", "", o.name))
    for i, slot in enumerate(o.material_slots):
        orig = slots[i] if i < len(slots) else ""
        is_tendon = orig in ("Tendon", "Ligament") or TENDON_NAMES.search(o.name)
        if MODE == "base":
            slot.material = BONE if kind == "bone" else (TENDON if is_tendon else MUSCLE)
        elif MODE == "deep":
            # Hlboká vrstva: trapéz a široký chrbtový skryté, mapujú sa len hlboké chrbtové svaly
            slot.material = GROUP_MATS[g] if g in DEEP_GROUPS else BLACK
        else:
            # Šľachy patria k svalu (zafarbia sa s ním), kosti a nezaradené svaly = 0
            slot.material = GROUP_MATS[g] if g else BLACK
        slot.link = 'DATA'
with open(os.path.join(OUT, "unmapped_muscles.txt"), "w", encoding="utf-8") as f:
    f.write("\n".join(sorted(unmapped)))

# --- kamera
mins = Vector((1e9,) * 3); maxs = Vector((-1e9,) * 3)
for o in objs:
    if o.get("ft_kind") != "bone":
        continue  # rozmer tela určí kostra — stabilné pre oba pohľady aj oba režimy
    for c in o.bound_box:
        w = o.matrix_world @ Vector(c)
        mins = Vector(map(min, mins, w)); maxs = Vector(map(max, maxs, w))
center = (mins + maxs) / 2
size = maxs - mins
log('bbox', mins, maxs)
cam_data = bpy.data.cameras.new("FT_cam")
cam_data.type = 'ORTHO'
cam = bpy.data.objects.new("FT_cam", cam_data)
scene.collection.objects.link(cam)
dist = 10 * size.z
if VIEW == "front":   # predná strana tela smeruje do -Y
    cam.location = (center.x, mins.y - dist, center.z)
    cam.rotation_euler = (math.radians(90), 0, 0)
else:
    cam.location = (center.x, maxs.y + dist, center.z)
    cam.rotation_euler = (math.radians(90), 0, math.radians(180))
# Obe postavy rovnaká mierka: výška tela + 2 % okraj, šírka podľa pomeru strán
cam_data.sensor_fit = 'VERTICAL'
cam_data.ortho_scale = size.z * 1.02
cam_data.clip_end = dist * 3
scene.camera = cam

scene.render.resolution_x = RES_X
scene.render.resolution_y = RES_Y
scene.render.resolution_percentage = 100
scene.render.film_transparent = True
scene.render.use_freestyle = False
scene.render.dither_intensity = 0.0
scene.use_nodes = False
scene.render.engine = 'BLENDER_WORKBENCH'
scene.view_settings.view_transform = 'Standard'
scene.view_settings.look = 'None'
sh = scene.display.shading
sh.color_type = 'MATERIAL'
if MODE == "base":
    sh.light = 'STUDIO'
    sh.show_cavity = True
    sh.cavity_type = 'BOTH'
    sh.cavity_ridge_factor = 1.0
    sh.cavity_valley_factor = 1.4
    sh.curvature_ridge_factor = 0.7
    sh.curvature_valley_factor = 1.0
    sh.show_specular_highlight = True
    scene.display.render_aa = '16'
else:
    sh.light = 'FLAT'
    sh.show_cavity = False
    sh.show_specular_highlight = False
    scene.display.render_aa = 'OFF'
    scene.view_settings.view_transform = 'Standard'
    scene.display_settings.display_device = 'sRGB'

name = f"{MODE}_{VIEW}.png"
scene.render.filepath = os.path.join(OUT, name)
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGBA'
log("render start", name)
bpy.ops.render.render(write_still=True)
log("DONE", scene.render.filepath, "unmapped:", len(unmapped))
