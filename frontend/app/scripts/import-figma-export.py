"""Import the user's SVG exports without modifying the source directory.

Python standard library only. Embedded PNGs are decoded byte-for-byte.
Screen SVGs are reference material, never runtime screen backgrounds.
"""
import base64
import hashlib
import json
import shutil
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parent.parent
source = Path(sys.argv[1])
destination = root / 'docs/figma/exports'
embedded = root / '.expo/embedded'
embedded.mkdir(parents=True, exist_ok=True)
records = []
for file in sorted(source.rglob('*.svg')):
    relative = file.relative_to(source)
    copy = destination / relative
    copy.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(file, copy)
    svg = ET.parse(file).getroot()
    images = []
    for image in svg.iter('{http://www.w3.org/2000/svg}image'):
        href = image.get('{http://www.w3.org/1999/xlink}href', image.get('href', ''))
        if not href.startswith('data:image/png;base64,'):
            raise ValueError('Unsupported embedded image: ' + image.get('id'))
        data = base64.b64decode(href.split(',', 1)[1], validate=True)
        name = image.get('id') + '.png'
        (embedded / name).write_bytes(data)
        images.append({'elementId': image.get('id'), 'width': int(image.get('width')),
                       'height': int(image.get('height')), 'sha256': hashlib.sha256(data).hexdigest(),
                       'extractedPath': '.expo/embedded/' + name})
    records.append({'sourceRelativePath': relative.as_posix(),
                    'referencePath': copy.relative_to(root).as_posix(),
                    'width': int(svg.get('width')), 'height': int(svg.get('height')),
                    'sha256': hashlib.sha256(file.read_bytes()).hexdigest(), 'images': images})
output = root / 'docs/figma/export-inventory.json'
output.write_text(json.dumps({'sourceDirectory': str(source), 'screens': records}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'Imported {len(records)} SVG references and {sum(len(x["images"]) for x in records)} embedded PNGs.')
