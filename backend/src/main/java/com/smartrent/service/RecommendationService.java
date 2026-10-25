package com.smartrent.service;

import com.smartrent.config.AppProperties;
import com.smartrent.domain.BookingStatus;
import com.smartrent.domain.Location;
import com.smartrent.domain.Vehicle;
import com.smartrent.domain.VehicleStatus;
import com.smartrent.dto.Dtos.PriceBreakdown;
import com.smartrent.dto.Dtos.RecommendationItem;
import com.smartrent.dto.Dtos.RecommendationRequest;
import com.smartrent.dto.Dtos.RecommendationResponse;
import com.smartrent.repo.BookingRepository;
import com.smartrent.repo.LocationRepository;
import com.smartrent.repo.VehicleRepository;
import com.smartrent.repo.VehicleSearchCriteria;
import com.smartrent.repo.VehicleSearchRepository;
import com.smartrent.web.error.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Explainable, deterministic, rule-based ranking. It is READ-ONLY: it never creates or holds a reservation. A customer
 * who picks a recommendation goes through the normal {@link BookingService#create}, which re-checks availability under
 * a row lock and re-prices the stay (and rejects with PRICE_CHANGED if the quote the customer saw is stale).
 *
 * <pre>
 * Mandatory (vehicle is excluded otherwise): bookable status, free for [pickup, return) (checked in SQL via the
 *   Criteria API), seats >= requested, average daily price <= maxDailyBudget (when given), same location when
 *   sameLocationOnly is set.
 * Score (0-100) = 100 x sum(weight_i x score_i) / sum(weight_i), over the components that apply to the request:
 *   price        1 - 0.5 x avgDaily/budget  (budget given)   |  relative cheapness among the candidates (no budget)
 *   capacity     1.0 for an exact seat match, minus 0.2 per surplus seat (never below 0.2)
 *   location     1 - distanceKm / maxLocationDistanceKm  (haversine; only when a pickup location was requested)
 *   preference   share of the requested type / fuel / transmission preferences that the vehicle satisfies
 *   availability share of this vehicle type that is still free for the dates (scarce types score lower)
 * Ties are broken by lower total price, then lower id.
 * </pre>
 */
@Service
public class RecommendationService {

    private static final int CANDIDATE_LIMIT = 100;

    private final VehicleSearchRepository search;
    private final LocationRepository locations;
    private final BookingRepository bookings;
    private final VehicleRepository vehicles;
    private final PricingService pricing;
    private final BookingService bookingRules;
    private final AppProperties props;

    public RecommendationService(VehicleSearchRepository search, LocationRepository locations, BookingRepository bookings,
                                 VehicleRepository vehicles, PricingService pricing, BookingService bookingRules,
                                 AppProperties props) {
        this.search = search;
        this.locations = locations;
        this.bookings = bookings;
        this.vehicles = vehicles;
        this.pricing = pricing;
        this.bookingRules = bookingRules;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recommend(RecommendationRequest req) {
        bookingRules.validateRentalDates(req.pickupDate(), req.returnDate());
        AppProperties.Recommendation w = props.getRecommendation();
        int seats = req.seats() == null ? 1 : req.seats();
        Location wanted = null;
        if (req.locationId() != null) {
            wanted = locations.findById(req.locationId()).orElseThrow(() -> ApiException.notFound("Location", req.locationId()));
        }

        VehicleSearchCriteria criteria = new VehicleSearchCriteria(null, null, req.pickupDate(), req.returnDate(), null,
                seats, req.sameLocationOnly() ? req.locationId() : null, null, null, VehicleStatus.AVAILABLE);
        List<Vehicle> candidates = search.findCandidates(criteria, CANDIDATE_LIMIT);

        record Priced(Vehicle v, PriceBreakdown q) {
        }
        List<Priced> eligible = new ArrayList<>();
        for (Vehicle v : candidates) {
            PriceBreakdown q = pricing.quote(v, req.pickupDate(), req.returnDate());
            if (req.maxDailyBudget() == null || q.averageDailyPrice().compareTo(req.maxDailyBudget()) <= 0) {
                eligible.add(new Priced(v, q));
            }
        }

        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("price", w.getWeightPrice());
        weights.put("capacity", w.getWeightCapacity());
        weights.put("location", w.getWeightLocation());
        weights.put("preference", w.getWeightPreference());
        weights.put("availability", w.getWeightAvailability());

        if (eligible.isEmpty()) {
            return new RecommendationResponse(List.of(), candidates.size(), noMatchMessage(req, seats, candidates.size()), weights);
        }

        BigDecimal min = eligible.stream().map(p -> p.q().averageDailyPrice()).min(Comparator.naturalOrder()).orElseThrow();
        BigDecimal max = eligible.stream().map(p -> p.q().averageDailyPrice()).max(Comparator.naturalOrder()).orElseThrow();
        boolean hasPreference = req.type() != null || req.preferredFuel() != null || req.preferredTransmission() != null;

        List<RecommendationItem> scored = new ArrayList<>();
        for (Priced p : eligible) {
            Vehicle v = p.v();
            PriceBreakdown q = p.q();
            Map<String, Double> parts = new LinkedHashMap<>();
            List<String> reasons = new ArrayList<>();
            double weightSum = 0;
            double total = 0;

            // price
            double priceScore;
            if (req.maxDailyBudget() != null) {
                priceScore = 1.0 - 0.5 * ratio(q.averageDailyPrice(), req.maxDailyBudget());
                reasons.add("Average " + q.currency() + " " + q.averageDailyPrice() + "/day fits your budget of "
                        + q.currency() + " " + req.maxDailyBudget() + "/day.");
            } else {
                priceScore = max.compareTo(min) == 0 ? 1.0 : 1.0 - ratio(q.averageDailyPrice().subtract(min), max.subtract(min));
                reasons.add("Average " + q.currency() + " " + q.averageDailyPrice() + "/day"
                        + (priceScore >= 0.99 ? " is the lowest among the available options." : "."));
            }
            parts.put("price", round2(priceScore));
            total += w.getWeightPrice() * priceScore;
            weightSum += w.getWeightPrice();

            // capacity
            int surplus = v.getSeats() - seats;
            double capacityScore = surplus <= 0 ? 1.0 : Math.max(0.2, 1.0 - 0.2 * surplus);
            reasons.add(surplus == 0 ? "Seats exactly the " + seats + " people you need."
                    : "Seats " + v.getSeats() + " (needs " + seats + ")" + (surplus > 2 ? " - more room than necessary." : "."));
            parts.put("capacity", round2(capacityScore));
            total += w.getWeightCapacity() * capacityScore;
            weightSum += w.getWeightCapacity();

            // location
            Double distance = null;
            if (wanted != null) {
                distance = haversineKm(wanted.getLatitude(), wanted.getLongitude(),
                        v.getLocation().getLatitude(), v.getLocation().getLongitude());
                double locScore = Math.max(0.0, 1.0 - distance / w.getMaxLocationDistanceKm());
                parts.put("location", round2(locScore));
                total += w.getWeightLocation() * locScore;
                weightSum += w.getWeightLocation();
                reasons.add(distance < 1.0 ? "Available at your chosen pickup location (" + v.getLocation().getName() + ")."
                        : "Located at " + v.getLocation().getName() + ", about " + Math.round(distance) + " km from your chosen location.");
            } else {
                reasons.add("Pickup location: " + v.getLocation().getName() + ".");
            }

            // preference
            if (hasPreference) {
                int asked = 0;
                int met = 0;
                List<String> matched = new ArrayList<>();
                if (req.type() != null) {
                    asked++;
                    if (v.getVehicleType() == req.type()) {
                        met++;
                        matched.add(req.type().name().toLowerCase());
                    }
                }
                if (req.preferredFuel() != null) {
                    asked++;
                    if (v.getFuelType() == req.preferredFuel()) {
                        met++;
                        matched.add(req.preferredFuel().name().toLowerCase());
                    }
                }
                if (req.preferredTransmission() != null) {
                    asked++;
                    if (v.getTransmission() == req.preferredTransmission()) {
                        met++;
                        matched.add(req.preferredTransmission().name().toLowerCase());
                    }
                }
                double prefScore = (double) met / asked;
                parts.put("preference", round2(prefScore));
                total += w.getWeightPreference() * prefScore;
                weightSum += w.getWeightPreference();
                reasons.add(met == asked ? "Matches all your preferences (" + String.join(", ", matched) + ")."
                        : met == 0 ? "Does not match your type/fuel/transmission preferences."
                        : "Matches " + met + " of " + asked + " preferences (" + String.join(", ", matched) + ").");
            }

            // availability (scarcity of this vehicle type for the requested dates)
            long totalOfType = vehicles.countBookableByType(v.getVehicleType());
            long blocked = bookings.countBlockedVehiclesOfType(v.getVehicleType(), req.pickupDate(), req.returnDate(),
                    BookingStatus.BLOCKING);
            double free = totalOfType == 0 ? 1.0 : (double) (totalOfType - blocked) / totalOfType;
            parts.put("availability", round2(free));
            total += w.getWeightAvailability() * free;
            weightSum += w.getWeightAvailability();
            reasons.add("Free for " + req.pickupDate() + " to " + req.returnDate() + "; "
                    + Math.round(free * 100) + "% of " + v.getVehicleType().name().toLowerCase() + " vehicles are still free.");
            if (q.multiplierClamped() || q.appliedMultiplier().compareTo(BigDecimal.ONE) != 0) {
                reasons.add("Dynamic price multiplier " + q.appliedMultiplier().toPlainString() + " applied (total "
                        + q.currency() + " " + q.totalPrice() + " for " + q.days() + " days).");
            }

            double score = Math.round(1000.0 * total / weightSum) / 10.0;
            scored.add(new RecommendationItem(0, score, Mappers.toDto(v), q.averageDailyPrice(), q.totalPrice(),
                    distance == null ? null : Math.round(distance * 10.0) / 10.0, parts, reasons, q));
        }

        scored.sort(Comparator.comparingDouble(RecommendationItem::score).reversed()
                .thenComparing(RecommendationItem::totalPrice)
                .thenComparing(i -> i.vehicle().id()));
        int limit = Math.min(props.getRecommendation().getMaxResults(), scored.size());
        List<RecommendationItem> top = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            RecommendationItem s = scored.get(i);
            top.add(new RecommendationItem(i + 1, s.score(), s.vehicle(), s.averageDailyPrice(), s.totalPrice(),
                    s.distanceKm(), s.componentScores(), s.reasons(), s.priceBreakdown()));
        }
        String msg = eligible.size() + " vehicle(s) satisfy all mandatory requirements; showing the top " + top.size()
                + ". Recommendations are not reservations - book one to secure it.";
        return new RecommendationResponse(top, candidates.size(), msg, weights);
    }

    private static String noMatchMessage(RecommendationRequest req, int seats, int available) {
        StringBuilder sb = new StringBuilder("No vehicle satisfies all mandatory requirements: available ")
                .append(req.pickupDate()).append(" to ").append(req.returnDate())
                .append(", at least ").append(seats).append(" seat(s)");
        if (req.maxDailyBudget() != null) {
            sb.append(", average price within ").append(req.maxDailyBudget()).append(" per day");
        }
        if (req.sameLocationOnly()) {
            sb.append(", at the selected location");
        }
        sb.append(available == 0 ? ". Nothing is free for those dates/seats - try other dates or fewer seats."
                : ". " + available + " vehicle(s) are free but none fit the budget - try a higher budget.");
        return sb.toString();
    }

    private static double ratio(BigDecimal a, BigDecimal b) {
        if (b.signum() == 0) {
            return 0;
        }
        return Math.min(1.0, a.divide(b, 4, RoundingMode.HALF_UP).doubleValue());
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }
}
