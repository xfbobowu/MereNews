package androidnews.kiloproject.fragment;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.blankj.utilcode.util.SPUtils;
import com.blankj.utilcode.util.ScreenUtils;
import com.blankj.utilcode.util.SnackbarUtils;
import com.bumptech.glide.Glide;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.florent37.materialviewpager.header.MaterialViewPagerHeaderDecorator;
import com.google.gson.reflect.TypeToken;
import com.scwang.smartrefresh.layout.api.RefreshLayout;
import com.scwang.smartrefresh.layout.listener.OnLoadMoreListener;
import com.scwang.smartrefresh.layout.listener.OnRefreshListener;
import com.zhouyou.http.EasyHttp;
import com.zhouyou.http.callback.SimpleCallBack;
import com.zhouyou.http.exception.ApiException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import androidnews.kiloproject.R;
import androidnews.kiloproject.adapter.VideoRvAdapter;
import androidnews.kiloproject.bean.data.CacheNews;
import androidnews.kiloproject.bean.net.VideoListData;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

import static androidnews.kiloproject.bean.data.CacheNews.CACHE_HISTORY;
import static androidnews.kiloproject.system.AppConfig.CONFIG_AUTO_LOADMORE;
import static androidnews.kiloproject.system.AppConfig.CONFIG_AUTO_REFRESH;
import static androidnews.kiloproject.system.AppConfig.getVideosA;
import static androidnews.kiloproject.system.AppConfig.getVideosB;
import static androidnews.kiloproject.system.AppConfig.getVideosC;

public class VideoRvFragment extends BaseRvFragment {

    VideoRvAdapter mainAdapter;
    //    MainListData contents;
    List<VideoListData> contents;

    private static final boolean GRID_LAYOUT = false;

    private String CACHE_LIST_DATA;

    private int currentPage = 0;
    private int questPage = 20;

    String typeStr;

    public static VideoRvFragment newInstance(int type) {
        VideoRvFragment f = new VideoRvFragment();
        Bundle b = new Bundle();
        b.putInt("type", type);
        f.setArguments(b);
        return f;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        int position = 999;
        if (args != null) {
            position = args.getInt("type");
        }
        typeStr = getResources().getStringArray(R.array.address)[position];
        this.CACHE_LIST_DATA = typeStr + "_data";
        return super.onCreateView(inflater, container, savedInstanceState);
    }


    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        Observable.create(new ObservableOnSubscribe<Boolean>() {
            @Override
            public void subscribe(ObservableEmitter<Boolean> e) throws Exception {
                String json = SPUtils.getInstance().getString(CACHE_LIST_DATA, "");
                if (!TextUtils.isEmpty(json)) {
                    contents = gson.fromJson(json, new TypeToken<List<VideoListData>>() {
                    }.getType());
                    if (contents != null && contents.size() > 0) {
                        try {
                            final String cacheJson = SPUtils.getInstance().getString(CACHE_HISTORY + "", "");
                            List<CacheNews> cacheNews = gson.fromJson(cacheJson, new TypeToken<List<CacheNews>>() {
                            }.getType());
                            if (cacheNews != null && cacheNews.size() > 0)
                                for (VideoListData data : contents) {
                                    for (CacheNews cacheNew : cacheNews) {
                                        if (TextUtils.equals(data.getVid(), cacheNew.getDocid())) {
                                            data.setReaded(true);
                                            break;
                                        }
                                    }
                                }

                            e.onNext(true);
                        } catch (Exception e1) {
                            e1.printStackTrace();
                            e.onNext(false);
                        }
                    } else
                        e.onNext(false);
                } else e.onNext(false);
                e.onComplete();
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean s) throws Exception {
                        if (s)
                            createAdapter();
                        VideoRvFragment.super.onViewCreated(view, savedInstanceState);
                    }
                });
        if (GRID_LAYOUT) {
            mRecyclerView.setLayoutManager(new GridLayoutManager(mActivity, 2));
        } else {
            mRecyclerView.setLayoutManager(new LinearLayoutManager(mActivity));
        }
        mRecyclerView.setHasFixedSize(true);

