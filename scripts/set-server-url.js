// Trägt die Server-Adresse in capacitor.config.json ein: KIEZ_SERVER_URL=https://kiez.example.de node scripts/set-server-url.js
const fs = require('node:fs');
const path = require('node:path');

const url = process.env.KIEZ_SERVER_URL;
if (!url) process.exit(0);
if (!/^https:\/\/[^/]+$/.test(url)) {
  console.error(`KIEZ_SERVER_URL muss wie https://kiez.example.de aussehen, war: ${url}`);
  process.exit(1);
}
const file = path.join(__dirname, '..', 'capacitor.config.json');
const config = JSON.parse(fs.readFileSync(file, 'utf8'));
config.server = { ...config.server, url };
fs.writeFileSync(file, JSON.stringify(config, null, 2) + '\n');
console.log(`Server-Adresse der App: ${url}`);
