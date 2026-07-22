package com.vs.vsaiagent.capability.governance;

import java.util.Collections;
import java.util.List;

public record CapabilityEvaluationContract(
        String profile,
        List<String> successCriteria,
        List<String> hardConstraints,
        List<String> goldenCaseTags,
        List<String> attributionStages
) {
    public CapabilityEvaluationContract {
        successCriteria = successCriteria == null ? Collections.emptyList() : List.copyOf(successCriteria);
        hardConstraints = hardConstraints == null ? Collections.emptyList() : List.copyOf(hardConstraints);
        goldenCaseTags = goldenCaseTags == null ? Collections.emptyList() : List.copyOf(goldenCaseTags);
        attributionStages = attributionStages == null ? Collections.emptyList() : List.copyOf(attributionStages);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String profile;
        private List<String> successCriteria;
        private List<String> hardConstraints;
        private List<String> goldenCaseTags;
        private List<String> attributionStages;

        public Builder profile(String v) { this.profile = v; return this; }
        public Builder successCriteria(List<String> v) { this.successCriteria = v; return this; }
        public Builder hardConstraints(List<String> v) { this.hardConstraints = v; return this; }
        public Builder goldenCaseTags(List<String> v) { this.goldenCaseTags = v; return this; }
        public Builder attributionStages(List<String> v) { this.attributionStages = v; return this; }

        public CapabilityEvaluationContract build() {
            return new CapabilityEvaluationContract(profile, successCriteria, hardConstraints,
                    goldenCaseTags, attributionStages);
        }
    }
}

