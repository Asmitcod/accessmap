package in.accessmap.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import in.accessmap.domain.Feature;
import in.accessmap.domain.FeatureType;
import in.accessmap.domain.Place;
import in.accessmap.domain.Report;
import in.accessmap.domain.ReportStatus;
import in.accessmap.domain.AppUser;
import in.accessmap.dto.CreateFeatureRequest;
import in.accessmap.dto.CreateReportRequest;
import in.accessmap.dto.FeatureResponse;
import in.accessmap.dto.PlaceResponse;
import in.accessmap.repository.FeatureRepository;
import in.accessmap.repository.AppUserRepository;
import in.accessmap.repository.PlaceRepository;
import in.accessmap.repository.ReportRepository;

@Service
public class PlaceService {
    private static final int MAX_REPORTS_PER_HOUR = 10;
    private static final int MAX_REPORTS_PER_DAY = 50;
    private static final int DUPLICATE_REPORT_WINDOW_HOURS = 24;
    private static final Set<ReportStatus> ENTRANCE_STATUSES =
            Set.of(ReportStatus.STEP_FREE, ReportStatus.LIMITED, ReportStatus.BLOCKED, ReportStatus.UNKNOWN);
    private static final Set<ReportStatus> LIFT_STATUSES =
            Set.of(ReportStatus.WORKING, ReportStatus.INTERMITTENT, ReportStatus.OUT_OF_SERVICE, ReportStatus.UNKNOWN);
    private static final Set<ReportStatus> RAMP_STATUSES =
            Set.of(ReportStatus.GENTLE, ReportStatus.MODERATE, ReportStatus.STEEP, ReportStatus.VERY_STEEP, ReportStatus.UNKNOWN);
    private static final Set<ReportStatus> RESTROOM_STATUSES =
            Set.of(ReportStatus.USABLE, ReportStatus.LOCKED, ReportStatus.OUT_OF_SERVICE, ReportStatus.UNKNOWN);

    private final PlaceRepository placeRepository;
    private final FeatureRepository featureRepository;
    private final ReportRepository reportRepository;
    private final AppUserRepository userRepository;

    public PlaceService(
            PlaceRepository placeRepository,
            FeatureRepository featureRepository,
            ReportRepository reportRepository,
            AppUserRepository userRepository) {
        this.placeRepository = placeRepository;
        this.featureRepository = featureRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<PlaceResponse> getPlaces(String search) {
        List<Place> places = placeRepository.findAll().stream()
                .filter(place -> search == null || search.isBlank()
                        || (place.getName() + " " + place.getAddress()).toLowerCase().contains(search.trim().toLowerCase()))
                .sorted(Comparator.comparing(Place::getName))
                .toList();

        List<Long> featureIds = places.stream()
                .map(Place::getFeatures)
                .flatMap(Collection::stream)
                .map(Feature::getId)
                .toList();

        Map<Long, List<Report>> reportsByFeature = featureIds.isEmpty()
                ? Map.of()
                : reportRepository.findAllByFeatureIdInOrderByReportedAtDesc(featureIds).stream()
                        .collect(Collectors.groupingBy(report -> report.getFeature().getId()));

        return places.stream()
                .map(place -> toResponse(place, reportsByFeature))
                .toList();
    }

    @Transactional
    public void createReport(Long placeId, Long featureId, CreateReportRequest request, String reporterEmail) {
        Feature feature = featureRepository.findById(featureId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feature not found."));
        if (!feature.getPlace().getId().equals(placeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Feature not found for this place.");
        }
        if (!allowedStatuses(feature.getType()).contains(request.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is not valid for this feature type.");
        }

        AppUser reporter = requireUser(reporterEmail);
        Instant now = Instant.now();
        if (reportRepository.existsByFeature_IdAndUser_IdAndReportedAtGreaterThanEqual(
                featureId, reporter.getId(), now.minus(DUPLICATE_REPORT_WINDOW_HOURS, ChronoUnit.HOURS))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "You have already reported this feature within the last 24 hours.");
        }
        enforceReportRateLimit(reporter, now);

        reportRepository.save(new Report(
                feature,
                reporter,
                request.status(),
                request.note() == null || request.note().isBlank() ? null : request.note().trim(),
                now,
                ttlDays(feature.getType())));
    }

    @Transactional
    public void createFeature(Long placeId, CreateFeatureRequest request, String reporterEmail) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Place not found."));
        if (!allowedStatuses(request.type()).contains(request.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is not valid for this feature type.");
        }

        String label = request.label().trim();
        if (featureRepository.existsByPlace_IdAndTypeAndLabelIgnoreCase(placeId, request.type(), label)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This feature has already been reported for the place.");
        }

        AppUser reporter = requireUser(reporterEmail);
        Instant now = Instant.now();
        enforceReportRateLimit(reporter, now);

        Feature feature = featureRepository.save(new Feature(place, request.type(), label));
        reportRepository.save(new Report(
                feature,
                reporter,
                request.status(),
                request.note() == null || request.note().isBlank() ? null : request.note().trim(),
                now,
                ttlDays(feature.getType())));
    }

    private void enforceReportRateLimit(AppUser reporter, Instant now) {
        if (reportRepository.countByUser_IdAndReportedAtGreaterThanEqual(
                reporter.getId(), now.minus(1, ChronoUnit.HOURS)) >= MAX_REPORTS_PER_HOUR) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "You can submit up to 10 reports per hour.");
        }
        if (reportRepository.countByUser_IdAndReportedAtGreaterThanEqual(
                reporter.getId(), now.minus(24, ChronoUnit.HOURS)) >= MAX_REPORTS_PER_DAY) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "You can submit up to 50 reports per day.");
        }
    }

    private AppUser requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is no longer available."));
    }

    private PlaceResponse toResponse(Place place, Map<Long, List<Report>> reportsByFeature) {
        List<FeatureResponse> features = place.getFeatures().stream()
                .map(feature -> toFeatureResponse(feature, reportsByFeature.getOrDefault(feature.getId(), List.of())))
                .toList();
        return new PlaceResponse(
                place.getId().toString(),
                place.getName(),
                place.getAddress(),
                place.getCategory(),
                place.getLatitude(),
                place.getLongitude(),
                place.isDemoData(),
                features);
    }

    private FeatureResponse toFeatureResponse(Feature feature, List<Report> reports) {
        Instant now = Instant.now();
        List<Report> currentReports = reports.stream()
                .filter(report -> report.getReportedAt().plus(report.getTtlDays(), ChronoUnit.DAYS).isAfter(now))
                .toList();
        Report latest = reports.isEmpty() ? null : reports.getFirst();
        Report current = currentReports.isEmpty() ? null : currentReports.getFirst();

        return new FeatureResponse(
                feature.getId().toString(),
                feature.getType(),
                feature.getLabel(),
                current == null ? ReportStatus.UNKNOWN : current.getStatus(),
                latest == null ? null : latest.getReportedAt(),
                currentReports.size());
    }

    private Set<ReportStatus> allowedStatuses(FeatureType type) {
        return switch (type) {
            case ENTRANCE -> ENTRANCE_STATUSES;
            case LIFT -> LIFT_STATUSES;
            case RAMP -> RAMP_STATUSES;
            case RESTROOM -> RESTROOM_STATUSES;
        };
    }

    private int ttlDays(FeatureType type) {
        return switch (type) {
            case LIFT, RESTROOM -> 14;
            case ENTRANCE, RAMP -> 90;
        };
    }
}
