// Kiez – Frontend ohne Framework
const $ = (s, root = document) => root.querySelector(s);
const $$ = (s, root = document) => [...root.querySelectorAll(s)];
const view = $('#view');
const state = {
  token: null, coords: null, placeLabel: '', sort: 'new', tag: null, view: 'feed', openPost: null,
  mineKind: 'posts', trending: [], feedIds: [], replyDraft: null, radiusKm: 10,
};
// Ausweich-Standorte, falls die Standortfreigabe fehlt
const CITIES = { Friedenau: [52.4707, 13.3304], Schöneberg: [52.4826, 13.3532], Kreuzberg: [52.4986, 13.4030], Mitte: [52.52, 13.405],
  Hamburg: [53.551, 9.993], München: [48.137, 11.575], Köln: [50.937, 6.96], Leipzig: [51.34, 12.375] };
const LEVEL_ICON = { Neuling: '🌱', Kiezkind: '🧭', Stammgast: '☕', Kiezlegende: '🏅', Kapitän: '⚓' };
const MAX_VIDEO_MB = 100;
const isNative = () => !!window.Capacitor?.isNativePlatform?.();

const store = {
  get(k) { try { return localStorage.getItem(k); } catch { return null; } },
  set(k, v) { try { localStorage.setItem(k, v); } catch {} },
};

// --- kleine Helfer ----------------------------------------------------------
const ICONS = {
  up: '<path d="m6 15 6-6 6 6"/>',
  down: '<path d="m6 9 6 6 6-6"/>',
  comment: '<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>',
  share: '<path d="M4 12v7a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7"/><path d="m16 6-4-4-4 4"/><path d="M12 2v13"/>',
  more: '<circle cx="5" cy="12" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="19" cy="12" r="1.5"/>',
  back: '<path d="m15 18-6-6 6-6"/>',
  pin: '<path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z"/><circle cx="12" cy="10" r="3"/>',
  camera: '<path d="M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3z"/><circle cx="12" cy="13" r="3"/>',
  video: '<path d="m16 13 5.2 3.5a.5.5 0 0 0 .8-.4V7.9a.5.5 0 0 0-.8-.4L16 11"/><rect x="2" y="6" width="14" height="12" rx="2"/>',
  image: '<rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-3.1-3.1a2 2 0 0 0-2.8 0L6 21"/>',
  x: '<path d="M18 6 6 18M6 6l12 12"/>',
  send: '<path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/>',
  flag: '<path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"/><path d="M4 22v-7"/>',
  trash: '<path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6"/>',
  sound: '<path d="M11 5 6 9H2v6h4l5 4z"/><path d="M15.5 8.5a5 5 0 0 1 0 7M19 5a10 10 0 0 1 0 14"/>',
  mute: '<path d="M11 5 6 9H2v6h4l5 4z"/><path d="m22 9-6 6M16 9l6 6"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
  hash: '<path d="M4 9h16M4 15h16M10 3 8 21M16 3l-2 18"/>',
  shield: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>',
};
const icon = (name, cls = '') => {
  const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  s.setAttribute('viewBox', '0 0 24 24'); s.setAttribute('class', `i ${cls}`); s.setAttribute('aria-hidden', 'true');
  s.innerHTML = ICONS[name];
  return s;
};

function el(tag, attrs = {}, ...children) {
  const n = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v === false || v == null) continue;
    if (k.startsWith('on')) n.addEventListener(k.slice(2), v);
    else if (k === 'style') n.style.cssText = v;
    else if (k in n && typeof v !== 'string') n[k] = v;
    else n.setAttribute(k, v);
  }
  for (const c of children.flat()) if (c != null && c !== false) n.append(c);
  return n;
}

// Kurze Töne, mit Web Audio erzeugt (keine Sounddateien, keine Lizenzfragen)
let audioCtx;
function sound(kind) {
  if (store.get('kiez-sound') === 'off') return;
  try {
    audioCtx ||= new (window.AudioContext || window.webkitAudioContext)();
    const notes = { send: [[660, 0], [990, 0.07]], vote: [[880, 0]], fresh: [[523, 0], [784, 0.09]] }[kind];
    for (const [freq, at] of notes) {
      const t = audioCtx.currentTime + at;
      const o = audioCtx.createOscillator(), g = audioCtx.createGain();
      o.type = 'sine'; o.frequency.setValueAtTime(freq, t);
      g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.12, t + 0.015); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.16);
      o.connect(g).connect(audioCtx.destination); o.start(t); o.stop(t + 0.18);
    }
  } catch {}
}

const haptic = (ms = 8) => { try { navigator.vibrate?.(ms); } catch {} };

function toast(msg) {
  const t = $('#toast');
  t.textContent = msg; t.hidden = false; t.classList.remove('out');
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => { t.classList.add('out'); setTimeout(() => (t.hidden = true), 250); }, 2600);
}

// Seitenwechsel mit View Transitions, wo der Browser sie kann
function transition(fn) {
  if (document.startViewTransition && !matchMedia('(prefers-reduced-motion: reduce)').matches) return document.startViewTransition(fn).finished.catch(() => {});
  return Promise.resolve(fn());
}

