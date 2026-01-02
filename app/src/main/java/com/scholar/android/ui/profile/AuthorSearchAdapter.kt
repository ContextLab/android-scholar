package com.scholar.android.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.scholar.android.R
import com.scholar.android.data.model.AuthorSearchResult
import com.scholar.android.databinding.ItemAuthorSearchBinding

/**
 * RecyclerView adapter for displaying author search results.
 */
class AuthorSearchAdapter(
    private val onAuthorClick: (AuthorSearchResult) -> Unit
) : ListAdapter<AuthorSearchResult, AuthorSearchAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAuthorSearchBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemAuthorSearchBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onAuthorClick(getItem(position))
                }
            }
        }

        fun bind(author: AuthorSearchResult) {
            binding.apply {
                textName.text = author.name

                // Hide affiliation if not available
                textAffiliation.text = author.affiliation ?: ""
                textAffiliation.isVisible = !author.affiliation.isNullOrBlank()

                // Hide citation count since we don't have this data from search results
                textCitedBy.isVisible = author.citedBy > 0
                if (author.citedBy > 0) {
                    textCitedBy.text = root.context.getString(
                        R.string.cited_by,
                        author.citedBy
                    )
                }

                // Hide interests if not available
                val interestsString = author.getInterestsString()
                textInterests.text = interestsString
                textInterests.isVisible = interestsString.isNotBlank()

                // Load profile image
                if (!author.imageUrl.isNullOrBlank()) {
                    imageProfile.load(author.imageUrl) {
                        crossfade(true)
                        placeholder(R.drawable.ic_person_placeholder)
                        error(R.drawable.ic_person_placeholder)
                        transformations(CircleCropTransformation())
                    }
                } else {
                    imageProfile.setImageResource(R.drawable.ic_person_placeholder)
                }
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<AuthorSearchResult>() {
        override fun areItemsTheSame(
            oldItem: AuthorSearchResult,
            newItem: AuthorSearchResult
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: AuthorSearchResult,
            newItem: AuthorSearchResult
        ): Boolean = oldItem == newItem
    }
}
