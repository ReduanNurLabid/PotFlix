require('dotenv').config();
const express = require('express');
const path = require('path');
const { execFile } = require('child_process');
const fs = require('fs');
const { scrapeDirectory, parseMovieName, getCacheStats } = require('./scraper');

const app = express();
const PORT = 3000;

// TMDB Configuration
const TMDB_API_KEY = process.env.TMDB_API_KEY || '';
const TMDB_BASE = 'https://api.themoviedb.org/3';
const TMDB_IMG = 'https://image.tmdb.org/t/p';

// TMDB poster cache
const tmdbCache = new Map();

// JSON body parser
app.use(express.json());

// Serve static frontend
app.use(express.static(path.join(__dirname, 'public')));

// ========== VLC CONFIGURATION ==========
const VLC_PATHS = [
    'C:\\Program Files\\VideoLAN\\VLC\\vlc.exe',
    'C:\\Program Files (x86)\\VideoLAN\\VLC\\vlc.exe',
    path.join(process.env.LOCALAPPDATA || '', 'Programs', 'VideoLAN', 'VLC', 'vlc.exe')
];

function findVlcPath() {
    for (const p of VLC_PATHS) {
        try {
            if (fs.existsSync(p)) return p;
        } catch { /* skip */ }
    }
    return null;
}

// ========== CATEGORIES ==========

const CATEGORIES = [
    // Movies
    {
        id: 'english-movies',
        name: 'English Movies',
        type: 'movies',
        url: 'http://172.16.50.7/DHAKA-FLIX-7/English%20Movies/',
        icon: '🎬'
    },
    {
        id: 'english-movies-1080p',
        name: 'English Movies 1080p',
        type: 'movies',
        url: 'http://172.16.50.14/DHAKA-FLIX-14/English%20Movies%20(1080p)/',
        icon: '🎬'
    },
    {
        id: 'imdb-top-250',
        name: 'IMDb Top 250',
        type: 'movies',
        url: 'http://172.16.50.14/DHAKA-FLIX-14/IMDb%20Top-250%20Movies/',
        icon: '⭐'
    },
    {
        id: 'hindi-movies',
        name: 'Hindi Movies',
        type: 'movies',
        url: 'http://172.16.50.14/DHAKA-FLIX-14/Hindi%20Movies/',
        icon: '🎭'
    },
    {
        id: 'south-indian',
        name: 'South Indian Movies',
        type: 'movies',
        url: 'http://172.16.50.14/DHAKA-FLIX-14/SOUTH%20INDIAN%20MOVIES/South%20Movies/',
        icon: '🎭'
    },
    {
        id: 'south-hindi-dubbed',
        name: 'South Hindi Dubbed',
        type: 'movies',
        url: 'http://172.16.50.14/DHAKA-FLIX-14/SOUTH%20INDIAN%20MOVIES/Hindi%20Dubbed/',
        icon: '🎭'
    },
    {
        id: 'kolkata-bangla',
        name: 'Kolkata Bangla Movies',
        type: 'movies',
        url: 'http://172.16.50.7/DHAKA-FLIX-7/Kolkata%20Bangla%20Movies/',
        icon: '🎭'
    },
    {
        id: 'animation',
        name: 'Animation Movies',
        type: 'movies',
        url: 'http://172.16.50.14/DHAKA-FLIX-14/Animation%20Movies/',
        icon: '🧸'
    },
    {
        id: 'animation-1080p',
        name: 'Animation Movies 1080p',
        type: 'movies',
        url: 'http://172.16.50.14/DHAKA-FLIX-14/Animation%20Movies%20(1080p)/',
        icon: '🧸'
    },
    {
        id: 'foreign',
        name: 'Foreign Language Movies',
        type: 'movies',
        url: 'http://172.16.50.7/DHAKA-FLIX-7/Foreign%20Language%20Movies/',
        icon: '🌍'
    },
    {
        id: '3d-movies',
        name: '3D Movies',
        type: 'movies',
        url: 'http://172.16.50.7/DHAKA-FLIX-7/3D%20Movies/',
        icon: '🥽'
    },
    // TV Series
    {
        id: 'tv-web-series',
        name: 'TV & WEB Series',
        type: 'tv',
        url: 'http://172.16.50.12/DHAKA-FLIX-12/TV-WEB-Series/',
        icon: '📺'
    },
    {
        id: 'korean-tv',
        name: 'Korean TV & WEB Series',
        type: 'tv',
        url: 'http://172.16.50.14/DHAKA-FLIX-14/KOREAN%20TV%20%26%20WEB%20Series/',
        icon: '🇰🇷'
    },
    {
        id: 'cartoon-tv',
        name: 'Cartoon TV Series',
        type: 'tv',
        url: 'http://172.16.50.9/DHAKA-FLIX-9/Anime%20%26%20Cartoon%20TV%20Series/',
        icon: '🎨'
    },
    {
        id: 'documentary',
        name: 'Documentary',
        type: 'tv',
        url: 'http://172.16.50.9/DHAKA-FLIX-9/Documentary/',
        icon: '📖'
    },
    {
        id: 'awards-tv-shows',
        name: 'Awards & TV Shows',
        type: 'tv',
        url: 'http://172.16.50.9/DHAKA-FLIX-9/Awards%20%26%20TV%20Shows/',
        icon: '🏆'
    }
];

