package com.allcli.engine.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import org.junit.jupiter.api.Test;

/**
 * InferenceRule 单测：覆盖 GET / POST / PUT / PATCH / DELETE 五种方法。
 */
class InferenceRuleTest {

    @Test
    void getIsReadonlyWithoutHitl() {
        InferenceRule.Inference r = InferenceRule.infer("GET", "alice");
        assertThat(r.getSideEffect()).isEqualTo("readonly");
        assertThat(r.getReversibility()).isEqualTo("readonly");
        assertThat(r.getHitl()).isNull();
    }

    @Test
    void postIsWriteReversibleWithHitl() {
        InferenceRule.Inference r = InferenceRule.infer("POST", "alice");
        assertThat(r.getSideEffect()).isEqualTo("write");
        assertThat(r.getReversibility()).isEqualTo("reversible");
        assertThat(r.getHitl()).containsExactly(
                entry("enabled", true),
                entry("approverType", "owner"),
                entry("approverRef", "alice"),
                entry("timeoutSec", 600));
    }

    @Test
    void putAndPatchAreWriteReversible() {
        for (String m : new String[]{"PUT", "PATCH"}) {
            InferenceRule.Inference r = InferenceRule.infer(m, "bob");
            assertThat(r.getSideEffect()).isEqualTo("write");
            assertThat(r.getReversibility()).isEqualTo("reversible");
            assertThat(r.getHitl()).isNotNull();
        }
    }

    @Test
    void deleteIsWriteIrreversible() {
        InferenceRule.Inference r = InferenceRule.infer("DELETE", "alice");
        assertThat(r.getSideEffect()).isEqualTo("write");
        assertThat(r.getReversibility()).isEqualTo("irreversible");
        assertThat(r.getHitl()).isNotNull();
    }

    @Test
    void methodIsCaseInsensitive() {
        assertThat(InferenceRule.infer("get", "x").getSideEffect()).isEqualTo("readonly");
        assertThat(InferenceRule.infer("Delete", "x").getReversibility()).isEqualTo("irreversible");
    }
}
