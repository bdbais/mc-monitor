#!/usr/bin/env python3
"""Genera la pagina del manuale per il sito, da MANUALE.md.

Il manuale stava solo su GitHub, e il pulsante "Manuale d'uso" dentro l'app
puntava lì. Quando GitHub è giù — succede — dall'app il manuale non si apre e
chi lo cerca vede una pagina di errore di un sito che non è il nostro. Il
manuale è documentazione dell'app: deve stare dove sta l'app.

Rigenerare dopo ogni ritocco a MANUALE.md:

    python tools/manuale.py && cd vetrina && npx wrangler deploy
"""

import re
import shutil
from pathlib import Path

import markdown

RADICE = Path(__file__).resolve().parent.parent
SORGENTE = RADICE / "MANUALE.md"
USCITA = RADICE / "vetrina" / "public" / "manuale"
IMMAGINI = USCITA / "img"


def versione() -> str:
    """La versione dell'app, presa dove è vera: il file di build."""
    testo = (RADICE / "app" / "build.gradle.kts").read_text(encoding="utf-8")
    trovata = re.search(r'versionName\s*=\s*"([^"]+)"', testo)
    return trovata.group(1) if trovata else ""


def pezzi(testo: str) -> tuple[str, str, str]:
    """Titolo, indice e corpo.

    L'indice nel file è un elenco di collegamenti, che su GitHub è l'unico modo
    di averne uno. Sulla pagina diventa la barra laterale, quindi va tolto dal
    corpo: lasciarlo vorrebbe dire lo stesso indice due volte.
    """
    righe = testo.split("\n")
    titolo = righe[0].lstrip("# ").strip()
    taglio = righe.index("---")
    testa = righe[1:taglio]
    indice = [r for r in testa if r.startswith("- [")]
    # La versione la stampa il modello, presa dal file di build: scritta a
    # mano nel testo resta indietro a ogni rilascio, e lo ha gia' fatto.
    intro = [r for r in testa
             if not r.startswith("- [") and not r.startswith("Versione ")]
    return titolo, "\n".join(indice), "\n".join(intro + righe[taglio + 1:])


def immagini(html: str) -> str:
    """Le immagini vengono copiate accanto alla pagina.

    Puntare a quelle di GitHub rimetterebbe la dipendenza da cui si sta
    scappando: pagina nostra, figure rotte.
    """
    IMMAGINI.mkdir(parents=True, exist_ok=True)
    for nome in set(re.findall(r'src="store/screenshots/([^"]+)"', html)):
        da = RADICE / "store" / "screenshots" / nome
        if da.exists():
            shutil.copy2(da, IMMAGINI / nome)
    return html.replace('src="store/screenshots/', 'src="/manuale/img/')


MODELLO = """<!doctype html>
<html lang="it">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{titolo} — MC Monitor</title>
<meta name="description" content="Manuale d'uso di MC Monitor: come amministrare un server Minecraft LinuxGSM dal telefono, scheda per scheda.">
<meta name="theme-color" content="#0E1512">
<link rel="icon" href="/img/icona-180.png" sizes="180x180">
<link rel="apple-touch-icon" href="/img/icona-180.png">
<link rel="preload" href="/press-start-2p.woff2" as="font" type="font/woff2" crossorigin>
<link rel="stylesheet" href="/stile.css">
<link rel="stylesheet" href="/manuale/manuale.css">
<meta property="og:type" content="article">
<meta property="og:title" content="{titolo} — MC Monitor">
<meta property="og:description" content="Come amministrare un server Minecraft LinuxGSM dal telefono, scheda per scheda.">
<meta property="og:url" content="https://mcmonitor.bais.info/manuale/">
<meta property="og:locale" content="it_IT">
</head>
<body>
<a class="salta" href="#contenuto">Vai al contenuto</a>

<header class="testata">
  <div class="dentro testata-riga">
    <a class="testata-casa" href="/"><img class="testata-icona" src="/img/icona-180.png" width="40" height="40" alt=""></a>
    <span class="pixel testata-nome">MC Monitor</span>
    <nav class="testata-nav" aria-label="Sezioni">
      <a href="/">Il sito</a>
      <a href="/scarica">Scarica</a>
    </nav>
  </div>
</header>

<div class="dentro manuale">

  <nav class="capitoli" aria-label="Capitoli">
    <p class="pixel capitoli-titolo">Capitoli</p>
    {indice}
  </nav>

  <main id="contenuto" class="testo" tabindex="-1">
    <h1 class="pixel titolo-manuale">{titolo}</h1>
    <p class="versione">Versione {versione}</p>
    {corpo}
  </main>

</div>

<footer class="coda">
  <div class="dentro">
    <p>MC Monitor è software libero, licenza Apache 2.0.
       <a href="https://github.com/bdbais/mc-monitor">Il codice sta su GitHub</a>.</p>
  </div>
</footer>
</body>
</html>
"""


def main() -> None:
    testo = SORGENTE.read_text(encoding="utf-8")
    titolo, indice, corpo = pezzi(testo)

    md = markdown.Markdown(extensions=["tables", "fenced_code", "sane_lists", "attr_list", "toc"])
    pagina = MODELLO.format(
        titolo=titolo,
        versione=versione(),
        indice=md.convert(indice),
        corpo=immagini(md.reset().convert(corpo)),
    )

    USCITA.mkdir(parents=True, exist_ok=True)
    (USCITA / "index.html").write_text(pagina, encoding="utf-8")
    print(f"scritto {USCITA / 'index.html'} ({len(pagina)} byte)")


if __name__ == "__main__":
    main()
