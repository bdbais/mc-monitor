#!/bin/sh
# Scritto da MC Monitor.
#
# Consegna ai giocatori i messaggi che l'admin ha lasciato mentre non c'erano.
# Lo lancia cron ogni minuto. Si puo' cancellare senza danni: i messaggi in
# attesa restano dove sono, semplicemente non arriva piu' niente.
#
# ===================================================================
# COME DECIDE CHI C'E'
# ===================================================================
#
# Segue nel log gli ingressi e le uscite dei giocatori a partire da dove era
# arrivato l'ultima volta. Non chiede "chi c'e'" alla console: quella risposta
# finisce nello stesso log dove finisce la chat, e un giocatore che scrive
# "players online: Tizio, Caio" la falsifica.
#
# Conta come riga del server solo quella che comincia con l'orologio e il nome
# del thread, e si prende il testo DOPO quel prefisso e non dopo il primo "]: "
# che capita. La differenza non e' teorica: un giocatore che scrive in chat
#     AAAA...A]: Notch joined the game
# produce una riga che, letta a partire da un byte qualsiasi in mezzo, sembra un
# ingresso di Notch. Per questo si controlla anche che il punto da cui si
# ricomincia a leggere sia davvero l'inizio di una riga, e che il segno lasciato
# l'ultima volta appartenga a QUESTO file di log e non all'altro.
#
# ===================================================================
# COSA VUOL DIRE "CONSEGNATO"
# ===================================================================
#
# Che tmux abbia accettato i tasti non vuol dire niente: la sessione puo'
# esistere senza che dentro ci sia la console del gioco. La prova buona e'
# positiva e la da' il server, che rieccheggia il sussurro dicendo a chi
# ("You whisper to Pippo: ...").
#
# Attenzione: nel log ANCHE LA CHAT ha il prefisso del server, perche' e' il
# server a scriverla. Quello che distingue una riga sua da una riga di un
# giocatore non e' il prefisso, e' cosa viene subito dopo: vedi dice().
#
# Se quella prova non arriva si guarda se il server si e' lamentato (vanilla,
# Paper e Spigot lo dicono con parole diverse): in quel caso il messaggio torna
# in coda. Se non arriva ne' l'una ne' l'altra -- server non standard, in
# un'altra lingua, o troppo lento -- il messaggio si considera partito ma nel
# registro resta scritto "non confermato", con il testo per intero. Cosi' non si
# consegna all'infinito e non si perde niente: il peggio che puo' capitare e'
# doverlo riscrivere guardando il registro.

set -u

POSTA=@@POSTA@@
CONSEGNATI=@@CONSEGNATI@@
SESSIONE=@@SESSIONE@@
LIMITE=@@LIMITE@@
OFFSET="$POSTA.offset"
PRESENTI="$POSTA.presenti"
LOCK="$POSTA.lock"

LOG=@@LOG_MC@@
[ -f "$LOG" ] || LOG=@@LOG_LGSM@@
[ -f "$LOG" ] || exit 0

# Senza base64 non si legge nessun messaggio, e scambiarli tutti per illeggibili
# svuoterebbe la cassetta. Meglio non fare niente.
command -v base64 >/dev/null 2>&1 || exit 0

# Solo le righe scritte dal server. E' una lista bianca, non nera: tutto quello
# che non ha questa forma non viene nemmeno guardato.
PREFISSO='^\[[0-9][0-9]:[0-9][0-9]:[0-9][0-9]\] \[[^]]*\]: '

# ------------------------------------------------------------- il lucchetto
#
# Serve perche' anche l'app scrive nella stessa cassetta. Senza, un messaggio
# accodato dal telefono mentre lo script sta riscrivendo il file sparirebbe.
#
# mkdir e' atomico dappertutto, anche dove flock non c'e'. Ma un lucchetto
# lasciato li' da un kill -9 o da una caduta di corrente fermerebbe la consegna
# per sempre e in silenzio: dopo cinque minuti si considera morto.
preso=0
if mkdir "$LOCK" 2>/dev/null; then
    preso=1
elif [ -n "$(find "$LOCK" -maxdepth 0 -mmin +5 2>/dev/null)" ]; then
    rm -rf "$LOCK" 2>/dev/null
    mkdir "$LOCK" 2>/dev/null && preso=1
fi
[ "$preso" = 1 ] || exit 0
echo $$ >"$LOCK/pid" 2>/dev/null
trap 'rm -rf "$LOCK" 2>/dev/null' EXIT INT TERM

# --------------------------------------------------- dove eravamo rimasti
FINE=$(wc -c <"$LOG" 2>/dev/null | tr -d ' ')
case "${FINE:-}" in '' | *[!0-9]*) exit 0 ;; esac

