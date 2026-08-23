import urllib.request
import re
import json
import os
import sys
from urllib.parse import urljoin, unquote

if len(sys.argv) > 1:
    TARGET = sys.argv[1]
else:
    TARGET = "http://172.16.50.7/DHAKA-FLIX-7/English%20Movies/"

if len(sys.argv) > 2:
    OUTPUT_FILE = sys.argv[2]
else:
    OUTPUT_FILE = "ftp_database.json"

visited = set()
total_folders_scanned = 0

def get_links(url):
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=10) as response:
            html = response.read().decode('utf-8')
            hrefs = re.findall(r'href="([^"]+)"', html)
            links = []
            for h in hrefs:
                if h.startswith('?') or h.startswith('#') or h == '/' or h == '../' or h == '..' or "larsjung" in h or "browsehappy" in h or "_h5ai" in h or "googleapis" in h:
                    continue
                full_url = urljoin(url, h)
                links.append((unquote(h).strip(), full_url, h.endswith('/')))
            
            seen = set()
            unique_links = []
            for name, f_url, is_dir in links:
                if f_url not in seen:
                    seen.add(f_url)
                    unique_links.append((name, f_url, is_dir))
            return unique_links
    except Exception as e:
        print(f"Error fetching {url}: {e}")
        return []

def get_basename(path):
    parts = [p for p in path.split('/') if p]
    return parts[-1] if parts else path

def crawl_tree(url, folder_name="Root"):
    global total_folders_scanned
    if url in visited:
        return None
    visited.add(url)
    total_folders_scanned += 1
    
    links = get_links(url)
    
    node = {
        "folder_name": get_basename(folder_name),
        "url": url,
        "type": "directory",
        "subfolders": [],
        "media": {
            "videos": [],
            "images": [],
            "others": []
        }
    }
    
    subfolders_to_crawl = []
    
    for name, full_url, is_dir in links:
        if is_dir:
            subfolders_to_crawl.append((name, full_url))
        else:
            file_obj = {"file_name": get_basename(name), "url": full_url}
            ext = name.split('.')[-1].lower() if '.' in name else ""
            if ext in ["mp4", "mkv", "avi", "webm", "ts", "m4v"]:
                node["media"]["videos"].append(file_obj)
            elif ext in ["jpg", "jpeg", "png", "webp", "gif"]:
                node["media"]["images"].append(file_obj)
            else:
                node["media"]["others"].append(file_obj)
    
    total_files = len(node["media"]["videos"]) + len(node["media"]["images"]) + len(node["media"]["others"])
    
    try:
        print(f"Scanned: {node['folder_name']} (Found {total_files} files, {len(subfolders_to_crawl)} subfolders)")
    except UnicodeEncodeError:
        print(f"Scanned: [Unicode Folder Name] (Found {total_files} files, {len(subfolders_to_crawl)} subfolders)")
            
    # Recursively crawl subfolders and append them to this node's tree
    for sub_name, sub_url in subfolders_to_crawl:
        child_node = crawl_tree(sub_url, sub_name)
        if child_node is not None:
            node["subfolders"].append(child_node)
            
    return node

print(f"Starting TREE crawler for {TARGET} ...")
root_tree = crawl_tree(TARGET)

output_data = {
    "target_url": TARGET,
    "total_folders_scanned": total_folders_scanned,
    "tree": root_tree
}

with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
    json.dump(output_data, f, indent=2, ensure_ascii=False)

print(f"\nDone! Scanned {total_folders_scanned} total folders.")
print(f"Saved complete hierarchical tree to {OUTPUT_FILE}")
