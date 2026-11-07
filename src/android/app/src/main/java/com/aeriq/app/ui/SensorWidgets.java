package com.aeriq.app.ui;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;

/** Gráficas de la pantalla Mi sensor. */
public final class SensorWidgets {
    private SensorWidgets() {}

    /** Punto (instante, valor) de una medición de ozono. */
    public static final class Point {
        public final long time;
        public final double value;
        public Point(long time, double value) { this.time = time; this.value = value; }
    }

    /** Línea con área rellena desde las 00:00 hasta ahora; el último punto se marca con un círculo hueco. */
    public static final class DayAreaChart extends View {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path stroke = new Path(), area = new Path();
        private List<Point> points = new ArrayList<>();
        private final float density;

        public DayAreaChart(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(3 * density);
            line.setStrokeJoin(Paint.Join.ROUND);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setColor(0xff1f9a6f);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0xffe3f1eb);
            dot.setStrokeWidth(3 * density);
            setContentDescription("Gráfica de ozono de hoy");
        }

        public void setPoints(List<Point> values) {
            points = new ArrayList<>(values);
            points.sort(Comparator.comparingLong(point -> point.time));
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth(), height = getHeight();
            float pad = 8 * density;
            if (points.isEmpty()) {
                fill.setColor(0xffeef3f1);
                canvas.drawRoundRect(0, height * .55f, width, height, 6 * density, 6 * density, fill);
                fill.setColor(0xffe3f1eb);
                return;
            }
            long start = Quality.startOfToday();
            long end = Math.max(System.currentTimeMillis(), points.get(points.size() - 1).time);
            double max = 0;
            for (Point point : points) max = Math.max(max, point.value);
            max = Math.max(max * 1.25, 1);
            stroke.reset();
            area.reset();
            float firstX = 0, lastX = 0, lastY = 0;
            for (int i = 0; i < points.size(); i++) {
                Point point = points.get(i);
                float x = points.size() == 1 ? width - pad
                    : pad + (float) ((point.time - start) / (double) Math.max(1, end - start)) * (width - 2 * pad);
                float y = (float) (height - (point.value / max) * (height - pad * 2));
                if (i == 0) {
                    firstX = x;
                    stroke.moveTo(x, y);
                    area.moveTo(x, height);
                    area.lineTo(x, y);
                } else {
                    stroke.lineTo(x, y);
                    area.lineTo(x, y);
                }
                lastX = x;
                lastY = y;
            }
            area.lineTo(lastX, height);
            area.lineTo(firstX, height);
            area.close();
            canvas.drawPath(area, fill);
            canvas.drawPath(stroke, line);
            dot.setStyle(Paint.Style.FILL);
            dot.setColor(Color.WHITE);
            canvas.drawCircle(lastX, lastY, 6 * density, dot);
            dot.setStyle(Paint.Style.STROKE);
            dot.setColor(0xff1f9a6f);
            canvas.drawCircle(lastX, lastY, 6 * density, dot);
        }
    }

    /** Barra segmentada de 00:00 a ahora con el color de la calidad del aire de cada tramo. */
    public static final class QualityStrip extends View {
        private static final int SEGMENTS = 13;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] colors = new int[SEGMENTS];
        private final float density;

        public QualityStrip(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            Arrays.fill(colors, Quality.NONE);
            setContentDescription("Calidad del aire respirado hoy por tramos");
        }

        public void setPoints(List<Point> points, double medium, double high) {
            long start = Quality.startOfToday();
            long span = Math.max(1, System.currentTimeMillis() - start);
            double[] sum = new double[SEGMENTS];
            int[] count = new int[SEGMENTS];
            for (Point point : points) {
                if (point.time < start) continue;
                int index = (int) Math.min(SEGMENTS - 1, (point.time - start) * SEGMENTS / span);
                sum[index] += point.value;
                count[index]++;
            }
            for (int i = 0; i < SEGMENTS; i++) {
                colors[i] = count[i] == 0 ? Quality.NONE : Quality.color(Quality.level(sum[i] / count[i], medium, high));
            }
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float gap = 3 * density, height = getHeight();
            float cell = (getWidth() - gap * (SEGMENTS - 1)) / SEGMENTS;
            float radius = height / 2;
            for (int i = 0; i < SEGMENTS; i++) {
                paint.setColor(colors[i]);
                float left = i * (cell + gap);
                if (i == 0 || i == SEGMENTS - 1) {
                    // extremos redondeados como en el diseño
                    canvas.drawRoundRect(left, 0, left + cell, height, radius, radius, paint);
                    float squareLeft = i == 0 ? left + cell / 2 : left;
                    canvas.drawRect(squareLeft, 0, squareLeft + cell / 2, height, paint);
                } else canvas.drawRect(left, 0, left + cell, height, paint);
            }
        }
    }

    /** Barras por tramo (hora, día o mes) coloreadas por calidad; los tramos futuros son puntos grises. */
    public static final class HistoryBars extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Double[] values = new Double[0];
        private boolean[] future = new boolean[0];
        private String[] labels = new String[0];
        private double medium = 60, high = 100;
        private final float density;

        public HistoryBars(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            text.setColor(Views.MUTED);
            text.setTextSize(12 * density);
            setContentDescription("Gráfica de barras de ozono");
        }

        public void set(Double[] values, boolean[] future, String[] labels, double medium, double high) {
            this.values = values;
            this.future = future;
            this.labels = labels;
            this.medium = medium;
            this.high = high;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth(), labelArea = 26 * density, chart = getHeight() - labelArea;
            paint.setColor(0xffe9eeec);
            paint.setStrokeWidth(density);
            for (int i = 0; i < 4; i++) {
                float y = chart * i / 4f + density;
                canvas.drawLine(0, y, width, y, paint);
            }
            double max = 1;
            for (Double value : values) if (value != null) max = Math.max(max, value);
            max *= 1.1;
            int count = Math.max(1, values.length);
            float cell = width / count, bar = Math.max(2 * density, Math.min(cell * .62f, 14 * density));
            for (int i = 0; i < values.length; i++) {
                float center = cell * i + cell / 2;
                if (values[i] == null) {
                    if (future.length > i && future[i]) {
                        paint.setColor(0xffe3e9e6);
                        float r = Math.min(bar / 2, 4 * density);
                        canvas.drawRoundRect(center - bar / 2, chart - 2 * r, center + bar / 2, chart, r, r, paint);
                    }
                    continue;
                }
                float top = (float) (chart - values[i] / max * (chart - 4 * density));
                paint.setColor(Quality.color(Quality.level(values[i], medium, high)));
                float r = Math.min(bar / 2, 3 * density);
                canvas.drawRoundRect(center - bar / 2, top, center + bar / 2, chart, r, r, paint);
            }
            if (labels.length == 0) return;
            float baseline = chart + 20 * density;
            for (int i = 0; i < labels.length; i++) {
                float x = labels.length == 1 ? width / 2 : i * width / (labels.length - 1);
                float w = text.measureText(labels[i]);
                float left = Math.max(0, Math.min(width - w, x - w / 2));
                canvas.drawText(labels[i], left, baseline, text);
            }
        }
    }

    /** Dos series suavizadas para comparar zonas. */
    public static final class CompareChart extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Double[]> series = new ArrayList<>();
        private final List<Integer> colors = new ArrayList<>();
        private String[] labels = new String[0];
        private final float density;

        public CompareChart(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            text.setColor(Views.MUTED);
            text.setTextSize(12 * density);
            setContentDescription("Comparación de zonas");
        }

        public void set(List<Double[]> values, List<Integer> lineColors, String[] labels) {
            series.clear();
            series.addAll(values);
            colors.clear();
            colors.addAll(lineColors);
            this.labels = labels;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth(), labelArea = 28 * density, chart = getHeight() - labelArea;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(density);
            paint.setColor(0xffe9eeec);
            for (int i = 0; i < 3; i++) {
                float y = chart * i / 2f;
                canvas.drawLine(0, Math.max(density, Math.min(chart - density, y)), width, Math.max(density, Math.min(chart - density, y)), paint);
            }
            double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
            for (Double[] line : series) for (Double v : line) if (v != null) { min = Math.min(min, v); max = Math.max(max, v); }
            if (min == Double.MAX_VALUE) { min = 0; max = 1; }
            double pad = Math.max(1, (max - min) * .35);
            min -= pad; max += pad;
            paint.setStrokeWidth(2.5f * density);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            for (int s = 0; s < series.size(); s++) {
                Double[] line = series.get(s);
                List<float[]> points = new ArrayList<>();
                for (int i = 0; i < line.length; i++) {
                    if (line[i] == null) continue;
                    float x = line.length == 1 ? width / 2 : i * width / (line.length - 1);
                    float y = (float) (chart - (line[i] - min) / (max - min) * chart);
                    points.add(new float[]{x, y});
                }
                if (points.isEmpty()) continue;
                Path path = new Path();
                path.moveTo(points.get(0)[0], points.get(0)[1]);
                for (int i = 1; i < points.size(); i++) {
                    float[] p0 = points.get(Math.max(0, i - 2)), p1 = points.get(i - 1), p2 = points.get(i),
                        p3 = points.get(Math.min(points.size() - 1, i + 1));
                    path.cubicTo(p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6,
                        p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6, p2[0], p2[1]);
                }
                paint.setColor(colors.get(s));
                canvas.drawPath(path, paint);
                if (points.size() == 1) {
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(points.get(0)[0], points.get(0)[1], 4 * density, paint);
                    paint.setStyle(Paint.Style.STROKE);
                }
            }
            float baseline = chart + 22 * density;
            for (int i = 0; i < labels.length; i++) {
                float x = labels.length == 1 ? width / 2 : i * width / (labels.length - 1);
                float w = text.measureText(labels[i]);
                canvas.drawText(labels[i], Math.max(0, Math.min(width - w, x - w / 2)), baseline, text);
            }
        }
    }
}
