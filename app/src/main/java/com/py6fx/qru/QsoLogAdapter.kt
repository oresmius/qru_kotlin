package com.py6fx.qru

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class QsoLogItem(
    val timestamp: String,
    val qrg: String,
    val mode: String,
    val rxCall: String,
    val rxRst: String,
    val rxNr: String,
    val rxExch: String,
    val txRst: String,
    val txNr: String,
    val txExch: String
)
class QsoLogAdapter(
    private var items: List<QsoLogItem>
) : RecyclerView.Adapter<QsoLogAdapter.QsoLogViewHolder>() {

    // ViewHolder: liga os campos do XML aos dados
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
    }

    override fun getItemCount() = items.size

    // Para atualizar os dados depois
    fun updateList(newItems: List<QsoLogItem>) {
        items = newItems
        notifyDataSetChanged()
    }

}
