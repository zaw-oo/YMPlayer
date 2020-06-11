package com.yash.ymplayer.storage;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(indices = {@Index(value = {"mediaId","name","artist","album","playlist"},unique = true)})
public class MediaItem {
    @PrimaryKey(autoGenerate = true)
    int id;

    String mediaId;
    String name;
    String artist;
    String album;
    @ForeignKey(entity = PlayList.class,parentColumns = "playlist",childColumns = "playlist")
    String playlist;


    public MediaItem(String mediaId, String name, String artist, String album, String playlist) {
        this.mediaId = mediaId;
        this.name = name;
        this.artist = artist;
        this.album = album;
        this.playlist = playlist;
    }

    public String getMediaId() {
        return mediaId;
    }

    public String getName() {
        return name;
    }

    public String getArtist() {
        return artist;
    }

    public String getAlbum() {
        return album;
    }

    public String getPlaylist() {
        return playlist;
    }
}
