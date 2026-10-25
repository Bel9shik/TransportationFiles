package kardash.project.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.os.SystemClock;
import android.view.View;
import java.io.InputStream;

/** Plays the same transfer illustration as desktop, including on Android 8. */
@SuppressWarnings("deprecation")
final class TransferAnimation extends View {
    private final Movie movie;
    private final long started = SystemClock.uptimeMillis();

    TransferAnimation(Context context) {
        super(context);
        try (InputStream input = getResources().openRawResource(R.raw.transfer_active)) {
            movie = Movie.decodeStream(input);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot load transfer illustration", e);
        }
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (movie == null) return;
        movie.setTime((int) ((SystemClock.uptimeMillis() - started) % Math.max(1, movie.duration())));
        float scale = Math.min((float) getWidth() / movie.width(), (float) getHeight() / movie.height());
        canvas.save();
        canvas.translate((getWidth() - movie.width() * scale) / 2, (getHeight() - movie.height() * scale) / 2);
        canvas.scale(scale, scale);
        movie.draw(canvas, 0, 0);
        canvas.restore();
        if (isShown() && getWindowVisibility() == VISIBLE) postInvalidateDelayed(32);
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) invalidate();
    }
}
