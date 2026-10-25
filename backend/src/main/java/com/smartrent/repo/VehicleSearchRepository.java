package com.smartrent.repo;

import com.smartrent.domain.Booking;
import com.smartrent.domain.BookingStatus;
import com.smartrent.domain.Vehicle;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/**
 * Dynamic vehicle search built with the JPA Criteria API (CriteriaBuilder).
 * <ul>
 *   <li>Optional predicates are combined at runtime - there is no repository method per filter combination.</li>
 *   <li>Availability is a correlated NOT EXISTS sub-query on bookings, executed by the database using the
 *       half-open overlap rule: existing.pickup &lt; requested.return AND existing.return &gt; requested.pickup.</li>
 *   <li>Sorting and pagination are applied in SQL (setFirstResult/setMaxResults + a separate count query).</li>
 * </ul>
 */
@Repository
public class VehicleSearchRepository {

    /** Whitelist of sortable keys exposed by the API, mapped to entity attributes. */
    public static final Map<String, String> SORT_FIELDS = Map.of(
            "price", "dailyRate",
            "seats", "seats",
            "year", "modelYear",
            "make", "make",
            "mileage", "mileageKm");

    @PersistenceContext
    private EntityManager em;

    public Page<Vehicle> search(VehicleSearchCriteria criteria, int page, int size, String sortKey, boolean ascending) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        // ---- count query ----
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Vehicle> countRoot = countQuery.from(Vehicle.class);
        countQuery.select(cb.count(countRoot))
                .where(buildPredicates(cb, countQuery, countRoot, criteria).toArray(new Predicate[0]));
        long total = em.createQuery(countQuery).getSingleResult();

        // ---- data query (fetch-joins the location to avoid N+1 when rendering cards) ----
        CriteriaQuery<Vehicle> query = cb.createQuery(Vehicle.class);
        Root<Vehicle> root = query.from(Vehicle.class);
        root.fetch("location");
        query.select(root)
                .where(buildPredicates(cb, query, root, criteria).toArray(new Predicate[0]))
                .orderBy(buildOrder(cb, root, sortKey, ascending));

        TypedQuery<Vehicle> typed = em.createQuery(query);
        typed.setFirstResult(page * size);
        typed.setMaxResults(size);
        List<Vehicle> content = typed.getResultList();

        return new PageImpl<>(content, PageRequest.of(page, size), total);
    }

    /** Unpaged-style variant used by the recommendation engine; capped by {@code limit}. */
    public List<Vehicle> findCandidates(VehicleSearchCriteria criteria, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Vehicle> query = cb.createQuery(Vehicle.class);
        Root<Vehicle> root = query.from(Vehicle.class);
        root.fetch("location");
        query.select(root)
                .where(buildPredicates(cb, query, root, criteria).toArray(new Predicate[0]))
                .orderBy(cb.asc(root.get("dailyRate")), cb.asc(root.get("id")));
        return em.createQuery(query).setMaxResults(limit).getResultList();
    }

    private List<Predicate> buildPredicates(CriteriaBuilder cb, CriteriaQuery<?> query, Root<Vehicle> v,
                                            VehicleSearchCriteria c) {
        List<Predicate> predicates = new ArrayList<>();

        if (c.status() != null) {
            predicates.add(cb.equal(v.get("status"), c.status()));
        }
        if (c.minPrice() != null) {
            predicates.add(cb.greaterThanOrEqualTo(v.<BigDecimal>get("dailyRate"), c.minPrice()));
        }
        if (c.maxPrice() != null) {
            predicates.add(cb.lessThanOrEqualTo(v.<BigDecimal>get("dailyRate"), c.maxPrice()));
        }
        if (c.type() != null) {
            predicates.add(cb.equal(v.get("vehicleType"), c.type()));
        }
        if (c.minSeats() != null) {
            predicates.add(cb.greaterThanOrEqualTo(v.<Integer>get("seats"), c.minSeats()));
        }
        if (c.locationId() != null) {
            predicates.add(cb.equal(v.get("location").get("id"), c.locationId()));
        }
        if (c.fuelType() != null) {
            predicates.add(cb.equal(v.get("fuelType"), c.fuelType()));
        }
        if (c.transmission() != null) {
            predicates.add(cb.equal(v.get("transmission"), c.transmission()));
        }
        if (c.hasDates()) {
            // NOT EXISTS (select 1 from bookings b where b.vehicle = v and b.status in (CONFIRMED, PICKED_UP)
            //             and b.pickup < :return and b.return > :pickup)
            Subquery<Long> conflicts = query.subquery(Long.class);
            Root<Booking> b = conflicts.from(Booking.class);
            conflicts.select(b.<Long>get("id")).where(
                    cb.equal(b.get("vehicle"), v),
                    b.get("status").in(BookingStatus.BLOCKING),
                    cb.lessThan(b.<LocalDate>get("pickupDate"), c.returnDate()),
                    cb.greaterThan(b.<LocalDate>get("returnDate"), c.pickupDate()));
            predicates.add(cb.not(cb.exists(conflicts)));
        }
        return predicates;
    }

    private List<Order> buildOrder(CriteriaBuilder cb, Root<Vehicle> root, String sortKey, boolean ascending) {
        String attribute = SORT_FIELDS.getOrDefault(sortKey == null ? "price" : sortKey, "dailyRate");
        List<Order> orders = new ArrayList<>();
        orders.add(ascending ? cb.asc(root.get(attribute)) : cb.desc(root.get(attribute)));
        orders.add(cb.asc(root.get("id")));   // deterministic paging
        return orders;
    }
}
