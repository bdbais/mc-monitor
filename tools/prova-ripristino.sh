#!/bin/bash
# Prova il ripristino di un backup contro un albero di server finto, con un
# archivio tar.gz vero fatto come lo fa LinuxGSM.
#
# Si lancia a mano dalla radice del progetto:
#
#     bash tools/prova-ripristino.sh
#
# È l'operazione da cui non si torna indietro da soli, quindi le prove che
# contano sono quelle in cui deve RIFIUTARSI: server acceso, archivio senza
# mondo, spazio finito. E quella in cui l'estrazione fallisce a metà e tutto
# deve tornare esattamente com'era.
set -u

QUI="$(cd "$(dirname "$0")" && pwd)"
SORGENTE="$QUI/../app/src/main/resources/ripristino.sh"
BASE="${TMPDIR:-/tmp}/mcmonitor-prova-ripristino"
ESITO=0

prepara() {
  rm -rf "$BASE"
  mkdir -p "$BASE/bin" "$BASE/server/serverfiles/world" "$BASE/server/lgsm/config-lgsm/mcserver" "$BASE/server/lgsm/backup"

  # tmux finto: c'e' una sessione solo se glielo diciamo noi.
  cat > "$BASE/bin/tmux" <<'STUB'
#!/bin/bash
case "$1" in has-session) [ -f "$HOME/.acceso" ] || exit 1 ;; esac
exit 0
STUB
  chmod +x "$BASE/bin/tmux"
  export HOME="$BASE"
  export PATH="$BASE/bin:$PATH"

  # Il mondo di IERI, quello che finisce nell'archivio.
  echo "il mondo di ieri" > "$BASE/server/serverfiles/world/level.dat"
  echo "server-name=ieri" > "$BASE/server/serverfiles/server.properties"
  # La configurazione di LinuxGSM di ieri: NON deve tornare indietro.
  echo 'executable="./minecraft_server.jar"' > "$BASE/server/lgsm/config-lgsm/mcserver/mcserver.cfg"

  # L'archivio, fatto come lo fa LinuxGSM: dalla radice del server, voci ./...
  (cd "$BASE/server" && tar -czf "$BASE/server/lgsm/backup/mcserver-2026-08-23-231000.tar.gz" \
     --exclude=./lgsm/backup ./serverfiles ./lgsm 2>/dev/null)

  # Il mondo di OGGI, quello rovinato che vogliamo buttare.
  echo "il mondo rovinato di oggi" > "$BASE/server/serverfiles/world/level.dat"
  echo "server-name=oggi" > "$BASE/server/serverfiles/server.properties"
  # E la configurazione di OGGI, che deve restare.
  echo 'executable="./fabric-server-launch.jar"' > "$BASE/server/lgsm/config-lgsm/mcserver/mcserver.cfg"

  sed -e "s|@@DIR@@|\"\$HOME\"/'server'|" \
      -e "s|@@ARCHIVIO@@|\"\$HOME\"/'server/lgsm/backup'/'mcserver-2026-08-23-231000.tar.gz'|" \
      -e "s|@@SESSIONE@@|'mcserver'|" \
      -e "s|@@EXIT_NO_ARCHIVIO@@|95|" \
      -e "s|@@EXIT_ACCESO@@|94|" \
      -e "s|@@EXIT_SPAZIO@@|93|" \
      -e "s|@@EXIT_NIENTE_MONDO@@|92|" \
      -e "s|@@EXIT_ESTRAZIONE@@|91|" \
      "$SORGENTE" | tr -d '\r' > "$BASE/ripristino.sh"
}

esegui() { sh "$BASE/ripristino.sh" > "$BASE/uscita.txt" 2>&1; echo $?; }
uscita() { cat "$BASE/uscita.txt" 2>/dev/null; }
mondo() { cat "$BASE/server/serverfiles/world/level.dat" 2>/dev/null || echo "(niente)"; }
config() { cat "$BASE/server/lgsm/config-lgsm/mcserver/mcserver.cfg" 2>/dev/null || echo "(niente)"; }
daparte() { ls -d "$BASE/server/serverfiles.prima-del-ripristino."* 2>/dev/null | head -1; }

ok() { echo "  OK   $1"; }
ko() { echo "  KO   $1"; echo "       atteso:  [$2]"; echo "       trovato: [$3]"; ESITO=1; }
uguale() { if [ "$2" = "$3" ]; then ok "$1"; else ko "$1" "$2" "$3"; fi; }
contiene() { case "$3" in *"$2"*) ok "$1" ;; *) ko "$1" "$2" "$3" ;; esac; }

echo "=== 1. server acceso: si rifiuta e non tocca niente ==="
prepara
touch "$BASE/.acceso"
uguale "esce con il codice del server acceso" "94" "$(esegui)"
contiene "e lo dice" "SERVER ACCESO" "$(uscita)"
contiene "il mondo di oggi e' intatto" "rovinato" "$(mondo)"
uguale "niente e' stato messo da parte" "" "$(daparte)"
rm -f "$BASE/.acceso"

