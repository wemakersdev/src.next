// Copyright 2019 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar.top;

import android.content.res.Resources;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewStub;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.chromium.base.Callback;
import org.chromium.base.CallbackController;
import org.chromium.base.supplier.Supplier;
import org.chromium.chrome.browser.flags.ChromeFeatureList;
import org.chromium.chrome.browser.layouts.LayoutType;
import org.chromium.chrome.browser.logo.LogoUtils;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.search_engines.TemplateUrlServiceFactory;
import org.chromium.chrome.browser.tabmodel.IncognitoStateProvider;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.theme.ThemeColorProvider;
import org.chromium.chrome.browser.toolbar.ButtonData;
import org.chromium.chrome.browser.toolbar.ButtonDataProvider;
import org.chromium.chrome.browser.toolbar.R;
import org.chromium.chrome.browser.toolbar.TabSwitcherButtonCoordinator;
import org.chromium.chrome.browser.toolbar.TabSwitcherButtonView;
import org.chromium.chrome.browser.toolbar.menu_button.MenuButtonCoordinator;
import org.chromium.chrome.browser.user_education.UserEducationHelper;
import org.chromium.chrome.features.start_surface.StartSurfaceConfiguration;
import org.chromium.chrome.features.start_surface.StartSurfaceState;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.ui.modelutil.PropertyModel;
import org.chromium.ui.modelutil.PropertyModelChangeProcessor;

import java.util.function.BooleanSupplier;

/**
 * The controller for the StartSurfaceToolbar. This class handles all interactions that the
 * StartSurfaceToolbar has with the outside world. Lazily creates the tab toolbar the first time
 * it's needed.
 */
public class StartSurfaceToolbarCoordinator {
    private final StartSurfaceToolbarMediator mToolbarMediator;
    private final ViewStub mStub;
    private final PropertyModel mPropertyModel;
    private final TopToolbarInteractabilityManager mTopToolbarInteractabilityManager;
    private final boolean mShouldCreateLogoInToolbar;

    private PropertyModelChangeProcessor mPropertyModelChangeProcessor;
    private StartSurfaceToolbarView mView;
    private TabModelSelector mTabModelSelector;
    private TabSwitcherButtonCoordinator mTabSwitcherButtonCoordinator;
    private TabSwitcherButtonView mTabSwitcherButtonView;
    private ThemeColorProvider mThemeColorProvider;
    private OnClickListener mTabSwitcherClickListener;
    private OnLongClickListener mTabSwitcherLongClickListener;
    private MenuButtonCoordinator mMenuButtonCoordinator;
    private CallbackController mCallbackController = new CallbackController();
    private boolean mIsNativeInitialized;
    private final boolean mIsSurfacePolishEnabled;
    private final boolean mIsSurfacePolishMoveDownLogoEnabled;
    private final boolean mIsSurfacePolishLessBrandSpaceEnabled;
    // This is used for 2 cases for the surface polish project, one is when the logo is moved down
    // from the toolbar, and the other is when the logo is moved down from the toolbar with less
    // brand space.
    private int mFakeSearchBoxOffsetForSurfacePolishMoveDownLogo;
    // This is used for the surface polish project for the case when the logo stays in the toolbar.
    private int mFakeSearchBoxOffsetForSurfacePolishLogoInToolbar;

