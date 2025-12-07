package com.guang.cloudx.ui.downloadManager

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.guang.cloudx.BaseActivity
import com.guang.cloudx.R
import com.guang.cloudx.logic.model.MusicDownloadRules
import com.guang.cloudx.logic.utils.SharedPreferencesUtils
import com.guang.cloudx.logic.utils.SystemUtils
import com.guang.cloudx.logic.utils.applicationViewModels
import java.text.SimpleDateFormat
import java.util.*

class DownloadManagerActivity : BaseActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var toolbar: MaterialToolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download_manager)

        toolbar = findViewById(R.id.downloadTopAppBar)
        applyTopInsets(toolbar)

        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)

        val viewModel: DownloadViewModel by applicationViewModels(application)

        viewPager.adapter = DownloadPagerAdapter(this)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = if (position == 0) "正在下载" else "已完成"
        }.attach()

        toolbar.setNavigationOnClickListener { finish() }

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_delete_all -> {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("提示")
                        .setMessage("真的要删除全部记录吗？")
                        .setPositiveButton("确定"){ _, _ ->
                            viewModel.deleteAllCompleted {}
                        }
                        .setNegativeButton("取消", null)
                        .show()
                    true
                }
                else -> false
            }
        }


        // 切页时控制“全部删除”菜单：仅在“已完成”页可见
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                toolbar.menu.findItem(R.id.action_delete_all)?.isVisible = (position == 1)
            }
        })
        toolbar.menu.findItem(R.id.action_delete_all)?.isVisible = false
    }
}

class DownloadPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount() = 2
    override fun createFragment(position: Int): Fragment =
        if (position == 0) DownloadingFragment() else CompletedFragment()
}

class DownloadingFragment : Fragment(R.layout.fragment_download_list) {
    private lateinit var recycler: RecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val viewModel: DownloadViewModel by applicationViewModels(requireActivity().application)

        recycler = view.findViewById(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        (recycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        val adapter = InProgressAdapter(
                onRetry = { item ->
                    val prefs = SharedPreferencesUtils(requireContext())
                    (requireContext() as BaseActivity).dir?.let { viewModel.retryDownload(
                        requireContext(),
                        item,
                        prefs.getMusicLevel(),
                        prefs.getCookie(),
                        it,
                        MusicDownloadRules(
                            isSaveLrc = prefs.getIsSaveLrc(),
                            isSaveTlLrc = prefs.getIsSaveTlLrc(),
                            isSaveRomaLrc = prefs.getIsSaveRomaLrc(),
                            isSaveYrc = prefs.getIsSaveYrc(),
                            fileName = prefs.getDownloadFileName()!!,
                            delimiter = prefs.getArtistsDelimiter()!!,
                            encoding = prefs.getLrcEncoding()!!,
                            concurrentDownloads = prefs.getConcurrentDownloads()
                        )
                    ) }
                },
                onDeleteFailed = { item ->
                    viewModel.deleteFailed(item)
                }
            )
        recycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.downloading.collect { list ->
                adapter.submitList(list)
            }
        }
    }
}


class CompletedFragment : Fragment(R.layout.fragment_download_list) {
    private lateinit var recycler: RecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val viewModel: DownloadViewModel by applicationViewModels(requireActivity().application)

        recycler = view.findViewById(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        val adapter = CompletedAdapter(
            onDelete = { item -> viewModel.deleteCompleted(item) {} },
            onClick = { item ->
                val message = with(item) {
                    """
                    标题：${music.name}
                    音乐ID：${music.id}
                    艺术家：${music.artists.joinToString("、") { "${it.name}(${it.id})" }}
                    专辑：${music.album.name}
                    专辑ID：${music.album.id}
                    封面：${music.album.picUrl}
                    
                    保存时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timeStamp))}
                    """.trimIndent()
                }
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("详细信息")
                    .setMessage(message)
                    .setPositiveButton("关闭", null)
                    .setNeutralButton("复制") { _, _ ->
                        SystemUtils.copyToClipboard(requireContext(), "MusicDetail", message)
                    }
                    .show()
            }
        )
        recycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.completed.collect { list ->
                adapter.submitList(list.asReversed())
            }
        }
    }
}