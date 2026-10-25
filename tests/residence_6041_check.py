#!/usr/bin/env python3
"""使用真实原包、线上旧包与旧 VIP，验证 6.0.4.1 离线兼容边界。"""
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
    # 只允许一个原条目改变、一个 helper 新增，许可证和其他资源逐字节保留。
    target = 'com/bekvon/bukkit/residence/protection/ResidencePermissions.class'
    helper = 'org/nomiteam/vip/compat/ResidenceGrant.class'
    with zipfile.ZipFile(args.inputs / 'Residence-6.0.4.1-original.jar') as original, \
            zipfile.ZipFile(args.candidate) as candidate:
        assert set(candidate.namelist()) == set(original.namelist()) | {helper}
        assert len(candidate.namelist()) == len(set(candidate.namelist()))
        changed = [name for name in original.namelist()
                   if original.read(name) != candidate.read(name)]
        assert changed == [target], changed
        assert candidate.read('LICENSE') == original.read('LICENSE')
    print('PASS exact archive entries and original license', flush=True)
    deps = []
    for path in dependencies(args.deps_dir):
        with zipfile.ZipFile(path) as archive:
            if any(name in archive.namelist() for name in (
                    'com/bekvon/bukkit/residence/Residence.class',
                    'net/Zrips/CMILib/CMILib.class',
                    'org/nomiteam/vip/NomiVIP.class')):
                continue
        deps.append(path)
    classpath = os.pathsep.join(map(str, [args.candidate,
        args.inputs / 'CMILib-1.6.1.0.jar',
        args.inputs / 'NomiVIP-1.0.0-current.jar', *deps]))
    with tempfile.TemporaryDirectory(dir=ROOT / '.work') as directory:
        subprocess.run(['javac', '--release', '21', '-encoding', 'UTF-8',
            '-cp', classpath, '-d', directory,
            str(ROOT / 'tests/compat/Residence6041Test.java')], check=True)
        subprocess.run(['java', '-Xverify:all', '-cp', directory + os.pathsep + classpath,
            'Residence6041Test', str(args.inputs / 'Residence-6.0.4.1-original.jar'),
            str(args.candidate), str(args.inputs / 'Residence-6.0.3.3-current.jar'),
            str(args.inputs / 'NomiVIP-1.0.0-current.jar')], check=True)


if __name__ == '__main__':
    main()
