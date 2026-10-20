// Flitch-style image slot server (Cloudflare Worker + KV)
// Bindings needed: STORE (KV namespace), SECRET (secret text)

const BLANK_B64 =
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4//8/AAX+Av4N70a4AAAAAElFTkSuQmCC";

function blankPng() {
  const bin = atob(BLANK_B64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}

const NO_CACHE = {
  "Cache-Control": "no-store, no-cache, must-revalidate, max-age=0",
  Pragma: "no-cache",
  Expires: "0",
};

const MAX_BYTES = 2 * 1024 * 1024; // 2 MB per image
const KEEP_SECONDS = 60 * 60 * 24 * 2; // slots clean themselves after 2 days

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;

    if (path === "/") {
      return new Response("ok", { headers: NO_CACHE });
    }

    // /i/<slot>  or  /i/<slot>.png
    const m = path.match(/^\/i\/([A-Za-z0-9_-]{1,40})(?:\.(?:png|jpg|jpeg))?$/);
    if (!m) return new Response("not found", { status: 404 });
    const slot = m[1];

    // Spectator's mail app: show current image (blank until replaced)
    if (request.method === "GET" || request.method === "HEAD") {
      const { value, metadata } = await env.STORE.getWithMetadata(slot, {
        type: "arrayBuffer",
      });
      const body = value ? value : blankPng();
      const type = value && metadata && metadata.type ? metadata.type : "image/png";
      return new Response(request.method === "HEAD" ? null : body, {
        headers: { "Content-Type": type, ...NO_CACHE },
      });
    }

    // Everything below needs the secret key (only your app knows it)
    if (!env.SECRET || request.headers.get("X-Key") !== env.SECRET) {
      return new Response("forbidden", { status: 403 });
    }

    // Your phone: replace the image
    if (request.method === "PUT") {
      const type = request.headers.get("Content-Type") || "image/jpeg";
      if (!type.startsWith("image/")) {
        return new Response("images only", { status: 415 });
      }
      const data = await request.arrayBuffer();
      if (data.byteLength === 0 || data.byteLength > MAX_BYTES) {
        return new Response("bad size", { status: 413 });
      }
      await env.STORE.put(slot, data, {
        metadata: { type },
        expirationTtl: KEEP_SECONDS,
      });
      return new Response("stored", { headers: NO_CACHE });
    }

    // Your phone: reset back to blank
    if (request.method === "DELETE") {
      await env.STORE.delete(slot);
      return new Response("reset", { headers: NO_CACHE });
    }

    return new Response("method not allowed", { status: 405 });
  },
};
