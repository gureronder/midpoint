/*
 * Copyright (c) 2010-2013 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.web.component;

import org.apache.wicket.Component;
import org.apache.wicket.WicketRuntimeException;
import org.apache.wicket.extensions.markup.html.tabs.ITab;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.Loop;
import org.apache.wicket.markup.html.list.LoopItem;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.lang.Args;

import java.io.Serializable;
import java.util.List;

/**
 * @author lazyman
 * @author Igor Vaynberg (ivaynberg)
 */
public class TabbedPanel<T extends ITab> extends Panel {

    /**
     * id used for child panels
     */
    public static final String TAB_PANEL_ID = "panel";

    private final IModel<List<T>> tabs;
    /**
     * the current tab
     */
    private int currentTab = -1;
    private transient VisibilityCache visibilityCache;

    public TabbedPanel(final String id, final List<T> tabs) {
        this(id, tabs, null);
    }

    public TabbedPanel(final String id, final List<T> tabs, IModel<Integer> model) {
        this(id, new Model((Serializable) tabs), model);
    }

    /**
     * Constructor
     *
     * @param id   component id
     * @param tabs list of ITab objects used to represent tabs
     */
    public TabbedPanel(final String id, final IModel<List<T>> tabs) {
        this(id, tabs, null);
    }

    /**
     * Constructor
     *
     * @param id    component id
     * @param tabs  list of ITab objects used to represent tabs
     * @param model model holding the index of the selected tab
     */
    public TabbedPanel(final String id, final IModel<List<T>> tabs, IModel<Integer> model) {
        super(id, model);

        this.tabs = Args.notNull(tabs, "tabs");

        final IModel<Integer> tabCount = new AbstractReadOnlyModel<Integer>() {
            private static final long serialVersionUID = 1L;

            @Override
            public Integer getObject() {
                return tabs.getObject().size();
            }
        };

        WebMarkupContainer tabsContainer = newTabsContainer("tabs-container");
        add(tabsContainer);

        // add the loop used to generate tab names
        tabsContainer.add(new Loop("tabs", tabCount) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void populateItem(final LoopItem item) {
                final int index = item.getIndex();
                final T tab = TabbedPanel.this.tabs.getObject().get(index);

                final WebMarkupContainer titleLink = newLink("link", index);

                titleLink.add(newTitle("title", tab.getTitle(), index));
                item.add(titleLink);
            }

            @Override
            protected LoopItem newItem(final int iteration) {
                return newTabContainer(iteration);
            }
        });

