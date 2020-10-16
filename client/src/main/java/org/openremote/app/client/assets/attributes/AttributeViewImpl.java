/*
 * Copyright 2017, OpenRemote Inc.
 *
 * See the CONTRIBUTORS.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openremote.app.client.assets.attributes;

import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import elemental.client.Browser;
import org.openremote.app.client.widget.*;
import org.openremote.app.client.Environment;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.asset.agent.ConnectionStatus;
import org.openremote.model.attribute.AttributeValueDescriptor;
import org.openremote.model.attribute.AttributeValueType;
import org.openremote.model.attribute.AttributeValidationResult;
import org.openremote.model.interop.Consumer;
import org.openremote.model.value.Value;

import java.util.List;
import java.util.Optional;

/**
 * This is an implementation of {@link AttributeView} that always shows the attribute value with configurable
 * action buttons and also supports view extensions in a collapsible panel.
 */
public class AttributeViewImpl extends FormGroup implements AttributeView {

    protected final Environment environment;
    protected final AttributeView.Style style;
    protected Consumer<Attribute> attributeModifiedCallback;
    protected Attribute attribute;
    protected List<FormButton> attributeActions;
    protected List<AbstractAttributeViewExtension> attributeExtensions;
    protected ConnectionStatus connectionStatus;
    protected boolean editMode;
    protected ValueEditorSupplier valueEditorSupplier;
    protected ValidationErrorConsumer validationErrorConsumer;

    public AttributeViewImpl(Environment environment,
                             AttributeView.Style style,
                             Attribute attribute) {
        this.environment = environment;
        this.style = style;
        this.attribute = attribute;

        addStyleName("flex-none");
        addStyleName(style.attributeView());
        addStyleName(environment.getWidgetStyle().FormListItem());

        showDisabledExtensionToggle();

        FormLabel formLabel = new FormLabel();
        formLabel.addStyleName("larger");
        setFormLabel(formLabel);

        Label infoLabel = new Label();
        addInfolabel(infoLabel);

        FormField formField = new FormField();
        setFormField(formField);

        FormGroupActions formGroupActions = new FormGroupActions();
        setFormGroupActions(formGroupActions);
    }

    public void setAttributeActions(List<FormButton> attributeActions) {
        this.attributeActions = attributeActions;

        if (attributeActions != null) {
            for (FormButton attributeAction : attributeActions) {
                formGroupActions.add(attributeAction);
            }
        }
    }

    public void setAttributeExtensions(List<AbstractAttributeViewExtension> attributeExtensions) {
        this.attributeExtensions = attributeExtensions;
        super.clearExtensions();

        if (attributeExtensions != null && !attributeExtensions.isEmpty()) {
            attributeExtensions.forEach(super::addExtension);
        }
    }

    @Override
    protected void onAttach() {
        super.onAttach();
        refresh();
    }

    @Override
    public Attribute getAttribute() {
        return attribute;
    }

    public List<FormButton> getActionButtons() {
        return attributeActions;
    }

    public List<AbstractAttributeViewExtension> getExtensions() {
        return attributeExtensions;
    }

    @Override
    public void setAttributeModifiedCallback(Consumer<Attribute> attributeModifiedCallback) {
        this.attributeModifiedCallback = attributeModifiedCallback;
    }

    @Override
    public void setValueEditorSupplier(ValueEditorSupplier valueEditorSupplier) {
        this.valueEditorSupplier = valueEditorSupplier;
    }

    @Override
    public void setValidationErrorConsumer(ValidationErrorConsumer validationErrorConsumer) {
        this.validationErrorConsumer = validationErrorConsumer;
    }

    @Override
    public void onValidationStateChange(AttributeValidationResult validationResult) {
        setError(!validationResult.isValid());
        setErrorInExtension(validationResult.getMetaFailures() != null && validationResult.getMetaFailures().size() > 0);

        if (validationResult.hasAttributeFailures() && validationErrorConsumer != null) {
            validationResult.getAttributeFailures().forEach(validationFailure ->
                    validationErrorConsumer.accept(this.attribute.getName().orElse(null), null, validationFailure)
            );
        }

        // Notify extensions
        getExtensions().forEach(viewExtension -> viewExtension.onValidationStateChange(validationResult));
    }

