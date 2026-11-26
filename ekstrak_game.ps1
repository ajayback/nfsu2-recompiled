
python -m pip install capstone pefile unicorn keystone-engine

$GAME = 'C:\Program Files (x86)\NFS Underground 2'  # ubah ke folder game Anda
New-Item -ItemType Directory -Force work | Out-Null

python scripts\normalize_exe.py "$GAME\SPEED2.EXE" work\SPEED2.analysis.exe
python ..\pcrecomp\tools\cpp\vtable_scan.py work\SPEED2.analysis.exe --seeds work\vtable_seeds.json
python ..\pcrecomp\tools\cpp\rtti.py work\SPEED2.analysis.exe --seeds work\rtti_seeds.json

python -c "import json; fs=['work/vtable_seeds.json','work/rtti_seeds.json']; d={s['address']:s for f in fs for s in json.load(open(f, encoding='utf-8'))}; json.dump(list(d.values()), open('work/seeds_all.json','w'), indent=1)"

python scripts\run_lift.py catalog work\SPEED2.analysis.exe work\seeds_all.json work\catalog.json
python scripts\run_lift.py lift work\SPEED2.analysis.exe work\catalog.json src\recomp\gen