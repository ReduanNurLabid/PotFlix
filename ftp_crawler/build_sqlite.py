import sqlite3
import json
import glob
import sys

def create_schema(cursor):
    # Movies table
    cursor.execute('''
    CREATE TABLE IF NOT EXISTS movies (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        title TEXT NOT NULL,
        year TEXT,
        poster_url TEXT,
        category TEXT,
        region TEXT,
        is_imdb_top_250 INTEGER NOT NULL DEFAULT 0,
        tmdb_id INTEGER,
        overview TEXT,
        rating REAL
    )
    ''')
    
    # Genres table
    cursor.execute('''
    CREATE TABLE IF NOT EXISTS genres (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        name TEXT UNIQUE NOT NULL
    )
    ''')
    
    # Movie Genres junction table
    cursor.execute('''
    CREATE TABLE IF NOT EXISTS movie_genres (
        movie_id INTEGER NOT NULL,
        genre_id INTEGER NOT NULL,
        FOREIGN KEY (movie_id) REFERENCES movies (id),
        FOREIGN KEY (genre_id) REFERENCES genres (id),
        PRIMARY KEY (movie_id, genre_id)
    )
    ''')
    
    # Videos table
    cursor.execute('''
    CREATE TABLE IF NOT EXISTS videos (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        movie_id INTEGER NOT NULL,
        quality TEXT,
        url TEXT,
        file_name TEXT,
        FOREIGN KEY (movie_id) REFERENCES movies (id)
    )
    ''')
    
    # Indexes for fast querying
    cursor.execute('CREATE INDEX IF NOT EXISTS idx_movies_title ON movies(title)')
    cursor.execute('CREATE INDEX IF NOT EXISTS idx_movies_category ON movies(category)')
    cursor.execute('CREATE INDEX IF NOT EXISTS idx_movies_region ON movies(region)')
    cursor.execute('CREATE INDEX IF NOT EXISTS idx_movie_genres_movie_id ON movie_genres(movie_id)')
    cursor.execute('CREATE INDEX IF NOT EXISTS idx_movie_genres_genre_id ON movie_genres(genre_id)')

def build_db(db_path):
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    create_schema(cursor)
    
    # Track genres globally to map IDs
    genre_cache = {}
    
    def get_genre_id(name):
        if name in genre_cache:
            return genre_cache[name]
        cursor.execute("INSERT OR IGNORE INTO genres (name) VALUES (?)", (name,))
        cursor.execute("SELECT id FROM genres WHERE name = ?", (name,))
        gid = cursor.fetchone()[0]
        genre_cache[name] = gid
        return gid
        
    total_movies = 0
    total_videos = 0
    
    # Load all enriched JSON files
    files = glob.glob("*_enriched.json")
    for f in files:
        print(f"Loading {f} into database...")
        with open(f, 'r', encoding='utf-8') as file:
            data = json.load(file)
            
        for movie in data.get('movies', []):
            # Insert Movie
            cursor.execute('''
            INSERT INTO movies (title, year, poster_url, category, region, is_imdb_top_250, tmdb_id, overview, rating)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ''', (
                movie.get('title'),
                movie.get('year'),
                movie.get('poster_url'),
                movie.get('category'),
                movie.get('region'),
                1 if movie.get('is_imdb_top_250', False) else 0,
                movie.get('tmdb_id'),
                movie.get('overview'),
                movie.get('rating')  # This might not be in all JSONs but None is fine
            ))
            
            movie_id = cursor.lastrowid
            total_movies += 1
            
            # Insert Genres
            for genre_name in movie.get('genres', []):
                gid = get_genre_id(genre_name)
                cursor.execute("INSERT OR IGNORE INTO movie_genres (movie_id, genre_id) VALUES (?, ?)", (movie_id, gid))
                
            # Insert Videos
            for video in movie.get('videos', []):
                cursor.execute('''
                INSERT INTO videos (movie_id, quality, url, file_name)
                VALUES (?, ?, ?, ?)
                ''', (
                    movie_id,
                    video.get('quality'),
                    video.get('url'),
                    video.get('file_name')
                ))
                total_videos += 1
                
    conn.commit()
    conn.close()
    print(f"\nSuccessfully built {db_path}!")
    print(f"Inserted {total_movies} movies and {total_videos} video streams across {len(genre_cache)} distinct genres.")

if __name__ == "__main__":
    db_name = "movies.db"
    build_db(db_name)
