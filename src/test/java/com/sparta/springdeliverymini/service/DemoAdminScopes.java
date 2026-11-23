package com.sparta.springdeliverymini.service;

import com.sparta.springdeliverymini.config.DemoDataProperties;
import com.sparta.springdeliverymini.repository.StoreRepository;
import com.sparta.springdeliverymini.repository.UserRepository;

import static org.mockito.Mockito.mock;

// 서비스 단위 테스트용 DemoAdminScope — 데모가 꺼져 있어 아무것도 제한하지 않음 (데모 범위 자체는 DemoAdminScopeTest)
final class DemoAdminScopes {

    private DemoAdminScopes() {
    }

    static DemoAdminScope disabled() {
        return new DemoAdminScope(new DemoDataProperties(false, null, null, null, null, null, null),
                mock(UserRepository.class), mock(StoreRepository.class));
    }
}