    StartSurfaceToolbarCoordinator(
            ViewStub startSurfaceToolbarStub,
            UserEducationHelper userEducationHelper,
            ButtonDataProvider identityDiscController,
            ThemeColorProvider provider,
            MenuButtonCoordinator menuButtonCoordinator,
            Supplier<ButtonData> identityDiscButtonSupplier,
            boolean isTabToGtsAnimationEnabled,
            BooleanSupplier isIncognitoModeEnabledSupplier,
            Callback<LoadUrlParams> logoClickedCallback,
            boolean isRefactorEnabled,
            boolean shouldCreateLogoInToolbar,
            Callback<Boolean> finishedTransitionCallback,
            ToolbarColorObserverManager toolbarColorObserverManager) {
        mStub = startSurfaceToolbarStub;
        mIsSurfacePolishEnabled = ChromeFeatureList.sSurfacePolish.isEnabled();
        mIsSurfacePolishMoveDownLogoEnabled =
                mIsSurfacePolishEnabled
                        && StartSurfaceConfiguration.SURFACE_POLISH_MOVE_DOWN_LOGO.getValue();
        mIsSurfacePolishLessBrandSpaceEnabled =
                mIsSurfacePolishMoveDownLogoEnabled
                        && StartSurfaceConfiguration.SURFACE_POLISH_LESS_BRAND_SPACE.getValue();

        if (mIsSurfacePolishEnabled) {
            setFakeSearchBoxToScreenTopOffsetForSurfacePolish();
        }

        mPropertyModel =
                new PropertyModel.Builder(StartSurfaceToolbarProperties.ALL_KEYS)
                        .with(
                                StartSurfaceToolbarProperties.INCOGNITO_SWITCHER_VISIBLE,
                                !StartSurfaceConfiguration
                                        .START_SURFACE_HIDE_INCOGNITO_SWITCH_NO_TAB
                                        .getValue())
                        .with(StartSurfaceToolbarProperties.MENU_IS_VISIBLE, true)
                        .with(StartSurfaceToolbarProperties.IS_VISIBLE, false)
                        .build();

        mShouldCreateLogoInToolbar = shouldCreateLogoInToolbar;
        mToolbarMediator =
                new StartSurfaceToolbarMediator(
                        mStub.getContext(),
                        mPropertyModel,
                        (iphCommandBuilder) -> {
                            // TODO(crbug.com/865801): Replace the null check with an assert after
                            // fixing or removing the ShareButtonControllerTest that necessitated
                            // it.
                            if (mView == null) return;
                            userEducationHelper.requestShowIPH(
                                    iphCommandBuilder
                                            .setAnchorView(mView.getIdentityDiscView())
                                            .build());
                        },
                        StartSurfaceConfiguration.START_SURFACE_HIDE_INCOGNITO_SWITCH_NO_TAB
                                .getValue(),
                        menuButtonCoordinator,
                        identityDiscController,
                        identityDiscButtonSupplier,
                        isTabToGtsAnimationEnabled,
                        isIncognitoModeEnabledSupplier,
                        logoClickedCallback,
                        isRefactorEnabled,
                        StartSurfaceConfiguration.IS_DOODLE_SUPPORTED.getValue(),
                        shouldCreateLogoInToolbar,
                        finishedTransitionCallback,
                        toolbarColorObserverManager);

        mThemeColorProvider = provider;
        mMenuButtonCoordinator = menuButtonCoordinator;
        mTopToolbarInteractabilityManager =
                new TopToolbarInteractabilityManager(
                        enabled -> mToolbarMediator.setNewTabEnabled(enabled));
    }

    /** Cleans up any code and removes observers as necessary. */
    void destroy() {
        mToolbarMediator.destroy();
        if (mTabSwitcherButtonCoordinator != null) mTabSwitcherButtonCoordinator.destroy();
        if (mMenuButtonCoordinator != null) {
            mMenuButtonCoordinator.destroy();
            mMenuButtonCoordinator = null;
        }
        if (mCallbackController != null) {
            mCallbackController.destroy();
            mCallbackController = null;
        }
        mTabSwitcherButtonCoordinator = null;
        mTabSwitcherButtonView = null;
        mThemeColorProvider = null;
        mTabSwitcherClickListener = null;
        mTabSwitcherLongClickListener = null;
    }

    /**
     * Sets the OnClickListener that will be notified when the New Tab button is pressed.
     * @param listener The callback that will be notified when the New Tab button is pressed.
     */
    void setOnNewTabClickHandler(View.OnClickListener listener) {
        mToolbarMediator.setOnNewTabClickHandler(listener);
    }

    /**
     * Sets the current {@Link TabModelSelector} so the toolbar can pass it into buttons that need
     * access to it.
     */
    void setTabModelSelector(TabModelSelector selector) {
        mTabModelSelector = selector;
        if (mTabSwitcherButtonCoordinator != null) {
            mTabSwitcherButtonCoordinator.setTabCountSupplier(
                    mTabModelSelector.getCurrentModelTabCountSupplier());
        }
        mToolbarMediator.setTabModelSelector(selector);
    }