    @Override
    public void onAttributeChanged(long timestamp) {
        if (!editMode) {
            getFormField().clear();
            getFormField().add(createAttributeValueEditor());

            // "Blink" the value editor so users know there might be a new value
            addStyleName(environment.getWidgetStyle().HighlightBackground());
            Browser.getWindow().setTimeout(() -> removeStyleName(environment.getWidgetStyle().HighlightBackground()), 250);

            // Notify extensions
            getExtensions().forEach(ext -> ext.onAttributeChanged(timestamp));
        }
    }

    @Override
    public void setBusy(boolean busy) {

    }

    @Override
    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
        getFormLabel().removeStyleName("larger");
        getFormLabel().removeStyleName("largest");
        if (editMode) {
            getFormLabel().addStyleName("largest");
        } else {
            getFormLabel().addStyleName("larger");
        }
    }

    @Override
    public Widget asWidget() {
        return this;
    }

    @Override
    public void setStatus(ConnectionStatus connectionStatus) {
        this.connectionStatus = connectionStatus;
        refresh();
    }

    protected void notifyAttributeModified(Value newValue) {
        // Push new value into the attribute
        attribute.setValue(newValue, 0);

        if (attributeModifiedCallback != null) {
            attributeModifiedCallback.accept(attribute);
        }
    }

    protected IsWidget createAttributeValueEditor() {
        return valueEditorSupplier.createValueEditor(attribute,
            attribute.getType().map(AttributeValueDescriptor::getValueType).orElse(null),
            style,
            null,
            this::notifyAttributeModified);
    }

    protected String getAttributeLabel() {
        return attribute == null ? "" : (editMode ? attribute.getName() : attribute.getLabelOrName())
            .orElse("");
    }

    protected Optional<String> getAttributeDescription() {
        return attribute == null ? Optional.empty() : attribute.getDescription();
    }

    protected void refresh() {
        removeStyleName(environment.getWidgetStyle().BorderDefault());
        removeStyleName(environment.getWidgetStyle().BorderGreen());
        removeStyleName(environment.getWidgetStyle().BorderYellow());
        removeStyleName(environment.getWidgetStyle().BorderRed());
        getFormLabel().setIcon(null);
        getFormLabel().setText(null);
        getInfoLabel().setText(null);
        getFormField().clear();

        if (attribute == null) {
            return;
        }

        if (connectionStatus != null) {
            switch (connectionStatus) {
                case DISABLED:
                    addStyleName(environment.getWidgetStyle().BorderDefault());
                    break;
                case CONNECTED:
                    addStyleName(environment.getWidgetStyle().BorderGreen());
                    break;
                case WAITING:
                case CONNECTING:
                case DISCONNECTING:
                    addStyleName(environment.getWidgetStyle().BorderYellow());
                    break;
                default:
                    addStyleName(environment.getWidgetStyle().BorderRed());
                    break;
            }
        } else {
            addStyleName(environment.getWidgetStyle().BorderDefault());
        }

        if (attribute.isExecutable()) {
            formLabel.setIcon("cog");
        } else if (attribute.isProtocolConfiguration()) {
            formLabel.setIcon("cogs");
        } else {
            formLabel.setIcon(attribute.getType().map(AttributeValueDescriptor::getIcon).orElse(AttributeValueType.DEFAULT_ICON));
        }

        getFormLabel().setText(getAttributeLabel());

        StringBuilder infoText = new StringBuilder();
        if (attribute.isExecutable()) {
            infoText.append(environment.getMessages().executable());
        } else if (attribute.isProtocolConfiguration()) {
            if (editMode) {
                infoText.append(environment.getMessages().protocolConfiguration());
            } else {
                infoText.append(connectionStatus != null ? connectionStatus.toString() : environment.getMessages().waitingForStatus());
            }
        } else if (attribute.getType().isPresent()) {
            infoText.append(environment.getMessages().attributeValueType(attribute.getTypeOrThrow().getName()));
        }
        getAttributeDescription()
            .ifPresent(description -> {
                if (infoText.length() > 0)
                    infoText.append(" - ");
                infoText.append(description);
            });

        getInfoLabel().setText(infoText.toString());

        getFormField().add(createAttributeValueEditor());
    }
}