// ========== API ENDPOINTS ==========

// Get all categories
app.get('/api/categories', (req, res) => {
    res.json(CATEGORIES);
});

// Browse a directory (scrape h5ai)
app.get('/api/browse', async (req, res) => {
    const { url } = req.query;
    if (!url) {
        return res.status(400).json({ error: 'URL parameter required' });
    }

    try {
        const entries = await scrapeDirectory(url);
        res.json(entries);
    } catch (err) {
        res.status(500).json({ error: `Failed to browse: ${err.message}` });
    }
});

// Get TMDB movie info (poster, overview, rating)
app.get('/api/tmdb', async (req, res) => {
    const { title, year, type } = req.query;
    if (!title) {
        return res.status(400).json({ error: 'Title parameter required' });
    }

    const cacheKey = `${title}__${year || ''}__${type || 'movie'}`;
    if (tmdbCache.has(cacheKey)) {
        return res.json(tmdbCache.get(cacheKey));
    }

    try {
        const searchType = type === 'tv' ? 'tv' : 'movie';
        let searchUrl = `${TMDB_BASE}/search/${searchType}?api_key=${TMDB_API_KEY}&query=${encodeURIComponent(title)}&language=en-US&page=1`;
        if (year) {
            searchUrl += searchType === 'tv' ? `&first_air_date_year=${year}` : `&primary_release_year=${year}`;
        }

        const response = await fetch(searchUrl, { signal: AbortSignal.timeout(8000) });
        const data = await response.json();

        if (data.results && data.results.length > 0) {
            const movie = data.results[0];

            let trailerKey = null;
            try {
                const vidRes = await fetch(`${TMDB_BASE}/${searchType}/${movie.id}/videos?api_key=${TMDB_API_KEY}&language=en-US`, { signal: AbortSignal.timeout(5000) });
                const vidData = await vidRes.json();
                if (vidData.results) {
                    const trailer = vidData.results.find(v => v.site === 'YouTube' && v.type === 'Trailer') ||
                        vidData.results.find(v => v.site === 'YouTube');
                    if (trailer) trailerKey = trailer.key;
                }
            } catch (err) { /* ignore trailer error */ }

            const result = {
                id: movie.id,
                title: movie.title || movie.name,
                overview: movie.overview,
                poster: movie.poster_path ? `${TMDB_IMG}/w342${movie.poster_path}` : null,
                backdrop: movie.backdrop_path ? `${TMDB_IMG}/w1280${movie.backdrop_path}` : null,
                rating: movie.vote_average,
                releaseDate: movie.release_date || movie.first_air_date,
                genreIds: movie.genre_ids,
                trailerKey
            };
            tmdbCache.set(cacheKey, result);
            res.json(result);
        } else {
            const empty = { title, poster: null, backdrop: null };
            tmdbCache.set(cacheKey, empty);
            res.json(empty);
        }
    } catch (err) {
        res.json({ title, poster: null, backdrop: null, error: err.message });
    }
});

