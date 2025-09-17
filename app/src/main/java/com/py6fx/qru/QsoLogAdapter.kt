/*
 * QRU - Amateur Radio Contest Logger
 * Copyright (C) 2025 Fabio Almeida e Sousa (PY6FX)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Disclaimer:
 * QRU does not include or redistribute any .udc files.
 * N1MM Logger+ and related marks are trademarks of their respective owners.
 * References to "N1MM Logger+" are for compatibility description only;
 * there is no affiliation, partnership, or endorsement.
 * Users are responsible for ensuring they have the rights to use any files
 * they import. All imports are processed locally on the user's device.
 */

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

}
