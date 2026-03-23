package org.thomcgn.backend.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thomcgn.backend.menu.domain.Drink;

import java.util.List;

public interface DrinkRepository extends JpaRepository<Drink, Long> {

    List<Drink> findAllByOrderByNameAsc();
}

