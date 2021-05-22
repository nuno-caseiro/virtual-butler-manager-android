package pt.ipleiria.estg.meicm.msc

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.Adapter
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.get
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pt.ipleiria.estg.meicm.msc.databinding.ActivityMainBinding
import java.util.*


class MainActivity : AppCompatActivity(), UtilCallback, AdapterView.OnItemSelectedListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var manager: Manager
    private var clickedItem: MutableLiveData<Pair<Int, Room>> = MutableLiveData()
    private var room: Pair<Int,Room> = Pair(0, Room("Empty", "0.0.0.0"))
    private lateinit var spinnerAdapter: ArrayAdapter<String>
    private var lastSelected: String = ""
    private var firstTime = true
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress: String = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        binding.ipLbl.text = ipAddress
        manager = Manager(this, ipAddress)
        binding.createRoomBtn.setOnClickListener {
            AddRoomDialog(this, manager.atualAvailableRooms).show(supportFragmentManager, "AddRoomDialog")
        }

        binding.removeRoomBtn.setOnClickListener {
            CoroutineScope(Dispatchers.Default).launch {
                manager.deleteAllRoomsContainer(room.second)
            }
        }


        spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, manager.allAvailableRooms)
        binding.currentRoomSpinner.adapter = spinnerAdapter
        binding.currentRoomSpinner.onItemSelectedListener = this
        manager.allAvailableRooms.add("None")



        val itemAdapter  = RoomItemAdapter(manager.mappedRooms, clickedItem)
        binding.roomList.adapter = itemAdapter
        binding.roomList.layoutManager = LinearLayoutManager(this)

        clickedItem.observe(this, {
            room = it
            for (i in 0 until itemAdapter.itemCount) {
                if (i != it.first){
                    val typedValue = TypedValue()
                    if (this.theme.resolveAttribute(android.R.attr.windowBackground, typedValue, true)) {
                        val colorWindowBackground = typedValue.data
                        binding.roomList[i].setBackgroundColor(colorWindowBackground)
                    }
                }
            }
        })

    }

     override fun showSnack(message: String){
        val snack = Snackbar.make(this.findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
        snack.setAction("Dismiss") { snack.dismiss() }
         snack.show()
    }

    override fun setMessage(item: String, message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            when (item) {
                "connected_to_ip" -> binding.connectedToIp.text = message
            }
        }
    }

    override fun saveRoom(room: Room) {
        CoroutineScope(Dispatchers.Default).launch {
            manager.saveRoom(room)
        }
    }

    override fun roomSaved(room: Room) {
        println(room.toString())

    }

    override fun notifyAdapter() {
        CoroutineScope(Dispatchers.Main).launch {
            binding.roomList.adapter?.notifyDataSetChanged()
        }
    }



    override fun notifySpinnerAdapterChanged() {
        CoroutineScope(Dispatchers.Main).launch {
            spinnerAdapter.notifyDataSetChanged()
        }
    }

    override fun roomRemoved() {
        CoroutineScope(Dispatchers.Main).launch {
            binding.roomList.adapter?.notifyDataSetChanged()
        }
    }

    override fun notifyActive(room: String) {
        CoroutineScope(Dispatchers.Main).launch {
            for (i in 0 until spinnerAdapter.count) {
                val item: String = binding.currentRoomSpinner.getItemAtPosition(i) as String
                if (item.decapitalize(Locale.ROOT) == room) {
                    lastSelected = room
                    binding.currentRoomSpinner.setSelection(i)
                    break
                }
            }

        }
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        if (!firstTime){
            val item = parent!!.getItemAtPosition(position) as String
            val itemDecap = item.decapitalize(Locale.ROOT)
           if (itemDecap != lastSelected){
               CoroutineScope(Dispatchers.Default).launch {
                   manager.changeCurrentLocation(itemDecap)
               }
           }
        }else{
            firstTime=!firstTime
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        TODO("Not yet implemented")
    }


}