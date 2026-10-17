// Kiez – Frontend ohne Framework
const $ = (s) => document.querySelector(s);
const view = $('#view');
const state = { token: null, coords: null, sort: 'new', openPost: null };
// Ausweich-Standorte, falls die Standortfreigabe fehlt (z. B. zum Testen am Desktop)
const CITIES = { Berlin: [52.52, 13.405], Hamburg: [53.551, 9.993], München: [48.137, 11.575], Köln: [50.937, 6.96],
  Leipzig: [51.34, 12.375], Wien: [48.208, 16.373], Zürich: [47.377, 8.541] };

const store = {
  get(k) { try { return localStorage.getItem(k); } catch { return null; } },
  set(k, v) { try { localStorage.setItem(k, v); } catch {} },
};

function toast(msg) {
  const t = $('#toast');
  t.textContent = msg; t.hidden = false;
  clearTimeout(toast.timer); toast.timer = setTimeout(() => (t.hidden = true), 2500);
}

async function api(path, opts = {}) {
  const res = await fetch(path, {
    ...opts,
    headers: { 'Content-Type': 'application/json', ...(state.token ? { Authorization: `Bearer ${state.token}` } : {}) },
    body: opts.body ? JSON.stringify(opts.body) : undefined,
  });
  if (res.status === 401 && path !== '/api/register') { await register(); return api(path, opts); }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || 'Fehler');
  return data;
}

async function register() {
  const { token } = await api('/api/register', { method: 'POST' });
  state.token = token; store.set('kiez-token', token);
}

// --- Fotos und Videos ---
const MAX_VIDEO_MB = 100;

// Fotos im Browser verkleinern; das Neu-Zeichnen entfernt auch EXIF-Daten wie den GPS-Ort
async function shrinkImage(file) {
  if (file.type === 'image/gif') return file;
  try {
    const bmp = await createImageBitmap(file, { imageOrientation: 'from-image' });
    const scale = Math.min(1, 1600 / Math.max(bmp.width, bmp.height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(bmp.width * scale); canvas.height = Math.round(bmp.height * scale);
    canvas.getContext('2d').drawImage(bmp, 0, 0, canvas.width, canvas.height);
    return await new Promise((r) => canvas.toBlob((b) => r(b || file), 'image/jpeg', 0.85));
  } catch { return file; }
}

function uploadMedia(file, onProgress) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', '/api/media');
    xhr.setRequestHeader('Authorization', `Bearer ${state.token}`);
    xhr.setRequestHeader('Content-Type', file.type);
    xhr.upload.onprogress = (e) => e.lengthComputable && onProgress(e.loaded / e.total);
    xhr.upload.onload = () => onProgress(1);
    xhr.onload = () => {
      let data = {}; try { data = JSON.parse(xhr.responseText); } catch {}
      xhr.status < 300 ? resolve(data) : reject(new Error(data.error || 'Upload fehlgeschlagen'));
    };
    xhr.onerror = () => reject(new Error('Keine Verbindung zum Server'));
    xhr.send(file);
  });
}

