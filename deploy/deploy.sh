#!/usr/bin/env bash
# EC2에서 root로 실행한다. ci-cd.yml이 SSM으로 이 커밋의 이 파일을 받아 부른다.
# 사용: bash /opt/alkkuni/deploy/deploy.sh <커밋 sha>
# 받은 파일은 이미지 pull이 성공한 뒤에만 교체한다. 교체 뒤 하나라도 실패하면 직전 파일(.prev)로 되돌리고 실패로 끝난다(rollback).
# 더 이전으로 돌리려면 그 커밋 sha로 다시 실행한다(ECR 수명 주기 정책이 최근 10개만 둔다).
# -E: compose 함수 안에서 난 실패에도 ERR trap(rollback)이 걸리게 한다.
set -Eeuo pipefail

SHA=${1:?커밋 sha가 필요하다}
REPO_RAW=https://raw.githubusercontent.com/tmdgusdlll/alkkuni-backend/$SHA
REGION=ap-northeast-2
# 배포 하나를 이루는 파일. 이미지 태그는 .env에 있어서 이 묶음을 되돌리면 이미지도 되돌아간다.
FILES=(compose.prod.yaml deploy/nginx/default.conf deploy/run-job.sh .env)
cd /opt/alkkuni
umask 077
compose() { docker compose -f compose.prod.yaml "$@"; }

# 1. 새 파일을 .next/에 받는다. 서버 파일을 손으로 고치지 않고 깃에 있는 것만 쓴다.
rm -rf .next && mkdir -p .next/deploy/nginx
for f in compose.prod.yaml deploy/nginx/default.conf deploy/run-job.sh; do
  curl -fsSL "$REPO_RAW/$f" -o ".next/$f"
done
# .env는 Parameter Store(/alkkuni/prod/*)로 만든다. 작은따옴표로 감싸야 compose가 값 안의 $·#를 해석하지 않는다.
# 감쌀 수 없는 작은따옴표가 들어간 값은 거부한다.
# 앞뒤 공백도 거부한다. 콘솔에 붙여 넣다 버킷 이름 끝에 공백이 들어가 첫 적재가 S3 400으로 실패했다(2026-11-16).
aws ssm get-parameters-by-path --region "$REGION" --path /alkkuni/prod --with-decryption \
  --query 'Parameters[].[Name,Value]' --output text \
  | awk -F '\t' -v q="'" '{
      sub(".*/", "", $1)
      if (index($2, q)) { print $1 " 값에 작은따옴표가 있다" > "/dev/stderr"; exit 1 }
      if ($2 ~ /^[ \t]|[ \t]$/) { print $1 " 값 앞뒤에 공백이 있다" > "/dev/stderr"; exit 1 }
      print $1 "=" q $2 q
    }' > .next/.env
printf "API_TAG='%s'\nBATCH_TAG='%s'\n" "$SHA" "$SHA" >> .next/.env

# 2. ECR 로그인(인스턴스 역할) 후 새 이미지 받기. 여기서 실패하면 서버는 아무것도 바뀌지 않는다.
REGISTRY=$(grep '^IMAGE_REGISTRY=' .next/.env | cut -d= -f2 | tr -d "'")
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY"
docker compose -f .next/compose.prod.yaml --env-file .next/.env --profile job pull api batch

# 3. 지금 파일을 .prev/에 두고 새 파일로 바꾼다. 첫 배포면 .prev가 없다.
rm -rf .prev
if [ -f .env ]; then
  mkdir -p .prev/deploy/nginx
  for f in "${FILES[@]}"; do cp -p "$f" ".prev/$f"; done
fi
mkdir -p deploy/nginx
for f in "${FILES[@]}"; do cp -p ".next/$f" "$f"; done

# 직전 파일로 api·db·nginx를 다시 띄운다. 직전 이미지는 아직 로컬에 있다(정리는 성공 뒤에만).
# 새 api가 적용한 Flyway 마이그레이션은 남는다. 옛 api가 새 스키마로 뜨려면 마이그레이션은 추가만 한다(REVIEW.md).
rollback() {
  trap - ERR
  echo "배포 실패: $SHA" >&2
  # 컨테이너 로그는 EC2에만 남긴다. SSM 출력은 공개 레포의 Actions 로그에 그대로 찍힌다.
  mkdir -p logs
  compose logs --no-color --tail 200 api > "logs/deploy-$SHA.log" 2>&1 || true
  echo "api 로그: /opt/alkkuni/logs/deploy-$SHA.log (EC2에서 확인)" >&2
  if [ ! -d .prev ]; then
    echo "첫 배포라 되돌릴 것이 없다" >&2
    exit 1
  fi
  for f in "${FILES[@]}"; do cp -p ".prev/$f" "$f"; done
  if compose up -d --wait --wait-timeout 180 db nginx api && compose exec -T nginx nginx -s reload; then
    echo "직전 배포로 되돌렸다($(grep '^API_TAG=' .env | cut -d= -f2 | tr -d "'"))" >&2
  else
    echo "롤백도 실패했다. 직접 확인이 필요하다" >&2
  fi
  exit 1
}
trap rollback ERR

# 4. db·nginx는 healthy까지 기다린다(api를 --no-deps로 띄워서 depends_on이 적용되지 않는다).
# db의 POSTGRES_* 는 빈 볼륨을 처음 만들 때만 쓰인다. DB_PASSWORD를 바꿀 땐 DB에서 ALTER USER를 먼저 한다(aws-setup 5단계).
# ponytail: api 교체 중에는 Nginx가 502를 낸다(PROGRESS.md 미해결 질문 "무중단 배포"). 롤백할 때도 한 번 더 끊긴다.
compose up -d --wait --wait-timeout 120 db nginx
start=$(date +%s)
compose up -d --no-deps --wait --wait-timeout 180 api
echo "api healthy: $(( $(date +%s) - start ))초"

# 5. Nginx 설정 파일이 바뀌었을 수 있어 다시 읽힌다. 마지막으로 Nginx를 거쳐 헬스를 확인한다.
compose exec -T nginx nginx -s reload
curl -fsS --retry 5 --retry-delay 2 --retry-all-errors http://localhost/actuator/health
echo
trap - ERR

# 지난 sha 이미지 정리(EBS가 차지 않게). 실행 중이 아닌 batch도 지우므로 방금 받은 것(3일 이내 생성)은 남긴다.
docker image prune -af --filter until=72h > /dev/null || true
