import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.bluetoothapp.R
import androidx.recyclerview.widget.RecyclerView
import java.sql.Timestamp

/*class Data ( val star_byte: Byte = 0xA5.toByte(),
             val type: Byte,
             var data1: Byte,
             var data2: Byte,
             var data3:Byte,
             val end_byte: Byte = 0x0A.toByte()) {

    fun sendByte(): ByteArray{
        val timestamp = System.currentTimeMillis().toInt()
        data1 = (timestamp shr 24).toByte()
        data2 = (timestamp shr 16).toByte()
        data3 = (timestamp shr 8).toByte()

        return byteArrayOf(star_byte,type,data1,data2,data3,end_byte)
    }

    companion object {
        fun receiveByte(bytes: ByteArray): Data{
            return Data(star_byte = bytes[0],
                type = bytes[1], data1 = bytes[2], data2 = bytes[3], data3 = bytes[4], end_byte = bytes[5])

        }
    }

             }*/


data class Data (val text: String, val isSent: Boolean )

class DataAdapter (private val messages: List<Data>): RecyclerView.Adapter<DataAdapter.DataViewHolder>() {
    class DataViewHolder(itemView: View): RecyclerView.ViewHolder(itemView){
        val textSent: TextView = itemView.findViewById(R.id.textSent)
        val textReceived: TextView = itemView.findViewById(R.id.textReceived)

    }

     override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DataViewHolder {
         val view = LayoutInflater.from(parent.context)
             .inflate(R.layout.item_message, parent, false)
         return DataViewHolder(view)
     }

     override fun onBindViewHolder(holder: DataViewHolder, position: Int) {
         val message = messages[position]

         if (message.isSent) {
             holder.textSent.visibility = View.VISIBLE
             holder.textReceived.visibility = View.GONE
             holder.textSent.text = message.text
         } else {
             holder.textReceived.visibility = View.VISIBLE
             holder.textSent.visibility = View.GONE
             holder.textReceived.text = message.text
         }
     }

     override fun getItemCount(): Int = messages.size
}