// Auswahl-Knöpfe, Vorschau und Fortschritt; wird im Composer und bei Antworten benutzt
function mediaPicker({ compact = false } = {}) {
  let file = null;
  const preview = el('div', { class: 'media-preview', hidden: true });
  const bar = el('div', { class: 'progress', hidden: true }, el('span'));
  const pick = async (accept) => {
    // In der Android-App bietet die Dateiauswahl keine Kamera an, daher selbst fragen
    let capture = null;
    if (window.Capacitor?.isNativePlatform?.()) {
      const choice = await chooseSource(accept.startsWith('video') ? 'Video' : 'Foto');
      if (!choice) return;
      if (choice === 'camera') capture = 'environment';
    }
    const input = el('input', { type: 'file', accept, capture });
    input.onchange = async () => {
      const f = input.files[0]; if (!f) return;
      if (f.type.startsWith('video/') && f.size > MAX_VIDEO_MB * 1024 * 1024) return toast(`Video ist zu groß (max. ${MAX_VIDEO_MB} MB)`);
      file = f.type.startsWith('image/') ? await shrinkImage(f) : f;
      const url = URL.createObjectURL(file);
      const remove = el('button', { type: 'button', class: 'remove', 'aria-label': 'Entfernen', onclick: reset }, '×');
      preview.replaceChildren(file.type.startsWith('video/')
        ? el('video', { src: url, muted: true, playsinline: true, controls: true, preload: 'metadata' })
        : el('img', { src: url, alt: 'Vorschau' }), remove);
      preview.hidden = false;
    };
    input.click();
  };
  function reset() { file = null; preview.hidden = true; preview.replaceChildren(); bar.hidden = true; }
  const buttons = el('div', { class: 'media-buttons' + (compact ? ' compact' : '') },
    el('button', { type: 'button', class: 'chip', onclick: () => pick('image/*') }, compact ? '📷' : '📷 Foto'),
    el('button', { type: 'button', class: 'chip', onclick: () => pick('video/*') }, compact ? '🎬' : '🎬 Video'));
  return {
    buttons, preview, bar, reset,
    get file() { return file; },
    // Lädt die Datei hoch (falls vorhanden) und liefert die Medien-ID
    async upload() {
      if (!file) return null;
      bar.hidden = false;
      const fill = bar.firstChild;
      const res = await uploadMedia(file, (f) => {
        fill.style.width = `${Math.round(f * 100)}%`;
        if (f >= 1 && file.type.startsWith('video/')) bar.dataset.label = 'Video wird verarbeitet…';
      });
      delete bar.dataset.label;
      return res.id;
    },
  };
}

function chooseSource(what) {
  return new Promise((resolve) => {
    const d = el('dialog', { class: 'sheet' });
    const done = (v) => { d.close(); d.remove(); resolve(v); };
    d.append(el('span', { class: 'eyebrow' }, `${what} hinzufügen`),
      el('button', { type: 'button', class: 'chip', onclick: () => done('camera') }, what === 'Video' ? '🎬 Video aufnehmen' : '📷 Foto aufnehmen'),
      el('button', { type: 'button', class: 'chip', onclick: () => done('gallery') }, '🖼️ Aus der Galerie'),
      el('button', { type: 'button', class: 'ghost', onclick: () => done(null) }, 'Abbrechen'));
    d.addEventListener('cancel', () => done(null));
    document.body.append(d); d.showModal();
  });
}

function mediaView(m) {
  if (!m) return null;
  const ratio = m.width && m.height ? `aspect-ratio:${m.width}/${m.height}` : '';
  if (m.kind === 'video') {
    return el('video', { class: 'media', src: m.url, poster: m.poster, controls: true, playsinline: true, preload: 'none', style: ratio,
      onclick: (e) => e.stopPropagation() });
  }
  return el('img', { class: 'media', src: m.url, alt: 'Foto', loading: 'lazy', style: ratio, onclick: (e) => { e.stopPropagation(); showImage(m.url); } });
}

function showImage(url) {
  const d = el('dialog', { class: 'lightbox', onclick: () => { d.close(); d.remove(); } }, el('img', { src: url, alt: 'Foto' }));
  document.body.append(d); d.showModal();
}

const qs = () => `lat=${state.coords.lat}&lng=${state.coords.lng}`;

function timeAgo(ts) {
  const s = Math.floor((Date.now() - ts) / 1000);
  if (s < 60) return 'gerade eben';
  if (s < 3600) return `vor ${Math.floor(s / 60)} Min.`;
  if (s < 86400) return `vor ${Math.floor(s / 3600)} Std.`;
  return `vor ${Math.floor(s / 86400)} T.`;
}

function el(tag, attrs = {}, ...children) {
  const n = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k.startsWith('on')) n.addEventListener(k.slice(2), v);
    else if (k === 'style') n.style.cssText = v;
    else if (v !== false && v != null) n.setAttribute(k, v);
  }
  for (const c of children.flat()) if (c != null) n.append(c);
  return n;
}

