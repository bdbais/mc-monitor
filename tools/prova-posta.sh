#!/bin/bash
# Prova lo script di consegna contro un server finto: tmux e' finto, il log e'
# un file vero. Serve a vedere cosa succede davvero, non cosa credo.
#
# Si lancia a mano dalla radice del progetto:
#
#     bash tools/prova-posta.sh
#
# Non e' un test JUnit perche' quello che va provato e' uno script sh, e provarlo
# dalla JVM vorrebbe dire riscriverlo in Kotlin per finta. Qui gira davvero.
#
# LE PROVE CHE CONTANO sono quelle in cui un giocatore prova a decidere lui chi
# risulta collegato, scrivendo in chat. Prima poteva, in tre modi diversi, e da
# li' si arrivava a far dare per consegnata -- cioe' a cancellare -- la posta di
# chiunque. Le prove 5, 6, 7 e 8 sono quelle.
set -u

QUI="$(cd "$(dirname "$0")" && pwd)"
SORGENTE="$QUI/../app/src/main/resources/posta.sh"
BASE="${TMPDIR:-/tmp}/mcmonitor-prova-posta"
ESITO=0

prepara() {
  rm -rf "$BASE"
  mkdir -p "$BASE/bin" "$BASE/.mcmonitor/posta" "$BASE/server1/serverfiles/logs" "$BASE/server1/log/console"

  # tmux finto. A seconda dei file-interruttore, il "server" rieccheggia il
  # sussurro (vanilla), si lamenta (in due dialetti), o non dice niente.
  cat > "$BASE/bin/tmux" <<'STUB'
#!/bin/bash
# Il server finto. Gli interruttori dicono che cosa finisce nel log: la
# conferma vera, la lamentela vera, oppure la stessa frase scritta in chat da un
# giocatore -- che e' l'attacco.
LOG="$HOME/server1/serverfiles/logs/latest.log"
[ -f "$HOME/.usa-console" ] && LOG="$HOME/server1/log/console/mcserver-console.log"
echo "$*" >> "$HOME/.mcmonitor/tmux.log"
case "$1" in
  send-keys)
    for a in "$@"; do
      case "$a" in
        "tell "*)
          nome=$(printf '%s' "$a" | awk '{print $2}')
          [ -f "$HOME/.chat-conferma" ] &&             echo "[10:00:04] [Server thread/INFO]: <Pluto> You whisper to $nome: ahah" >> "$LOG"
          [ -f "$HOME/.chat-assenza" ] &&             echo "[10:00:04] [Server thread/INFO]: <Pluto> No player was found" >> "$LOG"
          [ -f "$HOME/.echo-vanilla" ] &&             echo "[10:00:05] [Server thread/INFO]: You whisper to $nome: qualcosa" >> "$LOG"
          [ -f "$HOME/.echo-assente" ] &&             echo "[10:00:05] [Server thread/INFO]: No player was found" >> "$LOG"
          [ -f "$HOME/.echo-paper" ] &&             echo "[10:00:05] [Server thread/INFO]: There's no player by that name online." >> "$LOG"
          ;;
      esac
    done
    ;;
esac
exit 0
STUB
  chmod +x "$BASE/bin/tmux"

  # sleep finto: la prova non deve durare dieci secondi a messaggio.
  printf '#!/bin/bash\nexit 0\n' > "$BASE/bin/sleep"
  chmod +x "$BASE/bin/sleep"

  export HOME="$BASE"
  export PATH="$BASE/bin:$PATH"
  : > "$BASE/server1/serverfiles/logs/latest.log"
  : > "$BASE/server1/log/console/mcserver-console.log"
  touch "$BASE/.echo-vanilla"

  sed -e "s|@@POSTA@@|\"\$HOME\"/.mcmonitor/posta/server1.txt|" \
      -e "s|@@CONSEGNATI@@|\"\$HOME\"/.mcmonitor/posta/server1-consegnati.log|" \
      -e "s|@@SESSIONE@@|'mcserver'|" \
      -e "s|@@LIMITE@@|200|" \
      -e "s|@@LOG_MC@@|\"\$HOME\"/'server1/serverfiles/logs/latest.log'|" \
      -e "s|@@LOG_LGSM@@|\"\$HOME\"/'server1/log/console/mcserver-console.log'|" \
      "$SORGENTE" | tr -d '\r' > "$BASE/posta.sh"
}

