package com.sparta.springdeliverymini.service;

import com.sparta.springdeliverymini.dto.LoginRequest;
import com.sparta.springdeliverymini.dto.LoginResponse;
import com.sparta.springdeliverymini.dto.SignupRequest;
import com.sparta.springdeliverymini.dto.UserResponse;
import com.sparta.springdeliverymini.entity.User;
import com.sparta.springdeliverymini.exception.ApiException;
import com.sparta.springdeliverymini.jwt.JwtProvider;
import com.sparta.springdeliverymini.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    @Transactional
    public UserResponse signup(SignupRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다.");
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = new User(request.username(), encodedPassword, request.role());

        return UserResponse.from(userRepository.save(user));
    }

    public LoginResponse login(LoginRequest request) {
        // 아이디/비밀번호 중 어느 쪽이 틀렸는지 노출하지 않도록 같은 메시지 사용
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        return LoginResponse.bearer(jwtProvider.createToken(user.getUsername(), user.getRole()));
    }
}
