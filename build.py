#!/usr/bin/env python3
"""Offline build. Compile dependencies and test fixtures never enter the runtime JAR."""
import argparse
import os
import pathlib
import re
import shutil
import subprocess
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent


def dependencies(directory):
    jars = sorted(pathlib.Path(directory).rglob('*.jar'))
    api = [path for path in jars if path.name.startswith('purpur-api-26.3')]
    if not api:
        raise SystemExit('purpur-api 26.3 is required in --deps-dir')
    chosen = sorted(api, key=lambda path: ('stable' in path.name, path.name))[-1]

    def version(path):
        return tuple(int(number) for number in re.findall(r'\d+', path.parent.name))

    # ASM precedes embedded old copies; Adventure uses one coherent latest version.
    asm = {}
    adventure = {}
    others = []
    for path in jars:
        if 'purpur-api-' in path.name:
            continue
        if 'org/ow2/asm/' in path.as_posix():
            selected = asm
        elif '/net/kyori/' in path.as_posix():
            selected = adventure
        else:
            if 'plugins' in path.parts:
                with zipfile.ZipFile(path) as archive:
                    required = (
                        'com/bekvon/bukkit/residence/Residence.class',
                        'cn/handyplus/warp/api/PlayerWarpApi.class',
                        'net/milkbowl/vault/economy/Economy.class',
                    )
                    if not any(name in archive.namelist() for name in required):
                        continue
            others.append(path)
            continue
        key = path.parent.parent.name
        if key not in selected or version(path) > version(selected[key]):
            selected[key] = path
    return [chosen] + sorted(asm.values()) + sorted(adventure.values()) + others


def compile_sources(sources, destination, classpath):
    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir(parents=True)
    subprocess.run([
        'javac', '--release', '21', '-encoding', 'UTF-8', '-cp', classpath,
        '-d', str(destination), *map(str, sources),
    ], check=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--deps-dir', default=os.getenv('NOMI_DEPS_DIR'))
    parser.add_argument('--dh-jar', type=pathlib.Path)
    parser.add_argument('--lp-jar', type=pathlib.Path, required=True,
                        help='Actual LuckPerms runtime JAR with embedded API (compile/test only)')
    args = parser.parse_args()
    if not args.deps_dir:
        parser.error('use --deps-dir or NOMI_DEPS_DIR')
    if args.dh_jar and not args.dh_jar.is_file():
        parser.error('--dh-jar must identify an existing input JAR')
    if not args.lp_jar.is_file():
        parser.error('--lp-jar must identify an existing input JAR')
    version = subprocess.check_output(
        ['javac', '-version'], text=True, stderr=subprocess.STDOUT,
    ).strip()
    if not version.startswith('javac 25'):
        raise SystemExit('Build requires JDK 25; found ' + version)
    classpath = os.pathsep.join(map(str, [args.lp_jar, *dependencies(args.deps_dir)]))
    # 内嵌实现仅供离线契约回归，绝不合入 NomiVIP 运行包。
    lp_runtime = ROOT / 'build/test-deps/luckperms-runtime.jar'
    lp_runtime.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.lp_jar) as archive:
        lp_runtime.write_bytes(archive.read('luckperms-bukkit.jarinjar'))
    classes = ROOT / 'build/classes'
    tests = ROOT / 'build/test-classes'
    compile_sources(sorted((ROOT / 'src/main/java').rglob('*.java')), classes, classpath)
    test_classpath = os.pathsep.join([str(tests), str(classes), classpath])
    compile_sources(sorted((ROOT / 'tests').rglob('*.java')), tests,
                    test_classpath + os.pathsep + str(lp_runtime))

    def run_test(name, *arguments, extra_classpath=None):
        selected = test_classpath
        if extra_classpath:
            selected += os.pathsep + str(extra_classpath)
        subprocess.run([
            'java', '-ea', '-Xverify:all', '-cp', selected,
            'org.nomiteam.vip.' + name, *arguments,
        ], check=True)

    run_test('PolicyTest')
    run_test('ScaleControlTest')
    for case in 'restored off revoke claim native jump nofly quote stale-quote'.split():
        run_test('TransitionTest', case)
    run_test('DhTest')
    run_test('ReceiptIdentityTest', extra_classpath=lp_runtime)
    run_test('ReceiptExpiryTest', extra_classpath=lp_runtime)
    run_test('MembershipGrantTest', extra_classpath=lp_runtime)
    run_test('MembershipFeedbackTest')
    run_test('MembershipCommandTest', extra_classpath=lp_runtime)
    run_test('MembershipRuntimeTest', extra_classpath=lp_runtime)
    if args.dh_jar:
        run_test('DhBytecodeTest', extra_classpath=args.dh_jar)
    else:
        print('SKIP real DH bytecode: --dh-jar not supplied')
    for path in (ROOT / 'src/main/resources').rglob('*'):
        if path.is_file():
            target = classes / path.relative_to(ROOT / 'src/main/resources')
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(path, target)
    jar = ROOT / 'build/NomiVIP-1.0.0.jar'
    with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(classes.rglob('*')):
            if path.is_file():
                info = zipfile.ZipInfo(path.relative_to(classes).as_posix(), (2026, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(info, path.read_bytes())
    subprocess.run(['python3', str(ROOT / 'tests/artifact_check.py'), str(jar)], check=True)
    print(jar.relative_to(ROOT))


if __name__ == '__main__':
    main()
