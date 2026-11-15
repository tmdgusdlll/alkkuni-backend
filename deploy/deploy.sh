#!/usr/bin/env bash
# EC2에서 root로 실행한다. ci-cd.yml이 SSM으로 이 커밋의 이 파일을 받아 부른다.
# 사용: bash /opt/alkkuni/deploy/deploy.sh <커밋 sha>
# 롤백: 이전 커밋 sha로 다시 실행한다(ECR에 sha 태그가 남아 있다. 수명 주기 정책이 최근 10개만 둔다).
set -euo pipefail

SHA=${1:?커밋 sha가 필요하다}
REPO_RAW=https://raw.githubusercontent.com/tmdgusdlll/alkkuni-backend/$SHA
REGION=ap-northeast-2
cd /opt/alkkuni

# 1. 이 커밋의 compose·Nginx 설정·배치 스크립트. 서버 파일을 손으로 고치지 않고 깃에 있는 것만 쓴다.
mkdir -p deploy/nginx
curl -fsSL "$REPO_RAW/compose.prod.yaml" -o compose.prod.yaml
curl -fsSL "$REPO_RAW/deploy/nginx/default.conf" -o deploy/nginx/default.conf
curl -fsSL "$REPO_RAW/deploy/run-job.sh" -o deploy/run-job.sh

# 2. .env를 Parameter Store(/alkkuni/prod/*)에서 다시 만든다. 이미지 태그는 이번 커밋으로 고정한다.
umask 077
aws ssm get-parameters-by-path --region "$REGION" --path /alkkuni/prod --with-decryption \
  --query 'Parameters[].[Name,Value]' --output text \
  | awk -F '\t' '{ sub(".*/", "", $1); print $1 "=" $2 }' > .env.new
printf 'API_TAG=%s\nBATCH_TAG=%s\n' "$SHA" "$SHA" >> .env.new
mv .env.new .env

# 3. ECR 로그인(인스턴스 역할) 후 이미지 받기. batch는 받아만 두고 run-job.sh가 쓴다.
REGISTRY=$(grep '^IMAGE_REGISTRY=' .env | cut -d= -f2)
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY"
compose() { docker compose -f compose.prod.yaml "$@"; }
compose --profile job pull api batch

# 4. db·nginx는 없을 때(첫 배포)나 정의가 바뀌었을 때만 다시 만든다. api는 새 이미지로 교체하고 healthy까지 기다린다.
# ponytail: 교체 중에는 Nginx가 502를 낸다(PROGRESS.md 미해결 질문 "무중단 배포"). 실패해도 자동 롤백은 없다. 위 "롤백" 참고.
compose up -d db nginx
start=$(date +%s)
compose up -d --no-deps --wait --wait-timeout 180 api
echo "api healthy: $(( $(date +%s) - start ))초"

# 5. Nginx 설정 파일이 바뀌었을 수 있어 다시 읽힌다. 마지막으로 Nginx를 거쳐 헬스를 확인한다.
compose exec -T nginx nginx -s reload
curl -fsS --retry 5 --retry-delay 2 --retry-all-errors http://localhost/actuator/health
echo
# 지난 sha 이미지 정리(EBS가 차지 않게). 실행 중이 아닌 batch도 지우므로 방금 받은 것(3일 이내 생성)은 남긴다.
docker image prune -af --filter until=72h > /dev/null