        add(newPanel());
    }

    /**
     * Override of the default initModel behaviour. This component <strong>will not</strong> use any
     * compound model of a parent.
     *
     * @see org.apache.wicket.Component#initModel()
     */
    @Override
    protected IModel<?> initModel() {
        return new Model<Integer>(-1);
    }

    /**
     * Generates the container for all tabs. The default container automatically adds the css
     * <code>class</code> attribute based on the return value of {@link #getTabContainerCssClass()}
     *
     * @param id container id
     * @return container
     */
    protected WebMarkupContainer newTabsContainer(final String id) {
        WebMarkupContainer tabs = new WebMarkupContainer(id);
        tabs.setOutputMarkupId(true);
        return tabs;
    }

    /**
     * Generates a loop item used to represent a specific tab's <code>li</code> element.
     *
     * @param tabIndex
     * @return new loop item
     */
    protected LoopItem newTabContainer(final int tabIndex) {
        return new LoopItem(tabIndex) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onConfigure() {
                super.onConfigure();

                setVisible(getVisiblityCache().isVisible(tabIndex));
            }

            @Override
            protected void onComponentTag(final ComponentTag tag) {
                super.onComponentTag(tag);

                String cssClass = tag.getAttribute("class");
                if (cssClass == null) {
                    cssClass = " ";
                }
                cssClass += " tab" + getIndex();

                if (getIndex() == getSelectedTab()) {
                    cssClass += ' ' + getSelectedTabCssClass();
                }
                if (getVisiblityCache().getLastVisible() == getIndex()) {
                    cssClass += ' ' + getLastTabCssClass();
                }
                tag.put("class", cssClass.trim());
            }
        };
    }

    @Override
    protected void onBeforeRender() {
        int index = getSelectedTab();

        if (index == -1 || getVisiblityCache().isVisible(index) == false) {
            // find first visible tab
            index = -1;
            for (int i = 0; i < tabs.getObject().size(); i++) {
                if (getVisiblityCache().isVisible(i)) {
                    index = i;
                    break;
                }
            }

            if (index != -1) {
                // found a visible tab, so select it
                setSelectedTab(index);
            }
        }

        setCurrentTab(index);

        super.onBeforeRender();
    }


    /**
     * @return the value of css class attribute that will be added to last tab. The default value is
     *         <code>last</code>
     */
    protected String getLastTabCssClass() {
        return "";
    }

    /**
     * @return the value of css class attribute that will be added to a div containing the tabs. The
     *         default value is <code>tab-row</code>
     */
    protected String getTabContainerCssClass() {
        return "tab-row";
    }

    /**
     * @return the value of css class attribute that will be added to selected tab. The default
     *         value is <code>selected</code>
     */
    protected String getSelectedTabCssClass() {
        return "active";
    }

    /**
     * @return list of tabs that can be used by the user to add/remove/reorder tabs in the panel
     */
    public final IModel<List<T>> getTabs() {
        return tabs;
    }

    /**
     * Factory method for tab titles. Returned component can be anything that can attach to span
     * tags such as a fragment, panel, or a label
     *
     * @param titleId    id of tiatle component
     * @param titleModel model containing tab title
     * @param index      index of tab
     * @return title component
     */
    protected Component newTitle(final String titleId, final IModel<?> titleModel, final int index) {
        Label label = new Label(titleId, titleModel);
        label.setRenderBodyOnly(true);
        return label;
    }

    /**
     * Factory method for links used to switch between tabs.
     * <p/>
     * The created component is attached to the following markup. Label component with id: title
     * will be added for you by the tabbed panel.
     * <p/>
     * <pre>
     * &lt;a href=&quot;#&quot; wicket:id=&quot;link&quot;&gt;&lt;span wicket:id=&quot;title&quot;&gt;[[tab title]]&lt;/span&gt;&lt;/a&gt;
     * </pre>
     * <p/>
     * Example implementation:
     * <p/>
     * <pre>
     * protected WebMarkupContainer newLink(String linkId, final int index)
     * {
     * 	return new Link(linkId)
     *    {
     * 		private static final long serialVersionUID = 1L;
     *
     * 		public void onClick()
     *        {
     * 			setSelectedTab(index);
     *        }
     *    };
     * }
     * </pre>
     *
     * @param linkId component id with which the link should be created
     * @param index  index of the tab that should be activated when this link is clicked. See
     *               {@link #setSelectedTab(int)}.
     * @return created link component
     */
    protected WebMarkupContainer newLink(final String linkId, final int index) {
        return new Link<Void>(linkId) {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick() {
                setSelectedTab(index);
            }
        };
    }

    /**
     * sets the selected tab
     *
     * @param index index of the tab to select
     * @return this for chaining
     * @throws IndexOutOfBoundsException if index is not in the range of available tabs
     */
    public TabbedPanel<T> setSelectedTab(final int index) {
        if ((index < 0) || (index >= tabs.getObject().size())) {
            throw new IndexOutOfBoundsException();
        }

        setDefaultModelObject(index);

        // force the tab's component to be aquired again if already the current tab
        currentTab = -1;
        setCurrentTab(index);

        return this;
    }

    private void setCurrentTab(int index) {
        if (this.currentTab == index) {
            // already current
            return;
        }
        this.currentTab = index;

        final Component component;

        if (currentTab == -1 || (tabs.getObject().size() == 0) || !getVisiblityCache().isVisible(currentTab)) {
            // no tabs or the current tab is not visible
            component = newPanel();
        } else {
            // show panel from selected tab
            T tab = tabs.getObject().get(currentTab);
            component = tab.getPanel(TAB_PANEL_ID);
            if (component == null) {
                throw new WicketRuntimeException("ITab.getPanel() returned null. TabbedPanel [" +
                        getPath() + "] ITab index [" + currentTab + "]");
            }
        }

        if (!component.getId().equals(TAB_PANEL_ID)) {
            throw new WicketRuntimeException(
                    "ITab.getPanel() returned a panel with invalid id [" +
                            component.getId() +
                            "]. You must always return a panel with id equal to the provided panelId parameter. TabbedPanel [" +
                            getPath() + "] ITab index [" + currentTab + "]");
        }

        addOrReplace(component);
    }

    private WebMarkupContainer newPanel() {
        return new WebMarkupContainer(TAB_PANEL_ID);
    }

    /**
     * @return index of the selected tab
     */
    public final int getSelectedTab() {
        return (Integer) getDefaultModelObject();
    }

    @Override
    protected void onDetach() {
        visibilityCache = null;

        super.onDetach();
    }

    private VisibilityCache getVisiblityCache() {
        if (visibilityCache == null) {
            visibilityCache = new VisibilityCache();
        }

        return visibilityCache;
    }

    /**
     * A cache for visibilities of {@link ITab}s.
     */
    private class VisibilityCache {

        /**
         * Visibility for each tab.
         */
        private Boolean[] visibilities;

        /**
         * Last visible tab.
         */
        private int lastVisible = -1;

        public VisibilityCache() {
            visibilities = new Boolean[tabs.getObject().size()];
        }

        public int getLastVisible() {
            if (lastVisible == -1) {
                for (int t = 0; t < tabs.getObject().size(); t++) {
                    if (isVisible(t)) {
                        lastVisible = t;
                    }
                }
            }

            return lastVisible;
        }

        public boolean isVisible(int index) {
            if (visibilities.length < index + 1) {
                Boolean[] resized = new Boolean[index + 1];
                System.arraycopy(visibilities, 0, resized, 0, visibilities.length);
                visibilities = resized;
            }

            if (visibilities.length > 0) {
                Boolean visible = visibilities[index];
                if (visible == null) {
                    visible = tabs.getObject().get(index).isVisible();
                    visibilities[index] = visible;
                }
                return visible;
            } else {
                return false;
            }
        }
    }
}