echo "=== 2. archivio che non c'e': si rifiuta ==="
prepara
rm -f "$BASE/server/lgsm/backup/"*.tar.gz
uguale "esce con il codice dell'archivio" "95" "$(esegui)"
contiene "e lo dice" "ARCHIVIO NON TROVATO" "$(uscita)"
contiene "il mondo e' intatto" "rovinato" "$(mondo)"

echo "=== 3. archivio senza il mondo dentro: si rifiuta ==="
prepara
(cd "$BASE/server" && tar -czf "$BASE/server/lgsm/backup/mcserver-2026-08-23-231000.tar.gz" ./lgsm 2>/dev/null)
uguale "esce con il codice giusto" "92" "$(esegui)"
contiene "e lo dice" "NIENTE MONDO" "$(uscita)"
contiene "il mondo di oggi e' ancora li'" "rovinato" "$(mondo)"
uguale "niente messo da parte" "" "$(daparte)"

echo "=== 4. il caso buono: torna il mondo di ieri ==="
prepara
uguale "esce pulito" "0" "$(esegui)"
contiene "lo dice" "@@RIMESSO" "$(uscita)"
contiene "il mondo e' quello di ieri" "il mondo di ieri" "$(mondo)"
if [ -n "$(daparte)" ]; then ok "quello di oggi e' stato messo da parte"; else ko "da parte" "una cartella" "niente"; fi
contiene "e non e' stato cancellato" "rovinato" "$(cat "$(daparte)/world/level.dat" 2>/dev/null)"

echo "=== 5. la configurazione di LinuxGSM NON torna indietro ==="
contiene "resta quella di oggi" "fabric-server-launch" "$(config)"

echo "=== 6. spazio insufficiente: si rifiuta prima di toccare ==="
prepara
cat > "$BASE/bin/df" <<'STUB'
#!/bin/bash
echo "Filesystem 1024-blocks Used Available Capacity Mounted"
echo "/dev/finto 1000 1000 0 100% /"
STUB
chmod +x "$BASE/bin/df"
uguale "esce con il codice dello spazio" "93" "$(esegui)"
contiene "e lo dice" "SPAZIO INSUFFICIENTE" "$(uscita)"
contiene "il mondo di oggi e' intatto" "rovinato" "$(mondo)"
uguale "niente messo da parte" "" "$(daparte)"
rm -f "$BASE/bin/df"

echo "=== 7. l'estrazione fallisce a meta': tutto torna com'era ==="
prepara
cat > "$BASE/bin/tar" <<'STUB'
#!/bin/bash
# Elenca normalmente, ma si rifiuta di estrarre.
for a in "$@"; do case "$a" in -tzf|-t*) exec /usr/bin/tar "$@" ;; esac; done
echo "tar: errore finto" >&2
exit 2
STUB
chmod +x "$BASE/bin/tar"
uguale "esce con il codice dell'estrazione" "91" "$(esegui)"
contiene "e lo dice" "ESTRAZIONE FALLITA" "$(uscita)"
contiene "il mondo di oggi e' tornato al suo posto" "rovinato" "$(mondo)"
uguale "e non e' rimasta nessuna cartella a meta'" "" "$(daparte)"
rm -f "$BASE/bin/tar"

echo "=== 8. due ripristini nello stesso secondo non si mangiano il mondo ==="
# La data arriva al secondo: senza un nome libero, il secondo mv finirebbe
# DENTRO la cartella del primo, il rollback non la ritroverebbe piu', e l'app
# direbbe "rimesso" con il mondo sparito.
prepara
esegui > /dev/null
mkdir -p "$BASE/server/serverfiles/world"
echo "un altro mondo" > "$BASE/server/serverfiles/world/level.dat"
esegui > /dev/null
N=$(ls -d "$BASE/server/serverfiles.prima-del-ripristino."* 2>/dev/null | wc -l | tr -d ' ')
if [ "$N" -ge 2 ]; then ok "due cartelle distinte ($N)"; else ko "cartelle" ">=2" "$N"; fi
for d in "$BASE/server/serverfiles.prima-del-ripristino."*; do
  if [ -d "$d/serverfiles" ]; then ko "annidamento" "nessuno" "$d contiene serverfiles"; fi
done
ok "e nessuna e' finita dentro l'altra"
contiene "il mondo e' quello di ieri" "il mondo di ieri" "$(mondo)"

echo "=== 9. rifarlo due volte non impila danni ==="
prepara
esegui > /dev/null
sleep 1
esegui > /dev/null
contiene "il mondo e' quello di ieri" "il mondo di ieri" "$(mondo)"
N=$(ls -d "$BASE/server/serverfiles.prima-del-ripristino."* 2>/dev/null | wc -l | tr -d ' ')
if [ "$N" -ge 1 ]; then ok "ogni giro lascia la sua copia ($N)"; else ko "copie" ">=1" "$N"; fi

echo
[ "$ESITO" = "0" ] && echo ">>> tutto a posto" || echo ">>> QUALCOSA NON VA"
exit "$ESITO"
