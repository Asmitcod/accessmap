package in.accessmap.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import in.accessmap.domain.Feature;
import in.accessmap.domain.FeatureType;

public interface FeatureRepository extends JpaRepository<Feature, Long> {
    Optional<Feature> findByPlace_IdAndTypeAndLabelIgnoreCase(Long placeId, FeatureType type, String label);
}
