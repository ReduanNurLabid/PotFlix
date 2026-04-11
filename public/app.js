// ========================================
// PotFlix Streamer — Frontend App
// ========================================

(() => {
    'use strict';

    // ===== STATE =====
    const state = {
        categories: [],
        genres: {},
        currentView: 'home', // home | movies | tv | browse | search
        browseHistory: [],   // stack of { url, title }
        heroMovie: null,
        tmdbCache: new Map(),
        searchTimeout: null,
        moviesLoaded: false,
        tvLoaded: false
    };

    // ===== DOM REFS =====
    const $ = id => document.getElementById(id);
    const navbar = $('navbar');
    const heroBackdrop = $('heroBackdrop');
    const heroTitle = $('heroTitle');
    const heroOverview = $('heroOverview');
    const heroMeta = $('heroMeta');
    const heroBadge = $('heroBadge');
    const heroPlay = $('heroPlay');
    const heroInfo = $('heroInfo');
    const homeView = $('homeView');
    const homeRows = $('homeRows');
    const moviesView = $('moviesView');
    const moviesRows = $('moviesRows');
    const tvView = $('tvView');
    const tvRows = $('tvRows');
    const browseView = $('browseView');
    const browseTitle = $('browseTitle');
    const browseGrid = $('browseGrid');
    const browseBack = $('browseBack');
    const browseBreadcrumb = $('browseBreadcrumb');
    const browseLoading = $('browseLoading');
    const searchView = $('searchView');
    const searchGrid = $('searchGrid');
    const searchTitle = $('searchTitle');
    const searchLoading = $('searchLoading');
    const searchEmpty = $('searchEmpty');
    const searchToggle = $('searchToggle');
    const searchInput = $('searchInput');
    const searchClear = $('searchClear');
    const searchContainer = $('searchContainer');
    const movieModal = $('movieModal');
    const modalClose = $('modalClose');
    const modalBackdrop = $('modalBackdrop');
    const modalPoster = $('modalPoster');
    const modalTitle = $('modalTitle');
    const modalMeta = $('modalMeta');
    const modalOverview = $('modalOverview');
    const modalPlay = $('modalPlay');
    const modalBrowseFiles = $('modalBrowseFiles');
    const modalFiles = $('modalFiles');
    const toast = $('toast');
    const heroBanner = $('heroBanner');

    // ===== API HELPERS =====
    async function api(endpoint) {
        const res = await fetch(endpoint);
        if (!res.ok) throw new Error(`API error: ${res.status}`);
        return res.json();
    }

    async function fetchCategories() {
        return api('/api/categories');
    }

    async function browseUrl(url) {
        return api(`/api/browse?url=${encodeURIComponent(url)}`);
    }

    async function fetchTmdb(title, year, type) {
        const key = `${title}__${year || ''}__${type || 'movie'}`;
        if (state.tmdbCache.has(key)) return state.tmdbCache.get(key);

        try {
            const data = await api(`/api/tmdb?title=${encodeURIComponent(title)}&year=${year || ''}&type=${type || 'movie'}`);
            state.tmdbCache.set(key, data);
            return data;
        } catch {
            return null;
        }
    }

    async function fetchLatest(categoryId) {
        return api(`/api/latest?category=${categoryId}`);
    }

    async function fetchMovieFiles(url) {
        return api(`/api/movie-files?url=${encodeURIComponent(url)}`);
    }

    async function fetchSeriesEpisodes(url) {
        return api(`/api/series-episodes?url=${encodeURIComponent(url)}`);
    }

    async function fetchGenres() {
        try {
            state.genres = await api('/api/genres');
        } catch { /* ignore */ }
    }

    // ===== HELPERS =====
    function showToast(msg) {
        toast.textContent = msg;
        toast.classList.remove('hidden');
        requestAnimationFrame(() => toast.classList.add('show'));
        setTimeout(() => {
            toast.classList.remove('show');
            setTimeout(() => toast.classList.add('hidden'), 400);
        }, 3000);
    }

    async function playInVlc(urlOrUrls) {
        showToast('🎬 Opening in VLC...');
        try {
            const body = Array.isArray(urlOrUrls) ? { urls: urlOrUrls } : { url: urlOrUrls };
            const res = await fetch('/api/play-vlc', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });
            const data = await res.json();
            if (!res.ok) {
                showToast('❌ ' + (data.error || 'Failed to launch VLC'));
            }
        } catch (err) {
            showToast('❌ Could not connect to server');
        }
    }

    function genreNames(ids) {
        if (!ids || !ids.length) return '';
        return ids.map(id => state.genres[id]).filter(Boolean).slice(0, 3).join(' · ');
    }

    function switchView(view) {
        state.currentView = view;
        homeView.classList.toggle('active-view', view === 'home');
        moviesView.classList.toggle('active-view', view === 'movies');
        tvView.classList.toggle('active-view', view === 'tv');
        browseView.classList.toggle('active-view', view === 'browse');
        searchView.classList.toggle('active-view', view === 'search');

        // Show/hide hero
        heroBanner.style.display = view === 'home' ? '' : 'none';

        // Update nav links
        document.querySelectorAll('.nav-link').forEach(link => {
            link.classList.remove('active');
            if (link.dataset.section === view || (view === 'browse' && link.dataset.section === state.browseType)) {
                link.classList.add('active');
            }
        });
    }

    // ===== CARD RENDERING =====
    function cleanTitle(title) {
        // Strip "003. " style numbering prefix
        return title.replace(/^\d{2,4}\.\s*/, '');
    }

    function createMovieCard(entry) {
        const card = document.createElement('div');
        card.className = 'movie-card';
        card.dataset.url = entry.url;
        card.dataset.title = entry.title || entry.name;
        card.dataset.year = entry.year || '';
        card.dataset.type = entry.type || 'movie';

        const title = cleanTitle(entry.title || entry.name);
        const year = entry.year || '';
        const quality = entry.quality || '';

        // Poster placeholder
        card.innerHTML = `
      <img class="card-poster" src="" alt="${title}" loading="lazy" 
        onerror="this.src='data:image/svg+xml,${encodeURIComponent(posterPlaceholder(title))}'">
      <div class="card-play-icon">
        <svg viewBox="0 0 24 24" width="24" height="24"><path fill="white" d="M8 5v14l11-7z"/></svg>
      </div>
      <div class="card-overlay">
        <div class="card-title">${escapeHtml(title)}</div>
        <div class="card-meta">
          ${year ? `<span class="card-year">${year}</span>` : ''}
          ${quality ? `<span class="card-quality">${quality}</span>` : ''}
          <span class="card-rating"></span>
        </div>
      </div>
    `;

        // Lazy-load poster via TMDB
        const posterImg = card.querySelector('.card-poster');
        const ratingEl = card.querySelector('.card-rating');

        loadPoster(entry, posterImg, ratingEl);

        card.addEventListener('click', () => openMovieModal(entry));

        return card;
    }

    async function loadPoster(entry, imgEl, ratingEl) {
        const title = entry.title || entry.name;
        const year = entry.year || '';
        const type = entry.type === 'tv' ? 'tv' : 'movie';

        const tmdb = await fetchTmdb(title, year, type);
        if (tmdb && tmdb.poster) {
            imgEl.src = tmdb.poster;
        } else {
            // Try FTP thumbnail
            if (entry.url && entry.isDirectory) {
                imgEl.src = `data:image/svg+xml,${encodeURIComponent(posterPlaceholder(title))}`;
            }
        }
        if (tmdb && tmdb.rating) {
            ratingEl.textContent = `★ ${tmdb.rating.toFixed(1)}`;
        }
    }

    function posterPlaceholder(title) {
        const colors = ['#e50914', '#1a1a3e', '#2d1b69', '#0d253f', '#3d1c00', '#1b3a25'];
        const color = colors[Math.abs(hashStr(title)) % colors.length];
        const initials = title.split(' ').map(w => w[0]).join('').substring(0, 3).toUpperCase();

        return `<svg xmlns="http://www.w3.org/2000/svg" width="200" height="300" viewBox="0 0 200 300">
      <rect width="200" height="300" fill="${color}" rx="8"/>
      <text x="100" y="140" text-anchor="middle" fill="rgba(255,255,255,0.5)" font-size="48" font-family="Inter,sans-serif" font-weight="800">${initials}</text>
      <text x="100" y="180" text-anchor="middle" fill="rgba(255,255,255,0.3)" font-size="11" font-family="Inter,sans-serif" font-weight="500">${escapeXml(title.substring(0, 25))}</text>
    </svg>`;
    }

    function hashStr(str) {
        let hash = 0;
        for (let i = 0; i < str.length; i++) {
            hash = ((hash << 5) - hash) + str.charCodeAt(i);
            hash |= 0;
        }
        return hash;
    }

    function createFolderCard(entry, onClick) {
        const card = document.createElement('div');
        card.className = 'folder-card';

        const name = entry.name || '';
        const icon = entry.type === 'yearFolder' ? '📅' : '📁';

        card.innerHTML = `
      <div style="text-align:center;">
        <div class="folder-icon">${icon}</div>
        <div class="folder-name">${escapeHtml(name)}</div>
      </div>
    `;

        card.addEventListener('click', () => onClick(entry));
        return card;
    }

    function createSkeletonCards(count, type) {
        const fragment = document.createDocumentFragment();
        for (let i = 0; i < count; i++) {
            const el = document.createElement('div');
            el.className = type === 'folder' ? 'skeleton-folder' : 'skeleton-card';
            fragment.appendChild(el);
        }
        return fragment;
    }

    function escapeHtml(str) {
        const el = document.createElement('span');
        el.textContent = str;
        return el.innerHTML;
    }

    function escapeXml(str) {
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    // ===== HOME VIEW =====
    async function initHome() {
        homeRows.innerHTML = '';

        // Show skeleton rows first
        const categoriesToShow = state.categories.filter(c =>
            ['english-movies', 'hindi-movies', 'imdb-top-250', 'animation', 'tv-web-series', 'korean-tv'].includes(c.id)
        );

        for (const cat of categoriesToShow) {
            const row = createCategoryRow(cat);
            homeRows.appendChild(row);

            // Load movies asynchronously
            loadCategoryRow(cat, row);
        }
    }

    function createCategoryRow(cat) {
        const row = document.createElement('div');
        row.className = 'category-row';
        row.id = `row-${cat.id}`;

        const isDynamic = cat.id.startsWith('trending') || cat.id.startsWith('suggestions');
        const exploreBtnHtml = isDynamic ? '' : `<button class="row-explore" data-category-id="${cat.id}">Explore All →</button>`;

        row.innerHTML = `
      <div class="row-header">
        <h3 class="row-title">
          <span class="icon">${cat.icon || '🎬'}</span>
          ${escapeHtml(cat.name)}
        </h3>
        ${exploreBtnHtml}
      </div>
      <div class="row-slider-wrapper">
        <button class="row-arrow left" aria-label="Scroll left">‹</button>
        <div class="row-slider" id="slider-${cat.id}"></div>
        <button class="row-arrow right" aria-label="Scroll right">›</button>
      </div>
    `;

        // Skeleton loading
        const slider = row.querySelector('.row-slider');
        slider.appendChild(createSkeletonCards(8, 'card'));

        // Explore button — show flat grid, not folder navigation
        const exploreBtn = row.querySelector('.row-explore');
        if (exploreBtn) {
            exploreBtn.addEventListener('click', () => {
                exploreCategory(cat);
            });
        }

        // Arrow scroll
        const leftArrow = row.querySelector('.row-arrow.left');
        const rightArrow = row.querySelector('.row-arrow.right');

        leftArrow.addEventListener('click', () => {
            slider.scrollBy({ left: -600, behavior: 'smooth' });
        });
        rightArrow.addEventListener('click', () => {
            slider.scrollBy({ left: 600, behavior: 'smooth' });
        });

        return row;
    }

    async function loadCategoryRow(cat, row) {
        const slider = row.querySelector('.row-slider');

        try {
            const movies = await fetchLatest(cat.id);

            slider.innerHTML = '';

            if (!movies.length) {
                slider.innerHTML = '<div style="padding:20px;color:var(--color-text-muted);">No items found</div>';
                return;
            }

            // Show movie/tv entries only (no folders)
            const moviesToShow = movies.filter(m => m.type === 'movie' || m.type === 'tv').slice(0, 30);

            if (moviesToShow.length === 0) {
                slider.innerHTML = '<div style="padding:20px;color:var(--color-text-muted);">Loading content...</div>';
                return;
            }

            // Store data on the row for sorting/filtering later
            row._movieData = moviesToShow;

            for (const movie of moviesToShow) {
                slider.appendChild(createMovieCard(movie));
            }

            // Set hero from first movie with TMDB data
            if (!state.heroMovie && moviesToShow.length > 0) {
                setHero(moviesToShow[Math.floor(Math.random() * Math.min(5, moviesToShow.length))]);
            }

        } catch (err) {
            slider.innerHTML = `<div style="padding:20px;color:var(--color-text-muted);">Failed to load: ${err.message}</div>`;
        }
    }

    // ===== HERO =====
    async function setHero(movie) {
        if (!movie) return;
        state.heroMovie = movie;

        const title = cleanTitle(movie.title || movie.name);
        heroTitle.textContent = title;

        const tmdb = await fetchTmdb(title, movie.year, movie.type);

        if (tmdb) {
            if (tmdb.backdrop) {
                heroBackdrop.style.backgroundImage = `url(${tmdb.backdrop})`;
            }
            heroOverview.textContent = tmdb.overview || 'Stream this title directly in VLC.';

            const parts = [];
            if (tmdb.rating) parts.push(`<span class="rating">★ ${tmdb.rating.toFixed(1)}</span>`);
            if (movie.year) parts.push(`<span class="year">${movie.year}</span>`);
            if (movie.quality) parts.push(`<span class="quality">${movie.quality}</span>`);
            const genres = genreNames(tmdb.genreIds);
            if (genres) parts.push(`<span class="genre">${genres}</span>`);
            heroMeta.innerHTML = parts.join('');

            heroBadge.textContent = tmdb.rating >= 7.5 ? '⭐ Highly Rated' : '🎬 Featured';
        } else {
            heroOverview.textContent = 'Stream this title directly in VLC.';
            const parts = [];
            if (movie.year) parts.push(`<span class="year">${movie.year}</span>`);
            if (movie.quality) parts.push(`<span class="quality">${movie.quality}</span>`);
            heroMeta.innerHTML = parts.join('');
        }

        heroPlay.style.display = '';
        heroInfo.style.display = '';

        heroPlay.onclick = () => handlePlayMovie(movie);
        heroInfo.onclick = () => openMovieModal(movie);
    }

    // ===== BROWSE VIEW =====
    function navigateToCategory(cat) {
        state.browseType = cat.type;
        state.browseHistory = [{ url: cat.url, title: cat.name }];
        loadBrowseView(cat.url, cat.name);
    }

    // Explore All — flat movie grid using auto-flatten API
    async function exploreCategory(cat) {
        switchView('browse');
        state.browseType = cat.type;
        state.browseHistory = [{ url: cat.url, title: cat.name }];
        browseTitle.textContent = cat.name;
        browseBreadcrumb.innerHTML = '';
        browseGrid.innerHTML = '';
        browseLoading.classList.remove('hidden');

        try {
            const movies = await api(`/api/latest?category=${encodeURIComponent(cat.id)}&limit=100`);
            browseLoading.classList.add('hidden');
            browseGrid.innerHTML = '';

            const moviesToShow = movies.filter(m => m.type === 'movie' || m.type === 'tv');

            if (moviesToShow.length === 0) {
                browseGrid.innerHTML = '<div class="empty-state"><p>No items found in this category.</p></div>';
                return;
            }

            for (const movie of moviesToShow) {
                browseGrid.appendChild(createMovieCard(movie));
            }
        } catch (err) {
            browseLoading.classList.add('hidden');
            browseGrid.innerHTML = `<div class="empty-state"><p>Failed to load: ${err.message}</p></div>`;
        }
    }

    function navigateToBrowse(url, title, cat) {
        if (cat) {
            state.browseType = cat.type;
            state.browseHistory = [
                { url: cat.url, title: cat.name },
                { url, title }
            ];
        } else {
            state.browseHistory.push({ url, title });
        }
        loadBrowseView(url, title);
    }

    async function loadBrowseView(url, title) {
        switchView('browse');
        browseTitle.textContent = title;
        browseGrid.innerHTML = '';
        browseLoading.classList.remove('hidden');
        updateBreadcrumb();

        try {
            const entries = await browseUrl(url);

            browseGrid.innerHTML = '';
            browseLoading.classList.add('hidden');

            if (!entries.length) {
                browseGrid.innerHTML = '<div class="empty-state"><p>This folder is empty.</p></div>';
                return;
            }

            // Separate folders and files
            const folders = entries.filter(e => e.isDirectory);
            const videos = entries.filter(e => e.isVideo);
            const others = entries.filter(e => !e.isDirectory && !e.isVideo);

            // Render folders
            for (const folder of folders) {
                if (folder.type === 'movie' || folder.type === 'tv') {
                    browseGrid.appendChild(createMovieCard(folder));
                } else {
                    browseGrid.appendChild(createFolderCard(folder, (entry) => {
                        navigateToBrowse(entry.url, entry.name);
                    }));
                }
            }

            // Render video files directly
            for (const video of videos) {
                const card = createVideoFileCard(video);
                browseGrid.appendChild(card);
            }

        } catch (err) {
            browseLoading.classList.add('hidden');
            browseGrid.innerHTML = `<div class="empty-state"><p>Failed to load: ${err.message}</p></div>`;
        }
    }

    function createVideoFileCard(video) {
        const card = document.createElement('div');
        card.className = 'folder-card';
        card.style.height = '140px';

        const name = video.name || '';
        card.innerHTML = `
      <div style="text-align:center;width:100%;">
        <div class="folder-icon">🎬</div>
        <div class="folder-name" style="font-size:0.72rem;margin-bottom:8px;">${escapeHtml(name.substring(0, 60))}</div>
        <button class="file-play" style="font-size:0.7rem;">▶ Play in VLC</button>
      </div>
    `;

        card.querySelector('.file-play').addEventListener('click', (e) => {
            e.stopPropagation();
            playInVlc(video.url);
        });

        card.addEventListener('click', () => playInVlc(video.url));
        return card;
    }

    function updateBreadcrumb() {
        browseBreadcrumb.innerHTML = '';
        state.browseHistory.forEach((item, i) => {
            if (i > 0) {
                const sep = document.createElement('span');
                sep.className = 'breadcrumb-separator';
                sep.textContent = '›';
                browseBreadcrumb.appendChild(sep);
            }

            const crumb = document.createElement('span');
            crumb.className = 'breadcrumb-item';
            crumb.textContent = item.title;

            if (i < state.browseHistory.length - 1) {
                crumb.addEventListener('click', () => {
                    state.browseHistory = state.browseHistory.slice(0, i + 1);
                    loadBrowseView(item.url, item.title);
                });
            }

            browseBreadcrumb.appendChild(crumb);
        });
    }

    // ===== MOVIE MODAL =====
    async function openMovieModal(entry) {
        movieModal.classList.remove('hidden');
        document.body.style.overflow = 'hidden';

        const title = cleanTitle(entry.title || entry.name);
        const year = entry.year || '';
        const type = entry.type === 'tv' ? 'tv' : 'movie';

        modalTitle.textContent = title;
        modalOverview.textContent = 'Loading...';
        modalMeta.innerHTML = '';
        modalFiles.innerHTML = '';
        modalPoster.src = `data:image/svg+xml,${encodeURIComponent(posterPlaceholder(title))}`;
        modalBackdrop.style.backgroundImage = '';

        // Current state for play button
        let currentVideoUrl = null;
        let currentPlaylistUrls = null;
        modalPlay.innerHTML = '<span class="icon">▶</span> Play in VLC';

        // Fetch TMDB info
        const tmdb = await fetchTmdb(title, year, type);

        const btnTrailer = $('modalWatchTrailer');
        if (btnTrailer) {
            btnTrailer.classList.add('hidden');
            btnTrailer.onclick = null;
        }

        if (tmdb) {
            if (tmdb.poster) modalPoster.src = tmdb.poster;
            if (tmdb.backdrop) {
                modalBackdrop.style.backgroundImage = `url(${tmdb.backdrop})`;
            }
            if (tmdb.trailerKey && btnTrailer) {
                btnTrailer.classList.remove('hidden');
                btnTrailer.onclick = () => openTrailer(tmdb.trailerKey);
            }
            modalOverview.textContent = tmdb.overview || 'No description available.';

            const parts = [];
            if (tmdb.rating) parts.push(`<span class="rating">★ ${tmdb.rating.toFixed(1)}</span>`);
            if (year) parts.push(`<span class="year">${year}</span>`);
            if (entry.quality) parts.push(`<span class="quality">${entry.quality}</span>`);
            if (entry.isDualAudio) parts.push(`<span class="quality">Dual Audio</span>`);
            const genres = genreNames(tmdb.genreIds);
            if (genres) parts.push(`<span class="genre">${genres}</span>`);
            modalMeta.innerHTML = parts.join('');
        } else {
            modalOverview.textContent = 'No description available.';
            const parts = [];
            if (year) parts.push(`<span class="year">${year}</span>`);
            if (entry.quality) parts.push(`<span class="quality">${entry.quality}</span>`);
            modalMeta.innerHTML = parts.join('');
        }

        // ===== TV SERIES: Season/Episode View =====
        if (entry.isDirectory && (entry.type === 'tv' || entry.seriesInfo)) {
            try {
                const seriesData = await fetchSeriesEpisodes(entry.url);

                if (seriesData.seasons && seriesData.seasons.length > 0) {
                    // Build season tabs + episode list
                    const container = document.createElement('div');
                    container.className = 'series-container';

                    // Season tabs
                    const tabsDiv = document.createElement('div');
                    tabsDiv.className = 'season-tabs';

                    const episodeListDiv = document.createElement('div');
                    episodeListDiv.className = 'episode-list';

                    function showSeason(seasonIdx) {
                        // Update tab active state
                        tabsDiv.querySelectorAll('.season-tab').forEach((t, i) => {
                            t.classList.toggle('active', i === seasonIdx);
                        });

                        const season = seriesData.seasons[seasonIdx];
                        episodeListDiv.innerHTML = '';

                        if (!season.episodes.length) {
                            episodeListDiv.innerHTML = '<p style="color:var(--color-text-muted);padding:12px;">No episodes found</p>';
                            return;
                        }

                        // Group episodes by episode number for quality picker
                        const epGroups = new Map();
                        for (const ep of season.episodes) {
                            const key = ep.episode || ep.name;
                            if (!epGroups.has(key)) epGroups.set(key, []);
                            epGroups.get(key).push(ep);
                        }

                        for (const [epKey, episodes] of epGroups) {
                            const epEl = document.createElement('div');
                            epEl.className = 'episode-item';

                            const epNum = typeof epKey === 'number' ? epKey : null;
                            const label = epNum ? `Episode ${epNum}` : episodes[0].episodeLabel;

                            let buttonsHtml = '';
                            if (episodes.length > 1) {
                                // Multiple qualities — show quality picker
                                buttonsHtml = episodes.map(ep => {
                                    const q = ep.quality || 'Play';
                                    return `<button class="quality-btn" data-url="${escapeHtml(ep.url)}">▶ ${q}</button>`;
                                }).join('');
                            } else {
                                const q = episodes[0].quality;
                                buttonsHtml = `<button class="quality-btn" data-url="${escapeHtml(episodes[0].url)}">▶ ${q ? q : 'Play'}</button>`;
                            }

                            epEl.innerHTML = `
                                <div class="episode-info">
                                    <span class="episode-number">${epNum ? 'E' + String(epNum).padStart(2, '0') : '🎬'}</span>
                                    <span class="episode-label">${escapeHtml(label)}</span>
                                </div>
                                <div class="episode-actions">${buttonsHtml}</div>
                            `;

                            // Attach play handlers
                            epEl.querySelectorAll('.quality-btn').forEach(btn => {
                                btn.addEventListener('click', (e) => {
                                    e.stopPropagation();
                                    playInVlc(btn.dataset.url);
                                });
                            });

                            episodeListDiv.appendChild(epEl);
                        }

                        // Set the entire season as a playlist for the main play button
                        if (season.episodes.length > 0) {
                            currentPlaylistUrls = Array.from(epGroups.values()).map(eps => eps[0].url);
                            currentVideoUrl = null; // Clear single video fallback
                            modalPlay.innerHTML = '<span class="icon">▶</span> Play Season';
                        }
                    }

                    // Create tabs
                    seriesData.seasons.forEach((season, idx) => {
                        const tab = document.createElement('button');
                        tab.className = 'season-tab' + (idx === 0 ? ' active' : '');
                        tab.textContent = season.name;
                        tab.addEventListener('click', () => showSeason(idx));
                        tabsDiv.appendChild(tab);
                    });

                    container.appendChild(tabsDiv);
                    container.appendChild(episodeListDiv);
                    modalFiles.appendChild(container);

                    // Show first season
                    showSeason(0);

                } else if (seriesData.looseVideos && seriesData.looseVideos.length > 0) {
                    // No season folders, just loose episodes
                    renderVideoFilesWithQuality(seriesData.looseVideos, modalFiles);
                    if (seriesData.looseVideos.length > 0) {
                        currentVideoUrl = seriesData.looseVideos[0].url;
                    }
                } else {
                    // Fallback to regular file listing
                    await loadRegularFiles(entry, modalFiles, (url) => { currentVideoUrl = url; });
                }
            } catch {
                await loadRegularFiles(entry, modalFiles, (url) => { currentVideoUrl = url; });
            }
        }
        // ===== MOVIE: Regular file listing with quality picker =====
        else if (entry.isDirectory && entry.url) {
            await loadRegularFiles(entry, modalFiles, (url) => { currentVideoUrl = url; });
        }

        // Play button — plays season playlist or single video
        modalPlay.onclick = () => {
            if (currentPlaylistUrls) {
                playInVlc(currentPlaylistUrls);
            } else if (currentVideoUrl) {
                playInVlc(currentVideoUrl);
            } else if (entry.isVideo) {
                playInVlc(entry.url);
            } else {
                showToast('No video file found. Try browsing files.');
            }
        };

        // Browse files button
        modalBrowseFiles.onclick = () => {
            closeModal();
            if (entry.isDirectory) {
                navigateToBrowse(entry.url, title);
            }
        };
    }

    // Helper: load regular movie files with quality picker
    async function loadRegularFiles(entry, container, setVideoUrl) {
        try {
            const fileData = await fetchMovieFiles(entry.url);

            if (fileData.videos && fileData.videos.length > 0) {
                setVideoUrl(fileData.videos[0].url);
                renderVideoFilesWithQuality(fileData.videos, container);
            }

            // Sub-folders (seasons or other)
            if (fileData.folders && fileData.folders.length > 0) {
                const foldersSection = document.createElement('div');
                foldersSection.innerHTML = '<h4 style="color:var(--color-text-bright);margin:16px 0 8px;font-size:0.9rem;">Folders</h4>';
                for (const folder of fileData.folders) {
                    const folderEl = document.createElement('div');
                    folderEl.className = 'file-item';
                    folderEl.innerHTML = `
                        <span class="file-icon">📁</span>
                        <span class="file-name">${escapeHtml(folder.name)}</span>
                        <button class="file-play" style="background:var(--bg-card-hover);">Browse</button>
                    `;
                    folderEl.addEventListener('click', () => {
                        closeModal();
                        navigateToBrowse(folder.url, folder.name);
                    });
                    foldersSection.appendChild(folderEl);
                }
                container.appendChild(foldersSection);
            }
        } catch {
            container.innerHTML += '<p style="color:var(--color-text-muted);font-size:0.85rem;">Could not load files.</p>';
        }
    }

    // Helper: render video files grouped by quality
    function renderVideoFilesWithQuality(videos, container) {
        // Group by quality
        const qualityGroups = new Map();
        for (const v of videos) {
            const qMatch = v.name.match(/(2160p|1080p|720p|480p)/i);
            const quality = qMatch ? qMatch[1] : 'default';
            if (!qualityGroups.has(quality)) qualityGroups.set(quality, []);
            qualityGroups.get(quality).push(v);
        }

        const hasMultipleQualities = qualityGroups.size > 1;

        if (hasMultipleQualities) {
            // Show quality picker header
            const header = document.createElement('h4');
            header.style.cssText = 'color:var(--color-text-bright);margin-bottom:10px;font-size:0.9rem;';
            header.textContent = 'Choose Quality';
            container.appendChild(header);

            const qualityPicker = document.createElement('div');
            qualityPicker.className = 'quality-picker';

            for (const [quality, files] of qualityGroups) {
                const btn = document.createElement('button');
                btn.className = 'quality-picker-btn';
                const label = quality === 'default' ? 'Play' : quality;
                btn.innerHTML = `
                    <span class="quality-label">▶ ${label}</span>
                    <span class="quality-detail">${files[0].name.substring(0, 50)}...</span>
                `;
                btn.addEventListener('click', () => playInVlc(files[0].url));
                qualityPicker.appendChild(btn);
            }

            container.appendChild(qualityPicker);
        } else {
            // Single quality — show file list
            const header = document.createElement('h4');
            header.style.cssText = 'color:var(--color-text-bright);margin-bottom:8px;font-size:0.9rem;';
            header.textContent = 'Files';
            container.appendChild(header);

            for (const video of videos) {
                const fileEl = document.createElement('div');
                fileEl.className = 'file-item';
                fileEl.innerHTML = `
                    <span class="file-icon">🎬</span>
                    <span class="file-name">${escapeHtml(video.name)}</span>
                    <button class="file-play">▶ Play</button>
                `;
                fileEl.querySelector('.file-play').addEventListener('click', (e) => {
                    e.stopPropagation();
                    playInVlc(video.url);
                });
                fileEl.addEventListener('click', () => playInVlc(video.url));
                container.appendChild(fileEl);
            }
        }
    }

    function closeModal() {
        movieModal.classList.add('hidden');
        document.body.style.overflow = '';
        closeTrailer(); // Stop trailer if playing
    }

    const trailerOverlay = $('trailerOverlay');
    const trailerClose = $('trailerClose');
    const trailerIframe = $('trailerIframe');

    function openTrailer(key) {
        if (!trailerIframe || !trailerOverlay) return;
        trailerIframe.src = `https://www.youtube.com/embed/${key}?autoplay=1`;
        trailerOverlay.classList.remove('hidden');
    }

    function closeTrailer() {
        if (!trailerIframe || !trailerOverlay) return;
        trailerOverlay.classList.add('hidden');
        trailerIframe.src = '';
    }

    if (trailerClose && trailerOverlay) {
        trailerClose.addEventListener('click', closeTrailer);
        trailerOverlay.addEventListener('click', (e) => {
            if (e.target === trailerOverlay) closeTrailer();
        });
    }

    async function handlePlayMovie(movie) {
        if (movie.isVideo) {
            playInVlc(movie.url);
            return;
        }

        if (movie.isDirectory && movie.url) {
            try {
                showToast('Finding video file...');
                const files = await fetchMovieFiles(movie.url);
                if (files.videos && files.videos.length > 0) {
                    playInVlc(files.videos[0].url);
                } else {
                    showToast('No video found. Opening folder...');
                    navigateToBrowse(movie.url, movie.title || movie.name);
                }
            } catch {
                showToast('Error loading files.');
            }
        }
    }

    // ===== SEARCH =====
    function initSearch() {
        searchToggle.addEventListener('click', () => {
            searchContainer.classList.toggle('active');
            if (searchContainer.classList.contains('active')) {
                searchInput.focus();
            } else {
                searchInput.value = '';
                searchClear.classList.add('hidden');
                if (state.currentView === 'search') {
                    switchView('home');
                }
            }
        });

        searchInput.addEventListener('input', () => {
            const q = searchInput.value.trim();
            searchClear.classList.toggle('hidden', !q);

            clearTimeout(state.searchTimeout);
            if (q.length >= 2) {
                state.searchTimeout = setTimeout(() => performSearch(q), 400);
            } else if (!q) {
                if (state.currentView === 'search') switchView('home');
            }
        });

        searchInput.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                searchContainer.classList.remove('active');
                searchInput.value = '';
                searchClear.classList.add('hidden');
                if (state.currentView === 'search') switchView('home');
            }
        });

        searchClear.addEventListener('click', () => {
            searchInput.value = '';
            searchClear.classList.add('hidden');
            searchInput.focus();
            if (state.currentView === 'search') switchView('home');
        });
    }

    async function performSearch(query) {
        switchView('search');
        searchTitle.textContent = `Search: "${query}"`;
        searchGrid.innerHTML = '';
        searchEmpty.classList.add('hidden');
        searchLoading.classList.remove('hidden');

        try {
            const results = await api(`/api/search?q=${encodeURIComponent(query)}`);

            searchGrid.innerHTML = '';
            searchLoading.classList.add('hidden');

            if (!results.length) {
                searchEmpty.classList.remove('hidden');
                return;
            }

            for (const entry of results) {
                if (entry.type === 'movie' || entry.type === 'tv') {
                    searchGrid.appendChild(createMovieCard(entry));
                } else if (entry.isDirectory) {
                    searchGrid.appendChild(createFolderCard(entry, (e) => {
                        navigateToBrowse(e.url, e.name);
                    }));
                }
            }
        } catch (err) {
            searchLoading.classList.add('hidden');
            searchGrid.innerHTML = `<div class="empty-state"><p>Search error: ${err.message}</p></div>`;
        }
    }

    // ===== NAV EVENTS =====
    function initNav() {
        // Scroll effect
        window.addEventListener('scroll', () => {
            navbar.classList.toggle('scrolled', window.scrollY > 50);
        });

        // Nav links
        document.querySelectorAll('.nav-link').forEach(link => {
            link.addEventListener('click', (e) => {
                e.preventDefault();
                const section = link.dataset.section;

                if (section === 'home') {
                    switchView('home');
                    window.scrollTo({ top: 0, behavior: 'smooth' });
                } else if (section === 'movies') {
                    switchView('movies');
                    initMoviesPage();
                    window.scrollTo({ top: 0, behavior: 'smooth' });
                } else if (section === 'tv') {
                    switchView('tv');
                    initTvPage();
                    window.scrollTo({ top: 0, behavior: 'smooth' });
                }
            });
        });

        // Logo click → home
        document.querySelector('.logo').addEventListener('click', () => {
            switchView('home');
            window.scrollTo({ top: 0, behavior: 'smooth' });
        });

        // Browse back button
        browseBack.addEventListener('click', () => {
            if (state.browseHistory.length > 1) {
                state.browseHistory.pop();
                const prev = state.browseHistory[state.browseHistory.length - 1];
                loadBrowseView(prev.url, prev.title);
            } else {
                switchView('home');
            }
        });

        // Modal close
        modalClose.addEventListener('click', closeModal);
        movieModal.addEventListener('click', (e) => {
            if (e.target === movieModal) closeModal();
        });

        // Escape key
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                if (!movieModal.classList.contains('hidden')) {
                    closeModal();
                }
            }
        });
    }

    // ===== MOVIES PAGE (section-based + filtering) =====
    function initMoviesPage() {
        if (state.moviesLoaded) return;
        state.moviesLoaded = true;

        const movieCats = state.categories.filter(c => c.type === 'movies');
        moviesRows.innerHTML = '';

        // Build filter pills
        const pillsContainer = $('moviesFilterPills');
        pillsContainer.innerHTML = '';
        const allPill = createFilterPill('All', true);
        allPill.addEventListener('click', () => filterCategory('movies', null));
        pillsContainer.appendChild(allPill);
        for (const cat of movieCats) {
            const pill = createFilterPill(`${cat.icon} ${cat.name}`, false);
            pill.addEventListener('click', () => filterCategory('movies', cat.id));
            pillsContainer.appendChild(pill);
        }

        // Add TMDB Suggestions row first
        const suggestionsRow = createCategoryRow({ id: 'suggestions-movies', name: 'Trending Now', icon: '🔥' });
        suggestionsRow.dataset.catId = 'suggestions-movies';
        moviesRows.appendChild(suggestionsRow);
        loadTmdbSuggestions('movie', suggestionsRow);

        // Add each movie category as a row
        for (const cat of movieCats) {
            const row = createCategoryRow(cat);
            row.dataset.catId = cat.id;
            moviesRows.appendChild(row);
            loadCategoryRow(cat, row);
        }

        // Sorting
        $('moviesSortBy').addEventListener('change', () => applySorting('movies'));
        $('moviesQuality').addEventListener('change', () => applyQualityFilter('movies'));
    }

    // ===== TV SERIES PAGE (section-based + filtering) =====
    function initTvPage() {
        if (state.tvLoaded) return;
        state.tvLoaded = true;

        const tvCats = state.categories.filter(c => c.type === 'tv');
        tvRows.innerHTML = '';

        // Build filter pills
        const pillsContainer = $('tvFilterPills');
        pillsContainer.innerHTML = '';
        const allPill = createFilterPill('All', true);
        allPill.addEventListener('click', () => filterCategory('tv', null));
        pillsContainer.appendChild(allPill);
        for (const cat of tvCats) {
            const pill = createFilterPill(`${cat.icon} ${cat.name}`, false);
            pill.addEventListener('click', () => filterCategory('tv', cat.id));
            pillsContainer.appendChild(pill);
        }

        // Add TMDB Suggestions row first
        const suggestionsRow = createCategoryRow({ id: 'suggestions-tv', name: 'Trending TV Shows', icon: '🔥' });
        suggestionsRow.dataset.catId = 'suggestions-tv';
        tvRows.appendChild(suggestionsRow);
        loadTmdbSuggestions('tv', suggestionsRow);

        // Add each TV category as a row
        for (const cat of tvCats) {
            const row = createCategoryRow(cat);
            row.dataset.catId = cat.id;
            tvRows.appendChild(row);
            loadCategoryRow(cat, row);
        }

        // Sorting
        $('tvSortBy').addEventListener('change', () => applySorting('tv'));
    }

    // ===== FILTER/SORT HELPERS =====
    function createFilterPill(label, active) {
        const pill = document.createElement('button');
        pill.className = 'filter-pill' + (active ? ' active' : '');
        pill.textContent = label;
        return pill;
    }

    function filterCategory(pageType, categoryId) {
        const container = pageType === 'movies' ? moviesRows : tvRows;
        const pillsContainer = $(pageType === 'movies' ? 'moviesFilterPills' : 'tvFilterPills');

        // Update active pill
        pillsContainer.querySelectorAll('.filter-pill').forEach((p, i) => {
            p.classList.toggle('active', categoryId === null ? i === 0 : p.textContent.includes(
                state.categories.find(c => c.id === categoryId)?.name || ''
            ));
        });

        // Show/hide rows
        container.querySelectorAll('.category-row').forEach(row => {
            if (categoryId === null) {
                row.style.display = '';
            } else {
                const isSuggestion = row.dataset.catId?.startsWith('suggestions');
                row.style.display = (row.dataset.catId === categoryId || isSuggestion) ? '' : 'none';
            }
        });

        // If filtering to specific category, scroll to it
        if (categoryId) {
            const targetRow = container.querySelector(`[data-cat-id="${categoryId}"]`);
            if (targetRow) {
                targetRow.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
        }
    }

    function applySorting(pageType) {
        const sortValue = $(pageType === 'movies' ? 'moviesSortBy' : 'tvSortBy').value;
        const container = pageType === 'movies' ? moviesRows : tvRows;

        container.querySelectorAll('.category-row').forEach(row => {
            if (!row._movieData || row.dataset.catId?.startsWith('suggestions')) return;

            const slider = row.querySelector('.row-slider');
            let sorted = [...row._movieData];

            switch (sortValue) {
                case 'name-asc':
                    sorted.sort((a, b) => (a.title || a.name).localeCompare(b.title || b.name));
                    break;
                case 'name-desc':
                    sorted.sort((a, b) => (b.title || b.name).localeCompare(a.title || a.name));
                    break;
                case 'year-desc':
                    sorted.sort((a, b) => (b.year || 0) - (a.year || 0));
                    break;
                case 'year-asc':
                    sorted.sort((a, b) => (a.year || 0) - (b.year || 0));
                    break;
                case 'rating-desc':
                    sorted.sort((a, b) => (b.rating || 0) - (a.rating || 0));
                    break;
                default:
                    break;
            }

            slider.innerHTML = '';
            for (const movie of sorted) {
                slider.appendChild(createMovieCard(movie));
            }
        });
    }

    function applyQualityFilter(pageType) {
        const qualityValue = $('moviesQuality').value;
        const container = pageType === 'movies' ? moviesRows : tvRows;

        container.querySelectorAll('.category-row').forEach(row => {
            if (!row._movieData || row.dataset.catId?.startsWith('suggestions')) return;

            const slider = row.querySelector('.row-slider');
            let filtered = row._movieData;

            if (qualityValue !== 'all') {
                filtered = filtered.filter(m => {
                    const name = (m.title || m.name || '').toLowerCase();
                    return name.includes(qualityValue.toLowerCase());
                });
            }

            slider.innerHTML = '';
            if (filtered.length === 0) {
                slider.innerHTML = '<div style="padding:20px;color:var(--color-text-muted);">No matches for this quality</div>';
                return;
            }
            for (const movie of filtered) {
                slider.appendChild(createMovieCard(movie));
            }
        });
    }

    // ===== TMDB SUGGESTIONS =====
    async function loadTmdbSuggestions(type, row) {
        const slider = row.querySelector('.row-slider');
        try {
            const data = await api(`/api/suggestions?type=${type}`);
            slider.innerHTML = '';

            if (!data.length) {
                slider.innerHTML = '<div style="padding:20px;color:var(--color-text-muted);">No suggestions available</div>';
                return;
            }

            for (const item of data) {
                const card = document.createElement('div');
                card.className = 'movie-card';
                card.dataset.title = item.title;
                card.dataset.year = item.year || '';
                card.dataset.type = type;

                card.innerHTML = `
                    <img class="card-poster" src="${item.poster || ''}" alt="${escapeHtml(item.title)}" loading="lazy"
                        onerror="this.src='data:image/svg+xml,${encodeURIComponent(posterPlaceholder(item.title))}'">
                    <div class="card-play-icon">
                        <svg viewBox="0 0 24 24" width="24" height="24"><path fill="white" d="M8 5v14l11-7z"/></svg>
                    </div>
                    <div class="card-overlay">
                        <div class="card-title">${escapeHtml(item.title)}</div>
                        <div class="card-meta">
                            ${item.year ? `<span class="card-year">${item.year}</span>` : ''}
                            ${item.rating ? `<span class="card-rating">★ ${item.rating.toFixed(1)}</span>` : ''}
                        </div>
                    </div>
                    <div class="suggestion-badge">TMDB</div>
                `;

                card.addEventListener('click', () => {
                    searchInput.value = item.title;
                    searchContainer.classList.add('active');
                    performSearch(item.title);
                });

                slider.appendChild(card);
            }
        } catch {
            slider.innerHTML = '<div style="padding:20px;color:var(--color-text-muted);">Could not load suggestions</div>';
        }
    }

    // ===== INIT =====
    async function init() {
        console.log('🎬 PotFlix Streamer initializing...');

        initNav();
        initSearch();

        // Load categories
        state.categories = await fetchCategories();

        // Load genres
        fetchGenres();

        // Build home view
        await initHome();

        console.log('✅ PotFlix ready!');
    }

    // Start!
    document.addEventListener('DOMContentLoaded', init);
})();
