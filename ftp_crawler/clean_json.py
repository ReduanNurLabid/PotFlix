import json
import os
import glob
from urllib.parse import unquote

def get_basename(path):
    # Remove trailing slashes and split by '/'
    parts = [p for p in path.split('/') if p]
    return parts[-1] if parts else path

def clean_node(node):
    # Fix the folder name to be just the actual folder name, not the full path
    node["folder_name"] = get_basename(node["folder_name"])
    
    # Clean media arrays
    for media_type in ["videos", "images", "others"]:
        new_list = []
        for file_obj in node["media"][media_type]:
            # Skip favicons, h5ai, googleapis, and parent dir
            if "_h5ai" in file_obj["url"] or "favicon" in file_obj["url"] or "googleapis" in file_obj["url"]:
                continue
            
            basename = get_basename(file_obj["file_name"])
            if basename == ".." or basename == ".":
                continue
                
            file_obj["file_name"] = basename
            new_list.append(file_obj)
            
        node["media"][media_type] = new_list
        
    if "subtitles" in node["media"]:
        del node["media"]["subtitles"]
        
    # Recursively clean subfolders
    for child in node["subfolders"]:
        clean_node(child)

def process_file(filepath):
    print(f"Cleaning {filepath} ...")
    with open(filepath, 'r', encoding='utf-8') as f:
        data = json.load(f)
        
    if "tree" in data:
        clean_node(data["tree"])
        
    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    print(f"Finished {filepath}")

# Find all json files in current directory
for file in glob.glob("*.json"):
    process_file(file)

print("All JSON files cleaned successfully!")
