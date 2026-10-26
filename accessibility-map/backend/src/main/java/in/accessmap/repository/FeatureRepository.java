package in.accessmap.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import in.accessmap.domain.Feature;
import in.accessmap.domain.FeatureType;

public interface FeatureRepository extends JpaRepository<Feature, Long> {
    boolean existsByPlace_IdAndTypeAndLabelIgnoreCase(Long placeId, FeatureType type, String label);
}
