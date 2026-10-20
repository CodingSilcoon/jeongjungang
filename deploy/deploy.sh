#!/usr/bin/env bash
# 서버에서 main의 최신 설정과 이미지로 다시 띄운다. CI의 deploy job이 SSH로 실행하고, 손으로 실행해도 된다
#   ~/jeongjungang/deploy/deploy.sh
set -euo pipefail

cd "$(dirname "$0")"

HEALTH_TRIES=30   # 2초 간격, 최대 1분

git fetch --quiet origin main
git reset --quiet --hard origin/main   # 서버에서 고친 추적 파일은 덮어쓴다. .env는 git이 관리하지 않아 그대로 남는다
echo "commit: $(git log --oneline -1)"

docker compose pull --quiet
docker compose up -d --remove-orphans --wait

# Caddyfile만 바뀌면 컨테이너가 다시 만들어지지 않으므로 설정을 다시 읽힌다
docker compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile

# Caddy를 거쳐 앱까지 닿는지 본다. 외부 도메인 대신 서버 안에서 Host만 맞춰 확인한다
domain=$(grep -E '^DOMAIN=' .env | cut -d= -f2-)
for _ in $(seq "$HEALTH_TRIES"); do
  if curl -fsS --resolve "${domain}:443:127.0.0.1" "https://${domain}/api/v1/health" >/dev/null 2>&1; then
    echo "health: ok"
    docker image prune -f >/dev/null   # 예전 이미지가 디스크(20GB)를 채우지 않게
    exit 0
  fi
  sleep 2
done

echo "health: 실패. docker compose logs app 확인" >&2
docker compose ps >&2
exit 1
