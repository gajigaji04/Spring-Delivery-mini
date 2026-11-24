package com.sparta.springdeliverymini.dto;

import com.sparta.springdeliverymini.exception.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

// keyset 페이지 요청 (#54) — ?afterId=120&size=20 : 목록에서 주문 120 다음에 오는 주문 size건
// 정렬은 최신 주문이 위(주문 시각 내림차순, 같으면 id 내림차순, #98) — 위치는 afterId 주문의 (주문 시각, id)로 정함
// afterId = 0이면 처음부터
// OFFSET(?page=)은 뒤쪽 페이지일수록 건너뛸 행을 모두 읽고 버리지만, keyset은 마지막으로 받은 id 다음부터 바로 읽음
// 정렬 기준(id)이 고유하므로 페이지 사이에 중복·누락이 없고, 조회 중 새 주문이 추가돼도 이미 받은 행이 밀려 다시 나오지 않음
public record KeysetQuery(long afterId, int size) {

    // 범위 검사는 PageQuery와 같은 기준 (상한 100, 벗어나면 400)
    public static KeysetQuery of(long afterId, int size) {
        if (afterId < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "afterId: 0 이상이어야 합니다.");
        }
        if (size < 1 || size > PageQuery.MAX_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "size: 1 이상 " + PageQuery.MAX_SIZE + " 이하여야 합니다.");
        }
        return new KeysetQuery(afterId, size);
    }

    public boolean fromStart() {
        return afterId == 0;
    }

    // 항상 첫 페이지(OFFSET 0) — 위치는 afterId 조건이 정함. size + 1건 조회로 다음 페이지 여부만 판단(Slice)
    public Pageable toPageable(Sort sort) {
        return PageRequest.of(0, size, sort);
    }
}
