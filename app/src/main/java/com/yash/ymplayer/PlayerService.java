package com.yash.ymplayer;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.session.PlaybackState;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.service.media.MediaBrowserService;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.util.Pair;
import androidx.lifecycle.Observer;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;
import androidx.preference.PreferenceManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.github.kotvertolet.youtubejextractor.YoutubeJExtractor;
import com.github.kotvertolet.youtubejextractor.exception.ExtractionException;
import com.github.kotvertolet.youtubejextractor.exception.YoutubeRequestException;
import com.github.kotvertolet.youtubejextractor.models.AdaptiveAudioStream;
import com.github.kotvertolet.youtubejextractor.models.youtube.videoData.YoutubeVideoData;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioListener;
import com.google.android.exoplayer2.audio.AuxEffectInfo;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.ResolvingDataSource;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.HandlerWrapper;
import com.google.android.exoplayer2.util.Util;
import com.yash.ymplayer.data.UriCache;
import com.yash.ymplayer.helper.LogHelper;
import com.yash.ymplayer.repository.OnlineRepository;
import com.yash.ymplayer.repository.OnlineYoutubeRepository;
import com.yash.ymplayer.repository.Repository;
import com.yash.ymplayer.storage.AudioProvider;
import com.yash.ymplayer.storage.MediaItem;
import com.yash.ymplayer.storage.OfflineMediaProvider;
import com.yash.ymplayer.util.Keys;
import com.yash.ymplayer.util.Song;
import com.yash.ymplayer.util.YoutubeSong;

import java.io.IOException;
import java.security.Key;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;
import static android.media.AudioManager.STREAM_MUSIC;
import static android.net.ConnectivityManager.NetworkCallback;

public class PlayerService extends MediaBrowserServiceCompat implements PlayerHelper {
    public static final String STATE_PREF = "PlayerState";
    public static final String METADATA_KEY_LIKE = "like";
    public static final String MEDIA_ID = "mediaId";
    private static final String TAG = "PlayerService";
    private static final String CHANNEL_ID = "channelOne";
    public static final String METADATA_KEY_FAVOURITE = "favourite";
    MediaSessionCompat mSession;
    PlaybackStateCompat.Builder mPlaybackStateBuilder;
    MediaMetadataCompat.Builder mMediaMetadataBuilder;
    String currentMediaIdOrVideoId;
    SimpleExoPlayer player;
    List<MediaSessionCompat.QueueItem> playingQueue;
    List<String> mediaIdLists;
    int queuePos = -1;
    List<Song> songs;
    SharedPreferences preferences;
    long likeState = 0;
    Handler handler = new Handler();
    AudioManager audioManager;
    AudioFocusRequest audioFocusRequest;
    private long savedPlayerPosition;
    ConcatenatingMediaSource mediaSources;
    int repeatMode;
    boolean isShuffleModeEnabled;
    boolean isSeek;
    MediaSessionConnector mMediaSessionConnector;
    Keys.PLAYING_MODE playingMode = Keys.PLAYING_MODE.OFFLINE;
    ExecutorService executorService = Executors.newFixedThreadPool(20);
    SharedPreferences audioPreferences;
    ConnectivityManager connectivityManager;
    boolean isInternetAvailable;
    int playbackEndedStatus;
    DataSource.Factory factory;
    boolean isQueueChanged;
    UriCache uriCache;


    /* Declares that ContentStyle is supported */
    public static final String CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED";

    /*
     * Bundle extra indicating the presentation hint for playable media items.
     */
    public static final String CONTENT_STYLE_PLAYABLE_HINT =
            "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT";

    /*
     * Bundle extra indicating the presentation hint for browsable media items.
     */
    public static final String CONTENT_STYLE_BROWSABLE_HINT =
            "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT";

    /*
     * Specifies the corresponding items should be presented as lists.
     */
    public static final int CONTENT_STYLE_LIST_ITEM_HINT_VALUE = 1;

    /*
     * Specifies that the corresponding items should be presented as grids.
     */
    public static final int CONTENT_STYLE_GRID_ITEM_HINT_VALUE = 2;

    /*
     * Specifies that the corresponding items should be presented as lists and are
     * represented by a vector icon. This adds a small margin around the icons
     * instead of filling the full available area.
     */
    public static final int CONTENT_STYLE_CATEGORY_LIST_ITEM_HINT_VALUE = 3;

    /*
     * Specifies that the corresponding items should be presented as grids and are
     * represented by a vector icon. This adds a small margin around the icons
     * instead of filling the full available area.
     */
    public static final int CONTENT_STYLE_CATEGORY_GRID_ITEM_HINT_VALUE = 4;

    @Override
    public void onCreate() {
        super.onCreate();
        LogHelper.d(TAG, "onCreate: Service");
        IntentFilter noisyIntentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        //registerReceiver(noisyReceiver, noisyIntentFilter);

        //Variables
        songs = new ArrayList<>();
        playingQueue = new ArrayList<>();
        preferences = getSharedPreferences(STATE_PREF, MODE_PRIVATE);
        audioPreferences = getSharedPreferences(Keys.SHARED_PREFERENCES.AUDIO_URL_MAPPING, MODE_PRIVATE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        mediaSources = new ConcatenatingMediaSource();
        mediaIdLists = new ArrayList<>();
        isSeek = false;
        repeatMode = preferences.getInt(Keys.REPEAT_MODE, 0);
        isShuffleModeEnabled = preferences.getBoolean(Keys.SHUFFLE_MODE, false);
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager != null)
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback);

        //MediaSession
        mSession = new MediaSessionCompat(this, this.getClass().getSimpleName());
//        mMediaSessionConnector = new MediaSessionConnector(mSession);
//        mMediaSessionConnector.setPlayer(player);
//        mMediaSessionConnector.setPlaybackPreparer();
        mSession.setCallback(mediaSessionCallbacks);
        mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        setSessionToken(mSession.getSessionToken());
        initPlaybackState();
        mSession.setRepeatMode(repeatMode);
        mSession.setShuffleMode(isShuffleModeEnabled ? PlaybackStateCompat.SHUFFLE_MODE_ALL : PlaybackStateCompat.SHUFFLE_MODE_NONE);
        mSession.setActive(true);


        //MediaPlayer
        player = null;