// ========== SEARCH INDEX ==========
// Pre-built search index for instant results
let searchIndex = [];
let indexBuildTime = 0;
let indexBuilding = false;

const SEARCH_CACHE_FILE = path.join(__dirname, 'search_cache.json');

try {
    if (fs.existsSync(SEARCH_CACHE_FILE)) {
        const cachedData = JSON.parse(fs.readFileSync(SEARCH_CACHE_FILE, 'utf-8'));
        if (cachedData && Array.isArray(cachedData.index)) {
            searchIndex = cachedData.index;
            indexBuildTime = cachedData.time || 0;
            console.log(`✅ Loaded search index from local cache: ${searchIndex.length} items`);
        }
    }
} catch (err) {
    console.error('Failed to load search index cache:', err.message);
}

async function buildSearchIndex() {
    if (indexBuilding) return;
    indexBuilding = true;
    console.log('🔍 Building search index...');
    const startTime = Date.now();
    const newIndex = [];

    for (const cat of CATEGORIES) {
        try {
            const entries = await scrapeDirectory(cat.url);

            for (const entry of entries) {
                if (entry.isDirectory && entry.type === 'yearFolder') {
                    // Crawl year folders for individual movies
                    try {
                        const movies = await scrapeDirectory(entry.url);
                        for (const m of movies) {
                            if (m.isDirectory && (m.type === 'movie' || m.type === 'tv')) {
                                newIndex.push({
                                    ...m,
                                    categoryId: cat.id,
                                    categoryName: cat.name,
                                    _searchText: (m.title || m.name).toLowerCase()
                                });
                            }
                        }
                    } catch { /* skip failed year folders */ }
                } else if (entry.isDirectory && (entry.type === 'movie' || entry.type === 'tv')) {
                    newIndex.push({
                        ...entry,
                        categoryId: cat.id,
                        categoryName: cat.name,
                        _searchText: (entry.title || entry.name).toLowerCase()
                    });
                }
                // For non-year, non-movie folders (like A-L ranges in TV), crawl one level deeper
                else if (entry.isDirectory && !entry.type) {
                    try {
                        const subEntries = await scrapeDirectory(entry.url);
                        for (const sub of subEntries) {
                            if (sub.isDirectory && (sub.type === 'movie' || sub.type === 'tv')) {
                                newIndex.push({
                                    ...sub,
                                    categoryId: cat.id,
                                    categoryName: cat.name,
                                    _searchText: (sub.title || sub.name).toLowerCase()
                                });
                            }
                        }
                    } catch { /* skip */ }
                }
            }
        } catch (err) {
            console.error(`  ⚠ Skipped ${cat.name}: ${err.message}`);
        }
    }

    searchIndex = newIndex;
    indexBuildTime = Date.now();
    indexBuilding = false;
    console.log(`✅ Search index built: ${searchIndex.length} items in ${((Date.now() - startTime) / 1000).toFixed(1)}s`);

    try {
        fs.writeFileSync(SEARCH_CACHE_FILE, JSON.stringify({ index: searchIndex, time: indexBuildTime }), 'utf-8');
    } catch (err) {
        console.error('Failed to save search index cache:', err.message);
    }
}

