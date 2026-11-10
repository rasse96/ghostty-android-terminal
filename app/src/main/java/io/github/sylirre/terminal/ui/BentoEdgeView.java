/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2026 Sylirre */

package io.github.sylirre.terminal.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import io.github.sylirre.terminal.R;
import io.github.sylirre.terminal.term.SessionProfile;

/**
 * BentoEdgeView: Edge-docked minimized Bento handle and expandable Bento drawer.
 *
 * Keeps the terminal 100% full height by eliminating the permanent top bar.
 * A sleek floating edge handle allows expanding the drawer to switch tabs,
 * spawn multiple environments (Alpine/Debian userland, Termux bridge, Root su,
 * Shizuku rish, Android shell, VM), search, and customize themes.
 */
public class BentoEdgeView extends FrameLayout {

    public interface Listener {
        void onTabSelected(int index);
        void onTabClosed(int index);
        void onNewSession();
        void onLaunchEnvironment(SessionProfile.Type type);
        void onSearchClicked();
        void onThemesClicked();
        void onSettingsClicked();
        void onToggleTopBarClicked();
    }

    private Listener listener;
    private ChromePalette palette;

    private View scrimView;
    private FrameLayout drawerContainer;
    private LinearLayout sessionsListContainer;
    private FrameLayout handlePill;
    private TextView handleText;
    private View handleProgressDot;

    private boolean isDrawerOpen = false;
    private int drawerWidth = 0;
    private float handleY = -1;
    private float touchStartY = 0;
    private float touchStartX = 0;
    private boolean isDraggingHandle = false;

    private List<String> currentTitles = new ArrayList<>();
    private int currentActiveIndex = 0;
    private List<TabStripView.TabProgress> currentProgress = new ArrayList<>();

    public BentoEdgeView(Context context) {
        super(context);
        init(context);
    }

