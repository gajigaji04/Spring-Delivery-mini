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
}