    /**
     * @param provider The provider used to determine incognito state.
     */
    void setIncognitoStateProvider(IncognitoStateProvider provider) {
        mToolbarMediator.setIncognitoStateProvider(provider);
    }

    /**
     * Called when accessibility status changes.
     * @param enabled whether accessibility status is enabled.
     */
    void onAccessibilityStatusChanged(boolean enabled) {
        mToolbarMediator.onAccessibilityStatusChanged(enabled);
    }

    /**
     * @param onClickListener The {@link OnClickListener} for the tab switcher button.
     */
    void setTabSwitcherListener(OnClickListener onClickListener) {
        if (mTabSwitcherButtonCoordinator != null) {
            mTabSwitcherButtonCoordinator.setTabSwitcherListener(onClickListener);
        } else {
            mTabSwitcherClickListener = onClickListener;
        }
    }

    /**
     * @param listener The {@link OnLongClickListener} for the tab switcher button.
     */
    void setOnTabSwitcherLongClickHandler(OnLongClickListener listener) {
        if (mTabSwitcherButtonView != null) {
            mTabSwitcherButtonView.setOnLongClickListener(listener);
        } else {
            mTabSwitcherLongClickListener = listener;
        }
    }

    /**
     * Called when start surface state is changed.
     * @param newState The new {@link StartSurfaceState}. Should be removed after refactor is
     *         enabled.
     * @param shouldShowStartSurfaceToolbar Whether or not should show start surface toolbar.
     * @param newLayoutType The new {@link LayoutType}. Only used when refactor is enabled.
     */
    void onStartSurfaceStateChanged(
            @Nullable @StartSurfaceState Integer newState,
            boolean shouldShowStartSurfaceToolbar,
            @Nullable @LayoutType Integer newLayoutType) {
        if (shouldShowStartSurfaceToolbar && !isInflated()) inflate();
        mToolbarMediator.onStartSurfaceStateChanged(
                newState == null ? StartSurfaceState.NOT_SHOWN : newState,
                shouldShowStartSurfaceToolbar,
                newLayoutType == null ? LayoutType.NONE : newLayoutType);
    }

    void initLogoWithNative() {
        mIsNativeInitialized = true;
        mToolbarMediator.initLogoWithNative();
    }

    /**
     * Triggered when the offset of start surface header view is changed.
     * @param verticalOffset The start surface header view's offset.
     */
    void onStartSurfaceHeaderOffsetChanged(int verticalOffset) {
        mToolbarMediator.onStartSurfaceHeaderOffsetChanged(verticalOffset);
    }

    /**
     * @return Whether or not toolbar phone layout view should be shown.
     */
    boolean shouldShowRealSearchBox() {
        boolean isBigLogoShownInContent =
                !mShouldCreateLogoInToolbar
                        && mIsNativeInitialized
                        && TemplateUrlServiceFactory.getForProfile(
                                        Profile.getLastUsedRegularProfile())
                                .doesDefaultSearchEngineHaveLogo();
        int fakeSearchBoxMarginToScreenTop;
        if (mIsSurfacePolishEnabled) {
            fakeSearchBoxMarginToScreenTop =
                    isBigLogoShownInContent && mIsSurfacePolishMoveDownLogoEnabled
                            ? mFakeSearchBoxOffsetForSurfacePolishMoveDownLogo
                            : mFakeSearchBoxOffsetForSurfacePolishLogoInToolbar;
        } else {
            // This value should be equal to |fakeSearchBoxToRealSearchBoxTop +
            // realVerticalMargin| in StartSurfaceCoordinator#initializeOffsetChangedListener
            fakeSearchBoxMarginToScreenTop =
                    getDimenPixel(R.dimen.toolbar_height_no_shadow)
                            + (isBigLogoShownInContent
                                    ? getDimenPixel(R.dimen.start_surface_content_logo_height)
                                    : getDimenPixel(
                                            R.dimen.start_surface_fake_search_box_top_margin));
        }
        return mToolbarMediator.shouldShowRealSearchBox(fakeSearchBoxMarginToScreenTop);
    }

