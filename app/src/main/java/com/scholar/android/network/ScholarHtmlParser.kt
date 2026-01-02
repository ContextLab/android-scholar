package com.scholar.android.network

import android.util.Log
import com.scholar.android.data.model.Article
import com.scholar.android.data.model.ArticleDetail
import com.scholar.android.data.model.AuthorProfile
import com.scholar.android.data.model.AuthorSearchResult
import com.scholar.android.data.model.SearchResult
import com.scholar.android.data.model.YearlyCitation
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.UUID

/**
 * Parses Google Scholar HTML pages to extract article data.
 * Uses Jsoup for HTML parsing.
 */
class ScholarHtmlParser {

    companion object {
        private const val TAG = "ScholarHtmlParser"
        // CSS selectors for Google Scholar HTML elements
        private const val RESULT_CONTAINER = "div.gs_r.gs_or.gs_scl"
        private const val TITLE_SELECTOR = "h3.gs_rt"
        private const val TITLE_LINK_SELECTOR = "h3.gs_rt a"
        private const val AUTHORS_LINE_SELECTOR = "div.gs_a"
        private const val SNIPPET_SELECTOR = "div.gs_rs"
        private const val CITATION_LINK_SELECTOR = "a:contains(Cited by)"
        private const val PDF_LINK_SELECTOR = "div.gs_or_ggsm a, div.gs_ggsd a"
        private const val NEXT_PAGE_SELECTOR = "td[align=left] a"
        private const val TOTAL_RESULTS_SELECTOR = "div.gs_ab_mdw"

        // Author profile selectors
        private const val AUTHOR_NAME_SELECTOR = "#gsc_prf_in"
        private const val AUTHOR_AFFILIATION_SELECTOR = ".gsc_prf_il"
        private const val AUTHOR_EMAIL_SELECTOR = "#gsc_prf_ivh"
        private const val AUTHOR_IMAGE_SELECTOR = "#gsc_prf_pua img, #gsc_prf_pu img"
        private const val AUTHOR_STATS_TABLE = "#gsc_rsb_st"
        private const val AUTHOR_INTERESTS_SELECTOR = "#gsc_prf_int a"
        private const val AUTHOR_ARTICLES_CONTAINER = "#gsc_a_b .gsc_a_tr"

        // Author search selectors
        private const val AUTHOR_SEARCH_RESULT = "div.gsc_1usr"
        private const val AUTHOR_SEARCH_NAME = "h3.gs_ai_name a"
        private const val AUTHOR_SEARCH_AFFILIATION = "div.gs_ai_aff"
        private const val AUTHOR_SEARCH_CITED = "div.gs_ai_cby"
        private const val AUTHOR_SEARCH_IMAGE = "img.gs_ai_pho"
        private const val AUTHOR_SEARCH_INTERESTS = "div.gs_ai_int a"

        // Citation graph selectors
        private const val CITATION_GRAPH_CONTAINER = "#gsc_rsb_cit"
        private const val CITATION_GRAPH_BARS = ".gsc_g_a"
        private const val CITATION_GRAPH_YEAR = ".gsc_g_t"
        private const val CITATION_GRAPH_VALUE = ".gsc_g_al"

        private const val BASE_URL = "https://scholar.google.com"
    }

    /**
     * Parses search results HTML and returns a SearchResult object.
     *
     * @param html The raw HTML string from Google Scholar
     * @return SearchResult containing parsed articles and pagination info
     */
    fun parseSearchResults(html: String): SearchResult {
        Log.d(TAG, "parseSearchResults: Parsing HTML of length ${html.length}")
        // Log first 500 chars to see what kind of page we got
        Log.d(TAG, "parseSearchResults: HTML start: ${html.take(500)}")
        val document = Jsoup.parse(html)

        val containers = document.select(RESULT_CONTAINER)
        Log.d(TAG, "parseSearchResults: Found ${containers.size} result containers")

        // Try alternative selectors if the main one fails
        if (containers.isEmpty()) {
            val altContainers = document.select("div.gs_ri")
            Log.d(TAG, "parseSearchResults: Alt selector gs_ri found: ${altContainers.size}")
            val altContainers2 = document.select("[data-cid]")
            Log.d(TAG, "parseSearchResults: Alt selector data-cid found: ${altContainers2.size}")
        }

        val articles = containers.mapNotNull { element ->
            parseArticle(element)
        }
        Log.d(TAG, "parseSearchResults: Parsed ${articles.size} articles")

        val totalResults = parseTotalResults(document)
        val nextPageUrl = parseNextPageUrl(document)
        Log.d(TAG, "parseSearchResults: Total results: $totalResults, Next page: $nextPageUrl")

        return SearchResult(
            articles = articles,
            totalResults = totalResults,
            nextPageUrl = nextPageUrl
        )
    }

