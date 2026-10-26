package in.accessmap.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import in.accessmap.dto.CreateReportRequest;
import in.accessmap.dto.CreateFeatureRequest;
import in.accessmap.dto.PlaceResponse;
import in.accessmap.service.PlaceService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class PlaceController {
    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    @GetMapping("/places")
    public List<PlaceResponse> getPlaces(@RequestParam(required = false) String search) {
        return placeService.getPlaces(search);
    }

    @PostMapping("/places/{placeId}/features")
    @ResponseStatus(HttpStatus.CREATED)
    public void createFeature(
            @PathVariable Long placeId,
            @AuthenticationPrincipal String reporterEmail,
            @Valid @RequestBody CreateFeatureRequest request) {
        placeService.createFeature(placeId, request, reporterEmail);
    }

    @PostMapping("/places/{placeId}/features/{featureId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public void createReport(
            @PathVariable Long placeId,
            @PathVariable Long featureId,
            @AuthenticationPrincipal String reporterEmail,
            @Valid @RequestBody CreateReportRequest request) {
        placeService.createReport(placeId, featureId, request, reporterEmail);
    }
}
