"""核对完整菜单候选：仅替换饭团发货动作，所有其余字节保持原样。"""
import hashlib
import json
import pathlib
import re
import sys

inputs = pathlib.Path(sys.argv[1])
candidate = pathlib.Path(sys.argv[2])
manifest = json.loads((inputs / 'menu-sha256.json').read_text())
changed = []
for name, expected_sha in manifest.items():
    original = (inputs / 'menus' / name).read_bytes()
    assert hashlib.sha256(original).hexdigest() == expected_sha, name
    actual = (candidate / name).read_bytes()
    if pathlib.Path(name).name.startswith('Pay'):
        term = re.fullmatch(r'Pay(VIP|SVIP|MVIP)(30|90)\.yml', pathlib.Path(name).name)
        assert term, name
        group, days = term.groups()
        group = group.lower()
        new = f'nomivipgrant {{{{player uuid}}}} {group} {days}'.encode()
        old = f'lp user {{{{player uuid}}}} parent addtemp {group} {days}d accumulate'.encode()
        assert actual.count(new) == 1, f'{name}: 缺失固定本次UUID的完成回执桥'
        assert old not in actual, f'{name}: 不得同时执行两次发货'
        assert actual.replace(new, old) == original, f'{name}: 价格/扣费/扫码/布局/调度发生额外变化'
        changed.append(name)
    else:
        assert actual == original, name
assert len(changed) == 12
assert len(manifest) == 20
assert {str(p.relative_to(candidate)) for p in candidate.rglob('*.yml')} == set(manifest)
print('PASS 20 complete menus: 12 self-UUID grants; original prices/points/QR/products/layout/DELAY preserved; 8 navigation pages byte-identical')
