package com.sparta.springdeliverymini.dto;

import java.time.LocalDate;

// 정산이 있는 가장 최근 날짜 (GET /api/admin/settlements/latest-date) — 정산이 하나도 없으면 date: null
public record LatestSettlementDateResponse(LocalDate date) {
}
