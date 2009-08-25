/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xpn.xwiki.wysiwyg.client.plugin.macro.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xwiki.gwt.dom.client.Document;
import org.xwiki.gwt.dom.client.Element;

import com.google.gwt.dom.client.Node;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.IncrementalCommand;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.xpn.xwiki.wysiwyg.client.WysiwygService;
import com.xpn.xwiki.wysiwyg.client.editor.Strings;
import com.xpn.xwiki.wysiwyg.client.plugin.macro.MacroCall;
import com.xpn.xwiki.wysiwyg.client.plugin.macro.MacroDescriptor;
import com.xpn.xwiki.wysiwyg.client.util.Config;
import com.xpn.xwiki.wysiwyg.client.util.DeferredUpdater;
import com.xpn.xwiki.wysiwyg.client.util.Updatable;
import com.xpn.xwiki.wysiwyg.client.widget.LabeledTextBox;
import com.xpn.xwiki.wysiwyg.client.widget.ListBox;
import com.xpn.xwiki.wysiwyg.client.widget.ListItem;
import com.xpn.xwiki.wysiwyg.client.widget.VerticalResizePanel;
import com.xpn.xwiki.wysiwyg.client.widget.wizard.NavigationListener;
import com.xpn.xwiki.wysiwyg.client.widget.wizard.NavigationListenerCollection;
import com.xpn.xwiki.wysiwyg.client.widget.wizard.SourcesNavigationEvents;
import com.xpn.xwiki.wysiwyg.client.widget.wizard.NavigationListener.NavigationDirection;

/**
 * Wizard step for selecting one of the available macros.
 * 
 * @version $Id$
 */
