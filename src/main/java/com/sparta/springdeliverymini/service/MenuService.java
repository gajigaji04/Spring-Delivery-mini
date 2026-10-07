package com.sparta.springdeliverymini.service;

import com.sparta.springdeliverymini.dto.MenuCreateRequest;
import com.sparta.springdeliverymini.dto.MenuResponse;
import com.sparta.springdeliverymini.entity.Menu;
import com.sparta.springdeliverymini.entity.User;
import com.sparta.springdeliverymini.exception.ApiException;
import com.sparta.springdeliverymini.repository.MenuRepository;
import com.sparta.springdeliverymini.repository.UserRepository;
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
                menuRepository.findByIdAndDeletedFalse(id)
                        .orElseThrow(() ->
                                new ApiException(HttpStatus.NOT_FOUND, "메뉴가 없습니다."))
        );
    }
}

