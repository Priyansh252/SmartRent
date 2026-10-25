package com.smartrent.web;

import com.smartrent.dto.Dtos.RecommendationRequest;
import com.smartrent.dto.Dtos.RecommendationResponse;
import com.smartrent.service.RecommendationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecommendationController {

    private final RecommendationService recommendations;

    public RecommendationController(RecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    /** Read-only ranking: never reserves anything. Requires a logged-in user (any role). */
    @PostMapping("/api/recommendations")
    public RecommendationResponse recommend(@Valid @RequestBody RecommendationRequest body) {
        return recommendations.recommend(body);
    }
}
