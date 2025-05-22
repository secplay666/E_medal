package com.example.t4

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions

/**
 * 图片画廊适配器，用于显示图片网格
 * @param imageList 图片URI列表
 * @param onItemClick 图片点击回调
 */
class ImageGalleryAdapter(
        private val imageList: List<Uri>,
        private val onItemClick: (Uri) -> Unit
) : RecyclerView.Adapter<ImageGalleryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.galleryImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
                LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_gallery_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val imageUri = imageList[position]

        // 使用Glide加载图片，并添加过渡效果和占位图
        Glide.with(holder.itemView.context)
            .load(imageUri)
            .apply(RequestOptions()
                .centerCrop()
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_error))
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(holder.imageView)

        // 设置点击事件
        holder.itemView.setOnClickListener { onItemClick(imageUri) }
    }

    override fun getItemCount() = imageList.size
}
