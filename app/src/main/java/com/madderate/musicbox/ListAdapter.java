package com.madderate.musicbox;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.madderate.musicbox.model.MusicInfo;

import java.util.List;

public class ListAdapter extends BaseAdapter {

    private List<MusicInfo> musicList;
    private LayoutInflater layoutInflater;
    private Context context;
    private int currentPos = -1;
    private ViewHolder holder = null;

    public ListAdapter(Context context, List<MusicInfo> musicList) {
        this.musicList = musicList;
        this.context = context;

        layoutInflater = LayoutInflater.from(context);
    }

    public void setFocusItemPos(int pos) {
        currentPos = pos;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return musicList.size();
    }

    @Override
    public Object getItem(int position) {
        return musicList.get(position).getMusicTitle();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public void remove(int index) {
        musicList.remove(index);
    }

    public void refreshDataSet() {
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = layoutInflater.inflate(R.layout.item_layout, null);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder)convertView.getTag();
        }
        if (position == currentPos) {
            holder.itemIcon.setImageResource(R.drawable.music_playing);
            holder.itemMusicName.setTextColor(Color.RED);
            holder.itemMusicSinger.setTextColor(Color.RED);
        } else {
            holder.itemIcon.setImageResource(R.drawable.music);
            holder.itemMusicName.setTextColor(holder.defaultTextColor);
            holder.itemMusicSinger.setTextColor(holder.defaultTextColor);
        }
        holder.itemMusicName.setText(musicList.get(position).getMusicTitle());
        holder.itemMusicSinger.setText(musicList.get(position).getMusicArtist());
        return convertView;
    }

    static class ViewHolder {
        ImageView itemIcon;
        TextView itemMusicName;
        TextView itemMusicSinger;
        int defaultTextColor;

        View itemView;
        ViewHolder(View itemView) {
            if (itemView == null) {
                throw new IllegalArgumentException("itemView cannot be null!");
            }
            this.itemView = itemView;
            itemIcon = itemView.findViewById(R.id.rand_icon);
            itemMusicName = itemView.findViewById(R.id.item_music_name);
            itemMusicSinger = itemView.findViewById(R.id.item_music_singer);
            defaultTextColor = itemMusicName.getCurrentTextColor();
        }
    }
}
