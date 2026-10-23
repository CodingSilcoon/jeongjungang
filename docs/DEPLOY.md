# 서버 배포

> **한 줄 요약:** main에 merge되면 CI가 앱 이미지를 GHCR에 올리고 EC2에 SSH로 들어가 `deploy/deploy.sh`를 실행한다. 서버는 `deploy/compose.yaml`로 Caddy(HTTPS) + 앱 + Postgres + Redis를 띄운다.

```
인터넷 ──80/443──> Caddy ──/api/*──> app:8080 ──> postgres:5432, redis:6379
                                         (compose 내부 네트워크, 밖에서 접근 불가)
```

| 파일 | 내용 |
|:---|:---|
| `backend/Dockerfile` | 운영 이미지 (JDK로 빌드, JRE로 실행, 루트 아닌 사용자) |
| `deploy/compose.yaml` | 운영 구성. 앱은 `ghcr.io/codingsilcoon/jeongjungang-backend` 이미지를 받는다. 데이터는 볼륨(`postgres-data`, `redis-data`, `caddy-data`)에 남는다 |
| `deploy/caddy/Caddyfile` | `/api/*`는 앱으로, `/m/{코드}`는 초대 안내 페이지, `/.well-known/assetlinks.json`은 앱 링크 확인 파일. 나머지는 404. 인증서 자동 발급 |
| `deploy/caddy/site/` | 초대 안내 페이지(`invite.html`, `invite.js`)와 `assetlinks.json`. 폴더째 Caddy에 마운트한다 |
| `deploy/deploy.sh` | 서버에서 main 최신 설정·이미지로 다시 띄우고 health 확인. CI가 실행하고, 손으로 실행해도 된다 |
| `deploy/compose.local.yaml` | 로컬에서 이미지를 직접 빌드할 때 덧붙이는 파일 |
| `deploy/.env.example` | 서버에 채울 값: `DOMAIN`, `DB_PASSWORD`, `KAKAO_REST_API_KEY`, `FCM_CREDENTIALS_BASE64`, `APP_TAG`(선택) |
| `.github/workflows/backend-ci.yml` | main에 merge되면 테스트 → 이미지를 GHCR에 올림(`latest`, `sha-xxxxxxx`) → 서버 배포(`deploy` job) |

## 현재 서버

| 항목 | 값 |
|:---|:---|
| 주소 | `https://jeongjungang.duckdns.org/api/v1` (앱 배포 후) |
| EC2 | `jeongjungang-server`, t3.micro(크레딧 표준), Ubuntu 24.04 x86, gp3 20GB 암호화, 서울(ap-northeast-2) |
| 탄력적 IP | `15.165.29.46` (DuckDNS `jeongjungang`이 가리킴) |
| 보안 그룹 | 22·80·443 TCP 전체 허용 (SSH는 키로만 로그인) |
| 접속 | `ssh -i jeongjungang-server.pem ubuntu@15.165.29.46` (키는 서버 담당이 보관) |

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

