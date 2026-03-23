package org.thomcgn.backend.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thomcgn.backend.menu.domain.DrinkCategory;

import java.util.List;

public interface DrinkCategoryRepository extends JpaRepository<DrinkCategory, Long> {

    List<DrinkCategory> findAllByOrderBySortOrderAscNameAsc();
}

