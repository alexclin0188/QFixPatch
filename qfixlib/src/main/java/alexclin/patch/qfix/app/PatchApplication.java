package alexclin.patch.qfix.app;

import android.app.Application;
import android.content.res.Configuration;

import java.lang.reflect.Constructor;

/**
 * @author xiehonglin429 on 2016/12/23.
 */

public abstract class PatchApplication extends Application {
    private String delegateClassName;
    private ApplicationLifeCycle delegate;

    public PatchApplication(String delegateClassName) {
        this.delegateClassName = delegateClassName;
    }

    @Override
    public final void onCreate() {
        super.onCreate();
        ensureDelegate();
        delegate.onCreate();
    }

    @Override
    public final void onTerminate() {
        super.onTerminate();
        if(delegate!=null) delegate.onTerminate();
    }

    @Override
    public final void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if(delegate!=null) delegate.onConfigurationChanged(newConfig);
    }

    @Override
    public final void onLowMemory() {
        super.onLowMemory();
        if(delegate!=null) delegate.onLowMemory();
    }

    @Override
    public final void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if(delegate!=null) delegate.onTrimMemory(level);
    }

    private void ensureDelegate(){
        if(delegate==null){
            try {
                Class<?> delegateClass = Class.forName(delegateClassName,false,getClassLoader());
                Constructor<?> constructor = delegateClass.getConstructor(Application.class);
                delegate = (ApplicationLifeCycle) constructor.newInstance(this);
            } catch (Exception e) {
                throw new IllegalArgumentException("create app delegate failed",e);
            }
        }
    }
}
