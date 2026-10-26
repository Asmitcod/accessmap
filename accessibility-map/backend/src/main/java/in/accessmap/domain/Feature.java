package in.accessmap.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "features")
public class Feature {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeatureType type;

    @Column(nullable = false, length = 80)
    private String label;

    protected Feature() {
    }

    public Feature(Place place, FeatureType type, String label) {
        this.place = place;
        this.type = type;
        this.label = label;
    }

    public Feature(FeatureType type, String label) {
        this.type = type;
        this.label = label;
    }

    void setPlace(Place place) {
        this.place = place;
    }

    public Long getId() {
        return id;
    }

    public Place getPlace() {
        return place;
    }

    public FeatureType getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }
}
