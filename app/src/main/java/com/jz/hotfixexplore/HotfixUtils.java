package com.jz.hotfixexplore;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;
import android.support.annotation.Nullable;
import android.util.ArrayMap;
import android.util.Log;
import android.view.ContextThemeWrapper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import dalvik.system.BaseDexClassLoader;
import dalvik.system.DexClassLoader;

/**
 * @author: JerryZhu
 * @datetime: 2023/4/25
 */
public class HotfixUtils {

    public static native String stringFromJNI();

    public static void fixClass(Context context, ClassLoader classLoader, String patchJarPath) {

        String dexOptPath = context.getDir("dexopt", 0).getAbsolutePath();

        //DexClassLoader可以用于加载外部apk,jar等
        DexClassLoader patchDexClassLoader = new DexClassLoader(patchJarPath, dexOptPath, null, classLoader);

        /*
         * 合并插件和宿主的DexPathList的dexElements字段并设置到宿主的dexElements字段中
         * 由于加载好的dex最终存储在BaseDexClassLoader的dexPathList对象的dexElements字段中,
         * dexElements是个数组,所以这里需要将插件中的dexElements与宿主apk中dexElements进行合并
         * */
        Log.e(Constant.TAG,"classLoader ==========" + classLoader);
        Log.e(Constant.TAG,"开始合并插件和宿主的DexPathList的dexElements ==========");
        try {
            //获取补丁中的dexElements
            Field dexPathListField = BaseDexClassLoader.class.getDeclaredField("pathList");
            dexPathListField.setAccessible(true);
            Object patchDexPathList= dexPathListField.get(patchDexClassLoader);
            Field patchDexElementsFiled = patchDexPathList.getClass().getDeclaredField("dexElements");
            patchDexElementsFiled.setAccessible(true);
            Object[] pluginDexElements = (Object[]) patchDexElementsFiled.get(patchDexPathList);

            //获取应用中的dexElements
            Field oriDexPathListField = BaseDexClassLoader.class.getDeclaredField("pathList");
            oriDexPathListField.setAccessible(true);
            Object oriDexPathList = oriDexPathListField.get(classLoader);
            Field oriDexElementsField = oriDexPathList.getClass().getDeclaredField("dexElements");
            oriDexElementsField.setAccessible(true);
            Object[] oriDexElements = (Object[]) oriDexElementsField.get(oriDexPathList);

            //合并插件和宿主的dexElements
            Object[] newDexElements = (Object[]) Array.newInstance(oriDexElements.getClass().getComponentType(),
                    pluginDexElements.length + oriDexElements.length);
            System.arraycopy(pluginDexElements, 0, newDexElements, 0, pluginDexElements.length);
            System.arraycopy(oriDexElements, 0, newDexElements, pluginDexElements.length, oriDexElements.length);

            //合并后的dexElements字段设置到宿主的dexElements字段中
            oriDexElementsField.set(oriDexPathList,newDexElements);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        Log.e(Constant.TAG,"合并完成 ==========");
    }

    public static void fixRes(@Nullable Context context, @Nullable String apkPath, @Nullable Collection<Activity> activities) {
        if (apkPath == null) {
            return;
        }
        try {
            //利用反射创建一个新的AssetManager
            AssetManager newAssetManager = AssetManager.class.getConstructor().newInstance();
            //利用反射获取addAssetPath方法
            Method mAddAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
            mAddAssetPath.setAccessible(true);
            //利用反射调用addAssetPath方法加载外部的资源（SD卡）
            if (((Integer) mAddAssetPath.invoke(newAssetManager, apkPath)) == 0) {
                throw new IllegalStateException("Could not create new AssetManager");
            }
            // Kitkat needs this method call, Lollipop doesn't. However, it doesn't seem to cause any harm
            // in L, so we do it unconditionally.
            Method mEnsureStringBlocks = AssetManager.class.getDeclaredMethod("ensureStringBlocks");
            mEnsureStringBlocks.setAccessible(true);
            mEnsureStringBlocks.invoke(newAssetManager);
            if (activities != null) {
                //遍历activities
                for (Activity activity : activities) {
                    //拿到Activity的Resources
                    Resources resources = activity.getResources();
                    try {
                        //获取Resources的成员变量mAssets
                        Field mAssets = Resources.class.getDeclaredField("mAssets");
                        mAssets.setAccessible(true);
                        //给成员变量mAssets重新赋值为自己创建的newAssetManager
                        mAssets.set(resources, newAssetManager);
                    } catch (Throwable ignore) {
                        Field mResourcesImpl = Resources.class.getDeclaredField("mResourcesImpl");
                        mResourcesImpl.setAccessible(true);
                        Object resourceImpl = mResourcesImpl.get(resources);
                        Field implAssets = resourceImpl.getClass().getDeclaredField("mAssets");
                        implAssets.setAccessible(true);
                        implAssets.set(resourceImpl, newAssetManager);
                    }
                    //获取activity的theme
                    Resources.Theme theme = activity.getTheme();
                    try {
                        try {
                            //反射得到Resources.Theme的mAssets变量
                            Field ma = Resources.Theme.class.getDeclaredField("mAssets");
                            ma.setAccessible(true);
                            //将Resources.Theme的mAssets替换成newAssetManager
                            ma.set(theme, newAssetManager);
                        } catch (NoSuchFieldException ignore) {
                            Field themeField = Resources.Theme.class.getDeclaredField("mThemeImpl");
                            themeField.setAccessible(true);
                            Object impl = themeField.get(theme);
                            Field ma = impl.getClass().getDeclaredField("mAssets");
                            ma.setAccessible(true);
                            ma.set(impl, newAssetManager);
                        }
                        Field mt = ContextThemeWrapper.class.getDeclaredField("mTheme");
                        mt.setAccessible(true);
                        mt.set(activity, null);
                        Method mtm = ContextThemeWrapper.class.getDeclaredMethod("initializeTheme");
                        mtm.setAccessible(true);
                        mtm.invoke(activity);
                        Method mCreateTheme = AssetManager.class.getDeclaredMethod("createTheme");
                        mCreateTheme.setAccessible(true);
                        Object internalTheme = mCreateTheme.invoke(newAssetManager);
                       /* Field mTheme = Resources.Theme.class.getDeclaredField("mTheme");
                        mTheme.setAccessible(true);
                        mTheme.set(theme, internalTheme);*/
                    } catch (Throwable e) {
                        Log.e(Constant.TAG, "Failed to update existing theme for activity " + activity,
                                e);
                    }
                    //pruneResourceCaches(resources);
                }
            }
            // 根据sdk版本的不同，用不同的方式获取Resources的弱引用集合
            Collection<WeakReference<Resources>> references;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                // Find the singleton instance of ResourcesManager
                Class<?> resourcesManagerClass = Class.forName("android.app.ResourcesManager");
                Method mGetInstance = resourcesManagerClass.getDeclaredMethod("getInstance");
                mGetInstance.setAccessible(true);
                Object resourcesManager = mGetInstance.invoke(null);
                try {
                    Field fMActiveResources = resourcesManagerClass.getDeclaredField("mActiveResources");
                    fMActiveResources.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    ArrayMap<?, WeakReference<Resources>> arrayMap =
                            (ArrayMap<?, WeakReference<Resources>>) fMActiveResources.get(resourcesManager);
                    references = arrayMap.values();
                } catch (NoSuchFieldException ignore) {
                    Field mResourceReferences = resourcesManagerClass.getDeclaredField("mResourceReferences");
                    mResourceReferences.setAccessible(true);
                    //noinspection unchecked
                    references = (Collection<WeakReference<Resources>>) mResourceReferences.get(resourcesManager);
                }
            } else {
                Class<?> activityThread = Class.forName("android.app.ActivityThread");
                Field fMActiveResources = activityThread.getDeclaredField("mActiveResources");
                fMActiveResources.setAccessible(true);
                Object thread = getActivityThread(context, activityThread);
                @SuppressWarnings("unchecked")
                HashMap<?, WeakReference<Resources>> map =
                        (HashMap<?, WeakReference<Resources>>) fMActiveResources.get(thread);
                references = map.values();
            }
            //将的到的弱引用集合遍历得到Resources，将Resources中的mAssets字段替换为newAssetManager
            for (WeakReference<Resources> wr : references) {
                Resources resources = wr.get();
                if (resources != null) {
                    // Set the AssetManager of the Resources instance to our brand new one
                    try {
                        Field mAssets = Resources.class.getDeclaredField("mAssets");
                        mAssets.setAccessible(true);
                        mAssets.set(resources, newAssetManager);
                    } catch (Throwable ignore) {
                        Field mResourcesImpl = Resources.class.getDeclaredField("mResourcesImpl");
                        mResourcesImpl.setAccessible(true);
                        Object resourceImpl = mResourcesImpl.get(resources);
                        Field implAssets = resourceImpl.getClass().getDeclaredField("mAssets");
                        implAssets.setAccessible(true);
                        implAssets.set(resourceImpl, newAssetManager);
                    }
                    resources.updateConfiguration(resources.getConfiguration(), resources.getDisplayMetrics());
                }
            }
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }


    public static Object getActivityThread(@Nullable Context context, @Nullable Class<?> activityThread) {
        try {
            if (activityThread == null) {
                activityThread = Class.forName("android.app.ActivityThread");
            }
            Method m = activityThread.getMethod("currentActivityThread");
            m.setAccessible(true);
            Object currentActivityThread = m.invoke(null);
            if (currentActivityThread == null && context != null) {
                // In older versions of Android (prior to frameworks/base 66a017b63461a22842)
                // the currentActivityThread was built on thread locals, so we'll need to try
                // even harder
                Field mLoadedApk = context.getClass().getField("mLoadedApk");
                mLoadedApk.setAccessible(true);
                Object apk = mLoadedApk.get(context);
                Field mActivityThreadField = apk.getClass().getDeclaredField("mActivityThread");
                mActivityThreadField.setAccessible(true);
                currentActivityThread = mActivityThreadField.get(apk);
            }
            return currentActivityThread;
        } catch (Throwable ignore) {
            return null;
        }
    }

    public static void fixSo(Context context){
        try {
            String fixedSoPath =  "/sdcard/hotfixexplore_fixed.so";
            // 开辟一个输入流
            File fixedSoFile = new File(fixedSoPath);
            // 判断需加载的文件是否存在
            if (!fixedSoFile.exists()){
                System.loadLibrary("hotfixexplore");
                return;
            }
            FileInputStream fis = new FileInputStream(fixedSoFile);

            File dir = context.getDir("libs", Context.MODE_PRIVATE);
            // 获取驱动文件输出流
            File soFile = new File(dir,"hotfixexplore_fixed.so");
            if (!soFile.exists()) {
                Log.v(Constant.TAG, "" + soFile.getAbsolutePath() + " is not exists");
                FileOutputStream fos = new FileOutputStream(soFile);

                // 字节数组输出流，写入到内存中(ram)
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len = -1;
                while ((len = fis.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                // 从内存到写入到具体文件
                fos.write(baos.toByteArray());
                // 关闭文件流
                baos.close();
                fos.close();
            }
            fis.close();
            Log.v(Constant.TAG, "System.load start");
            // 加载外设驱动
            System.load(soFile.getAbsolutePath());
            Log.v(Constant.TAG, "System.load End");

        } catch (Exception e) {

            e.printStackTrace();

        }
    }


}
