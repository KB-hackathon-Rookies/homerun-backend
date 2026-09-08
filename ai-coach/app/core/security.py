"""Spring 이 발급한 JWT(HMAC) 검증. sub = member id 만 사용한다."""

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.core.config import settings

_bearer = HTTPBearer(auto_error=True)

# Spring 의 Keys.hmacShaKeyFor(secret) 는 시크릿 길이에 따라 HS256/384/512 를 자동 선택한다
# (64바이트 이상이면 HS512). 그래서 HMAC 계열을 모두 허용해 서명 알고리즘 불일치로 인한 401 을
# 막는다. 대칭키(HMAC)끼리라 알고리즘 혼동 공격 위험은 없다. 설정값이 그중 하나가 아니어도 포함한다.
_ACCEPTED_ALGORITHMS = sorted({settings.jwt_algorithm, "HS256", "HS384", "HS512"})


def get_current_member_id(
    credentials: HTTPAuthorizationCredentials = Depends(_bearer),
) -> int:
    """토큰을 검증하고 member id 를 돌려준다. 서명·만료가 틀리면 401."""
    try:
        payload = jwt.decode(
            credentials.credentials,
            settings.jwt_secret,
            algorithms=_ACCEPTED_ALGORITHMS,
        )
    except jwt.PyJWTError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="유효하지 않은 토큰입니다.",
        ) from exc

    sub = payload.get("sub")
    if sub is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="토큰에 사용자 정보가 없습니다.",
        )
    try:
        return int(sub)
    except (TypeError, ValueError) as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="토큰의 사용자 식별자가 올바르지 않습니다.",
        ) from exc
