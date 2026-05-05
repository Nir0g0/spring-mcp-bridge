package dev.dylanott.mcp.example.tools;

import dev.dylanott.mcp.annotation.MCPParam;
import dev.dylanott.mcp.annotation.MCPTool;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
public class TimeTool {

    private final Clock clock;

    public TimeTool(Clock clock) {
        this.clock = clock;
    }

    @MCPTool(name = "time.now", description = "Return the current time in a given IANA timezone",
            roles = {"reader"})
    public Map<String, String> now(
            @MCPParam(description = "IANA timezone, e.g. Asia/Shanghai") String zone) {
        ZoneId zoneId = (zone == null || zone.isEmpty()) ? ZoneId.of("UTC") : ZoneId.of(zone);
        Instant instant = Instant.now(clock);
        return Map.of(
                "zone", zoneId.toString(),
                "iso", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instant.atZone(zoneId)),
                "epochMillis", String.valueOf(instant.toEpochMilli())
        );
    }
}
