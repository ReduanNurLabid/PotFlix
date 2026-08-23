import json
import re
import sys

def clean_movie_name(raw_name):
    # Extract up to the (Year)
    match = re.search(r'^(.*? \(\d{4}\))', raw_name)
    if match:
        name = match.group(1)
        # Remove prefix like "001. "
        name = re.sub(r'^\d{3}\.\s*', '', name)
        return name.strip()
    
    # Fallback
    name = raw_name
    tags_to_remove = [r'1080p', r'720p', r'480p', r'\[.*?\]', r'BluRay', r'WEBRip', r'HDCAM', r'HDTC', r'HDTS', r'AMZN', r'NF', r'REM']
    for tag in tags_to_remove:
        name = re.sub(tag, '', name, flags=re.IGNORECASE)
    
    name = re.sub(r'^\d{3}\.\s*', '', name)
    return name.strip(' -').strip()

def extract_movies_from_node(node, source_file):
    movies = []
    
    # Is this a movie node? (has videos)
    if len(node.get("media", {}).get("videos", [])) > 0:
        raw_name = node["folder_name"]
        clean_name = clean_movie_name(raw_name)
        
        year_match = re.search(r'\((\d{4})\)', clean_name)
        year = year_match.group(1) if year_match else ""
        
        title = re.sub(r'\(\d{4}\)', '', clean_name).strip()
        
        poster_url = ""
        if len(node["media"].get("images", [])) > 0:
            poster_url = node["media"]["images"][0]["url"]
            
        videos = []
        for v in node["media"]["videos"]:
            q = "720p" # Default
            v_name_lower = v["file_name"].lower()
            if "1080p" in v_name_lower or "1080" in v_name_lower:
                q = "1080p"
            elif "720p" in v_name_lower or "720" in v_name_lower:
                q = "720p"
            elif "480p" in v_name_lower or "480" in v_name_lower:
                q = "480p"
            elif "2160p" in v_name_lower or "4k" in v_name_lower:
                q = "4K"
            elif "1080p" in source_file.lower():
                q = "1080p"
                
            videos.append({
                "quality": q,
                "url": v["url"],
                "file_name": v["file_name"]
            })
            
        movies.append({
            "title": title,
            "year": year,
            "poster_url": poster_url,
            "videos": videos
        })
        
    # Recurse
    for child in node.get("subfolders", []):
        movies.extend(extract_movies_from_node(child, source_file))
        
    return movies

def merge_files(input_files, output_file):
    all_extracted_movies = []
    
    for filepath in input_files:
        print(f"Reading {filepath}...")
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                data = json.load(f)
            if "tree" in data:
                all_extracted_movies.extend(extract_movies_from_node(data["tree"], filepath))
        except Exception as e:
            print(f"Error reading {filepath}: {e}")
            
    print(f"Extracted {len(all_extracted_movies)} total raw movie entries.")
    
    merged_dict = {}
    
    for m in all_extracted_movies:
        key = f"{m['title'].lower()}_{m['year']}"
        
        if key not in merged_dict:
            merged_dict[key] = m
        else:
            existing = merged_dict[key]
            
            existing_urls = {v['url'] for v in existing['videos']}
            for v in m['videos']:
                if v['url'] not in existing_urls:
                    existing['videos'].append(v)
                    existing_urls.add(v['url'])
                    
            if not existing['poster_url'] and m['poster_url']:
                existing['poster_url'] = m['poster_url']
                
    final_movies = list(merged_dict.values())
    final_movies.sort(key=lambda x: x['title'])
    
    print(f"Merged down to {len(final_movies)} unique movies.")
    
    output_data = {
        "movie_count": len(final_movies),
        "movies": final_movies
    }
    
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(output_data, f, indent=2, ensure_ascii=False)
        
    print(f"Saved optimized database to {output_file}\n")

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python merge_movies.py <output_file> <input1> <input2> ...")
        sys.exit(1)
        
    out_file = sys.argv[1]
    in_files = sys.argv[2:]
    merge_files(in_files, out_file)
