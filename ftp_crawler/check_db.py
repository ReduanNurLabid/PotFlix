import sqlite3

conn = sqlite3.connect('movies.db')
c = conn.cursor()

print("=== TABLES ===")
c.execute("SELECT name FROM sqlite_master WHERE type='table'")
print([r[0] for r in c.fetchall()])

print("\n=== GENRE LIST ===")
c.execute("SELECT * FROM genres")
for r in c.fetchall():
    print(f"  {r[0]}. {r[1]}")

print("\n=== SAMPLE: Inception ===")
c.execute("SELECT * FROM movies WHERE title LIKE '%Inception%'")
row = c.fetchone()
print(f"  ID={row[0]}, Title={row[1]}, Year={row[2]}, Category={row[4]}, TMDB={row[7]}")

print("\n=== Its Genres ===")
c.execute("SELECT g.name FROM movie_genres mg JOIN genres g ON mg.genre_id=g.id WHERE mg.movie_id=?", (row[0],))
print(f"  {[r[0] for r in c.fetchall()]}")

print("\n=== Its Videos ===")
c.execute("SELECT quality, url FROM videos WHERE movie_id=?", (row[0],))
for r in c.fetchall():
    print(f"  [{r[0]}] {r[1][:100]}...")

print("\n=== MOVIES BY CATEGORY ===")
c.execute("SELECT category, COUNT(*) FROM movies GROUP BY category ORDER BY COUNT(*) DESC")
for r in c.fetchall():
    print(f"  {r[0]}: {r[1]}")

print("\n=== IMDB TOP 250 COUNT ===")
c.execute("SELECT COUNT(*) FROM movies WHERE is_imdb_top_250=1")
print(f"  {c.fetchone()[0]} movies tagged as IMDB Top 250")

print("\n=== TOP GENRES ===")
c.execute("SELECT g.name, COUNT(*) as cnt FROM movie_genres mg JOIN genres g ON mg.genre_id=g.id GROUP BY g.name ORDER BY cnt DESC")
for r in c.fetchall():
    print(f"  {r[0]}: {r[1]}")

conn.close()