1. **EC2 만들기**: Ubuntu 24.04 x86, t3.micro(1GB)로 시작. 고급 세부 정보에서 **크레딧 사양 표준**(무제한이면 CPU를 많이 쓸 때 추가 요금), **종료 방지** 켜기. 스토리지 gp3 20GB(8GB는 이미지·스왑·로그로 빠듯함). 서버에서 빌드하지 않아서 실행만 하면 되고, 로컬 측정 기준 전체 약 400MB. 모자라면 인스턴스 정지 → 타입을 t3.small로 변경 → 시작 (디스크·탄력적 IP 유지)
2. **보안 그룹**: 80·443(TCP)은 전체 허용. 22는 작업 장소가 고정이면 내 IP만, 아니면 전체 허용(Ubuntu AMI는 키로만 로그인됨). 5432·6379는 열지 않는다
3. **탄력적 IP** 할당 후 인스턴스에 연결 (안 하면 재시작 때 IP가 바뀜)
4. **DuckDNS**: 서브도메인을 만들고 탄력적 IP를 넣는다. 만들 때 **접속한 PC의 IP가 자동으로 들어가므로** 탄력적 IP로 바꾸고 update ip. 인증서 발급 전에 도메인이 이 IP를 가리켜야 한다
5. **서버 준비** (SSH 접속 후):
   ```bash
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker $USER && exit   # 다시 접속
   # 1GB 서버라 스왑 2GB. 메모리가 꽤 찰 때만 쓰게 swappiness 10
   sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
   echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
   echo "vm.swappiness=10" | sudo tee /etc/sysctl.d/99-swappiness.conf && sudo sysctl -p /etc/sysctl.d/99-swappiness.conf
   sudo timedatectl set-timezone Asia/Seoul   # 로그 시간을 한국 시간으로
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

`backend/`나 `deploy/`가 바뀐 PR이 main에 merge되면 자동으로 배포된다: `test`·`docker` → `publish` → `deploy`. Actions의 `deploy` job이 실패하면 그 로그에 서버 출력이 그대로 나온다.

손으로 배포할 때(예: `.env`를 바꾼 뒤):

```bash
~/jeongjungang/deploy/deploy.sh
```

- 스크립트는 `git reset --hard origin/main`으로 서버의 추적 파일을 main과 똑같이 맞춘다. 서버에서 저장소 파일을 직접 고치지 않는다(`.env`는 git이 관리하지 않아 남는다)
- 되돌리기: `.env`의 `APP_TAG`를 이전 태그(`sha-xxxxxxx`)로 바꾸고 `deploy.sh`. 태그 목록은 저장소 오른쪽 Packages. `APP_TAG`를 고정해 두면 자동 배포도 그 태그를 쓰니, 고친 뒤 비운다

### 자동 배포용 SSH 키

CI는 전용 키로 접속하고, 서버의 `~/.ssh/authorized_keys`에서 그 키는 `deploy.sh`만 실행하도록 묶여 있다. 키가 새어도 셸을 얻을 수 없다.

```
command="/home/ubuntu/jeongjungang/deploy/deploy.sh",restrict ssh-ed25519 AAAA… github-actions-deploy
```

| 저장소 Secret (environment `production`) | 값 |
|:---|:---|
| `DEPLOY_SSH_KEY` | 위 키의 개인 키 |
| `DEPLOY_KNOWN_HOSTS` | `ssh-keyscan -t ed25519 15.165.29.46` 결과 (서버를 새로 만들면 다시 넣는다) |

키를 바꿀 때: 새 키를 만들어 `authorized_keys`의 그 줄을 교체하고 `DEPLOY_SSH_KEY`를 갱신한다.

### 앱 링크 지문 추가

초대 링크가 앱으로 바로 열리려면 그 기기에 설치한 빌드의 서명 지문이 `deploy/caddy/site/.well-known/assetlinks.json`에 있어야 한다. 디버그 키는 PC마다 달라서 시연용 빌드를 만드는 PC의 지문을 모두 넣는다(`cd android && gradlew.bat signingReport`의 `SHA-256`). 지금은 hefour PC의 디버그 키만 들어 있다. PR로 합치면 자동 배포로 반영된다.

DB 스키마 변경은 앱이 시작할 때 Flyway가 적용한다. 바뀐 컨테이너만 다시 만들어지고, 데이터 볼륨은 그대로다.

### FCM 키 넣기

Firebase 콘솔 → 프로젝트 설정 → 서비스 계정 → 새 비공개 키 생성으로 받은 JSON을 base64 한 줄로 바꿔 `.env`의 `FCM_CREDENTIALS_BASE64`에 넣는다. 키는 git·채팅에 올리지 않는다.

```bash
# PC(Git Bash)에서: 키를 서버로 보내고 서버에서 .env에 넣은 뒤 원본을 지운다
scp -i ~/.ssh/jeongjungang-server.pem fcm-service-account.json ubuntu@15.165.29.46:~/
ssh -i ~/.ssh/jeongjungang-server.pem ubuntu@15.165.29.46 \
  'cd ~/jeongjungang/deploy && sed -i "/^FCM_CREDENTIALS_BASE64=/d" .env && echo "FCM_CREDENTIALS_BASE64=$(base64 -w0 ~/fcm-service-account.json)" >> .env && rm ~/fcm-service-account.json && ./deploy.sh'
```

키가 잘못됐으면 앱이 뜨지 않는다(`docker compose logs app`). 푸시가 나가면 로그에 `FCM 푸시 N건 보냄`이 남는다.

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

- 앱의 App Links 설정(`MainActivity`에 `autoVerify` intent-filter). 서버 쪽 `assetlinks.json`은 준비됨
- DB 자동 백업. 지금은 위 `pg_dump`를 손으로 실행
- `DB_PASSWORD`는 Postgres를 **처음 띄울 때** 저장된다. 나중에 `.env`만 바꾸면 앱이 접속하지 못한다
