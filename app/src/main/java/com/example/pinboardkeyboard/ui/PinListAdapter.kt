package com.example.pinboardkeyboard.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pinboardkeyboard.R
import com.example.pinboardkeyboard.data.PinItem
import com.example.pinboardkeyboard.databinding.ItemPinBinding

/**
 * Efficient [ListAdapter] backed by [DiffUtil] – no more blanket `notifyDataSetChanged()`.
 *
 * @param compact renders the dense two-line variant used inside the keyboard.
 */
class PinListAdapter(
    private val compact: Boolean = false,
    private val categoryNameProvider: (String?) -> String? = { null },
    private val onClick: (PinItem) -> Unit,
    private val onLongClick: ((PinItem) -> Unit)? = null
) : ListAdapter<PinItem, PinListAdapter.PinViewHolder>(DIFF) {

    inner class PinViewHolder(private val binding: ItemPinBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(pin: PinItem) = with(binding) {
            pinTitle.text = pin.title
            pinContentPreview.text = pin.preview
            pinContentPreview.maxLines = if (compact) 2 else 3

            val categoryName = categoryNameProvider(pin.categoryId)
            pinCategory.text = categoryName
            pinCategory.visibility = if (categoryName.isNullOrBlank() || compact) {
                android.view.View.GONE
            } else {
                android.view.View.VISIBLE
            }

            pinPinnedIndicator.visibility =
                if (pin.pinned) android.view.View.VISIBLE else android.view.View.GONE

            root.contentDescription = root.context.getString(
                R.string.cd_pin_item,
                pin.title,
                pin.preview
            )
            root.setOnClickListener { onClick(pin) }
            root.setOnLongClickListener {
                onLongClick?.invoke(pin)
                onLongClick != null
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PinViewHolder {
        val binding = ItemPinBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PinViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PinViewHolder, position: Int) =
        holder.bind(getItem(position))

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<PinItem>() {
            override fun areItemsTheSame(oldItem: PinItem, newItem: PinItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: PinItem, newItem: PinItem) =
                oldItem == newItem
        }
    }
}
