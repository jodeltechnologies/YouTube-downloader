"""Bundle a pinned official yt-dlp release and verify its published SHA-256."""
import hashlib
import json
from pathlib import Path
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parent.parent
config = json.loads((ROOT / 'engine.json').read_text())
target = ROOT / 'app/src/main/res/raw/ytdlp'
version_file = ROOT / 'app/src/main/assets/engine-version.txt'
expected = config['sha256']
if not target.is_file() or hashlib.sha256(target.read_bytes()).hexdigest() != expected:
    request = Request(f"https://github.com/yt-dlp/yt-dlp/releases/download/{config['version']}/yt-dlp", headers={'User-Agent':'VideoPocket-Build'})
    with urlopen(request, timeout=90) as response:
        binary = response.read(20 * 1024 * 1024)
    if hashlib.sha256(binary).hexdigest() != expected:
        raise RuntimeError('Download engine checksum did not match the pinned release.')
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(binary)
version_file.parent.mkdir(parents=True, exist_ok=True)
version_file.write_text(config['version'] + '\n')
print('Verified bundled yt-dlp ' + config['version'])