// Search endpoint — fast substring + fuzzy matching against pre-built index
app.get('/api/search', (req, res) => {
    const { q } = req.query;
    if (!q) return res.status(400).json({ error: 'Query parameter required' });

    const query = q.toLowerCase().trim();
    const queryWords = query.split(/\s+/);

    // Score each entry
    const scored = [];
    for (const entry of searchIndex) {
        const text = entry._searchText;

        // Exact substring match = highest priority
        if (text.includes(query)) {
            scored.push({ ...entry, _score: text.startsWith(query) ? 100 : 80 });
            continue;
        }

        // All words match (order-independent)
        if (queryWords.every(w => text.includes(w))) {
            scored.push({ ...entry, _score: 60 });
            continue;
        }

        // Partial word matching (at least 2 of 3 words match)
        if (queryWords.length >= 2) {
            const matchCount = queryWords.filter(w => text.includes(w)).length;
            if (matchCount >= Math.ceil(queryWords.length * 0.6)) {
                scored.push({ ...entry, _score: 30 + (matchCount / queryWords.length) * 20 });
            }
        }
    }

    // Sort by score desc
    scored.sort((a, b) => b._score - a._score);

    // Deduplicate by title + year + type, keeping highest quality
    const qualities = { '2160p': 4, '1080p': 3, '720p': 2, '480p': 1 };
    function getQ(q) { return qualities[q?.toLowerCase()] || 0; }

    const deduped = new Map();
    for (const entry of scored) {
        // If name isn't parsed properly, skip deduplication uniqueness to be safe
        if (!entry.title && !entry.name) {
            deduped.set(entry.url, entry);
            continue;
        }

        const key = `${(entry.title || entry.name).toLowerCase()}__${entry.year || ''}__${entry.type || ''}`;
        if (!deduped.has(key)) {
            deduped.set(key, entry);
        } else {
            const existing = deduped.get(key);
            if (getQ(entry.quality) > getQ(existing.quality)) {
                // Keep the better quality but maintain the original _score
                entry._score = existing._score;
                deduped.set(key, entry);
            }
        }
    }

    // Now convert mapped values back to array and return top 80
    const results = Array.from(deduped.values())
        .slice(0, 80)
        .map(({ _searchText, _score, ...rest }) => rest);

    res.json(results);
});

// Index stats
app.get('/api/search-index-stats', (req, res) => {
    res.json({
        items: searchIndex.length,
        building: indexBuilding,
        lastBuilt: indexBuildTime ? new Date(indexBuildTime).toISOString() : null
    });
});

// TMDB trending suggestions — filtered to FTP-available only
app.get('/api/suggestions', async (req, res) => {
    const { type } = req.query;
    const mediaType = type === 'tv' ? 'tv' : 'movie';

    try {
        // Fetch multiple pages to get more candidates for matching
        const pages = [1, 2];
        const allResults = [];

        for (const page of pages) {
            try {
                const response = await fetch(
                    `${TMDB_BASE}/trending/${mediaType}/week?api_key=${TMDB_API_KEY}&language=en-US&page=${page}`,
                    { signal: AbortSignal.timeout(8000) }
                );
                const data = await response.json();
                if (data.results) allResults.push(...data.results);
            } catch { /* skip page */ }
        }

        if (!allResults.length) return res.json([]);

        const items = allResults.map(m => ({
            title: m.title || m.name,
            year: (m.release_date || m.first_air_date || '').substring(0, 4),
            poster: m.poster_path ? `${TMDB_IMG}/w342${m.poster_path}` : null,
            backdrop: m.backdrop_path ? `${TMDB_IMG}/w1280${m.backdrop_path}` : null,
            rating: m.vote_average,
            overview: m.overview,
            genreIds: m.genre_ids
        }));

        // Cross-reference with search index (if built)
        if (searchIndex.length > 0) {
            const matched = items.filter(item => {
                const titleLower = item.title.toLowerCase();
                return searchIndex.some(entry => {
                    const entryText = entry._searchText;
                    // Check if the TMDB title closely matches an FTP entry
                    return entryText.includes(titleLower) || titleLower.includes(entryText);
                });
            });

            // If we found enough matches, return only those
            if (matched.length >= 5) {
                return res.json(matched.slice(0, 20));
            }
        }

        // Fallback: return all (index might still be building)
        res.json(items.slice(0, 20));
    } catch (err) {
        res.json([]);
    }
});

