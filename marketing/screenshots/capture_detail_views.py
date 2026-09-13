"""Capture existing app screens after normal touch scrolling to their main evidence."""
from pathlib import Path
import re
import subprocess
import time
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
ADB = ['C:/Android/Sdk/platform-tools/adb.exe', '-s', 'emulator-5556']
PACKAGE = 'com.f7developer.wifiheatmap3d'

def adb(*args):
    return subprocess.check_output(ADB + list(args), text=True, stderr=subprocess.STDOUT)

def dump():
    adb('shell', 'uiautomator', 'dump', '/sdcard/store-ui.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/store-ui.xml'))

for page, target, desired, name in [
    (3, '2.4 GHZ BAND', 480, '04-channel-analysis'),
    (4, 'EVERY NETWORK, GRADED', 505, '05-security-check'),
    (5, 'Best', 1030, '06-ping-test'),
]:
    if len(sys.argv) > 1 and str(page) not in sys.argv[1:]:
        continue
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'am', 'start', '-W', '-n', f'{PACKAGE}/com.sinyal.app.marketing.StoreScreenshotActivity', '--ei', 'page', str(page), '--ez', 'framed', 'true')
    time.sleep(4)
    found = False
    for attempt in range(15):
        tree = dump()
        matching = [node for node in tree.iter('node') if node.attrib.get('text', '').casefold() == target.casefold()]
        ys = []
        for node in matching:
            bounds = [int(v) for v in re.findall(r'\d+', node.attrib['bounds'])]
            if len(bounds) == 4 and bounds[2] > bounds[0] and bounds[3] > bounds[1]:
                ys.append(bounds[1])
        if ys:
            delta = ys[-1] - desired
            if abs(delta) < 40:
                found = True
                break
            # Slow drags minimize fling and place the observed label near its target.
            delta = max(-850, min(850, delta))
            start = 1550 if delta > 0 else 650
            end = start - delta - (20 if delta > 0 else -20)
            adb('shell', 'input', 'swipe', '520', str(start), '520', str(end), '1100')
        else:
            # Near the bottom, a section label can sit just above the clipped viewport.
            # Backtrack in shorter drags instead of repeatedly pushing against the end.
            if attempt >= 8:
                adb('shell', 'input', 'swipe', '520', '650', '520', '1050', '1100')
            else:
                adb('shell', 'input', 'swipe', '520', '1600', '520', '650', '1100')
        time.sleep(1)
    assert found, f'Could not frame {target}'
    time.sleep(1)
    adb('shell', 'screencap', '-p', '/sdcard/store-detail.png')
    adb('pull', '/sdcard/store-detail.png', str(ROOT / 'play-store' / f'{name}.png'))
    print(f'Captured {name} with {target} visible', flush=True)
