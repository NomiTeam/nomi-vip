import pathlib
import sys
import zipfile

jar = pathlib.Path(sys.argv[1])
with zipfile.ZipFile(jar) as archive:
    leaked = [name for name in archive.namelist() if 'Test' in name or 'Fixture' in name]
    assert not leaked, f'运行 JAR 混入测试夹具: {leaked}'
    foreign = [name for name in archive.namelist()
               if name.endswith('.class') and not name.startswith('org/nomiteam/vip/')]
    assert not foreign, f'运行 JAR 混入第三方依赖: {foreign}'
print('PASS runtime artifact contains no test fixtures')
