package com.yash.ymplayer.ui.youtube.search;

import android.app.SearchManager;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.yash.logging.LogHelper;
import com.yash.ymplayer.BasePlayerActivity;
import com.yash.ymplayer.R;
import com.yash.ymplayer.databinding.ActivityUtubeSearchBinding;
import com.yash.ymplayer.databinding.BasePlayerActivityBinding;
import com.yash.ymplayer.repository.OnlineYoutubeRepository;
import com.yash.ymplayer.ui.youtube.YoutubeTracksAdapter;
import com.yash.ymplayer.util.DownloadUtil;
import com.yash.ymplayer.util.StringUtil;
import com.yash.ymplayer.util.TrackContextMenuClickListener;
import com.yash.ymplayer.util.YoutubeSong;
import com.yash.youtube_extractor.utility.JsonUtil;


import org.apache.commons.text.StringEscapeUtils;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YoutubeSearch extends BasePlayerActivity {
    private static final String TAG = "YoutubeSearch";
    ActivityUtubeSearchBinding utubeSearchBinding;
    SearchView searchView;
    MediaControllerCompat mediaController;
    Handler handler = new Handler();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState, MediaBrowserCompat mediaBrowser, BasePlayerActivityBinding playerActivityBinding) {
        utubeSearchBinding = ActivityUtubeSearchBinding.inflate(getLayoutInflater());
        playerActivityBinding.container.addView(utubeSearchBinding.getRoot());
        setCustomToolbar(null, "Search");
        utubeSearchBinding.progressBar.setVisibility(View.INVISIBLE);
        utubeSearchBinding.noResult.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onConnected(MediaControllerCompat mediaController) {
        this.mediaController = mediaController;
    }

    @Override
    public void refresh() {

    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.app_search_menu, menu);
        MenuItem item = menu.findItem(R.id.search_menu);
        item.expandActionView();
        searchView = (SearchView) item.getActionView();
        searchView.setIconified(false);
        searchView.setOnCloseListener(() -> {
            YoutubeSearch.this.finish();
            return true;
        });
        searchView.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        MatrixCursor matrixCursor = new MatrixCursor(new String[]{SearchManager.SUGGEST_COLUMN_TEXT_1, "_id"});
        matrixCursor.addRow(Arrays.asList("", 1));
        SuggestionAdapter suggestionAdapter = new SuggestionAdapter(this, matrixCursor);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (!StringUtil.hasLength(query)) {
                    Toast.makeText(YoutubeSearch.this, "Please enter text to search", Toast.LENGTH_SHORT).show();
                }
                query = query.trim();
                String mediaIdPrefix = "Search/" + query + "|";
                utubeSearchBinding.progressBar.setVisibility(View.VISIBLE);
                LogHelper.d(TAG, "onQueryTextSubmit: " + query);
                OnlineYoutubeRepository.getInstance(YoutubeSearch.this).searchTracks(query, new OnlineYoutubeRepository.TracksLoadedCallback() {
                    @Override
                    public void onLoaded(List<YoutubeSong> songs) {
                        LogHelper.d(TAG, "onLoaded: Youtube search songs" + songs);
                        runOnUiThread(() -> {
                            YoutubeTracksAdapter adapter = new YoutubeTracksAdapter(YoutubeSearch.this, songs, new TrackContextMenuClickListener(YoutubeSearch.this, mediaController, mediaIdPrefix));
                            utubeSearchBinding.noResult.setVisibility(songs.isEmpty() ? View.VISIBLE : View.INVISIBLE);
                            utubeSearchBinding.progressBar.setVisibility(View.INVISIBLE);
                            utubeSearchBinding.searchResultContainer.setLayoutManager(new LinearLayoutManager(YoutubeSearch.this));
                            utubeSearchBinding.searchResultContainer.setAdapter(adapter);
                        });
                    }

                    @Override
                    public <E extends Exception> void onError(E e) {
                        LogHelper.e(TAG, "onError: ", e);
                    }
                });
                return false;
            }

             private Runnable queryTask = null;
            @Override
            public boolean onQueryTextChange(String newText) {
                if(queryTask != null)
                    handler.removeCallbacks(queryTask);
                queryTask = () -> getSuggestions(newText, suggestionAdapter::updateCursor);
                handler.postDelayed(queryTask, 100);
                return true;
            }
        });
        String query = getIntent().getStringExtra("query");
        if (StringUtil.hasText(query)) {
            searchView.setQuery(query, true);
        }

        // Set up the suggestions adapter
        SearchManager searchManager = (SearchManager) getSystemService(SEARCH_SERVICE);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setSuggestionsAdapter(suggestionAdapter);
        searchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override
            public boolean onSuggestionSelect(int position) {
                return false;
            }

            @Override
            public boolean onSuggestionClick(int position) {
                CursorAdapter cursorAdapter = searchView.getSuggestionsAdapter();
                Cursor cursor = cursorAdapter.getCursor();
                cursor.moveToPosition(position);
                String suggestion = cursor.getString(cursor.getColumnIndexOrThrow(SearchManager.SUGGEST_COLUMN_TEXT_1));
                searchView.setQuery(suggestion, true);
                return true;
            }
        });
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Pattern pattern = Pattern.compile("\\[\"([^\"]+)\",");

    private void getSuggestions(String query, CallBack callback) {
        executorService.execute(() -> {
            try {
                String url = "https://suggestqueries-clients6.youtube.com/complete/search?client=youtube&hl=en&gl=in&sugexp=foo%2Chm.evie%3D0&gs_rn=64&gs_ri=youtube&ds=yt&cp=1&gs_id=4&q=" + URLEncoder.encode(query) + "&xhr=t&xssi=t";
                byte[] download = DownloadUtil.download(url);
                String response = StringEscapeUtils.unescapeJava(new String(download, StandardCharsets.UTF_8));
                String result = JsonUtil.extractJsonFromHtml("[", response);
                Matcher matcher = pattern.matcher(result);
                MatrixCursor cursor = new MatrixCursor(new String[]{SearchManager.SUGGEST_COLUMN_TEXT_1, "_id"});
                int i = 0;
                while (matcher.find()) {
                    LogHelper.d(TAG, "Suggestion available : %s", matcher.group(1));
                    cursor.addRow(Arrays.asList(matcher.group(1), ++i));
                }
                callback.onDone(cursor);
                LogHelper.d(TAG, "getSuggestions: result " + result);
            } catch (Exception e) {
                Log.e(TAG, "getSuggestions: ", e);
            }
        });
    }


    public static class SuggestionAdapter extends CursorAdapter {

        private final Handler handler = new Handler(Looper.getMainLooper());

        public SuggestionAdapter(Context context, Cursor c) {
            super(context, c, false);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return LayoutInflater.from(context).inflate(R.layout.item_simple_list, parent, false);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            String suggestion = cursor.getString(cursor.getColumnIndexOrThrow(SearchManager.SUGGEST_COLUMN_TEXT_1));
            TextView tvSuggestion = view.findViewById(android.R.id.text1);
            tvSuggestion.setText(suggestion);
        }


        public void updateCursor(Cursor c) {
            handler.post(() -> {
                changeCursor(c);
                notifyDataSetChanged();;
            });
        }
    }

    public interface CallBack {
        void onDone(Cursor c);
    }
}
