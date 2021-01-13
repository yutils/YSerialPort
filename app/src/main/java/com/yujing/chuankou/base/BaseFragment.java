package com.yujing.chuankou.base;

import androidx.databinding.ViewDataBinding;

import com.yujing.base.YBaseFragment;

/**
 * 基础Fragment
 *
 * @author yujing 2021年1月13日17:21:58
 */
/* 用法举例
public class Fragment1 extends BaseFragment<ActivityMainBinding> {
    public Fragment1() {
        super(R.layout.activity_main);
    }
    @Override
    protected void init() {
    }
}
*/
public abstract class BaseFragment<B extends ViewDataBinding> extends YBaseFragment<B> {
    protected B binding;

    public BaseFragment(int layout) {
        super(layout);
    }

    @Override
    public void initBefore() {
        binding = getBinding();
    }
}
