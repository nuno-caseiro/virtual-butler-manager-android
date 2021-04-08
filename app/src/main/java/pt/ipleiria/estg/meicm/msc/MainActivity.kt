package pt.ipleiria.estg.meicm.msc

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter

import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pt.ipleiria.estg.meicm.msc.databinding.ActivityMainBinding
import java.util.*


class MainActivity : AppCompatActivity(), UtilCallback {
    private lateinit var binding: ActivityMainBinding
    private lateinit var manager: Manager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress: String = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        binding.ipLbl.text = ipAddress
        manager = Manager(this, ipAddress)
        binding.createRoomBtn.setOnClickListener {
            AddRoomDialog(this, manager.availableRooms).show(supportFragmentManager, "AddRoomDialog")
        }

        binding.imageButton.setOnClickListener {
            manager.connect()
        }

        val itemAdapter  = RoomItemAdapter(manager.mappedRooms)
        binding.roomList.adapter = itemAdapter
        binding.roomList.layoutManager = LinearLayoutManager(this)


    }

     override fun showSnack(message: String){
        val snack = Snackbar.make(this.findViewById(android.R.id.content),message, Snackbar.LENGTH_LONG)
        snack.setAction("Dismiss") { snack.dismiss() }
         snack.show()
    }

    override fun setMessage(item: String ,message: String) {
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

    override fun roomAddedToList() {
        CoroutineScope(Dispatchers.Main).launch {
            binding.roomList.adapter?.notifyDataSetChanged()
        }
    }


}