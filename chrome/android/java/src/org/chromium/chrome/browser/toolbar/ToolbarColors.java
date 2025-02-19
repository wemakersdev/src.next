// Copyright 2019 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.content.Context;

import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.tasks.ReturnToChromeUtil;
import org.chromium.chrome.browser.tasks.tab_management.TabUiFeatureUtilities;

import org.chromium.base.ContextUtils;

/**
 * Helpers to determine colors in toolbars.
 */
public class ToolbarColors {
    /**
     * Returns whether the incognito toolbar theme color can be used in overview mode.
     * @param context The activity context.
     */
    public static boolean canUseIncognitoToolbarThemeColorInOverview(Context context) {
        if (true)
          return true;
        final boolean isAccessibilityEnabled =
                DeviceClassManager.enableAccessibilityLayout(context);
        final boolean isTabGridEnabled = TabUiFeatureUtilities.isGridTabSwitcherEnabled(context);
        final boolean isStartSurfaceEnabled = ReturnToChromeUtil.isStartSurfaceEnabled(context);
        if (ContextUtils.getAppSharedPreferences().getString("active_tabswitcher", "default").equals("default")
                  || ContextUtils.getAppSharedPreferences().getString("active_tabswitcher", "default").equals("original")
                  || ContextUtils.getAppSharedPreferences().getString("active_tabswitcher", "default").equals("horizontal"))
            return true;
        return (isAccessibilityEnabled || isTabGridEnabled || isStartSurfaceEnabled);
    }
}