async function api(path, opts = {}) {
  const res = await fetch(path, {
    ...opts,
    headers: { 'Content-Type': 'application/json', ...(state.token ? { Authorization: `Bearer ${state.token}` } : {}) },
    body: opts.body ? JSON.stringify(opts.body) : undefined,
  });
  if (res.status === 401 && path !== '/api/register') { await register(); return api(path, opts); }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || 'Etwas ist schiefgelaufen');
  return data;
}

async function register() {
  const { token } = await api('/api/register', { method: 'POST' });
  state.token = token; store.set('kiez-token', token);
}

const qs = () => (state.coords ? `lat=${state.coords.lat}&lng=${state.coords.lng}` : '');

function timeAgo(ts) {
  const s = Math.floor((Date.now() - ts) / 1000);
  if (s < 60) return 'gerade eben';
  if (s < 3600) return `${Math.floor(s / 60)} Min.`;
  if (s < 86400) return `${Math.floor(s / 3600)} Std.`;
  return `${Math.floor(s / 86400)} T.`;
}
setInterval(() => $$('[data-ts]').forEach((n) => (n.textContent = timeAgo(Number(n.dataset.ts)))), 30_000);

function greeting() {
  const h = new Date().getHours();
  return h < 5 ? 'Gute Nacht' : h < 11 ? 'Guten Morgen' : h < 17 ? 'Hallo' : h < 22 ? 'Guten Abend' : 'Gute Nacht';
}