    public BentoEdgeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0) {
            drawerWidth = Math.min(dp(320), (int) (w * 0.85f));
            if (!isDrawerOpen && drawerContainer != null) {
                drawerContainer.setTranslationX(drawerWidth);
            }
        }
    }

    public BentoEdgeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    private int dp(float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics());
    }

    private void init(Context context) {
        setClipChildren(false);
        setClipToPadding(false);

        // 1. Scrim
        scrimView = new View(context);
        scrimView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        scrimView.setBackgroundColor(Color.argb(120, 0, 0, 0));
        scrimView.setAlpha(0f);
        scrimView.setVisibility(GONE);
        scrimView.setOnClickListener(v -> collapseDrawer(true));
        addView(scrimView);

        // 2. Expandable Bento Drawer
        drawerWidth = Math.min(dp(320), (int) (getResources().getDisplayMetrics().widthPixels * 0.85f));
        drawerContainer = new FrameLayout(context);
        LayoutParams drawerLp = new LayoutParams(drawerWidth, LayoutParams.MATCH_PARENT, Gravity.END);
        drawerContainer.setLayoutParams(drawerLp);
        drawerContainer.setElevation(dp(16));
        drawerContainer.setClickable(true);
        drawerContainer.setVisibility(GONE);
        drawerContainer.setTranslationX(drawerWidth);

        buildDrawerContent(context);
        addView(drawerContainer);

        // 3. Floating Edge Handle Pill
        handlePill = new FrameLayout(context);
        int pillW = dp(38);
        int pillH = dp(56);
        LayoutParams pillLp = new LayoutParams(pillW, pillH, Gravity.END | Gravity.TOP);
        pillLp.topMargin = dp(120);
        handlePill.setLayoutParams(pillLp);
        handlePill.setElevation(dp(8));
        handlePill.setClickable(true);
        handlePill.setFocusable(true);

        LinearLayout pillContent = new LinearLayout(context);
        pillContent.setOrientation(LinearLayout.VERTICAL);
        pillContent.setGravity(Gravity.CENTER);
        pillContent.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        handleProgressDot = new View(context);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(5), dp(5));
        dotLp.bottomMargin = dp(3);
        handleProgressDot.setLayoutParams(dotLp);
        handleProgressDot.setVisibility(GONE);
        pillContent.addView(handleProgressDot);

        handleText = new TextView(context);
        handleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        handleText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        handleText.setGravity(Gravity.CENTER);
        handleText.setText("1/1");
        pillContent.addView(handleText);

        handlePill.addView(pillContent);

        // Handle touch & drag handling
        handlePill.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchStartY = event.getRawY();
                    touchStartX = event.getRawX();
                    isDraggingHandle = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dy = event.getRawY() - touchStartY;
                    float dx = event.getRawX() - touchStartX;
                    if (Math.abs(dy) > dp(6) || Math.abs(dx) > dp(6)) {
                        isDraggingHandle = true;
                    }
                    if (isDraggingHandle) {
                        float newY = handlePill.getY() + (event.getRawY() - touchStartY);
                        touchStartY = event.getRawY();
                        int maxY = getHeight() - handlePill.getHeight() - dp(80);
                        int minY = dp(40);
                        if (newY < minY) newY = minY;
                        if (newY > maxY) newY = maxY;
                        handlePill.setY(newY);
                        handleY = newY;
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (!isDraggingHandle) {
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        expandDrawer(true);
                    }
                    isDraggingHandle = false;
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    isDraggingHandle = false;
                    return true;
            }
            return false;
        });

        addView(handlePill);
    }

    private void buildDrawerContent(Context context) {
        ScrollView scroll = new ScrollView(context);
        scroll.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(36), dp(16), dp(36));
        layout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // --- Drawer Header Card ---
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(14));

        TextView title = new TextView(context);
        title.setText(R.string.bento_drawer_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        header.addView(title);

        TextView closeBtn = new TextView(context);
        closeBtn.setText("✕");
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        closeBtn.setGravity(Gravity.CENTER);
        int closeSize = dp(36);
        closeBtn.setLayoutParams(new LinearLayout.LayoutParams(closeSize, closeSize));
        closeBtn.setOnClickListener(v -> collapseDrawer(true));
        header.addView(closeBtn);
        layout.addView(header);

        // --- Active Sessions Container ---
        sessionsListContainer = new LinearLayout(context);
        sessionsListContainer.setOrientation(LinearLayout.VERTICAL);
        layout.addView(sessionsListContainer);

        // --- Environment Launcher Section ---
        TextView envSectionTitle = new TextView(context);
        envSectionTitle.setText(R.string.bento_environment_section);
        envSectionTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        envSectionTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        envSectionTitle.setPadding(dp(4), dp(16), 0, dp(8));
        layout.addView(envSectionTitle);

        LinearLayout envGrid = new LinearLayout(context);
        envGrid.setOrientation(LinearLayout.VERTICAL);
        buildEnvironmentTiles(context, envGrid);
        layout.addView(envGrid);

        // --- Quick Utility Action Grid ---
        LinearLayout actionRow = new LinearLayout(context);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(0, dp(18), 0, 0);

        actionRow.addView(createActionTile(context, "🔍", R.string.bento_action_search, v -> {
            collapseDrawer(true);
            if (listener != null) listener.onSearchClicked();
        }));

        actionRow.addView(createActionTile(context, "🎨", R.string.bento_action_themes, v -> {
            collapseDrawer(true);
            if (listener != null) listener.onThemesClicked();
        }));

        actionRow.addView(createActionTile(context, "⚙", R.string.bento_action_settings, v -> {
            collapseDrawer(true);
            if (listener != null) listener.onSettingsClicked();
        }));

        actionRow.addView(createActionTile(context, "↕", R.string.bento_action_toggle_topbar, v -> {
            collapseDrawer(true);
            if (listener != null) listener.onToggleTopBarClicked();
        }));

        layout.addView(actionRow);
        scroll.addView(layout);
        drawerContainer.addView(scroll);
    }

    private View createActionTile(Context context, String icon, int labelRes, OnClickListener onClick) {
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(58), 1);
        lp.setMargins(dp(3), 0, dp(3), 0);
        tile.setLayoutParams(lp);
        tile.setPadding(dp(4), dp(6), dp(4), dp(6));
        tile.setClickable(true);
        tile.setFocusable(true);

        TextView iconView = new TextView(context);
        iconView.setText(icon);
        iconView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        iconView.setGravity(Gravity.CENTER);
        tile.addView(iconView);

        TextView labelView = new TextView(context);
        labelView.setText(labelRes);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        labelView.setGravity(Gravity.CENTER);
        tile.addView(labelView);

        tile.setOnClickListener(onClick);
        return tile;
    }

    private void buildEnvironmentTiles(Context context, LinearLayout container) {
        List<SessionProfile> profiles = SessionProfile.availableProfiles(context);
        for (SessionProfile p : profiles) {
            LinearLayout item = new LinearLayout(context);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48));
            lp.bottomMargin = dp(6);
            item.setLayoutParams(lp);
            item.setPadding(dp(12), dp(4), dp(12), dp(4));
            item.setClickable(true);
            item.setFocusable(true);

            TextView glyphView = new TextView(context);
            glyphView.setText(p.glyph);
            glyphView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            glyphView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            glyphView.setGravity(Gravity.CENTER);
            int glyphSize = dp(28);
            glyphView.setLayoutParams(new LinearLayout.LayoutParams(glyphSize, glyphSize));
            item.addView(glyphView);

            LinearLayout textCol = new LinearLayout(context);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
            tlp.setMarginStart(dp(10));
            textCol.setLayoutParams(tlp);

            TextView nameView = new TextView(context);
            nameView.setText(p.name);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            nameView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            textCol.addView(nameView);

            TextView descView = new TextView(context);
            descView.setText(p.description);
            descView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            textCol.addView(descView);

            item.addView(textCol);

            item.setOnClickListener(v -> {
                collapseDrawer(true);
                if (listener != null) listener.onLaunchEnvironment(p.type);
            });

            container.addView(item);
        }
    }

    public void applyPalette(ChromePalette p) {
        this.palette = p;
        if (p == null) return;

        // Drawer background
        drawerContainer.setBackground(p.rounded(p.surface1, dp(16), p.border, dp(1)));

        // Handle pill background
        handlePill.setBackground(p.rounded(p.surface2, dp(19), p.accent, dp(1.5f)));
        handleText.setTextColor(p.accent);

        // Dot
        GradientDrawable dotDrawable = new GradientDrawable();
        dotDrawable.setShape(GradientDrawable.OVAL);
        dotDrawable.setColor(p.accent);
        handleProgressDot.setBackground(dotDrawable);

        // Re-render sessions list to apply theme colors
        renderSessions();

        if (!isDrawerOpen && handlePill != null) {
            handlePill.setVisibility(VISIBLE);
        }
    }

    public void update(List<String> titles, int activeIndex, List<TabStripView.TabProgress> progress) {
        this.currentTitles = new ArrayList<>(titles);
        this.currentActiveIndex = activeIndex;
        this.currentProgress = new ArrayList<>(progress);

        // Update handle pill label: active/total
        int total = titles.isEmpty() ? 1 : titles.size();
        int active = (activeIndex >= 0 && activeIndex < total) ? (activeIndex + 1) : 1;
        handleText.setText(active + "/" + total);

        // Check if any tab has progress active
        boolean hasProgress = false;
        for (TabStripView.TabProgress pr : progress) {
            if (pr != null && pr.state != 0) { // TerminalSession.PROGRESS_NONE = 0
                hasProgress = true;
                break;
            }
        }
        handleProgressDot.setVisibility(hasProgress ? VISIBLE : GONE);

        renderSessions();

        if (!isDrawerOpen && handlePill != null) {
            handlePill.setVisibility(VISIBLE);
        }
    }

    private void renderSessions() {
        if (sessionsListContainer == null) return;
        sessionsListContainer.removeAllViews();
        Context context = getContext();

        for (int i = 0; i < currentTitles.size(); i++) {
            final int index = i;
            String titleStr = currentTitles.get(i);
            boolean isActive = (i == currentActiveIndex);

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48));
            lp.bottomMargin = dp(8);
            card.setLayoutParams(lp);
            card.setPadding(dp(12), dp(4), dp(8), dp(4));
            card.setClickable(true);
            card.setFocusable(true);

            if (palette != null) {
                if (isActive) {
                    card.setBackground(palette.rounded(palette.surface3, dp(12), palette.accent, dp(1.5f)));
                } else {
                    card.setBackground(palette.rounded(palette.surface2, dp(12), palette.border, dp(1)));
                }
            }

            // Tab index dot / indicator
            TextView indexView = new TextView(context);
            indexView.setText(String.valueOf(i + 1));
            indexView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            indexView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            indexView.setGravity(Gravity.CENTER);
            if (palette != null) {
                indexView.setTextColor(isActive ? palette.accent : palette.textSecondary);
            }
            int idxSize = dp(24);
            indexView.setLayoutParams(new LinearLayout.LayoutParams(idxSize, idxSize));
            card.addView(indexView);

            // Tab Title
            TextView titleView = new TextView(context);
            titleView.setText(titleStr);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            titleView.setTypeface(Typeface.create("sans-serif-medium", isActive ? Typeface.BOLD : Typeface.NORMAL));
            titleView.setSingleLine(true);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
            tlp.setMarginStart(dp(8));
            titleView.setLayoutParams(tlp);
            if (palette != null) {
                titleView.setTextColor(isActive ? palette.textPrimary : palette.textSecondary);
            }
            card.addView(titleView);

            // Close button (x)
            TextView closeBtn = new TextView(context);
            closeBtn.setText("✕");
            closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            closeBtn.setGravity(Gravity.CENTER);
            int closeSize = dp(32);
            closeBtn.setLayoutParams(new LinearLayout.LayoutParams(closeSize, closeSize));
            if (palette != null) {
                closeBtn.setTextColor(palette.textTertiary);
            }
            closeBtn.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (listener != null) listener.onTabClosed(index);
            });
            card.addView(closeBtn);

            card.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                collapseDrawer(true);
                if (listener != null) listener.onTabSelected(index);
            });

            sessionsListContainer.addView(card);
        }
    }

    public void expandDrawer(boolean animate) {
        if (isDrawerOpen) return;
        isDrawerOpen = true;

        if (scrimView != null) scrimView.animate().setListener(null);
        if (drawerContainer != null) drawerContainer.animate().setListener(null);

        if (scrimView != null) scrimView.setVisibility(VISIBLE);
        if (drawerContainer != null) drawerContainer.setVisibility(VISIBLE);
        if (handlePill != null) handlePill.setVisibility(GONE);

        if (animate) {
            if (scrimView != null) scrimView.animate().alpha(1f).setDuration(220).start();
            if (drawerContainer != null) {
                drawerContainer.animate()
                        .translationX(0f)
                        .setInterpolator(new DecelerateInterpolator(1.8f))
                        .setDuration(260)
                        .start();
            }
        } else {
            if (scrimView != null) scrimView.setAlpha(1f);
            if (drawerContainer != null) drawerContainer.setTranslationX(0f);
        }
    }

    public void collapseDrawer(boolean animate) {
        if (!isDrawerOpen) {
            if (handlePill != null) handlePill.setVisibility(VISIBLE);
            if (drawerContainer != null) drawerContainer.setVisibility(GONE);
            if (scrimView != null) scrimView.setVisibility(GONE);
            return;
        }
        isDrawerOpen = false;

        if (handlePill != null) handlePill.setVisibility(VISIBLE);
        int targetX = drawerWidth > 0 ? drawerWidth : (drawerContainer != null && drawerContainer.getWidth() > 0 ? drawerContainer.getWidth() : dp(320));

        if (scrimView != null) scrimView.animate().setListener(null);
        if (drawerContainer != null) drawerContainer.animate().setListener(null);

        if (animate) {
            if (scrimView != null) scrimView.animate().alpha(0f).setDuration(180).start();
            if (drawerContainer != null) {
                drawerContainer.animate()
                        .translationX(targetX)
                        .setInterpolator(new DecelerateInterpolator(1.8f))
                        .setDuration(220)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (drawerContainer != null) drawerContainer.animate().setListener(null);
                                if (scrimView != null) scrimView.animate().setListener(null);
                                if (!isDrawerOpen) {
                                    if (drawerContainer != null) drawerContainer.setVisibility(GONE);
                                    if (scrimView != null) scrimView.setVisibility(GONE);
                                    if (handlePill != null) handlePill.setVisibility(VISIBLE);
                                }
                            }
                        })
                        .start();
            }
        } else {
            if (scrimView != null) {
                scrimView.setAlpha(0f);
                scrimView.setVisibility(GONE);
            }
            if (drawerContainer != null) {
                drawerContainer.setVisibility(GONE);
                drawerContainer.setTranslationX(targetX);
            }
            if (handlePill != null) handlePill.setVisibility(VISIBLE);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isDrawerOpen) {
            return false;
        }
        return super.onTouchEvent(event);
    }

    public boolean isDrawerOpen() {
        return isDrawerOpen;
    }
}
