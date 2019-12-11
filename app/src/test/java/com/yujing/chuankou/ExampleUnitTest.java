package com.yujing.chuankou;

import org.junit.Test;
/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void test() throws InterruptedException {

        Thread thread=new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("1111111111111111111");
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("111111111222222222222");

            }
        });


//        Thread thread2=new Thread(new Runnable() {
//            @Override
//            public void run() {
//                System.out.println("222222222221111111111111111");
//                try {
//                    thread.join(1000);
//                } catch (InterruptedException e) {
//                    System.out.println("异常");
//                }
//                System.out.println("2222222222222222222222222");
//            }
//        });

        thread.start();
        try {
            Thread.sleep(100);
//            thread2.start();
//            thread.interrupt();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            thread.join(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("33333333333333333333");

    }

}