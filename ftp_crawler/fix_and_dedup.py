import json
import re
import urllib.request
import urllib.parse
from concurrent.futures import ThreadPoolExecutor, as_completed
import threading
import glob
import sys

API_KEY = "cdb4d6683a4de1f186e7da86dccdd7f1"
BASE_URL = "https://api.themoviedb.org/3"
genre_map = {}

def load_genres():
    global genre_map
    url = f"{BASE_URL}/genre/movie/list?api_key={API_KEY}&language=en-US"
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=10) as response:
            data = json.loads(response.read().decode('utf-8'))
            for g in data.get('genres', []):
                genre_map[g['id']] = g['name']
    except Exception as e:
        print(f"Failed to load genres: {e}")

def aggressive_clean(title):
    """Strip numbered prefixes like '01 - ', '04-James Bond - ', etc."""
    # Strip "01 - " or "01-" prefix
    title = re.sub(r'^\d{1,3}\s*[-–]\s*', '', title)
    # Strip "01. " prefix
    title = re.sub(r'^\d{1,3}\.\s*', '', title)
    # Strip "James Bond - " prefix if still present after number removal
    # e.g. "James Bond - Thunderball" -> "Thunderball"  (only if it didn't match before)
    return title.strip()

def search_tmdb(title, year):
    query = urllib.parse.quote(title)
    url = f"{BASE_URL}/search/movie?api_key={API_KEY}&query={query}"
    if year:
        url += f"&primary_release_year={year}"
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=10) as response:
            data = json.loads(response.read().decode('utf-8'))
            results = data.get('results', [])
            if results:
                best = results[0]
                genres = [genre_map.get(gid, str(gid)) for gid in best.get('genre_ids', []) if gid in genre_map]
                poster = ""
                if best.get('poster_path'):
                    poster = f"https://image.tmdb.org/t/p/w500{best['poster_path']}"
                return {
                    "tmdb_id": best['id'],
                    "genres": genres,
                    "tmdb_poster": poster,
                    "overview": best.get('overview', '')
                }
    except:
        pass
    return None

def retry_unmatched(movie):
    """Try harder to match movies that failed the first time."""
    title = movie['title']
    year = movie['year']
    
    # Strategy 1: Aggressively clean the title
    cleaned = aggressive_clean(title)
    if cleaned != title:
        result = search_tmdb(cleaned, year)
        if result:
            return result, cleaned
    
    # Strategy 2: Try without year (some years are wrong)
    result = search_tmdb(cleaned, "")
    if result:
        return result, cleaned
    
    # Strategy 3: Remove everything after a dash/colon
    # e.g. "Batman-Hush" -> "Batman"... no that's too aggressive
    # But "Lemony Snicket's A Series..." might help with partial
    
    return None, title

def fix_and_dedup(filepath):
    print(f"\n{'='*60}")
    print(f"FIXING: {filepath}")
    print(f"{'='*60}")
    
    with open(filepath, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    movies = data.get('movies', [])
    
    # ---- PHASE 1: Retry unmatched entries ----
    unmatched = [m for m in movies if m.get('tmdb_id') is None]
    print(f"\nPhase 1: Retrying {len(unmatched)} unmatched movies with aggressive cleaning...")
    
    recovered = 0
    lock = threading.Lock()
    
    def process_retry(movie):
        nonlocal recovered
        result, cleaned_title = retry_unmatched(movie)
        if result:
            movie['tmdb_id'] = result['tmdb_id']
            movie['genres'] = result['genres']
            movie['overview'] = result['overview']
            if result['tmdb_poster']:
                movie['poster_url'] = result['tmdb_poster']
            with lock:
                nonlocal recovered
                recovered += 1
        return movie
    
    with ThreadPoolExecutor(max_workers=20) as executor:
        futures = {executor.submit(process_retry, m): m for m in unmatched}
        done = 0
        for future in as_completed(futures):
            future.result()
            done += 1
            if done % 50 == 0:
                print(f"  Retry progress: {done}/{len(unmatched)}")
    
    print(f"  Recovered {recovered} previously unmatched movies!")
    
    still_unmatched = sum(1 for m in movies if m.get('tmdb_id') is None)
    print(f"  Still unmatched: {still_unmatched}")
    
    # ---- PHASE 2: Deduplicate by TMDB ID ----
    print(f"\nPhase 2: Deduplicating by TMDB ID...")
    
    seen_tmdb = {}
    deduped = []
    merged_count = 0
    
    for m in movies:
        tmdb_id = m.get('tmdb_id')
        
        if tmdb_id is None:
            # Keep unmatched movies as-is
            deduped.append(m)
            continue
            
        if tmdb_id in seen_tmdb:
            # Merge videos into existing entry
            existing = seen_tmdb[tmdb_id]
            existing_urls = {v['url'] for v in existing['videos']}
            for v in m['videos']:
                if v['url'] not in existing_urls:
                    existing['videos'].append(v)
                    existing_urls.add(v['url'])
            merged_count += 1
        else:
            seen_tmdb[tmdb_id] = m
            deduped.append(m)
    
    print(f"  Merged {merged_count} duplicate entries.")
    print(f"  Final count: {len(deduped)} unique movies (was {len(movies)})")
    
    deduped.sort(key=lambda x: x['title'])
    data['movies'] = deduped
    data['movie_count'] = len(deduped)
    
    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    
    print(f"  Saved fixed file to {filepath}")

if __name__ == "__main__":
    load_genres()
    files = sys.argv[1:] if len(sys.argv) > 1 else glob.glob("*_enriched.json")
    for f in files:
        fix_and_dedup(f)
    print("\nAll done!")
