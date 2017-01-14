package alexclin.qfix.qfixgradle;

import android.app.Application;
import android.util.Log;

import alexclin.patch.qfix.app.ApplicationLike;

/**
 * AppDelegate
 *
 * @author alexclin  2017/1/14 13:54
 */

public class AppDelegate extends ApplicationLike {
    public AppDelegate(Application realApplication) {
        super(realApplication);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("AppDelegate","You can do real Application logic here");
        Log.e("AppDelegate","Test log bug origin-----");
    }
}
