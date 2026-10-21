"""Verify exact borrowed player/resource helpers with only the selected port changed."""
import hashlib,subprocess
from pathlib import Path
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
OLD=HERE.parent/'cross-family-whitespace-review-r1'
NAMES=('launch_player.ps1','stop_player.ps1','observe_player_host.ps1','record_preview_exit.ps1','observe_browser.ps1','player_bootstrap.mjs')
def verify():
    result={}
    for name in NAMES:
        prior=OLD/name;raw=prior.read_bytes()
        committed=subprocess.check_output(['git','show','6426b3ff702c8197324dd996b5b4ede899bb9139:'+prior.relative_to(ROOT).as_posix()],cwd=ROOT)
        assert raw==committed
        source=raw.decode('utf-8-sig').replace('\r\n','\n')
        if name in ('launch_player.ps1','observe_player_host.ps1'):source=source.replace('8910','8911')
        assert (HERE/name).read_bytes()==source.encode('utf-8')
        result[name]={'publishedSha256':hashlib.sha256(raw).hexdigest(),'selectedSha256':hashlib.sha256((HERE/name).read_bytes()).hexdigest(),'adaptation':'port8910->8911 only' if name in ('launch_player.ps1','observe_player_host.ps1') else 'unchanged except explicit LF encoding'}
    return result
if __name__=='__main__':verify();print('PASS six selected resource/player helpers exact published bytes with port8911 adaptation')
