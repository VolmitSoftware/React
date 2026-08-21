package art.arcane.react.content.sampler;

import art.arcane.react.React;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SamplerIncidentScoreContributorsTest {

    @Test
    void contributionsReturnsEightEntries() {
        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
             MockedStatic<React> react = Mockito.mockStatic(React.class)) {
            SamplerIncidentScore score = new SamplerIncidentScore();
            List<SamplerIncidentScore.Contribution> contribs = score.contributions();
            assertEquals(8, contribs.size());
        }
    }

    @Test
    void contributionsWeightsMatchExpectedOrderAndValues() {
        double[] expectedWeights = {0.30, 0.15, 0.10, 0.12, 0.08, 0.10, 0.08, 0.07};

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
             MockedStatic<React> react = Mockito.mockStatic(React.class)) {
            SamplerIncidentScore score = new SamplerIncidentScore();
            List<SamplerIncidentScore.Contribution> contribs = score.contributions();

            for (int i = 0; i < expectedWeights.length; i++) {
                assertEquals(expectedWeights[i], contribs.get(i).weight(), 1e-9,
                        "weight at index " + i + " must be " + expectedWeights[i]);
            }
        }
    }

    @Test
    void contributionWeightsSumToOne() {
        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
             MockedStatic<React> react = Mockito.mockStatic(React.class)) {
            SamplerIncidentScore score = new SamplerIncidentScore();
            List<SamplerIncidentScore.Contribution> contribs = score.contributions();

            double sum = contribs.stream().mapToDouble(SamplerIncidentScore.Contribution::weight).sum();
            assertEquals(1.0, sum, 1e-9, "sum of all weights must equal 1.0");
        }
    }

    @Test
    void contributionsNamesAreNonBlankAndValuesAreFinite() {
        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
             MockedStatic<React> react = Mockito.mockStatic(React.class)) {
            SamplerIncidentScore score = new SamplerIncidentScore();
            List<SamplerIncidentScore.Contribution> contribs = score.contributions();

            for (SamplerIncidentScore.Contribution c : contribs) {
                assertFalse(c.name().isBlank(), "contribution name must not be blank");
                assertTrue(Double.isFinite(c.value()), "contribution value must be finite: " + c.value());
            }
        }
    }
}
