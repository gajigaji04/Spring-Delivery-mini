package com.sparta.springdeliverymini.service;

import com.sparta.springdeliverymini.dto.MenuCreateRequest;
import com.sparta.springdeliverymini.dto.MenuResponse;
import com.sparta.springdeliverymini.entity.Menu;
import com.sparta.springdeliverymini.entity.User;
import com.sparta.springdeliverymini.exception.ApiException;
import com.sparta.springdeliverymini.repository.MenuRepository;
import com.sparta.springdeliverymini.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class MenuService {

    private final MenuRepository menuRepository;
    private final UserRepository userRepository;

    public MenuService(MenuRepository menuRepository, UserRepository userRepository) {
        this.menuRepository = menuRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public MenuResponse createMenu(MenuCreateRequest request, String username) {
        // 토큰은 유효하지만 그 사이 회원이 삭제된 경우
        User owner = userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "존재하지 않는 회원입니다."));

        Menu menu = new Menu(request.name(), request.price(), request.description(), owner);
        return MenuResponse.from(menuRepository.save(menu));
    }

    public List<MenuResponse> getMenus() {
        return menuRepository.findAllByDeletedFalse()
                .stream()
                .map(MenuResponse::from)
                .toList();
    }

    public MenuResponse getMenu(Long id) {
        return MenuResponse.from(
                // 메뉴가 없으면 404 반환
                menuRepository.findByIdAndDeletedFalse(id)
                        .orElseThrow(() ->
                                new ApiException(HttpStatus.NOT_FOUND, "메뉴가 없습니다."))
        );
    }

    @Transactional // 값 변경 후 저장(Dirty Checking)
    public MenuResponse putMenu(Long id, MenuCreateRequest request, String username) {

        // 1. 현재 로그인한 사용자 찾기
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new ApiException(
                                HttpStatus.UNAUTHORIZED,
                                "존재하지 않는 회원입니다."
                        )
                );

        // 2. 수정할 메뉴 찾기
        // 없거나 삭제된 메뉴 → 404
        Menu menu = menuRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() ->
                        new ApiException(
                                HttpStatus.NOT_FOUND,
                                "메뉴가 없습니다."
                        )
                );

        // 3. 현재 사용자가 메뉴의 주인인지 확인
        System.out.println("username = " + username);
        System.out.println("currentUser id = " + currentUser.getId());
        System.out.println("menu owner id = " + menu.getOwner().getId());
        if (!menu.getOwner().getId().equals(currentUser.getId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "다른 사장님의 메뉴입니다."
            );
        }

        // 4. 기존 메뉴의 값 변경
        menu.update(
                request.name(),
                request.price(),
                request.description()
        );

        // 5. Dirty Checking으로 UPDATE
        return MenuResponse.from(menu);
    }
}

