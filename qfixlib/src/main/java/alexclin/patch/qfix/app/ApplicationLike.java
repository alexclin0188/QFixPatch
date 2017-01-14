package alexclin.patch.qfix.app;

import android.app.Application;
import android.content.res.Configuration;
import android.support.annotation.NonNull;

/**
 * @author xiehonglin429 on 2016/12/23.
 */

public class ApplicationLike implements ApplicationLifeCycle {
    private Application realApplication;

    public ApplicationLike(@NonNull Application realApplication) {
        this.realApplication = realApplication;
    }

    public Application getApplication(){
        return realApplication;
    }

    @Override
    public void onCreate() {

    }

    @Override
    public void onLowMemory() {

    }

    @Override
    public void onTrimMemory(int level) {

    }

    @Override
    public void onTerminate() {

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {

    }
}
