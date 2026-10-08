package com.sparta.springdeliverymini.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "menus")
@EntityListeners(AuditingEntityListener.class)
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

    protected Menu() {
    }

    public Menu(String name, int price, String description, User owner) {
        this.name = name;
        this.price = price;
        this.description = description;
        this.owner = owner;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getPrice() {
        return price;
    }

    public String getDescription() {
        return description;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public User getOwner() {
        return owner;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // 소프트 삭제 처리 메서드
    public void delete() {
        this.deleted = true;
    }

    public void update(@NotBlank(message = "메뉴 이름을 입력해야 합니다.") @Size(max = 50, message = "메뉴 이름은 50자 이하여야 합니다.") String name, @NotNull(message = "가격을 입력해야 합니다.") @Min(value = 1, message = "가격은 1원 이상이어야 합니다.") Integer price, @Size(max = 255, message = "설명은 255자 이하여야 합니다.") String description) {
        this.name = name;
        this.price = price;
        this.description = description;
    }
}