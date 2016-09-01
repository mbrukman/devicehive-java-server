package com.devicehive.websockets.handlers;


import com.devicehive.auth.HivePrincipal;
import com.devicehive.configuration.Constants;
import com.devicehive.configuration.Messages;
import com.devicehive.exceptions.HiveException;
import com.devicehive.json.strategies.JsonPolicyDef;
import com.devicehive.messages.handler.ClientHandler;
import com.devicehive.messages.handler.WebSocketClientHandler;
import com.devicehive.messages.handler.WebsocketHandlerCreator;
import com.devicehive.messages.subscriptions.NotificationSubscription;
import com.devicehive.messages.subscriptions.SubscriptionManager;
import com.devicehive.model.DeviceNotification;
import com.devicehive.model.wrappers.DeviceNotificationWrapper;
import com.devicehive.service.DeviceNotificationService;
import com.devicehive.service.DeviceService;
import com.devicehive.util.ServerResponsesFactory;
import com.devicehive.vo.DeviceVO;
import com.devicehive.websockets.HiveWebsocketSessionState;
import com.devicehive.websockets.InsertNotification;
import com.devicehive.websockets.converters.WebSocketResponse;
import com.devicehive.websockets.handlers.annotations.Action;
import com.devicehive.websockets.handlers.annotations.WsParam;
import com.devicehive.websockets.util.AsyncMessageSupplier;
import com.devicehive.websockets.util.SubscriptionSessionMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.devicehive.configuration.Constants.*;
import static com.devicehive.json.strategies.JsonPolicyDef.Policy.NOTIFICATION_FROM_DEVICE;
import static com.devicehive.json.strategies.JsonPolicyDef.Policy.NOTIFICATION_TO_DEVICE;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;

@Component
public class NotificationHandlers extends WebsocketHandlers {
    private static final Logger logger = LoggerFactory.getLogger(NotificationHandlers.class);

    @Autowired
    private SubscriptionManager subscriptionManager;
    @Autowired
    private DeviceService deviceService;
    @Autowired
    private AsyncMessageSupplier asyncMessageDeliverer;
    @Autowired
    private SubscriptionSessionMap subscriptionSessionMap;
    @Autowired
    private DeviceNotificationService notificationService;
    @Autowired
    private AsyncMessageSupplier messageSupplier;

    @Action(value = "notification/subscribe")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENT', 'KEY') and hasPermission(null, 'GET_DEVICE_NOTIFICATION')")
    public WebSocketResponse processNotificationSubscribe(@WsParam(TIMESTAMP) Date timestamp,
                                                          @WsParam(DEVICE_GUIDS) Set<String> devices,
                                                          @WsParam(NAMES) Set<String> names,
                                                          @WsParam(DEVICE_GUID) String deviceId,
                                                          WebSocketSession session) throws Exception {
        Assert.notEmpty(devices);
        logger.debug("notification/subscribe requested for devices: {}, {}. Timestamp: {}. Names {} Session: {}",
                devices, deviceId, timestamp, names, session.getId());

        ClientHandler clientHandler = new WebSocketClientHandler(session, asyncMessageDeliverer);

        devices = prepareActualList(devices, deviceId);
        Pair<String, Set<DeviceNotification>> result
                = notificationService.submitDeviceSubscribeNotification(devices, names, timestamp, clientHandler);

        logger.debug("notification/subscribe done for devices: {}, {}. Timestamp: {}. Names {} Session: {}",
                devices, deviceId, timestamp, names, session.getId());

        WebSocketResponse response = new WebSocketResponse();
        response.addValue(SUBSCRIPTION_ID, result.getKey(), null);
        return response;
    }

