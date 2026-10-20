"""Copy audited launch/observe/stop helpers with a fresh owned preview port."""
from pathlib import Path
HERE=Path(__file__).resolve().parent;OLD=HERE.parent/'to92-molded-detail-r2'
if __name__=='__main__':
    for name in ('launch_player.ps1','stop_player.ps1','observe_player_host.ps1','record_preview_exit.ps1','player_bootstrap.mjs'):
        raw=(OLD/name).read_bytes()
        if b'8909' in raw:raw=raw.replace(b'8909',b'8910')
        with (HERE/name).open('xb') as f:f.write(raw)
    print('PASS audited player helpers copied; port8910, no launch')
