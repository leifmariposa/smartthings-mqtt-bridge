/**
 *  Electricity
 *
 *  Copyright 2019 Leif Persson
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.Field

definition(
    name: "Electricity",
    namespace: "leifmariposa",
    author: "Leif Persson",
    description: "A bridge between SmartThings and Electricity MQTT",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Partner/wattvision.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Partner/wattvision@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Partner/wattvision@2x.png")


preferences {
//	section("Title") {
		// TODO: put inputs here
//	}

//	section("Send Notifications?") {
//        input("recipients", "contact", title: "Send notifications to", multiple: true, required: false)
//    }

    section ("Bridge") {
        //input "bridge", "capability.notification", title: "Notify this Bridge", required: true, multiple: false
        input "bridge", "capability.Power Meter", title: "Notify this Bridge", required: true, multiple: false
    }
}

def installed() {
	log.debug "***** SA: Installed with settings: ${settings}"

	runEvery15Minutes(initialize)
	initialize()
}

def updated() {
	log.debug "***** SA: updated"

	unsubscribe()
	initialize()
}

def initialize() {
	log.debug "***** SA: initialize"

	// Subscribe to events from the bridge
    //subscribe(bridge, "message", bridgeHandler)

    // Update the bridge
    updateSubscription()
}

// Update the bridge"s subscription
def updateSubscription() {
	log.debug "***** SA: updateSubscription"
    def attributes = [
        //notify: ["Contacts", "System", "sparsnas/597416"],
        597416: ["sparsnas"]
    ]
//    CAPABILITY_MAP.each { key, capability ->
//        capability["attributes"].each { attribute ->
//            if (!attributes.containsKey(attribute)) {
//                attributes[attribute] = []
//            }
//            settings[key].each {device ->
//                attributes[attribute].push(device.displayName)
//            }
//        }
//    }
    def json = new groovy.json.JsonOutput().toJson([
        path: "/subscribe",
        body: [
            devices: attributes
        ]
    ])

    log.debug "***** SA: Updating subscription: ${json}"

    bridge.deviceNotification(json)
}

// Receive an event from the bridge
def bridgeHandler(evt) {
    def json = new JsonSlurper().parseText(evt.value)
    log.debug "***** SA: Received device event from bridge: ${json}"

    if (json.type == "notify") {
        if (json.name == "Contacts") {
            sendNotificationToContacts("${json.value}", recipients)
        } else {
            sendNotificationEvent("${json.value}")
        }
        return
    }
}

// Receive an event from a device
def inputHandler(evt) {
	log.debug "***** SA: inputHandler"
    if (
        state.ignoreEvent
        && state.ignoreEvent.name == evt.displayName
        && state.ignoreEvent.type == evt.name
        && state.ignoreEvent.value == evt.value
    ) {
        log.debug "Ignoring event ${state.ignoreEvent}"
        state.ignoreEvent = false;
    }
    else {
        def json = new JsonOutput().toJson([
            path: "/push",
            body: [
                name: evt.displayName,
                value: evt.value,
                type: evt.name
            ]
        ])

        log.debug "***** SA: Forwarding device event to bridge: ${json}"
        bridge.deviceNotification(json)
    }
}