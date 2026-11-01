/**
 * @typedef {Object} ContentDTO
 * @property {string} id
 * @property {string} title
 * @property {string} description
 * @property {string} category
 * @property {number} part
 * @property {'A1'|'A2'|'B1'|'B2'|'C1'} difficulty
 * @property {'video'|'audio'|'article'} media_type
 * @property {string} [media_url]
 * @property {string} [thumbnail_url]
 * @property {'YouTube'|'Audio'|'Podcast'|'News'} source_platform
 * @property {number} duration_minutes
 * @property {number} total_views
 * @property {boolean} [is_bookmarked]
 */

/**
 * @typedef {Object} ContentFilterParams
 * @property {string} [category]
 * @property {string} [difficulty]
 * @property {number} [part]
 * @property {string} [search]
 */

export {};
