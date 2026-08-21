package com.example.pinboardkeyboard.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.pinboardkeyboard.R
import com.example.pinboardkeyboard.data.PinItem

class PinAdapter(
    private var pins: List<PinItem>,
    private val onClick: (PinItem) -> Unit,
    private val onLongClick: ((PinItem) -> Unit)? = null
) : RecyclerView.Adapter<PinAdapter.PinViewHolder>() {

    class PinViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.pinTitle)
        val preview: TextView = view.findViewById(R.id.pinContentPreview)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PinViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pin, parent, false)
        return PinViewHolder(view)
    }

    override fun onBindViewHolder(holder: PinViewHolder, position: Int) {
        val pin = pins[position]
        holder.title.text = pin.title
        holder.preview.text = pin.content.replace("\n", " ").take(50)
        holder.itemView.setOnClickListener { onClick(pin) }
        holder.itemView.setOnLongClickListener {
            onLongClick?.invoke(pin)
            onLongClick != null
        }
    }

    override fun getItemCount() = pins.size

    fun updatePins(newPins: List<PinItem>) {
        pins = newPins.toList()
        notifyDataSetChanged()
    }
}