// Text mit anklickbaren Hashtags
function richText(text) {
  const out = []; let last = 0;
  for (const m of text.matchAll(/#[\p{L}\p{N}_]{2,30}/gu)) {
    out.push(text.slice(last, m.index));
    const tag = m[0].slice(1).toLowerCase();
    out.push(el('button', { class: 'hashtag', type: 'button', onclick: (e) => { e.stopPropagation(); openTag(tag); } }, m[0]));
    last = m.index + m[0].length;
  }
  out.push(text.slice(last));
  return out;
}

// --- Fotos und Videos ---------------------------------------------------------
async function shrinkImage(file) {
  if (file.type === 'image/gif') return file;
  try {
    const bmp = await createImageBitmap(file, { imageOrientation: 'from-image' });
    const scale = Math.min(1, 1600 / Math.max(bmp.width, bmp.height));
    const canvas = el('canvas', { width: Math.round(bmp.width * scale), height: Math.round(bmp.height * scale) });
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

function sheet(title, items) {
  return new Promise((resolve) => {
    const d = el('dialog', { class: 'sheet menu' });
    const done = (v) => { d.classList.add('closing'); setTimeout(() => { d.close(); d.remove(); }, 180); resolve(v); };
    d.append(el('div', { class: 'sheet-handle' }), title ? el('span', { class: 'eyebrow' }, title) : null,
      ...items.map((it) => el('button', { type: 'button', class: `menu-item ${it.danger ? 'danger' : ''}`, onclick: () => done(it.value) },
        it.icon ? icon(it.icon) : null, el('span', {}, it.label))),
      el('button', { type: 'button', class: 'menu-item cancel', onclick: () => done(null) }, 'Abbrechen'));
    d.addEventListener('cancel', (e) => { e.preventDefault(); done(null); });
    d.addEventListener('click', (e) => { if (e.target === d) done(null); });
    document.body.append(d); d.showModal();
  });
}

// Auswahl-Knöpfe, Vorschau und Fortschritt; im Composer und bei Antworten
function mediaPicker({ compact = false } = {}) {
  let file = null;
  const preview = el('div', { class: 'media-preview', hidden: true });
  const bar = el('div', { class: 'progress', hidden: true }, el('span'));
  async function pick(kind) {
    const accept = kind === 'video' ? 'video/*' : 'image/*';
    let capture = null;
    if (isNative()) {
      // In der Android-App bietet die Dateiauswahl keine Kamera an, daher selbst fragen
      const choice = await sheet(kind === 'video' ? 'Video hinzufügen' : 'Foto hinzufügen', [
        { value: 'camera', icon: kind === 'video' ? 'video' : 'camera', label: kind === 'video' ? 'Video aufnehmen' : 'Foto aufnehmen' },
        { value: 'gallery', icon: 'image', label: 'Aus der Galerie' }]);
      if (!choice) return;
      if (choice === 'camera') capture = 'environment';
    }
    const input = el('input', { type: 'file', accept, capture });
    input.onchange = async () => {
      const f = input.files[0]; if (!f) return;
      if (f.type.startsWith('video/') && f.size > MAX_VIDEO_MB * 1024 * 1024) return toast(`Video ist zu groß (max. ${MAX_VIDEO_MB} MB)`);
      file = f.type.startsWith('image/') ? await shrinkImage(f) : f;
      const url = URL.createObjectURL(file);
      preview.replaceChildren(file.type.startsWith('video/')
        ? el('video', { src: url, muted: true, playsInline: true, autoplay: true, loop: true })
        : el('img', { src: url, alt: 'Vorschau' }),
      el('button', { type: 'button', class: 'remove', 'aria-label': 'Entfernen', onclick: reset }, icon('x')));
      preview.hidden = false;
      preview.animate([{ opacity: 0, transform: 'scale(.96)' }, { opacity: 1, transform: 'none' }], { duration: 220, easing: 'cubic-bezier(.2,.8,.2,1)' });
    };
    input.click();
  }
  function reset() { file = null; preview.hidden = true; preview.replaceChildren(); bar.hidden = true; }
  const buttons = el('div', { class: `media-buttons ${compact ? 'compact' : ''}` },
    el('button', { type: 'button', class: 'icon-btn', 'aria-label': 'Foto', onclick: () => pick('image') }, icon('camera')),
    el('button', { type: 'button', class: 'icon-btn', 'aria-label': 'Video', onclick: () => pick('video') }, icon('video')));
  return {
    buttons, preview, bar, reset,
    get file() { return file; },
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

// Videos im Feed starten stumm, sobald sie sichtbar sind (wie bei Instagram/TikTok)
const videoObserver = 'IntersectionObserver' in window ? new IntersectionObserver((entries) => {
  for (const e of entries) {
    const v = e.target;
    if (e.isIntersecting && e.intersectionRatio > 0.6) v.play().catch(() => {});
    else v.pause();
  }
}, { threshold: [0, 0.6, 1] }) : null;

function mediaView(m, { inDetail = false } = {}) {
  if (!m) return null;
  const ratio = m.width && m.height ? `aspect-ratio:${m.width}/${m.height}` : '';
  if (m.kind === 'video') {
    const v = el('video', { src: m.url, poster: m.poster || null, muted: true, loop: true, playsInline: true, preload: 'metadata', controls: inDetail });
    const btn = el('button', { type: 'button', class: 'mute-btn', 'aria-label': 'Ton an/aus' }, icon('mute'));
    btn.onclick = (e) => { e.stopPropagation(); v.muted = !v.muted; btn.replaceChildren(icon(v.muted ? 'mute' : 'sound')); if (!v.muted) v.play(); };
    videoObserver?.observe(v);
    return el('div', { class: 'media video', style: ratio, onclick: (e) => e.stopPropagation() }, v, inDetail ? null : btn);
  }
  return el('div', { class: 'media', style: ratio },
    el('img', { src: m.url, alt: 'Foto', loading: 'lazy', decoding: 'async', onload: (e) => e.target.classList.add('loaded'),
      onclick: (e) => { e.stopPropagation(); showImage(m.url); } }));
}

function showImage(url) {
  const d = el('dialog', { class: 'lightbox', onclick: () => { d.close(); d.remove(); } }, el('img', { src: url, alt: 'Foto' }));
  document.body.append(d); d.showModal();
}

// --- Post-Karte ---------------------------------------------------------------
function postCard(p, { isReply = false, inDetail = false, onOpen } = {}) {
  const score = el('div', { class: 'score' }, String(p.score));
  const up = el('button', { class: `vote up ${p.myVote === 1 ? 'on' : ''}`, disabled: p.mine, 'aria-label': 'Upvote' }, icon('up'));
  const down = el('button', { class: `vote down ${p.myVote === -1 ? 'on' : ''}`, disabled: p.mine, 'aria-label': 'Downvote' }, icon('down'));
  const paint = () => {
    up.classList.toggle('on', p.myVote === 1); down.classList.toggle('on', p.myVote === -1);
    score.textContent = p.score;
    score.animate([{ transform: 'scale(1.35)' }, { transform: 'scale(1)' }], { duration: 260, easing: 'cubic-bezier(.2,.8,.2,1)' });
  };
  const vote = async (v) => {
    if (p.mine) return;
    const before = { myVote: p.myVote, score: p.score };
    const value = p.myVote === v ? 0 : v;
    // Sofort anzeigen, bei Fehler zurücksetzen
    p.score += value - p.myVote; p.myVote = value; paint(); haptic(value ? 12 : 6); if (value === 1) sound('vote');
    try {
      const r = await api(`/api/posts/${p.id}/vote`, { method: 'POST', body: { value } });
      p.score = r.score; p.myVote = r.myVote; score.textContent = r.score;
    } catch (e) { Object.assign(p, before); paint(); toast(e.message); }
  };
  up.onclick = (e) => { e.stopPropagation(); vote(1); };
  down.onclick = (e) => { e.stopPropagation(); vote(-1); };

  let badge = null;
  if (isReply) badge = el('span', { class: 'badge' }, p.isOP ? 'OJ' : `#${p.authorNo}`);
  else if (p.mine) badge = el('span', { class: 'badge' }, 'Du');

  const more = el('button', { class: 'icon-btn small', 'aria-label': 'Mehr', onclick: async (e) => {
    e.stopPropagation();
    const choice = await sheet(null, p.mine
      ? [{ value: 'delete', icon: 'trash', label: 'Löschen', danger: true }]
      : [{ value: 'report', icon: 'flag', label: 'Melden', danger: true }]);
    if (choice === 'delete') {
      try { await api(`/api/posts/${p.id}`, { method: 'DELETE' }); toast('Gelöscht'); card.classList.add('removing'); setTimeout(() => (isReply ? openPost(state.openPost, { quiet: true }) : card.remove()), 250); }
      catch (err) { toast(err.message); }
    } else if (choice === 'report') {
      try { await api(`/api/posts/${p.id}/report`, { method: 'POST' }); toast('Danke, wurde gemeldet'); } catch (err) { toast(err.message); }
    }
  } }, icon('more'));

  const share = !isReply && navigator.share ? el('button', { class: 'icon-btn small', 'aria-label': 'Teilen', onclick: (e) => {
    e.stopPropagation();
    navigator.share({ title: 'Kiez', text: p.text ? `„${p.text}“ – gesehen auf Kiez` : 'Gesehen auf Kiez', url: `${location.origin}/#post-${p.id}` }).catch(() => {});
  } }, icon('share')) : null;

  const body = el('div', { class: 'body' },
    el('div', { class: 'meta' }, badge, el('span', { 'data-ts': p.createdAt }, timeAgo(p.createdAt)),
      p.distance && !isReply ? el('span', { class: 'dist' }, icon('pin'), p.distance) : null),
    p.text ? el('div', { class: 'text' }, richText(p.text)) : null,
    mediaView(p.media, { inDetail }),
    el('div', { class: 'footer' },
      isReply ? null : el('span', { class: 'comments' }, icon('comment'), String(p.commentCount)),
      el('span', { class: 'spacer' }), share, more));

  const card = el('article', { class: `post ${isReply ? 'reply' : ''} ${inDetail ? 'detail' : ''}`, style: `--accent:${p.color}`,
    'data-id': p.id }, body, el('div', { class: 'votes' }, up, score, down));
  if (onOpen) { card.classList.add('tappable'); card.style.viewTransitionName = `post-${p.id}`; }

  // Einfach tippen öffnet, doppelt tippen gibt ein Upvote
  let tapTimer = null;
  card.addEventListener('click', (e) => {
    if (e.target.closest('button, video, a')) return;
    if (tapTimer) {
      clearTimeout(tapTimer); tapTimer = null;
      if (!p.mine && p.myVote !== 1) vote(1);
      burst(card, e);
      return;
    }
    tapTimer = setTimeout(() => { tapTimer = null; onOpen?.(); }, onOpen ? 230 : 0);
  });
  return card;
}

function burst(card, e) {
  const r = card.getBoundingClientRect();
  const b = el('div', { class: 'burst', style: `left:${e.clientX - r.left}px;top:${e.clientY - r.top}px` }, icon('up'));
  card.append(b); setTimeout(() => b.remove(), 700);
}

const skeletons = (n = 4) => Array.from({ length: n }, () => el('div', { class: 'post skeleton' },
  el('div', { class: 'body' }, el('i', { class: 'line short' }), el('i', { class: 'line' }), el('i', { class: 'line mid' }))));

function empty(title, text, action) {
  return el('div', { class: 'empty' }, el('div', { class: 'empty-icon' }, icon('hash')), el('h2', {}, title), el('p', {}, text), action || null);
}

// --- Navigation -----------------------------------------------------------------
function setNav(name) {
  state.view = name;
  $$('#bottomnav [data-nav]').forEach((b) => b.classList.toggle('active', b.dataset.nav === name));
  document.body.dataset.view = name;
}

function go(name) {
  haptic(5);
  if (name === 'feed') { if (state.view === 'feed') { window.scrollTo({ top: 0, behavior: 'smooth' }); return; } return transition(showFeed); }
  if (name === 'mine') return transition(showMine);
  if (name === 'channels') return transition(showChannels);
  if (name === 'profile') return transition(showProfile);
}

function segmented(options, active, onChange) {
  const wrap = el('div', { class: 'segmented', role: 'tablist' });
  const pill = el('span', { class: 'seg-pill' });
  const buttons = options.map(([value, label]) => el('button', { role: 'tab', class: value === active ? 'active' : '', 'aria-selected': String(value === active),
    onclick: () => { buttons.forEach((b) => { b.classList.remove('active'); b.setAttribute('aria-selected', 'false'); });
      btnFor(value).classList.add('active'); btnFor(value).setAttribute('aria-selected', 'true'); move(); haptic(5); onChange(value); } }, label));
  const btnFor = (v) => buttons[options.findIndex(([x]) => x === v)];
  function move() {
    const b = wrap.querySelector('button.active'); if (!b) return;
    pill.style.width = `${b.offsetWidth}px`; pill.style.transform = `translateX(${b.offsetLeft - 4}px)`;
  }
  wrap.append(pill, ...buttons);
  requestAnimationFrame(() => requestAnimationFrame(move));
  window.addEventListener('resize', move);
  return wrap;
}

// --- Feed ----------------------------------------------------------------------
let feedList;
async function showFeed() {
  setNav('feed');
  state.openPost = null; state.replyDraft = null;
  if (location.hash) history.replaceState(null, '', '/');
  feedList = el('div', { class: 'list' }, skeletons());
  const tagBar = el('div', { class: 'tagbar', id: 'tagbar' });
  view.replaceChildren(
    el('section', { class: 'intro' },
      el('span', { class: 'eyebrow' }, `Anonym · ${state.radiusKm} km um dich`),
      el('h1', {}, state.tag ? `#${state.tag}` : `${greeting()}, ${state.placeLabel || 'Kiez'}`)),
    el('div', { class: 'controls' }, segmented([['new', 'Neueste'], ['popular', 'Beliebt'], ['loud', 'Am lautesten']], state.sort, (v) => { state.sort = v; loadFeed(); }), tagBar),
    el('button', { class: 'new-pill', id: 'newPill', hidden: true, onclick: () => { loadFeed(); window.scrollTo({ top: 0, behavior: 'smooth' }); } }),
    feedList);
  await loadFeed();
}

function renderTagBar() {
  const bar = $('#tagbar'); if (!bar) return;
  const tags = state.trending.slice(0, 10);
  if (state.tag && !tags.some((t) => t.tag === state.tag)) tags.unshift({ tag: state.tag, count: 0 });
  bar.replaceChildren(...tags.map(({ tag }) => el('button', { class: `tagchip ${state.tag === tag ? 'active' : ''}`,
    onclick: () => { state.tag = state.tag === tag ? null : tag; haptic(5); transition(showFeed); } }, `#${tag}`)));
  bar.hidden = !tags.length;
}

async function loadFeed({ silent = false } = {}) {
  if (!state.coords) return;
  if (!silent) feedList.replaceChildren(...skeletons());
  try {
    const tag = state.tag ? `&tag=${encodeURIComponent(state.tag)}` : '';
    const { posts, radiusKm, trending } = await api(`/api/posts?${qs()}&sort=${state.sort}${tag}`);
    state.radiusKm = radiusKm; state.trending = trending; state.feedIds = posts.map((p) => p.id);
    renderTagBar();
    $('#newPill') && ($('#newPill').hidden = true);
    if (!posts.length) {
      feedList.replaceChildren(empty(state.tag ? `Noch nichts unter #${state.tag}` : 'Noch ruhig hier',
        state.tag ? 'Starte den Kanal mit dem ersten Post.' : `Im Umkreis von ${radiusKm} km hat noch niemand gepostet. Sei die erste Person!`,
        el('button', { class: 'primary', onclick: openComposer }, 'Ersten Post schreiben')));
      return;
    }
    feedList.replaceChildren(...posts.map((p, i) => {
      const c = postCard(p, { onOpen: () => openPost(p.id) });
      c.style.animationDelay = `${Math.min(i, 8) * 35}ms`;
      return c;
    }));
  } catch (e) { feedList.replaceChildren(empty('Keine Verbindung', e.message, el('button', { class: 'primary', onclick: () => loadFeed() }, 'Erneut versuchen'))); }
}

// Alle 30 Sekunden still nach neuen Posts schauen und einen Hinweis zeigen
setInterval(async () => {
  if (state.view !== 'feed' || document.hidden || !state.coords || state.sort !== 'new') return;
  try {
    const tag = state.tag ? `&tag=${encodeURIComponent(state.tag)}` : '';
    const { posts } = await api(`/api/posts?${qs()}&sort=new${tag}`);
    const fresh = posts.filter((p) => !state.feedIds.includes(p.id) && !p.mine).length;
    const pill = $('#newPill');
    if (pill && fresh && pill.hidden) sound('fresh');
    if (pill && fresh) { pill.textContent = `↑ ${fresh} neue${fresh === 1 ? 'r Post' : ' Posts'}`; pill.hidden = false; }
  } catch {}
}, 30_000);

// --- Detailansicht -------------------------------------------------------------
async function openPost(id, { quiet = false } = {}) {
  if (state.openPost !== id) history.pushState({ post: id }, '', `#post-${id}`);
  state.openPost = id;
  setNav('detail');
  if (!quiet) view.replaceChildren(el('div', { class: 'list' }, skeletons(2)));
  try {
    const { post, replies } = await api(`/api/posts/${id}?${qs()}`);
    const input = el('textarea', { placeholder: 'Antworten…', maxlength: 250, rows: 1,
      oninput: () => { input.style.height = 'auto'; input.style.height = `${Math.min(input.scrollHeight, 120)}px`; } });
    const picker = mediaPicker({ compact: true });
    state.replyDraft = () => input.value.trim() || picker.file;
    const send = el('button', { class: 'send-btn', type: 'submit', 'aria-label': 'Senden' }, icon('send'));
    const form = el('form', { class: 'reply-form', onsubmit: async (e) => {
      e.preventDefault();
      if (!input.value.trim() && !picker.file) return;
      send.disabled = true;
      try {
        const mediaId = await picker.upload();
        await api(`/api/posts/${id}/replies`, { method: 'POST', body: { text: input.value, mediaId } });
        haptic(12); sound('send');
        await openPost(id, { quiet: true });
        window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });
      } catch (err) { toast(err.message); send.disabled = false; }
    } }, el('div', { class: 'reply-extras' }, picker.preview, picker.bar),
      el('div', { class: 'reply-row' }, picker.buttons, input, send));
    const main = postCard(post, { inDetail: true });
    main.style.viewTransitionName = `post-${post.id}`;
    view.replaceChildren(
      el('div', { class: 'detail-head' },
        el('button', { class: 'back', onclick: () => history.back() }, icon('back'), 'Zurück'),
        el('span', { class: 'eyebrow' }, `${replies.length} ${replies.length === 1 ? 'Antwort' : 'Antworten'}`)),
      main,
      el('div', { class: 'thread' }, replies.map((r) => postCard(r, { isReply: true, inDetail: true }))),
      ...(replies.length ? [] : [el('p', { class: 'hint' }, 'Noch keine Antworten. Schreib die erste!')]),
      form);
    if (!quiet) window.scrollTo(0, 0);
  } catch (e) { toast(e.message); showFeed(); }
}

// --- Meine -----------------------------------------------------------------------
async function showMine() {
  setNav('mine');
  const list = el('div', { class: 'list' }, skeletons(3));
  const load = async () => {
    list.replaceChildren(...skeletons(3));
    try {
      const { posts } = await api(`/api/me/activity?kind=${state.mineKind}&${qs()}`);
      const texts = { posts: ['Noch keine Posts', 'Was du postest, findest du hier wieder.'],
        replies: ['Noch keine Antworten', 'Posts, auf die du geantwortet hast, landen hier.'],
        voted: ['Noch nichts gevotet', 'Posts mit deinem Upvote sammeln sich hier.'] }[state.mineKind];
      list.replaceChildren(...(posts.length ? posts.map((p) => postCard(p, { onOpen: () => openPost(p.id) })) : [empty(...texts)]));
    } catch (e) { list.replaceChildren(empty('Keine Verbindung', e.message)); }
  };
  view.replaceChildren(
    el('section', { class: 'intro' }, el('span', { class: 'eyebrow' }, 'Deine Aktivität'), el('h1', {}, 'Meine')),
    el('div', { class: 'controls' }, segmented([['posts', 'Posts'], ['replies', 'Kommentiert'], ['voted', 'Gevotet']], state.mineKind, (v) => { state.mineKind = v; load(); })),
    list);
  window.scrollTo(0, 0);
  await load();
}

// --- Kanäle ----------------------------------------------------------------------
async function showChannels() {
  setNav('channels');
  const grid = el('div', { class: 'channels' }, skeletons(2));
  view.replaceChildren(
    el('section', { class: 'intro' }, el('span', { class: 'eyebrow' }, 'Was gerade läuft'), el('h1', {}, 'Kanäle'),
      el('p', { class: 'lead' }, 'Jeder #Hashtag in einem Post wird zum Kanal. Tipp einen an, um nur diese Posts zu sehen.')),
    grid);
  window.scrollTo(0, 0);
  try {
    const { trending } = await api(`/api/posts?${qs()}&sort=new`);
    state.trending = trending;
    const suggestions = ['flohmarkt', 'verloren', 'gefunden', 'wg', 'event', 'frage', 'nachbarschaft', 'ideenwerft'];
    const tags = [...trending, ...suggestions.filter((s) => !trending.some((t) => t.tag === s)).map((tag) => ({ tag, count: 0 }))];
    grid.replaceChildren(...tags.map(({ tag, count }, i) => el('button', { class: 'channel', style: `--hue:${(i * 47) % 360}`,
      onclick: () => { state.tag = tag; transition(showFeed); } },
      el('span', { class: 'channel-hash' }, '#'), el('span', { class: 'channel-name' }, tag),
      el('span', { class: 'channel-count' }, count ? `${count} ${count === 1 ? 'Post' : 'Posts'}` : 'neu starten'))));
  } catch (e) { grid.replaceChildren(empty('Keine Verbindung', e.message)); }
}

function openTag(tag) { state.tag = tag; haptic(5); transition(showFeed); }

// --- Profil ----------------------------------------------------------------------
async function showProfile() {
  setNav('profile');
  view.replaceChildren(el('div', { class: 'list' }, skeletons(2)));
  window.scrollTo(0, 0);
  try {
    const me = await api('/api/me');
    const lv = me.level;
    const progress = lv.next ? Math.max(0, Math.min(1, (me.karma - lv.from) / (lv.next.at - lv.from))) : 1;
    const theme = store.get('kiez-theme') || 'auto';
    view.replaceChildren(
      el('section', { class: 'intro' }, el('span', { class: 'eyebrow' }, 'Anonym, aber mit Karma'), el('h1', {}, 'Profil')),
      el('div', { class: 'karma-card' },
        el('div', { class: 'karma-top' },
          el('div', {}, el('span', { class: 'eyebrow light' }, 'Dein Karma'), el('div', { class: 'karma-big' }, String(me.karma))),
          el('div', { class: 'level' }, el('span', { class: 'level-icon' }, LEVEL_ICON[lv.name] || '⭐'), lv.name)),
        el('div', { class: 'level-bar' }, el('span', { style: `width:${Math.round(progress * 100)}%` })),
        el('p', { class: 'level-next' }, lv.next ? `Noch ${lv.next.at - me.karma} Karma bis ${lv.next.name}` : 'Höchste Stufe erreicht. Ahoi, Kapitän!')),
      el('div', { class: 'stats' },
        stat(me.posts, 'Posts'), stat(me.replies, 'Antworten'), stat(me.votes, 'Votes')),
      el('div', { class: 'settings' },
        el('span', { class: 'eyebrow' }, 'Einstellungen'),
        el('button', { class: 'setting', onclick: chooseLocation }, icon('pin'), el('span', {}, 'Standort'), el('small', {}, state.placeLabel)),
        el('div', { class: 'setting' }, icon('sun'), el('span', {}, 'Design'),
          segmented([['auto', 'Auto'], ['light', 'Hell'], ['dark', 'Dunkel']], theme, (v) => { store.set('kiez-theme', v); applyTheme(); })),
        el('div', { class: 'setting' }, icon('sound'), el('span', {}, 'Töne'),
          segmented([['on', 'An'], ['off', 'Aus']], store.get('kiez-sound') === 'off' ? 'off' : 'on', (v) => { store.set('kiez-sound', v); if (v === 'on') sound('send'); })),
        el('div', { class: 'setting info' }, icon('shield'), el('span', {}, 'Du bist anonym. Kiez speichert keinen Namen und deinen Standort nur auf etwa 100 m gerundet. Fotos und Videos werden ohne GPS-Daten gespeichert.'))));
  } catch (e) { view.replaceChildren(empty('Keine Verbindung', e.message)); }
}
const stat = (n, label) => el('div', { class: 'stat' }, el('strong', {}, String(n)), el('span', {}, label));

function applyTheme() {
  const t = store.get('kiez-theme') || 'auto';
  if (t === 'auto') delete document.documentElement.dataset.theme; else document.documentElement.dataset.theme = t;
}

// --- Composer ------------------------------------------------------------------
let composerPicker;
function openComposer() {
  if (!state.coords) return toast('Erst einen Standort wählen');
  haptic(8);
  const ta = $('#composeText');
  ta.value = ''; updateCounter();
  composerPicker = mediaPicker();
  $('#composeMedia').replaceChildren(composerPicker.preview, composerPicker.bar);
  $('#composeButtons').replaceChildren(composerPicker.buttons);
  const tags = (state.tag ? [state.tag] : []).concat(state.trending.map((t) => t.tag)).filter((t, i, a) => a.indexOf(t) === i).slice(0, 6);
  $('#composeTags').replaceChildren(...tags.map((t) => el('button', { type: 'button', class: 'tagchip', onclick: () => {
    if (!ta.value.toLowerCase().includes(`#${t}`)) ta.value = `${ta.value.trimEnd()} #${t} `.trimStart();
    updateCounter(); ta.focus();
  } }, `#${t}`)));
  if (state.tag) ta.value = `#${state.tag} `;
  updateCounter();
  $('#sendCompose').disabled = false;
  $('#composer').classList.remove('closing');
  $('#composer').showModal();
  setTimeout(() => ta.focus(), 60);
}
function closeComposer() {
  const d = $('#composer');
  d.classList.add('closing');
  setTimeout(() => { d.close(); d.classList.remove('closing'); }, 180);
}
function updateCounter() {
  const left = 250 - $('#composeText').value.length;
  $('#count').textContent = left;
  $('#count').classList.toggle('warn', left < 30);
  const c = 2 * Math.PI * 15;
  const fill = $('#ringFill');
  fill.style.strokeDasharray = c;
  fill.style.strokeDashoffset = c * (left / 250);
  fill.classList.toggle('warn', left < 30);
}

// --- Standort ------------------------------------------------------------------
function setCoords(lat, lng, label, gps = state.gps) {
  state.coords = { lat, lng }; state.placeLabel = label; state.gps = gps;
  store.set('kiez-coords', JSON.stringify({ lat, lng, label, gps }));
  $('#placeLabel').textContent = label;
  refreshKarma();
}

async function chooseLocation() {
  const choice = await sheet('Standort', [{ value: 'gps', icon: 'pin', label: 'Mein aktueller Standort' },
    ...Object.keys(CITIES).map((c) => ({ value: c, label: c }))]);
  if (!choice) return;
  if (choice === 'gps') return locate(true);
  const [lat, lng] = CITIES[choice];
  setCoords(lat, lng, choice, false);
  transition(showFeed);
}

function locate(force = false) {
  const saved = (() => { try { return JSON.parse(store.get('kiez-coords')); } catch { return null; } })();
  if (saved && !force && !saved.gps) { setCoords(saved.lat, saved.lng, saved.label); showFeed(); }
  if (!navigator.geolocation) return saved ? null : askCity('Dein Gerät unterstützt keine Standortabfrage.');
  if (!saved || force || saved.gps) $('#placeLabel').textContent = 'Suche…';
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      if (saved && !force && !saved.gps) return;
      setCoords(pos.coords.latitude, pos.coords.longitude, 'In deiner Nähe', true);
      if (state.view === 'feed' || !state.view) showFeed();
      // Ortsnamen nachladen, z. B. „Friedenau“
      api(`/api/place?${qs()}`).then(({ name }) => {
        if (!name) return;
        setCoords(state.coords.lat, state.coords.lng, name);
        const h1 = $('.intro h1');
        if (state.view === 'feed' && !state.tag && h1) h1.textContent = `${greeting()}, ${name}`;
      }).catch(() => {});
    },
    () => { if (!saved || force) askCity('Ohne Standort kann Kiez dir keine Posts aus deiner Umgebung zeigen.'); },
    { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 });
}

function askCity(reason) {
  $('#placeLabel').textContent = 'Kein Standort';
  setNav('feed');
  view.replaceChildren(el('div', { class: 'empty' },
    el('div', { class: 'empty-icon' }, icon('pin')), el('h2', {}, 'Wo bist du?'), el('p', {}, reason),
    el('button', { class: 'primary', onclick: () => locate(true) }, 'Standort freigeben'),
    el('p', { class: 'small' }, 'Oder einen Ort zum Ausprobieren wählen:'),
    el('div', { class: 'city-list' }, Object.entries(CITIES).map(([name, [lat, lng]]) =>
      el('button', { class: 'tagchip', onclick: () => { setCoords(lat, lng, name, false); showFeed(); } }, name)))));
}

async function refreshKarma() {
  try { const { karma } = await api('/api/me'); $('#karmaValue').textContent = karma; } catch {}
}

// --- Wischen zum Aktualisieren -------------------------------------------------
(function pullToRefresh() {
  const ptr = $('#ptr'); let startY = null, dy = 0;
  window.addEventListener('touchstart', (e) => { startY = window.scrollY <= 0 && !document.querySelector('dialog[open]') ? e.touches[0].clientY : null; dy = 0; }, { passive: true });
  window.addEventListener('touchmove', (e) => {
    if (startY == null) return;
    dy = Math.max(0, e.touches[0].clientY - startY);
    const d = Math.min(dy * 0.5, 90);
    ptr.style.transform = `translate(-50%, ${d}px) rotate(${d * 4}deg)`;
    ptr.style.opacity = Math.min(1, d / 60);
    ptr.classList.toggle('ready', d >= 70);
  }, { passive: true });
  window.addEventListener('touchend', async () => {
    if (startY == null) return;
    startY = null;
    if (dy * 0.5 >= 70) {
      ptr.classList.add('spin'); haptic(10);
      if (state.view === 'feed') await loadFeed({ silent: true });
      else if (state.view === 'mine') await showMine();
      else if (state.view === 'detail') await openPost(state.openPost, { quiet: true });
      else if (state.view === 'channels') await showChannels();
      else if (state.view === 'profile') await showProfile();
      refreshKarma();
    }
    ptr.classList.remove('spin', 'ready');
    ptr.style.transform = ''; ptr.style.opacity = '';
  });
})();

// --- Events --------------------------------------------------------------------
$$('#bottomnav [data-nav]').forEach((b) => b.addEventListener('click', () => go(b.dataset.nav)));
$('#fab').onclick = openComposer;
$('#place').onclick = chooseLocation;
$('#karma').onclick = () => go('profile');
$('.brand').onclick = (e) => { e.preventDefault(); state.tag = null; go('feed'); };
$('#cancelCompose').onclick = closeComposer;
$('#composer').addEventListener('cancel', (e) => { e.preventDefault(); closeComposer(); });
$('#composer').addEventListener('click', (e) => { if (e.target === $('#composer')) closeComposer(); });
$('#composeText').oninput = updateCounter;
$('#composeForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const text = $('#composeText').value.trim();
  if (!text && !composerPicker.file) return toast('Schreib etwas oder füge ein Foto/Video hinzu');
  $('#sendCompose').disabled = true;
  try {
    const mediaId = await composerPicker.upload();
    await api('/api/posts', { method: 'POST', body: { text, mediaId, ...state.coords } });
    haptic(15); sound('send');
    closeComposer();
    state.sort = 'new';
    transition(showFeed);
    toast('Gepostet');
  } catch (err) { toast(err.message); $('#sendCompose').disabled = false; }
});

// Zurück-Taste (Browser und Android) führt von einem Post zurück
window.addEventListener('popstate', () => { if (state.view === 'detail') transition(showFeed); });

// Beim Zurückkehren in die App neu laden, aber nicht während man schreibt oder ein Foto auswählt
let hiddenAt = 0;
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'hidden') { hiddenAt = Date.now(); $$('video').forEach((v) => v.pause()); return; }
  if (!state.coords || Date.now() - hiddenAt < 60_000 || $('#composer').open) return;
  if (state.view === 'detail') { if (!state.replyDraft?.()) openPost(state.openPost, { quiet: true }); }
  else if (state.view === 'feed') loadFeed({ silent: true });
});

// --- Start ---------------------------------------------------------------------
(async function init() {
  applyTheme();
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js').catch(() => {});
  state.token = store.get('kiez-token');
  if (!state.token) await register();
  const m = location.hash.match(/^#post-(\d+)$/);
  locate();
  if (m) { history.replaceState(null, '', '/'); const wait = setInterval(() => { if (state.coords) { clearInterval(wait); openPost(Number(m[1])); } }, 100); setTimeout(() => clearInterval(wait), 10000); }
})();