        mRecyclerView.addItemDecoration(new MaterialViewPagerHeaderDecorator());

//        refreshLayout.setRefreshHeader(new MaterialHeader(mActivity));
//        refreshLayout.setRefreshFooter(new ClassicsFooter(mActivity));
        refreshLayout.setOnRefreshListener(new OnRefreshListener() {
            @Override
            public void onRefresh(RefreshLayout refreshlayout) {
                requestData(TYPE_REFRESH);
            }
        });
        refreshLayout.setOnLoadMoreListener(new OnLoadMoreListener() {
            @Override
            public void onLoadMore(RefreshLayout refreshlayout) {
                requestData(TYPE_LOADMORE);
            }
        });
    }

    protected void onFragmentVisibleChange(boolean isVisible) {
        if (isVisible) {
            if (ScreenUtils.isLandscape()) {
                mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            } else {
                mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            }
            if (contents == null ||
                    (SPUtils.getInstance().getBoolean(CONFIG_AUTO_REFRESH)) &&
                            (System.currentTimeMillis() - lastAutoRefreshTime > dividerAutoRefresh)) {
                if (refreshLayout != null)
                    refreshLayout.autoRefresh();
            }
        } else {
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    private void requestData(int type) {
        String dataUrl = "";
        switch (type) {
            case TYPE_REFRESH:
                currentPage = 0;
            case TYPE_LOADMORE:
                dataUrl = getVideosA + typeStr + getVideosB + currentPage + getVideosC;
                break;
        }
        EasyHttp.get(dataUrl)
                .readTimeOut(30 * 1000)//局部定义读超时
                .writeTimeOut(30 * 1000)
                .connectTimeout(30 * 1000)
                .timeStamp(true)
                .execute(new SimpleCallBack<String>() {
                    @Override
                    public void onError(ApiException e) {
                        if (refreshLayout != null) {
                            switch (type) {
                                case TYPE_REFRESH:
                                    refreshLayout.finishRefresh(false);
                                    break;
                                case TYPE_LOADMORE:
                                    if (SPUtils.getInstance().getBoolean(CONFIG_AUTO_LOADMORE))
                                        mainAdapter.loadMoreFail();
                                    else
                                        refreshLayout.finishLoadMore(false);
                                    break;
                            }
                            SnackbarUtils.with(refreshLayout).
                                    setMessage(getString(R.string.load_fail) + e.getMessage()).
                                    showError();
                        }
                    }

                    @Override
                    public void onSuccess(String response) {
                        if (!TextUtils.isEmpty(response) || TextUtils.equals(response, "{}")) {
                            Observable.create(new ObservableOnSubscribe<Boolean>() {
                                @Override
                                public void subscribe(ObservableEmitter<Boolean> e) throws Exception {
                                    HashMap<String, List<VideoListData>> retMap = null;
                                    try {
                                        retMap = gson.fromJson(response,
                                                new TypeToken<HashMap<String, List<VideoListData>>>() {
                                                }.getType());
                                    } catch (Exception e1) {
                                        e1.printStackTrace();
                                        loadFailed(type);
                                    }
                                    List<VideoListData> newList = new ArrayList<>();

                                    final String cacheJson = SPUtils.getInstance().getString(CACHE_HISTORY + "", "");
                                    List<CacheNews> cacheNews = gson.fromJson(cacheJson, new TypeToken<List<CacheNews>>() {
                                    }.getType());

                                    switch (type) {
                                        case TYPE_REFRESH:
                                            currentPage = 0;
                                            contents = new ArrayList<>();
                                            try {
                                                newList = retMap.get(typeStr);
                                            } catch (Exception e1) {
                                                e1.printStackTrace();
                                                loadFailed(type);
                                            }
                                            contents.addAll(newList);
                                            try {
                                                SPUtils.getInstance().put(CACHE_LIST_DATA, gson.toJson(contents));
                                            } catch (Exception e1) {
                                                e1.printStackTrace();
                                            }
                                            break;
                                        case TYPE_LOADMORE:
                                            currentPage += questPage;
                                            try {
                                                newList.addAll(contents);
                                            } catch (Exception e1) {
                                                e1.printStackTrace();
                                            }
                                            boolean isAllSame = true;
                                            try {
                                                for (VideoListData newBean : retMap.get(typeStr)) {
                                                    boolean isSame = false;
//                                                if (TextUtils.isEmpty(newBean.getSource()) && !TextUtils.isEmpty(newBean.getTAG())){

                                                    for (VideoListData myBean : contents) {
                                                        if (TextUtils.equals(myBean.getVid(), newBean.getVid())) {
                                                            isSame = true;
                                                            break;
                                                        }
                                                    }
                                                    if (!isSame) {
                                                        if (cacheNews != null && cacheNews.size() > 0)
                                                            for (CacheNews cacheNew : cacheNews) {
                                                                if (TextUtils.equals(newBean.getVid(), cacheNew.getDocid())) {
                                                                    newBean.setReaded(true);
                                                                    break;
                                                                }
                                                            }

                                                        newList.add(newBean);
                                                        isAllSame = false;
                                                    }
                                                }
                                            } catch (Exception e1) {
                                                e1.printStackTrace();
                                                loadFailed(type);
                                            }
                                            if (!isAllSame) {
                                                contents.clear();
                                                contents.addAll(newList);
                                            }
                                            break;
                                    }
                                    e.onNext(true);
                                    e.onComplete();
                                }
                            }).subscribeOn(Schedulers.computation())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(new Consumer<Boolean>() {
                                        @Override
                                        public void accept(Boolean o) throws Exception {
                                            if (mainAdapter == null || type == TYPE_REFRESH) {
                                                createAdapter();
                                                lastAutoRefreshTime = System.currentTimeMillis();
                                                try {
                                                    refreshLayout.finishRefresh(true);
                                                    SnackbarUtils.with(refreshLayout)
                                                            .setMessage(getString(R.string.load_success))
                                                            .showSuccess();
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }

                                            } else if (type == TYPE_LOADMORE) {
                                                mainAdapter.notifyDataSetChanged();
                                                if (SPUtils.getInstance().getBoolean(CONFIG_AUTO_LOADMORE))
                                                    mainAdapter.loadMoreComplete();
                                                else
                                                    try {
                                                        refreshLayout.finishLoadMore(true);
                                                    } catch (Exception e) {
                                                        e.printStackTrace();
                                                    }
                                            }
                                        }
                                    });
                        } else {
                            loadFailed(type);
                        }
                    }
                });
    }

    private void loadFailed(int type) {
        switch (type) {
            case TYPE_REFRESH:
                refreshLayout.finishRefresh(false);
                SnackbarUtils.with(refreshLayout).setMessage(getString(R.string.server_fail)).showError();
                break;
            case TYPE_LOADMORE:
                if (SPUtils.getInstance().getBoolean(CONFIG_AUTO_LOADMORE))
                    mainAdapter.loadMoreFail();
                else
                    refreshLayout.finishLoadMore(false);
                SnackbarUtils.with(refreshLayout).setMessage(getString(R.string.server_fail)).showError();
                break;
        }
    }

    private void createAdapter() {
        mainAdapter = new VideoRvAdapter(mActivity,Glide.with(this), contents);
//        mainAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
//            @Override
//            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
//                VideoListData bean = contents.get(position);
//                Intent intent = null;
//            }
//        });
//        mainAdapter.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
//            @Override
//            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
//
//                return true;
//            }
//        });
        if (mRecyclerView == null)
            return;
        mRecyclerView.setAdapter(mainAdapter);
        if (SPUtils.getInstance().getBoolean(CONFIG_AUTO_LOADMORE)) {
            mainAdapter.setPreLoadNumber(5);
            mainAdapter.setOnLoadMoreListener(new BaseQuickAdapter.RequestLoadMoreListener() {
                @Override
                public void onLoadMoreRequested() {
                    requestData(TYPE_LOADMORE);
                }
            }, mRecyclerView);
            mainAdapter.disableLoadMoreIfNotFullPage();
            refreshLayout.setEnableLoadMore(false);
        }
    }

}