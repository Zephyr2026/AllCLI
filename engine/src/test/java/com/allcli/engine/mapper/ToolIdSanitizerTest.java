package com.allcli.engine.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ToolIdSanitizerTest {

    @Test
    void sanitizesMethodAndPath() {
        assertThat(ToolIdSanitizer.sanitize("GET", "/pets"))
                .isEqualTo("get_pets");
    }

    @Test
    void replacesBracesAndSlashes() {
        // DELETE /pets/{petId} → 去掉大括号/斜杠转下划线
        assertThat(ToolIdSanitizer.sanitize("DELETE", "/pets/{petId}"))
                .isEqualTo("delete_pets_petId");
    }

    @Test
    void collapsesConsecutiveUnderscores() {
        assertThat(ToolIdSanitizer.sanitize("POST", "/a//b"))
                .isEqualTo("post_a_b");
    }

    @Test
    void handlesEmptyInput() {
        assertThat(ToolIdSanitizer.sanitize(null, null)).isEqualTo("tool");
    }
}