LOG() { echo "[10:00:00] [Server thread/INFO]: $1" >> "$BASE/server1/serverfiles/logs/latest.log"; }
GREZZO() { printf '%s\n' "$1" >> "$BASE/server1/serverfiles/logs/latest.log"; }
cassetta() { printf '%s\n' "$@" > "$BASE/.mcmonitor/posta/server1.txt"; }
msg() { printf '%s\t%s\t%s' "$1" "$2" "$(printf '%s' "$3" | base64 -w0)"; }
esegui() { sh "$BASE/posta.sh" >"$BASE/uscita.txt" 2>&1; echo $?; }
tell() { grep -o 'tell [A-Za-z0-9_]*' "$BASE/.mcmonitor/tmux.log" 2>/dev/null; }
rimasti() { [ -s "$BASE/.mcmonitor/posta/server1.txt" ] && wc -l < "$BASE/.mcmonitor/posta/server1.txt" | tr -d ' ' || echo 0; }
registro() { [ -f "$BASE/.mcmonitor/posta/server1-consegnati.log" ] && cat "$BASE/.mcmonitor/posta/server1-consegnati.log" || echo ""; }
segno() { sed -n '2p' "$BASE/.mcmonitor/posta/server1.txt.offset" 2>/dev/null || echo "-"; }
segnoFile() { sed -n '1p' "$BASE/.mcmonitor/posta/server1.txt.offset" 2>/dev/null || echo "-"; }

ok() { echo "  OK   $1"; }
ko() { echo "  KO   $1"; echo "       atteso:  [$2]"; echo "       trovato: [$3]"; ESITO=1; }
uguale() { if [ "$2" = "$3" ]; then ok "$1"; else ko "$1" "$2" "$3"; fi; }
contiene() { case "$3" in *"$2"*) ok "$1" ;; *) ko "$1" "$2" "$3" ;; esac; }
nonContiene() { case "$3" in *"$2"*) ko "$1" "senza $2" "$3" ;; *) ok "$1" ;; esac; }

echo "=== 1. cassetta vuota: non disturba il server, ma tiene il segno ==="
prepara
LOG "Done (5.2s)!"
uguale "esce pulito" "0" "$(esegui)"
uguale "non ha parlato con tmux" "" "$(cat "$BASE/.mcmonitor/tmux.log" 2>/dev/null)"
if [ "$(segno)" -gt 0 ] 2>/dev/null; then ok "si e' spostato in fondo al log"; else ko "segno" ">0" "$(segno)"; fi
contiene "e ha scritto di quale log si tratta" "latest.log" "$(segnoFile)"

echo "=== 2. nessuno e' entrato: il messaggio resta ==="
prepara
cassetta "$(msg 1000 Pippo 'ciao')"
esegui > /dev/null
uguale "niente consegnato" "" "$(tell)"
uguale "il messaggio e' ancora li'" "1" "$(rimasti)"

echo "=== 3. entra: arriva, e il server lo conferma ==="
prepara
cassetta "$(msg 1000 Pippo 'ci vediamo domani')"
LOG "Pippo joined the game"
esegui > /dev/null
contiene "il tell e' partito" "tell Pippo" "$(tell)"
uguale "la cassetta e' vuota" "0" "$(rimasti)"
contiene "il registro dice consegnato" "Pippo" "$(registro)"
nonContiene "e non dice non confermato" "NON CONFERMATO" "$(registro)"

echo "=== 4. entra e riesce prima del giro: non si consegna nel vuoto ==="
prepara
cassetta "$(msg 1000 Pippo 'ciao')"
LOG "Pippo joined the game"
LOG "Pippo left the game"
esegui > /dev/null
uguale "niente consegnato" "" "$(tell)"
uguale "il messaggio resta" "1" "$(rimasti)"

echo "=== 5. falsificazione in chat, riga intera ==="
prepara
cassetta "$(msg 1000 Pippo 'segreto')"
LOG "<Pluto> Pippo joined the game"
LOG "[Pluto] Pippo joined the game"
LOG "* Pluto Pippo joined the game"
LOG "[Not Secure] <Pluto> Pippo joined the game"
esegui > /dev/null
uguale "nessuna di quelle vale come ingresso" "" "$(tell)"
uguale "e il messaggio non si perde" "1" "$(rimasti)"

