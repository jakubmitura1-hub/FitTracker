# Anatomická postava pre MuscleMapView

Zdroj: Z-Anatomy (Startup.blend, CC BY-SA 4.0), Blender 4.2 portable.

1. `blender --background --disable-autoexec Startup.blend --python export_meshes.py -- body.blend 1.50`
   (povrchové svaly + kostra + koža hlavy nad výškou 1.50 m)
2. pre view v {front, back} a mode v {base, labels}:
   `blender --background --factory-startup --python render_body.py -- body.blend <out> <view> <mode> 560 1200`
   a navyše `... back deep 560 1200` (hlboká vrstva chrbta)
3. `blender --background --factory-startup --python pack.py -- <out> <out>/pack`
   -> skopírovať `pack/*.webp, *.bin` do `app/src/main/assets/muscle_map/`
4. náhľad: `preview.py -- <pack> out.png '["lats"]' '["biceps"]'`

Poradie skupín (GROUPS) musí sedieť s `MuscleMapView.GROUPS`.
