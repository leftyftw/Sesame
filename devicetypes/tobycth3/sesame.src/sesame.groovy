/**
 *  Sesame
 *
 *  Copyright 2018 Toby Harris
 *
 *  Author: toby@cth3.com
 *  Date: 10/20/2018
 *
 *  Integrates SmartThings with Sesame door lock by Candy House
 */

 
preferences {
	input(name: "apikey", type: "password", title: "API Key", required: "true", description: "A valid API token")
	input(name: "threshold", type: "number", title: "Lock timeout threshold", defaultValue: "60", required: "true", description: "Number of seconds")
}
  
  
metadata {
	definition (name: "Sesame", namespace: "tobycth3", author: "Toby Harris") {
		capability "Battery"
		capability "Lock"
		capability "Polling"
		capability "Refresh"
//		command "unlockHack"
		attribute "api", "string"		
		attribute "events", "string"
	}

	
	tiles(scale: 2) {
		multiAttributeTile(name:"toggle", type: "generic", width: 6, height: 4){
			tileAttribute ("device.lock", key: "PRIMARY_CONTROL") {
                attributeState "unknown", label:"unknown", action:"lock.lock", icon:"st.locks.lock.unknown", backgroundColor:"#ff3333", nextState:"locking"
                attributeState "locked", label:'locked', action:"lock.unlock", icon:"st.locks.lock.locked", backgroundColor:"#79b821", nextState:"unlocking"
                attributeState "unlocked", label:'unlocked', action:"lock.lock", icon:"st.locks.lock.unlocked", backgroundColor:"#ea9900", nextState:"locking"
                attributeState "locking", label:'locking', icon:"st.locks.lock.locked", backgroundColor:"#79b821"
                attributeState "unlocking", label:'unlocking', icon:"st.locks.lock.unlocked", backgroundColor:"#ffffff"
			}
			tileAttribute ("device.events", key: "SECONDARY_CONTROL") {
				attributeState "default", label:'${currentValue}'
            }
		}
        standardTile("lock", "device.lock", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:'Lock', action:"lock.lock", icon:"st.locks.lock.locked", nextState:"locking"
		}
		standardTile("unlock", "device.lock", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:'Unlock', action:"lock.unlock", icon:"st.locks.lock.unlocked", nextState:"unlocking"
		}
		valueTile("nickname", "device.nickname", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "nickname", label:'${currentValue}'
		}
		valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "battery", label:'${currentValue}% battery', unit:"%"
		}
		valueTile("api", "device.api", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "api", label:'API ${currentValue}'
		}
		standardTile("refresh", "device.lock", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:'', action:"polling.poll", icon:"st.secondary.refresh"
		}

		main "toggle"
		details(["toggle", "lock", "nickname", "unlock", "battery", "api", "refresh"])
	}
}


def installed() {
	init()
}

def updated() {
	unschedule()
	init()
}
  
def init() {
	log.info "Setting up Schedule (every 5 minutes)..."
	login()
	runEvery5Minutes(poll)
}



def lock() {
	log.info "Executing lock"
	
	log.debug "Starting lock check timer"
	def lockdelay = settings.threshold
	runIn(lockdelay, doorlockcheck)

	api('lock') { resp ->
		//	log.trace "Lock response $response.status $response.data"

		log.debug "Starting status check timer"
		runIn(lockdelay, poll)		
	}
}

def unlock() {
	log.info "Executing unlock"

	log.debug "Starting lock check timer"
	def lockdelay = settings.threshold
	runIn(lockdelay, doorlockcheck)

	api('unlock') { resp ->
		//	log.trace "Unlock response $response.status $response.data"

		log.debug "Starting status check timer"
		runIn(lockdelay, poll)	
	}
}


def locked() {
	log.info "Lock state is locked"
	
	def locked_changed = device.currentValue("lock") != "locked"
	sendEvent(name: 'lock', value: 'locked', displayed: locked_changed, isStateChange: locked_changed)
    
    def raw_stamp = new Date().format('h:mma M/d/yy',location.timeZone)
    def stamp = raw_stamp.toLowerCase()
	sendEvent(name: 'events', value: "Locked - $stamp", displayed: false, isStateChange: locked_changed)
    
	unschedule("doorlockcheck")
}

