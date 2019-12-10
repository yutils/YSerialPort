package com.yujing.chuankou;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.fragment.app.Fragment;

import com.yujing.utils.YToast;

@SuppressWarnings("unused")
public abstract class BaseFragment<B extends ViewDataBinding> extends Fragment {
    protected B binding;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Integer contentViewId = getContentLayoutId();
        if (contentViewId != null) {
            binding = DataBindingUtil.inflate(inflater, contentViewId, container, false);
        }
        initData();
        return binding == null ? null : binding.getRoot();
    }

    protected abstract Integer getContentLayoutId();

    protected abstract void initData();

    protected int dip2px(float dpValue) {
        float scale = getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    protected void show(String str) {
        YToast.show(App.getContext(), str);
    }

    protected void startActivity(Class<?> classActivity) {
        Intent intent = new Intent();
        intent.setClass(getActivity(), classActivity);
        startActivity(intent);
    }

    protected void startActivity(Class<?> classActivity, int resultCode) {
        startActivityForResult(classActivity, resultCode);
    }

    protected void startActivityForResult(Class<?> classActivity, int resultCode) {
        Intent intent = new Intent();
        intent.setClass(getActivity(), classActivity);
        startActivityForResult(intent, resultCode);
    }
}