    /**
     * Parses a single article result element.
     *
     * @param element The Jsoup Element representing a single result
     * @return Article object or null if parsing fails
     */
    private fun parseArticle(element: Element): Article? {
        return try {
            // Parse title and article URL
            val titleElement = element.selectFirst(TITLE_LINK_SELECTOR)
            val title = titleElement?.text()?.trim()
                ?: element.selectFirst(TITLE_SELECTOR)?.text()?.trim()
                ?: return null

            val articleUrl = titleElement?.attr("href")?.let { makeAbsoluteUrl(it) } ?: ""

            // Parse ID from data-cid attribute or generate one
            val id = element.attr("data-cid").ifEmpty {
                extractClusterIdFromElement(element) ?: UUID.randomUUID().toString()
            }

            // Parse authors, source, and year from the authors line
            val authorsLine = element.selectFirst(AUTHORS_LINE_SELECTOR)?.text() ?: ""
            val (authors, source, year) = parseAuthorsLine(authorsLine)

            // Parse snippet
            val snippet = element.selectFirst(SNIPPET_SELECTOR)?.text()?.trim() ?: ""

            // Parse citation count
            val citationCount = parseCitationCount(element)

            // Parse PDF URL
            val pdfUrl = element.selectFirst(PDF_LINK_SELECTOR)?.attr("href")?.let { makeAbsoluteUrl(it) }

            Article(
                id = id,
                title = title,
                authors = authors,
                year = year,
                citationCount = citationCount,
                snippet = snippet,
                pdfUrl = pdfUrl,
                articleUrl = articleUrl,
                source = source
            )
        } catch (e: Exception) {
            // Return null for articles that fail to parse
            null
        }
    }

    /**
     * Parses the authors line to extract authors, source, and year.
     * Format is typically: "Author1, Author2 - Journal Name, Year - Publisher"
     *
     * @param authorsLine The raw authors line text
     * @return Triple of (authors list, source, year)
     */
    private fun parseAuthorsLine(authorsLine: String): Triple<List<String>, String?, String?> {
        if (authorsLine.isBlank()) {
            return Triple(emptyList(), null, null)
        }

        // Split by " - " to separate sections
        val parts = authorsLine.split(" - ").map { it.trim() }

        // First part is typically authors
        val authorsText = parts.firstOrNull() ?: ""
        val authors = authorsText.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.matches(Regex("\\d{4}")) }

        // Try to find year (4-digit number)
        val yearPattern = Regex("\\b(19|20)\\d{2}\\b")
        val year = yearPattern.find(authorsLine)?.value

        // Source is typically in the second or third part
        val source = if (parts.size > 1) {
            val sourceText = parts.drop(1).joinToString(" - ")
            // Remove year and clean up
            yearPattern.replace(sourceText, "")
                .replace(Regex(",\\s*$"), "")
                .trim()
                .ifEmpty { null }
        } else {
            null
        }

