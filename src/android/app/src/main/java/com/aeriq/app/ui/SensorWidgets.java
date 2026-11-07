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
}
