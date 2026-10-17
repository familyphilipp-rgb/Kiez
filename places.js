// Ortsname zu Koordinaten, offline aus GeoNames-Daten (wie OfflineReverseGeocode bei Jodel)
const fs = require('node:fs');
const path = require('node:path');

const CELL = 0.1; // Raster in Grad, damit nur nahe Orte verglichen werden
const grid = new Map();
const key = (lat, lng) => `${Math.floor(lat / CELL)}:${Math.floor(lng / CELL)}`;

for (const line of fs.readFileSync(path.join(__dirname, 'data', 'places.tsv'), 'utf8').split('\n')) {
  if (!line || line.startsWith('#')) continue;
  const [name, lat, lng] = line.split('\t');
  const p = { name, lat: Number(lat), lng: Number(lng) };
  const k = key(p.lat, p.lng);
  if (!grid.has(k)) grid.set(k, []);
  grid.get(k).push(p);
}

// Nächster bekannter Ort im Umkreis von maxKm, sonst null
function nearestPlace(lat, lng, maxKm = 15) {
  const cy = Math.floor(lat / CELL), cx = Math.floor(lng / CELL);
  const cosLat = Math.cos((lat * Math.PI) / 180);
  let best = null, bestD = Infinity;
  for (let dy = -2; dy <= 2; dy++) {
    for (let dx = -3; dx <= 3; dx++) {
      for (const p of grid.get(`${cy + dy}:${cx + dx}`) || []) {
        const d = Math.hypot((p.lat - lat) * 111, (p.lng - lng) * 111 * cosLat);
        if (d < bestD) { bestD = d; best = p; }
      }
    }
  }
  return best && bestD <= maxKm ? best.name : null;
}

module.exports = { nearestPlace };
