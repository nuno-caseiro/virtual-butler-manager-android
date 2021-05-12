package pt.ipleiria.estg.meicm.msc

import android.util.Log
import androidx.lifecycle.MutableLiveData
import io.ktor.application.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.*


class Manager(private val callback: UtilCallback, private var deviceIp: String) {
    private val serverIP = "192.168.1.78:7579"
    private val serverURI = "http://" + this.serverIP
    private val onem2m = "/onem2m"
    private var managerURI = ""
    private var managerContainerURI = ""
    private var locationContainerURI = ""
    private var locationURI = ""
    private var locationRoomsContainerURI = ""
    private var sentencesToSpeakContainerURI = ""
    private var butlerURI = ""
    private val managerLabel = "butlermanager"
    private val managerContainerLabel = "iproomcnt"
    private val locationLabel = "location"
    private val locationContainerLabel = "currentroomcnt"
    private val locationRoomsContainerLabel = "roomscnt"
    private val butlerLabel = "butler"
    private val sentencesToSpeakContainerLabel = "speakcnt"

    private val client: OkHttpClient = OkHttpClient()
    private var connected: MutableLiveData<Boolean> = MutableLiveData<Boolean>()
    private var receivedNotification: MutableLiveData<String> = MutableLiveData<String>()

    private val jsonPostType = "application/vnd.onem2m-res+json"
    private val xmlType = "application/xml"

    val availableRooms = LinkedList<String>()
    val mappedRooms = LinkedList<Room>()

    init {

        connected.postValue(false)
        mappedRooms.clear()
        callback.notifyAdapter()
        connect()

       embeddedServer(Jetty, 1401, deviceIp) {
            routing {
                post("/monitor"){
                    //call.receiveText()
                    val receiveText = call.receiveText()
                    Log.d("NOTIFICATION", receiveText)
                    receivedNotification.postValue(receiveText)
                }
            }
        }.start(wait = false)

        receivedNotification.observeForever {
            if (it != null){
                newRoomAvailable(it)
            }
        }

    }

    private fun getRooms(label: String){

        var responseContainer = query("$label?fu=1&ty=4")
        if (responseContainer != "Not found" && responseContainer.isNotEmpty()) {
            val resp = JSONObject(responseContainer)
            val respArray = resp["m2m:uril"] as JSONArray
            val len: Int = respArray.length()
            if (len > 0){
                for (i in 0 until len){
                    val value = respArray[i] as String
                    responseContainer = query(value)
                    Log.d("RESPONSE", responseContainer)
                    var roomJson = JSONObject(responseContainer)
                    if (roomJson.has("m2m:cin")){
                        roomJson = roomJson.getJSONObject("m2m:cin")
                        if (roomJson.has("rn") && roomJson.has("con")){
                            val room: Room
                            if (label == managerContainerURI){
                                room = Room(roomJson.getString("con"), roomJson.getString("rn"))
                                mappedRooms.add(room)
                                callback.notifyAdapter()
                            }else{
                                room = Room(roomJson.getString("rn"))
                                var found = false
                                for (roomM in mappedRooms){
                                    if (room.roomName == roomM.roomName){
                                        found = true
                                        break
                                    }
                                }
                                if (!found){
                                    availableRooms.add(room.roomName.capitalize(Locale.ROOT))
                                }
                            }

                        }
                    }
                }
            }

        }

    }

    //TODO is it really necessary ? and if that is deleted - > should i replace with getrooms refresh? checking the owner notification
    private fun newRoomAvailable(string: String){
        var jsonObject = JSONObject(string)
        if (jsonObject.has("m2m:sgn")){
            jsonObject = jsonObject.getJSONObject("m2m:sgn")
            if(jsonObject.has("nev")){
                jsonObject = jsonObject.getJSONObject("nev")
                if(jsonObject.has("rep")){
                    jsonObject = jsonObject.getJSONObject("rep")
                    if (jsonObject.has("m2m:cin")){
                        jsonObject = jsonObject.getJSONObject("m2m:cin")
                        val room = jsonObject.getString("rn").toLowerCase(Locale.ROOT).capitalize(Locale.ROOT)
                        Log.d("ROOM NTF", room.toString())
                        availableRooms.add(room)
                    }

                }
            }
        }
    }

    fun connect(){
        CoroutineScope(Dispatchers.Default).launch {
            searchForLocationAE()
            searchForManagerAE()
            searchForButlerAE()

            getRooms(managerContainerURI)
            getRooms(locationRoomsContainerURI)

            println("#######FOR TESTING START###########")
            println(managerURI)
            println(managerContainerURI)
            println(locationURI)
            println(locationContainerURI)
            println(locationRoomsContainerURI)
            println(butlerURI)
            println(sentencesToSpeakContainerURI)

            println("#######FOR TESTING END###########")
        }

        connected.observeForever {
            if (it == true) {
                callback.setMessage("connected_to_ip", serverIP)
            }
        }
    }

