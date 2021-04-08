package pt.ipleiria.estg.meicm.msc

interface UtilCallback {
    fun showSnack(message: String)
    fun setMessage(item: String, message: String)
    fun saveRoom(room: Room)
    fun roomSaved(room: Room)
    fun roomAddedToList()
}