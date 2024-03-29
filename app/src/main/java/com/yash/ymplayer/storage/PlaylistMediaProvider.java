package com.yash.ymplayer.storage;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.yash.ymplayer.storage.dao.MediaItemDao;

@Database(entities = {PlayList.class,MediaItem.class},version = 4,exportSchema = false)
public abstract class PlaylistMediaProvider extends RoomDatabase {
    public abstract MediaItemDao getMediaItemDao();
}
