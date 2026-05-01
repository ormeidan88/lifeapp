package app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

public class RecurrenceExpander {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, DayOfWeek> DAY_MAP = Map.of(
            "MON", DayOfWeek.MONDAY, "TUE", DayOfWeek.TUESDAY, "WED", DayOfWeek.WEDNESDAY,
            "THU", DayOfWeek.THURSDAY, "FRI", DayOfWeek.FRIDAY, "SAT", DayOfWeek.SATURDAY,
            "SUN", DayOfWeek.SUNDAY
    );

    public static List<String> expand(String ruleJson, String startDateStr, String rangeFrom, String rangeTo) {
        if (ruleJson == null || ruleJson.isBlank()) return List.of();
        try {
            JsonNode rule = MAPPER.readTree(ruleJson);
            String freq = rule.path("freq").asText("");
            int interval = rule.has("interval") ? rule.path("interval").asInt(1) : 1;
            if (interval < 1) interval = 1;
            String endDateStr = rule.has("endDate") && !rule.path("endDate").isNull() ? rule.path("endDate").asText() : null;

            LocalDate start = LocalDate.parse(startDateStr);
            LocalDate from = LocalDate.parse(rangeFrom);
            LocalDate to = LocalDate.parse(rangeTo);
            LocalDate end = endDateStr != null ? LocalDate.parse(endDateStr) : to;
            LocalDate limit = end.isBefore(to) ? end : to;

            return switch (freq) {
                case "DAILY" -> expandDaily(start, interval, from, limit);
                case "WEEKLY" -> expandWeekly(start, interval, rule, from, limit);
                case "MONTHLY" -> expandMonthly(start, interval, rule, from, limit);
                case "CUSTOM" -> expandCustom(start, interval, rule, from, limit);
                default -> List.of();
            };
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<String> expandDaily(LocalDate start, int interval, LocalDate from, LocalDate limit) {
        List<String> dates = new ArrayList<>();
        // Jump to first occurrence >= from
        LocalDate d = start;
        if (d.isBefore(from)) {
            long daysBetween = from.toEpochDay() - d.toEpochDay();
            long skip = (daysBetween / interval) * interval;
            d = d.plusDays(skip);
            if (d.isBefore(from)) d = d.plusDays(interval);
        }
        while (!d.isAfter(limit)) {
            dates.add(d.toString());
            d = d.plusDays(interval);
        }
        return dates;
    }

    private static List<String> expandWeekly(LocalDate start, int interval, JsonNode rule, LocalDate from, LocalDate limit) {
        List<String> dates = new ArrayList<>();
        Set<DayOfWeek> days = new java.util.HashSet<>();
        if (rule.has("daysOfWeek") && rule.get("daysOfWeek").isArray()) {
            for (JsonNode d : rule.get("daysOfWeek")) {
                DayOfWeek dow = DAY_MAP.get(d.asText().toUpperCase());
                if (dow != null) days.add(dow);
            }
        }
        if (days.isEmpty()) days.add(start.getDayOfWeek());

        // Iterate day by day from start (or from, whichever is later) to limit
        LocalDate cursor = start.isBefore(from) ? from : start;
        // For interval > 1, we need to check which week number relative to start
        long startWeekEpoch = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay();

        while (!cursor.isAfter(limit)) {
            if (days.contains(cursor.getDayOfWeek())) {
                // Check if this week is a valid interval week
                long cursorWeekEpoch = cursor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay();
                long weeksDiff = (cursorWeekEpoch - startWeekEpoch) / 7;
                if (weeksDiff % interval == 0) {
                    dates.add(cursor.toString());
                }
            }
            cursor = cursor.plusDays(1);
        }
        return dates;
    }

    private static List<String> expandMonthly(LocalDate start, int interval, JsonNode rule, LocalDate from, LocalDate limit) {
        String monthlyType = rule.path("monthlyType").asText("DATE");
        if ("WEEKDAY".equals(monthlyType)) {
            return expandMonthlyWeekday(start, interval, from, limit);
        }
        return expandMonthlyDate(start, interval, from, limit);
    }

    private static List<String> expandMonthlyDate(LocalDate start, int interval, LocalDate from, LocalDate limit) {
        List<String> dates = new ArrayList<>();
        int dayOfMonth = start.getDayOfMonth();
        LocalDate d = start;
        // Jump ahead
        if (d.isBefore(from)) {
            long monthsBetween = (from.getYear() - d.getYear()) * 12L + (from.getMonthValue() - d.getMonthValue());
            long skip = (monthsBetween / interval) * interval;
            d = start.plusMonths(skip);
            if (d.isBefore(from)) d = d.plusMonths(interval);
        }
        for (int i = 0; i < 500 && !d.isAfter(limit); i++) {
            // Handle months with fewer days
            LocalDate candidate = d.withDayOfMonth(Math.min(dayOfMonth, d.lengthOfMonth()));
            if (!candidate.isBefore(from) && !candidate.isAfter(limit)) {
                dates.add(candidate.toString());
            }
            d = d.plusMonths(interval);
        }
        return dates;
    }

    private static List<String> expandMonthlyWeekday(LocalDate start, int interval, LocalDate from, LocalDate limit) {
        List<String> dates = new ArrayList<>();
        DayOfWeek dow = start.getDayOfWeek();
        int weekOfMonth = (start.getDayOfMonth() - 1) / 7 + 1; // 1-based week position

        LocalDate d = start;
        if (d.isBefore(from)) {
            long monthsBetween = (from.getYear() - d.getYear()) * 12L + (from.getMonthValue() - d.getMonthValue());
            long skip = (monthsBetween / interval) * interval;
            d = start.plusMonths(skip);
            if (d.isBefore(from)) d = d.plusMonths(interval);
        }
        for (int i = 0; i < 500 && !d.isAfter(limit); i++) {
            LocalDate first = d.withDayOfMonth(1).with(TemporalAdjusters.nextOrSame(dow));
            LocalDate candidate = first.plusWeeks(weekOfMonth - 1);
            if (candidate.getMonth() == d.getMonth() && !candidate.isBefore(from) && !candidate.isAfter(limit)) {
                dates.add(candidate.toString());
            }
            d = d.plusMonths(interval);
        }
        return dates;
    }

    private static List<String> expandCustom(LocalDate start, int interval, JsonNode rule, LocalDate from, LocalDate limit) {
        String unit = rule.path("unit").asText("DAY");
        return switch (unit) {
            case "DAY" -> expandDaily(start, interval, from, limit);
            case "WEEK" -> {
                try {
                    JsonNode weekRule = MAPPER.readTree("{\"daysOfWeek\":[\"" + start.getDayOfWeek().name() + "\"]}");
                    yield expandWeekly(start, interval, weekRule, from, limit);
                } catch (Exception e) {
                    yield List.of();
                }
            }
            case "MONTH" -> expandMonthlyDate(start, interval, from, limit);
            default -> List.of();
        };
    }
}