function postCard(p, { isReply = false, onOpen } = {}) {
  const votes = el('div', { class: 'votes' });
  const up = el('button', { class: p.myVote === 1 ? 'on' : '', disabled: p.mine, 'aria-label': 'Upvote' }, '▲');
  const score = el('div', { class: 'score' }, String(p.score));
  const down = el('button', { class: p.myVote === -1 ? 'on' : '', disabled: p.mine, 'aria-label': 'Downvote' }, '▼');
  const vote = async (v) => {
    const value = p.myVote === v ? 0 : v;
    try {
      const r = await api(`/api/posts/${p.id}/vote`, { method: 'POST', body: { value } });
      p.myVote = r.myVote; p.score = r.score;
      score.textContent = r.score;
      up.className = p.myVote === 1 ? 'on' : ''; down.className = p.myVote === -1 ? 'on' : '';
    } catch (e) { toast(e.message); }
  };
  up.onclick = () => vote(1); down.onclick = () => vote(-1);
  votes.append(up, score, down);

  let author = null;
  if (isReply) author = el('span', { class: 'badge' }, p.isOP ? 'OJ' : `#${p.authorNo}`);
  else if (p.mine) author = el('span', { class: 'badge' }, 'Du');

  const actions = [];
  if (p.mine) actions.push(el('button', { onclick: async (e) => {
    e.stopPropagation();
    if (!window.confirm('Beitrag löschen?')) return;
    try { await api(`/api/posts/${p.id}`, { method: 'DELETE' }); toast('Gelöscht'); isReply ? openPost(state.openPost) : showFeed(); }
    catch (err) { toast(err.message); }
  } }, 'Löschen'));
  else actions.push(el('button', { onclick: async (e) => {
    e.stopPropagation();
    try { await api(`/api/posts/${p.id}/report`, { method: 'POST' }); toast('Danke, wurde gemeldet'); }
    catch (err) { toast(err.message); }
  } }, 'Melden'));

  const body = el('div', { class: 'body', onclick: onOpen || null },
    el('div', { class: 'meta' }, author, el('span', {}, timeAgo(p.createdAt)), p.distance && !isReply ? el('span', {}, '📍 ' + p.distance) : null),
    p.text ? el('div', { class: 'text' }, p.text) : null,
    mediaView(p.media),
    el('div', { class: 'footer' }, isReply ? null : el('span', {}, `💬 ${p.commentCount}`), ...actions));

  return el('article', { class: 'post' + (isReply ? ' reply' : ''), style: `--accent:${p.color}` }, body, votes);
}

async function showFeed() {
  state.openPost = null; state.replyDraft = null;
  $('#tabs').hidden = false; $('#fab').hidden = false; $('#intro').hidden = false;
  if (location.hash) history.replaceState(null, '', '/');
  try {
    const { posts, radiusKm } = await api(`/api/posts?${qs()}&sort=${state.sort}`);
    view.replaceChildren();
    if (!posts.length) {
      view.append(el('div', { class: 'empty' },
        el('p', {}, `Noch nichts los im Umkreis von ${radiusKm} km.`),
        el('p', {}, 'Sei die erste Person, die hier etwas postet!'),
        el('button', { class: 'primary', onclick: openComposer }, 'Ersten Post schreiben')));
      return;
    }
    view.append(...posts.map((p) => postCard(p, { onOpen: () => openPost(p.id) })));
  } catch (e) { toast(e.message); }
}

async function openPost(id) {
  if (state.openPost !== id) history.pushState({ post: id }, '', `#post-${id}`);
  state.openPost = id;
  $('#tabs').hidden = true; $('#fab').hidden = true; $('#intro').hidden = true;
  try {
    const { post, replies } = await api(`/api/posts/${id}?${qs()}`);
    const input = el('input', { placeholder: 'Antworten…', maxlength: 250 });
    const picker = mediaPicker({ compact: true });
    state.replyDraft = () => input.value.trim() || picker.file;
    const send = el('button', { class: 'primary', type: 'submit' }, 'Senden');
    const form = el('form', { class: 'reply-form', onsubmit: async (e) => {
      e.preventDefault();
      if (!input.value.trim() && !picker.file) return;
      send.disabled = true;
      try {
        const mediaId = await picker.upload();
        await api(`/api/posts/${id}/replies`, { method: 'POST', body: { text: input.value, mediaId } });
        openPost(id);
      } catch (err) { toast(err.message); send.disabled = false; }
    } }, el('div', { class: 'reply-extras' }, picker.preview, picker.bar),
      el('div', { class: 'reply-row' }, picker.buttons, input, send));
    view.replaceChildren(
      el('div', { class: 'detail-head' }, el('button', { onclick: showFeed }, '← Zurück')),
      postCard(post),
      ...replies.map((r) => postCard(r, { isReply: true })),
      ...(replies.length ? [] : [el('div', { class: 'empty' }, 'Noch keine Antworten.')]),
      form);
    window.scrollTo(0, 0);
  } catch (e) { toast(e.message); showFeed(); }
}

