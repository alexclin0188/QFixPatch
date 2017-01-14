package alexclin.patch.qfix.app;

/**
 * @author xiehonglin429 on 2016/12/23.
 */

import android.app.Application;
import android.content.res.Configuration;

/**
 * This interface is used to delegate calls from main Application object.
 *
 * Implementations of this interface must have a one-argument constructor that takes
 * an argument of type {@link Application}.
 */
public interface ApplicationLifeCycle {

    /**
     * Same as {@link Application#onCreate()}.
     */
    void onCreate();

    /**
     * Same as {@link Application#onLowMemory()}.
     */
    void onLowMemory();

    /**
     * Same as {@link Application#onTrimMemory(int level)}.
     * @param level
     */
    void onTrimMemory(int level);

    /**
     * Same as {@link Application#onTerminate()}.
     */
    void onTerminate();

    /**
     * Same as {@link Application#onConfigurationChanged(Configuration newconfig)}.
     */
    void onConfigurationChanged(Configuration newConfig);
}
