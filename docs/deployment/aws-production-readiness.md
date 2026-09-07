# AWS 운영 배포 준비

현재 CD는 `main` 커밋의 백엔드 이미지를 GHCR에 발행하는 단계까지만 자동화합니다. 실제 EC2 컨테이너 교체는 운영 서버, 네트워크, 승인 절차가 확정된 뒤 별도 변경으로 활성화합니다.

## 서버 준비 체크리스트

- EC2에 Docker Engine과 Compose plugin 설치
- 최소 권한 보안 그룹 구성(SSH, 애플리케이션, AI 코치 포트)
- `compose.prod.yaml`과 운영 `.env` 배치
- PostgreSQL·Redis·Chroma 영속 볼륨과 백업 정책 확인
- GHCR private package용 `read:packages` 토큰 준비
- 백엔드 `/actuator/health/readiness`와 AI 코치 `/health` 점검

## GitHub production 환경에 둘 값

- `PROD_HOST`, `PROD_USER`, `PROD_PORT`
- `PROD_SSH_KEY`, 검증된 `PROD_SSH_KNOWN_HOSTS`
- `PROD_DEPLOY_PATH`
- `GHCR_USERNAME`, `GHCR_READ_TOKEN`

운영 환경에는 승인자를 지정하고, 실제 자동 배포 워크플로는 두 서비스가 모두 healthy일 때만 성공 처리해야 합니다. 실패 시 직전 SHA 태그의 백엔드와 AI 코치 이미지를 함께 되돌려야 합니다.

## 운영 전 검증

1. 서버에서 `.env` 필수 값 누락 여부를 확인합니다.
2. `docker compose -f compose.prod.yaml config`로 설정을 검증합니다.
3. 새 SHA 이미지로 수동 배포하고 두 health endpoint를 확인합니다.
4. 이전 SHA 이미지로 실제 롤백 훈련을 한 번 수행합니다.
5. 위 절차가 검증된 뒤 `main` 자동 배포를 별도 승인으로 활성화합니다.