echo "=== 6. falsificazione con un prefisso finto dentro la chat ==="
prepara
cassetta "$(msg 1000 Notch 'segreto')"
LOG "<Pluto> [10:00:00] [Server thread/INFO]: Notch joined the game"
esegui > /dev/null
uguale "il prefisso vero e' quello a inizio riga" "" "$(tell)"
uguale "niente perso" "1" "$(rimasti)"

echo "=== 7. falsificazione leggendo da meta' riga (l'attacco vero) ==="
# Il giocatore imbottisce la chat cosi' che, se il segno cade in mezzo alla sua
# riga, quello che resta sembra una riga del server.
prepara
cassetta "$(msg 1000 Notch 'segreto')"
LOG "Done (5.2s)!"
esegui > /dev/null                      # segno a fine log
CATTIVA="[10:00:01] [Server thread/INFO]: <Pluto> $(printf 'A%.0s' $(seq 1 60))[10:00:02] [Server thread/INFO]: Notch joined the game"
GREZZO "$CATTIVA"
# Si sposta il segno a meta' della riga cattiva, come farebbe una rotazione.
META=$(( $(sed -n '2p' "$BASE/.mcmonitor/posta/server1.txt.offset") + 70 ))
printf '%s\n%s\n' "$BASE/server1/serverfiles/logs/latest.log" "$META" > "$BASE/.mcmonitor/posta/server1.txt.offset"
esegui > /dev/null
uguale "leggere da meta' riga non consegna a nessuno" "" "$(tell)"
uguale "il messaggio e' ancora in cassetta" "1" "$(rimasti)"

echo "=== 8. il segno di un log non vale per l'altro ==="
prepara
cassetta "$(msg 1000 Pippo 'ciao')"
LOG "Done!"
esegui > /dev/null
GRANDE="$BASE/server1/log/console/mcserver-console.log"
for i in $(seq 1 50); do echo "[10:00:00] [Server thread/INFO]: riga di riempimento $i" >> "$GRANDE"; done
echo "[10:00:00] [Server thread/INFO]: <Pluto> Pippo joined the game" >> "$GRANDE"
rm -f "$BASE/server1/serverfiles/logs/latest.log"
esegui > /dev/null
contiene "il segno ora parla del console.log" "console.log" "$(segnoFile)"
uguale "e non ha consegnato niente" "" "$(tell)"

echo "=== 9. il server dice che non c'e': torna in coda ==="
prepara
rm -f "$BASE/.echo-vanilla"; touch "$BASE/.echo-assente"
cassetta "$(msg 1000 Pippo 'importante')"
LOG "Pippo joined the game"
esegui > /dev/null
contiene "il tell e' partito" "tell Pippo" "$(tell)"
uguale "ma il messaggio e' tornato in coda" "1" "$(rimasti)"

echo "=== 10. server Paper, parole diverse: torna in coda lo stesso ==="
prepara
rm -f "$BASE/.echo-vanilla"; touch "$BASE/.echo-paper"
cassetta "$(msg 1000 Pippo 'importante')"
LOG "Pippo joined the game"
esegui > /dev/null
uguale "il messaggio e' tornato in coda" "1" "$(rimasti)"

echo "=== 11. server muto: non si consegna all'infinito, e il testo si ritrova ==="
prepara
rm -f "$BASE/.echo-vanilla"
cassetta "$(msg 1000 Pippo 'il testo che conta')"
LOG "Pippo joined the game"
esegui > /dev/null
uguale "la cassetta si svuota" "0" "$(rimasti)"
contiene "ma il registro lo dice" "NON CONFERMATO Pippo" "$(registro)"
contiene "e il testo c'e' per intero" "$(printf '%s' 'il testo che conta' | base64 -w0)" "$(registro)"

echo "=== 12. un giocatore finge la conferma: non diventa \"consegnato\" ==="
# L'attacco vero: nel log anche la chat ha il prefisso del server, quindi il
# prefisso da solo non distingue niente. Qui il server NON conferma: se la riga
# di chat passasse, il messaggio risulterebbe consegnato e sparirebbe.
prepara
rm -f "$BASE/.echo-vanilla"; touch "$BASE/.chat-conferma"
cassetta "$(msg 1000 Pippo 'segreto')"
LOG "Pippo joined the game"
esegui > /dev/null
contiene "il tell e' partito" "tell Pippo" "$(tell)"
contiene "ma non risulta confermato da nessuno" "NON CONFERMATO" "$(registro)"
contiene "e il testo resta recuperabile" "$(printf '%s' 'segreto' | base64 -w0)" "$(registro)"

