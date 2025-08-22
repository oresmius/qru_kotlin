package com.py6fx.qru

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


// Representa um item de QSO para o RecyclerView
data class QsoLogItem(
    val id: Int,             // ID do QSO no banco de dados
    val timestamp: String,   // Data/hora do QSO
    val qrg: String,         // Frequência
    val mode: String,        // Modo (LSB, USB, CW, etc.)
    val rxCall: String,      // Indicativo da estação RX
    val rxRst: String,       // RST recebido
    val rxNr: String,        // Número recebido
    val rxExch: String,      // Exchange recebido
    val txRst: String,       // RST transmitido
    val txNr: String,        // Número transmitido
    val txExch: String       // Exchange transmitido
)


class QsoLogAdapter(
    var items: List<QsoLogItem>,
    private val onItemLongPress: ((QsoLogItem) -> Unit)? = null
) : RecyclerView.Adapter<QsoLogAdapter.QsoLogViewHolder>() {

    class QsoLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timestamp: TextView = itemView.findViewById(R.id.text_timestamp)
        val qrg: TextView = itemView.findViewById(R.id.text_qrg)
        val mode: TextView = itemView.findViewById(R.id.text_mode)
        val rxCall: TextView = itemView.findViewById(R.id.text_rx_call)
        val rxRst: TextView = itemView.findViewById(R.id.text_rx_rst)
        val rxNr: TextView = itemView.findViewById(R.id.text_rx_nr)
        val rxExch: TextView = itemView.findViewById(R.id.text_rx_exch)
        val txRst: TextView = itemView.findViewById(R.id.text_tx_rst)
        val txNr: TextView = itemView.findViewById(R.id.text_tx_nr)
        val txExch: TextView = itemView.findViewById(R.id.text_tx_exch)
    }
    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return items[position].id.toLong()
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QsoLogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_qso, parent, false)
        return QsoLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: QsoLogViewHolder, position: Int) {
        val item = items[position]
        holder.timestamp.text = item.timestamp
        holder.qrg.text = item.qrg
        holder.mode.text = item.mode
        holder.rxCall.text = item.rxCall
        holder.rxRst.text = item.rxRst
        holder.rxNr.text = item.rxNr
        holder.rxExch.text = item.rxExch
        holder.txRst.text = item.txRst
        holder.txNr.text = item.txNr
        holder.txExch.text = item.txExch

        holder.itemView.setOnLongClickListener {
            onItemLongPress?.invoke(item)
            true
        }
    }

    override fun getItemCount() = items.size

    fun updateList(newItems: List<QsoLogItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
