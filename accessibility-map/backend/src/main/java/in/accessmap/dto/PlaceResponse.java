package in.accessmap.dto;

import java.util.List;

public record PlaceResponse(
        String id,
        String name,
        String address,
        String category,
        double latitude,
        double longitude,
        boolean demoData,
        List<FeatureResponse> features) {
}