echo "=== 13. un giocatore finge la conferma mentre il server dice che non c'e' ==="
# Qui la bugia doveva vincere sulla verita': la conferma si controlla per prima.
prepara
rm -f "$BASE/.echo-vanilla"; touch "$BASE/.echo-assente"; touch "$BASE/.chat-conferma"
cassetta "$(msg 1000 Pippo 'importante')"
LOG "Pippo joined the game"
esegui > /dev/null
uguale "vince il server: il messaggio torna in coda" "1" "$(rimasti)"
nonContiene "e non risulta consegnato" "	Pippo	1000" "$(registro)"

echo "=== 14. un giocatore finge l'assenza: la consegna vera non torna indietro ==="
# L'attacco opposto: far tornare in coda un messaggio gia' arrivato, e far
# sussurrare il bersaglio ogni minuto finche' la coda non si riempie.
prepara
# Il server tace: se la riga di chat passasse, deciderebbe lei, e il messaggio
# tornerebbe in coda per essere risussurrato al giro dopo. E a quello dopo.
rm -f "$BASE/.echo-vanilla"; touch "$BASE/.chat-assenza"
cassetta "$(msg 1000 Pippo 'ciao')"
LOG "Pippo joined the game"
esegui > /dev/null
uguale "la chat non lo rimette in coda" "0" "$(rimasti)"
contiene "resta segnato non confermato" "NON CONFERMATO" "$(registro)"

# E con la conferma vera del server, la chat non la annulla.
prepara
touch "$BASE/.chat-assenza"
cassetta "$(msg 1000 Anna 'ciao')"
LOG "Anna joined the game"
esegui > /dev/null
uguale "con la conferma vera resta consegnato" "0" "$(rimasti)"
contiene "e il registro lo dice" "Anna" "$(registro)"

echo "=== 15. server fermo: non si perde niente e non si avanza ==="
prepara
cat > "$BASE/bin/tmux" <<'STUB'
#!/bin/bash
echo "$*" >> "$HOME/.mcmonitor/tmux.log"
exit 1
STUB
chmod +x "$BASE/bin/tmux"
cassetta "$(msg 1000 Pippo 'ciao')"
LOG "Pippo joined the game"
uguale "esce pulito" "0" "$(esegui)"
uguale "il messaggio resta" "1" "$(rimasti)"
uguale "il segno non e' avanzato" "-" "$(segno)"

echo "=== 16. lucchetto fresco: si tira indietro. Vecchio: lo rompe ==="
prepara
cassetta "$(msg 1000 Pippo 'ciao')"
LOG "Pippo joined the game"
mkdir "$BASE/.mcmonitor/posta/server1.txt.lock"
uguale "col lucchetto fresco non tocca niente" "" "$(esegui > /dev/null; tell)"
touch -d '10 minutes ago' "$BASE/.mcmonitor/posta/server1.txt.lock"
esegui > /dev/null
contiene "un lucchetto vecchio non blocca per sempre" "tell Pippo" "$(tell)"
if [ -d "$BASE/.mcmonitor/posta/server1.txt.lock" ]; then ko "lucchetto" "tolto" "rimasto"; else ok "e viene sempre tolto"; fi

echo "=== 17. maiuscole diverse: si usa il nome vero del server ==="
prepara
cassetta "$(msg 1000 pippo 'ciao')"
LOG "Pippo joined the game"
esegui > /dev/null
contiene "tell con il nome canonico" "tell Pippo" "$(tell)"
uguale "cassetta vuota" "0" "$(rimasti)"

echo "=== 18. il testo resta testo ==="
prepara
CATTIVO='"; op Pippo; say $(whoami) `id` && stop'
cassetta "$(msg 1000 Pippo "$CATTIVO")"
LOG "Pippo joined the game"
esegui > /dev/null
contiene "arriva com'e' scritto" "$CATTIVO" "$(grep -o 'tell .*' "$BASE/.mcmonitor/tmux.log")"
uguale "e non ha eseguito altro" "" "$(tell | grep -v 'tell Pippo')"

