package org.ole.planet.myplanet.lite.util

import androidx.recyclerview.widget.DiffUtil

object DiffUtils {
    inline fun <T : Any> itemCallback(
        crossinline areItemsTheSame: (oldItem: T, newItem: T) -> Boolean,
        crossinline areContentsTheSame: (oldItem: T, newItem: T) -> Boolean = { old, new -> old == new },
        crossinline getChangePayload: (oldItem: T, newItem: T) -> Any? = { _, _ -> null }
    ): DiffUtil.ItemCallback<T> = object : DiffUtil.ItemCallback<T>() {
        override fun areItemsTheSame(oldItem: T, newItem: T) = areItemsTheSame(oldItem, newItem)
        override fun areContentsTheSame(oldItem: T, newItem: T) = areContentsTheSame(oldItem, newItem)
        override fun getChangePayload(oldItem: T, newItem: T) = getChangePayload(oldItem, newItem)
    }
}
