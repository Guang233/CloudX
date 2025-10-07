package com.guang.cloudx.ui.downloadManager

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.guang.cloudx.BaseActivity
import com.guang.cloudx.R
import com.guang.cloudx.logic.utils.SharedPreferencesUtils
import com.guang.cloudx.logic.utils.applicationViewModels

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
                    viewModel.deleteAllCompleted()
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
        val adapter = InProgressAdapter(
                onRetry = { item ->
                    (requireContext() as BaseActivity).dir?.let { viewModel.retryDownload(requireContext(), item, SharedPreferencesUtils(requireContext()).getMusicLevel(), SharedPreferencesUtils(requireContext()).getCookie(), it) }
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
        val adapter = CompletedAdapter(onDelete = { item -> viewModel.deleteCompleted(item) })
        recycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.completed.collect { list ->
                adapter.submitList(list)
            }
        }
    }
}


