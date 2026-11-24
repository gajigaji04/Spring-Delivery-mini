-- 주문 목록을 최신 주문이 위로 (#98) — 정렬이 id 오름차순 → (created_at DESC, id DESC)
--   SELECT ... FROM orders WHERE user_id = ?  ORDER BY created_at DESC, id DESC ...  (고객의 주문 목록, OFFSET·keyset)
--   SELECT ... FROM orders WHERE store_id = ? ORDER BY created_at DESC, id DESC ...  (매장별 주문 목록)
--   keyset 다음 페이지: ... AND (created_at < ? OR (created_at = ? AND id < ?))
--
-- id가 아니라 created_at으로 정렬하는 이유: id 순서와 주문 시각 순서가 다를 수 있음
--   (데모 시더가 지난 3일 정산용 주문을 "지금" 주문보다 나중에 넣고 created_at만 과거로 기록)
--
-- V12의 (user_id, id)·(store_id, id)와 같은 원리 — (조건 컬럼, 정렬 컬럼들) 순서라 정렬 없이 그 고객·매장의 행만 읽고 멈춤
-- 오름차순 인덱스를 거꾸로 읽으면(Index Scan Backward) DESC 정렬이 되므로 DESC로 만들 필요 없음
-- 새 인덱스의 앞부분(user_id·store_id)이 기존 인덱스 역할(FK 검사, 고객 진행 중 주문 확인)도 하므로 V12 인덱스는 교체
--   → orders 쓰기마다 갱신하는 인덱스 수는 그대로
-- 측정(EXPLAIN)은 아직 하지 않음 — 데이터를 쌓아 확인하는 절차는 docs/performance.md

CREATE INDEX idx_orders_user_id_created_at_id ON orders (user_id, created_at, id);
DROP INDEX idx_orders_user_id_id;

CREATE INDEX idx_orders_store_id_created_at_id ON orders (store_id, created_at, id);
DROP INDEX idx_orders_store_id_id;

-- 점주의 전체 매장 주문 목록(stores.owner_id 조인)은 V12와 마찬가지로 인덱스로 개선하지 않음 (docs/performance.md 6장 남은 병목)
-- 주의: CREATE INDEX는 만드는 동안 orders 쓰기를 막음 — 데모 규모라 일반 생성 (많이 쌓인 운영이면 CONCURRENTLY 검토)
