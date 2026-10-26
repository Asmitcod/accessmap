package in.accessmap.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "places")
public class Place {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String osmId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String category;
    private double latitude;
    private double longitude;
    private boolean demoData;

    @OneToMany(mappedBy = "place", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<Feature> features = new ArrayList<>();

    protected Place() {
    }

    public Place(
            String osmId,
            String name,
            String address,
            String category,
            double latitude,
            double longitude,
            boolean demoData) {
        this.osmId = osmId;
        this.name = name;
        this.address = address;
        this.category = category;
        this.latitude = latitude;
        this.longitude = longitude;
        this.demoData = demoData;
    }

    public void addFeature(Feature feature) {
        features.add(feature);
        feature.setPlace(this);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getOsmId() {
        return osmId;
    }

    public String getAddress() {
        return address;
    }

    public String getCategory() {
        return category;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public boolean isDemoData() {
        return demoData;
    }

    public List<Feature> getFeatures() {
        return features;
    }
}
