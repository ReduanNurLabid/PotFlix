import json
import re
import urllib.request
import urllib.parse
from concurrent.futures import ThreadPoolExecutor, as_completed
import threading
import os

API_KEY = "cdb4d6683a4de1f186e7da86dccdd7f1"
BASE_URL = "https://api.themoviedb.org/3"
genre_map = {}

def load_genres():
    global genre_map
    url = f"{BASE_URL}/genre/tv/list?api_key={API_KEY}&language=en-US"
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=10) as response:
            data = json.loads(response.read().decode('utf-8'))
            for g in data.get('genres', []):
                genre_map[g['id']] = g['name']
    except Exception as e:
        print(f"Failed to load genres: {e}")

def search_tmdb_tv(title, year):
    query = urllib.parse.quote(title)
    url = f"{BASE_URL}/search/tv?api_key={API_KEY}&query={query}"
    if year:
        url += f"&first_air_date_year={year}"
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
    
    if year:
        url = f"{BASE_URL}/search/tv?api_key={API_KEY}&query={query}"
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

def process_file(input_file, output_file, category):
    if not os.path.exists(input_file):
        print(f"Skipping {input_file} (does not exist)")
        return
        
    print(f"Processing {input_file}...")
    with open(input_file, 'r', encoding='utf-8') as f:
        data = json.load(f)
        
    movies = []
    
    def find_series_folders(node):
        series_nodes = []
        for child in node.get('subfolders', []):
            if re.search(r'\((?:TV.*?|19\d{2}|20\d{2}).*\)', child['folder_name']):
                series_nodes.append(child)
            else:
                series_nodes.extend(find_series_folders(child))
        return series_nodes

    series_folders = find_series_folders(data['tree'])
    
    for folder in series_folders:
        folder_name = folder['folder_name']
        url = folder['url']
        
        match = re.match(r'^(.*?)\s*\((.*?)(?:19|20)\d{2}.*\)', folder_name)
        if match:
            title = match.group(1).strip()
        else:
            match2 = re.match(r'^(.*?)\s*(?:19|20)\d{2}', folder_name)
            if match2:
                title = match2.group(1).strip()
            else:
                title = folder_name.split('(')[0].strip()
                
        year_match = re.search(r'((?:19|20)\d{2})', folder_name)
        year = year_match.group(1) if year_match else ""
        
        movie = {
            "title": title,
            "year": year,
            "category": category,
            "region": "English",
            "poster_url": "",
            "tmdb_id": None,
            "is_imdb_top_250": False,
            "overview": "",
            "genres": [category],
            "videos": [
                {
                    "quality": "Folder",
                    "url": url,
                    "file_name": folder_name
                }
            ]
        }
        movies.append(movie)

    print("Enriching with TMDB...")
    lock = threading.Lock()
    done = 0
    
    def process_enrich(m):
        res = search_tmdb_tv(m['title'], m['year'])
        if res:
            m['tmdb_id'] = res['tmdb_id']
            if res['genres']:
                m['genres'].extend(res['genres'])
                m['genres'] = list(set(m['genres']))
            m['poster_url'] = res['tmdb_poster']
            m['overview'] = res['overview']
        
        with lock:
            nonlocal done
            done += 1
            if done % 100 == 0:
                print(f"  Progress: {done}/{len(movies)}")
                
    with ThreadPoolExecutor(max_workers=10) as executor:
        for m in movies:
            executor.submit(process_enrich, m)
            
    out_data = {
        "movies": movies,
        "movie_count": len(movies)
    }
    
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(out_data, f, indent=2, ensure_ascii=False)
    
    print(f"Saved {len(movies)} TV Series to {output_file}")

if __name__ == "__main__":
    load_genres()
    process_file("tv_web_series.json", "tv_series_merged_enriched.json", "TV Series")
    process_file("korean_tv_web_series.json", "korean_tv_merged_enriched.json", "Korean TV Series")
