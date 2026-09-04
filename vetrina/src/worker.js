/**
 * Il sito vetrina di MC Monitor.
 *
 * Le pagine sono file statici serviti da Cloudflare. Al Worker restano le due
 * cose che un file statico non sa fare: dire qual e' l'ultima versione e
 * mandare al download giusto senza che il sito vada rifatto a ogni release.
 *
 * Il numero di versione non e' scritto dentro la pagina apposta: il QR stampato
 * su un foglio vive piu' a lungo di qualsiasi versione, e deve continuare a
 * portare all'APK di oggi.
 */

const REPO = "bdbais/mc-monitor";
const API = `https://api.github.com/repos/${REPO}/releases/latest`;
const PAGINA_RELEASE = `https://github.com/${REPO}/releases/latest`;

/** Se GitHub non risponde si mostra questo, che e' vero al momento del deploy. */
const RISERVA = {
    versione: "1.34",
    file: "MC-Monitor-1.34.apk",
    url: "https://github.com/bdbais/mc-monitor/releases/download/v1.34/MC-Monitor-1.34.apk",
    byte: 5881516,
    sha256: "afee2c71d1458e8615a8b2b2940d16ae9ae3137687c74c8bfed5612e5de61900",
    data: "2026-08-24",
    riserva: true,
};

/**
 * L'ultima release, chiesta a GitHub e tenuta in cache al bordo.
 *
 * La cache non e' un'ottimizzazione: senza, ogni visita consumerebbe una delle
 * 60 chiamate all'ora che GitHub concede senza credenziali, e il sito
 * smetterebbe di sapere la versione proprio quando lo guardano in tanti.
 */
async function ultima() {
    try {
        const r = await fetch(API, {
            headers: {
                // GitHub rifiuta le richieste senza User-Agent.
                "User-Agent": "mcmonitor-vetrina",
                Accept: "application/vnd.github+json",
            },
            cf: { cacheTtl: 900, cacheEverything: true },
        });
        if (!r.ok) return RISERVA;

        const j = await r.json();
        const apk = (j.assets || []).find((a) => a.name && a.name.endsWith(".apk"));
        // L'indirizzo arriva da fuori: se non e' un https assoluto, Response.redirect
        // lancerebbe e il sito risponderebbe 500 invece di mandare al download.
        // Meglio la pagina delle release che una schermata di errore.
        if (!apk || !/^https:\/\//.test(String(apk.browser_download_url || ""))) return RISERVA;

        return {
            versione: String(j.tag_name || "").replace(/^v/, "") || RISERVA.versione,
            file: apk.name,
            url: apk.browser_download_url,
            byte: apk.size,
            // Da qualche tempo GitHub pubblica il digest dell'asset: quando c'e',
            // e' l'impronta vera e non una copiata a mano nel sito.
            sha256: (apk.digest || "").replace(/^sha256:/, "") || null,
            data: (j.published_at || "").slice(0, 10),
            riserva: false,
        };
    } catch {
        return RISERVA;
    }
}

const INTESTAZIONI = {
    "X-Content-Type-Options": "nosniff",
    "Referrer-Policy": "strict-origin-when-cross-origin",
    "X-Frame-Options": "DENY",
    "Permissions-Policy": "geolocation=(), microphone=(), camera=(), interest-cohort=()",
    // Nel sito non c'e' un solo attributo style= ne' uno script scritto dentro la
    // pagina: 'unsafe-inline' non serve, e lasciarlo per abitudine vorrebbe dire
    // spegnere proprio la parte della CSP che ferma un'iniezione.
    "Content-Security-Policy": [
        "default-src 'self'",
        "img-src 'self'",
        "style-src 'self'",
        "script-src 'self'",
        "font-src 'self'",
        "connect-src 'self'",
        "form-action 'none'",
        "frame-ancestors 'none'",
        "base-uri 'self'",
        "object-src 'none'",
    ].join("; "),
};

export default {
    async fetch(request, env) {
        const url = new URL(request.url);

        // Il bersaglio del QR e del pulsante. Una sola strada per scaricare:
        // cosi' il conteggio dei download di GitHub resta l'unico numero vero.
        if (url.pathname === "/apk" || url.pathname === "/scarica") {
            const r = await ultima();
            return new Response(null, {
                status: 302,
                headers: {
                    Location: r.riserva ? PAGINA_RELEASE : r.url,
                    // Anche una risposta senza corpo passa dalle stesse regole: una
                    // rotta scoperta e' una rotta scoperta.
                    ...INTESTAZIONI,
                },
            });
        }

        if (url.pathname === "/api/versione") {
            return new Response(JSON.stringify(await ultima()), {
                headers: {
                    "Content-Type": "application/json; charset=utf-8",
                    "Cache-Control": "public, max-age=600",
                    ...INTESTAZIONI,
                },
            });
        }

        // Il Worker gira prima degli asset (run_worker_first), altrimenti le
        // pagine uscirebbero dallo strato statico senza passare di qui — e senza
        // le intestazioni di sicurezza, che e' l'unico motivo per cui ci passano.
        if (!env || !env.ASSETS) {
            return new Response("Sito non configurato.", { status: 500, headers: INTESTAZIONI });
        }

        const risposta = await env.ASSETS.fetch(request);
        const con = new Response(risposta.body, risposta);
        for (const [k, v] of Object.entries(INTESTAZIONI)) con.headers.set(k, v);
        return con;
    },
};
