package com.sparta.springdeliverymini.controller;

import com.sparta.springdeliverymini.dto.MenuCreateRequest;
import com.sparta.springdeliverymini.dto.MenuResponse;
import com.sparta.springdeliverymini.service.MenuService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    // OWNER 권한 확인은 SecurityConfig에서 토큰의 role로 처리 (CUSTOMER → 403)
    @PostMapping("/menus")
    public ResponseEntity<MenuResponse> addMenu(@Valid @RequestBody MenuCreateRequest request,
                                                Authentication authentication) {
        MenuResponse menu = menuService.createMenu(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(menu);
    }

    @GetMapping("/menus")
    public List<MenuResponse> getMenus() {
        return menuService.getMenus();
    }

    @GetMapping("/menus/{id}")
    public MenuResponse getMenu(@PathVariable Long id) {
        return menuService.getMenu(id);
    }

    @PutMapping("/menus/{id}")
    public ResponseEntity<MenuResponse> putMenu(
            @PathVariable Long id,
            // 요청 Body의 JSON을 MenuCreateRequest로 변환 및 검증
            @Valid @RequestBody MenuCreateRequest request,
            // JWT 인증을 통해 현재 로그인한 사용자 정보 확인
            Authentication authentication) {

        // Service에 전달하여 실제 수정 로직 수행
        MenuResponse menu = menuService.putMenu(
                id,
                request,
                authentication.getName()
        );

        // 수정 성공 시 200 OK와 수정된 메뉴 정보 반환
        return ResponseEntity.status(HttpStatus.OK).body(menu);
    }

    @DeleteMapping("/menus/{id}")
    public ResponseEntity<Void> deleteMenu(
            @PathVariable Long id,
            Authentication authentication
    ) {
        // 삭제할 메뉴의 정보를 Service에 전달
        menuService.deleteMenu(id, authentication.getName());

        // 삭제 성공 응답으로 줄 데이터 제외
        return ResponseEntity.noContent().build();
    }
}