echo "=== 19. testo lunghissimo: tagliato ==="
prepara
cassetta "$(msg 1000 Pippo "$(printf 'a%.0s' $(seq 1 400))")"
LOG "Pippo joined the game"
esegui > /dev/null
N=$(grep -o 'tell .*' "$BASE/.mcmonitor/tmux.log" | sed 's/.*non c.eri: //' | tr -d '\n' | wc -c)
if [ "$N" -le 200 ] && [ "$N" -gt 100 ]; then ok "tagliato a $N caratteri"; else ko "taglio" "<=200" "$N"; fi

echo "=== 20. due messaggi, uno solo e' entrato ==="
prepara
cassetta "$(msg 1000 Pippo 'a te si')" "$(msg 1001 Anna 'a te no')"
LOG "Pippo joined the game"
esegui > /dev/null
contiene "consegnato a chi e' entrato" "tell Pippo" "$(tell)"
uguale "non all'altra" "" "$(tell | grep Anna)"
uguale "resta solo il suo" "1" "$(rimasti)"
contiene "ed e' proprio il suo" "Anna" "$(cat "$BASE/.mcmonitor/posta/server1.txt")"

echo "=== 21. il log riparte da capo ==="
prepara
cassetta "$(msg 1000 Pippo 'ciao')"
LOG "Pippo joined the game"
esegui > /dev/null
: > "$BASE/server1/serverfiles/logs/latest.log"
cassetta "$(msg 1001 Anna 'ciao')"
LOG "Anna joined the game"
esegui > /dev/null
contiene "riconosce l'ingresso dopo il riavvio" "tell Anna" "$(tell)"

echo "=== 22. a chi era GIA' dentro il messaggio arriva lo stesso ==="
# Il caso che non funzionava: il suo ingresso e' rimasto dietro al segno, e
# l'app intanto prometteva "gli arriva entro un minuto".
prepara
LOG "Pippo joined the game"
esegui > /dev/null                       # cassetta vuota: il segno va in fondo
uguale "il segno ha superato il suo ingresso" "" "$(tell)"
cassetta "$(msg 1000 Pippo 'ti scrivo mentre sei dentro')"
esegui > /dev/null
contiene "gli arriva lo stesso" "tell Pippo" "$(tell)"
uguale "e la cassetta si svuota" "0" "$(rimasti)"

echo "=== 23. chi e' uscito non risulta piu' dentro ==="
prepara
LOG "Pippo joined the game"
esegui > /dev/null
LOG "Pippo left the game"
esegui > /dev/null
cassetta "$(msg 1000 Pippo 'ciao')"
esegui > /dev/null
uguale "niente consegnato" "" "$(tell)"
uguale "il messaggio resta" "1" "$(rimasti)"

echo "=== 24. dopo un riavvio del server non c'e' dentro piu' nessuno ==="
prepara
LOG "Pippo joined the game"
esegui > /dev/null
: > "$BASE/server1/serverfiles/logs/latest.log"   # il log riparte da capo
cassetta "$(msg 1000 Pippo 'ciao')"
esegui > /dev/null
uguale "non si consegna a un fantasma" "" "$(tell)"
uguale "il messaggio resta in attesa" "1" "$(rimasti)"

echo "=== 25. si lavora a lotti: la coda lunga non tiene il lucchetto per sempre ==="
prepara
{ for i in $(seq 1 12); do msg "100$i" "Pippo" "messaggio $i"; echo; done; } > "$BASE/.mcmonitor/posta/server1.txt"
LOG "Pippo joined the game"
esegui > /dev/null
N=$(grep -c 'tell Pippo' "$BASE/.mcmonitor/tmux.log" 2>/dev/null || echo 0)
if [ "$N" -le 8 ] && [ "$N" -gt 0 ]; then ok "ne ha fatti $N, non tutti e dodici"; else ko "lotto" "1..8" "$N"; fi
uguale "gli altri restano in coda" "4" "$(rimasti)"

echo "=== 26. il file di lavoro sta accanto alla cassetta, non in /tmp ==="
prepara
grep -q 'mktemp "$POSTA' "$BASE/posta.sh" && ok "mktemp accanto alla cassetta" || ko "mktemp" 'mktemp "$POSTA...' "?"

echo
[ "$ESITO" = "0" ] && echo ">>> tutto a posto" || echo ">>> QUALCOSA NON VA"
exit "$ESITO"
