import json
import sys
from difflib import SequenceMatcher

def similarity(a, b):
    return SequenceMatcher(None, a.lower(), b.lower()).ratio()

def verify(filepath):
    print(f"\n{'='*60}")
    print(f"VERIFYING: {filepath}")
    print(f"{'='*60}")
    
    with open(filepath, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    movies = data.get('movies', [])
    total = len(movies)
    
    no_match = []
    no_genres = []
    no_poster = []
    duplicate_tmdb = {}
    
    for m in movies:
        tmdb_id = m.get('tmdb_id')
        title = m.get('title', '')
        year = m.get('year', '')
        genres = m.get('genres', [])
        poster = m.get('poster_url', '')
        
        if tmdb_id is None:
            no_match.append(f"  - {title} ({year})")
        else:
            # Track duplicate TMDB IDs
            if tmdb_id in duplicate_tmdb:
                duplicate_tmdb[tmdb_id].append(f"{title} ({year})")
            else:
                duplicate_tmdb[tmdb_id] = [f"{title} ({year})"]
        
        if not genres:
            no_genres.append(f"  - {title} ({year})")
            
        if not poster:
            no_poster.append(f"  - {title} ({year})")
    
    # Filter only actual duplicates
    actual_dupes = {k: v for k, v in duplicate_tmdb.items() if len(v) > 1}
    
    matched = total - len(no_match)
    match_rate = (matched / total * 100) if total > 0 else 0
    
    print(f"\nTotal movies: {total}")
    print(f"TMDB matched: {matched} ({match_rate:.1f}%)")
    print(f"No TMDB match: {len(no_match)}")
    print(f"No genres: {len(no_genres)}")
    print(f"No poster: {len(no_poster)}")
    print(f"Duplicate TMDB IDs: {len(actual_dupes)}")
    
    if no_match:
        print(f"\n--- NO TMDB MATCH ({len(no_match)}) ---")
        for item in no_match[:20]:
            print(item)
        if len(no_match) > 20:
            print(f"  ... and {len(no_match) - 20} more")
    
    if actual_dupes:
        print(f"\n--- DUPLICATE TMDB IDs ({len(actual_dupes)}) ---")
        count = 0
        for tmdb_id, titles in actual_dupes.items():
            if count >= 15:
                print(f"  ... and {len(actual_dupes) - 15} more")
                break
            print(f"  TMDB #{tmdb_id}: {' | '.join(titles)}")
            count += 1

if __name__ == "__main__":
    import glob
    files = sys.argv[1:] if len(sys.argv) > 1 else glob.glob("*_enriched.json")
    for f in files:
        verify(f)
