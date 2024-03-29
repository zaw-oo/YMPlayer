package com.yash.ymplayer.util;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.widget.Toast;
import com.yash.logging.LogHelper;
import com.yash.ymplayer.interfaces.Keys;
import com.yash.ymplayer.repository.Repository;
import com.yash.ymplayer.interfaces.AudioProvider;
import com.yash.ymplayer.interfaces.AlbumOrArtistContextMenuListener;

import java.util.List;

public class AlbumOrArtistContextMenuClickListener implements AlbumOrArtistContextMenuListener {
    private static final String TAG = "AlbumOrArtistContextMen";
    MediaControllerCompat mMediaController;
    Context context;

    public AlbumOrArtistContextMenuClickListener(Context context, MediaControllerCompat mMediaController) {
        this.mMediaController = mMediaController;
        this.context = context;
    }

    @Override
    public void play(MediaBrowserCompat.MediaItem item, ITEM_TYPE type) {
        try {
            if (item.getMediaId() == null)
                throw new AlbumOrArtistContextMenuClickException("getting null from play() method");

            mMediaController.getTransportControls().playFromMediaId(item.getMediaId(), null);
            LogHelper.d(TAG, "AlbumOrArtistContextMenuClickListener play: " + item.getMediaId());

        } catch (AlbumOrArtistContextMenuClickException e) {
            e.log();
        }

    }

    @Override
    public void queueNext(MediaBrowserCompat.MediaItem item, ITEM_TYPE type) {
        try {
            if (item.getMediaId() == null)
                throw new AlbumOrArtistContextMenuClickException("getting null from queueNext() method");

//            String mediaId = getFirstToken(type) + item.getMediaId();
            Bundle extras = new Bundle();
            extras.putString(Keys.MEDIA_ID, item.getMediaId());
            extras.putString(Keys.QUEUE_MODE, Keys.QueueMode.OFFLINE.name());
            extras.putInt(Keys.QUEUE_HINT, type == ITEM_TYPE.ARTISTS ? AudioProvider.QueueHint.ARTIST_SONGS : AudioProvider.QueueHint.ALBUM_SONGS);
            mMediaController.getTransportControls().sendCustomAction(Keys.Action.QUEUE_NEXT, extras);
            LogHelper.d(TAG, "AlbumOrArtistContextMenuClickListener queueNext: " + item.getMediaId());

        } catch (AlbumOrArtistContextMenuClickException e) {
            e.log();
        }
    }

    @Override
    public void addToPlaylist(MediaBrowserCompat.MediaItem item, ITEM_TYPE type) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Choose Playlist");
        List<MediaBrowserCompat.MediaItem> lists = Repository.getInstance(context).getAllPlaylists();
        String[] list = new String[lists.size()];
        for (int i = 0; i < lists.size(); i++) {
            list[i] = lists.get(i).getDescription().getTitle() + "";
        }
        builder.setItems(list, (dialog, which) -> {
            addToPlaylist(item, type, lists.get(which));
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void addToPlaylist(MediaBrowserCompat.MediaItem item, ITEM_TYPE type, MediaBrowserCompat.MediaItem playlistObj) {
        Keys.PlaylistType playlistType = playlistObj.getDescription().getDescription() == null? Keys.PlaylistType.PLAYLIST: Keys.PlaylistType.HYBRID_PLAYLIST;
        String playlist = String.valueOf(playlistObj.getDescription().getTitle());
        if(playlistType == Keys.PlaylistType.HYBRID_PLAYLIST) {
            if(type == ITEM_TYPE.ALBUMS){

            } else if(type == ITEM_TYPE.ARTISTS){

            }
        } else {
            ContentResolver resolver = context.getContentResolver();
            Cursor cursor = resolver.query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, new String[]{MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME}, MediaStore.Audio.Playlists.NAME + " = '" + playlist + "'", null, null);
            cursor.moveToFirst();
            long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID));
            cursor.close();
            Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", id);
            Cursor cur = resolver.query(uri, new String[]{MediaStore.Audio.Playlists.Members.PLAY_ORDER}, null, null, null);
            cur.moveToLast();
            int base = cur.getCount() == 0 ? -1 : cur.getInt(cur.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.PLAY_ORDER));
            cur.close();
            if(type == ITEM_TYPE.ALBUMS){
                List<MediaBrowserCompat.MediaItem> songs =  Repository.getInstance(context).getSongsOfAlbum(item.getMediaId());
                for(MediaBrowserCompat.MediaItem song:songs){
                    String[] parts = song.getMediaId().split("[/|]");
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, ++base);
                    values.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, parts[parts.length - 1]);
                    resolver.insert(uri, values);
                }
            } else if(type == ITEM_TYPE.ARTISTS){
                List<MediaBrowserCompat.MediaItem> songs =  Repository.getInstance(context).getSongsOfArtist(item.getMediaId());
                for(MediaBrowserCompat.MediaItem song:songs){
                    String[] parts = song.getMediaId().split("[/|]");
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, ++base);
                    values.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, parts[parts.length - 1]);
                    resolver.insert(uri, values);
                }
            }
        }


        Toast.makeText(context, "Added to "+playlist, Toast.LENGTH_SHORT).show();
    }

    String getFirstToken(ITEM_TYPE type) {
        switch (type) {
            case ALBUMS:
                return "ALBUMS/";
            case ARTISTS:
                return "ARTISTS/";
            default:
                return "";
        }
    }

    static class AlbumOrArtistContextMenuClickException extends Exception {
        String str;

        public AlbumOrArtistContextMenuClickException(String message) {
            super(message);
            this.str = message;
        }

        void log() {
            LogHelper.d(TAG, "log: " + str);
        }
    }
}
