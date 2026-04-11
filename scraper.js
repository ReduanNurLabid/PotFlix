const cheerio = require('cheerio');

// In-memory cache with TTL
const cache = new Map();
const CACHE_TTL = 60 * 60 * 1000; // 1 hour

/**
 * Scrape an h5ai directory listing page and return structured entries
 */
async function scrapeDirectory(url) {
  // Check cache first
  const cached = cache.get(url);
  if (cached && Date.now() - cached.timestamp < CACHE_TTL) {
    return cached.data;
  }

  try {
    const response = await fetch(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
      },
      signal: AbortSignal.timeout(15000)
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }

    const html = await response.text();
    const $ = cheerio.load(html);
    const entries = [];

    // h5ai uses anchor tags in the listing
    $('a').each((_, el) => {
      const href = $(el).attr('href');
      const text = $(el).text().trim();

      // Skip navigation, parent directory, and h5ai system links
      if (!href || !text ||
        text === 'Parent Directory' ||
        text === 'powered by SamOnline' ||
        text === 'modern browsers' ||
        href.startsWith('http://browsehappy') ||
        href.startsWith('https://larsjung') ||
        href === '../' ||
        href === '/' ||
        href.startsWith('#') ||
        href.startsWith('?')) {
        return;
      }

      let fullUrl;
      try {
        fullUrl = new URL(href, url).href;
      } catch {
        // Skip malformed URLs
        return;
      }
      const isDirectory = href.endsWith('/');
      let name;
      try {
        name = decodeURIComponent(text).trim();
      } catch {
        name = text.trim();
      }

      const entry = {
        name,
        url: fullUrl,
        isDirectory
      };

      // Try to parse movie metadata from name
      if (isDirectory) {
        const parsed = parseMovieName(name);
        if (parsed) {
          Object.assign(entry, parsed);
        }
      } else {
        // It's a file - get extension
        const ext = name.split('.').pop().toLowerCase();
        entry.extension = ext;
        entry.isVideo = ['mkv', 'mp4', 'avi', 'wmv', 'mov', 'flv', 'webm'].includes(ext);
        entry.isImage = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].includes(ext);

        // Also parse file name for metadata
        const parsed = parseMovieName(name);
        if (parsed) {
          Object.assign(entry, parsed);
        }
      }

      entries.push(entry);
    });

    // Deduplicate by URL (h5ai repeats links)
    const seen = new Set();
    const unique = entries.filter(e => {
      if (seen.has(e.url)) return false;
      seen.add(e.url);
      return true;
    });

    // Cache the result
    cache.set(url, { data: unique, timestamp: Date.now() });
    return unique;
  } catch (err) {
    console.error(`Failed to scrape ${url}:`, err.message);
    // Return cached even if expired on error
    if (cached) return cached.data;
    throw err;
  }
}

/**
 * Parse movie/series name from folder naming convention
 * e.g. "28 Years Later (2025) 720p BluRay [Dual Audio]"
 */
function parseMovieName(folderName) {
  // Remove file extension if present
  let name = folderName.replace(/\.[a-zA-Z0-9]{2,4}$/, '');

  // Strip numbering prefixes like "003. " (IMDb Top 250 style)
  name = name.replace(/^\d{2,4}\.\s*/, '');

  // Pattern: Title (Year) Quality [extras]
  const movieMatch = name.match(/^(.+?)\s*\((\d{4})\)\s*(.*)/);
  if (!movieMatch) {
    // TV Series pattern: Title (TV Series YYYY–YYYY) Quality
    const tvMatch = name.match(/^(.+?)\s*\((TV(?:\s+Mini)?\s+Series\s+\d{4}[–\-]?\s*\d{0,4}\s*)\)\s*(.*)/);
    if (tvMatch) {
      return {
        title: tvMatch[1].trim(),
        type: 'tv',
        seriesInfo: tvMatch[2].trim(),
        quality: extractQuality(tvMatch[3]),
        isDualAudio: /dual\s*audio/i.test(tvMatch[3]),
        rawMeta: tvMatch[3].trim()
      };
    }

    // Year-only folder like "(2025)"
    const yearMatch = name.match(/^\((\d{4})\)(\s+.*)?$/);
    if (yearMatch) {
      return {
        title: yearMatch[1],
        year: parseInt(yearMatch[1]),
        type: 'yearFolder'
      };
    }

    return null;
  }

  return {
    title: movieMatch[1].trim().replace(/-/g, ': ').replace(/\s+/g, ' '),
    year: parseInt(movieMatch[2]),
    quality: extractQuality(movieMatch[3]),
    isDualAudio: /dual\s*audio/i.test(movieMatch[3]),
    type: 'movie',
    rawMeta: movieMatch[3].trim()
  };
}

function extractQuality(str) {
  const match = str.match(/(2160p|1080p|720p|480p|360p)/i);
  return match ? match[1] : null;
}

/**
 * Clear the scraper cache
 */
function clearCache() {
  cache.clear();
}

/**
 * Get cache stats
 */
function getCacheStats() {
  return {
    entries: cache.size,
    keys: Array.from(cache.keys())
  };
}

module.exports = {
  scrapeDirectory,
  parseMovieName,
  clearCache,
  getCacheStats
};