    /** Returns whether it's on the start surface homepage.*/
    boolean isOnHomepage() {
        return mToolbarMediator.isOnHomepage();
    }

    boolean isShowingTabSwitcher() {
        return mToolbarMediator.isOnGridTabSwitcher();
    }

    /**
     * @param highlight If the new tab button should be highlighted.
     */
    void setNewTabButtonHighlight(boolean highlight) {
        mToolbarMediator.setNewTabButtonHighlight(highlight);
    }

    @NonNull
    TopToolbarInteractabilityManager getTopToolbarInteractabilityManager() {
        return mTopToolbarInteractabilityManager;
    }

    private void inflate() {
        mStub.setLayoutResource(R.layout.start_top_toolbar);
        mView = (StartSurfaceToolbarView) mStub.inflate();
        mMenuButtonCoordinator.setMenuButton(mView.findViewById(R.id.menu_button_wrapper));
        mMenuButtonCoordinator.setVisibility(
                mPropertyModel.get(StartSurfaceToolbarProperties.MENU_IS_VISIBLE));
        mPropertyModelChangeProcessor =
                PropertyModelChangeProcessor.create(
                        mPropertyModel, mView, StartSurfaceToolbarViewBinder::bind);

        mToolbarMediator.onLogoViewReady(mView.findViewById(R.id.logo));

        mTabSwitcherButtonView = mView.findViewById(R.id.start_tab_switcher_button);
        if (mTabSwitcherLongClickListener != null) {
            mTabSwitcherButtonView.setOnLongClickListener(mTabSwitcherLongClickListener);
            mTabSwitcherLongClickListener = null;
        }
        mTabSwitcherButtonCoordinator = new TabSwitcherButtonCoordinator(mTabSwitcherButtonView);
        mTabSwitcherButtonCoordinator.setThemeColorProvider(mThemeColorProvider);
        if (mTabModelSelector != null) {
            mTabSwitcherButtonCoordinator.setTabCountSupplier(
                    mTabModelSelector.getCurrentModelTabCountSupplier());
        }
        if (mTabSwitcherClickListener != null) {
            mTabSwitcherButtonCoordinator.setTabSwitcherListener(mTabSwitcherClickListener);
            mTabSwitcherClickListener = null;
        }
    }

    private boolean isInflated() {
        return mView != null;
    }

    private int getDimenPixel(int id) {
        return mStub.getResources().getDimensionPixelOffset(id);
    }

    /**
     * Set the distance to be added for the offset which indicated where the toolbar phone layout
     * view should be shown when the user scrolls up the screen for Surface Polish.
     */
    private void setFakeSearchBoxToScreenTopOffsetForSurfacePolish() {
        assert mIsSurfacePolishEnabled;

        // Ensure the fake search box reaches the top of the screen.
        Resources resources = mStub.getResources();
        int toolbarPlaceholderHeight = getDimenPixel(R.dimen.toolbar_height_no_shadow);
        if (mIsSurfacePolishLessBrandSpaceEnabled) {
            mFakeSearchBoxOffsetForSurfacePolishMoveDownLogo =
                    toolbarPlaceholderHeight
                            + LogoUtils.getLogoHeightPolishedShort(resources)
                            + LogoUtils.getTopMarginPolishedSmall(resources)
                            + LogoUtils.getBottomMarginPolishedSmall(resources);
        } else if (mIsSurfacePolishMoveDownLogoEnabled) {
            mFakeSearchBoxOffsetForSurfacePolishMoveDownLogo =
                    toolbarPlaceholderHeight
                            + LogoUtils.getLogoHeightPolished(resources)
                            + LogoUtils.getTopMarginPolished(resources)
                            + LogoUtils.getBottomMarginPolished(resources);
        }
        mFakeSearchBoxOffsetForSurfacePolishLogoInToolbar =
                toolbarPlaceholderHeight
                        + getDimenPixel(R.dimen.start_surface_fake_search_box_top_margin);
    }

    public TabModelSelector getIncognitoToggleTabModelSelectorForTesting() {
        return mPropertyModel.get(
                StartSurfaceToolbarProperties.INCOGNITO_TOGGLE_TAB_MODEL_SELECTOR);
    }
}
