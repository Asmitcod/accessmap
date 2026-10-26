package in.accessmap.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import in.accessmap.domain.Place;

public interface PlaceRepository extends JpaRepository<Place, Long> {
}
