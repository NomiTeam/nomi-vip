#!/usr/bin/env python3
"""Offline build. Dependencies remain outside distributable source/artifacts."""
import argparse, os, pathlib, subprocess, shutil, zipfile, re
ROOT=pathlib.Path(__file__).resolve().parent

def dependencies(directory):
    root=pathlib.Path(directory)
    jars=sorted(root.rglob('*.jar'))
    api=[p for p in jars if p.name.startswith('purpur-api-26.3')]
    if not api: raise SystemExit('purpur-api 26.3 is required in --deps-dir')
    chosen=sorted(api,key=lambda p: ('stable' in p.name,p.name))[-1]
    # Exclude obsolete APIs. All other dependencies are compileOnly, never bundled.
    def version(p):return tuple(int(n) for n in re.findall(r'\d+',p.parent.name))
    # ASM must precede jars that might embed older unrelocated ASM classes.
    asm={}
    for p in jars:
        if 'org/ow2/asm/' in p.as_posix():
            key=p.parent.parent.name
            if key not in asm or version(p)>version(asm[key]):asm[key]=p
    # The API runtime checks need a coherent Adventure version, not the oldest
    # duplicate in the supplied offline library tree.
    adventure={}
    for p in jars:
        if '/net/kyori/' in p.as_posix():
            key=p.parent.parent.name
            if key not in adventure or version(p)>version(adventure[key]):adventure[key]=p
    others=[]
    for p in jars:
        if 'purpur-api-' in p.name or 'org/ow2/asm/' in p.as_posix() or '/net/kyori/' in p.as_posix():continue
        if 'plugins' in p.parts:
            with zipfile.ZipFile(p) as z:
                if not any(n in z.namelist() for n in ['com/bekvon/bukkit/residence/Residence.class','cn/handyplus/warp/api/PlayerWarpApi.class','net/milkbowl/vault/economy/Economy.class']):continue
        others.append(p)
    return [chosen]+sorted(asm.values())+sorted(adventure.values())+others

def main():
    a=argparse.ArgumentParser();a.add_argument('--deps-dir',default=os.getenv('NOMI_DEPS_DIR'));a.add_argument('--dh-jar',type=pathlib.Path);args=a.parse_args()
    if not args.deps_dir:a.error('use --deps-dir or NOMI_DEPS_DIR')
    version=subprocess.check_output(['javac','-version'],text=True,stderr=subprocess.STDOUT).strip()
    if not version.startswith('javac 25'):raise SystemExit('Build requires JDK 25; found '+version)
    cp=os.pathsep.join(map(str,dependencies(args.deps_dir)))
    out=ROOT/'build';classes=out/'classes'
    if classes.exists():shutil.rmtree(classes)
    classes.mkdir(parents=True,exist_ok=True)
    sources=sorted((ROOT/'src/main/java').rglob('*.java'))+sorted((ROOT/'tests').rglob('*.java'))
    subprocess.run(['javac','--release','21','-encoding','UTF-8','-cp',cp,'-d',str(classes),*map(str,sources)],check=True)
    subprocess.run(['java','-cp',str(classes),'org.nomiteam.vip.PolicyTest'],check=True)
    for name in 'restored off revoke claim native jump nofly quote stale-quote'.split():
        subprocess.run(['java','-ea','-cp',str(classes),'org.nomiteam.vip.TransitionTest',name],check=True)
    subprocess.run(['java','-Xverify:all','-cp',str(classes)+os.pathsep+cp,'org.nomiteam.vip.DhTest'],check=True)
    if args.dh_jar:
        subprocess.run(['java','-Xverify:all','-cp',str(classes)+os.pathsep+cp+os.pathsep+str(args.dh_jar),'org.nomiteam.vip.DhBytecodeTest'],check=True)
    for p in (ROOT/'src/main/resources').rglob('*'):
        if p.is_file():shutil.copyfile(p,classes/p.relative_to(ROOT/'src/main/resources'))
    jar=out/'NomiVIP-1.0.0.jar'
    with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
        for p in sorted(classes.rglob('*')):
            if p.is_file():
                info=zipfile.ZipInfo(p.relative_to(classes).as_posix(),(2026,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
                z.writestr(info,p.read_bytes())
    print(jar.relative_to(ROOT))
if __name__=='__main__':main()
