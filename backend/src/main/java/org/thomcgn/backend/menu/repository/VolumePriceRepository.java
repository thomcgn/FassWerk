package org.thomcgn.backend.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thomcgn.backend.menu.domain.VolumePrice;

import java.util.List;
import java.util.Optional;

public interface VolumePriceRepository extends JpaRepository<VolumePrice, Long> {

    List<VolumePrice> findAllByOrderByVolumeMlAsc();

    Optional<VolumePrice> findByVolumeMl(Integer volumeMl);

    boolean existsByVolumeMl(Integer volumeMl);

    void deleteByVolumeMl(Integer volumeMl);
}

