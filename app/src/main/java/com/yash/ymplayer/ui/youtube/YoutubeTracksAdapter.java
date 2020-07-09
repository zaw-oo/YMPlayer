package com.yash.ymplayer.ui.youtube;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.yash.ymplayer.databinding.ItemMusicBinding;
import com.yash.ymplayer.util.YoutubeSong;

import java.util.List;

public class YoutubeTracksAdapter extends RecyclerView.Adapter<YoutubeTracksAdapter.YouTubeTracksViewHolder> {
    Context context;
    List<YoutubeSong> songs;
    TrackClickListener listener;

    public YoutubeTracksAdapter(Context context, List<YoutubeSong> songs, TrackClickListener listener) {
        this.context = context;
        this.songs = songs;
        this.listener = listener;
    }

    @NonNull
    @Override
    public YouTubeTracksViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMusicBinding binding = ItemMusicBinding.inflate(LayoutInflater.from(parent.getContext()));
        return new YouTubeTracksViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull YouTubeTracksViewHolder holder, int position) {
        holder.bindTracks(songs.get(position),listener);
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }


   class YouTubeTracksViewHolder extends RecyclerView.ViewHolder{
        ItemMusicBinding binding;

        public YouTubeTracksViewHolder(@NonNull ItemMusicBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bindTracks(YoutubeSong song,TrackClickListener listener){
            binding.title.setText(song.getTitle());
            binding.subTitle.setText(song.getChannelTitle());
            Glide.with(context).load(song.getArt_url_small()).into(binding.art);
            itemView.setOnClickListener(v -> listener.onClick(song));
        }
    }


    public interface TrackClickListener{
        void onClick(YoutubeSong song);
    }
}
