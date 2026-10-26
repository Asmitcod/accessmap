package in.accessmap.config;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import in.accessmap.domain.Feature;
import in.accessmap.domain.FeatureType;
import in.accessmap.domain.Place;
import in.accessmap.domain.Report;
import in.accessmap.domain.ReportStatus;
import in.accessmap.repository.PlaceRepository;
import in.accessmap.repository.ReportRepository;

@Component
public class PlaceDataLoader implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(PlaceDataLoader.class);
    private static final String GEOJSON_PATH = "osm/delhi-places.geojson";
    private static final List<String> CATEGORY_KEYS =
            List.of("amenity", "railway", "shop", "healthcare", "tourism", "leisure", "building", "public_transport");

    private final PlaceRepository placeRepository;
    private final ReportRepository reportRepository;
    private final ObjectMapper objectMapper;

    public PlaceDataLoader(
            PlaceRepository placeRepository,
            ReportRepository reportRepository,
            ObjectMapper objectMapper) {
        this.placeRepository = placeRepository;
        this.reportRepository = reportRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (placeRepository.count() > 0) return;

        ClassPathResource geoJson = new ClassPathResource(GEOJSON_PATH);
        if (geoJson.exists()) {
            importOpenStreetMapPlaces(geoJson);
            return;
        }

        logger.warn("OSM dataset {} is missing; loading fictional demo places.", GEOJSON_PATH);
        loadDemoPlaces();
    }

    private void importOpenStreetMapPlaces(ClassPathResource resource) throws IOException {
        JsonNode root;
        try (InputStream input = resource.getInputStream()) {
            root = objectMapper.readTree(input);
        }

        JsonNode features = root.path("features");
        if (!root.path("type").asText().equals("FeatureCollection") || !features.isArray()) {
            throw new IllegalStateException("Invalid GeoJSON in classpath:" + GEOJSON_PATH);
        }

        List<Place> places = new ArrayList<>(features.size());
        for (int index = 0; index < features.size(); index++) {
            JsonNode feature = features.get(index);
            JsonNode properties = feature.path("properties");
            JsonNode coordinates = feature.path("geometry").path("coordinates");
            String osmId = properties.path("@id").asText("");
            String name = properties.path("name").asText("").trim();
            if (osmId.isBlank() || name.isBlank() || !coordinates.isArray() || coordinates.size() < 2) {
                throw new IllegalStateException(
                        "Invalid OSM place at GeoJSON feature index " + index + " in " + GEOJSON_PATH);
            }
            double longitude = coordinates.get(0).asDouble(Double.NaN);
            double latitude = coordinates.get(1).asDouble(Double.NaN);
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                    || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                throw new IllegalStateException(
                        "Invalid coordinates at GeoJSON feature index " + index + " in " + GEOJSON_PATH);
            }

            places.add(new Place(
                    osmId,
                    name,
                    buildAddress(properties),
                    findCategory(properties),
                    latitude,
                    longitude,
                    false));
        }

        if (places.isEmpty()) {
            throw new IllegalStateException("No named places found in classpath:" + GEOJSON_PATH);
        }
        placeRepository.saveAll(places);
        logger.info("Imported {} OpenStreetMap places from {}.", places.size(), GEOJSON_PATH);
    }

    private String buildAddress(JsonNode properties) {
        String fullAddress = properties.path("addr:full").asText("").trim();
        if (!fullAddress.isBlank()) return fullAddress;

        List<String> parts = new ArrayList<>();
        for (String key : List.of("addr:housenumber", "addr:street", "addr:suburb", "addr:city")) {
            String value = properties.path(key).asText("").trim();
            if (!value.isBlank() && !parts.contains(value)) parts.add(value);
        }
        return parts.isEmpty() ? "Delhi, India" : String.join(", ", parts);
    }

    private String findCategory(JsonNode properties) {
        for (String key : CATEGORY_KEYS) {
            String value = properties.path(key).asText("").trim();
            if (!value.isBlank()) return value.replace('_', ' ');
        }
        return "place";
    }

    private void loadDemoPlaces() {
        Place connaught = new Place(
                null,
                "Demo place near Connaught Place",
                "Central Delhi · illustrative location, not a real venue",
                "Demo",
                28.6328,
                77.2197,
                true);
        Feature entrance = new Feature(FeatureType.ENTRANCE, "Example entrance");
        Feature ramp = new Feature(FeatureType.RAMP, "Example ramp");
        Feature restroom = new Feature(FeatureType.RESTROOM, "Example restroom");
        connaught.addFeature(entrance);
        connaught.addFeature(ramp);
        connaught.addFeature(restroom);
        placeRepository.save(connaught);

        Place mandi = new Place(
                null,
                "Demo place near Mandi House",
                "Central Delhi · illustrative location, not a real venue",
                "Demo",
                28.6262,
                77.2342,
                true);
        Feature lift = new Feature(FeatureType.LIFT, "Example lift");
        Feature secondEntrance = new Feature(FeatureType.ENTRANCE, "Example entrance");
        mandi.addFeature(lift);
        mandi.addFeature(secondEntrance);
        placeRepository.save(mandi);

        Place karol = new Place(
                null,
                "Demo place near Karol Bagh",
                "West Delhi · illustrative location, not a real venue",
                "Demo",
                28.6514,
                77.1907,
                true);
        Feature secondRamp = new Feature(FeatureType.RAMP, "Example ramp");
        Feature secondRestroom = new Feature(FeatureType.RESTROOM, "Example restroom");
        karol.addFeature(secondRamp);
        karol.addFeature(secondRestroom);
        placeRepository.save(karol);

        Instant now = Instant.now();
        reportRepository.save(new Report(entrance, ReportStatus.STEP_FREE, null, now.minus(3, ChronoUnit.DAYS), 90));
        reportRepository.save(new Report(ramp, ReportStatus.MODERATE, null, now.minus(7, ChronoUnit.DAYS), 90));
        reportRepository.save(new Report(lift, ReportStatus.WORKING, null, now.minus(1, ChronoUnit.DAYS), 14));
        reportRepository.save(new Report(secondEntrance, ReportStatus.LIMITED, null, now.minus(4, ChronoUnit.DAYS), 90));
        reportRepository.save(new Report(secondRamp, ReportStatus.STEEP, null, now.minus(9, ChronoUnit.DAYS), 90));
        reportRepository.save(new Report(secondRestroom, ReportStatus.USABLE, null, now.minus(5, ChronoUnit.DAYS), 14));
    }
}
