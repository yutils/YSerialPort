package com.yujing.chuankou.base;

import androidx.databinding.ViewDataBinding;

import com.yujing.base.YBaseActivity;

/**
 * 基础activity
 *
 * @author yujing 2021年1月13日17:21:58
 */
/* 用法举例
public class MainActivity extends BaseActivity<ActivityMainBinding> {
    public MainActivity() {
        super(R.layout.activity_main);
    }
    @Override
    protected void init() {
    }
}
*/
public abstract class BaseActivity<B extends ViewDataBinding> extends YBaseActivity<B> {
    protected B binding;

    public BaseActivity(int layout) {
        super(layout);
    }

    @Override
    public void initBefore() {
        binding = getBinding();
    }
}
