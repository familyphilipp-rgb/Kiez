// Service Worker: App-Hülle offline, Fotos und Vorschaubilder im Zwischenspeicher, API immer frisch
const SHELL_CACHE = 'kiez-shell-v4';
const MEDIA_CACHE = 'kiez-media-v1';
const MEDIA_LIMIT = 150;
const SHELL = ['/', '/style.css', '/app.js', '/icon.svg', '/manifest.webmanifest', '/fonts/SpaceGrotesk.woff2', '/fonts/Syne.woff2'];

self.addEventListener('install', (e) => e.waitUntil(caches.open(SHELL_CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting())));
self.addEventListener('activate', (e) => e.waitUntil(
  caches.keys().then((keys) => Promise.all(keys.filter((k) => ![SHELL_CACHE, MEDIA_CACHE].includes(k)).map((k) => caches.delete(k))))
    .then(() => self.clients.claim())));

async function trimMedia() {
  const cache = await caches.open(MEDIA_CACHE);
  const keys = await cache.keys();
  for (const k of keys.slice(0, Math.max(0, keys.length - MEDIA_LIMIT))) await cache.delete(k);
}

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== location.origin || url.pathname.startsWith('/api/')) return;
  // Fotos und Vorschaubilder ändern sich nie: zuerst aus dem Zwischenspeicher
  if (url.pathname.startsWith('/media/') && /\.(jpg|png|webp|gif)$/.test(url.pathname)) {
    e.respondWith(caches.open(MEDIA_CACHE).then(async (cache) => {
      const hit = await cache.match(e.request);
      if (hit) return hit;
      const res = await fetch(e.request);
      if (res.ok) { cache.put(e.request, res.clone()); trimMedia(); }
      return res;
    }));
    return;
  }
  if (url.pathname.startsWith('/media/')) return; // Videos: Browser-Cache mit Range-Anfragen
  e.respondWith(fetch(e.request).then((r) => {
    const copy = r.clone();
    caches.open(SHELL_CACHE).then((c) => c.put(e.request, copy));
    return r;
  }).catch(() => caches.match(e.request).then((r) => r || caches.match('/'))));
});
