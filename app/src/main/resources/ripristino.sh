#!/bin/sh
# Scritto da MC Monitor: rimette il mondo com'era in una copia di sicurezza.
#
# È l'operazione da cui non si torna indietro da soli, quindi tre scelte:
#
# - SI TOCCA SOLO serverfiles, cioe' la cartella del gioco. Torna indietro
#   tutto quello che c'e' dentro: il mondo, ma anche i mod, server.properties e
#   i jar. NON tornano indietro le impostazioni di LinuxGSM (lgsm/), che sono
#   quelle da cui dipende se il server parte: dopo una riparazione della riga di
#   avvio, rimettere un backup non la rimangia.
# - QUELLO CHE C'È ADESSO NON SI CANCELLA, SI SPOSTA. Una rinomina è istantanea
#   e non occupa un byte in più, e se il ripristino delude si torna indietro.
#   Cancellarla è una decisione dell'utente, e la prende dopo aver guardato.
# - IL SERVER DEVE ESSERE FERMO. Estrarre sopra un mondo in esecuzione lo
#   rovina, e Minecraft riscriverebbe sopra quello appena tornato.

set -u

D=@@DIR@@
A=@@ARCHIVIO@@
SESSIONE=@@SESSIONE@@

[ -d "$D" ] || { echo 'CARTELLA NON TROVATA'; exit @@EXIT_NO_ARCHIVIO@@; }
[ -f "$A" ] || { echo 'ARCHIVIO NON TROVATO'; exit @@EXIT_NO_ARCHIVIO@@; }

if command -v tmux >/dev/null 2>&1 && tmux has-session -t "$SESSIONE" 2>/dev/null; then
    echo 'SERVER ACCESO'
    exit @@EXIT_ACCESO@@
fi

# Dentro l'archivio ci deve essere il mondo, e si estrae solo quello.
PREF=$(tar -tzf "$A" 2>/dev/null | grep -m1 -E '^(\./)?serverfiles/')
case "$PREF" in
    ./serverfiles/*) MEMBRO='./serverfiles' ;;
    serverfiles/*) MEMBRO='serverfiles' ;;
    *) echo 'NIENTE MONDO NELL ARCHIVIO'; exit @@EXIT_NIENTE_MONDO@@ ;;
esac

# Serve spazio per il mondo che torna mentre quello di adesso è ancora lì: si
# chiede il triplo dell'archivio compresso, che per un mondo Minecraft è una
# stima prudente ma non assurda.
PESO=$(stat -c %s "$A" 2>/dev/null || echo 0)
LIBERI=$(df -Pk "$D" 2>/dev/null | awk 'NR==2{print $4}')
case "${LIBERI:-}" in '' | *[!0-9]*) LIBERI=0 ;; esac
if [ "$((LIBERI * 1024))" -lt "$((PESO * 3))" ]; then
    echo 'SPAZIO INSUFFICIENTE'
    exit @@EXIT_SPAZIO@@
fi

SF="$D/serverfiles"
DAPARTE=''
if [ -d "$SF" ]; then
    # La data arriva al secondo: due ripristini nello stesso secondo
    # sceglierebbero lo stesso nome, e il secondo mv finirebbe DENTRO la
    # cartella del primo. Il rollback poi non la ritroverebbe, e l'app direbbe
    # "rimesso" con il mondo sparito.
    BASE="$SF.prima-del-ripristino.$(date +%Y%m%d%H%M%S)"
    DAPARTE="$BASE"
    n=1
    while [ -e "$DAPARTE" ]; do
        DAPARTE="$BASE-$n"
        n=$((n + 1))
        [ "$n" -gt 50 ] && {
            echo 'NON RIESCO A METTERE DA PARTE IL MONDO DI ADESSO'
            exit @@EXIT_ESTRAZIONE@@
        }
    done
    mv "$SF" "$DAPARTE" || {
        echo 'NON RIESCO A METTERE DA PARTE IL MONDO DI ADESSO'
        exit @@EXIT_ESTRAZIONE@@
    }
fi

if tar -xzf "$A" -C "$D" "$MEMBRO" 2>&1; then
    echo "@@RIMESSO $DAPARTE"
    exit 0
fi

# Non è riuscito: si rimette esattamente com'era prima di cominciare.
rm -rf "$SF" 2>/dev/null
if [ -n "$DAPARTE" ]; then
    mv "$DAPARTE" "$SF" 2>/dev/null
fi
echo 'ESTRAZIONE FALLITA'
exit @@EXIT_ESTRAZIONE@@
