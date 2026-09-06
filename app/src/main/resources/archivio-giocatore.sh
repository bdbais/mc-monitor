#!/bin/sh
# Scritto da MC Monitor: tira fuori da un backup i file di UN giocatore, e li
# rimette al loro posto senza toccare il mondo di tutti gli altri.
#
# Nasce dal caso che capita davvero: "a Tizio e' sparita la roba". Rimettere
# l'intero backup per quello vuol dire buttare via anche tutto quello che gli
# altri hanno costruito da allora. Qui tornano indietro solo i file di quella
# persona.
#
# Le regole sono le stesse del ripristino del mondo, per gli stessi motivi:
#
# - QUELLO CHE C'E' ADESSO NON SI CANCELLA, SI SPOSTA. Se il backup non era
#   quello giusto, o se il giocatore preferisce com'era prima, si torna
#   indietro. Il nome dello spostato viene stampato: e' l'unica cosa che
#   permette di disfare.
# - IL GIOCATORE DEVE ESSERE FUORI. Finche' e' collegato, Minecraft tiene il
#   suo inventario in memoria e lo riscrive uscendo: il file rimesso adesso
#   verrebbe cancellato dal suo prossimo logout. Il controllo di chi c'e' lo fa
#   l'app prima di lanciare questo script, perche' e' l'app ad avere RCON; qui
#   si controlla l'unica cosa visibile da qui, cioe' che il file non cambi
#   sotto i piedi mentre lo si sposta.
# - SI ESTRAE IN UNA CARTELLA TEMPORANEA E SI CONTROLLA PRIMA. Estrarre da un
#   tar direttamente sopra i file del mondo, se l'archivio e' troncato, lascia
#   un file a meta'.
#
# Modi:
#   leggi   - estrae e stampa il .dat in base64, piu' l'elenco di cosa c'e'
#   rimetti - estrae, sposta di lato quello di adesso e mette al suo posto
#             quello del backup, poi controlla che sia arrivato identico

set -u

A=@@ARCHIVIO@@
SF=@@SERVERFILES@@
MONDO=@@MONDO@@
UUID=@@UUID@@
MODO=@@MODO@@
# 'dat' = solo inventario, posizione, vita, esperienza.
# 'tutto' = anche statistiche e progressi.
COSA=@@COSA@@

E_NO_ARCHIVIO=@@E_NO_ARCHIVIO@@
E_NIENTE_GIOCATORE=@@E_NIENTE_GIOCATORE@@
E_ESTRAZIONE=@@E_ESTRAZIONE@@
E_STRUMENTI=@@E_STRUMENTI@@

# L'ora dell'ultima scrittura del file di adesso, presa PRIMA di aprire
# l'archivio: l'estrazione puo' durare decine di secondi, ed e' proprio in quel
# tempo che il server potrebbe scrivere sopra. Confrontarla dopo lo spostamento
# non servirebbe a niente, perche' spostare un file non ne cambia l'orario.
DAT_VIVO="$SF/$MONDO/playerdata/$UUID.dat"
ORA_PRIMA=$(stat -c %Y "$DAT_VIVO" 2>/dev/null || echo assente)

[ -f "$A" ] || { echo 'ARCHIVIO NON TROVATO'; exit "$E_NO_ARCHIVIO"; }
command -v tar >/dev/null 2>&1 || { echo 'MANCA tar'; exit "$E_STRUMENTI"; }
command -v base64 >/dev/null 2>&1 || { echo 'MANCA base64'; exit "$E_STRUMENTI"; }

TMP=$(mktemp -d 2>/dev/null || echo "${TMPDIR:-/tmp}/mcm-arch-$$")
mkdir -p "$TMP" || { echo 'CARTELLA TEMPORANEA NON CREATA'; exit "$E_ESTRAZIONE"; }
# shellcheck disable=SC2064
trap "rm -rf '$TMP'" EXIT HUP INT TERM

# ---------------------------------------------------------------- estrazione
#
# Un solo passaggio sull'archivio. Un tar.gz non si puo' leggere a salti: ogni
# giro costa la decompressione dell'intero file, e su un mondo da un giga sono
# decine di secondi. Quindi si prende tutto quello che potrebbe servire adesso,
# anche se poi se ne usa una parte sola.
#
# --wildcards e' di GNU tar. Dove non c'e' si ripiega sull'elenco: due
# passaggi invece di uno, ma funziona lo stesso.
estrai() {
    if tar -xzf "$A" -C "$TMP" --wildcards \
        "*/playerdata/$UUID.dat" \
        "*/playerdata/$UUID.dat_old" \
        "*/stats/$UUID.json" \
        "*/advancements/$UUID.json" 2>/dev/null
    then
        return 0
    fi
    # Il tar puo' aver estratto una parte e essersi lamentato per i modelli che
    # non hanno trovato niente: se qualcosa e' arrivato, va bene cosi'.
    if [ -n "$(find "$TMP" -name "$UUID.*" -print -quit 2>/dev/null)" ]; then
        return 0
    fi
    VOCI=$(tar -tzf "$A" 2>/dev/null | grep -E "/(playerdata|stats|advancements)/$UUID\.(dat|dat_old|json)$")
    [ -n "$VOCI" ] || return 1
    # shellcheck disable=SC2086
    echo "$VOCI" | tr '\n' '\0' | xargs -0 tar -xzf "$A" -C "$TMP" 2>/dev/null || return 1
    return 0
}

estrai || { echo 'NIENTE PER QUESTO GIOCATORE NELL ARCHIVIO'; exit "$E_NIENTE_GIOCATORE"; }