        initDataSourceFactory();
        uriCache = new UriCache(10);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogHelper.d(TAG, "onStartCommand: Handle Event");
        MediaButtonReceiver.handleIntent(mSession, intent);
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        Bundle extras = new Bundle();
        extras.putBoolean(BrowserRoot.EXTRA_OFFLINE, true);
        return new BrowserRoot("ROOT", extras);
    }


    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        //result.detach();

        List<MediaBrowserCompat.MediaItem> mediaItems = null;
        if (parentId.equals("ROOT")) {
            mediaItems = getRootChildren();
            result.sendResult(mediaItems);
        } else {
            onLoadChildren(parentId, result, new Bundle());
        }
    }


    Result<List<MediaBrowserCompat.MediaItem>> resultSender;

    boolean isResultSent;

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result, @NonNull Bundle options) {
        LogHelper.d(TAG, "onLoadChildren: PlayerService: Parent Id: " + parentId);
        result.detach();
        resultSender = result;
        List<MediaBrowserCompat.MediaItem> mediaItems = null;
        if (parentId.equals("TOP_TRACKS")) {
            isResultSent = false;
            OnlineYoutubeRepository.getInstance(this).topTracks("", (tracks, prevToken, nextToken) -> {
                if (!isResultSent) {
                    isResultSent = true;
                    result.sendResult(mapToMediaItems(tracks));
                }
            });
        } else {
            if (parentId.equals("ALL_SONGS")) {
                mediaItems = Repository.getInstance(this).getAllSongs();
            } else if (parentId.contains("ARTISTS")) {
                if (parentId.equals("ARTISTS"))
                    mediaItems = Repository.getInstance(this).getAllArtists();
                else {
                    mediaItems = Repository.getInstance(this).getSongsOfArtist(parentId);
                }
            } else if (parentId.contains("ALBUMS")) {
                if (parentId.equals("ALBUMS"))
                    mediaItems = Repository.getInstance(this).getAllAlbums();
                else {
                    mediaItems = Repository.getInstance(this).getSongsOfAlbum(parentId);
                }
            } else if (parentId.contains("PLAYLISTS")) {
                if (parentId.equals("PLAYLISTS")) {
                    mediaItems = Repository.getInstance(this).getAllPlaylists();
                } else {
                    mediaItems = Repository.getInstance(this).getAllSongsOfPlaylist(parentId);
                }
            }
            LogHelper.d(TAG, "onLoadChildren: PlayerService : MediaItem length - " + ((mediaItems != null) ? mediaItems.size() : null));
            result.sendResult(mediaItems);
        }
    }

    List<MediaBrowserCompat.MediaItem> mapToMediaItems(List<YoutubeSong> list) {
        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();
        for (YoutubeSong song : list) {
            MediaBrowserCompat.MediaItem item = new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                    .setMediaId(song.getVideoId())
                    .setMediaUri(Uri.parse(song.getVideoId()))
                    .setTitle(song.getTitle())
                    .setSubtitle(song.getChannelTitle())
                    .setIconUri(Uri.parse(song.getArt_url_medium()))
                    .build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);
            mediaItems.add(item);
        }
        return mediaItems;
    }


    private List<MediaBrowserCompat.MediaItem> getRootChildren() {
        List<MediaBrowserCompat.MediaItem> items = new ArrayList<>();
        Bundle extras = new Bundle();
        items.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                .setMediaId("ALL_SONGS")
                .setTitle("All Tracks")
                .build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
        items.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                .setTitle("Artists")
                .setMediaId("ARTISTS")
                .build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
        extras.putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_GRID_ITEM_HINT_VALUE);
        extras.putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE);
        items.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                .setTitle("Albums")
                .setMediaId("ALBUMS")
                .setExtras(extras)
                .build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
        items.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                .setTitle("Playlists")
                .setMediaId("PLAYLISTS")
                .build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
        extras.putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE);
        extras.putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_GRID_ITEM_HINT_VALUE);
        items.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                .setTitle("Top Online Tracks")
                .setMediaId("TOP_TRACKS")
                .setExtras(extras)
                .build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
        return items;
    }


    @Override
    public void onDestroy() {
        if (connectivityManager != null)
            connectivityManager.unregisterNetworkCallback(networkCallback);
        if (player != null)
            player.release();
        mSession.release();
        stopForeground(true);
        LogHelper.d(TAG, "onDestroy: Service Destroyed");
    }

    MediaSessionCompat.Callback mediaSessionCallbacks = new MediaSessionCompat.Callback() {
        @Override
        public boolean onMediaButtonEvent(@NonNull Intent mediaButtonIntent) {
            LogHelper.d(TAG, "onMediaButtonEvent: ");
            return super.onMediaButtonEvent(mediaButtonIntent);
        }

        /**
         * Override to handle requests to prepare playback. During the preparation, a session should
         * not hold audio focus in order to allow other sessions play seamlessly. The state of
         * playback should be updated to {@link PlaybackState#STATE_PAUSED} after the preparation is
         * done.
         */
        @Override
        public void onPrepare() {
            super.onPrepare();
        }

        @Override
        public void onPlayFromUri(Uri uri, Bundle extras) {
            playingMode = Keys.PLAYING_MODE.ONLINE;
            currentMediaIdOrVideoId = uri.toString();
            setOnlinePlayingQueue(currentMediaIdOrVideoId);
            resolveQueuePosition(extractId(currentMediaIdOrVideoId));
            dispatchPlayRequest();
        }

        /**
         * Override to handle requests to play a specific mediaId that was
         * provided by your app's {@link MediaBrowserService}.
         *
         * @param mediaId id of the audio
         * @param extras any extra
         */
        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            LogHelper.d(TAG, "onPlayFromMediaId: extra: " + extras + " mediaId: " + mediaId);
            playingMode = Keys.PLAYING_MODE.OFFLINE;
            String id = extractId(mediaId);
            if (!pattern.matcher(id).matches()) {
                onPlayFromUri(Uri.parse(mediaId), null);
                return;
            }

            currentMediaIdOrVideoId = mediaId;
            setPlayingQueue(mediaId, extras);
            resolveQueuePosition(id);
            dispatchPlayRequest();
            LogHelper.d(TAG, "onPlayFromMediaId: \n MediaID : " + currentMediaIdOrVideoId + " \n Queue Position : " + queuePos +
                    " \n No Queue Items : " + playingQueue.size() + " \n Player Position: ");
        }

        /**
         * Override to handle requests to begin playback.
         */
        @Override
        public void onPlay() {
            LogHelper.d(TAG, "onPlay: currentMediaId:" + currentMediaIdOrVideoId);

            LogHelper.d(TAG, "onPlay: playbackstate:" + mSession.getController().getPlaybackState().getState());
            if (currentMediaIdOrVideoId == null) {
                if (playingQueue.isEmpty())
                    setPlayingQueue(null, new Bundle());
                queuePos = 0;
            }
            dispatchPlayRequest();
        }


        /**
         * Override to handle requests to pause playback.
         */
        @Override
        public void onPause() {
            LogHelper.d(TAG, "onPause: ");
            if (player != null) {
                LogHelper.d(TAG, "onPause: isPlaying: " + player.isPlaying());
                if (player.isPlaying()) setPlaybackState(PlaybackStateCompat.STATE_PAUSED);
                else setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
                player.setPlayWhenReady(false);
                handler.removeCallbacks(playNextOnMediaError);
                pushNotification(PlaybackStateCompat.STATE_PAUSED);

            }


        }

        /**
         * Override to handle requests to skip to the next media item.
         */
        @Override
        public void onSkipToNext() {
            LogHelper.d(TAG, "onSkipToNext: =================================>");
            handler.removeCallbacks(playNextOnMediaError);

            if (player == null) {
                if (playbackEndedStatus == PlaybackEndedStatus.Finished && !isShuffleModeEnabled && repeatMode == ExoPlayer.REPEAT_MODE_OFF)
                    return;
                player = getSimpleExoPlayer(null);
                preparePlayer(player);
                LogHelper.d(TAG, "onSkipToNext: Next window index: " + player.getNextWindowIndex() + " Previous window index:" + player.getPreviousWindowIndex() + " queue pos: " + queuePos + " player current index:" + player.getCurrentWindowIndex() + "Timeline:" + player.getCurrentTimeline().isEmpty() + "    nnnkn:  ");
            } else if (player.getPlaybackError() != null) {
                LogHelper.d(TAG, "onSkipToNext: Playback Error");
                if (player.getNextWindowIndex() == C.INDEX_UNSET) return;
                queuePos = player.getNextWindowIndex();
                preparePlayer(player);
            } else {
                player.next();
            }
            setPlayWhenReady(true);
        }

        /**
         * Override to handle requests to skip to the previous media item.
         */
        @Override
        public void onSkipToPrevious() {
            LogHelper.d(TAG, "onSkipToPrevious: <==============================================");
            handler.removeCallbacks(playNextOnMediaError);

            if (player == null) {
                player = getSimpleExoPlayer(null);
                preparePlayer(player);
            } else if (player.getPlaybackError() != null) {
                if (player.getPreviousWindowIndex() == C.INDEX_UNSET) return;
                queuePos = player.getPreviousWindowIndex();
                preparePlayer(player);
            } else {
                LogHelper.d(TAG, "onSkipToPrevious: " + player);
                if (player.getContentPosition() > 2000) {
                    isSeek = true;
                    player.seekTo(0);
                } else {
                    LogHelper.d(TAG, "onSkipToPrevious: previous index: " + player.getPreviousWindowIndex() + " playwhenready:" + player.getPlayWhenReady());
                    player.previous();
                }
            }
            setPlayWhenReady(true);
            LogHelper.d(TAG, "onSkipToPrevious: setting playWhenReady true");
        }

        /**
         * Override to handle requests to stop playback.
         */
        @Override
        public void onStop() {
            if (player == null) return;
            savedPlayerPosition = player.getCurrentPosition();
            player.stop();
            player.release();
            player = null;
            setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
            pushNotification(PlaybackStateCompat.STATE_STOPPED);
            LogHelper.d(TAG, "onStop: " + savedPlayerPosition);
        }

        /**
         * Override to handle requests to seek to a specific position in ms.
         *
         * @param pos New position to move to, in milliseconds.
         */
        @Override
        public void onSeekTo(long pos) {
            LogHelper.d(TAG, "onSeekTo: " + pos);
            if (player != null) {
                isSeek = true;
                player.seekTo(pos);
                setPlaybackState(mSession.getController().getPlaybackState().getState());
            }
        }

        @Override
        public void onSetRepeatMode(int repeatMode) {
            LogHelper.d(TAG, "onSetRepeatMode: ");
            PlayerService.this.repeatMode = Math.min(repeatMode, 2);
            if (player != null)
                player.setRepeatMode(PlayerService.this.repeatMode);
            mSession.setRepeatMode(repeatMode);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt(Keys.REPEAT_MODE, PlayerService.this.repeatMode);
            editor.apply();
        }

        @Override
        public void onSetShuffleMode(int shuffleMode) {
            LogHelper.d(TAG, "onSetShuffleMode: ");
            if (shuffleMode == 0) {
                isShuffleModeEnabled = false;
            } else if (shuffleMode > 0) {
                isShuffleModeEnabled = true;
            }
            if (player != null)
                player.setShuffleModeEnabled(isShuffleModeEnabled);
            mSession.setShuffleMode(shuffleMode);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(Keys.SHUFFLE_MODE, isShuffleModeEnabled);
            editor.apply();

        }

        @Override
        public void onSkipToQueueItem(long id) {
            LogHelper.d(TAG, "onSkipToQueueItem: id: " + id);
            if (id == -1) return;
            queuePos = (int) id;
            isQueueChanged = false;
            setPlaybackState(PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM);
            dispatchPlayRequest();
        }

        @Override
        public void onCustomAction(String action, Bundle extras) {
            LogHelper.d(TAG, "onCustomAction: ");
            Bundle extra = new Bundle();
            switch (action) {
                case "like":
                    likeState = extras.getLong("like_enabled", 0);
                    LogHelper.d(TAG, "onCustomAction: likeState: " + likeState);
                    setMediaMetadata(playingQueue.get(queuePos));
                    break;
                case Keys.Action.ADD_TO_PLAYLIST:
                    if (extras != null && extras.containsKey(Keys.PLAYLIST_NAME) && extras.containsKey(Keys.MEDIA_ID) && extras.containsKey(Keys.TITLE) && extras.containsKey(Keys.ARTIST) && extras.containsKey(Keys.ALBUM)) {
                        String playlist = extras.getString(Keys.PLAYLIST_NAME);
                        String mediaId = extras.getString(Keys.MEDIA_ID);
                        String title = extras.getString(Keys.TITLE);
                        String artist = extras.getString(Keys.ARTIST);
                        String album = extras.getString(Keys.ALBUM);
                        String artwork = extra.getString(Keys.ARTWORK);
                        MediaItem item = new MediaItem(mediaId, title, artist, album, playlist, artwork);

                        if (Repository.getInstance(PlayerService.this).addToPlaylist(item) == -1)
                            Toast.makeText(PlayerService.this, "Already Added to " + playlist, Toast.LENGTH_SHORT).show();
                        else
                            Toast.makeText(PlayerService.this, "Added to " + playlist, Toast.LENGTH_SHORT).show();
                    } else
                        Toast.makeText(PlayerService.this, "Please Select Playlist name", Toast.LENGTH_SHORT).show();
                    break;
                case Keys.Action.QUEUE_NEXT:
                    if (extras != null && extras.containsKey(Keys.MEDIA_ID) && extras.containsKey(Keys.QUEUE_HINT)) {
                        String mediaId = extras.getString(Keys.MEDIA_ID);
                        int type = extras.getInt(Keys.QUEUE_HINT);
                        LogHelper.d(TAG, "onCustomAction: QUEUE_NEXT:MediaId:" + mediaId);

                        List<MediaSessionCompat.QueueItem> items = Repository.getInstance(PlayerService.this).getQueue(type, mediaId);
                        playingQueue.addAll((queuePos + 1), items);
                        for (int i = 0; i < items.size(); i++) {
                            int queue_pos = queuePos + 1 + i;
                            String media_id = items.get(i).getDescription().getMediaId();
                            addToMediaSources(media_id, queue_pos);
                            mediaIdLists.add(queue_pos, media_id);
                        }


                        mSession.setQueueTitle(Keys.QUEUE_TITLE.USER_QUEUE);
                        mSession.setQueue(playingQueue);
                        if (player != null)
                            setPlaybackState(mSession.getController().getPlaybackState().getState());
                    }
                    break;

                case Keys.Action.REMOVE_FROM_QUEUE:
                    if (extras != null && extras.containsKey(Keys.MEDIA_ID)) {
                        int pos = mediaIdLists.indexOf(extras.getString(Keys.MEDIA_ID));
                        if (pos == -1) return;
                        LogHelper.d(TAG, "onCustomAction: REMOVE_FROM_QUEUE:" + " Position found:" + pos);
                        playingQueue.remove(pos);
                        mediaIdLists.remove(pos);
                        mediaSources.removeMediaSource(pos);

                        if (playingQueue.size() != 0) {
                            mSession.setQueueTitle(Keys.QUEUE_TITLE.CUSTOM);
                            if (queuePos == pos) {
                                if (isShuffleModeEnabled) {
                                    if (player == null) return;
                                    player.setPlayWhenReady(false);
                                    pos = player.getCurrentTimeline().getNextWindowIndex(queuePos, Player.REPEAT_MODE_ALL, true);
                                    player.prepare(mediaSources);
                                    queuePos = pos > queuePos ? pos - 1 : pos;
                                } else {
                                    queuePos = queuePos % playingQueue.size();
                                    LogHelper.d(TAG, "onCustomAction: REMOVE_FROM_QUEUE: queuePos:" + queuePos);
                                }
                                player.seekToDefaultPosition(queuePos);
                                player.setPlayWhenReady(true);
                            } else {
                                if (pos < queuePos) queuePos--;
                                setPlaybackState(mSession.getController().getPlaybackState().getState());
                            }
                            mSession.setQueue(playingQueue);
                        } else {
                            queuePos = -1;
                            mSession.setMetadata(null);
                            mSession.setQueueTitle("");
                            currentMediaIdOrVideoId = null;
                            setPlaybackState(PlaybackStateCompat.STATE_NONE);
                            stopForeground(true);
                            mSession.getController().getTransportControls().stop();
                        }

                    }
                    break;

                case Keys.Action.SWAP_QUEUE_ITEM:
                    if (extras != null && extras.containsKey(Keys.FROM_POSITION) && extras.containsKey(Keys.TO_POSITION)) {
                        mSession.setQueueTitle(Keys.QUEUE_TITLE.CUSTOM);
                        String mediaId = mediaIdLists.get(queuePos);
                        LogHelper.d(TAG, "onCustomAction: SWAP_QUEUE_ITEM mediaid = " + mediaId + " pos:" + queuePos);
                        int fromPosition = extras.getInt(Keys.FROM_POSITION);
                        int toPosition = extras.getInt(Keys.TO_POSITION);
                        LogHelper.d(TAG, "onCustomAction: from :" + fromPosition + " to :" + toPosition);
                        MediaSessionCompat.QueueItem item = playingQueue.remove(fromPosition);
                        playingQueue.add(toPosition, item);
                        String id = mediaIdLists.remove(fromPosition);
                        mediaIdLists.add(toPosition, id);
                        mediaSources.moveMediaSource(fromPosition, toPosition);
                        queuePos = mediaIdLists.indexOf(mediaId);
                        LogHelper.d(TAG, "onCustomAction: new pos = " + queuePos);
                        setPlaybackState(mSession.getController().getPlaybackState().getState());
                        mSession.setQueue(playingQueue);
                    }
                    break;

                case Keys.Action.TOGGLE_FAVOURITE:
                    String mediaId = extractId(playingQueue.get(queuePos).getDescription().getMediaId());
                    if (mSession.getController().getMetadata().getLong(PlayerService.METADATA_KEY_FAVOURITE) == 0) {
                        Repository.getInstance(PlayerService.this).addToPlaylist(new MediaItem(mediaId, playingQueue.get(queuePos).getDescription().getTitle().toString(), playingQueue.get(queuePos).getDescription().getSubtitle().toString(), playingQueue.get(queuePos).getDescription().getDescription().toString(), Keys.PLAYLISTS.FAVOURITES, null));
                    } else {
                        Repository.getInstance(PlayerService.this).removeFromPlaylist(mediaId, Keys.PLAYLISTS.FAVOURITES);
                    }
                    setMediaMetadata(playingQueue.get(queuePos));
                    break;
                default:
            }


        }

    };

    /*
      Custom Methods
     */

    /**
     *
     */
    void dispatchPlayRequest() {
        PlaybackStateCompat mState = mSession.getController().getPlaybackState();
        switch (mState.getState()) {
            case PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM:
                LogHelper.d(TAG, "dispatchPlayRequest: STATE_SKIPPING_TO_QUEUE_ITEM");
                player = getSimpleExoPlayer(player);
                if (player.getPlaybackError() != null) {
                    handler.removeCallbacks(playNextOnMediaError);
                    preparePlayer(player);
                } else seekPlayer(queuePos, C.TIME_UNSET);
                setPlayWhenReady(true);
                break;

            case PlaybackStateCompat.STATE_BUFFERING:
            case PlaybackStateCompat.STATE_PLAYING:
                LogHelper.d(TAG, "dispatchPlayRequest: STATE_BUFFERING or STATE_PLAYING");
                seekPlayer(queuePos, C.TIME_UNSET);
                setPlayWhenReady(true);
                break;

            case PlaybackStateCompat.STATE_PAUSED:
                LogHelper.d(TAG, "dispatchPlayRequest: STATE_PAUSED");
                setPlayWhenReady(true);
                break;

            case PlaybackStateCompat.STATE_STOPPED:
                LogHelper.d(TAG, "dispatchPlayRequest: STATE_STOPPED");
                player = getSimpleExoPlayer(player);
                if (playbackEndedStatus == PlaybackEndedStatus.Interrupted)
                    seekPlayer(queuePos, savedPlayerPosition);
                else seekPlayer(queuePos, 0);
                setPlayWhenReady(true);
                break;
            case PlaybackStateCompat.STATE_NONE:
                LogHelper.d(TAG, "dispatchPlayRequest: STATE_NONE qPos:" + queuePos);
                player = getSimpleExoPlayer(player);
                player.seekTo(queuePos, 0);
                setPlayWhenReady(true);
                break;
            default:
        }
    }

    @Override
    public void setOnlinePlayingQueue(String uri) {
        playingQueue.clear();
        mediaIdLists.clear();
        if (player != null && player.isPlaying())
            player.stop();
        playingQueue = OnlineYoutubeRepository.getInstance(this).getPlayingQueue(uri);
        mediaSources.clear();
        mSession.setQueueTitle(uri.split("[|]")[0]);
        for (int i = 0; i < playingQueue.size(); i++) {
            String id = playingQueue.get(i).getDescription().getMediaId();
            mediaIdLists.add(id);
            addHttpSourceToMediaSources(id, i);
            LogHelper.d(TAG, "setOnlinePlayingQueue: video id: " + id);
        }
        mSession.setQueue(playingQueue);
        isQueueChanged = true;
        if (player != null)
            player.prepare(mediaSources);
    }

    @Override
    public int getPositionInQueue(@NonNull String mediaId) {
        String[] parts = mediaId.split("[/|]");
        String id = parts[parts.length - 1];
        return mediaIdLists.indexOf(id);
    }

    /**
     * Extract the position of mediaId from Queue
     *
     * @param mediaId the id of current media
     */
    @Override
    public void resolveQueuePosition(String mediaId) {
        int pos = mediaIdLists.indexOf(mediaId);
        if (pos != -1) {
            queuePos = pos;
            LogHelper.d(TAG, "resolveQueuePosition: pos:" + queuePos);
        } else {
            if (!playingQueue.isEmpty()) {
                queuePos = 0;
            }
            LogHelper.d(TAG, "mediaId is not available in Queue");
        }
    }

    public String extractId(String mediaId) {
        String[] parts = mediaId.split("[/|]");
        return parts[parts.length - 1];
    }

    /**
     * Obtain Playing Queue and Set to mediaSession
     * The Queue includes - MediaSources, Playing QueueItem
     *
     * @param mediaId the id of current media
     */
    @Override
    public void setPlayingQueue(@Nullable String mediaId, Bundle extras) {
        try {
            boolean playSingle = extras.getBoolean(Keys.PLAY_SINGLE, false);
            String queueTitle;
            if (playSingle)
                queueTitle = mediaId + "_PlaYSinglE";
            else queueTitle = mediaId.split("[|]")[0];
            if (mSession.getController().getQueueTitle() != null && queueTitle.contentEquals(mSession.getController().getQueueTitle())) {
                LogHelper.d(TAG, "setPlayingQueue: .... Queue Already set");
                isQueueChanged = false;
                return;
            }
            mSession.setQueueTitle(queueTitle);

            if (player != null && player.isPlaying())
                player.stop();

            mediaIdLists.clear();
            if (!playSingle) {
                playingQueue = Repository.getInstance(PlayerService.this).getCurrentPlayingQueue(mediaId);
                mediaSources.clear();
                for (int i = 0; i < playingQueue.size(); i++) {
                    String id = playingQueue.get(i).getDescription().getMediaId();
                    mediaIdLists.add(id);
                    addHttpSourceToMediaSources(id, i);
                }
                mSession.setQueue(playingQueue);

                LogHelper.d(TAG, "setPlayingQueue: All");
            } else if (extras.getBoolean(Keys.PLAY_SINGLE)) {
                playingQueue = Repository.getInstance(PlayerService.this).getQueue(AudioProvider.QueueHint.SINGLE_SONG, mediaId);
                mediaSources.clear();
                for (int i = 0; i < playingQueue.size(); i++) {
                    String id = playingQueue.get(i).getDescription().getMediaId();
                    mediaIdLists.add(id);
                    addHttpSourceToMediaSources(id, i);
                }
                mSession.setQueue(playingQueue);
                LogHelper.d(TAG, "setPlayingQueue: Single");
            }
        } catch (NullPointerException e) {
            LogHelper.d(TAG, e.getMessage());
            if (mediaId == null) {
                mSession.setQueueTitle("RANDOM");
                setPlaybackState(PlaybackStateCompat.STATE_NONE);
                mediaIdLists.clear();
                playingQueue = Repository.getInstance(PlayerService.this).getRandomQueue();
                for (int i = 0; i < playingQueue.size(); i++) {
                    String id = playingQueue.get(i).getDescription().getMediaId();
                    mediaIdLists.add(id);
                    addHttpSourceToMediaSources(id, i);
                }
                mSession.setQueue(playingQueue);
            }
        }
        isQueueChanged = true;
        if (player != null)
            player.prepare(mediaSources);

    }

    /**
     * Initial Builder of PlaybackState
     */
    void initPlaybackState() {
        mPlaybackStateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                        | PlaybackStateCompat.ACTION_PLAY_FROM_URI
                        | PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_PAUSE
                        | PlaybackStateCompat.ACTION_PLAY_PAUSE
                        | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                        | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackStateCompat.ACTION_SEEK_TO
                        | PlaybackStateCompat.ACTION_SET_REPEAT_MODE
                        | PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE)
                .setState(PlaybackStateCompat.STATE_NONE, player != null ? player.getCurrentPosition() : 0, player != null ? player.getPlaybackParameters().speed : 1.0f)
                .setBufferedPosition(player != null ? player.getBufferedPosition() : 0)
                .setActiveQueueItemId(queuePos);
        mSession.setPlaybackState(mPlaybackStateBuilder.build());

    }

    /**
     * Build and set playback state to @link MediaSessionCompat
     *
     * @param state current playbackState
     */
    void setPlaybackState(int state) {
        mPlaybackStateBuilder.setState(state, player != null ? player.getCurrentPosition() : 0, player != null ? player.getPlaybackParameters().speed : 1.0f)
                .setBufferedPosition(player != null ? player.getBufferedPosition() : 0)
                .setActiveQueueItemId(queuePos);
        mSession.setPlaybackState(mPlaybackStateBuilder.build());
    }


    int retryCount = 0;

    void loadAlbumArtAndPushNotification(Uri uri) {
        Glide.with(PlayerService.this).asBitmap().load(uri).into(new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                currentBitmapUriPair = new Pair<>(resource, uri.toString());
                setMediaMetadata(playingQueue.get(queuePos));
                pushNotification(PlaybackStateCompat.STATE_PLAYING);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                if (isInternetAvailable && retryCount < 3) {
                    retryCount++;
                    LogHelper.d(TAG, "onLoadFailed: Retry to load album art: retry:" + retryCount);
                    loadAlbumArtAndPushNotification(uri);
                } else {
                    currentBitmapUriPair = new Pair<>(BitmapFactory.decodeResource(getResources(), R.drawable.album_art_placeholder), "");
                    pushNotification(mSession.getController().getPlaybackState().getState());
                }
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
            }
        });
    }

    Pair<Bitmap, String> currentBitmapUriPair = new Pair<>(null, "");

    /**
     * Build and set mediaMetaData to MediaSessionCompat
     *
     * @param currentItem current mediaItem playing
     */
    void setMediaMetadata(MediaSessionCompat.QueueItem currentItem) {
        Uri albumArtUri;
        try {
            long playingMediaDuration = 0L;

            try {
                albumArtUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Long.parseLong(currentItem.getDescription().getMediaId()));
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(this, albumArtUri);
                byte[] data = retriever.getEmbeddedPicture();
                Bitmap bitmap;
                if (data != null)
                    bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                else
                    bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.album_art_placeholder);
                playingMediaDuration = Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                currentBitmapUriPair = new Pair<>(bitmap, albumArtUri.toString());
            } catch (NumberFormatException e) {
                LogHelper.d(TAG, "setMediaMetadata: Error: " + e.getMessage());
                albumArtUri = currentItem.getDescription().getIconUri();
                playingMediaDuration = currentItem.getDescription().getExtras().getLong("duration", 0L);
                if (!currentBitmapUriPair.second.equals(albumArtUri.toString())) {
                    retryCount = 0;
                    loadAlbumArtAndPushNotification(albumArtUri);
                }
            }
