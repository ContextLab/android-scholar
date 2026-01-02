package com.scholar.android.ui.results

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.scholar.android.R
import com.scholar.android.data.model.Article
import com.scholar.android.databinding.ItemArticleCardBinding

/**
 * RecyclerView adapter for displaying scholarly articles using ListAdapter with DiffUtil.
 *
 * @param onArticleClick Callback when an article card is clicked
 * @param onPdfClick Callback when the PDF button is clicked (receives article with non-null pdfUrl)
 * @param onSaveClick Callback when the save/bookmark button is clicked (receives article and current saved state)
 * @param onShareClick Callback when the share button is clicked
 * @param onCitationsClick Callback when the citations chip is clicked
 * @param onAuthorClick Callback when an author name is clicked (receives author name for search)
 */
class ArticleAdapter(
    private val onArticleClick: (Article) -> Unit,
    private val onPdfClick: (Article) -> Unit,
    private val onSaveClick: (Article) -> Unit,
    private val onShareClick: (Article) -> Unit,
    private val onCitationsClick: (Article) -> Unit = {},
    private val onAuthorClick: (String) -> Unit = {}
) : ListAdapter<Article, ArticleAdapter.ArticleViewHolder>(ArticleDiffCallback()) {

    // Set of saved article IDs for showing saved state
    private var savedArticleIds: Set<String> = emptySet()

    /**
     * Updates the set of saved article IDs and refreshes the list.
     */
    fun updateSavedArticles(savedIds: Set<String>) {
        if (savedArticleIds != savedIds) {
            savedArticleIds = savedIds
            notifyDataSetChanged()
        }
    }

    /**
     * Check if an article is saved.
     */
    fun isArticleSaved(articleId: String): Boolean = savedArticleIds.contains(articleId)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArticleViewHolder {
        val binding = ItemArticleCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ArticleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ArticleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ArticleViewHolder(
        private val binding: ItemArticleCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            // Set up click listeners once in init for better performance
            binding.articleCard.setOnClickListener {
                @Suppress("DEPRECATION")
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onArticleClick(getItem(position))
                }
            }

            binding.buttonPdf.setOnClickListener {
                @Suppress("DEPRECATION")
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val article = getItem(position)
                    if (article.pdfUrl != null) {
                        onPdfClick(article)
                    }
                }
            }

            binding.buttonSave.setOnClickListener {
                @Suppress("DEPRECATION")
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onSaveClick(getItem(position))
                }
            }

            binding.buttonShare.setOnClickListener {
                @Suppress("DEPRECATION")
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onShareClick(getItem(position))
                }
            }

            binding.chipCitations.setOnClickListener {
                @Suppress("DEPRECATION")
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onCitationsClick(getItem(position))
                }
            }

            // Make authors clickable - clicking searches for the first author
            binding.textAuthors.setOnClickListener {
                @Suppress("DEPRECATION")
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val article = getItem(position)
                    if (article.authors.isNotEmpty()) {
                        onAuthorClick(article.authors.first())
                    }
                }
            }
        }

        fun bind(article: Article) {
            val isSaved = savedArticleIds.contains(article.id)

            binding.apply {
                // Title
                textTitle.text = article.title

                // Authors - styled as clickable link
                textAuthors.text = article.getAuthorsString()
                textAuthors.visibility = if (article.authors.isNotEmpty()) View.VISIBLE else View.GONE
                if (article.authors.isNotEmpty()) {
                    textAuthors.setTextColor(root.context.getColor(R.color.primary))
                }

                // Source and year
                val formattedSource = article.getFormattedSource()
                textSource.text = formattedSource
                textSource.visibility = if (formattedSource.isNotEmpty()) View.VISIBLE else View.GONE

                // Snippet/Abstract
                textSnippet.text = article.snippet
                textSnippet.visibility = if (article.snippet.isNotEmpty()) View.VISIBLE else View.GONE

                // Citation count chip
                chipCitations.text = article.getFormattedCitationCount()
                chipCitations.visibility = if (article.citationCount > 0) View.VISIBLE else View.GONE

                // PDF button visibility based on URL availability
                buttonPdf.visibility = if (article.pdfUrl != null) View.VISIBLE else View.GONE

                // Save button icon based on saved state
                val saveIcon = if (isSaved) R.drawable.ic_save_filled else R.drawable.ic_save
                buttonSave.setIconResource(saveIcon)
            }
        }
    }

    /**
     * DiffUtil callback for efficient list updates.
     */
    class ArticleDiffCallback : DiffUtil.ItemCallback<Article>() {
        override fun areItemsTheSame(oldItem: Article, newItem: Article): Boolean {
            // Compare by unique ID
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Article, newItem: Article): Boolean {
            // Compare full content for changes
            return oldItem == newItem
        }
    }
}
