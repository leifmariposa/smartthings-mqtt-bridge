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
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

metadata {
	definition (name: "Electricity device", namespace: "leifmariposa", author: "Leif Persson", cstHandler: true) {
        capability "Sensor"
        capability "Power Meter"
        capability "Energy Meter"
        capability "Notification"

        attribute "power", "string"
    	attribute "powerLow", "string"
    	attribute "powerHigh", "string"

        attribute "energy", "string"
        attribute "energy", "string"
        attribute "energySinceMidnight", "string"
        attribute "energyYesterday", "string"
        attribute "energyDayBeforeYesterday", "string"
        attribute "energyTwoDaysBeforeYesterday", "string"

        attribute "battery", "string"

        command "resetmaxmin"
	}

	preferences {
        input("ip", "string",
            title: "MQTT Bridge IP Address",
            description: "MQTT Bridge IP Address",
            required: true,
            displayDuringSetup: true
        )
        input("port", "string",
            title: "MQTT Bridge Port",
            description: "MQTT Bridge Port",
            required: true,
            displayDuringSetup: true
        )
        input("mac", "string",
            title: "MQTT Bridge MAC Address",
            description: "MQTT Bridge MAC Address",
            required: true,
            displayDuringSetup: true
        )
        input("price", "decimal",
            title: "Electricity price",
            description: "Price in Skr/kWh",
            required: true,
            displayDuringSetup: true
        )
    }

	simulator {
		// TODO: define status and reply messages here
	}

	tiles {
        multiAttributeTile(name:"power", type: "lighting", width: 6, height: 4, decoration: "flat", canChangeIcon: true, canChangeBackground: true){
			tileAttribute ("device.power", key: "PRIMARY_CONTROL") {
				attributeState "default", label: '${currentValue}', icon: "st.switches.light.on", backgroundColor: "#79b821"
			}
            tileAttribute ("statusText", key: "SECONDARY_CONTROL") {
           		attributeState "statusText", label:'${currentValue}'
            }
        }

        valueTile("statusText", "statusText", width: 3, height: 2, inactiveLabel: false) {
            state "statusText", label:'${currentValue}', backgroundColor:"#ffffff"
        }

        valueTile("powerLow", "powerLow", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", label:'Min:\n${currentValue}', backgroundColor:"#ffffff"
        }

        valueTile("powerHigh", "powerHigh", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", label:'Max:\n${currentValue}', backgroundColor:"#ffffff"
        }
        standardTile("resetmaxmin", "device.energy", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", label:'Reset\nWatts', action:"resetmaxmin", icon:"st.secondary.refresh-icon"
        }

        valueTile("energySinceMidnight", "device.energySinceMidnight", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state("default", label: 'I dag:\n${currentValue}', backgroundColor:"#ffffff")
        }
        valueTile("energyYesterday", "device.energyYesterday", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state("default", label: 'I går:\n${currentValue}', backgroundColor:"#ffffff")
        }
        valueTile("energyDayBeforeYesterday", "device.energyDayBeforeYesterday", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state("default", label: 'I förrgår:\n${currentValue}', backgroundColor:"#ffffff")
        }
        valueTile("energyTwoDaysBeforeYesterday", "device.energyTwoDaysBeforeYesterday", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state("default", label: 'I förrförrgår:\n${currentValue}', backgroundColor:"#ffffff")
        }

        valueTile("energy", "device.energy", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state("default", label: 'Mätarställning:\n${currentValue}', backgroundColor:"#ffffff")
        }

        standardTile("battery", "device.battery", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", label:'Battery:\n${currentValue}', backgroundcolor:"#ffffff"
        }

        main (["power"])
        details(["power",
                 "powerLow",
                 "resetmaxmin",
                 "powerHigh",
                 "energy",
                 "energySinceMidnight",
                 "energyYesterday",
                 "energyDayBeforeYesterday",
                 "energyTwoDaysBeforeYesterday",
                 "battery"])
	}
}

def resetmaxmin() {
    log.debug "${device.name} reset max/min values"
    state.powerHigh = 0
    state.powerLow = 99999

	def timeString = new Date().format("yyyy-MM-dd h:mm a", location.timeZone)
    sendEvent(name: "powerLow", value: "", unit: "")
    sendEvent(name: "powerHigh", value: "", unit: "")
}

// Store the MAC address as the device ID so that it can talk to SmartThings
def setNetworkAddress() {
    // Setting Network Device Id
    def hex = "$settings.mac".toUpperCase().replaceAll(':', '')
    if (device.deviceNetworkId != "$hex") {
        device.deviceNetworkId = "$hex"
        log.debug "***** DH: Device Network Id set to ${device.deviceNetworkId}"
    }
}