//            if (playingMode == Keys.PLAYING_MODE.OFFLINE) {
//
//            } else {
//
//            }
            currentMediaIdOrVideoId = currentItem.getDescription().getMediaId();
            mMediaMetadataBuilder = new MediaMetadataCompat.Builder()
                    .putText(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, currentItem.getDescription().getMediaId())
                    .putText(MediaMetadataCompat.METADATA_KEY_TITLE, currentItem.getDescription().getTitle())
                    .putText(MediaMetadataCompat.METADATA_KEY_ARTIST, currentItem.getDescription().getSubtitle())
                    .putText(MediaMetadataCompat.METADATA_KEY_ALBUM, currentItem.getDescription().getDescription())
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, albumArtUri.toString())
                    .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, mediaIdLists.indexOf(currentItem.getDescription().getMediaId()))
                    .putLong(PlayerService.METADATA_KEY_FAVOURITE, Repository.getInstance(this).isAddedTo(currentItem.getDescription().getMediaId(), Keys.PLAYLISTS.FAVOURITES))
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, playingMediaDuration);
            if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("albumart_enabled", true))
                mMediaMetadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentBitmapUriPair.first);
            mSession.setMetadata(mMediaMetadataBuilder.build());
        } catch (Exception e) {
            e.printStackTrace();
            mMediaMetadataBuilder = new MediaMetadataCompat.Builder()
                    .putText(MediaMetadataCompat.METADATA_KEY_TITLE, "Media Playback Error")
                    .putText(MediaMetadataCompat.METADATA_KEY_ARTIST, "Corrupted media file")
                    .putText(MediaMetadataCompat.METADATA_KEY_ALBUM, "error")
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, null)
                    .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, mediaIdLists.indexOf(currentItem.getDescription().getMediaId()))
                    .putLong(PlayerService.METADATA_KEY_FAVOURITE, Repository.getInstance(this).isAddedTo(currentItem.getDescription().getMediaId(), Keys.PLAYLISTS.FAVOURITES))
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 0);
            mSession.setMetadata(mMediaMetadataBuilder.build());
        }

    }

    void pushNotification(long state) {
        LogHelper.d(TAG, "pushNotification: ");
        if (currentBitmapUriPair.first == null)
            return;
        MediaMetadataCompat metadata = mSession.getController().getMetadata();
        if (metadata == null)
            return;

        Bitmap bitmap = currentBitmapUriPair.first;// mSession.getController().getMetadata().getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
        int minLength = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        PendingIntent notificationPendingIntent = PendingIntent.getActivity(this, 11, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.icon_notif)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(mSession.getSessionToken()).setShowActionsInCompactView(0, 1, 2))
                .setContentTitle(metadata.getText(MediaMetadataCompat.METADATA_KEY_TITLE))
                .setContentText(metadata.getText(MediaMetadataCompat.METADATA_KEY_ARTIST))
                .setSubText(metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM))
                .setContentInfo("YM Player")
                .setLargeIcon(Bitmap.createBitmap(bitmap, (bitmap.getWidth() - minLength) / 2, (bitmap.getHeight() - minLength) / 2, minLength, minLength))
                .setContentIntent(notificationPendingIntent)
                .addAction(R.drawable.icon_skip_prev, PlayerService.this.getString(R.string.prev), MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS))
                .addAction(state == PlaybackStateCompat.STATE_PLAYING ? R.drawable.icon_pause : R.drawable.icon_play, PlayerService.this.getString(R.string.play_pause), MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE))
                .addAction(R.drawable.icon_skip_next, PlayerService.this.getString(R.string.next), MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT))
                .build();
        startForeground(10, notification);

        if (state != PlaybackStateCompat.STATE_PLAYING) {
            stopForeground(false);
        }
    }


    ExoPlayer.EventListener ExoplayerEventListener = new ExoPlayer.EventListener() {
        @Override
        public void onPositionDiscontinuity(int reason) {
            switch (reason) {
                case ExoPlayer.DISCONTINUITY_REASON_PERIOD_TRANSITION:
                    LogHelper.d(TAG, "onPositionDiscontinuity: DISCONTINUITY_REASON_PERIOD_TRANSITION :");
                    LogHelper.d(TAG, "onPositionDiscontinuity:  Window index: " + player.getCurrentWindowIndex());
                    if (queuePos != player.getCurrentWindowIndex()) {
                        queuePos = player.getCurrentWindowIndex();
                        setMediaMetadata(playingQueue.get(queuePos));
                    }
                    setPlaybackState(player.getPlayWhenReady() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
                    pushNotification(player.getPlayWhenReady() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
                    break;
                case ExoPlayer.DISCONTINUITY_REASON_SEEK:
                    LogHelper.d(TAG, "onPositionDiscontinuity: DISCONTINUITY_REASON_SEEK ");
                    LogHelper.d(TAG, "onPositionDiscontinuity:  Window index : " + player.getCurrentWindowIndex() + " Play when ready : " + player.getPlayWhenReady());
                    if (player.getCurrentWindowIndex() == -1 || player.getCurrentWindowIndex() >= playingQueue.size())
                        return;
                    if (isSeek) {
                        isSeek = false;
                        return;
                    }
                    queuePos = player.getCurrentWindowIndex();
                    setMediaMetadata(playingQueue.get(queuePos));
                    pushNotification(player.getPlayWhenReady() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
                    break;
                case ExoPlayer.DISCONTINUITY_REASON_INTERNAL:
                    LogHelper.d(TAG, "onPositionDiscontinuity: DISCONTINUITY_REASON_INTERNAL");
                    break;
                case ExoPlayer.DISCONTINUITY_REASON_AD_INSERTION:
                    LogHelper.d(TAG, "onPositionDiscontinuity: DISCONTINUITY_REASON_AD_INSERTION");
                    break;
                case ExoPlayer.DISCONTINUITY_REASON_SEEK_ADJUSTMENT:
                    LogHelper.d(TAG, "onPositionDiscontinuity: DISCONTINUITY_REASON_SEEK_ADJUSTMENT");
                    LogHelper.d(TAG, "onPositionDiscontinuity: DISCONTINUITY_REASON_SEEK_ADJUSTMENT: quePos: " + player.getCurrentWindowIndex());
                    break;
                default:

            }
        }

        @Override
        public void onLoadingChanged(boolean isLoading) {
            LogHelper.d(TAG, "onLoadingChanged: loading:" + isLoading);
            if (!isLoading)
                setPlaybackState(mSession.getController().getPlaybackState().getState());
            LogHelper.d(TAG, "onLoadingChanged: Duration: " + TimeUnit.MILLISECONDS.toSeconds(player.getBufferedPosition()) + "s");

        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            LogHelper.d(TAG, "IsPlayingChanged Playing: isPlaying - " + isPlaying);

            if (isPlaying) {
                LogHelper.d(TAG, "onIsPlayingChanged: Playing");
                setPlaybackState(PlaybackStateCompat.STATE_PLAYING);
                pushNotification(PlaybackStateCompat.STATE_PLAYING);
            } else {
                if (player != null) {
                    if (player.getPlaybackState() == ExoPlayer.STATE_BUFFERING) {
                        LogHelper.d(TAG, "onIsPlayingChanged: Buffering");
                        setPlaybackState(PlaybackStateCompat.STATE_BUFFERING);
                        pushNotification(PlaybackStateCompat.STATE_PLAYING);
                    } else {
                        LogHelper.d(TAG, "onIsPlayingChanged: Paused");
                        setPlaybackState(PlaybackStateCompat.STATE_PAUSED);
                        pushNotification(PlaybackStateCompat.STATE_PAUSED);
                    }
                }
            }
        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {
            switch (error.type) {

                case ExoPlaybackException.TYPE_OUT_OF_MEMORY:
                    LogHelper.d(TAG, "PlayerError: TYPE_OUT_OF_MEMORY");
                    break;
                case ExoPlaybackException.TYPE_REMOTE:
                    LogHelper.d(TAG, "PlayerError: TYPE_REMOTE");
                    break;
                case ExoPlaybackException.TYPE_RENDERER:
                    LogHelper.d(TAG, "PlayerError: TYPE_RENDERER");
                    break;
                case ExoPlaybackException.TYPE_SOURCE:
                    LogHelper.d(TAG, "PlayerError: TYPE_SOURCE");
                    if (playingMode == Keys.PLAYING_MODE.OFFLINE) {
                        Toast.makeText(PlayerService.this, "Unable to play! Skipping next", Toast.LENGTH_SHORT).show();
                        handler.postDelayed(playNextOnMediaError, 2000);
                    } else {
                        if (!isInternetAvailable) {
                            playbackEndedStatus = PlaybackEndedStatus.Interrupted;
                            Toast.makeText(PlayerService.this, "No Internet Access", Toast.LENGTH_SHORT).show();
                            mSession.getController().getTransportControls().stop();
                        } else {
                            Toast.makeText(PlayerService.this, "Unable to play! Skipping next", Toast.LENGTH_SHORT).show();
                            handler.postDelayed(playNextOnMediaError, 2000);
                        }
                    }
                    LogHelper.d(TAG, "PlayerError: curr indx = " + player.getCurrentWindowIndex() + " next indx = " + player.getNextWindowIndex());
                    break;
                case ExoPlaybackException.TYPE_UNEXPECTED:
                    LogHelper.d(TAG, "PlayerError: TYPE_UNEXPECTED");
                    break;
            }
        }

        @Override
        public void onTimelineChanged(Timeline timeline, int reason) {
            LogHelper.d(TAG, "TimelineChanged: period_count: " + timeline.getPeriodCount());
        }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            LogHelper.d(TAG, "ExoPlayer playWhenReady: " + playWhenReady);
            switch (playbackState) {
                case Player.STATE_BUFFERING:
                    LogHelper.d(TAG, "ExoPlayer PlaybackState : STATE_BUFFERING");
                    setPlaybackState(playWhenReady ? PlaybackStateCompat.STATE_BUFFERING : PlaybackStateCompat.STATE_PAUSED);
                    break;
                case Player.STATE_ENDED:
                    LogHelper.d(TAG, "ExoPlayer PlaybackState: STATE_ENDED");
                    playbackEndedStatus = PlaybackEndedStatus.Finished;
                    mSession.getController().getTransportControls().stop();
                    break;
                case Player.STATE_IDLE:
                    LogHelper.d(TAG, "ExoPlayer PlaybackState: STATE_IDLE");
                    setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
                    pushNotification(PlaybackStateCompat.STATE_STOPPED);
                    break;
                case Player.STATE_READY:
                    LogHelper.d(TAG, "ExoPlayer PlaybackState: STATE_READY");
                    break;
            }

        }

    };

//    MediaSource getMediaSource(String mediaId) {
//        LogHelper.d(TAG, "MediaSource Media ID:" + mediaId);
//        String[] parts = mediaId.split("[/|]");
//        String id = parts[parts.length - 1];
//        Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Long.parseLong(id));
//        DataSource.Factory factory = new DefaultDataSourceFactory(PlayerService.this, Util.getUserAgent(getApplicationContext(), "YM Player"));
//        return new ProgressiveMediaSource.Factory(factory).setTag(id).createMediaSource(contentUri);
//    }

    Pair<String, Uri> tempAudioUriCache = new Pair<>("", null);
    Pattern pattern = Pattern.compile("[0-9]+");

    void initDataSourceFactory() {
        factory = new ResolvingDataSource.Factory(new DefaultDataSourceFactory(this, Util.getUserAgent(PlayerService.this, "YM Player")), new ResolvingDataSource.Resolver() {
            @Override
            public DataSpec resolveDataSpec(DataSpec dataSpec) throws IOException {
                LogHelper.d(TAG, "resolveDataSpec: " + dataSpec.uri);
                LogHelper.d(TAG, "resolveDataSpec: in cache: " + tempAudioUriCache.first);
                boolean isMatch = pattern.matcher(dataSpec.uri.toString()).matches();
                String uriId = dataSpec.uri.toString();
                LogHelper.d(TAG, "resolveDataSpec: pattern media id: matches: " + isMatch);
                if (isMatch)
                    return dataSpec.withUri(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Long.parseLong(uriId)));

                Uri audioUri = null;
                if (uriCache.isInCache(uriId)) {
                    LogHelper.d(TAG, "resolveDataSpec: In Cache no need of loading");
                    return dataSpec.withUri(uriCache.getUri(uriId));
                } else {
                    try {
                        YoutubeJExtractor youtubeJExtractor = new YoutubeJExtractor();
                        YoutubeVideoData videoData = youtubeJExtractor.extract(uriId);
                        if (!videoData.getStreamingData().getAdaptiveAudioStreams().isEmpty()) {
                            List<AdaptiveAudioStream> audioStreams = videoData.getStreamingData().getAdaptiveAudioStreams();
                            audioUri = Uri.parse(audioStreams.get(audioStreams.size() - 1).getUrl());
                            uriCache.pushUri(dataSpec.uri.toString(), audioUri);
                            LogHelper.d(TAG, "resolveDataSpec: new uri:" + audioUri);
                            for (AdaptiveAudioStream stream : videoData.getStreamingData().getAdaptiveAudioStreams()) {
                                LogHelper.d(TAG, "resolveDataSpec: audio sample rate:" + stream.getAudioSampleRate() + " bit rate" + stream.getAverageBitrate());
                            }
                        }
                    } catch (ExtractionException | YoutubeRequestException e) {
                        e.printStackTrace();
                    }
                }
                return dataSpec.withUri(audioUri);
            }
        });
    }

    @Override
    public void addHttpSourceToMediaSources(String videoId, int pos) {
        mediaSources.addMediaSource(pos, new ProgressiveMediaSource.Factory(factory).createMediaSource(Uri.parse(videoId)));

    }

    @Override
    public void addToMediaSources(@Nullable String mediaId, int pos) {
        if (mediaId == null) return;
        String[] parts = mediaId.split("[/|]");
        String id = parts[parts.length - 1];
        DataSource.Factory factory = new DefaultDataSourceFactory(this, Util.getUserAgent(getApplicationContext(), "YM Player"));
        Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Long.parseLong(id));
        mediaSources.addMediaSource(pos, new ProgressiveMediaSource.Factory(factory).setTag(id).createMediaSource(contentUri));
    }

    /**
     * @param player current instance of EXOPLAYER
     */
    @Override
    public void preparePlayer(SimpleExoPlayer player) {
        player.prepare(mediaSources, true, true);
        player.seekToDefaultPosition(queuePos);
    }

    @Override
    public void preparePlayer(SimpleExoPlayer player, long seekPosition) {
        player.prepare(mediaSources, true, true);
        player.seekTo(queuePos, seekPosition);
    }

    public void seekPlayer(int windowIndex, long seekPosition) {
        if (player == null) return;
        boolean isNoSeek = (!isQueueChanged && windowIndex == player.getCurrentWindowIndex() && player.getCurrentPosition() == C.TIME_UNSET);
        boolean isOnlySeek = (!isQueueChanged && windowIndex == player.getCurrentWindowIndex());
        LogHelper.d(TAG, "seekPlayer: seek : " + !isNoSeek + " only seek: " + isOnlySeek);
        if (isNoSeek) return;
        if (isOnlySeek)
            mSession.getController().getTransportControls().seekTo(seekPosition);
        else player.seekTo(windowIndex, seekPosition);
    }

    @Override
    public void setPlayWhenReady(boolean playWhenReady) {
        player.setPlayWhenReady(playWhenReady);
    }


    @Override
    public SimpleExoPlayer getSimpleExoPlayer(SimpleExoPlayer player) {
        if (player == null) {
            player = new SimpleExoPlayer.Builder(this)
                    .setTrackSelector(new DefaultTrackSelector(this))
                    .build();
            player.addListener(ExoplayerEventListener);
            player.setRepeatMode(repeatMode);
            player.prepare(mediaSources);
            player.setPlayWhenReady(false);
            player.setShuffleModeEnabled(isShuffleModeEnabled);
            player.setHandleAudioBecomingNoisy(true);
            player.setAudioAttributes(new com.google.android.exoplayer2.audio.AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.CONTENT_TYPE_MUSIC).build(), true);
            player.setWakeMode(C.WAKE_MODE_NETWORK);

        }
        return player;
    }

    Runnable playNextOnMediaError = new Runnable() {
        @Override
        public void run() {
            LogHelper.d(TAG, "playNextOnMediaError : Executed playNextOnMediaError");
            if (player.getNextWindowIndex() == -1)
                mSession.getController().getTransportControls().stop();
            else {
                queuePos = player.getNextWindowIndex();
                preparePlayer(player);
                player.setPlayWhenReady(true);
                LogHelper.d(TAG, "onPlayerError: Next");
            }
        }
    };

    NetworkRequest networkRequest = new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_BLUETOOTH)
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build();

    NetworkCallback networkCallback = new NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            isInternetAvailable = true;
        }

        @Override
        public void onLost(@NonNull Network network) {
            isInternetAvailable = false;
        }
    };

    interface PlaybackEndedStatus {
        int Invalid = 0;
        int Finished = 1;
        int Interrupted = 2;
    }

}



