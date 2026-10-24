#!/usr/bin/env python3
"""Generate compatibility artifacts locally from owner-supplied, audited inputs."""
import argparse,hashlib,pathlib,subprocess,sys,os,tempfile,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
sys.dont_write_bytecode=True
from build import dependencies
HASHES={'residence':'b275107efa695a4c4b54035f8523a49ffcec37948699c90672661a10550945ce','warp':'68aceac6f875a91a80fb881b9554ba9c56315004eb4116b673b59f4109cab2ee'}
def main():
 a=argparse.ArgumentParser();a.add_argument('kind',choices=HASHES);a.add_argument('input',type=pathlib.Path);a.add_argument('output',type=pathlib.Path);a.add_argument('--deps-dir',default=os.getenv('NOMI_DEPS_DIR'));x=a.parse_args()
 if not x.deps_dir:a.error('--deps-dir or NOMI_DEPS_DIR required')
 if x.output.exists():a.error('output already exists; choose a new output path')
 if hashlib.sha256(x.input.read_bytes()).hexdigest()!=HASHES[x.kind]:a.error('unsupported input SHA256; review a new version before updating generator')
 with zipfile.ZipFile(x.input) as z:
  if len(z.namelist())!=len(set(z.namelist())):a.error('duplicate archive entries')
  if any(n.upper().endswith(('.SF','.RSA','.DSA')) for n in z.namelist()):a.error('signed archives require separate review')
 cp=os.pathsep.join(map(str,dependencies(x.deps_dir)))
 work=ROOT/'.work';work.mkdir(exist_ok=True)
 with tempfile.TemporaryDirectory(prefix='compat-',dir=work) as temp:
  classes=pathlib.Path(temp)/'classes';classes.mkdir()
  src=list((ROOT/'compat/src').rglob('*.java'))+[ROOT/'src/main/java/org/nomiteam/vip'/n for n in ['WarpPolicy.java','QuoteBook.java']]
  subprocess.run(['javac','--release','21','-encoding','UTF-8','-cp',cp,'-d',str(classes),*map(str,src)],check=True)
  staged=pathlib.Path(temp)/'output.jar'
  subprocess.run(['java','-cp',str(classes)+os.pathsep+cp,'Patch',x.kind,str(x.input),str(staged),str(classes)],check=True)
  subprocess.run(['java','-Xverify:all','-cp',str(staged)+os.pathsep+str(classes)+os.pathsep+cp,'VerifyGenerated',x.kind],check=True)
  checks=['residence','unchanged'] if x.kind=='residence' else ['price','payments','paid','quote','serialized','unchanged']
  for case in checks:subprocess.run([sys.executable,str(ROOT/'tests/compat_check.py'),case,str(x.input),str(staged)],check=True)
  x.output.parent.mkdir(parents=True,exist_ok=True)
  # Exclusive creation avoids overwriting an existing artifact in a race.
  with x.output.open('xb') as f:f.write(staged.read_bytes())
 print('SHA256',hashlib.sha256(x.output.read_bytes()).hexdigest())
if __name__=='__main__':main()
