"""Validate final Play PNGs, normalize their encoding, and lay out a review contact sheet.

The PNG normalization only removes an entirely opaque alpha channel; pixels do not change.
"""
from pathlib import Path
import hashlib
import json
import zipfile
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
files = sorted((ROOT / 'play-store').glob('*.png'))
assert len(files) == 8, f'Expected 8 Play screenshots, got {len(files)}'
records = []
for number, path in enumerate(files, 1):
    with Image.open(path) as source:
        assert source.size == (1080, 1920), (path.name, source.size)
        if source.mode == 'RGBA':
            assert source.getchannel('A').getextrema() == (255, 255), f'Unexpected transparency: {path.name}'
        rgb = source.convert('RGB')
        rgb.save(path, optimize=True)
    records.append({
        'order': number,
        'file': path.name,
        'width': 1080, 'height': 1920, 'format': '24-bit RGB PNG',
        'provenance': 'Actual user-supplied phone capture' if number == 1 else
            ('Actual app settings' if number == 8 else 'Actual app renderer with disclosed illustrative fixtures'),
        'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
        'bytes': path.stat().st_size,
    })
(ROOT / 'manifest.json').write_text(json.dumps({'language': 'en', 'screenshots': records}, indent=2), encoding='utf-8')

# Original review layout composed from the final assets, without altering their contents.
sheet = Image.new('RGB', (1640, 1550), '#07141d')
draw = ImageDraw.Draw(sheet)
font_path = ROOT.parent.parent / 'app/src/main/res/font/jakarta_bold.ttf'
font = ImageFont.truetype(str(font_path), 24)
small = ImageFont.truetype(str(font_path), 14)
draw.text((28, 20), 'WI-FI HEATMAP 3D  /  GOOGLE PLAY', font=font, fill='#dff9ee')
draw.text((28, 58), '8 English screenshots · actual app visuals · 1080 × 1920', font=small, fill='#9ab9c1')
for index, path in enumerate(files):
    x, y = 28 + (index % 4) * 402, 96 + (index // 4) * 716
    with Image.open(path) as source:
        thumb = source.resize((378, 672), Image.Resampling.LANCZOS)
        sheet.paste(thumb, (x, y))
    draw.text((x, y + 680), path.stem.replace('-', ' '), font=small, fill='#9ab9c1')
sheet.save(ROOT / 'contact-sheet.png', optimize=True)
with zipfile.ZipFile(ROOT / 'play-store-screenshots.zip', 'w', zipfile.ZIP_DEFLATED) as bundle:
    for path in files:
        bundle.write(path, path.name)
    bundle.write(ROOT / 'manifest.json', 'manifest.json')
print(json.dumps({'verified': len(files), 'mode': 'RGB', 'size': [1080, 1920], 'zip': str(ROOT / 'play-store-screenshots.zip')}))
