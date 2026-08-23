import sqlite3

def clean_db():
    print("Connecting to DB...")
    conn = sqlite3.connect('app/src/main/assets/databases/movies.db')
    cursor = conn.cursor()

    print("Counting initial rows...")
    cursor.execute("SELECT COUNT(*) FROM movies")
    print(f"Initial movies: {cursor.fetchone()[0]}")
    cursor.execute("SELECT COUNT(*) FROM videos")
    print(f"Initial videos: {cursor.fetchone()[0]}")

    print("Deleting duplicates in movies...")
    cursor.execute("""
        DELETE FROM movies 
        WHERE id NOT IN (
            SELECT MIN(id) 
            FROM movies 
            GROUP BY title, year, category
        )
    """)
    movies_deleted = cursor.rowcount
    print(f"Deleted {movies_deleted} duplicate movies.")

    print("Deleting orphaned videos...")
    cursor.execute("""
        DELETE FROM videos 
        WHERE movie_id NOT IN (
            SELECT id FROM movies
        )
    """)
    videos_deleted = cursor.rowcount
    print(f"Deleted {videos_deleted} orphaned videos.")

    print("Deleting orphaned cross refs...")
    cursor.execute("""
        DELETE FROM movie_genres 
        WHERE movie_id NOT IN (
            SELECT id FROM movies
        )
    """)
    print(f"Deleted {cursor.rowcount} orphaned cross refs.")

    conn.commit()

    print("Vacuuming DB...")
    cursor.execute("VACUUM")
    
    print("Counting final rows...")
    cursor.execute("SELECT COUNT(*) FROM movies")
    print(f"Final movies: {cursor.fetchone()[0]}")
    cursor.execute("SELECT COUNT(*) FROM videos")
    print(f"Final videos: {cursor.fetchone()[0]}")

    conn.close()

if __name__ == '__main__':
    clean_db()
