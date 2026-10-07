package com.sparta.springdeliverymini.repository;

import com.sparta.springdeliverymini.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MenuRepository extends JpaRepository<Menu, Long> {
    Optional<Menu> findByIdAndDeletedFalse(Long id);

    List<Menu> findAllByDeletedFalse();
}