        return Triple(authors, source, year)
    }

    /**
     * Parses the citation count from a result element.
     *
     * @param element The article result element
     * @return Citation count as integer, 0 if not found
     */
    private fun parseCitationCount(element: Element): Int {
        val citationLink = element.selectFirst(CITATION_LINK_SELECTOR) ?: return 0
        val text = citationLink.text()

        // Extract number from "Cited by 123" text
        val match = Regex("Cited by (\\d+)").find(text)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    /**
     * Extracts cluster ID from various element attributes.
     *
     * @param element The article result element
     * @return Cluster ID string or null
     */
    private fun extractClusterIdFromElement(element: Element): String? {
        // Try to find cluster ID in citation link
        element.select("a[href*=cites=]").firstOrNull()?.let { link ->
            val href = link.attr("href")
            Regex("cites=([^&]+)").find(href)?.groupValues?.get(1)?.let { return it }
        }

        // Try to find in related link
        element.select("a[href*=related:]").firstOrNull()?.let { link ->
            val href = link.attr("href")
            Regex("related:([^&:]+)").find(href)?.groupValues?.get(1)?.let { return it }
        }

        return null
    }

    /**
     * Parses total results text from the page.
     *
     * @param document The parsed HTML document
     * @return Total results string or null
     */
    private fun parseTotalResults(document: Document): String? {
        val resultsText = document.selectFirst(TOTAL_RESULTS_SELECTOR)?.text()
        return resultsText?.let {
            // Extract "About X results" text
            Regex("About [\\d,]+ results?").find(it)?.value
                ?: Regex("[\\d,]+ results?").find(it)?.value
        }
    }

    /**
     * Parses the next page URL from pagination.
     *
     * @param document The parsed HTML document
     * @return Next page URL or null if no more pages
     */
    private fun parseNextPageUrl(document: Document): String? {
        // Look for "Next" button in pagination
        val nextButton = document.select("a.gs_ico.gs_ico_nav_next, button.gs_btnPR").firstOrNull()
            ?: document.selectFirst(NEXT_PAGE_SELECTOR)

        return nextButton?.attr("href")?.let { makeAbsoluteUrl(it) }
    }

    /**
     * Makes a URL absolute if it's relative.
     *
     * @param url The URL to process
     * @return Absolute URL
     */
    private fun makeAbsoluteUrl(url: String): String {
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("//") -> "https:$url"  // Protocol-relative URL
            url.startsWith("/") -> "$BASE_URL$url"
            else -> "$BASE_URL/$url"
        }
    }

    /**
     * Checks if the HTML indicates a CAPTCHA or rate limiting.
     *
     * @param html The HTML to check
     * @return True if blocked, false otherwise
     */
    fun isBlocked(html: String): Boolean {
        val document = Jsoup.parse(html)
        val body = document.body().text().lowercase()

        return body.contains("unusual traffic") ||
                body.contains("automated requests") ||
                body.contains("captcha") ||
                body.contains("verify you're not a robot") ||
                document.select("form[action*=sorry]").isNotEmpty()
    }

    /**
     * Checks if the HTML contains valid search results.
     *
     * @param html The HTML to check
     * @return True if contains results, false otherwise
     */
    fun hasResults(html: String): Boolean {
        val document = Jsoup.parse(html)
        return document.select(RESULT_CONTAINER).isNotEmpty()
    }

    /**
     * Parses an article detail page HTML and returns an ArticleDetail object.
     * This handles various article page formats from Google Scholar.
     *
     * @param html The raw HTML string from the article page
     * @param articleUrl The URL of the article page
     * @return ArticleDetail object or null if parsing fails
     */
    fun parseArticleDetail(html: String, articleUrl: String): ArticleDetail? {
        return try {
            Log.d(TAG, "parseArticleDetail: Parsing HTML of length ${html.length}")
            val document = Jsoup.parse(html)

            // Try to detect page type and parse accordingly
            // Google Scholar can redirect to different page types

            // Check if this is a Google Scholar citation page
            val isScholarCitationPage = document.selectFirst("#gs_cit") != null ||
                    document.selectFirst("#gsc_oci_title") != null

            if (isScholarCitationPage) {
                return parseScholarCitationPage(document, articleUrl)
            }

            // Check if this is a search results page (article clicked from search)
            val searchResult = document.selectFirst(RESULT_CONTAINER)
            if (searchResult != null) {
                val article = parseArticle(searchResult)
                if (article != null) {
                    return ArticleDetail.fromArticle(article).copy(
                        abstract = extractAbstractFromPage(document),
                        citedByUrl = extractCitedByUrl(searchResult),
                        relatedUrl = extractRelatedUrl(searchResult)
                    )
                }
            }

            // Try to parse as a generic article page
            parseGenericArticlePage(document, articleUrl)
        } catch (e: Exception) {
            Log.e(TAG, "parseArticleDetail: Error parsing article", e)
            null
        }
    }

    /**
     * Parses a Google Scholar citation page (the detailed view).
     */
    private fun parseScholarCitationPage(document: Document, articleUrl: String): ArticleDetail? {
        // Title from the citation page
        val title = document.selectFirst("#gsc_oci_title, .gsc_oci_title_link, h3.gs_rt")?.text()?.trim()
            ?: document.selectFirst("title")?.text()?.trim()
            ?: return null

        // Authors - look in various locations
        val authorsText = document.selectFirst(".gsc_oci_value, .gs_a")?.text() ?: ""
        val (authors, source, year) = parseAuthorsLine(authorsText)

        // Abstract/Description
        val abstract = document.selectFirst("#gsc_oci_descr, .gsc_oci_value:contains(Abstract)")?.text()?.trim()
            ?: document.selectFirst("meta[name=description]")?.attr("content")

        // Citation count
        val citationCountText = document.selectFirst("a:contains(Cited by)")?.text() ?: ""
        val citationCount = Regex("Cited by (\\d+)").find(citationCountText)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        // PDF URL
        val pdfUrl = document.selectFirst("a[href*=.pdf], .gsc_oci_title_ggi a")?.attr("href")?.let { makeAbsoluteUrl(it) }

        // Cited by URL
        val citedByUrl = document.selectFirst("a:contains(Cited by)")?.attr("href")?.let { makeAbsoluteUrl(it) }

        // Related articles URL
        val relatedUrl = document.selectFirst("a:contains(Related articles)")?.attr("href")?.let { makeAbsoluteUrl(it) }

        // Cluster ID from URL
        val clusterId = Regex("cluster=([^&]+)").find(articleUrl)?.groupValues?.get(1)
            ?: Regex("cites=([^&]+)").find(citedByUrl ?: "")?.groupValues?.get(1)

        // BibTeX - try to find citation link (for future use if needed)
        @Suppress("UNUSED_VARIABLE")
        val bibtexUrl = document.selectFirst("a:contains(BibTeX), a[href*=bibtex]")?.attr("href")

        // Parse citing articles preview
        val citingArticles = parseCitingArticlesPreview(document)

        // Parse related articles preview
        val relatedArticles = parseRelatedArticlesPreview(document)

        return ArticleDetail(
            id = clusterId ?: UUID.randomUUID().toString(),
            title = title,
            authors = authors,
            year = year,
            venue = source,
            abstract = abstract,
            citationCount = citationCount,
            pdfUrl = pdfUrl,
            articleUrl = articleUrl,
            citedByUrl = citedByUrl,
            relatedUrl = relatedUrl,
            citingArticles = citingArticles,
            relatedArticles = relatedArticles,
            bibtex = null, // Would need additional fetch
            clusterId = clusterId
        )
    }

    /**
     * Parses a generic article page (external publisher page).
     */
    private fun parseGenericArticlePage(document: Document, articleUrl: String): ArticleDetail? {
        // Try common meta tags first
        val title = document.selectFirst("meta[property=og:title], meta[name=citation_title]")?.attr("content")
            ?: document.selectFirst("h1, title")?.text()?.trim()
            ?: return null

        // Authors from meta tags or common patterns
        val authorMetas = document.select("meta[name=citation_author], meta[name=author]")
        val authors = if (authorMetas.isNotEmpty()) {
            authorMetas.map { it.attr("content").trim() }
        } else {
            document.selectFirst(".authors, .author, [class*=author]")?.text()
                ?.split(",", ";", " and ")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
        }

        // Year
        val year = document.selectFirst("meta[name=citation_publication_date], meta[name=citation_date]")?.attr("content")
            ?.let { Regex("(\\d{4})").find(it)?.value }
            ?: document.selectFirst("time, .date, .year, [class*=date]")?.text()
                ?.let { Regex("(19|20)\\d{2}").find(it)?.value }

        // Venue/Journal
        val venue = document.selectFirst("meta[name=citation_journal_title]")?.attr("content")
            ?: document.selectFirst(".journal, .venue, [class*=journal]")?.text()?.trim()

        // Abstract
        val abstract = document.selectFirst("meta[name=description], meta[property=og:description]")?.attr("content")
            ?: document.selectFirst("#abstract, .abstract, [class*=abstract], [id*=abstract]")?.text()?.trim()

        // PDF URL
        val pdfUrl = document.selectFirst("meta[name=citation_pdf_url]")?.attr("content")
            ?: document.selectFirst("a[href*=.pdf]:contains(PDF), a.pdf-link")?.attr("href")
                ?.let { makeAbsoluteUrl(it) }

        return ArticleDetail(
            id = UUID.randomUUID().toString(),
            title = title,
            authors = authors,
            year = year,
            venue = venue,
            abstract = abstract,
            citationCount = 0,
            pdfUrl = pdfUrl,
            articleUrl = articleUrl,
            citedByUrl = null,
            relatedUrl = null
        )
    }

    /**
     * Extracts abstract from a search results page.
     */
    private fun extractAbstractFromPage(document: Document): String? {
        return document.selectFirst(SNIPPET_SELECTOR)?.text()?.trim()
            ?: document.selectFirst("meta[name=description]")?.attr("content")
    }

    /**
     * Extracts the "Cited by" URL from an article element.
     */
    private fun extractCitedByUrl(element: Element): String? {
        return element.selectFirst("a:contains(Cited by)")?.attr("href")?.let { makeAbsoluteUrl(it) }
    }

    /**
     * Extracts the "Related articles" URL from an article element.
     */
    private fun extractRelatedUrl(element: Element): String? {
        return element.selectFirst("a:contains(Related articles)")?.attr("href")?.let { makeAbsoluteUrl(it) }
    }

    /**
     * Parses a preview of citing articles from the page.
     */
    private fun parseCitingArticlesPreview(document: Document): List<Article> {
        // Look for "Cited by" section or related items
        val citingSection = document.selectFirst("#gs_cit_list, .gs_cit_list")
        return citingSection?.select(RESULT_CONTAINER)?.take(5)?.mapNotNull { parseArticle(it) }
            ?: emptyList()
    }

    /**
     * Parses a preview of related articles from the page.
     */
    private fun parseRelatedArticlesPreview(document: Document): List<Article> {
        // Look for "Related articles" section
        val relatedSection = document.selectFirst("#gs_rel_list, .gs_rel_list")
        return relatedSection?.select(RESULT_CONTAINER)?.take(5)?.mapNotNull { parseArticle(it) }
            ?: emptyList()
    }

    /**
     * Parses an author profile page HTML and returns an AuthorProfile object.
     *
     * @param html The raw HTML string from a Google Scholar author page
     * @param authorId The author ID used to fetch the page
     * @return AuthorProfile object or null if parsing fails
     */
    fun parseAuthorProfile(html: String, authorId: String): AuthorProfile? {
        return try {
            val document = Jsoup.parse(html)

            // Parse author name
            val name = document.selectFirst(AUTHOR_NAME_SELECTOR)?.text()?.trim()
                ?: return null

            // Parse affiliation
            val affiliation = document.selectFirst(AUTHOR_AFFILIATION_SELECTOR)?.text()?.trim()

            // Parse email (may be in a verification block)
            val emailElement = document.selectFirst(AUTHOR_EMAIL_SELECTOR)
            val email = emailElement?.text()?.let { text ->
                // Email is often displayed as "Verified email at domain.edu"
                if (text.contains("@")) {
                    Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
                        .find(text)?.value
                } else if (text.contains(" at ")) {
                    // Extract domain from "Verified email at domain.edu"
                    text.substringAfter(" at ").trim().takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            }

            // Parse profile image URL
            val imageUrl = document.selectFirst(AUTHOR_IMAGE_SELECTOR)?.attr("src")?.let {
                val absoluteUrl = makeAbsoluteUrl(it)
                Log.d(TAG, "parseAuthorProfile: Found image URL: $it -> $absoluteUrl")
                absoluteUrl
            }
            Log.d(TAG, "parseAuthorProfile: Final imageUrl: $imageUrl")

            // Parse citation statistics
            val stats = parseAuthorStats(document)

            // Parse research interests
            val interests = document.select(AUTHOR_INTERESTS_SELECTOR).map { it.text().trim() }

            // Parse articles
            val articles = parseAuthorArticles(document)

            // Parse citation history (for bar chart)
            val citationHistory = parseCitationHistory(document)

            AuthorProfile(
                id = authorId,
                name = name,
                affiliation = affiliation,
                email = email,
                imageUrl = imageUrl,
                citationCount = stats.citationCount,
                hIndex = stats.hIndex,
                i10Index = stats.i10Index,
                interests = interests,
                articles = articles,
                citationHistory = citationHistory
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Container for author citation statistics.
     */
    private data class AuthorStats(
        val citationCount: Int,
        val hIndex: Int,
        val i10Index: Int
    )

    /**
     * Parses citation statistics from the author profile stats table.
     *
     * @param document The parsed HTML document
     * @return AuthorStats with citation metrics
     */
    private fun parseAuthorStats(document: Document): AuthorStats {
        var citationCount = 0
        var hIndex = 0
        var i10Index = 0

        val statsTable = document.selectFirst(AUTHOR_STATS_TABLE)
        if (statsTable != null) {
            // The stats table has rows for Citations, h-index, i10-index
            // Each row has cells: label, all-time value, recent value
            val rows = statsTable.select("tr")

            for (row in rows) {
                val cells = row.select("td")
                if (cells.size >= 2) {
                    val label = cells.firstOrNull()?.text()?.lowercase() ?: ""
                    val value = cells.getOrNull(1)?.text()?.replace(",", "")?.toIntOrNull() ?: 0

                    when {
                        label.contains("citation") -> citationCount = value
                        label.contains("h-index") -> hIndex = value
                        label.contains("i10-index") -> i10Index = value
                    }
                }
            }
        }

        return AuthorStats(citationCount, hIndex, i10Index)
    }

    /**
     * Parses the citation history from the author profile page.
     * Google Scholar displays a bar chart showing citations per year.
     *
     * @param document The parsed HTML document
     * @return List of YearlyCitation objects sorted by year
     */
    private fun parseCitationHistory(document: Document): List<YearlyCitation> {
        val citations = mutableListOf<YearlyCitation>()

        try {
            // Google Scholar uses a graph container with year labels and bar heights
            // The years are in .gsc_g_t spans
            // The citation counts are in .gsc_g_al spans (or can be derived from bar height)

            // Try to find year labels and citation values
            val yearElements = document.select(CITATION_GRAPH_YEAR)
            val valueElements = document.select(CITATION_GRAPH_VALUE)

            Log.d(TAG, "parseCitationHistory: Found ${yearElements.size} years, ${valueElements.size} values")

            if (yearElements.isNotEmpty() && valueElements.isNotEmpty()) {
                // Match years with values
                for (i in 0 until minOf(yearElements.size, valueElements.size)) {
                    val yearText = yearElements[i].text().trim()
                    val valueText = valueElements[i].text().trim()

                    val year = yearText.toIntOrNull()
                    val value = valueText.replace(",", "").toIntOrNull() ?: 0

                    if (year != null && year > 1900 && year < 2100) {
                        citations.add(YearlyCitation(year, value))
                    }
                }
            }

            // Alternative parsing: try to extract from bar elements with data attributes
            if (citations.isEmpty()) {
                val barElements = document.select(CITATION_GRAPH_BARS)
                Log.d(TAG, "parseCitationHistory: Trying bar elements, found ${barElements.size}")

                for (bar in barElements) {
                    // Try to extract year and count from bar attributes or child elements
                    val href = bar.attr("href")
                    // Links often have format: /scholar?...&as_ylo=2020&as_yhi=2020...
                    val yearMatch = Regex("as_ylo=(\\d{4})").find(href)
                    val year = yearMatch?.groupValues?.get(1)?.toIntOrNull()

                    // The citation count might be in the bar's text or a child span
                    val countText = bar.selectFirst(".gsc_g_al")?.text()
                        ?: bar.selectFirst("span")?.text()
                        ?: bar.text()
                    val count = countText.replace(",", "").toIntOrNull() ?: 0

                    if (year != null && year > 1900 && year < 2100) {
                        citations.add(YearlyCitation(year, count))
                    }
                }
            }

            // If still empty, try a more general approach
            if (citations.isEmpty()) {
                // Look for the citation graph container and parse its structure
                val graphContainer = document.selectFirst(CITATION_GRAPH_CONTAINER)
                if (graphContainer != null) {
                    // Find all year-value pairs in the container
                    val allSpans = graphContainer.select("span")
                    val yearRegex = Regex("^(19|20)\\d{2}$")

                    var currentYear: Int? = null
                    for (span in allSpans) {
                        val text = span.text().trim()
                        if (yearRegex.matches(text)) {
                            currentYear = text.toIntOrNull()
                        } else if (currentYear != null) {
                            val count = text.replace(",", "").toIntOrNull()
                            if (count != null) {
                                citations.add(YearlyCitation(currentYear, count))
                                currentYear = null
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "parseCitationHistory: Error parsing citation history", e)
        }

        // Sort by year and return
        return citations.sortedBy { it.year }
    }

    /**
     * Parses articles from the author profile articles table.
     *
     * @param document The parsed HTML document
     * @return List of Article objects
     */
    private fun parseAuthorArticles(document: Document): List<Article> {
        return document.select(AUTHOR_ARTICLES_CONTAINER).mapNotNull { element ->
            parseAuthorArticle(element)
        }
    }

    /**
     * Parses a single article from the author profile articles table.
     *
     * @param element The article row element
     * @return Article object or null if parsing fails
     */
    private fun parseAuthorArticle(element: Element): Article? {
        return try {
            // Title and link
            val titleLink = element.selectFirst(".gsc_a_at") ?: return null
            val title = titleLink.text().trim()
            if (title.isEmpty()) return null

            val articleUrl = titleLink.attr("href").let {
                if (it.isNotEmpty()) makeAbsoluteUrl(it) else ""
            }

            // Extract article ID from data-href or link
            val id = titleLink.attr("data-href").ifEmpty {
                Regex("citation_for_view=([^&]+)").find(articleUrl)?.groupValues?.get(1)
                    ?: UUID.randomUUID().toString()
            }

            // Authors and source info (second line)
            val authorsLine = element.selectFirst(".gs_gray")?.text() ?: ""
            val authors = authorsLine.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            // Source/venue info (third line, second gray div)
            val grayDivs = element.select(".gs_gray")
            val sourceInfo = if (grayDivs.size > 1) grayDivs[1].text() else ""

            // Parse year from source info
            val yearPattern = Regex("\\b(19|20)\\d{2}\\b")
            val year = yearPattern.find(sourceInfo)?.value

            // Clean source (remove year)
            val source = yearPattern.replace(sourceInfo, "")
                .replace(Regex(",\\s*$"), "")
                .trim()
                .ifEmpty { null }

            // Citation count
            val citationText = element.selectFirst(".gsc_a_c a")?.text() ?: "0"
            val citationCount = citationText.replace(",", "").toIntOrNull() ?: 0

            Article(
                id = id,
                title = title,
                authors = authors,
                year = year,
                citationCount = citationCount,
                snippet = "", // Author page doesn't show snippets
                pdfUrl = null, // Would need to fetch article page for PDF
                articleUrl = articleUrl,
                source = source
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if the HTML represents a valid author profile page.
     *
     * @param html The HTML to check
     * @return True if it's an author profile page, false otherwise
     */
    fun isAuthorProfilePage(html: String): Boolean {
        val document = Jsoup.parse(html)
        return document.selectFirst(AUTHOR_NAME_SELECTOR) != null
    }

    /**
     * Parses author search results from regular search HTML.
     * Since the author search endpoint requires auth, we use regular search
     * with author: prefix and extract unique authors from the results.
     *
     * @param html The raw HTML string from Google Scholar search
     * @return List of AuthorSearchResult objects (unique authors)
     */
    fun parseAuthorSearchResults(html: String): List<AuthorSearchResult> {
        Log.d(TAG, "parseAuthorSearchResults: Parsing HTML of length ${html.length}")
        val document = Jsoup.parse(html)

        // First try to parse author search results page (gsc_1usr divs)
        // This format is used by label search and direct author search
        val authorSearchResults = document.select(AUTHOR_SEARCH_RESULT)
        if (authorSearchResults.isNotEmpty()) {
            Log.d(TAG, "parseAuthorSearchResults: Found ${authorSearchResults.size} author search results")
            return authorSearchResults.mapNotNull { parseAuthorSearchResult(it) }
        }

        // Fall back to extracting authors from regular search results
        val authorMap = mutableMapOf<String, AuthorSearchResult>()

        // Find all author profile links in the results
        // These are in the gs_a div (author line) with links to /citations?user=ID
        val resultContainers = document.select(RESULT_CONTAINER)
        Log.d(TAG, "parseAuthorSearchResults: Found ${resultContainers.size} search results")

        for (container in resultContainers) {
            val authorsDiv = container.selectFirst(AUTHORS_LINE_SELECTOR) ?: continue

            // Find links to author profiles
            val authorLinks = authorsDiv.select("a[href*=/citations?user=]")

            for (link in authorLinks) {
                val href = link.attr("href")
                val authorId = Regex("user=([^&]+)").find(href)?.groupValues?.get(1) ?: continue
                val authorName = link.text().trim()

                if (authorName.isNotEmpty() && !authorMap.containsKey(authorId)) {
                    authorMap[authorId] = AuthorSearchResult(
                        id = authorId,
                        name = authorName,
                        affiliation = null, // Not available in search results
                        citedBy = 0, // Not available in search results
                        imageUrl = null, // Not available in search results
                        interests = emptyList() // Not available in search results
                    )
                }
            }
        }

        Log.d(TAG, "parseAuthorSearchResults: Found ${authorMap.size} unique authors")
        return authorMap.values.toList()
    }

    /**
     * Parses a single author search result element.
     *
     * @param element The Jsoup Element representing a single author result
     * @return AuthorSearchResult object or null if parsing fails
     */
    private fun parseAuthorSearchResult(element: Element): AuthorSearchResult? {
        return try {
            // Parse name and author ID from link
            val nameLink = element.selectFirst(AUTHOR_SEARCH_NAME) ?: return null
            val name = nameLink.text().trim()
            if (name.isEmpty()) return null

            // Extract author ID from link href
            val href = nameLink.attr("href")
            val authorId = Regex("user=([^&]+)").find(href)?.groupValues?.get(1) ?: return null

            // Parse affiliation
            val affiliation = element.selectFirst(AUTHOR_SEARCH_AFFILIATION)?.text()?.trim()

            // Parse cited by count
            val citedByText = element.selectFirst(AUTHOR_SEARCH_CITED)?.text() ?: ""
            val citedBy = Regex("\\d+").find(citedByText.replace(",", ""))?.value?.toIntOrNull() ?: 0

            // Parse profile image URL
            val imageUrl = element.selectFirst(AUTHOR_SEARCH_IMAGE)?.attr("src")?.let {
                makeAbsoluteUrl(it)
            }

            // Parse research interests
            val interests = element.select(AUTHOR_SEARCH_INTERESTS).map { it.text().trim() }

            AuthorSearchResult(
                id = authorId,
                name = name,
                affiliation = affiliation,
                citedBy = citedBy,
                imageUrl = imageUrl,
                interests = interests
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseAuthorSearchResult: Failed to parse author", e)
            null
        }
    }
}