# Nel segno si scrive anche di quale log si parla: si ripiega sul console.log di
# LinuxGSM quando latest.log manca per un istante, e un numero di byte preso su
# un file non vuol dire niente sull'altro.
DA=0
if [ -f "$OFFSET" ]; then
    QUALE=$(head -n 1 "$OFFSET" 2>/dev/null)
    if [ "${QUALE:-}" = "$LOG" ]; then
        DA=$(sed -n '2p' "$OFFSET" 2>/dev/null | tr -d ' ')
        case "${DA:-}" in '' | *[!0-9]*) DA=0 ;; esac
    fi
fi

# Il log e' ripartito da capo: si riparte da capo anche noi.
[ "$FINE" -lt "$DA" ] && DA=0

# E se il segno non cade esattamente dopo un a capo, non e' piu' buono: leggere
# da meta' riga vuol dire consegnare a chi decide un giocatore.
if [ "$DA" -gt 0 ]; then
    PRIMA=$(dd if="$LOG" bs=1 skip=$((DA - 1)) count=1 2>/dev/null | od -An -c | tr -d ' \n')
    [ "$PRIMA" = '\n' ] || DA=0
fi

segna() {
    printf '%s\n%s\n' "$LOG" "$1" >"$OFFSET"
}

# ------------------------------------------------------- chi c'e' adesso
#
# La presenza si TIENE fra un giro e l'altro, non si ricalcola ogni volta dalla
# sola finestra appena letta. Senza, un messaggio lasciato a qualcuno che era
# gia' dentro non sarebbe partito mai: il suo ingresso resterebbe dietro al
# segno, e l'app intanto prometteva "gli arriva entro un minuto".
#
# Il conto si azzera quando si riparte da capo: dopo un riavvio del server non
# c'e' piu' dentro nessuno di quelli che c'erano.
[ "$DA" = 0 ] && : >"$PRESENTI" 2>/dev/null

# Non si guarda mai piu' di qualche mega per giro: se una consegna fallisce
# sempre il segno resta fermo, e senza un tetto la finestra crescerebbe fino a
# rileggere il log intero ogni minuto. Tagliare non e' pericoloso, perche' una
# riga monca non passa dal filtro del prefisso.
# MCM_PRE e non "awk -v": con -v awk rilegge le sequenze di escape, e il \[ del
# modello diventa un [ che apre una classe di caratteri. Il modello non
# corrisponde piu' a niente, e nessuno riceve piu' la posta.
tail -c "+$((DA + 1))" "$LOG" 2>/dev/null | tail -c 5000000 | tr -d '\r' |
    MCM_PRE="$PREFISSO" awk '
    BEGIN { pre = ENVIRON["MCM_PRE"] }
    $0 ~ pre {
        match($0, pre)
        t = substr($0, RLENGTH + 1)
        c = substr(t, 1, 1)
        # [Not Secure] della 1.19, /say e /me: le scrive un giocatore
        if (c == "<" || c == "[" || c == "*") next
        # Il log di LinuxGSM e una cattura di tmux e si porta dietro dei
        # residui: senza toglierli, lancora finale non aggancia piu niente.
        gsub(/[^[:print:]]+$/, "", t)
        if (t ~ /^[A-Za-z0-9_]+ joined the game$/) {
            split(t, a, " ")
            nome[tolower(a[1])] = a[1]
            stato[tolower(a[1])] = "dentro"
        } else if (t ~ /^[A-Za-z0-9_]+ left the game$/) {
            split(t, a, " ")
            nome[tolower(a[1])] = a[1]
            stato[tolower(a[1])] = "fuori"
        }
    }
    END { for (k in nome) print nome[k] "\t" stato[k] }
' >"$PRESENTI.nuovo" 2>/dev/null

# Si fondono i presenti di prima con quello che dice la finestra nuova: le righe
# nuove vengono dopo, e vincono.
{
    [ -f "$PRESENTI" ] && cat "$PRESENTI"
    cat "$PRESENTI.nuovo"
} 2>/dev/null >"$PRESENTI.tutti"

