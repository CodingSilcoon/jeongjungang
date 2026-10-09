# 서버 배포

> **한 줄 요약:** CI가 main에서 앱 이미지를 GHCR에 올리고, EC2 한 대가 그 이미지를 받아 `deploy/compose.yaml`로 Caddy(HTTPS) + 앱 + Postgres + Redis를 띄운다.

```
인터넷 ──80/443──> Caddy ──/api/*──> app:8080 ──> postgres:5432, redis:6379
                                         (compose 내부 네트워크, 밖에서 접근 불가)
```

| 파일 | 내용 |
|:---|:---|
| `backend/Dockerfile` | 운영 이미지 (JDK로 빌드, JRE로 실행, 루트 아닌 사용자) |
| `deploy/compose.yaml` | 운영 구성. 앱은 `ghcr.io/codingsilcoon/jeongjungang-backend` 이미지를 받는다. 데이터는 볼륨(`postgres-data`, `redis-data`, `caddy-data`)에 남는다 |
| `deploy/Caddyfile` | `/api/*`만 앱으로 넘김. 인증서 자동 발급 |
| `deploy/compose.local.yaml` | 로컬에서 이미지를 직접 빌드할 때 덧붙이는 파일 |
| `deploy/.env.example` | 서버에 채울 값: `DOMAIN`, `DB_PASSWORD`, `KAKAO_REST_API_KEY`, `APP_TAG`(선택) |
| `.github/workflows/backend-ci.yml` | main에 merge되면 테스트 통과 후 이미지를 GHCR에 올림 (`latest`, `sha-xxxxxxx`) |

## 로컬에서 운영 구성 띄워 보기

Docker Desktop을 켜고:

```bash
cd deploy
cp .env.example .env      # DOMAIN=localhost, DB_PASSWORD는 아무 값
docker compose -f compose.yaml -f compose.local.yaml up -d --build   # 현재 코드로 직접 빌드
curl -k https://localhost/api/v1/health
docker compose down       # 데이터는 남는다. 지우려면 down -v
```

로컬 개발(`gradlew bootRun`)은 `backend/compose.yaml`을 쓰고, 이 구성과는 별개다.

## EC2에 처음 올리기

1. **EC2 만들기**: Ubuntu 24.04, t3.micro(1GB)로 시작. 서버에서 빌드하지 않아서 실행만 하면 되고, 로컬 측정 기준 전체 약 400MB. 모자라면 인스턴스 정지 → 타입을 t3.small로 변경 → 시작 (디스크·탄력적 IP 유지)
2. **보안 그룹**: 80·443(TCP), 443(UDP)은 전체 허용, 22는 **내 IP만**. 5432·6379는 열지 않는다
3. **탄력적 IP** 할당 후 인스턴스에 연결 (안 하면 재시작 때 IP가 바뀜)
4. **DuckDNS**: 서브도메인을 만들고 탄력적 IP를 넣는다. 인증서 발급 전에 도메인이 이 IP를 가리켜야 한다
5. **서버 준비** (SSH 접속 후):
   ```bash
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker $USER && exit   # 다시 접속
   # 1GB 서버라 스왑 2GB
   sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
   echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
   ```
6. **설정 받고 띄우기** (저장소·이미지 모두 공개라 로그인 필요 없음)
   ```bash
   git clone https://github.com/CodingSilcoon/jeongjungang.git && cd jeongjungang/deploy
   cp .env.example .env && chmod 600 .env && nano .env
   docker compose pull && docker compose up -d
   ```
   - `pull`이 `denied`로 실패하면 이미지가 비공개인 것: 조직 Packages → `jeongjungang-backend` → Package settings → Change visibility → Public (처음 한 번만)
7. **확인**: `curl https://{도메인}/api/v1/health`

## 업데이트

```bash
cd jeongjungang && git pull && cd deploy && docker compose pull && docker compose up -d
```

- main에 merge된 뒤 Actions의 `publish`가 끝나야 새 `latest` 이미지가 있다
- 되돌리기: `.env`의 `APP_TAG`를 이전 태그(`sha-xxxxxxx`)로 바꾸고 `docker compose up -d`. 태그 목록은 저장소 오른쪽 Packages

DB 스키마 변경은 앱이 시작할 때 Flyway가 적용한다. 바뀐 컨테이너만 다시 만들어지고, 데이터 볼륨은 그대로다.

## 운영

| 할 일 | 명령 |
|:---|:---|
| 상태 | `docker compose ps` |
| 로그 | `docker compose logs -f app` |
| DB 백업 | `docker compose exec -T postgres pg_dump -U jeongjungang jeongjungang \| gzip > backup-$(date +%F).sql.gz` |
| DB 복원 | `gunzip -c backup.sql.gz \| docker compose exec -T postgres psql -U jeongjungang jeongjungang` |

- 디스크 통째 백업은 EBS 스냅샷 (콘솔 → 볼륨 → 스냅샷 생성)
- AWS Budgets에 월 예산 알림을 걸어 둔다

## 아직 안 한 것

- 자동 배포(CD): `publish` 뒤에 서버에서 위 "업데이트"를 실행하는 단계. 지금은 손으로 실행
- 초대 링크 `https://{도메인}/m/{코드}` 안내 페이지와 앱 링크용 `/.well-known/assetlinks.json` (지금은 Caddy가 404)
- `DB_PASSWORD`는 Postgres를 **처음 띄울 때** 저장된다. 나중에 `.env`만 바꾸면 앱이 접속하지 못한다
