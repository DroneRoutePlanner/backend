package org.example.environment;

/**
 * Niestacjonarne pole wiatru. Wiatr w ticku {@code t} jest czystą funkcją {@code t}
 * (stały wiatr bazowy + okresowe podmuchy), dzięki czemu obiekt jest niemutowalny
 * i może być współdzielony między wątkami / równoległymi ocenami tras.
 */
public final class WindField {

    private static final double GUST_AMPLITUDE = 0.08;

    private static final double GUST_FREQUENCY_X = 0.12;

    private static final double GUST_FREQUENCY_Y = 0.11;

    /** Wektor wiatru w płaszczyźnie XY. */
    public record Wind(double x, double y) {
    }

    private final double baseX;

    private final double baseY;

    public WindField(double baseX, double baseY) {
        this.baseX = baseX;
        this.baseY = baseY;
    }

    public Wind windAt(int tick) {
        return new Wind(
                baseX + GUST_AMPLITUDE * Math.sin(tick * GUST_FREQUENCY_X),
                baseY + GUST_AMPLITUDE * Math.cos(tick * GUST_FREQUENCY_Y));
    }

    public double getBaseX() {
        return baseX;
    }

    public double getBaseY() {
        return baseY;
    }
}
