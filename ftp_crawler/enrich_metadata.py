import json
import urllib.request
import urllib.parse
from concurrent.futures import ThreadPoolExecutor, as_completed
import time
import sys
import threading

API_KEY = "cdb4d6683a4de1f186e7da86dccdd7f1"
BASE_URL = "https://api.themoviedb.org/3"

# Global genre map
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
        print(f"Loaded {len(genre_map)} genres from TMDB.")
    except Exception as e:
        print(f"Failed to load genres: {e}")

def search_tmdb(title, year, category="Movie"):
    # Prepare query
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
                # Find the best result based on category
                best = None
                for res in results:
                    orig_lang = res.get('original_language', '')
                    if category == "Bollywood" and orig_lang == "hi":
                        best = res
                        break
                    elif category == "South Indian" and orig_lang in ["te", "ta", "ml", "kn"]:
                        best = res
                        break
                    elif category == "Tollywood" and orig_lang == "bn":
                        best = res
                        break
                    elif category in ["Hollywood", "Animation", "Foreign"] and (orig_lang == "en" or orig_lang != "hi"):
                        best = res
                        break
                
                if not best:
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
    except Exception as e:
        pass
    return None

def process_movie(movie, category, region, is_top_250):
    title = movie['title']
    year = movie['year']
    
    # Check if we already have it
    if "tmdb_id" in movie:
        return movie
        
    tmdb_data = search_tmdb(title, year, category)
    
    movie['category'] = category
    movie['region'] = region
    movie['is_imdb_top_250'] = is_top_250
    
    if tmdb_data:
        movie['tmdb_id'] = tmdb_data['tmdb_id']
        movie['genres'] = tmdb_data['genres']
        movie['overview'] = tmdb_data['overview']
        # Prioritize FTP poster if available, otherwise fallback to TMDB poster
        if not movie.get('poster_url') and tmdb_data['tmdb_poster']:
            movie['poster_url'] = tmdb_data['tmdb_poster']
    else:
        movie['tmdb_id'] = None
        movie['genres'] = []
        movie['overview'] = ""
        
    return movie

def enrich_file(filepath, output_filepath, category="Movie", region="Unknown", is_top_250=False):
    print(f"\nLoading {filepath}...")
    with open(filepath, 'r', encoding='utf-8') as f:
        data = json.load(f)
        
    movies = data.get('movies', [])
    if not movies:
        print("No 'movies' array found. Did you run merge_movies.py first?")
        return
        
    total = len(movies)
    print(f"Enriching {total} movies...")
    
    enriched = []
    completed = 0
    lock = threading.Lock()
    
    # 20 workers to stay within 40-50 req/sec rate limit of TMDB
    with ThreadPoolExecutor(max_workers=20) as executor:
        futures = {executor.submit(process_movie, m, category, region, is_top_250): m for m in movies}
        
        for future in as_completed(futures):
            enriched.append(future.result())
            with lock:
                completed += 1
                if completed % 100 == 0:
                    print(f"Progress: {completed}/{total} ({(completed/total)*100:.1f}%)")
                    
    # Maintain original sorting
    enriched.sort(key=lambda x: x['title'])
    data['movies'] = enriched
    
    print(f"Saving enriched data to {output_filepath}...")
    with open(output_filepath, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python enrich_metadata.py <input.json>")
        sys.exit(1)
        
    load_genres()
    
    input_file = sys.argv[1]
    
    # Determine tags based on filename
    cat = "Hollywood"
    reg = "English"
    top_250 = False
    
    if "animation" in input_file.lower():
        cat = "Animation"
    elif "kolkata" in input_file.lower():
        reg = "Bengali"
        cat = "Tollywood"
    elif "hindi" in input_file.lower():
        reg = "Hindi"
        cat = "Bollywood"
    elif "south" in input_file.lower():
        reg = "South Indian"
    elif "korean" in input_file.lower():
        reg = "Korean"
        cat = "K-Drama"
    elif "imdb" in input_file.lower():
        top_250 = True
        
    out_file = input_file.replace(".json", "_enriched.json")
    enrich_file(input_file, out_file, category=cat, region=reg, is_top_250=top_250)
    print("Done!")
