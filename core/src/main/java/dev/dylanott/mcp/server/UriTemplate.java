package dev.dylanott.mcp.server;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UriTemplate {

    private static final Pattern VARIABLE = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_]*)}");

    private UriTemplate() {
    }

    public static Compiled compile(String template) {
        StringBuilder regex = new StringBuilder("^");
        List<String> names = new ArrayList<>();
        Matcher m = VARIABLE.matcher(template);
        int last = 0;
        while (m.find()) {
            regex.append(Pattern.quote(template.substring(last, m.start())));
            String name = m.group(1);
            names.add(name);
            regex.append("(?<").append(name).append(">[^/]+)");
            last = m.end();
        }
        regex.append(Pattern.quote(template.substring(last)));
        regex.append("$");
        return new Compiled(Pattern.compile(regex.toString()), List.copyOf(names));
    }

    public record Compiled(Pattern pattern, List<String> names) {
    }
}
