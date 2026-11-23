package com.sparta.springdeliverymini.entity;

// Refresh Token 폐기 사유 (V15 refresh_tokens.revoke_reason)
public enum RefreshTokenRevokeReason {
    // 재발급에 사용됨 — 이 토큰이 다시 오면 재사용(탈취 의심)
    ROTATED,
    // 로그아웃
    LOGOUT,
    // 같은 계열에서 재사용이 감지되어 함께 폐기됨
    REUSE_DETECTED,
    // 비밀번호 변경·재설정 (V23) — 그 회원의 다른 로그인을 모두 끊음 (비밀번호가 새었을 수 있으므로)
    PASSWORD_CHANGED
}
