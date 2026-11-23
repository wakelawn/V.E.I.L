package com.veil.ai;

import com.veil.core.database.DatabaseAdapter;
import com.veil.parser.Parser;
import com.veil.parser.SemanticValidationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AiResponseValidatorTest {

    @Autowired
    private AiResponseValidator validator;

    @Test
    void validRecommendationPasses() {
        AiRecommendation rec = new AiRecommendation(
                "test",
                "nvidia-nim",
                "test-model",
                "Summary",
                List.of("Bottleneck 1"),
                List.of(new AiRecommendation.Finding("Title", "Message", "high", "CREATE INDEX idx ON t (c);")),
                "~2x faster"
        );

        AiResponseValidator.ValidationResult result = validator.validateRecommendation(rec, null);
        assertTrue(result.isValid());
    }

    @Test
    void missingTitleFails() {
        AiRecommendation.Finding f = new AiRecommendation.Finding("", "Message", "high", "CREATE INDEX idx ON t (c);");
        AiRecommendation rec = new AiRecommendation("test", "nvidia-nim", "model", "Summary", List.of(), List.of(f), "");

        AiResponseValidator.ValidationResult result = validator.validateRecommendation(rec, null);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("title")));
    }

    @Test
    void invalidSeverityWarns() {
        AiRecommendation.Finding f = new AiRecommendation.Finding("Title", "Message", "critical", "CREATE INDEX idx ON t (c);");
        AiRecommendation rec = new AiRecommendation("test", "nvidia-nim", "model", "Summary", List.of(), List.of(f), "");

        AiResponseValidator.ValidationResult result = validator.validateRecommendation(rec, null);
        assertTrue(result.isValid());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("invalid severity")));
    }

    @Test
    void dangerousDdlRejected() {
        AiRecommendation.Finding f = new AiRecommendation.Finding("Title", "Message", "high", "DROP TABLE users;");
        AiRecommendation rec = new AiRecommendation("test", "nvidia-nim", "model", "Summary", List.of(), List.of(f), "");

        AiResponseValidator.ValidationResult result = validator.validateRecommendation(rec, null);
        assertFalse(result.isValid());
    }

    @Test
    void unrealisticImprovementRejected() {
        AiRecommendation rec = new AiRecommendation(
                "test", "nvidia-nim", "model", "Summary", List.of(),
                List.of(new AiRecommendation.Finding("Title", "Message", "high", "CREATE INDEX idx ON t (c);")),
                "1000% faster"
        );

        AiResponseValidator.ValidationResult result = validator.validateRecommendation(rec, null);
        assertFalse(result.isValid());
    }

    @Test
    void emptyResponseRejected() {
        AiResponseValidator.ValidationResult result = validator.validateChatResponse("");
        assertFalse(result.isValid());
    }
}