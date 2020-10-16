/*
 * Copyright 2016, OpenRemote Inc.
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
package org.openremote.app.client.i18n;

import com.google.gwt.i18n.client.LocalizableResource;
import com.google.gwt.i18n.client.Messages;
import jsinterop.annotations.JsType;
import org.openremote.app.client.notifications.FilterOptions;
import org.openremote.app.client.notifications.NotificationEditorImpl;
import org.openremote.model.rules.RulesEngineStatus;

@LocalizableResource.DefaultLocale
@JsType
public interface ManagerMessages extends Messages {

    String logout();

    String map();

    String assets();

    String rules();

    String apps();

    String admin();

    String lastModifiedOn();

    String loadingDotdotdot();

    String editAccount();

    String manageTenants();

    String manageUsers();

    String createTenant();

    String tenantName();

    String realm();

    String enabled();

    String continueOnError();

    String editTenant();

    String tenantDisplayName();

    String updateTenant();

    String deleteTenant();

    String OK();

    String cancel();

    String close();

    String filter();

    String search();

    String newUser();

    String selectTenant();

    String username();

    String firstName();

    String lastName();

    String editUser();

    String updateUser();

    String createUser();

    String deleteUser();

    String email();

    String resetPassword();

    String repeatPassword();

    String notePasswordAfterCreate();

    String accessDenied(String requiredRole);

    String conflictRequest();

    String tenantCreated(String displayName);

    String tenantUpdated(String displayName);

    String tenantDeleted(String displayName);

    String userDeleted(String username);

    String userCreated(String username);

    String userUpdated(String username);

    String passwordUpdated();

    String passwordsMustMatch();

    String roleLabel(@Select String roleName);

    String assignedRoles();

    String noteRolesAfterCreate();

    String showMoreAssets();

    String emptyAsset();

    String description();

    String unsupportedAttributeType(String name);

    String unsupportedMetaItemType(String name);

    String unsupportedValueType(String name);

    String loadingAssets();

    String assetName();

    String updateAsset();

    String createAsset();

    String deleteAsset();

    String assetType();

    String createdOn();

    String sentOn();

    String deliveredOn();

    String acknowledgedOn();

    String acknowledgement();

    String error();

    String selectedLocation();

    String assetCreated(String name);

    String assetUpdated(String name);

    String assetDeleted(String name);

    String location();

    String selectLocation();

    String confirmation();

    String confirmationDelete(String label);

    String parentAsset();

    String selectAsset();

    String selectAssetDescription();

    String invalidAssetParent();

    String centerMap();

    String showHistory();

    String sort();

    String assetTypeLabel(@Select String name);

    String enterCustomAssetType();

    String read();

    String write();

    String noAgentsFound();

    String deleteAttribute();

    String addAttribute();

    String itemName();

    String deleteItem();

    String newMetaItems();

    String addItem();

    String type();

    String value();

    String metaItems();

    String protocolLinks();

    String protocolLinkDiscovery();

    String protocolLinkDiscoveryParent();

    String uploadProtocolFile();

    String discoverDevices();

    String importInProgress();

    String discoveryInProgress();

    String or();

    String and();

    String customItem();

    String enterCustomAttributeMetaName();

    String selectType();

    String valueTypeDisplayName(@Select String valueType);

    String metaItemDisplayName(@Select String metaItemName);

    String attributeDeleted(String name);

    String attributeAdded(String name);

    String newAttribute();

    String attributes();

    String attributeName();

    String fullscreen();

    String selectConsoleApp();

    String manageGlobalRulesets();

    String manageTenantRulesets();

    String newRuleset();

    String rulesetName();

    String editGlobalRules();

    String manageTenantAssets();

    String manageAssetRulesets();

    String uploadRulesFile();

    String updateRuleset();

    String createRuleset();

    String deleteRuleset();

    String rulesetCreated(String name);

    String rulesetDeleted(String name);

    String rulesetUpdated(String name);

    String downloadRulesFile();

    String editTenantRuleset();

    String editAssetRuleset();

    String attributeWriteSent(String name);

    String duplicateAttributeName();

    String invalidAttributeName();

    String invalidAttributeType();

    String editAsset();

    String viewAsset();

    String errorLoadingTenant(double statusCode);

    String subscriptionFailed(String eventType);

    String datapointInterval(@Select String name);

    String showChartAggregatedFor();

    String historicalData();

    String canvasNotSupported();

    String previous();

    String next();

    String noRulesetsFound();

    String noTenantsFound();

    String noUserFound();

    String showLiveUpdates();

    String refreshAllAttributes();

    String start();

    String repeat();

    String getStatus();

    String commandRequestSent(String name);

    String attributeValueType(@Select String name);

    String validationFailureOnAttribute(String attributeName);

    String validationFailureOnMetaItem(String attributeName, String metaItemName);

    String validationFailure(@Optional String parameter, @Select String name);

    String validationFailureParameter(@Select String parameter);

    String syslog();

    String pauseLog();

    String continueLog();

    String clear();

    String noLogMessagesReceived();

    String jsonObject();

    String jsonArray();

    String edit();

    String emptyJsonData();

    String reset();

    String executable();

    String noAssetSelected();

    String clearSelection();

    String protocolConfiguration();

    String showLast();

    String events();

    String store();

    String eventsFor();

    String saveSettings();

    String removeAll();

    String minutes();

    String hours();

    String days();

    String eventsRemoved();

    String settingsSaved();

    String noAttributes();

    String simulator();

    String invalidValues();

    String writeSimulatorState();

    String simulatorStateSubmitted();

    String selectAgent();

    String selectAttribute();

    String selectProtocolConfiguration();

    String protocolLinkDiscoveryStarted();

    String protocolLinkDiscoverySuccess(int assetCount);

    String protocolLinkDiscoveryFailure(int failureCode);

    String protocolLinkImportStarted();

    String protocolLinkImportSuccess(int assetCount);

    String protocolLinkImportFailure(int failureCode);

    String attributeLinkConverterValues();

    String attributeLinkNewConverterValue();

    String enterConverterValue();

    String selectConverter();

    String customConverter();

    String converterType(@Select String type);

    String addConverterValue();

    String deleteConverterValue();

    String linkAssetUsers();

    String waitingForStatus();

    String noAttributesLinkedToSimulator();

    String noRegisteredDevices();

    String registeredDeviceId();

    String deleteRegistration();

    String registeredDeviceType();

    String registeredDeviceLastLogin();

    String registeredDeviceNotificationToken();

    String registeredDeviceDeleted(String deviceId);

    String sendNotification();

    String title();

    String message();

    String notificationActionUrl();

    String notificationSendFailure(String errorMsg);

    String notificationSentSuccessfully();

    String notificationMessageMissing();

    String notificationTargetMissing();

    String notificationOpenApplicationDetails();

    String withUser();

    String linkAsset();

    String createAssetLink();

    String noUserSelected();

    String noAssetUserLinks();

    String asset();

    String deleteLink();

    String linkedToUser();

    String userAssetLinkCreated();

    String userAssetLinkDeleted();

    String assetNotInTenant(String displayName);

    String notifications();

    String allUsers();

    String noNotifications();

    String notificationDeleted(double id);

    String deleteNotification();

    String status();

    String action();

    String buttons();

    String notificationsDeleted();

    String refresh();

    String deleteNotifications();

    String refreshDeviceRegistrations();

    String tenant();

    String publicAccess();

    String shareLink();

    String noAppsFound();

    String language();

    String confirmationRulesLangChange();

    String targets();

    String sentIn();

    String targetType();

    String notificationType(@Select String type);

    String targetTypes(@Select String type);

    String sentInLast(@Select FilterOptions.SentInLast sentInLast);

    String body();

    String data();

    String notificationData();

    String notificationActionType(@Select NotificationEditorImpl.ActionType type);

    String none();

    String notificationBuildError();

    String engineStatusLabel();

    String engineStatus(@Select RulesEngineStatus status);

    String compilationErrorCount();

    String executionErrorCount();
}
