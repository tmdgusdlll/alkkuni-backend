#!/usr/bin/env bash
# 배치 작업 하나를 돌린다. cron·직접 실행 모두 이걸 부른다.
# 사용: bash /opt/alkkuni/deploy/run-job.sh <작업 이름>   예) initial-load, link-ingredients
# batch는 Flyway 없이 validate만 해서 api가 healthy(마이그레이션 끝)인 뒤에만 돌린다.
# depends_on 대신 여기서 확인하는 이유: run이 api를 다시 만들 수 있고, api가 unhealthy면 배치가 영영 안 돈다.
set -euo pipefail

JOB=${1:?작업 이름이 필요하다}
cd /opt/alkkuni
compose() { docker compose -f compose.prod.yaml "$@"; }

# api 기동(start_period 60초 + 헬스 체크 간격)을 넉넉히 덮는 2분.
for _ in $(seq 24); do
  id=$(compose ps -q api)
  if [ -n "$id" ] && [ "$(docker inspect -f '{{.State.Health.Status}}' "$id")" = healthy ]; then
    exec docker compose -f compose.prod.yaml run --rm --no-deps batch --alkkuni.job="$JOB"
  fi
  sleep 5
done
echo "api가 2분 안에 healthy가 되지 않아 $JOB 을(를) 돌리지 않는다" >&2
exit 1
