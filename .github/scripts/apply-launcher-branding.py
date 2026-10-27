#!/usr/bin/env python3
"""Generate clean ChurchCare launcher icons from a high-quality, symbol-only emblem."""
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
densities = {"mdpi":48, "hdpi":72, "xhdpi":96, "xxhdpi":144, "xxxhdpi":192}
background = "#0B4F85"

with Image.open(asset) as original:
    source = original.convert("RGBA")
    # The source asset is a high-quality crop of the church/people/hands/swoosh,
    # excluding the tiny wordmark that becomes illegible at launcher-icon size.
    # Upscale with Lanczos into the adaptive-icon safe area; do not use a low-quality thumbnail.
    source = source.resize((448, 448), Image.Resampling.LANCZOS)
    foreground = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    foreground.alpha_composite(source, (32, 32))
    drawable = res / "drawable-nodpi"
    drawable.mkdir(parents=True, exist_ok=True)
    foreground.save(drawable / "churchcare_icon_foreground.png", optimize=True)

    for density, size in densities.items():
        folder = res / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        icon = source.resize((size, size), Image.Resampling.LANCZOS)
        icon.save(folder / "ic_launcher.png", optimize=True)
        icon.save(folder / "ic_launcher_round.png", optimize=True)

values = res / "values"
values.mkdir(parents=True, exist_ok=True)
colors = values / "colors.xml"
if colors.exists():
    color_text = colors.read_text(encoding="utf-8")
    if re.search(r'<color name="churchcare_icon_background">.*?</color>', color_text):
        color_text = re.sub(r'<color name="churchcare_icon_background">.*?</color>',
                            f'<color name="churchcare_icon_background">{background}</color>', color_text)
    else:
        color_text = color_text.replace("</resources>",
            f'    <color name="churchcare_icon_background">{background}</color>\n</resources>')
else:
    color_text = f'<resources>\n    <color name="churchcare_icon_background">{background}</color>\n</resources>\n'
colors.write_text(color_text, encoding="utf-8")

v26 = res / "mipmap-anydpi-v26"
v26.mkdir(parents=True, exist_ok=True)
adaptive = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/churchcare_icon_background" />
    <foreground android:drawable="@drawable/churchcare_icon_foreground" />
</adaptive-icon>
'''
(v26 / "ic_launcher.xml").write_text(adaptive, encoding="utf-8")
(v26 / "ic_launcher_round.xml").write_text(adaptive, encoding="utf-8")

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
print(f"Applied clean ChurchCare launcher branding in {project}")