def unlocked() {
	log.info "Lock state is unlocked"
	
	def unlocked_changed = device.currentValue("lock") != "unlocked"
	sendEvent(name: 'lock', value: 'unlocked', displayed: unlocked_changed, isStateChange: unlocked_changed)
    
    def raw_stamp = new Date().format('h:mma M/d/yy',location.timeZone)
    def stamp = raw_stamp.toLowerCase()
	sendEvent(name: 'events', value: "Unlocked - $stamp", displayed: false, isStateChange: unlocked_changed) 
    
	unschedule("doorlockcheck")
}

def api_ok() {
	log.info "API connection ok"
	def api_changed = device.currentValue("api") != "OK"
	sendEvent(name: 'api', value: "OK", displayed: api_changed, isStateChange: api_changed)
}

def api_failed() {
	log.info "API connection failed"
	def api_changed = device.currentValue("api") != "FAILED"
	sendEvent(name: 'api', value: "FAILED", displayed: api_changed, isStateChange: api_changed)
	
    def raw_stamp = new Date().format('h:mma M/d/yy',location.timeZone)
    def stamp = raw_stamp.toLowerCase()
	sendEvent(name: 'events', value: "API FAILED - $stamp", displayed: false, isStateChange: true)
}

def doorlockcheck() {
	log.warn "Lock state is unknown"
	sendEvent(name: 'lock', value: 'unknown', displayed: true, isStateChange: true)
	
    def raw_stamp = new Date().format('h:mma M/d/yy',location.timeZone)
    def stamp = raw_stamp.toLowerCase()
	sendEvent(name: 'events', value: "Unknown - $stamp", displayed: false, isStateChange: true)
  }


def poll(){
	if (!state.auth) {
	    return
    }
    

	if (!state.lastDevicePoll || (state.lastDevicePoll + (30*60*1000)) < now()) {
		log.info "Executing 'poll' on deviceID"
		state.lastDevicePoll = now()
		api('deviceID') { resp ->
			log.trace "Device ID: ${resp.data[0].device_id}"

			//	log.trace "Nickname: ${resp.data.nickname}"
			def new_nickname = resp.data[0].nickname
			def old_nickname = device.currentValue("nickname")
			def nickname_changed = new_nickname != old_nickname
			sendEvent(name: 'nickname', value: new_nickname, displayed: nickname_changed, isStateChange: nickname_changed)
		}
	}

	log.info "Executing 'poll' on status"

	api('status') { resp ->

		//	log.trace "Locked: ${resp.data.locked}"
		def is_locked = resp.data.locked
		if (is_locked) { 
			locked() 
		}
		else if (!is_locked) { 
			unlocked()
		}
		else {
			doorlockcheck()
		}

	//	log.trace "API: ${resp.data.responsive}"
		def new_api = resp.data.responsive
		if (new_api) { api_ok() }
		else { api_failed() }


	//	log.trace "Battery: ${resp.data.battery}"
		def new_battery = resp.data.battery
		def old_battery = device.currentValue("battery")
		def battery_changed = new_battery != old_battery
		sendEvent(name: 'battery', value: new_battery, unit:"%", displayed: battery_changed, isStateChange: battery_changed)	
	}
}
  
  
def api(method, success = {}) {
	// log.info "Executing 'api'"

	def methods = [
		'deviceID': [uri: "https://sesame-hack.herokuapp.com/sesame/devices"],
		'lock': [uri: "https://sesame-hack.herokuapp.com/sesame/$state.deviceID/lock"],
		'unlock': [uri: "https://sesame-hack.herokuapp.com/sesame/$state.deviceID/unlock"],
		'status': [uri: "https://sesame-hack.herokuapp.com/sesame/$state.deviceID/status"]
	]

	def request = methods.getAt(method)

	log.debug "Starting $method : $args, request: $request.uri"
	doRequest(request.uri, success)
}

def doRequest(uri, success) {
	log.debug "Calling $type : $uri : $state"

	def params = [
		uri: uri,
		contentType: 'application/json',
		headers: [
        "Authorization": state.auth
		]
	]

	// log.trace params

	try {
		httpGet(params, success)
	} catch (e) {
		log.debug "something went wrong: $e"
	}
}

def login() { 
	if (settings.threshold < 15) {
		log.warn 'Threshold too short.  Defaulting to 15s'
		settings.threshold = 15
	}

	log.info "Executing 'login'"
    state.auth = settings.apikey
    // get device ID
    deviceID()
}

def deviceID() {
	log.info "Executing 'device ID'"
	api('deviceID') { resp ->
		log.trace "Device ID: ${resp.data[0].device_id}"
		state.deviceID = resp.data[0].device_id
	}
}
