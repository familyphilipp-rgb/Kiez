#!/bin/sh
# Kiez mit einem Befehl auf dem Server einrichten, neben der Ideenwerft-Website:
#   curl -fsSL https://raw.githubusercontent.com/familyphilipp-rgb/Kiez/main/deploy/install.sh | sh
# Braucht keine eigene Domain: die Adresse ist <IP-mit-Bindestrichen>.sslip.io, Caddy holt das HTTPS-Zertifikat.
set -e

DIR=/opt/kiez
REPO=https://github.com/familyphilipp-rgb/Kiez.git

say() { printf '\n\033[1;31m▸\033[0m %s\n' "$*"; }
fail() { printf '\n\033[1;31mFehler:\033[0m %s\n' "$*" >&2; exit 1; }

command -v docker >/dev/null || fail "Docker ist nicht installiert."
SUDO=""; [ "$(id -u)" = 0 ] || SUDO="sudo"

say "Öffentliche IP ermitteln"
IP=$(curl -4 -fsS https://ifconfig.me 2>/dev/null || curl -4 -fsS https://api.ipify.org)
[ -n "$IP" ] || fail "IP nicht gefunden."
HOST="$(echo "$IP" | tr . -).sslip.io"
echo "IP $IP, Adresse https://$HOST"

say "Code nach $DIR holen"
if [ -d "$DIR/.git" ]; then $SUDO git -C "$DIR" pull --ff-only
else $SUDO git clone "$REPO" "$DIR"; fi

say "Ideenwerft-Caddy suchen"
CADDY=$($SUDO docker ps --filter network=ideenwerft_default --format '{{.Names}} {{.Image}}' | awk '$2 ~ /caddy/ {print $1; exit}')
[ -n "$CADDY" ] || fail "Kein Caddy-Container im Netz ideenwerft_default gefunden. Läuft die Ideenwerft-Website?"
CADDYFILE=$($SUDO docker inspect "$CADDY" --format '{{range .Mounts}}{{if eq .Destination "/etc/caddy/Caddyfile"}}{{.Source}}{{end}}{{end}}')
[ -n "$CADDYFILE" ] && $SUDO test -f "$CADDYFILE" || fail "Caddyfile von $CADDY nicht gefunden."
echo "Caddy: $CADDY, Datei: $CADDYFILE"

say "Kiez bauen und starten (dauert beim ersten Mal ein paar Minuten)"
$SUDO docker compose -f "$DIR/deploy/docker-compose.yml" up -d --build

say "Caddy um Kiez erweitern"
if $SUDO grep -q "$HOST" "$CADDYFILE"; then
  echo "Eintrag für $HOST ist schon da."
else
  BACKUP="$CADDYFILE.vor-kiez-$(date +%Y%m%d%H%M%S)"
  $SUDO cp "$CADDYFILE" "$BACKUP"
  { echo; echo "# Kiez-App (eingefügt von deploy/install.sh)"; sed "s/^kiez\.ideenwerft-friedenau\.de {/$HOST {/" "$DIR/deploy/Caddyfile.kiez" | grep -v '^#'; } \
    | $SUDO tee -a "$CADDYFILE" >/dev/null
  if ! $SUDO docker exec "$CADDY" caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile >/dev/null 2>&1; then
    $SUDO cp "$BACKUP" "$CADDYFILE"
    fail "Caddy-Konfiguration ungültig, alte Datei wiederhergestellt ($BACKUP)."
  fi
  echo "Sicherung der alten Datei: $BACKUP"
fi
$SUDO docker exec "$CADDY" caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile

say "Kurz warten, bis das Zertifikat da ist"
i=0
until curl -fsS "https://$HOST/api/place?lat=52.47&lng=13.33" >/dev/null 2>&1 || [ $i -ge 30 ]; do sleep 2; i=$((i + 1)); done

say "Fertig"
echo "Kiez läuft unter: https://$HOST"
echo "Diese Adresse auf GitHub als Variable KIEZ_SERVER_URL eintragen, dann wird die APK damit gebaut."
