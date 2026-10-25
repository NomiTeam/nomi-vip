#!/usr/bin/env python3
# Exclusive creation avoids overwriting an existing artifact in a race.
"""Generate compatibility artifacts locally from owner-supplied, audited inputs."""
import argparse
import hashlib
import os
import pathlib
import subprocess
import sys
import tempfile
import zipfile
ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.dont_write_bytecode = True
from build import dependencies
HASHES = {'residence': 'b275107efa695a4c4b54035f8523a49ffcec37948699c90672661a10550945ce', 'warp': '68aceac6f875a91a80fb881b9554ba9c56315004eb4116b673b59f4109cab2ee'}

WARP_281 = '0374ecb6d237bf3ca3a6eb824682bf35e99699f5413427ad49b102511085063a'

RESIDENCE_6041 = '3dbfe5d775b2da05ae8377df78e3808755b8cce454e75ff7e786ac784bc431ca'


def main():
    a = argparse.ArgumentParser()
    a.add_argument('kind', choices=HASHES)
    a.add_argument('input', type=pathlib.Path)
    a.add_argument('output', type=pathlib.Path)
    a.add_argument('--deps-dir', default=os.getenv('NOMI_DEPS_DIR'))
    x = a.parse_args()
    if not x.deps_dir:
        a.error('--deps-dir or NOMI_DEPS_DIR required')
    if x.output.exists():
        a.error('output already exists; choose a new output path')
    fingerprint = hashlib.sha256(x.input.read_bytes()).hexdigest()
    native_residence = x.kind == 'residence' and fingerprint == RESIDENCE_6041
    native_warp = x.kind == 'warp' and fingerprint == WARP_281
    if fingerprint != HASHES[x.kind] and not native_residence and not native_warp:
        a.error('unsupported input SHA256; review a new version before updating generator')
    with zipfile.ZipFile(x.input) as z:
        if len(z.namelist()) != len(set(z.namelist())):
            a.error('duplicate archive entries')
        if any((n.upper().endswith(('.SF', '.RSA', '.DSA')) for n in z.namelist())):
            a.error('signed archives require separate review')
    # Residence 优先使用本次真实输入，避免旧插件或旧 CMILib 遮蔽验证。
    deps = dependencies(x.deps_dir)
    if x.kind == 'residence':
        def is_residence_dependency(path):
            with zipfile.ZipFile(path) as archive:
                names = archive.namelist()
                return ('com/bekvon/bukkit/residence/Residence.class' in names
                        or (native_residence and 'net/Zrips/CMILib/CMILib.class' in names))
        deps = [p for p in deps if not is_residence_dependency(p)]
        cmilib = x.input.parent / 'CMILib-1.6.1.0.jar'
        if native_residence and not cmilib.is_file():
            a.error('Residence 6.0.4.1 requires adjacent CMILib-1.6.1.0.jar')
        if native_residence and hashlib.sha256(cmilib.read_bytes()).hexdigest() != (
                '4f625972e1cf81ae40eca5dedf6bd39180c7a193b4888226fe99ccf8fd9794b7'):
            a.error('unsupported CMILib SHA256 for Residence 6.0.4.1')
        deps = [x.input] + ([cmilib] if cmilib.is_file() else []) + deps
    if native_warp:
        # 新原包优先，避免线上旧 Warp 遮蔽真实 API 验证。
        def is_warp_dependency(path):
            with zipfile.ZipFile(path) as archive:
                return 'cn/handyplus/warp/api/PlayerWarpApi.class' in archive.namelist()
        deps = [x.input] + [p for p in deps if not is_warp_dependency(p)]
    cp = os.pathsep.join(map(str, deps))
    work = ROOT / '.work'
    work.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='compat-', dir=work) as temp:
        classes = pathlib.Path(temp) / 'classes'
        classes.mkdir()
        src = list((ROOT / 'compat/src').rglob('*.java')) + [ROOT / 'src/main/java/org/nomiteam/vip' / n for n in ['WarpPolicy.java', 'QuoteBook.java']]
        subprocess.run(['javac', '--release', '21', '-encoding', 'UTF-8', '-cp', cp, '-d', str(classes), *map(str, src)], check=True)
        staged = pathlib.Path(temp) / 'output.jar'
        subprocess.run(['java', '-cp', str(classes) + os.pathsep + cp, 'Patch', x.kind, str(x.input), str(staged), str(classes), 'native-6041' if native_residence else 'native-281' if native_warp else 'legacy'], check=True)
        subprocess.run(['java', '-Xverify:all', '-cp', str(staged) + os.pathsep + str(classes) + os.pathsep + cp, 'VerifyGenerated', x.kind], check=True)
        checks = ['residence', 'unchanged'] if x.kind == 'residence' else ['price', 'payments', 'paid', 'quote', 'serialized', 'unchanged']
        for case in checks:
            subprocess.run([sys.executable, str(ROOT / 'tests/compat_check.py'), case, str(x.input), str(staged)], check=True)
        x.output.parent.mkdir(parents=True, exist_ok=True)
        with x.output.open('xb') as f:
            f.write(staged.read_bytes())
    print('SHA256', hashlib.sha256(x.output.read_bytes()).hexdigest())
if __name__ == '__main__':
    main()
