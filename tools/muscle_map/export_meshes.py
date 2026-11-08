# Krok 1: zo Z-Anatomy vytiahne len povrchové svaly (vrátane šliach) a kostru do čistého .blend súboru.
# blender --background --disable-autoexec Startup.blend --python export_meshes.py -- <out.blend>
import bpy, sys, time, re

T0 = time.time()
def log(*a):
    print(f'[{time.time()-T0:6.1f}s]', *a, flush=True)

argv = sys.argv[sys.argv.index("--") + 1:]
OUT = argv[0]
HEAD_Z = float(argv[1]) if len(argv) > 1 else 1.50

SKIP_MATS = {"Fascia", "Bursa", "Articular capsule"}
muscles = bpy.data.collections["4: Muscular system"]
skeleton = bpy.data.collections["1: Skeletal system"]

def is_side_mesh(o):
    return o.type == 'MESH' and (o.name.endswith(".l") or o.name.endswith(".r") or "." not in o.name)

def main_mat(o):
    return o.data.materials[0].name if o.data.materials and o.data.materials[0] else ""

picked = []
for o in muscles.objects:
    if is_side_mesh(o) and main_mat(o) not in SKIP_MATS:
        o["ft_kind"] = "muscle"
        picked.append(o)
for o in skeleton.objects:
    if is_side_mesh(o):
        o["ft_kind"] = "bone"
        picked.append(o)

# Koža hlavy: kožné oblasti, ktorých stred je nad bradou -> hladká hlava namiesto lebky so zubami
from mathutils import Vector
regions = bpy.data.collections["9: Regions of human body"]
heads = []
for o in regions.all_objects:
    if o.type != "MESH" or not o.data.polygons or not is_side_mesh(o) or re.search("hair|eyelash|eyebrow", o.name, re.I):
        continue
    c = o.matrix_world @ (sum((Vector(b) for b in o.bound_box), Vector()) / 8)
    if c.z > HEAD_Z:
        o["ft_kind"] = "skin"
        picked.append(o); heads.append(o.name)
log("head skin", len(heads), sorted(heads))

# Zapečie svetovú transformáciu (rodičia sa do nového súboru nezapisujú) a aplikuje modifikátory
dg = bpy.context.evaluated_depsgraph_get()
for o in picked:
    mw = o.matrix_world.copy()
    if o.modifiers:
        ev = o.evaluated_get(dg)
        o.data = bpy.data.meshes.new_from_object(ev)
        o.modifiers.clear()
    o.parent = None
    o.matrix_world = mw
    o.constraints.clear()
    o.animation_data_clear()
    # Názov materiálu v slote si uložíme — v renderi rozhodne sval vs. šľacha
    o["ft_slots"] = ",".join((s.material.name if s.material else "") for s in o.material_slots)
log("picked", len(picked))

bpy.data.libraries.write(OUT, set(picked), fake_user=True, compress=True)
log("written", OUT)