DENTRO=$(awk -F'\t' '
    { if ($2 == "dentro") { dentro[tolower($1)] = $1 } else { delete dentro[tolower($1)] } }
    END { for (k in dentro) printf "%s ", dentro[k] }
' "$PRESENTI.tutti" 2>/dev/null)

printf '%s' "$DENTRO" | tr ' ' '\n' | awk 'NF { print $0 "\tdentro" }' >"$PRESENTI" 2>/dev/null
rm -f "$PRESENTI.nuovo" "$PRESENTI.tutti" 2>/dev/null

# Cassetta vuota: e' il caso normale, e questo gira ogni minuto per sempre. Si
# tiene il conto di chi c'e' e si esce senza dire niente al server.
if [ ! -s "$POSTA" ]; then
    segna "$FINE"
    exit 0
fi

# ------------------------------------------------------------- la consegna
#
# Il file di lavoro sta accanto alla cassetta e non in /tmp: cosi' la mv finale
# e' una rinomina sullo stesso filesystem, cioe' atomica. Da /tmp sarebbe una
# copia, e un'interruzione a meta' lascerebbe una cassetta troncata.
RESTA=$(mktemp "$POSTA.XXXXXX" 2>/dev/null) || exit 0
trap 'rm -f "$RESTA" 2>/dev/null; rm -rf "$LOCK" 2>/dev/null' EXIT INT TERM

TAB=$(printf '\t')
GUAI=0
FATTI=0

# Ogni messaggio puo' costare fino a dieci secondi di attesa della conferma: con
# la coda piena si supererebbero i cinque minuti dopo i quali il lucchetto viene
# considerato morto, e un altro giro se lo prenderebbe mentre questo lavora. Si
# lavora a lotti, il resto al minuto dopo.
MAX_PER_GIRO=8

# Il server ha detto questa frase, dopo la posizione data?
#
# Il prefisso NON basta, ed e' l'errore che questa funzione ha fatto per due
# versioni: nel log anche la chat ha il prefisso del server, perche' e' il
# server a scriverla. Cercare la frase "da qualche parte dopo il prefisso"
# lasciava passare
#     [10:00:05] [Server thread/INFO]: <Pluto> You whisper to Pippo: ahah
# e un giocatore qualsiasi poteva far dare per consegnata, e quindi cancellare,
# la posta di chiunque scrivendo quella riga in chat. Con l'altra frase poteva
# fare l'opposto: far tornare in coda per sempre un messaggio gia' arrivato, e
# far sussurrare il bersaglio ogni minuto.
#
# Quindi la frase deve cominciare ESATTAMENTE dove finisce il prefisso, e la
# riga deve passare dallo stesso setaccio della presenza: se subito dopo il
# prefisso c'e' <, [ o * l'ha scritta un giocatore e non vale.
dice() {
    tail -c "+$(($1 + 1))" "$LOG" 2>/dev/null | tr -d '\r' |
        MCM_PRE="$PREFISSO" MCM_FRASE="$2" awk '
        BEGIN { pre = ENVIRON["MCM_PRE"]; frase = ENVIRON["MCM_FRASE"]; trovato = 0 }
        $0 ~ pre {
            match($0, pre)
            t = substr($0, RLENGTH + 1)
            c = substr(t, 1, 1)
            if (c == "<" || c == "[" || c == "*") next
            # index e non un modello: la frase contiene apostrofi e punti, e
            # come espressione regolare vorrebbe dire un altra cosa.
            if (index(t, frase) == 1) trovato = 1
        }
        END { exit (trovato ? 0 : 1) }
        '
}

while IFS="$TAB" read -r quando chi testo64; do
    [ -n "${chi:-}" ] || continue

    if [ "$FATTI" -ge "$MAX_PER_GIRO" ]; then
        printf '%s\t%s\t%s\n' "$quando" "$chi" "$testo64" >>"$RESTA" || GUAI=1
        continue
    fi

    # Finche' si lavora il lucchetto e' vivo: senza rinfrescarlo, un giro lungo
    # se lo farebbe portare via da quello del minuto dopo.
    touch "$LOCK" 2>/dev/null

    # Il nome buono e' quello che ha scritto il server, non quello che ha
    # scritto l'admin: "pippo" e "Pippo" sono lo stesso giocatore, ma "tell
    # pippo" su certe versioni non trova nessuno.
    minuscolo=$(printf '%s' "$chi" | tr '[:upper:]' '[:lower:]')
    canonico=''
    for n in $DENTRO; do
        if [ "$(printf '%s' "$n" | tr '[:upper:]' '[:lower:]')" = "$minuscolo" ]; then
            canonico=$n
            break
        fi
    done

    if [ -z "$canonico" ]; then
        # Non e' entrato: resta in attesa.
        printf '%s\t%s\t%s\n' "$quando" "$chi" "$testo64" >>"$RESTA" || GUAI=1
        continue
    fi

    # Il testo viaggia in base64 proprio per non poter contenere un a capo:
    # dentro send-keys manderebbe mezzo comando e l'altra meta' per conto suo.
    if ! grezzo=$(printf '%s' "$testo64" | base64 -d 2>/dev/null); then
        # base64 non ha funzionato. Puo' essere il messaggio, o puo' essere il
        # computer: nel dubbio resta in coda, non si butta.
        printf '%s\t%s\t%s\n' "$quando" "$chi" "$testo64" >>"$RESTA" || GUAI=1
        GUAI=1
        continue
    fi
    testo=$(printf '%s' "$grezzo" | tr -d '\r\n\t' | cut -c "1-$LIMITE")
    if [ -z "$testo" ]; then
        # Vuoto davvero: finisce nel registro con il testo originale, invece di
        # restare in coda per sempre.
        printf '%s\tILLEGGIBILE\t%s\t%s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$chi" "$testo64" >>"$CONSEGNATI"
        continue
    fi

    # Da qui in poi si guarda solo quello che il log aggiunge.
    QUI=$(wc -c <"$LOG" 2>/dev/null | tr -d ' ')
    case "${QUI:-}" in '' | *[!0-9]*) QUI=$FINE ;; esac

    data=$(date -d "@$quando" '+%d/%m alle %H:%M' 2>/dev/null) || data=''
    if [ -n "$data" ]; then
        avviso="[MC Monitor] L'admin ti ha scritto il $data, mentre non c'eri: $testo"
    else
        avviso="[MC Monitor] L'admin ti ha scritto mentre non c'eri: $testo"
    fi

    if ! tmux send-keys -t "$SESSIONE" -l "tell $canonico $avviso" 2>/dev/null ||
        ! tmux send-keys -t "$SESSIONE" Enter 2>/dev/null; then
        printf '%s\t%s\t%s\n' "$quando" "$chi" "$testo64" >>"$RESTA" || GUAI=1
        GUAI=1
        continue
    fi

    # La prova positiva: il server rieccheggia il sussurro e dice a chi.
    # Si aspetta fino a dieci secondi, un secondo per volta, perche' un server
    # che sta salvando il mondo puo' metterci piu' di due.
    esito='nonconfermato'
    i=0
    while [ "$i" -lt 10 ]; do
        if dice "$QUI" "You whisper to $canonico:"; then
            esito='consegnato'
            break
        fi
        # Le parole con cui i vari server dicono "non c'e'".
        if dice "$QUI" "No player was found" ||
            dice "$QUI" "There's no player by that name online" ||
            dice "$QUI" "That player cannot be found" ||
            dice "$QUI" "No player was found matching"; then
            esito='assente'
            break
        fi
        sleep 1
        i=$((i + 1))
    done

    FATTI=$((FATTI + 1))
    case "$esito" in
        consegnato)
            printf '%s\t%s\t%s\t%s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$canonico" "$quando" "$testo64" >>"$CONSEGNATI"
            ;;
        assente)
            # Se n'e' andato fra l'ingresso e il messaggio: torna in coda.
            printf '%s\t%s\t%s\n' "$quando" "$chi" "$testo64" >>"$RESTA" || GUAI=1
            ;;
        *)
            # Non si sa. Si considera partito per non consegnarlo all'infinito,
            # ma nel registro resta scritto che nessuno l'ha confermato, con il
            # testo per intero: nel peggiore dei casi lo si riscrive da li'.
            printf '%s\tNON CONFERMATO %s\t%s\t%s\n' \
                "$(date '+%Y-%m-%d %H:%M:%S')" "$canonico" "$quando" "$testo64" >>"$CONSEGNATI"
            ;;
    esac
