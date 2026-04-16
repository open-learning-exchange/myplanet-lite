package org.ole.planet.myplanet.lite.util

import androidx.recyclerview.widget.DiffUtil

object DiffUtils {
    inline fun <T : Any> itemCallback(
        crossinline areItemsTheSame: (oldItem: T, newItem: T) -> Boolean,
        crossinline areContentsTheSame: (oldItem: T, newItem: T) -> Boolean = { oldItem, newItem -> oldItem == newItem },
        crossinline getChangePayload: (oldItem: T, newItem: T) -> Any? = { _, _ -> null }
    ): DiffUtil.ItemCallback<T> {
        return object : DiffUtil.ItemCallback<T>() {
            override fun areItemsTheSame(oldItem: T, newItem: T): Boolean = areItemsTheSame(oldItem, newItem)
            override fun areContentsTheSame(oldItem: T, newItem: T): Boolean = areContentsTheSame(oldItem, newItem)
            override fun getChangePayload(oldItem: T, newItem: T): Any? = getChangePayload(oldItem, newItem)
        }
    }
}