DAT=$(find "$TMP" -type f -name "$UUID.dat" 2>/dev/null | head -1)
[ -n "$DAT" ] || { echo 'NIENTE PER QUESTO GIOCATORE NELL ARCHIVIO'; exit "$E_NIENTE_GIOCATORE"; }

DAT_OLD=$(find "$TMP" -type f -name "$UUID.dat_old" 2>/dev/null | head -1)
STATS=$(find "$TMP" -type f -path '*/stats/*' -name "$UUID.json" 2>/dev/null | head -1)
AVANZ=$(find "$TMP" -type f -path '*/advancements/*' -name "$UUID.json" 2>/dev/null | head -1)

peso() { stat -c %s "$1" 2>/dev/null || echo 0; }
quando() { stat -c %Y "$1" 2>/dev/null || echo 0; }

echo "@@TROVATO dat=$(peso "$DAT") quando=$(quando "$DAT")"
[ -n "$DAT_OLD" ] && echo "@@TROVATO dat_old=$(peso "$DAT_OLD")"
[ -n "$STATS" ] && echo "@@TROVATO stats=$(peso "$STATS")"
[ -n "$AVANZ" ] && echo "@@TROVATO avanzamenti=$(peso "$AVANZ")"

# ------------------------------------------------------------------- leggere
if [ "$MODO" = leggi ]; then
    echo '@@INIZIO'
    base64 <"$DAT"
    echo '@@FINE'
    exit 0
fi

# ------------------------------------------------------------------ rimettere
DEST="$SF/$MONDO"
[ -d "$DEST/playerdata" ] || { echo "NON TROVO $DEST/playerdata"; exit "$E_ESTRAZIONE"; }

QUANDO=$(date +%Y%m%d%H%M%S)

# Un nome libero: due ripristini nello stesso secondo sceglierebbero lo stesso
# nome, e il secondo si infilerebbe dentro il primo. E' gia' successo.
nome_libero() {
    base="$1.mcmonitor.$QUANDO"
    n=0
    candidato="$base"
    while [ -e "$candidato" ]; do
        n=$((n + 1))
        candidato="$base-$n"
        [ "$n" -lt 100 ] || return 1
    done
    printf '%s' "$candidato"
}

# Il server ha scritto sul file mentre estraevamo? Allora il giocatore non era
# fuori come credevamo, e quello che stiamo per mettere via non e' lo stato che
# l'app ha mostrato un attimo fa. Non e' un motivo per fermarsi -- il file di
# adesso viene comunque messo da parte e si torna indietro -- ma va detto.
ORA_DOPO=$(stat -c %Y "$DAT_VIVO" 2>/dev/null || echo assente)
[ "$ORA_PRIMA" = "$ORA_DOPO" ] || echo '@@CAMBIATO il file e stato riscritto mentre leggevamo'

# Sposta di lato il file di adesso e mette al suo posto quello del backup.
rimetti() {
    sorgente="$1"
    destinazione="$2"
    etichetta="$3"

    if [ -e "$destinazione" ]; then
        daparte=$(nome_libero "$destinazione") || {
            echo "TROPPI FILE MESSI DA PARTE PER $etichetta"; return 1; }
        mv "$destinazione" "$daparte" || { echo "NON SPOSTATO: $etichetta"; return 1; }
        echo "@@DAPARTE $etichetta $daparte"
    fi

    cp "$sorgente" "$destinazione" || { echo "NON COPIATO: $etichetta"; return 1; }

    # Controllo che sia arrivato identico: una copia interrotta lascia un file
    # piu' corto, e un file di giocatore piu' corto e' un giocatore rovinato.
    if [ "$(peso "$sorgente")" != "$(peso "$destinazione")" ]; then
        echo "COPIA INCOMPLETA: $etichetta"
        return 1
    fi
    if command -v sha1sum >/dev/null 2>&1; then
        a=$(sha1sum <"$sorgente" | cut -d' ' -f1)
        b=$(sha1sum <"$destinazione" | cut -d' ' -f1)
        [ "$a" = "$b" ] || { echo "COPIA DIVERSA: $etichetta"; return 1; }
    fi
    echo "@@RIMESSO $etichetta"
    return 0
}

rimetti "$DAT" "$DEST/playerdata/$UUID.dat" dat || exit "$E_ESTRAZIONE"

# Il .dat_old e' la copia precedente che tiene Minecraft. Se si rimette solo il
# .dat e si lascia quello di oggi, alla prima lettura fallita il gioco
# ripescherebbe proprio lo stato che stiamo annullando.
if [ -n "$DAT_OLD" ]; then
    rimetti "$DAT_OLD" "$DEST/playerdata/$UUID.dat_old" dat_old || exit "$E_ESTRAZIONE"
elif [ -e "$DEST/playerdata/$UUID.dat_old" ]; then
    daparte=$(nome_libero "$DEST/playerdata/$UUID.dat_old") &&
        mv "$DEST/playerdata/$UUID.dat_old" "$daparte" &&
        echo "@@DAPARTE dat_old $daparte"
fi

if [ "$COSA" = tutto ]; then
    [ -n "$STATS" ] && [ -d "$DEST/stats" ] &&
        { rimetti "$STATS" "$DEST/stats/$UUID.json" stats || exit "$E_ESTRAZIONE"; }
    [ -n "$AVANZ" ] && [ -d "$DEST/advancements" ] &&
        { rimetti "$AVANZ" "$DEST/advancements/$UUID.json" avanzamenti || exit "$E_ESTRAZIONE"; }
fi

echo '@@FATTO'
exit 0
