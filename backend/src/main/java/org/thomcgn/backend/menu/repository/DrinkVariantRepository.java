package org.thomcgn.backend.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thomcgn.backend.menu.domain.DrinkVariant;

import java.util.List;

public interface DrinkVariantRepository extends JpaRepository<DrinkVariant, Long> {

    List<DrinkVariant> findAllByOrderByDrink_NameAscDisplayVolumeNameAsc();

    List<DrinkVariant> findAllByActiveTrueOrderByDrink_NameAscDisplayVolumeNameAsc();

    List<DrinkVariant> findByDrinkId(Long drinkId);

    List<DrinkVariant> findAllByVolumeMl(Integer volumeMl);

    List<DrinkVariant> findAllByVolumeMlAndUseVolumeStandardPriceTrue(Integer volumeMl);
}


