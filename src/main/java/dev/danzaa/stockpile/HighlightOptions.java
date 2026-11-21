package dev.danzaa.stockpile;

import com.google.gson.JsonObject;

public record HighlightOptions(String color, int seconds, boolean throughWalls) {
    public static final HighlightOptions DEFAULT = new HighlightOptions("#ffc857", 30, true);

    public HighlightOptions {
        if (color == null || !color.matches("#[0-9a-fA-F]{6}") || seconds < 5 || seconds > 120) {
            throw new IllegalArgumentException("Choose a color and a duration between 5 and 120 seconds.");
        }
    }

    int rgb() {
        return Integer.parseInt(color.substring(1), 16);
    }

    public JsonObject json() {
        var result = new JsonObject();
        result.addProperty("color", color);
        result.addProperty("seconds", seconds);
        result.addProperty("throughWalls", throughWalls);
        return result;
    }

    static HighlightOptions parse(JsonObject json) {
        var color = json.getAsJsonPrimitive("color");
        var seconds = json.getAsJsonPrimitive("seconds");
        var throughWalls = json.getAsJsonPrimitive("throughWalls");
        if (!color.isString() || !seconds.isNumber() || !throughWalls.isBoolean()) {
            throw new IllegalArgumentException("Invalid highlight settings.");
        }
        return new HighlightOptions(color.getAsString(), seconds.getAsBigDecimal().intValueExact(), throughWalls.getAsBoolean());
    }
}