    private fun searchForButlerAE() {
        val response: String = query("$onem2m?fu=1&lbl=$butlerLabel")
        if (response != "Not found" && response.isNotEmpty()) {

            var resp = JSONObject(response)
            var respArray = resp["m2m:uril"] as JSONArray
            if (respArray.length() == 0) {
                createButlerAE()
            } else {
                butlerURI = respArray[0].toString()
                //location uri
                //println(resp)
                //check container
                val responseContainer: String =
                        query("$onem2m?fu=1&lbl=$sentencesToSpeakContainerLabel")
                if (responseContainer != "Not found") {
                    resp = JSONObject(responseContainer)
                    respArray = resp["m2m:uril"] as JSONArray
                    if (respArray.length() == 0) {
                        creteSentencesToSpeakContainer()
                    } else {
                        sentencesToSpeakContainerURI = respArray[0].toString()
                    }
                }
            }

        }else{
            callback.showSnack("Error getting query")
        }
    }

    private fun createButlerAE() {
        val mediaType: MediaType? = "application/vnd.onem2m-res+json; ty=2".toMediaTypeOrNull()
        val body: RequestBody = "{ \"m2m:ae\": {\"rn\": \"$butlerLabel\",\"api\": \"pt.ipleiria.estg.$butlerLabel\",\"rr\": false, \"lbl\":[\"$butlerLabel\"]}}"
                .toRequestBody(mediaType)
        val request: Request = makeRequest(serverURI + onem2m, body, jsonPostType, "2", "00003")

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                callback.showSnack("Creating Butler AE failed")
            } else {
                butlerURI = "$onem2m/$butlerLabel"
                creteSentencesToSpeakContainer()
                //createRoomsContainer()
            }
        }
    }

    private fun creteSentencesToSpeakContainer() {
        val mediaType = "application/vnd.onem2mres+json; ty=3".toMediaTypeOrNull()
        val body: RequestBody = "{ \"m2m:cnt\": {\"rn\": \"$sentencesToSpeakContainerLabel\", \"lbl\":[\"$sentencesToSpeakContainerLabel\"]}}"
                .toRequestBody(mediaType)
        val request: Request = makeRequest(serverURI + butlerURI, body, jsonPostType, "3", "0005")
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                callback.showSnack("Creating sentences to speak container failed")
            } else {
                sentencesToSpeakContainerURI = "$onem2m/$butlerLabel/$sentencesToSpeakContainerLabel"
            }
        }
    }


    private fun query(parameters: String): String {
        var responseToReturn = ""
        try {
            val request: Request = Request.Builder()
                    .url(serverURI + parameters)
                    .addHeader("Accept", "application/json")
                    .addHeader("X-M2m-RI", "00001")
                    .build()
            client.newCall(request).execute().use { response ->
                responseToReturn = if (!response.isSuccessful && response.code != 404) {
                    "Not found"
                }else{
                    response.body?.string() ?: ""
                }
            }
        } catch (e: Exception) {
            callback.showSnack("Query Request failed")
        }
        return responseToReturn
    }


    private fun searchForLocationAE() {
        val response: String = query("$onem2m?fu=1&lbl=$locationLabel")
        if (response != "Not found" && response.isNotEmpty()) {

            connected.postValue(true)

            var resp = JSONObject(response)
            var respArray = resp["m2m:uril"] as JSONArray
            if (respArray.length() == 0) {
                createLocationAE()
            } else {
                locationURI = respArray[0].toString()
                //location uri
                //println(resp)
                //check container
                var responseContainer: String = query("$onem2m?fu=1&lbl=$locationContainerLabel")
                if (responseContainer != "Not found") {
                    resp = JSONObject(responseContainer)
                    respArray = resp["m2m:uril"] as JSONArray
                    if (respArray.length() == 0) {
                        createCurrentRoomLocationContainer()

                    } else {
                        locationContainerURI = respArray[0].toString()
                    }
                }
                responseContainer = query("$onem2m?fu=1&lbl=$locationRoomsContainerLabel")
                if (responseContainer != "Not found") {
                    resp = JSONObject(responseContainer)
                    respArray = resp["m2m:uril"] as JSONArray
                    if (respArray.length() == 0) {
                        createRoomsContainer()
                    } else {
                        locationRoomsContainerURI = respArray[0].toString()

                    }
                }

                responseContainer = query("$locationRoomsContainerURI/$deviceIp")
                if (responseContainer != "Not found") {
                    resp = JSONObject(responseContainer)
                    if (resp.has("m2m:dbg")){
                        if (resp["m2m:dbg"] == "resource does not exist"){
                            subscribeRoomsContainer()
                        }
                    }
                }
            }
        }else{
            callback.showSnack("Error getting query")
        }
    }

    private fun createRoomsContainer() {
        val mediaType = "application/vnd.onem2mres+json; ty=3".toMediaTypeOrNull()
        val body: RequestBody = RequestBody.create(
                mediaType,
                "{ \"m2m:cnt\": {\"rn\": \"$locationRoomsContainerLabel\", \"lbl\":[\"$locationRoomsContainerLabel\"]}}"
        )
        val request: Request = makeRequest(serverURI + locationURI, body, jsonPostType, "3", "0005")
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                callback.showSnack("Creating Location AE rooms container failed")
            } else {
                locationRoomsContainerURI = "$onem2m/$locationLabel/$locationRoomsContainerLabel"
                subscribeRoomsContainer()
            }
        }
    }

    private fun createLocationAE() {
        val mediaType: MediaType? = "application/vnd.onem2m-res+json; ty=2".toMediaTypeOrNull()
        val body: RequestBody = RequestBody.create(
                mediaType,
                "{ \"m2m:ae\": {\"rn\": \"$locationLabel\",\"api\": \"pt.ipleiria.estg.location\",\"rr\": false, \"lbl\":[\"$locationLabel\"]}}"
        )
        val request: Request = makeRequest(serverURI + onem2m, body, jsonPostType, "2", "00003")

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                callback.showSnack("Creating Location AE failed")
            } else {
                locationURI = "$onem2m/$locationLabel"
                createCurrentRoomLocationContainer()
                createRoomsContainer()
            }
        }
    }


    private fun createCurrentRoomLocationContainer() {
        val mediaType = "application/vnd.onem2mres+json; ty=3".toMediaTypeOrNull()
        val body: RequestBody = RequestBody.create(
                mediaType,
                "{ \"m2m:cnt\": {\"rn\": \"$locationContainerLabel\", \"lbl\":[\"$locationContainerLabel\", \"currentroomcontainer\"]}}"
        )
        val request: Request = makeRequest(serverURI + locationURI, body, jsonPostType, "3", "0005")
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                callback.showSnack("Creating Location AE container failed")
            } else {
                locationContainerURI = "$onem2m/$locationLabel/$locationContainerLabel"
            }
        }
    }

    private fun makeRequest(url: String, body: RequestBody, type: String, ty: String, XM2MRI: String): Request {
        return Request.Builder()
                .url(url)
                .method("POST", body)
                .addHeader("Content-Type", "$type; ty=$ty")
                .addHeader("X-M2M-RI", XM2MRI)
                .addHeader("Authorization", "Basic c3VwZXJhZG1pbjpzbWFydGhvbWUyMQ==")
                .build()
    }


    private fun searchForManagerAE() {
        val response = query("$onem2m?fu=1&lbl=$managerLabel")
        if (response != "Not found" && response.isNotEmpty()) {
            var resp = JSONObject(response)
            var respArray = resp["m2m:uril"] as JSONArray
            if (respArray.length() == 0) {
                createButlerManagerAE()
            } else {
                managerURI = respArray[0].toString()

                var responseContainer = query("$onem2m?fu=1&lbl=$managerContainerLabel")
                if (responseContainer != "Not found") {
                    resp = JSONObject(responseContainer)
                    respArray = resp["m2m:uril"] as JSONArray
                    if (respArray.length() == 0) {
                        createButlerManagerIpRoomContainer()

                    } else {
                        managerContainerURI = respArray[0].toString()
                        //checksub
                    }
                }
            }
        }else{
            callback.showSnack("Error getting query")
        }
    }

    private fun createButlerManagerAE() {
        val mediaType = "application/vnd.onem2m-res+json; ty=2".toMediaTypeOrNull()
        val body: RequestBody = RequestBody.create(
                mediaType,
                "{ \"m2m:ae\": {\"rn\": \"$managerLabel\",\"api\": \"pt.ipleiria.estg.butlermanager\",\"rr\": false, \"lbl\":[\"$managerLabel\"]}}"
        )
        val request: Request = makeRequest(serverURI + onem2m, body, jsonPostType, "2", "00003")
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                callback.showSnack("Creating Butler Manager AE failed")
            } else {
                managerURI = "$onem2m/$managerLabel"
                createButlerManagerIpRoomContainer()

            }
        }
    }

    private fun createButlerManagerIpRoomContainer() {
        val mediaType = "application/vnd.onem2mres+json; ty=3".toMediaTypeOrNull()
        val body: RequestBody = RequestBody.create(
                mediaType,
                "{ \"m2m:cnt\": {\"rn\": \"$managerContainerLabel\", \"lbl\":[\"iproomcnt\", \"$managerContainerLabel\"]}}"
        )
        val request = makeRequest(serverURI + managerURI, body, jsonPostType, "3", "0005")
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                callback.showSnack("Creating Butler Manager AE container failed")
            } else {
                managerContainerURI = "$onem2m/$managerLabel/$managerContainerLabel"
            }
        }
    }

    fun saveRoom(room: Room) {

        val queryResponse = query("$managerContainerURI?fu=1&lbl=${room.roomName}")
        if (queryResponse != "Not found") {
            var resp = JSONObject(queryResponse)
            var respArray = resp["m2m:uril"] as JSONArray
            if (respArray.length() == 0) {
                val mediaType = "application/vnd.onem2mres+json; ty=4".toMediaTypeOrNull()
                val body: RequestBody = RequestBody.create(
                        mediaType,
                        "{ \"m2m:cin\": {\"rn\": \"${room.ip}\",\"cnf\":\"text/plain:0\",\"con\": \"${room.roomName}\", \"lbl\":[\"${room.roomName}\"]} }\n"
                )
                val request: Request = makeRequest(serverURI + managerContainerURI, body, jsonPostType, "4", "0006")

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        callback.showSnack("Creating Room on Manager AE container failed")
                    } else if (response.code == 209) {
                        callback.showSnack("Room with that IP already exists")
                    } else {
                        subscribeCurrentLocation(room)

                        //post para subscrever frases para ler

                    }
                }
            } else {
                callback.showSnack("That room name already exists")
            }

        } else {
            callback.showSnack("Query for room not successfully")
        }


    }

    private fun subscribeCurrentLocation(room: Room) {
        /*val mediaType = "application/xml;ty=23".toMediaTypeOrNull()
        val body: RequestBody = RequestBody.create(
                mediaType,
                "<m2m:sub xmlns:m2m= \"http://www.onem2m.org/xml/protocols\" rn=\"${room.ip}\"><nu>http://${room.ip}:1400/location</nu><nct>2</nct><enc><net>3</net></enc></m2m:sub>"
        )
        val request: Request = makeRequest(serverURI + locationContainerURI, body, xmlType, "23", "0008")

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                callback.showSnack("Error subscribing current location's container")
            }else{
                subscribeSentencesToSpeakContainer(room);
            }
        }*/

        val client: OkHttpClient = OkHttpClient().newBuilder()
                .build()
        val mediaType = "application/xml;ty=23".toMediaTypeOrNull()
        val body: RequestBody = RequestBody.create(mediaType, "<m2m:sub xmlns:m2m= \"http://www.onem2m.org/xml/protocols\" rn=\"${room.ip}\"><nu>http://${room.ip}:1400/location</nu><nct>2</nct><enc><net>3</net></enc></m2m:sub>")
 /*       val request: Request = Request.Builder()
                .url("http://192.168.1.78:7579/onem2m/location/currentroomcnt")
                .method("POST", body)
                .addHeader("Content-Type", "application/xml;ty=23")
                .addHeader("X-M2M-RI", "0008")
                .addHeader("Authorization", "Basic c3VwZXJhZG1pbjpmN2M2YzEyZA==")
                .build()
*/
        val request: Request = makeRequest(serverURI + locationContainerURI, body, xmlType, "23", "0008")
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                callback.showSnack("Error subscribing sentences to speak container")
            }else{
                subscribeSentencesToSpeakContainer(room)
            }
        }
    }

    private fun subscribeSentencesToSpeakContainer(room: Room) {
        val mediaType = "application/xml;ty=23".toMediaTypeOrNull()
        val body: RequestBody = RequestBody.create(
                mediaType,
                "<m2m:sub xmlns:m2m= \"http://www.onem2m.org/xml/protocols\" rn=\"${room.ip}\"><nu>http://${room.ip}:1400/sentences</nu><nct>2</nct><enc><net>3</net></enc></m2m:sub>"
        )
        val request: Request = makeRequest(serverURI + sentencesToSpeakContainerURI, body, xmlType, "23", "0008")

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                callback.showSnack("Error subscribing sentences to speak container")
            }else{
                mappedRooms.add(room)
                callback.notifyAdapter()
            }
        }
    }

    private fun subscribeRoomsContainer() {
        val mediaType = "application/xml;ty=23".toMediaTypeOrNull()
        val body: RequestBody = RequestBody.create(
                mediaType,
                "<m2m:sub xmlns:m2m= \"http://www.onem2m.org/xml/protocols\" rn=\"${deviceIp}\"><nu>http://$deviceIp:1401/monitor</nu><nct>2</nct><enc><net>3</net></enc></m2m:sub>"
        )
        val request: Request = makeRequest(serverURI + locationRoomsContainerURI, body, xmlType, "23", "0008")

        val response = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                callback.showSnack("Error subscribing rooms location's container")
            }else{
                println("####### SUB ###### Rooms")
            }
        }
    }

    private fun editRoom(){

    }

    private  fun deleteRoom(){

    }



}

