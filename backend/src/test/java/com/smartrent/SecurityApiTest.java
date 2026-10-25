package com.smartrent;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.smartrent.domain.Vehicle;
import com.smartrent.dto.Dtos.BookingDto;
import com.smartrent.dto.Dtos.BookingRequest;
import com.smartrent.security.UserDetailsServiceImpl;
import com.smartrent.service.BookingService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserDetailsServiceImpl userDetails;
    @Autowired TestSupport support;
    @Autowired BookingService bookings;

    private UserDetails who(String email) {
        return userDetails.loadUserByUsername(email);
    }

    @Test
    void publicCatalogueIsOpen() throws Exception {
        mvc.perform(get("/api/vehicles")).andExpect(status().isOk());
        mvc.perform(get("/api/meta")).andExpect(status().isOk());
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/vehicles")).andExpect(status().isOk());
        mvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test
    void protectedEndpointsReturnJson401WhenAnonymous() throws Exception {
        mvc.perform(get("/api/bookings/mine")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/staff/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void rolesAreEnforcedOnStaffAndAdminEndpoints() throws Exception {
        mvc.perform(get("/api/admin/dashboard").with(user(who("customer@smartrent.demo")))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/api/staff/dashboard").with(user(who("customer@smartrent.demo")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/dashboard").with(user(who("employee@smartrent.demo")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/staff/dashboard").with(user(who("employee@smartrent.demo")))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/dashboard").with(user(who("admin@smartrent.demo")))).andExpect(status().isOk());
        mvc.perform(get("/admin").with(user(who("customer@smartrent.demo")))).andExpect(status().isForbidden());
    }

    @Test
    void adminOnlyReportAndPricingEndpointsRejectCustomersAndEmployees() throws Exception {
        for (String email : new String[] {"customer@smartrent.demo", "employee@smartrent.demo"}) {
            mvc.perform(get("/api/admin/reports").with(user(who(email)))).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/pricing/config").with(user(who(email)))).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/audit").with(user(who(email)))).andExpect(status().isForbidden());
        }
    }

    @Test
    void customerCannotReadOrCancelSomeoneElsesBooking() throws Exception {
        Vehicle v = support.newVehicle();
        BookingDto b = bookings.create(support.customer(),
                new BookingRequest(v.getId(), LocalDate.now().plusDays(400 - 40), LocalDate.now().plusDays(400 - 38), null), null);
        UserDetails other = who("arjun.mehta@smartrent.demo");
        mvc.perform(get("/api/bookings/" + b.id()).with(user(other))).andExpect(status().isForbidden());
        mvc.perform(get("/api/bookings/" + b.id() + "/timeline").with(user(other))).andExpect(status().isForbidden());
        mvc.perform(post("/api/bookings/" + b.id() + "/cancel").with(user(other)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/bookings/" + b.id()).with(user(who("customer@smartrent.demo")))).andExpect(status().isOk());
    }

    @Test
    void stateChangingRequestsWithoutCsrfTokenAreRejected() throws Exception {
        String body = "{\"vehicleId\":1,\"pickupDate\":\"2031-01-01\",\"returnDate\":\"2031-01-03\"}";
        mvc.perform(post("/api/bookings").with(user(who("customer@smartrent.demo")))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
    }

    @Test
    void bookingEndpointReturnsStructuredErrorsForBadInput() throws Exception {
        UserDetails c = who("customer@smartrent.demo");
        mvc.perform(post("/api/bookings").with(user(c)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").exists()).andExpect(jsonPath("$.message").exists());
        mvc.perform(post("/api/bookings").with(user(c)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"vehicleId\":999999,\"pickupDate\":\"" + LocalDate.now().plusDays(5) + "\",\"returnDate\":\""
                        + LocalDate.now().plusDays(7) + "\"}")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_FOUND"));
        mvc.perform(get("/api/vehicles/999999")).andExpect(status().isNotFound());
    }

    @Test
    void staffCannotCreateBookingsAsCustomers() throws Exception {
        mvc.perform(post("/api/bookings").with(user(who("employee@smartrent.demo"))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"vehicleId\":1,\"pickupDate\":\"2031-01-01\",\"returnDate\":\"2031-01-03\"}")).andExpect(status().isForbidden());
    }

    @Test
    void registrationCreatesCustomerAndRejectsDuplicateEmail() throws Exception {
        String email = "new" + System.nanoTime() + "@example.test";
        String body = "{\"fullName\":\"New Person\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!x\"}";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is2xxSuccessful());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"X\",\"email\":\"not-an-email\",\"password\":\"1\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }
}
