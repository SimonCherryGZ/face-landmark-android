package com.simoncherry.artest.ui.adapter;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.simoncherry.artest.R;
import com.simoncherry.artest.model.Ornament;

import java.util.List;

/**
 * Created by Simon on 2017/6/25.
 */

public class OrnamentAdapter extends RecyclerView.Adapter<OrnamentAdapter.MyViewHolder>{
    private Context mContext;
    private List<Ornament> mData;

    public OrnamentAdapter(Context mContext, List<Ornament> mData) {
        this.mContext = mContext;
        this.mData = mData;
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new MyViewHolder(LayoutInflater.from(mContext)
                .inflate(R.layout.item_img, parent, false));
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    @Override
    public void onBindViewHolder(MyViewHolder holder, final int position) {
        final Ornament bean = mData.get(position);
        if (bean != null) {
            Glide.with(mContext).load(bean.getImgResId())
                    .placeholder(R.mipmap.ic_launcher)
                    .error(R.mipmap.ic_launcher)
                    .into(holder.ivImg);

            holder.ivImg.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (onItemClickListener != null) {
                        onItemClickListener.onItemClick(position);
                    }
                }
            });
        } else {
            holder.ivImg.setImageResource(0);
        }
    }

    class MyViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivImg;

        MyViewHolder(View itemView) {
            super(itemView);
            ivImg = (ImageView) itemView.findViewById(R.id.iv_img);
        }
    }

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    private OnItemClickListener onItemClickListener;

    public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }
}
