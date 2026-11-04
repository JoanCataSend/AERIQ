package com.aeriq.app.ui;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;

public final class MeasurementGraph extends View {
    private final List<Double> values;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public MeasurementGraph(Context context, List<Double> values) {
        super(context);
        this.values = new ArrayList<>(values);
        setContentDescription("Gráfica de " + values.size() + " mediciones");
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth(), height = getHeight();
        paint.setColor(Views.LINE);
        paint.setStrokeWidth(2);
        for (int line = 1; line < 4; line++) canvas.drawLine(0, height * line / 4, width, height * line / 4, paint);
        if (values.isEmpty()) return;
        double min = Math.min(0, Collections.min(values));
        double max = Math.max(min + 1, Collections.max(values));
        Path path = new Path();
        for (int i = 0; i < values.size(); i++) {
            float x = values.size() == 1 ? width / 2 : i * width / (values.size() - 1);
            float y = (float) (height - 4 - (values.get(i) - min) / (max - min) * (height - 8));
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
            paint.setColor(Views.GREEN);
            if (i == values.size() - 1) canvas.drawCircle(x, y, 4, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }
}
