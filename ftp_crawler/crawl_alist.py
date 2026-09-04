import urllib.request
import urllib.parse
import json
import time
import os
import re
import sys
from typing import List, Dict

if sys.stdout.encoding.lower() != 'utf-8':
    sys.stdout.reconfigure(encoding='utf-8')

TMDB_API_KEY = "cdb4d6683a4de1f186e7da86dccdd7f1"
ALIST_URL = "https://cdn.nagordola.com.bd/api/fs/list"
BASE_URL = "https://cdn.nagordola.com.bd"

def fetch_dir(path: str) -> List[Dict]:
    req = urllib.request.Request(ALIST_URL, method='POST')
    req.add_header('Content-Type', 'application/json')
    
    clean_path = path[2:] if path.startswith("/d/") else path
    
    entries = []
    page = 1
    has_more = True
    
    while has_more and page <= 10:
        data = json.dumps({'path': clean_path, 'password': '', 'page': page, 'per_page': 1000}).encode('utf-8')
        try:
            with urllib.request.urlopen(req, data=data, timeout=10) as response:
                res = json.loads(response.read().decode())
                
                if 'data' in res and res['data'] and 'content' in res['data']:
                    content = res['data']['content']
                    if content is None or len(content) == 0:
                        has_more = False
                    else:
                        for item in content:
                            name = item['name']
                            is_dir = item['is_dir']
                            
                            # Construct full URL safely
                            parts = [urllib.parse.quote(p) for p in clean_path.strip('/').split('/') if p]
                            encoded_path = '/' + '/'.join(parts) if parts else ''
                            encoded_name = urllib.parse.quote(name)
                            item_url = f"{BASE_URL}{encoded_path}/{encoded_name}"
                            
                            entries.append({
                                'name': name,
                                'is_dir': is_dir,
                                'url': item_url,
                                'size': item.get('size', 0)
                            })
                        page += 1
                else:
                    has_more = False
        except Exception as e:
            print(f'Error fetching {path}: {e}')
            has_more = False
            
    return entries

def search_tmdb(title: str, year: str, is_tv: bool) -> Dict:
    search_type = 'tv' if is_tv else 'movie'
    query = urllib.parse.quote(title)
    url = f"https://api.themoviedb.org/3/search/{search_type}?api_key={TMDB_API_KEY}&query={query}"
    if year and not is_tv:
        url += f"&year={year}"
        
    try:
        with urllib.request.urlopen(url, timeout=5) as response:
            res = json.loads(response.read().decode())
            results = res.get('results', [])
            if results:
                return results[0]
    except Exception as e:
        print(f"TMDB Error for {title}: {e}")
        
    return None

def process_category(category_name: str, base_path: str, is_tv: bool):
    print(f"\nProcessing category: {category_name} ({base_path})")
    
    root_entries = fetch_dir(base_path)
    
    movies_to_process = [e for e in root_entries if e['is_dir']]
    if not movies_to_process:
        movies_to_process = [e for e in root_entries if not e['is_dir'] and e['name'].lower().endswith(('.mp4', '.mkv', '.avi'))]
            
    results = []
    print(f"Found {len(movies_to_process)} items to process in {category_name}")
    
    for mf in movies_to_process:
        name = mf['name']
        is_dir = mf['is_dir']
        
        movie_match = re.search(r'^(.+?)\s*\((19|20)\d{2}\)', name)
        if movie_match:
            title = movie_match.group(1).strip()
            year = movie_match.group(2)
        else:
            tv_match = re.search(r'^(.+?)\s*\((TV(?:\s+Mini)?\s+Series\s+\d{4}.*?)\)', name)
            if tv_match:
                title = tv_match.group(1).strip()
                year = ""
            else:
                title = name.replace('.mp4', '').replace('.mkv', '')
                year = ""
                
        print(f"    Fetching TMDB for: {title} ({year})")
        tmdb_data = search_tmdb(title, year, is_tv)
        
        poster_url = None
        tmdb_id = None
        overview = None
        rating = None
        
        if tmdb_data:
            tmdb_id = tmdb_data.get('id')
            poster_path = tmdb_data.get('poster_path')
            if poster_path:
                poster_url = f"https://image.tmdb.org/t/p/w342{poster_path}"
            overview = tmdb_data.get('overview')
            rating = tmdb_data.get('vote_average')
            
        videos = []
        if is_dir:
            contents = fetch_dir(f"{base_path}/{name}")
            video_files = [c for c in contents if not c['is_dir'] and c['name'].lower().endswith(('.mp4', '.mkv', '.avi', '.webm'))]
            for v in video_files:
                videos.append({
                    'name': v['name'],
                    'url': v['url'],
                    'quality': '1080p' if '1080p' in v['name'].lower() else '720p'
                })
        else:
            videos.append({
                'name': mf['name'],
                'url': mf['url'],
                'quality': '1080p' if '1080p' in mf['name'].lower() else '720p'
            })
            
        if is_tv and is_dir:
            videos = [{
                'name': name,
                'url': mf['url'],
                'quality': 'Tv & Web Series'
            }]
            
        results.append({
            'title': title,
            'year': year,
            'category': category_name,
            'posterUrl': poster_url,
            'tmdbId': tmdb_id,
            'overview': overview,
            'rating': rating,
            'isTvSeries': is_tv,
            'videos': videos
        })
        
        time.sleep(0.1)
        
    output_filename = f"{category_name.replace(' ', '_').replace('-', '_').lower()}.json"
    with open(output_filename, 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"Saved {len(results)} items to {output_filename}")

def main():
    bases = [
        ("Movies", "/movies"),
        ("TV Series", "/tv-series"),
        ("Anime", "/anime")
    ]
    
    for parent_type, base_url in bases:
        print(f"--- Discovering {parent_type} ---")
        entries = fetch_dir(base_url)
        folders = [e for e in entries if e['is_dir']]
        
        for f in folders:
            is_tv = parent_type == "TV Series" or "tvshows" in f['name'].lower()
            process_category(f['name'], f"{base_url}/{f['name']}", is_tv)
    
if __name__ == "__main__":
    main()
