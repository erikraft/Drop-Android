package com.erikraft.drop;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

/**
 * Compatibility preference that dims only its text, keeping the icon at its normal opacity.
 */
public final class CompatibilityPreference extends Preference {
    private boolean compatible = true;

    public CompatibilityPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setCompatible(boolean value) {
        if (compatible == value) return;
        compatible = value;
        setEnabled(true);
        setSelectable(value);
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        final float alpha = compatible ? 1f : 0.55f;
        final View title = holder.findViewById(android.R.id.title);
        final View summary = holder.findViewById(android.R.id.summary);
        if (title instanceof TextView) title.setAlpha(alpha);
        if (summary instanceof TextView) summary.setAlpha(alpha);
    }
}
