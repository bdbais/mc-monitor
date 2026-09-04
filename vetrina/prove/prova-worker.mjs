/*
 * Prova del Worker senza Cloudflare: si sostituiscono le due cose che vengono da
 * fuori — la chiamata a GitHub e lo strato degli asset — e si guarda cosa esce.
 */
import worker from "../src/worker.js";

let esiti = 0, falliti = 0;
const dice = (nome, condizione, extra = "") => {
    esiti++;
    if (!condizione) { falliti++; console.log(`  NO   ${nome} ${extra}`); }
    else console.log(`  ok   ${nome}`);
};

const ASSET = {
    async fetch(req) {
        const p = new URL(req.url).pathname;
        if (p === "/" || p === "/index.html")
            return new Response("<!doctype html>ciao", {
                headers: { "Content-Type": "text/html", "Cache-Control": "public, max-age=0" },
            });
        if (p === "/img/qr.svg")
            return new Response("<svg/>", { headers: { "Content-Type": "image/svg+xml" } });
        return new Response("non trovato", { status: 404 });
    },
};

const RELEASE = {
    tag_name: "v1.34",
    published_at: "2026-08-24T16:22:35Z",
    assets: [
        { name: "MC-Monitor-1.34.apk", size: 5881516,
          digest: "sha256:afee2c71d1458e8615a8b2b2940d16ae9ae3137687c74c8bfed5612e5de61900",
          browser_download_url: "https://github.com/bdbais/mc-monitor/releases/download/v1.34/MC-Monitor-1.34.apk" },
    ],
};

const conGitHub = (modo) => {
    globalThis.fetch = async (url, init) => {
        if (String(url).includes("api.github.com")) {
            if (modo === "giu")   throw new Error("rete assente");
            if (modo === "500")   return new Response("boom", { status: 500 });
            if (modo === "senza") return Response.json({ ...RELEASE, assets: [] });
            if (modo === "vuoto") return new Response("non e json", { status: 200 });
            if (modo === "storto")
                return Response.json({ ...RELEASE, assets: [{ ...RELEASE.assets[0], browser_download_url: "/scarica-mi" }] });
            if (modo === "http")
                return Response.json({ ...RELEASE, assets: [{ ...RELEASE.assets[0], browser_download_url: "http://esempio.invalid/x.apk" }] });
            if (!init?.headers?.["User-Agent"]) throw new Error("manca lo User-Agent");
            return Response.json(RELEASE);
        }
        throw new Error("chiamata inattesa: " + url);
    };
};

// Un'eccezione qui dentro e' un difetto come un altro: va contata, non deve far
// morire il banco di prova lasciando credere che le verifiche fossero tutte a posto.
const chiama = async (percorso, env = { ASSETS: ASSET }) => {
    try {
        return await worker.fetch(new Request("https://mcmonitor.bais.info" + percorso), env);
    } catch (e) {
        return new Response("il Worker ha lanciato: " + e.message, { status: 599 });
    }
};

const CSP = "Content-Security-Policy";

console.log("\n— GitHub risponde —");
conGitHub("ok");
{
    const r = await chiama("/apk");
    dice("/apk reindirizza all'APK", r.status === 302 &&
        r.headers.get("location") === RELEASE.assets[0].browser_download_url,
        `${r.status} ${r.headers.get("location")}`);

    const v = await (await chiama("/api/versione")).json();
    dice("versione senza la v", v.versione === "1.34", v.versione);
    dice("impronta senza il prefisso sha256:", v.sha256 === RELEASE.assets[0].digest.slice(7), v.sha256);
    dice("peso in byte", v.byte === 5881516, String(v.byte));
    dice("data corta", v.data === "2026-08-24", v.data);
    dice("non e la riserva", v.riserva === false);

    const h = await chiama("/");
    dice("la pagina esce con la CSP", (h.headers.get(CSP) || "").includes("default-src 'self'"));
    dice("CSP senza unsafe-inline", !(h.headers.get(CSP) || "").includes("unsafe-inline"),
        h.headers.get(CSP));
    dice("nosniff sulla pagina", h.headers.get("X-Content-Type-Options") === "nosniff");
    dice("il Content-Type dell'asset sopravvive", h.headers.get("Content-Type") === "text/html");
    dice("il corpo dell'asset sopravvive", (await h.text()) === "<!doctype html>ciao");

    const q = await chiama("/img/qr.svg");
    dice("anche un'immagine esce con la CSP", !!q.headers.get(CSP));

    const nf = await chiama("/pagina/che/non/esiste");
    dice("404 resta 404", nf.status === 404, String(nf.status));
}

console.log("\n— GitHub non risponde —");
for (const modo of ["giu", "500", "senza", "vuoto", "storto", "http"]) {
    conGitHub(modo);
    const r = await chiama("/apk");
    dice(`/apk con GitHub «${modo}» manda alla pagina delle release`,
        r.status === 302 && r.headers.get("location") === "https://github.com/bdbais/mc-monitor/releases/latest",
        `${r.status} ${r.headers.get("location")}`);
    const v = await (await chiama("/api/versione")).json();
    dice(`/api/versione con GitHub «${modo}» ripiega e lo dichiara`,
        v.riserva === true && v.versione === "1.34", JSON.stringify(v).slice(0, 90));
}

console.log("\n— casi storti —");
conGitHub("ok");
{
    const r = await chiama("/", {});                       // binding mancante
    dice("senza ASSETS non esplode, risponde 500", r.status === 500, String(r.status));
    dice("anche il 500 ha le intestazioni", !!r.headers.get(CSP));

    const s = await chiama("/scarica");
    dice("/scarica e' un sinonimo di /apk", s.status === 302 &&
        s.headers.get("location") === RELEASE.assets[0].browser_download_url);

    const a = await chiama("/apk");
    dice("anche il reindirizzamento porta le intestazioni", !!a.headers.get(CSP));
    dice("il reindirizzamento non ha corpo", (await a.text()) === "");
}

console.log(`\n${esiti - falliti}/${esiti} verifiche passate`);
process.exit(falliti ? 1 : 0);
