// Copyright 2019 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management;

import android.app.Activity;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ListView;

import androidx.appcompat.content.res.AppCompatResources;

import org.chromium.base.Callback;
import org.chromium.base.LifetimeAssert;
import org.chromium.chrome.tab_ui.R;
import org.chromium.components.browser_ui.widget.BrowserUiListMenuUtils;
import org.chromium.ui.listmenu.BasicListMenu.ListMenuItemType;
import org.chromium.ui.listmenu.ListMenuItemProperties;
import org.chromium.ui.listmenu.ListMenuItemViewBinder;
import org.chromium.ui.modelutil.LayoutViewBuilder;
import org.chromium.ui.modelutil.MVCListAdapter.ModelList;
import org.chromium.ui.modelutil.ModelListAdapter;
import org.chromium.ui.widget.AnchoredPopupWindow;
import org.chromium.ui.widget.ViewRectProvider;

/**
 * A coordinator for the menu in TabGridDialog toolbar. It is responsible for creating a list of
 * menu items, setting up the menu and displaying the menu.
 */
public class TabGridDialogMenuCoordinator {
    private final Context mContext;
    private final ComponentCallbacks mComponentCallbacks;
    private final Callback<Integer> mOnItemClickedCallback;
    private final LifetimeAssert mLifetimeAssert = LifetimeAssert.create(this);
    private AnchoredPopupWindow mMenuWindow;

    /**
     * Creates a {@link View.OnClickListener} that creates the menu and shows it when clicked.
     * @param onItemClicked  The clicked listener callback that handles clicks on menu items.
     * @return A {@link View.OnClickListener} for the button that opens up the menu.
     */
    static View.OnClickListener getTabGridDialogMenuOnClickListener(
            Callback<Integer> onItemClicked) {
        return view -> {
            Context context = view.getContext();
            TabGridDialogMenuCoordinator menu =
                    new TabGridDialogMenuCoordinator(context, view, onItemClicked);
            menu.display();
        };
    }

    private TabGridDialogMenuCoordinator(
            Context context, View anchorView, Callback<Integer> onItemClicked) {
        mContext = context;
        mOnItemClickedCallback = onItemClicked;
        mComponentCallbacks =
                new ComponentCallbacks() {
                    @Override
                    public void onConfigurationChanged(Configuration newConfig) {
                        if (mMenuWindow == null || !mMenuWindow.isShowing()) return;
                        mMenuWindow.dismiss();
                    }

                    @Override
                    public void onLowMemory() {}
                };
        mContext.registerComponentCallbacks(mComponentCallbacks);

        final View contentView =
                LayoutInflater.from(context)
                        .inflate(R.layout.tab_switcher_action_menu_layout, null);
        setupMenu(contentView, anchorView);
    }

    private void setupMenu(View contentView, View anchorView) {
        ListView listView = contentView.findViewById(R.id.tab_switcher_action_menu_list);
        ModelList modelList = buildMenuItems();
        ModelListAdapter adapter =
                new ModelListAdapter(modelList) {
                    @Override
                    public long getItemId(int position) {
                        return ((ListItem) getItem(position))
                                .model.get(ListMenuItemProperties.MENU_ITEM_ID);
                    }
                };
        listView.setAdapter(adapter);
        adapter.registerType(
                ListMenuItemType.MENU_ITEM,
                new LayoutViewBuilder(R.layout.list_menu_item),
                ListMenuItemViewBinder::binder);
        listView.setOnItemClickListener(
                (p, v, pos, id) -> {
                    if (mOnItemClickedCallback != null) {
                        mOnItemClickedCallback.onResult((int) id);
                    }
                    mMenuWindow.dismiss();
                });

        View decorView = ((Activity) contentView.getContext()).getWindow().getDecorView();
        ViewRectProvider rectProvider = new ViewRectProvider(anchorView);

        mMenuWindow =
                new AnchoredPopupWindow(
                        mContext,
                        decorView,
                        AppCompatResources.getDrawable(mContext, R.drawable.menu_bg_tinted),
                        contentView,
                        rectProvider);
        mMenuWindow.setFocusable(true);
        mMenuWindow.setHorizontalOverlapAnchor(true);
        mMenuWindow.setVerticalOverlapAnchor(true);
        mMenuWindow.setAnimationStyle(R.style.EndIconMenuAnim);
        int popupWidth = mContext.getResources().getDimensionPixelSize(R.dimen.menu_width);
        mMenuWindow.setMaxWidth(popupWidth);

        // When the menu is dismissed, call destroy to unregister the orientation listener.
        mMenuWindow.addOnDismissListener(this::destroy);
    }

    private void display() {
        if (mMenuWindow == null) return;

        mMenuWindow.show();
    }

    private void destroy() {
        mContext.unregisterComponentCallbacks(mComponentCallbacks);
        // If mLifetimeAssert is GC'ed before this is called, it will throw an exception
        // with a stack trace showing the stack during LifetimeAssert.create().
        LifetimeAssert.setSafeToGc(mLifetimeAssert, true);
    }

    private ModelList buildMenuItems() {
        ModelList itemList = new ModelList();
        itemList.add(
                BrowserUiListMenuUtils.buildMenuListItem(
                        R.string.menu_select_tabs, R.id.select_tabs, 0, true));
        itemList.add(
                BrowserUiListMenuUtils.buildMenuListItem(
                        R.string.tab_grid_dialog_toolbar_edit_group_name,
                        R.id.edit_group_name,
                        0,
                        true));
        return itemList;
    }
}
