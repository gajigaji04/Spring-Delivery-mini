package com.sparta.springdeliverymini.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "menus")
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

    // 소프트 삭제 처리 메서드
    public void delete() {
        this.deleted = true;
    }
}