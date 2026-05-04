package dev.dylanott.mcp.server;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;

import static org.assertj.core.api.Assertions.assertThat;

class UriTemplateTest {

    @Test
    void compilesSingleVariable() {
        UriTemplate.Compiled c = UriTemplate.compile("db://customers/{region}");
        assertThat(c.names()).containsExactly("region");
        Matcher m = c.pattern().matcher("db://customers/CN-SOUTH");
        assertThat(m.matches()).isTrue();
        assertThat(m.group("region")).isEqualTo("CN-SOUTH");
    }

    @Test
    void compilesMultipleVariables() {
        UriTemplate.Compiled c = UriTemplate.compile("db://customers/{id}/invoices/{type}");
        Matcher m = c.pattern().matcher("db://customers/42/invoices/paid");
        assertThat(m.matches()).isTrue();
        assertThat(m.group("id")).isEqualTo("42");
        assertThat(m.group("type")).isEqualTo("paid");
    }

    @Test
    void rejectsNonMatchingUri() {
        UriTemplate.Compiled c = UriTemplate.compile("db://customers/{region}");
        assertThat(c.pattern().matcher("db://invoices/x").matches()).isFalse();
        assertThat(c.pattern().matcher("db://customers/x/y").matches()).isFalse();
    }

    @Test
    void escapesRegexMetacharacters() {
        UriTemplate.Compiled c = UriTemplate.compile("file:///etc/{name}.conf");
        Matcher m = c.pattern().matcher("file:///etc/hosts.conf");
        assertThat(m.matches()).isTrue();
        assertThat(m.group("name")).isEqualTo("hosts");
    }
}
