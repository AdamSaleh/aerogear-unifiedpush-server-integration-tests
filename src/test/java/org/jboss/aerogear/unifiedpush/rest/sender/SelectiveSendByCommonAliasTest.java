/**
 * JBoss, Home of Professional Open Source
 * Copyright Red Hat, Inc., and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.aerogear.unifiedpush.rest.sender;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jboss.aerogear.test.model.InstallationImpl;
import org.jboss.aerogear.unifiedpush.message.UnifiedMessage;
import org.jboss.aerogear.unifiedpush.test.Deployments;
import org.jboss.aerogear.unifiedpush.test.GenericUnifiedPushTest;
import org.jboss.aerogear.unifiedpush.utils.Constants;
import org.jboss.aerogear.unifiedpush.utils.InstallationUtils;
import org.jboss.aerogear.unifiedpush.utils.PushNotificationSenderUtils;
import org.jboss.aerogear.unifiedpush.utils.SenderStatisticsEndpoint;
import org.jboss.aerogear.unifiedpush.utils.ServerSocketUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;

import com.google.android.gcm.server.Sender;
import com.notnoop.apns.internal.ApnsServiceImpl;

public class SelectiveSendByCommonAliasTest extends GenericUnifiedPushTest {

    private final static String SIMPLE_PUSH_VERSION = "version=15";

    private final static String NOTIFICATION_ALERT_MSG = "Hello AeroGearers";

    private static final String COMMON_ALIAS = UUID.randomUUID().toString();

    private static List<InstallationImpl> installationsWithCommonAlias;
    private static InstallationImpl simplePushInstallation;

    @Override
    protected String getContextRoot() {
        return Constants.INSECURE_AG_PUSH_ENDPOINT;
    }

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        return Deployments.customUnifiedPushServerWithClasses();
    }

    @Test
    @InSequence(12)
    public void registeriOSInstallation() {
        // FIXME this will work only if this test is first in sequence!
        installationsWithCommonAlias = new ArrayList<InstallationImpl>();

        InstallationImpl generatedInstallation = InstallationUtils.generateIos();

        generatedInstallation.setAlias(COMMON_ALIAS);

        InstallationUtils.register(generatedInstallation, getRegisteredIOSVariant(), getSession());

        installationsWithCommonAlias.add(generatedInstallation);
    }

    @Test
    @InSequence(13)
    public void registerAndroidInstallation() {
        InstallationImpl generatedInstallation = InstallationUtils.generateAndroid();

        generatedInstallation.setAlias(COMMON_ALIAS);

        InstallationUtils.register(generatedInstallation, getRegisteredAndroidVariant(), getSession());

        installationsWithCommonAlias.add(generatedInstallation);
    }

    @Test
    @InSequence(14)
    public void registerSimplePushInstallation() {
        InstallationImpl generatedInstallation = InstallationUtils.generateSimplePush();

        generatedInstallation.setAlias(COMMON_ALIAS);

        InstallationUtils.register(generatedInstallation, getRegisteredSimplePushVariant(), getSession());

        // FIXME SimplePush should be also done the same way Sender and ApnsServiceImpl are done
        simplePushInstallation = generatedInstallation;
        // installationsWithCommonAlias.add(generatedInstallation);
    }

    @Test
    @InSequence(15)
    public void selectiveSendByCommonAlias() throws UnknownHostException, IOException {
        List<String> aliases = new ArrayList<String>();
        aliases.add(COMMON_ALIAS);
        ApnsServiceImpl.clear();
        Sender.clear();

        UnifiedMessage.Builder message = new UnifiedMessage.Builder()
            .aliases(aliases).alert(NOTIFICATION_ALERT_MSG).simplePush("15")
            .pushApplicationId(getRegisteredPushApplication().getPushApplicationID())
            .masterSecret(getRegisteredPushApplication().getMasterSecret());

        ServerSocket serverSocket = ServerSocketUtils.createServerSocket(Constants.SOCKET_SERVER_PORT);
        assertNotNull(serverSocket);

        PushNotificationSenderUtils.send(message.build(), getSession());

        final String serverInput = ServerSocketUtils.readUntilMessageIsShown(serverSocket, NOTIFICATION_ALERT_MSG);

        assertNotNull(serverInput);
        assertTrue(serverInput.contains(SIMPLE_PUSH_VERSION));
        assertTrue(serverInput.contains("PUT /endpoint/" + simplePushInstallation.getDeviceToken()));
    }

    @Test
    @InSequence(16)
    public void verifyGCMandAPNnotifications() {
        SenderStatisticsEndpoint.SenderStatistics senderStatistics = PushNotificationSenderUtils.waitSenderStatisticsAndReset(
            installationsWithCommonAlias.size(), getSession());

        for (InstallationImpl installation : installationsWithCommonAlias) {
            assertTrue(senderStatistics.deviceTokens.contains(installation.getDeviceToken()));
        }

        assertNotNull(senderStatistics.gcmMessage);
        assertEquals(NOTIFICATION_ALERT_MSG, senderStatistics.gcmMessage.getData().get("alert"));
        assertEquals(NOTIFICATION_ALERT_MSG, senderStatistics.apnsAlert);
    }
}
