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
package org.jboss.aerogear.unifiedpush.pushapp


import groovy.json.JsonSlurper

import javax.ws.rs.core.Response.Status

import org.jboss.aerogear.unifiedpush.common.AuthenticationUtils
import org.jboss.aerogear.unifiedpush.common.Constants
import org.jboss.aerogear.unifiedpush.common.PushApplicationUtils
import org.jboss.aerogear.unifiedpush.model.PushApplication
import org.jboss.arquillian.spock.ArquillianSpecification

import spock.lang.Shared
import spock.lang.Specification

@ArquillianSpecification
@Mixin([AuthenticationUtils, PushApplicationUtils])
class SecureRegisterReadDeletePushAppSpecification extends Specification {


    def private final static URL root = new URL(Constants.SECURE_ENDPOINT)

    def private static final String pushAppName = "My App"
    def private static final String  pushAppDesc = "Awesome App"

    @Shared def static authCookies
    @Shared def static pushAppId

    def setup() {
        authCookies = authCookies ? authCookies : secureLogin().getCookies()
        // RestAssured.filters(new RequestLoggingFilter(System.err), new ResponseLoggingFilter(System.err))
    }

    def "Registering a push application"() {

        given: "A PushApplication"
        PushApplication pushApp = createPushApplication(pushAppName, pushAppDesc,
                null, null, null)

        when: "The Push Application is registered"
        def response = registerPushApplication(pushApp, authCookies, "application/json")
        def body = response.body().jsonPath()
        pushAppId = body.get("pushApplicationID")

        then: "Response code 201 is returned"
        response.statusCode() == Status.CREATED.getStatusCode()

        and: "Push App Id is not null"
        pushAppId != null

        and: "Push App Name is the expected one"
        pushAppName.equals(body.get("name"))

        and: "Push App Description is the expected one"
        pushAppDesc.equals(body.get("description"))
    }

    def "Retrieve all push applications and found newly registered one in the list"() {

        when: "Apps are retrieved"
        def response = listAllPushApplications(authCookies)
        def responseString = response.asString()
        def slurper = new JsonSlurper()
        def apps = slurper.parseText responseString

        then: "Response code 200 is returned"
        response.statusCode() == Status.OK.getStatusCode()

        and: "pushAppId is in the list"
        def found = apps.find {
            it.get("pushApplicationID") == pushAppId
        }
        found != null
        pushAppName.equals(found.name)
    }

    def "Retrieve registered application"() {

        when: "Application is retrieved"
        def response = findPushApplicationById(authCookies, pushAppId)
        def body = response.body().jsonPath()

        then: "Response code 200 is returned"
        response.statusCode() == Status.OK.getStatusCode()

        and: "App name is the expected one"
        pushAppId.equals(body.get("pushApplicationID"))
        pushAppName.equals(body.get("name"))
    }

    def "Delete registered push app"() {

        when: "Application is deleted"
        def response = deletePushApplication(authCookies, pushAppId)
        def responseString = response.asString()

        then: "Response code 204 is returned"
        response.statusCode() == Status.NO_CONTENT.getStatusCode()

        and: "Content is empty"
        responseString == ""
    }
}
