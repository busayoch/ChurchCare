#!/usr/bin/env python3
"""Generate ChurchCare launcher icons from the community emblem asset."""
from pathlib import Path
import re
import sys

project = Path(sys.argv[1]).resolve()
asset = Path(__file__).resolve().parent.parent / "assets" / "churchcare-community-emblem.png"
if not asset.exists():
    raise SystemExit(f"Missing ChurchCare emblem asset: {asset}")

try:
    from PIL import Image
except ImportError:
    raise SystemExit("Pillow is required to generate launcher icon resources.")

manifest = project / "app/src/main/AndroidManifest.xml"
if not manifest.exists():
    candidates = list(project.glob("app/src/main/**/AndroidManifest.xml"))
    if not candidates:
        raise SystemExit("Could not locate AndroidManifest.xml under app/src/main.")
    manifest = candidates[0]

res = manifest.parent / "res"
mipmap = res / "mipmap"
mipmap.mkdir(parents=True, exist_ok=True)
densities = {"mdpi":48, "hdpi":72, "xhdpi":96, "xxhdpi":144, "xxxhdpi":192}
with Image.open(asset) as original:
    source = original.convert("RGBA")
    # Preserve the full emblem and leave breathing room for Android's adaptive mask.
    source.thumbnail((400, 400), Image.Resampling.LANCZOS)
    foreground = Image.new("RGBA", (512, 512), (255, 255, 255, 0))
    foreground.alpha_composite(source, ((512-source.width)//2, (512-source.height)//2))
    drawable = res / "drawable-nodpi"
    drawable.mkdir(parents=True, exist_ok=True)
    foreground.save(drawable / "churchcare_icon_foreground.png", optimize=True)
    for density, size in densities.items():
        folder = res / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        icon = source.resize((size, size), Image.Resampling.LANCZOS)
        # Legacy icons use an opaque square background so they remain legible on older Android versions.
        legacy = Image.new("RGBA", (size, size), (255, 255, 255, 255))
        legacy.alpha_composite(icon, (0, 0))
        legacy.save(folder / "ic_launcher.png", optimize=True)
        legacy.save(folder / "ic_launcher_round.png", optimize=True)

v26 = res / "mipmap-anydpi-v26"
v26.mkdir(parents=True, exist_ok=True)
(v26 / "ic_launcher.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@android:color/white" />
    <foreground android:drawable="@drawable/churchcare_icon_foreground" />
</adaptive-icon>
''', encoding="utf-8")
(v26 / "ic_launcher_round.xml").write_text((v26 / "ic_launcher.xml").read_text(encoding="utf-8"), encoding="utf-8")

text = manifest.read_text(encoding="utf-8")
app_match = re.search(r"<application\b[^>]*>", text, flags=re.DOTALL)
if not app_match:
    raise SystemExit("Could not locate <application> tag in AndroidManifest.xml.")
tag = app_match.group(0)
for attr in ("icon", "roundIcon"):
    pattern = rf'android:{attr}="[^"]*"'
    replacement = f'android:{attr}="@mipmap/ic_launcher"'
    if re.search(pattern, tag):
        tag = re.sub(pattern, replacement, tag)
    else:
        tag = tag[:-1] + f' android:{attr}="@mipmap/ic_launcher">'
text = text[:app_match.start()] + tag + text[app_match.end():]
manifest.write_text(text, encoding="utf-8")
print(f"Applied ChurchCare launcher branding in {project}")
