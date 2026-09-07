from pathlib import Path

root = Path(__file__).resolve().parents[2]

f = root / 'app/src/main/java/com/erikraft/drop/OnionTransferActivity.java'
s = f.read_text(encoding='utf-8').replace('import org.nanohttpd.protocols.http.NanoHTTPD;', 'import fi.iki.elonen.NanoHTTPD;')
f.write_text(s, encoding='utf-8')

t = root / 'app/src/main/java/com/erikraft/drop/TorController.java'
s = t.read_text(encoding='utf-8').replace('if (instance == null) instance = new TorController(context.getApplicationContext());', 'if (instance == null) instance = new TorController((android.app.Application) context.getApplicationContext());')
t.write_text(s, encoding='utf-8')
