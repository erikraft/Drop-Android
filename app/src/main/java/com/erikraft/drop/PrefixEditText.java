package com.erikraft.drop;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatEditText;


/**
 * Custom view for adding a prefix to EditText
 * Based on code from Ali Muzaffar (Medium article about adding a prefix to EditText)
 **/
public class PrefixEditText extends AppCompatEditText {
    private float mOriginalLeftPadding = -1;

    public PrefixEditText(final Context context) {
        super(context);
    }

    public PrefixEditText(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public PrefixEditText(final Context context, final AttributeSet attrs,
                          final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec,
                             final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        calculatePrefix();
    }

    private void calculatePrefix() {
        if (mOriginalLeftPadding == -1) {
            final String prefix = (String) getTag();
            float textWidth = 0;
            if (prefix != null) {
                final float[] widths = new float[prefix.length()];
                getPaint().getTextWidths(prefix, widths);
                for (float w : widths) {
                    textWidth += w;
                }
            }
            mOriginalLeftPadding = getCompoundPaddingLeft();
            setPadding((int) (textWidth + mOriginalLeftPadding),
                    getPaddingRight(), getPaddingTop(),
                    getPaddingBottom());
        }
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        final String prefix = (String) getTag();
        if (prefix != null) {
            canvas.drawText(prefix, mOriginalLeftPadding,
                    getLineBounds(0, null), getPaint());
        }
    }
}
