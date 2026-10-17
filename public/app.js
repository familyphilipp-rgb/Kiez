// Kiez – Frontend ohne Framework
const $ = (s) => document.querySelector(s);
const view = $('#view');
const state = { token: null, coords: null, sort: 'new', openPost: null };
const COLORS = ['#FF9908', '#FFBA00', '#DD5F5F', '#06A3CB', '#8ABDB0', '#9EC41C'];
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
    el('div', { class: 'text' }, p.text),
    el('div', { class: 'footer' }, isReply ? null : el('span', {}, `💬 ${p.commentCount}`), ...actions));

  return el('article', { class: 'post' + (isReply ? ' reply' : ''), style: `background:${p.color}` }, body, votes);
}

async function showFeed() {
  state.openPost = null;
  $('#tabs').hidden = false; $('#fab').hidden = false;
  history.replaceState(null, '', '/');
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
  state.openPost = id;
  $('#tabs').hidden = true; $('#fab').hidden = true;
  try {
    const { post, replies } = await api(`/api/posts/${id}?${qs()}`);
    const input = el('input', { placeholder: 'Antworten…', maxlength: 250, required: true });
    const form = el('form', { class: 'reply-form', onsubmit: async (e) => {
      e.preventDefault();
      try { await api(`/api/posts/${id}/replies`, { method: 'POST', body: { text: input.value } }); openPost(id); }
      catch (err) { toast(err.message); }
    } }, input, el('button', { class: 'primary', type: 'submit' }, 'Senden'));
    view.replaceChildren(
      el('div', { class: 'detail-head' }, el('button', { onclick: showFeed }, '← Zurück')),
      postCard(post),
      ...replies.map((r) => postCard(r, { isReply: true })),
      ...(replies.length ? [] : [el('div', { class: 'empty' }, 'Noch keine Antworten.')]),
      form);
    window.scrollTo(0, 0);
  } catch (e) { toast(e.message); showFeed(); }
}

function openComposer() {
  const color = COLORS[Math.floor(Math.random() * COLORS.length)];
  $('#composeCard').style.background = color;
  $('#composeText').value = ''; $('#count').textContent = '0';
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
  if (!text) return;
  try {
    await api('/api/posts', { method: 'POST', body: { text, ...state.coords } });
    $('#composer').close();
    state.sort = 'new';
    document.querySelectorAll('#tabs button').forEach((x) => x.classList.toggle('active', x.dataset.sort === 'new'));
    showFeed();
  } catch (err) { toast(err.message); }
});
$('#karma').onclick = async () => {
  try { const { karma, posts } = await api('/api/me'); toast(`Karma ${karma} · ${posts} Beiträge`); } catch {}
};
// Pull-to-refresh-Ersatz: beim Zurückkehren in den Tab neu laden
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible' && state.coords) state.openPost ? openPost(state.openPost) : showFeed();
});

// --- Start ---
(async function init() {
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js').catch(() => {});
  state.token = store.get('kiez-token');
  if (!state.token) await register();
  locate();
})();