public class SelectMacroWizardStep extends AbstractNavigationAwareWizardStep implements DoubleClickHandler,
    KeyUpHandler, SourcesNavigationEvents, Updatable
{
    /**
     * Creates the macro list items that will fill the macro list. The user will be able to filter this list items by
     * category or by a search query.
     */
    private class CreateMacroListItemsCommand implements IncrementalCommand
    {
        /**
         * The list of macro descriptors.
         */
        private final List<MacroDescriptor> descriptors;

        /**
         * The macro list items grouped by category.
         */
        private final Map<String, List<ListItem<MacroDescriptor>>> itemsByCategory =
            new HashMap<String, List<ListItem<MacroDescriptor>>>();

        /**
         * Creates a new incremental command for creating the macro list items based on the given descriptors.
         * 
         * @param descriptors the list of macro descriptors
         */
        public CreateMacroListItemsCommand(List<MacroDescriptor> descriptors)
        {
            this.descriptors = descriptors;
            itemsByCategory.put(CATEGORY_ALL, new ArrayList<ListItem<MacroDescriptor>>());
            itemsByCategory.put(CATEGORY_USED, new ArrayList<ListItem<MacroDescriptor>>());
        }

        /**
         * {@inheritDoc}
         * 
         * @see IncrementalCommand#execute()
         */
        public boolean execute()
        {
            int step = 10;
            List<ListItem<MacroDescriptor>> items = itemsByCategory.get(CATEGORY_ALL);
            while (items.size() < descriptors.size() && step-- > 0) {
                ListItem<MacroDescriptor> item = createMacroListItem(descriptors.get(items.size()));
                items.add(item);
                getItemsForCategory(item.getData().getCategory()).add(item);
            }
            if (items.size() < descriptors.size()) {
                return true;
            } else {
                macroListItemsByCategory = itemsByCategory;
                macroDescriptorsCallback = null;
                macroFilter.setCategories(macroListItemsByCategory.keySet());
                if (initCallback != null) {
                    initCallback.onSuccess(null);
                }
                updater.deferUpdate();
                return false;
            }
        }

        /**
         * @param category a macro category or {@code null} for {@link SelectMacroWizardStep#CATEGORY_OTHER}
         * @return the macro list items in the specified category
         */
        private List<ListItem<MacroDescriptor>> getItemsForCategory(String category)
        {
            String cat = category == null ? CATEGORY_OTHER : category;
            List<ListItem<MacroDescriptor>> items = itemsByCategory.get(cat);
            if (items == null) {
                items = new ArrayList<ListItem<MacroDescriptor>>();
                itemsByCategory.put(cat, items);
            }
            return items;
        }
    }

    /**
     * A composite of widgets that allow the user to filter the macros.
     */
    private class MacroFilter extends Composite implements ChangeHandler, KeyUpHandler
    {
        /**
         * The list box displaying the available macro categories. The user can filter the macros by category.
         */
        private final com.google.gwt.user.client.ui.ListBox categoryList;

        /**
         * Allows the user to search for a macro in the current category.
         */
        private final LabeledTextBox searchBox;

        /**
         * Creates a new macro filter.
         */
        public MacroFilter()
        {
            categoryList = new com.google.gwt.user.client.ui.ListBox(false);
            categoryList.addStyleName("xMacroCategoryList");
            categoryList.addChangeHandler(this);

            searchBox = new LabeledTextBox(Strings.INSTANCE.quickSearch());
            searchBox.addStyleName("xSearchBox");
            searchBox.addKeyUpHandler(this);

            FlowPanel container = new FlowPanel();
            container.addStyleName("xMacroFilter");
            container.add(categoryList);
            container.add(searchBox);

            initWidget(container);
        }

        /**
         * {@inheritDoc}
         * 
         * @see ChangeHandler#onChange(ChangeEvent)
         */
        public void onChange(ChangeEvent event)
        {
            if (event.getSource() == categoryList) {
                updater.deferUpdate();
            }
        }

        /**
         * {@inheritDoc}
         * 
         * @see KeyUpHandler#onKeyUp(KeyUpEvent)
         */
        public void onKeyUp(KeyUpEvent event)
        {
            if (event.getSource() == searchBox) {
                updater.deferUpdate();
            }
        }

        /**
         * @return the selected macro category
         */
        public String getCategory()
        {
            return categoryList.getValue(categoryList.getSelectedIndex());
        }

        /**
         * @return the text from the search box
         */
        public String getSearchText()
        {
            return searchBox.getValue();
        }

        /**
         * Fills the drop down list of categories based on the given category set.
         * 
         * @param categoryInputSet the set of categories
         */
        public void setCategories(Set<String> categoryInputSet)
        {
            // The input category set could be immutable so we should use a new set.
            Set<String> categorySet = new HashSet<String>(categoryInputSet);
            // All and Used categories should be the first two in the list of options, so don't sort them.
            categorySet.remove(CATEGORY_ALL);
            categorySet.remove(CATEGORY_USED);

            // Sort the categories.
            List<String> categories = new ArrayList<String>();
            categories.addAll(categorySet);
            Collections.sort(categories);

            categoryList.clear();
            categoryList.addItem(Strings.INSTANCE.macroCategoryAll(), CATEGORY_ALL);
            categoryList.addItem(Strings.INSTANCE.macroCategoryUsed(), CATEGORY_USED);
            for (String category : categories) {
                String label = CATEGORY_OTHER.equals(category) ? Strings.INSTANCE.macroCategoryOther() : category;
                categoryList.addItem(label, category);
            }

            // Group all real categories (the categories that were sorted).
            Node select = categoryList.getElement();
            Element group = ((Document) select.getOwnerDocument()).xCreateElement("optgroup");
            group.setAttribute("label", Strings.INSTANCE.macroCategories());
            while (select.getChildNodes().getLength() > 2) {
                group.appendChild(select.getChildNodes().getItem(2));
            }
            select.appendChild(group);

            // Select initially the All category.
            categoryList.setSelectedIndex(0);
        }
    }

    /**
     * The category that includes all the macros.
     */
    private static final String CATEGORY_ALL = "__all";

    /**
     * The category that includes all the macros that don't specify a category.
     */
    private static final String CATEGORY_OTHER = "__other";

    /**
     * The category that includes all the top level macros used in the edited document.
     */
    private static final String CATEGORY_USED = "__used";

    /**
     * The call-back used to notify the wizard that this wizard step has finished loading.
     */
    private AsyncCallback< ? > initCallback;

    /**
     * The object called back when the macro descriptors are received. A request for macro descriptors is pending
     * whenever this object is not null.
     */
    private AsyncCallback<List<MacroDescriptor>> macroDescriptorsCallback;

    /**
     * The macro list items grouped by category for quick display.
     */
    private Map<String, List<ListItem<MacroDescriptor>>> macroListItemsByCategory;

    /**
     * The list of macros that have been inserted in the edited document.
     */
    private List<String> usedMacroIds;

    /**
     * The list box displaying the available macros. Each list item has a macro id associated.
     */
    private final ListBox<MacroDescriptor> macroList;

    /**
     * The panel containing the widgets for filtering the macros.
     */
    private final MacroFilter macroFilter;

    /**
     * Holds the text displayed after the validation, if this wizard step can't be submitted.
     */
    private final Label validationMessage;

    /**
     * The list of navigation listeners. This wizard step generates navigation events when a user chooses a macro by
     * double clicking on it or by pressing the Enter key.
     */
    private final NavigationListenerCollection navigationListeners = new NavigationListenerCollection();

    /**
     * Schedules updates and executes only the most recent one.
     */
    private final DeferredUpdater updater = new DeferredUpdater(this);

    /**
     * Creates a new wizard step for selecting one of the available macros.
     * 
     * @param config the object used to configure the newly created wizard step
     */
    public SelectMacroWizardStep(Config config)
    {
        super(config, new VerticalResizePanel());

        macroFilter = new MacroFilter();
        getPanel().add(macroFilter);

        validationMessage = new Label(Strings.INSTANCE.macroNoMacroSelected());
        validationMessage.setVisible(false);
        validationMessage.addStyleName("xMacroSelectorError");
        getPanel().add(validationMessage);

        macroList = new ListBox<MacroDescriptor>();
        macroList.addDoubleClickHandler(this);
        macroList.addKeyUpHandler(this);
        getPanel().add(macroList);

        getPanel().addStyleName("xMacroSelector");
        ((VerticalResizePanel) getPanel()).setExpandingWidget(macroList, false);
    }

    /**
     * {@inheritDoc}
     * 
     * @see AbstractNavigationAwareWizardStep#getResult()
     */
    public Object getResult()
    {
        if (macroList.getSelectedItem() != null) {
            MacroCall macroCall = new MacroCall();
            macroCall.setName(macroList.getSelectedItem().getData().getId());
            return macroCall;
        } else {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see AbstractNavigationAwareWizardStep#getStepTitle()
     */
    public String getStepTitle()
    {
        return Strings.INSTANCE.macroInsertDialogTitle();
    }

    /**
     * {@inheritDoc}
     * 
     * @see AbstractNavigationAwareWizardStep#init(Object, AsyncCallback)
     */
    @SuppressWarnings("unchecked")
    public void init(Object data, AsyncCallback< ? > initCallback)
    {
        // Save the initialization call-back to be able to notify the wizard when we're done.
        this.initCallback = initCallback;

        // Save the list of used macros in order to update the corresponding category later, when the user choose it.
        // Check if the data is a list since this method can be called after a "Back" action.
        usedMacroIds = data instanceof List ? (List<String>) data : null;

        if (macroListItemsByCategory != null) {
            // Macro descriptors have been received.
            // If we have a list of used macros and the current category is CATEGORY_USED then trigger an update.
            if (usedMacroIds != null && CATEGORY_USED.equals(macroFilter.getCategory())) {
                updater.deferUpdate();
            } else {
                // Remove any validation messages.
                setValid(true);
            }
            initCallback.onSuccess(null);
        } else if (macroDescriptorsCallback == null) {
            // There's no pending request for macro descriptors.
            macroDescriptorsCallback = new AsyncCallback<List<MacroDescriptor>>()
            {
                public void onFailure(Throwable caught)
                {
                    macroDescriptorsCallback = null;
                    // Notify the last initialization call-back.
                    SelectMacroWizardStep.this.initCallback.onFailure(caught);
                }

                public void onSuccess(List<MacroDescriptor> result)
                {
                    if (result != null) {
                        DeferredCommand.addCommand(new CreateMacroListItemsCommand(result));
                    } else {
                        macroDescriptorsCallback = null;
                    }
                }
            };
            WysiwygService.Singleton.getInstance().getMacroDescriptors(getSyntax(), macroDescriptorsCallback);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see AbstractNavigationAwareWizardStep#onCancel()
     */
    public void onCancel()
    {
        initCallback = null;
    }

    /**
     * {@inheritDoc}
     * 
     * @see AbstractNavigationAwareWizardStep#onSubmit(AsyncCallback)
     */
    public void onSubmit(AsyncCallback<Boolean> async)
    {
        async.onSuccess(validate());
    }

    /**
     * {@inheritDoc}
     * 
     * @see DoubleClickHandler#onDoubleClick(DoubleClickEvent)
     */
    public void onDoubleClick(DoubleClickEvent event)
    {
        if (event.getSource() == macroList && macroList.getSelectedItem() != null) {
            navigationListeners.fireNavigationEvent(NavigationDirection.NEXT);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see KeyUpHandler#onKeyUp(KeyUpEvent)
     */
    public void onKeyUp(KeyUpEvent event)
    {
        if (event.getSource() == macroList && event.getNativeKeyCode() == KeyCodes.KEY_ENTER
            && macroList.getSelectedItem() != null) {
            navigationListeners.fireNavigationEvent(NavigationDirection.NEXT);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see SourcesNavigationEvents#addNavigationListener(NavigationListener)
     */
    public void addNavigationListener(NavigationListener listener)
    {
        navigationListeners.add(listener);
    }

    /**
     * {@inheritDoc}
     * 
     * @see SourcesNavigationEvents#removeNavigationListener(NavigationListener)
     */
    public void removeNavigationListener(NavigationListener listener)
    {
        navigationListeners.remove(listener);
    }

    /**
     * {@inheritDoc}
     * 
     * @see Updatable#canUpdate()
     */
    public boolean canUpdate()
    {
        return true;
    }

    /**
     * {@inheritDoc}<br/>
     * Updates the {@link #macroList} based on the chosen category and specified search query.
     * 
     * @see Updatable#update()
     */
    public void update()
    {
        if (usedMacroIds != null && CATEGORY_USED.equals(macroFilter.getCategory())) {
            updateUsedMacroCategory();
        }
        List<ListItem<MacroDescriptor>> items = macroListItemsByCategory.get(macroFilter.getCategory());
        macroList.clear();
        setValid(true);
        String searchText = macroFilter.getSearchText();
        if (searchText != null && searchText.length() > 0) {
            searchText = searchText.toLowerCase();
            for (ListItem<MacroDescriptor> item : items) {
                if (macroMatchesSearchQuery(item.getData(), searchText)) {
                    macroList.addItem(item);
                }
            }
        } else {
            for (ListItem<MacroDescriptor> item : items) {
                macroList.addItem(item);
            }
        }
    }

    /**
     * Checks if the name or the description of the specified macro contains the given search text.
     * 
     * @param descriptor a macro descriptor
     * @param searchText the text to look for in the macro descriptor
     * @return {@code true} if the given macro descriptor matches the specified search query, {@code false} otherwise
     */
    private boolean macroMatchesSearchQuery(MacroDescriptor descriptor, String searchText)
    {
        return (descriptor.getName() != null && descriptor.getName().toLowerCase().contains(searchText))
            || (descriptor.getDescription() != null && descriptor.getDescription().toLowerCase().contains(searchText));
    }

    /**
     * @return the string identifier for the storage syntax
     */
    private String getSyntax()
    {
        return getConfig().getParameter("syntax");
    }

    /**
     * Creates a macro list item to display information about a macro, information taken from the given macro
     * descriptor.
     * 
     * @param descriptor the object describing the macro
     * @return the newly created list item
     */
    private ListItem<MacroDescriptor> createMacroListItem(MacroDescriptor descriptor)
    {
        Label name = new Label(descriptor.getName());
        name.addStyleName("xMacroLabel");

        Label description = new Label(descriptor.getDescription());
        description.addStyleName("xMacroDescription");

        ListItem<MacroDescriptor> item = new ListItem<MacroDescriptor>();
        item.setData(descriptor);
        item.addStyleName("xMacro");
        item.add(name);
        item.add(description);

        return item;
    }

    /**
     * Updates the list of used macros in {@link #macroListItemsByCategory} based on {@link #usedMacroIds}.
     */
    private void updateUsedMacroCategory()
    {
        List<ListItem<MacroDescriptor>> allItems = macroListItemsByCategory.get(CATEGORY_ALL);
        List<ListItem<MacroDescriptor>> items = macroListItemsByCategory.get(CATEGORY_USED);
        items.clear();
        for (ListItem<MacroDescriptor> item : allItems) {
            for (String macroId : usedMacroIds) {
                if (item.getData().getId().equalsIgnoreCase(macroId)) {
                    items.add(item);
                }
            }
        }
        // Prevent further updates till this wizard step is not re-initialized.
        usedMacroIds = null;
    }

    /**
     * Validates this wizard step, showing error messages near the fields that don't validate.
     * 
     * @return {@code true} if this wizard step can be submitted, {@code false} otherwise
     */
    private boolean validate()
    {
        boolean valid = macroList.getSelectedItem() != null;
        setValid(valid);
        return valid;
    }

    /**
     * Marks this wizard step as valid or invalid. When the wizard step is invalid a validation message is shown.
     * 
     * @param valid {@code true} if the wizard step ca be submitted, {@code false} otherwise
     */
    private void setValid(boolean valid)
    {
        if (valid == validationMessage.isVisible()) {
            validationMessage.setVisible(!valid);
            ((VerticalResizePanel) getPanel()).refreshHeights();
        }
    }
}
