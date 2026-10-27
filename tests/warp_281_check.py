#!/usr/bin/env python3
"""使用真实原包、线上旧包与旧 VIP，验证 2.8.1-free 离线兼容边界。"""
import argparse
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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('inputs', type=pathlib.Path)
    parser.add_argument('candidate', type=pathlib.Path)
    parser.add_argument('--deps-dir', required=True)
    args = parser.parse_args()
    # 仅三个目标类可变化；所有原条目和许可逐字节保留。
    targets = {'cn/handyplus/warp/' + name + '.class' for name in (
        'inventory/CreateGui', 'listener/gui/CreateClickEvent', 'util/WarpUtil')}
    helpers = {'org/nomiteam/vip/' + name + '.class' for name in (
        'compat/WarpPricing', 'WarpPolicy', 'QuoteBook', 'QuoteBook$Quote')}
    with zipfile.ZipFile(args.inputs / 'PlayerWarp-2.8.1-free-original.jar') as original, \
            zipfile.ZipFile(args.candidate) as candidate:
        assert set(candidate.namelist()) == set(original.namelist()) | helpers
        assert len(candidate.namelist()) == len(set(candidate.namelist()))
        changed = {name for name in original.namelist()
                   if original.read(name) != candidate.read(name)}
        assert changed == targets, changed
    print('PASS exact archive entries and original license', flush=True)
    deps = []
    for path in dependencies(args.deps_dir):
        with zipfile.ZipFile(path) as archive:
            if any(name in archive.namelist() for name in (
                    'cn/handyplus/warp/api/PlayerWarpApi.class',
                    'org/nomiteam/vip/NomiVIP.class')):
                continue
        deps.append(path)
    classpath = os.pathsep.join(map(str, [args.candidate,
        args.inputs / 'NomiVIP-1.0.0-current.jar', *deps]))
    with tempfile.TemporaryDirectory(dir=ROOT / '.work') as directory:
        subprocess.run(['javac', '--release', '21', '-encoding', 'UTF-8',
            '-cp', classpath, '-d', directory,
            str(ROOT / 'tests/compat/Warp281Test.java')], check=True)
        subprocess.run(['java', '-Xverify:all', '-cp', directory + os.pathsep + classpath,
            'Warp281Test', str(args.inputs / 'PlayerWarp-2.8.1-free-original.jar'),
            str(args.candidate), str(args.inputs / 'PlayerWarp-2.8.0-free-current.jar'),
            str(args.inputs / 'NomiVIP-1.0.0-current.jar')], check=True)


if __name__ == '__main__':
    main()
