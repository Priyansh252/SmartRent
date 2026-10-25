package com.smartrent;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartrent.domain.Vehicle;
import com.smartrent.domain.VehicleType;
import com.smartrent.dto.Dtos.BookingRequest;
import com.smartrent.dto.Dtos.RecommendationItem;
import com.smartrent.dto.Dtos.RecommendationRequest;
import com.smartrent.dto.Dtos.RecommendationResponse;
import com.smartrent.repo.BookingRepository;
import com.smartrent.repo.VehicleSearchCriteria;
import com.smartrent.repo.VehicleSearchRepository;
import com.smartrent.service.BookingService;
import com.smartrent.service.RecommendationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;

@SpringBootTest
class SearchAndRecommendationTest {

    @Autowired VehicleSearchRepository search;
    @Autowired RecommendationService recommendations;
    @Autowired BookingService bookings;
    @Autowired BookingRepository bookingRepo;
    @Autowired TestSupport support;

    private static final LocalDate BASE = LocalDate.now().plusDays(300);

    private VehicleSearchCriteria criteria(BigDecimal min, BigDecimal max, LocalDate from, LocalDate to, VehicleType type, Integer seats) {
        return new VehicleSearchCriteria(min, max, from, to, type, seats, null, null, null, null);
    }

    private List<Long> ids(Page<Vehicle> p) {
        return p.getContent().stream().map(Vehicle::getId).collect(Collectors.toList());
    }

    @Test
    void priceRangeTypeAndSeatFiltersAreAppliedInTheDatabase() {
        Vehicle cheap = support.newVehicle(VehicleType.MPV, 7, "777.00");
        Vehicle pricey = support.newVehicle(VehicleType.MPV, 7, "999999.00");
        Page<Vehicle> p = search.search(criteria(new BigDecimal("700"), new BigDecimal("800"), null, null, VehicleType.MPV, 7), 0, 50, "price", true);
        assertThat(ids(p)).contains(cheap.getId()).doesNotContain(pricey.getId());
        assertThat(p.getContent()).allMatch(v -> v.getVehicleType() == VehicleType.MPV && v.getSeats() >= 7
                && v.getDailyRate().compareTo(new BigDecimal("700")) >= 0 && v.getDailyRate().compareTo(new BigDecimal("800")) <= 0);
    }

    @Test
    void availabilityFilterExcludesBookedVehiclesButHonoursHalfOpenIntervals() {
        Vehicle v = support.newVehicle(VehicleType.LUXURY, 2, "54321.00");
        bookings.create(support.customer(), new BookingRequest(v.getId(), BASE, BASE.plusDays(3), null), null);
        var base = criteria(new BigDecimal("54321"), new BigDecimal("54321"), null, null, null, null);
        assertThat(ids(search.search(base, 0, 10, "price", true))).contains(v.getId()); // no dates -> not filtered

        assertThat(ids(search.search(criteria(new BigDecimal("54321"), new BigDecimal("54321"), BASE.plusDays(1), BASE.plusDays(2), null, null), 0, 10, "price", true)))
                .doesNotContain(v.getId());
        assertThat(ids(search.search(criteria(new BigDecimal("54321"), new BigDecimal("54321"), BASE.plusDays(3), BASE.plusDays(5), null, null), 0, 10, "price", true)))
                .contains(v.getId()); // pickup on the previous return day
        assertThat(ids(search.search(criteria(new BigDecimal("54321"), new BigDecimal("54321"), BASE.minusDays(2), BASE, null, null), 0, 10, "price", true)))
                .contains(v.getId()); // return on the existing pickup day
    }

    @Test
    void sortingAndPaginationWorkAndReportTotals() {
        for (int i = 0; i < 3; i++) support.newVehicle(VehicleType.HATCHBACK, 4, String.valueOf(11111 + i));
        var c = criteria(new BigDecimal("11111"), new BigDecimal("11113"), null, null, VehicleType.HATCHBACK, null);
        Page<Vehicle> asc = search.search(c, 0, 2, "price", true);
        Page<Vehicle> desc = search.search(c, 0, 2, "price", false);
        assertThat(asc.getTotalElements()).isEqualTo(3);
        assertThat(asc.getTotalPages()).isEqualTo(2);
        assertThat(asc.getContent()).hasSize(2);
        assertThat(asc.getContent().get(0).getDailyRate()).isLessThan(asc.getContent().get(1).getDailyRate());
        assertThat(desc.getContent().get(0).getDailyRate()).isGreaterThan(desc.getContent().get(1).getDailyRate());
        assertThat(search.search(c, 1, 2, "price", true).getContent()).hasSize(1);
    }

    @Test
    void unknownSortKeyFallsBackToPriceInsteadOfInjectingAnAttribute() {
        support.newVehicle();
        assertThat(search.search(criteria(null, null, null, null, null, null), 0, 5, "password; drop table", true).getContent()).isNotEmpty();
    }

    @Test
    void recommendationsAreRankedExplainedAndRespectMandatoryFilters() {
        Vehicle fits = support.newVehicle(VehicleType.SUV, 6, "3000.00");
        Vehicle tooSmall = support.newVehicle(VehicleType.SUV, 2, "3000.00");
        Vehicle overBudget = support.newVehicle(VehicleType.SUV, 6, "90000.00");
        RecommendationResponse r = recommendations.recommend(new RecommendationRequest(BASE.plusDays(40), BASE.plusDays(43),
                new BigDecimal("5000"), 5, VehicleType.SUV, null, false, null, null));
        List<Long> ids = r.recommendations().stream().map(i -> i.vehicle().id()).toList();
        assertThat(ids).contains(fits.getId()).doesNotContain(tooSmall.getId(), overBudget.getId());
        for (int i = 0; i < r.recommendations().size(); i++) {
            RecommendationItem it = r.recommendations().get(i);
            assertThat(it.rank()).isEqualTo(i + 1);
            assertThat(it.reasons()).isNotEmpty();
            assertThat(it.priceBreakdown()).isNotNull();
            if (i > 0) assertThat(it.score()).isLessThanOrEqualTo(r.recommendations().get(i - 1).score());
        }
    }

    @Test
    void recommendationsNeverCreateBookingsAndSkipBookedVehicles() {
        Vehicle booked = support.newVehicle(VehicleType.ELECTRIC, 5, "4000.00");
        bookings.create(support.customer(), new BookingRequest(booked.getId(), BASE.plusDays(60), BASE.plusDays(63), null), null);
        long before = bookingRepo.count();
        RecommendationResponse r = recommendations.recommend(new RecommendationRequest(BASE.plusDays(61), BASE.plusDays(62),
                null, 4, VehicleType.ELECTRIC, null, false, null, null));
        assertThat(r.recommendations().stream().map(i -> i.vehicle().id())).doesNotContain(booked.getId());
        assertThat(bookingRepo.count()).isEqualTo(before);
    }
}
