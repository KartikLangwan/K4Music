"""Ports the Android DatabaseAccessor DAO to the desktop (Room on JVM: blocking DAO calls must be suspend)."""
import re, sys

src, dst = sys.argv[1], sys.argv[2]
text = open(src, encoding='utf-8').read().replace('\r\n', '\n')
start = text.index('@Dao\n@Suppress("TooManyFunctions")\ninterface DatabaseAccessor {')
end = text.index('@androidx.room.Database(')
body = text[start:end]

# Drop Android-only members (MediaItem / SupportSQLite based); desktop versions are written by hand.
def drop(body, signature_start):
    i = body.index(signature_start)
    # walk back over annotations / doc comment directly above
    lines_before = body[:i].split('\n')
    # the signature line itself starts in lines_before[-1] (indentation); keep only what precedes it
    indent = lines_before.pop()
    while lines_before and lines_before[-1].strip().startswith(('@', '*', '/**')):
        lines_before.pop()
    head = '\n'.join(lines_before) + '\n'
    rest = indent + body[i:]
    # function end: if it has a block body, match braces from the body's own '{'
    # (default arguments such as `= { it }` contain braces too); else end of line
    line_end = rest.index('\n')
    first_line = rest[:line_end]
    if first_line.rstrip().endswith('{'):
        depth, j = 0, first_line.rstrip().rindex('{')
        while True:
            c = rest[j]
            if c == '{': depth += 1
            elif c == '}':
                depth -= 1
                if depth == 0: break
            j += 1
        rest = rest[j + 1:]
    else:
        rest = rest[line_end:]
    return head + rest

for sig in [
    'suspend fun filterBlacklistedSongs(',
    'fun createPlaylist(playlist: Playlist, mediaItems',
    'fun addToPlaylist(playlist: Playlist, mediaItems',
    'fun insert(mediaItem: MediaItem',
    'fun raw(supportSQLiteQuery',
    'fun checkpoint()',
    'fun removeFromPlaylist(',  # suspend default methods live in LibraryDao (an abstract class)
]:
    body = drop(body, sig)

# Annotated members without a Flow result become suspend.
out = []
lines = body.split('\n')
pending_annotation = False
for line in lines:
    s = line.strip()
    if s.startswith('@'):
        pending_annotation = True
    m = re.match(r'^(\s+)fun (\w+)\((.*)', line)
    if m and pending_annotation:
        signature = line
        if 'Flow<' not in signature and not signature.rstrip().endswith('= when (sortBy) {'):
            line = line.replace('fun ', 'suspend fun ', 1)
        pending_annotation = False
    elif m:
        pending_annotation = False
    out.append(line)
body = '\n'.join(out)
# Signatures spanning several lines: a Flow result is never suspend.
body = re.sub(r'suspend fun (\w+\((?:[^()]|\([^()]*\))*\)\s*:\s*Flow<)', r'fun \1', body)
body = re.sub(r'[ \t]+\n', '\n', body)
body = re.sub(r'\n{3,}', '\n\n', body)
body = body.replace('interface DatabaseAccessor {', 'interface K4Dao {')
body = body.replace('    @Throws(SQLException::class)\n', '')  # android.database.SQLException
body = body.replace("'$LOCAL_KEY_PREFIX%'", "'local:%'")

header = '''// GENERATED from app/src/main/kotlin/com/k4music/android/Database.kt by windows/tools/port_dao.py,
// then extended in K4Database.kt (LibraryDao). Same queries as Android; blocking calls are suspend here.
@file:Suppress("all")

package com.k4music.desktop.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.k4music.android.models.*
import com.k4music.core.data.enums.AlbumSortBy
import com.k4music.core.data.enums.ArtistSortBy
import com.k4music.core.data.enums.PlaylistSortBy
import com.k4music.core.data.enums.SongSortBy
import com.k4music.core.data.enums.SortOrder
import kotlinx.coroutines.flow.Flow

'''
open(dst, 'w', encoding='utf-8', newline='').write(header + body.rstrip() + '\n')
print('ok', body.count('suspend fun'), 'suspend functions')