    private Set<String> prepareActualList(Set<String> deviceIdSet, final String deviceId) {
        if (deviceId == null && deviceIdSet == null) {
            return null;
        }
        if (deviceIdSet != null && deviceId == null) {
            deviceIdSet.remove(null);
            return deviceIdSet;
        }

        if (deviceIdSet == null) {
            return new HashSet<String>() {
                {
                    add(deviceId);
                }

                private static final long serialVersionUID = 955343867580964077L;
            };

        }
        throw new HiveException(Messages.INVALID_REQUEST_PARAMETERS, SC_BAD_REQUEST);
    }

    /**
     * Implementation of the <a href="http://www.devicehive.com/restful#WsReference/Client/notificationunsubscribe">
     * WebSocket API: Client: notification/unsubscribe</a> Unsubscribes from device notifications.
     *
     * @param session Current session
     * @return Json object with the following structure <code> { "action": {string}, "status": {string}, "requestId":
     * {object} } </code>
     */
    @Action(value = "notification/unsubscribe")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENT', 'KEY') and hasPermission(null, 'GET_DEVICE_NOTIFICATION')")
    public WebSocketResponse processNotificationUnsubscribe(@WsParam(SUBSCRIPTION_ID) UUID subId,
                                                            @WsParam(DEVICE_GUIDS) Set<String> deviceGuids,
                                                            WebSocketSession session) {
        logger.debug("notification/unsubscribe action. Session {} ", session.getId());
        if (subId == null && deviceGuids == null) {
            Set<String> subForAll = new HashSet<String>() {
                {
                    add(Constants.NULL_SUBSTITUTE);
                }

                private static final long serialVersionUID = 8001668138178383978L;
            };
            notificationService.submitNotificationUnsubscribe(null, subForAll);
        } else if (subId != null) {
            notificationService.submitNotificationUnsubscribe(subId.toString(), deviceGuids);
        } else {
            notificationService.submitNotificationUnsubscribe(null, deviceGuids);
        }
        logger.debug("notification/unsubscribe completed for session {}", session.getId());
        return new WebSocketResponse();
    }

    @Action("notification/insert")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENT', 'KEY') and hasPermission(null, 'CREATE_DEVICE_NOTIFICATION')")
    public WebSocketResponse processNotificationInsert(@WsParam(DEVICE_GUID) String deviceGuid,
                                                       @WsParam(NOTIFICATION)
                                                       @JsonPolicyDef(NOTIFICATION_FROM_DEVICE)
                                                               DeviceNotificationWrapper notificationSubmit,
                                                       WebSocketSession session) {
        logger.debug("notification/insert requested. Session {}. Guid {}", session, deviceGuid);
        HivePrincipal principal = (HivePrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (notificationSubmit == null || notificationSubmit.getNotification() == null) {
            logger.debug(
                    "notification/insert proceed with error. Bad notification: notification is required.");
            throw new HiveException(Messages.NOTIFICATION_REQUIRED, SC_BAD_REQUEST);
        }

        DeviceVO device;
        if (deviceGuid == null) {
            device = principal.getDevice();
        } else {
            device = deviceService.findByGuidWithPermissionsCheck(deviceGuid, principal);
        }
        if (device == null) {
            logger.debug("notification/insert canceled for session: {}. Guid is not provided", session);
            throw new HiveException(Messages.DEVICE_GUID_REQUIRED, SC_FORBIDDEN);
        }
        if (device.getNetwork() == null) {
            logger.debug("notification/insert. No network specified for device with guid = {}", deviceGuid);
            throw new HiveException(String.format(Messages.DEVICE_IS_NOT_CONNECTED_TO_NETWORK, deviceGuid), SC_FORBIDDEN);
        }
        DeviceNotification message = notificationService.convertToMessage(notificationSubmit, device);
        notificationService.submitDeviceNotification(message, device);
        logger.debug("notification/insert proceed successfully. Session {}. Guid {}", session, deviceGuid);

        WebSocketResponse response = new WebSocketResponse();
        response.addValue(NOTIFICATION, new InsertNotification(message.getId(), message.getTimestamp()), NOTIFICATION_TO_DEVICE);
        return response;
    }
}
