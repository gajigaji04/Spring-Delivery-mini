package com.sparta.springdeliverymini.entity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "menus")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA용 기본 생성자, 외부에서 new 금지
public class Menu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private int price;

    @Column(length = 255)
    private String description;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private boolean deleted = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    public Menu(String name, int price, String description, User owner) {
        this.name = name;
        this.price = price;
        this.description = description;
        this.owner = owner;
    }

    // 소프트 삭제 처리 메서드
    public void delete() {
        this.deleted = true;
    }

    // 메뉴 정보 수정 메서드
    // 요청 값 검증은 Controller의 @Valid(MenuCreateRequest)에서 이미 끝난 상태로 호출됨
    public void update(String name, int price, String description) {
        this.name = name;
        this.price = price;
        this.description = description;
    }
}