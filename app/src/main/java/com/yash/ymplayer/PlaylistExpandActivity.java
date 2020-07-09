package com.yash.ymplayer;

import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat.Token;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.yash.ymplayer.databinding.ListExpandActivityBinding;
import com.yash.ymplayer.helper.LogHelper;
import com.yash.ymplayer.repository.OnlineYoutubeRepository;
import com.yash.ymplayer.ui.youtube.YoutubeTracksAdapter;
import com.yash.ymplayer.util.Keys;
import com.yash.ymplayer.util.YoutubeSong;
import java.util.ArrayList;
import java.util.List;

public class PlaylistExpandActivity extends BaseActivity{
    private static final String TAG = "PlaylistExpandActivity";
    ListExpandActivityBinding activityBinding;
    String playlistId;
    String title;
    String headerArt;
    MediaBrowserCompat mediaBrowser;
    MediaControllerCompat mediaController;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityBinding = ListExpandActivityBinding.inflate(getLayoutInflater());
        setContentView(activityBinding.getRoot());
        setSupportActionBar(activityBinding.toolbar);

        Intent intent = getIntent();
        playlistId = intent.getStringExtra(Keys.EXTRA_PARENT_ID);
        title = intent.getStringExtra(Keys.EXTRA_TITLE);
        headerArt = intent.getStringExtra(Keys.EXTRA_ART_URL);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(title);

        Glide.with(this).load(headerArt).into(activityBinding.appBarImage);
        mediaBrowser =new MediaBrowserCompat(this,new ComponentName(this,PlayerService.class),connectionCallback,null);
        mediaBrowser.connect();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mediaBrowser.disconnect();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if(item.getItemId() == android.R.id.home)
            finish();
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void refresh() {

    }

    MediaBrowserCompat.ConnectionCallback connectionCallback = new MediaBrowserCompat.ConnectionCallback(){
        @Override
        public void onConnected() {
            try {
                mediaController = new MediaControllerCompat(PlaylistExpandActivity.this, mediaBrowser.getSessionToken());
                OnlineYoutubeRepository.getInstance(PlaylistExpandActivity.this).getTracks(playlistId, "-1", new OnlineYoutubeRepository.TracksLoadedCallback() {
                    @Override
                    public void onLoaded(List<YoutubeSong> songs) {
                        activityBinding.listProgress.setVisibility(View.GONE);
                        YoutubeTracksAdapter adapter = new YoutubeTracksAdapter(PlaylistExpandActivity.this, songs, new YoutubeTracksAdapter.TrackClickListener() {
                            @Override
                            public void onClick(YoutubeSong song) {
                                String id = playlistId +"|"+ song.getVideoId();
                                LogHelper.d(TAG, "onClick: uri"+id);
                                mediaController.getTransportControls().playFromUri(Uri.parse(id),null);
                            }
                        });
                        activityBinding.list.setLayoutManager(new LinearLayoutManager(PlaylistExpandActivity.this));
                        activityBinding.list.setAdapter(adapter);
                    }

                    @Override
                    public void onError() {
                        LogHelper.d(TAG, "onError: ");
                    }
                });
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };
}