let composerPicker;
function openComposer() {
  $('#composeText').value = ''; $('#count').textContent = '0';
  composerPicker = mediaPicker();
  $('#composeMedia').replaceChildren(composerPicker.preview, composerPicker.bar, composerPicker.buttons);
  $('#sendCompose').disabled = false;
  $('#composer').showModal();
  $('#composeText').focus();
}

async function refreshKarma() {
  try { const { karma } = await api('/api/me'); $('#karma').textContent = karma; } catch {}
}

function setCoords(lat, lng, label) {
  state.coords = { lat, lng };
  store.set('kiez-coords', JSON.stringify({ lat, lng, label }));
  $('#place').textContent = label;
  showFeed(); refreshKarma();
}

function askCity(reason) {
  $('#place').textContent = 'Kein Standort';
  $('#tabs').hidden = true; $('#fab').hidden = true;
  view.replaceChildren(el('div', { class: 'empty' },
    el('p', {}, reason),
    el('button', { class: 'primary', onclick: locate }, 'Standort erneut freigeben'),
    el('p', {}, 'Oder eine Stadt zum Ausprobieren wählen:'),
    el('div', { class: 'city-list' }, Object.entries(CITIES).map(([name, [lat, lng]]) =>
      el('button', { onclick: () => { $('#tabs').hidden = false; $('#fab').hidden = false; setCoords(lat, lng, name); } }, name)))));
}

function locate() {
  if (!navigator.geolocation) return askCity('Dein Browser unterstützt keine Standortabfrage.');
  $('#place').textContent = 'Standort wird gesucht…';
  navigator.geolocation.getCurrentPosition(
    (pos) => { $('#tabs').hidden = false; $('#fab').hidden = false; setCoords(pos.coords.latitude, pos.coords.longitude, 'In deiner Nähe'); },
    () => askCity('Ohne Standort kann Kiez dir keine Posts aus deiner Umgebung zeigen.'),
    { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 });
}

// --- Events ---
$('#tabs').addEventListener('click', (e) => {
  const b = e.target.closest('button'); if (!b) return;
  state.sort = b.dataset.sort;
  document.querySelectorAll('#tabs button').forEach((x) => x.classList.toggle('active', x === b));
  showFeed();
});
$('#fab').onclick = openComposer;
$('#cancelCompose').onclick = () => $('#composer').close();
$('#composeText').oninput = (e) => ($('#count').textContent = e.target.value.length);
$('#composeForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const text = $('#composeText').value.trim();
  if (!text && !composerPicker.file) return toast('Schreib etwas oder füge ein Foto/Video hinzu');
  $('#sendCompose').disabled = true;
  try {
    const mediaId = await composerPicker.upload();
    await api('/api/posts', { method: 'POST', body: { text, mediaId, ...state.coords } });
    $('#composer').close();
    state.sort = 'new';
    document.querySelectorAll('#tabs button').forEach((x) => x.classList.toggle('active', x.dataset.sort === 'new'));
    showFeed();
  } catch (err) { toast(err.message); $('#sendCompose').disabled = false; }
});
$('#karma').onclick = async () => {
  try { const { karma, posts } = await api('/api/me'); toast(`Karma ${karma} · ${posts} Beiträge`); } catch {}
};
// Zurück-Taste (Browser und Android) führt von einem Post zurück zum Feed
window.addEventListener('popstate', () => { if (state.openPost) showFeed(); });

// Beim Zurückkehren in die App neu laden, aber nicht während man gerade schreibt oder ein Foto auswählt
let hiddenAt = 0;
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'hidden') { hiddenAt = Date.now(); return; }
  if (!state.coords || Date.now() - hiddenAt < 60_000 || $('#composer').open) return;
  if (state.openPost) { if (!state.replyDraft?.()) openPost(state.openPost); } else showFeed();
});

// --- Start ---
(async function init() {
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js').catch(() => {});
  state.token = store.get('kiez-token');
  if (!state.token) await register();
  locate();
})();
