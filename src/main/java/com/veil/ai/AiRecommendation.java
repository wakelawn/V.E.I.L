package com.veil.ai;

import java.util.List;

// The recommendation shape the frontend already renders. {@code source} tells the UI which badge to show.
public record AiRecommendation(
        String source,
        String provider,
        String model,
        String summary,
        List<String> bottlenecks,
        List<Finding> findings,
        String estimatedImprovement
) {

    public record Finding(
            String title,
            String message,
            String severity,
            String suggestionSql
    ) {
    }
}