// parse events into attributes
def parse(String description) {
	setNetworkAddress()

    def powerValue
    def temp
	def msg = parseLanMessage(description)
    def value = new JsonOutput().toJson(msg.data)
    def slurper = new JsonSlurper().parseText(value)
    log.debug "***** parse: '${slurper.value}'"
    if (slurper && slurper.value) {
        def slurper2 = new JsonSlurper().parseText(slurper.value)

        def timeString = new Date().format("yyyy-MM-dd HH:mm", location.timeZone)
        sendEvent("name":"statusText", "value":"Updated on " + timeString)

        powerValue = Integer.valueOf(slurper2.Watt.intValue())
        if (powerValue != state.powerValue) {
            if (powerValue < state.powerLow) {
                temp = powerValue + " W" + " on " + timeString
                sendEvent(name: "powerLow", value: temp as String, unit: "")
                state.powerLow = powerValue
            }
            if (powerValue > state.powerHigh) {
                temp = powerValue + " W" + " on " + timeString
                sendEvent(name: "powerHigh", value: temp as String, unit: "")
                state.powerHigh = powerValue
            }
            state.powerValue = powerValue
        }

        def energyValue = slurper2.kWh
        if (energyValue != state.energyValue) {
            temp = String.format("%5.2f", energyValue)+"\n kWh"
            sendEvent(name: "energy", value: temp as String, unit: "")
            state.energyValue = energyValue

            Calendar cal=Calendar.getInstance(location.timeZone);
            def hour = cal.get(Calendar.HOUR_OF_DAY)
            def date = cal.get(Calendar.DAY_OF_MONTH)
            if (!state.energyAtLastMidnight || date != state.date) {
                state.energyTwoDaysBeforeYesterday = state.energyDayBeforeYesterday
                state.energyDayBeforeYesterday = state.energyYesterday
                state.energyYesterday = state.energySinceMidnight
                state.energyAtLastMidnight = energyValue
                state.date = date;
            }
            state.energySinceMidnight = energyValue - state.energyAtLastMidnight
            temp = String.format("%5.3f kWh\n%5.2f Skr", state.energySinceMidnight, state.energySinceMidnight * price)
            sendEvent(name: "energySinceMidnight", value: temp as String, unit: "")
            if (state.energyYesterday) {
                temp = String.format("%5.3f kWh\n%5.2f Skr", state.energyYesterday, state.energyYesterday * price)
                sendEvent(name: "energyYesterday", value: temp as String, unit: "")
            } else {
                sendEvent(name: "energyYesterday", value: "--", unit: "")
            }
            if (state.energyDayBeforeYesterday) {
                temp = String.format("%5.3f kWh\n%5.2f Skr", state.energyDayBeforeYesterday, state.energyDayBeforeYesterday * price)
                sendEvent(name: "energyDayBeforeYesterday", value: temp as String, unit: "")
            } else {
                sendEvent(name: "energyDayBeforeYesterday", value: "--", unit: "")
            }
            if (state.energyTwoDaysBeforeYesterday) {
                temp = String.format("%5.3f kWh\n%5.2f Skr", state.energyTwoDaysBeforeYesterday, state.energyTwoDaysBeforeYesterday * price)
                sendEvent(name: "energyTwoDaysBeforeYesterday", value: temp as String, unit: "")
            } else {
                sendEvent(name: "energyTwoDaysBeforeYesterday", value: "--", unit: "")
            }
        }

        def batteryValue = slurper2.battery
        if (batteryValue != state.batteryValue) {
            temp = batteryValue + " %"
            sendEvent(name: "battery", value: temp as String, unit: "")
            state.batteryValue = batteryValue
        }
    }

    return createEvent(name: "power", value: powerValue, , unit: "W")
}

// Send message to the Bridge
def deviceNotification(message) {
	log.debug "***** DH: deviceNotification"
    if (device.hub == null)
    {
        log.error "Hub is null, must set the hub in the device settings so we can get local hub IP and port"
        return
    }

    log.debug "***** DH: Sending '${message}' to device: ip: $ip:$port"
    setNetworkAddress()

    def slurper = new JsonSlurper()
    def parsed = slurper.parseText(message)

    if (parsed.path == '/subscribe') {
        parsed.body.callback = device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP")
    }

    def headers = [:]
    headers.put("HOST", "$ip:$port")
    headers.put("Content-Type", "application/json")

    def hubAction = new physicalgraph.device.HubAction(
        method: "POST",
        path: parsed.path,
        headers: headers,
        body: parsed.body
    )
    hubAction
}