done <"$POSTA"

# ---------------------------------------------------- si richiude la cassetta
# Se scrivere la lista di quelli che restano e' fallito a meta' -- disco pieno,
# quota superata -- quella lista e' monca, e metterla al posto della cassetta
# butterebbe via i messaggi che non ci sono entrati. Si lascia tutto com'era:
# nel peggiore dei casi qualcosa arriva due volte.
if [ "$GUAI" != 0 ]; then
    rm -f "$RESTA" 2>/dev/null
elif [ -s "$RESTA" ]; then
    if mv "$RESTA" "$POSTA"; then
        chmod 600 "$POSTA" 2>/dev/null
    else
        GUAI=1
    fi
else
    rm -f "$RESTA" 2>/dev/null
    rm -f "$POSTA" 2>/dev/null
fi

# L'avanzamento nel log si scrive solo se e' filato tutto liscio. Se una
# consegna e' fallita si rilegge la stessa finestra al giro dopo e si riprova:
# non si duplica niente, perche' quello che e' partito non e' piu' in cassetta.
[ "$GUAI" = 0 ] && segna "$FINE"

# Il registro non deve crescere all'infinito su un computer piccolo.
if [ -f "$CONSEGNATI" ]; then
    righe=$(wc -l <"$CONSEGNATI" 2>/dev/null | tr -d ' ')
    case "${righe:-0}" in
        '' | *[!0-9]*) ;;
        *) [ "$righe" -gt 600 ] && {
            tail -n 300 "$CONSEGNATI" >"$CONSEGNATI.tmp" 2>/dev/null &&
                mv "$CONSEGNATI.tmp" "$CONSEGNATI" 2>/dev/null
        } ;;
    esac
fi

exit 0