// Get latest movies from recent years — with auto-flatten for sub-folder categories
app.get('/api/latest', async (req, res) => {
    const { category, limit = 30 } = req.query;
    const cat = CATEGORIES.find(c => c.id === (category || 'english-movies'));
    if (!cat) return res.status(404).json({ error: 'Category not found' });

    try {
        const entries = await scrapeDirectory(cat.url);
        const results = [];
        const maxItems = parseInt(limit);

        // Strategy 1: Year folders (e.g. (2024), (2025))
        const yearFolders = entries
            .filter(e => e.isDirectory && e.type === 'yearFolder')
            .sort((a, b) => (b.year || 0) - (a.year || 0))
            .slice(0, 3);

        if (yearFolders.length > 0) {
            for (const yf of yearFolders) {
                if (results.length >= maxItems) break;
                try {
                    const movies = await scrapeDirectory(yf.url);
                    const movieEntries = movies.filter(m => m.isDirectory && (m.type === 'movie' || m.type === 'tv'));
                    results.push(...movieEntries.slice(0, maxItems - results.length));
                } catch { /* skip */ }
            }
        }

        // Strategy 2: Direct movie/tv entries
        if (results.length === 0) {
            const directItems = entries.filter(e => e.isDirectory && (e.type === 'movie' || e.type === 'tv'));
            if (directItems.length > 0) {
                results.push(...directItems.slice(0, maxItems));
            }
        }

        // Strategy 3: Auto-flatten sub-folders (A-L, M-R ranges, numbered ranges, etc.)
        if (results.length === 0) {
            const subFolders = entries.filter(e => e.isDirectory);
            // Crawl sub-folders in parallel to find actual content
            const crawlPromises = subFolders.slice(0, 8).map(async (folder) => {
                try {
                    const subEntries = await scrapeDirectory(folder.url);
                    return subEntries.filter(e => e.isDirectory && (e.type === 'movie' || e.type === 'tv'));
                } catch {
                    return [];
                }
            });

            const subResults = await Promise.all(crawlPromises);
            for (const items of subResults) {
                if (results.length >= maxItems) break;
                results.push(...items.slice(0, maxItems - results.length));
            }
        }

        res.json(results);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// Get movie detail - files inside a movie folder
app.get('/api/movie-files', async (req, res) => {
    const { url } = req.query;
    if (!url) return res.status(400).json({ error: 'URL required' });

    try {
        const entries = await scrapeDirectory(url);
        const videos = entries.filter(e => e.isVideo);
        const images = entries.filter(e => e.isImage);
        const folders = entries.filter(e => e.isDirectory);

        res.json({ videos, images, folders, all: entries });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// TMDB genre map
app.get('/api/genres', async (req, res) => {
    try {
        const [movieRes, tvRes] = await Promise.all([
            fetch(`${TMDB_BASE}/genre/movie/list?api_key=${TMDB_API_KEY}&language=en-US`),
            fetch(`${TMDB_BASE}/genre/tv/list?api_key=${TMDB_API_KEY}&language=en-US`)
        ]);
        const movies = await movieRes.json();
        const tv = await tvRes.json();
        const map = {};
        [...(movies.genres || []), ...(tv.genres || [])].forEach(g => map[g.id] = g.name);
        res.json(map);
    } catch (err) {
        res.json({});
    }
});

// ========== VLC LAUNCH ==========
app.post('/api/play-vlc', (req, res) => {
    const { url, urls } = req.body;
    if (!url && (!urls || !Array.isArray(urls) || urls.length === 0)) {
        return res.status(400).json({ error: 'url string or urls array required' });
    }

    const vlcPath = findVlcPath();
    if (!vlcPath) {
        return res.status(404).json({
            error: 'VLC not found. Please install VLC from https://www.videolan.org/',
            searchedPaths: VLC_PATHS
        });
    }

    try {
        const vlcArgs = urls ? [...urls] : [url];
        const child = execFile(vlcPath, vlcArgs, {
            detached: true,
            stdio: 'ignore'
        });
        child.unref();
        res.json({ success: true, vlcPath });
    } catch (err) {
        res.status(500).json({ error: `Failed to launch VLC: ${err.message}` });
    }
});

// VLC status check
app.get('/api/vlc-status', (req, res) => {
    const vlcPath = findVlcPath();
    res.json({ installed: !!vlcPath, path: vlcPath });
});

// ========== TV SERIES EPISODES ==========
app.get('/api/series-episodes', async (req, res) => {
    const { url } = req.query;
    if (!url) return res.status(400).json({ error: 'URL required' });

    try {
        const entries = await scrapeDirectory(url);
        const seasons = [];
        const looseVideos = [];

        // Find season folders
        const seasonFolders = entries
            .filter(e => e.isDirectory && /season\s*\d+/i.test(e.name))
            .sort((a, b) => {
                const numA = parseInt(a.name.match(/\d+/)?.[0] || '0');
                const numB = parseInt(b.name.match(/\d+/)?.[0] || '0');
                return numA - numB;
            });

        // Fetch episodes for each season
        for (const sf of seasonFolders) {
            try {
                const seasonEntries = await scrapeDirectory(sf.url);
                const episodes = seasonEntries
                    .filter(e => e.isVideo)
                    .map(e => {
                        const epMatch = e.name.match(/S(\d{1,2})E(\d{1,2})/i);
                        const qualMatch = e.name.match(/(2160p|1080p|720p|480p)/i);
                        return {
                            ...e,
                            season: epMatch ? parseInt(epMatch[1]) : null,
                            episode: epMatch ? parseInt(epMatch[2]) : null,
                            episodeLabel: epMatch ? `Episode ${parseInt(epMatch[2])}` : e.name,
                            quality: qualMatch ? qualMatch[1] : null
                        };
                    })
                    .sort((a, b) => (a.episode || 0) - (b.episode || 0));

                seasons.push({
                    name: sf.name,
                    url: sf.url,
                    number: parseInt(sf.name.match(/\d+/)?.[0] || '0'),
                    episodes
                });
            } catch { /* skip failed seasons */ }
        }

        // Also check for loose video files (no season folders)
        const directVideos = entries.filter(e => e.isVideo);
        if (directVideos.length > 0) {
            for (const v of directVideos) {
                const epMatch = v.name.match(/S(\d{1,2})E(\d{1,2})/i);
                const qualMatch = v.name.match(/(2160p|1080p|720p|480p)/i);
                looseVideos.push({
                    ...v,
                    season: epMatch ? parseInt(epMatch[1]) : null,
                    episode: epMatch ? parseInt(epMatch[2]) : null,
                    episodeLabel: epMatch ? `Episode ${parseInt(epMatch[2])}` : v.name,
                    quality: qualMatch ? qualMatch[1] : null
                });
            }
        }

        res.json({ seasons, looseVideos });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// Cache stats
app.get('/api/cache-stats', (req, res) => {
    res.json(getCacheStats());
});

// Start server
const serverInstance = app.listen(PORT, () => {
    console.log(`\n🎬 PotFlix Streamer running at http://localhost:${PORT}\n`);
    console.log(`   Categories: ${CATEGORIES.length}`);
    console.log(`   TMDB API: Enabled`);
    const vlcPath = findVlcPath();
    console.log(`   VLC: ${vlcPath ? '✅ Found at ' + vlcPath : '❌ Not found'}`);
    console.log(`   Search: Building index in background...\n`);

    // Build search index after a short delay if cache is old or empty
    setTimeout(() => {
        const CACHE_TTL = 12 * 60 * 60 * 1000; // 12 hours
        if (searchIndex.length === 0 || (Date.now() - indexBuildTime > CACHE_TTL)) {
            buildSearchIndex();
        } else {
            console.log('✅ Using cached search index from disk, skipping initial rebuild.');
        }
    }, 2000);

    // Refresh index every 2 hours
    setInterval(() => buildSearchIndex(), 2 * 60 * 60 * 1000);
});

module.exports = serverInstance;
